package sa.zood.nearmosque.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class OnboardingTest {
    private val withLook = OnboardingFlow(hasLook = true)
    private val noLook = OnboardingFlow(hasLook = false)

    @Test
    fun stepsInOrder() {
        assertEquals(13, withLook.steps.size)
        assertEquals(12, noLook.steps.size)
        assertFalse(OnboardingStep.LOOK in noLook.steps)
        assertEquals(OnboardingStep.FRIDAY, withLook.next(OnboardingStep.ADHAN))
        assertEquals(OnboardingStep.BUILDING, noLook.next(OnboardingStep.WEATHER))
        assertEquals(OnboardingStep.EXTRAS, withLook.next(OnboardingStep.DAY))
        assertNull(withLook.next(OnboardingStep.DONE))
        assertEquals(OnboardingStep.PLACE, OnboardingStep.of("where"))
        assertEquals("friday", OnboardingStep.FRIDAY.key)
    }

    @Test
    fun backTargets() {
        val expected = mapOf(
            OnboardingStep.PLACE to OnboardingStep.PROMISE, OnboardingStep.ASR to OnboardingStep.PLACE,
            OnboardingStep.ADHAN to OnboardingStep.ASR, OnboardingStep.FRIDAY to OnboardingStep.ADHAN,
            OnboardingStep.WEATHER to OnboardingStep.FRIDAY, OnboardingStep.LOOK to OnboardingStep.WEATHER,
            OnboardingStep.DAY to OnboardingStep.LOOK, OnboardingStep.EXTRAS to OnboardingStep.DAY,
            OnboardingStep.MORE to OnboardingStep.EXTRAS, OnboardingStep.PRIVACY to OnboardingStep.MORE,
        )
        expected.forEach { (from, to) -> assertEquals("back from $from", to, withLook.back(from)) }
        assertEquals(OnboardingStep.WEATHER, noLook.back(OnboardingStep.DAY))
        listOf(OnboardingStep.PROMISE, OnboardingStep.BUILDING, OnboardingStep.DONE).forEach { assertNull(withLook.back(it)) }
    }

    @Test
    fun skipTargets() {
        listOf(OnboardingStep.ASR, OnboardingStep.ADHAN, OnboardingStep.FRIDAY, OnboardingStep.WEATHER, OnboardingStep.LOOK)
            .forEach { assertEquals(OnboardingStep.BUILDING, withLook.skip(it)) }
        assertEquals(OnboardingStep.PRIVACY, withLook.skip(OnboardingStep.EXTRAS))
        assertEquals(OnboardingStep.PRIVACY, withLook.skip(OnboardingStep.MORE))
        listOf(OnboardingStep.PROMISE, OnboardingStep.PLACE, OnboardingStep.BUILDING, OnboardingStep.DAY, OnboardingStep.PRIVACY, OnboardingStep.DONE)
            .forEach { assertNull(withLook.skip(it)) }
    }

    @Test
    fun headerAndQuestions() {
        assertTrue(OnboardingFlow.showsHeader(OnboardingStep.FRIDAY))
        assertTrue(OnboardingFlow.showsHeader(OnboardingStep.EXTRAS))
        assertTrue(OnboardingFlow.showsHeader(OnboardingStep.MORE))
        assertTrue(OnboardingFlow.showsHeader(OnboardingStep.PRIVACY))
        assertFalse(OnboardingFlow.showsHeader(OnboardingStep.PROMISE))
        assertFalse(OnboardingFlow.showsHeader(OnboardingStep.DONE))
        assertTrue(OnboardingFlow.isQuestion(OnboardingStep.FRIDAY))
        assertTrue(OnboardingFlow.isQuestion(OnboardingStep.LOOK))
        assertFalse(OnboardingFlow.isQuestion(OnboardingStep.EXTRAS))
        assertFalse(OnboardingFlow.isQuestion(OnboardingStep.BUILDING))
    }

    @Test
    fun moreKeepsOrderAndShowsOnlyTheRest() {
        val all = OnboardingFeature.entries.toSet()
        val more = OnboardingFeature.more(all, shownEarlier = setOf(OnboardingFeature.QUIET))
        assertFalse(OnboardingFeature.QUIET in more)
        // The compass wins over the north bearing, the library over the classical books.
        assertFalse(OnboardingFeature.QIBLA_NORTH in more)
        assertFalse(OnboardingFeature.CLASSICS in more)
        assertEquals(more.sortedBy { it.ordinal }, more)
        val desktop = OnboardingFeature.more(setOf(OnboardingFeature.QIBLA_NORTH, OnboardingFeature.CLASSICS, OnboardingFeature.HIJRI, OnboardingFeature.IQAMA))
        assertEquals(listOf(OnboardingFeature.IQAMA, OnboardingFeature.QIBLA_NORTH, OnboardingFeature.CLASSICS, OnboardingFeature.HIJRI), desktop)
        assertTrue(OnboardingFeature.more(emptySet()).isEmpty())
    }

    private val riyadh = LatLng(24.7136, 46.6753)
    private val riyadhZone = ZoneId.of("Asia/Riyadh")
    private val umm = PrayerSettings(method = Method.UMM_AL_QURA)
    private val calc = PrayerCalculator()

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) = ZonedDateTime.of(y, m, d, h, min, 0, 0, riyadhZone).toInstant()

    @Test
    fun fridayFromSaturdayIsTheComingFriday() {
        val f = FridayPreview.next(riyadh, riyadhZone, at(2026, 10, 17, 9, 0), umm, calc)!!
        assertEquals(LocalDate.of(2026, 10, 23), f.dhuhr.atZone(riyadhZone).toLocalDate())
        assertEquals(calc.schedule(riyadh, LocalDate.of(2026, 10, 23), riyadhZone, umm)[PrayerEvent.DHUHR], f.dhuhr)
        assertEquals(Duration.ofMinutes(AlertSettings.FRIDAY_LEAD_MIN.toLong()), Duration.between(f.reminder, f.dhuhr))
    }

    @Test
    fun fridayIsTodayUntilTheReminderPasses() {
        val morning = FridayPreview.next(riyadh, riyadhZone, at(2026, 10, 23, 8, 0), umm, calc)!!
        assertEquals(LocalDate.of(2026, 10, 23), morning.dhuhr.atZone(riyadhZone).toLocalDate())
        val noon = FridayPreview.next(riyadh, riyadhZone, at(2026, 10, 23, 12, 0), umm, calc)!!
        assertEquals(LocalDate.of(2026, 10, 30), noon.dhuhr.atZone(riyadhZone).toLocalDate())
    }

    @Test
    fun adhanChoiceRoundTrips() {
        val start = AlertSettings(before10 = true, adhanSource = AdhanSource.MADINAH, fajrAdhanSource = AdhanSource.MADINAH)
            .withSounds(PrayerEvent.MAGHRIB, PrayerSounds(adhan = false))
        for (c in AdhanChoice.entries) {
            val a = start.with(c)
            assertEquals(c, a.adhanChoice)
            assertEquals(start.perPrayer, a.perPrayer)
            assertTrue(a.before10)
        }
        val makkah = start.with(AdhanChoice.MAKKAH)
        assertTrue(makkah.adhan && makkah.fajrAdhan && !makkah.shortAdhan)
        assertEquals(AdhanSource.MAKKAH, makkah.fajrAdhanSource)
        // Short keeps the recordings; banner only keeps everything but the two Adhan switches.
        assertEquals(AdhanSource.MADINAH, start.with(AdhanChoice.SHORT).adhanSource)
        val banner = makkah.with(AdhanChoice.SHORT).with(AdhanChoice.BANNER_ONLY)
        assertFalse(banner.adhan || banner.fajrAdhan)
        assertTrue(banner.shortAdhan)
        assertEquals("banner_only", AdhanChoice.BANNER_ONLY.key)
    }

    @Test
    fun onboardingChoiceDefaultsToMakkahOnlyForANewUser() {
        assertEquals(AdhanChoice.MAKKAH, AlertSettings().onboardingChoice)
        assertEquals(AdhanChoice.BANNER_ONLY, AlertSettings(friday = true).onboardingChoice)
        assertEquals(AdhanChoice.MADINAH, AlertSettings().with(AdhanChoice.MADINAH).onboardingChoice)
    }

    @Test
    fun asrTimesHanafiIsLater() {
        val t = calc.asrTimes(riyadh, LocalDate.of(2026, 10, 17), riyadhZone, umm)
        val shafi = t.getValue(AsrMadhab.SHAFI)
        val hanafi = t.getValue(AsrMadhab.HANAFI)
        assertTrue(hanafi.isAfter(shafi))
        assertEquals(calc.schedule(riyadh, LocalDate.of(2026, 10, 17), riyadhZone, umm)[PrayerEvent.ASR], shafi)
    }

    @Test
    fun weatherPreviewSamples() {
        assertEquals(7, WeatherPreview.order.size)
        val now = at(2026, 10, 17, 12, 0)
        for (c in WeatherPreview.order) {
            val w = WeatherPreview.now(c, now)
            assertEquals(c, w.symbol.condition)
            assertEquals(c, Weather.symbol(WeatherPreview.code(c))?.condition)
        }
        val storm = WeatherPreview.now(WeatherCondition.THUNDERSTORM, now)
        assertEquals(19.0, storm.temperatureC, 1e-9)
        assertEquals(34.0 / 3.6, storm.windMs, 1e-9)
        assertEquals(-2.0, WeatherPreview.now(WeatherCondition.SNOW, now).temperatureC, 1e-9)
    }

    @Test
    fun dayArcHalves() {
        val date = LocalDate.of(2026, 10, 17)
        val days = (-1L..1L).map { calc.schedule(riyadh, date.plusDays(it), riyadhZone, umm) }
        val noon = at(2026, 10, 17, 12, 30)
        val night = DayArc.at(noon, days, DayArc.Half.NIGHT)!!
        assertFalse(night.isDay)
        assertEquals(days[1][PrayerEvent.MAGHRIB], night.start)
        assertEquals(0.0, night.fraction, 1e-9)
        assertEquals(DayArc.at(noon, days), DayArc.at(noon, days, DayArc.Half.DAY))
        val evening = days[1][PrayerEvent.MAGHRIB]!!.plusSeconds(3600)
        val nextDay = DayArc.at(evening, days, DayArc.Half.DAY)!!
        assertTrue(nextDay.isDay)
        assertEquals(days[2][PrayerEvent.SUNRISE], nextDay.start)
        assertEquals(0.0, nextDay.fraction, 1e-9)
        val early = days[1][PrayerEvent.FAJR]!!
        assertEquals(days[0][PrayerEvent.MAGHRIB], DayArc.at(early, days, DayArc.Half.NIGHT)!!.start)
    }

    @Test
    fun largestCityInZone() {
        val index = CityIndex(
            listOf(
                City(1, "Small", "Small", emptyList(), "SA", LatLng(1.0, 1.0), "Asia/Riyadh", 10),
                City(2, "Big", "Big", emptyList(), "SA", LatLng(2.0, 2.0), "Asia/Riyadh", 1000),
                City(3, "Other", "Other", emptyList(), "AE", LatLng(3.0, 3.0), "Asia/Dubai", 5000),
            ),
        )
        assertEquals("Big", index.largest("Asia/Riyadh")?.name)
        assertNull(index.largest("Europe/Oslo"))
        assertNotNull(index.largest("Asia/Dubai"))
    }
}
