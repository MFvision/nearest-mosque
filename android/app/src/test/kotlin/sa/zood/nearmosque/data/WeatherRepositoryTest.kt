package sa.zood.nearmosque.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import sa.zood.nearmosque.BuildConfig
import sa.zood.nearmosque.core.LatLng
import sa.zood.nearmosque.core.PrayerSettings
import sa.zood.nearmosque.core.TemperatureUnit
import sa.zood.nearmosque.core.Weather
import sa.zood.nearmosque.core.WeatherCondition
import sa.zood.nearmosque.core.WeatherPolicy
import sa.zood.nearmosque.core.WeatherUnits
import sa.zood.nearmosque.core.WindUnit
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.coroutines.CoroutineContext

/**
 * The app side of "Sky follows the weather": what is sent (and when), what is kept on disk, and what is
 * forgotten. MET's rules are checked here end to end with a fake source and a clock that only moves when told.
 */
class WeatherRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val root = File(System.getProperty("nm.root")!!)
    private val sample = File(root, "shared/fixtures/met-compact-sample.json").readText()
    private val makkah = LatLng(21.4225, 39.8262)
    private val capeTown = LatLng(-33.92584, 18.42322)
    private val makkahKey = "21.42,39.83"

    private class Fake : WeatherSource {
        data class Call(val url: String, val userAgent: String, val lastModified: String?)
        val calls = mutableListOf<Call>()
        val replies = ArrayDeque<WeatherHttp?>()
        override suspend fun fetch(url: String, userAgent: String, lastModified: String?): WeatherHttp? {
            calls += Call(url, userAgent, lastModified)
            return replies.removeFirst()
        }
    }

    private var now = Instant.parse("2026-10-17T13:20:00Z")
    private fun at(s: String) { now = Instant.parse(s) }

    private fun repo(source: WeatherSource, file: File = File(tmp.root, Weather.CACHE_FILE_NAME), random: Double = 0.0) =
        WeatherRepository(source, file, { now }, { random })

    private fun ok(expires: String? = "Sat, 17 Oct 2026 13:47:30 GMT", lastModified: String? = "Sat, 17 Oct 2026 13:17:28 GMT") =
        WeatherHttp(200, expires, lastModified, sample)

    @Test
    fun honoursExpiresIfModifiedSinceBackoffAndTheDayLongBlock() = runBlocking {
        val src = Fake()
        val file = File(tmp.root, Weather.CACHE_FILE_NAME)
        val r = repo(src, file)

        // 200: the rounded city only, the app's own User-Agent, nothing to revalidate yet.
        src.replies += ok()
        r.refreshIfDue(makkah)
        assertEquals(1, src.calls.size)
        with(src.calls.last()) {
            assertEquals("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=21.42&lon=39.83", url)
            assertEquals("NearMosque/${BuildConfig.VERSION_NAME} (Android; +https://nearmosque.net/; app@vision.bz)", userAgent)
            assertFalse(userAgent.contains("Dalvik") || userAgent.startsWith("Java"))
            assertNull(lastModified)
        }
        val first = r.cache.value!!
        assertEquals(makkahKey, first.key)
        assertEquals(Instant.parse("2026-10-17T13:47:30Z"), first.expires)
        assertEquals(first, Weather.decodeCache(file.readText()))
        assertFalse(File(file.path + ".tmp").exists())
        val shown = Weather.now(first, makkahKey, now)!!
        assertEquals(WeatherCondition.PARTLY_CLOUDY, shown.symbol.condition)
        assertEquals(35.4, shown.temperatureC, 0.0)

        // Nothing before Expires (and at least 30 minutes after the last request).
        assertEquals(Duration.ofMinutes(30).toMillis(), r.millisUntilDue(makkah))
        at("2026-10-17T13:49:59Z")
        r.refreshIfDue(makkah)
        assertEquals(1, src.calls.size)

        // Then If-Modified-Since with the stored Last-Modified, unchanged; a 304 keeps the forecast and renews it.
        at("2026-10-17T13:50:00Z")
        src.replies += WeatherHttp(304, "Sat, 17 Oct 2026 14:20:00 GMT", null, null)
        r.refreshIfDue(makkah)
        assertEquals("Sat, 17 Oct 2026 13:17:28 GMT", src.calls.last().lastModified)
        val revalidated = r.cache.value!!
        assertEquals(first.samples, revalidated.samples)
        assertEquals(now, revalidated.validatedAt)
        assertEquals(Instant.parse("2026-10-17T14:20:00Z"), revalidated.expires)
        assertEquals("Sat, 17 Oct 2026 13:17:28 GMT", revalidated.lastModified)

        // 429: back off 30 minutes, then an hour; the last forecast keeps showing meanwhile.
        at("2026-10-17T14:20:00Z")
        src.replies += WeatherHttp(429, null, null, null)
        r.refreshIfDue(makkah)
        assertEquals(3, src.calls.size)
        assertEquals(1, r.cache.value!!.failures)
        at("2026-10-17T14:49:59Z"); r.refreshIfDue(makkah)
        assertEquals(3, src.calls.size)
        at("2026-10-17T14:50:00Z")
        src.replies += WeatherHttp(429, null, null, null)
        r.refreshIfDue(makkah)
        assertEquals(4, src.calls.size)
        at("2026-10-17T15:49:59Z"); r.refreshIfDue(makkah)
        assertEquals(4, src.calls.size)
        assertNotNull(Weather.now(r.cache.value, makkahKey, now))

        // 403: nothing for 24 hours, for any city.
        at("2026-10-17T15:50:00Z")
        src.replies += WeatherHttp(403, null, null, null)
        r.refreshIfDue(makkah)
        assertEquals(5, src.calls.size)
        at("2026-10-17T16:51:00Z")
        r.refreshIfDue(makkah)
        r.refreshIfDue(capeTown)
        assertEquals(5, src.calls.size)
        // Over three hours since MET last confirmed it: the normal sky, silently.
        assertNull(Weather.now(r.cache.value, makkahKey, now))

        // Turning the switch off and on again does not get round the block, and keeps no city on disk.
        r.clear()
        assertNull(r.cache.value)
        val tombstone = Weather.decodeCache(file.readText())!!
        assertEquals("", tombstone.key)
        assertTrue(tombstone.samples.isEmpty())
        assertFalse(file.readText().contains("21.42"))
        at("2026-10-18T15:49:59Z")
        r.refreshIfDue(makkah)
        assertEquals(5, src.calls.size)
        // Still held after the app is restarted.
        val reopened = repo(src, file)
        reopened.refreshIfDue(makkah)
        assertEquals(5, src.calls.size)

        at("2026-10-18T15:50:00Z")
        src.replies += ok(expires = "Sun, 18 Oct 2026 16:20:00 GMT", lastModified = "Sun, 18 Oct 2026 15:30:00 GMT")
        reopened.refreshIfDue(makkah)
        assertEquals(6, src.calls.size)
        assertNull(src.calls.last().lastModified)
        assertEquals(makkahKey, reopened.cache.value!!.key)

        // Switched off: the file is deleted and the flow emptied.
        reopened.clear()
        assertNull(reopened.cache.value)
        assertFalse(file.exists())
    }

    @Test
    fun anotherCityWaitsAMinuteAndSendsNoValidator() = runBlocking {
        val src = Fake()
        val r = repo(src)
        src.replies += ok()
        r.refreshIfDue(makkah)
        at("2026-10-17T13:20:30Z")
        r.refreshIfDue(capeTown)
        assertEquals(1, src.calls.size)
        assertEquals(30_000L, r.millisUntilDue(capeTown))
        at("2026-10-17T13:21:00Z")
        src.replies += ok()
        r.refreshIfDue(capeTown)
        assertEquals(2, src.calls.size)
        assertTrue(src.calls.last().url.endsWith("?lat=-33.93&lon=18.42"))
        assertNull(src.calls.last().lastModified)
        assertEquals("-33.93,18.42", r.cache.value!!.key)
        // The previous city is not shown for the new one.
        assertNull(Weather.now(r.cache.value, makkahKey, now))
    }

    @Test
    fun offlineKeepsTheLastForecastThenFallsBack() = runBlocking {
        val src = Fake()
        val r = repo(src)
        src.replies += null
        r.refreshIfDue(makkah)
        assertEquals(1, r.cache.value!!.failures)
        assertNull(Weather.now(r.cache.value, makkahKey, now))

        at("2026-10-17T13:50:00Z")
        src.replies += ok()
        r.refreshIfDue(makkah)
        at("2026-10-17T14:20:00Z")
        src.replies += null
        r.refreshIfDue(makkah)
        assertEquals(1, r.cache.value!!.failures)
        assertNotNull(Weather.now(r.cache.value, makkahKey, now))
        at("2026-10-17T16:50:01Z")
        assertNull(Weather.now(r.cache.value, makkahKey, now))
    }

    @Test
    fun jitterIsDrawnOnceAndAddedToTheSchedule() = runBlocking {
        val src = Fake()
        val r = repo(src, random = 1.0)
        src.replies += ok(expires = "Sat, 17 Oct 2026 14:00:00 GMT")
        r.refreshIfDue(makkah)
        assertEquals(Duration.ofMinutes(40).plus(WeatherPolicy.MAX_JITTER).toMillis(), r.millisUntilDue(makkah))
        at("2026-10-17T14:01:29Z"); r.refreshIfDue(makkah)
        assertEquals(1, src.calls.size)
        at("2026-10-17T14:01:30Z")
        src.replies += WeatherHttp(304, null, null, null)
        r.refreshIfDue(makkah)
        assertEquals(2, src.calls.size)
    }

    @Test
    fun theCacheIsReadBackLazilyAndDamagedFilesAreIgnored() = runBlocking {
        val file = File(tmp.root, Weather.CACHE_FILE_NAME)
        val src = Fake()
        src.replies += ok()
        repo(src, file).refreshIfDue(makkah)

        // A restarted app reads the file on its first refresh and does not ask again before Expires.
        val again = repo(src, file)
        assertNull(again.cache.value)
        at("2026-10-17T13:40:00Z")
        again.refreshIfDue(makkah)
        assertEquals(1, src.calls.size)
        assertEquals(makkahKey, again.cache.value!!.key)

        file.writeText("{not json")
        val damaged = repo(src, file)
        src.replies += ok()
        damaged.refreshIfDue(makkah)
        assertEquals(2, src.calls.size)
        assertEquals(makkahKey, damaged.cache.value!!.key)
    }

    @Test
    fun aReplyArrivingAfterTheSwitchIsTurnedOffIsDropped() = runBlocking {
        val file = File(tmp.root, Weather.CACHE_FILE_NAME)
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<WeatherHttp?>()
        val r = repo({ _, _, _ -> entered.complete(Unit); gate.await() }, file)
        val job = launch { r.refreshIfDue(makkah) }
        entered.await()
        r.clear()
        gate.complete(ok())
        job.join()
        assertNull(r.cache.value)
        assertFalse(file.exists())
    }

    @Test
    fun turningOffWhileTheReplyIsBeingWrittenLeavesNoFile() = runBlocking {
        val file = File(tmp.root, Weather.CACHE_FILE_NAME)
        val src = Fake()
        src.replies += ok()
        lateinit var r: WeatherRepository
        var turnOff = true
        // The switch is turned off just as the write is handed to the IO thread (after the reply was checked).
        val io = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) = Dispatchers.IO.dispatch(context, Runnable {
                if (turnOff) { turnOff = false; r.clear() }
                block.run()
            })
        }
        r = WeatherRepository(src, file, { now }, { 0.0 }, io = io)
        r.refreshIfDue(makkah)
        assertEquals(1, src.calls.size)
        assertFalse(turnOff)
        assertNull(r.cache.value)
        assertFalse("the forecast is not written back after clear()", file.exists())
        assertFalse(File(file.path + ".tmp").exists())
    }

    @Test
    fun aReplyArrivingAfterTheAppLeftTheScreenIsStillKept() = runBlocking {
        val file = File(tmp.root, Weather.CACHE_FILE_NAME)
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<WeatherHttp?>()
        val r = repo({ _, _, _ -> entered.complete(Unit); gate.await() }, file)
        val job = launch { r.refreshIfDue(makkah) }
        entered.await()
        // The app stops (repeatOnLifecycle cancels the loop) while MET is answering: its Expires still counts.
        job.cancel()
        gate.complete(ok())
        job.join()
        assertEquals(makkahKey, r.cache.value!!.key)
        assertEquals(Instant.parse("2026-10-17T13:47:30Z"), Weather.decodeCache(file.readText())!!.expires)
    }

    @Test
    fun theSkyShowsOnlyWithTheSwitchOnForThePrayerCity() = runBlocking {
        val src = Fake()
        val r = repo(src)
        src.replies += ok()
        r.refreshIfDue(makkah)
        val loc = PrayerLocation("Makkah", makkah, ZoneId.of("Asia/Riyadh"), "SA", PrayerLocation.Source.CITY)
        val on = sa.zood.nearmosque.data.AppSettings(loc, false, PrayerSettings(), false, emptySet(), "compass", skyWeather = true)
        assertNotNull(sa.zood.nearmosque.ui.prayer.PrayerViewModel.weatherNow(on, r.cache.value, now))
        assertNull(sa.zood.nearmosque.ui.prayer.PrayerViewModel.weatherNow(on.copy(skyWeather = false), r.cache.value, now))
        assertNull(sa.zood.nearmosque.ui.prayer.PrayerViewModel.weatherNow(on.copy(prayerLocation = null), r.cache.value, now))
        val elsewhere = on.copy(prayerLocation = loc.copy(location = capeTown))
        assertNull(sa.zood.nearmosque.ui.prayer.PrayerViewModel.weatherNow(elsewhere, r.cache.value, now))
        // "Use my location": the weather of the city near the device (its coordinates are what is sent), never of the
        // device's own position; with no city within 30 km, none at all.
        val device = loc.copy(location = LatLng(21.4012, 39.8571), source = PrayerLocation.Source.DEVICE, nearCity = makkah)
        assertEquals(makkah, device.weatherLocation)
        assertEquals(makkah, loc.weatherLocation)
        assertNotNull(sa.zood.nearmosque.ui.prayer.PrayerViewModel.weatherNow(on.copy(prayerLocation = device), r.cache.value, now))
        assertNull(device.copy(nearCity = null).weatherLocation)
        assertNull(sa.zood.nearmosque.ui.prayer.PrayerViewModel.weatherNow(on.copy(prayerLocation = device.copy(nearCity = null)), r.cache.value, now))
    }

    @Test
    fun theCityNearTheDeviceIsSavedWithTheLocation() = runBlocking {
        val file = File.createTempFile("weather-loc", ".preferences_pb").also { it.delete() }
        val job = SupervisorJob()
        val s = SettingsStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file })
        val device = PrayerLocation("Makkah", LatLng(21.4012, 39.8571), ZoneId.of("Asia/Riyadh"), "SA", PrayerLocation.Source.DEVICE, nearCity = makkah)
        s.setPrayerLocation(device, follow = true)
        assertEquals(device, s.settings.first().prayerLocation)
        s.setPrayerLocation(device.copy(nearCity = null), follow = true)
        assertNull(s.settings.first().prayerLocation!!.nearCity)
        job.cancel(); job.join()
    }

    @Test
    fun oldSavesHaveTheSwitchOffAndItSurvivesAReopen() = runBlocking {
        val file = File.createTempFile("weather", ".preferences_pb").also { it.delete() }
        fun store(job: kotlinx.coroutines.Job) = SettingsStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file })
        val old = SupervisorJob()
        val s = store(old)
        s.setOnlineSearch(false)
        assertFalse(s.settings.first().skyWeather)
        s.setSkyWeather(true)
        old.cancel(); old.join()
        assertTrue(store(SupervisorJob()).settings.first().skyWeather)
    }

    @Test
    fun unitsFollowAnExplicitTemperatureChoiceThenTheRegion() {
        fun units(locale: Locale, system: String? = null, explicit: String = "") = WeatherDisplay.units(locale, { system }, { explicit })
        assertEquals(WeatherUnits.METRIC, units(Locale.forLanguageTag("ar-SA")))
        assertEquals(WeatherUnits(TemperatureUnit.FAHRENHEIT, WindUnit.MPH), units(Locale.US))
        assertEquals(WeatherUnits(TemperatureUnit.CELSIUS, WindUnit.MPH), units(Locale.UK))
        // An app language without a region ("ar") takes the phone's region.
        assertEquals(WeatherUnits(TemperatureUnit.FAHRENHEIT, WindUnit.MPH), units(Locale.forLanguageTag("ar"), system = "US"))
        assertEquals(WeatherUnits.METRIC, units(Locale.forLanguageTag("ar"), system = null))
        // Android 14 Regional preferences: the temperature only; the wind still follows the region.
        assertEquals(WeatherUnits(TemperatureUnit.CELSIUS, WindUnit.MPH), units(Locale.US, explicit = "celsius"))
        assertEquals(WeatherUnits(TemperatureUnit.FAHRENHEIT, WindUnit.KMH), units(Locale.forLanguageTag("ar-SA"), explicit = "fahrenhe"))
        assertEquals(WeatherUnits(TemperatureUnit.FAHRENHEIT, WindUnit.MPH), units(Locale.US, explicit = "kelvin"))
    }
}
