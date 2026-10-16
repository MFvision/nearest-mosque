package sa.zood.nearmosque

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sa.zood.nearmosque.core.LatLng
import sa.zood.nearmosque.core.Method
import sa.zood.nearmosque.data.PrayerLocation
import sa.zood.nearmosque.ui.AppRoot
import sa.zood.nearmosque.ui.theme.NearMosqueTheme
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Foldables and large screens: unfolding a Galaxy Z Fold (cover screen to inner screen) while the app is open keeps
 * what the person was doing, and the app's activity takes any size and orientation.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w344dp-h882dp-420dpi", application = android.app.Application::class)
class FoldTest {
    @get:Rule val compose = createEmptyComposeRule()

    /** Hosts the app's screens like MainActivity, without its own handling of size changes (so it is recreated). */
    class Host : ComponentActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContent { NearMosqueTheme(dark = true) { AppRoot(app!!, showOnboarding = false) } }
        }
    }

    companion object {
        var app: AppContainer? = null
    }

    @Test fun mainActivityTakesAnySizeWithoutRestarting() {
        val context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.getActivityInfo(android.content.ComponentName(context, MainActivity::class.java), 0)
        val needed = ActivityInfo.CONFIG_SCREEN_SIZE or ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE or ActivityInfo.CONFIG_SCREEN_LAYOUT or
            ActivityInfo.CONFIG_ORIENTATION or ActivityInfo.CONFIG_DENSITY
        assertEquals("configChanges handles folding and resizing", needed, info.configChanges and needed)
        assertEquals("no fixed orientation", ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, info.screenOrientation)
        // resizeMode is not public: the manifest itself must not turn resizing off.
        val manifest = java.io.File(System.getProperty("nm.root") ?: "../..", "android/app/src/main/AndroidManifest.xml").readText()
        assertTrue("resizeable", !manifest.contains("resizeableActivity=\"false\""))
    }

    /** Google Play offers the app on Chromebooks, desktops and Android XR headsets: no hardware is required. */
    @Test fun noHardwareIsRequired() {
        val manifest = java.io.File(System.getProperty("nm.root") ?: "../..", "android/app/src/main/AndroidManifest.xml").readText()
        for (feature in listOf("touchscreen", "location", "location.gps", "sensor.compass", "sensor.accelerometer")) {
            assertTrue("$feature not required", Regex("android:name=\"android\\.hardware\\.${Regex.escape(feature)}\"\\s+android:required=\"false\"").containsMatchIn(manifest))
        }
    }

    /** The worst case, an activity that is recreated at the new size: the Ask conversation and the open screen stay. */
    @Test fun unfoldingKeepsTheConversation() = runBlocking {
        val zone = ZoneId.of("Asia/Riyadh")
        val at = ZonedDateTime.of(2026, 10, 3, 16, 20, 0, 0, zone).toInstant()
        val context = RuntimeEnvironment.getApplication()
        android.provider.Settings.Global.putFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val c = AppContainer(
            context, inMemoryDb = true, clock = { at },
            settingsFile = java.io.File.createTempFile("settings", ".preferences_pb").also { it.delete() },
            online = sa.zood.nearmosque.data.OnlineMosqueSource { _, _ -> emptyList() },
        )
        c.packs.ensureBuiltins { !sa.zood.nearmosque.data.ChunkScope.isLibrary(it.id) }
        c.start(installLibraries = false)
        c.settings.setPrayerLocation(PrayerLocation("Riyadh", LatLng(24.7136, 46.6753), zone, "SA", PrayerLocation.Source.CITY), follow = false)
        c.settings.setMethod(Method.UMM_AL_QURA, byUser = false)
        app = c

        val controller = Robolectric.buildActivity(Host::class.java).setup()
        val folded = controller.get()
        compose.waitForIdle()
        // On the cover screen: open Ask and ask a common question.
        compose.onNodeWithText(context.getString(R.string.tab_ask)).performClick()
        val question = "How do I perform wudu (ablution)?"
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithText(question).assertExists() }.isSuccess }
        compose.onNodeWithText(question).performScrollTo().performClick()
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithText(context.getString(R.string.sources)).assertExists() }.isSuccess }

        // Unfold: the inner screen's size and density.
        RuntimeEnvironment.setQualifiers("w690dp-h829dp-420dpi")
        controller.configurationChange(context.resources.configuration)
        // Robolectric recreates the activity but does not attach its new window (a real device shows it).
        controller.visible()
        val unfolded = controller.get()
        assertNotSame("recreated at the new size", folded, unfolded)
        assertEquals(690, unfolded.resources.configuration.screenWidthDp)
        compose.waitForIdle()
        // Still in Ask, with the question and its answer.
        assertTrue(compose.onAllNodesWithText(question).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText(context.getString(R.string.sources)).fetchSemanticsNodes().isNotEmpty())
        assertTrue(unfolded.resources.configuration.screenHeightDp >= 800)
        app = null
    }
}
