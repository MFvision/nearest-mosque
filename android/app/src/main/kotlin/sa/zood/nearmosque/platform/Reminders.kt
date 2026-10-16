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
import sa.zood.nearmosque.core.DaySchedule
import sa.zood.nearmosque.core.PlannedAlert
import sa.zood.nearmosque.core.HijriCalendar
import sa.zood.nearmosque.core.PrayerCalculator
import sa.zood.nearmosque.core.PrayerEvent
import sa.zood.nearmosque.core.VoiceLanguage
import sa.zood.nearmosque.data.AppSettings
import sa.zood.nearmosque.ui.Format
import java.time.Instant

/**
 * Local prayer reminders: a bounded horizon (7 days) of one-shot alarms, rebuilt whenever settings
 * change, the app opens, the device reboots, the time/zone changes, or one of them fires. The Adhan and spoken
 * reminders are played by [PrayerAudioService] while their notification shows, then it stops; the iqama is a plain
 * notification on the reminder channel, never the Adhan. Respects
 * notification and exact-alarm permissions, the ringer and Do Not Disturb.
 */
class ReminderScheduler(private val context: Context, private val calculator: PrayerCalculator = PrayerCalculator()) {
    private val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun notificationsAllowed(): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun exactAlarmsAllowed(): Boolean = exactAlarmsAllowed(context)

    /** Returns the number of reminders and alarms scheduled. */
    fun reschedule(settings: AppSettings, now: Instant = Instant.now()): Int {
        cancelAll()
        val loc = settings.prayerLocation ?: return 0
        if (!notificationsAllowed()) return 0
        ensureChannel()
        val (days, plan) = plan(settings, now, calculator) ?: return 0
        val voice = settings.alerts.voice ?: VoiceLanguage.forInterface(Format.languageCode(context))
        plan.forEachIndexed { code, a ->
            val name = context.getString(Format.prayerName(a.event))
            val prayerAt = days.firstOrNull { it.date == a.date }?.get(a.event) ?: a.at
            val time = Format.time(context, prayerAt, loc.zoneId)
            val minutes = ((prayerAt.epochSecond - a.at.epochSecond) / 60).toInt()
            val (title, body) = when (a.kind) {
                AlertKind.AT_PRAYER if a.event == PrayerEvent.SUNRISE -> context.getString(R.string.notif_sunrise_title) to context.getString(R.string.reminder_body, loc.name, time)
                AlertKind.AT_PRAYER, AlertKind.FAJR_ALARM, AlertKind.ADHAN -> context.getString(R.string.reminder_title, name) to context.getString(R.string.reminder_body, loc.name, time)
                AlertKind.BEFORE -> context.getString(R.string.reminder_before_title, name, settings.alerts.minutesBefore) to context.getString(R.string.reminder_body, loc.name, time)
                AlertKind.BEFORE_10, AlertKind.BEFORE_5 -> context.getString(R.string.notif_minutes_until, minutes, name) to context.getString(R.string.reminder_body, loc.name, time)
                AlertKind.NEAR -> context.getString(R.string.notif_near_title, name) to context.getString(R.string.reminder_body, loc.name, time)
                AlertKind.FRIDAY -> context.getString(R.string.reminder_friday_title) to context.getString(R.string.reminder_friday_body, time)
                AlertKind.SUHOOR -> context.getString(R.string.reminder_suhoor_title) to context.getString(R.string.reminder_suhoor_body, time)
                AlertKind.IFTAR -> context.getString(R.string.reminder_iftar_title) to context.getString(R.string.reminder_body, loc.name, time)
                AlertKind.SALAWAT -> context.getString(R.string.notif_salawat_title) to context.getString(R.string.salawat_note)
                AlertKind.QURAN -> context.getString(R.string.notif_quran_title) to context.getString(R.string.notif_quran_body)
                AlertKind.SLEEP -> context.getString(R.string.notif_sleep_title) to context.getString(R.string.alerts_sleep_note)
                AlertKind.KAHF -> context.getString(R.string.notif_kahf_title) to context.getString(R.string.alerts_kahf_note)
                AlertKind.IQAMA -> context.getString(R.string.notif_iqama_title, name) to context.getString(R.string.notif_iqama_body)
            }
            val sound = AudioCatalog.sound(a.kind, a.event, settings.alerts, voice)
            val alarm = a.kind == AlertKind.FAJR_ALARM
            val pi = PendingIntent.getBroadcast(
                context, code,
                Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_BODY, body)
                    .putExtra(EXTRA_ID, NOTIFICATION_ID_BASE + code).putExtra(EXTRA_ALARM, alarm).putExtra(EXTRA_SOUND, sound)
                    .putExtra(EXTRA_ADHAN, a.kind == AlertKind.ADHAN),
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
        /** The prayer days of the horizon and the alerts planned on them; null without a place. */
        fun plan(settings: AppSettings, now: Instant, calculator: PrayerCalculator = PrayerCalculator()): Pair<List<DaySchedule>, List<PlannedAlert>>? {
            val loc = settings.prayerLocation ?: return null
            val today = now.atZone(loc.zoneId).toLocalDate()
            val days = (0 until HORIZON_DAYS).map { calculator.schedule(loc.location, today.plusDays(it.toLong()), loc.zoneId, settings.prayer) }
            val plan = AlertPlanner.plan(days, now, settings.reminders, settings.alerts, settings.iqama) {
                HijriCalendar.isRamadan(it, settings.prayer.hijriAdjustmentDays)
            }.take(MAX_ALERTS)
            return days to plan
        }

        /**
         * Whether these settings ring or act at a set minute: a reminder, the Adhan, an iqama alert, the Fajr alarm or
         * silence during prayer. Those need exact alarms to be on time.
         */
        fun wantsTimedAlerts(settings: AppSettings, now: Instant = Instant.now()): Boolean =
            settings.silence.enabled || plan(settings, now)?.second?.isNotEmpty() == true

        /** Exact alarms are always allowed below Android 12; from 12 the user allows "Alarms & reminders". */
        fun exactAlarmsAllowed(context: Context): Boolean =
            Build.VERSION.SDK_INT < 31 || (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()

        /** The system page that allows exact alarms for this app (Android 12+), or null where there is none. */
        fun exactAlarmSettingsIntent(context: Context): Intent? =
            if (Build.VERSION.SDK_INT < 31) {
                null
            } else {
                Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:" + context.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        /**
         * The channel for an Adhan that [PrayerAudioService] could not play (Android refused to start it, e.g. from an
         * inexact alarm on Android 12+): the notification itself plays the recording. One channel per recording, since a
         * channel's sound is fixed; the sound is referenced by name so it survives app updates. Null when the recording
         * is not in the app (then the reminder channel's sound is used).
         */
        fun adhanChannel(context: Context, sound: String): String? {
            if (AudioCatalog.rawId(context, sound) == 0) return null
            val id = "adhan_sound_$sound"
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(id) == null) {
                val source = sa.zood.nearmosque.core.AdhanSource.entries.firstOrNull { sound.contains(it.name.lowercase()) }
                val place = when (source) {
                    sa.zood.nearmosque.core.AdhanSource.MADINAH -> context.getString(R.string.source_madinah)
                    else -> context.getString(R.string.source_makkah)
                }
                val name = when {
                    sound.startsWith("fajr_") -> context.getString(R.string.adhan_fajr) + " · " + place
                    sound.endsWith("_short") -> context.getString(R.string.adhan_playing, place) + " · " + context.getString(R.string.adhan_short)
                    else -> context.getString(R.string.adhan_playing, place)
                }
                nm.createNotificationChannel(
                    NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
                        setSound(
                            android.net.Uri.parse("android.resource://${context.packageName}/raw/$sound"),
                            android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                        )
                        enableVibration(true)
                    },
                )
            }
            return id
        }

        const val HORIZON_DAYS = 7
        const val MAX_ALERTS = 120
        const val CHANNEL = "prayer_reminders"
        const val ALARM_CHANNEL = "fajr_alarm"
        const val EXTRA_ALARM = "alarm"
        const val ACTION_STOP = "sa.zood.nearmosque.alarm.STOP"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_ID = "id"
        /** Notification ids start here (a foreground service may not use 0). */
        const val NOTIFICATION_ID_BASE = 1000
        /** A recording in res/raw (AudioCatalog) played by PrayerAudioService; absent = the channel's sound. */
        const val EXTRA_SOUND = "sound"
        const val EXTRA_ADHAN = "adhan"
    }
}

class ReminderReceiver : BroadcastReceiver() {
    companion object {
        /** Starts the Adhan/voice player; replaced in tests to act as if Android refused the foreground service. */
        internal var startAudio: (Context, Intent) -> Boolean = PrayerAudioService::start
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ReminderScheduler.ACTION_STOP) {
            NotificationManagerCompat.from(context).cancel(intent.getIntExtra(ReminderScheduler.EXTRA_ID, 0))
            return
        }
        val alarm = intent.getBooleanExtra(ReminderScheduler.EXTRA_ALARM, false)
        val id = intent.getIntExtra(ReminderScheduler.EXTRA_ID, 0)
        val sound = intent.getStringExtra(ReminderScheduler.EXTRA_SOUND)
        // The Adhan and the spoken reminders: the app plays the recording (with Stop on the notification).
        if (!alarm && sound != null && startAudio(context, intent)) {
            RescheduleReceiver.rescheduleAsync(context, goAsync())
            return
        }
        // Android refused the player (e.g. Android 12+ after an inexact alarm): an Adhan still sounds, through a
        // notification channel whose sound is that recording; a spoken reminder falls back to the reminder sound.
        val adhanChannel = if (!alarm && sound != null && intent.getBooleanExtra(ReminderScheduler.EXTRA_ADHAN, false)) {
            runCatching { ReminderScheduler.adhanChannel(context, sound) }.getOrNull()
        } else {
            null
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, if (alarm) ReminderScheduler.ALARM_CHANNEL else adhanChannel ?: ReminderScheduler.CHANNEL)
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

/** Boot, time, zone and exact-alarm-permission changes rebuild the bounded schedule and the prayer silences. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = rescheduleAsync(context, goAsync())

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun rescheduleAsync(context: Context, pending: PendingResult?) {
            val app = context.applicationContext as? NearMosqueApp ?: run { pending?.finish(); return }
            scope.launch {
                try {
                    val settings = app.container.settings.settings.first()
                    runCatching { ReminderScheduler(app).reschedule(settings) }
                    PrayerSilence(app).sync(settings)
                } finally {
                    pending?.finish()
                }
            }
        }
    }
}
