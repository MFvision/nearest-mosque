package sa.zood.nearmosque.ui.ask

import sa.zood.nearmosque.ui.theme.Accent

import sa.zood.nearmosque.ui.theme.Ink

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import sa.zood.nearmosque.platform.ExternalActions
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sa.zood.nearmosque.R
import sa.zood.nearmosque.appContainer
import sa.zood.nearmosque.core.AnswerKind
import sa.zood.nearmosque.core.CommonQuestion
import sa.zood.nearmosque.data.ResolvedCitation
import sa.zood.nearmosque.ui.Format
import sa.zood.nearmosque.ui.glass.GlassButton
import sa.zood.nearmosque.ui.glass.GlassButtonText
import sa.zood.nearmosque.ui.glass.GlassCard
import sa.zood.nearmosque.ui.glass.GlassIconButton
import sa.zood.nearmosque.ui.glass.bottomBarPadding
import sa.zood.nearmosque.ui.glass.glass
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import sa.zood.nearmosque.ui.theme.LocalExtraColors
import sa.zood.nearmosque.ui.theme.Tokens

@OptIn(ExperimentalLayoutApi::class)
@Composable
/** Ask AI, opened full screen from the floating pill; [onClose] goes back to the tab underneath. */
fun AskScreen(vm: AskViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val container = appContainer()
    val lang = Format.languageCode(context)
    val ui by vm.ui.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var reading by remember { mutableStateOf<ResolvedCitation?>(null) }
    var library by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(false) }
    val chats by vm.chats.collectAsStateWithLifecycle()
    if (history) {
        ChatHistorySheet(
            chats, onOpen = { history = false; vm.open(it, lang) }, onDelete = vm::deleteChat,
            onDeleteAll = vm::deleteAllChats, onDismiss = { history = false },
        )
    }
    // The library packs follow the interface language (installed in the background when it changes).
    LaunchedEffect(lang) { container.ensureLibraries(lang) }
    if (library) LibraryScreen(onDismiss = { library = false })
    val list = rememberLazyListState()
    LaunchedEffect(ui.turns.size, ui.turns.lastOrNull()?.answer) { if (ui.turns.isNotEmpty()) list.animateScrollToItem(list.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1) }
    val white = Ink

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(rememberVectorPainter(Icons.Filled.Close), stringResource(R.string.close), onClose)
            Text(
                stringResource(R.string.tab_ask), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = white,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1,
            )
            if (ui.turns.isNotEmpty()) {
                GlassIconButton(rememberVectorPainter(Icons.Filled.Edit), stringResource(R.string.new_conversation), vm::newConversation)
                Spacer(Modifier.size(8.dp))
            }
            GlassIconButton(androidx.compose.ui.res.painterResource(R.drawable.ic_history), stringResource(R.string.chat_history), { history = true })
            Spacer(Modifier.size(8.dp))
            GlassIconButton(androidx.compose.ui.res.painterResource(R.drawable.ic_book), stringResource(R.string.library_title), { library = true })
        }
        LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ui.turns.isEmpty()) {
                item("skyline-space") { Spacer(Modifier.height(130.dp)) }
                item("intro") {
                    Text(stringResource(R.string.ask_title), style = MaterialTheme.typography.displaySmall, color = white, modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.ask_subtitle), style = MaterialTheme.typography.bodyLarge, color = white.copy(alpha = 0.85f))
                    Spacer(Modifier.height(12.dp))
                    LibraryEntryCard { library = true }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.ai_pack_not_installed), style = MaterialTheme.typography.bodySmall, color = white.copy(alpha = 0.7f))
                }
                item("common") {
                    Text(stringResource(R.string.common_questions), style = MaterialTheme.typography.titleMedium, color = white, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
                    Spacer(Modifier.height(10.dp))
                    if (!ui.ready) CircularProgressIndicator(Modifier.size(24.dp), color = white)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ui.common.forEach { q ->
                            val text = q.question[lang] ?: q.question["en"] ?: q.id
                            Text(
                                text, color = white, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.heightIn(min = 48.dp).glass(RoundedCornerShape(18.dp), shadow = 6.dp)
                                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { vm.askCommon(q, text, lang) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            }
            items(ui.turns, key = { it.id }) { t ->
                Column {
                    Text(
                        t.question, color = white, style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.End).glass(RoundedCornerShape(20.dp), tint = Tokens.navy.copy(alpha = 0.55f), shadow = 6.dp)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    if (t.answer == null) {
                        ThinkingCard(ui.stage, ui.stagesDone, onStop = vm::stop)
                    } else {
                        AnswerCard(t, lang, onRead = { reading = it })
                        if (t.id == ui.turns.last().id && !ui.busy) {
                            Spacer(Modifier.height(10.dp))
                            Suggestions(ui.common, ui.turns, lang) { q, text -> vm.askCommon(q, text, lang) }
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f).glass(RoundedCornerShape(26.dp), shadow = 6.dp),
                placeholder = { Text(stringResource(if (ui.turns.isEmpty()) R.string.ask_placeholder else R.string.ask_follow_up_placeholder), color = white.copy(alpha = 0.7f)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { vm.ask(input, lang); input = "" }),
                maxLines = 4,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedTextColor = white, unfocusedTextColor = white, cursorColor = Tokens.gold,
                ),
            )
            Spacer(Modifier.width(10.dp))
            GlassButton(onClick = { vm.ask(input, lang); input = "" }, prominent = true, enabled = input.isNotBlank() && !ui.busy, modifier = Modifier.size(56.dp)) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.ask_send), modifier = Modifier.size(20.dp))
            }
        }
    }
    reading?.let { ReaderSheet(it, vm, onDismiss = { reading = null }) }
}

@Composable
private fun AnswerCard(t: Turn, lang: String, onRead: (ResolvedCitation) -> Unit) {
    val a = t.answer ?: return
    var more by rememberSaveable(t.id) { mutableStateOf(false) }
    val context = LocalContext.current
    // The answer in the interface language, then the quoted verses (Arabic, then their translation).
    val spoken = buildList {
        if (a.kind == AnswerKind.COMMON) a.commonQuestion?.let { add(summaryFor(it, lang) to lang) }
        t.citations.take(2).forEach { c ->
            add(c.chunk.original.text to c.chunk.original.lang)
            c.chunk.translations.firstOrNull()?.let { add(it.text to it.lang) }
        }
    }
    val speaking by sa.zood.nearmosque.platform.Speaker.speaking.collectAsStateWithLifecycle()
    GlassCard(Modifier.semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite }) {
        if (spoken.isNotEmpty()) {
            val on = speaking == t.id.toString()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { sa.zood.nearmosque.platform.Speaker.toggle(context, t.id.toString(), spoken) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(
                        if (on) androidx.compose.ui.res.painterResource(R.drawable.ic_stop) else androidx.compose.ui.res.painterResource(R.drawable.ic_speaker),
                        contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (on) R.string.read_aloud_stop else R.string.read_aloud), color = Accent)
                }
            }
        }
        when (a.kind) {
            AnswerKind.COMMON -> {
                val q = a.commonQuestion!!
                Text(
                    if (q.isReviewed) stringResource(R.string.answer_common_reviewed, q.reviewedBy!!) else stringResource(R.string.answer_common_unreviewed),
                    style = MaterialTheme.typography.labelLarge, color = Accent,
                )
                Spacer(Modifier.height(6.dp))
                Text(summaryFor(q, lang), style = MaterialTheme.typography.bodyLarge)
            }
            AnswerKind.PASSAGES -> {
                Text(stringResource(R.string.answer_from_passages), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.answer_from_passages_body), style = MaterialTheme.typography.bodyMedium)
            }
            AnswerKind.INSUFFICIENT -> if (t.library.isNotEmpty()) {
                Text(stringResource(R.string.library_found_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.library_found_body), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(stringResource(R.string.answer_insufficient_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.answer_insufficient_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (t.citations.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.sources), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            t.citations.forEach { SourceCard(it, lang, onRead) }
        }
        if (t.library.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.library_section), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            t.library.forEach { LibraryCard(it) }
            Text(stringResource(R.string.library_note) + " " + stringResource(R.string.library_offline_note), style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = 0.7f), modifier = Modifier.padding(top = 6.dp))
        }
        if (t.related.isNotEmpty()) {
            TextButton(onClick = { more = !more }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (more) R.string.show_less_detail else R.string.show_more_detail), color = androidx.compose.ui.graphics.Color(0xFF8CC0DE))
            }
            if (more) {
                Text(stringResource(R.string.related_passages), style = MaterialTheme.typography.titleSmall)
                t.related.forEach { SourceCard(it, lang, onRead) }
            }
        }
        if (lang != "ar" && lang != "en" && a.kind != AnswerKind.INSUFFICIENT) {
            Text(stringResource(R.string.answer_language_note), style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = 0.7f))
        }
        Spacer(Modifier.height(6.dp))
        OtherSourcesRow(t.question, lang)
        Text(stringResource(R.string.answer_not_fatwa), style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = 0.7f))
    }
}

/** Summary in the interface language, else English; the label always says it is not yet reviewed. */
fun summaryFor(q: CommonQuestion, lang: String): String = q.summary[lang] ?: q.summary["en"].orEmpty()

/** Sites the app links to but does not copy: each opens its own search for the question when tapped. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OtherSourcesRow(question: String, lang: String) {
    val context = LocalContext.current
    val labels = mapOf(
        "islamqa" to R.string.site_islamqa, "dorar" to R.string.site_dorar,
        "binothaimeen" to R.string.site_binothaimeen, "alifta" to R.string.site_alifta,
    )
    Column(Modifier.padding(top = 6.dp)) {
        Text(stringResource(R.string.answer_search_more), style = MaterialTheme.typography.labelMedium, color = Accent)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            sa.zood.nearmosque.core.OtherSources.links(question, lang).forEach { l ->
                Text(
                    stringResource(labels.getValue(l.id)), color = androidx.compose.ui.graphics.Color(0xFF8CC0DE),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.heightIn(min = 40.dp).glass(RoundedCornerShape(50)).clickable(role = androidx.compose.ui.semantics.Role.Button) { ExternalActions.open(context, l.url) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
        Text(stringResource(R.string.answer_search_more_note), style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = 0.6f), modifier = Modifier.padding(top = 4.dp))
    }
}

