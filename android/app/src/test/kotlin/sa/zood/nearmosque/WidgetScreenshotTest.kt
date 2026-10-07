package sa.zood.nearmosque

import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sa.zood.nearmosque.core.LatLng
import sa.zood.nearmosque.core.Method
import sa.zood.nearmosque.data.PrayerLocation
import sa.zood.nearmosque.platform.PrayerWidgets
import sa.zood.nearmosque.platform.WidgetExtras
import sa.zood.nearmosque.platform.WidgetKind
import sa.zood.nearmosque.platform.WidgetMosque
import sa.zood.nearmosque.platform.WidgetStyle
import java.time.ZoneId
import java.time.ZonedDateTime

/** The home-screen widgets in each look, drawn from their RemoteViews (build/screenshots/widget_*.png). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi", sdk = [35], application = android.app.Application::class)
class WidgetScreenshotTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun shoot(name: String, kind: WidgetKind, style: WidgetStyle, extras: WidgetExtras = WidgetExtras()) = runBlocking {
        val c = AppContainer(
            context, inMemoryDb = true,
            clock = { ZonedDateTime.of(2026, 10, 3, 14, 10, 0, 0, ZoneId.of("Africa/Johannesburg")).toInstant() },
            settingsFile = java.io.File.createTempFile("settings", ".preferences_pb").also { it.delete() },
        )
        c.settings.setPrayerLocation(
            PrayerLocation("Cape Town", LatLng(-33.92584, 18.42322), ZoneId.of("Africa/Johannesburg"), "ZA", PrayerLocation.Source.CITY), follow = false,
        )
        c.settings.setMethod(Method.MUSLIM_WORLD_LEAGUE, byUser = false)
        c.settings.setReminder(sa.zood.nearmosque.core.PrayerEvent.FAJR, true)
        c.settings.setReminder(sa.zood.nearmosque.core.PrayerEvent.MAGHRIB, true)
        val (views, _) = PrayerWidgets.views(context, c, c.settings.settings.first(), kind, style, extras)
        val density = context.resources.displayMetrics.density
        val w = ((if (kind in setOf(WidgetKind.SMALL, WidgetKind.MOSQUE_SMALL, WidgetKind.ASK_SMALL)) 170 else 340) * density).toInt()
        val h = (when (kind) { WidgetKind.LARGE, WidgetKind.TODAY_LARGE -> 380; WidgetKind.TODAY -> 180; else -> 170 } * density).toInt()
        val activity = org.robolectric.Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).setup().get()
        val parent = FrameLayout(activity).apply { setBackgroundColor(0xFFC8C8CD.toInt()); setPadding(24, 24, 24, 24) }
        val v = views.apply(activity, parent)
        parent.addView(v, FrameLayout.LayoutParams(w, h))
        activity.setContentView(parent, android.view.ViewGroup.LayoutParams(w + 48, h + 48))
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        parent.captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun smallCream() = shoot("widget_small_cream", WidgetKind.SMALL, WidgetStyle.CREAM)
    @Test fun smallGreen() = shoot("widget_small_green", WidgetKind.SMALL, WidgetStyle.GREEN)
    @Test fun mediumCream() = shoot("widget_medium_cream", WidgetKind.MEDIUM, WidgetStyle.CREAM)
    @Test fun mediumGreen() = shoot("widget_medium_green", WidgetKind.MEDIUM, WidgetStyle.GREEN)
    @Test fun mediumNight() = shoot("widget_medium_night", WidgetKind.MEDIUM, WidgetStyle.NIGHT)
    @Test fun countdownGreen() = shoot("widget_countdown_green", WidgetKind.COUNTDOWN, WidgetStyle.GREEN)
    @Test fun countdownCream() = shoot("widget_countdown_cream", WidgetKind.COUNTDOWN, WidgetStyle.CREAM)
    @Test fun todayNight() = shoot("widget_today_night", WidgetKind.TODAY, WidgetStyle.NIGHT)
    @Test fun largeCream() = shoot("widget_large_cream", WidgetKind.LARGE, WidgetStyle.CREAM)
    @Test fun largeNight() = shoot("widget_large_night", WidgetKind.LARGE, WidgetStyle.NIGHT)
    @Test fun todayLargeGreen() = shoot("widget_today_large_green", WidgetKind.TODAY_LARGE, WidgetStyle.GREEN)
    @Test @Config(qualifiers = "ar-w393dp-h852dp-xxhdpi")
    fun todayLargeArabic() = shoot("widget_today_large_cream_ar", WidgetKind.TODAY_LARGE, WidgetStyle.CREAM)
    @Test fun todayGreen() = shoot("widget_today_green", WidgetKind.TODAY, WidgetStyle.GREEN)

    @Test @Config(qualifiers = "ar-w393dp-h852dp-xxhdpi")
    fun mediumArabic() = shoot("widget_medium_cream_ar", WidgetKind.MEDIUM, WidgetStyle.CREAM)

    @Test @Config(qualifiers = "ar-w393dp-h852dp-xxhdpi")
    fun todayArabic() = shoot("widget_today_cream_ar", WidgetKind.TODAY, WidgetStyle.CREAM)

    @Test @Config(qualifiers = "ar-w393dp-h852dp-xxhdpi")
    fun countdownArabic() = shoot("widget_countdown_night_ar", WidgetKind.COUNTDOWN, WidgetStyle.NIGHT)

    private val sampleMosques = WidgetExtras(
        mosques = listOf(
            WidgetMosque("way/1", "Masjid Al-Noor", 420.0, 35.0),
            WidgetMosque("way/2", "Auwal Mosque", 1250.0, 200.0),
            WidgetMosque("way/3", "Palm Tree Mosque", 2900.0, 290.0),
        ),
        questions = listOf("How do I pray Witr?", "What breaks the fast?", "How is zakat calculated?", "Can I combine prayers when travelling?"),
    )

    @Test fun mosqueSmallCream() = shoot("widget_mosque_small_cream", WidgetKind.MOSQUE_SMALL, WidgetStyle.CREAM, sampleMosques)
    @Test fun mosqueGreen() = shoot("widget_mosque_green", WidgetKind.MOSQUE, WidgetStyle.GREEN, sampleMosques)
    @Test fun mosqueEmpty() = shoot("widget_mosque_empty_night", WidgetKind.MOSQUE, WidgetStyle.NIGHT)
    @Test fun askSmallNight() = shoot("widget_ask_small_night", WidgetKind.ASK_SMALL, WidgetStyle.NIGHT, sampleMosques)
    @Test fun askCream() = shoot("widget_ask_cream", WidgetKind.ASK, WidgetStyle.CREAM, sampleMosques)

    @Test @Config(qualifiers = "ar-w393dp-h852dp-xxhdpi")
    fun askArabic() = shoot("widget_ask_green_ar", WidgetKind.ASK, WidgetStyle.GREEN,
        sampleMosques.copy(questions = listOf("كيف أصلي الوتر؟", "ما مفطرات الصيام؟")))
}
