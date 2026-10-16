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
import sa.zood.nearmosque.core.PrayerEvent
import sa.zood.nearmosque.core.PrayerMoment
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Renders the main screen off-screen (no display needed) into build/shots, in English and Arabic. */
class ScreenshotTest {
    private val riyadh get() = Cities.index.search("Riyadh", 1).first()
    private val noon = Instant.parse("2026-10-12T10:00:00Z")

    /** Riyadh's Dhuhr Adhan on the screenshot day. */
    private fun dhuhr(clock: Clock): Instant = clock.today(noon)[PrayerEvent.DHUHR]!!

    @OptIn(ExperimentalComposeUiApi::class)
    private fun shot(name: String, language: String, at: ((Clock) -> Instant)? = null) {
        Strings.language = language
        val city = riyadh
        val settings = DesktopSettings(cityId = city.id, language = language)
        val clock = Clock(city, settings)
        val now = at?.invoke(clock) ?: noon
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

    // Five minutes after the Dhuhr Adhan: the countdown to the iqama (20 minutes by default).
    @Test fun iqamaScreenEnglish() = shot("iqama-en", "en") { dhuhr(it).plusSeconds(300) }

    @Test fun iqamaScreenArabic() = shot("iqama-ar", "ar") { dhuhr(it).plusSeconds(300) }

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
