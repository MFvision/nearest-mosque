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
 * the mosques). Output: app/build/store-play/<appLang>/<device>/NN-name.png, flattened and checked afterwards by
 * tools/store/play_graphics.py (which takes phone, seven and ten; the other devices are for checking layouts).
 *
 * Left out of ordinary test runs: Gradle runs it only with -PstoreShots=true; -PstoreLangs="en ar" for some
 * languages, -PstoreDevices="phone cover" for other devices than phone, seven and ten, -PstoreClock=12 or 24 to
 * force the phone's clock (e.g. to check the 12-hour times of every language). For example:
 * ./gradlew :app:testDebugUnitTest -PstoreShots=true -Proborazzi.test.record=true --tests '*StoreScreenshotTest'
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w432dp-h768dp-400dpi", application = android.app.Application::class)
class StoreScreenshotTest(private val lang: String, private val qualifier: String, private val device: Device) {
    /** The screens rendered: Google Play's phone, 7-inch and 10-inch slots, and other sizes to check layouts on. */
    enum class Device(val qualifiers: String) {
        /** 1080x1920, 9:16: a large phone. */
        PHONE("w432dp-h768dp-400dpi"),
        /** Galaxy Z Fold inner screen, unfolded: 1811x2176 (Play's 7-inch slot). */
        SEVEN("w690dp-h829dp-420dpi"),
        /** A 10-inch tablet held sideways, its app window: 2560x1440, 16:9 (Play's 10-inch slot). */
        TEN("w1280dp-h720dp-xhdpi"),
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
                .map { d -> Device.entries.first { it.folder == d } }.ifEmpty { listOf(Device.PHONE, Device.SEVEN, Device.TEN) }
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

    private fun out(name: String) = "build/store-play/$lang/${device.folder}/$name.png"

    /** The language's resources, then a fresh activity in it (its layout direction follows). */
    private fun launch() {
        RuntimeEnvironment.setQualifiers("+$qualifier-${device.qualifiers}")
        android.provider.Settings.Global.putFloat(RuntimeEnvironment.getApplication().contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        if (clock != null) android.provider.Settings.System.putString(RuntimeEnvironment.getApplication().contentResolver, android.provider.Settings.System.TIME_12_24, clock)
        scenario = ActivityScenario.launch(ComponentActivity::class.java).also { s -> s.onActivity { activity = it } }
    }

    @After fun close() { scenario?.close() }

    private fun string(id: Int, vararg args: Any): String = activity.getString(id, *args)

    private fun container(location: PrayerLocation = riyadh, method: Method = Method.UMM_AL_QURA): AppContainer = runBlocking {
        val c = AppContainer(
            activity, inMemoryDb = true, clock = { at },
            settingsFile = File.createTempFile("settings", ".preferences_pb").also { it.delete() },
            online = sa.zood.nearmosque.data.OnlineMosqueSource { _, _ -> emptyList() },
        )
        c.packs.ensureBuiltins { !sa.zood.nearmosque.data.ChunkScope.isLibrary(it.id) }
        c.start(installLibraries = false)
        c.settings.setPrayerLocation(location, follow = false)
        c.settings.setMethod(method, byUser = false)
        c.settings.setReminder(PrayerEvent.FAJR, true)
        c.settings.setReminder(PrayerEvent.MAGHRIB, true)
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

    /** The Qibla compass, facing the Qibla (the light brightens and the arrow points straight up). */
    @Test fun shot2Qibla() {
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
        compose.onRoot().captureRoboImage(out("02-qibla"))
    }

    /** The mosque compass around Cairo (the MapLibre map does not draw under Robolectric) and the nearest ones. */
    @Test fun shot3Mosques() {
        launch()
        app(container(cairo, Method.EGYPTIAN), Tab.MOSQUES)
        // The area's mosques come from the Cairo pack on a background thread.
        waitForText(string(R.string.nearest_known_mosque), 30_000)
        settle()
        compose.onRoot().captureRoboImage(out("03-mosques"))
    }

    /** Ask: the question and the start of the common answer on the Quran as a miracle; the Ask screen where there is none. */
    @Test fun shot4Ask() {
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
        compose.onRoot().captureRoboImage(out("04-ask"))
    }

    /** The library's classical books (opened from Ask: library, browse, classical books). */
    @OptIn(ExperimentalRoborazziApi::class)
    @Test fun shot5Library() {
        launch()
        app(container(), Tab.ASK)
        compose.onNodeWithContentDescription(string(R.string.library_title)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(string(R.string.library_browse)).performClick()
        waitForText(string(R.string.classics_title))
        compose.onNodeWithText(string(R.string.classics_title)).performClick()
        waitForText(string(R.string.classics_note))
        settle()
        captureScreenRoboImage(out("05-library"))
    }

    /** Home-screen widgets on a dusk wallpaper, drawn from their RemoteViews like WidgetScreenshotTest. */
    @Test fun shot6Widgets() = runBlocking {
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
        column.captureRoboImage(out("06-widgets"))
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

    @Test fun shot7Iqama() {
        launch()
        prayerAudio(
            string(R.string.iqama_section),
            AlertSettings(adhan = true, before10 = true, near = true),
            IqamaSettings().withWait(PrayerEvent.ISHA, 15), SilenceSettings(), "07-iqama",
        )
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
