package sa.zood.nearmosque.platform

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import sa.zood.nearmosque.MainActivity
import sa.zood.nearmosque.NearMosqueApp
import sa.zood.nearmosque.R
import sa.zood.nearmosque.core.AlertKind
import sa.zood.nearmosque.core.AlertPlanner
import sa.zood.nearmosque.core.AlertSettings
import sa.zood.nearmosque.core.DaySchedule
import sa.zood.nearmosque.core.PlannedAlert
import sa.zood.nearmosque.core.HijriCalendar
import sa.zood.nearmosque.core.PrayerCalculator
import sa.zood.nearmosque.core.PrayerEvent
import sa.zood.nearmosque.core.VoiceLanguage
import sa.zood.nearmosque.data.AppSettings
import sa.zood.nearmosque.ui.Format
import java.time.Duration
import java.time.Instant

/**
 * Local prayer reminders: a bounded horizon (7 days) of one-shot alarms, rebuilt whenever settings
 * change, the app opens, the device reboots or the app is updated, the time/zone changes, or one of them fires. The
 * Adhan and spoken reminders are played by [PrayerAudioService] while their notification shows, then it stops (the
 * Adhan as an alarm, so it rings on silent too); the iqama is a plain notification on the reminder channel, never the
 * Adhan. Respects notification and exact-alarm permissions. Each rebuild keeps a copy of what it set ([ScheduledAlert]):
 * for Check my alerts, to set again an alert Android had not delivered yet, and to set the next day's alarms again
 * before the first unlock after a restart ([LockedBootReceiver]).
 */
class ReminderScheduler(private val context: Context, private val calculator: PrayerCalculator = PrayerCalculator()) {
    private val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun notificationsAllowed(): Boolean = notificationsAllowed(context)

    fun exactAlarmsAllowed(): Boolean = exactAlarmsAllowed(context)

    /** Returns the number of reminders and alarms scheduled. One rebuild at a time, so two never mix their alarms. */
    fun reschedule(settings: AppSettings, now: Instant = Instant.now()): Int = synchronized(LOCK) {
        val before = scheduled(context).map { it.key }.toSet()
        cancelAll()
        val loc = settings.prayerLocation
        val planned = if (notificationsAllowed()) plan(settings, now.minus(LATE), calculator) else null
        if (loc == null || planned == null) {
            keep(context, emptyList())
            return 0
        }
        ensureChannel()
        val (days, all) = planned
        val delivered = delivered(context)
        // Due already but not delivered yet (without exact alarms Android may hold one for up to an hour): set again,
        // if this app set it and it still makes sense late. A countdown ("5 minutes until …") does not.
        val plan = all.filter { a ->
            a.at.isAfter(now) || (key(a) in before && key(a) !in delivered && !a.at.isBefore(now.minus(lateness(a.kind))))
        }
        // A Fajr alarm at the Fajr time with the Fajr Adhan on is one alarm that plays the Adhan, not two sounds at once.
        val fajrAlarms = plan.filter { it.kind == AlertKind.FAJR_ALARM }.map { it.event to it.at }.toSet()
        val adhans = plan.filter { it.kind == AlertKind.ADHAN }.map { it.event to it.at }.toSet()
        val voice = settings.alerts.voice ?: VoiceLanguage.forInterface(Format.languageCode(context))
        val set = plan.filterNot { it.kind == AlertKind.ADHAN && (it.event to it.at) in fajrAlarms }.mapIndexed { code, a ->
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
            val sound = if (a.kind == AlertKind.FAJR_ALARM && (a.event to a.at) in adhans) {
                AudioCatalog.adhan(a.event, settings.alerts)
            } else {
                AudioCatalog.sound(a.kind, a.event, settings.alerts, voice)
            }
            ScheduledAlert(code, a.at, a.kind, a.event, title, body, notificationId(a), sound)
        }
        val exact = exactAlarmsAllowed()
        set.forEach { arm(context, alarms, it, exact) }
        keep(context, set)
        return set.size
    }

    private fun cancelAll() {
        for (code in (0 until MAX_ALERTS) + (WINDOW_CODE until WINDOW_CODE + MAX_ALERTS)) {
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

    /** One alert as it was set: what its alarm carries, kept to show it and to set it again. */
    data class ScheduledAlert(
        /** The alarm's request code (its place in the plan). */
        val code: Int,
        val at: Instant,
        val kind: AlertKind,
        val event: PrayerEvent,
        val title: String,
        val body: String,
        /** The notification id, from what the alert is (the same after a rebuild). */
        val id: Int,
        /** A recording in res/raw (AudioCatalog), or null for the channel's sound (the Fajr alarm: the alarm tone). */
        val sound: String?,
    ) {
        val alarm: Boolean get() = kind == AlertKind.FAJR_ALARM
        /** The Adhan plays, as an alarm: the Adhan itself, or a Fajr alarm at the Fajr time with the Fajr Adhan on. */
        val adhan: Boolean get() = kind == AlertKind.ADHAN || (alarm && sound != null)
        val key: String get() = key(at, kind, event)

        fun intent(context: Context): Intent = Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_BODY, body)
            .putExtra(EXTRA_ID, id).putExtra(EXTRA_ALARM, alarm).putExtra(EXTRA_SOUND, sound).putExtra(EXTRA_ADHAN, adhan).putExtra(EXTRA_KEY, key)
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

        /** Whether anything is set to alert (a reminder, the Adhan, an iqama alert, the Fajr alarm): all need notifications. */
        fun wantsAlerts(settings: AppSettings, now: Instant = Instant.now()): Boolean =
            plan(settings, now)?.second?.isNotEmpty() == true

        /**
         * Whether these settings ring or act at a set minute: a reminder, the Adhan, an iqama alert, the Fajr alarm or
         * silence during prayer. Those need exact alarms to be on time.
         */
        fun wantsTimedAlerts(settings: AppSettings, now: Instant = Instant.now()): Boolean =
            settings.silence.enabled || wantsAlerts(settings, now)

        fun notificationsAllowed(context: Context): Boolean =
            (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
                NotificationManagerCompat.from(context).areNotificationsEnabled()

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

        /** Opens the page that allows exact alarms; some builds lack it, and the app's own page has "Alarms & reminders". */
        fun openExactAlarmSettings(context: Context) {
            val intent = exactAlarmSettingsIntent(context) ?: return
            try {
                context.startActivity(intent)
            } catch (e: android.content.ActivityNotFoundException) {
                runCatching {
                    context.startActivity(
                        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.data).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }

        /** The app's notification settings (to turn notifications back on). */
        fun openNotificationSettings(context: Context) {
            val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (e: android.content.ActivityNotFoundException) {
                runCatching {
                    context.startActivity(
                        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + context.packageName))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }

        /**
         * The notification id of an alert, from what it is (its day, kind and prayer), not its place in the plan: each
         * stays in the shade until the user clears it, and keeps its id when the plan is rebuilt. At most 40,989.
         */
        fun notificationId(a: PlannedAlert): Int =
            NOTIFICATION_ID_BASE + Math.floorMod(a.date.toEpochDay(), 400L).toInt() * 100 + a.kind.ordinal * 6 + a.event.ordinal

        fun key(a: PlannedAlert) = key(a.at, a.kind, a.event)

        private fun key(at: Instant, kind: AlertKind, event: PrayerEvent) = "${at.epochSecond}|${kind.name}|${event.name}"

        /**
         * How late an alert Android held back is still worth giving: a countdown not at all, the iqama briefly, a lead
         * before a prayer (Friday, suhoor) until that prayer begins. A late Adhan is given without it ([adhanLate]).
         */
        private fun lateness(kind: AlertKind): Duration = when (kind) {
            AlertKind.BEFORE, AlertKind.BEFORE_10, AlertKind.BEFORE_5, AlertKind.NEAR -> Duration.ZERO
            AlertKind.IQAMA -> Duration.ofMinutes(15)
            AlertKind.FRIDAY -> Duration.ofMinutes(AlertSettings.FRIDAY_LEAD_MIN.toLong())
            AlertKind.SUHOOR -> Duration.ofMinutes(AlertSettings.SUHOOR_LEAD_MIN.toLong())
            else -> LATE
        }

        /**
         * An Adhan arriving at [now] is not played: it is more than [ADHAN_LATE] after its time (Android held it back,
         * or it was set again late), or it would still be playing when the app's own silence during prayer begins.
         */
        fun adhanLate(context: Context, key: String?, sound: String, now: Instant = Instant.now()): Boolean {
            val at = key?.substringBefore('|')?.toLongOrNull()
            if (at != null && now.epochSecond - at > ADHAN_LATE.seconds) return true
            return PrayerSilence.silentBefore(context, now.plusSeconds(AudioCatalog.durationSeconds(sound) ?: 250L), now)
        }

        /**
         * The channel for an Adhan that [PrayerAudioService] could not play (Android refused to start it, e.g. from an
         * inexact alarm on Android 12+): the notification itself plays the recording, as an alarm (alarm volume; it
         * rings on silent and vibrate, and through Do Not Disturb unless alarms are blocked there). One channel per
         * recording, since a channel's sound is fixed; the sound is referenced by name so it survives app updates. Null
         * when the recording is not in the app (then the reminder channel's sound is used).
         */
        fun adhanChannel(context: Context, sound: String): String? {
            if (AudioCatalog.rawId(context, sound) == 0) return null
            val id = "adhan_alarm_$sound"
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(id) == null) {
                // The earlier channels played it as a notification, which silent mode mutes.
                nm.notificationChannels.filter { it.id.startsWith("adhan_sound_") }.forEach { nm.deleteNotificationChannel(it.id) }
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
                            android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                        )
                        enableVibration(true)
                        setBypassDnd(true)
                    },
                )
            }
            return id
        }

        /** The next alerts set, soonest first, from the copy the last rebuild kept. */
        fun upcoming(context: Context, now: Instant = Instant.now(), count: Int = 5): List<ScheduledAlert> =
            scheduled(context).filter { it.at.isAfter(now) }.sortedBy { it.at }.take(count)

        /**
         * The alert with [key] arrives: true the first time, false when it has been given already (its windowed twin,
         * or set again after Android held it back). Kept for two days.
         */
        fun firstDelivery(context: Context, key: String, now: Instant = Instant.now()): Boolean = synchronized(DELIVERED_LOCK) {
            val p = prefs(context)
            val seen = p.getStringSet(K_DELIVERED, null).orEmpty()
            if (key in seen) return false
            val cutoff = now.minus(Duration.ofDays(2)).epochSecond
            p.edit().putStringSet(K_DELIVERED, seen.filter { (it.substringBefore('|').toLongOrNull() ?: 0L) > cutoff }.toSet() + key).commit()
            return true
        }

        /** A restart cleared every alarm: alerts that were due before it are not given late. */
        fun afterRestart(context: Context, now: Instant = Instant.now()) = synchronized(LOCK) {
            val booted = now.minusMillis(SystemClock.elapsedRealtime())
            keep(context, scheduled(context).filter { !it.at.isBefore(booted) })
        }

        /**
         * Before the first unlock after a restart, when the settings cannot be read yet: the alarms of the next day
         * are set again from the copy the last rebuild kept, with the same request codes, so the rebuild after the
         * unlock replaces them.
         */
        fun restore(context: Context, now: Instant = Instant.now()): Int = synchronized(LOCK) {
            afterRestart(context, now)
            val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val exact = exactAlarmsAllowed(context)
            val next = scheduled(context).filter { it.at.isAfter(now) && it.at.isBefore(now.plus(Duration.ofDays(1))) }
            next.forEach { arm(context, alarms, it, exact) }
            return next.size
        }

        private fun arm(context: Context, alarms: AlarmManager, a: ScheduledAlert, exact: Boolean) {
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val pi = PendingIntent.getBroadcast(context, a.code, a.intent(context), flags)
            val at = a.at.toEpochMilli()
            try {
                when {
                    // The Fajr alarm is a real alarm clock (shown in the status bar, exempt from Doze).
                    a.alarm && exact -> {
                        val show = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
                        alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
                    }
                    exact -> alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                    else -> {
                        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                        // That one may come up to an hour late; a twin with a 10-minute window (the shortest Android
                        // allows without the permission) is on time to within 10 minutes while the phone is awake.
                        // Whichever comes first is given ([firstDelivery]).
                        alarms.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, PendingIntent.getBroadcast(context, WINDOW_CODE + a.code, a.intent(context), flags))
                    }
                }
            } catch (e: SecurityException) {
                // Exact alarms were turned off meanwhile.
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }

        /** Kept where it can be read before the first unlock after a restart (the alerts' times and texts). */
        private fun prefs(context: Context): SharedPreferences =
            context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private fun delivered(context: Context): Set<String> = prefs(context).getStringSet(K_DELIVERED, null).orEmpty()

        /** What the last rebuild set. */
        fun scheduled(context: Context): List<ScheduledAlert> = runCatching {
            val a = JSONArray(prefs(context).getString(K_SET, null) ?: return emptyList())
            (0 until a.length()).mapNotNull { i ->
                val o = a.getJSONObject(i)
                runCatching {
                    ScheduledAlert(
                        o.getInt("code"), Instant.ofEpochMilli(o.getLong("at")), AlertKind.valueOf(o.getString("kind")),
                        PrayerEvent.valueOf(o.getString("event")), o.getString("title"), o.getString("body"), o.getInt("id"),
                        if (o.has("sound")) o.getString("sound") else null,
                    )
                }.getOrNull()
            }
        }.getOrDefault(emptyList())

        private fun keep(context: Context, set: List<ScheduledAlert>) {
            val a = JSONArray()
            set.forEach { s ->
                a.put(
                    JSONObject().put("code", s.code).put("at", s.at.toEpochMilli()).put("kind", s.kind.name).put("event", s.event.name)
                        .put("title", s.title).put("body", s.body).put("id", s.id).apply { s.sound?.let { put("sound", it) } },
                )
            }
            prefs(context).edit().putString(K_SET, a.toString()).commit()
        }

        const val HORIZON_DAYS = 7
        const val MAX_ALERTS = 120
        /** The request codes of the windowed twins (without exact alarms). */
        private const val WINDOW_CODE = 1000
        private const val WINDOW_MS = 10 * 60_000L
        /** Android delivers an inexact alarm at most an hour late. */
        private val LATE: Duration = Duration.ofHours(1)
        /** Later than this the Adhan is not played (it would ring into the iqama, or in the mosque on silent). */
        private val ADHAN_LATE: Duration = Duration.ofMinutes(5)
        private val LOCK = Any()
        private val DELIVERED_LOCK = Any()
        private const val PREFS = "reminders"
        private const val K_SET = "scheduled"
        private const val K_DELIVERED = "delivered"
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
        /** What the alert is and when (the same for both alarms of one alert). */
        const val EXTRA_KEY = "key"
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
        // The same alert can come twice; it is given once.
        val key = intent.getStringExtra(ReminderScheduler.EXTRA_KEY)
        if (key != null && !ReminderScheduler.firstDelivery(context, key)) return
        val alarm = intent.getBooleanExtra(ReminderScheduler.EXTRA_ALARM, false)
        val adhan = intent.getBooleanExtra(ReminderScheduler.EXTRA_ADHAN, false)
        val id = intent.getIntExtra(ReminderScheduler.EXTRA_ID, 0)
        val recording = intent.getStringExtra(ReminderScheduler.EXTRA_SOUND)
        // A late Adhan is given without it: the prayer may have begun. The Fajr alarm rings whenever it comes.
        val late = adhan && !alarm && recording != null && ReminderScheduler.adhanLate(context, key, recording)
        val sound = recording.takeUnless { late }
        // The Adhan (also as the Fajr alarm) and the spoken reminders: the app plays the recording (with Stop on the notification).
        if (sound != null && startAudio(context, intent)) {
            RescheduleReceiver.rescheduleAsync(context, goAsync())
            return
        }
        // Android refused the player (e.g. Android 12+ after an inexact alarm): an Adhan still rings, as an alarm, through
        // a notification channel whose sound is that recording; a spoken reminder falls back to the reminder sound.
        val adhanChannel = if (adhan && sound != null) {
            runCatching { ReminderScheduler.adhanChannel(context, sound) }.getOrNull()
        } else {
            null
        }
        val rings = alarm || adhanChannel != null
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val body = intent.getStringExtra(ReminderScheduler.EXTRA_BODY)
        val builder = NotificationCompat.Builder(context, adhanChannel ?: if (alarm) ReminderScheduler.ALARM_CHANNEL else ReminderScheduler.CHANNEL)
            .setSmallIcon(R.drawable.ic_tab_prayer)
            .setContentTitle(intent.getStringExtra(ReminderScheduler.EXTRA_TITLE))
            .setContentText(body)
            .setCategory(if (rings) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
        // Why no Adhan was heard.
        if (late) builder.setStyle(NotificationCompat.BigTextStyle().bigText(listOfNotNull(body, context.getString(R.string.adhan_muted_late)).joinToString("\n")))
        if (rings) {
            // Stop (or swiping it away) ends the sound.
            val stop = PendingIntent.getBroadcast(
                context, 50_000 + id,
                Intent(context, ReminderReceiver::class.java).setAction(ReminderScheduler.ACTION_STOP).putExtra(ReminderScheduler.EXTRA_ID, id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, context.getString(R.string.alarm_stop), stop).setDeleteIntent(stop)
            if (alarm) builder.setPriority(NotificationCompat.PRIORITY_MAX)
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

/**
 * Boot, an app update, time, zone and exact-alarm-permission changes rebuild the bounded schedule, the prayer silences
 * and the widgets.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in RESTARTS) ReminderScheduler.afterRestart(context)
        rescheduleAsync(context, goAsync())
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val RESTARTS = setOf(Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON", "com.htc.intent.action.QUICKBOOT_POWERON")

        fun rescheduleAsync(context: Context, pending: PendingResult?) {
            val app = context.applicationContext as? NearMosqueApp
            // Before the first unlock after a restart the settings cannot be read; the unlock (boot completed) rebuilds.
            if (app == null || !NearMosqueApp.unlocked(app)) {
                pending?.finish()
                return
            }
            scope.launch {
                try {
                    val settings = app.container.settings.settings.first()
                    runCatching { ReminderScheduler(app).reschedule(settings) }
                    PrayerSilence(app).sync(settings)
                    // The widgets change with the Adhan and the iqama, and their times follow a new time or zone.
                    runCatching { PrayerWidgets.redraw(app) }
                } finally {
                    pending?.finish()
                }
            }
        }
    }
}

/**
 * A restart, before the first unlock: the next day's alarms are set again from the copy the last rebuild kept (the
 * settings cannot be read yet), so the Fajr Adhan and the Fajr alarm still come. The unlock rebuilds everything.
 */
class LockedBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) runCatching { ReminderScheduler.restore(context) }
    }
}
