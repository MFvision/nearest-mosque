package sa.zood.nearmosque.ui.onboarding

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import sa.zood.nearmosque.AppStyle
import sa.zood.nearmosque.R
import sa.zood.nearmosque.core.CommonQuestion
import sa.zood.nearmosque.core.OnboardingFeature
import sa.zood.nearmosque.core.OnboardingStep
import sa.zood.nearmosque.core.SourceChunk
import sa.zood.nearmosque.platform.PrayerWidgetRingSmall
import sa.zood.nearmosque.platform.PrayerWidgetSmall
import sa.zood.nearmosque.platform.PrayerWidgetToday
import sa.zood.nearmosque.platform.PrayerWidgets
import sa.zood.nearmosque.platform.QiblaWidgetSmall
import sa.zood.nearmosque.platform.WidgetExtras
import sa.zood.nearmosque.platform.WidgetKind
import sa.zood.nearmosque.platform.WidgetStyle
import sa.zood.nearmosque.ui.Format
import sa.zood.nearmosque.ui.ask.referenceLabel
import sa.zood.nearmosque.ui.glass.glass
import sa.zood.nearmosque.ui.theme.Accent
import sa.zood.nearmosque.ui.theme.Ink
import sa.zood.nearmosque.ui.theme.LocalSky
import sa.zood.nearmosque.ui.theme.Tokens

/* "Near Mosque, everywhere" (the widgets, the app shortcuts and Ask) and "Also in Near Mosque" (the rest). */

/** A widget offered on the extras step: what it draws, its size and the provider a launcher pins. */
private data class WidgetPreview(val kind: WidgetKind, val name: Int, val width: Dp, val height: Dp, val provider: Class<*>)

private val PREVIEWS = listOf(
    WidgetPreview(WidgetKind.SMALL, R.string.widget_kind_next, 160.dp, 160.dp, PrayerWidgetSmall::class.java),
    WidgetPreview(WidgetKind.TODAY, R.string.widget_kind_today, 340.dp, 180.dp, PrayerWidgetToday::class.java),
    WidgetPreview(WidgetKind.RING_SMALL, R.string.widget_kind_ring, 160.dp, 160.dp, PrayerWidgetRingSmall::class.java),
    WidgetPreview(WidgetKind.QIBLA_SMALL, R.string.widget_kind_qibla, 160.dp, 160.dp, QiblaWidgetSmall::class.java),
)

/**
 * Near Mosque, everywhere: the real home-screen widgets in the chosen style (drawn from their RemoteViews, not
 * clickable), with Add where the launcher can pin one; the app icon's shortcuts; and Ask with a reviewed answer and its
 * source. Only scrolling and Add are interactive.
 */
@Composable
internal fun ExtrasStep(ctx: OnbContext) {
    val context = LocalContext.current
    // ChromeOS and Android XR put no app widgets on a home screen.
    val widgets = remember {
        val pm = context.packageManager
        !pm.hasSystemFeature(PackageManager.FEATURE_PC) && !pm.hasSystemFeature("android.software.xr.api.spatial")
    }
    StepScaffold(ctx, bottom = { PrimaryButton(stringResource(R.string.onb2_continue), onClick = { ctx.go(OnboardingStep.MORE) }) }) {
        OnbTitle(stringResource(R.string.onb2_extras_title), Modifier.enter(0))
        Spacer(Modifier.height(8.dp))
        OnbBody(stringResource(R.string.onb2_extras_body), Modifier.enter(1))
        Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (widgets) WidgetsCard(ctx, Modifier.enter(2))
            ShortcutsCard(Modifier.enter(3))
            AskCard(ctx, Modifier.enter(4))
        }
    }
}

@Composable
private fun WidgetsCard(ctx: OnbContext, modifier: Modifier) {
    val context = LocalContext.current
    val settings = ctx.ui.settings ?: return
    val lang = Format.languageCode(context)
    val extras by produceState(WidgetExtras(), settings.prayerLocation, lang) { value = WidgetExtras.load(ctx.container, settings, lang) }
    val style = WidgetStyle.of(AppStyle.current) ?: WidgetStyle.CREAM
    val canPin = remember { runCatching { AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported }.getOrDefault(false) }
    ShowcaseCard(stringResource(R.string.onb2_widgets), modifier) {
        Text(stringResource(R.string.onb2_widgets_style, stringResource(style.label)), color = InkSoft, style = MaterialTheme.typography.bodySmall)
        // The row runs to the card's edges (past its padding), so a preview scrolls out under the glass edge.
        LazyRow(
            Modifier.layout { m, c ->
                val extra = 32.dp.roundToPx()
                val p = m.measure(c.copy(minWidth = c.maxWidth + extra, maxWidth = c.maxWidth + extra))
                layout(c.maxWidth, p.height) { p.place(-extra / 2, 0) }
            },
            horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
        ) {
            items(PREVIEWS, key = { it.kind.name }) { p ->
                val name = stringResource(p.name)
                val a11y = stringResource(R.string.onb2_preview_a11y, name)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RemoteWidget(ctx, settings, p, style, extras, Modifier.semantics { contentDescription = a11y })
                    Spacer(Modifier.height(6.dp))
                    Text(name, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                    if (canPin) {
                        TextAction(stringResource(R.string.onb2_widget_add), onClick = {
                            runCatching { AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, p.provider), null, null) }
                        })
                    }
                }
            }
        }
        Text(stringResource(R.string.onb2_widgets_howto_android), color = InkSoft, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * One widget's real RemoteViews, applied into a frame. Its PendingIntents are cleared (a preview opens nothing) and
 * TalkBack reads the label instead of the views inside. Redrawn when the style changes.
 */
@Composable
private fun RemoteWidget(ctx: OnbContext, settings: sa.zood.nearmosque.data.AppSettings, p: WidgetPreview, style: WidgetStyle, extras: WidgetExtras, modifier: Modifier) {
    val density = LocalDensity.current
    val w = with(density) { p.width.roundToPx() }
    val h = with(density) { p.height.roundToPx() }
    // Widgets keep their own layout direction; the row of previews follows the reading direction.
    AndroidView(
        factory = { c -> FrameLayout(c).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS } },
        update = { frame ->
            val (views, _) = PrayerWidgets.views(frame.context, ctx.container, settings, p.kind, style, extras)
            frame.removeAllViews()
            frame.addView(views.apply(frame.context, frame), FrameLayout.LayoutParams(w, h))
            clearClicks(frame)
        },
        modifier = modifier.size(p.width, p.height).clearAndSetSemantics { },
    )
}

private fun clearClicks(v: View) {
    v.setOnClickListener(null)
    v.isClickable = false
    v.isLongClickable = false
    if (v is ViewGroup) for (i in 0 until v.childCount) clearClicks(v.getChildAt(i))
}

/** The app icon's long-press shortcuts (res/xml/shortcuts.xml), as chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShortcutsCard(modifier: Modifier) {
    ShowcaseCard(stringResource(R.string.onb2_shortcuts_title), modifier) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.drawable.ic_tab_prayer to R.string.qibla, R.drawable.ic_tab_mosque to R.string.tab_mosques, R.drawable.ic_sparkle to R.string.tab_ask).forEach { (icon, label) ->
                SmallChip(painterResource(icon), stringResource(label))
            }
        }
        Text(stringResource(R.string.onb2_shortcuts_android), color = InkSoft, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SmallChip(icon: Painter, text: String) {
    val bg = if (LocalSky.current.dark) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.62f)
    Row(
        Modifier.heightIn(min = 32.dp).background(bg, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
    }
}

/**
 * Ask: the common question about the Friday prayer (or the first one) with its label (reviewed, or awaiting review),
 * its summary and the source it quotes. Opens nothing; read as one.
 */
@Composable
private fun AskCard(ctx: OnbContext, modifier: Modifier) {
    val context = LocalContext.current
    val lang = Format.languageCode(context)
    val data by produceState<Pair<CommonQuestion, SourceChunk?>?>(null) {
        val q = runCatching { ctx.container.ask.commonQuestions() }.getOrNull()?.let { all -> all.firstOrNull { it.id == "friday-prayer" } ?: all.firstOrNull() }
        value = q?.let { question ->
            val chunk = question.citations.firstOrNull()?.let { id -> runCatching { ctx.container.ask.resolve(listOf(id)) }.getOrNull()?.firstOrNull()?.chunk }
            question to chunk
        }
    }
    ShowcaseCard(stringResource(R.string.tab_ask), modifier.semantics(mergeDescendants = true) { }) {
        Text(stringResource(R.string.onb2_ask_line), color = InkSoft, style = MaterialTheme.typography.bodySmall)
        AnimatedVisibility(data != null, enter = fadeIn(tween(400))) {
            val (q, chunk) = data ?: return@AnimatedVisibility
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val question = q.question[lang] ?: q.question["en"].orEmpty()
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Text(
                        question, color = Ink, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.widthIn(max = 300.dp).background(Tokens.gold.copy(alpha = 0.18f), RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
                Text(
                    if (q.isReviewed) stringResource(R.string.answer_common_reviewed, q.reviewedBy.orEmpty()) else stringResource(R.string.answer_common_unreviewed),
                    color = Accent, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(q.summary[lang] ?: q.summary["en"].orEmpty(), color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                val ref = chunk?.let { referenceLabel(it, lang) } ?: q.citations.firstOrNull()?.let { quranReference(it) }
                if (ref != null) SmallChip(painterResource(R.drawable.ic_book), ref)
            }
        }
    }
}

/** "quran:2:255" as "Quran 2:255" when the citation cannot be resolved from the installed packs. */
@Composable
private fun quranReference(id: String): String? {
    val parts = id.split(':')
    if (parts.size != 3 || parts[0] != "quran") return null
    val s = parts[1].toIntOrNull() ?: return null
    val a = parts[2].toIntOrNull() ?: return null
    return stringResource(R.string.reference_quran, s, a)
}

/** Each "Also in Near Mosque" tile: its name, its line and its icon. */
@Composable
private fun featureText(f: OnboardingFeature): Triple<String, String, Painter>? = when (f) {
    OnboardingFeature.IQAMA -> Triple(stringResource(R.string.onb2_more_iqama), stringResource(R.string.onb2_more_iqama_sub), painterResource(R.drawable.ic_timer))
    OnboardingFeature.BEFORE_PRAYER -> Triple(stringResource(R.string.onb2_more_before), stringResource(R.string.onb2_more_before_sub), painterResource(R.drawable.ic_bell))
    OnboardingFeature.FAJR_ALARM -> Triple(stringResource(R.string.alerts_fajr_alarm), stringResource(R.string.onb2_more_fajr_sub), painterResource(R.drawable.ic_alarm))
    OnboardingFeature.QUIET -> Triple(stringResource(R.string.silence_section), stringResource(R.string.onb2_more_quiet_sub_android), painterResource(R.drawable.ic_bell_off))
    OnboardingFeature.QIBLA_COMPASS -> Triple(stringResource(R.string.onb2_more_compass), stringResource(R.string.onb2_more_compass_sub), painterResource(R.drawable.ic_compass))
    OnboardingFeature.QIBLA_NORTH -> Triple(stringResource(R.string.onb2_more_qibla_north), stringResource(R.string.onb2_more_qibla_north_sub), painterResource(R.drawable.ic_kaaba))
    OnboardingFeature.MOSQUES -> Triple(stringResource(R.string.onb2_more_mosques), stringResource(R.string.onb2_more_mosques_sub), painterResource(R.drawable.ic_tab_mosque))
    OnboardingFeature.LIBRARY -> Triple(stringResource(R.string.onb2_more_library), stringResource(R.string.onb2_more_library_sub), painterResource(R.drawable.ic_book))
    OnboardingFeature.CLASSICS -> Triple(stringResource(R.string.classics_title), stringResource(R.string.onb2_more_classics_sub), painterResource(R.drawable.ic_book))
    OnboardingFeature.HIJRI -> Triple(stringResource(R.string.onb2_more_hijri), stringResource(R.string.onb2_more_hijri_sub), rememberVectorPainter(Icons.Filled.DateRange))
    OnboardingFeature.TASBIH, OnboardingFeature.START_AT_SIGN_IN -> null
}

/**
 * Also in Near Mosque: the rest of the app as tiles, two to a row (one on narrow screens and with large text), each read
 * as one. Quiet during prayer is left out when the Adhan step already offered it; the compass replaces the bearing
 * from north where the phone has one.
 */
@Composable
internal fun MoreStep(ctx: OnbContext) {
    val compass = remember { ctx.container.heading.hasCompass }
    val available = buildSet {
        addAll(listOf(OnboardingFeature.IQAMA, OnboardingFeature.BEFORE_PRAYER, OnboardingFeature.FAJR_ALARM, OnboardingFeature.QUIET))
        add(if (compass) OnboardingFeature.QIBLA_COMPASS else OnboardingFeature.QIBLA_NORTH)
        addAll(listOf(OnboardingFeature.MOSQUES, OnboardingFeature.LIBRARY, OnboardingFeature.HIJRI))
    }
    val shown = if (ctx.adhanVisited) setOf(OnboardingFeature.QUIET) else emptySet()
    val features = OnboardingFeature.more(available, shown)
    StepScaffold(ctx, bottom = { PrimaryButton(stringResource(R.string.onb2_continue), onClick = { ctx.go(OnboardingStep.PRIVACY) }) }) {
        OnbTitle(stringResource(R.string.onb2_more_title), Modifier.enter(0))
        Spacer(Modifier.height(16.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth < 340.dp || LocalDensity.current.fontScale >= 1.3f) 1 else 2
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                features.chunked(columns).forEachIndexed { r, row ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEachIndexed { c, f ->
                            val (name, sub, icon) = featureText(f) ?: return@forEachIndexed
                            Column(
                                Modifier.weight(1f).fillMaxHeight().enter(1 + r * columns + c).heightIn(min = 96.dp)
                                    .glass(CardShape, onbGlassTint(), shadow = 6.dp).padding(14.dp)
                                    .semantics(mergeDescendants = true) { },
                            ) {
                                IconCircle(icon, 36.dp)
                                Spacer(Modifier.height(8.dp))
                                Text(name, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp))
                                Spacer(Modifier.height(2.dp))
                                Text(sub, color = InkSoft, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (row.size < columns) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        OnbFoot(stringResource(R.string.onb2_more_foot), Modifier.padding(top = 12.dp).enter(features.size + 1))
    }
}
