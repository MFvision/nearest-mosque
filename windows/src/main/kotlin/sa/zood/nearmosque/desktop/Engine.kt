package sa.zood.nearmosque.desktop

import javazoom.jl.player.Player
import sa.zood.nearmosque.core.AdhanSource
import sa.zood.nearmosque.core.AlertKind
import sa.zood.nearmosque.core.AlertPlanner
import sa.zood.nearmosque.core.AlertSettings
import sa.zood.nearmosque.core.City
import sa.zood.nearmosque.core.CityIndex
import sa.zood.nearmosque.core.DaySchedule
import sa.zood.nearmosque.core.PrayerCalculator
import sa.zood.nearmosque.core.PrayerEvent
import sa.zood.nearmosque.core.PrayerMoment
import sa.zood.nearmosque.core.PrayerSettings
import sa.zood.nearmosque.core.moment
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The city table (packs/cities/cities.tsv), loaded once. */
object Cities {
    val index: CityIndex by lazy { CityIndex.parse(Strings.resource("cities.tsv")) }

    fun byId(id: Long?): City? = id?.let { i -> index.cities.firstOrNull { it.id == i } }

    /** First run: the largest city in the PC's time zone, so times show at once (the person can change it). */
    fun guess(): City? {
        val zone = ZoneId.systemDefault().id
        return index.cities.filter { it.zoneId == zone }.maxByOrNull { it.population }
    }
}

/** Prayer times for the chosen city, in the city's own time zone. */
class Clock(val city: City, private val s: DesktopSettings) {
    val zone: ZoneId = runCatching { ZoneId.of(city.zoneId) }.getOrDefault(ZoneId.systemDefault())
    private val calc = PrayerCalculator()
    val settings = PrayerSettings(
        method = s.method ?: PrayerCalculator.suggestedMethod(city.countryCode),
        madhab = s.madhab,
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

/** Plays the app's recordings (MP3 in the app's resources) one at a time. */
object Sound {
    @Volatile private var player: Player? = null
    @Volatile var playing: String? = null
        private set

    fun adhanFile(event: PrayerEvent, s: DesktopSettings): String {
        val fajr = event == PrayerEvent.FAJR
        val src = if (fajr) s.fajrAdhanSource else s.adhanSource
        val place = if (src == AdhanSource.MADINAH) "madinah" else "makkah"
        // The short Adhan is the same first takbir at every prayer.
        if (s.shortAdhan) return "adhan_${place}_short"
        return if (fajr) "fajr_$place" else "adhan_$place"
    }

    /** "Ten minutes remain until …", Arabic for an Arabic interface, English otherwise. */
    fun reminderFile(event: PrayerEvent, language: String): String =
        "voice_${if (language == "ar") "ar" else "en"}_${event.name.lowercase()}_10"

    fun play(sound: String) {
        stop()
        val stream = Sound::class.java.classLoader.getResourceAsStream("sounds/$sound.mp3") ?: return
        val p = Player(stream.buffered())
        player = p
        playing = sound
        Thread {
            runCatching { p.play() }
            if (player === p) { player = null; playing = null }
        }.apply { isDaemon = true }.start()
    }

    fun stop() {
        player?.close()
        player = null
        playing = null
    }
}

/**
 * Fires the prayer reminders while the app runs (it stays in the notification area when its window is closed):
 * at each prayer a notification and, unless silenced for that prayer, the Adhan; optionally ten minutes before,
 * at sunrise and (a notification only, no sound) at each iqama. Checked every 15 seconds against freshly computed times, so a sleep or clock change is caught up.
 */
class Reminders(private val notify: (String, String) -> Unit) {
    private val fired = mutableSetOf<String>()

    fun tick(clock: Clock?, s: DesktopSettings, now: Instant = Instant.now()) {
        clock ?: return
        val today = clock.today(now)
        for (e in PrayerEvent.entries) {
            val at = today[e] ?: continue
            if (e == PrayerEvent.SUNRISE) {
                if (s.sunrise) fire("${today.date}-$e", at, now) { notify(t("notif_sunrise_title"), clockText(at, clock.zone)) }
                continue
            }
            if (s.before10) {
                val early = at.minusSeconds(600)
                fire("${today.date}-$e-10", early, now) {
                    notify(t(prayerKey(e)), t("notif_minutes_until", 10, t(prayerKey(e))))
                    if (s.adhan && e !in s.silent) Sound.play(Sound.reminderFile(e, s.language))
                }
            }
            fire("${today.date}-$e", at, now) {
                notify(t(prayerKey(e)), clockText(at, clock.zone))
                if (s.adhan && e !in s.silent) Sound.play(Sound.adhanFile(e, s))
            }
        }
        // The iqama, planned by the shared core (every prayer notifies here); yesterday too, for an Isha iqama
        // after midnight. A plain notification: the Adhan has already been heard.
        AlertPlanner.plan(listOf(clock.day(today.date.minusDays(1)), today), now.minusSeconds(120), PrayerEvent.prayers.toSet(), AlertSettings(), s.iqama) { false }
            .filter { it.kind == AlertKind.IQAMA }
            .forEach { a ->
                fire("${a.date}-${a.event}-iqama", a.at, now) { notify(t("notif_iqama_title", t(prayerKey(a.event))), t("notif_iqama_body")) }
            }
        if (fired.size > 200) fired.clear()
    }

    /** Runs once per key, within two minutes after its time (never for times long past, e.g. after start-up). */
    private fun fire(key: String, at: Instant, now: Instant, action: () -> Unit) {
        if (now.isBefore(at) || now.isAfter(at.plusSeconds(120)) || !fired.add(key)) return
        action()
    }
}

fun prayerKey(e: PrayerEvent): String = "prayer_" + e.name.lowercase()

fun clockText(at: Instant, zone: ZoneId): String =
    java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)
        .withLocale(java.util.Locale.forLanguageTag(Strings.language))
        .format(at.atZone(zone))
