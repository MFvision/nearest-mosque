package sa.zood.nearmosque.platform

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sa.zood.nearmosque.core.AdhanSource
import sa.zood.nearmosque.core.AlertKind
import sa.zood.nearmosque.core.AlertSettings
import sa.zood.nearmosque.core.PrayerEvent
import sa.zood.nearmosque.core.VoiceLanguage

/** Which Adhan recording plays: the whole one (Fajr has its own), or the short one for every prayer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AudioCatalogTest {
    private val a = AlertSettings(adhan = true, adhanSource = AdhanSource.MADINAH, fajrAdhan = true, fajrAdhanSource = AdhanSource.MAKKAH)

    private fun adhan(e: PrayerEvent, s: AlertSettings) = AudioCatalog.sound(AlertKind.ADHAN, e, s, VoiceLanguage.ENGLISH)

    @Test
    fun wholeAdhanByDefault() {
        assertEquals("fajr_makkah", adhan(PrayerEvent.FAJR, a))
        assertEquals("adhan_madinah", adhan(PrayerEvent.DHUHR, a))
        assertEquals("adhan_madinah", adhan(PrayerEvent.ISHA, a))
    }

    @Test
    fun shortAdhanForEveryPrayerIncludingFajr() {
        val s = a.copy(shortAdhan = true)
        // Fajr uses its own source, the other prayers theirs.
        assertEquals("adhan_makkah_short", adhan(PrayerEvent.FAJR, s))
        PrayerEvent.prayers.filter { it != PrayerEvent.FAJR }.forEach { assertEquals("adhan_madinah_short", adhan(it, s)) }
    }

    @Test
    fun everyAdhanRecordingIsBundled() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        AdhanSource.entries.forEach { source ->
            listOf(AudioCatalog.adhan(source, false), AudioCatalog.adhan(source, true), AudioCatalog.shortAdhan(source)).forEach {
                assertNotEquals(it, 0, AudioCatalog.rawId(context, it))
            }
        }
    }
}
