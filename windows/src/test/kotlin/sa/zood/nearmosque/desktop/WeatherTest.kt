package sa.zood.nearmosque.desktop

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import sa.zood.nearmosque.core.LatLng
import sa.zood.nearmosque.core.PrayerEvent
import sa.zood.nearmosque.core.Weather
import sa.zood.nearmosque.core.WeatherCondition
import sa.zood.nearmosque.core.WeatherNow
import sa.zood.nearmosque.core.WeatherPolicy
import sa.zood.nearmosque.core.WeatherUnits
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Sky follows the weather on the PC: the GET to MET Norway (against a local stand-in), the cache, the line, the settings. */
class WeatherTest {
    /** The shared fixture's time (shared/fixtures/met-compact-sample.json). */
    private val fixtureNow = Instant.parse("2026-10-17T13:20:00Z")
    private val fixture = File("../shared/fixtures/met-compact-sample.json").readText()
    private val makkah = LatLng(21.4225, 39.8262)
    private val riyadh = LatLng(24.6877, 46.7219)

    /** A stand-in for api.met.no on localhost: records each request and answers with [reply]. */
    private class FakeMet : AutoCloseable {
        class Seen(val method: String, val path: String, val query: String?, val headers: Map<String, String?>)
        /** gzip: the body is sent gzipped, with Content-Encoding: gzip. */
        class Answer(val status: Int, val headers: Map<String, String> = emptyMap(), val body: String? = null, val gzip: Boolean = false)

        val requests = CopyOnWriteArrayList<Seen>()
        @Volatile var reply: (Seen) -> Answer = { Answer(500) }
        @Volatile var hold: CountDownLatch? = null
        /** Set: the headers and half the body go out, then nothing until it opens (a connection gone quiet mid-reply). */
        @Volatile var stall: CountDownLatch? = null
        @Volatile var stalled = false
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { ex ->
                val h = ex.requestHeaders
                val seen = Seen(ex.requestMethod, ex.requestURI.rawPath, ex.requestURI.rawQuery, listOf("User-Agent", "If-Modified-Since", "Accept-Encoding").associateWith { h.getFirst(it) })
                requests += seen
                hold?.await(10, TimeUnit.SECONDS)
                val a = reply(seen)
                a.headers.forEach { (k, v) -> ex.responseHeaders.add(k, v) }
                if (a.gzip) ex.responseHeaders.add("Content-Encoding", "gzip")
                val bytes = a.body?.toByteArray()?.let { b ->
                    if (!a.gzip) b else ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(b) } }.toByteArray()
                }
                ex.sendResponseHeaders(a.status, if (bytes == null) -1 else bytes.size.toLong())
                val s = stall
                if (bytes != null && s != null) {
                    ex.responseBody.write(bytes, 0, bytes.size / 2)
                    ex.responseBody.flush()
                    stalled = true
                    s.await(10, TimeUnit.SECONDS)
                    ex.responseBody.write(bytes, bytes.size / 2, bytes.size - bytes.size / 2)
                } else {
                    bytes?.let { ex.responseBody.write(it) }
                }
                ex.close()
            }
            start()
        }
        val port: Int get() = server.address.port

        /** The real request's query (lat and lon only) on this server. */
        fun url(p: LatLng) = "http://127.0.0.1:$port/weatherapi/locationforecast/2.0/compact?" + Weather.url(p).substringAfter('?')

        override fun close() = server.stop(0)
    }

    private val closing = mutableListOf<AutoCloseable>()
    @AfterTest fun closeServers() = closing.forEach { it.close() }

    private fun met() = FakeMet().also { closing += it }
    private fun cacheFile(): File = File(Files.createTempDirectory("nm-weather").toFile().apply { deleteOnExit() }, Weather.CACHE_FILE_NAME)

    private var time = fixtureNow
    private fun client(file: File, url: (LatLng) -> String) = WeatherClient(file, url = url, clock = { time }, jitter = { Duration.ZERO })

    private val ok = FakeMet.Answer(
        200,
        mapOf("Last-Modified" to "Sat, 17 Oct 2026 13:17:28 GMT", "Expires" to "Sat, 17 Oct 2026 13:47:30 GMT", "Content-Type" to "application/json"),
        fixture,
    )

    @Test fun asksWithItsOwnNameAndRevalidates() = runBlocking {
        val met = met()
        val file = cacheFile()
        val c = client(file, met::url)
        met.reply = { ok }
        assertTrue(c.refreshIfDue(makkah))
        val first = met.requests.single()
        assertEquals("GET", first.method)
        assertEquals("lat=21.42&lon=39.83", first.query, "only the city, rounded to 0.01°")
        val agent = first.headers["User-Agent"]!!
        assertTrue(agent.startsWith("NearMosque/"), agent)
        assertTrue("+https://nearmosque.net/" in agent && "app@vision.bz" in agent, agent)
        assertFalse("Java" in agent, "MET blocks the JVM's default agent")
        assertNull(first.headers["If-Modified-Since"], "nothing cached yet")
        assertEquals("gzip, deflate", first.headers["Accept-Encoding"], "MET's terms: every client must support gzip")

        val shown = assertNotNull(c.now(makkah, time))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, shown.symbol.condition)
        assertEquals(35.4, shown.temperatureC)
        assertEquals("21.42,39.83", assertNotNull(Weather.decodeCache(file.readText())).key, "kept on disk")

        // Before Expires (and the half hour): no request.
        time = fixtureNow.plus(Duration.ofMinutes(20))
        assertFalse(c.refreshIfDue(makkah))
        assertEquals(1, met.requests.size)

        // Then: If-Modified-Since with the stored Last-Modified, and a 304 keeps the forecast.
        time = fixtureNow.plus(Duration.ofMinutes(31))
        met.reply = { FakeMet.Answer(304, mapOf("Expires" to "Sat, 17 Oct 2026 14:21:30 GMT")) }
        val samples = c.cache!!.samples
        assertTrue(c.refreshIfDue(makkah))
        assertEquals("Sat, 17 Oct 2026 13:17:28 GMT", met.requests[1].headers["If-Modified-Since"])
        assertEquals(samples, c.cache!!.samples)
        assertEquals(time, c.cache!!.validatedAt)
        assertNotNull(c.now(makkah, time))
        assertEquals(Instant.parse("2026-10-17T14:21:30Z"), c.nextFetch(makkah), "the new Expires")

        // The next run of the app reads it back.
        assertEquals(samples, client(file, met::url).cache!!.samples)
    }

    @Test fun readsAGzippedReplyAndFollowsARedirect() = runBlocking {
        val met = met()
        val c = client(cacheFile()) { p -> "http://127.0.0.1:${met.port}/old/compact?" + Weather.url(p).substringAfter('?') }
        met.reply = { seen ->
            if (seen.path.startsWith("/old/")) FakeMet.Answer(301, mapOf("Location" to "/weatherapi/locationforecast/2.0/compact?${seen.query}"))
            else FakeMet.Answer(ok.status, ok.headers, ok.body, gzip = true)
        }
        assertTrue(c.refreshIfDue(makkah))
        assertEquals(listOf("/old/compact", "/weatherapi/locationforecast/2.0/compact"), met.requests.map { it.path })
        assertEquals("lat=21.42&lon=39.83", met.requests[1].query)
        assertTrue(met.requests[1].headers["User-Agent"]!!.startsWith("NearMosque/"), "the redirected request keeps the app's name")
        assertEquals(0, c.cache!!.failures, "a redirect is not a failure")
        val shown = assertNotNull(c.now(makkah, time), "the gzipped forecast was read")
        assertEquals(35.4, shown.temperatureC)
    }

    @Test fun anotherCityWaitsAMinuteAndShowsNothingMeanwhile() = runBlocking {
        val met = met()
        val c = client(cacheFile(), met::url)
        met.reply = { ok }
        c.refreshIfDue(makkah)
        time = fixtureNow.plusSeconds(10)
        assertNull(c.now(riyadh, time), "Makkah's weather is never shown for Riyadh")
        assertEquals(fixtureNow.plus(WeatherPolicy.MIN_GAP), c.nextFetch(riyadh))
        assertFalse(c.refreshIfDue(riyadh))
        time = fixtureNow.plus(WeatherPolicy.MIN_GAP)
        assertTrue(c.refreshIfDue(riyadh))
        assertEquals("lat=24.69&lon=46.72", met.requests[1].query)
        assertNull(met.requests[1].headers["If-Modified-Since"], "Makkah's Last-Modified is not sent for Riyadh")
    }

    @Test fun offlineIsSilent() = runBlocking {
        // A port nothing listens on any more: the connection is refused, as with no network.
        val gone = FakeMet()
        val port = gone.port
        gone.close()
        val file = cacheFile()
        val c = client(file) { p -> "http://127.0.0.1:$port/compact?" + Weather.url(p).substringAfter('?') }
        assertTrue(c.refreshIfDue(makkah), "one try")
        assertNull(c.now(makkah, time), "the normal sky")
        assertEquals(1, c.cache!!.failures)
        assertTrue(c.nextFetch(makkah) >= time.plus(WeatherPolicy.RETRY), "no retry storm")
        assertFalse(c.refreshIfDue(makkah))
    }

    @Test fun oldDataFallsBackToTheNormalSky() = runBlocking {
        val met = met()
        val c = client(cacheFile(), met::url)
        met.reply = { ok }
        c.refreshIfDue(makkah)
        assertNull(c.now(makkah, fixtureNow.plus(WeatherPolicy.MAX_AGE).plusSeconds(60)))
    }

    @Test fun aRefusalWaitsADayEvenAfterTheSwitchIsTurnedOffAndOn() = runBlocking {
        val met = met()
        val file = cacheFile()
        val c = client(file, met::url)
        met.reply = { FakeMet.Answer(403) }
        assertTrue(c.refreshIfDue(makkah))
        c.clear()
        assertFalse(file.exists())
        time = fixtureNow.plus(Duration.ofHours(2))
        assertFalse(c.refreshIfDue(makkah))
        assertFalse(c.refreshIfDue(riyadh), "the block is app-wide")
        assertEquals(1, met.requests.size)
        time = fixtureNow.plus(WeatherPolicy.AFTER_403).plusSeconds(1)
        met.reply = { ok }
        assertTrue(c.refreshIfDue(makkah))
    }

    @Test fun turningOffDeletesTheForecastAndDropsALateReply() = runBlocking {
        val met = met()
        val file = cacheFile()
        val c = client(file, met::url)
        met.reply = { ok }
        c.refreshIfDue(makkah)
        assertTrue(file.isFile)
        c.clear()
        assertFalse(file.exists())
        assertNull(c.cache)
        assertNull(c.now(makkah, time))

        // Switched off while a request is out: its reply is not kept.
        time = fixtureNow.plus(Duration.ofMinutes(5))
        val gate = CountDownLatch(1)
        met.hold = gate
        val job = launch { c.refreshIfDue(makkah) }
        withTimeout(10_000) { while (met.requests.size < 2) delay(10) }
        c.clear()
        gate.countDown()
        job.join()
        assertFalse(file.exists())
        assertNull(c.cache)
    }

    @Test fun aRequestCancelledOnTheWayStillHoldsTheNextOneBackAMinute() = runBlocking {
        val met = met()
        val c = client(cacheFile(), met::url)
        met.reply = { ok }
        assertTrue(c.refreshIfDue(makkah))
        // Due again; the window is minimized while MET is slow to answer, so the request is cancelled unrecorded.
        val sent = fixtureNow.plus(Duration.ofMinutes(31))
        time = sent
        val gate = CountDownLatch(1)
        met.hold = gate
        val job = launch { c.refreshIfDue(makkah) }
        withTimeout(10_000) { while (met.requests.size < 2) delay(10) }
        job.cancelAndJoin()
        gate.countDown()
        met.hold = null
        // Restored at once: no new request within the minute, then one.
        time = sent.plusSeconds(20)
        assertFalse(c.refreshIfDue(makkah))
        assertEquals(2, met.requests.size)
        time = sent.plus(WeatherPolicy.MIN_GAP)
        assertTrue(c.refreshIfDue(makkah))
        assertEquals(3, met.requests.size)
    }

    @Test fun aReplyThatStallsMidBodyLetsGoWhenCancelled() = runBlocking {
        val met = met()
        val c = client(cacheFile(), met::url)
        met.reply = { ok }
        // The headers and half the forecast arrive, then the connection goes quiet (the PC sleeps, the Wi-Fi changes).
        val gate = CountDownLatch(1)
        met.stall = gate
        val job = launch { c.refreshIfDue(makkah) }
        withTimeout(10_000) { while (!met.stalled) delay(10) }
        delay(200)
        // The window is minimized: the blocked read is interrupted, so the request ends and the lock is let go.
        withTimeout(5_000) { job.cancelAndJoin() }
        gate.countDown()
        met.stall = null
        time = fixtureNow.plus(WeatherPolicy.MIN_GAP)
        assertTrue(withTimeout(5_000) { c.refreshIfDue(makkah) }, "the next request still goes out")
        assertNotNull(c.now(makkah, time))
        assertEquals(2, met.requests.size)
    }

    @Test fun aDamagedFileReadsAsNoForecast() {
        val file = cacheFile().apply { parentFile.mkdirs(); writeText("{ not json") }
        val c = client(file) { "http://127.0.0.1:9/" }
        assertNull(c.cache)
        assertNull(c.now(makkah, time))
    }

    private val partly = WeatherNow(Weather.symbol("partlycloudy")!!, 35.4, 5.7, fixtureNow)

    @Test fun theLineHasTheTemperatureAndTheWindOnly() {
        Strings.language = "en"
        assertEquals("⁨35⁩°C · Wind ⁨21⁩ km/h", weatherLine(partly, WeatherUnits.METRIC))
        assertEquals("⁨96⁩°F · Wind ⁨13⁩ mph", weatherLine(partly, WeatherUnits.forRegion("US")))
        assertEquals("⁨35⁩°C · Wind ⁨13⁩ mph", weatherLine(partly, WeatherUnits.forRegion("GB")))
        assertEquals("⁨-3⁩°C · Wind ⁨0⁩ km/h", weatherLine(partly.copy(temperatureC = -2.5, windMs = 0.1), WeatherUnits.METRIC))
        val riyadhCity = Cities.index.search("Riyadh", 1).first()
        assertEquals("Weather in Riyadh: Partly cloudy, ⁨35⁩°C, wind ⁨21⁩ km/h", weatherDescription(partly, riyadhCity, WeatherUnits.METRIC))
    }

    @Test fun theLineInArabic() {
        Strings.language = "ar"
        try {
            val line = weatherLine(partly, WeatherUnits.METRIC)
            assertTrue(line.startsWith("⁨") && "°م" in line && "الرياح" in line && "كم/س" in line, line)
            // Western digits, as the clock and the Hijri date beside it, each number isolated from the RTL text around it.
            assertTrue("⁨35⁩" in line && "⁨21⁩" in line, line)
            assertFalse(line.any { it in '٠'..'٩' || it in '۰'..'۹' }, line)
            assertEquals(4, line.count { it == '⁨' || it == '⁩' })
        } finally {
            Strings.language = "en"
        }
    }

    @Test fun theSwitchIsOffUntilTurnedOn() {
        val file = File(Files.createTempDirectory("nm-weather").toFile(), "settings.properties")
        // A save from before the feature: off, and the offer not yet answered.
        file.writeText("cityId=108410\nlanguage=en\nonlineSearch=true\n")
        val old = Store.load(FilePreferences(file))
        assertFalse(old.skyWeather)
        assertFalse(old.weatherOffered)
        assertFalse(DesktopSettings().skyWeather)
        val on = old.copy(skyWeather = true, weatherOffered = true)
        Store.save(on, FilePreferences(file))
        val p = FilePreferences(file)
        assertTrue(p.getBoolean("skyWeather", false))
        assertTrue(p.getBoolean("weatherOffered", false))
        assertEquals(on, Store.load(p))
        Store.save(on.copy(skyWeather = false), FilePreferences(file))
        assertEquals(on.copy(skyWeather = false), Store.load(FilePreferences(file)))
    }

    @Test fun desktopAnimationSetting() {
        assertEquals(false, Platform.windowsAnimations("\r\nHKEY_CURRENT_USER\\Control Panel\\Desktop\\WindowMetrics\r\n    MinAnimate    REG_SZ    0\r\n\r\n"))
        assertEquals(true, Platform.windowsAnimations("HKEY_CURRENT_USER\\Control Panel\\Desktop\\WindowMetrics\n    MinAnimate    REG_SZ    1\n"))
        assertNull(Platform.windowsAnimations("ERROR: The system was unable to find the specified registry key or value."))
        assertNull(Platform.windowsAnimations(null))
        assertEquals(false, Platform.gnomeAnimations("false\n"))
        assertEquals(true, Platform.gnomeAnimations("true"))
        assertNull(Platform.gnomeAnimations("No such schema"))
        assertEquals(false, Platform.kdeAnimations("[General]\nColorScheme=Breeze\n\n[KDE]\nAnimationDurationFactor=0\nLookAndFeelPackage=x\n"))
        assertEquals(true, Platform.kdeAnimations("[KDE]\nAnimationDurationFactor=0.5\n"))
        assertNull(Platform.kdeAnimations("[Other]\nAnimationDurationFactor=0\n"))
        assertNull(Platform.kdeAnimations(null))
    }

    @Test fun skyPeriodFollowsThePrayerTimes() {
        Strings.language = "en"
        assertEquals(SkyPeriod.NIGHT, SkyPeriod.at(fixtureNow, null, null))
        val sunrise = Instant.parse("2026-10-12T02:52:00Z")
        assertEquals(SkyPeriod.SUNRISE, SkyPeriod.at(sunrise.plusSeconds(1800), sunrise, PrayerEvent.SUNRISE))
        assertEquals(SkyPeriod.DAY, SkyPeriod.at(sunrise.plusSeconds(4000), sunrise, PrayerEvent.SUNRISE))
        val city = Cities.index.search("Riyadh", 1).first()
        val clock = Clock(city, DesktopSettings(cityId = city.id))
        assertEquals(SkyPeriod.DAY, SkyPeriod.of(clock, Instant.parse("2026-10-12T10:00:00Z")))
        assertEquals(SkyPeriod.ASR, SkyPeriod.of(clock, Instant.parse("2026-10-12T13:00:00Z")))
        assertEquals(SkyPeriod.MAGHRIB, SkyPeriod.of(clock, Instant.parse("2026-10-12T15:00:00Z")))
        assertEquals(SkyPeriod.ISHA, SkyPeriod.of(clock, Instant.parse("2026-10-12T18:00:00Z")))
        assertEquals(SkyPeriod.NIGHT, SkyPeriod.of(clock, Instant.parse("2026-10-11T23:30:00Z")), "02:30 in Riyadh, before Fajr")
        assertEquals(SkyPeriod.FAJR, SkyPeriod.of(clock, Instant.parse("2026-10-12T02:00:00Z")))
    }
}
