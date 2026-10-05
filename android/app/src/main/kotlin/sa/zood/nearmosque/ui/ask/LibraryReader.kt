package sa.zood.nearmosque.ui.ask

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import sa.zood.nearmosque.R
import sa.zood.nearmosque.core.SourceChunk
import sa.zood.nearmosque.data.LibraryFiles
import sa.zood.nearmosque.platform.ExternalActions
import java.io.File

/** How a library item opens inside the app. */
sealed class LibraryMode {
    data class Pdf(val url: String) : LibraryMode()
    data class Media(val url: String, val video: Boolean) : LibraryMode()
    data class Web(val url: String) : LibraryMode()
    /** A record stored in parts (fatwa, hadith, Qur'an translation): read natively ([PartsText]). */
    data object Text : LibraryMode()

    companion object {
        fun of(c: SourceChunk): LibraryMode? {
            if (sa.zood.nearmosque.core.LibraryParts.parts(c).isNotEmpty()) return Text
            val file = c.sectionName("attachment")?.takeIf { LibraryFiles.isAllowed(it) }
            return when (c.sectionName("attachmentType")?.uppercase()) {
                "PDF" -> file?.let { Pdf(it) }
                "MP4", "M4V" -> file?.let { Media(it, video = true) }
                "MP3", "M4A", "WAV" -> file?.let { Media(it, video = false) }
                else -> null
            } ?: c.url?.takeIf { LibraryFiles.isAllowed(it) }?.let { Web(it) }
        }
    }
}

/** Full-screen reader for an IslamHouse item: the book itself (PDF), the video or audio, or the article page. */
@Composable
fun LibraryReader(c: SourceChunk, mode: LibraryMode, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF0B1220)).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.close), color = Color.White) }
                Text(libraryTitle(c), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                c.url?.let { url ->
                    TextButton(onClick = { ExternalActions.open(context, url) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(libraryWebLabel(c), color = Color(0xFF8CC0DE))
                    }
                }
            }
            when (mode) {
                is LibraryMode.Pdf -> PdfReader(c, mode.url)
                is LibraryMode.Media -> MediaPlayer(mode.url)
                is LibraryMode.Web -> WebPage(mode.url)
                LibraryMode.Text -> PartsText(c)
            }
        }
    }
}

@Composable
private fun PdfReader(c: SourceChunk, url: String) {
    val context = LocalContext.current
    var progress by remember { mutableFloatStateOf(0f) }
    var file by remember { mutableStateOf<File?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(url, attempt) {
        failed = false
        file = runCatching { LibraryFiles(context).fetch(url, c.id) { progress = it } }.getOrNull()
        failed = file == null
    }
    val f = file
    when {
        f != null -> PdfPages(f)
        failed -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.library_download_failed), color = Color.White, style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = { attempt++ }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.library_retry), color = Color(0xFF8CC0DE)) }
        }
        else -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.library_downloading), color = Color.White, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.padding(6.dp))
            if (progress > 0f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.width(220.dp)) else LinearProgressIndicator(Modifier.width(220.dp))
        }
    }
}

@Composable
private fun PdfPages(file: File) {
    val renderer = remember(file) { runCatching { PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)) }.getOrNull() }
    DisposableEffect(renderer) { onDispose { renderer?.close() } }
    if (renderer == null) {
        Text(stringResource(R.string.library_download_failed), Modifier.padding(24.dp), color = Color.White)
        return
    }
    // PdfRenderer allows one open page at a time.
    val lock = remember(renderer) { Mutex() }
    val list = rememberLazyListState()
    val page by remember { derivedStateOf { list.firstVisibleItemIndex + 1 } }
    val widthPx = LocalContext.current.resources.displayMetrics.widthPixels.coerceAtMost(1600)
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(renderer.pageCount) { i ->
                val bitmap by produceState<Bitmap?>(null, i) {
                    value = withContext(Dispatchers.IO) {
                        lock.withLock {
                            runCatching {
                                renderer.openPage(i).use { p ->
                                    val w = widthPx
                                    val h = (w.toFloat() * p.height / p.width).toInt().coerceAtLeast(1)
                                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
                                        b.eraseColor(android.graphics.Color.WHITE)
                                        p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    }
                                }
                            }.getOrNull()
                        }
                    }
                }
                val pageLabel = stringResource(R.string.library_page, i + 1, renderer.pageCount)
                val b = bitmap
                if (b != null) {
                    Image(b.asImageBitmap(), pageLabel, Modifier.fillMaxWidth().aspectRatio(b.width.toFloat() / b.height), contentScale = ContentScale.FillWidth)
                } else {
                    Box(Modifier.fillMaxWidth().aspectRatio(0.707f).background(Color.White.copy(alpha = 0.06f)).semantics { contentDescription = pageLabel }, contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }
        }
        Text(
            stringResource(R.string.library_page, page, renderer.pageCount),
            Modifier.align(Alignment.BottomCenter).padding(16.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp),
            color = Color.White, style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun MediaPlayer(url: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    val controller = MediaController(ctx)
                    controller.setAnchorView(this)
                    setMediaController(controller)
                    setVideoURI(Uri.parse(url))
                    setOnPreparedListener { start(); controller.show(0) }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            onRelease = { it.stopPlayback() },
        )
    }
}

@Composable
internal fun WebPage(url: String) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                loadUrl(url)
            }
        },
        modifier = Modifier.fillMaxSize(),
        onRelease = { it.destroy() },
    )
}
