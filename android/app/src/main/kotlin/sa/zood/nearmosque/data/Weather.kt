package sa.zood.nearmosque.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import sa.zood.nearmosque.core.LatLng
import sa.zood.nearmosque.core.TemperatureUnit
import sa.zood.nearmosque.core.Weather
import sa.zood.nearmosque.core.WeatherCache
import sa.zood.nearmosque.core.WeatherPolicy
import sa.zood.nearmosque.core.WeatherUnits
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/*
 * "Sky follows the weather" (opt-in): the HTTP GET and the on-disk cache. What to ask, when, and how to read the
 * reply all live in the shared core (Weather, WeatherPolicy). Only the city rounded to 0.01° is sent, with the
 * app's identifying User-Agent; nothing here is logged (no coordinates, no replies), except in debug builds a
 * 203 status, which MET sends when this API version is deprecated (its terms ask apps to log or show it).
 */

/** What came back: the status, the two caching headers, and the body for 200/203 only. */
data class WeatherHttp(val status: Int, val expires: String?, val lastModified: String?, val body: String?)

/** One GET to MET Norway. null means a transport error or no network (the sky then stays as it is). */
fun interface WeatherSource {
    suspend fun fetch(url: String, userAgent: String, lastModified: String?): WeatherHttp?
}

/**
 * MET Norway Locationforecast 2.0 over HttpURLConnection. The User-Agent is always set: MET blocks Android's
 * default "Dalvik/..." (and the JVM's "Java/..."). If-Modified-Since carries the stored Last-Modified unchanged.
 * Android inflates gzip by itself. No HEAD request, no cookies, no HTTP cache of its own.
 */
class MetWeatherSource : WeatherSource {
    override suspend fun fetch(url: String, userAgent: String, lastModified: String?): WeatherHttp? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                conn.useCaches = false
                conn.setRequestProperty("User-Agent", userAgent)
                if (!lastModified.isNullOrEmpty()) conn.setRequestProperty("If-Modified-Since", lastModified)
                val status = conn.responseCode
                // The status only, never the URL or the coordinates.
                if (status == 203 && sa.zood.nearmosque.BuildConfig.DEBUG) {
                    runCatching { android.util.Log.w("Weather", "HTTP 203: MET has deprecated this API version") }
                }
                val body = if (status == 200 || status == 203) {
                    conn.inputStream.use { input -> readCapped(input) }
                } else null
                WeatherHttp(status, conn.getHeaderField("Expires"), conn.getHeaderField("Last-Modified"), body)
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private fun readCapped(input: java.io.InputStream): String {
        // A compact reply is about 40 KB; anything far larger is not a forecast.
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_BODY_BYTES) throw java.io.IOException("reply too large")
        }
        return out.toString(Charsets.UTF_8.name())
    }

    companion object {
        const val MAX_BODY_BYTES = 2 * 1024 * 1024
    }
}

/**
 * The one cached city on disk (written atomically) and the refresh rules around it. The app calls
 * [refreshIfDue] only while it is on screen, then waits [millisUntilDue]; nothing runs in the background.
 * [clear] forgets the city and the forecast when the switch is turned off.
 *
 * [random] gives 0..1 for the 0-90 s jitter, drawn once after each request so that [millisUntilDue] and the
 * next [refreshIfDue] agree on the same moment.
 */
class WeatherRepository(
    private val source: WeatherSource,
    private val file: File,
    private val clock: () -> Instant,
    private val random: () -> Double = { kotlin.random.Random.nextDouble() },
    private val userAgent: String = Weather.userAgent("Android", sa.zood.nearmosque.BuildConfig.VERSION_NAME),
    /** Where the file is written (tests pass one that lets them act in between). */
    private val io: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private val state = MutableStateFlow<WeatherCache?>(null)
    @Volatile private var loaded = false
    /** Bumped by [clear], so a reply that was already on its way is dropped. */
    private val generation = AtomicInteger()
    /** Held by [clear] and by each write of the file, so a write that was already on its way never lands after clear(). */
    private val fileLock = Any()

    /**
     * Timing limits that outlive [clear] (in memory, and on disk without any city while a 403 block lasts):
     * the last request for any city, and a running 403 block, so turning the switch off and on again never
     * gets round the 60 s gap or MET's 24 h block.
     */
    @Volatile private var lastAnyRequest: Instant? = null
    @Volatile private var held: WeatherCache? = null
    @Volatile private var jitter: Duration = Duration.ZERO

    /** The cached forecast (null: none, or switched off). Loaded from disk by the first [refreshIfDue]. */
    val cache: StateFlow<WeatherCache?> = state.asStateFlow()

    /** Asks MET for [city] (already rounded or not; the core rounds to 0.01°) when the policy allows it. */
    suspend fun refreshIfDue(city: LatLng) {
        mutex.withLock {
            ensureLoaded()
            val gen = generation.get()
            val key = Weather.key(city)
            val now = clock()
            val previous = state.value ?: held
            if (!WeatherPolicy.shouldFetch(previous, key, lastAnyRequest, now, jitter)) return
            lastAnyRequest = now
            jitter = drawJitter()
            val headers = Weather.requestHeaders(userAgent, previous, key)
            // A request that has gone out is always folded in, even if the app leaves the screen meanwhile, so
            // its Expires and Last-Modified are honoured; only turning the switch off ([clear]) drops it.
            withContext(NonCancellable) {
                val reply = source.fetch(Weather.url(city), userAgent, headers["If-Modified-Since"])
                if (gen != generation.get()) return@withContext
                val replyHeaders = buildMap {
                    reply?.expires?.let { put("Expires", it) }
                    reply?.lastModified?.let { put("Last-Modified", it) }
                }
                val next = WeatherPolicy.apply(reply?.status, replyHeaders, reply?.body, clock(), previous, key)
                state.value = next
                held = null
                write(next, gen)
            }
        }
    }

    /** How long to wait before calling [refreshIfDue] again for [city] (at least 1 s, at most 1 h). */
    fun millisUntilDue(city: LatLng): Long {
        val now = clock()
        val next = WeatherPolicy.nextFetch(state.value ?: held, Weather.key(city), lastAnyRequest, now, jitter)
        return Duration.between(now, next).toMillis().coerceIn(MIN_WAIT_MS, MAX_WAIT_MS)
    }

    /**
     * The switch was turned off: deletes the file and empties [cache]. Only a running 403 block is kept, in a
     * file with no city and no forecast, so it still holds after the switch is turned on again.
     */
    fun clear() {
        synchronized(fileLock) {
            generation.incrementAndGet()
            val now = clock()
            val block = (state.value ?: held)?.takeIf { (it.blockedUntil ?: Instant.MIN) > now }
            state.value = null
            loaded = true
            runCatching { File(file.path + ".tmp").delete() }
            if (block != null) {
                val tombstone = WeatherCache(key = "", lastAttempt = block.lastAttempt, failures = block.failures, blockedUntil = block.blockedUntil)
                held = tombstone
                runCatching { writeNow(tombstone) }
            } else {
                held = null
                runCatching { file.delete() }
            }
        }
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        val gen = generation.get()
        val c = withContext(Dispatchers.IO) {
            runCatching { if (file.exists()) Weather.decodeCache(file.readText(Charsets.UTF_8)) else null }.getOrNull()
        }
        if (loaded || gen != generation.get()) return
        if (c != null && c.key.isEmpty()) held = c else state.value = c
        loaded = true
    }

    /** Writes [c] unless [clear] ran since the reply's generation [gen] was read (checked under [fileLock]). */
    private suspend fun write(c: WeatherCache, gen: Int) = withContext(io) {
        synchronized(fileLock) { if (gen == generation.get()) runCatching { writeNow(c) } }
    }

    /** Temp file, then rename: a crash mid-write never leaves half a file. */
    private fun writeNow(c: WeatherCache) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(Weather.encodeCache(c), Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) tmp.delete()
        }
    }

    private fun drawJitter(): Duration =
        Duration.ofMillis((random().coerceIn(0.0, 1.0) * WeatherPolicy.MAX_JITTER.toMillis()).toLong())

    companion object {
        const val MIN_WAIT_MS = 1_000L
        const val MAX_WAIT_MS = 60 * 60_000L
    }
}

/**
 * Units of the temperature and wind line. The temperature follows an explicit system choice (Android 14
 * Regional preferences, the -u-mu- locale extension) when there is one; otherwise, and for the wind, the
 * region of the formatting locale, then of the phone (an app language such as "ar" carries no region).
 */
object WeatherDisplay {
    fun units(
        formatLocale: Locale = Locale.getDefault(Locale.Category.FORMAT),
        systemRegion: () -> String? = { runCatching { android.content.res.Resources.getSystem().configuration.locales[0]?.country }.getOrNull() },
        explicitTemperature: () -> String = { runCatching { androidx.core.text.util.LocalePreferences.getTemperatureUnit(false) }.getOrDefault("") },
    ): WeatherUnits {
        val region = formatLocale.country.ifEmpty { systemRegion().orEmpty() }
        val base = WeatherUnits.forRegion(region)
        return when (explicitTemperature()) {
            androidx.core.text.util.LocalePreferences.TemperatureUnit.CELSIUS -> base.copy(temperature = TemperatureUnit.CELSIUS)
            androidx.core.text.util.LocalePreferences.TemperatureUnit.FAHRENHEIT -> base.copy(temperature = TemperatureUnit.FAHRENHEIT)
            else -> base
        }
    }
}
