package sa.zood.nearmosque.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sa.zood.nearmosque.R
import sa.zood.nearmosque.appContainer
import sa.zood.nearmosque.core.Languages
import sa.zood.nearmosque.core.RemotePack
import sa.zood.nearmosque.data.ContentDownloads
import sa.zood.nearmosque.ui.glass.GlassButton
import sa.zood.nearmosque.ui.glass.GlassButtonText
import sa.zood.nearmosque.ui.glass.glass
import sa.zood.nearmosque.ui.theme.Accent
import sa.zood.nearmosque.ui.theme.Ink
import sa.zood.nearmosque.ui.theme.LocalExtraColors

/**
 * Offers the content packs of [lang] (hadith, Quran translation, tafsir, library) when the app has none
 * bundled for it. Nothing is downloaded until the reader taps the button. Shows nothing when there is
 * nothing to download. [onSky] = the onboarding style (glass on the sky) instead of the Settings one.
 */
@Composable
fun ContentDownloadCard(lang: String, modifier: Modifier = Modifier, onSky: Boolean = false) {
    val downloads = appContainer().downloads
    val states by downloads.state.collectAsState()
    val state = states[lang]
    var missing by remember(lang) { mutableStateOf<List<RemotePack>?>(null) }
    LaunchedEffect(lang, state) { missing = downloads.missing(lang) }
    val todo = missing ?: return
    if (todo.isEmpty() && state != ContentDownloads.State.Done) return
    val context = LocalContext.current
    fun size(b: Long) = android.text.format.Formatter.formatShortFileSize(context, b)
    val ink = if (onSky) Ink else MaterialTheme.colorScheme.onBackground
    val secondary = if (onSky) Ink.copy(alpha = 0.8f) else LocalExtraColors.current.textSecondary
    val name = Languages.name(lang)
    Column(
        modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp), tint = if (onSky) null else Color.White.copy(alpha = 0.04f), shadow = 4.dp).padding(16.dp),
    ) {
        Text(stringResource(R.string.content_title, name), color = ink, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(6.dp))
        when (state) {
            is ContentDownloads.State.Running -> {
                Text(
                    stringResource(R.string.content_downloading, size(state.done), size(state.total)), color = secondary,
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { if (state.total > 0) (state.done.toFloat() / state.total).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth(), color = Accent,
                )
            }
            ContentDownloads.State.Done -> Text(stringResource(R.string.content_done), color = Accent, style = MaterialTheme.typography.bodyMedium)
            else -> {
                Text(stringResource(R.string.content_body, name), color = secondary, style = MaterialTheme.typography.bodyMedium)
                if (state == ContentDownloads.State.Failed) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.content_failed), color = Color(0xFFF2B8B5), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(12.dp))
                val label = stringResource(R.string.content_download, size(todo.sumOf { it.bytes }))
                if (onSky) {
                    GlassButton(onClick = { downloads.download(lang) }, prominent = true, modifier = Modifier.fillMaxWidth()) { GlassButtonText(label) }
                } else {
                    OutlinedButton(onClick = { downloads.download(lang) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
                }
            }
        }
    }
}
