package sa.zood.nearmosque.ui.prayer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import sa.zood.nearmosque.R
import sa.zood.nearmosque.core.CompassState
import sa.zood.nearmosque.core.DayArc
import sa.zood.nearmosque.core.PrayerCalculator
import sa.zood.nearmosque.core.PrayerEvent
import sa.zood.nearmosque.ui.Format
import sa.zood.nearmosque.ui.glass.drawStars
import sa.zood.nearmosque.ui.glass.glass
import sa.zood.nearmosque.ui.theme.Accent
import sa.zood.nearmosque.ui.theme.Ink
import sa.zood.nearmosque.ui.theme.LocalDark
import sa.zood.nearmosque.ui.theme.Sky
import sa.zood.nearmosque.ui.theme.SkyPeriod
import sa.zood.nearmosque.ui.theme.Tokens
import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A small sky for the time of day: daylight runs from sunrise (start) to Maghrib (end) with the sun on
 * its path; at night the moon moves from Maghrib to the next sunrise. Dhuhr and Asr (or Isha and Fajr)
 * are marked where they fall. Time runs in the reading direction (right to left in Arabic and Urdu).
 */
@Composable
fun DayArcView(arc: DayArc, period: SkyPeriod, zone: ZoneId, height: Dp = 156.dp) {
    val context = LocalContext.current
    val sky = Sky.of(period, LocalDark.current)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val ink = Ink
    val accent = Accent
    val s = Format.time(context, arc.start, zone)
    val e = Format.time(context, arc.end, zone)
    val pct = java.text.NumberFormat.getPercentInstance(context.resources.configuration.locales[0]).format(arc.fraction)
    val desc = stringResource(if (arc.isDay) R.string.sky_day_a11y else R.string.sky_night_a11y, s, e, pct)
    val shape = RoundedCornerShape(20.dp)
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(height).clip(shape).border(1.dp, ink.copy(alpha = 0.12f), shape)
            .clearAndSetSemantics { contentDescription = desc },
    ) {
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val horizon = h * 0.74f
        val a = w / 2 - with(density) { 34.dp.toPx() }
        val b = horizon - with(density) { 40.dp.toPx() }
        fun point(t: Double): Offset {
            val f = if (rtl) 1 - t else t
            val theta = PI * (1 - f)
            return Offset((w / 2 + a * cos(theta)).toFloat(), (horizon - b * sin(theta)).toFloat())
        }
        val here = point(arc.fraction)
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(sky.top, sky.mid, lerp(sky.mid, sky.glow, 0.45f)), endY = horizon))
            drawRect(sky.low.copy(alpha = 0.55f), topLeft = Offset(0f, horizon), size = Size(w, h - horizon))
            if (!arc.isDay && sky.dark) drawStars(11, Size(w, horizon), 0.7f)
            val glowR = 90.dp.toPx()
            drawCircle(Brush.radialGradient(listOf(sky.glow.copy(alpha = if (arc.isDay) 0.85f else 0.35f), sky.glow.copy(alpha = 0f)), here, glowR), glowR, here)
            val path = Path().apply { for (i in 0..60) point(i / 60.0).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
            drawPath(path, ink.copy(alpha = 0.45f), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx()))))
            val n = max(1, (arc.fraction * 60).toInt())
            val done = Path().apply { for (i in 0..n) point(arc.fraction * i / n).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
            drawPath(done, accent.copy(alpha = 0.9f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            drawLine(ink.copy(alpha = 0.35f), Offset(0f, horizon), Offset(w, horizon), 1.dp.toPx())
            arc.marks.forEach { m -> drawCircle(if (m.fraction <= arc.fraction) accent else ink.copy(alpha = 0.8f), 4.dp.toPx(), point(m.fraction)) }
            if (arc.isDay) {
                drawCircle(Tokens.gold.copy(alpha = 0.35f), 20.dp.toPx(), here)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF4D6), Tokens.gold), here, 13.dp.toPx()), 13.dp.toPx(), here)
            } else {
                // Crescent: a pale disc with a sky-coloured disc over it.
                val r = 11.dp.toPx()
                drawCircle(Color.White.copy(alpha = 0.25f), r * 1.7f, here)
                drawCircle(Color(0xFFF4EEDF), r, here)
                drawCircle(lerp(sky.top, sky.mid, 0.5f), r * 0.85f, here + Offset(r * 0.55f, -r * 0.3f))
            }
        }
        val gap = with(density) { 22.dp.toPx() }
        // Labels sit inside the arc; outside it near the ends (clear of the horizon labels) or where
        // the sun or moon is on that mark.
        arc.marks.forEach { m ->
            val outside = kotlin.math.abs(m.fraction - arc.fraction) < 0.1 || m.fraction < 0.2 || m.fraction > 0.8
            point(m.fraction).let { ArcLabel(m.event, m.at, zone, it + Offset(0f, if (outside) -gap else gap)) }
        }
        ArcLabel(arc.startEvent, arc.start, zone, Offset(point(0.0).x, horizon + with(density) { 20.dp.toPx() }))
        ArcLabel(arc.endEvent, arc.end, zone, Offset(point(1.0).x, horizon + with(density) { 20.dp.toPx() }))
    }
}

/** Prayer name and time, centred on [at] (pixels from the top-left, never mirrored). */
@Composable
private fun ArcLabel(e: PrayerEvent, time: Instant, zone: ZoneId, at: Offset) {
    val inset = with(LocalDensity.current) { 6.dp.roundToPx() }
    val context = LocalContext.current
    Column(
        // Fills the sky and places itself by absolute pixels (place, not placeRelative: no RTL mirroring).
        Modifier.fillMaxSize().layout { m, c ->
            val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
            // Centred on the point, but kept inside the sky (large text makes labels wider than the margin).
            val x = (at.x - p.width / 2f).roundToInt().coerceIn(inset, maxOf(inset, c.maxWidth - p.width - inset))
            val y = (at.y - p.height / 2f).roundToInt().coerceIn(0, maxOf(0, c.maxHeight - p.height))
            layout(c.maxWidth, c.maxHeight) { p.place(x, y) }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(Format.prayerName(e)), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1)
        Text(Format.time(context, time, zone), style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = Ink.copy(alpha = 0.8f), maxLines = 1)
    }
}

private fun lerp(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)
