package sa.zood.nearmosque.desktop

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import sa.zood.nearmosque.core.City
import sa.zood.nearmosque.core.LatLng
import sa.zood.nearmosque.core.Weather
import sa.zood.nearmosque.core.WeatherCache
import sa.zood.nearmosque.core.WeatherCondition
import sa.zood.nearmosque.core.WeatherNow
import sa.zood.nearmosque.core.WeatherPolicy
import sa.zood.nearmosque.core.WeatherUnits
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

/**
 * "Sky follows the weather" (opt-in, off by default): the chosen city's weather from MET Norway Locationforecast
 * 2.0 compact, for the sky behind the next-prayer card and the temperature and wind line. The shared core
 * (sa.zood.nearmosque.core.Weather and WeatherPolicy) builds the request, reads the reply and decides when to ask
 * again; this class only makes the GET and keeps the one cached city in the app's data folder.
 *
 * Only the city rounded to 0.01° (about 1 km) is sent, with the app's own User-Agent (MET blocks the JVM's
 * "Java-http-client/…"), If-Modified-Since for the same city and Accept-Encoding: gzip, deflate (MET's terms: every
 * client must support gzip and redirects; this HTTP client does neither by itself, so [get] inflates the body and
 * the client follows redirects). Nothing is logged, except in development runs (./gradlew run) a 203 status, which
 * MET sends when this API version is deprecated. Main.kt calls [refreshIfDue] only while the window is shown and
 * not minimized, never from the tick that keeps running in the notification area.
 */
internal class WeatherClient(
    private val file: File,
    private val userAgent: String = Weather.userAgent(if (Platform.isLinux) "Linux" else "Windows", appVersion ?: "0.5"),
    private val url: (LatLng) -> String = Weather::url,
    private val http: () -> HttpClient = { defaultHttp },
    private val clock: () -> Instant = Instant::now,
    private val jitter: () -> Duration = { WeatherPolicy.randomJitter() },
) {
    private val state = mutableStateOf<WeatherCache?>(null)
    private var loaded = false

    /** The cached forecast (read from disk on first use; null for none, another app's or a damaged file). */
    val cache: WeatherCache?
        get() {
            if (!loaded) {
                loaded = true
                state.value = runCatching { if (file.isFile) Weather.decodeCache(file.readText()) else null }.getOrNull()
            }
            return state.value
        }

    /** The last request for any city in this run (a quick change of city still waits WeatherPolicy.MIN_GAP). */
    private var lastAnyRequest: Instant? = null
    /** A 403 keeps its day-long wait in this run even after the switch is turned off and on (the file is deleted). */
    private var blockedUntil: Instant? = null
    /** 0-90 s, drawn again after each request so PCs do not all ask at the same moment. */
    private var nextJitter: Duration = jitter()
    /** Bumped by [clear]: a reply that comes back after the switch was turned off is dropped. */
    private var generation = 0
    private val busy = Mutex()

    /** What the sky shows for [p] at [at], or null for the normal sky (no data, another city, older than 3 hours). */
    fun now(p: LatLng, at: Instant): WeatherNow? = Weather.now(cache, Weather.key(p), at)

    /** The earliest time the next request for [p] may go (Expires, backoff, the 60 s gap, a 403's day). */
    fun nextFetch(p: LatLng, at: Instant = clock()): Instant {
        val next = WeatherPolicy.nextFetch(cache, Weather.key(p), lastAnyRequest, at, nextJitter)
        return blockedUntil?.takeIf { it > next } ?: next
    }

    /** How long the window's loop sleeps before asking [refreshIfDue] again (at most 5 minutes, so a PC waking from sleep catches up). */
    fun millisUntilDue(p: LatLng): Long = Duration.between(clock(), nextFetch(p)).toMillis().coerceIn(1_000L, 5 * 60_000L)

    /**
     * Asks MET for [p] when the policy allows it and folds the reply (or the failure) into the cache on disk.
     * Never throws for a network or disk problem: offline, the sky simply stays as it is. True if a request went out.
     */
    suspend fun refreshIfDue(p: LatLng): Boolean = busy.withLock {
        val started = clock()
        if (nextFetch(p, started) > started) return@withLock false
        val key = Weather.key(p)
        val previous = cache
        val gen = generation
        lastAnyRequest = started
        val updated = withContext(Dispatchers.IO) {
            val reply = get(url(p), Weather.requestHeaders(userAgent, previous, key))
            // MET's terms: log a 203 (deprecated API version). The status only, never the URL or the coordinates.
            if (reply?.status == 203 && debug) System.err.println("weather: HTTP 203, MET has deprecated this API version")
            WeatherPolicy.apply(reply?.status, reply?.headers.orEmpty(), reply?.body, clock(), previous, key)
        }
        nextJitter = jitter()
        updated.blockedUntil?.let { b -> if (blockedUntil?.let { it > b } != true) blockedUntil = b }
        // Turned off while the request was out: keep nothing (clear() already deleted the file).
        if (gen != generation) return@withLock true
        state.value = updated
        loaded = true
        save(updated)
        true
    }

    /** The switch was turned off: the forecast is forgotten and its file deleted. */
    fun clear() {
        generation++
        state.value?.blockedUntil?.let { b -> if (blockedUntil?.let { it > b } != true) blockedUntil = b }
        state.value = null
        loaded = true
        runCatching { Files.deleteIfExists(file.toPath()) }
    }

    private class Reply(val status: Int, val headers: Map<String, String>, val body: String?)

    /**
     * One GET; null when it could not be made or answered (offline, a timeout, a bad certificate, a damaged or
     * oversized body). The body is read only for 200/203, inflated when MET sent it gzipped or deflated.
     */
    private suspend fun get(url: String, headers: Map<String, String>): Reply? = try {
        withTimeoutOrNull(45_000) {
            val request = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(15)).GET()
            headers.forEach { (name, value) -> request.header(name, value) }
            request.header("Accept-Encoding", "gzip, deflate")
            val res = http().sendAsync(request.build(), HttpResponse.BodyHandlers.ofInputStream()).await()
            val status = res.statusCode()
            // HttpRequest.timeout stops at the headers (JDK 21); a cancel or the timeout above interrupts this blocking read.
            val body = runInterruptible {
                res.body().use { raw ->
                    if (status != 200 && status != 203) return@use null
                    val encoding = res.headers().firstValue("Content-Encoding").orElse("").trim().lowercase(Locale.ROOT)
                    val input = when (encoding) {
                        "gzip", "x-gzip" -> GZIPInputStream(raw)
                        "deflate" -> InflaterInputStream(raw)
                        else -> raw
                    }
                    input.use(::readCapped)
                }
            }
            Reply(status, res.headers().map().mapValues { it.value.firstOrNull().orEmpty() }, body)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /** A compact reply is about 40 KB (inflated); anything far larger is not a forecast (as on Android). */
    private fun readCapped(input: InputStream): String {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_BODY_BYTES) throw IOException("reply too large")
        }
        return out.toString(Charsets.UTF_8)
    }

    /** Written in one move (as the settings file is); a disk that cannot be written keeps the forecast for this run. */
    private fun save(c: WeatherCache) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(Weather.encodeCache(c))
            runCatching { Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
                .recoverCatching { Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
                .getOrThrow()
        }
    }

    companion object {
        /** Set by ./gradlew run (build.gradle.kts), never in the packaged app. */
        private val debug: Boolean = System.getProperty("nearmosque.debug") == "true"

        /** Inflated, as on Android (MetWeatherSource.MAX_BODY_BYTES). */
        const val MAX_BODY_BYTES = 2 * 1024 * 1024

        /** Follows redirects (never from https to http), as MET's terms require of every client. */
        private val defaultHttp: HttpClient by lazy {
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build()
        }

        /** The app's one client: %LOCALAPPDATA%\NearMosque\weather-cache.json, ~/.local/share/near-mosque/weather-cache.json. */
        val shared: WeatherClient by lazy { WeatherClient(File(Store.dataDir, Weather.CACHE_FILE_NAME)) }
    }
}

// The temperature and wind line

/** Celsius and km/h, or Fahrenheit and mph where the PC's region uses them (its format region, as the clock follows). */
internal fun weatherUnits(): WeatherUnits = WeatherUnits.forRegion(Locale.getDefault(Locale.Category.FORMAT).country)

/**
 * A whole number in Western digits, as the clock, the Hijri date and the countdown beside it on the desktop (also in
 * Arabic, Persian or Urdu), kept in one piece (with the language's minus sign) inside right-to-left text.
 */
internal fun weatherNumber(n: Int): String {
    val latn = Locale.Builder().setLocale(Locale.forLanguageTag(Strings.language)).setUnicodeLocaleKeyword("nu", "latn").build()
    return Weather.isolate(NumberFormat.getIntegerInstance(latn).apply { isGroupingUsed = false }.format(n))
}

internal fun weatherTemperature(w: WeatherNow, u: WeatherUnits): String = t(u.temperature.stringKey, weatherNumber(u.temperature(w.temperatureC)))

internal fun weatherWind(w: WeatherNow, u: WeatherUnits): String = t(u.wind.stringKey, weatherNumber(u.wind(w.windMs)))

/** "35°C · Wind 21 km/h": the only weather text on screen. */
internal fun weatherLine(w: WeatherNow, u: WeatherUnits = weatherUnits()): String = t(Weather.LINE_KEY, weatherTemperature(w, u), weatherWind(w, u))

/**
 * The condition's icon at the start of the weather line (Material Symbols, the Android app's drawables copied in by
 * the build), the night variant for a clear or partly cloudy sky with stars.
 */
internal fun weatherIconPath(c: WeatherCondition, night: Boolean): String = "icons/ic_wx_" + when (c) {
    WeatherCondition.CLEAR -> if (night) "clear_night" else "clear_day"
    WeatherCondition.PARTLY_CLOUDY -> if (night) "partly_cloudy_night" else "partly_cloudy_day"
    WeatherCondition.CLOUDY -> "cloudy"
    WeatherCondition.RAIN -> "rain"
    WeatherCondition.THUNDERSTORM -> "thunderstorm"
    WeatherCondition.FOG -> "fog"
    WeatherCondition.SNOW -> "snow"
} + ".xml"

/** "Weather in Riyadh: Partly cloudy, 35°C, wind 21 km/h", for screen readers. */
internal fun weatherDescription(w: WeatherNow, city: City, u: WeatherUnits = weatherUnits()): String =
    t(Weather.A11Y_KEY, city.displayName(Strings.language), t(w.symbol.condition.stringKey), weatherTemperature(w, u), weatherWind(w, u))
