package sa.zood.nearmosque.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertNotNull

/** Renders the main screen off-screen (no display needed) into build/shots, in English and Arabic. */
class ScreenshotTest {
    @OptIn(ExperimentalComposeUiApi::class)
    private fun shot(name: String, language: String) {
        Strings.language = language
        val city = Cities.index.search("Riyadh", 1).first()
        val settings = DesktopSettings(cityId = city.id, language = language)
        val clock = Clock(city, settings)
        val now = Instant.parse("2026-10-12T10:00:00Z")
        val scene = ImageComposeScene(980, 680, Density(1f)) {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFE2C275), background = Navy)) {
                CompositionLocalProvider(LocalLayoutDirection provides if (Strings.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    AppScreen(settings, city, clock, now, onCity = {}, onSettings = {})
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
}
