package sa.zood.nearmosque.core

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.util.Locale

/** Same cases as NMCore WeatherTests.swift, from shared/fixtures/met-compact-sample.json and met-symbols.json. */
class WeatherTest {
    private fun t(s: String) = Instant.parse(s)
    private val sample = Fixtures.file("shared/fixtures/met-compact-sample.json").readText()
    private val symbols = Fixtures.read("shared/fixtures/met-symbols.json").jsonObject
    private val key = "21.42,39.83"
    private val s0 = Duration.ZERO
    private val s30 = Duration.ofSeconds(30)

    private fun expected(o: JsonObject) = WeatherSymbol(
        WeatherCondition.fromKey(o.getValue("condition").jsonPrimitive.content)!!,
        o.getValue("intensity").jsonPrimitive.double,
        o.getValue("showers").jsonPrimitive.boolean,
        Precipitation.valueOf(o.getValue("precipitation").jsonPrimitive.content.uppercase()),
    )

    // Request

    @Test
    fun urlAndKeyUseRoundedCityWithTwoAsciiDecimals() {
        for (e in symbols.getValue("coordinates").jsonArray) {
            val c = e.jsonObject
            val p = LatLng(c.getValue("lat").jsonPrimitive.double, c.getValue("lon").jsonPrimitive.double)
            assertEquals("https://api.met.no/weatherapi/locationforecast/2.0/compact?" + c.getValue("query").jsonPrimitive.content, Weather.url(p))
            assertEquals(c.getValue("key").jsonPrimitive.content, Weather.key(p))
        }
        val u = Weather.url(LatLng(-33.92487, 18.42401))
        assertTrue(u.endsWith("?lat=-33.92&lon=18.42"))
        assertFalse(u.contains("33.924"))
        assertFalse(u.contains("altitude"))
        assertTrue(u.all { it.code < 128 })
    }

    @Test
    fun urlIgnoresTheDefaultLocale() {
        val saved = Locale.getDefault()
        try {
            for (tag in listOf("ar", "ar-SA", "fr", "fr-FR")) {
                Locale.setDefault(Locale.forLanguageTag(tag))
                assertEquals(tag, "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=-33.92&lon=18.42", Weather.url(LatLng(-33.92487, 18.42401)))
                assertEquals(tag, "21.42,39.83", Weather.key(LatLng(21.4225, 39.8262)))
            }
        } finally {
            Locale.setDefault(saved)
        }
        assertEquals("0.00", Weather.coordinate(-0.0))
        assertEquals("0.00", Weather.coordinate(-0.001))
        assertEquals("21.42", Weather.coordinate(21.4199999))
    }

    @Test
    fun userAgentAndHeaders() {
        val ua = Weather.userAgent("Android", "1.4")
        assertEquals("NearMosque/1.4 (Android; +https://nearmosque.net/; app@vision.bz)", ua)
        val cache = WeatherCache(key = key, lastModified = "Sat, 17 Oct 2026 13:17:28 GMT", lastAttempt = t("2026-10-17T13:17:40Z"))
        assertEquals(mapOf("User-Agent" to ua, "If-Modified-Since" to "Sat, 17 Oct 2026 13:17:28 GMT"), Weather.requestHeaders(ua, cache, key))
        assertEquals(mapOf("User-Agent" to ua), Weather.requestHeaders(ua, cache, "-33.92,18.42"))
        assertEquals(mapOf("User-Agent" to ua), Weather.requestHeaders(ua, null, key))
        assertEquals("⁨-3°C⁩", Weather.isolate("-3°C"))
    }

    // Symbols

    @Test
    fun everyLegendCodeAndVariant() {
        val codes = symbols.getValue("codes").jsonObject
        assertEquals(41, codes.size)
        for ((code, o) in codes) assertEquals(code, expected(o.jsonObject), Weather.symbol(code))
        for (code in symbols.getValue("withVariants").jsonArray.map { it.jsonPrimitive.content }) {
            for (suffix in symbols.getValue("suffixes").jsonArray.map { it.jsonPrimitive.content }) {
                assertEquals(code + suffix, expected(codes.getValue(code).jsonObject), Weather.symbol(code + suffix))
            }
        }
        for ((code, o) in symbols.getValue("alternates").jsonObject) assertEquals(code, expected(o.jsonObject), Weather.symbol(code))
        assertEquals(expected(codes.getValue("lightssleetshowersandthunder").jsonObject), Weather.symbol("lightssleetshowersandthunder "))
    }

    @Test
    fun unknownCodesFallBackByTheirWords() {
        for ((code, o) in symbols.getValue("fallback").jsonObject) {
            if (o is JsonNull) assertNull(code, Weather.symbol(code)) else assertEquals(code, expected(o.jsonObject), Weather.symbol(code))
        }
    }

    // Parsing

    @Test
    fun parseKeepsTheNextHoursWithFallbacksAndStrippedSuffixes() {
        val s = Weather.parse(sample, t("2026-10-17T13:20:00Z"))
        assertEquals((12..19).map { t("2026-10-17T$it:00:00Z") }, s.map { it.time })
        assertEquals(WeatherSample(t("2026-10-17T13:00:00Z"), 35.4, 5.7, "fair"), s[1])
        assertEquals(
            listOf("clearsky", "fair", "partlycloudy", "heavyrainshowersandthunder", "cloudy", "lightssnowshowersandthunder", "clearsky", "fog"),
            s.map { it.symbol },
        )
        assertEquals(-2.5, s[5].temperatureC!!, 0.0)
        assertTrue(s.size <= WeatherPolicy.MAX_SAMPLES)
        // Boundaries: now - 90 min and now + 6 h are inclusive.
        assertEquals(t("2026-10-17T12:00:00Z"), Weather.parse(sample, t("2026-10-17T13:30:00Z")).first().time)
        assertEquals(t("2026-10-17T13:00:00Z"), Weather.parse(sample, t("2026-10-17T13:31:00Z")).first().time)
        assertEquals(t("2026-10-17T20:00:00Z"), Weather.parse(sample, t("2026-10-17T14:00:00Z")).last().time)
    }

    @Test
    fun parseReadsTheSharedStepsLikeNMCore() {
        val steps = symbols.getValue("steps").jsonObject
        val now = t(steps.getValue("now").jsonPrimitive.content)
        for (e in steps.getValue("cases").jsonArray.map { it.jsonObject }) {
            val body = """{"properties":{"meta":{"units":{"air_temperature":"celsius","wind_speed":"m/s"}},"timeseries":[${e.getValue("step")}]}}"""
            val x = e.getValue("expected").jsonObject
            fun num(k: String) = (x.getValue(k) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.double
            val want = WeatherSample(t(x.getValue("time").jsonPrimitive.content), num("temperatureC"), num("windMs"), x.getValue("symbol").jsonPrimitive.content)
            assertEquals("$e", listOf(want), Weather.parse(body, now))
        }
    }

    @Test
    fun parseRejectsOtherUnitsAndBadJson() {
        val now = t("2026-10-17T13:20:00Z")
        assertTrue(Weather.parse(sample.replace("\"celsius\"", "\"fahrenheit\""), now).isEmpty())
        assertTrue(Weather.parse(sample.replace("\"m/s\"", "\"knots\""), now).isEmpty())
        assertTrue(Weather.parse("not json", now).isEmpty())
        assertTrue(Weather.parse("{\"properties\":{}}", now).isEmpty())
    }

    // What to show

    private fun fresh(now: Instant) = WeatherPolicy.apply(
        200, mapOf("Last-Modified" to "Sat, 17 Oct 2026 13:17:28 GMT", "Expires" to "Sat, 17 Oct 2026 13:47:30 GMT"),
        sample, now, null, key,
    )

    @Test
    fun nowPicksTheCurrentStep() {
        val c = fresh(t("2026-10-17T13:17:40Z"))
        assertEquals(
            WeatherNow(WeatherSymbol(WeatherCondition.PARTLY_CLOUDY, 0.5), 35.4, 5.7, t("2026-10-17T13:00:00Z")),
            Weather.now(c, key, t("2026-10-17T13:20:00Z")),
        )
        assertEquals(t("2026-10-17T13:00:00Z"), Weather.now(c, key, t("2026-10-17T13:59:59Z"))?.at)
        assertEquals(WeatherCondition.PARTLY_CLOUDY, Weather.now(c, key, t("2026-10-17T14:00:00Z"))?.symbol?.condition)
        assertEquals(WeatherCondition.THUNDERSTORM, Weather.now(c, key, t("2026-10-17T15:10:00Z"))?.symbol?.condition)
        assertEquals(WeatherCondition.CLOUDY, Weather.now(c, key, t("2026-10-17T16:10:00Z"))?.symbol?.condition)
    }

    @Test
    fun nowFallsBackToTheNormalSky() {
        val c = fresh(t("2026-10-17T13:17:40Z"))
        assertNull(Weather.now(null, key, t("2026-10-17T13:20:00Z")))
        assertNull("another city", Weather.now(c, "-33.92,18.42", t("2026-10-17T13:20:00Z")))
        assertNotNull("exactly 3 h old", Weather.now(c, key, t("2026-10-17T16:17:40Z")))
        assertNull("older than 3 h", Weather.now(c, key, t("2026-10-17T16:17:41Z")))
        assertNull(Weather.now(c.copy(validatedAt = null), key, t("2026-10-17T13:20:00Z")))

        val one = WeatherCache(
            key = key, samples = listOf(WeatherSample(t("2026-10-17T13:00:00Z"), 30.0, 2.0, "rain")),
            validatedAt = t("2026-10-17T13:00:00Z"), lastAttempt = t("2026-10-17T13:00:00Z"),
        )
        assertNotNull("90 min after the step", Weather.now(one, key, t("2026-10-17T14:30:00Z")))
        assertNull("no step within 90 min", Weather.now(one, key, t("2026-10-17T14:30:01Z")))
        assertNotNull("first step within 60 min", Weather.now(one, key, t("2026-10-17T12:00:00Z")))
        assertNull(Weather.now(one, key, t("2026-10-17T11:59:59Z")))

        assertNull(Weather.now(one.copy(samples = listOf(one.samples[0].copy(windMs = null))), key, t("2026-10-17T13:10:00Z")))
        assertNull(Weather.now(one.copy(samples = listOf(one.samples[0].copy(symbol = "sandstorm"))), key, t("2026-10-17T13:10:00Z")))
    }

    // Policy

    @Test
    fun refreshSequence() {
        val t0 = t("2026-10-17T13:17:40Z")
        assertEquals("first fetch now", t0, WeatherPolicy.nextFetch(null, key, null, t0, s30))
        var c = fresh(t0)
        assertEquals(8, c.samples.size)
        assertEquals("Sat, 17 Oct 2026 13:17:28 GMT", c.lastModified)
        assertEquals(t("2026-10-17T13:47:30Z"), c.expires)
        assertEquals(t0, c.validatedAt)
        assertEquals(0, c.failures)

        // Before Expires: no request. After: one, with If-Modified-Since.
        assertEquals(t("2026-10-17T13:48:10Z"), WeatherPolicy.nextFetch(c, key, t0, t("2026-10-17T13:27:40Z"), s30))
        assertFalse(WeatherPolicy.shouldFetch(c, key, t0, t("2026-10-17T13:47:31Z"), s0))
        val t1 = t("2026-10-17T13:48:10Z")
        assertTrue(WeatherPolicy.shouldFetch(c, key, t0, t1, s30))
        assertEquals("Sat, 17 Oct 2026 13:17:28 GMT", Weather.requestHeaders("ua", c, key)["If-Modified-Since"])

        // 304: same steps, new Expires, validated again; header names in any case.
        val before = c.samples
        c = WeatherPolicy.apply(304, mapOf("expires" to "Sat, 17 Oct 2026 14:18:30 GMT"), null, t1, c, key)
        assertEquals(before, c.samples)
        assertEquals(t1, c.validatedAt)
        assertEquals(t("2026-10-17T14:18:30Z"), c.expires)
        assertEquals("Sat, 17 Oct 2026 13:17:28 GMT", c.lastModified)

        // 429, then 5xx, then offline: +30 min, then +60 min (cap); the steps stay.
        val t2 = t("2026-10-17T14:19:00Z")
        assertTrue(WeatherPolicy.shouldFetch(c, key, t1, t2, s0))
        c = WeatherPolicy.apply(429, emptyMap(), null, t2, c, key)
        assertEquals(1, c.failures)
        assertEquals(before, c.samples)
        assertEquals(t1, c.validatedAt)
        assertEquals(t("2026-10-17T14:49:00Z"), WeatherPolicy.nextFetch(c, key, t2, t2, s0))
        c = WeatherPolicy.apply(503, emptyMap(), null, t("2026-10-17T14:49:00Z"), c, key)
        assertEquals(t("2026-10-17T15:49:00Z"), WeatherPolicy.nextFetch(c, key, null, t("2026-10-17T14:49:00Z"), s0))
        c = WeatherPolicy.apply(null, emptyMap(), null, t("2026-10-17T15:49:00Z"), c, key)
        assertEquals(3, c.failures)
        assertEquals(t("2026-10-17T16:49:00Z"), WeatherPolicy.nextFetch(c, key, null, t("2026-10-17T15:49:00Z"), s0))
        assertNotNull("still within 3 h of the 304", Weather.now(c, key, t("2026-10-17T16:00:00Z")))
        assertNull("stale: normal sky without a request", Weather.now(c, key, t("2026-10-17T16:49:00Z")))

        // A 200 resets the failures.
        c = WeatherPolicy.apply(200, emptyMap(), sample, t("2026-10-17T16:49:00Z"), c, key)
        assertEquals(0, c.failures)
        assertNull(c.lastModified)
    }

    @Test
    fun forbiddenWaitsADayAndNewCityRespectsTheGap() {
        val t0 = t("2026-10-17T13:17:40Z")
        val c = fresh(t0)
        val t1 = t("2026-10-17T13:50:00Z")
        val blocked = WeatherPolicy.apply(403, emptyMap(), null, t1, c, key)
        assertEquals(t("2026-10-18T13:50:00Z"), blocked.blockedUntil)
        assertEquals(t("2026-10-18T13:50:00Z"), WeatherPolicy.nextFetch(blocked, key, t1, t1, s0))
        assertEquals("the block is for the app, not the city", t("2026-10-18T13:50:00Z"), WeatherPolicy.nextFetch(blocked, "-33.92,18.42", t1, t1, s0))

        // A new city: at once, but never within a minute of the last request.
        val other = "-33.92,18.42"
        assertEquals(t("2026-10-17T13:18:40Z"), WeatherPolicy.nextFetch(c, other, t0, t("2026-10-17T13:18:00Z"), s30))
        assertEquals(t("2026-10-17T13:30:00Z"), WeatherPolicy.nextFetch(c, other, t0, t("2026-10-17T13:30:00Z"), s30))
        val moved = WeatherPolicy.apply(null, emptyMap(), null, t("2026-10-17T13:30:00Z"), c, other)
        assertEquals(other, moved.key)
        assertTrue("only one city is kept", moved.samples.isEmpty())
        assertEquals(1, moved.failures)
        assertEquals("a 304 for another city is not data", 1, WeatherPolicy.apply(304, emptyMap(), null, t1, c, other).failures)
    }

    @Test
    fun aRequestWhoseReplyWasNeverFoldedInStillHoldsTheNextOneBackAMinute() {
        val c = fresh(t("2026-10-17T13:17:40Z"))
        // Due at 13:47:40; sent at 13:48:00 and cancelled (the cache never saw it): not again before 13:49:00.
        val sent = t("2026-10-17T13:48:00Z")
        assertEquals(t("2026-10-17T13:49:00Z"), WeatherPolicy.nextFetch(c, key, sent, t("2026-10-17T13:48:20Z"), s0))
        assertFalse(WeatherPolicy.shouldFetch(c, key, sent, t("2026-10-17T13:48:59Z"), s0))
        assertTrue(WeatherPolicy.shouldFetch(c, key, sent, t("2026-10-17T13:49:00Z"), s0))
        assertEquals("the jitter still comes on top", t("2026-10-17T13:49:30Z"), WeatherPolicy.nextFetch(c, key, sent, t("2026-10-17T13:48:20Z"), s30))
    }

    @Test
    fun expiresMissingUnreadableOrPastMeansThirtyMinutes() {
        val now = t("2026-10-17T13:17:40Z")
        for (h in listOf(emptyMap(), mapOf("Expires" to "soon"), mapOf("Expires" to "Sat, 17 Oct 2026 12:00:00 GMT"), mapOf("Expires" to "0"))) {
            assertEquals("$h", t("2026-10-17T13:47:40Z"), WeatherPolicy.apply(200, h, sample, now, null, key).expires)
        }
        assertEquals(t("2026-10-17T13:47:30Z"), WeatherPolicy.httpDate("Sat, 17 Oct 2026 13:47:30 GMT"))
        val longer = WeatherPolicy.apply(200, mapOf("Expires" to "Sat, 17 Oct 2026 15:00:00 GMT"), sample, now, null, key)
        assertEquals("a long Expires is honoured", t("2026-10-17T15:00:00Z"), WeatherPolicy.nextFetch(longer, key, now, now, s0))
        val j = WeatherPolicy.randomJitter()
        assertTrue(!j.isNegative && j <= WeatherPolicy.MAX_JITTER)
    }

    @Test
    fun notModifiedAfterALongPauseAsksAfreshNextTime() {
        val c = fresh(t("2026-10-17T13:17:40Z"))
        val late = WeatherPolicy.apply(304, emptyMap(), null, t("2026-10-18T09:00:00Z"), c, key)
        assertNull(late.lastModified)
        assertNull(Weather.now(late, key, t("2026-10-18T09:00:00Z")))
    }

    @Test
    fun cacheRoundTripsOnDisk() {
        val c = fresh(t("2026-10-17T13:17:40Z"))
        assertEquals(c, Weather.decodeCache(Weather.encodeCache(c)))
        assertNull(Weather.decodeCache("{}"))
        assertNull(Weather.decodeCache(""))
        val swiftShape = """{"failures":0,"key":"21.42,39.83","lastAttempt":"2026-10-17T13:17:40.123Z","samples":[{"symbol":"fair","temperatureC":35.4,"time":"2026-10-17T13:00:00.000Z","windMs":5.7}],"validatedAt":"2026-10-17T13:17:40.000Z"}"""
        assertEquals("fair", Weather.decodeCache(swiftShape)?.samples?.first()?.symbol)
    }

    // Units and rounding

    @Test
    fun roundingUnitsAndRegions() {
        for (r in symbols.getValue("rounding").jsonArray.map { it.jsonObject }) {
            assertEquals("$r", r.getValue("expected").jsonPrimitive.int, WeatherUnits.round(r.getValue("x").jsonPrimitive.double))
        }
        for (r in symbols.getValue("temperature").jsonArray.map { it.jsonObject }) {
            val unit = TemperatureUnit.valueOf(r.getValue("unit").jsonPrimitive.content.uppercase())
            assertEquals("$r", r.getValue("expected").jsonPrimitive.int, WeatherUnits.displayTemperature(r.getValue("celsius").jsonPrimitive.double, unit))
        }
        for (r in symbols.getValue("wind").jsonArray.map { it.jsonObject }) {
            val unit = WindUnit.valueOf(r.getValue("unit").jsonPrimitive.content.uppercase())
            assertEquals("$r", r.getValue("expected").jsonPrimitive.int, WeatherUnits.displayWind(r.getValue("ms").jsonPrimitive.double, unit))
        }
        for ((region, units) in symbols.getValue("regions").jsonObject) {
            val u = WeatherUnits.forRegion(if (region == "_none") null else region)
            assertEquals(region, units.jsonArray.map { it.jsonPrimitive.content }, listOf(u.temperature.name.lowercase(), u.wind.name.lowercase()))
        }
        assertEquals(96, WeatherUnits.forRegion("US").temperature(35.4))
        assertEquals(13, WeatherUnits.forRegion("GB").wind(5.7))
        assertEquals("weather_temp_f", TemperatureUnit.FAHRENHEIT.stringKey)
        assertEquals("weather_wind_kmh", WindUnit.KMH.stringKey)
    }

    // Look

    private fun tokenName(c: WeatherCondition) = if (c == WeatherCondition.PARTLY_CLOUDY) "partlyCloudy" else c.key

    @Test
    fun lookMatchesTokens() {
        val w = Fixtures.read("shared/design/tokens.json").jsonObject.getValue("weather").jsonObject
        fun table(name: String) = w.getValue(name).jsonObject.mapValues { it.value.jsonPrimitive.double }
        fun byKey(m: Map<WeatherCondition, Double>) = m.mapKeys { tokenName(it.key) }
        assertEquals(table("mix"), byKey(WeatherLook.mix))
        assertEquals(table("glow"), byKey(WeatherLook.glow))
        assertEquals(table("stars"), byKey(WeatherLook.stars))
        assertEquals(table("clouds"), byKey(WeatherLook.clouds))
        assertEquals(table("deck"), byKey(WeatherLook.deck))
        assertEquals(table("fogBand"), byKey(WeatherLook.fogBands))
        assertEquals(w.getValue("cloudsClearNight").jsonPrimitive.double, WeatherLook.CLOUDS_CLEAR_NIGHT, 0.0)
        assertEquals(w.getValue("showersMix").jsonPrimitive.double, WeatherLook.SHOWERS_MIX, 0.0)
        assertEquals(w.getValue("rainStreaks").jsonPrimitive.int, WeatherLook.RAIN_STREAKS)
        assertEquals(w.getValue("snowFlakes").jsonPrimitive.int, WeatherLook.SNOW_FLAKES)
        for (c in WeatherCondition.entries.filter { it != WeatherCondition.CLEAR }) {
            assertNotNull("$c", w.getValue("veilDark").jsonObject[tokenName(c)])
            assertNotNull("$c", w.getValue("veilLight").jsonObject[tokenName(c)])
        }
    }

    @Test
    fun lookPerCondition() {
        assertEquals(WeatherLook.NORMAL, WeatherLook.of(null))
        assertEquals(WeatherLook(0.0, 1.0, 1.0, null, 0.0, 0, 0, 0.0, false), WeatherLook.of(null))
        assertEquals(0.25, WeatherLook.of(Weather.symbol("clearsky")).cloudOpacity!!, 0.0)
        assertEquals(0.15, WeatherLook.of(Weather.symbol("clearsky_night"), night = true).cloudOpacity!!, 0.0)
        assertNull(WeatherLook.of(Weather.symbol("partlycloudy")).cloudOpacity)
        val heavy = WeatherLook.of(Weather.symbol("heavyrain"))
        assertEquals(60, heavy.rainStreaks)
        assertEquals(0.9, heavy.deckOpacity, 0.0)
        assertEquals(0.55, heavy.veilMix, 1e-9)
        val showers = WeatherLook.of(Weather.symbol("rainshowers"))
        assertEquals(45, showers.rainStreaks)
        assertEquals(0.44, showers.veilMix, 1e-9)
        val thunder = WeatherLook.of(Weather.symbol("lightssnowshowersandthunder"))
        assertTrue(thunder.lightning)
        assertEquals(28, thunder.snowFlakes)
        assertEquals(0, thunder.rainStreaks)
        assertEquals(1.0, thunder.deckOpacity, 0.0)
        assertEquals(41, WeatherLook.of(Weather.symbol("snow")).snowFlakes)
        assertEquals(1.0, WeatherLook.of(Weather.symbol("fog")).fogBand, 0.0)
        assertEquals(0.0, WeatherLook.of(Weather.symbol("fog")).deckOpacity, 0.0)
        assertFalse(WeatherLook.of(Weather.symbol("heavyrain")).lightning)
        assertEquals("weather_partly_cloudy", WeatherCondition.PARTLY_CLOUDY.stringKey)
    }
}
