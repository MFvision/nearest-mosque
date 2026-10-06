package sa.zood.nearmosque.ui.ask

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sa.zood.nearmosque.R
import sa.zood.nearmosque.core.CommonQuestion
import sa.zood.nearmosque.data.SavedChat
import sa.zood.nearmosque.ui.glass.GlassButton
import sa.zood.nearmosque.ui.glass.GlassButtonText
import sa.zood.nearmosque.ui.glass.GlassCard
import sa.zood.nearmosque.ui.glass.glass
import sa.zood.nearmosque.ui.glass.loopingFloat
import sa.zood.nearmosque.ui.glass.reducedMotion
import sa.zood.nearmosque.ui.theme.Accent
import sa.zood.nearmosque.ui.theme.Ink
import sa.zood.nearmosque.ui.theme.Tokens
import java.text.DateFormat
import java.util.Date

/**
 * The waiting card: the logo breathing inside a turning gold ring, the steps already done with a
 * check, and the current step. Still under "remove animations".
 */
@Composable
fun ThinkingCard(stage: AskStage, done: List<AskStage>, onStop: () -> Unit) {
    val animate = !reducedMotion()
    val turn = loopingFloat(animate, 1600, reverse = false, label = "ring", still = 0f)
    val breathe = loopingFloat(animate, 900, reverse = true, label = "logo", still = 1f)
    val gold = Tokens.gold
    GlassCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().rotate(turn * 360f)) {
                    drawCircle(Brush.sweepGradient(listOf(gold.copy(alpha = 0f), gold, gold.copy(alpha = 0f))), size.minDimension / 2 - 2.dp.toPx(), style = Stroke(3.dp.toPx()))
                }
                Image(painterResource(R.drawable.logo_mark), contentDescription = null, modifier = Modifier.size(26.dp).scale(0.92f + 0.16f * breathe))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                done.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(rememberVectorPainter(Icons.Filled.Check), contentDescription = null, tint = Ink.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(s.label), style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = 0.7f))
                    }
                }
                AnimatedContent(stage, label = "stage", transitionSpec = { (fadeIn() + slideInVertically { it / 2 }) togetherWith fadeOut() }) { s ->
                    Text(
                        stringResource(s.label) + "…", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Ink,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            GlassButton(onClick = onStop) { GlassButtonText(stringResource(R.string.stop)) }
        }
    }
}

/** The library, large and first: books, fatwas and lectures to open directly. */
@Composable
fun LibraryEntryCard(onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(Tokens.cardRadius.dp)).clickable(role = Role.Button, onClick = onOpen).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).background(Tokens.gold.copy(alpha = 0.16f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_book), contentDescription = null, tint = Accent, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.library_title), style = MaterialTheme.typography.titleMedium, color = Ink)
            Text(stringResource(R.string.library_card_body), style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = 0.8f))
        }
    }
}

/** Up to three common questions not yet asked here, closest in wording to the last question first. */
@Composable
fun Suggestions(common: List<CommonQuestion>, turns: List<Turn>, lang: String, onAsk: (CommonQuestion, String) -> Unit) {
    val asked = turns.map { it.question }.toSet()
    fun words(s: String) = s.lowercase().split(Regex("[^\\p{L}]+")).filter { it.length > 2 }.toSet()
    val last = words(turns.lastOrNull()?.question.orEmpty())
    val ranked = common.mapIndexed { i, q -> Triple(i, q, q.question[lang] ?: q.question["en"] ?: q.id) }
        .filter { it.third !in asked }
        .sortedWith(compareByDescending<Triple<Int, CommonQuestion, String>> { (words(it.third) intersect last).size }.thenBy { it.first })
        .take(3)
    if (ranked.isEmpty()) return
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.suggested_questions), style = MaterialTheme.typography.titleSmall, color = Ink.copy(alpha = 0.8f), modifier = Modifier.semantics { heading() })
        ranked.forEach { (_, q, text) ->
            Text(
                "↳ $text", color = Ink, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.heightIn(min = 48.dp).glass(RoundedCornerShape(18.dp), shadow = 6.dp)
                    .clickable(role = Role.Button) { onAsk(q, text) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
    }
}

/** Saved chats: tap to open again, delete one or all. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHistorySheet(chats: List<SavedChat>, onOpen: (SavedChat) -> Unit, onDelete: (String) -> Unit, onDeleteAll: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, context.resources.configuration.locales[0])
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.chat_history), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.chat_history_note), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            if (chats.isEmpty()) Text(stringResource(R.string.chat_history_empty), modifier = Modifier.padding(vertical = 16.dp))
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(chats, key = { it.id }) { chat ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onOpen(chat) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(chat.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                            Text(fmt.format(Date(chat.updated)), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        IconButton(onClick = { onDelete(chat.id) }) {
                            Icon(rememberVectorPainter(Icons.Filled.Delete), contentDescription = stringResource(R.string.chat_delete))
                        }
                    }
                }
            }
            if (chats.isNotEmpty()) {
                TextButton(onClick = onDeleteAll, modifier = Modifier.heightIn(min = 48.dp).padding(bottom = 16.dp)) {
                    Text(stringResource(R.string.chat_delete_all), color = Color(0xFFD32F2F))
                }
            }
        }
    }
}
