package sa.zood.nearmosque.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import sa.zood.nearmosque.core.WeatherLook
import sa.zood.nearmosque.core.WeatherNow
import sa.zood.nearmosque.ui.theme.Sky

/**
 * A 0…1 value that loops while [enabled]; [still] otherwise. No infinite transition is created when
 * disabled, so "remove animations" really stops all motion (and UI tests can settle).
 */
@Composable
fun loopingFloat(enabled: Boolean, periodMs: Int, reverse: Boolean, label: String, still: Float): Float {
    if (!enabled) return still
    val t = rememberInfiniteTransition(label = label)
    val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing), if (reverse) RepeatMode.Reverse else RepeatMode.Restart), label = label)
    return v
}

/** System "remove animations" setting (animator duration scale 0). */
@Composable
fun reducedMotion(): Boolean {
    val ctx = LocalContext.current
    return android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}

/**
 * Full-screen landscape behind the glass: gradient sky, stars at night, drifting clouds, a horizon
 * glow behind two mountain ridges and a calm lake; optionally a mosque skyline. Decorative only.
 * Text never sits over the brightest glow (tools/check_tokens.py).
 *
 * With [weather] (Sky follows the weather, opt-in) the same sky also shows the city's weather: pass a [sky]
 * already veiled with Sky.under. Stars, glow and clouds are scaled by the shared WeatherLook, and an overcast
 * deck, rain, snow, a fog band or a rare soft flash (animations on, not in Battery Saver) are added. The layers
 * fade in and out over 2 s; with animations off everything is one still frame.
 */
@Composable
fun SkyBackdrop(
    sky: Sky, modifier: Modifier = Modifier, horizon: Float = 0.64f, skyline: Boolean = false, animate: Boolean = !reducedMotion(),
    weather: WeatherNow? = null,
) {
    val top by animateColorAsState(sky.top, tween(1200), label = "top")
    val mid by animateColorAsState(sky.mid, tween(1200), label = "mid")
    val glow by animateColorAsState(sky.glow, tween(1200), label = "glow")
    val low by animateColorAsState(sky.low, tween(1200), label = "low")
    val d = loopingFloat(animate, 140_000, reverse = true, label = "drift", still = 0.3f)
    val tw = loopingFloat(animate, 3200, reverse = true, label = "twinkle", still = 0.5f)
    val stars = sky.period.hasStars
    // The last weather is kept while its layers fade out after the switch is turned off or the data gets old.
    val held = remember { HeldWeather() }
    if (weather != null) held.symbol = weather.symbol
    val fade by animateFloatAsState(if (weather != null) 1f else 0f, if (animate) tween(2000) else snap(), label = "weather")
    val symbol = if (weather != null || fade > 0f) held.symbol else null
    val look = WeatherLook.of(symbol, night = stars)
    val veil = symbol?.let { WeatherTokens.veil(it.condition, sky.dark) }
    val starK = lerpF(1f, look.starOpacity.toFloat(), fade)
    val glowK = lerpF(1f, look.glowStrength.toFloat(), fade)
    val cloudDefault = if (stars) 0.35f else 0.8f
    val cloudK = lerpF(cloudDefault, look.cloudOpacity?.toFloat() ?: cloudDefault, fade)
    val deck = look.deckOpacity.toFloat() * fade
    val fog = look.fogBand.toFloat() * fade
    val rain = look.rainStreaks > 0 && fade > 0f
    val snow = look.snowFlakes > 0 && fade > 0f
    // Motion only through loopingFloat: with animations off each is a fixed value and nothing loops.
    val rainPhase = loopingFloat(animate && rain, 1600, reverse = false, label = "rain", still = 0.37f)
    val snowPhase = loopingFloat(animate && snow, 14_000, reverse = false, label = "snow", still = 0.42f)
    val flashes = look.lightning && weather != null && animate && !powerSaveMode()
    val bolt = loopingFloat(flashes, 12_000, reverse = false, label = "bolt", still = 0f)
    Canvas(modifier.clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height
        val y0 = h * horizon
        drawRect(
            Brush.verticalGradient(
                0f to top, horizon * 0.72f to mid, horizon to lerp(mid, glow, 0.4f),
                (horizon + 0.14f).coerceAtMost(1f) to low, 1f to low,
            ),
        )
        if (stars && starK > 0f) {
            drawStars(7, Size(w, y0 * 0.85f), (0.55f + 0.4f * tw) * starK)
            drawStars(31, Size(w, y0 * 0.85f), (0.9f - 0.45f * tw) * starK)
        }
        // Horizon glow (ellipse), faded to nothing well below the text area.
        scale(1f, 0.42f, pivot = Offset(w / 2, y0)) {
            drawCircle(
                Brush.radialGradient(listOf(glow.copy(alpha = 0.95f * glowK), glow.copy(alpha = 0.4f * glowK), glow.copy(alpha = 0f)), center = Offset(w / 2, y0), radius = w * 0.7f),
                radius = w * 0.7f, center = Offset(w / 2, y0),
            )
        }
        // Light skies: softer ridges and lake (the night palette darkens them a lot more).
        val shade = if (sky.dark) 1f else 0.35f
        val cloudTint = lerp(Color.White, glow, if (sky.dark) 0.35f else 0.15f)
        translate(left = -w * (0.1f + 0.45f * d), top = y0 * 0.38f) {
            drawClouds(Size(w * 1.7f, y0 * 0.55f), cloudTint, cloudK)
        }
        if (veil != null && deck > 0f) drawOvercast(Size(w, y0), veil, sky.dark, deck, d)
        if (flashes) drawLightning(Size(w, y0), bolt, sky.dark)
        val lakeTop = y0 + h * 0.06f
        if (rain) drawRain(Size(w, lakeTop), look.rainStreaks, WeatherTokens.streak(sky.dark), (if (sky.dark) 0.36f else 0.26f) * fade, rainPhase)
        if (snow) drawSnow(Size(w, lakeTop), look.snowFlakes, if (sky.dark) WeatherTokens.streakDark else WeatherTokens.flake, if (sky.dark) null else WeatherTokens.streakLight, fade, snowPhase)
        drawRidge(FAR, Offset(0f, y0 - h * 0.075f), Size(w, h * 0.16f), Brush.verticalGradient(listOf(lerp(mid, glow, 0.22f).copy(alpha = 0.85f), low), startY = y0 - h * 0.075f, endY = y0 + h * 0.085f))
        if (skyline) drawSkyline(Offset(w * 0.19f, y0 - h * 0.13f), Size(w * 0.62f, h * 0.15f), lerp(low, Color.Black, 0.25f * shade))
        drawRidge(NEAR, Offset(0f, y0 - h * 0.035f), Size(w, h * 0.12f), Brush.verticalGradient(listOf(lerp(low, Color.Black, 0.18f * shade), lerp(low, Color.Black, 0.18f * shade))))
        drawRect(Brush.verticalGradient(listOf(lerp(low, glow, 0.12f), lerp(low, Color.Black, 0.2f * shade)), startY = lakeTop, endY = h), topLeft = Offset(0f, lakeTop), size = Size(w, h - lakeTop))
        scale(1f, 1.3f, pivot = Offset(w / 2, y0 + h * 0.16f)) {
            drawCircle(
                Brush.radialGradient(listOf(glow.copy(alpha = 0.32f * glowK * (0.75f + 0.25f * tw)), glow.copy(alpha = 0f)), center = Offset(w / 2, y0 + h * 0.16f), radius = w * 0.3f),
                radius = w * 0.3f, center = Offset(w / 2, y0 + h * 0.16f),
            )
        }
        if (fog > 0f) drawFogBand(y0, WeatherTokens.fog(sky.dark), fog)
    }
}

/** The symbol last shown, so its layers can fade out after the weather goes away. */
private class HeldWeather { var symbol: sa.zood.nearmosque.core.WeatherSymbol? = null }

private fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private val FAR = listOf(0f to 0.55f, 0.08f to 0.42f, 0.17f to 0.2f, 0.26f to 0.38f, 0.36f to 0.3f, 0.47f to 0.5f, 0.58f to 0.34f, 0.7f to 0.12f, 0.8f to 0.36f, 0.9f to 0.28f, 1f to 0.45f)
private val NEAR = listOf(0f to 0.35f, 0.12f to 0.55f, 0.24f to 0.42f, 0.38f to 0.7f, 0.52f to 0.6f, 0.66f to 0.75f, 0.8f to 0.5f, 0.92f to 0.62f, 1f to 0.4f)

private fun DrawScope.drawRidge(points: List<Pair<Float, Float>>, origin: Offset, sz: Size, brush: Brush) {
    val pts = points.map { Offset(origin.x + it.first * sz.width, origin.y + it.second * sz.height) }
    val p = Path().apply {
        moveTo(origin.x, origin.y + sz.height)
        lineTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val b = pts[i]
            quadraticTo(a.x, a.y, (a.x + b.x) / 2, (a.y + b.y) / 2)
        }
        lineTo(pts.last().x, pts.last().y)
        lineTo(origin.x + sz.width, origin.y + sz.height)
        close()
    }
    drawPath(p, brush)
}

private val PUFFS = listOf(
    floatArrayOf(0.08f, 0.55f, 0.10f, 0.32f, 0.20f), floatArrayOf(0.18f, 0.62f, 0.14f, 0.28f, 0.16f), floatArrayOf(0.30f, 0.40f, 0.09f, 0.35f, 0.14f),
    floatArrayOf(0.42f, 0.70f, 0.16f, 0.25f, 0.18f), floatArrayOf(0.55f, 0.50f, 0.12f, 0.30f, 0.15f), floatArrayOf(0.66f, 0.66f, 0.15f, 0.26f, 0.20f),
    floatArrayOf(0.78f, 0.45f, 0.10f, 0.34f, 0.13f), floatArrayOf(0.88f, 0.60f, 0.14f, 0.28f, 0.17f), floatArrayOf(0.97f, 0.52f, 0.09f, 0.32f, 0.12f),
)

private fun DrawScope.drawClouds(sz: Size, tint: Color, opacity: Float) {
    for (p in PUFFS) {
        val c = Offset(p[0] * sz.width, p[1] * sz.height)
        val r = p[2] * sz.width
        scale(1f, p[3], pivot = c) {
            drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = p[4] * opacity), tint.copy(alpha = 0f)), center = c, radius = r), radius = r, center = c)
        }
    }
}

internal fun DrawScope.drawStars(seed: Long, area: Size, alpha: Float) {
    var s = seed * 6364136223846793005L + 1442695040888963407L
    fun next(): Float {
        s = s * 6364136223846793005L + 1442695040888963407L
        return ((s ushr 33) % 10_000).toFloat() / 10_000f
    }
    repeat(45) {
        val x = next() * area.width
        val y = next() * area.height
        val r = (0.5f + next() * 1.1f) * density
        val fade = 1f - (y / area.height) * 0.7f
        drawCircle(Color.White.copy(alpha = (fade * alpha).coerceIn(0f, 1f)), radius = r, center = Offset(x, y))
    }
}

/** Mosque skyline on the horizon: a large dome, two side domes, two minarets. */
fun DrawScope.drawSkyline(origin: Offset, sz: Size, color: Color) {
    fun p(x: Float, y: Float) = Offset(origin.x + x * sz.width, origin.y + y * sz.height)
    val path = Path()
    fun rect(x: Float, y: Float, w: Float, h: Float) {
        val a = p(x, y)
        path.addRect(androidx.compose.ui.geometry.Rect(a.x, a.y, a.x + w * sz.width, a.y + h * sz.height))
    }
    rect(0.16f, 0.66f, 0.68f, 0.34f)
    fun dome(cx: Float, hw: Float, base: Float, top: Float) {
        val s0 = p(cx - hw, base)
        path.moveTo(s0.x, s0.y)
        val c1 = p(cx - hw * 1.05f, base - (base - top) * 0.55f); val c2 = p(cx - hw * 0.45f, top + (base - top) * 0.2f); val e1 = p(cx, top)
        path.cubicTo(c1.x, c1.y, c2.x, c2.y, e1.x, e1.y)
        val c3 = p(cx + hw * 0.45f, top + (base - top) * 0.2f); val c4 = p(cx + hw * 1.05f, base - (base - top) * 0.55f); val e2 = p(cx + hw, base)
        path.cubicTo(c3.x, c3.y, c4.x, c4.y, e2.x, e2.y)
        path.close()
        rect(cx - 0.004f, top - 0.08f, 0.008f, 0.09f)
    }
    dome(0.5f, 0.17f, 0.68f, 0.2f)
    dome(0.27f, 0.08f, 0.7f, 0.46f)
    dome(0.73f, 0.08f, 0.7f, 0.46f)
    for (x in listOf(0.06f, 0.9f)) {
        rect(x, 0.12f, 0.04f, 0.88f)
        rect(x - 0.008f, 0.34f, 0.056f, 0.03f)
        val a = p(x, 0.12f); val b = p(x + 0.02f, 0f); val c = p(x + 0.04f, 0.12f)
        path.moveTo(a.x, a.y); path.lineTo(b.x, b.y); path.lineTo(c.x, c.y); path.close()
    }
    drawPath(path, color)
}

/** A bright stretch of a circle's rim on the side facing [light], fading round it. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLightRim(light: sa.zood.nearmosque.ui.theme.SkyLight, center: Offset, radius: Float, width: Float) {
    // Sweep gradients start at 3 o'clock; the light's angle is from 12 o'clock.
    val c = (((light.angle - 90f) % 360f) + 360f) % 360f / 360f
    val stops = (0..48).map { i ->
        val t = i / 48f
        val d = minOf(kotlin.math.abs(t - c), 1 - kotlin.math.abs(t - c))
        t to light.color.copy(alpha = light.strength * maxOf(0f, 1 - d / 0.2f))
    }.toTypedArray()
    drawCircle(Brush.sweepGradient(*stops, center = center), radius, center, style = androidx.compose.ui.graphics.drawscope.Stroke(width))
}

/** A soft glow just inside a circle on the side facing [light]. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLightSheen(light: sa.zood.nearmosque.ui.theme.SkyLight, center: Offset, radius: Float) {
    val r = Math.toRadians(light.angle.toDouble())
    val at = Offset(center.x + radius * 0.72f * kotlin.math.sin(r).toFloat(), center.y - radius * 0.72f * kotlin.math.cos(r).toFloat())
    val g = radius * 0.84f
    drawCircle(Brush.radialGradient(listOf(light.color.copy(alpha = 0.6f * light.strength), light.color.copy(alpha = 0f)), at, g), g, at)
}
