package sa.zood.nearmosque.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import sa.zood.nearmosque.AppContainer
import sa.zood.nearmosque.BuildConfig
import sa.zood.nearmosque.R
import sa.zood.nearmosque.core.PackError
import sa.zood.nearmosque.core.PackJson
import sa.zood.nearmosque.core.PackManifest
import sa.zood.nearmosque.data.InstalledPackEntity
import sa.zood.nearmosque.platform.ExternalActions
import sa.zood.nearmosque.ui.Format
import sa.zood.nearmosque.ui.prayer.RadioRow
import sa.zood.nearmosque.ui.prayer.PrayerUi
import sa.zood.nearmosque.ui.theme.LocalExtraColors

/** Each language is listed in its own name so anyone can find theirs. */
val LANGUAGES: List<Pair<String, String>> = sa.zood.nearmosque.core.Languages.all.map { it.code to it.name }

/** Makes [code] the app's language (its platform locale tag, e.g. fa-AF for Dari). */
fun setAppLanguage(code: String?) = AppCompatDelegate.setApplicationLocales(
    code?.let { LocaleListCompat.forLanguageTags(sa.zood.nearmosque.core.Languages.tag(it)) } ?: LocaleListCompat.getEmptyLocaleList(),
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    prayer: PrayerUi,
    onPickCity: () -> Unit,
    onOpenCalculation: () -> Unit,
    onReplayTour: () -> Unit,
    onOnlineSearch: (Boolean) -> Unit,
    onSkyWeather: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val lang = Format.languageCode(context)
    val scope = rememberCoroutineScope()
    val packs by container.packs.installed.collectAsStateWithLifecycle(emptyList())
    var message by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf<InstalledPackEntity?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            message = try {
                val m = container.packs.importZip(uri, container.downloads.catalog.packs.map { it.id }.toSet())
                context.getString(R.string.import_done, m.title(lang))
            } catch (e: PackError.Reserved) {
                context.getString(R.string.import_failed, context.getString(R.string.error_pack_reserved))
            } catch (e: PackError.Checksum) {
                context.getString(R.string.import_failed, context.getString(R.string.error_pack_checksum))
            } catch (e: PackError.NewerSchema) {
                context.getString(R.string.import_failed, context.getString(R.string.error_pack_newer))
            } catch (e: Exception) {
                context.getString(R.string.import_failed, context.getString(R.string.error_pack_format))
            }
        }
    }
    var showAudio by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showLanguages by remember { mutableStateOf(false) }
    if (showAudio) {
        PrayerAudioScreen(
            container, prayer.settings?.alerts ?: sa.zood.nearmosque.core.AlertSettings(), prayer.settings?.reminders.orEmpty(),
            prayer.settings?.iqama ?: sa.zood.nearmosque.core.IqamaSettings(),
            silence = prayer.settings?.silence ?: sa.zood.nearmosque.core.SilenceSettings(),
            onClose = { showAudio = false },
        )
        return
    }
    val current = AppCompatDelegate.getApplicationLocales().takeIf { !it.isEmpty }?.get(0)?.let { sa.zood.nearmosque.core.Languages.code(it.language, it.country, it.script) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().wrapContentWidth(androidx.compose.ui.Alignment.CenterHorizontally).widthIn(max = sa.zood.nearmosque.ui.Adaptive.contentWidth).safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close)) }
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Section(stringResource(R.string.language))
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { showLanguages = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.language), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        current?.let { c -> LANGUAGES.firstOrNull { it.first == c }?.second } ?: stringResource(R.string.use_device_language),
                        style = MaterialTheme.typography.bodyLarge, color = LocalExtraColors.current.accentText,
                    )
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                }
                Text(stringResource(R.string.draft_translations_note), style = MaterialTheme.typography.bodySmall, color = LocalExtraColors.current.textSecondary)
                ContentDownloadCard(sa.zood.nearmosque.ui.Format.languageCode(context), Modifier.padding(top = 12.dp))

                Section(stringResource(R.string.appearance))
                // Held here: Appearance isn't Compose state, and picking one doesn't recreate the activity to re-read it.
                var appearance by remember { mutableStateOf(sa.zood.nearmosque.Appearance.current(context)) }
                listOf(
                    sa.zood.nearmosque.Appearance.SYSTEM to R.string.appearance_system,
                    sa.zood.nearmosque.Appearance.LIGHT to R.string.appearance_light,
                    sa.zood.nearmosque.Appearance.DARK to R.string.appearance_dark,
                ).forEach { (mode, label) ->
                    RadioRow(stringResource(label), appearance == mode) { appearance = mode; sa.zood.nearmosque.Appearance.set(context, mode) }
                }
                // Sky follows the weather (opt-in, off by default), with what it sends and the credit MET Norway's licence asks for.
                val skyWeather = prayer.settings?.skyWeather == true
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .toggleable(skyWeather, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onSkyWeather),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.sky_weather), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    androidx.compose.material3.Switch(checked = skyWeather, onCheckedChange = null)
                }
                Text(stringResource(R.string.sky_weather_note), style = MaterialTheme.typography.bodySmall, color = LocalExtraColors.current.textSecondary)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { ExternalActions.open(context, sa.zood.nearmosque.core.Weather.ATTRIBUTION_URL) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.sky_weather_attribution), style = MaterialTheme.typography.bodyMedium, color = LocalExtraColors.current.accentText)
                }

                Section(stringResource(R.string.app_style_title))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    sa.zood.nearmosque.platform.WidgetStyle.entries.forEach { st ->
                        val on = sa.zood.nearmosque.AppStyle.current == st.key
                        val (a, b) = sa.zood.nearmosque.ui.styleSwatch(st)
                        Column(
                            Modifier.width(76.dp).clickable(role = androidx.compose.ui.semantics.Role.RadioButton) { sa.zood.nearmosque.AppStyle.set(context, st.key) }
                                .semantics(mergeDescendants = true) { selected = on },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            androidx.compose.foundation.layout.Box(
                                Modifier.size(52.dp)
                                    .border(if (on) 3.dp else 1.dp, if (on) sa.zood.nearmosque.ui.theme.Tokens.gold else Color(0x33888888), androidx.compose.foundation.shape.CircleShape)
                                    .padding(4.dp)
                                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(a, b)), androidx.compose.foundation.shape.CircleShape),
                            )
                            Text(stringResource(st.label), style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                Text(stringResource(R.string.app_style_note_android), style = MaterialTheme.typography.bodySmall, color = LocalExtraColors.current.textSecondary, modifier = Modifier.padding(top = 6.dp))

                Section(stringResource(R.string.location_section))
                Text(prayer.location?.name ?: "—", style = MaterialTheme.typography.bodyLarge)
                prayer.location?.let { Text(stringResource(R.string.time_zone_label, Format.zoneName(context, it.zoneId)), style = MaterialTheme.typography.bodySmall) }
                OutlinedButton(onClick = onPickCity, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.change)) }

                Section(stringResource(R.string.tab_mosques))
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.online_search), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    androidx.compose.material3.Switch(checked = prayer.settings?.onlineSearch ?: true, onCheckedChange = onOnlineSearch)
                }
                Text(stringResource(R.string.online_search_body), style = MaterialTheme.typography.bodySmall, color = LocalExtraColors.current.textSecondary)

                Section(stringResource(R.string.prayer_section))
                prayer.settings?.prayer?.let { Text(stringResource(Format.methodName(it.method)), style = MaterialTheme.typography.bodyLarge) }
                OutlinedButton(onClick = onOpenCalculation, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.calculation)) }

                Section(stringResource(R.string.alerts_section))
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { showAudio = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.audio_title), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.audio_row_note), style = MaterialTheme.typography.bodySmall, color = LocalExtraColors.current.textSecondary)
                    }
                    Icon(androidx.compose.material.icons.Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.graphicsLayer(rotationZ = if (androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl) 90f else -90f))
                }
                Text(stringResource(R.string.alerts_bells_note), style = MaterialTheme.typography.bodySmall, color = LocalExtraColors.current.textSecondary)

                Section(stringResource(R.string.downloads))
                Text(stringResource(R.string.downloads_body), style = MaterialTheme.typography.bodyMedium)
                container.packs.citiesManifest()?.let { m ->
                    PackRow(stringResource(R.string.pack_kind_cities), m, m.recordCount, m.files.sumOf { it.bytes }, builtin = true, onRemove = null)
                }
                packs.forEach { p ->
                    val m = PackJson.decodeFromString(PackManifest.serializer(), p.manifestJson)
                    val title = when {
                        p.kind == "mosques" -> stringResource(R.string.pack_kind_mosques, m.coverage?.name ?: m.title(lang))
                        sa.zood.nearmosque.data.ChunkScope.isLibrary(p.id) -> stringResource(R.string.pack_kind_library, m.title(lang))
                        else -> stringResource(R.string.pack_kind_books, m.title(lang))
                    }
                    PackRow(title, m, p.recordCount, p.bytes, p.builtin) { confirmRemove = p }
                }
                Row {
                    TextButton(onClick = { importer.launch(arrayOf("application/zip", "application/octet-stream")) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.import_pack))
                    }
                    TextButton(onClick = { scope.launch { container.packs.restoreBuiltins(container.builtinsFor(sa.zood.nearmosque.ui.Format.languageCode(context))) } }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.restore_builtin))
                    }
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = LocalExtraColors.current.accentText) }

                Section(stringResource(R.string.on_device_ai))
                Text(stringResource(R.string.ai_pack_not_installed), style = MaterialTheme.typography.bodyMedium)
                Section(stringResource(R.string.cloud_ai))
                Text(stringResource(R.string.cloud_ai_off), style = MaterialTheme.typography.bodyMedium)

                Section(stringResource(R.string.sources_licenses))
                (listOfNotNull(container.packs.citiesManifest()) + packs.map { PackJson.decodeFromString(PackManifest.serializer(), it.manifestJson) })
                    .distinctBy { it.license.attribution }
                    .forEach { m ->
                        Text(m.license.attribution, style = MaterialTheme.typography.bodyMedium)
                        Text(m.license.name + " · " + m.license.url, style = MaterialTheme.typography.bodySmall, color = LocalExtraColors.current.textSecondary)
                        Spacer(Modifier.height(6.dp))
                    }
                Text("Adhan (MIT) · Batoul Apps; MapLibre Android (BSD-2-Clause)", style = MaterialTheme.typography.bodySmall)
                Text("Search model: static-similarity-mrl-multilingual-v1 · sentence-transformers (Apache-2.0), reduced to 256 dimensions", style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.map_attribution_ofm), style = MaterialTheme.typography.bodySmall)
                Text(
                    sa.zood.nearmosque.core.Weather.CREDIT, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable { ExternalActions.open(context, sa.zood.nearmosque.core.Weather.ATTRIBUTION_URL) },
                )

                Section(stringResource(R.string.privacy))
                Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodyMedium)

                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onReplayTour, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.onb_replay)) }

                Section(stringResource(R.string.about))
                Text(stringResource(R.string.version_label, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
                // Who makes the app and how to reach them, as on the store pages and the website.
                LinkRow(stringResource(R.string.about_publisher), Publisher.NAME) { ExternalActions.open(context, Publisher.COMPANY_URL) }
                LinkRow(stringResource(R.string.website), Publisher.WEBSITE) { ExternalActions.open(context, Publisher.websiteUrl(lang)) }
                LinkRow(stringResource(R.string.about_support), Publisher.EMAIL) { ExternalActions.email(context, Publisher.EMAIL) }
                LinkRow(stringResource(R.string.about_phone), Publisher.PHONE_DISPLAY) { ExternalActions.call(context, Publisher.PHONE) }
                LinkRow(stringResource(R.string.about_terms), null) { ExternalActions.open(context, Publisher.termsUrl(lang)) }
                Text(
                    "© ${Publisher.YEAR} ${Publisher.NAME}. " + stringResource(R.string.about_rights),
                    Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = LocalExtraColors.current.textSecondary,
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
    if (showLanguages) LanguageDialog(current, onDismiss = { showLanguages = false })
    confirmRemove?.let { p ->
        val m = PackJson.decodeFromString(PackManifest.serializer(), p.manifestJson)
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            text = { Text(stringResource(R.string.remove_pack_confirm, m.coverage?.name ?: m.title(lang))) },
            confirmButton = { TextButton(onClick = { scope.launch { container.packs.remove(p.id) }; confirmRemove = null }) { Text(stringResource(R.string.remove)) } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** The publisher's details shown in Settings (the same as on the store pages and nearmosque.net). */
object Publisher {
    const val NAME = "AVA ai"
    const val COMPANY_URL = "https://avaai.sa"
    const val WEBSITE = "nearmosque.net"
    const val EMAIL = "app@vision.bz"
    const val PHONE = "+966579372403"
    /** Kept left to right inside Arabic text. */
    const val PHONE_DISPLAY = "\u2066+966 57 937 2403\u2069"
    const val YEAR = 2026

    /** The site and its terms page, in Arabic for an Arabic interface. */
    fun websiteUrl(language: String) = "https://nearmosque.net/" + if (language == "ar") "#ar" else ""
    fun termsUrl(language: String) = "https://nearmosque.net/terms.html" + if (language == "ar") "#ar" else ""
}

/** A row that opens a web page, an email or a call, with its value (if any) after the title. */
@Composable
private fun LinkRow(title: String, value: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        value?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = LocalExtraColors.current.accentText) }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(20.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
}

@Composable
private fun PackRow(title: String, m: PackManifest, records: Int, bytes: Long, builtin: Boolean, onRemove: (() -> Unit)?) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(
                    R.string.pack_details, Format.number(context, records.toDouble(), 0),
                    android.text.format.Formatter.formatShortFileSize(context, bytes), m.version.toString(),
                ) + if (builtin) " · " + stringResource(R.string.pack_builtin) else "",
                style = MaterialTheme.typography.bodySmall, color = LocalExtraColors.current.textSecondary,
            )
        }
        if (onRemove != null) TextButton(onClick = onRemove, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.remove)) }
    }
}

/** The app's language: the device's language, then each language by its own name, with a search field. */
@Composable
private fun LanguageDialog(current: String?, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val shown = LANGUAGES.filter { (code, name) ->
        q.isEmpty() || name.lowercase().contains(q) || code.contains(q) ||
            java.util.Locale.forLanguageTag(code).getDisplayLanguage(java.util.Locale.ENGLISH).lowercase().contains(q)
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(vertical = 16.dp).heightIn(max = 560.dp)) {
                Text(stringResource(R.string.language), Modifier.padding(horizontal = 24.dp).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                androidx.compose.material3.OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.language_search)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f, fill = false)) {
                    if (q.isEmpty()) item {
                        RadioRow(stringResource(R.string.use_device_language), current == null) {
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()); onDismiss()
                        }
                    }
                    items(shown.size) { i ->
                        val (code, name) = shown[i]
                        RadioRow(name, current == code) { setAppLanguage(code); onDismiss() }
                    }
                }
            }
        }
    }
}
