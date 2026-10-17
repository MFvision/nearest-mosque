package sa.zood.nearmosque.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import sa.zood.nearmosque.core.Weather
import sa.zood.nearmosque.core.WeatherLook
import sa.zood.nearmosque.core.WeatherSymbol
import kotlin.math.max
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The card's Gold, Ink and Muted text keeps 4.5:1 on the sky strip for every period and weather (showers too),
 * with the strip's own numbers (StripLook) and colours (SkyColors, WeatherColors). Each layer is taken at its
 * brightest where it could meet text, two clouds overlapping, and the card-coloured scrim over all of it. Rain,
 * snow and stars are left out: single-pixel lines and dots, faint, and kept away from the text side.
 */
class SkyStripTest {
    private fun channel(c: Float): Double = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun luminance(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    private fun ratio(a: Color, b: Color): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    /** Source-over in sRGB (how the canvas blends), [n] layers deep; whichever is brighter per channel is kept. */
    private fun over(x: Color, c: Color, a: Float, n: Int = 1): Color {
        var y = x
        repeat(n) { y = Color(y.red + (c.red - y.red) * a, y.green + (c.green - y.green) * a, y.blue + (c.blue - y.blue) * a) }
        return brighter(x, y)
    }

    private fun brighter(a: Color, b: Color) = Color(max(a.red, b.red), max(a.green, b.green), max(a.blue, b.blue))

    /** The brightest the strip gets under text for [period] and [symbol] (null: no weather). */
    private fun brightest(period: SkyPeriod, symbol: WeatherSymbol?): Color {
        val look = WeatherLook.of(symbol, night = period.hasStars)
        val veil = symbol?.let { WeatherColors.veil(it.condition) }
        val sky = SkyColors.of(period).let { if (veil != null && look.veilMix > 0) it.veiled(veil, look.veilMix.toFloat()) else it }
        val g = look.glowStrength.toFloat()
        val bottom = lerp(sky.mid, sky.glow, StripLook.HORIZON * g)
        var x = brighter(brighter(sky.top, sky.mid), bottom)
        x = over(x, sky.glow, StripLook.GLOW * g)
        val clouds = look.cloudOpacity?.toFloat() ?: defaultClouds(period)
        x = over(x, lerp(sky.mid, Color.White, StripLook.CLOUD_WHITE), PUFFS.maxOf { it[4] } * StripLook.CLOUDS * clouds, n = 2)
        val deck = look.deckOpacity.toFloat()
        if (veil != null && deck > 0f) {
            x = over(x, veil, StripLook.DECK * deck)
            x = over(x, lerp(veil, Color.White, 0.08f), DECK_PUFFS.maxOf { it[4] } * deck, n = 2)
        }
        if (look.fogBand > 0) x = over(x, WeatherColors.fog, StripLook.FOG * look.fogBand.toFloat())
        // The scrim is drawn source-over in sRGB (Compose's lerp, used for the veil and the tints, mixes in Oklab).
        val s = StripLook.SCRIM
        return Color(x.red + (Card.red - x.red) * s, x.green + (Card.green - x.green) * s, x.blue + (Card.blue - x.blue) * s)
    }

    @Test fun textStaysLegibleOnEverySky() {
        val codes = listOf(
            null, "clearsky", "fair", "partlycloudy", "cloudy", "fog", "lightrain", "heavyrain", "heavyrainshowers",
            "heavyrainandthunder", "heavyrainshowersandthunder", "heavysnow", "heavysnowshowers", "heavysleet",
        )
        val inks = mapOf("Gold" to Gold, "Ink" to Ink, "Muted" to Muted)
        var checks = 0
        val failures = mutableListOf<String>()
        var tightest = Double.MAX_VALUE
        for (period in SkyPeriod.entries) for (code in codes) {
            val bg = brightest(period, code?.let { Weather.symbol(it)!! })
            for ((name, ink) in inks) {
                val r = ratio(ink, bg)
                checks++
                tightest = minOf(tightest, r)
                if (r < 4.5) failures += "$name on $period ${code ?: "no weather"}: %.2f".format(r)
            }
        }
        assertEquals(7 * codes.size * 3, checks)
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
        assertTrue(tightest >= 4.5, "tightest %.2f".format(tightest))
    }

    @Test fun theStripKeepsToTheTokens() {
        // tools/check_tokens.py reads the hex colours in SkyStrip.kt; the card's own colours come in by name.
        val text = java.io.File("src/main/kotlin/sa/zood/nearmosque/desktop/SkyStrip.kt").readText()
        val used = Regex("0xFF([0-9A-Fa-f]{6})").findAll(text).map { it.groupValues[1].uppercase() }.toSet()
        val tokens = java.io.File("../shared/design/tokens.json").readText()
        val missing = used.filter { "#$it" !in tokens.uppercase() }
        assertTrue(missing.isEmpty(), "not in tokens.json: $missing")
        // Every dark sky stop is there.
        assertEquals(28, SkyPeriod.entries.flatMap { SkyColors.of(it).let { s -> listOf(s.top, s.mid, s.glow, s.low) } }.size)
    }
}
