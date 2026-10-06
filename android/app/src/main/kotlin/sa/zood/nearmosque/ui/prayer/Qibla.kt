package sa.zood.nearmosque.ui.prayer

import androidx.compose.runtime.getValue

import sa.zood.nearmosque.ui.theme.Accent

import sa.zood.nearmosque.ui.theme.Ink

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import sa.zood.nearmosque.R
import sa.zood.nearmosque.core.Angles
import sa.zood.nearmosque.core.CompassState
import sa.zood.nearmosque.ui.Format
import androidx.compose.ui.semantics.clearAndSetSemantics
import sa.zood.nearmosque.ui.glass.glass
import androidx.compose.ui.graphics.drawscope.translate
import sa.zood.nearmosque.ui.theme.Tokens
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun qiblaGuidance(compass: CompassState, bearing: Double): String {
    val context = LocalContext.current
    return when (compass) {
        is CompassState.BearingOnly -> stringResource(R.string.qibla_no_sensor, Format.degrees(context, bearing))
        is CompassState.Live -> {
            val rel = Angles.relativeToQibla(bearing, compass.headingTrue)
            when {
                compass.needsCalibration -> stringResource(R.string.qibla_calibrate)
                abs(rel) <= 5 -> stringResource(R.string.qibla_you_are_facing)
                rel > 0 -> stringResource(R.string.qibla_turn_right, Format.degrees(context, abs(rel)))
                else -> stringResource(R.string.qibla_turn_left, Format.degrees(context, abs(rel)))
            }
        }
    }
}

private fun reducedMotion(context: android.content.Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/**
 * The dial. Live: the ring rotates so N points to true north and the gold arrow points to the Qibla,
 * both by the shortest path across 359°/0°. Bearing-only: a fixed north-up diagram.
 * Compass geometry never mirrors with RTL text.
 */
@Composable
fun QiblaDial(bearing: Double, compass: CompassState, aligned: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val live = compass as? CompassState.Live
    val ringTarget = if (live != null) -live.headingTrue else 0.0
    val ring = remember { Animatable(ringTarget.toFloat()) }
    val instant = reducedMotion(context)
    LaunchedEffect(ringTarget) {
        val target = Angles.shortestTarget(ring.value.toDouble(), ringTarget).toFloat()
        if (instant) ring.snapTo(target) else ring.animateTo(target, spring(dampingRatio = 0.9f, stiffness = 220f))
    }
    val north = stringResource(R.string.compass_north)
    val desc = stringResource(R.string.qibla_dial_a11y, stringResource(R.string.qibla_bearing, Format.degrees(context, bearing)))
    val onSurface = Ink
    val kaaba = painterResource(R.drawable.ic_kaaba)
    val textPaint = remember { android.graphics.Paint().apply { isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER } }
    val ink = Ink
    Canvas(modifier.aspectRatio(1f).semantics { contentDescription = desc }) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 - 4.dp.toPx()
        if (aligned) drawCircle(Brush.radialGradient(listOf(Tokens.gold.copy(alpha = 0.45f), Color.Transparent), c, r * 1.05f), r * 1.05f, c)
        drawCircle(Brush.verticalGradient(listOf(ink.copy(alpha = 0.16f), ink.copy(alpha = 0.05f))), r, c)
        drawCircle(Brush.linearGradient(listOf(ink.copy(alpha = 0.55f), ink.copy(alpha = 0.08f), ink.copy(alpha = 0.25f))), r, c, style = Stroke(1.dp.toPx()))
        rotate(ring.value, c) {
            ticks(c, r, onSurface)
            textPaint.color = android.graphics.Color.argb(255, 242, 184, 181)
            textPaint.textSize = r * 0.22f
            drawContext.canvas.nativeCanvas.drawText(north, c.x, c.y - r + r * 0.32f, textPaint)
            // Qibla arrow fixed to the ring at the true bearing.
            rotate(bearing.toFloat(), c) {
                // Direction light: a soft 28° beam from the centre towards the Kaaba, brighter when facing it.
                val beam = androidx.compose.ui.graphics.Path().apply {
                    moveTo(c.x, c.y)
                    arcTo(androidx.compose.ui.geometry.Rect(c, r * 0.96f), -104f, 28f, false)
                    close()
                }
                drawPath(beam, Brush.radialGradient(listOf(Tokens.gold.copy(alpha = if (aligned) 0.6f else 0.24f), Tokens.gold.copy(alpha = 0f)), c, r * 0.96f))
                val tip = Offset(c.x, c.y - r * 0.62f)
                drawLine(Brush.verticalGradient(listOf(Tokens.gold, Tokens.gold.copy(alpha = 0.15f)), startY = tip.y, endY = c.y), c, tip, 3.dp.toPx(), StrokeCap.Round)
                val k = r * 0.26f
                drawCircle(ink.copy(alpha = 0.9f), k * 0.72f, Offset(c.x, c.y - r * 0.74f))
                translate(c.x - k / 2, c.y - r * 0.74f - k / 2) { with(kaaba) { draw(androidx.compose.ui.geometry.Size(k, k)) } }
            }
        }
        // Phone's forward direction.
        if (live != null) drawLine(if (aligned) Tokens.gold else ink, Offset(c.x, c.y - r - 2.dp.toPx()), Offset(c.x, c.y - r + 12.dp.toPx()), 4.dp.toPx(), StrokeCap.Round)
        drawCircle(ink, 9.dp.toPx(), c)
        drawCircle(sa.zood.nearmosque.ui.glass.BLUE, 6.dp.toPx(), c)
    }
}

private fun DrawScope.ticks(c: Offset, r: Float, color: Color) {
    for (i in 0 until 72) {
        val major = i % 18 == 0
        rotate(i * 5f, c) {
            drawLine(
                color.copy(alpha = if (major) 0.7f else 0.25f),
                Offset(c.x, c.y - r), Offset(c.x, c.y - r + (if (major) 12 else 6).dp.toPx()),
                (if (major) 2 else 1).dp.toPx(),
            )
        }
    }
}

/** Full-screen compass on the sky. Sensors run only while this (or a compass tab) is visible. */
@Composable
fun QiblaCompassScreen(ui: PrayerUi, compass: CompassState, aligned: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val bearing = ui.qiblaBearing ?: return
    val sky = sa.zood.nearmosque.ui.theme.LocalSky.current
    Box(Modifier.fillMaxSize()) {
        sa.zood.nearmosque.ui.glass.SkyBackdrop(sky, Modifier.fillMaxSize(), horizon = 0.82f)
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.qibla), style = MaterialTheme.typography.headlineMedium, color = Ink, modifier = Modifier.semantics { heading() })
                    Text(ui.location?.name.orEmpty(), style = MaterialTheme.typography.bodyLarge, color = Ink.copy(alpha = 0.8f))
                }
                sa.zood.nearmosque.ui.glass.GlassIconButton(androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Filled.Close), stringResource(R.string.close), onClose)
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().widthIn(max = 420.dp).weight(1f, fill = false), contentAlignment = Alignment.Center) {
                val live = compass as? CompassState.Live
                GuidingLight(if (live != null) Angles.relativeToQibla(bearing, live.headingTrue) else bearing, aligned, Modifier.matchParentSize())
                QiblaDial(bearing, compass, aligned, Modifier.fillMaxWidth(0.9f))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                qiblaGuidance(compass, bearing), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                color = if (aligned) Accent else Ink,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Spacer(Modifier.height(10.dp))
            val note = when (compass) {
                is CompassState.Live -> compass.accuracyDeg?.let { stringResource(R.string.heading_accuracy, it.roundToInt().toString()) }
                CompassState.BearingOnly -> stringResource(R.string.qibla_north_up)
            }
            note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = 0.75f)) }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.qibla_hold_flat) + " · " + stringResource(R.string.qibla_approximate), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = Ink.copy(alpha = 0.75f))
        }
    }
}

/**
 * The light that guides to the Qibla: a wide glow off the edge of the dial on the Qibla's side, turning
 * with the phone and brightening (and widening) when the phone faces it.
 */
@Composable
private fun GuidingLight(angle: Double, aligned: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val shown = remember { Animatable(angle.toFloat()) }
    val instant = reducedMotion(context)
    LaunchedEffect(angle) {
        val target = Angles.shortestTarget(shown.value.toDouble(), angle).toFloat()
        if (instant) shown.snapTo(target) else shown.animateTo(target, spring(dampingRatio = 0.75f, stiffness = 140f))
    }
    val strength by androidx.compose.animation.core.animateFloatAsState(if (aligned) 1f else 0f, label = "light")
    Canvas(modifier.clearAndSetSemantics {}) {
        val s = size.minDimension
        val rad = Math.toRadians(shown.value.toDouble())
        val at = Offset(center.x + s * 0.5f * kotlin.math.sin(rad).toFloat(), center.y - s * 0.5f * kotlin.math.cos(rad).toFloat())
        val r = s * (0.55f + 0.2f * strength)
        drawCircle(Brush.radialGradient(listOf(Tokens.gold.copy(alpha = 0.45f + 0.3f * strength), Tokens.gold.copy(alpha = 0f)), at, r), r, at)
    }
}
