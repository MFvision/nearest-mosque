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
import sa.zood.nearmosque.core.IqamaSettings
import sa.zood.nearmosque.core.PrayerEvent
import sa.zood.nearmosque.core.PrayerSounds
import sa.zood.nearmosque.core.Reciter
import sa.zood.nearmosque.core.SilenceSettings
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
            adhan = true, adhanSource = AdhanSource.MADINAH, fajrAdhan = false, fajrAdhanSource = AdhanSource.MAKKAH, shortAdhan = true,
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
    fun oldSavesKeepTheWholeAdhan() = runBlocking {
        val file = File.createTempFile("adhan", ".preferences_pb").also { it.delete() }
        assertEquals(false, store(file, SupervisorJob()).settings.first().alerts.shortAdhan)
    }

    @Test
    fun iqamaSurvivesAReopen() = runBlocking {
        val file = File.createTempFile("iqama", ".preferences_pb").also { it.delete() }
        // A save from before the iqama: the usual waits.
        val old = SupervisorJob()
        assertEquals(IqamaSettings(), store(file, old).settings.first().iqama)
        old.cancel()
        old.join()
        val set = IqamaSettings(enabled = false, prayerMinutes = 20, alert = false).withWait(PrayerEvent.FAJR, 30).withWait(PrayerEvent.MAGHRIB, 5)
        val first = SupervisorJob()
        store(file, first).setIqama(set)
        first.cancel()
        first.join()
        assertEquals(set, store(file, SupervisorJob()).settings.first().iqama)
    }

    @Test
    fun silenceSurvivesAReopen() = runBlocking {
        val file = File.createTempFile("silence", ".preferences_pb").also { it.delete() }
        // Off until the user turns it on, every prayer chosen.
        val old = SupervisorJob()
        val fresh = store(file, old).settings.first().silence
        assertEquals(SilenceSettings(), fresh)
        assertEquals(false, fresh.enabled)
        old.cancel()
        old.join()
        val set = SilenceSettings(enabled = true, start = SilenceSettings.Start.ADHAN, fridayMinutes = 90)
            .with(PrayerEvent.FAJR, false).with(PrayerEvent.ISHA, false)
        val first = SupervisorJob()
        store(file, first).setSilence(set)
        first.cancel()
        first.join()
        assertEquals(set, store(file, SupervisorJob()).settings.first().silence)
    }

    @Test
    fun noPrayerSilencedSurvivesAReopen() = runBlocking {
        val file = File.createTempFile("silence-none", ".preferences_pb").also { it.delete() }
        val set = SilenceSettings(enabled = true, prayers = emptySet())
        val first = SupervisorJob()
        store(file, first).setSilence(set)
        first.cancel()
        first.join()
        assertEquals(set, store(file, SupervisorJob()).settings.first().silence)
        assertEquals(setOf(PrayerEvent.ASR), SettingsStore.decodePrayers(setOf("ASR", "SUNRISE", "garbage")))
    }

    @Test
    fun iqamaEncoding() {
        val m = mapOf(PrayerEvent.FAJR to 25, PrayerEvent.ISHA to 15)
        assertEquals(m, SettingsStore.decodeIqamaMinutes(SettingsStore.encodeIqamaMinutes(m)))
        assertEquals(emptyMap<PrayerEvent, Int>(), SettingsStore.decodeIqamaMinutes("garbage,SUNRISE:10,ASR:x,DHUHR:-5"))
    }

    @Test
    fun perPrayerEncoding() {
        val m = mapOf(PrayerEvent.ISHA to PrayerSounds(adhan = false, before10 = true, before5 = false, near = true))
        assertEquals(m, SettingsStore.decodePerPrayer(SettingsStore.encodePerPrayer(m)))
        assertEquals(emptyMap<PrayerEvent, PrayerSounds>(), SettingsStore.decodePerPrayer("garbage,FAJR:12"))
    }
}
