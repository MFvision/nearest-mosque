package sa.zood.nearmosque.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import sa.zood.nearmosque.R
import sa.zood.nearmosque.ui.glass.drawSkyline
import sa.zood.nearmosque.ui.glass.glass
import sa.zood.nearmosque.ui.glass.loopingFloat
import sa.zood.nearmosque.ui.theme.Accent
import sa.zood.nearmosque.ui.theme.Ink
import sa.zood.nearmosque.ui.theme.LocalSky
import sa.zood.nearmosque.ui.theme.Sky
import sa.zood.nearmosque.ui.theme.SkyPeriod
import sa.zood.nearmosque.ui.theme.Tokens
import kotlin.math.roundToInt

/* The onboarding's parts: text on the sky, the main buttons, glass rows and switch rows, the "YOUR DAY" header with
 * its chips, the sun and the moon, the Friday picture and the Allow button. Colours come from the sky (LocalSky) and
 * the chosen style (Tokens.gold), so the look step's choice shows everywhere at once. */

/** Cards on Android and the desktop (the panel is 22). */
internal val CardShape = RoundedCornerShape(16.dp)

/** Sun and moon (shared/design/tokens.json: sunCore, sunMid, sunEdge, moonlight, moonlightLight). */
internal object HeroColors {
    val sunCore = Color(0xFFFFF6DA)
    val sunMid = Color(0xFFF7C76A)
    val sunEdge = Color(0xFFE9A23B)
    val moonlightLight = Color(0xFF8EA4D6)
}

/** Text on the sky: in dark mode a soft shadow keeps it legible over the bright horizon glow. */
@Composable
internal fun skyText(style: TextStyle): TextStyle =
    if (LocalSky.current.dark) style.copy(shadow = Shadow(Color.Black.copy(alpha = 0.3f), blurRadius = 7f * LocalDensity.current.density)) else style

/**
 * The onboarding's glass: a little denser than the app's at night (low at 50%), because its rows and cards sit over the
 * stars and the bright horizon of a full-screen sky; frosted white on the light skies.
 */
@Composable
internal fun onbGlassTint(): Color = LocalSky.current.let { if (it.dark) it.low.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.6f) }

/** Secondary text on the sky: 80% ink at night, the token grey on the light skies. */
internal val InkSoft: Color @Composable get() = LocalSky.current.let { if (it.dark) it.ink.copy(alpha = 0.8f) else Tokens.textSecondaryLight }

/** Uppercase with 0.08em tracking only for scripts with letter case (never for Arabic script, where spacing breaks the joins). */
@Composable
internal fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val cased = !rtl && text.any { it.isLowerCase() }
    Text(
        if (cased) text.uppercase() else text, color = Accent, modifier = modifier,
        style = skyText(MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = if (cased) 0.08.em else 0.sp)),
    )
}

@Composable
internal fun OnbTitle(text: String, modifier: Modifier = Modifier, large: Boolean = false, align: TextAlign = TextAlign.Start) {
    val base = if (large) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium
    Text(
        text, color = Ink, textAlign = align,
        style = skyText(base.copy(fontWeight = FontWeight.Bold)),
        modifier = modifier.fillMaxWidth().semantics { heading() },
    )
}

@Composable
internal fun OnbBody(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    Text(text, color = InkSoft, textAlign = align, style = skyText(MaterialTheme.typography.bodyLarge), modifier = modifier.fillMaxWidth())
}

@Composable
internal fun OnbFoot(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start, color: Color = InkSoft) {
    Text(text, color = color, textAlign = align, style = skyText(MaterialTheme.typography.bodySmall), modifier = modifier.fillMaxWidth())
}

/** The main button: a 54 dp gold capsule with dark text; two lines for long translations. */
@Composable
internal fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(50)
    Box(
        modifier.fillMaxWidth().heightIn(min = 54.dp).pressScale(source)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .clip(shape).background(Tokens.gold, shape).border(1.dp, Color.White.copy(alpha = 0.35f), shape)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = Tokens.navyNight, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

/** The quieter twin of [PrimaryButton]: glass with ink text (Not now). */
@Composable
internal fun GlassWideButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(50)
    Box(
        modifier.fillMaxWidth().heightIn(min = 54.dp).pressScale(source).glass(shape, onbGlassTint(), shadow = 6.dp)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
    }
}

/** A text button in the accent (Skip, Change, Set up). */
@Composable
internal fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Accent) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier.heightIn(min = 44.dp).pressScale(source).clip(RoundedCornerShape(50))
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, style = skyText(MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)))
    }
}

/** Back (a 44 dp glass circle with the arrow that follows the reading direction) and Skip. */
@Composable
internal fun TopBar(onBack: (() -> Unit)?, onSkip: (() -> Unit)?, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(onBack != null, enter = androidx.compose.animation.fadeIn(tween(300)), exit = fadeOut(tween(150))) {
            val label = stringResource(R.string.onb_back)
            val source = remember { MutableInteractionSource() }
            Box(
                Modifier.size(44.dp).pressScale(source).glass(CircleShape, onbGlassTint(), shadow = 6.dp)
                    .clickable(interactionSource = source, indication = null, role = Role.Button, onClickLabel = label) { onBack?.invoke() }
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Icon(rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack), contentDescription = null, tint = Ink, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
        AnimatedVisibility(onSkip != null, enter = androidx.compose.animation.fadeIn(tween(300)), exit = fadeOut(tween(150))) {
            TextAction(stringResource(R.string.onb_skip), onClick = { onSkip?.invoke() }, color = Ink)
        }
    }
}

/** A 40 dp circle with an accent icon (rows and tiles). */
@Composable
internal fun IconCircle(icon: Painter, size: Dp = 40.dp) {
    Box(Modifier.size(size).background(Tokens.gold.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(size * 0.5f))
    }
}

/** A round check in the accent that pops in. */
@Composable
internal fun CheckBadge(modifier: Modifier = Modifier, size: Dp = 24.dp, color: Color = Tokens.gold) {
    Box(modifier.size(size).pop().background(color, CircleShape), contentAlignment = Alignment.Center) {
        Icon(rememberVectorPainter(Icons.Filled.Check), contentDescription = null, tint = Tokens.navyNight, modifier = Modifier.size(size * 0.66f))
    }
}

/**
 * The accent ring of a selected row or card (2 dp), and the pulse when it is chosen: a ring growing from 2 to 18 dp
 * that fades from 75% to nothing in 650 ms (not under Reduce Motion).
 */
internal fun Modifier.selectionRing(selected: Boolean, pulseKey: Int, shape: androidx.compose.ui.graphics.Shape, color: Color): Modifier = this.then(
    Modifier.composedRing(selected, pulseKey, shape, color),
)

private fun Modifier.composedRing(selected: Boolean, pulseKey: Int, shape: androidx.compose.ui.graphics.Shape, color: Color): Modifier = composed {
    val reduce = LocalOnbReduce.current
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(pulseKey) {
        if (pulseKey > 0 && !reduce) { pulse.snapTo(0f); pulse.animateTo(1f, tween(650, easing = LinearEasing)) }
    }
    val ring by animateFloatAsState(if (selected) 1f else 0f, tween(200), label = "ring")
    drawWithContent {
        drawContent()
        val outline = shape.createOutline(size, layoutDirection, this)
        val r = (outline as? androidx.compose.ui.graphics.Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f
        // Rings sit just outside the edge: [grow] is how far the stroke's centre is from it.
        fun ring(grow: Float, c: Color) = drawRoundRect(
            c, topLeft = Offset(-grow, -grow), size = Size(size.width + 2 * grow, size.height + 2 * grow),
            cornerRadius = CornerRadius(r + grow), style = Stroke(2.dp.toPx()),
        )
        if (ring > 0f) ring(1.dp.toPx(), color.copy(alpha = ring))
        val p = pulse.value
        if (p < 1f) ring((1 + 8 * p).dp.toPx(), color.copy(alpha = 0.75f * (1 - p)))
    }
}

/**
 * A glass row: an accent icon circle, the title and a sub line, and at the end a chevron, a spinner or a check. Selected
 * rows are ringed. [role] is Button for actions, RadioButton inside a choice group.
 */
@Composable
internal fun OnbRow(
    icon: Painter, title: String, sub: String?, onClick: () -> Unit, modifier: Modifier = Modifier,
    selected: Boolean = false, busy: Boolean = false, pulseKey: Int = 0, role: Role = Role.Button, showChevron: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val sky = LocalSky.current
    Row(
        modifier.fillMaxWidth().heightIn(min = 74.dp).pressScale(source)
            .selectionRing(selected, pulseKey, CardShape, Tokens.gold)
            .glass(CardShape, onbGlassTint(), shadow = 8.dp)
            .then(
                if (role == Role.RadioButton) Modifier.selectable(selected, interactionSource = source, indication = null, role = role, onClick = onClick)
                else Modifier.clickable(interactionSource = source, indication = null, role = role, onClick = onClick),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircle(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Ink, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            sub?.let { Text(it, color = InkSoft, style = MaterialTheme.typography.bodySmall) }
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when {
                busy -> CircularProgressIndicator(Modifier.size(18.dp), color = Accent, strokeWidth = 2.dp)
                selected -> Icon(rememberVectorPainter(Icons.Filled.Check), contentDescription = null, tint = Accent, modifier = Modifier.size(22.dp).pop())
                showChevron -> Icon(rememberVectorPainter(Icons.AutoMirrored.Filled.KeyboardArrowRight), contentDescription = null, tint = InkSoft, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** A glass row that is one switch: the whole row toggles. */
@Composable
internal fun SwitchRow(icon: Painter?, title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    Row(
        modifier.fillMaxWidth().heightIn(min = if (icon != null) 74.dp else 58.dp).pressScale(source)
            .glass(CardShape, onbGlassTint(), shadow = 8.dp)
            .toggleable(checked, interactionSource = source, indication = null, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconCircle(icon)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Ink, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            sub?.let { Text(it, color = InkSoft, style = MaterialTheme.typography.bodySmall) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = Tokens.gold, checkedThumbColor = Color.White, checkedBorderColor = Tokens.gold),
        )
    }
}

/** One answer in the "YOUR DAY" header. [bump] changes when the same answer is chosen again (a small bounce). */
internal data class DayChip(val key: String, val icon: Painter, val text: String, val bump: Int = 0)

/** A header slot: the chip once answered; before that a dashed pill [placeholder] wide (or nothing). */
internal data class ChipSlot(val key: String, val chip: DayChip?, val placeholder: Dp?)

/**
 * "YOUR DAY": a glass capsule with the sun's slot, the eyebrow and the answers as chips, which fly up into it as they are
 * given (from below, or from the panel's side when wide) and scroll sideways when they overflow. Read as one phrase
 * ("Your day: Riyadh, Umm al-Qura, …"), politely announced as it fills. [onSlot] gets the slot's centre in root pixels.
 */
@Composable
internal fun DayHeader(slots: List<ChipSlot>, wide: Boolean, visible: Boolean, onSlot: (Offset) -> Unit, modifier: Modifier = Modifier) {
    val reduce = LocalOnbReduce.current
    val title = stringResource(R.string.onb2_your_day)
    val chips = slots.mapNotNull { it.chip }
    val description = if (chips.isEmpty()) title else title + ": " + chips.joinToString(", ") { it.text }
    val shown by animateFloatAsState(if (visible) 1f else 0f, tween(450), label = "header")
    val height = if (wide) 50.dp else 46.dp
    var firstFrame by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { withFrameNanos { }; firstFrame = false }
    Box(
        modifier.fillMaxWidth().height(height)
            .graphicsLayer {
                alpha = shown
                if (!reduce) {
                    translationY = -14.dp.toPx() * (1 - shown)
                    val s = 0.97f + 0.03f * shown
                    scaleX = s; scaleY = s
                }
            }
            .clearAndSetSemantics { if (visible) { contentDescription = description; liveRegion = LiveRegionMode.Polite } },
    ) {
        // The glass is a layer of its own, so chips can fly in from outside the capsule.
        Box(Modifier.matchParentSize().glass(RoundedCornerShape(50), onbGlassTint(), shadow = 10.dp))
        Row(Modifier.fillMaxSize().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp).onGloballyPositioned { c -> onSlot(c.positionInRoot() + Offset(c.size.width / 2f, c.size.height / 2f)) })
            Spacer(Modifier.width(8.dp))
            Eyebrow(title, Modifier.padding(end = 2.dp))
            Spacer(Modifier.width(6.dp))
            ChipStrip(slots.indexOfLast { it.chip != null }, height, Modifier.weight(1f).fillMaxSize()) {
                slots.forEach { slot ->
                    androidx.compose.runtime.key(slot.key) {
                        ChipSlotView(slot, wide, fly = !firstFrame)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipSlotView(slot: ChipSlot, wide: Boolean, fly: Boolean) {
    var last by remember { mutableStateOf(slot.chip) }
    if (slot.chip != null) last = slot.chip
    Box(contentAlignment = Alignment.Center) {
        if (slot.chip == null && slot.placeholder != null) Placeholder(slot.placeholder, if (wide) 30.dp else 28.dp)
        AnimatedVisibility(slot.chip != null, enter = EnterTransition.None, exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.8f)) {
            last?.let { Chip(it, wide, fly) }
        }
    }
}

@Composable
private fun Placeholder(width: Dp, height: Dp) {
    val color = if (LocalSky.current.dark) Color.White.copy(alpha = 0.36f) else Tokens.textLight.copy(alpha = 0.26f)
    Canvas(Modifier.size(width, height)) {
        val w = 1.5.dp.toPx()
        drawRoundRect(
            color, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), cornerRadius = CornerRadius(size.height / 2),
            style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
        )
    }
}

@Composable
private fun Chip(chip: DayChip, wide: Boolean, fly: Boolean) {
    val reduce = LocalOnbReduce.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val p = remember { Animatable(if (fly) 0f else 1f) }
    LaunchedEffect(Unit) { if (p.value < 1f) p.animateTo(1f, if (reduce) tween(OnbMotion.FADE_MS) else OnbMotion.bouncy()) }
    val bump = remember { Animatable(1f) }
    var lastBump by remember { mutableIntStateOf(chip.bump) }
    var lastText by remember { mutableStateOf(chip.text) }
    LaunchedEffect(chip.bump, chip.text) {
        if (chip.bump != lastBump || chip.text != lastText) {
            lastBump = chip.bump; lastText = chip.text
            if (!reduce) { bump.snapTo(0.8f); bump.animateTo(1f, OnbMotion.bouncy()) }
        }
    }
    val sky = LocalSky.current
    val bg = if (sky.dark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.62f)
    val line = if (sky.dark) Color.White.copy(alpha = 0.28f) else Tokens.textLight.copy(alpha = 0.10f)
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .graphicsLayer {
                val v = p.value
                alpha = if (reduce) v.coerceIn(0f, 1f) else (v * 2.4f).coerceIn(0f, 1f)
                if (!reduce) {
                    translationY = (if (wide) 300.dp else 260.dp).toPx() * (1 - v)
                    if (wide) translationX = (if (rtl) -150.dp else 150.dp).toPx() * (1 - v)
                    val s = (1.25f - 0.25f * v) * bump.value
                    scaleX = s; scaleY = s
                }
            }
            .height(if (wide) 30.dp else 28.dp).widthIn(max = if (wide) 150.dp else 140.dp)
            .background(bg, shape).border(1.dp, line, shape)
            .padding(start = 8.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(chip.icon, contentDescription = null, tint = Accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(chip.text, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp))
    }
}

/**
 * The chips' row: scrolls sideways (no scroll bar), fades at an edge with more beyond it, and follows the newest chip
 * ([focus], a slot index). Its drawing layer reaches far above and below the capsule, so a chip can fly in from below;
 * only the capsule itself takes the scroll gesture.
 */
@Composable
private fun ChipStrip(focus: Int, height: Dp, modifier: Modifier, content: @Composable () -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var offset by remember { mutableFloatStateOf(0f) }
    var max by remember { mutableIntStateOf(0) }
    var viewport by remember { mutableIntStateOf(0) }
    var focusEnd by remember { mutableIntStateOf(0) }
    val state = rememberScrollableState { d ->
        val old = offset
        offset = (offset - d).coerceIn(0f, max.toFloat())
        old - offset
    }
    LaunchedEffect(focus) {
        withFrameNanos { }
        val target = if (focus < 0) 0f else (focusEnd - viewport).coerceIn(0, max).toFloat()
        androidx.compose.animation.core.animate(offset, target, animationSpec = OnbMotion.spring()) { v, _ -> offset = v }
    }
    val reach = 320.dp
    Box(modifier.scrollable(state, Orientation.Horizontal, reverseDirection = rtl), contentAlignment = Alignment.Center) {
        Layout(
            content = content,
            modifier = Modifier.fillMaxWidth().requiredHeight(height + reach * 2)
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // Soft edges where more chips are hidden (start and end follow the reading direction).
                    val fade = 14.dp.toPx() / size.width
                    val atStart = offset > 0.5f
                    val atEnd = offset < max - 0.5f
                    val left = if (rtl) atEnd else atStart
                    val right = if (rtl) atStart else atEnd
                    drawRect(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = if (left) 0f else 1f), fade to Color.Black,
                            1 - fade to Color.Black, 1f to Color.Black.copy(alpha = if (right) 0f else 1f),
                        ),
                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                    )
                },
        ) { measurables, c ->
            val row = height.roundToPx()
            val gap = 6.dp.roundToPx()
            val start = 2.dp.roundToPx()
            val endPad = 12.dp.roundToPx()
            val placeables = measurables.map { it.measure(Constraints(maxHeight = row)) }
            val xs = IntArray(placeables.size)
            var x = start
            placeables.forEachIndexed { i, p ->
                xs[i] = x
                x += p.width + if (p.width > 0) gap else 0
            }
            val total = x - gap + endPad
            viewport = c.maxWidth
            max = (total - c.maxWidth).coerceAtLeast(0)
            focusEnd = if (focus in placeables.indices) xs[focus] + placeables[focus].width + endPad else 0
            layout(c.maxWidth, c.maxHeight) {
                val off = offset.coerceIn(0f, max.toFloat()).roundToInt()
                placeables.forEachIndexed { i, p -> p.placeRelative(xs[i] - off, (c.maxHeight - p.height) / 2) }
            }
        }
    }
}

/**
 * The sun (or the moon) at [center] with radius [radius]: the radial gold disc with its glow, or a crescent in
 * moonlight. [halo] adds the breathing light of the promise and done steps (scale [haloScale]).
 */
internal fun DrawScope.drawHero(center: Offset, radius: Float, moon: Boolean, lightSky: Boolean, halo: Boolean, haloScale: Float, alpha: Float) {
    if (alpha <= 0f || radius <= 0f) return
    if (moon) {
        val c = if (lightSky) HeroColors.moonlightLight else Tokens.moonlight
        if (halo) {
            val hr = radius * 1.75f * haloScale
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.26f * alpha), c.copy(alpha = 0f)), center, hr), hr, center)
        }
        val gr = radius * 1.35f
        drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.35f * alpha), c.copy(alpha = 0f)), center, gr), gr, center)
        val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, radius)) }
        val cut = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center + Offset(-radius * 0.46f, -radius * 0.15f), radius * 0.98f)) }
        val crescent = Path().apply { op(disc, cut, PathOperation.Difference) }
        rotate(-22f, center) { drawPath(crescent, c.copy(alpha = alpha)) }
    } else {
        if (halo) {
            val hr = radius * 1.75f * haloScale
            drawCircle(Brush.radialGradient(listOf(HeroColors.sunMid.copy(alpha = 0.38f * alpha), HeroColors.sunMid.copy(alpha = 0f)), center, hr), hr, center)
        }
        val gr = radius * 1.65f
        drawCircle(Brush.radialGradient(listOf(HeroColors.sunMid.copy(alpha = 0.45f * alpha), HeroColors.sunMid.copy(alpha = 0f)), center, gr), gr, center)
        drawCircle(
            Brush.radialGradient(
                0f to HeroColors.sunCore.copy(alpha = alpha), 0.58f to HeroColors.sunMid.copy(alpha = alpha), 1f to HeroColors.sunEdge.copy(alpha = alpha),
                center = center + Offset(0f, -radius * 0.08f), radius = radius,
            ),
            radius, center,
        )
    }
}

/**
 * The Friday picture (phones): the midday sky, a soft glow high up and the mosque skyline rising into it. No sun disc:
 * the sun stays in the header. Decorative, never mirrored.
 */
@Composable
internal fun FridayArt(modifier: Modifier = Modifier) {
    val dark = LocalSky.current.dark
    val sky = Sky.of(SkyPeriod.DAY, dark)
    val reduce = LocalOnbReduce.current
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(120)
        rise.animateTo(1f, if (reduce) tween(OnbMotion.FADE_MS) else OnbMotion.soft())
    }
    val ink = sky.ink
    Canvas(modifier.fillMaxWidth().height(132.dp).clip(CardShape).clearAndSetSemantics { }) {
        drawRect(Brush.verticalGradient(listOf(sky.top, sky.mid, sky.low)))
        val g = Offset(size.width * 0.5f, size.height * 0.18f)
        val gr = size.width * 0.45f
        drawCircle(Brush.radialGradient(listOf(sky.glow.copy(alpha = 0.75f), sky.glow.copy(alpha = 0f)), g, gr), gr, g)
        val w = size.width * 0.62f
        val h = w * 0.24f
        val dy = if (reduce) 0f else 12.dp.toPx() * (1 - rise.value)
        val color = if (dark) lerp(sky.low, Color.Black, 0.35f) else ink.copy(alpha = 0.22f)
        drawSkyline(Offset((size.width - w) / 2, size.height - h + dy), Size(w, h), color.copy(alpha = color.alpha * rise.value.coerceIn(0f, 1f)))
    }
}

/** The Allow button's three looks: its label, a 54 dp circle with a spinner, a circle with the check. */
internal enum class AllowPhase { IDLE, WORKING, DONE }

/** The privacy step's button: it shrinks into a circle with a spinner, then the check pops. */
@Composable
internal fun AllowButton(text: String, phase: AllowPhase, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val reduce = LocalOnbReduce.current
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(50)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val target = if (phase == AllowPhase.IDLE) Dp.Unspecified else 54.dp
        val w by animateDpAsState(if (target == Dp.Unspecified) 2000.dp else 54.dp, if (reduce) tween(OnbMotion.FADE_MS) else OnbMotion.spring(), label = "allow")
        Box(
            Modifier.widthIn(max = w).fillMaxWidth().heightIn(min = 54.dp).pressScale(source)
                .clip(shape).background(Tokens.gold, shape).border(1.dp, Color.White.copy(alpha = 0.35f), shape)
                .clickable(interactionSource = source, indication = null, enabled = phase == AllowPhase.IDLE, role = Role.Button, onClick = onClick)
                .semantics { if (phase != AllowPhase.IDLE) contentDescription = text },
            contentAlignment = Alignment.Center,
        ) {
            when (phase) {
                AllowPhase.IDLE -> Text(
                    text, color = Tokens.navyNight, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                AllowPhase.WORKING -> CircularProgressIndicator(Modifier.size(22.dp), color = Tokens.navyNight, strokeWidth = 2.5.dp)
                AllowPhase.DONE -> Icon(rememberVectorPainter(Icons.Filled.Check), contentDescription = null, tint = Tokens.navyNight, modifier = Modifier.size(26.dp).pop())
            }
        }
    }
}

/** A glass card with a 15 sp heading (extras), radius 16. */
@Composable
internal fun ShowcaseCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().glass(CardShape, onbGlassTint(), shadow = 8.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = Ink, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp), modifier = Modifier.semantics { heading() })
        content()
    }
}

/** The breathing scale of the halo (still with animations off). */
@Composable
internal fun haloScale(animate: Boolean): Float = 1f + 0.08f * loopingFloat(animate, 2750, reverse = true, label = "halo", still = 0f)

/** A small pill in the accent (the weather step's "New"). */
@Composable
internal fun Badge(text: String, modifier: Modifier = Modifier) {
    val dark = LocalSky.current.dark
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val cased = !rtl && text.any { it.isLowerCase() }
    Box(
        modifier.heightIn(min = 24.dp).background(Tokens.gold.copy(alpha = if (dark) 0.24f else 0.2f), RoundedCornerShape(50)).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (cased) text.uppercase() else text, color = Accent,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = if (cased) 0.08.em else 0.sp),
        )
    }
}

/** A small glass container (the method line, the preview card). */
@Composable
internal fun GlassBox(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = CardShape, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.glass(shape, onbGlassTint(), shadow = 6.dp), content = content)
}
