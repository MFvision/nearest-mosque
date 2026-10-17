package sa.zood.nearmosque.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import sa.zood.nearmosque.core.PrayerEvent
import sa.zood.nearmosque.core.WeatherCondition
import sa.zood.nearmosque.core.WeatherLook
import sa.zood.nearmosque.core.WeatherNow
import sa.zood.nearmosque.core.WeatherSymbol
import java.time.Duration
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/*
 * The desktop's sky (Sky follows the weather, opt-in): a calm strip behind the next-prayer card, shown only while
 * the switch is on, so with it off the window looks exactly as before. It is the phone's time-of-day sky in
 * miniature (shared/design/tokens.json "sky", dark palette) with the shared WeatherLook on top (tokens.json
 * "weather"): a veil, the horizon glow, clouds, an overcast deck, rain streaks, snow dots or a fog band. Without
 * weather (offline, data older than 3 hours) it is the plain time-of-day sky. tools/check_tokens.py scans this file
 * for colours that are not tokens; the card's own colours come from Main.kt by name.
 *
 * Still by design, so a window left open costs nothing: no lightning, no falling rain or snow; only the clouds
 * drift, by under a pixel a second, redrawn with the countdown that ticks anyway. With the desktop's animations
 * off (Platform.animations) nothing moves at all.
 */

/** The prayer period whose sky is shown, as on the phones (android ui/theme/Theme.kt SkyPeriod). */
internal enum class SkyPeriod {
    NIGHT, FAJR, SUNRISE, DAY, ASR, MAGHRIB, ISHA;

    val hasStars: Boolean get() = this == NIGHT || this == ISHA || this == FAJR

    companion object {
        fun at(now: Instant, sunrise: Instant?, current: PrayerEvent?): SkyPeriod = when (current) {
            PrayerEvent.FAJR -> FAJR
            PrayerEvent.SUNRISE -> if (sunrise != null && Duration.between(sunrise, now).seconds < 3600) SUNRISE else DAY
            PrayerEvent.DHUHR -> DAY
            PrayerEvent.ASR -> ASR
            PrayerEvent.MAGHRIB -> MAGHRIB
            PrayerEvent.ISHA -> ISHA
            null -> NIGHT
        }

        /** The period in the chosen city now: the last of today's times already passed (none: before Fajr, night). */
        fun of(clock: Clock, now: Instant): SkyPeriod {
            val today = clock.today(now)
            val current = PrayerEvent.entries.lastOrNull { e -> today[e]?.let { !it.isAfter(now) } == true }
            return at(now, today[PrayerEvent.SUNRISE], current)
        }
    }
}

/** One period's dark sky, from tokens.json "sky": top and mid carry the light, glow is the horizon, low the ground. */
internal data class SkyColors(val top: Color, val mid: Color, val glow: Color, val low: Color) {
    /** Every stop mixed toward the weather's veil by [f] (WeatherLook.veilMix). */
    fun veiled(veil: Color, f: Float): SkyColors = SkyColors(lerp(top, veil, f), lerp(mid, veil, f), lerp(glow, veil, f), lerp(low, veil, f))

    companion object {
        fun of(p: SkyPeriod): SkyColors = when (p) {
            SkyPeriod.NIGHT -> SkyColors(Color(0xFF081B29), Color(0xFF12304A), Color(0xFF2C4C7A), Color(0xFF0B1A2C))
            SkyPeriod.FAJR -> SkyColors(Color(0xFF141E3C), Color(0xFF3A3566), Color(0xFFC98A8A), Color(0xFF1E2242))
            SkyPeriod.SUNRISE -> SkyColors(Color(0xFF1C3F7A), Color(0xFF345A99), Color(0xFFF3C98B), Color(0xFF26365E))
            SkyPeriod.DAY -> SkyColors(Color(0xFF1C3F7A), Color(0xFF2F5590), Color(0xFFF7E0A8), Color(0xFF22406E))
            SkyPeriod.ASR -> SkyColors(Color(0xFF173F5A), Color(0xFF23606F), Color(0xFFF2D27A), Color(0xFF1D3E4E))
            SkyPeriod.MAGHRIB -> SkyColors(Color(0xFF1B1F45), Color(0xFF4A2F5E), Color(0xFFF08A5D), Color(0xFF2A1E3E))
            SkyPeriod.ISHA -> SkyColors(Color(0xFF0A1430), Color(0xFF16264D), Color(0xFF3A4E86), Color(0xFF0B1530))
        }
    }
}

/** The weather's colours on the dark sky, from tokens.json "weather". */
internal object WeatherColors {
    private val veil = mapOf(
        WeatherCondition.PARTLY_CLOUDY to Color(0xFF2B3440), WeatherCondition.CLOUDY to Color(0xFF2B3440),
        WeatherCondition.RAIN to Color(0xFF25303B), WeatherCondition.THUNDERSTORM to Color(0xFF1F2630),
        WeatherCondition.FOG to Color(0xFF3A434D), WeatherCondition.SNOW to Color(0xFF3B4656),
    )
    val streak = Color(0xFFC9D6E3)
    val flake = Color(0xFFFFFFFF)
    val fog = Color(0xFF3A434D)

    /** null for a clear sky (no veil). */
    fun veil(condition: WeatherCondition): Color? = veil[condition]
}

/**
 * The strip's own strengths. Under the card's text (Gold, Ink, Muted) every layer is bounded and a scrim of the card
 * colour lies over all of it; SkyStripTest checks 4.5:1 for the three inks on every period and condition (showers
 * too) with these numbers. A 35% scrim would leave Muted at about 3.8:1 on the day sky. Past the end of the text
 * the scrim thins out and a warmer glow sits in the far corner, where no text ever is.
 */
internal object StripLook {
    /** The card colour over the sky under the text. */
    const val SCRIM = 0.6f
    /** The card colour over the sky past the text, reached after a soft fade (at least [SCRIM_FADE_DP], or 40% of the room). */
    const val SCRIM_FAR = 0.3f
    const val SCRIM_FADE_DP = 140f
    /** The far corner's glow (never under text), times glowStrength. */
    const val FAR_GLOW = 0.35f
    /** The bottom of the gradient: mid mixed toward the glow by this times WeatherLook.glowStrength. */
    const val HORIZON = 0.08f
    /** The horizon glow's peak alpha, times glowStrength. */
    const val GLOW = 0.12f
    /** Clouds: the phone's puff alphas (at most 0.2) times this times the cloud opacity. */
    const val CLOUDS = 0.6f
    /** Cloud colour: the sky's mid stop mixed this far toward white (softer than the phone's, on a dim card). */
    const val CLOUD_WHITE = 0.45f
    /** Overcast deck: the veil band's alpha at full deck (the banks keep the phone's alphas, at most 0.3). */
    const val DECK = 0.3f
    /** Rain streaks, snow dots and stars before the scrim (so 40% of these on screen). */
    const val RAIN = 0.4f
    const val SNOW = 0.75f
    const val STARS = 0.6f
    /** The fog band's peak alpha, times WeatherLook.fogBand. */
    const val FOG = 0.7f
    /** Cloud drift: one slow sway every ten minutes, at most a fifth of the card's width. */
    const val DRIFT_PERIOD_MS = 600_000L
}

/**
 * The sky behind the next-prayer card: [period]'s sky with [weather] (null: the plain time-of-day sky). [textWidth]
 * (px from the card's start edge) is kept fully under the scrim; unknown, the whole card is. [now] moves the clouds
 * when [animate]; otherwise everything is one still picture. Decorative: screen readers skip it (the weather line
 * under the city name says the same in words). Not [framed] (the onboarding's whole-window sky), it has square corners
 * and no scrim: the text there sits on glass or carries its own shadow.
 */
@Composable
internal fun SkyStrip(
    period: SkyPeriod, weather: WeatherNow?, now: Instant, animate: Boolean, modifier: Modifier = Modifier, textWidth: Float = Float.MAX_VALUE,
    framed: Boolean = true,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // The last weather is kept while its layers fade out (switch turned off, data too old).
    val held = remember { HeldSymbol() }
    if (weather != null) held.symbol = weather.symbol
    val fade by animateFloatAsState(if (weather != null) 1f else 0f, if (animate) tween(1500) else snap(), label = "weather")
    val symbol = if (weather != null || fade > 0f) held.symbol else null
    val look = WeatherLook.of(symbol, night = period.hasStars)
    val veil = symbol?.let { WeatherColors.veil(it.condition) }
    val base = SkyColors.of(period)
    val target = if (veil != null && look.veilMix > 0.0) base.veiled(veil, look.veilMix.toFloat() * fade) else base
    val colorSpec = if (animate) tween<Color>(1200) else snap()
    val top by animateColorAsState(target.top, colorSpec, label = "top")
    val mid by animateColorAsState(target.mid, colorSpec, label = "mid")
    val glow by animateColorAsState(target.glow, colorSpec, label = "glow")
    val drift = if (animate) pingPong((now.toEpochMilli() % StripLook.DRIFT_PERIOD_MS).toFloat() / StripLook.DRIFT_PERIOD_MS) else 0.3f
    val layers = StripLayers(
        glow = mix(1f, look.glowStrength.toFloat(), fade),
        stars = if (period.hasStars) mix(1f, look.starOpacity.toFloat(), fade) else 0f,
        clouds = mix(defaultClouds(period), (look.cloudOpacity?.toFloat() ?: defaultClouds(period)), fade),
        deck = look.deckOpacity.toFloat() * fade,
        fog = look.fogBand.toFloat() * fade,
        rain = if (fade > 0f) look.rainStreaks else 0,
        snow = if (fade > 0f) look.snowFlakes else 0,
        particles = fade,
    )
    Canvas((if (framed) modifier.clip(RoundedCornerShape(20.dp)) else modifier).clearAndSetSemantics { }) {
        drawStrip(top, mid, glow, veil, layers, drift, rtl)
        if (framed) drawFarSide(glow, layers.glow, textWidth, rtl)
    }
}

/**
 * Past the text: a warm glow in the far bottom corner that never reaches the text, then the scrim, solid over the
 * text and thinning to [StripLook.SCRIM_FAR] over a long, soft fade.
 */
private fun DrawScope.drawFarSide(glow: Color, strength: Float, textWidth: Float, rtl: Boolean) {
    val w = size.width
    val text = textWidth.coerceIn(0f, w)
    val room = minOf(w - text, w * 0.4f)
    if (room > 24.dp.toPx()) {
        val c = Offset(if (rtl) 0f else w, size.height)
        scale(1f, 0.6f, pivot = c) {
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = StripLook.FAR_GLOW * strength), glow.copy(alpha = 0f)), center = c, radius = room), radius = room, center = c)
        }
    }
    val near = Card.copy(alpha = StripLook.SCRIM)
    val far = Card.copy(alpha = StripLook.SCRIM_FAR)
    val fadeEnd = text + maxOf(StripLook.SCRIM_FADE_DP.dp.toPx(), (w - text) * 0.4f)
    if (text >= w) drawRect(near)
    else drawRect(Brush.horizontalGradient(listOf(near, far), startX = if (rtl) w - text else text, endX = if (rtl) w - fadeEnd else fadeEnd))
}

/** How much of each layer is drawn (WeatherLook, faded in or out). */
internal data class StripLayers(
    val glow: Float, val stars: Float, val clouds: Float, val deck: Float, val fog: Float, val rain: Int, val snow: Int, val particles: Float,
)

/** The phone's cloud band: fainter on the night skies. */
internal fun defaultClouds(p: SkyPeriod): Float = if (p.hasStars) 0.35f else 0.8f

private class HeldSymbol { var symbol: WeatherSymbol? = null }

private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** 0 → 1 → 0 over t in 0..1. */
private fun pingPong(t: Float): Float = 1f - abs(2f * t - 1f)

/** Everything under the scrim. The glow and the busier layers lean to the card's empty end (left in Arabic). */
private fun DrawScope.drawStrip(top: Color, mid: Color, glow: Color, veil: Color?, l: StripLayers, drift: Float, rtl: Boolean) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(0f to top, 0.62f to mid, 1f to lerp(mid, glow, StripLook.HORIZON * l.glow)))
    if (l.stars > 0f) drawStars(Size(w, h * 0.75f), StripLook.STARS * l.stars, rtl)
    // The horizon glow along the bottom edge, towards the end of the card.
    val gc = Offset(w * (if (rtl) 0.3f else 0.7f), h)
    val gr = w * 0.55f
    scale(1f, 0.45f, pivot = gc) {
        drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = StripLook.GLOW * l.glow), glow.copy(alpha = 0f)), center = gc, radius = gr), radius = gr, center = gc)
    }
    val cloudTint = lerp(mid, Color.White, StripLook.CLOUD_WHITE)
    translate(left = -w * (0.05f + 0.2f * drift), top = h * 0.08f) {
        drawPuffs(PUFFS, Size(w * 1.4f, h * 0.7f), cloudTint, StripLook.CLOUDS * l.clouds)
    }
    if (veil != null && l.deck > 0f) drawOvercast(veil, l.deck, drift)
    if (l.rain > 0) drawRain(l.rain, WeatherColors.streak, StripLook.RAIN * l.particles, rtl)
    if (l.snow > 0) drawSnow(l.snow, WeatherColors.flake, StripLook.SNOW * l.particles, rtl)
    if (l.fog > 0f) drawFogBand(WeatherColors.fog, StripLook.FOG * l.fog)
}

// x, y (fractions of the band), radius (fraction of its width), vertical squash, alpha: the phone's cloud band.
internal val PUFFS = listOf(
    floatArrayOf(0.08f, 0.55f, 0.10f, 0.32f, 0.20f), floatArrayOf(0.18f, 0.62f, 0.14f, 0.28f, 0.16f), floatArrayOf(0.30f, 0.40f, 0.09f, 0.35f, 0.14f),
    floatArrayOf(0.42f, 0.70f, 0.16f, 0.25f, 0.18f), floatArrayOf(0.55f, 0.50f, 0.12f, 0.30f, 0.15f), floatArrayOf(0.66f, 0.66f, 0.15f, 0.26f, 0.20f),
    floatArrayOf(0.78f, 0.45f, 0.10f, 0.34f, 0.13f), floatArrayOf(0.88f, 0.60f, 0.14f, 0.28f, 0.17f), floatArrayOf(0.97f, 0.52f, 0.09f, 0.32f, 0.12f),
)

// The phone's overcast banks (android ui/glass/WeatherSky.kt).
internal val DECK_PUFFS = listOf(
    floatArrayOf(0.05f, 0.30f, 0.20f, 0.30f, 0.30f), floatArrayOf(0.24f, 0.18f, 0.24f, 0.26f, 0.26f), floatArrayOf(0.42f, 0.36f, 0.22f, 0.28f, 0.28f),
    floatArrayOf(0.60f, 0.20f, 0.26f, 0.24f, 0.26f), floatArrayOf(0.78f, 0.34f, 0.22f, 0.28f, 0.30f), floatArrayOf(0.96f, 0.22f, 0.24f, 0.26f, 0.26f),
    floatArrayOf(0.14f, 0.62f, 0.20f, 0.22f, 0.20f), floatArrayOf(0.50f, 0.66f, 0.24f, 0.20f, 0.20f), floatArrayOf(0.86f, 0.60f, 0.20f, 0.22f, 0.20f),
)

private fun DrawScope.drawPuffs(puffs: List<FloatArray>, band: Size, tint: Color, opacity: Float) {
    if (opacity <= 0f) return
    for (p in puffs) {
        val c = Offset(p[0] * band.width, p[1] * band.height)
        val r = p[2] * band.width
        scale(1f, p[3], pivot = c) {
            drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = (p[4] * opacity).coerceIn(0f, 1f)), tint.copy(alpha = 0f)), center = c, radius = r), radius = r, center = c)
        }
    }
}

/** Overcast: a band of the veil from the top and wide flat banks a shade lighter, drifting with the clouds. */
private fun DrawScope.drawOvercast(veil: Color, opacity: Float, drift: Float) {
    val o = opacity.coerceIn(0f, 1f)
    val a = StripLook.DECK * o
    drawRect(Brush.verticalGradient(0f to veil.copy(alpha = a), 0.6f to veil.copy(alpha = a * 0.6f), 1f to veil.copy(alpha = 0f)))
    translate(left = -size.width * (0.05f + 0.2f * drift)) {
        drawPuffs(DECK_PUFFS, Size(size.width * 1.4f, size.height * 0.9f), lerp(veil, Color.White, 0.08f), o)
    }
}

/** Small deterministic generator: the same stars, streaks and dots in every frame and every screenshot. */
private class Lcg(seed: Long) {
    private var s = seed * 6364136223846793005L + 1442695040888963407L
    fun next(): Float {
        s = s * 6364136223846793005L + 1442695040888963407L
        return ((s ushr 33) % 10_000).toFloat() / 10_000f
    }
}

/** 0.45 at the card's text side, 1 at its empty end, so the start of the card stays quiet. */
private fun calm(x: Float, width: Float, rtl: Boolean): Float {
    val f = (x / width).coerceIn(0f, 1f)
    return 0.45f + 0.55f * (if (rtl) 1f - f else f)
}

private fun DrawScope.drawStars(area: Size, alpha: Float, rtl: Boolean) {
    val rnd = Lcg(7)
    repeat(28) {
        val x = rnd.next() * area.width
        val y = rnd.next() * area.height
        val r = (0.5f + rnd.next() * 0.8f) * density
        val a = alpha * (1f - (y / area.height) * 0.6f) * calm(x, area.width, rtl)
        drawCircle(Color.White.copy(alpha = a.coerceIn(0f, 1f)), r, Offset(x, y))
    }
}

private val SLANT = 12.0 * PI / 180.0

/** Rain: thin still streaks at 12°, fainter towards the top and the text side. */
private fun DrawScope.drawRain(count: Int, color: Color, alpha: Float, rtl: Boolean) {
    val rnd = Lcg(11)
    val tanS = tan(SLANT).toFloat()
    val sinS = sin(SLANT).toFloat()
    val cosS = cos(SLANT).toFloat()
    val w = size.width
    val h = size.height
    repeat(count) { i ->
        val near = i % 3 == 0
        val len = (10f + 8f * rnd.next()) * density
        val y = rnd.next() * (h + len) - len
        val x = rnd.next() * (w + h * tanS) - (y + len) * tanS
        val a = alpha * (if (near) 1f else 0.6f) * (0.7f + 0.3f * rnd.next()) * (0.4f + 0.6f * (y / h).coerceIn(0f, 1f)) * calm(x, w, rtl)
        drawLine(color.copy(alpha = a.coerceIn(0f, 1f)), Offset(x, y), Offset(x - len * sinS, y + len * cosS), strokeWidth = 1f * density, cap = StrokeCap.Round)
    }
}

/** Snow: small still dots (1-2 dp), fainter towards the top and the text side. */
private fun DrawScope.drawSnow(count: Int, color: Color, alpha: Float, rtl: Boolean) {
    val rnd = Lcg(23)
    val w = size.width
    val h = size.height
    repeat(count) { i ->
        val near = i % 3 == 0
        val r = (if (near) 1.5f + 0.6f * rnd.next() else 1f + 0.5f * rnd.next()) * density
        val x = rnd.next() * w
        val y = rnd.next() * h
        val a = alpha * (if (near) 0.95f else 0.7f) * (0.4f + 0.6f * (y / h).coerceIn(0f, 1f)) * calm(x, w, rtl)
        drawCircle(color.copy(alpha = a.coerceIn(0f, 1f)), r, Offset(x, y))
    }
}

/** Fog: a soft band rising from the bottom edge over the lower half of the card. */
private fun DrawScope.drawFogBand(color: Color, alpha: Float) {
    val top = size.height * 0.4f
    drawRect(
        Brush.verticalGradient(0f to color.copy(alpha = 0f), 0.7f to color.copy(alpha = alpha), 1f to color.copy(alpha = alpha), startY = top, endY = size.height),
        topLeft = Offset(0f, top), size = Size(size.width, size.height - top),
    )
}
