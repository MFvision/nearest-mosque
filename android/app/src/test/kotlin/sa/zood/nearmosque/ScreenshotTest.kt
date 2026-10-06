package sa.zood.nearmosque

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sa.zood.nearmosque.core.LatLng
import sa.zood.nearmosque.core.Method
import sa.zood.nearmosque.data.PrayerLocation
import sa.zood.nearmosque.ui.AppRoot
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import sa.zood.nearmosque.ui.Tab
import sa.zood.nearmosque.ui.theme.NearMosqueTheme
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

    private fun container(location: PrayerLocation? = capeTown, at: java.time.Instant = fixed): AppContainer = runBlocking {
        // Still frames (system "remove animations"), and no network: live search returns nothing new.
        android.provider.Settings.Global.putFloat(compose.activity.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val c = AppContainer(
            compose.activity, inMemoryDb = true, clock = { at },
            settingsFile = java.io.File.createTempFile("settings", ".preferences_pb").also { it.delete() },
            online = sa.zood.nearmosque.data.OnlineMosqueSource { _, _ -> emptyList() },
        )
        c.packs.ensureBuiltins { !sa.zood.nearmosque.data.ChunkScope.isLibrary(it.id) }
        c.start(installLibraries = false)
        if (location != null) {
            c.settings.setPrayerLocation(location, follow = false)
            c.settings.setMethod(Method.MUSLIM_WORLD_LEAGUE, byUser = false)
        }
        c
    }

    private fun shoot(name: String, tab: Tab, location: PrayerLocation? = capeTown, onboardingPage: Int? = null, dark: Boolean = true, at: java.time.Instant = fixed, after: () -> Unit = {}) {
        val c = container(location, at)
        compose.setContent { NearMosqueTheme(dark = dark) { AppRoot(c, initialTab = tab, showOnboarding = onboardingPage != null, onboardingPage = onboardingPage ?: 0) } }
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

    @Test fun onboardingWelcome() = shoot("onboarding_welcome_en", Tab.PRAYER, location = null, onboardingPage = 0)

    @Test fun onboardingQibla() = shoot("onboarding_qibla_en", Tab.PRAYER, location = null, onboardingPage = 2)

    @Test fun onboardingMosque() = shoot("onboarding_mosque_en", Tab.PRAYER, location = null, onboardingPage = 3)

    @Test fun onboardingAsk() = shoot("onboarding_ask_en", Tab.PRAYER, location = null, onboardingPage = 4)

    @Test fun onboardingSetup() = shoot("onboarding_setup_en", Tab.PRAYER, location = null, onboardingPage = 5)

    @Test @Config(qualifiers = "ar-w393dp-h852dp-xxhdpi")
    fun onboardingArabic() = shoot("onboarding_qibla_ar", Tab.PRAYER, location = null, onboardingPage = 2)

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

    @Test fun prayerDayLight() = shoot("prayer_day_en_light", Tab.PRAYER, dark = false, at = ZonedDateTime.of(2026, 10, 3, 10, 0, 0, 0, ZoneId.of("Africa/Johannesburg")).toInstant())

    /** The full Qibla view, with a live heading [heading]° (Cape Town's Qibla is about 23°). */
    private fun shootQibla(name: String, heading: Double, dark: Boolean = true) {
        val c = container()
        val vm = sa.zood.nearmosque.ui.prayer.PrayerViewModel(c)
        vm.refreshNearestMosque("en")
        compose.setContent {
            NearMosqueTheme(dark = dark) {
                androidx.compose.runtime.CompositionLocalProvider(LocalAppContainer provides c) {
                    val ui by vm.ui.collectAsState()
                    androidx.compose.runtime.CompositionLocalProvider(sa.zood.nearmosque.ui.theme.LocalSky provides sa.zood.nearmosque.ui.theme.Sky.of(ui.sky, dark)) {
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

    // The setup page offers the language's content for download (nothing is fetched in the test).
    @Test @Config(qualifiers = "sw-w393dp-h852dp-xxhdpi")
    fun onboardingSetupSwahili() = shoot("onboarding_setup_sw", Tab.PRAYER, onboardingPage = 5)
}
