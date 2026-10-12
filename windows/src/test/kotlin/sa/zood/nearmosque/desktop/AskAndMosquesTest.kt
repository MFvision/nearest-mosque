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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import sa.zood.nearmosque.core.AnswerKind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AskAndMosquesTest {
    @Test fun commonQuestionIsAnsweredWithItsVerses() {
        val a = AskEngine.ask("How do I perform wudu?")
        assertEquals(AnswerKind.COMMON, a.kind)
        assertEquals("wudu", a.commonQuestion?.id)
        assertTrue(a.citations.all { AskEngine.chunk(it) != null }, "every cited verse is in the bundled Qur'an")
        assertTrue(AskEngine.hadithFor(a.commonQuestion!!, "en").isNotEmpty(), "the quoted hadith is bundled")
    }

    @Test fun unrelatedQuestionFindsNothing() {
        assertEquals(AnswerKind.INSUFFICIENT, AskEngine.ask("qwertyuiop zxcvbnm").kind)
    }

    @Test fun bundledMosquesNearCairo() = runBlocking {
        val cairo = Cities.index.search("Cairo", 1).first()
        val r = MosqueFinder.near(cairo.location, searchOnline = false)
        assertTrue(r.mosques.size > 50, "Cairo's bundled mosques are found (${r.mosques.size})")
        assertTrue(r.mosques.zipWithNext().all { (a, b) -> a.distanceMeters <= b.distanceMeters }, "nearest first")
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun shot(name: String, language: String, content: @Composable () -> Unit) {
        Strings.language = language
        val scene = ImageComposeScene(980, 680, Density(1f)) {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFE2C275), background = Navy)) {
                CompositionLocalProvider(LocalLayoutDirection provides if (Strings.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Box(Modifier.fillMaxSize().background(Navy).padding(24.dp)) { content() }
                }
            }
        }
        // Draw a few frames so work started on the first (the mosque search) shows up.
        var img = scene.render(0)
        for (i in 1..20) { Thread.sleep(150); img = scene.render(i * 100_000_000L) }
        val out = File("build/shots").apply { mkdirs() }
        File(out, "$name.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
    }

    @Test fun askScreen() = shot("ask-en", "en") { AskScreen() }

    @Test fun mosquesScreenArabic() {
        val cairo = Cities.index.search("Cairo", 1).first()
        val result = runBlocking { MosqueFinder.near(cairo.location, searchOnline = false) }
        shot("mosques-ar", "ar") { MosqueList(cairo, online = false, result = result) }
    }
}

