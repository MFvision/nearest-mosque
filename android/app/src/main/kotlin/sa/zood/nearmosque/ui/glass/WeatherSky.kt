package sa.zood.nearmosque.ui.glass

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import sa.zood.nearmosque.core.WeatherCondition
import sa.zood.nearmosque.core.WeatherLook
import sa.zood.nearmosque.core.WeatherNow
import sa.zood.nearmosque.ui.theme.Sky
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/*
 * "Sky follows the weather": the chosen city's weather on the time-of-day sky. The numbers come from the shared
 * WeatherLook (tokens.json "weather"); the colours below are the same tokens (tools/check_tokens.py scans this
 * file for any other colour). Calm by design: a veil over the sky, an overcast deck, thin rain streaks, small
 * flakes, a low fog band and, only with animations on and outside Battery Saver, a rare soft flash. With
 * "remove animations" on, everything is one still frame.
 */

/** Colours of the weather sky, from shared/design/tokens.json "weather". */
object WeatherTokens {
    private val veilDark = mapOf(
        WeatherCondition.PARTLY_CLOUDY to Color(0xFF2B3440), WeatherCondition.CLOUDY to Color(0xFF2B3440),
        WeatherCondition.RAIN to Color(0xFF25303B), WeatherCondition.THUNDERSTORM to Color(0xFF1F2630),
        WeatherCondition.FOG to Color(0xFF3A434D), WeatherCondition.SNOW to Color(0xFF3B4656),
    )
    private val veilLight = mapOf(
        WeatherCondition.PARTLY_CLOUDY to Color(0xFFD9DEE4), WeatherCondition.CLOUDY to Color(0xFFD9DEE4),
        WeatherCondition.RAIN to Color(0xFFCDD5DE), WeatherCondition.THUNDERSTORM to Color(0xFFC4CCD6),
        WeatherCondition.FOG to Color(0xFFE3E6EA), WeatherCondition.SNOW to Color(0xFFE6EBF1),
    )
    val streakDark = Color(0xFFC9D6E3)
    val streakLight = Color(0xFF4A5A66)
    val flake = Color(0xFFFFFFFF)
    val fogDark = Color(0xFF3A434D)
    val fogLight = Color(0xFFE3E6EA)

    /** null for a clear sky (no veil). */
    fun veil(condition: WeatherCondition, dark: Boolean): Color? = (if (dark) veilDark else veilLight)[condition]
    fun streak(dark: Boolean): Color = if (dark) streakDark else streakLight
    fun fog(dark: Boolean): Color = if (dark) fogDark else fogLight
}

/**
 * This sky under [w]: every stop mixed toward the condition's veil (lighter for showers). Unchanged for a clear
 * sky or without weather, so the fallback is always today's sky.
 */
fun Sky.under(w: WeatherNow?): Sky {
    val symbol = w?.symbol ?: return this
    val look = WeatherLook.of(symbol, night = period.hasStars)
    val veil = WeatherTokens.veil(symbol.condition, dark) ?: return this
    if (look.veilMix <= 0.0) return this
    return veiled(veil, look.veilMix.toFloat())
}

/** Battery Saver, followed while shown (no flashes then). */
@Composable
fun powerSaveMode(): Boolean {
    val ctx = LocalContext.current
    val pm = remember(ctx) { ctx.getSystemService(PowerManager::class.java) }
    var on by remember(ctx) { mutableStateOf(pm?.isPowerSaveMode == true) }
    DisposableEffect(ctx) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { on = pm?.isPowerSaveMode == true }
        }
        runCatching { ContextCompat.registerReceiver(ctx, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED) }
        onDispose { runCatching { ctx.unregisterReceiver(receiver) } }
    }
    return on
}

/** The deck is at most this opaque, so text over it keeps 4.5:1 on every light sky (checked against tokens.json). */
internal const val DECK_MAX_ALPHA = 0.3f

/** The rain fades in from the top: 0.35 at the top of the sky, 1 at the horizon (text sits high). */
private fun mask(y: Float, height: Float): Float = 0.35f + 0.65f * (y / height).coerceIn(0f, 1f)

/**
 * Flakes fade harder under the header text, so none reads as a star or as a letter's dot beside it: 0.1 at the
 * top of the sky, 0.3 at 0.6 of the way down, full only at the horizon (as iOS SnowMask).
 */
private fun snowMask(y: Float, height: Float): Float {
    val f = (y / height).coerceIn(0f, 1f)
    return if (f < 0.6f) 0.1f + 0.2f * f / 0.6f else 0.3f + 0.7f * (f - 0.6f) / 0.4f
}

/** Small deterministic generator, so every frame (and every screenshot) places the same streaks and flakes. */
private class Lcg(seed: Long) {
    private var s = seed * 6364136223846793005L + 1442695040888963407L
    fun next(): Float {
        s = s * 6364136223846793005L + 1442695040888963407L
        return ((s ushr 33) % 10_000).toFloat() / 10_000f
    }
}

// x, y (fractions of the deck area), radius (fraction of its width), vertical squash, alpha
private val DECK = listOf(
    floatArrayOf(0.05f, 0.30f, 0.20f, 0.30f, 0.30f), floatArrayOf(0.24f, 0.18f, 0.24f, 0.26f, 0.26f), floatArrayOf(0.42f, 0.36f, 0.22f, 0.28f, 0.28f),
    floatArrayOf(0.60f, 0.20f, 0.26f, 0.24f, 0.26f), floatArrayOf(0.78f, 0.34f, 0.22f, 0.28f, 0.30f), floatArrayOf(0.96f, 0.22f, 0.24f, 0.26f, 0.26f),
    floatArrayOf(0.14f, 0.62f, 0.20f, 0.22f, 0.20f), floatArrayOf(0.50f, 0.66f, 0.24f, 0.20f, 0.20f), floatArrayOf(0.86f, 0.60f, 0.20f, 0.22f, 0.20f),
)

/**
 * The overcast deck over the upper sky ([area]: full width, down to the horizon): a soft band of the veil colour
 * from the top, and wide flat cloud banks drifting with the clouds ([drift] 0..1). [opacity]: WeatherLook.deckOpacity.
 */
internal fun DrawScope.drawOvercast(area: Size, veil: Color, dark: Boolean, opacity: Float, drift: Float) {
    if (opacity <= 0f) return
    val a = DECK_MAX_ALPHA * opacity.coerceIn(0f, 1f)
    val bandH = area.height * 0.8f
    drawRect(
        Brush.verticalGradient(0f to veil.copy(alpha = a), 0.55f to veil.copy(alpha = a * 0.6f), 1f to veil.copy(alpha = 0f), startY = 0f, endY = bandH),
        size = Size(area.width, bandH),
    )
    // Banks a little lighter than the veil (by night only just), so the deck reads as cloud, not as a shade. Lighter
    // only helps the dark ink of the light skies; by night 8% keeps white text far above 4.5:1.
    val tint = lerp(veil, Color.White, if (dark) 0.08f else 0.4f)
    val w = area.width * 1.4f
    val h = area.height * 0.7f
    val dx = -area.width * (0.05f + 0.3f * drift)
    for (p in DECK) {
        val c = Offset(dx + p[0] * w, p[1] * h)
        val r = p[2] * w
        scale(1f, p[3], pivot = c) {
            drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = p[4] * opacity.coerceIn(0f, 1f)), tint.copy(alpha = 0f)), center = c, radius = r), radius = r, center = c)
        }
    }
}

private val SLANT = (12.0 * PI / 180.0)

/**
 * Rain: [count] streaks 1 dp wide and 12-20 dp long at 12°, falling down to the bottom of [area] (the ridges are
 * drawn over their ends). Two layers: far streaks shorter and fainter, near ones twice as fast. [phase] 0..1 loops
 * seamlessly; it is fixed with animations off.
 */
internal fun DrawScope.drawRain(area: Size, count: Int, color: Color, alpha: Float, phase: Float) {
    if (count <= 0 || alpha <= 0f) return
    val rnd = Lcg(11)
    val tanS = tan(SLANT).toFloat()
    val sinS = sin(SLANT).toFloat()
    val cosS = cos(SLANT).toFloat()
    val stroke = 1f * density
    val spanX = area.width + area.height * tanS
    repeat(count) { i ->
        val near = i % 3 == 0
        val len = (12f + 8f * rnd.next()) * density
        val x0 = rnd.next() * spanX
        val base = rnd.next()
        val speed = if (near) 2 else 1
        val t = (base + phase * speed) % 1f
        val y = t * (area.height + len) - len
        val x = x0 - (y + len) * tanS
        val a = alpha * (if (near) 1f else 0.6f) * (0.7f + 0.3f * rnd.next()) * mask(y, area.height)
        drawLine(color.copy(alpha = a.coerceIn(0f, 1f)), Offset(x, y), Offset(x - len * sinS, y + len * cosS), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

/**
 * Snow: [count] small flakes (1.2-2.6 dp) drifting down with a gentle sway, faint under the header text
 * ([snowMask]). On the light skies each flake gets a faint [rim] so it still shows on the pale veil; on the dark
 * ones [color] is a pale blue-grey, not the white of the text.
 */
internal fun DrawScope.drawSnow(area: Size, count: Int, color: Color, rim: Color?, alpha: Float, phase: Float) {
    if (count <= 0 || alpha <= 0f) return
    val rnd = Lcg(23)
    val sway = 6f * density
    repeat(count) { i ->
        val near = i % 3 == 0
        val r = (if (near) 1.8f + 0.8f * rnd.next() else 1.2f + 0.6f * rnd.next()) * density
        val x0 = rnd.next() * area.width
        val base = rnd.next()
        val seed = rnd.next()
        val speed = if (near) 2 else 1
        val t = (base + phase * speed) % 1f
        val y = t * (area.height + 2 * r) - r
        val x = x0 + sway * sin((t * 2f + seed) * 2f * PI.toFloat())
        val m = snowMask(y, area.height)
        val a = alpha * (if (near) 0.95f else 0.7f) * m
        drawCircle(color.copy(alpha = a.coerceIn(0f, 1f)), r, Offset(x, y))
        if (rim != null) drawCircle(rim.copy(alpha = (0.2f * m * alpha).coerceIn(0f, 1f)), r, Offset(x, y), style = Stroke(0.75f * density))
    }
}

/** Fog: a soft band 0.22 of the height, centred on the horizon [y0], over the ridges and the lake. */
internal fun DrawScope.drawFogBand(y0: Float, color: Color, opacity: Float) {
    if (opacity <= 0f) return
    val h = size.height * 0.22f
    val top = y0 - h / 2
    val a = 0.85f * opacity.coerceIn(0f, 1f)
    drawRect(
        Brush.verticalGradient(0f to color.copy(alpha = 0f), 0.5f to color.copy(alpha = a), 1f to color.copy(alpha = 0f), startY = top, endY = top + h),
        topLeft = Offset(0f, top), size = Size(size.width, h),
    )
}

/**
 * Thunderstorm: a soft glow inside the clouds, twice near the start of each cycle ([phase] 0..1 over 12 s), never
 * a bolt and never a hard flash. Only drawn with animations on and outside Battery Saver.
 */
internal fun DrawScope.drawLightning(area: Size, phase: Float, dark: Boolean) {
    val a = pulse(phase, 0f, 0.022f) + 0.55f * pulse(phase, 0.035f, 0.05f)
    if (a <= 0f) return
    val peak = if (dark) 0.16f else 0.24f
    val c = Offset(area.width * 0.68f, area.height * 0.26f)
    val r = area.width * 0.75f
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = (peak * a).coerceIn(0f, 1f)), Color.White.copy(alpha = 0f)), center = c, radius = r), radius = r, center = c)
}

private fun pulse(p: Float, start: Float, end: Float): Float =
    if (p < start || p > end) 0f else sin(PI.toFloat() * (p - start) / (end - start))
