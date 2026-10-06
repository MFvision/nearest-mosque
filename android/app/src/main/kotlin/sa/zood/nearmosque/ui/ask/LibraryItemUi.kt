package sa.zood.nearmosque.ui.ask

import sa.zood.nearmosque.ui.theme.Accent

import sa.zood.nearmosque.ui.theme.Ink

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.jsonPrimitive
import sa.zood.nearmosque.R
import sa.zood.nearmosque.appContainer
import sa.zood.nearmosque.core.LibraryParts
import sa.zood.nearmosque.core.SourceChunk
import sa.zood.nearmosque.data.LibraryFiles
import sa.zood.nearmosque.ui.theme.Tokens

private fun rtl(lang: String) = lang == "ar" || lang == "ur"

/** Title shown for a library record (a Qur'an reference for QuranEnc verses). */
@Composable
fun libraryTitle(c: SourceChunk): String {
    val s = c.section["surah"]?.jsonPrimitive?.content?.toIntOrNull()
    val a = c.section["ayah"]?.jsonPrimitive?.content?.toIntOrNull()
    return if (LibraryParts.publisher(c) == "quranenc" && s != null && a != null) stringResource(R.string.reference_quran, s, a) else c.anchor
}

@Composable
fun libraryTypeLabel(c: SourceChunk): String? {
    val type = when (c.sectionName("type")) {
        "books" -> stringResource(R.string.library_type_books)
        "articles" -> stringResource(R.string.library_type_articles)
        "fatwa" -> stringResource(R.string.library_type_fatwa)
        "videos" -> stringResource(R.string.library_type_videos)
        "audios" -> stringResource(R.string.library_type_audios)
        "hadith" -> stringResource(R.string.library_type_hadith)
        "quran" -> stringResource(R.string.library_type_quran)
        else -> null
    } ?: return null
    return listOfNotNull(type, c.sectionName("collection")).joinToString(" · ")
}

/** Attribution required by each source (binbaz: the source; HadeethEnc/QuranEnc: source and version). */
@Composable
fun libraryPublisherLine(c: SourceChunk): String? = when (LibraryParts.publisher(c)) {
    "binbaz" -> stringResource(R.string.publisher_binbaz)
    "hadeethenc" -> stringResource(R.string.publisher_hadeethenc, c.sectionName("version").orEmpty())
    "quranenc" -> stringResource(R.string.publisher_quranenc, c.sectionName("translationTitle").orEmpty(), c.sectionName("version").orEmpty())
    else -> null
}

@Composable
fun libraryWebLabel(c: SourceChunk): String {
    val host = c.url?.let { runCatching { java.net.URI(it).host?.removePrefix("www.") }.getOrNull() }
    return if (LibraryParts.publisher(c) == null || host == null) stringResource(R.string.library_web) else stringResource(R.string.library_web_site, host)
}

@Composable
private fun partLabel(kind: String): String? = when (kind) {
    "question" -> stringResource(R.string.library_question)
    "answer" -> stringResource(R.string.library_answer)
    "hadith" -> stringResource(R.string.library_part_hadith)
    "explanation" -> stringResource(R.string.library_part_explanation)
    "benefits" -> stringResource(R.string.library_part_benefits)
    "words" -> stringResource(R.string.library_part_words)
    "verse" -> stringResource(R.string.library_part_verse)
    "translation" -> stringResource(R.string.library_part_translation)
    "footnotes" -> stringResource(R.string.library_part_footnotes)
    else -> null
}

/**
 * A stored record read natively: each part (question, answer, hadith, explanation, translation...) with
 * its label and in its own direction; the Arabic verse above a QuranEnc translation (from the Quran
 * pack); grade and source; the required attribution; and the page for the full text when shortened.
 */
@Composable
fun PartsText(c: SourceChunk) {
    var full by remember { mutableStateOf(false) }
    val url = c.url?.takeIf { LibraryFiles.isAllowed(it) }
    if (full && url != null) { WebPage(url); return }
    val context = LocalContext.current
    val container = appContainer()
    var verse by remember { mutableStateOf<LibraryParts.Part?>(null) }
    val verseId = c.sectionName("verse")
    LaunchedEffect(verseId) {
        if (verseId != null) verse = container.ask.resolve(listOf(verseId)).firstOrNull()?.let { LibraryParts.Part("verse", "ar", it.chunk.original.text) }
    }
    val parts = listOfNotNull(verse) + LibraryParts.parts(c).filter { it.kind != "title" }
    val white = Ink
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
        libraryTypeLabel(c)?.let { Text(it, color = Accent, style = MaterialTheme.typography.labelLarge) }
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl(c.original.lang)) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Text(libraryTitle(c), color = white, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
        for (p in parts) {
            partLabel(p.kind)?.let { Text(it, color = Accent, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp)) }
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl(p.lang)) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                SelectionContainer {
                    Text(
                        p.text, color = if (p.kind == "footnotes") white.copy(alpha = 0.8f) else white,
                        style = (if (p.kind == "footnotes") MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge).copy(
                            textDirection = if (rtl(p.lang)) TextDirection.Rtl else TextDirection.Ltr,
                            lineHeight = if (p.kind == "verse") MaterialTheme.typography.bodyLarge.lineHeight * 1.6f else MaterialTheme.typography.bodyLarge.lineHeight * 1.35f,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (c.section["truncated"] != null && url != null) {
            TextButton(onClick = { full = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.library_full_text), color = Color(0xFF8CC0DE)) }
        }
        val meta = white.copy(alpha = 0.75f)
        c.sectionName("grade")?.takeIf { it.isNotBlank() }?.let { Text(stringResource(R.string.library_grade, it), color = meta, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 16.dp)) }
        c.sectionName("source")?.takeIf { it.isNotBlank() }?.let { Text(stringResource(R.string.library_source, it), color = meta, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
        libraryPublisherLine(c)?.let { Text(it, color = meta, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp, bottom = 24.dp)) }
    }
}
