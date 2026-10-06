package sa.zood.nearmosque.platform

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import sa.zood.nearmosque.core.AlertKind
import sa.zood.nearmosque.core.AlertPlanner
import sa.zood.nearmosque.core.HijriCalendar
import sa.zood.nearmosque.core.PrayerCalculator
import sa.zood.nearmosque.core.PrayerEvent
import sa.zood.nearmosque.data.AppSettings
import sa.zood.nearmosque.ui.Format
import java.time.Instant

/**
 * Local prayer reminders: a bounded horizon (7 days) of one-shot alarms, rebuilt whenever settings
 * change, the app opens, the device reboots, or the time/zone changes. Nothing runs in the background
 * otherwise, and no audio is kept alive. Respects notification and exact-alarm permissions.
 */
class ReminderScheduler(private val context: Context, private val calculator: PrayerCalculator = PrayerCalculator()) {
    private val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun notificationsAllowed(): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun exactAlarmsAllowed(): Boolean = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    /** Returns the number of reminders and alarms scheduled. */
    fun reschedule(settings: AppSettings, now: Instant = Instant.now()): Int {
        cancelAll()
        val loc = settings.prayerLocation ?: return 0
        if (!notificationsAllowed()) return 0
        ensureChannel()
        val today = now.atZone(loc.zoneId).toLocalDate()
        val days = (0 until HORIZON_DAYS).map { calculator.schedule(loc.location, today.plusDays(it.toLong()), loc.zoneId, settings.prayer) }
        val plan = AlertPlanner.plan(days, now, settings.reminders, settings.alerts) {
            HijriCalendar.isRamadan(it, settings.prayer.hijriAdjustmentDays)
        }.take(MAX_ALERTS)
        plan.forEachIndexed { code, a ->
            val name = context.getString(Format.prayerName(a.event))
            val prayerAt = days.firstOrNull { it.date == a.date }?.get(a.event) ?: a.at
            val time = Format.time(context, prayerAt, loc.zoneId)
            val (title, body) = when (a.kind) {
                AlertKind.AT_PRAYER, AlertKind.FAJR_ALARM -> context.getString(R.string.reminder_title, name) to context.getString(R.string.reminder_body, loc.name, time)
                AlertKind.BEFORE -> context.getString(R.string.reminder_before_title, name, settings.alerts.minutesBefore) to context.getString(R.string.reminder_body, loc.name, time)
                AlertKind.FRIDAY -> context.getString(R.string.reminder_friday_title) to context.getString(R.string.reminder_friday_body, time)
                AlertKind.SUHOOR -> context.getString(R.string.reminder_suhoor_title) to context.getString(R.string.reminder_suhoor_body, time)
                AlertKind.IFTAR -> context.getString(R.string.reminder_iftar_title) to context.getString(R.string.reminder_body, loc.name, time)
            }
            val alarm = a.kind == AlertKind.FAJR_ALARM
            val pi = PendingIntent.getBroadcast(
                context, code,
                Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_BODY, body)
                    .putExtra(EXTRA_ID, code).putExtra(EXTRA_ALARM, alarm),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val at = a.at.toEpochMilli()
            when {
                // The Fajr alarm is a real alarm clock (shown in the status bar, exempt from Doze).
                alarm && exactAlarmsAllowed() -> {
                    val show = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
                    alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
                }
                exactAlarmsAllowed() -> alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                else -> alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
        return plan.size
    }

    private fun cancelAll() {
        for (code in 0 until MAX_ALERTS) {
            val pi = PendingIntent.getBroadcast(
                context, code, Intent(context, ReminderReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
            ) ?: continue
            alarms.cancel(pi)
            pi.cancel()
        }
    }

    private fun ensureChannel() {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.prayer_section), NotificationManager.IMPORTANCE_HIGH))
        }
        if (nm.getNotificationChannel(ALARM_CHANNEL) == null) {
            // The Fajr alarm: the phone's alarm sound, played as an alarm (so alarm volume and Do Not Disturb rules for alarms apply).
            nm.createNotificationChannel(
                NotificationChannel(ALARM_CHANNEL, context.getString(R.string.alerts_fajr_alarm), NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(
                        android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM),
                        android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                    )
                    enableVibration(true)
                    setBypassDnd(true)
                },
            )
        }
    }

    companion object {
        const val HORIZON_DAYS = 7
        const val MAX_ALERTS = 120
        const val CHANNEL = "prayer_reminders"
        const val ALARM_CHANNEL = "fajr_alarm"
        const val EXTRA_ALARM = "alarm"
        const val ACTION_STOP = "sa.zood.nearmosque.alarm.STOP"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_ID = "id"
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ReminderScheduler.ACTION_STOP) {
            NotificationManagerCompat.from(context).cancel(intent.getIntExtra(ReminderScheduler.EXTRA_ID, 0))
            return
        }
        val alarm = intent.getBooleanExtra(ReminderScheduler.EXTRA_ALARM, false)
        val id = intent.getIntExtra(ReminderScheduler.EXTRA_ID, 0)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, if (alarm) ReminderScheduler.ALARM_CHANNEL else ReminderScheduler.CHANNEL)
            .setSmallIcon(R.drawable.ic_tab_prayer)
            .setContentTitle(intent.getStringExtra(ReminderScheduler.EXTRA_TITLE))
            .setContentText(intent.getStringExtra(ReminderScheduler.EXTRA_BODY))
            .setCategory(if (alarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
        if (alarm) {
            val stop = PendingIntent.getBroadcast(
                context, 50_000 + id,
                Intent(context, ReminderReceiver::class.java).setAction(ReminderScheduler.ACTION_STOP).putExtra(ReminderScheduler.EXTRA_ID, id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.setPriority(NotificationCompat.PRIORITY_MAX)
                .addAction(0, context.getString(R.string.alarm_stop), stop)
                .setDeleteIntent(stop)
                .setFullScreenIntent(open, true)
        }
        val n = builder.build()
        // An alarm keeps ringing until it is stopped or opened.
        if (alarm) n.flags = n.flags or android.app.Notification.FLAG_INSISTENT
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(id, n)
        }
        RescheduleReceiver.rescheduleAsync(context, goAsync())
    }
}

/** Boot, time, zone and exact-alarm-permission changes rebuild the bounded schedule. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = rescheduleAsync(context, goAsync())

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun rescheduleAsync(context: Context, pending: PendingResult?) {
            val app = context.applicationContext as NearMosqueApp
            scope.launch {
                try {
                    ReminderScheduler(app).reschedule(app.container.settings.settings.first())
                } finally {
                    pending?.finish()
                }
            }
        }
    }
}
