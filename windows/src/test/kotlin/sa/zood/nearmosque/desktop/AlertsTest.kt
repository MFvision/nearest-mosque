package sa.zood.nearmosque.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import sa.zood.nearmosque.core.AdhanSource
import sa.zood.nearmosque.core.AlertKind
import sa.zood.nearmosque.core.AlertSettings
import sa.zood.nearmosque.core.HighLatRule
import sa.zood.nearmosque.core.HijriCalendar
import sa.zood.nearmosque.core.PlannedAlert
import sa.zood.nearmosque.core.PrayerEvent
import sa.zood.nearmosque.core.PrayerSounds
import sa.zood.nearmosque.core.Reciter
import sa.zood.nearmosque.core.VoiceLanguage
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The reminders on the PC: what the shared planner schedules for the person's choices, how each is given, and how they are kept. */
class AlertsTest {
    private val riyadh get() = Cities.index.search("Riyadh", 1).first()
    private val riyadhZone = ZoneId.of("Asia/Riyadh")
    private val monday = LocalDate.of(2026, 10, 12)
    private val thursday = LocalDate.of(2026, 10, 15)
    private val friday = LocalDate.of(2026, 10, 16)

    private fun settingsFile(): File =
        File(Files.createTempDirectory("nm-alerts").toFile().apply { deleteOnExit() }, "settings.properties")

    /** Reminders with nothing real behind them: what was shown, played and rung (no sound plays). */
    private class Rig {
        val sent = mutableListOf<Pair<String, String>>()
        val urgent = mutableListOf<Pair<String, String>>()
        val played = mutableListOf<String>()
        val looped = mutableListOf<String>()
        var alarms = 0
        var ended = 0
        /** What "plays" now, with the same rule as the real player. */
        var current: Sound.Priority? = null
        val reminders = Reminders { title, body -> sent += title to body }.apply {
            notifyAlarm = { title, body -> urgent += title to body }
            sound = { name, p -> Sound.allows(p, current).also { if (it) { current = p; played += name } } }
            alarmSound = { name -> current = Sound.Priority.ALARM; looped += name }
            endAlarm = { if (current == Sound.Priority.ALARM) { current = null; ended++ } }
            onAlarm = { alarms++ }
        }
    }

    /** The alerts planned for [date] (in the city's time) with these settings. */
    private fun planOn(s: DesktopSettings, date: LocalDate, city: sa.zood.nearmosque.core.City = riyadh): List<PlannedAlert> {
        val clock = Clock(city, s)
        val start = date.atStartOfDay(clock.zone).toInstant()
        return Reminders.plan(clock, s, start.plusSeconds(120)).second.filter { it.at.atZone(clock.zone).toLocalDate() == date }
    }

    private fun at(date: LocalDate, hour: Int, minute: Int): Instant = date.atTime(hour, minute).atZone(riyadhZone).toInstant()

    @Test fun mappingKeepsTodaysAdhanRule() {
        val s = DesktopSettings(cityId = riyadh.id, silent = setOf(PrayerEvent.ASR))
        val a = s.alerts()
        PrayerEvent.prayers.forEach { e -> assertEquals(s.adhan && e !in s.silent, a.adhanPlays(e), "$e") }
        assertEquals(0, a.minutesBefore, "no single early reminder")
        assertTrue(a.fajrAdhan, "one Adhan switch for every prayer, Fajr included")
        val off = DesktopSettings(adhan = false).alerts()
        assertTrue(PrayerEvent.prayers.none { off.adhanPlays(it) })
        // Nothing new is on by default.
        val d = DesktopSettings().alerts()
        assertFalse(d.friday || d.ramadan || d.kahf || d.salawat || d.quran || d.near || d.before10 || d.before5)
        assertNull(d.fajrAlarmMinutesBefore)
        assertNull(d.bedtimeMinutes)
        // So a day is what it was: the Adhan (or a notification) at each prayer and each iqama.
        val plan = planOn(s, monday).map { it.kind to it.event }
        val want = PrayerEvent.prayers.flatMap { e -> listOf((if (e == PrayerEvent.ASR) AlertKind.AT_PRAYER else AlertKind.ADHAN) to e, AlertKind.IQAMA to e) }
        assertEquals(want.toSet(), plan.toSet())
        assertEquals(want.size, plan.size)
        // Changing nothing through the shared model changes nothing.
        assertEquals(s, s.withAlerts(a))
    }

    @Test fun fridayRemindersOnFridayOnly() {
        Strings.language = "en"
        val s = DesktopSettings(cityId = riyadh.id, friday = true, kahf = true, salawat = true)
        val clock = Clock(riyadh, s)
        val day = clock.day(friday)
        val dhuhr = day[PrayerEvent.DHUHR]!!
        val plan = planOn(s, friday)
        fun time(kind: AlertKind) = plan.single { it.kind == kind }.at
        assertEquals(dhuhr.minusSeconds(45 * 60), time(AlertKind.FRIDAY))
        assertEquals(at(friday, 9, 0), time(AlertKind.KAHF))
        assertEquals(at(friday, 11, 0), time(AlertKind.SALAWAT))
        assertTrue(planOn(s, thursday).none { it.kind in setOf(AlertKind.FRIDAY, AlertKind.KAHF, AlertKind.SALAWAT) }, "not on Thursday")

        val rig = Rig()
        rig.reminders.tick(clock, s, time(AlertKind.SALAWAT).plusSeconds(5))
        assertEquals(listOf("Salli Ala Rasulillah ﷺ" to "Fridays only, with Al-Ahzab 33:56 recited."), rig.sent)
        assertEquals(listOf("salawat_alafasy"), rig.played)
        val classic = s.copy(reciter = Reciter.CLASSIC)
        val husary = Rig()
        husary.reminders.tick(Clock(riyadh, classic), classic, time(AlertKind.SALAWAT).plusSeconds(5))
        assertEquals(listOf("salawat_husary"), husary.played)

        val jumuah = Rig()
        jumuah.reminders.tick(clock, s, time(AlertKind.FRIDAY).plusSeconds(5))
        assertEquals(listOf("Jumu'ah today" to "Dhuhr is at ${clockText(dhuhr, clock.zone)}. Time to get ready for the Friday prayer."), jumuah.sent)
        assertTrue(jumuah.played.isEmpty(), "a notification only")
        val kahf = Rig()
        kahf.reminders.tick(clock, s, time(AlertKind.KAHF).plusSeconds(5))
        assertEquals(listOf("Jumu'ah: Surat Al-Kahf" to "Friday at 9:00 in the morning, with its first verse."), kahf.sent)
        assertTrue(kahf.played.isEmpty())
    }

    @Test fun quranAndBedtimeVerses() {
        Strings.language = "en"
        val s = DesktopSettings(cityId = riyadh.id, quran = true, bedtimeMinutes = AlertSettings.DEFAULT_BEDTIME_MIN)
        val clock = Clock(riyadh, s)
        val plan = planOn(s, monday)
        assertEquals(at(monday, 20, 30), plan.single { it.kind == AlertKind.QURAN }.at)
        assertEquals(at(monday, 22, 30), plan.single { it.kind == AlertKind.SLEEP }.at)
        val rig = Rig()
        rig.reminders.tick(clock, s, at(monday, 20, 30).plusSeconds(5))
        assertEquals(listOf("Time for the Qur'an" to "Read a page today, even a few verses."), rig.sent)
        assertEquals(listOf("evening_alafasy"), rig.played)
        rig.reminders.tick(clock, s, at(monday, 22, 30).plusSeconds(5))
        assertEquals("Before you sleep", rig.sent.last().first)
        assertEquals(listOf("evening_alafasy", "evening_alafasy"), rig.played)
        val classic = s.copy(reciter = Reciter.CLASSIC)
        assertEquals("evening_abdulbasit", Sound.file(AlertKind.SLEEP, PrayerEvent.ISHA, classic))
    }

    @Test fun ramadanSuhoorAndIftar() {
        Strings.language = "en"
        val s = DesktopSettings(cityId = riyadh.id, ramadan = true)
        val clock = Clock(riyadh, s)
        val ramadanDay = LocalDate.of(2026, 3, 1) // 12 Ramadan 1447
        assertEquals(HijriCalendar.HijriDate(1447, 9, 12), HijriCalendar.of(ramadanDay, 0))
        val day = clock.day(ramadanDay)
        val plan = planOn(s, ramadanDay)
        assertEquals(day[PrayerEvent.FAJR]!!.minusSeconds(45 * 60), plan.single { it.kind == AlertKind.SUHOOR }.at)
        val maghrib = day[PrayerEvent.MAGHRIB]!!
        assertEquals(maghrib, plan.single { it.kind == AlertKind.IFTAR }.at)
        assertTrue(planOn(s, monday).none { it.kind == AlertKind.SUHOOR || it.kind == AlertKind.IFTAR }, "not outside Ramadan")
        assertTrue(planOn(s.copy(ramadan = false), ramadanDay).none { it.kind == AlertKind.SUHOOR || it.kind == AlertKind.IFTAR }, "off")

        val rig = Rig()
        rig.reminders.tick(clock, s, day[PrayerEvent.FAJR]!!.minusSeconds(45 * 60 - 5))
        assertEquals(listOf("Suhoor" to "Fajr is at ${clockText(day[PrayerEvent.FAJR]!!, clock.zone)}."), rig.sent)
        rig.reminders.tick(clock, s, maghrib.plusSeconds(5))
        assertTrue(("Iftar: time to break the fast" to "${riyadh.displayName("en")}, ${clockText(maghrib, clock.zone)}") in rig.sent)

        // The Hijri adjustment moves the first day of Ramadan, and the suhoor with it.
        val first = (0L..60L).map { LocalDate.of(2026, 1, 20).plusDays(it) }.first { HijriCalendar.isRamadan(it, 0) }
        assertTrue(planOn(s, first).any { it.kind == AlertKind.SUHOOR })
        assertTrue(planOn(s.copy(hijriAdjustment = -1), first).none { it.kind == AlertKind.SUHOOR }, "a day later with −1")
    }

    @Test fun nearReminderSpeaksInTheChosenVoice() {
        Strings.language = "en"
        val s = DesktopSettings(cityId = riyadh.id, adhan = false, near = true, nearMinutes = 20)
        val clock = Clock(riyadh, s)
        val dhuhr = clock.day(monday)[PrayerEvent.DHUHR]!!
        val rig = Rig()
        rig.reminders.tick(clock, s, dhuhr.minusSeconds(20 * 60 - 5))
        assertEquals(listOf("Dhuhr" to "The call to Dhuhr prayer is approaching"), rig.sent)
        // Spoken even with the Adhan off, as on the phone.
        assertEquals(listOf("voice_en_dhuhr_near"), rig.played)
        val arabic = s.copy(voice = VoiceLanguage.ARABIC)
        val ar = Rig()
        ar.reminders.tick(Clock(riyadh, arabic), arabic, dhuhr.minusSeconds(20 * 60 - 5))
        assertEquals(listOf("voice_ar_dhuhr_near"), ar.played)
        // No voice chosen: the interface's language.
        assertEquals("voice_ar_asr_5", Sound.file(AlertKind.BEFORE_5, PrayerEvent.ASR, DesktopSettings(language = "ar")))
        assertEquals("voice_en_asr_5", Sound.file(AlertKind.BEFORE_5, PrayerEvent.ASR, DesktopSettings(language = "ar", voice = VoiceLanguage.ENGLISH)))
    }

    @Test fun perPrayerSwitches() {
        val base = DesktopSettings(cityId = riyadh.id, before10 = true)
        val s = base.withAlerts(base.alerts().withPrayer(PrayerEvent.ASR, AlertSettings.PrayerSound.BEFORE_10, false))
        assertEquals(mapOf(PrayerEvent.ASR to PrayerSounds(before10 = false)), s.perPrayer)
        assertEquals(PrayerEvent.prayers - PrayerEvent.ASR, planOn(s, monday).filter { it.kind == AlertKind.BEFORE_10 }.map { it.event })
        assertEquals(PrayerEvent.prayers, planOn(base, monday).filter { it.kind == AlertKind.BEFORE_10 }.map { it.event })
        // On for one prayer while off for all: that prayer only.
        val one = DesktopSettings(cityId = riyadh.id).let { it.withAlerts(it.alerts().withPrayer(PrayerEvent.MAGHRIB, AlertSettings.PrayerSound.BEFORE_5, true)) }
        assertTrue(one.before5)
        assertEquals(listOf(PrayerEvent.MAGHRIB), planOn(one, monday).filter { it.kind == AlertKind.BEFORE_5 }.map { it.event })
        // The Adhan stays with "silent".
        val quiet = s.copy(silent = setOf(PrayerEvent.ASR))
        assertFalse(quiet.alerts().adhanPlays(PrayerEvent.ASR))
        assertTrue(quiet.alerts().isOn(PrayerEvent.FAJR, AlertSettings.PrayerSound.BEFORE_10))
        // A prayer's Adhan switch shows what plays, and turning it on with the Adhan off is for that prayer only.
        val off = DesktopSettings(cityId = riyadh.id, adhan = false)
        assertTrue(PrayerEvent.prayers.none { off.alerts().isOn(it, AlertSettings.PrayerSound.ADHAN) }, "all off while the Adhan is off")
        val maghrib = off.withAdhan(PrayerEvent.MAGHRIB, true)
        assertTrue(maghrib.adhan)
        assertEquals(listOf(PrayerEvent.MAGHRIB), PrayerEvent.prayers.filter { maghrib.alerts().adhanPlays(it) })
        assertEquals(listOf(PrayerEvent.MAGHRIB), planOn(maghrib, monday).filter { it.kind == AlertKind.ADHAN }.map { it.event })
        val asrToo = maghrib.withAdhan(PrayerEvent.ASR, true)
        assertEquals(listOf(PrayerEvent.ASR, PrayerEvent.MAGHRIB), PrayerEvent.prayers.filter { asrToo.alerts().adhanPlays(it) })
        assertEquals(setOf(PrayerEvent.ASR), DesktopSettings().withAdhan(PrayerEvent.ASR, false).silent)
        assertTrue(DesktopSettings().withAdhan(PrayerEvent.ASR, false).adhan, "one prayer off leaves the others")
    }

    @Test fun fajrAlarmRingsOnceAndWinsOverTheAdhan() {
        Strings.language = "en"
        val s = DesktopSettings(cityId = riyadh.id, fajrAlarmMinutesBefore = 10)
        val clock = Clock(riyadh, s)
        val fajr = clock.day(monday)[PrayerEvent.FAJR]!!
        val rig = Rig()
        rig.reminders.tick(clock, s, fajr.minusSeconds(600 - 5))
        assertEquals(listOf("adhan_makkah_short"), rig.looped)
        assertEquals(1, rig.alarms)
        assertEquals(listOf("Time for Fajr" to "${riyadh.displayName("en")}, ${clockText(fajr, clock.zone)}"), rig.urgent)
        rig.reminders.tick(clock, s, fajr.minusSeconds(600 - 30))
        rig.reminders.tick(clock, s, fajr.minusSeconds(600 - 110))
        assertEquals(1, rig.alarms, "no second ring inside the window")
        assertEquals(1, rig.looped.size)
        assertEquals("adhan_madinah_short", Sound.file(AlertKind.FAJR_ALARM, PrayerEvent.FAJR, s.copy(fajrAdhanSource = AdhanSource.MADINAH)))

        // At Fajr: the alarm comes first and the Fajr Adhan does not cut it.
        val atFajr = s.copy(fajrAlarmMinutesBefore = 0)
        val atFajrClock = Clock(riyadh, atFajr)
        val both = Rig()
        both.reminders.tick(atFajrClock, atFajr, fajr.plusSeconds(5))
        assertEquals(listOf("adhan_makkah_short"), both.looped)
        assertTrue(both.played.isEmpty(), "the Adhan waits for the alarm: ${both.played}")
        assertEquals(listOf("Fajr" to clockText(fajr, clock.zone)), both.sent, "the Fajr notification still shows")
        both.reminders.tick(atFajrClock, atFajr, fajr.plusSeconds(30))
        assertTrue(both.played.isEmpty(), "still ringing: the Adhan still waits")
        assertEquals(0, both.ended, "nothing cuts an alarm at Fajr")
        // Stop within the Adhan's two minutes: the Adhan plays, once.
        both.current = null
        both.reminders.tick(atFajrClock, atFajr, fajr.plusSeconds(60))
        both.reminders.tick(atFajrClock, atFajr, fajr.plusSeconds(61))
        assertEquals(listOf("fajr_makkah"), both.played)
        // Stopped after them: it no longer comes.
        val late = Rig()
        late.reminders.tick(atFajrClock, atFajr, fajr.plusSeconds(5))
        late.current = null
        late.reminders.tick(atFajrClock, atFajr, fajr.plusSeconds(130))
        assertTrue(late.played.isEmpty(), "${late.played}")
    }

    @Test fun anAlarmBeforeFajrEndsAtTheFajrAdhan() {
        Strings.language = "en"
        // Ten minutes before, as long as the alarm rings: still ringing (no Stop) when Fajr comes.
        val s = DesktopSettings(cityId = riyadh.id, fajrAlarmMinutesBefore = 10)
        val clock = Clock(riyadh, s)
        val fajr = clock.day(monday)[PrayerEvent.FAJR]!!
        val rig = Rig()
        rig.reminders.tick(clock, s, fajr.minusSeconds(600))
        assertEquals(1, rig.alarms)
        rig.reminders.tick(clock, s, fajr.plusSeconds(1))
        assertEquals(1, rig.ended, "the alarm ends at Fajr")
        assertEquals(listOf("fajr_makkah"), rig.played, "and the Fajr Adhan plays on time")
        assertEquals(Sound.Priority.ADHAN, rig.current)
        // With the Adhan off for Fajr the alarm is still ended at Fajr, and nothing else plays.
        val quiet = s.copy(silent = setOf(PrayerEvent.FAJR))
        val q = Rig()
        q.reminders.tick(Clock(riyadh, quiet), quiet, fajr.minusSeconds(600))
        q.reminders.tick(Clock(riyadh, quiet), quiet, fajr.plusSeconds(1))
        assertTrue(q.played.isEmpty())
        assertEquals(Sound.Priority.ALARM, q.current, "no Adhan to give way to: the alarm rings to its end")
    }

    @Test fun anAlarmThatCannotRingIsShownForItsFullTime() {
        // A recording that is not there behaves as a PC without a sound device: the loop ends at once.
        Sound.loop("no_such_recording")
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (Sound.alarm && System.nanoTime() < deadline) Thread.sleep(10)
        assertFalse(Sound.alarm)
        assertTrue(Sound.alarmUnheard, "the window keeps the alarm with Stop")
        assertEquals(null, Sound.playing, "nothing is held back behind it")
        Sound.stopAlarm()
        assertTrue(Sound.alarmUnheard, "Stop on nothing ringing changes nothing")
    }

    @Test fun priorities() {
        assertTrue(Sound.allows(Sound.Priority.RECITATION, null))
        assertTrue(Sound.allows(Sound.Priority.ADHAN, Sound.Priority.RECITATION))
        assertFalse(Sound.allows(Sound.Priority.RECITATION, Sound.Priority.ADHAN), "a recitation never cuts the Adhan")
        assertFalse(Sound.allows(Sound.Priority.ADHAN, Sound.Priority.ALARM), "nothing cuts the alarm")
        assertEquals(Sound.Priority.VOICE, Sound.priorityOf(AlertKind.NEAR))
        assertEquals(Sound.Priority.RECITATION, Sound.priorityOf(AlertKind.QURAN))
    }

    @Test fun alarmLoopEndsWithoutADeviceAndAfterTheMaximum() {
        var t = 0L
        val now = { t }
        val sleep = { ms: Long -> t += ms }
        val max = Sound.ALARM_MAX.toMillis()
        assertEquals(1, Sound.ring({ true }, max, now = now, sleep = sleep) { false }, "no sound device: one try")
        assertEquals(1, Sound.ring({ true }, max, now = now, sleep = sleep) { true }, "a round that ends at once: no spinning")
        t = 0
        val rounds = Sound.ring({ true }, max, now = now, sleep = sleep) { t += 12_000; true }
        assertTrue(t in max until max + 13_500, "stops on its own after the maximum: $t ms")
        assertEquals(45, rounds)
        var on = true
        var n = 0
        assertEquals(3, Sound.ring({ on }, max, now = now, sleep = sleep) { t += 12_000; n++; if (n == 3) on = false; true }, "Stop ends it")
    }

    @Test fun everyRecordingExists() {
        val names = mutableSetOf<String>()
        for (kind in AlertKind.entries) for (e in PrayerEvent.prayers) for (r in Reciter.entries) for (v in listOf(null) + VoiceLanguage.entries)
            for (language in listOf("en", "ar", "fr")) for (src in AdhanSource.entries) for (short in listOf(false, true)) {
                val s = DesktopSettings(language = language, voice = v, reciter = r, adhanSource = src, fajrAdhanSource = src, shortAdhan = short)
                Sound.file(kind, e, s)?.let { names += it }
            }
        assertEquals(40, names.size, "every recording the PC has: $names")
        names.forEach { assertNotNull(Sound::class.java.classLoader.getResource("sounds/$it.mp3"), it) }
        listOf(AlertKind.FRIDAY, AlertKind.SUHOOR, AlertKind.IFTAR, AlertKind.KAHF, AlertKind.IQAMA, AlertKind.AT_PRAYER).forEach {
            assertNull(Sound.file(it, PrayerEvent.DHUHR, DesktopSettings()), "$it is a notification only")
        }
    }

    @Test fun missedAlertsDoNotAllComeOnWake() {
        Strings.language = "en"
        val s = DesktopSettings(cityId = riyadh.id, before10 = true, before5 = true, near = true, fajrAlarmMinutesBefore = 30)
        val clock = Clock(riyadh, s)
        val fajr = clock.day(monday)[PrayerEvent.FAJR]!!
        val rig = Rig()
        // Asleep from an hour before Fajr until a minute after it.
        rig.reminders.tick(clock, s, fajr.minusSeconds(3600))
        rig.reminders.tick(clock, s, fajr.plusSeconds(60))
        val alarmAt = clockText(fajr.minusSeconds(1800), clock.zone)
        assertEquals(
            listOf("Fajr" to clockText(fajr, clock.zone), t("missed_title") to t("alerts_fajr_alarm") + " " + alarmAt), rig.sent,
            "the Adhan; the countdowns dropped; the alarm, half an hour old, named in one notice",
        )
        assertEquals(0, rig.alarms, "no late alarm")
        assertEquals(listOf("fajr_makkah"), rig.played)
        rig.reminders.tick(clock, s, fajr.plusSeconds(61))
        assertEquals(2, rig.sent.size, "the notice comes once")
    }

    @Test fun afterASleepTheLastTenMinutesStillCome() {
        Strings.language = "en"
        val s = DesktopSettings(cityId = riyadh.id, before10 = true, before5 = true)
        val clock = Clock(riyadh, s)
        val dhuhr = clock.day(monday)[PrayerEvent.DHUHR]!!
        val dhuhrAt = clockText(dhuhr, clock.zone)
        // Asleep from half an hour before Dhuhr to six minutes after it: the Adhan still comes, with its sound.
        val rig = Rig()
        rig.reminders.tick(clock, s, dhuhr.minusSeconds(1800))
        rig.reminders.tick(clock, s, dhuhr.plusSeconds(360))
        assertEquals(listOf("Dhuhr" to dhuhrAt), rig.sent, "no late \"5 minutes until Dhuhr\": ${rig.sent}")
        assertEquals(listOf("adhan_makkah"), rig.played)
        rig.reminders.tick(clock, s, dhuhr.plusSeconds(361))
        assertEquals(1, rig.sent.size, "once")
        // Waking 25 minutes after it: the iqama (5 minutes old) comes, the Adhan is named in the notice, nothing plays.
        val later = Rig()
        later.reminders.tick(clock, s, dhuhr.minusSeconds(1800))
        later.reminders.tick(clock, s, dhuhr.plusSeconds(25 * 60))
        assertEquals(listOf("Iqama for Dhuhr" to "Time to stand for the prayer.", t("missed_title") to "Dhuhr $dhuhrAt"), later.sent)
        assertTrue(later.played.isEmpty(), "${later.played}")
        // A night asleep: the prayers that passed in one notice.
        val night = Rig()
        val isha = clock.day(monday)[PrayerEvent.ISHA]!!
        val fajr = clock.day(monday.plusDays(1))[PrayerEvent.FAJR]!!
        night.reminders.tick(clock, s, isha.minusSeconds(3600))
        night.reminders.tick(clock, s, fajr.plusSeconds(3600))
        assertEquals(listOf(t("missed_title") to "Isha ${clockText(isha, clock.zone)} · Fajr ${clockText(fajr, clock.zone)}"), night.sent)
        // The Fajr alarm five minutes late still rings.
        val alarm = s.copy(fajrAlarmMinutesBefore = 30)
        val ring = Rig()
        ring.reminders.tick(Clock(riyadh, alarm), alarm, fajr.minusSeconds(3 * 3600))
        ring.reminders.tick(Clock(riyadh, alarm), alarm, fajr.minusSeconds(1800 - 300))
        assertEquals(1, ring.alarms)
        assertEquals(listOf("adhan_makkah_short"), ring.looped)
        // A start-up is not a wake: nothing older than two minutes.
        val start = Rig()
        start.reminders.tick(clock, s, dhuhr.plusSeconds(360))
        assertTrue(start.sent.isEmpty())
    }

    @Test fun testNotificationTakesTheRealPath() {
        Strings.language = "en"
        val s = DesktopSettings(cityId = riyadh.id)
        val clock = Clock(riyadh, s)
        val dhuhr = clock.day(monday)[PrayerEvent.DHUHR]!!
        val rig = Rig()
        rig.reminders.test(clock, s, dhuhr.minusSeconds(3600))
        assertEquals(listOf("Dhuhr" to t("test_notification_body")), rig.sent)
        assertEquals(listOf("adhan_makkah_short"), rig.played, "the short Adhan, whatever the length chosen")
        assertTrue(rig.reminders.firedKeys.isEmpty(), "the real Dhuhr still comes")
        rig.reminders.tick(clock, s, dhuhr.plusSeconds(5))
        assertEquals("Dhuhr" to clockText(dhuhr, clock.zone), rig.sent.last())
    }

    @Test fun alertsBeforeAnEarlyFajrOnThePreviousEvening() {
        // Tromsø in spring with "middle of the night" and Fajr 30 minutes earlier: Fajr soon after midnight, so an
        // alarm 45 minutes before it falls on the evening before.
        val tromso = Cities.byId(3133895)!!
        val s = DesktopSettings(cityId = tromso.id, highLatitudeRule = HighLatRule.MIDDLE_OF_THE_NIGHT, offsets = mapOf(PrayerEvent.FAJR to -30), fajrAlarmMinutesBefore = 45)
        val clock = Clock(tromso, s)
        val day = (0L..60L).map { LocalDate.of(2026, 4, 1).plusDays(it) }.firstOrNull { d ->
            clock.day(d)[PrayerEvent.FAJR]?.minusSeconds(45 * 60)?.atZone(clock.zone)?.toLocalDate()?.isBefore(d) == true
        }
        assertNotNull(day, "a date with the alarm on the evening before")
        val ring = clock.day(day)[PrayerEvent.FAJR]!!.minusSeconds(45 * 60)
        val rig = Rig()
        rig.reminders.tick(clock, s, ring.plusSeconds(5))
        assertEquals(1, rig.alarms)
    }

    @Test fun firedAlertsArePrunedNotCleared() {
        Strings.language = "en"
        val s = DesktopSettings(cityId = riyadh.id, adhan = false)
        val clock = Clock(riyadh, s)
        val dhuhr = clock.day(monday)[PrayerEvent.DHUHR]!!
        val rig = Rig()
        rig.reminders.tick(clock, s, dhuhr.plusSeconds(10))
        rig.reminders.tick(clock, s, dhuhr.plusSeconds(100))
        assertEquals(1, rig.sent.size, "once")
        assertTrue(rig.reminders.firedKeys.any { it.endsWith("AT_PRAYER-DHUHR") })
        rig.reminders.tick(clock, s, dhuhr.plusSeconds(20 * 60 + 30))
        assertEquals(2, rig.sent.size, "the iqama")
        rig.reminders.tick(clock, s, dhuhr.plusSeconds(20 * 60 + 90))
        assertEquals(2, rig.sent.size, "still once")
        // The PC's clock set back 20 minutes: nothing comes a second time.
        rig.reminders.tick(clock, s, dhuhr.plusSeconds(15))
        rig.reminders.tick(clock, s, dhuhr.plusSeconds(20 * 60 + 15))
        assertEquals(2, rig.sent.size, "not again after the clock went back: ${rig.sent}")
        // Kept while the date is among the planner's days, dropped after.
        rig.reminders.tick(clock, s, dhuhr.plusSeconds(86_400))
        assertTrue(rig.reminders.firedKeys.any { it.startsWith("$monday-") })
        rig.reminders.tick(clock, s, dhuhr.plusSeconds(2 * 86_400))
        assertTrue(rig.reminders.firedKeys.none { it.startsWith("$monday-") }, "${rig.reminders.firedKeys}")
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test fun theLoopUsesTheLatestCityAndSettings() {
        // As in the app: the loop starts before a city is chosen, then the city and a minute adjustment change.
        val seen = LinkedBlockingQueue<Pair<Clock?, DesktopSettings>>()
        var clock by mutableStateOf<Clock?>(null)
        var settings by mutableStateOf(DesktopSettings())
        val scene = ImageComposeScene(10, 10) { TickLoop(clock, settings, periodMillis = 5) { c, s -> seen += c to s } }
        try {
            scene.render()
            assertNull(seen.poll(5, TimeUnit.SECONDS)?.first)
            fun change(s: DesktopSettings): Clock {
                val c = Clock(riyadh, s)
                clock = c
                settings = s
                Snapshot.sendApplyNotifications()
                scene.render()
                return c
            }
            fun awaitPass(c: Clock): DesktopSettings {
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (System.nanoTime() < deadline) {
                    val (got, s) = seen.poll(100, TimeUnit.MILLISECONDS) ?: continue
                    if (got === c) return s
                }
                error("the loop never used the new clock")
            }
            val picked = DesktopSettings(cityId = riyadh.id)
            assertEquals(picked, awaitPass(change(picked)))
            val later = picked.copy(offsets = mapOf(PrayerEvent.DHUHR to 5))
            val c = change(later)
            assertEquals(later, awaitPass(c))
            val plan = Reminders.plan(c, later, monday.atStartOfDay(c.zone).toInstant()).second
            assertEquals(Clock(riyadh, picked).day(monday)[PrayerEvent.DHUHR]!!.plusSeconds(300), plan.first { it.kind == AlertKind.ADHAN && it.event == PrayerEvent.DHUHR }.at)
        } finally {
            scene.close()
        }
    }

    @Test fun storeRoundTrip() {
        val file = settingsFile()
        val s = DesktopSettings(
            language = "ar", silent = setOf(PrayerEvent.FAJR), near = true, nearMinutes = 30, voice = VoiceLanguage.ENGLISH,
            perPrayer = mapOf(PrayerEvent.ASR to PrayerSounds(before10 = false), PrayerEvent.FAJR to PrayerSounds(before5 = false, near = false)),
            friday = true, ramadan = true, kahf = true, fajrAlarmMinutesBefore = 45, bedtimeMinutes = 23 * 60,
            salawat = true, salawatMinutes = 10 * 60 + 15, quran = true, quranMinutes = 21 * 60 + 7, reciter = Reciter.CLASSIC,
            zoneKept = "Asia/Dubai",
        )
        Store.save(s, FilePreferences(file))
        val p = FilePreferences(file)
        assertEquals("ASR=1011,FAJR=0100", p.get("perPrayer", null), "the Adhan bit follows silent")
        assertEquals("ENGLISH", p.get("voice", null))
        assertEquals(45, p.getInt("fajrAlarm", -1))
        assertEquals(1380, p.getInt("bedtime", -1))
        assertTrue(p.getBoolean("fridayReminder", false) && p.getBoolean("ramadanReminders", false) && p.getBoolean("kahfReminder", false))
        assertTrue(p.getBoolean("quranReminder", false))
        assertEquals("CLASSIC", p.get("reciter", null))
        assertEquals(s, Store.load(p))
        // Off again: the voice follows the interface, the alarm and bedtime read as off.
        val off = s.copy(voice = null, fajrAlarmMinutesBefore = null, bedtimeMinutes = null, perPrayer = emptyMap(), zoneKept = null)
        Store.save(off, FilePreferences(file))
        val again = FilePreferences(file)
        assertNull(again.get("voice", null))
        assertEquals(-1, again.getInt("fajrAlarm", 0))
        assertEquals("", again.get("perPrayer", null))
        assertEquals(off, Store.load(again))
        // The Fajr alarm "at Fajr" is 0, not off.
        Store.save(off.copy(fajrAlarmMinutesBefore = 0), FilePreferences(file))
        assertEquals(0, Store.load(FilePreferences(file)).fajrAlarmMinutesBefore)
    }

    @Test fun anOlderSaveHasEveryNewReminderOff() {
        val file = settingsFile()
        file.writeText("cityId=108410\nlanguage=ar\nadhan=true\nbefore10=true\nbefore5=false\nsilent=ASR\n")
        val s = Store.load(FilePreferences(file))
        assertTrue(s.before10)
        assertFalse(s.near)
        assertEquals(15, s.nearMinutes)
        assertNull(s.voice, "the voice follows the interface")
        assertEquals(VoiceLanguage.ARABIC, s.alerts().voice ?: VoiceLanguage.forInterface(s.language))
        assertEquals(emptyMap(), s.perPrayer)
        assertFalse(s.friday || s.ramadan || s.kahf || s.salawat || s.quran)
        assertNull(s.fajrAlarmMinutesBefore)
        assertNull(s.bedtimeMinutes)
        assertEquals(660, s.salawatMinutes)
        assertEquals(1230, s.quranMinutes)
        assertEquals(Reciter.ALAFASY, s.reciter)
        // Saved again, the older keys are where an older build reads them.
        Store.save(s, FilePreferences(file))
        val p = FilePreferences(file)
        assertTrue(p.getBoolean("before10", false))
        assertEquals("ASR", p.get("silent", null))
        assertEquals(s, Store.load(p))
    }

    @Test fun badValuesFallBackToDefaults() {
        val file = settingsFile()
        file.writeText(
            """
            language=en
            near=perhaps
            nearMinutes=17
            voice=KLINGON
            perPrayer=FAJR=10,ASR=1x11,SUNRISE=1111,XYZ=1111,DHUHR=1101,=,MAGHRIB,ISHA=11111
            fridayReminder=1
            ramadanReminders=
            kahfReminder=yes
            fajrAlarm=999
            bedtime=2000
            salawat=true
            salawatMinutes=-5
            quranReminder=TRUE
            quranMinutes=abc
            reciter=NOBODY
            """.trimIndent(),
        )
        val s = Store.load(FilePreferences(file))
        assertFalse(s.near)
        assertEquals(15, s.nearMinutes)
        assertNull(s.voice)
        assertEquals(mapOf(PrayerEvent.DHUHR to PrayerSounds(before10 = true, before5 = false, near = true)), s.perPrayer)
        assertFalse(s.friday || s.ramadan || s.kahf)
        assertNull(s.fajrAlarmMinutesBefore)
        assertNull(s.bedtimeMinutes)
        assertTrue(s.salawat)
        assertEquals(660, s.salawatMinutes)
        assertTrue(s.quran)
        assertEquals(1230, s.quranMinutes)
        assertEquals(Reciter.ALAFASY, s.reciter)
        file.writeText("fajrAlarm=-1\nbedtime=-1\n")
        val none = Store.load(FilePreferences(file))
        assertNull(none.fajrAlarmMinutesBefore)
        assertNull(none.bedtimeMinutes)
    }

    @Test fun urgentNotifySend() {
        assertEquals(
            listOf("notify-send", "--app-name=Near Mosque", "--urgency=critical", "--", "Time for Fajr", "Riyadh, 4:32"),
            Platform.notifySendCommand("notify-send", "Time for Fajr", "Riyadh, 4:32", null, urgent = true),
        )
    }

    @Test fun timeOfDayChoices() {
        assertEquals(96, timeChoices(20 * 60 + 30).size)
        val odd = timeChoices(21 * 60 + 7)
        assertEquals(97, odd.size, "a saved time between the steps stays in the list")
        assertTrue(21 * 60 + 7 in odd)
        assertEquals(odd.sorted(), odd)
        Strings.language = "en"
        assertTrue(timeOfDay(20 * 60 + 30).startsWith("8:30"), timeOfDay(20 * 60 + 30))
        Strings.language = "ar"
        assertTrue(timeOfDay(20 * 60 + 30).endsWith("م"), timeOfDay(20 * 60 + 30))
        Strings.language = "en"
    }
}
