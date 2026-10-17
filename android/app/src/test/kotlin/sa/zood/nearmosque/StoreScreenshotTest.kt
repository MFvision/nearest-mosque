package sa.zood.nearmosque

import android.graphics.drawable.GradientDrawable
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sa.zood.nearmosque.core.AlertSettings
import sa.zood.nearmosque.core.IqamaSettings
import sa.zood.nearmosque.core.LatLng
import sa.zood.nearmosque.core.Method
import sa.zood.nearmosque.core.PrayerEvent
import sa.zood.nearmosque.core.SilenceSettings
import sa.zood.nearmosque.data.PrayerLocation
import sa.zood.nearmosque.platform.PrayerWidgets
import sa.zood.nearmosque.platform.WidgetExtras
import sa.zood.nearmosque.platform.WidgetKind
import sa.zood.nearmosque.platform.WidgetMosque
import sa.zood.nearmosque.platform.WidgetStyle
import sa.zood.nearmosque.ui.AppRoot
import sa.zood.nearmosque.ui.Tab
import sa.zood.nearmosque.ui.theme.NearMosqueTheme
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The Google Play screenshots: for every app language and device ([Device]), eight real screens with the same demo
 * data as the App Store screenshots (tools/store/screens.json: Riyadh for the prayer times and the Qibla, Cairo for
 * the mosques), in store order: 01 prayer, 02 weather (the same prayer screen with Sky follows the weather on: a
 * fixed rain forecast from a fake MET source, nothing fetched), 03 Qibla, 04 mosques, 05 Ask, 06 library,
 * 07 widgets, 08 silence. Output: app/build/store-play/<appLang>/<device>/NN-name.png, flattened and checked
 * afterwards by tools/store/play_graphics.py (which takes phone, seven, ten, desktop and xr; the other devices are
 * for checking layouts). The desktop and XR slots leave out the home-screen widgets (07): neither ChromeOS nor
 * Android XR has them. No test here touches the network: the mosque search and the weather are fakes.
 *
 * Left out of ordinary test runs: Gradle runs it only with -PstoreShots=true; -PstoreLangs="en ar" for some
 * languages, -PstoreDevices="phone cover" for other devices than phone, seven, ten, desktop and xr, -PstoreClock=12 or 24 to
 * force the phone's clock (e.g. to check the 12-hour times of every language). For example:
 * ./gradlew :app:testDebugUnitTest -PstoreShots=true -Proborazzi.test.record=true --tests '*StoreScreenshotTest'
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w432dp-h768dp-400dpi", application = android.app.Application::class)
class StoreScreenshotTest(private val lang: String, private val qualifier: String, private val device: Device) {
    /** The screens rendered: Google Play's phone, 7-inch, 10-inch, Chromebook (desktop) and Android XR slots, and other sizes to check layouts on. */
    enum class Device(val qualifiers: String) {
        /** 1080x1920, 9:16: a large phone. */
        PHONE("w432dp-h768dp-400dpi"),
        /** Galaxy Z Fold inner screen, unfolded: 1811x2176 (Play's 7-inch slot). */
        SEVEN("w690dp-h829dp-420dpi"),
        /** A 10-inch tablet held sideways, its app window: 2560x1440, 16:9 (Play's 10-inch slot). */
        TEN("w1280dp-h720dp-xhdpi"),
        /** A desktop (Chromebook or PC) window, maximized: 2880x1620, 16:9 (Play's Chromebook slot: 16:9, 1080 px or more). */
        DESKTOP("w1440dp-h810dp-xhdpi"),
        /**
         * An Android XR panel: Android XR shows large-screen apps on a 1024 x 720 dp panel; 3072x2160, which
         * play_graphics.py sets in a space at 3840x2160 (Play Console takes only 16:9 or 9:16 in the XR slot).
         */
        XR("w1024dp-h720dp-xxhdpi"),
        /** Galaxy Z Fold cover screen: 903x2315. */
        COVER("w344dp-h882dp-420dpi"),
        /** The Fold's inner screen turned sideways: 2176x1811. */
        INNER_LAND("w829dp-h690dp-420dpi"),
        /** A 10-inch tablet upright: 1600x2560. */
        TABLET("w800dp-h1280dp-xhdpi"),
        ;

        val folder get() = name.lowercase().replace('_', '-')
    }

    companion object {
        /** The enabled languages of shared/i18n/languages.json (app code, resource qualifier), on each device asked for. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{2}")
        fun languages(): List<Array<Any>> {
            val root = File(System.getProperty("nm.root") ?: "../..")
            val wanted = System.getProperty("nm.storeLangs").orEmpty().split(' ', ',').filter(String::isNotBlank).toSet()
            val devices = System.getProperty("nm.storeDevices").orEmpty().split(' ', ',').filter(String::isNotBlank)
                .map { d -> Device.entries.first { it.folder == d } }.ifEmpty { listOf(Device.PHONE, Device.SEVEN, Device.TEN, Device.DESKTOP, Device.XR) }
            val all = Json.parseToJsonElement(File(root, "shared/i18n/languages.json").readText()).jsonObject["languages"]!!.jsonArray
            return all.map { it.jsonObject }
                .filter { it["enabled"]!!.jsonPrimitive.boolean }
                .map { it["code"]!!.jsonPrimitive.content to it["android"]!!.jsonPrimitive.content.removePrefix("values").removePrefix("-") }
                .filter { wanted.isEmpty() || it.first in wanted }
                .flatMap { (code, q) -> devices.map { arrayOf<Any>(code, q.ifEmpty { "en" }, it) } }
        }

        private val riyadhZone = ZoneId.of("Asia/Riyadh")

        /** 16:20 in Riyadh on 3 October 2026: after Asr, the late-afternoon sky and the countdown to Maghrib. */
        val at: Instant = ZonedDateTime.of(2026, 10, 3, 16, 20, 37, 0, riyadhZone).toInstant()

        /** "12" or "24" forces the phone's clock (-PstoreClock); otherwise each language's usual one. */
        private val clock = System.getProperty("nm.storeClock").orEmpty().takeIf { it == "12" || it == "24" }

        /** The demo cities' names in the languages that write them differently (the others show the English name). */
        private val riyadhNames = mapOf(
            "ar" to "الرياض", "ur" to "ریاض", "fa" to "ریاض", "prs" to "ریاض", "ug" to "رىياد", "ckb" to "ڕیاز",
            "ru" to "Эр-Рияд", "sr" to "Ријад", "mk" to "Ријад", "zh" to "利雅得", "hi" to "रियाद", "bn" to "রিয়াদ",
            "th" to "ริยาด", "ka" to "ერ-რიადი", "tr" to "Riyad", "fr" to "Riyad", "es" to "Riad", "pt" to "Riade",
            "nl" to "Riyad", "hu" to "Rijád", "bs" to "Rijad",
        )
        private val cairoNames = mapOf(
            "ar" to "القاهرة", "ur" to "قاہرہ", "fa" to "قاهره", "prs" to "قاهره", "ug" to "قاھىرە", "ckb" to "قاهیرە",
            "ru" to "Каир", "sr" to "Каиро", "mk" to "Каиро", "zh" to "开罗", "hi" to "काहिरा", "bn" to "কায়রো",
            "th" to "ไคโร", "ka" to "კაირო", "tr" to "Kahire", "fr" to "Le Caire", "es" to "El Cairo", "pt" to "Cairo",
            "nl" to "Caïro", "hu" to "Kairó", "bs" to "Kairo", "id" to "Kairo", "sw" to "Kairo",
        )
    }

    @get:Rule val compose = createEmptyComposeRule()

    private val riyadh get() = PrayerLocation(riyadhNames[lang] ?: "Riyadh", LatLng(24.7136, 46.6753), riyadhZone, "SA", PrayerLocation.Source.CITY)
    private val cairo get() = PrayerLocation(cairoNames[lang] ?: "Cairo", LatLng(30.0444, 31.2357), ZoneId.of("Africa/Cairo"), "EG", PrayerLocation.Source.CITY)

    private var scenario: ActivityScenario<ComponentActivity>? = null
    private lateinit var activity: ComponentActivity

    /**
     * Where shot [name] ("NN-name") goes. Any other file with the same number NN, left there by an older set
     * (e.g. 07-iqama before 07-widgets), is removed first: play_graphics.py takes every PNG in the folder.
     */
    private fun out(name: String): String {
        val file = File("build/store-play/$lang/${device.folder}/$name.png")
        clearNumber(name.substringBefore('-'), keep = file.name)
        return file.path
    }

    /** Deletes the device folder's NN-*.png files other than [keep] (none kept when it is null). */
    private fun clearNumber(number: String, keep: String? = null) {
        File("build/store-play/$lang/${device.folder}").listFiles { f -> f.name.startsWith("$number-") && f.name.endsWith(".png") && f.name != keep }
            ?.forEach { it.delete() }
    }

    /** The language's resources, then a fresh activity in it (its layout direction follows). */
    private fun launch() {
        RuntimeEnvironment.setQualifiers("+$qualifier-${device.qualifiers}")
        // The JVM's default locale follows the language too, as on a phone set to it (and as with @Config qualifiers
        // in ScreenshotTest); otherwise it stays en-US for every language, and the weather line, which takes its units
        // from the formatting locale's region, would show °F and mph in Arabic. With no region (en, ar, ...) the units
        // are °C and km/h.
        savedLocale = java.util.Locale.getDefault()
        java.util.Locale.setDefault(RuntimeEnvironment.getApplication().resources.configuration.locales[0])
        android.provider.Settings.Global.putFloat(RuntimeEnvironment.getApplication().contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        if (clock != null) android.provider.Settings.System.putString(RuntimeEnvironment.getApplication().contentResolver, android.provider.Settings.System.TIME_12_24, clock)
        scenario = ActivityScenario.launch(ComponentActivity::class.java).also { s -> s.onActivity { activity = it } }
    }

    private var savedLocale: java.util.Locale? = null

    @After fun close() {
        scenario?.close()
        savedLocale?.let { java.util.Locale.setDefault(it) }
    }

    private fun string(id: Int, vararg args: Any): String = activity.getString(id, *args)

    /** "Sky follows the weather" in a screenshot: a MET symbol code with its temperature (°C) and wind (m/s). */
    private data class Wx(val symbol: String, val tempC: Double, val windMs: Double)

    /** A compact MET reply whose steps start at the hour of [at], all with [w] (as ScreenshotTest builds it). */
    private fun metBody(w: Wx): String {
        val start = at.truncatedTo(java.time.temporal.ChronoUnit.HOURS)
        val steps = (0..3).joinToString(",") { i ->
            """{"time":"${start.plusSeconds(3600L * i)}","data":{"instant":{"details":{"air_temperature":${w.tempC},"wind_speed":${w.windMs}}},""" +
                """"next_1_hours":{"summary":{"symbol_code":"${w.symbol}"}}}}"""
        }
        return """{"type":"Feature","properties":{"meta":{"units":{"air_temperature":"celsius","wind_speed":"m/s"}},"timeseries":[$steps]}}"""
    }

    /**
     * The app with the demo data. No network: live mosque search returns nothing new, and the weather comes from a
     * fake MET source that answers with [weather] (only when it is given; otherwise the switch stays off, the
     * default, and the fake is never asked).
     */
    private fun container(location: PrayerLocation = riyadh, method: Method = Method.UMM_AL_QURA, weather: Wx? = null): AppContainer = runBlocking {
        val c = AppContainer(
            activity, inMemoryDb = true, clock = { at },
            settingsFile = File.createTempFile("settings", ".preferences_pb").also { it.delete() },
            online = sa.zood.nearmosque.data.OnlineMosqueSource { _, _ -> emptyList() },
            weatherSource = sa.zood.nearmosque.data.WeatherSource { _, _, _ ->
                weather?.let { sa.zood.nearmosque.data.WeatherHttp(200, null, null, metBody(it)) }
            },
        )
        c.packs.ensureBuiltins { !sa.zood.nearmosque.data.ChunkScope.isLibrary(it.id) }
        c.start(installLibraries = false)
        c.settings.setPrayerLocation(location, follow = false)
        c.settings.setMethod(method, byUser = false)
        c.settings.setReminder(PrayerEvent.FAJR, true)
        c.settings.setReminder(PrayerEvent.MAGHRIB, true)
        c.weather.clear()
        if (weather != null) {
            c.settings.setSkyWeather(true)
            // Cached before the first frame, so the screenshot shows it without waiting for the refresh loop.
            c.weather.refreshIfDue(location.location)
        }
        c
    }

    private fun app(c: AppContainer, tab: Tab, dark: Boolean = true) {
        activity.setContent { NearMosqueTheme(dark = dark) { AppRoot(c, initialTab = tab, showOnboarding = false) } }
        compose.waitForIdle()
    }

    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2000)
        compose.waitForIdle()
    }

    private fun waitForText(text: String, timeout: Long = 20_000) =
        compose.waitUntil(timeout) { runCatching { compose.onNodeWithText(text, useUnmergedTree = true).assertExists() }.isSuccess }

    /** A phone compass (rotation vector sensor), added before the screen starts listening. */
    private fun compassSensor(): android.hardware.Sensor {
        val sm = activity.getSystemService(android.hardware.SensorManager::class.java)
        val sensor = org.robolectric.shadows.ShadowSensor.newInstance(android.hardware.Sensor.TYPE_ROTATION_VECTOR)
        org.robolectric.Shadows.shadowOf(sm).addSensor(sensor)
        return sensor
    }

    /** The phone lying flat, its top [azimuth] degrees from magnetic north. */
    private fun sendHeading(sensor: android.hardware.Sensor, azimuth: Double) {
        val sm = activity.getSystemService(android.hardware.SensorManager::class.java)
        val half = Math.toRadians(-azimuth) / 2
        repeat(30) {
            val e = org.robolectric.shadows.SensorEventBuilder.newBuilder().setSensor(sensor)
                .setValues(floatArrayOf(0f, 0f, kotlin.math.sin(half).toFloat(), kotlin.math.cos(half).toFloat(), 0f)).build()
            org.robolectric.Shadows.shadowOf(sm).sendSensorEventToListeners(e)
            compose.mainClock.advanceTimeBy(50)
        }
    }

    /** Magnetic heading that faces the Qibla from Riyadh (true bearing minus the local declination). */
    private fun facingQibla(): Double {
        val loc = riyadh.location
        val decl = android.hardware.GeomagneticField(loc.latitude.toFloat(), loc.longitude.toFloat(), 0f, at.toEpochMilli()).declination
        // Held sideways the top of the screen is the phone's left edge (HeadingService turns the axes to match).
        @Suppress("DEPRECATION")
        val rotation = activity.windowManager.defaultDisplay.rotation
        return sa.zood.nearmosque.core.Qibla.bearing(loc) - decl - 90.0 * rotation
    }

    @Test fun shot1Prayer() {
        launch()
        val sensor = compassSensor()
        app(container(), Tab.PRAYER)
        sendHeading(sensor, facingQibla())
        settle()
        compose.onRoot().captureRoboImage(out("01-prayer"))
    }

    /**
     * The same prayer screen with Sky follows the weather turned on (it is off by default): rain on the sky, with the
     * temperature and the wind under the city. A fixed demo forecast (rain, 18 °C, wind 4 m/s, as the iOS
     * -demoWeather screenshots) from the fake MET source; in the app the weather comes from MET Norway.
     */
    @Test fun shot2Weather() {
        launch()
        val sensor = compassSensor()
        app(container(weather = Wx("rain", 18.0, 4.0)), Tab.PRAYER)
        sendHeading(sensor, facingQibla())
        settle()
        compose.onRoot().captureRoboImage(out("02-weather"))
    }

    /** The Qibla compass, facing the Qibla (the light brightens and the arrow points straight up). */
    @Test fun shot3Qibla() {
        launch()
        val c = container()
        val vm = sa.zood.nearmosque.ui.prayer.PrayerViewModel(c)
        vm.refreshNearestMosque(lang)
        val heading = sa.zood.nearmosque.core.Qibla.bearing(riyadh.location) - 1.0
        activity.setContent {
            NearMosqueTheme(dark = true) {
                CompositionLocalProvider(LocalAppContainer provides c) {
                    val ui by vm.ui.collectAsState()
                    CompositionLocalProvider(sa.zood.nearmosque.ui.theme.LocalSky provides sa.zood.nearmosque.ui.theme.Sky.of(ui.sky, true)) {
                        val compass = sa.zood.nearmosque.core.CompassState.Live(heading, 8.0, false)
                        val aligned = ui.qiblaBearing?.let { kotlin.math.abs(sa.zood.nearmosque.core.Angles.relativeToQibla(it, heading)) < 3 } ?: false
                        sa.zood.nearmosque.ui.prayer.QiblaCompassScreen(ui, compass, aligned, onClose = {})
                    }
                }
            }
        }
        settle()
        compose.onRoot().captureRoboImage(out("03-qibla"))
    }

    /** The mosque compass around Cairo (the MapLibre map does not draw under Robolectric) and the nearest ones. */
    @Test fun shot4Mosques() {
        launch()
        app(container(cairo, Method.EGYPTIAN), Tab.MOSQUES)
        // The area's mosques come from the Cairo pack on a background thread.
        waitForText(string(R.string.nearest_known_mosque), 30_000)
        settle()
        compose.onRoot().captureRoboImage(out("04-mosques"))
    }

    /** Ask: the question and the start of the common answer on the Quran as a miracle; the Ask screen where there is none. */
    @Test fun shot5Ask() {
        launch()
        val c = container()
        val q = runBlocking { c.ask.commonQuestions() }.firstOrNull { it.id == "quran-miracle" }?.question?.get(lang)
        app(c, Tab.ASK)
        if (q != null) {
            compose.onNode(androidx.compose.ui.test.hasScrollToNodeAction() and androidx.compose.ui.test.hasAnyDescendant(androidx.compose.ui.test.hasText(string(R.string.ask_title))))
                .performScrollToIndex(2)
            waitForText(q)
            compose.onNodeWithText(q).performScrollTo().performClick()
            // The answer scrolls up to the question: the question, then the start of the answer.
            waitForText(string(R.string.sources))
        }
        settle()
        compose.onRoot().captureRoboImage(out("05-ask"))
    }

    /** The library's classical books (opened from Ask: library, browse, classical books). */
    @OptIn(ExperimentalRoborazziApi::class)
    @Test fun shot6Library() {
        launch()
        app(container(), Tab.ASK)
        compose.onNodeWithContentDescription(string(R.string.library_title)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(string(R.string.library_browse)).performClick()
        waitForText(string(R.string.classics_title))
        compose.onNodeWithText(string(R.string.classics_title)).performClick()
        waitForText(string(R.string.classics_note))
        settle()
        captureScreenRoboImage(out("06-library"))
    }

    /** Home-screen widgets on a dusk wallpaper, drawn from their RemoteViews like WidgetScreenshotTest. */
    @Test fun shot7Widgets() = runBlocking {
        // Neither ChromeOS nor Android XR puts app widgets on a home screen: no widgets in those listings (and no
        // 07 file left there from an older set).
        if (device == Device.DESKTOP || device == Device.XR) clearNumber("07")
        org.junit.Assume.assumeTrue(device != Device.DESKTOP && device != Device.XR)
        launch()
        val c = container()
        val s = c.settings.settings.first()
        val d = activity.resources.displayMetrics.density
        fun px(dp: Int) = (dp * d).toInt()
        val extras = WidgetExtras(mosques = listOf(WidgetMosque("demo/1", "Al Rajhi Mosque", 650.0, 300.0)))
        fun widget(kind: WidgetKind, style: WidgetStyle, w: Int, h: Int): android.view.View {
            val (views, _) = PrayerWidgets.views(activity, c, s, kind, style, extras)
            val frame = FrameLayout(activity)
            frame.addView(views.apply(activity, frame), FrameLayout.LayoutParams(px(w), px(h)))
            return frame
        }
        fun row(vararg v: android.view.View) = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            v.forEachIndexed { i, child ->
                addView(child, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { if (i > 0) marginStart = px(12) })
            }
        }
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(0, px(60), 0, 0)
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF101B33.toInt(), 0xFF2C3A63.toInt(), 0xFF7A5A86.toInt(), 0xFFD99A6C.toInt()))
            val gap = { LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = px(16) } }
            addView(row(widget(WidgetKind.SMALL, WidgetStyle.CREAM, 184, 184), widget(WidgetKind.QIBLA_SMALL, WidgetStyle.GREEN, 184, 184)))
            addView(widget(WidgetKind.TODAY, WidgetStyle.NIGHT, 380, 196), gap())
            addView(row(widget(WidgetKind.RING_SMALL, WidgetStyle.TEAL, 184, 184), widget(WidgetKind.MOSQUE_SMALL, WidgetStyle.CREAM, 184, 184)), gap())
        }
        activity.setContentView(column, android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT))
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        column.captureRoboImage(out("07-widgets"))
    }

    /** Prayer & Audio scrolled so the section headed [header] starts at the top. */
    private fun prayerAudio(header: String, alerts: AlertSettings, iqama: IqamaSettings, silence: SilenceSettings, name: String) {
        val c = container()
        activity.setContent {
            NearMosqueTheme(dark = true) {
                sa.zood.nearmosque.ui.settings.PrayerAudioScreen(c, alerts, setOf(PrayerEvent.FAJR, PrayerEvent.MAGHRIB), iqama, silence) {}
            }
        }
        compose.waitForIdle()
        val node = compose.onNodeWithText(header).performScrollTo().fetchSemanticsNode()
        val scroller = compose.onNode(hasScrollAction())
        val top = scroller.fetchSemanticsNode().boundsInRoot.top
        val dy = node.boundsInRoot.top - top - 8 * activity.resources.displayMetrics.density
        scroller.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, dy) }
        settle()
        compose.onRoot().captureRoboImage(out(name))
    }

    /** Silent during prayer, on, with Do Not Disturb access granted and Fajr left out. */
    @Test fun shot8Silence() {
        launch()
        org.robolectric.Shadows.shadowOf(activity.getSystemService(android.app.NotificationManager::class.java)).setNotificationPolicyAccessGranted(true)
        prayerAudio(
            string(R.string.silence_section),
            AlertSettings(adhan = true), IqamaSettings(),
            SilenceSettings(enabled = true).with(PrayerEvent.FAJR, false), "08-silence",
        )
    }
}
