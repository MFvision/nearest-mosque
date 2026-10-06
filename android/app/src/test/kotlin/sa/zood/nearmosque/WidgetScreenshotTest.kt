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
import sa.zood.nearmosque.platform.WidgetStyle
import java.time.ZoneId
import java.time.ZonedDateTime

/** The home-screen widgets in each look, drawn from their RemoteViews (build/screenshots/widget_*.png). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi", sdk = [35], application = android.app.Application::class)
class WidgetScreenshotTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun shoot(name: String, medium: Boolean, style: WidgetStyle) = runBlocking {
        val c = AppContainer(
            context, inMemoryDb = true,
            clock = { ZonedDateTime.of(2026, 10, 3, 14, 10, 0, 0, ZoneId.of("Africa/Johannesburg")).toInstant() },
            settingsFile = java.io.File.createTempFile("settings", ".preferences_pb").also { it.delete() },
        )
        c.settings.setPrayerLocation(
            PrayerLocation("Cape Town", LatLng(-33.92584, 18.42322), ZoneId.of("Africa/Johannesburg"), "ZA", PrayerLocation.Source.CITY), follow = false,
        )
        c.settings.setMethod(Method.MUSLIM_WORLD_LEAGUE, byUser = false)
        val (views, _) = PrayerWidgets.views(context, c, c.settings.settings.first(), medium, style)
        val density = context.resources.displayMetrics.density
        val w = ((if (medium) 330 else 160) * density).toInt()
        val h = (160 * density).toInt()
        val activity = org.robolectric.Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).setup().get()
        val parent = FrameLayout(activity).apply { setBackgroundColor(0xFFC8C8CD.toInt()); setPadding(24, 24, 24, 24) }
        val v = views.apply(activity, parent)
        parent.addView(v, FrameLayout.LayoutParams(w, h))
        activity.setContentView(parent, android.view.ViewGroup.LayoutParams(w + 48, h + 48))
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        parent.captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun smallCream() = shoot("widget_small_cream", false, WidgetStyle.CREAM)
    @Test fun smallGreen() = shoot("widget_small_green", false, WidgetStyle.GREEN)
    @Test fun mediumCream() = shoot("widget_medium_cream", true, WidgetStyle.CREAM)
    @Test fun mediumGreen() = shoot("widget_medium_green", true, WidgetStyle.GREEN)
    @Test fun mediumNight() = shoot("widget_medium_night", true, WidgetStyle.NIGHT)

    @Test @Config(qualifiers = "ar-w393dp-h852dp-xxhdpi")
    fun mediumArabic() = shoot("widget_medium_cream_ar", true, WidgetStyle.CREAM)
}
