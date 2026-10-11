package sa.zood.nearmosque.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import sa.zood.nearmosque.core.AdhanSource
import sa.zood.nearmosque.core.AlertSettings
import sa.zood.nearmosque.core.PrayerEvent
import sa.zood.nearmosque.core.PrayerSounds
import sa.zood.nearmosque.core.Reciter
import sa.zood.nearmosque.core.VoiceLanguage
import java.io.File

/** Every audio and reminder setting is saved and read back as set (after the app is closed and reopened). */
class AudioSettingsTest {
    private fun store(file: File, job: kotlinx.coroutines.Job) = SettingsStore(
        PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file },
    )

    @Test
    fun everySettingSurvivesAReopen() = runBlocking {
        val file = File.createTempFile("audio", ".preferences_pb").also { it.delete() }
        val set = AlertSettings(
            friday = true, kahf = true, bedtimeMinutes = 22 * 60 + 15,
            adhan = true, adhanSource = AdhanSource.MADINAH, fajrAdhan = false, fajrAdhanSource = AdhanSource.MAKKAH,
            before10 = true, before5 = false, near = true, nearMinutes = 20, voice = VoiceLanguage.ARABIC,
            salawat = true, salawatMinutes = 10 * 60 + 30, quran = false, quranMinutes = 21 * 60, reciter = Reciter.CLASSIC,
        ).withSounds(PrayerEvent.MAGHRIB, PrayerSounds(adhan = false, before10 = true, before5 = true, near = false))
            .withSounds(PrayerEvent.FAJR, PrayerSounds(adhan = true, before10 = false, before5 = true, near = true))
        // One DataStore per file at a time: the first is closed before the "reopened" one reads.
        val first = SupervisorJob()
        store(file, first).setAlerts(set)
        first.cancel()
        first.join()
        assertEquals(set, store(file, SupervisorJob()).settings.first().alerts)
    }

    @Test
    fun perPrayerEncoding() {
        val m = mapOf(PrayerEvent.ISHA to PrayerSounds(adhan = false, before10 = true, before5 = false, near = true))
        assertEquals(m, SettingsStore.decodePerPrayer(SettingsStore.encodePerPrayer(m)))
        assertEquals(emptyMap<PrayerEvent, PrayerSounds>(), SettingsStore.decodePerPrayer("garbage,FAJR:12"))
    }
}
