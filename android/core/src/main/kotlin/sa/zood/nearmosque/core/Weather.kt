package sa.zood.nearmosque.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.random.Random

/*
 * "Sky follows the weather" (opt-in): the current weather of the chosen city on the time-of-day sky, from
 * MET Norway Locationforecast 2.0 compact (CC BY 4.0 / NLOD 2.0, credit "Based on data from MET Norway").
 * Like OnlineMosques, this file only builds the request and reads the reply; each app makes the HTTP GET
 * itself (User-Agent always, If-Modified-Since when cached, never HEAD) and passes the status, headers and
 * body here. Only the city rounded to 0.01° (about 1 km) is sent. Mirrors NMCore/Weather.swift; both read
 * shared/fixtures/met-compact-sample.json and met-symbols.json in their tests.
 */

enum class WeatherCondition(
    /** "clear", "partly_cloudy", ...; also the suffix of the string key `weather_<key>`. */
    val key: String,
) {
    CLEAR("clear"), PARTLY_CLOUDY("partly_cloudy"), CLOUDY("cloudy"), RAIN("rain"),
    THUNDERSTORM("thunderstorm"), FOG("fog"), SNOW("snow");

    /** The localized condition name: weather_clear, weather_partly_cloudy, ... */
    val stringKey: String get() = "weather_$key"

    companion object {
        fun fromKey(key: String): WeatherCondition? = entries.firstOrNull { it.key == key }
    }
}

enum class Precipitation { NONE, RAIN, SNOW }

/** What a MET symbol code means for the sky. Sleet is drawn as rain. */
data class WeatherSymbol(
    val condition: WeatherCondition,
    /** 0.5 light, 0.75 normal, 1.0 heavy (also 1.0 for clear sky, partly cloudy, cloudy and fog; 0.5 for "fair"). */
    val intensity: Double,
    val showers: Boolean = false,
    val precipitation: Precipitation = Precipitation.NONE,
)

/** ISO-8601 UTC text, e.g. "2026-10-17T13:00:00Z" (the same shape NMCore writes). */
object InstantIsoSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("sa.zood.nearmosque.Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.truncatedTo(ChronoUnit.MILLIS).toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

/** One forecast step as kept on disk: instant values plus the base symbol code (no _day/_night suffix). */
@Serializable
data class WeatherSample(
    @Serializable(with = InstantIsoSerializer::class) val time: Instant,
    val temperatureC: Double? = null,
    val windMs: Double? = null,
    val symbol: String? = null,
)

/** What the sky shows right now. [at] is the time of the forecast step shown. */
data class WeatherNow(val symbol: WeatherSymbol, val temperatureC: Double, val windMs: Double, val at: Instant)

/** The one cached city (kept on disk while the switch is on; the app deletes the file when it is turned off). */
@Serializable
data class WeatherCache(
    /** Weather.key of the rounded city, e.g. "21.42,39.83". */
    val key: String,
    /** At most [WeatherPolicy.MAX_SAMPLES] steps around the time of the last 200. */
    val samples: List<WeatherSample> = emptyList(),
    /** Raw Last-Modified header, sent back unchanged as If-Modified-Since. */
    val lastModified: String? = null,
    @Serializable(with = InstantIsoSerializer::class) val expires: Instant? = null,
    /** Last 200 or 304. */
    @Serializable(with = InstantIsoSerializer::class) val validatedAt: Instant? = null,
    @Serializable(with = InstantIsoSerializer::class) val lastAttempt: Instant,
    val failures: Int = 0,
    /** Set after a 403 (usually a rejected User-Agent). */
    @Serializable(with = InstantIsoSerializer::class) val blockedUntil: Instant? = null,
)

object Weather {
    const val ENDPOINT = "https://api.met.no/weatherapi/locationforecast/2.0/compact"
    /** Opened by the "Weather: based on data from MET Norway" link (sky_weather_attribution). */
    const val ATTRIBUTION_URL = "https://www.met.no/en/free-meteorological-data/Licensing-and-crediting"
    /** English credits line for Sources and licences (not localized, like the other credit lines). */
    const val CREDIT = "Weather (optional): MET Norway, Locationforecast 2.0 · CC BY 4.0 and NLOD 2.0"
    /** Same file name on every platform, in the app's cache/support folder. */
    const val CACHE_FILE_NAME = "weather-cache.json"
    const val LINE_KEY = "weather_line"
    const val A11Y_KEY = "weather_a11y"
    private const val PUBLISHER_SITE = "https://nearmosque.net/"
    private const val PUBLISHER_EMAIL = "app@vision.bz"

    private val json = Json { ignoreUnknownKeys = true }
    private val cacheJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    /** Two decimals in Locale.ROOT (never Arabic-Indic digits or a decimal comma), -0.00 as 0.00. */
    internal fun coordinate(v: Double): String {
        val s = String.format(Locale.ROOT, "%.2f", v)
        return if (s == "-0.00") "0.00" else s
    }

    /**
     * To 0.01° (about 1 km, like OnlineMosques.privacyRound), halves away from zero, with the same arithmetic as
     * NMCore (sign(v) * floor(|v| * 100 + 0.5) / 100): privacyRound's Math.round and Swift's .rounded() disagree on
     * negative halves (-60.025 gives -60.02 here and -60.03 on iOS), and every platform must ask for the same point.
     */
    internal fun round2(v: Double): Double {
        val r = floor(abs(v) * 100 + 0.5) / 100
        return if (v < 0) -r else r
    }

    /** "lat,lon" of the city rounded to 0.01° ([round2]); the cache key. */
    fun key(p: LatLng): String = coordinate(round2(p.latitude)) + "," + coordinate(round2(p.longitude))

    /** The only request: the city rounded to 0.01° ([round2]), nothing else (no altitude). */
    fun url(p: LatLng): String = "$ENDPOINT?lat=${coordinate(round2(p.latitude))}&lon=${coordinate(round2(p.longitude))}"

    /**
     * platform: iOS, iPadOS, macOS, visionOS, Android, Windows or Linux. The publisher's contact, never the user's.
     * Always set it: MET blocks the defaults (Dalvik/..., Java/..., Java-http-client/..., okhttp).
     */
    fun userAgent(platform: String, version: String): String =
        "NearMosque/$version ($platform; +$PUBLISHER_SITE; $PUBLISHER_EMAIL)"

    /**
     * Headers for the GET: User-Agent always; If-Modified-Since only for the same city. No Accept-Encoding here:
     * HttpURLConnection adds gzip and inflates by itself, and the desktop's HttpClient, which does not, adds
     * "gzip, deflate" and inflates the body in its WeatherClient (MET's terms: clients must support gzip).
     */
    fun requestHeaders(userAgent: String, cache: WeatherCache?, key: String): Map<String, String> = buildMap {
        put("User-Agent", userAgent)
        val lm = cache?.lastModified
        if (cache != null && cache.key == key && !lm.isNullOrEmpty()) put("If-Modified-Since", lm)
    }

    /** Wraps a formatted value in FSI...PDI so a minus sign and the degree sign keep their order in RTL text. */
    fun isolate(s: String): String = "⁨$s⁩"

    // Parsing

    /**
     * Steps in [now - 90 min, now + 6 h], oldest first. Empty for invalid JSON or units other than celsius and m/s.
     * Times with or without fractional seconds are read alike, as in NMCore.
     */
    fun parse(body: String, now: Instant): List<WeatherSample> {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyList()
        val props = root["properties"] as? JsonObject ?: return emptyList()
        val units = (props["meta"] as? JsonObject)?.get("units") as? JsonObject ?: return emptyList()
        if (units.text("air_temperature") != "celsius" || units.text("wind_speed") != "m/s") return emptyList()
        val series = props["timeseries"] as? JsonArray ?: return emptyList()
        val from = now.minus(WeatherPolicy.NEAR_SAMPLE)
        val to = now.plus(WeatherPolicy.HORIZON)
        return series.mapNotNull { e ->
            val step = e as? JsonObject ?: return@mapNotNull null
            val time = step.text("time")?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return@mapNotNull null
            if (time < from || time > to) return@mapNotNull null
            val data = step["data"] as? JsonObject
            val details = (data?.get("instant") as? JsonObject)?.get("details") as? JsonObject
            fun code(period: String) = ((data?.get(period) as? JsonObject)?.get("summary") as? JsonObject)?.text("symbol_code")
            val symbol = (code("next_1_hours") ?: code("next_6_hours") ?: code("next_12_hours"))?.let(::baseCode)
            WeatherSample(time, details?.number("air_temperature"), details?.number("wind_speed"), symbol)
        }.sortedBy { it.time }
    }

    private fun JsonObject.text(k: String): String? = runCatching { this[k]?.jsonPrimitive?.contentOrNull }.getOrNull()
    /** A JSON number only: a quoted "12.5" is no value, as in NMCore (shared/fixtures/met-symbols.json "steps"). */
    private fun JsonObject.number(k: String): Double? =
        runCatching { this[k]?.jsonPrimitive?.takeIf { !it.isString }?.doubleOrNull }.getOrNull()?.takeIf { it.isFinite() }

    /** The symbol code without _day / _night / _polartwilight (the app's own sky period decides day or night). */
    fun baseCode(code: String): String {
        val c = code.trim().lowercase(Locale.ROOT)
        for (s in listOf("_day", "_night", "_polartwilight")) if (c.endsWith(s)) return c.dropLast(s.length)
        return c
    }

    // Symbols

    /**
     * Every code of MET's legend (41, plus "lightsleetshowersandthunder" / "lightsnowshowersandthunder" next to
     * MET's "lightss..." spelling), each as light / normal / heavy.
     */
    private val table: Map<String, WeatherSymbol> = buildMap {
        put("clearsky", WeatherSymbol(WeatherCondition.CLEAR, 1.0))
        put("fair", WeatherSymbol(WeatherCondition.PARTLY_CLOUDY, 0.5))
        put("partlycloudy", WeatherSymbol(WeatherCondition.PARTLY_CLOUDY, 1.0))
        put("cloudy", WeatherSymbol(WeatherCondition.CLOUDY, 1.0))
        put("fog", WeatherSymbol(WeatherCondition.FOG, 1.0))
        val levels = listOf("light" to 0.5, "" to 0.75, "heavy" to 1.0)
        val r = Precipitation.RAIN
        val s = Precipitation.SNOW
        // stem, condition, showers, precipitation
        val families = listOf(
            Family("rainshowers", WeatherCondition.RAIN, true, r), Family("rain", WeatherCondition.RAIN, false, r),
            Family("sleetshowers", WeatherCondition.RAIN, true, r), Family("sleet", WeatherCondition.RAIN, false, r),
            Family("snowshowers", WeatherCondition.SNOW, true, s), Family("snow", WeatherCondition.SNOW, false, s),
            Family("rainshowersandthunder", WeatherCondition.THUNDERSTORM, true, r),
            Family("sleetshowersandthunder", WeatherCondition.THUNDERSTORM, true, r),
            Family("snowshowersandthunder", WeatherCondition.THUNDERSTORM, true, s),
            Family("rainandthunder", WeatherCondition.THUNDERSTORM, false, r),
            Family("sleetandthunder", WeatherCondition.THUNDERSTORM, false, r),
            Family("snowandthunder", WeatherCondition.THUNDERSTORM, false, s),
        )
        for (f in families) for ((prefix, intensity) in levels) {
            put(prefix + f.stem, WeatherSymbol(f.condition, intensity, f.showers, f.precipitation))
        }
        put("lightssleetshowersandthunder", getValue("lightsleetshowersandthunder"))
        put("lightssnowshowersandthunder", getValue("lightsnowshowersandthunder"))
    }

    private data class Family(val stem: String, val condition: WeatherCondition, val showers: Boolean, val precipitation: Precipitation)

    /**
     * The sky's reading of a MET symbol code (suffix allowed). An unknown code is read from its words
     * (thunder, snow, sleet/rain, fog, partly/fair, cloudy, clear); anything else is null: the normal sky.
     */
    fun symbol(code: String): WeatherSymbol? {
        val c = baseCode(code)
        table[c]?.let { return it }
        val condition = when {
            "thunder" in c -> WeatherCondition.THUNDERSTORM
            "snow" in c -> WeatherCondition.SNOW
            "sleet" in c || "rain" in c -> WeatherCondition.RAIN
            "fog" in c -> WeatherCondition.FOG
            "partly" in c || "fair" in c -> WeatherCondition.PARTLY_CLOUDY
            "cloudy" in c -> WeatherCondition.CLOUDY
            "clear" in c -> WeatherCondition.CLEAR
            else -> return null
        }
        val wet = condition == WeatherCondition.RAIN || condition == WeatherCondition.SNOW || condition == WeatherCondition.THUNDERSTORM
        val intensity = when {
            c.startsWith("light") -> 0.5
            c.startsWith("heavy") -> 1.0
            wet -> 0.75
            else -> 1.0
        }
        val precipitation = when {
            "snow" in c -> Precipitation.SNOW
            "sleet" in c || "rain" in c -> Precipitation.RAIN
            else -> Precipitation.NONE
        }
        return WeatherSymbol(condition, intensity, "showers" in c, precipitation)
    }

    // What to show

    /**
     * The weather to draw now, or null for the normal sky: no cache or another city, nothing confirmed by MET
     * within [WeatherPolicy.MAX_AGE], no step within 90 minutes, or a missing value. Call it every tick.
     */
    fun now(cache: WeatherCache?, key: String, now: Instant): WeatherNow? {
        val c = cache?.takeIf { it.key == key } ?: return null
        val v = c.validatedAt ?: return null
        if (Duration.between(v, now) > WeatherPolicy.MAX_AGE) return null
        val past = c.samples.filter { it.time <= now && Duration.between(it.time, now) <= WeatherPolicy.NEAR_SAMPLE }
        val future = c.samples.filter { it.time > now && Duration.between(now, it.time) <= WeatherPolicy.FUTURE_SAMPLE }
        val s = past.maxByOrNull { it.time } ?: future.minByOrNull { it.time } ?: return null
        val t = s.temperatureC ?: return null
        val w = s.windMs ?: return null
        val sym = s.symbol?.let(::symbol) ?: return null
        return WeatherNow(sym, t, w, s.time)
    }

    // Disk format

    /** JSON with ISO-8601 UTC times (the same shape NMCore/Weather.swift writes). */
    fun encodeCache(cache: WeatherCache): String = cacheJson.encodeToString(WeatherCache.serializer(), cache)

    /** null for a missing, damaged or foreign file (the sky then stays normal until the next fetch). */
    fun decodeCache(text: String): WeatherCache? =
        runCatching { cacheJson.decodeFromString(WeatherCache.serializer(), text) }.getOrNull()
}

/**
 * When to ask MET again and how to fold a reply into the cache. Honours Expires and If-Modified-Since,
 * backs off on errors, waits a day after a 403, and never asks twice within a minute.
 */
object WeatherPolicy {
    val MIN_INTERVAL: Duration = Duration.ofMinutes(30)
    val MAX_AGE: Duration = Duration.ofHours(3)
    /** After the first failure; later failures wait [RETRY_CAP]. */
    val RETRY: Duration = Duration.ofMinutes(30)
    val RETRY_CAP: Duration = Duration.ofMinutes(60)
    val AFTER_403: Duration = Duration.ofHours(24)
    /** Between any two requests (switching cities quickly). */
    val MIN_GAP: Duration = Duration.ofSeconds(60)
    val MAX_JITTER: Duration = Duration.ofSeconds(90)
    /** Used when Expires is missing, unreadable or already past (clock skew). */
    val DEFAULT_EXPIRY: Duration = Duration.ofMinutes(30)
    /** A step must be at most this far from now to be shown; parse keeps steps from now minus this. */
    val NEAR_SAMPLE: Duration = Duration.ofMinutes(90)
    /** When no step has started yet, the first one within this time is shown. */
    val FUTURE_SAMPLE: Duration = Duration.ofMinutes(60)
    /** parse keeps steps up to now plus this. */
    val HORIZON: Duration = Duration.ofHours(6)
    const val MAX_SAMPLES = 12

    /** 0-90 s, added to scheduled refreshes so devices do not all ask at the same moment. */
    fun randomJitter(random: Random = Random.Default): Duration = Duration.ofMillis(random.nextLong(0, MAX_JITTER.toMillis() + 1))

    /**
     * The earliest time the app may send the next request for [key]. lastAnyRequest: the last request for
     * any city, this one included (cache.lastAttempt counts too). It holds the next request back [MIN_GAP] even
     * for the same city, for a request that went out but whose reply was never folded in (cancelled).
     */
    fun nextFetch(cache: WeatherCache?, key: String, lastAnyRequest: Instant?, now: Instant, jitter: Duration): Instant {
        val blocked = cache?.blockedUntil ?: Instant.MIN
        if (cache == null || cache.key != key) {
            val last = listOfNotNull(lastAnyRequest, cache?.lastAttempt).maxOrNull()
            return listOf(now, last?.plus(MIN_GAP) ?: now, blocked).max()
        }
        val wait = when (cache.failures) {
            0 -> MIN_INTERVAL
            1 -> RETRY
            else -> RETRY_CAP
        }
        val gap = lastAnyRequest?.plus(MIN_GAP) ?: Instant.MIN
        val base = listOf(cache.expires ?: cache.lastAttempt.plus(MIN_INTERVAL), cache.lastAttempt.plus(wait), blocked, gap).max()
        return base.plus(jitter)
    }

    fun shouldFetch(cache: WeatherCache?, key: String, lastAnyRequest: Instant?, now: Instant, jitter: Duration): Boolean =
        nextFetch(cache, key, lastAnyRequest, now, jitter) <= now

    /**
     * Folds a reply into the cache. status null means a transport error (offline). The body is read only on
     * 200/203; a 203 means the API version is deprecated (log it in debug builds). Header names are matched
     * case-insensitively. Another key replaces the previous city entirely.
     */
    fun apply(status: Int?, headers: Map<String, String>, body: String?, now: Instant, previous: WeatherCache?, key: String): WeatherCache {
        val same = previous?.takeIf { it.key == key }
        fun header(name: String) = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
        fun expiry(): Instant = header("Expires")?.let(::httpDate)?.takeIf { it > now } ?: now.plus(DEFAULT_EXPIRY)
        return when {
            status == 200 || status == 203 -> WeatherCache(
                key = key, samples = Weather.parse(body.orEmpty(), now).take(MAX_SAMPLES),
                lastModified = header("Last-Modified"), expires = expiry(),
                validatedAt = now, lastAttempt = now, failures = 0, blockedUntil = null,
            )
            status == 304 && same != null -> {
                val c = same.copy(
                    expires = expiry(), lastModified = header("Last-Modified") ?: same.lastModified,
                    validatedAt = now, lastAttempt = now, failures = 0, blockedUntil = null,
                )
                // Kept steps no longer reach now (a 304 after a long pause): ask without If-Modified-Since next time.
                if (c.samples.none { abs(Duration.between(it.time, now).seconds) <= NEAR_SAMPLE.seconds }) c.copy(lastModified = null) else c
            }
            status == 403 -> (same ?: WeatherCache(key = key, lastAttempt = now)).let {
                it.copy(lastAttempt = now, failures = it.failures + 1, blockedUntil = now.plus(AFTER_403))
            }
            else -> (same ?: WeatherCache(key = key, lastAttempt = now)).let {
                it.copy(lastAttempt = now, failures = it.failures + 1)
            }
        }
    }

    /** RFC 1123 HTTP-date, e.g. "Sat, 17 Oct 2026 13:47:30 GMT". */
    fun httpDate(s: String): Instant? =
        runCatching { ZonedDateTime.parse(s.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()
}

enum class TemperatureUnit(
    /** weather_temp_c / weather_temp_f */
    val stringKey: String,
) { CELSIUS("weather_temp_c"), FAHRENHEIT("weather_temp_f") }

enum class WindUnit(
    /** weather_wind_kmh / weather_wind_mph */
    val stringKey: String,
) { KMH("weather_wind_kmh"), MPH("weather_wind_mph") }

/** Units of the temperature and wind line. Default from the region; the apps may override from system settings. */
data class WeatherUnits(val temperature: TemperatureUnit, val wind: WindUnit) {
    fun temperature(celsius: Double): Int = displayTemperature(celsius, temperature)
    fun wind(ms: Double): Int = displayWind(ms, wind)

    companion object {
        val METRIC = WeatherUnits(TemperatureUnit.CELSIUS, WindUnit.KMH)
        private val fahrenheitRegions = setOf("US", "PR", "GU", "VI", "AS", "MP", "UM", "BS", "BZ", "KY", "PW", "FM", "MH", "LR")
        private val mphRegions = fahrenheitRegions + setOf("GB", "IM", "JE", "GG")

        /** ISO 3166 region code (any case); null or unknown gives Celsius and km/h. */
        fun forRegion(code: String?): WeatherUnits {
            val r = code.orEmpty().uppercase(Locale.ROOT)
            return WeatherUnits(
                if (r in fahrenheitRegions) TemperatureUnit.FAHRENHEIT else TemperatureUnit.CELSIUS,
                if (r in mphRegions) WindUnit.MPH else WindUnit.KMH,
            )
        }

        fun displayTemperature(celsius: Double, unit: TemperatureUnit): Int =
            round(if (unit == TemperatureUnit.CELSIUS) celsius else celsius * 9 / 5 + 32)

        fun displayWind(ms: Double, unit: WindUnit): Int =
            maxOf(0, round(maxOf(0.0, ms) * (if (unit == WindUnit.KMH) 3.6 else 2.2369363)))

        /**
         * sign(x) * floor(|x| + 0.5), never -0: the same on every platform (Swift .rounded() and Kotlin
         * roundToInt() disagree on negative halves).
         */
        fun round(x: Double): Int {
            if (!x.isFinite() || abs(x) >= 1e9) return 0
            val r = floor(abs(x) + 0.5).toInt()
            return if (x < 0) -r else r
        }
    }
}

/**
 * Plain numbers that drive the drawing, from shared/design/tokens.json "weather" (checked by WeatherTest), so
 * iOS, Android and the desktop draw the same sky. Colours (veil, streaks, flakes, fog) stay in each theme.
 */
data class WeatherLook(
    /** How far each sky stop (top, mid, glow, low) is mixed toward the condition's veil colour. */
    val veilMix: Double,
    /** Multiplies the horizon glow. */
    val glowStrength: Double,
    val starOpacity: Double,
    /** Opacity of the existing cloud band; null keeps today's default. */
    val cloudOpacity: Double?,
    /** Opacity of the overcast deck (0: none). */
    val deckOpacity: Double,
    val rainStreaks: Int,
    val snowFlakes: Int,
    /** Opacity of the low fog band (0: none). */
    val fogBand: Double,
    /** Occasional soft flash; the apps never flash with reduced animations on. */
    val lightning: Boolean,
) {
    companion object {
        /** Today's sky, unchanged. */
        val NORMAL = WeatherLook(0.0, 1.0, 1.0, null, 0.0, 0, 0, 0.0, false)

        // tokens.json "weather"
        internal val mix = mapOf(
            WeatherCondition.CLEAR to 0.0, WeatherCondition.PARTLY_CLOUDY to 0.12, WeatherCondition.CLOUDY to 0.45,
            WeatherCondition.RAIN to 0.55, WeatherCondition.THUNDERSTORM to 0.65, WeatherCondition.FOG to 0.5, WeatherCondition.SNOW to 0.45,
        )
        internal val glow = mapOf(
            WeatherCondition.CLEAR to 1.0, WeatherCondition.PARTLY_CLOUDY to 0.9, WeatherCondition.CLOUDY to 0.45,
            WeatherCondition.RAIN to 0.35, WeatherCondition.THUNDERSTORM to 0.25, WeatherCondition.FOG to 0.5, WeatherCondition.SNOW to 0.5,
        )
        internal val stars = mapOf(
            WeatherCondition.CLEAR to 1.0, WeatherCondition.PARTLY_CLOUDY to 0.8, WeatherCondition.CLOUDY to 0.15,
            WeatherCondition.RAIN to 0.0, WeatherCondition.THUNDERSTORM to 0.0, WeatherCondition.FOG to 0.3, WeatherCondition.SNOW to 0.1,
        )
        internal val clouds = mapOf(
            WeatherCondition.CLEAR to 0.25, WeatherCondition.CLOUDY to 0.9, WeatherCondition.RAIN to 0.9,
            WeatherCondition.THUNDERSTORM to 0.95, WeatherCondition.FOG to 0.5, WeatherCondition.SNOW to 0.85,
        )
        internal val deck = mapOf(
            WeatherCondition.CLOUDY to 0.9, WeatherCondition.RAIN to 0.9, WeatherCondition.THUNDERSTORM to 1.0, WeatherCondition.SNOW to 0.9,
        )
        internal val fogBands = mapOf(WeatherCondition.FOG to 1.0)
        internal const val CLOUDS_CLEAR_NIGHT = 0.15
        internal const val SHOWERS_MIX = 0.8
        internal const val RAIN_STREAKS = 60
        internal const val SNOW_FLAKES = 55

        /** null gives [NORMAL]. night: the sky period is night (a clear night keeps a fainter cloud band). */
        fun of(symbol: WeatherSymbol?, night: Boolean = false): WeatherLook {
            val s = symbol ?: return NORMAL
            val c = s.condition
            return WeatherLook(
                veilMix = (mix[c] ?: 0.0) * (if (s.showers) SHOWERS_MIX else 1.0),
                glowStrength = glow[c] ?: 1.0,
                starOpacity = stars[c] ?: 1.0,
                cloudOpacity = if (c == WeatherCondition.CLEAR && night) CLOUDS_CLEAR_NIGHT else clouds[c],
                deckOpacity = deck[c] ?: 0.0,
                rainStreaks = if (s.precipitation == Precipitation.RAIN) WeatherUnits.round(RAIN_STREAKS * s.intensity) else 0,
                snowFlakes = if (s.precipitation == Precipitation.SNOW) WeatherUnits.round(SNOW_FLAKES * s.intensity) else 0,
                fogBand = fogBands[c] ?: 0.0,
                lightning = c == WeatherCondition.THUNDERSTORM,
            )
        }
    }
}
