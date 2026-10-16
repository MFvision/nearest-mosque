package sa.zood.nearmosque.platform

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import sa.zood.nearmosque.core.AlertSettings
import sa.zood.nearmosque.core.LatLng
import sa.zood.nearmosque.core.Method
import sa.zood.nearmosque.core.PrayerEvent
import sa.zood.nearmosque.core.PrayerSettings
import sa.zood.nearmosque.core.SilenceSettings
import sa.zood.nearmosque.data.AppSettings
import sa.zood.nearmosque.data.PrayerLocation
import java.time.Instant
import java.time.ZoneId

/**
 * What happens when Android refuses the Adhan player (a foreground service may not start from an inexact alarm on
 * Android 12+), and when exact alarms are needed and allowed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReminderFallbackTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val nm get() = app.getSystemService(NotificationManager::class.java)
    private val makkah = PrayerLocation("Makkah", LatLng(21.4225, 39.8262), ZoneId.of("Asia/Riyadh"), "SA", PrayerLocation.Source.CITY)
    private val now = Instant.parse("2026-10-15T09:00:00Z")

    private fun settings(reminders: Set<PrayerEvent> = emptySet(), alerts: AlertSettings = AlertSettings(), silence: SilenceSettings = SilenceSettings()) =
        AppSettings(makkah, false, PrayerSettings(method = Method.UMM_AL_QURA), true, reminders, "compass", alerts = alerts, silence = silence)

    private fun fired(sound: String?, adhan: Boolean, id: Int = 1001) = Intent(app, ReminderReceiver::class.java)
        .putExtra(ReminderScheduler.EXTRA_TITLE, "Dhuhr").putExtra(ReminderScheduler.EXTRA_BODY, "Makkah · 11:49")
        .putExtra(ReminderScheduler.EXTRA_ID, id).putExtra(ReminderScheduler.EXTRA_ALARM, false)
        .putExtra(ReminderScheduler.EXTRA_SOUND, sound).putExtra(ReminderScheduler.EXTRA_ADHAN, adhan)

    @Before fun grant() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After fun restore() {
        ReminderReceiver.startAudio = PrayerAudioService::start
    }

    @Test fun refusedAdhanStillSoundsThroughItsOwnChannel() {
        ReminderReceiver.startAudio = { _, _ -> false }
        ReminderReceiver().onReceive(app, fired("adhan_makkah", adhan = true))
        val n = shadowOf(nm).getNotification(1001)
        assertNotNull("the reminder is still shown", n)
        assertEquals("adhan_sound_adhan_makkah", n.channelId)
        val channel = nm.getNotificationChannel(n.channelId)
        assertEquals("android.resource://${app.packageName}/raw/adhan_makkah", channel.sound.toString())
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
    }

    @Test fun refusedShortAndFajrAdhansGetTheirRecordings() {
        ReminderReceiver.startAudio = { _, _ -> false }
        ReminderReceiver().onReceive(app, fired("adhan_madinah_short", adhan = true, id = 1002))
        ReminderReceiver().onReceive(app, fired("fajr_makkah", adhan = true, id = 1003))
        assertEquals("android.resource://${app.packageName}/raw/adhan_madinah_short", nm.getNotificationChannel(shadowOf(nm).getNotification(1002).channelId).sound.toString())
        assertEquals("android.resource://${app.packageName}/raw/fajr_makkah", nm.getNotificationChannel(shadowOf(nm).getNotification(1003).channelId).sound.toString())
    }

    @Test fun refusedSpokenReminderUsesTheReminderChannel() {
        ReminderReceiver.startAudio = { _, _ -> false }
        ReminderReceiver().onReceive(app, fired("voice_en_dhuhr_5", adhan = false))
        assertEquals(ReminderScheduler.CHANNEL, shadowOf(nm).getNotification(1001).channelId)
    }

    @Test fun unknownRecordingFallsBackToTheReminderChannel() {
        ReminderReceiver.startAudio = { _, _ -> false }
        ReminderReceiver().onReceive(app, fired("adhan_nowhere", adhan = true))
        assertEquals(ReminderScheduler.CHANNEL, shadowOf(nm).getNotification(1001).channelId)
        assertNull(nm.getNotificationChannel("adhan_sound_adhan_nowhere"))
    }

    @Test fun startedPlayerLeavesTheNotificationToTheService() {
        ReminderReceiver.startAudio = { _, _ -> true }
        ReminderReceiver().onReceive(app, fired("adhan_makkah", adhan = true))
        assertNull(shadowOf(nm).getNotification(1001))
    }

    @Test fun refusedForegroundServiceIsCaughtNotThrown() {
        val refusing = object : ContextWrapper(app) {
            override fun startForegroundService(service: Intent?) = throw ForegroundServiceStartNotAllowedException("not from the background")
            override fun getApplicationContext(): Context = this
        }
        assertFalse(PrayerAudioService.start(refusing, fired("adhan_makkah", adhan = true)))
    }

    @Test fun exactAlarmsAreAskedForOnlyWhenSomethingRingsAtASetMinute() {
        assertFalse(ReminderScheduler.wantsTimedAlerts(settings(), now))
        assertFalse(ReminderScheduler.wantsTimedAlerts(settings().copy(prayerLocation = null, reminders = setOf(PrayerEvent.FAJR)), now))
        assertTrue(ReminderScheduler.wantsTimedAlerts(settings(reminders = setOf(PrayerEvent.FAJR)), now))
        assertTrue(ReminderScheduler.wantsTimedAlerts(settings(alerts = AlertSettings(adhan = true)), now))
        assertTrue(ReminderScheduler.wantsTimedAlerts(settings(alerts = AlertSettings(fajrAlarmMinutesBefore = 0)), now))
        assertTrue(ReminderScheduler.wantsTimedAlerts(settings(silence = SilenceSettings(enabled = true)), now))
    }

    @Test fun exactAlarmPageIsThisAppsOwn() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertFalse(ReminderScheduler.exactAlarmsAllowed(app))
        val intent = ReminderScheduler.exactAlarmSettingsIntent(app)!!
        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, intent.action)
        assertEquals("package:${app.packageName}", intent.data.toString())
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        assertTrue(ReminderScheduler.exactAlarmsAllowed(app))
    }

    @Test fun withoutExactAlarmsTheFajrAlarmIsStillScheduled() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val count = ReminderScheduler(app).reschedule(settings(alerts = AlertSettings(fajrAlarmMinutesBefore = 0)), now)
        assertTrue(count > 0)
        assertEquals(count, shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.size)
    }
}
