package sa.zood.nearmosque.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sa.zood.nearmosque.core.Answer
import sa.zood.nearmosque.core.AnswerKind
import sa.zood.nearmosque.core.City
import sa.zood.nearmosque.core.Geo
import sa.zood.nearmosque.core.SourceChunk
import kotlin.math.roundToInt

private fun openInBrowser(url: String) = Platform.open(url)

private fun distanceText(meters: Double): String =
    if (meters < 1000) t("distance_m", meters.roundToInt().toString())
    else t("distance_km", "%.1f".format(java.util.Locale.forLanguageTag(Strings.language), meters / 1000))

/** Mosques near the chosen city, nearest first, with their direction from north and walking directions. */
@Composable
internal fun MosquesScreen(city: City, online: Boolean) {
    var result by remember(city.id, online) { mutableStateOf<MosqueFinder.Result?>(null) }
    LaunchedEffect(city.id, online) { result = MosqueFinder.near(city.location, online) }
    MosqueList(city, online, result)
}

/** The list for [result] (null while searching). */
@Composable
internal fun MosqueList(city: City, online: Boolean, result: MosqueFinder.Result?) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(t("tab_mosques"), color = Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Text(t("searching_from_city", city.displayName(Strings.language)), color = Muted)
        }
        val r = result
        when {
            r == null -> item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = Gold)
                    Text(t(if (online) "online_searching" else "answer_searching"), color = Muted)
                }
            }
            r.mosques.isEmpty() -> item {
                Text(t("no_records_title"), color = Ink, fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp))
                if (!online) Text(t("online_search_body"), color = Muted)
            }
            else -> {
                if (r.offlineOnly) item { Text(t("online_offline_note"), color = Muted) }
                items(r.mosques, key = { it.mosque.sourceId }) { m ->
                    val bearing = Geo.initialBearing(city.location, m.mosque.location)
                    Surface(color = Card, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("↑", color = Gold, fontSize = 22.sp, modifier = Modifier.rotate(bearing.toFloat()))
                            Column(Modifier.weight(1f)) {
                                Text(m.mosque.displayName(Strings.language) ?: t("mosque_unnamed"), color = Ink, fontSize = 17.sp)
                                Text(distanceText(m.distanceMeters) + " · " + t("qibla_bearing", bearing.roundToInt().toString()), color = Muted, fontSize = 13.sp)
                            }
                            TextButton(onClick = { openInBrowser(MosqueFinder.directionsUrl(m.mosque)) }) { Text(t("get_directions"), color = Gold) }
                        }
                    }
                }
                item { Text(t(if (r.online) "source_online_osm" else "source_offline_pack"), color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        }
    }
}

/** Ask: a question answered from the books that come with the app, every passage with its source. */
@Composable
internal fun AskScreen() {
    val lang = Strings.language
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf<Answer?>(null) }
    LaunchedEffect(Unit) { withContext(Dispatchers.Default) { AskEngine.warmUp() } }
    fun ask(q: String) {
        if (q.isBlank() || busy) return
        busy = true
        scope.launch {
            answer = withContext(Dispatchers.Default) { AskEngine.ask(q.take(300)) }
            busy = false
        }
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(t("ask_title"), color = Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Text(t("ask_subtitle"), color = Muted)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    text, { text = it }, singleLine = true, placeholder = { Text(t("ask_placeholder")) },
                    modifier = Modifier.weight(1f).onKeyEvent { if (it.key == Key.Enter) { ask(text); true } else false },
                )
                Button(onClick = { ask(text) }, enabled = !busy) { Text(t("ask_send")) }
            }
        }
        val a = answer
        when {
            busy -> item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = Gold); Text(t("answer_searching"), color = Muted)
                }
            }
            a == null -> {
                item { Text(t("common_questions"), color = Gold, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp)) }
                items(AskEngine.questions, key = { it.id }) { q ->
                    val shown = AskEngine.question(q, lang)
                    Surface(color = Card, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().clickable { text = shown; ask(shown) }) {
                        Text(shown, color = Ink, modifier = Modifier.padding(14.dp))
                    }
                }
            }
            else -> {
                item { AnswerCard(a, lang) }
                item { TextButton(onClick = { answer = null; text = "" }) { Text("‹ " + t("common_questions"), color = Gold) } }
            }
        }
    }
}

@Composable
private fun AnswerCard(a: Answer, lang: String) {
    Surface(color = Card, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(a.question, color = Gold, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            val q = a.commonQuestion
            when (a.kind) {
                AnswerKind.COMMON -> if (q != null) {
                    Text(if (q.isReviewed) t("answer_common_reviewed", q.reviewedBy.orEmpty()) else t("answer_common_unreviewed"), color = Muted, fontSize = 13.sp)
                    Text(AskEngine.summary(q, lang), color = Ink, fontSize = 16.sp)
                }
                AnswerKind.PASSAGES -> {
                    Text(t("answer_from_passages"), color = Ink, fontWeight = FontWeight.SemiBold)
                    Text(t("answer_from_passages_body"), color = Muted, fontSize = 13.sp)
                }
                AnswerKind.INSUFFICIENT -> {
                    Text(t("answer_insufficient_title"), color = Ink, fontWeight = FontWeight.SemiBold)
                    Text(t("answer_insufficient_body"), color = Muted, fontSize = 13.sp)
                }
            }
            val cited = a.citations.mapNotNull { AskEngine.chunk(it) } + (q?.let { AskEngine.hadithFor(it, lang) } ?: emptyList())
            if (cited.isNotEmpty()) Text(t("sources"), color = Gold, fontSize = 15.sp)
            cited.forEach { Passage(it) }
            val related = a.related.mapNotNull { AskEngine.chunk(it) }
            if (related.isNotEmpty()) Text(t("related_passages"), color = Gold, fontSize = 15.sp)
            related.forEach { Passage(it) }
            Text(t("answer_not_fatwa"), color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Passage(c: SourceChunk) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(AskEngine.reference(c), color = Muted, fontSize = 12.sp)
        val ar = if (c.original.lang == "ar") c.original.text else c.translations.firstOrNull { it.lang == "ar" }?.text
        if (ar != null) CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Text(ar, color = Ink, fontSize = 18.sp, modifier = Modifier.fillMaxWidth())
        }
        val other = if (c.original.lang != "ar") c.original.text else c.translations.firstOrNull { it.lang != "ar" }?.text
        if (other != null) Text(other.take(900), color = Ink.copy(alpha = 0.85f), fontSize = 14.sp)
        c.url?.let { u -> TextButton(onClick = { openInBrowser(u) }) { Text(t("source_original_link"), color = Gold, fontSize = 12.sp) } }
    }
}
