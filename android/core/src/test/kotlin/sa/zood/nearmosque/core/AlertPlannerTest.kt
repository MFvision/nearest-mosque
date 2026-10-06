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
}
