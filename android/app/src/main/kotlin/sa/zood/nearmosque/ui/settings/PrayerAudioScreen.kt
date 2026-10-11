package sa.zood.nearmosque.ui.settings

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sa.zood.nearmosque.AppContainer
import sa.zood.nearmosque.R
import sa.zood.nearmosque.core.AdhanSource
import sa.zood.nearmosque.core.AlertKind
import sa.zood.nearmosque.core.AlertSettings
import sa.zood.nearmosque.core.PrayerEvent
import sa.zood.nearmosque.core.PrayerSounds
import sa.zood.nearmosque.core.Reciter
import sa.zood.nearmosque.core.VoiceLanguage
import sa.zood.nearmosque.platform.AudioCatalog
import sa.zood.nearmosque.platform.AudioPreview
import sa.zood.nearmosque.platform.ReminderScheduler
import sa.zood.nearmosque.ui.Format
import sa.zood.nearmosque.ui.prayer.prayerIcon
import sa.zood.nearmosque.ui.theme.LocalExtraColors

/**
 * Prayer & Audio: every Adhan, reminder and recitation has its own switch, and none changes another. Grouped
 * as the Adhan, reminders before the prayer, each prayer (expandable), Qur'an and dhikr, and more reminders.
 * Each change is saved at once and the reminders are rebuilt. Mirrors the iOS PrayerAudioSettingsView.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PrayerAudioScreen(container: AppContainer, alerts: AlertSettings, reminders: Set<PrayerEvent>, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) { onDispose { AudioPreview.stop() } }
    val voice = alerts.voice ?: VoiceLanguage.forInterface(Format.languageCode(context))

    fun set(a: AlertSettings) {
        if (android.os.Build.VERSION.SDK_INT >= 33) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        scope.launch {
            container.settings.setAlerts(a)
            withContext(Dispatchers.Default) { ReminderScheduler(context).reschedule(container.settings.settings.first()) }
        }
    }
    fun setBell(e: PrayerEvent, on: Boolean) = scope.launch {
        container.settings.setReminder(e, on)
        withContext(Dispatchers.Default) { ReminderScheduler(context).reschedule(container.settings.settings.first()) }
    }

    @Composable
    fun Preview(name: String, label: String) {
        val on = tick >= 0 && AudioPreview.playing == name
        IconButton(onClick = { AudioPreview.toggle(context, name) { tick++ } }) {
            Icon(
                if (on) painterResource(R.drawable.ic_audio_stop) else androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Filled.PlayArrow),
                contentDescription = if (on) stringResource(R.string.alarm_stop) else stringResource(R.string.preview_play, label),
                tint = LocalExtraColors.current.accentText,
            )
        }
    }

    @Composable
    fun Toggle(title: String, note: String?, on: Boolean, enabled: Boolean = true, preview: Pair<String, String>? = null, onChange: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else LocalExtraColors.current.textSecondary)
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LocalExtraColors.current.textSecondary) }
            }
            preview?.let { Preview(it.first, it.second) }
            Switch(checked = on, onCheckedChange = onChange, enabled = enabled)
        }
    }

    @Composable
    fun TimeRow(minutes: Int, onChange: (Int) -> Unit) {
        val label = java.time.LocalTime.of(minutes / 60, minutes % 60)
            .format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT).withLocale(Format.locale(context)))
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.reminder_time), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = {
                TimePickerDialog(context, { _, h, m -> onChange(h * 60 + m) }, minutes / 60, minutes % 60, DateFormat.is24HourFormat(context)).show()
            }, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
        }
    }

    @Composable
    fun SourceRow(source: AdhanSource, fajr: Boolean) {
        val available = !fajr || source.hasFajr
        val chosen = (if (fajr) alerts.fajrAdhanSource else alerts.adhanSource) == source && available
        val title = stringResource(if (source == AdhanSource.MAKKAH) R.string.source_makkah else R.string.source_madinah)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .selectable(chosen, enabled = available, role = Role.RadioButton) {
                    set(if (fajr) alerts.copy(fajrAdhanSource = source) else alerts.copy(adhanSource = source))
                }
                .padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = chosen, onClick = null, enabled = available)
            Text(
                if (available) title else stringResource(R.string.source_unavailable, title),
                Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium,
                color = if (available) MaterialTheme.colorScheme.onSurface else LocalExtraColors.current.textSecondary,
            )
            if (available) Preview(AudioCatalog.adhan(source, fajr), title)
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close)) }
                Text(stringResource(R.string.audio_title), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Header(stringResource(R.string.adhan_section))
                Toggle(stringResource(R.string.adhan_full), null, alerts.adhan) { set(alerts.copy(adhan = it)) }
                SourceRow(AdhanSource.MAKKAH, fajr = false)
                SourceRow(AdhanSource.MADINAH, fajr = false)
                Toggle(stringResource(R.string.adhan_fajr), null, alerts.fajrAdhan) { set(alerts.copy(fajrAdhan = it)) }
                SourceRow(AdhanSource.MAKKAH, fajr = true)
                SourceRow(AdhanSource.MADINAH, fajr = true)
                Note(stringResource(R.string.adhan_note_android))

                Header(stringResource(R.string.before_section))
                Toggle(stringResource(R.string.before10), null, alerts.before10) { set(alerts.copy(before10 = it)) }
                Toggle(stringResource(R.string.before5), null, alerts.before5) { set(alerts.copy(before5 = it)) }
                Toggle(stringResource(R.string.near), stringResource(R.string.near_note), alerts.near) { set(alerts.copy(near = it)) }
                AnimatedVisibility(alerts.near) {
                    FlowRow(Modifier.padding(start = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AlertSettings.nearChoices.forEach { m ->
                            FilterChip(selected = alerts.nearMinutes == m, onClick = { set(alerts.copy(nearMinutes = m)) },
                                label = { Text(stringResource(R.string.alerts_minutes, m)) })
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.voice_language), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    FilterChip(selected = voice == VoiceLanguage.ARABIC, onClick = { set(alerts.copy(voice = VoiceLanguage.ARABIC)) },
                        label = { Text(stringResource(R.string.voice_arabic)) })
                    Spacer(Modifier.size(8.dp))
                    FilterChip(selected = voice == VoiceLanguage.ENGLISH, onClick = { set(alerts.copy(voice = VoiceLanguage.ENGLISH)) },
                        label = { Text(stringResource(R.string.voice_english)) })
                    AudioCatalog.voice(AlertKind.BEFORE_10, PrayerEvent.MAGHRIB, voice)?.let { Preview(it, stringResource(R.string.voice_language)) }
                }
                Note(stringResource(R.string.before_note))

                Header(stringResource(R.string.per_prayer_section))
                PrayerEvent.prayers.forEach { e -> PrayerRows(e, alerts, e in reminders, { setBell(e, it) }) { set(alerts.withSounds(e, it)) } }
                Note(stringResource(R.string.per_prayer_note))

                Header(stringResource(R.string.dhikr_section))
                Toggle(stringResource(R.string.salawat), stringResource(R.string.salawat_note), alerts.salawat,
                    preview = AudioCatalog.salawat(alerts.reciter) to stringResource(R.string.salawat)) { set(alerts.copy(salawat = it)) }
                if (alerts.salawat) TimeRow(alerts.salawatMinutes) { set(alerts.copy(salawatMinutes = it)) }
                Toggle(stringResource(R.string.quran_reminder), stringResource(R.string.quran_reminder_note), alerts.quran,
                    preview = AudioCatalog.evening(alerts.reciter) to stringResource(R.string.quran_reminder)) { set(alerts.copy(quran = it)) }
                if (alerts.quran) TimeRow(alerts.quranMinutes) { set(alerts.copy(quranMinutes = it)) }
                Toggle(stringResource(R.string.alerts_sleep), stringResource(R.string.alerts_sleep_note), alerts.bedtimeMinutes != null,
                    preview = AudioCatalog.evening(alerts.reciter) to stringResource(R.string.alerts_sleep)) {
                    set(alerts.copy(bedtimeMinutes = if (it) alerts.bedtimeMinutes ?: AlertSettings.DEFAULT_BEDTIME_MIN else null))
                }
                alerts.bedtimeMinutes?.let { m -> TimeRow(m) { set(alerts.copy(bedtimeMinutes = it)) } }
                Toggle(stringResource(R.string.alerts_kahf), stringResource(R.string.alerts_kahf_note), alerts.kahf) { set(alerts.copy(kahf = it)) }
                Text(stringResource(R.string.reciter), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = alerts.reciter == Reciter.ALAFASY, onClick = { set(alerts.copy(reciter = Reciter.ALAFASY)) },
                        label = { Text(stringResource(R.string.reciter_alafasy)) })
                    FilterChip(selected = alerts.reciter == Reciter.CLASSIC, onClick = { set(alerts.copy(reciter = Reciter.CLASSIC)) },
                        label = { Text(stringResource(R.string.reciter_classic)) })
                }

                Header(stringResource(R.string.more_reminders_section))
                Toggle(stringResource(R.string.alerts_friday), stringResource(R.string.alerts_friday_note), alerts.friday) { set(alerts.copy(friday = it)) }
                Toggle(stringResource(R.string.alerts_ramadan), stringResource(R.string.alerts_ramadan_note), alerts.ramadan) { set(alerts.copy(ramadan = it)) }
                Text(stringResource(R.string.alerts_fajr_alarm), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                Note(stringResource(R.string.alerts_fajr_alarm_note))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = alerts.fajrAlarmMinutesBefore == null, onClick = { set(alerts.copy(fajrAlarmMinutesBefore = null)) },
                        label = { Text(stringResource(R.string.alerts_off)) })
                    AlertSettings.fajrAlarmChoices.forEach { m ->
                        FilterChip(selected = alerts.fajrAlarmMinutesBefore == m, onClick = { set(alerts.copy(fajrAlarmMinutesBefore = m)) },
                            label = { Text(if (m == 0) stringResource(R.string.alerts_at_fajr) else stringResource(R.string.alerts_minutes, m)) })
                    }
                }

                Spacer(Modifier.height(20.dp))
                Note(stringResource(R.string.audio_permission_note))
                Note(stringResource(R.string.audio_credits))
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/** One prayer, collapsed to its name and what is on; expanded to its own switches. */
@Composable
private fun PrayerRows(e: PrayerEvent, alerts: AlertSettings, bell: Boolean, onBell: (Boolean) -> Unit, onChange: (PrayerSounds) -> Unit) {
    var open by rememberSaveable(e) { mutableStateOf(false) }
    val s = alerts.sounds(e)
    val name = stringResource(Format.prayerName(e))
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { open = !open },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(prayerIcon(e)), contentDescription = null, tint = LocalExtraColors.current.accentText, modifier = Modifier.size(22.dp))
        Text(name, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
        Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
    }
    AnimatedVisibility(open) {
        Column(Modifier.padding(start = 16.dp)) {
            SubSwitch(stringResource(R.string.at_prayer_notify), bell, true, onBell)
            SubSwitch(stringResource(R.string.play_adhan), s.adhan, if (e == PrayerEvent.FAJR) alerts.fajrAdhan else alerts.adhan) { onChange(s.copy(adhan = it)) }
            SubSwitch(stringResource(R.string.notif_minutes_until, 10, name), s.before10, alerts.before10) { onChange(s.copy(before10 = it)) }
            SubSwitch(stringResource(R.string.notif_minutes_until, 5, name), s.before5, alerts.before5) { onChange(s.copy(before5 = it)) }
            SubSwitch(stringResource(R.string.near) + " · " + stringResource(R.string.alerts_minutes, alerts.nearMinutes), s.near, alerts.near) { onChange(s.copy(near = it)) }
        }
    }
    HorizontalDivider()
}

/** A per-prayer switch; it works only while the same reminder is on for all prayers above. */
@Composable
private fun SubSwitch(title: String, on: Boolean, master: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = if (master) MaterialTheme.colorScheme.onSurface else LocalExtraColors.current.textSecondary)
            if (!master) Text(stringResource(R.string.off_above), style = MaterialTheme.typography.bodySmall, color = LocalExtraColors.current.textSecondary)
        }
        // While the reminder is off above, the switch shows off (nothing plays); its own choice is kept.
        Switch(checked = on && master, onCheckedChange = onChange, enabled = master)
    }
}

@Composable
private fun Header(title: String) {
    Spacer(Modifier.height(20.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = LocalExtraColors.current.textSecondary, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
}
