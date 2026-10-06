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

    /** Scrolls until the compact bar shows, then taps it: the sky card. */
    private fun openSkyCard() {
        // Past the hero (item 0), so the compact bar is pinned.
        compose.onNode(androidx.compose.ui.test.hasScrollToIndexAction()).performScrollToIndex(2)
        compose.waitForIdle()
        compose.onNode(androidx.compose.ui.test.SemanticsMatcher("opens the sky card") {
            androidx.compose.ui.semantics.SemanticsActions.OnClick in it.config && it.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].label in setOf("Expand", "توسيع")
        }).performClick()
        compose.mainClock.advanceTimeBy(1500)
    }

    // 05:30, before sunrise: the night half with the moon.
    @Test fun skyCardNight() = shoot("sky_card_night_en", Tab.PRAYER) { openSkyCard() }

    // 14:10 in light mode: the sun past Dhuhr on its path.
    @Test fun skyCardDayLight() = shoot(
        "sky_card_day_en_light", Tab.PRAYER, dark = false,
        at = ZonedDateTime.of(2026, 10, 3, 14, 10, 0, 0, ZoneId.of("Africa/Johannesburg")).toInstant(),
    ) { openSkyCard() }

    @Test @Config(qualifiers = "ar-w393dp-h852dp-xxhdpi")
    fun skyCardArabic() = shoot(
        "sky_card_day_ar", Tab.PRAYER,
        at = ZonedDateTime.of(2026, 10, 3, 14, 10, 0, 0, ZoneId.of("Africa/Johannesburg")).toInstant(),
    ) { openSkyCard() }

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
}
