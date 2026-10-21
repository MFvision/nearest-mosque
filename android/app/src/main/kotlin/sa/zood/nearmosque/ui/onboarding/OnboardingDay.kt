package sa.zood.nearmosque.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sa.zood.nearmosque.R
import sa.zood.nearmosque.core.DayArc
import sa.zood.nearmosque.core.DaySchedule
import sa.zood.nearmosque.core.OnboardingStep
import sa.zood.nearmosque.core.OnboardingTiming
import sa.zood.nearmosque.core.PrayerCalculator
import sa.zood.nearmosque.core.PrayerEvent
import sa.zood.nearmosque.platform.AlertAccess
import sa.zood.nearmosque.platform.PrayerSilence
import sa.zood.nearmosque.platform.ReminderScheduler
import sa.zood.nearmosque.ui.Format
import sa.zood.nearmosque.ui.glass.glass
import sa.zood.nearmosque.ui.glass.loopingFloat
import sa.zood.nearmosque.ui.prayer.weatherConditionName
import sa.zood.nearmosque.ui.prayer.weatherIcon
import sa.zood.nearmosque.ui.settings.ContentDownloadCard
import sa.zood.nearmosque.ui.theme.Accent
import sa.zood.nearmosque.ui.theme.Ink
import sa.zood.nearmosque.ui.theme.LocalSky
import sa.zood.nearmosque.ui.theme.SkyPeriod
import sa.zood.nearmosque.ui.theme.Tokens
import java.time.Duration
import java.time.Instant
import kotlin.math.cos
import kotlin.math.sin

/* Building the day, the day itself (the arc you can walk along), exactly what leaves the phone, and done. */

/** "Building your prayer day…": three lines tick to a check over a pulsing outline of what comes, then the day. */
@Composable
internal fun BuildingStep(ctx: OnbContext) {
    val s = ctx.ui.settings
    val method = s?.prayer?.method
    var done by remember { mutableIntStateOf(if (ctx.still) 3 else 0) }
    LaunchedEffect(Unit) {
        if (ctx.still) return@LaunchedEffect
        repeat(3) { delay(OnboardingTiming.BUILDING_LINE.toLong()); done++ }
        delay((OnboardingTiming.BUILDING - 3 * OnboardingTiming.BUILDING_LINE).toLong().coerceAtLeast(0))
        ctx.go(OnboardingStep.DAY)
    }
    val reduce = LocalOnbReduce.current
    val breathe = loopingFloat(!reduce, 750, reverse = true, label = "skeleton", still = 0.5f)
    val lines = listOf(
        stringResource(R.string.onb2_building_method, method?.let { stringResource(Format.methodShortName(it)) } ?: "—"),
        stringResource(R.string.onb2_building_qibla, ctx.ui.location?.name ?: "—"),
        stringResource(R.string.onb2_building_sun),
    )
    val sky = LocalSky.current
    val track = if (sky.dark) Color.White.copy(alpha = 0.26f) else Tokens.textLight.copy(alpha = 0.20f)
    val chip = if (sky.dark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.62f)
    Column(
        Modifier.fillMaxWidth().then(if (ctx.wide) Modifier.padding(vertical = 8.dp) else Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.onb2_building), color = Ink, textAlign = TextAlign.Center,
            style = skyText(MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)), modifier = Modifier.fillMaxWidth().enter(0),
        )
        Spacer(Modifier.height(16.dp))
        Canvas(Modifier.fillMaxWidth().height(if (ctx.wide) 120.dp else 160.dp).graphicsLayer { alpha = 0.55f + 0.45f * breathe }.clearAndSetSemantics { }) {
            val g = ArcGeometry(size.width, 24.dp.toPx())
            val p = Path().apply { for (i in 0..60) g.point(i / 60f, false).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
            drawPath(p, track, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.widthIn(max = 360.dp).fillMaxWidth().padding(horizontal = 20.dp).semantics { liveRegion = LiveRegionMode.Polite }.enter(1),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            lines.forEachIndexed { i, text ->
                val ok = i < done
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(24.dp).background(if (ok) Tokens.gold else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                        if (ok) Icon(rememberVectorPainter(Icons.Filled.Check), contentDescription = null, tint = Tokens.navyNight, modifier = Modifier.size(16.dp).pop())
                        else if (i == done) CircularProgressIndicator(Modifier.size(16.dp), color = InkSoft, strokeWidth = 2.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(text, color = if (ok) Ink else InkSoft, style = skyText(MaterialTheme.typography.bodyLarge))
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth().graphicsLayer { alpha = 0.55f + 0.45f * breathe }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { Box(Modifier.weight(1f).height(if (ctx.wide) 72.dp else 116.dp).background(chip, CardShape)) }
        }
    }
}

/**
 * The arc: a circular arc with a 70° half-angle and a chord of 0.86 of the width, [top] below the box's top. [point]
 * follows the reading direction (right to left in RTL); the landscape around it is never mirrored.
 */
internal class ArcGeometry(width: Float, top: Float) {
    private val half = Math.toRadians(70.0)
    val chord = 0.86f * width
    val r = (chord / 2 / sin(half)).toFloat()
    val sag = (r * (1 - cos(half))).toFloat()
    val cx = width / 2
    val baseY = top + sag
    private val cy = (baseY + r * cos(half)).toFloat()

    fun point(t: Float, rtl: Boolean): Offset {
        val f = if (rtl) 1 - t else t
        val th = Math.toRadians(-70.0 + 140.0 * f)
        return Offset((cx + r * sin(th)).toFloat(), (cy - r * cos(th)).toFloat())
    }

    /** [distance] from the arc at [t] towards its centre (negative: outside the arc). */
    fun inside(t: Float, rtl: Boolean, distance: Float): Offset {
        val p = point(t, rtl)
        val d = Offset(cx, cy) - p
        return p + d / d.getDistance() * distance
    }
}

/** The day's schedules (yesterday, today, tomorrow) for the chosen place, recomputed when the date or settings change. */
@Composable
private fun rememberDays(ctx: OnbContext): List<DaySchedule>? {
    val loc = ctx.ui.location ?: return null
    val s = ctx.ui.settings ?: return null
    val today = ctx.ui.now.atZone(loc.zoneId).toLocalDate()
    return remember(loc, s.prayer, today) { (-1L..1L).map { ctx.container.calculator.schedule(loc.location, today.plusDays(it), loc.zoneId, s.prayer) } }
}

/** The prayer begun last at [t] (Isha before Fajr) and the sky's period then; the night past the days known. */
private fun periodAt(t: Instant, days: List<DaySchedule>): Pair<PrayerEvent?, SkyPeriod> {
    val i = days.indexOfFirst { it.date == t.atZone(it.zone).toLocalDate() }
    if (i < 0) return null to SkyPeriod.NIGHT
    val d = days[i]
    val current = PrayerEvent.entries.lastOrNull { e -> d[e]?.let { !it.isAfter(t) } == true }
        ?: return (if (i > 0 && days[i - 1][PrayerEvent.ISHA] != null) PrayerEvent.ISHA else null) to SkyPeriod.NIGHT
    return current to SkyPeriod.at(t, d[PrayerEvent.SUNRISE], current, hasLocation = true)
}

/**
 * The arc of the day (or the night) with the prayers on it, the hint and the Day | Night control. The sun (HeroLayer)
 * rides it: drag it, use the arrow keys or TalkBack's adjust actions to walk through the day; the sky follows, and on
 * release the sun springs back to now. Past an end the day turns to night and back.
 */
@Composable
internal fun DayArcPane(ctx: OnbContext, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val days = rememberDays(ctx) ?: return
    val loc = ctx.ui.location ?: return
    val shared = ctx.shared
    val now = ctx.ui.now
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val currentHalf = if (DayArc.at(now, days)?.isDay == false) DayArc.Half.NIGHT else DayArc.Half.DAY
    val half = shared.half ?: currentHalf
    val arc = DayArc.at(now, days, half)
    SideEffect { shared.arcIsDay = arc?.isDay ?: (half == DayArc.Half.DAY) }
    if (arc == null) {
        // Polar day or night: no arc to walk along; the times below show what exists.
        SideEffect { shared.arcPoint = null }
        return
    }
    val t = (shared.scrub ?: arc.fraction.toFloat()).coerceIn(0f, 1f)
    val at = arc.start.plus(Duration.ofMillis((Duration.between(arc.start, arc.end).toMillis() * t).toLong()))
    val (event, period) = remember(at.epochSecond / 30, days) { periodAt(at, days) }
    LaunchedEffect(shared.scrub, period) { shared.scrubPeriod = if (shared.scrub != null) period else null }
    val bubble = (event?.let { stringResource(Format.prayerName(it)) } ?: stringResource(if (arc.isDay) R.string.onb2_day else R.string.onb2_night)) + " · " + Format.time(context, at, loc.zoneId)
    val currentHalfState by rememberUpdatedState(currentHalf)
    val haptics = LocalHapticFeedback.current
    fun other(h: DayArc.Half) = if (h == DayArc.Half.DAY) DayArc.Half.NIGHT else DayArc.Half.DAY
    fun moveBy(d: Float) {
        val h = shared.half ?: currentHalfState
        var v = (shared.scrub ?: arc.fraction.toFloat()) + d
        if (v > 1.06f) { shared.half = other(h); v = 0f; haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
        else if (v < -0.06f) { shared.half = other(h); v = 1f; haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
        shared.scrub = v
    }
    fun release() { shared.dragging = false; shared.scrub = null; shared.half = null }
    val label = stringResource(R.string.onb2_time_of_day)
    val density = LocalDensity.current
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val w = with(density) { maxWidth.toPx() }
            val g = ArcGeometry(w, with(density) { 30.dp.toPx() })
            val height = with(density) { (g.baseY + 46.dp.toPx()).toDp() }
            var origin by remember { mutableStateOf(Offset.Zero) }
            val here = g.point(t, rtl)
            SideEffect { shared.arcPoint = origin + here }
            val ink = Ink
            val accent = Tokens.gold
            val track = if (LocalSky.current.dark) Color.White.copy(alpha = 0.3f) else Tokens.textLight.copy(alpha = 0.22f)
            Box(
                Modifier.fillMaxWidth().height(height)
                    .onGloballyPositioned { origin = it.positionInRoot() }
                    .pointerInput(g.chord, rtl) {
                        detectDragGestures(
                            onDragStart = { shared.dragging = true; if (shared.scrub == null) shared.scrub = arc.fraction.toFloat() },
                            onDragEnd = { release() }, onDragCancel = { release() },
                        ) { change, amount ->
                            change.consume()
                            moveBy(amount.x / g.chord * (if (rtl) -1 else 1))
                        }
                    }
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        val step = when (e.key) { Key.DirectionRight -> 0.02f; Key.DirectionLeft -> -0.02f; else -> return@onKeyEvent false }
                        moveBy(if (rtl) -step else step)
                        true
                    }
                    .focusable()
                    .semantics {
                        contentDescription = label
                        stateDescription = bubble
                        progressBarRangeInfo = ProgressBarRangeInfo(t, 0f..1f)
                        setProgress { v -> shared.scrub = v.coerceIn(0f, 1f); true }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val n = 80
                    val full = Path().apply { for (i in 0..n) g.point(i / n.toFloat(), rtl).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
                    drawPath(full, track, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 7.dp.toPx()))))
                    val a = g.point(0f, rtl)
                    val b = g.point(1f, rtl)
                    drawLine(track, a, b, 1.dp.toPx())
                    if (t > 0f) {
                        val k = maxOf(2, (n * t).toInt())
                        val done = Path().apply { for (i in 0..k) g.point(t * i / k, rtl).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
                        drawPath(done, accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    }
                    arc.marks.forEach { m ->
                        val p = g.point(m.fraction.toFloat(), rtl)
                        drawCircle(Color.Black.copy(alpha = 0.16f), 7.5.dp.toPx(), p)
                        drawCircle(if (m.fraction <= t) accent else ink, 4.5.dp.toPx(), p)
                    }
                }
                arc.marks.forEach { m ->
                    // Inside the arc, clear of its line; outside where the sun (or the moon) is on the mark.
                    val f = m.fraction.toFloat()
                    val gap = with(density) { 20.dp.toPx() }
                    ArcText(g.inside(f, rtl, if (kotlin.math.abs(f - t) < 0.09f) -gap * 1.2f else gap)) {
                        Text(stringResource(Format.prayerName(m.event)), color = InkSoft, maxLines = 1, style = skyText(MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)))
                    }
                }
                listOf(0f to arc.startEvent to arc.start, 1f to arc.endEvent to arc.end).forEach { (pe, time) ->
                    val (f, e) = pe
                    val p = g.point(f, rtl)
                    ArcText(Offset(p.x, p.y + with(density) { 22.dp.toPx() })) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(Format.prayerName(e)), color = InkSoft, maxLines = 1, style = skyText(MaterialTheme.typography.labelSmall))
                            Text(Format.time(context, time, loc.zoneId), color = Ink, maxLines = 1, style = skyText(MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")))
                        }
                    }
                }
                if (shared.scrub != null) {
                    ArcText(Offset(here.x, here.y - with(density) { 34.dp.toPx() })) {
                        Text(
                            bubble, color = Ink, maxLines = 1,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                            modifier = Modifier.glass(RoundedCornerShape(50), onbGlassTint(), shadow = 6.dp).padding(horizontal = 11.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        OnbFoot(stringResource(if (arc.isDay) R.string.onb2_hint_sun else R.string.onb2_hint_moon), align = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.glass(RoundedCornerShape(50), onbGlassTint(), shadow = 4.dp).padding(2.dp).selectableGroup()) {
            listOf(DayArc.Half.DAY to R.string.onb2_day, DayArc.Half.NIGHT to R.string.onb2_night).forEach { (h, l) ->
                val on = arc.isDay == (h == DayArc.Half.DAY)
                Box(
                    Modifier.heightIn(min = 36.dp).widthIn(min = 76.dp).clip(RoundedCornerShape(50))
                        .background(if (on) (if (LocalSky.current.dark) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.8f)) else Color.Transparent)
                        .selectable(on, role = Role.Tab) { shared.scrub = null; shared.half = if (h == currentHalf) null else h }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(l), color = if (on) Ink else InkSoft, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}

/** Text centred on [at] (pixels from the box's top left, never mirrored), kept inside the box. */
@Composable
private fun ArcText(at: Offset, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().layout { m, c ->
            val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
            val x = (at.x - p.width / 2f).toInt().coerceIn(0, maxOf(0, c.maxWidth - p.width))
            val y = (at.y - p.height / 2f).toInt().coerceIn(0, maxOf(0, c.maxHeight - p.height))
            layout(c.maxWidth, c.maxHeight) { p.place(x, y) }
        },
    ) { content() }
}

/**
 * Whether the day's countdown would sit on the bright core of the horizon glow (a Fold held open, a short window): then
 * it goes on glass like the times row. Phones in dark mode while the horizon glows (see glowBand). The glow is
 * SkyBackdrop's ellipse, reaching 0.7 × 0.42 of the width above the horizon; its inner 40% is too bright for text. The
 * distance is measured from the top of the step, so neither the scroll nor the entering slide changes the answer.
 */
@Composable
private fun countdownOnGlow(ctx: OnbContext, anchorY: Float, bottom: Float): Boolean {
    val sky = LocalSky.current
    val box = ctx.shared.skyBox
    if (ctx.wide || !sky.dark || glowBand(sky.period) == 0f || box == null || anchorY.isNaN() || bottom.isNaN()) return false
    val top = ctx.shared.stepTop + with(LocalDensity.current) { 8.dp.toPx() }
    val horizon = box.top + box.height * onbHorizon(OnboardingStep.DAY, wide = false)
    return horizon - (top + bottom - anchorY) < box.width * 0.7f * 0.42f * 0.4f
}

/** The glass under the countdown on the glow: the readability band's shade, deep enough for the small line too. */
@Composable
private fun glowShade(): Color = lerp(LocalSky.current.low, Color.Black, 0.45f).copy(alpha = 0.62f)

/**
 * Your day: built from the answers. The arc (on phones; in the leading pane when wide), the next prayer counting down,
 * today's six times, and three cards: the Qibla, the Hijri date, and the real weather or the length of daylight.
 */
@Composable
internal fun DayStep(ctx: OnbContext) {
    val context = LocalContext.current
    val loc = ctx.ui.location
    val s = ctx.ui.settings
    val city = loc?.name.orEmpty()
    // Where the countdown falls, from the top of the step (see countdownOnGlow).
    var anchorY by remember { mutableFloatStateOf(Float.NaN) }
    var countdownBottom by remember { mutableFloatStateOf(Float.NaN) }
    StepScaffold(ctx, bottom = { PrimaryButton(stringResource(R.string.onb2_continue), onClick = { ctx.go(OnboardingStep.EXTRAS) }) }) {
        Spacer(Modifier.onGloballyPositioned { anchorY = it.positionInRoot().y })
        Eyebrow(stringResource(R.string.onb2_day_eyebrow), Modifier.enter(0))
        Spacer(Modifier.height(2.dp))
        val titleStyle = skyText(MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = Ink))
        BasicText(
            stringResource(R.string.onb2_day_title, city), style = titleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = titleStyle.fontSize * 0.8f, maxFontSize = titleStyle.fontSize, stepSize = 1.sp),
            modifier = Modifier.fillMaxWidth().enter(0).semantics { heading() },
        )
        if (!ctx.wide) {
            Spacer(Modifier.height(10.dp))
            DayArcPane(ctx, Modifier.enter(1))
        }
        Spacer(Modifier.height(14.dp))
        val next = ctx.ui.next
        if (next != null && loc != null) {
            val name = stringResource(Format.prayerName(next.event))
            val remaining = PrayerCalculator.remaining(ctx.ui.now, next.at)
            val time = Format.time(context, next.at, loc.zoneId)
            val a11y = stringResource(R.string.countdown_a11y, name, time, Format.remainingLong(context, remaining))
            val onGlow = countdownOnGlow(ctx, anchorY, countdownBottom)
            Column(
                Modifier.fillMaxWidth()
                    .onGloballyPositioned { countdownBottom = it.positionInRoot().y + it.size.height }
                    .then(if (onGlow) Modifier.glass(RoundedCornerShape(18.dp), glowShade(), shadow = 6.dp).padding(vertical = 8.dp) else Modifier)
                    .clearAndSetSemantics { contentDescription = a11y }.enter(2),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.onb2_countdown, name, Format.countdown(context, remaining)), color = Ink, textAlign = TextAlign.Center,
                    style = skyText(MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, fontFeatureSettings = "tnum")),
                )
                Text(stringResource(R.string.onb2_at_time, time), color = if (onGlow) Ink else InkSoft, style = skyText(MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")))
            }
            Spacer(Modifier.height(14.dp))
        }
        val today = ctx.ui.today
        Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).glass(RoundedCornerShape(18.dp), onbGlassTint(), shadow = 6.dp).padding(4.dp).enter(3)) {
            PrayerEvent.entries.forEach { e ->
                val nx = next?.event == e && next.isTomorrow.not()
                val bg = if (nx) Tokens.gold.copy(alpha = 0.2f) else Color.Transparent
                Column(
                    Modifier.weight(1f).heightIn(min = 50.dp).background(bg, RoundedCornerShape(14.dp)).padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Text(stringResource(Format.prayerName(e)), color = if (nx) Accent else InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
                    // Long times ("12:36 PM", wide AM/PM marks) shrink to fit their column rather than touch the next one.
                    BasicText(
                        today?.get(e)?.let { Format.time(context, it, loc!!.zoneId) } ?: "—", maxLines = 1, modifier = Modifier.padding(horizontal = 2.dp),
                        style = MaterialTheme.typography.labelMedium.copy(color = if (nx) Accent else Ink, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum", textAlign = TextAlign.Center),
                        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 12.sp, stepSize = 0.5.sp),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max).enter(4), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val cardMod = Modifier.weight(1f).fillMaxHeight()
            val bearing = ctx.ui.qiblaBearing
            val atKaaba = (ctx.ui.qiblaDistance ?: Double.MAX_VALUE) < 1000
            MiniCard(
                stringResource(R.string.qibla),
                if (atKaaba) stringResource(R.string.onb2_at_kaaba) else bearing?.let { stringResource(R.string.qibla_bearing, Format.degrees(context, it)) } ?: "—",
                cardMod,
            ) { Needle(bearing ?: 0.0) }
            val hijri = loc?.let { s?.prayer?.hijriAdjustmentDays?.let { adj -> Format.hijri(context, ctx.ui.now.atZone(loc.zoneId).toLocalDate(), adj) } }
            MiniCard(stringResource(R.string.onb2_today), hijri ?: "—", cardMod) {
                Icon(painterResource(R.drawable.ic_p_isha), contentDescription = null, tint = Accent, modifier = Modifier.size(30.dp))
            }
            val w = ctx.ui.weather
            if (w != null) {
                MiniCard(stringResource(R.string.onb2_weather), stringResource(weatherConditionName(w.symbol.condition)), cardMod) {
                    Icon(painterResource(weatherIcon(w.symbol.condition, ctx.ui.sky.hasStars)), contentDescription = null, tint = Accent, modifier = Modifier.size(30.dp))
                }
            } else {
                val sunrise = today?.get(PrayerEvent.SUNRISE)
                val maghrib = today?.get(PrayerEvent.MAGHRIB)
                val text = if (sunrise != null && maghrib != null) {
                    val m = Duration.between(sunrise, maghrib).toMinutes()
                    stringResource(R.string.onb2_duration, Format.number(context, (m / 60).toDouble(), 0), Format.number(context, (m % 60).toDouble(), 0))
                } else "—"
                MiniCard(stringResource(R.string.onb2_daylight), text, cardMod) {
                    Icon(painterResource(R.drawable.ic_p_sunrise), contentDescription = null, tint = Accent, modifier = Modifier.size(30.dp))
                }
            }
        }
    }
}

/** A small glass card: label, picture, value; read as one. */
@Composable
private fun MiniCard(label: String, value: String, modifier: Modifier, visual: @Composable () -> Unit) {
    Column(
        modifier.heightIn(min = 116.dp).glass(CardShape, onbGlassTint(), shadow = 6.dp).padding(horizontal = 12.dp, vertical = 11.dp)
            .semantics(mergeDescendants = true) { },
    ) {
        Text(label, color = Accent, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) { visual() }
        Spacer(Modifier.weight(1f).heightIn(min = 6.dp))
        Text(value, color = Ink, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 17.sp, fontFeatureSettings = "tnum"))
    }
}

/** A tiny compass needle pointing to the Qibla from north (decorative; the value says it). */
@Composable
private fun Needle(bearing: Double) {
    val accent = Tokens.gold
    val ink = Ink
    val track = if (LocalSky.current.dark) Color.White.copy(alpha = 0.3f) else Tokens.textLight.copy(alpha = 0.22f)
    Canvas(Modifier.size(34.dp).clearAndSetSemantics { }) {
        val c = center
        val r = size.minDimension / 2 - 1.dp.toPx()
        drawCircle(track, r, c, style = Stroke(1.5.dp.toPx()))
        rotate(bearing.toFloat(), c) {
            val n = Path().apply { moveTo(c.x, c.y - r * 0.8f); lineTo(c.x + r * 0.2f, c.y); lineTo(c.x - r * 0.2f, c.y); close() }
            val sPath = Path().apply { moveTo(c.x, c.y + r * 0.8f); lineTo(c.x - r * 0.2f, c.y); lineTo(c.x + r * 0.2f, c.y); close() }
            drawPath(n, accent)
            drawPath(sPath, ink.copy(alpha = 0.4f))
        }
        drawCircle(ink, 2.dp.toPx(), c)
    }
}

/**
 * Exactly what leaves your phone: a three-step timeline (on the phone always; at each prayer nothing is sent; only for
 * mosques and the weather sky, a rounded location), then "Notify me at prayer times" and the one button that asks.
 * Notifications are asked for when the bells are wanted or anything chosen already alerts (the Adhan); right after,
 * on Android 12+, "Alarms & reminders" when something rings at a set minute, with a line saying why first.
 */
@Composable
internal fun PrivacyStep(ctx: OnbContext) {
    val context = LocalContext.current
    val s = ctx.ui.settings
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var notify by rememberSaveable { mutableStateOf(true) }
    var phase by remember { mutableStateOf(AllowPhase.IDLE) }
    var pending by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> pending?.complete(granted) }
    // "Alarms & reminders" is a system page: the flow waits for the user to come back from it.
    var exactStep by rememberSaveable { mutableStateOf(false) }
    var exactPending by remember { mutableStateOf<CompletableDeferred<Unit>?>(null) }
    val exactPage = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { exactPending?.complete(Unit) }
    fun needsAsk() = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    fun blocked() = !NotificationManagerCompat.from(context).areNotificationsEnabled()
    // Notifications turned off for the app (in system settings, or just declined): no check and no prompt; the note
    // says where to turn them on, and the button reads Finish. Checked again when the flow comes back from settings.
    var denied by remember { mutableStateOf(blocked() && !needsAsk()) }
    var exactAllowed by remember { mutableStateOf(ReminderScheduler.exactAlarmsAllowed(context)) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        exactAllowed = ReminderScheduler.exactAlarmsAllowed(context)
        if (denied) denied = blocked()
        onPauseOrDispose { }
    }
    // What will be scheduled once the bells are on: the exact-alarm row shows only when something rings at a set minute.
    val planned = s?.let { if (notify && it.reminders.isEmpty()) it.copy(reminders = PrayerEvent.prayers.toSet()) else it }
    val needsExact = Build.VERSION.SDK_INT >= 31 && !exactAllowed && planned != null &&
        remember(planned) { ReminderScheduler.wantsTimedAlerts(planned) } && ReminderScheduler.exactAlarmSettingsIntent(context) != null
    // With the switch off, an Adhan chosen a step earlier still needs notifications.
    val asks = notify || (s != null && remember(s) { ReminderScheduler.wantsAlerts(s) })
    suspend fun finish() {
        phase = AllowPhase.DONE
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        delay(OnboardingTiming.ALLOW_HOLD.toLong())
        ctx.go(OnboardingStep.DONE)
    }
    fun press() {
        if (asks && denied) {
            // Nothing to ask; the bells are on for when notifications are allowed.
            scope.launch {
                if (notify) ctx.vm.enablePrayerBells(context)
                ctx.go(OnboardingStep.DONE)
            }
            return
        }
        phase = AllowPhase.WORKING
        scope.launch {
            val started = System.currentTimeMillis()
            if (asks) {
                if (needsAsk()) {
                    val d = CompletableDeferred<Boolean>()
                    pending = d
                    AlertAccess.asking(context)
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    d.await()
                }
                // Allowed or not, the bells are on for when notifications are allowed; the alarms follow the answer.
                if (notify) ctx.vm.enablePrayerBells(context).join() else ctx.vm.rescheduleReminders(context).join()
            }
            val left = OnboardingTiming.ALLOW_SPINNER - (System.currentTimeMillis() - started)
            if (left > 0) delay(left)
            if (asks && blocked()) {
                denied = true
                phase = AllowPhase.IDLE
                return@launch
            }
            // Something rings at a set minute: "Alarms & reminders" next, explained before the system page opens.
            val saved = ctx.container.settings.settings.first()
            if (Build.VERSION.SDK_INT >= 31 && !ReminderScheduler.exactAlarmsAllowed(context) && ReminderScheduler.exactAlarmSettingsIntent(context) != null &&
                ReminderScheduler.wantsTimedAlerts(saved)
            ) {
                exactStep = true
                phase = AllowPhase.IDLE
                return@launch
            }
            finish()
        }
    }
    fun pressExact() {
        phase = AllowPhase.WORKING
        scope.launch {
            val d = CompletableDeferred<Unit>()
            exactPending = d
            val page = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:" + context.packageName))
            try {
                exactPage.launch(page)
            } catch (e: android.content.ActivityNotFoundException) {
                // Some builds lack the per-app page; the app's own settings page has "Alarms & reminders".
                runCatching { exactPage.launch(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, page.data)) }
                    .onFailure { d.complete(Unit) }
            }
            d.await()
            exactAllowed = ReminderScheduler.exactAlarmsAllowed(context)
            // Rebuilt at once, not only when Android's broadcast for the change comes.
            val saved = ctx.container.settings.settings.first()
            withContext(Dispatchers.Default) {
                runCatching { ReminderScheduler(context).reschedule(saved) }
                PrayerSilence(context).sync(saved)
            }
            exactStep = false
            finish()
        }
    }
    StepScaffold(
        ctx,
        bottom = {
            if (exactStep) {
                AllowButton(stringResource(R.string.exact_allow_android), phase, onClick = ::pressExact)
                OnbFoot(stringResource(R.string.onb2_exact_note_android), Modifier.padding(top = 8.dp), align = TextAlign.Center)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextAction(stringResource(R.string.not_now), onClick = { if (phase == AllowPhase.IDLE) ctx.go(OnboardingStep.DONE) })
                }
            } else {
                AllowButton(stringResource(if (asks && !denied) R.string.onb2_allow else R.string.onb2_finish), phase, onClick = ::press)
            }
            if (!exactStep && asks && denied) {
                OnbFoot(stringResource(R.string.notifications_denied), Modifier.padding(top = 8.dp), align = TextAlign.Center)
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(CardShape)
                        .clickable(role = Role.Button) {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                            runCatching { context.startActivity(intent) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.open_settings), color = Accent, style = skyText(MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)))
                }
            } else if (!exactStep && asks && needsAsk()) {
                OnbFoot(stringResource(if (ctx.device) R.string.onb2_ask_confirm_device else R.string.onb2_ask_confirm), Modifier.padding(top = 8.dp), align = TextAlign.Center)
            }
            OnbFoot(stringResource(R.string.onb2_change_later), Modifier.padding(top = 2.dp), align = TextAlign.Center)
        },
    ) {
        OnbTitle(stringResource(if (ctx.device) R.string.onb2_privacy_title_device else R.string.onb2_privacy_title), Modifier.enter(0))
        Spacer(Modifier.height(18.dp))
        Column(Modifier.enter(1)) {
            TimelineNode(
                painterResource(R.drawable.ic_phone_outline), first = true, line = true,
                stringResource(if (ctx.device) R.string.onb2_privacy_n1_title_device else R.string.onb2_privacy_n1_title),
                stringResource(R.string.onb2_privacy_n1_body),
            )
            TimelineNode(
                painterResource(R.drawable.ic_bell), first = false, line = true,
                stringResource(R.string.onb2_privacy_n2_title),
                stringResource(if (ctx.device) R.string.onb2_privacy_n2_body_device else R.string.onb2_privacy_n2_body),
            )
            TimelineNode(
                painterResource(R.drawable.ic_pin), first = false, line = false,
                stringResource(R.string.onb2_privacy_n3_title), stringResource(R.string.onb2_privacy_n3_body),
            ) {
                val on = s?.skyWeather == true
                Row(
                    Modifier.padding(top = 8.dp).heightIn(min = 26.dp).glass(RoundedCornerShape(50), onbGlassTint(), shadow = 2.dp).padding(horizontal = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(7.dp).background(if (on) Color(0xFF5ED38A) else InkSoft, CircleShape))
                    Spacer(Modifier.width(7.dp))
                    Text(stringResource(if (on) R.string.onb2_privacy_weather_on else R.string.onb2_privacy_weather_off), color = Ink, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        SwitchRow(null, stringResource(R.string.onb2_notify), null, notify, { notify = it }, Modifier.enter(2))
        if (needsExact && notify && !exactStep) {
            Box(
                Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp).clip(CardShape)
                    .clickable(role = Role.Button) { ReminderScheduler.exactAlarmSettingsIntent(context)?.let { runCatching { context.startActivity(it) } } }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(stringResource(R.string.exact_alarm_needed), color = Accent, style = skyText(MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)))
            }
        }
    }
}

/** One step of the privacy timeline: a 32 dp node (the first one filled) joined to the next by a gold line. */
@Composable
private fun TimelineNode(icon: androidx.compose.ui.graphics.painter.Painter, first: Boolean, line: Boolean, title: String, body: String, extra: @Composable () -> Unit = {}) {
    val gold = Tokens.gold
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).semantics(mergeDescendants = true) { }) {
        Box(Modifier.width(32.dp).fillMaxHeight()) {
            if (line) {
                Canvas(Modifier.fillMaxSize()) {
                    val x = size.width / 2
                    drawLine(
                        androidx.compose.ui.graphics.Brush.verticalGradient(listOf(gold, gold.copy(alpha = 0.25f)), startY = 34.dp.toPx(), endY = size.height),
                        Offset(x, 34.dp.toPx()), Offset(x, size.height - 2.dp.toPx()), 2.dp.toPx(), StrokeCap.Round,
                    )
                }
            }
            Box(
                Modifier.size(32.dp).then(
                    if (first) Modifier.background(gold, CircleShape)
                    else Modifier.glass(CircleShape, onbGlassTint(), shadow = 0.dp).border(2.dp, gold, CircleShape),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = if (first) Tokens.navyNight else Accent, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(bottom = 16.dp)) {
            Text(title, color = Ink, style = skyText(MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)), modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
            Text(body, color = InkSoft, style = skyText(MaterialTheme.typography.bodyMedium))
            extra()
        }
    }
}

/**
 * You're all set: the header dissolves, the sun glides to where it really is and the skyline appears. The next prayer,
 * and "We'll call you." only when notifications are allowed and something will ring.
 */
@Composable
internal fun DoneStep(ctx: OnbContext) {
    val context = LocalContext.current
    val loc = ctx.ui.location
    val s = ctx.ui.settings
    val next = ctx.ui.next
    val allowed = remember(ctx.ui.now.epochSecond / 5) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val exact = remember(ctx.ui.now.epochSecond / 5) { ReminderScheduler.exactAlarmsAllowed(context) }
    val rings = s != null && (s.reminders.isNotEmpty() || s.alerts.adhan || s.alerts.fajrAdhan)
    // "We'll call you" only when it will be on time; otherwise the way to allow "Alarms & reminders".
    val sub = if (next != null && loc != null) {
        stringResource(R.string.onb2_done_sub, stringResource(Format.prayerName(next.event)), Format.time(context, next.at, loc.zoneId), loc.name) +
            (if (allowed && rings && exact) " " + stringResource(R.string.onb2_done_notify) else "")
    } else null
    val content: @Composable () -> Unit = {
        OnbTitle(stringResource(R.string.onb2_done_title), Modifier.enter(0), large = true, align = TextAlign.Center)
        if (sub != null) {
            Spacer(Modifier.height(10.dp))
            OnbBody(sub, Modifier.enter(1), align = TextAlign.Center)
        }
        if (allowed && rings && !exact && ReminderScheduler.exactAlarmSettingsIntent(context) != null) {
            Box(
                Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp).clip(CardShape)
                    .clickable(role = Role.Button) { ReminderScheduler.openExactAlarmSettings(context) }.enter(1),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.exact_alarm_needed), color = Accent, textAlign = TextAlign.Center, style = skyText(MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)))
            }
        }
        ContentDownloadCard(Format.languageCode(context), Modifier.padding(top = 16.dp).enter(2), onSky = true)
        Spacer(Modifier.height(26.dp))
        PrimaryButton(stringResource(R.string.onb2_open_app), onClick = ctx.onFinish, modifier = Modifier.enter(3))
    }
    if (ctx.wide) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) { content() }
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 28.dp, end = 28.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

