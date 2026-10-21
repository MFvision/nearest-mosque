package sa.zood.nearmosque.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class AlertPlannerTest {
    private val calc = PrayerCalculator()
    private val makkah = LatLng(21.4225, 39.8262)
    private val zone = ZoneId.of("Asia/Riyadh")
    // Thursday 2026-10-01 to Saturday 2026-10-03.
    private val start = LocalDate.of(2026, 10, 1)
    private val days = (0L..2L).map { calc.schedule(makkah, start.plusDays(it), zone, PrayerSettings(method = Method.UMM_AL_QURA)) }
    private val now = start.atStartOfDay(zone).toInstant()

    @Test
    fun atPrayerAndEarlyReminders() {
        val p = AlertPlanner.plan(days, now, setOf(PrayerEvent.ASR), AlertSettings(minutesBefore = 15)) { false }
        assertEquals(6, p.size)
        val first = p.take(2)
        assertEquals(listOf(AlertKind.BEFORE, AlertKind.AT_PRAYER), first.map { it.kind })
        assertEquals(15 * 60L, java.time.Duration.between(first[0].at, first[1].at).seconds)
        assertTrue(p.zipWithNext().all { (a, b) -> !a.at.isAfter(b.at) })
    }

    @Test
    fun fridayOnlyOnFriday() {
        val p = AlertPlanner.plan(days, now, emptySet(), AlertSettings(friday = true)) { false }
        assertEquals(1, p.size)
        assertEquals(DayOfWeek.FRIDAY, p[0].date.dayOfWeek)
        assertEquals(days[1][PrayerEvent.DHUHR]!!.minusSeconds(45 * 60), p[0].at)
    }

    @Test
    fun ramadanSuhoorAndIftarOnlyInRamadan() {
        val p = AlertPlanner.plan(days, now, emptySet(), AlertSettings(ramadan = true)) { it == start.plusDays(2) }
        assertEquals(listOf(AlertKind.SUHOOR, AlertKind.IFTAR), p.map { it.kind })
        assertTrue(p.all { it.date == start.plusDays(2) })
    }

    @Test
    fun fajrAlarmEachDayAndNothingInThePast() {
        val later = days[1][PrayerEvent.FAJR]!!.plusSeconds(60)
        val p = AlertPlanner.plan(days, later, emptySet(), AlertSettings(fajrAlarmMinutesBefore = 20)) { false }
        assertEquals(1, p.size)
        assertEquals(days[2][PrayerEvent.FAJR]!!.minusSeconds(20 * 60), p[0].at)
    }

    // Every reminder and sound is independent: one on, the others stay off.
    @Test
    fun adhanAndRemindersAreIndependent() {
        val a = AlertSettings(adhan = true, before10 = true)
            .withSounds(PrayerEvent.MAGHRIB, PrayerSounds(adhan = false))
        val p = AlertPlanner.plan(listOf(days[0]), now, emptySet(), a) { false }
        assertEquals(listOf(PrayerEvent.DHUHR, PrayerEvent.ASR, PrayerEvent.ISHA), p.filter { it.kind == AlertKind.ADHAN }.map { it.event })
        assertEquals(5, p.count { it.kind == AlertKind.BEFORE_10 })
        assertTrue(p.none { it.kind == AlertKind.BEFORE_5 || it.kind == AlertKind.NEAR || it.kind == AlertKind.AT_PRAYER })
    }

    @Test
    fun bellWithoutAdhanAndAdhanWithoutBell() {
        val p = AlertPlanner.plan(listOf(days[0]), now, setOf(PrayerEvent.MAGHRIB), AlertSettings(fajrAdhan = true)) { false }
        assertEquals(listOf(AlertKind.ADHAN, AlertKind.AT_PRAYER), p.map { it.kind })
        assertEquals(listOf(PrayerEvent.FAJR, PrayerEvent.MAGHRIB), p.map { it.event })
    }

    @Test
    fun fajrAdhanFromEitherMosque() {
        assertTrue(AlertSettings(fajrAdhan = true, fajrAdhanSource = AdhanSource.MADINAH).adhanPlays(PrayerEvent.FAJR))
        assertTrue(!AlertSettings(fajrAdhan = false, fajrAdhanSource = AdhanSource.MADINAH).adhanPlays(PrayerEvent.FAJR))
    }

    @Test
    fun remindersBeforeThePrayer() {
        val d = days[0]
        val p = AlertPlanner.plan(listOf(d), now, emptySet(), AlertSettings(before5 = true, near = true, nearMinutes = 20)) { false }
            .filter { it.event == PrayerEvent.ASR }
        assertEquals(listOf(AlertKind.NEAR, AlertKind.BEFORE_5), p.map { it.kind })
        assertEquals(d[PrayerEvent.ASR]!!.minusSeconds(1200), p[0].at)
        assertEquals(d[PrayerEvent.ASR]!!.minusSeconds(300), p[1].at)
    }

    @Test
    fun salawatAndKahfOnFridayOnlyQuranAndSleepDaily() {
        val a = AlertSettings(salawat = true, salawatMinutes = 11 * 60, kahf = true, quran = true, bedtimeMinutes = 22 * 60)
        val p = AlertPlanner.plan(days, now, emptySet(), a) { false }
        assertEquals(listOf(start.plusDays(1)), p.filter { it.kind == AlertKind.SALAWAT }.map { it.date })
        assertTrue(p.filter { it.kind == AlertKind.SALAWAT || it.kind == AlertKind.KAHF }.all { it.date.dayOfWeek == DayOfWeek.FRIDAY })
        assertEquals(3, p.count { it.kind == AlertKind.QURAN })
        assertEquals(3, p.count { it.kind == AlertKind.SLEEP })
        assertEquals(11, p.first { it.kind == AlertKind.SALAWAT }.at.atZone(zone).hour)
    }

    // Reminders at a time of day stay at that time on the days the clocks change (summer time starts or ends).
    @Test
    fun timeOfDayRemindersKeepTheirTimeWhenTheClocksChange() {
        val london = ZoneId.of("Europe/London")
        val a = AlertSettings(quran = true, quranMinutes = 20 * 60 + 30, bedtimeMinutes = 22 * 60 + 30)
        listOf(LocalDate.of(2026, 10, 25), LocalDate.of(2027, 3, 28)).forEach { date ->
            val d = calc.schedule(LatLng(51.5074, -0.1278), date, london, PrayerSettings(method = Method.MUSLIM_WORLD_LEAGUE))
            val p = AlertPlanner.plan(listOf(d), date.atStartOfDay(london).toInstant(), emptySet(), a) { false }
            assertEquals(java.time.LocalTime.of(20, 30), p.single { it.kind == AlertKind.QURAN }.at.atZone(london).toLocalTime())
            assertEquals(java.time.LocalTime.of(22, 30), p.single { it.kind == AlertKind.SLEEP }.at.atZone(london).toLocalTime())
        }
        // Egypt moves its clocks on a Friday, at midnight.
        val cairo = ZoneId.of("Africa/Cairo")
        val friday = LocalDate.of(2027, 4, 30)
        val d = calc.schedule(LatLng(30.0444, 31.2357), friday, cairo, PrayerSettings(method = Method.EGYPTIAN))
        val p = AlertPlanner.plan(listOf(d), friday.atStartOfDay(cairo).toInstant(), emptySet(), AlertSettings(kahf = true, salawat = true, salawatMinutes = 11 * 60)) { false }
        assertEquals(java.time.LocalTime.of(9, 0), p.single { it.kind == AlertKind.KAHF }.at.atZone(cairo).toLocalTime())
        assertEquals(java.time.LocalTime.of(11, 0), p.single { it.kind == AlertKind.SALAWAT }.at.atZone(cairo).toLocalTime())
    }

    @Test
    fun oldEarlyReminderMovesToTheNewOnes() {
        assertEquals(AlertSettings(before10 = true), AlertSettings(minutesBefore = 10).migrated())
        assertEquals(AlertSettings(near = true, nearMinutes = 20), AlertSettings(minutesBefore = 20).migrated())
    }

    @Test
    fun sunriseBellGivesANotificationAtSunrise() {
        val d = days[0]
        val p = AlertPlanner.plan(listOf(d), now, setOf(PrayerEvent.SUNRISE), AlertSettings()) { false }
        assertEquals(listOf(PrayerEvent.SUNRISE), p.map { it.event })
        assertEquals(d[PrayerEvent.SUNRISE], p[0].at)
    }

    @Test
    fun oneSwitchForOnePrayer() {
        // Off for all prayers; turning on Maghrib's 10-minute reminder turns it on for Maghrib only.
        val a = AlertSettings().withPrayer(PrayerEvent.MAGHRIB, AlertSettings.PrayerSound.BEFORE_10, true)
        assertTrue(a.before10)
        assertEquals(listOf(PrayerEvent.MAGHRIB), PrayerEvent.prayers.filter { a.isOn(it, AlertSettings.PrayerSound.BEFORE_10) })
        // The Fajr Adhan is separate from the Dhuhr to Isha Adhan.
        val b = AlertSettings().withPrayer(PrayerEvent.ASR, AlertSettings.PrayerSound.ADHAN, true)
        assertEquals(listOf(PrayerEvent.ASR), PrayerEvent.prayers.filter { b.isOn(it, AlertSettings.PrayerSound.ADHAN) })
        assertTrue(!b.fajrAdhan)
        // Turning one off leaves the others as they were.
        val c = AlertSettings(before5 = true).withPrayer(PrayerEvent.ISHA, AlertSettings.PrayerSound.BEFORE_5, false)
        assertEquals(4, PrayerEvent.prayers.count { c.isOn(it, AlertSettings.PrayerSound.BEFORE_5) })
    }
}
