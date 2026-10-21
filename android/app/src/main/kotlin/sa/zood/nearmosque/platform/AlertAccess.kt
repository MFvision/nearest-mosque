package sa.zood.nearmosque.platform

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * What the phone allows that decides whether the alerts that are on reach the user: read again each time a screen
 * shows (Prayer, Prayer & Audio, Check my alerts), since all of it is changed in system settings.
 */
data class AlertAccess(
    val notifications: Boolean,
    /** "Alarms & reminders" (always allowed below Android 12). */
    val exactAlarms: Boolean,
    /** Battery use "Unrestricted": not under battery optimisation. */
    val unrestricted: Boolean,
    /** Battery use "Restricted" (Android 9+): Android holds the app's alarms and work back. */
    val restricted: Boolean,
) {
    /** Battery saving may hold the alerts back. */
    val batteryLimited: Boolean get() = restricted || !unrestricted

    companion object {
        fun read(context: Context) = AlertAccess(
            ReminderScheduler.notificationsAllowed(context),
            ReminderScheduler.exactAlarmsAllowed(context),
            runCatching { context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(true),
            Build.VERSION.SDK_INT >= 28 && runCatching { context.getSystemService(ActivityManager::class.java).isBackgroundRestricted }.getOrDefault(false),
        )

        /** Samsung adds its own sleeping apps on top of Android's battery optimisation. */
        val samsung: Boolean get() = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

        /**
         * Android's battery optimisation list, where the app can be set to not optimised; for "Restricted", the app's
         * own page (Battery there). Neither needs a permission.
         */
        fun openBatterySettings(context: Context, restricted: Boolean) {
            val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
            val intent = if (restricted) details else Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { runCatching { context.startActivity(details.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }

        /** Do Not Disturb's own page: what gets through it (alarms among them). */
        fun openDoNotDisturbSettings(context: Context) {
            runCatching { context.startActivity(Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }

        /** Do Not Disturb, when on, blocks alarms (the user's own settings for it), and so the Adhan. */
        fun doNotDisturbBlocksAlarms(context: Context): Boolean = Build.VERSION.SDK_INT >= 28 && runCatching {
            context.getSystemService(NotificationManager::class.java).notificationPolicy.priorityCategories and
                NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS == 0
        }.getOrDefault(false)

        /** Do Not Disturb is on now (any mode). */
        fun doNotDisturbOn(context: Context): Boolean = runCatching {
            val f = context.getSystemService(NotificationManager::class.java).currentInterruptionFilter
            f != NotificationManager.INTERRUPTION_FILTER_ALL && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        }.getOrDefault(false)

        fun activity(context: Context): Activity? =
            generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()

        /**
         * Whether asking for notifications shows Android's dialog: never asked here, or declined once. After two "Don't
         * allow" Android answers no at once, and only Settings can turn them on.
         */
        fun canAskNotifications(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < 33) return false
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return false
            val asked = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(K_ASKED, false)
            return !asked || activity(context)?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
        }

        /** Notes that the app is about to ask for notifications (see [canAskNotifications]). */
        fun asking(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(K_ASKED, true).apply()
        }

        private const val PREFS = "ui"
        private const val K_ASKED = "notifications_asked"
    }
}
