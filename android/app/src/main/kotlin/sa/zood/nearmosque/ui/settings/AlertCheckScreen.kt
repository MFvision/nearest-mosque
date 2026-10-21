package sa.zood.nearmosque.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sa.zood.nearmosque.AppContainer
import sa.zood.nearmosque.R
import sa.zood.nearmosque.core.AlertKind
import sa.zood.nearmosque.platform.AlertAccess
import sa.zood.nearmosque.platform.AudioPreview
import sa.zood.nearmosque.platform.PrayerAudioService
import sa.zood.nearmosque.platform.ReminderScheduler
import sa.zood.nearmosque.ui.Format
import sa.zood.nearmosque.ui.theme.LocalExtraColors
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Prayer & Audio → Check my alerts: what the phone allows that decides whether an alert arrives (notifications,
 * "Alarms & reminders", battery use, Do Not Disturb, the alarm volume), read again each time the page shows, and the
 * next five alerts set, with their time, kind and sound. Each row with a problem opens the system page that fixes it.
 * Nothing leaves the phone. Mirrors the iOS AlertCheckView.
 */
@Composable
fun AlertCheckScreen(container: AppContainer, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    var report by remember { mutableStateOf(Report.read(context)) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        report = Report.read(context)
        onPauseOrDispose { }
    }
    val zone = settings?.prayerLocation?.zoneId ?: java.time.ZoneId.systemDefault()
    val weekday = remember(zone) { DateTimeFormatter.ofPattern("EEE", Format.locale(context)).withZone(zone) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = sa.zood.nearmosque.ui.Adaptive.contentWidth).safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close)) }
                Text(stringResource(R.string.check_alerts), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Section(stringResource(R.string.check_status))
                val a = report.access
                Status(
                    stringResource(R.string.check_notifications), stringResource(if (a.notifications) R.string.check_allowed else R.string.check_not_allowed),
                    if (a.notifications) null else stringResource(R.string.notifications_denied),
                ) { ReminderScheduler.openNotificationSettings(context) }
                if (ReminderScheduler.exactAlarmSettingsIntent(context) != null) {
                    Status(
                        stringResource(R.string.check_exact_android), stringResource(if (a.exactAlarms) R.string.check_allowed else R.string.check_not_allowed),
                        if (a.exactAlarms) null else stringResource(R.string.exact_off_body_android),
                    ) { ReminderScheduler.openExactAlarmSettings(context) }
                }
                Status(
                    stringResource(R.string.check_battery_android),
                    stringResource(
                        when {
                            a.restricted -> R.string.check_battery_restricted_android
                            a.unrestricted -> R.string.check_battery_unrestricted_android
                            else -> R.string.check_battery_optimised_android
                        },
                    ),
                    if (a.batteryLimited) stringResource(R.string.battery_body_android) + if (AlertAccess.samsung) " " + stringResource(R.string.battery_samsung_android) else "" else null,
                ) { AlertAccess.openBatterySettings(context, a.restricted) }
                Status(
                    stringResource(R.string.check_dnd_android), stringResource(if (report.dndOn) R.string.check_on else R.string.check_off),
                    when {
                        report.alarmsBlocked -> stringResource(R.string.check_dnd_blocking_android)
                        report.dndBlocksAlarms -> stringResource(R.string.check_dnd_policy_android)
                        else -> null
                    },
                ) { AlertAccess.openDoNotDisturbSettings(context) }
                Status(
                    stringResource(R.string.check_alarm_volume_android), stringResource(if (report.alarmVolumeOff) R.string.check_off else R.string.check_on),
                    if (report.alarmVolumeOff) stringResource(R.string.adhan_volume_off_android) else null,
                ) {
                    context.getSystemService(android.media.AudioManager::class.java)
                        .adjustStreamVolume(android.media.AudioManager.STREAM_ALARM, android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.FLAG_SHOW_UI)
                    report = Report.read(context)
                }

                Section(stringResource(R.string.check_next))
                if (report.next.isEmpty()) {
                    Text(stringResource(R.string.check_nothing), style = MaterialTheme.typography.bodyMedium, color = LocalExtraColors.current.textSecondary, modifier = Modifier.padding(vertical = 8.dp))
                }
                report.next.forEach { n ->
                    // The Adhan rings as an alarm, like the Fajr alarm; everything else is a notification.
                    val rings = n.alarm || n.adhan
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(if (rings) R.drawable.ic_alarm else R.drawable.ic_bell), contentDescription = null,
                            tint = LocalExtraColors.current.accentText, modifier = Modifier.size(20.dp),
                        )
                        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                            Text(n.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                            Text(
                                stringResource(if (rings) R.string.check_kind_alarm else R.string.check_kind_notification) + " · " + soundName(n.kind, n.sound),
                                style = MaterialTheme.typography.bodySmall, color = LocalExtraColors.current.textSecondary,
                            )
                        }
                        Text(
                            weekday.format(n.at) + " " + Format.time(context, n.at, zone),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = LocalExtraColors.current.textSecondary,
                        )
                    }
                    HorizontalDivider()
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.check_note_android), style = MaterialTheme.typography.bodySmall, color = LocalExtraColors.current.textSecondary)
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/** What Check my alerts shows, read from the phone when the page shows. */
private data class Report(
    val access: AlertAccess,
    val dndOn: Boolean,
    /** Do Not Disturb holds alarms back now, so the Adhan stays silent. */
    val alarmsBlocked: Boolean,
    /** Do Not Disturb, when on, blocks alarms. */
    val dndBlocksAlarms: Boolean,
    val alarmVolumeOff: Boolean,
    val next: List<ReminderScheduler.ScheduledAlert>,
) {
    companion object {
        fun read(context: android.content.Context) = Report(
            AlertAccess.read(context), AlertAccess.doNotDisturbOn(context), PrayerAudioService.alarmsBlocked(context),
            AlertAccess.doNotDisturbBlocksAlarms(context), AudioPreview.alarmVolumeOff(context),
            ReminderScheduler.upcoming(context, Instant.now()),
        )
    }
}

/** The sound as the user knows it. */
@Composable
private fun soundName(kind: AlertKind, sound: String?): String = when {
    // The Fajr alarm at the Fajr time with the Fajr Adhan on plays the Adhan.
    kind == AlertKind.FAJR_ALARM && sound == null -> stringResource(R.string.check_sound_alarm)
    sound == null -> stringResource(R.string.check_sound_default)
    sound.startsWith("adhan_") || sound.startsWith("fajr_") -> stringResource(R.string.adhan_section) + " · " + stringResource(
        when {
            sound.endsWith("_short") -> R.string.adhan_short
            sound.contains("madinah") -> R.string.source_madinah
            else -> R.string.source_makkah
        },
    )
    sound.startsWith("voice_") -> stringResource(R.string.check_sound_voice)
    else -> stringResource(R.string.check_sound_recitation)
}

/** One thing the phone allows: its state, and when it keeps alerts from coming, why; tapping opens its page. */
@Composable
private fun Status(title: String, value: String, problem: String?, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) { },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            if (problem != null) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFE8A33D), modifier = Modifier.size(18.dp).padding(end = 4.dp))
            }
            Text(value, style = MaterialTheme.typography.bodyMedium, color = LocalExtraColors.current.textSecondary)
        }
        problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LocalExtraColors.current.textSecondary, modifier = Modifier.padding(top = 2.dp)) }
    }
    HorizontalDivider()
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(20.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
}
