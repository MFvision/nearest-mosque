package sa.zood.nearmosque.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.EncodedImageFormat
import sa.zood.nearmosque.core.City
import sa.zood.nearmosque.core.HighLatRule
import sa.zood.nearmosque.core.PolarRule
import sa.zood.nearmosque.core.PrayerEvent
import sa.zood.nearmosque.core.PrayerMoment
import sa.zood.nearmosque.core.Weather
import sa.zood.nearmosque.core.WeatherCache
import sa.zood.nearmosque.core.WeatherNow
import sa.zood.nearmosque.core.WeatherSample
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Renders the main screen off-screen (no display needed) into build/shots, in English and Arabic. */
class ScreenshotTest {
    private val riyadh get() = Cities.index.search("Riyadh", 1).first()
    private val noon = Instant.parse("2026-10-12T10:00:00Z")

    /** Riyadh's Dhuhr Adhan on the screenshot day. */
    private fun dhuhr(clock: Clock): Instant = clock.today(noon)[PrayerEvent.DHUHR]!!

    private val tromso get() = Cities.byId(3133895)!!
    /** Polar night in Tromsø: the sun does not rise. */
    private val polarNoon = Instant.parse("2026-12-21T11:00:00Z")

    private fun shot(
        name: String, language: String, city: City = riyadh, change: (DesktopSettings) -> DesktopSettings = { it },
        at: ((Clock) -> Instant)? = null,
    ) {
        Strings.language = language
        // The one-time weather offer already answered, so these show the screens as they were before it.
        val settings = change(DesktopSettings(cityId = city.id, language = language, weatherOffered = true))
        val clock = Clock(city, settings)
        val now = at?.invoke(clock) ?: noon
        render(name) { AppScreen(settings, city, clock, now, onCity = {}, onSettings = {}) }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(name: String, height: Int = 680, content: @Composable () -> Unit) {
        val scene = ImageComposeScene(980, height, Density(1f)) {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFE2C275), background = Navy)) {
                CompositionLocalProvider(LocalLayoutDirection provides if (Strings.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    content()
                }
            }
        }
        scene.render()
        val img = scene.render(1_000_000_000L)
        val out = File("build/shots").apply { mkdirs() }
        File(out, "$name.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
    }

    @Test fun cityTableLoads() { assertNotNull(Cities.index.search("Makkah", 1).firstOrNull()) }

    @Test fun prayerScreenEnglish() = shot("prayer-en", "en")

    @Test fun prayerScreenArabic() = shot("prayer-ar", "ar")

    // Five minutes after the Dhuhr Adhan: the countdown to the iqama (20 minutes by default).
    @Test fun iqamaScreenEnglish() = shot("iqama-en", "en") { dhuhr(it).plusSeconds(300) }

    @Test fun iqamaScreenArabic() = shot("iqama-ar", "ar") { dhuhr(it).plusSeconds(300) }

    // No times on a polar night: the card that offers the nearest-latitude rule.
    @Test fun polarUnavailableEnglish() = shot("polar-unavailable-en", "en", tromso) { polarNoon }

    @Test fun polarUnavailableArabic() = shot("polar-unavailable-ar", "ar", tromso) { polarNoon }

    @Test fun polarEstimatedEnglish() = shot("polar-estimated-en", "en", tromso, { it.copy(polarRule = PolarRule.NEAREST_LATITUDE) }) { polarNoon }

    /** Settings with the calculation choices (high-latitude and polar rules, minute adjustments). */
    private fun settingsShot(name: String, language: String) {
        Strings.language = language
        val city = riyadh
        val settings = DesktopSettings(
            cityId = city.id, language = language, highLatitudeRule = HighLatRule.TWILIGHT_ANGLE,
            offsets = mapOf(PrayerEvent.FAJR to 2, PrayerEvent.SUNRISE to -1, PrayerEvent.ISHA to 30),
        )
        render(name, height = 1300) {
            Box(Modifier.fillMaxSize().background(Navy).padding(24.dp)) { SettingsScreen(settings, Clock(city, settings), onChange = {}, onChangeCity = {}) }
        }
    }

    @Test fun calculationSettingsEnglish() = settingsShot("settings-calculation-en", "en")

    /** The whole Settings page with the reminders on (before the prayer, each prayer, Qur'an and dhikr, more reminders). */
    private fun alertsShot(name: String, language: String) {
        Strings.language = language
        val city = riyadh
        val settings = DesktopSettings(
            cityId = city.id, language = language, near = true, nearMinutes = 20, before10 = true,
            perPrayer = mapOf(PrayerEvent.ASR to sa.zood.nearmosque.core.PrayerSounds(before10 = false)),
            salawat = true, quran = true, quranMinutes = 21 * 60 + 7, bedtimeMinutes = 22 * 60 + 30, kahf = true,
            friday = true, ramadan = true, fajrAlarmMinutesBefore = 10,
        )
        render(name, height = 4900) {
            Box(Modifier.fillMaxSize().background(Navy).padding(24.dp)) {
                SettingsScreen(settings, Clock(city, settings), onChange = {}, onChangeCity = {}, openPrayer = PrayerEvent.ASR)
            }
        }
    }

    @Test fun alertSettingsEnglish() = alertsShot("settings-alerts-en", "en")

    @Test fun alertSettingsArabic() = alertsShot("settings-alerts-ar", "ar")

    /** The Fajr alarm ringing: its card with Stop above the prayer screen. */
    private fun alarmShot(name: String, language: String) {
        Strings.language = language
        val settings = DesktopSettings(cityId = riyadh.id, language = language, fajrAlarmMinutesBefore = 10, weatherOffered = true)
        val clock = Clock(riyadh, settings)
        val now = clock.today(noon)[PrayerEvent.FAJR]!!.minusSeconds(590)
        render(name) { AppScreen(settings, riyadh, clock, now, onCity = {}, onSettings = {}, alarm = true) }
    }

    @Test fun alarmEnglish() = alarmShot("alarm-en", "en")

    @Test fun alarmArabic() = alarmShot("alarm-ar", "ar")

    @Test fun calculationSettingsArabic() = settingsShot("settings-calculation-ar", "ar")

    // Sky follows the weather: the next-prayer card on the time-of-day sky with the city's weather, from a seeded
    // cache read the way the app reads it (Weather.now), and the temperature and wind line under the city name.
    private val istanbul get() = Cities.index.search("Istanbul", 1).first()

    private fun weatherShot(
        name: String, code: String?, city: City = riyadh, language: String = "en", tempC: Double = 24.0, windMs: Double = 4.0,
        at: (Clock) -> Instant,
    ): WeatherNow? {
        Strings.language = language
        val settings = DesktopSettings(cityId = city.id, language = language, skyWeather = true, weatherOffered = true)
        val clock = Clock(city, settings)
        val now = at(clock)
        val key = Weather.key(city.location)
        val cache = code?.let {
            WeatherCache(
                key = key, samples = listOf(WeatherSample(now.minusSeconds(600), tempC, windMs, it)), lastModified = "Mon, 12 Oct 2026 09:30:00 GMT",
                expires = now.plusSeconds(1500), validatedAt = now.minusSeconds(300), lastAttempt = now.minusSeconds(300),
            )
        }
        val weather = Weather.now(cache, key, now)
        if (code != null) assertNotNull(weather, "the seeded $code is shown")
        render(name) { AppScreen(settings, city, clock, now, onCity = {}, onSettings = {}, weather = weather, animate = false) }
        return weather
    }

    private fun localTime(clock: Clock, hhmm: String): Instant =
        java.time.LocalDate.of(2026, 10, 12).atTime(java.time.LocalTime.parse(hhmm)).atZone(clock.zone).toInstant()

    @Test fun weatherClearDay() { weatherShot("weather-clear-en", "clearsky_day", tempC = 33.6, windMs = 3.1) { localTime(it, "13:00") } }

    @Test fun weatherPartlyCloudyAsr() { weatherShot("weather-partly-cloudy-en", "partlycloudy_day", tempC = 31.2) { localTime(it, "16:00") } }

    @Test fun weatherCloudySunrise() { weatherShot("weather-cloudy-en", "cloudy", tempC = 22.0) { localTime(it, "06:15") } }

    @Test fun weatherRainDay() { weatherShot("weather-rain-en", "rain", tempC = 19.4, windMs = 6.2) { localTime(it, "13:00") } }

    @Test fun weatherRainArabic() { weatherShot("weather-rain-ar", "heavyrainshowers_day", language = "ar", tempC = 19.4, windMs = 6.2) { localTime(it, "13:00") } }

    @Test fun weatherThunderstormMaghrib() { weatherShot("weather-thunderstorm-en", "heavyrainandthunder", tempC = 21.0, windMs = 9.5) { localTime(it, "18:00") } }

    @Test fun weatherFogFajr() { weatherShot("weather-fog-en", "fog", istanbul, tempC = 11.0, windMs = 1.2) { localTime(it, "07:00") } }

    @Test fun weatherSnowIsha() { weatherShot("weather-snow-en", "snow", istanbul, tempC = -2.5, windMs = 3.0) { localTime(it, "21:00") } }

    @Test fun weatherClearNightArabic() { weatherShot("weather-clear-night-ar", "clearsky_night", language = "ar", tempC = 26.0, windMs = 2.0) { localTime(it, "23:30") } }

    // On, but no fresh data (offline, or older than 3 hours): the plain time-of-day sky and no line, no error.
    @Test fun weatherFallback() { assertNull(weatherShot("weather-fallback-en", null) { localTime(it, "13:00") }) }

    /** The one-time offer at the top of the prayer screen (the desktop's onboarding for the feature). */
    private fun offerShot(name: String, language: String) {
        Strings.language = language
        val settings = DesktopSettings(cityId = riyadh.id, language = language)
        val clock = Clock(riyadh, settings)
        var answered: DesktopSettings? = null
        render(name) { AppScreen(settings, riyadh, clock, noon, onCity = {}, onSettings = { answered = it }) }
        assertNull(answered)
    }

    @Test fun weatherOfferEnglish() = offerShot("weather-offer-en", "en")

    @Test fun weatherOfferArabic() = offerShot("weather-offer-ar", "ar")

    @Test fun stagesFollowTheIqama() {
        Strings.language = "en"
        val clock = Clock(riyadh, DesktopSettings(cityId = riyadh.id))
        val adhan = dhuhr(clock)
        val wait = clock.moment(adhan.plusSeconds(300))!!
        assertEquals(PrayerMoment.Stage.TO_IQAMA, wait.stage)
        assertEquals(PrayerEvent.DHUHR, wait.event)
        assertEquals(adhan.plusSeconds(20 * 60), wait.until)
        assertTrue(trayStatus(clock, adhan.plusSeconds(300)).endsWith("Time for Dhuhr · 15 min until the iqama"))
        assertEquals(PrayerMoment.Stage.PRAYER, clock.moment(adhan.plusSeconds(25 * 60))!!.stage)
        assertTrue(trayStatus(clock, adhan.plusSeconds(25 * 60)).endsWith("Dhuhr prayer has begun"))
        assertEquals(PrayerMoment.Stage.UPCOMING, clock.moment(adhan.plusSeconds(40 * 60))!!.stage)
        // With the iqama off, straight to the next Adhan.
        val off = Clock(riyadh, DesktopSettings(cityId = riyadh.id, iqama = sa.zood.nearmosque.core.IqamaSettings.OFF))
        assertEquals(PrayerMoment.Stage.UPCOMING, off.moment(adhan.plusSeconds(300))!!.stage)
    }

    @Test fun iqamaNotifiesWithoutTheAdhan() {
        Strings.language = "en"
        val settings = DesktopSettings(cityId = riyadh.id, adhan = false)
        val clock = Clock(riyadh, settings)
        val sent = mutableListOf<Pair<String, String>>()
        val reminders = Reminders { title, body -> sent += title to body }
        val iqama = dhuhr(clock).plusSeconds(20 * 60)
        reminders.tick(clock, settings, iqama.plusSeconds(30))
        reminders.tick(clock, settings, iqama.plusSeconds(60))
        assertEquals(listOf("Iqama for Dhuhr" to "Time to stand for the prayer."), sent)
        // Off: nothing at the iqama.
        val quiet = mutableListOf<String>()
        Reminders { title, _ -> quiet += title }.tick(clock, settings.copy(iqama = settings.iqama.copy(alert = false)), iqama.plusSeconds(30))
        assertTrue(quiet.isEmpty())
    }
}
