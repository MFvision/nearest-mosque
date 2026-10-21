package sa.zood.nearmosque.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Mirrors NMCore SilenceTests.swift. */
class SilenceTest {
    private val calc = PrayerCalculator()
    private val makkah = LatLng(21.4225, 39.8262)
    private val zone = ZoneId.of("Asia/Riyadh")
    // Thursday 15, Friday 16 October 2026.
    private val thursday = LocalDate.of(2026, 10, 15)
    private fun days(d: LocalDate) = (-1L..1L).map { calc.schedule(makkah, d.plusDays(it), zone, PrayerSettings(method = Method.UMM_AL_QURA)) }

    @Test
    fun offByDefault() {
        assertFalse(SilenceSettings().enabled)
        assertTrue(SilenceSettings().windows(days(thursday), IqamaSettings(), Instant.EPOCH).isEmpty())
    }

    @Test
    fun fromIqama() {
        val ds = days(thursday)
        val dhuhr = ds[1][PrayerEvent.DHUHR]!!
        val s = SilenceSettings(enabled = true)
        val w = s.windows(ds, IqamaSettings(), dhuhr).first()
        assertEquals(PrayerEvent.DHUHR, w.event)
        assertEquals(dhuhr.plusSeconds(20 * 60), w.start)
        assertEquals(dhuhr.plusSeconds(35 * 60), w.end)
        assertFalse(w.friday)
        assertNull(s.current(ds, IqamaSettings(), dhuhr.plusSeconds(60)))
        assertEquals(w, s.current(ds, IqamaSettings(), dhuhr.plusSeconds(25 * 60)))
        assertNull(s.current(ds, IqamaSettings(), dhuhr.plusSeconds(36 * 60)))
    }

    @Test
    fun fromAdhan() {
        val ds = days(thursday)
        val asr = ds[1][PrayerEvent.ASR]!!
        val w = SilenceSettings(enabled = true, start = SilenceSettings.Start.ADHAN).windows(ds, IqamaSettings(), asr).first()
        assertEquals(PrayerEvent.ASR, w.event)
        assertEquals(asr, w.start)
        assertEquals(asr.plusSeconds(35 * 60), w.end)
    }

    @Test
    fun friday() {
        val ds = days(thursday.plusDays(1))
        val dhuhr = ds[1][PrayerEvent.DHUHR]!!
        val w = SilenceSettings(enabled = true, fridayMinutes = 75).windows(ds, IqamaSettings(), dhuhr).first()
        assertTrue(w.friday)
        assertEquals(dhuhr, w.start)
        assertEquals(dhuhr.plusSeconds(75 * 60), w.end)
    }

    // The app's own Adhan plays first: the silence starts when it has ended, on Friday and with "From the Adhan" too.
    @Test
    fun afterTheAppsOwnAdhan() {
        val fri = days(thursday.plusDays(1))
        val dhuhr = fri[1][PrayerEvent.DHUHR]!!
        val adhan = { e: PrayerEvent -> if (e == PrayerEvent.DHUHR) 210L else 0L }
        val w = SilenceSettings(enabled = true, fridayMinutes = 60).windows(fri, IqamaSettings(), dhuhr, adhan).first()
        assertTrue(w.friday)
        assertEquals(dhuhr.plusSeconds(210), w.start)
        assertEquals(dhuhr.plusSeconds(60 * 60), w.end)
        val ds = days(thursday)
        val asr = ds[1][PrayerEvent.ASR]!!
        val fromAdhan = SilenceSettings(enabled = true, start = SilenceSettings.Start.ADHAN)
        assertEquals(asr.plusSeconds(250), fromAdhan.windows(ds, IqamaSettings(), asr, { 250L }).first().start)
        // From the iqama, 20 minutes after the Adhan: already after it.
        assertEquals(asr.plusSeconds(20 * 60), SilenceSettings(enabled = true).windows(ds, IqamaSettings(), asr, { 250L }).first().start)
        // A few seconds for an alert at the same minute.
        assertEquals(asr.plusSeconds(20 * 60 + 10), SilenceSettings(enabled = true).windows(ds, IqamaSettings(), asr, alertSeconds = 10).first().start)
        assertNull(fromAdhan.current(ds, IqamaSettings(), asr.plusSeconds(100), { 250L }))
        assertEquals(PrayerEvent.ASR, fromAdhan.current(ds, IqamaSettings(), asr.plusSeconds(260), { 250L })?.event)
    }

    @Test
    fun onlyChosenPrayers() {
        val s = SilenceSettings(enabled = true).with(PrayerEvent.FAJR, false).with(PrayerEvent.ISHA, false)
        val ds = days(thursday)
        val events = s.windows(ds, IqamaSettings(), ds[1][PrayerEvent.FAJR]!!.minusSeconds(3600)).map { it.event }
        assertFalse(PrayerEvent.FAJR in events)
        assertFalse(PrayerEvent.ISHA in events)
        assertEquals(listOf(PrayerEvent.DHUHR, PrayerEvent.ASR, PrayerEvent.MAGHRIB), events.take(3))
    }

    @Test
    fun endsByNextAdhan() {
        val ds = days(thursday)
        val maghrib = ds[1][PrayerEvent.MAGHRIB]!!
        val isha = ds[1][PrayerEvent.ISHA]!!
        val iq = IqamaSettings(prayerMinutes = 60).withWait(PrayerEvent.MAGHRIB, 45)
        val w = SilenceSettings(enabled = true).windows(ds, iq, maghrib).first()
        assertEquals(PrayerEvent.MAGHRIB, w.event)
        assertFalse(w.end.isAfter(isha))
    }
}
