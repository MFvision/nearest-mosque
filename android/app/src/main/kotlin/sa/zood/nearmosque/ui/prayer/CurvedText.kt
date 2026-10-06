package sa.zood.nearmosque.ui.prayer

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Paint for curved labels; sizes follow the font-size setting (sp). */
@Composable
internal fun curvedPaint(size: TextUnit, bold: Boolean, color: Color): Paint {
    val px = with(LocalDensity.current) { size.toPx() }
    return remember(px, bold, color) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = px
            this.color = color.toArgb()
            typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        }
    }
}

/**
 * Draws [text] bent along a circle. Android shapes the string as a normal line first (Arabic letters
 * keep their joined forms, mixed Arabic and Latin keep their order), then lays the shaped line along the
 * arc. [outside]: along the outside, reading clockwise (down the right side, up the left), tops outward;
 * otherwise along the inside, reading left to right across the bottom, tops towards the centre. [angle]
 * (degrees clockwise from the top) is the middle of the text; [radius] is the baseline, in pixels.
 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCurvedText(
    text: String, paint: Paint, center: Offset, radius: Float, angle: Float, outside: Boolean = true,
) {
    val sweep = Math.toDegrees((paint.measureText(text) / radius).toDouble()).toFloat()
    // Arc angles here start at 3 o'clock; ours at 12.
    val mid = angle - 90f
    val oval = RectF(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
    val path = android.graphics.Path().apply {
        if (outside) addArc(oval, mid - sweep / 2, sweep) else addArc(oval, mid + sweep / 2, -sweep)
    }
    // Inside the ring the glyphs hang towards the centre, so their baseline sits on the arc via the ascent.
    val v = if (outside) 0f else -paint.ascent() * 0.85f
    drawIntoCanvas { it.nativeCanvas.drawTextOnPath(text, path, 0f, v, paint) }
}

/**
 * A label curved along the outside of a ring with a small [icon] just before its visual start and, with
 * [onClick], a tap target over it. [center] and [radius] in pixels; the composable fills its parent.
 */
@Composable
internal fun CurvedSideLabel(
    text: String, color: Color, center: Offset, radius: Float, angle: Float,
    icon: Painter? = null, size: TextUnit = 12.sp, bold: Boolean = false, onClick: (() -> Unit)? = null,
) {
    val paint = curvedPaint(size, bold, color)
    val w = paint.measureText(text)
    val gap = with(LocalDensity.current) { 11.dp.toPx() }
    val iconAngle = angle - Math.toDegrees(((w / 2 + gap) / radius).toDouble()).toFloat()
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) { drawCurvedText(text, paint, center, radius, angle) }
        if (icon != null) {
            val a = Math.toRadians(iconAngle.toDouble())
            val r = radius + with(LocalDensity.current) { 5.dp.toPx() }
            Icon(
                icon, contentDescription = null, tint = color,
                modifier = Modifier.pinned(Offset(center.x + r * sin(a).toFloat(), center.y - r * cos(a).toFloat()))
                    .graphicsLayer { rotationZ = iconAngle }.size(14.dp),
            )
        }
        // Tap target (or, without one, a node that reads the label): straight, tangent at the middle.
        val mid = angle - Math.toDegrees((gap / 2 / radius).toDouble()).toFloat()
        val a = Math.toRadians(mid.toDouble())
        val r = radius + with(LocalDensity.current) { 5.dp.toPx() }
        val wDp = with(LocalDensity.current) { (w + gap * 2).toDp() }
        Box(
            Modifier.pinned(Offset(center.x + r * sin(a).toFloat(), center.y - r * cos(a).toFloat()))
                .graphicsLayer { rotationZ = mid }
                .size(wDp, 36.dp)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .semantics(mergeDescendants = true) { contentDescription = text; if (onClick != null) role = Role.Button },
        )
    }
}

/** Places this element centred on [at] (pixels from the top-left of the parent, never mirrored). */
internal fun Modifier.pinned(at: Offset): Modifier = layout { m, c ->
    val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
    layout(c.maxWidth, c.maxHeight) { p.place((at.x - p.width / 2f).roundToInt(), (at.y - p.height / 2f).roundToInt()) }
}
