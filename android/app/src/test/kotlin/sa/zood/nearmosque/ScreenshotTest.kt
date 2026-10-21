package sa.zood.nearmosque

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sa.zood.nearmosque.core.LatLng
import sa.zood.nearmosque.core.Method
import sa.zood.nearmosque.core.OnboardingStep
import sa.zood.nearmosque.core.WeatherCondition
import sa.zood.nearmosque.data.PrayerLocation
import sa.zood.nearmosque.ui.AppRoot
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import sa.zood.nearmosque.ui.Tab
import sa.zood.nearmosque.ui.theme.NearMosqueTheme
import sa.zood.nearmosque.ui.glass.under as underWeather
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Renders the real screens (real database, packs, calculations) at a fixed instant for visual review.
 * Output: PNG files in app/build/screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi", application = android.app.Application::class)
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val capeTown = PrayerLocation("Cape Town", LatLng(-33.92584, 18.42322), ZoneId.of("Africa/Johannesburg"), "ZA", PrayerLocation.Source.CITY)
    // 2026-10-03 05:30 SAST: between Fajr and sunrise, like the reference screenshot's situation.
    private val fixed = ZonedDateTime.of(2026, 10, 3, 5, 30, 28, 0, ZoneId.of("Africa/Johannesburg")).toInstant()

    /** "Sky follows the weather" in a test: a MET symbol code with its temperature (°C) and wind (m/s). */
    private data class Wx(val symbol: String, val tempC: Double, val windMs: Double)

    /** A compact MET reply whose steps start at the hour of [at], all with [w] (the shape of shared/fixtures/met-compact-sample.json). */
    private fun metBody(at: java.time.Instant, w: Wx): String {
        val start = at.truncatedTo(java.time.temporal.ChronoUnit.HOURS)
        val steps = (0..3).joinToString(",") { i ->
            """{"time":"${start.plusSeconds(3600L * i)}","data":{"instant":{"details":{"air_temperature":${w.tempC},"wind_speed":${w.windMs}}},""" +
                """"next_1_hours":{"summary":{"symbol_code":"${w.symbol}"}}}}"""
        }
        return """{"type":"Feature","properties":{"meta":{"units":{"air_temperature":"celsius","wind_speed":"m/s"}},"timeseries":[$steps]}}"""
    }

    private fun container(location: PrayerLocation? = capeTown, at: java.time.Instant = fixed, weather: Wx? = null): AppContainer = runBlocking {
        // Still frames (system "remove animations"), and no network: live search returns nothing new, and the
        // weather (only when asked for) comes from a fake MET that answers once with [weather].
        android.provider.Settings.Global.putFloat(compose.activity.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        // The app style is process-wide: every test starts from Gold and cream.
        compose.activity.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        AppStyle.load(compose.activity)
        val c = AppContainer(
            compose.activity, inMemoryDb = true, clock = { at },
            settingsFile = java.io.File.createTempFile("settings", ".preferences_pb").also { it.delete() },
            online = sa.zood.nearmosque.data.OnlineMosqueSource { _, _ -> emptyList() },
            weatherSource = sa.zood.nearmosque.data.WeatherSource { _, _, _ ->
                weather?.let { sa.zood.nearmosque.data.WeatherHttp(200, null, null, metBody(at, it)) }
            },
        )
        c.packs.ensureBuiltins { !sa.zood.nearmosque.data.ChunkScope.isLibrary(it.id) }
        c.start(installLibraries = false)
        if (location != null) {
            c.settings.setPrayerLocation(location, follow = false)
            c.settings.setMethod(Method.MUSLIM_WORLD_LEAGUE, byUser = false)
        }
        c.weather.clear()
        if (weather != null) {
            c.settings.setSkyWeather(true)
            // Cached before the first frame, so the screenshot shows it without waiting for the refresh loop.
            location?.let { c.weather.refreshIfDue(it.location) }
        }
        c
    }

    private fun shoot(
        name: String, tab: Tab, location: PrayerLocation? = capeTown, onboarding: OnboardingStep? = null, dark: Boolean = true, at: java.time.Instant = fixed,
        weather: Wx? = null, preview: WeatherCondition? = null, after: () -> Unit = {},
    ) {
        val c = container(location, at, weather)
        compose.setContent {
            NearMosqueTheme(dark = dark) {
                AppRoot(
                    c, initialTab = tab, showOnboarding = onboarding != null,
                    onboardingStep = onboarding ?: OnboardingStep.PROMISE, onboardingStill = true, onboardingPreview = preview,
                )
            }
        }
        compose.waitForIdle()
        after()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun prayerEnglish() = shoot("prayer_en", Tab.PRAYER)

    @Test fun prayerLight() = shoot("prayer_en_light", Tab.PRAYER, dark = false)

    @Test fun mosquesLight() = shoot("mosques_en_light", Tab.MOSQUES, dark = false)

    @Test fun askLight() = shoot("ask_en_light", Tab.ASK, dark = false)

    @Test @Config(qualifiers = "ar-w393dp-h852dp-xxhdpi")
    fun prayerArabicLight() = shoot("prayer_ar_light", Tab.PRAYER, dark = false)

    @Test @Config(qualifiers = "ar-w393dp-h852dp-xxhdpi")
    fun prayerArabicRtl() = shoot("prayer_ar", Tab.PRAYER)

    @Test fun firstLaunchNoLocation() = shoot("prayer_first_launch_en", Tab.PRAYER, location = null)

    @Test fun prayerScrolled() = shoot("prayer_scrolled_en", Tab.PRAYER) {
        compose.onNodeWithText("Today’s prayer times").performScrollTo()
    }

    /** Scrolls down until the header has shrunk into the bar across the top. */
    private fun scrollToCompact() {
        compose.onNode(androidx.compose.ui.test.hasScrollToIndexAction()).performScrollToIndex(2)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1500)
    }

    @Test fun compactNight() = shoot("compact_night_en", Tab.PRAYER) { scrollToCompact() }

    // 14:10 in light mode: the bar by day; the sun past Dhuhr on its path.
    @Test fun compactDayLight() = shoot(
        "compact_day_en_light", Tab.PRAYER, dark = false,
        at = ZonedDateTime.of(2026, 10, 3, 14, 10, 0, 0, ZoneId.of("Africa/Johannesburg")).toInstant(),
    ) { scrollToCompact() }

    @Test @Config(qualifiers = "ar-w393dp-h852dp-xxhdpi")
    fun compactArabic() = shoot(
        "compact_day_ar", Tab.PRAYER,
        at = ZonedDateTime.of(2026, 10, 3, 14, 10, 0, 0, ZoneId.of("Africa/Johannesburg")).toInstant(),
    ) { scrollToCompact() }

    // The phone's largest common text size (150%): nothing may be cut or overlap.
    @Test @Config(fontScale = 1.5f)
    fun prayerLargeText() = shoot("prayer_en_large_text", Tab.PRAYER)

    @Test @Config(fontScale = 1.5f)
    fun askLargeText() = shoot("ask_en_large_text", Tab.ASK)

    @Test @Config(fontScale = 1.5f)
    fun compactLargeText() = shoot("compact_en_large_text", Tab.PRAYER) { scrollToCompact() }

    // Maghrib light on the glass (18:40) in dark and light mode.
    @Test fun prayerMaghrib() = shoot("prayer_maghrib_en", Tab.PRAYER, at = ZonedDateTime.of(2026, 10, 3, 18, 40, 0, 0, ZoneId.of("Africa/Johannesburg")).toInstant())

    /** [minutes] after that day's Asr Adhan in Cape Town. */
    private fun afterAsr(minutes: Long) = sa.zood.nearmosque.core.PrayerCalculator()
        .schedule(capeTown.location, java.time.LocalDate.of(2026, 10, 3), capeTown.zoneId, sa.zood.nearmosque.core.PrayerSettings())
        .get(sa.zood.nearmosque.core.PrayerEvent.ASR)!!.plusSeconds(minutes * 60)

    // After the Asr Adhan: "Time for Asr" and the countdown to the iqama; then the prayer itself, with no countdown.
    @Test fun prayerToIqama() = shoot("prayer_iqama_en", Tab.PRAYER, at = afterAsr(5))
    @Test fun prayerStarted() = shoot("prayer_started_en", Tab.PRAYER, dark = false, at = afterAsr(25))
    @Test fun compactToIqama() = shoot("compact_iqama_en", Tab.PRAYER, at = afterAsr(5)) { scrollToCompact() }
    @Test @Config(qualifiers = "ar-w393dp-h852dp-xxhdpi")
    fun prayerToIqamaArabic() = shoot("prayer_iqama_ar", Tab.PRAYER, at = afterAsr(5))

    @Test fun prayerDayLight() = shoot("prayer_day_en_light", Tab.PRAYER, dark = false, at = ZonedDateTime.of(2026, 10, 3, 10, 0, 0, 0, ZoneId.of("Africa/Johannesburg")).toInstant())

    /** The full Qibla view, with a live heading [heading]° (Cape Town's Qibla is about 23°). */
    private fun shootQibla(name: String, heading: Double, dark: Boolean = true, weather: Wx? = null) {
        val c = container(weather = weather)
        val vm = sa.zood.nearmosque.ui.prayer.PrayerViewModel(c)
        vm.refreshNearestMosque("en")
        compose.setContent {
            NearMosqueTheme(dark = dark) {
                androidx.compose.runtime.CompositionLocalProvider(LocalAppContainer provides c) {
                    val ui by vm.ui.collectAsState()
                    val sky = sa.zood.nearmosque.ui.theme.Sky.of(ui.sky, dark)
                    androidx.compose.runtime.CompositionLocalProvider(sa.zood.nearmosque.ui.theme.LocalSky provides sky.underWeather(ui.weather)) {
                        val compass = sa.zood.nearmosque.core.CompassState.Live(heading, 8.0, false)
                        val aligned = ui.qiblaBearing?.let { kotlin.math.abs(sa.zood.nearmosque.core.Angles.relativeToQibla(it, heading)) < 3 } ?: false
                        sa.zood.nearmosque.ui.prayer.QiblaCompassScreen(ui, compass, aligned, onClose = {})
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    // Phone pointing 80° away: "Turn left".
    @Test fun qiblaTurn() = shootQibla("qibla_turn_en", heading = 103.0)

    @Test fun qiblaTurnLight() = shootQibla("qibla_turn_en_light", heading = 330.0, dark = false)

    // Facing it: the dot reaches the Kaaba, the logo arrow points straight up, the light brightens.
    @Test fun qiblaFacing() = shootQibla("qibla_facing_en", heading = 23.4)

    @Test @Config(qualifiers = "ar-w393dp-h852dp-xxhdpi")
    fun qiblaArabic() = shootQibla("qibla_turn_ar", heading = 60.0)

    // Sky follows the weather (opt-in): each condition on the sky, by night and by day, light and dark, Arabic RTL.
    // Robolectric's default locale is en-US, so the English line shows °F and mph; Arabic has no region: °C and km/h.
    private fun sast(h: Int, m: Int) = ZonedDateTime.of(2026, 10, 3, h, m, 0, 0, ZoneId.of("Africa/Johannesburg")).toInstant()

    @Test fun prayerRainNight() = shoot("prayer_rain_night_en", Tab.PRAYER, at = sast(22, 30), weather = Wx("rain", 14.2, 6.1))

    @Test fun prayerThunderDayLight() = shoot("prayer_thunder_day_en_light", Tab.PRAYER, dark = false, at = sast(14, 10), weather = Wx("heavyrainandthunder", 19.6, 9.4))

    @Test fun prayerFog() = shoot("prayer_fog_en", Tab.PRAYER, at = sast(7, 10), weather = Wx("fog", 11.8, 1.2))

    @Test @Config(qualifiers = "ar-w393dp-h852dp-xxhdpi")
    fun prayerSnowArabic() = shoot("prayer_snow_ar", Tab.PRAYER, at = sast(10, 0), weather = Wx("snow", -2.6, 4.3))

    @Test fun prayerCloudyLight() = shoot("prayer_cloudy_en_light", Tab.PRAYER, dark = false, at = sast(10, 0), weather = Wx("cloudy", 16.0, 3.0))

    @Test fun prayerPartlyCloudyEvening() = shoot("prayer_partly_cloudy_evening_en", Tab.PRAYER, at = sast(18, 40), weather = Wx("partlycloudy_day", 18.0, 2.5))

    @Test fun compactCloudy() = shoot("compact_cloudy_en", Tab.PRAYER, at = sast(14, 10), weather = Wx("cloudy", 17.4, 5.0)) { scrollToCompact() }

    @Test fun qiblaRain() = shootQibla("qibla_rain_en", heading = 103.0, weather = Wx("rainshowers_day", 15.0, 7.0))

    /** Appearance in Settings: the switch on, what it sends, and the credit to MET Norway. */
    @Test fun settingsWeather() {
        val c = container(weather = Wx("partlycloudy_day", 18.0, 2.5))
        val vm = sa.zood.nearmosque.ui.prayer.PrayerViewModel(c)
        compose.setContent {
            NearMosqueTheme(dark = true) {
                androidx.compose.runtime.CompositionLocalProvider(LocalAppContainer provides c) {
                    val ui by vm.ui.collectAsState()
                    sa.zood.nearmosque.ui.settings.SettingsScreen(c, ui, {}, {}, {}, {}, vm::setSkyWeather) {}
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Weather: based on data from MET Norway").performScrollTo()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/settings_weather_en.png")
    }

    @Test fun mosquesList() = shoot("mosques_en", Tab.MOSQUES)

    @Test @Config(qualifiers = "ur-w393dp-h852dp-xxhdpi")
    fun mosquesUrdu() = shoot("mosques_ur", Tab.MOSQUES)

    @Test fun askHome() = shoot("ask_en", Tab.ASK)

    @Test fun askAnswer() = shoot("ask_answer_en", Tab.ASK) {
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithText("How do I perform wudu (ablution)?").assertExists() }.isSuccess }
        compose.onNodeWithText("How do I perform wudu (ablution)?").performClick()
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithText("Sources").assertExists() }.isSuccess }
    }

    @Test @Config(qualifiers = "ar-w393dp-h852dp-xxhdpi")
    fun askAnswerArabic() = shoot("ask_answer_ar", Tab.ASK) {
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithText("كيف أتوضأ؟").assertExists() }.isSuccess }
        compose.onNodeWithText("كيف أتوضأ؟").performClick()
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithText("المصادر").assertExists() }.isSuccess }
    }

    @Test @Config(qualifiers = "tr-w393dp-h852dp-xxhdpi")
    fun askTurkish() = shoot("ask_tr", Tab.ASK)

    // Draft languages: layout, script and direction (Persian right to left).
    @Test @Config(qualifiers = "fa-w393dp-h852dp-xxhdpi")
    fun prayerPersian() = shoot("prayer_fa", Tab.PRAYER)

    @Test @Config(qualifiers = "hi-w393dp-h852dp-xxhdpi")
    fun prayerHindi() = shoot("prayer_hi", Tab.PRAYER, dark = false)

    @Test @Config(qualifiers = "ru-w393dp-h852dp-xxhdpi")
    fun mosquesRussian() = shoot("mosques_ru", Tab.MOSQUES)

    @Test @Config(qualifiers = "b+zh+Hans-w393dp-h852dp-xxhdpi")
    fun askChinese() = shoot("ask_zh", Tab.ASK)

    // Large screens: an unfolded Galaxy Z Fold, and a 10-inch tablet held sideways (two panes).
    @Test @Config(qualifiers = "w690dp-h829dp-420dpi")
    fun prayerFoldInner() = shoot("prayer_fold_inner_en", Tab.PRAYER)

    @Test @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun prayerTabletLandscape() = shoot("prayer_tablet_land_en", Tab.PRAYER)

    @Test @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun mosquesTabletLandscape() = shoot("mosques_tablet_land_en", Tab.MOSQUES) {
        compose.waitUntil(30_000) { runCatching { compose.onNodeWithText("Nearest known mosque").assertExists() }.isSuccess }
    }

    @Test @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun askTabletLandscape() = shoot("ask_answer_tablet_land_en", Tab.ASK) {
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithText("How do I perform wudu (ablution)?").assertExists() }.isSuccess }
        compose.onNodeWithText("How do I perform wudu (ablution)?").performClick()
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithText("Sources").assertExists() }.isSuccess }
    }

    /** The classical books in English: the page reads left to right, only the Arabic titles right to left. */
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test fun classicsEnglish() {
        val c = container()
        compose.setContent { NearMosqueTheme(dark = true) { AppRoot(c, initialTab = Tab.ASK, showOnboarding = false) } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Library").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Browse").performClick()
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithText("Classical books").assertExists() }.isSuccess }
        compose.onNodeWithText("Classical books").performClick()
        compose.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/screenshots/classics_en.png")
    }

    /** Full-screen map cards (the map itself is MapLibre, not drawn here). */
    private fun shootMapCards(name: String, dark: Boolean) {
        fun mosque(id: String, n: String, addr: String?, phone: String?, web: String?, lat: Double, lng: Double, d: Double) =
            sa.zood.nearmosque.core.RankedMosque(
                sa.zood.nearmosque.core.Mosque(id, "mosques.za", sa.zood.nearmosque.core.MosqueCategory.MOSQUE, mapOf("default" to n),
                    sa.zood.nearmosque.core.LatLng(lat, lng), addr, phone, web, null, null), d)
        val a = mosque("osm:1", "Masjid Al-Noor", "12 Long Street, Cape Town", "+27 21 000 0000", "https://example.org", -33.92, 18.42, 420.0)
        val b = mosque("osm:2", "Palm Tree Mosque", null, null, null, -33.93, 18.41, 5200.0)
        compose.setContent {
            NearMosqueTheme(dark = dark) {
                androidx.compose.foundation.layout.Column(
                    androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color(0xFF9DB7C9)).padding(20.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                ) {
                    sa.zood.nearmosque.ui.mosques.MosqueMapCard(a, nearest = true, next = "Asr" to "4:08 PM") {}
                    sa.zood.nearmosque.ui.mosques.MosqueMapCard(b, nearest = false, next = "Asr" to "4:08 PM") {}
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun mapCards() = shootMapCards("map_cards_en", dark = false)
    @Test fun mapCardsDark() = shootMapCards("map_cards_en_dark", dark = true)

    /** Prayer & Audio with a mix of switches on, and Maghrib's own settings open. */
    @Test fun prayerAudioSettings() {
        // Exact alarms allowed, as after the user grants them; the row that asks for them has its own screenshot.
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val c = container()
        val alerts = sa.zood.nearmosque.core.AlertSettings(adhan = true, before10 = true, near = true, salawat = true, quran = true)
            .withSounds(sa.zood.nearmosque.core.PrayerEvent.MAGHRIB, sa.zood.nearmosque.core.PrayerSounds(adhan = false))
        compose.setContent {
            NearMosqueTheme(dark = true) {
                sa.zood.nearmosque.ui.settings.PrayerAudioScreen(c, alerts, setOf(sa.zood.nearmosque.core.PrayerEvent.FAJR), sa.zood.nearmosque.core.IqamaSettings()) {}
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/prayer_audio_en.png")
        // The first "Maghrib" is its own prayer's row; the iqama section has one too.
        compose.onAllNodesWithText("Maghrib")[0].performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Play the Adhan").performScrollTo()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/prayer_audio_maghrib_en.png")
    }

    /** The iqama section of Prayer & Audio: the switch, a wait per prayer, the prayer after it and its notification. */
    @Test fun iqamaSettings() {
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val c = container()
        compose.setContent {
            NearMosqueTheme(dark = true) {
                sa.zood.nearmosque.ui.settings.PrayerAudioScreen(
                    c, sa.zood.nearmosque.core.AlertSettings(), emptySet(),
                    sa.zood.nearmosque.core.IqamaSettings().withWait(sa.zood.nearmosque.core.PrayerEvent.ISHA, 15),
                ) {}
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Notify at the iqama").performScrollTo()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/iqama_settings_en.png")
    }

    /** Silent during prayer: on, without Do Not Disturb access yet (the row that asks for it), Fajr left out. */
    @Test fun silenceSettings() {
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val c = container()
        compose.setContent {
            NearMosqueTheme(dark = true) {
                sa.zood.nearmosque.ui.settings.PrayerAudioScreen(
                    c, sa.zood.nearmosque.core.AlertSettings(), emptySet(), sa.zood.nearmosque.core.IqamaSettings(),
                    sa.zood.nearmosque.core.SilenceSettings(enabled = true).with(sa.zood.nearmosque.core.PrayerEvent.FAJR, false),
                ) {}
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Allow Do Not Disturb access").performScrollTo()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/silence_settings_en.png")
        compose.onNodeWithText("Do Not Disturb turns on", substring = true).performScrollTo()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/silence_settings_friday_en.png")
    }

    /** Android 12+ without "Alarms & reminders": with the Adhan on, Prayer & Audio starts with the row that opens it. */
    @Test fun exactAlarmRow() {
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val c = container()
        compose.setContent {
            NearMosqueTheme(dark = true) {
                sa.zood.nearmosque.ui.settings.PrayerAudioScreen(c, sa.zood.nearmosque.core.AlertSettings(adhan = true), emptySet(), sa.zood.nearmosque.core.IqamaSettings()) {}
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("To ring on time", substring = true).assertExists()
        compose.onRoot().captureRoboImage("build/screenshots/exact_alarm_en.png")
    }

    /** Check my alerts, from Prayer & Audio: what the phone allows, and the next five alerts set (the Adhan as an alarm). */
    @Test fun alertCheck() {
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(false)
        org.robolectric.Shadows.shadowOf(compose.activity.application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val c = container()
        runBlocking {
            c.settings.setAlerts(sa.zood.nearmosque.core.AlertSettings(adhan = true, fajrAdhan = true, before10 = true))
            sa.zood.nearmosque.platform.ReminderScheduler(compose.activity).reschedule(c.settings.settings.first())
        }
        compose.setContent {
            NearMosqueTheme(dark = true) { sa.zood.nearmosque.ui.settings.AlertCheckScreen(c) {} }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Check my alerts").assertExists()
        compose.onNodeWithText("Alarms & reminders").assertExists()
        compose.onAllNodesWithText("Alarm · Adhan · Masjid al-Haram, Makkah")[0].assertExists()
        compose.onRoot().captureRoboImage("build/screenshots/alert_check_en.png")
    }

    /** First Light on Android 14+: right after notifications, "Alarms & reminders" is asked for, with a line saying why. */
    @Test fun onboardingAsksForExactAlarms() {
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(false)
        org.robolectric.Shadows.shadowOf(compose.activity.application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        onb("onboarding_exact_en", OnboardingStep.PRIVACY) {
            compose.onNodeWithText("Allow notifications").performClick()
            // The step's own "Not now" (the Prayer screen under the tour shows its own "Allow Alarms & reminders").
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Not now").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("One more step", substring = true).assertExists()
            assertEquals(2, compose.onAllNodesWithText("Allow Alarms & reminders").fetchSemanticsNodes().size)
        }
    }

    // First Light, the welcome tour: every step in English and Arabic (right to left), still frames. The promise and the
    // place start with no place chosen; the rest in Cape Town. The Arabic frames name it as the app does when the city is
    // picked in Arabic (City.displayName("ar") of the bundled row: كيب تاون).
    private val capeTownArabic = capeTown.copy(name = "كيب تاون")
    private fun onb(name: String, step: OnboardingStep, at: java.time.Instant = fixed, dark: Boolean = true, preview: WeatherCondition? = null, after: () -> Unit = {}) =
        shoot(name, Tab.PRAYER, location = if (step.ordinal <= OnboardingStep.PLACE.ordinal) null else if ("_ar" in name) capeTownArabic else capeTown, onboarding = step, dark = dark, at = at, preview = preview, after = after)

    @Test fun onboardingPromise() = onb("onboarding_promise_en", OnboardingStep.PROMISE)
    @Test fun onboardingWhere() = onb("onboarding_where_en", OnboardingStep.PLACE)
    @Test fun onboardingAsr() = onb("onboarding_asr_en", OnboardingStep.ASR)
    @Test fun onboardingAdhan() = onb("onboarding_adhan_en", OnboardingStep.ADHAN)
    @Test fun onboardingFriday() = onb("onboarding_friday_en", OnboardingStep.FRIDAY)
    @Test fun onboardingWeather() = onb("onboarding_weather_en", OnboardingStep.WEATHER, preview = WeatherCondition.RAIN)
    @Test fun onboardingLook() = onb("onboarding_look_en", OnboardingStep.LOOK)
    @Test fun onboardingBuilding() = onb("onboarding_building_en", OnboardingStep.BUILDING)
    // 14:10: the sun on the day's arc.
    @Test fun onboardingDay() = onb("onboarding_day_en", OnboardingStep.DAY, at = sast(14, 10))
    @Test fun onboardingExtras() = onb("onboarding_extras_en", OnboardingStep.EXTRAS)
    @Test fun onboardingMore() = onb("onboarding_more_en", OnboardingStep.MORE)
    @Test fun onboardingPrivacy() = onb("onboarding_privacy_en", OnboardingStep.PRIVACY)
    @Test fun onboardingDone() = onb("onboarding_done_en", OnboardingStep.DONE)

    @Test @Config(qualifiers = "ar-w393dp-h852dp-xxhdpi") fun onboardingPromiseArabic() = onb("onboarding_promise_ar", OnboardingStep.PROMISE)
    @Test @Config(qualifiers = "ar-w393dp-h852dp-xxhdpi") fun onboardingWhereArabic() = onb("onboarding_where_ar", OnboardingStep.PLACE)
    @Test @Config(qualifiers = "ar-w393dp-h852dp-xxhdpi") fun onboardingAsrArabic() = onb("onboarding_asr_ar", OnboardingStep.ASR)
    @Test @Config(qualifiers = "ar-w393dp-h852dp-xxhdpi") fun onboardingAdhanArabic() = onb("onboarding_adhan_ar", OnboardingStep.ADHAN)
    @Test @Config(qualifiers = "ar-w393dp-h852dp-xxhdpi") fun onboardingFridayArabic() = onb("onboarding_friday_ar", OnboardingStep.FRIDAY)
    @Test @Config(qualifiers = "ar-w393dp-h852dp-xxhdpi") fun onboardingWeatherArabic() = onb("onboarding_weather_ar", OnboardingStep.WEATHER, preview = WeatherCondition.SNOW)
    @Test @Config(qualifiers = "ar-w393dp-h852dp-xxhdpi") fun onboardingLookArabic() = onb("onboarding_look_ar", OnboardingStep.LOOK)
    @Test @Config(qualifiers = "ar-w393dp-h852dp-xxhdpi") fun onboardingBuildingArabic() = onb("onboarding_building_ar", OnboardingStep.BUILDING)
    // 05:30, before sunrise: the moon on the night's arc, which runs right to left.
    @Test @Config(qualifiers = "ar-w393dp-h852dp-xxhdpi") fun onboardingDayArabic() = onb("onboarding_day_ar", OnboardingStep.DAY)
    @Test @Config(qualifiers = "ar-w393dp-h852dp-xxhdpi") fun onboardingExtrasArabic() = onb("onboarding_extras_ar", OnboardingStep.EXTRAS)
    @Test @Config(qualifiers = "ar-w393dp-h852dp-xxhdpi") fun onboardingMoreArabic() = onb("onboarding_more_ar", OnboardingStep.MORE)
    @Test @Config(qualifiers = "ar-w393dp-h852dp-xxhdpi") fun onboardingPrivacyArabic() = onb("onboarding_privacy_ar", OnboardingStep.PRIVACY)
    @Test @Config(qualifiers = "ar-w393dp-h852dp-xxhdpi") fun onboardingDoneArabic() = onb("onboarding_done_ar", OnboardingStep.DONE)

    // Two panes (a tablet held sideways), an unfolded Fold, light mode and long Swahili text.
    @Test @Config(qualifiers = "w1280dp-h800dp-xhdpi") fun onboardingWhereTablet() = onb("onboarding_where_tablet_en", OnboardingStep.PLACE)
    @Test @Config(qualifiers = "w1280dp-h800dp-xhdpi") fun onboardingDayTablet() = onb("onboarding_day_tablet_en", OnboardingStep.DAY, at = sast(14, 10))
    @Test @Config(qualifiers = "w1280dp-h800dp-xhdpi") fun onboardingFridayTablet() = onb("onboarding_friday_tablet_en", OnboardingStep.FRIDAY)
    @Test @Config(qualifiers = "w1280dp-h800dp-xhdpi") fun onboardingPrivacyTablet() = onb("onboarding_privacy_tablet_en", OnboardingStep.PRIVACY)
    @Test @Config(qualifiers = "ar-w1280dp-h800dp-xhdpi") fun onboardingLookTabletArabic() = onb("onboarding_look_tablet_ar", OnboardingStep.LOOK)
    @Test @Config(qualifiers = "w690dp-h829dp-420dpi") fun onboardingDayFold() = onb("onboarding_day_fold_en", OnboardingStep.DAY, at = sast(14, 10))
    @Test fun onboardingLookLight() = onb("onboarding_look_en_light", OnboardingStep.LOOK, dark = false, at = sast(10, 0))
    @Test fun onboardingPrivacyLight() = onb("onboarding_privacy_en_light", OnboardingStep.PRIVACY, dark = false, at = sast(10, 0))
    @Test @Config(qualifiers = "sw-w393dp-h852dp-xxhdpi") fun onboardingPrivacySwahili() = onb("onboarding_privacy_sw", OnboardingStep.PRIVACY)
    @Test @Config(qualifiers = "sw-w393dp-h852dp-xxhdpi") fun onboardingMoreSwahili() = onb("onboarding_more_sw", OnboardingStep.MORE)
    // A phone turned sideways keeps the one scrolling column (two panes would leave each step a sliver).
    @Test @Config(qualifiers = "w852dp-h393dp-xxhdpi") fun onboardingAdhanLandscape() = onb("onboarding_adhan_land_en", OnboardingStep.ADHAN)
    @Test @Config(qualifiers = "w852dp-h393dp-xxhdpi") fun onboardingLookLandscape() = onb("onboarding_look_land_en", OnboardingStep.LOOK)
    @Test @Config(qualifiers = "w852dp-h393dp-xxhdpi") fun onboardingPrivacyLandscape() = onb("onboarding_privacy_land_en", OnboardingStep.PRIVACY)

    /** The flow on its own (no screenshot), starting at [step], for the behaviour tests below. */
    private fun startOnboarding(step: OnboardingStep, before: suspend (AppContainer) -> Unit = {}): AppContainer {
        val c = container()
        runBlocking { before(c) }
        compose.setContent {
            NearMosqueTheme(dark = true) { AppRoot(c, showOnboarding = true, onboardingStep = step, onboardingStill = true) }
        }
        compose.waitForIdle()
        return c
    }

    /** Waits for a saved setting: the view model saves on the main looper, which only runs while the test idles it. */
    private fun waitFor(condition: suspend () -> Boolean) {
        repeat(100) {
            compose.waitForIdle()
            if (runBlocking { condition() }) return
            Thread.sleep(50)
        }
        throw AssertionError("The setting was not saved")
    }

    private fun headerDescription(): String = compose.onNode(androidx.compose.ui.test.hasContentDescription("Your day", substring = true))
        .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].joinToString()

    @Test fun lookChoosesTheAppStyle() {
        startOnboarding(OnboardingStep.LOOK)
        compose.onNodeWithText("Teal").performClick()
        compose.waitForIdle()
        org.junit.Assert.assertEquals("teal", AppStyle.current)
        org.junit.Assert.assertTrue(headerDescription().contains("Teal"))
    }

    @Test fun shortAdhanIsSaved() {
        val c = startOnboarding(OnboardingStep.ADHAN)
        compose.onNodeWithText("Short Adhan").performClick()
        waitFor { c.settings.settings.first().alerts.shortAdhan }
        val a = runBlocking { c.settings.settings.first().alerts }
        org.junit.Assert.assertTrue(a.adhan && a.fajrAdhan)
    }

    @Test fun fridaySwitchSavesAndShowsTheChip() {
        val c = startOnboarding(OnboardingStep.FRIDAY)
        org.junit.Assert.assertFalse(headerDescription().contains("Friday"))
        compose.onNodeWithText("Friday (Jumu'ah) reminder").performClick()
        waitFor { c.settings.settings.first().alerts.friday }
        compose.waitForIdle()
        org.junit.Assert.assertTrue(headerDescription().endsWith("Friday"))
        // The Friday line shows the coming Friday's real times.
        compose.onNodeWithText("On Friday:", substring = true).assertExists()
        compose.onNodeWithText("Friday (Jumu'ah) reminder").performClick()
        waitFor { !c.settings.settings.first().alerts.friday }
        compose.waitForIdle()
        org.junit.Assert.assertFalse(headerDescription().contains("Friday"))
    }

    @Test fun skipOnFridayChangesNothing() {
        val c = startOnboarding(OnboardingStep.FRIDAY)
        val before = runBlocking { c.settings.settings.first().alerts }
        compose.onNodeWithText("Skip").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Building your prayer day…").assertExists()
        org.junit.Assert.assertEquals(before, runBlocking { c.settings.settings.first().alerts })
    }

    // Someone who has set nothing sees Makkah selected; Skip keeps what they saw, as Continue does.
    @Test fun skipOnAdhanKeepsTheChoiceShown() {
        val c = startOnboarding(OnboardingStep.ADHAN)
        org.junit.Assert.assertEquals(sa.zood.nearmosque.core.AlertSettings(), runBlocking { c.settings.settings.first().alerts })
        compose.onNodeWithText("Skip").performClick()
        waitFor { c.settings.settings.first().alerts.adhanChoice == sa.zood.nearmosque.core.AdhanChoice.MAKKAH }
        compose.onNodeWithText("Building your prayer day…").assertExists()
        org.junit.Assert.assertTrue(headerDescription().contains("Makkah Adhan"))
    }

    // Skipped from the Asr step, the Adhan step was never shown: its chip stays a placeholder, not "Banner only".
    @Test fun skipPastTheAdhanLeavesItsChipEmpty() {
        val c = startOnboarding(OnboardingStep.ASR)
        compose.onNodeWithText("Skip").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Building your prayer day…").assertExists()
        org.junit.Assert.assertEquals(sa.zood.nearmosque.core.AlertSettings(), runBlocking { c.settings.settings.first().alerts })
        org.junit.Assert.assertFalse(headerDescription().contains("Banner only"))
    }

    // Seen again with the weather sky already on: Not now leaves it on.
    @Test fun notNowKeepsTheWeatherSkyOn() {
        val c = startOnboarding(OnboardingStep.WEATHER) { it.settings.setSkyWeather(true) }
        compose.onNodeWithText("Not now").performClick()
        compose.waitForIdle()
        org.junit.Assert.assertTrue(runBlocking { c.settings.settings.first().skyWeather })
    }

    // Building and done have no Back; the system Back stays in the tour instead of leaving it half-finished.
    @Test fun systemBackStaysOnDone() {
        startOnboarding(OnboardingStep.DONE)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        org.junit.Assert.assertFalse(compose.activity.isFinishing)
        compose.onNodeWithText("You’re all set.").assertExists()
    }

    @Test fun skipOnExtrasGoesToPrivacy() {
        startOnboarding(OnboardingStep.EXTRAS)
        compose.onNodeWithText("Skip").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Exactly what leaves your phone").assertExists()
    }

    // Robolectric has no sensors: the bearing from north instead of the compass; the Adhan step (passed) offered the quiet.
    @Test fun moreShowsOnlyTheRest() {
        startOnboarding(OnboardingStep.MORE)
        compose.onNodeWithText("Qibla from north").assertExists()
        compose.onNodeWithText("Qibla compass").assertDoesNotExist()
        compose.onNodeWithText("Silent during prayer").assertDoesNotExist()
    }

    @Test fun extrasDrawsTheRealWidgets() {
        startOnboarding(OnboardingStep.EXTRAS)
        compose.onNodeWithContentDescription("Preview: Next prayer").assertExists()
        compose.onNodeWithContentDescription("Preview: Today's prayers").assertExists()
        compose.onNode(androidx.compose.ui.test.hasScrollToIndexAction() and androidx.compose.ui.test.hasAnyDescendant(androidx.compose.ui.test.hasContentDescription("Preview: Next prayer")))
            .performScrollToIndex(3)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Preview: Prayer ring").assertExists()
        compose.onNodeWithContentDescription("Preview: Qibla").assertExists()
    }

    @Test fun finishedOnboardingIsNotShownAgain() {
        val c = container()
        runBlocking { c.settings.setOnboarded(true) }
        compose.setContent { NearMosqueTheme(dark = true) { AppRoot(c) } }
        compose.waitForIdle()
        compose.onNodeWithText("Pray on time, wherever you are.").assertDoesNotExist()
    }

    @Test fun newUserSeesTheOnboarding() {
        val c = container(location = null)
        compose.setContent { NearMosqueTheme(dark = true) { AppRoot(c, onboardingStill = true) } }
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithText("Pray on time, wherever you are.").assertExists() }.isSuccess }
    }
}
