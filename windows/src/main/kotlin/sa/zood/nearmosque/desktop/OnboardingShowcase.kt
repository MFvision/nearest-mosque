package sa.zood.nearmosque.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sa.zood.nearmosque.core.OnboardingFeature
import sa.zood.nearmosque.core.OnboardingStep
import java.util.concurrent.ConcurrentHashMap

/*
 * The onboarding's showcase on the desktop: "Near Mosque, everywhere" (the notification area, where the PC has one,
 * and Ask with a real common answer) and "Also in Near Mosque" (the rest of the app as tiles). Nothing here is
 * interactive; each card is read as one.
 */

/**
 * Near Mosque, everywhere: the notification area (the app stays there with the window closed, its icon telling the
 * next prayer) where the desktop has one, else that closing the window ends the Adhan, and Ask with a common answer
 * and the source it quotes.
 */
@Composable
internal fun ExtrasStep(o: Onb) {
    StepFrame(o, button = { OnbPrimaryButton(t("onb2_continue"), onClick = { o.go(OnboardingStep.MORE) }) }) {
        OnbTitle(t("onb2_extras_title"), Modifier.enter(0))
        Spacer(Modifier.height(10.dp))
        OnbBody(t("onb2_extras_body"), Modifier.enter(1))
        Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (o.tray) TrayCard(o, Modifier.enter(2)) else NoTrayCard(Modifier.enter(2))
            AskCard(Modifier.enter(3))
        }
    }
}

/** A showcase card's title (15 semibold, a heading for screen readers). */
@Composable
private fun CardTitle(text: String) {
    Text(text, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
}

/**
 * The notification area, drawn (not clickable): the icon's tooltip with the real next prayer (trayStatus, as the icon
 * shows it) and the icon's menu with its real items.
 */
@Composable
private fun TrayCard(o: Onb, modifier: Modifier) {
    val status = trayStatus(o.clock, o.now)
    val preview = t("onb2_preview_a11y", t("onb2_tray_title"))
    GlassCard(modifier) {
        CardTitle(t("onb2_tray_title"))
        Spacer(Modifier.height(4.dp))
        Text(t("onb2_tray_body") + " " + t("tray_quit_note"), color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = preview }, verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                // The tooltip, above the strip where the icon sits.
                Text(
                    status, color = Ink, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.background(Navy, RoundedCornerShape(8.dp)).border(1.dp, OnbLook.line, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().height(30.dp).background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(3) { Box(Modifier.size(12.dp).background(OnbLook.track, RoundedCornerShape(3.dp))); Spacer(Modifier.width(8.dp)) }
                    Image(painterResource("icon.png"), contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier.widthIn(min = 110.dp, max = 150.dp).background(Navy, RoundedCornerShape(8.dp)).border(1.dp, OnbLook.line, RoundedCornerShape(8.dp))
                    .padding(vertical = 4.dp),
            ) {
                listOf("tab_prayer", "stop", "tray_close_window", "tray_quit").forEach { key ->
                    Text(t(key), color = Ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
                }
            }
        }
    }
}

/** No notification area: the window is the app, so closing it stops the Adhan (said before the first close). */
@Composable
private fun NoTrayCard(modifier: Modifier) {
    GlassCard(modifier.semantics(mergeDescendants = true) { }) {
        Text(t("no_tray_window_note"), color = Ink, fontSize = 14.sp, lineHeight = 19.sp)
    }
}

/** The Ask card's content: the question, its label, the summary and the source it quotes. */
internal data class AskPreview(val question: String, val label: String, val summary: String, val source: String?)

/**
 * The common question about the Friday prayer (or the first one), read once per language off the window's thread:
 * the first time loads the books Ask searches (a few seconds), which the Ask screen needs anyway.
 */
internal object AskPreviews {
    private val cache = ConcurrentHashMap<String, AskPreview>()

    fun peek(lang: String): AskPreview? = cache[lang]

    fun load(lang: String): AskPreview? = cache[lang] ?: runCatching {
        val all = AskEngine.questions
        val q = all.firstOrNull { it.id == "friday-prayer" } ?: all.firstOrNull() ?: return null
        val label = if (q.isReviewed) t("answer_common_reviewed", q.reviewedBy.orEmpty()) else t("answer_common_unreviewed")
        val cited = q.citations.firstOrNull()
        val source = cited?.let { id -> AskEngine.chunk(id)?.let(AskEngine::reference)?.takeIf { it.isNotBlank() } ?: quranReference(id) }
        AskPreview(AskEngine.question(q, lang), label, AskEngine.summary(q, lang), source)
    }.getOrNull()?.also { cache[lang] = it }

    /** "quran:62:9" as "Quran 62:9" when the citation cannot be found in the books. */
    private fun quranReference(id: String): String? {
        val parts = id.split(':')
        if (parts.size != 3 || parts[0] != "quran") return null
        val s = parts[1].toIntOrNull() ?: return null
        val a = parts[2].toIntOrNull() ?: return null
        return t("reference_quran", s, a)
    }
}

/** Ask: a common answer with its label (reviewed, or awaiting review), its summary and its source. It fades in when read. */
@Composable
private fun AskCard(modifier: Modifier) {
    val lang = Strings.language
    val data by produceState(AskPreviews.peek(lang), lang) {
        if (value == null) value = withContext(Dispatchers.Default) { AskPreviews.load(lang) }
    }
    val still = LocalOnbStill.current
    val shown by animateFloatAsState(if (data != null) 1f else 0f, if (still) snap() else tween(400), label = "ask")
    GlassCard(modifier.semantics(mergeDescendants = true) { }) {
        CardTitle(t("tab_ask"))
        Spacer(Modifier.height(4.dp))
        Text(t("onb2_ask_line"), color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
        val d = data ?: return@GlassCard
        Column(Modifier.padding(top = 12.dp).graphicsLayer { alpha = shown }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Text(
                    d.question, color = Ink, fontSize = 14.sp, lineHeight = 19.sp,
                    modifier = Modifier.widthIn(max = 300.dp).background(OnbLook.accentSoft, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            Text(d.label, color = Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(d.summary, color = Ink, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            d.source?.let { SmallChip(onbIcon("ic_book"), it) }
        }
    }
}

@Composable
private fun SmallChip(icon: Painter, text: String) {
    Row(
        Modifier.heightIn(min = 28.dp).background(OnbLook.chip, OnbLook.capsule).border(1.dp, OnbLook.chipLine, OnbLook.capsule).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Gold, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** What the desktop has of "Also in Near Mosque": no compass on a PC (the bearing from north), the classical books for a library. */
internal fun desktopFeatures(startup: Boolean): Set<OnboardingFeature> = buildSet {
    addAll(
        listOf(
            OnboardingFeature.IQAMA, OnboardingFeature.BEFORE_PRAYER, OnboardingFeature.FAJR_ALARM, OnboardingFeature.QIBLA_NORTH,
            OnboardingFeature.MOSQUES, OnboardingFeature.CLASSICS, OnboardingFeature.HIJRI,
        ),
    )
    if (startup) add(OnboardingFeature.START_AT_SIGN_IN)
}

/** Each tile: its name, its line and its icon (null for what the desktop does not have). */
@Composable
private fun featureText(f: OnboardingFeature): Triple<String, String, Painter>? = when (f) {
    OnboardingFeature.IQAMA -> Triple(t("onb2_more_iqama"), t("onb2_more_iqama_sub"), onbIcon("ic_timer"))
    OnboardingFeature.BEFORE_PRAYER -> Triple(t("onb2_more_before"), t("onb2_more_before_sub"), onbIcon("ic_bell"))
    OnboardingFeature.FAJR_ALARM -> Triple(t("alerts_fajr_alarm"), t("onb2_more_fajr_sub"), onbIcon("ic_alarm"))
    OnboardingFeature.QIBLA_NORTH -> Triple(t("onb2_more_qibla_north"), t("onb2_more_qibla_north_sub"), onbIcon("ic_kaaba"))
    OnboardingFeature.MOSQUES -> Triple(t("onb2_more_mosques"), t("onb2_more_mosques_sub"), onbIcon("ic_tab_mosque"))
    OnboardingFeature.CLASSICS -> Triple(t("classics_title"), t("onb2_more_classics_sub"), onbIcon("ic_book"))
    OnboardingFeature.HIJRI -> Triple(t("onb2_more_hijri"), t("onb2_more_hijri_sub"), rememberVectorPainter(OnbIcons.date))
    OnboardingFeature.START_AT_SIGN_IN ->
        Triple(t(if (Platform.isLinux) "start_at_login_linux" else "start_at_login"), t("onb2_more_startup_sub"), onbIcon("ic_onb_startup"))
    OnboardingFeature.QUIET, OnboardingFeature.QIBLA_COMPASS, OnboardingFeature.LIBRARY, OnboardingFeature.TASBIH -> null
}

/** Also in Near Mosque: the rest of the app as tiles, two to a row (one when narrow), each read as one. */
@Composable
internal fun MoreStep(o: Onb) {
    val features = OnboardingFeature.more(desktopFeatures(o.startup))
    StepFrame(o, button = { OnbPrimaryButton(t("onb2_continue"), onClick = { o.go(OnboardingStep.PRIVACY) }) }) {
        OnbTitle(t("onb2_more_title"), Modifier.enter(0))
        Spacer(Modifier.height(16.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth < 340.dp) 1 else 2
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                features.chunked(columns).forEachIndexed { r, row ->
                    // One to a row: the icon beside the words, so the list stays short.
                    if (columns == 1) {
                        val (name, sub, icon) = featureText(row.first()) ?: return@forEachIndexed
                        Row(
                            Modifier.fillMaxWidth().enter(1 + r).heightIn(min = 64.dp).glass(OnbLook.card, OnbLook.fill)
                                .padding(horizontal = 14.dp, vertical = 12.dp).semantics(mergeDescendants = true) { },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconCircle(icon, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, color = Ink, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
                                Text(sub, color = Muted, fontSize = 13.sp, lineHeight = 17.sp)
                            }
                        }
                        return@forEachIndexed
                    }
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEachIndexed { c, f ->
                            val (name, sub, icon) = featureText(f) ?: return@forEachIndexed
                            Column(
                                Modifier.weight(1f).fillMaxHeight().enter(1 + r * columns + c).heightIn(min = 96.dp)
                                    .glass(OnbLook.card, OnbLook.fill).padding(14.dp).semantics(mergeDescendants = true) { },
                            ) {
                                IconCircle(icon, 36.dp)
                                Spacer(Modifier.height(8.dp))
                                Text(name, color = Ink, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(2.dp))
                                Text(sub, color = Muted, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (row.size < columns) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        OnbFoot(t("onb2_more_foot"), Modifier.padding(top = 12.dp).enter(features.size + 1))
    }
}
