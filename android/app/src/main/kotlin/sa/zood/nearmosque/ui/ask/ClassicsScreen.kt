package sa.zood.nearmosque.ui.ask

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import sa.zood.nearmosque.R
import sa.zood.nearmosque.core.BookCatalog
import sa.zood.nearmosque.core.BookVolume
import sa.zood.nearmosque.core.ClassicBook
import sa.zood.nearmosque.data.LibraryFiles
import sa.zood.nearmosque.ui.glass.glass
import sa.zood.nearmosque.ui.theme.Accent
import sa.zood.nearmosque.ui.theme.Ink
import sa.zood.nearmosque.ui.theme.LibraryAppearance

/**
 * The classical books (assets/books-catalog.json): the author's text of each book, one PDF per printed volume,
 * downloaded only when the person taps it and read in the app's own reader. Not used by Ask.
 */
object Classics {
    @Volatile private var cached: BookCatalog? = null

    fun catalog(context: Context): BookCatalog = cached ?: runCatching {
        BookCatalog.parse(context.assets.open("books-catalog.json").use { it.readBytes().decodeToString() })
    }.getOrDefault(BookCatalog.EMPTY).also { cached = it }

    fun key(v: BookVolume): String = "book_" + v.file.removeSuffix(".pdf")

    fun isDownloaded(context: Context, v: BookVolume): Boolean {
        val f = LibraryFiles(context).local(key(v), catalog(context).url(v))
        return f.isFile && f.length() > 0
    }

    /** The book and volume a downloaded file belongs to (for the downloads list). */
    fun volumeForFile(context: Context, name: String): Pair<ClassicBook, BookVolume>? =
        catalog(context).books.firstNotNullOfOrNull { b -> b.volumes.firstOrNull { key(it) + ".pdf" == name }?.let { b to it } }

    fun size(context: Context, bytes: Long): String = android.text.format.Formatter.formatShortFileSize(context, bytes)
}

private val Gold = Color(0xFFE2C275)
private val LinkBlue = Color(0xFF8CC0DE)

/** The card at the top of the library that opens the classical books. */
@Composable
fun ClassicsCard(onOpen: () -> Unit) {
    val context = LocalContext.current
    val count = Classics.catalog(context).books.size
    if (count == 0) return
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp), tint = Gold.copy(alpha = 0.10f))
            .clickable(role = Role.Button, onClick = onOpen).semantics(mergeDescendants = true) {}.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.classics_title), color = Ink, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.classics_count, count), color = Ink.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
        }
        Text("›", color = Ink.copy(alpha = 0.5f), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun ClassicsList(onBack: () -> Unit) {
    val context = LocalContext.current
    val catalog = Classics.catalog(context)
    var book by remember { mutableStateOf<ClassicBook?>(null) }
    val b = book
    if (b != null) { BookScreen(b, onBack = { book = null }); return }
    // The page follows the interface's direction (the note, the headings and "791 kB" read as in the interface
    // language); only the books' Arabic titles and authors are laid out right to left, aligned with the rest.
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("‹ " + stringResource(R.string.library_browse), color = LinkBlue) }
            Text(stringResource(R.string.classics_note), color = Ink.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
        }
        catalog.byGroup.forEach { (group, books) ->
            item(key = "g-$group") {
                Text(groupLabel(group), Modifier.padding(top = 10.dp), color = Accent, style = MaterialTheme.typography.titleMedium)
            }
            items(books, key = { it.id }) { bk ->
                Column(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp), tint = Ink.copy(alpha = 0.04f))
                        .clickable(role = Role.Button) { book = bk }.semantics(mergeDescendants = true) {}.padding(14.dp),
                ) {
                    ArabicText(bk.title, Ink, MaterialTheme.typography.titleSmall)
                    ArabicText(bk.author, Ink.copy(alpha = 0.8f), MaterialTheme.typography.bodyMedium)
                    val vols = if (bk.volumes.size > 1) stringResource(R.string.classics_volumes, bk.volumes.size) + " · " else ""
                    Text(vols + Classics.size(context, bk.bytes), color = Ink.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Text(catalog.source, Modifier.padding(top = 12.dp), color = Ink.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall) }
    }
}

/**
 * A book's Arabic title or author: a right-to-left paragraph (its brackets and dates stay in place) in any interface
 * language, on the same edge as the text around it.
 */
@Composable
private fun ArabicText(text: String, color: Color, style: androidx.compose.ui.text.TextStyle) {
    val edge = if (LocalLayoutDirection.current == LayoutDirection.Rtl) androidx.compose.ui.text.style.TextAlign.Right else androidx.compose.ui.text.style.TextAlign.Left
    Text(
        text, Modifier.fillMaxWidth(), color = color, textAlign = edge,
        style = style.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Rtl),
    )
}

@Composable
private fun groupLabel(group: String): String = stringResource(
    when (group) {
        "aqidah" -> R.string.classics_group_aqidah
        "tafsir" -> R.string.classics_group_tafsir
        "hadith" -> R.string.classics_group_hadith
        "fiqh" -> R.string.classics_group_fiqh
        "sira" -> R.string.classics_group_sira
        "tarikh" -> R.string.classics_group_tarikh
        else -> R.string.classics_group_raqaiq
    },
)

/** One book: its volumes, each downloaded on first tap and then opened offline. */
@Composable
private fun BookScreen(book: ClassicBook, onBack: () -> Unit) {
    val context = LocalContext.current
    val files = remember { LibraryFiles(context) }
    var reading by remember { mutableStateOf<BookVolume?>(null) }
    var version by remember { mutableIntStateOf(0) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("‹ " + stringResource(R.string.classics_title), color = LinkBlue) }
            Column(Modifier.fillMaxWidth()) {
                ArabicText(book.title, Ink, MaterialTheme.typography.titleLarge)
                ArabicText(book.author, Ink, MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.classics_edition, book.edition), color = Ink.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
            }
        }
        items(book.volumes, key = { it.file }) { v ->
            val saved = remember(version, v.file) { Classics.isDownloaded(context, v) }
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp), tint = Ink.copy(alpha = 0.04f))
                    .clickable(role = Role.Button) { reading = v }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(if (book.volumes.size > 1) stringResource(R.string.classics_volume, v.volume) else stringResource(R.string.classics_read), color = Ink, style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.classics_pages, v.pages) + " · " + Classics.size(context, v.bytes), color = Ink.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                }
                if (saved) {
                    TextButton(onClick = { files.delete(files.local(Classics.key(v), Classics.catalog(context).url(v))); version++ }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.library_delete), color = Color(0xFFE8A0A0))
                    }
                }
                Text(stringResource(if (saved) R.string.classics_open else R.string.classics_download), color = if (saved) Gold else LinkBlue, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    reading?.let { v ->
        Dialog(onDismissRequest = { reading = null; version++ }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        LibraryAppearance {
                Column(Modifier.fillMaxSize().background(Color(0xFF0B1220)).statusBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { reading = null; version++ }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.close), color = Ink) }
                        val title = if (book.volumes.size > 1) book.title + " · " + stringResource(R.string.classics_volume, v.volume) else book.title
                        Text(title, Modifier.weight(1f), color = Ink, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    }
                    PdfReader(Classics.key(v), Classics.catalog(context).url(v), v.sha256)
                }
            }
    }
    }
}
