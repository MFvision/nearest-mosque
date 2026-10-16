package sa.zood.nearmosque.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Mirrors NMCore IqamaTests.swift. */
class IqamaTest {
    private val calc = PrayerCalculator()
    private val makkah = LatLng(21.4225, 39.8262)
    private val zone = ZoneId.of("Asia/Riyadh")
    private val start = LocalDate.of(2026, 10, 15)
    private val days = (-1L..1L).map { calc.schedule(makkah, start.plusDays(it), zone, PrayerSettings(method = Method.UMM_AL_QURA)) }
    private val today = days[1]

    @Test
    fun defaults() {
        val s = IqamaSettings()
        assertEquals(listOf(25, 20, 20, 10, 20), PrayerEvent.prayers.map { s.wait(it) })
        assertEquals(0, s.wait(PrayerEvent.SUNRISE))
        assertEquals(15, s.prayerMinutes)
    }

    @Test
    fun stagesAfterDhuhr() {
        val s = IqamaSettings()
        val dhuhr = today[PrayerEvent.DHUHR]!!
        val before = calc.moment(days, dhuhr.minusSeconds(60), s)!!
        assertEquals(PrayerMoment.Stage.UPCOMING, before.stage)
        assertEquals(PrayerEvent.DHUHR, before.event)
        assertEquals(dhuhr.plusSeconds(20 * 60), before.iqama)

        val wait = calc.moment(days, dhuhr.plusSeconds(1), s)!!
        assertEquals(PrayerMoment.Stage.TO_IQAMA, wait.stage)
        assertEquals(PrayerEvent.DHUHR, wait.event)
        assertEquals(dhuhr.plusSeconds(20 * 60), wait.until)
        assertEquals(PrayerEvent.ASR, wait.next.event)

        val prayer = calc.moment(days, dhuhr.plusSeconds(21 * 60), s)!!
        assertEquals(PrayerMoment.Stage.PRAYER, prayer.stage)
        assertEquals(dhuhr.plusSeconds(35 * 60), prayer.until)

        val after = calc.moment(days, dhuhr.plusSeconds(36 * 60), s)!!
        assertEquals(PrayerMoment.Stage.UPCOMING, after.stage)
        assertEquals(PrayerEvent.ASR, after.event)
        assertEquals(dhuhr.plusSeconds(35 * 60), after.since)
    }

    @Test
    fun maghribWaitIsTen() {
        val m = today[PrayerEvent.MAGHRIB]!!
        val w = calc.moment(days, m.plusSeconds(5 * 60), IqamaSettings())!!
        assertEquals(PrayerMoment.Stage.TO_IQAMA, w.stage)
        assertEquals(m.plusSeconds(10 * 60), w.until)
    }

    @Test
    fun offGoesStraightToTheNextAdhan() {
        val dhuhr = today[PrayerEvent.DHUHR]!!
        val m = calc.moment(days, dhuhr.plusSeconds(1), IqamaSettings.OFF)!!
        assertEquals(PrayerMoment.Stage.UPCOMING, m.stage)
        assertEquals(PrayerEvent.ASR, m.event)
        assertNull(m.iqama)
        assertEquals(dhuhr, m.since)
    }

    @Test
    fun neverPastTheNextAdhan() {
        val s = IqamaSettings(prayerMinutes = 30).withWait(PrayerEvent.MAGHRIB, 45)
        val isha = today[PrayerEvent.ISHA]!!
        assertEquals(PrayerMoment.Stage.UPCOMING, calc.moment(days, isha.minusSeconds(60), s)!!.stage)
        val w = calc.moment(days, today[PrayerEvent.MAGHRIB]!!.plusSeconds(60), s)!!
        assertTrue(!w.until.isAfter(isha))
    }

    @Test
    fun stageChangesForATimeline() {
        val dhuhr = today[PrayerEvent.DHUHR]!!
        val c = stageChanges(days, dhuhr.minusSeconds(1), IqamaSettings())
        assertEquals(listOf(dhuhr, dhuhr.plusSeconds(20 * 60), dhuhr.plusSeconds(35 * 60), today[PrayerEvent.ASR]!!), c.take(4))
    }

    @Test
    fun iqamaAlertFollowsTheAdhanOrBell() {
        val a = AlertSettings(adhan = true)
        val now = today[PrayerEvent.FAJR]!!.minusSeconds(3600)
        val p = AlertPlanner.plan(listOf(today), now, emptySet(), a, IqamaSettings()) { false }
        val iq = p.filter { it.kind == AlertKind.IQAMA }
        assertEquals(listOf(PrayerEvent.DHUHR, PrayerEvent.ASR, PrayerEvent.MAGHRIB, PrayerEvent.ISHA), iq.map { it.event })
        assertEquals(today[PrayerEvent.DHUHR]!!.plusSeconds(20 * 60), iq[0].at)
        assertTrue(AlertPlanner.plan(listOf(today), now, emptySet(), a) { false }.none { it.kind == AlertKind.IQAMA })
    }
}
