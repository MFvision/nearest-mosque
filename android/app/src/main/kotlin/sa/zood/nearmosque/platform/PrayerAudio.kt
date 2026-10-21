package sa.zood.nearmosque.platform

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import sa.zood.nearmosque.MainActivity
import sa.zood.nearmosque.R
import sa.zood.nearmosque.core.AdhanSource
import sa.zood.nearmosque.core.AlertKind
import sa.zood.nearmosque.core.AlertSettings
import sa.zood.nearmosque.core.PrayerEvent
import sa.zood.nearmosque.core.Reciter
import sa.zood.nearmosque.core.VoiceLanguage

/**
 * Which recording goes with which alert: files in res/raw made by tools/build_audio.py (sources in
 * shared/audio/CREDITS.md; kept by res/raw/keep.xml since they are looked up by name).
 */
object AudioCatalog {
    fun adhan(source: AdhanSource, fajr: Boolean) = (if (fajr) "fajr_" else "adhan_") + source.name.lowercase()

    /** The first "Allahu akbar, Allahu akbar" only; the same words at Fajr, so one recording per source. */
    fun shortAdhan(source: AdhanSource) = "adhan_" + source.name.lowercase() + "_short"

    /** The Adhan for this prayer: the whole one, or with the short Adhan chosen its first takbir (Fajr too). */
    fun adhan(e: PrayerEvent, a: AlertSettings): String {
        val fajr = e == PrayerEvent.FAJR
        val source = if (fajr) a.fajrAdhanSource else a.adhanSource
        return if (a.shortAdhan) shortAdhan(source) else adhan(source, fajr)
    }

    fun voice(kind: AlertKind, e: PrayerEvent, language: VoiceLanguage): String? {
        val variant = when (kind) {
            AlertKind.BEFORE_10 -> "10"
            AlertKind.BEFORE_5 -> "5"
            AlertKind.NEAR -> "near"
            else -> return null
        }
        return "voice_" + (if (language == VoiceLanguage.ARABIC) "ar" else "en") + "_" + e.name.lowercase() + "_" + variant
    }

    fun salawat(r: Reciter) = if (r == Reciter.ALAFASY) "salawat_alafasy" else "salawat_husary"
    fun evening(r: Reciter) = if (r == Reciter.ALAFASY) "evening_alafasy" else "evening_abdulbasit"

    /** The recording for a planned alert, or null for the plain notification sound. */
    fun sound(kind: AlertKind, e: PrayerEvent, a: AlertSettings, voice: VoiceLanguage): String? = when (kind) {
        AlertKind.ADHAN -> adhan(e, a)
        AlertKind.BEFORE_10, AlertKind.BEFORE_5, AlertKind.NEAR -> voice(kind, e, voice)
        AlertKind.SALAWAT -> salawat(a.reciter)
        AlertKind.QURAN, AlertKind.SLEEP -> evening(a.reciter)
        else -> null
    }

    @SuppressLint("DiscouragedApi")
    fun rawId(context: Context, name: String): Int = context.resources.getIdentifier(name, "raw", context.packageName)

    /** How long each Adhan recording plays, in whole seconds (rounded up, measured from the files in res/raw). */
    fun durationSeconds(name: String): Long? = when (name) {
        "adhan_makkah" -> 205
        "adhan_madinah" -> 243
        "fajr_makkah" -> 215
        "fajr_madinah" -> 221
        "adhan_makkah_short" -> 12
        "adhan_madinah_short" -> 13
        else -> null
    }
}

/**
 * Shows an Adhan or spoken reminder and plays its recording, as a short media-playback foreground service
 * (the notification carries Stop). The Adhan plays as an alarm: at the alarm volume, on silent and vibrate too, and
 * through Do Not Disturb unless alarms are blocked there (then its notification says so); a Fajr alarm at the Fajr time
 * plays it until stopped. Spoken reminders and recitations play only when the ringer is on and Do Not Disturb is off.
 * When it ends the notification stays as an ordinary one and the service stops.
 */
class PrayerAudioService : Service() {
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var shownId = 0
    private var shown: NotificationCompat.Builder? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            finish()
            return START_NOT_STICKY
        }
        val sound = intent?.getStringExtra(ReminderScheduler.EXTRA_SOUND)
        val res = sound?.let { AudioCatalog.rawId(this, it) } ?: 0
        val adhan = intent?.getBooleanExtra(ReminderScheduler.EXTRA_ADHAN, false) == true
        val alarm = intent?.getBooleanExtra(ReminderScheduler.EXTRA_ALARM, false) == true
        stopPlayer()
        val id = (intent?.getIntExtra(ReminderScheduler.EXTRA_ID, 0) ?: 0).coerceAtLeast(1)
        // Another alert while one plays: the earlier one's notification stays, without Stop.
        if (shown != null && id != shownId) release()
        shownId = id
        val muted = if (adhan) adhanMuted(this) else null
        val builder = notification(intent, withStop = true, adhan, muted)
        shown = builder
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(shownId, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(shownId, builder.build())
        }
        if (res == 0 || muted != null || (!adhan && !audioAllowed(this))) {
            finish()
            return START_NOT_STICKY
        }
        val attrs = attributes(adhan)
        val audio = getSystemService(AudioManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs).build().also { audio.requestAudioFocus(it) }
        }
        player = MediaPlayer.create(this, res, attrs, audio.generateAudioSessionId())?.apply {
            // The Fajr alarm plays the Adhan until it is stopped, as it rings its tone.
            isLooping = alarm
            setOnCompletionListener { finish() }
            setOnErrorListener { _, _, _ -> finish(); true }
            start()
        }
        if (player == null) finish()
        return START_NOT_STICKY
    }

    private fun notification(intent: Intent?, withStop: Boolean, adhan: Boolean, muted: String?): NotificationCompat.Builder {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE,
        )
        val body = intent?.getStringExtra(ReminderScheduler.EXTRA_BODY)
        val b = NotificationCompat.Builder(this, AUDIO_CHANNEL)
            .setSmallIcon(R.drawable.ic_tab_prayer)
            .setContentTitle(intent?.getStringExtra(ReminderScheduler.EXTRA_TITLE))
            .setContentText(body)
            // As an alarm, the Adhan's notification shows through Do Not Disturb when alarms may.
            .setCategory(if (adhan) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
        // Why no Adhan was heard.
        if (muted != null) b.setStyle(NotificationCompat.BigTextStyle().bigText(listOfNotNull(body, muted).joinToString("\n")))
        if (withStop) {
            val stop = PendingIntent.getService(this, 1, Intent(this, PrayerAudioService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
            b.addAction(0, getString(R.string.alarm_stop), stop).setDeleteIntent(stop)
        }
        return b
    }

    /** Stops the sound; the notification stays, without Stop. */
    private fun finish() {
        stopPlayer()
        release()
        stopSelf()
    }

    /** The shown notification stays as an ordinary one, without Stop, and quietly (it has alerted already). */
    private fun release() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_DETACH) else @Suppress("DEPRECATION") stopForeground(false)
        shown?.let { b ->
            b.clearActions().setDeleteIntent(null).setOnlyAlertOnce(true)
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                Build.VERSION.SDK_INT < 33
            ) {
                NotificationManagerCompat.from(this).notify(shownId, b.build())
            }
        }
        shown = null
    }

    private fun stopPlayer() {
        player?.run { runCatching { stop() }; release() }
        player = null
        if (Build.VERSION.SDK_INT >= 26) focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focus = null
    }

    override fun onDestroy() {
        stopPlayer()
        super.onDestroy()
    }

    companion object {
        const val AUDIO_CHANNEL = "prayer_audio"
        const val ACTION_STOP = "sa.zood.nearmosque.audio.STOP"

        /**
         * The Adhan as an alarm (alarm volume; it plays on silent and vibrate, and through Do Not Disturb unless alarms
         * are blocked there); spoken reminders and recitations as notification sounds. The preview uses the same.
         */
        fun attributes(adhan: Boolean): AudioAttributes = AudioAttributes.Builder()
            .setUsage(if (adhan) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(if (adhan) AudioAttributes.CONTENT_TYPE_MUSIC else AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        /** Do Not Disturb holds alarms back now: total silence, or priority only without alarms. */
        fun alarmsBlocked(context: Context): Boolean {
            val nm = context.getSystemService(NotificationManager::class.java)
            return when (nm.currentInterruptionFilter) {
                NotificationManager.INTERRUPTION_FILTER_NONE -> true
                NotificationManager.INTERRUPTION_FILTER_PRIORITY -> Build.VERSION.SDK_INT >= 28 && runCatching {
                    // What applies now, with every mode that is on (Android 11+), or the user's own.
                    val p = if (Build.VERSION.SDK_INT >= 30) nm.consolidatedNotificationPolicy else nm.notificationPolicy
                    p.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS == 0
                }.getOrDefault(false)
                else -> false
            }
        }

        /** Why the Adhan cannot be heard now, or null when it can. */
        fun adhanMuted(context: Context): String? = when {
            alarmsBlocked(context) -> context.getString(R.string.adhan_muted_dnd)
            AudioPreview.alarmVolumeOff(context) -> context.getString(R.string.adhan_muted_volume)
            else -> null
        }

        /** Ringer on and Do Not Disturb off: spoken reminders and recitations never play through silent or DND. */
        fun audioAllowed(context: Context): Boolean {
            val audio = context.getSystemService(AudioManager::class.java)
            val nm = context.getSystemService(NotificationManager::class.java)
            val filter = nm.currentInterruptionFilter
            return audio.ringerMode == AudioManager.RINGER_MODE_NORMAL &&
                (filter == NotificationManager.INTERRUPTION_FILTER_ALL || filter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN)
        }

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(AUDIO_CHANNEL) == null) {
                // Silent channel: the app plays the recording itself.
                nm.createNotificationChannel(
                    NotificationChannel(AUDIO_CHANNEL, context.getString(R.string.audio_title), NotificationManager.IMPORTANCE_HIGH).apply {
                        setSound(null, null)
                        enableVibration(true)
                    },
                )
            }
        }

        /** Starts the service for a fired reminder; false when Android does not allow it (then the caller notifies). */
        fun start(context: Context, reminder: Intent): Boolean {
            ensureChannel(context)
            val i = Intent(context, PrayerAudioService::class.java).putExtras(reminder)
            return try {
                ContextCompat.startForegroundService(context, i)
                true
            } catch (e: IllegalStateException) {
                false
            } catch (e: SecurityException) {
                false
            }
        }
    }
}

/** Plays a recording in Settings (one at a time), on the same volume as at prayer time: the Adhan at the alarm volume. */
object AudioPreview {
    private var player: MediaPlayer? = null
    var playing: String? = null
        private set
    private var onChange: (() -> Unit)? = null

    fun toggle(context: Context, name: String, changed: () -> Unit) {
        val wasPlaying = playing == name
        stop()
        onChange = changed
        if (!wasPlaying) {
            val res = AudioCatalog.rawId(context, name)
            if (res != 0) {
                val adhan = name.startsWith("adhan_") || name.startsWith("fajr_")
                val session = context.getSystemService(AudioManager::class.java).generateAudioSessionId()
                player = MediaPlayer.create(context, res, PrayerAudioService.attributes(adhan), session)?.apply {
                    setOnCompletionListener { stop() }
                    start()
                }
                if (player != null) playing = name
            }
        }
        changed()
    }

    fun stop() {
        player?.run { runCatching { stop() }; release() }
        player = null
        playing = null
        onChange?.invoke()
    }

    /** While a screen with Adhan previews shows, the volume keys set the alarm volume, which the Adhan plays at. Returns the undo. */
    fun volumeKeysForAdhan(context: Context): () -> Unit {
        val activity = AlertAccess.activity(context) ?: return {}
        activity.volumeControlStream = AudioManager.STREAM_ALARM
        return { activity.volumeControlStream = AudioManager.USE_DEFAULT_STREAM_TYPE }
    }

    /** The alarm volume is all the way down, so the Adhan cannot be heard. */
    fun alarmVolumeOff(context: Context): Boolean =
        context.getSystemService(AudioManager::class.java).getStreamVolume(AudioManager.STREAM_ALARM) == 0
}
