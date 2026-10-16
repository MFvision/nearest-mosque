package sa.zood.nearmosque

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sa.zood.nearmosque.ui.ask.Classics
import sa.zood.nearmosque.ui.ask.ClassicsList
import sa.zood.nearmosque.ui.theme.NearMosqueTheme

/**
 * The classical books list and one book, in a left-to-right and two right-to-left interfaces: the interface text
 * follows the interface direction, only the books' Arabic title and author are always right to left.
 * Output: PNG files in app/build/screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi", application = android.app.Application::class)
class ClassicsScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun shoot(lang: String) {
        compose.setContent { NearMosqueTheme(dark = true) { Box(Modifier.fillMaxSize().background(Color(0xFF0B1220))) { ClassicsList(onBack = {}) } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/classics_$lang.png")
        val first = Classics.catalog(compose.activity).byGroup.first().second.first()
        compose.onNodeWithText(first.title).performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/classics_book_$lang.png")
    }

    @Test fun english() = shoot("en")

    @Test @Config(qualifiers = "ar-w393dp-h852dp-xxhdpi")
    fun arabic() = shoot("ar")

    @Test @Config(qualifiers = "ur-w393dp-h852dp-xxhdpi")
    fun urdu() = shoot("ur")
}
