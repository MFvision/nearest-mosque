package sa.zood.nearmosque.platform

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
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
import sa.zood.nearmosque.core.AlertKind
import sa.zood.nearmosque.core.AlertSettings
import sa.zood.nearmosque.core.HijriCalendar
import sa.zood.nearmosque.core.IqamaSettings
import sa.zood.nearmosque.core.LatLng
import sa.zood.nearmosque.core.Method
import sa.zood.nearmosque.core.PrayerCalculator
import sa.zood.nearmosque.core.PrayerEvent
import sa.zood.nearmosque.core.PrayerSettings
import sa.zood.nearmosque.data.AppSettings
import sa.zood.nearmosque.data.PrayerLocation
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The bounded schedule: notification ids from what each alert is, one rebuild at a time, an alert Android held back
 * set again (once), and the copy that sets the next day's alarms again after a restart.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReminderSchedulerTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val makkah = PrayerLocation("Makkah", LatLng(21.4225, 39.8262), ZoneId.of("Asia/Riyadh"), "SA", PrayerLocation.Source.CITY)
    private val prayer = PrayerSettings(method = Method.UMM_AL_QURA)
    private val thursday = LocalDate.of(2026, 10, 15)
    private val dhuhr = PrayerCalculator().schedule(makkah.location, thursday, makkah.zoneId, prayer)[PrayerEvent.DHUHR]!!
    private fun calc(date: LocalDate) = PrayerCalculator().schedule(makkah.location, date, makkah.zoneId, prayer)
    private val am get() = app.getSystemService(AlarmManager::class.java)

    private fun settings(alerts: AlertSettings = AlertSettings(adhan = true, fajrAdhan = true), reminders: Set<PrayerEvent> = emptySet()) =
        AppSettings(makkah, false, prayer, true, reminders, "compass", alerts = alerts)

    private fun scheduledTimes() = shadowOf(am).scheduledAlarms.map { Instant.ofEpochMilli(it.triggerAtTime) }

    @Before fun grant() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @After fun restore() {
        ReminderReceiver.startAudio = PrayerAudioService::start
    }

    @Test fun notificationIdsComeFromWhatTheAlertIs() {
        val busy = settings(
            AlertSettings(adhan = true, fajrAdhan = true, before10 = true, before5 = true, near = true, quran = true, bedtimeMinutes = 22 * 60, fajrAlarmMinutesBefore = 10),
            reminders = PrayerEvent.entries.toSet(),
        ).copy(iqama = IqamaSettings(alert = true))
        val start = dhuhr.minus(Duration.ofHours(1))
        ReminderScheduler(app).reschedule(busy, start)
        val first = ReminderScheduler.scheduled(app)
        assertEquals("every alert has its own id", first.size, first.map { it.id }.toSet().size)
        assertTrue(first.all { it.id in 1000 until 50_000 })
        // An hour later the plan starts elsewhere; the alerts still there keep their ids.
        ReminderScheduler(app).reschedule(busy, start.plus(Duration.ofHours(1)))
        val later = ReminderScheduler.scheduled(app).associate { it.key to it.id }
        assertTrue(later.isNotEmpty())
        first.filter { it.key in later }.forEach { assertEquals(it.id, later[it.key]) }
        // The Dhuhr Adhan and the Dhuhr iqama are two notifications, not one replacing the other.
        val dhuhrAlerts = first.filter { it.event == PrayerEvent.DHUHR && it.at.atZone(makkah.zoneId).toLocalDate() == thursday }
        assertTrue(dhuhrAlerts.any { it.kind == AlertKind.ADHAN } && dhuhrAlerts.any { it.kind == AlertKind.IQAMA })
        assertEquals(dhuhrAlerts.size, dhuhrAlerts.map { it.id }.toSet().size)
    }

    @Test fun anAlertAndroidHeldBackIsSetAgainOnce() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val s = settings(AlertSettings(adhan = true, before5 = true))
        ReminderScheduler(app).reschedule(s, dhuhr.minus(Duration.ofHours(1)))
        // Five minutes after the Adhan, not delivered yet (inexact): the app opens and rebuilds.
        ReminderScheduler(app).reschedule(s, dhuhr.plus(Duration.ofMinutes(5)))
        val adhan = ReminderScheduler.scheduled(app).single { it.event == PrayerEvent.DHUHR && it.kind == AlertKind.ADHAN && it.at == dhuhr }
        assertTrue(dhuhr in scheduledTimes())
        // "5 minutes until Dhuhr" is no use late.
        assertTrue(ReminderScheduler.scheduled(app).none { it.kind == AlertKind.BEFORE_5 && it.at.isBefore(dhuhr) })
        // Once given, it is not set again.
        assertTrue(ReminderScheduler.firstDelivery(app, adhan.key))
        ReminderScheduler(app).reschedule(s, dhuhr.plus(Duration.ofMinutes(6)))
        assertTrue(ReminderScheduler.scheduled(app).none { it.key == adhan.key })
    }

    @Test fun aLeadBeforeAPrayerIsSetAgainOnlyUntilThatPrayer() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val friday = thursday.plusDays(1)
        val ramadan = generateSequence(thursday) { it.plusDays(1) }.first { HijriCalendar.isRamadan(it, 0) }
        listOf(
            Triple(AlertSettings(friday = true), AlertKind.FRIDAY, calc(friday)[PrayerEvent.DHUHR]!!),
            Triple(AlertSettings(ramadan = true), AlertKind.SUHOOR, calc(ramadan)[PrayerEvent.FAJR]!!),
        ).forEach { (alerts, kind, prayerAt) ->
            val s = settings(alerts)
            ReminderScheduler(app).reschedule(s, prayerAt.minus(Duration.ofHours(1)))
            val lead = ReminderScheduler.scheduled(app).single { it.kind == kind && it.at.isBefore(prayerAt) && it.at.isAfter(prayerAt.minus(Duration.ofHours(1))) }
            // Held back, the prayer not begun yet: set again.
            ReminderScheduler(app).reschedule(s, prayerAt.minus(Duration.ofMinutes(10)))
            assertTrue(kind.name, ReminderScheduler.scheduled(app).any { it.key == lead.key })
            // Once the prayer has begun ("Fajr is at 04:12" after it), no longer.
            ReminderScheduler(app).reschedule(s, prayerAt.plus(Duration.ofMinutes(5)))
            assertTrue(kind.name, ReminderScheduler.scheduled(app).none { it.key == lead.key })
        }
    }

    @Test fun aFajrAlarmAtFajrWithTheFajrAdhanIsOneAlarmPlayingIt() {
        val fajr = calc(thursday.plusDays(1))[PrayerEvent.FAJR]!!
        val now = fajr.minus(Duration.ofHours(3))
        ReminderScheduler(app).reschedule(settings(AlertSettings(adhan = true, fajrAdhan = true, fajrAlarmMinutesBefore = 0)), now)
        val atFajr = ReminderScheduler.scheduled(app).filter { it.at == fajr }
        assertEquals(1, atFajr.size)
        val one = atFajr.single()
        assertEquals(AlertKind.FAJR_ALARM, one.kind)
        assertEquals("fajr_makkah", one.sound)
        assertTrue(one.alarm && one.adhan)
        assertEquals(1, scheduledTimes().count { it == fajr })
        // Fired, the app plays it (as an alarm, until stopped).
        var played: Intent? = null
        ReminderReceiver.startAudio = { _, i -> played = i; true }
        ReminderReceiver().onReceive(app, one.intent(app))
        assertEquals("fajr_makkah", played?.getStringExtra(ReminderScheduler.EXTRA_SOUND))
        assertTrue(played!!.getBooleanExtra(ReminderScheduler.EXTRA_ALARM, false) && played!!.getBooleanExtra(ReminderScheduler.EXTRA_ADHAN, false))
        // Before Fajr the alarm stays its own, with the alarm tone; the Adhan comes at Fajr.
        ReminderScheduler(app).reschedule(settings(AlertSettings(adhan = true, fajrAdhan = true, fajrAlarmMinutesBefore = 10)), now)
        val alarm = ReminderScheduler.scheduled(app).single { it.kind == AlertKind.FAJR_ALARM && it.at == fajr.minus(Duration.ofMinutes(10)) }
        assertNull(alarm.sound)
        assertFalse(alarm.adhan)
        assertEquals(AlertKind.ADHAN, ReminderScheduler.scheduled(app).single { it.at == fajr }.kind)
    }

    @Test fun aLateAdhanIsGivenWithoutIt() {
        var played = 0
        ReminderReceiver.startAudio = { _, _ -> played++; true }
        val nm = app.getSystemService(NotificationManager::class.java)
        fun adhan(at: Instant) = Intent(app, ReminderReceiver::class.java).putExtra(ReminderScheduler.EXTRA_TITLE, "Dhuhr")
            .putExtra(ReminderScheduler.EXTRA_BODY, "Makkah").putExtra(ReminderScheduler.EXTRA_ID, 1234).putExtra(ReminderScheduler.EXTRA_SOUND, "adhan_makkah")
            .putExtra(ReminderScheduler.EXTRA_ADHAN, true).putExtra(ReminderScheduler.EXTRA_KEY, "${at.epochSecond}|ADHAN|DHUHR")
        // Ten minutes late (held back, or set again when the app opened): the notification, without the Adhan.
        ReminderReceiver().onReceive(app, adhan(Instant.now().minus(Duration.ofMinutes(10))))
        assertEquals(0, played)
        val n = shadowOf(nm).getNotification(1234)
        assertEquals(ReminderScheduler.CHANNEL, n.channelId)
        assertEquals(android.app.Notification.CATEGORY_REMINDER, n.category)
        assertNull(n.actions)
        assertTrue(n.extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT).toString().endsWith(app.getString(sa.zood.nearmosque.R.string.adhan_muted_late)))
        // On time: played.
        ReminderReceiver().onReceive(app, adhan(Instant.now().minusSeconds(2)))
        assertEquals(1, played)
    }

    @Test fun anAlertNeverSetIsNotGivenLate() {
        // Turned on five minutes after the Adhan: nothing rings for it now.
        ReminderScheduler(app).reschedule(settings(), dhuhr.plus(Duration.ofMinutes(5)))
        assertTrue(ReminderScheduler.scheduled(app).none { it.at == dhuhr })
        assertTrue(ReminderScheduler.scheduled(app).all { it.at.isAfter(dhuhr) })
    }

    @Test fun theSameAlertIsGivenOnce() {
        ReminderReceiver.startAudio = { _, _ -> false }
        val nm = app.getSystemService(NotificationManager::class.java)
        val alert = Intent(app, ReminderReceiver::class.java).putExtra(ReminderScheduler.EXTRA_TITLE, "Dhuhr").putExtra(ReminderScheduler.EXTRA_BODY, "Makkah")
            .putExtra(ReminderScheduler.EXTRA_ID, 1234).putExtra(ReminderScheduler.EXTRA_KEY, "${dhuhr.epochSecond}|IQAMA|DHUHR")
        ReminderReceiver().onReceive(app, alert)
        assertNotNull(shadowOf(nm).getNotification(1234))
        nm.cancelAll()
        // Its windowed twin, a moment later.
        ReminderReceiver().onReceive(app, alert)
        assertNull(shadowOf(nm).getNotification(1234))
        assertFalse(ReminderScheduler.firstDelivery(app, "${dhuhr.epochSecond}|IQAMA|DHUHR"))
    }

    @Test fun afterARestartTheNextDayIsSetFromTheCopy() {
        val now = dhuhr.minus(Duration.ofHours(1))
        val count = ReminderScheduler(app).reschedule(settings(), now)
        assertTrue(count > 5)
        // The restart clears every alarm.
        shadowOf(am).scheduledAlarms.toList().forEach { a -> a.operation?.let { am.cancel(it) } }
        assertTrue(shadowOf(am).scheduledAlarms.isEmpty())
        val restored = ReminderScheduler.restore(app, now)
        assertTrue(restored in 1 until count)
        val times = scheduledTimes()
        assertTrue(dhuhr in times)
        assertTrue(times.all { it.isAfter(now) && it.isBefore(now.plus(Duration.ofDays(1))) })
    }

    @Test fun theNextFiveAreListed() {
        val now = dhuhr.minus(Duration.ofHours(1))
        ReminderScheduler(app).reschedule(settings(), now)
        val next = ReminderScheduler.upcoming(app, now)
        assertEquals(5, next.size)
        assertEquals(dhuhr, next.first().at)
        assertEquals(next.sortedBy { it.at }, next)
        assertEquals("adhan_makkah", next.first().sound)
        // Without notifications nothing is set, and nothing is listed.
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertEquals(0, ReminderScheduler(app).reschedule(settings(), now))
        assertTrue(ReminderScheduler.upcoming(app, now).isEmpty())
    }

    @Test fun everyAdhanRecordingHasItsLength() {
        listOf("adhan_makkah", "adhan_madinah", "fajr_makkah", "fajr_madinah", "adhan_makkah_short", "adhan_madinah_short").forEach {
            assertNotNull(it, AudioCatalog.durationSeconds(it))
        }
    }

    @Test fun widgetRedrawsWithoutExactAlarmsKeepTheLastWaitShort() {
        val now = dhuhr.minus(Duration.ofHours(2))
        assertEquals(listOf(dhuhr.minus(Duration.ofMinutes(10)), dhuhr.minus(Duration.ofMinutes(3))), PrayerWidgets.hops(dhuhr, now, exact = false))
        assertEquals(listOf(dhuhr.minus(Duration.ofMinutes(3))), PrayerWidgets.hops(dhuhr, dhuhr.minus(Duration.ofMinutes(5)), exact = false))
        assertTrue(PrayerWidgets.hops(dhuhr, now, exact = true).isEmpty())
        // A 10-minute window only when Android's own would be longer.
        assertEquals(600_000L, PrayerWidgets.window(Duration.ofHours(2).toMillis()))
        assertNull(PrayerWidgets.window(Duration.ofMinutes(10).toMillis()))
    }
}
