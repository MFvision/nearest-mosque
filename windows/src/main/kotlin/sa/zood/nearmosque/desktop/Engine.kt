package sa.zood.nearmosque.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import javazoom.jl.player.Player
import sa.zood.nearmosque.core.AdhanSource
import sa.zood.nearmosque.core.AlertKind
import sa.zood.nearmosque.core.AlertPlanner
import sa.zood.nearmosque.core.City
import sa.zood.nearmosque.core.CityIndex
import sa.zood.nearmosque.core.DaySchedule
import sa.zood.nearmosque.core.HijriCalendar
import sa.zood.nearmosque.core.PlannedAlert
import sa.zood.nearmosque.core.PrayerCalculator
import sa.zood.nearmosque.core.PrayerEvent
import sa.zood.nearmosque.core.PrayerMoment
import sa.zood.nearmosque.core.PrayerSettings
import sa.zood.nearmosque.core.Reciter
import sa.zood.nearmosque.core.VoiceLanguage
import sa.zood.nearmosque.core.moment
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

/** The city table (packs/cities/cities.tsv), loaded once. */
object Cities {
    val index: CityIndex by lazy { CityIndex.parse(Strings.resource("cities.tsv")) }

    fun byId(id: Long?): City? = id?.let { i -> index.cities.firstOrNull { it.id == i } }

    /** First run: the PC's time zone's city, so times show at once (the person can change it). */
    fun guess(): City? = inZone(ZoneId.systemDefault())

    /** The city a time zone is named after (Asia/Riyadh: Riyadh), else the zone's largest. */
    fun inZone(zone: ZoneId): City? {
        val all = index.cities.filter { it.zoneId == zone.id }
        val named = zone.id.substringAfterLast('/').replace('_', ' ')
        return all.filter { it.asciiName.equals(named, true) || it.name.equals(named, true) }.maxByOrNull { it.population }
            ?: all.maxByOrNull { it.population }
    }
}

/** The PC's time zone as Windows or Linux has it now: Java keeps the one it started with unless asked again. */
object PcZone {
    /** Set on the command line (-Duser.timezone): kept as it is. Taken in main(), before Java fills the property in. */
    @Volatile var fixed = false

    fun read(): ZoneId {
        if (!fixed) {
            System.clearProperty("user.timezone")
            java.util.TimeZone.setDefault(null)
        }
        return ZoneId.systemDefault()
    }
}

/**
 * The PC's time zone when it no longer keeps the chosen city's time (a laptop taken abroad) and the person has not
 * kept the city for it, else null. The offsets now are compared, not the names, so Asia/Kuwait and Asia/Riyadh agree.
 */
fun zoneChange(pc: ZoneId?, city: City?, s: DesktopSettings, now: Instant): ZoneId? {
    if (pc == null || city == null || s.cityId != city.id || !s.onboarded || pc.id == s.zoneKept) return null
    val cityZone = runCatching { ZoneId.of(city.zoneId) }.getOrNull() ?: return null
    return pc.takeIf { it.rules.getOffset(now) != cityZone.rules.getOffset(now) }
}

/** A time zone as the person knows it: its city's name, or its name when the table has none. */
fun zoneName(zone: ZoneId): String = Cities.inZone(zone)?.displayName(Strings.language) ?: zone.id

/** Prayer times for the chosen city, in the city's own time zone, with the person's calculation choices. */
class Clock(val city: City, private val s: DesktopSettings) {
    val zone: ZoneId = runCatching { ZoneId.of(city.zoneId) }.getOrDefault(ZoneId.systemDefault())
    private val calc = PrayerCalculator()
    val settings = PrayerSettings(
        method = s.method ?: PrayerCalculator.suggestedMethod(city.countryCode),
        madhab = s.madhab,
        highLatitudeRule = s.highLatitudeRule,
        polarRule = s.polarRule,
        offsets = s.offsets,
        ramadanIshaExtension = s.ramadanIsha,
        hijriAdjustmentDays = s.hijriAdjustment,
    )

    fun day(date: LocalDate): DaySchedule = calc.schedule(city.location, date, zone, settings)

    fun today(now: Instant = Instant.now()): DaySchedule = day(now.atZone(zone).toLocalDate())

    fun next(now: Instant = Instant.now()) = calc.nextPrayer(city.location, now, zone, settings)

    val iqama get() = s.iqama

    /** The countdown to the next Adhan, the wait for the iqama, or the prayer itself (yesterday to tomorrow). */
    fun moment(now: Instant = Instant.now()): PrayerMoment? {
        val today = now.atZone(zone).toLocalDate()
        return calc.moment((-1L..1L).map { day(today.plusDays(it)) }, now, s.iqama)
    }
}

/** One line for the notification-area icon: the next prayer, the wait for the iqama, or the prayer under way. */
fun trayStatus(clock: Clock?, now: Instant = Instant.now()): String {
    val m = clock?.moment(now) ?: return t("app_name")
    val name = t(prayerKey(m.event))
    val line = when (m.stage) {
        PrayerMoment.Stage.UPCOMING -> "$name ${clockText(m.adhan, clock.zone)}" + (m.iqama?.let { " · " + t("iqama_at", clockText(it, clock.zone)) } ?: "")
        PrayerMoment.Stage.TO_IQAMA -> t("reminder_title", name) + " · " + t("iqama_left", t("minutes_value", minutesLeft(now, m.until)))
        PrayerMoment.Stage.PRAYER -> t("prayer_started", name)
    }
    return t("app_name") + " · " + line
}

/** Whole minutes until [until], rounded up (so "1 min" shows until the very end). */
fun minutesLeft(now: Instant, until: Instant): Int =
    ((PrayerCalculator.remaining(now, until).seconds + 59) / 60).toInt()

/**
 * Plays the app's recordings (MP3 in the app's resources) one at a time. Without a working sound device (none, or
 * busy) or a missing recording it stays silent: the notification still shows and nothing stops the app.
 */
object Sound {
    /**
     * Which sound may cut which: a recitation never cuts the Adhan, and no alert cuts the Fajr alarm (the Fajr Adhan
     * ends an alarm set before Fajr through [stopAlarm]; one "at Fajr" makes it wait).
     */
    enum class Priority { RECITATION, VOICE, ADHAN, ALARM }

    /** The Fajr alarm stops on its own after this long, so a PC left alone does not ring for hours. */
    val ALARM_MAX: Duration = Duration.ofMinutes(10)

    private val lock = Any()
    @Volatile private var player: Player? = null
    /** What plays now (a recording's name), or null; read by the screens to show Stop. */
    var playing: String? by mutableStateOf(null)
        private set
    /** The Fajr alarm is ringing. */
    var alarm: Boolean by mutableStateOf(false)
        private set
    /**
     * The last Fajr alarm ended on its own before [ALARM_MAX] without Stop: no sound device (or a busy line), so the
     * window keeps showing it with Stop for its full time.
     */
    var alarmUnheard: Boolean by mutableStateOf(false)
        private set
    /** The priority of what plays now; null for a sound the person started (any alert may replace it). */
    @Volatile private var priority: Priority? = null
    /** Bumped by every stop and start, so an old alarm loop knows it is no longer wanted. */
    private val generation = AtomicInteger()

    fun adhanFile(event: PrayerEvent, s: DesktopSettings): String {
        val fajr = event == PrayerEvent.FAJR
        val src = if (fajr) s.fajrAdhanSource else s.adhanSource
        val place = if (src == AdhanSource.MADINAH) "madinah" else "makkah"
        // The short Adhan is the same first takbir at every prayer.
        if (s.shortAdhan) return "adhan_${place}_short"
        return if (fajr) "fajr_$place" else "adhan_$place"
    }

    /**
     * The recording for a planned alert, as on the phone (AudioCatalog), or null for a notification without sound.
     * The Fajr alarm has no alarm tone of its own: the short Adhan of the Fajr source, repeated.
     */
    fun file(kind: AlertKind, e: PrayerEvent, s: DesktopSettings): String? = when (kind) {
        AlertKind.ADHAN -> adhanFile(e, s)
        AlertKind.BEFORE_10, AlertKind.BEFORE_5, AlertKind.NEAR -> {
            val v = s.voice ?: VoiceLanguage.forInterface(s.language)
            val variant = when (kind) { AlertKind.BEFORE_10 -> "10"; AlertKind.BEFORE_5 -> "5"; else -> "near" }
            "voice_" + (if (v == VoiceLanguage.ARABIC) "ar" else "en") + "_" + e.name.lowercase() + "_" + variant
        }
        AlertKind.SALAWAT -> if (s.reciter == Reciter.ALAFASY) "salawat_alafasy" else "salawat_husary"
        AlertKind.QURAN, AlertKind.SLEEP -> if (s.reciter == Reciter.ALAFASY) "evening_alafasy" else "evening_abdulbasit"
        AlertKind.FAJR_ALARM -> "adhan_" + s.fajrAdhanSource.name.lowercase() + "_short"
        else -> null
    }

    /** "Ten (or five) minutes remain until …", Arabic for an Arabic interface, English otherwise. */
    fun reminderFile(event: PrayerEvent, language: String, minutes: Int = 10): String =
        file(if (minutes == 5) AlertKind.BEFORE_5 else AlertKind.BEFORE_10, event, DesktopSettings(language = language))!!

    /** The priority of an alert's sound. */
    fun priorityOf(kind: AlertKind): Priority = when (kind) {
        AlertKind.FAJR_ALARM -> Priority.ALARM
        AlertKind.ADHAN -> Priority.ADHAN
        AlertKind.BEFORE_10, AlertKind.BEFORE_5, AlertKind.NEAR -> Priority.VOICE
        else -> Priority.RECITATION
    }

    /** Whether a sound of priority [next] may start while one of [current] plays (null: nothing, or the person's own). */
    fun allows(next: Priority, current: Priority?): Boolean = current == null || next >= current

    private fun open(sound: String) = Sound::class.java.classLoader.getResourceAsStream("sounds/$sound.mp3")

    /** Plays [sound] for the person (a preview, ▶): it replaces whatever plays. */
    fun play(sound: String) = start(sound, null)

    /**
     * Plays an alert's [sound], unless something that comes first plays (the alarm, or the Adhan over a recitation).
     * False when it was held back, so the caller may try again once that ends.
     */
    fun playAlert(sound: String, priority: Priority): Boolean {
        synchronized(lock) { if (playing != null && !allows(priority, this.priority)) return false }
        start(sound, priority)
        return true
    }

    private fun start(sound: String, priority: Priority?) {
        stop()
        val stream = open(sound) ?: return
        val p = runCatching { Player(stream.buffered()) }.getOrElse { runCatching { stream.close() }; return }
        synchronized(lock) {
            player = p
            playing = sound
            this.priority = priority
        }
        Thread {
            runCatching { p.play() }
            runCatching { p.close() }
            synchronized(lock) { if (player === p) { player = null; playing = null; this.priority = null } }
        }.apply { isDaemon = true }.start()
    }

    /**
     * The Fajr alarm: [sound] again and again, with a short pause, until [stop] (or another sound) or [ALARM_MAX].
     * Without a sound device, or with the recording missing, it ends at once instead of trying over and over.
     */
    fun loop(sound: String) {
        stop()
        val gen = generation.incrementAndGet()
        synchronized(lock) {
            alarm = true
            alarmUnheard = false
            playing = sound
            priority = Priority.ALARM
        }
        Thread {
            val began = System.nanoTime()
            runCatching {
                ring(running = { generation.get() == gen }, maxMillis = ALARM_MAX.toMillis()) {
                    // Each round opens the recording again: the player reads its stream to the end.
                    val stream = open(sound) ?: return@ring false
                    val p = runCatching { Player(stream.buffered()) }.getOrElse { runCatching { stream.close() }; return@ring false }
                    synchronized(lock) { if (generation.get() == gen) player = p else { runCatching { p.close() }; return@ring false } }
                    // No device or a busy line: play() throws (JavaSoundAudioDevice.createSource).
                    val ok = runCatching { p.play() }.isSuccess
                    runCatching { p.close() }
                    ok
                }
            }
            synchronized(lock) {
                if (generation.get() == gen) {
                    // Still wanted but over before its time: it could not ring (set before `alarm`, so the card never blinks).
                    alarmUnheard = System.nanoTime() - began < ALARM_MAX.toNanos()
                    player = null; playing = null; priority = null; alarm = false
                }
            }
        }.apply { isDaemon = true; name = "fajr-alarm" }.start()
    }

    /**
     * The alarm's rounds: [round] plays the sound once (false when it could not). Repeats, with [pauseMillis] between,
     * while [running]; ends when a round fails or ends in under a second (so it never spins), or after [maxMillis].
     * Returns the number of rounds. Timed by the monotonic clock, so a change of the PC's clock does not stretch it.
     */
    internal fun ring(
        running: () -> Boolean, maxMillis: Long, pauseMillis: Long = 1_500,
        now: () -> Long = { System.nanoTime() / 1_000_000 }, sleep: (Long) -> Unit = Thread::sleep, round: () -> Boolean,
    ): Int {
        val start = now()
        var rounds = 0
        while (running() && now() - start < maxMillis) {
            val began = now()
            val ok = round()
            rounds++
            if (!ok || now() - began < 1_000) break
            if (!running()) break
            sleep(pauseMillis)
        }
        return rounds
    }

    /** Ends the Fajr alarm if it rings; anything else that plays goes on. */
    fun stopAlarm() {
        synchronized(lock) { if (alarm) stop() }
    }

    /** Stops whatever plays, the Fajr alarm included. */
    fun stop() {
        synchronized(lock) {
            generation.incrementAndGet()
            player?.let { runCatching { it.close() } }
            player = null
            playing = null
            priority = null
            alarm = false
        }
    }
}

/**
 * Fires the prayer reminders while the app runs (it stays in the notification area when its window is closed), from
 * the shared core's planner with the person's choices: at each prayer a notification and, unless silenced for that
 * prayer, the Adhan; the 10, 5 and "approaching" reminders (spoken); sunrise; the iqama (a notification only);
 * Friday, Al-Kahf and salawat; the Qur'an and bedtime verses; suhoor and iftar in Ramadan; and the Fajr alarm.
 * Checked every second against freshly computed times, so a clock change is caught up; an alert is never more than
 * two minutes late, so after a restart the ones missed do not all come at once. After a sleep (a gap between two
 * ticks) what came due in the last [LATE_MAX] still comes, and what is older is named in one notice.
 */
class Reminders(private val notify: (String, String) -> Unit) {
    /**
     * Alerts already given, with their date; kept while that date is among the planner's days (yesterday to
     * tomorrow), so a clock set back does not give them again.
     */
    private val fired = mutableMapOf<String, LocalDate>()

    /** A sound held back by one that comes first (the Fajr Adhan behind an alarm "at Fajr"), tried again within its two minutes. */
    private class Waiting(val file: String, val priority: Sound.Priority, val until: Instant)
    private var waiting: Waiting? = null

    /** The Fajr alarm started ringing (the window shows Stop). */
    var onAlarm: () -> Unit = {}
    /** The Fajr alarm's notification (urgent where the desktop allows); the plain one by default. */
    var notifyAlarm: (String, String) -> Unit = notify
    /** Plays an alert's recording, false when held back; tests pass their own so no sound plays. */
    var sound: (String, Sound.Priority) -> Boolean = Sound::playAlert
    /** Rings the Fajr alarm until Stop. */
    var alarmSound: (String) -> Unit = Sound::loop
    /** Ends the Fajr alarm (when the Fajr Adhan comes after an alarm set before Fajr). */
    var endAlarm: () -> Unit = Sound::stopAlarm

    internal val firedKeys: Set<String> get() = fired.keys

    /** The previous tick; null before the first, so a start-up catches nothing up. */
    private var lastTick: Instant? = null

    fun tick(clock: Clock?, s: DesktopSettings, now: Instant = Instant.now()) {
        val last = lastTick
        lastTick = now
        clock ?: return
        // What waited plays once what held it back ends (Stop), if that is within its two minutes.
        waiting?.let { w -> if (now.isAfter(w.until) || sound(w.file, w.priority)) waiting = null }
        // More than two minutes since the last tick: the PC slept (or the clock jumped), so plan from that tick.
        val lateFrom = now.minusSeconds(120)
        val asleep = last != null && last.isBefore(lateFrom)
        val (days, plan) = plan(clock, s, now, from = if (asleep) maxOf(last, now.minus(MISSED_MAX)) else lateFrom)
        val missed = mutableListOf<PlannedAlert>()
        plan.forEach { a ->
            val key = "${a.date}-${a.kind}-${a.event}"
            if (!a.at.isBefore(lateFrom)) fire(key, a.date, a.at, now) { deliver(a, days, clock, s) }
            else if (key !in fired) {
                fired[key] = a.date
                when {
                    // "10 minutes until Dhuhr" is no longer true: dropped, the Adhan still comes on time.
                    a.kind in countdowns -> {}
                    !a.at.isBefore(now.minus(LATE_MAX)) -> deliver(a, days, clock, s, heldUntil = now.plusSeconds(120))
                    a.kind in prayerTimes -> missed += a
                }
            }
        }
        // One notice for the older ones, without sound, after what was given now.
        if (missed.isNotEmpty()) notify(t("missed_title"), missed.joinToString(" · ") { missedLine(it, clock) })
        val yesterday = now.atZone(clock.zone).toLocalDate().minusDays(1)
        fired.values.removeAll { it.isBefore(yesterday) }
    }

    /**
     * Settings → Test notification: the next prayer's Adhan notification through the same path as the real one, and
     * the short Adhan, so the sound device is checked too. Nothing is marked as given.
     */
    fun test(clock: Clock, s: DesktopSettings, now: Instant = Instant.now()) {
        val e = clock.next(now)?.event ?: PrayerEvent.DHUHR
        notify(t(prayerKey(e)), t("test_notification_body"))
        sound(Sound.adhanFile(e, s.copy(shortAdhan = true)), Sound.Priority.ADHAN)
    }

    private fun missedLine(a: PlannedAlert, clock: Clock): String =
        (if (a.kind == AlertKind.FAJR_ALARM) t("alerts_fajr_alarm") else t(prayerKey(a.event))) + " " + clockText(a.at, clock.zone)

    private fun deliver(a: PlannedAlert, days: List<DaySchedule>, clock: Clock, s: DesktopSettings, heldUntil: Instant = a.at.plusSeconds(120)) {
        val (title, body) = text(a, days, clock)
        val file = Sound.file(a.kind, a.event, s)
        if (a.kind == AlertKind.FAJR_ALARM) {
            notifyAlarm(title, body)
            file?.let(alarmSound)
            onAlarm()
            return
        }
        notify(title, body)
        // An alarm set before Fajr has done its work when the Fajr Adhan comes; one "at Fajr" goes on and the Adhan waits.
        if (a.kind == AlertKind.ADHAN && a.event == PrayerEvent.FAJR && (s.fajrAlarmMinutesBefore ?: 0) > 0) endAlarm()
        file?.let {
            val p = Sound.priorityOf(a.kind)
            if (!sound(it, p) && waiting.let { w -> w == null || p >= w.priority }) waiting = Waiting(it, p, heldUntil)
        }
    }

    /** Runs once per key, within two minutes after its time (never for times long past, e.g. after start-up). */
    private fun fire(key: String, date: LocalDate, at: Instant, now: Instant, action: () -> Unit) {
        if (now.isBefore(at) || now.isAfter(at.plusSeconds(120)) || key in fired) return
        fired[key] = date
        action()
    }

    companion object {
        /** After a sleep, what came due this recently is still given (the Adhan, the Fajr alarm, the iqama…). */
        val LATE_MAX: Duration = Duration.ofMinutes(10)
        /** How far back a sleep is looked at for the notice of what was missed. */
        val MISSED_MAX: Duration = Duration.ofHours(24)
        /** Reminders that count down to a prayer, which mean nothing late. */
        private val countdowns = setOf(AlertKind.BEFORE, AlertKind.BEFORE_10, AlertKind.BEFORE_5, AlertKind.NEAR)
        /** What the notice of missed alerts names: the prayer times (sunrise when chosen) and the Fajr alarm. */
        private val prayerTimes = setOf(AlertKind.ADHAN, AlertKind.AT_PRAYER, AlertKind.FAJR_ALARM)

        /**
         * The days around [now] and the alerts on them after [from] (two minutes ago). Yesterday for an Isha iqama
         * after midnight; tomorrow for what comes before an early Fajr on the evening before (suhoor, the Fajr alarm,
         * the reminders). Every prayer notifies at its time; sunrise when chosen.
         */
        fun plan(clock: Clock, s: DesktopSettings, now: Instant, from: Instant = now.minusSeconds(120)): Pair<List<DaySchedule>, List<PlannedAlert>> {
            val date = now.atZone(clock.zone).toLocalDate()
            val days = listOf(clock.day(date.minusDays(1)), clock.day(date), clock.day(date.plusDays(1)))
            val atPrayer = PrayerEvent.prayers.toSet() + if (s.sunrise) setOf(PrayerEvent.SUNRISE) else emptySet()
            val plan = AlertPlanner.plan(days, from, atPrayer, s.alerts(), s.iqama) { d -> HijriCalendar.isRamadan(d, s.hijriAdjustment) }
            return days to plan
        }

        /** An alert's notification: its title and text, as on the phone (the desktop's own wording where it had one). */
        fun text(a: PlannedAlert, days: List<DaySchedule>, clock: Clock): Pair<String, String> {
            val prayerAt = days.firstOrNull { it.date == a.date }?.get(a.event) ?: a.at
            val time = clockText(prayerAt, clock.zone)
            val name = t(prayerKey(a.event))
            val city = clock.city.displayName(Strings.language)
            val minutes = ((prayerAt.epochSecond - a.at.epochSecond) / 60).toInt()
            return when (a.kind) {
                AlertKind.AT_PRAYER if a.event == PrayerEvent.SUNRISE -> t("notif_sunrise_title") to time
                AlertKind.AT_PRAYER, AlertKind.ADHAN -> name to time
                AlertKind.BEFORE_10, AlertKind.BEFORE_5 -> name to t("notif_minutes_until", minutes, name)
                AlertKind.NEAR -> name to t("notif_near_title", name)
                AlertKind.FRIDAY -> t("reminder_friday_title") to t("reminder_friday_body", time)
                AlertKind.SUHOOR -> t("reminder_suhoor_title") to t("reminder_suhoor_body", time)
                AlertKind.IFTAR -> t("reminder_iftar_title") to t("reminder_body", city, time)
                AlertKind.SALAWAT -> t("notif_salawat_title") to t("salawat_note")
                AlertKind.QURAN -> t("notif_quran_title") to t("notif_quran_body")
                AlertKind.SLEEP -> t("notif_sleep_title") to t("alerts_sleep_note")
                AlertKind.KAHF -> t("notif_kahf_title") to t("alerts_kahf_note")
                AlertKind.FAJR_ALARM -> t("reminder_title", name) to t("reminder_body", city, time)
                AlertKind.IQAMA -> t("notif_iqama_title", name) to t("notif_iqama_body")
                AlertKind.BEFORE -> name to t("reminder_before_title", name, minutes)
            }
        }
    }
}

fun prayerKey(e: PrayerEvent): String = "prayer_" + e.name.lowercase()

fun clockText(at: Instant, zone: ZoneId): String =
    java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)
        .withLocale(java.util.Locale.forLanguageTag(Strings.language))
        .format(at.atZone(zone))
