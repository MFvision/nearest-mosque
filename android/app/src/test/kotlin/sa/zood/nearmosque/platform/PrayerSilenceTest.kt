package sa.zood.nearmosque.platform

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
import sa.zood.nearmosque.R
import sa.zood.nearmosque.core.IqamaSettings
import sa.zood.nearmosque.core.LatLng
import sa.zood.nearmosque.core.Method
import sa.zood.nearmosque.core.PrayerCalculator
import sa.zood.nearmosque.core.PrayerEvent
import sa.zood.nearmosque.core.PrayerSettings
import sa.zood.nearmosque.core.SilenceSettings
import sa.zood.nearmosque.data.AppSettings
import sa.zood.nearmosque.data.PrayerLocation
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** When silence turns on and off, and which alarms are set, at app start and as each alarm fires. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PrayerSilenceTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val calc = PrayerCalculator()
    private val makkah = PrayerLocation("Makkah", LatLng(21.4225, 39.8262), ZoneId.of("Asia/Riyadh"), "SA", PrayerLocation.Source.CITY)
    private val prayer = PrayerSettings(method = Method.UMM_AL_QURA)
    // Thursday 15 October 2026; the next day is a Friday.
    private val thursday = LocalDate.of(2026, 10, 15)
    private val on = SilenceSettings(enabled = true)

    private fun settings(silence: SilenceSettings = on) = AppSettings(makkah, false, prayer, true, emptySet(), "compass", silence = silence)
    private fun adhan(e: PrayerEvent, date: LocalDate = thursday) = calc.schedule(makkah.location, date, makkah.zoneId, prayer)[e]!!
    private fun days(date: LocalDate = thursday) = (-1L..2L).map { calc.schedule(makkah.location, date.plusDays(it), makkah.zoneId, prayer) }
    private fun Instant.min(m: Long) = plusSeconds(m * 60)
    // A silence starts a few seconds after its minute, so an alert at that minute sounds first.
    private fun Instant.start() = plusSeconds(PrayerSilence.ALERT_SECONDS)

    private class FakeDnd(var allowed: Boolean = true) : DndControl {
        val calls = mutableListOf<String>()
        override fun granted() = allowed
        override fun on() { calls += "on" }
        override fun off(byUser: Boolean) { calls += if (byUser) "off-user" else "off" }
        override fun remove() { calls += "remove" }
    }

    private fun alarms() = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.map { Instant.ofEpochMilli(it.triggerAtTime) }.sorted()
    private fun shown() = shadowOf(app.getSystemService(NotificationManager::class.java)).getNotification(PrayerSilence.NOTIFICATION_ID)

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun planAroundAPrayer() {
        val dhuhr = adhan(PrayerEvent.DHUHR)
        val asr = adhan(PrayerEvent.ASR)
        val q = IqamaSettings()
        // Before the iqama: nothing yet; the alarms are this prayer's start and end.
        PrayerSilence.plan(on, days(), q, dhuhr.min(5), null).let {
            assertNull(it.current)
            assertEquals(dhuhr.min(20), it.startAt)
            assertEquals(dhuhr.min(35), it.endAt)
        }
        // During the prayer: on, ending with it; the next start is Asr's iqama.
        PrayerSilence.plan(on, days(), q, dhuhr.min(25), null).let {
            assertEquals(PrayerEvent.DHUHR, it.current?.event)
            assertEquals(asr.min(20), it.startAt)
            assertEquals(dhuhr.min(35), it.endAt)
        }
        // Ended by the user: off until Asr.
        PrayerSilence.plan(on, days(), q, dhuhr.min(25), dhuhr.min(35)).let {
            assertNull(it.current)
            assertEquals(asr.min(20), it.startAt)
            assertEquals(asr.min(35), it.endAt)
        }
        // After the prayer: off, Asr next.
        PrayerSilence.plan(on, days(), q, dhuhr.min(40), null).let {
            assertNull(it.current)
            assertEquals(asr.min(20), it.startAt)
        }
        // From the Adhan.
        assertEquals(dhuhr, PrayerSilence.plan(on.copy(start = SilenceSettings.Start.ADHAN), days(), q, dhuhr.min(-10), null).startAt)
        // Off: nothing.
        assertEquals(PrayerSilence.Plan(null, null, null), PrayerSilence.plan(SilenceSettings(), days(), q, dhuhr, null))
    }

    @Test
    fun appStartDuringAPrayerTurnsOnOnce() {
        val dnd = FakeDnd()
        val s = PrayerSilence(app, dnd)
        val dhuhr = adhan(PrayerEvent.DHUHR)
        assertEquals(PrayerEvent.DHUHR, s.sync(settings(), dhuhr.min(25))?.current?.event)
        assertEquals(listOf("on"), dnd.calls)
        assertEquals(listOf(dhuhr.min(35), adhan(PrayerEvent.ASR).min(20).start()), alarms())
        val n = shown()
        assertNotNull(n)
        n!!
        assertEquals(
            app.getString(R.string.silence_on_title, app.getString(R.string.prayer_dhuhr)),
            n.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString(),
        )
        assertEquals(app.getString(R.string.silence_unmute), n.actions.single().title.toString())
        // Again during the same prayer (the app reopened, another alarm): not turned on a second time, so sound
        // the user turned on stays on.
        s.sync(settings(), dhuhr.min(30))
        assertEquals(listOf("on"), dnd.calls)
    }

    @Test
    fun theEndTurnsOff() {
        val dnd = FakeDnd()
        val s = PrayerSilence(app, dnd)
        val dhuhr = adhan(PrayerEvent.DHUHR)
        val asr = adhan(PrayerEvent.ASR)
        s.sync(settings(), dhuhr.min(25))
        s.sync(settings(), dhuhr.min(35))
        assertEquals(listOf("on", "off"), dnd.calls)
        assertNull(shown())
        assertEquals(listOf(asr.min(20).start(), asr.min(35)), alarms())
        // Asr's start alarm.
        s.sync(settings(), asr.min(20).start())
        assertEquals(listOf("on", "off", "on"), dnd.calls)
    }

    @Test
    fun beforeAPrayerOnlyTheAlarms() {
        val dnd = FakeDnd()
        val dhuhr = adhan(PrayerEvent.DHUHR)
        PrayerSilence(app, dnd).sync(settings(), dhuhr.min(5))
        assertTrue(dnd.calls.isEmpty())
        assertNull(shown())
        assertEquals(listOf(dhuhr.min(20).start(), dhuhr.min(35)), alarms())
    }

    @Test
    fun soundOnNowEndsThisPrayerOnly() {
        val dnd = FakeDnd()
        val s = PrayerSilence(app, dnd)
        val dhuhr = adhan(PrayerEvent.DHUHR)
        val asr = adhan(PrayerEvent.ASR)
        s.sync(settings(), dhuhr.min(25))
        s.unmute(settings(), dhuhr.min(26))
        assertEquals(listOf("on", "off-user"), dnd.calls)
        assertNull(shown())
        assertEquals(listOf(asr.min(20).start(), asr.min(35)), alarms())
        // The app reopened during the same prayer: still off.
        s.sync(settings(), dhuhr.min(30))
        assertEquals(listOf("on", "off-user"), dnd.calls)
        // Asr is silenced as usual.
        s.sync(settings(), asr.min(21))
        assertEquals(listOf("on", "off-user", "on"), dnd.calls)
    }

    @Test
    fun withoutPermissionNothingHappens() {
        val dnd = FakeDnd(allowed = false)
        assertNull(PrayerSilence(app, dnd).sync(settings(), adhan(PrayerEvent.DHUHR).min(25)))
        assertTrue(dnd.calls.isEmpty())
        assertTrue(alarms().isEmpty())
        assertNull(shown())
    }

    @Test
    fun turningTheSettingOffEndsSilenceAndRemovesTheRule() {
        val dnd = FakeDnd()
        val s = PrayerSilence(app, dnd)
        val dhuhr = adhan(PrayerEvent.DHUHR)
        s.sync(settings(), dhuhr.min(25))
        assertNull(s.sync(settings(on.copy(enabled = false)), dhuhr.min(26)))
        assertEquals(listOf("on", "off", "remove"), dnd.calls)
        assertTrue(alarms().isEmpty())
        assertNull(shown())
    }

    @Test
    fun prayerNotChosenIsNotSilenced() {
        val dnd = FakeDnd()
        val dhuhr = adhan(PrayerEvent.DHUHR)
        PrayerSilence(app, dnd).sync(settings(on.with(PrayerEvent.DHUHR, false)), dhuhr.min(25))
        assertTrue(dnd.calls.isEmpty())
        assertEquals(listOf(adhan(PrayerEvent.ASR).min(20).start(), adhan(PrayerEvent.ASR).min(35)), alarms())
    }

    @Test
    fun fridayFromTheAdhan() {
        val dnd = FakeDnd()
        val friday = thursday.plusDays(1)
        val dhuhr = adhan(PrayerEvent.DHUHR, friday)
        val p = PrayerSilence(app, dnd).sync(settings(on.copy(fridayMinutes = 45)), dhuhr.min(1))
        assertEquals(true, p?.current?.friday)
        assertEquals(listOf("on"), dnd.calls)
        assertEquals(dhuhr.min(45), p?.endAt)
    }

    /** With the app's Adhan on, the silence starts when it has played (Friday, and "From the Adhan"), not with it. */
    @Test
    fun neverSilencesItsOwnAdhan() {
        val dnd = FakeDnd()
        val friday = thursday.plusDays(1)
        val dhuhr = adhan(PrayerEvent.DHUHR, friday)
        val withAdhan = settings(on).copy(alerts = sa.zood.nearmosque.core.AlertSettings(adhan = true))
        // The whole Makkah Adhan plays 205 s; the silence waits 5 s more.
        val p = PrayerSilence(app, dnd).sync(withAdhan, dhuhr.min(1))
        assertNull(p?.current)
        assertTrue(dnd.calls.isEmpty())
        assertEquals(dhuhr.plusSeconds(210), p?.startAt)
        val asr = adhan(PrayerEvent.ASR)
        val fromAdhan = withAdhan.copy(silence = on.copy(start = SilenceSettings.Start.ADHAN))
        assertEquals(asr.plusSeconds(210), PrayerSilence(app, FakeDnd()).sync(fromAdhan, asr.min(-5))?.startAt)
        // The short Adhan is over in seconds.
        val short = fromAdhan.copy(alerts = fromAdhan.alerts.copy(shortAdhan = true))
        assertEquals(asr.plusSeconds(17), PrayerSilence(app, FakeDnd()).sync(short, asr.min(-5))?.startAt)
    }

    /** A late Adhan (Android held it back) never rings into the silence: not during it, nor into its start. */
    @Test
    fun aLateAdhanNeverRingsIntoTheSilence() {
        val asr = adhan(PrayerEvent.ASR)
        val key = "${asr.epochSecond}|ADHAN|ASR"
        fun late(now: Instant) = ReminderScheduler.adhanLate(app, key, "adhan_makkah", now)
        val withAdhan = settings(on).copy(alerts = sa.zood.nearmosque.core.AlertSettings(adhan = true))
        // From the iqama: on time, or two minutes late, it plays; six minutes late it does not.
        PrayerSilence(app, FakeDnd()).sync(withAdhan, asr.min(-5))
        assertTrue(!late(asr.plusSeconds(1)) && !late(asr.min(2)))
        assertTrue(late(asr.min(6)))
        // From the Adhan (the silence starts as the Adhan ends): on time it plays, half a minute late it does not.
        PrayerSilence(app, FakeDnd()).sync(withAdhan.copy(silence = on.copy(start = SilenceSettings.Start.ADHAN)), asr.min(-5))
        assertTrue(!late(asr.plusSeconds(1)))
        assertTrue(late(asr.plusSeconds(30)))
        // During a silence (the previous prayer's, still on).
        val dhuhr = adhan(PrayerEvent.DHUHR)
        PrayerSilence(app, FakeDnd()).sync(withAdhan, dhuhr.min(25))
        assertTrue(PrayerSilence.silentBefore(app, dhuhr.min(25), dhuhr.min(25)))
        // Silence off: only the lateness counts.
        PrayerSilence(app, FakeDnd()).sync(settings(SilenceSettings()), asr.min(-5))
        assertTrue(!late(asr.plusSeconds(30)) && !late(asr.min(4)))
    }

    @Test
    fun withoutExactAlarmsStillScheduled() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val dhuhr = adhan(PrayerEvent.DHUHR)
        PrayerSilence(app, FakeDnd()).sync(settings(), dhuhr.min(5))
        assertEquals(listOf(dhuhr.min(20).start(), dhuhr.min(35)), alarms())
    }

    /** Android 8 and 9: priority only if Do Not Disturb was off, and back only if it is still the app's. */
    @Test
    fun olderAndroidLeavesTheUsersDoNotDisturbAlone() {
        val nm = app.getSystemService(NotificationManager::class.java)
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        val dnd = SystemDnd(app, sdk = 28)
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        dnd.on()
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, nm.currentInterruptionFilter)
        dnd.off(byUser = false)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, nm.currentInterruptionFilter)
        // The user's own Do Not Disturb: left on.
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        dnd.on()
        dnd.off(byUser = false)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, nm.currentInterruptionFilter)
        // Changed by the user during the prayer: theirs stays.
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        dnd.on()
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        dnd.off(byUser = false)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_NONE, nm.currentInterruptionFilter)
    }

    /** Android 10 and later: the app's own rule, made again if the user deleted it, removed when the setting is off. */
    @Test
    fun ownRuleOnNewerAndroid() {
        val nm = app.getSystemService(NotificationManager::class.java)
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        val dnd = SystemDnd(app)
        assertTrue(dnd.granted())
        dnd.on()
        val rule = nm.automaticZenRules.values.single()
        assertEquals(SystemDnd.CONDITION, rule.conditionId)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, rule.interruptionFilter)
        assertEquals(app.getString(R.string.silence_section), rule.name)
        dnd.off(byUser = false)
        nm.removeAutomaticZenRule(nm.automaticZenRules.keys.single())
        dnd.on()
        assertEquals(1, nm.automaticZenRules.size)
        dnd.remove()
        assertTrue(nm.automaticZenRules.isEmpty())
        // The user's Do Not Disturb is never touched.
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, nm.currentInterruptionFilter)
    }

    @Test
    fun noAccessNoCrash() {
        val dnd = SystemDnd(app)
        assertEquals(false, dnd.granted())
        dnd.on()
        dnd.off(byUser = true)
        dnd.remove()
    }
}
