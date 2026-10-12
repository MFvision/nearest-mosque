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
        AlertKind.ADHAN -> if (e == PrayerEvent.FAJR) adhan(a.fajrAdhanSource, true) else adhan(a.adhanSource, false)
        AlertKind.BEFORE_10, AlertKind.BEFORE_5, AlertKind.NEAR -> voice(kind, e, voice)
        AlertKind.SALAWAT -> salawat(a.reciter)
        AlertKind.QURAN, AlertKind.SLEEP -> evening(a.reciter)
        else -> null
    }

    @SuppressLint("DiscouragedApi")
    fun rawId(context: Context, name: String): Int = context.resources.getIdentifier(name, "raw", context.packageName)
}

/**
 * Shows an Adhan or spoken reminder and plays its recording, as a short media-playback foreground service
 * (the notification carries Stop). Plays only when the ringer is on and Do Not Disturb is off; when it ends the
 * notification stays as an ordinary one and the service stops.
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
        stopPlayer()
        shownId = (intent?.getIntExtra(ReminderScheduler.EXTRA_ID, 0) ?: 0).coerceAtLeast(1)
        val builder = notification(intent, withStop = true)
        shown = builder
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(shownId, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(shownId, builder.build())
        }
        if (res == 0 || !audioAllowed(this)) {
            finish()
            return START_NOT_STICKY
        }
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(if (intent?.getBooleanExtra(ReminderScheduler.EXTRA_ADHAN, false) == true) AudioAttributes.CONTENT_TYPE_MUSIC else AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val audio = getSystemService(AudioManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs).build().also { audio.requestAudioFocus(it) }
        }
        player = MediaPlayer.create(this, res, attrs, audio.generateAudioSessionId())?.apply {
            setOnCompletionListener { finish() }
            setOnErrorListener { _, _, _ -> finish(); true }
            start()
        }
        if (player == null) finish()
        return START_NOT_STICKY
    }

    private fun notification(intent: Intent?, withStop: Boolean): NotificationCompat.Builder {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(this, AUDIO_CHANNEL)
            .setSmallIcon(R.drawable.ic_tab_prayer)
            .setContentTitle(intent?.getStringExtra(ReminderScheduler.EXTRA_TITLE))
            .setContentText(intent?.getStringExtra(ReminderScheduler.EXTRA_BODY))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
        if (withStop) {
            val stop = PendingIntent.getService(this, 1, Intent(this, PrayerAudioService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
            b.addAction(0, getString(R.string.alarm_stop), stop).setDeleteIntent(stop)
        }
        return b
    }

    /** Stops the sound; the notification stays, without Stop. */
    private fun finish() {
        stopPlayer()
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_DETACH) else @Suppress("DEPRECATION") stopForeground(false)
        shown?.let { b ->
            b.clearActions().setDeleteIntent(null)
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                Build.VERSION.SDK_INT < 33
            ) {
                NotificationManagerCompat.from(this).notify(shownId, b.build())
            }
        }
        stopSelf()
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

        /** Ringer on and Do Not Disturb off (the app never plays through silent or DND). */
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

/** Plays a recording in Settings (one at a time), on the media volume. */
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
                player = MediaPlayer.create(context, res)?.apply {
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
}
