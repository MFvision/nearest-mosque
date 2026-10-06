package sa.zood.nearmosque.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

class DayArcTest {
    private val calc = PrayerCalculator()
    private val makkah = LatLng(21.4225, 39.8262)
    private val zone = ZoneId.of("Asia/Riyadh")
    private val date = LocalDate.of(2026, 3, 21)
    private val days = (-1L..1L).map { calc.schedule(makkah, date.plusDays(it), zone, PrayerSettings(method = Method.UMM_AL_QURA)) }

    @Test
    fun daylightHasDhuhrAndAsrBetweenSunriseAndMaghrib() {
        val sunrise = days[1][PrayerEvent.SUNRISE]!!
        val maghrib = days[1][PrayerEvent.MAGHRIB]!!
        val noonish = sunrise.plus(Duration.between(sunrise, maghrib).dividedBy(2))
        val arc = DayArc.at(noonish, days)!!
        assertTrue(arc.isDay)
        assertEquals(PrayerEvent.SUNRISE, arc.startEvent)
        assertEquals(0.5, arc.fraction, 1e-6)
        assertEquals(listOf(PrayerEvent.DHUHR, PrayerEvent.ASR), arc.marks.map { it.event })
        assertTrue(arc.marks.all { it.fraction > 0 && it.fraction < 1 })
        // Dhuhr is just after solar noon, near the middle of daylight.
        assertEquals(0.5, arc.marks[0].fraction, 0.03)
    }

    @Test
    fun eveningRunsFromMaghribToTomorrowsSunrise() {
        val after = days[1][PrayerEvent.MAGHRIB]!!.plusSeconds(60)
        val arc = DayArc.at(after, days)!!
        assertFalse(arc.isDay)
        assertEquals(days[2][PrayerEvent.SUNRISE], arc.end)
        assertEquals(listOf(PrayerEvent.ISHA, PrayerEvent.FAJR), arc.marks.map { it.event })
        assertEquals(days[2][PrayerEvent.FAJR], arc.marks[1].at)
    }

    @Test
    fun beforeSunriseRunsFromYesterdaysMaghrib() {
        val early = days[1][PrayerEvent.SUNRISE]!!.minusSeconds(60)
        val arc = DayArc.at(early, days)!!
        assertFalse(arc.isDay)
        assertEquals(days[0][PrayerEvent.MAGHRIB], arc.start)
        assertEquals(days[1][PrayerEvent.FAJR], arc.marks.last().at)
        assertTrue(arc.fraction > 0.95)
    }

    @Test
    fun polarDayHasNoArc() {
        val tromso = LatLng(69.6496, 18.956)
        val d = LocalDate.of(2026, 6, 21)
        val oslo = ZoneId.of("Europe/Oslo")
        val polar = (-1L..1L).map { calc.schedule(tromso, d.plusDays(it), oslo, PrayerSettings()) }
        assertNull(DayArc.at(d.atStartOfDay(oslo).plusHours(12).toInstant(), polar))
    }
}
