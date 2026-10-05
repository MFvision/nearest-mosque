package sa.zood.nearmosque.ui.ask

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import sa.zood.nearmosque.R
import sa.zood.nearmosque.appContainer
import sa.zood.nearmosque.data.AskRepository
import sa.zood.nearmosque.data.LibraryFiles
import sa.zood.nearmosque.data.ResolvedCitation
import sa.zood.nearmosque.ui.Format
import sa.zood.nearmosque.ui.glass.glass
import sa.zood.nearmosque.ui.theme.Tokens

/** The library on its own: saved items, every installed collection to browse and search, downloaded books. */
@Composable
fun LibraryScreen(onDismiss: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF0B1220)).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.close), color = Color.White) }
                Text(stringResource(R.string.library_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
            TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = Tokens.gold) {
                listOf(R.string.library_saved, R.string.library_browse, R.string.library_downloads).forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(label), color = if (tab == i) Tokens.gold else Color.White) })
                }
            }
            when (tab) {
                0 -> SavedTab()
                1 -> BrowseTab()
                else -> DownloadsTab()
            }
        }
    }
}

@Composable
private fun Empty(text: String) {
    Text(text, Modifier.fillMaxWidth().padding(32.dp), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun SavedTab() {
    val c = appContainer()
    val ids by c.settings.bookmarks.collectAsState(initial = emptySet())
    var items by remember { mutableStateOf<List<ResolvedCitation>>(emptyList()) }
    LaunchedEffect(ids) { items = c.ask.resolve(ids.sorted()) }
    if (items.isEmpty()) { Empty(stringResource(R.string.library_saved_empty)); return }
    LazyColumn(contentPadding = PaddingValues(16.dp)) { items(items, key = { it.chunk.id }) { LibraryCard(it) } }
}

@Composable
private fun BrowseTab() {
    val context = LocalContext.current
    val container = appContainer()
    val c = container
    val lang = Format.languageCode(context)
    var packs by remember { mutableStateOf<List<AskRepository.LibraryPack>>(emptyList()) }
    var open by remember { mutableStateOf<AskRepository.LibraryPack?>(null) }
    LaunchedEffect(Unit) { packs = c.ask.libraryPacks() }
    val selected = open
    if (selected != null) { CollectionView(selected, lang, onBack = { open = null }); return }
    if (packs.isEmpty()) { Empty(stringResource(R.string.library_installing)); return }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(packs, key = { it.id }) { p ->
            Column(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp), tint = Color.White.copy(alpha = 0.04f)).clickable { open = p }.padding(16.dp),
            ) {
                Text(p.title[lang] ?: p.title["en"] ?: p.id, color = Color.White, style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.library_items_count, p.count), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CollectionView(p: AskRepository.LibraryPack, lang: String, onBack: () -> Unit) {
    val c = appContainer()
    val title = p.title[lang] ?: p.title["en"] ?: p.id
    var query by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(1) }
    var items by remember { mutableStateOf<List<ResolvedCitation>>(emptyList()) }
    LaunchedEffect(query, page) {
        if (query.isBlank()) items = c.ask.browse(p.id, 0, page * PAGE)
        else { delay(250); items = c.ask.searchIn(p.id, query) }
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("‹ " + stringResource(R.string.library_browse), color = Color(0xFF8CC0DE)) }
            Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
        OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text(stringResource(R.string.library_search_in, title)) }, singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Tokens.gold),
        )
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            items(items, key = { it.chunk.id }) { LibraryCard(it) }
            if (query.isBlank() && items.size == page * PAGE && items.size < p.count) {
                item { TextButton(onClick = { page++ }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.library_load_more), color = Color(0xFF8CC0DE)) } }
            }
        }
    }
}

private const val PAGE = 40

@Composable
private fun DownloadsTab() {
    val context = LocalContext.current
    val container = appContainer()
    val files = remember { LibraryFiles(context) }
    var version by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<Pair<ResolvedCitation?, java.io.File>>>(emptyList()) }
    LaunchedEffect(version) {
        val list = files.downloaded()
        val resolved = container.ask.resolve(list.map { it.first }).associateBy { it.chunk.id }
        items = list.map { (id, f) -> resolved[id] to f }
    }
    if (items.isEmpty()) { Empty(stringResource(R.string.library_downloads_empty)); return }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(android.text.format.Formatter.formatShortFileSize(context, items.sumOf { it.second.length() }), Modifier.weight(1f), color = Color.White.copy(alpha = 0.8f))
                TextButton(onClick = { files.deleteAll(); version++ }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.library_delete_all), color = Color(0xFFE8A0A0)) }
            }
        }
        items(items, key = { it.second.name }) { (r, f) ->
            Column {
                if (r != null) LibraryCard(r) else Text(f.name, color = Color.White)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(android.text.format.Formatter.formatShortFileSize(context, f.length()), Modifier.weight(1f), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { files.delete(f); version++ }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.library_delete), color = Color(0xFFE8A0A0)) }
                }
            }
        }
    }
}
