package sa.zood.nearmosque.platform

import android.Manifest
import android.app.AlarmManager
import android.app.AutomaticZenRule
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import sa.zood.nearmosque.MainActivity
import sa.zood.nearmosque.NearMosqueApp
import sa.zood.nearmosque.R
import sa.zood.nearmosque.core.DaySchedule
import sa.zood.nearmosque.core.IqamaSettings
import sa.zood.nearmosque.core.PrayerCalculator
import sa.zood.nearmosque.core.PrayerEvent
import sa.zood.nearmosque.core.SilenceSettings
import sa.zood.nearmosque.core.SilenceWindow
import sa.zood.nearmosque.data.AppSettings
import sa.zood.nearmosque.ui.Format
import java.time.Instant
import java.time.ZoneId

/** What silences the phone: the system's Do Not Disturb in the app, a fake in tests. Never throws. */
interface DndControl {
    /** The user allowed the app to change Do Not Disturb. */
    fun granted(): Boolean

    /** Silence starts. */
    fun on()

    /** Silence ends; [byUser] when the user ended it from the notification. */
    fun off(byUser: Boolean)

    /** The setting was turned off: nothing of the app's is left in the system's Do Not Disturb settings. */
    fun remove()
}

/**
 * Do Not Disturb through the app's own automatic rule (Android 10 and later): the rule is listed with the user's
 * Do Not Disturb schedules and only it is turned on and off, so the user's own Do Not Disturb is never changed.
 * Android 8 and 9 have no such rules: there the app turns on priority-only Do Not Disturb if it was off, and turns
 * it off again only if it is still the app's (left alone if the user changed it meanwhile).
 */
class SystemDnd(private val context: Context, private val sdk: Int = Build.VERSION.SDK_INT) : DndControl {
    private val nm = context.getSystemService(NotificationManager::class.java)
    private val prefs = context.getSharedPreferences(PrayerSilence.PREFS, Context.MODE_PRIVATE)

    override fun granted(): Boolean = try {
        nm.isNotificationPolicyAccessGranted
    } catch (e: Exception) {
        false
    }

    override fun on() {
        try {
            if (sdk >= 29) {
                val id = ensureRule() ?: return
                nm.setAutomaticZenRuleState(id, condition(Condition.STATE_TRUE, byUser = false))
            } else if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                prefs.edit().putBoolean(K_LEGACY_SET, true).apply()
            }
        } catch (e: Exception) {
            // No access (revoked meanwhile) or a system that refuses: stay as the user has it.
        }
    }

    override fun off(byUser: Boolean) {
        try {
            if (sdk >= 29) {
                val id = prefs.getString(K_RULE, null) ?: return
                if (nm.getAutomaticZenRule(id) != null) nm.setAutomaticZenRuleState(id, condition(Condition.STATE_FALSE, byUser))
            } else {
                val ours = prefs.getBoolean(K_LEGACY_SET, false)
                prefs.edit().remove(K_LEGACY_SET).apply()
                // Only the filter the app set; if the user changed it since, theirs stays.
                if (ours && nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                }
            }
        } catch (e: Exception) {
        }
    }

    override fun remove() {
        try {
            if (sdk >= 29) prefs.getString(K_RULE, null)?.let { nm.removeAutomaticZenRule(it) }
        } catch (e: Exception) {
        }
        prefs.edit().remove(K_RULE).apply()
    }

    /** The app's rule, made again if the user deleted it. */
    private fun ensureRule(): String? {
        prefs.getString(K_RULE, null)?.let { id -> if (nm.getAutomaticZenRule(id) != null) return id }
        val rule = AutomaticZenRule(
            context.getString(R.string.silence_section), null, ComponentName(context, MainActivity::class.java),
            CONDITION, null, NotificationManager.INTERRUPTION_FILTER_PRIORITY, true,
        )
        val id = nm.addAutomaticZenRule(rule) ?: return null
        prefs.edit().putString(K_RULE, id).apply()
        return id
    }

    private fun condition(state: Int, byUser: Boolean): Condition {
        val summary = context.getString(R.string.silence_section)
        return if (sdk >= 35) {
            Condition(CONDITION, summary, state, if (byUser) Condition.SOURCE_USER_ACTION else Condition.SOURCE_SCHEDULE)
        } else {
            Condition(CONDITION, summary, state)
        }
    }

    companion object {
        val CONDITION: Uri = Uri.parse("nearmosque://silence")
        private const val K_RULE = "rule_id"
        private const val K_LEGACY_SET = "legacy_filter_set"
    }
}

/**
 * Silent during prayer: Do Not Disturb from the Adhan or the iqama until the end of each chosen prayer, from the
 * Friday Adhan for the khutbah; when the app plays the Adhan, from its end (it never silences its own Adhan), and a
 * few seconds after an alert at that minute. Two exact alarms (the next silence's start, and the end of the one under way or
 * the next) wake [SilenceReceiver], which applies the state and sets the next ones. Rebuilt when the app starts,
 * when the settings change, and on boot and time or zone changes ([RescheduleReceiver]). The app turns silence on
 * only when a silence begins, so sound the user turns on during a prayer stays on. Without the user's permission
 * nothing happens.
 */
class PrayerSilence(
    private val context: Context,
    private val dnd: DndControl = SystemDnd(context),
    private val calculator: PrayerCalculator = PrayerCalculator(),
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val alarms = context.getSystemService(AlarmManager::class.java)

    /** What should be the case at a moment. */
    data class Plan(
        /** The silence that should be on now (none if the user ended it). */
        val current: SilenceWindow?,
        /** When the next silence starts. */
        val startAt: Instant?,
        /** When the current silence ends, or else the next one. */
        val endAt: Instant?,
    )

    fun granted(): Boolean = dnd.granted()

    /** Applies the state for [now] and sets the next alarms. Returns the plan, or null when nothing is to be done. */
    fun sync(settings: AppSettings, now: Instant = Instant.now()): Plan? = synchronized(LOCK) {
        try {
            val loc = settings.prayerLocation
            if (!settings.silence.enabled || loc == null || !dnd.granted()) {
                cancelAlarms()
                prefs.edit().remove(K_NEXT).apply()
                if (activeStart() != 0L) end(byUser = false)
                if (!settings.silence.enabled) {
                    prefs.edit().remove(K_SKIP).apply()
                    if (dnd.granted()) dnd.remove()
                }
                return null
            }
            val skip = prefs.getLong(K_SKIP, 0L).takeIf { it > now.toEpochMilli() }?.let(Instant::ofEpochMilli)
            if (skip == null) prefs.edit().remove(K_SKIP).apply()
            val p = plan(settings.silence, days(settings, now), settings.iqama, now, skip, adhanSeconds(settings), ALERT_SECONDS)
            val cur = p.current
            when {
                cur == null -> if (activeStart() != 0L) end(byUser = false)
                // Turned on once per silence: a second time would undo the user turning sound on during it.
                activeStart() != cur.start.toEpochMilli() -> {
                    dnd.on()
                    remember(cur)
                    notifyOn(cur, loc.zoneId)
                }
                // The same silence with a new end (the settings changed during it).
                prefs.getLong(K_END, 0L) != cur.end.toEpochMilli() -> {
                    remember(cur)
                    notifyOn(cur, loc.zoneId)
                }
            }
            schedule(p)
            p
        } catch (e: Exception) {
            null
        }
    }

    /** "Sound on now": ends this silence; the next one starts as usual. */
    fun unmute(settings: AppSettings, now: Instant = Instant.now()) = synchronized(LOCK) {
        try {
            val end = prefs.getLong(K_END, 0L).takeIf { it > now.toEpochMilli() }
                ?: settings.prayerLocation?.let {
                    plan(settings.silence, days(settings, now), settings.iqama, now, null, adhanSeconds(settings), ALERT_SECONDS).current?.end?.toEpochMilli()
                }
            if (end != null) prefs.edit().putLong(K_SKIP, end).apply()
            end(byUser = true)
        } catch (e: Exception) {
        }
        sync(settings, now)
    }

    private fun days(settings: AppSettings, now: Instant): List<DaySchedule> {
        val loc = settings.prayerLocation ?: return emptyList()
        val today = now.atZone(loc.zoneId).toLocalDate()
        return (-1L..2L).map { calculator.schedule(loc.location, today.plusDays(it), loc.zoneId, settings.prayer) }
    }

    private fun activeStart() = prefs.getLong(K_START, 0L)

    private fun remember(w: SilenceWindow) =
        prefs.edit().putLong(K_START, w.start.toEpochMilli()).putLong(K_END, w.end.toEpochMilli()).apply()

    private fun end(byUser: Boolean) {
        dnd.off(byUser)
        prefs.edit().remove(K_START).remove(K_END).apply()
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
        }
    }

    private fun notifyOn(w: SilenceWindow, zone: ZoneId) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        try {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, context.getString(R.string.silence_section), NotificationManager.IMPORTANCE_LOW).apply {
                        setSound(null, null)
                        enableVibration(false)
                        setShowBadge(false)
                    },
                )
            }
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE,
            )
            val unmute = PendingIntent.getBroadcast(
                context, CODE_UNMUTE, Intent(context, SilenceReceiver::class.java).setAction(ACTION_UNMUTE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_tab_prayer)
                .setContentTitle(context.getString(R.string.silence_on_title, context.getString(Format.prayerName(w.event))))
                .setContentText(context.getString(R.string.silence_on_body, Format.time(context, w.end, zone)))
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setContentIntent(open)
                .addAction(0, context.getString(R.string.silence_unmute), unmute)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
        } catch (e: SecurityException) {
        }
    }

    private fun alarmIntent(action: String, code: Int, flags: Int) = PendingIntent.getBroadcast(
        context, code, Intent(context, SilenceReceiver::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or flags,
    )

    private fun cancelAlarms() {
        for ((action, code) in listOf(ACTION_START to CODE_START, ACTION_END to CODE_END)) {
            val pi = alarmIntent(action, code, PendingIntent.FLAG_NO_CREATE) ?: continue
            alarms.cancel(pi)
            pi.cancel()
        }
    }

    private fun schedule(p: Plan) {
        cancelAlarms()
        p.startAt?.let { set(it, alarmIntent(ACTION_START, CODE_START, PendingIntent.FLAG_UPDATE_CURRENT)) }
        p.endAt?.let { set(it, alarmIntent(ACTION_END, CODE_END, PendingIntent.FLAG_UPDATE_CURRENT)) }
        // For the Adhan, which never rings into a silence ([silentBefore]).
        prefs.edit().putLong(K_NEXT, p.startAt?.toEpochMilli() ?: 0L).apply()
    }

    /** Exact when the user allows exact alarms (as the reminders), otherwise as close as Android lets it. */
    private fun set(at: Instant, pi: PendingIntent) {
        val ms = at.toEpochMilli()
        try {
            if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
                return
            }
        } catch (e: SecurityException) {
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
    }

    companion object {
        const val PREFS = "silence"
        const val CHANNEL = "prayer_silence"
        /** Below the reminders' ids (1000 and up). */
        const val NOTIFICATION_ID = 990
        const val ACTION_START = "sa.zood.nearmosque.silence.START"
        const val ACTION_END = "sa.zood.nearmosque.silence.END"
        const val ACTION_UNMUTE = "sa.zood.nearmosque.silence.UNMUTE"
        private const val CODE_START = 0
        private const val CODE_END = 1
        private const val CODE_UNMUTE = 2
        private const val K_START = "active_start"
        private const val K_END = "active_end"
        /** The user ended the silence that ends at this moment (epoch ms). */
        private const val K_SKIP = "skip_until"
        /** When the next silence starts (epoch ms), as the last sync set its alarm. */
        private const val K_NEXT = "next_start"
        private val LOCK = Any()
        /** An alert at the minute a silence starts (a bell, the iqama) sounds first. */
        const val ALERT_SECONDS = 10L

        /** How long the app's own Adhan plays at each prayer, and a little more: the silence starts after it. */
        fun adhanSeconds(settings: AppSettings): (PrayerEvent) -> Long = { e ->
            if (settings.alerts.adhanPlays(e)) (AudioCatalog.durationSeconds(AudioCatalog.adhan(e, settings.alerts)) ?: 250L) + 5L else 0L
        }

        /** What should be on at [now], and the next alarms. A silence the user ended (it began before [skipUntil]) stays off. */
        fun plan(
            silence: SilenceSettings, days: List<DaySchedule>, iqama: IqamaSettings, now: Instant, skipUntil: Instant?,
            adhanSeconds: (PrayerEvent) -> Long = { 0L }, alertSeconds: Long = 0L,
        ): Plan {
            val windows = silence.windows(days, iqama, now, adhanSeconds, alertSeconds)
            val current = windows.firstOrNull { !it.start.isAfter(now) }?.takeUnless { skipUntil != null && it.start.isBefore(skipUntil) }
            val next = windows.firstOrNull { it.start.isAfter(now) }
            return Plan(current, next?.start, (current ?: next)?.end)
        }

        /**
         * The app's own silence is on at [now] or starts before [until]: an Adhan still playing then is not started.
         * Read after the first unlock only (the app silences nothing before it).
         */
        fun silentBefore(context: Context, until: Instant, now: Instant = Instant.now()): Boolean {
            if (!NearMosqueApp.unlocked(context)) return false
            return try {
                val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val on = p.getLong(K_START, 0L) != 0L && p.getLong(K_END, 0L) > now.toEpochMilli()
                val next = p.getLong(K_NEXT, 0L)
                on || (next != 0L && next < until.toEpochMilli())
            } catch (e: Exception) {
                false
            }
        }

        /** Opens the system screen where the user lets the app change Do Not Disturb. */
        fun permissionIntent(): Intent =
            Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/** A silence starts or ends, or the user taps "Sound on now". */
class SilenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? NearMosqueApp ?: return
        val pending = goAsync()
        scope.launch {
            try {
                val settings = app.container.settings.settings.first()
                val silence = PrayerSilence(app)
                if (intent.action == PrayerSilence.ACTION_UNMUTE) silence.unmute(settings) else silence.sync(settings)
            } catch (e: Exception) {
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
