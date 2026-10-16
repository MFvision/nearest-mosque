package sa.zood.nearmosque

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sa.zood.nearmosque.core.LatLng
import sa.zood.nearmosque.core.Method
import sa.zood.nearmosque.data.PrayerLocation
import sa.zood.nearmosque.platform.PrayerWidgets
import sa.zood.nearmosque.platform.WidgetKind
import sa.zood.nearmosque.platform.WidgetStyle
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Every prayer time in the widgets fits on its line, uncut, in every app language with the 12-hour clock: some
 * languages' AM/PM mark is a long word (Assamese "অপৰাহ্ন", Kannada "ಅಪರಾಹ್ನ"), which used to push the time out
 * of the small widget.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi", sdk = [35], application = android.app.Application::class)
class WidgetTimeFitTest {
    private val timeIds = listOf(
        R.id.widget_time,
        R.id.row0_time, R.id.row1_time, R.id.row2_time, R.id.row3_time, R.id.row4_time, R.id.row5_time,
        R.id.tile0_time, R.id.tile1_time, R.id.tile2_time, R.id.tile3_time, R.id.tile4_time, R.id.tile5_time,
        R.id.tile0_mark, R.id.tile1_mark, R.id.tile2_mark, R.id.tile3_mark, R.id.tile4_mark, R.id.tile5_mark,
    )
    private val kinds = listOf(WidgetKind.SMALL, WidgetKind.MEDIUM, WidgetKind.LARGE, WidgetKind.COUNTDOWN, WidgetKind.TODAY, WidgetKind.TODAY_LARGE)

    /** The enabled languages' resource qualifiers (shared/i18n/languages.json). */
    private fun qualifiers(): Map<String, String> {
        val root = File(System.getProperty("nm.root") ?: "../..")
        return Json.parseToJsonElement(File(root, "shared/i18n/languages.json").readText()).jsonObject["languages"]!!.jsonArray
            .map { it.jsonObject }.filter { it["enabled"]!!.jsonPrimitive.boolean }
            .associate { it["code"]!!.jsonPrimitive.content to it["android"]!!.jsonPrimitive.content.removePrefix("values").removePrefix("-").ifEmpty { "en" } }
    }

    private fun timeViews(v: View, out: MutableList<TextView> = mutableListOf()): List<TextView> {
        if (v is TextView && v.id in timeIds && v.visibility == View.VISIBLE && v.text.isNotEmpty()) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) timeViews(v.getChildAt(i), out)
        return out
    }

    @Test fun timesFitInEveryLanguage() = runBlocking {
        val zone = ZoneId.of("Asia/Riyadh")
        // 14:10: the next prayer is Asr, in the afternoon (the longest marks).
        val at = ZonedDateTime.of(2026, 10, 3, 14, 10, 0, 0, zone).toInstant()
        val problems = mutableListOf<String>()
        for ((lang, q) in qualifiers()) {
            RuntimeEnvironment.setQualifiers("+$q")
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            android.provider.Settings.System.putString(context.contentResolver, android.provider.Settings.System.TIME_12_24, "12")
            val c = AppContainer(context, inMemoryDb = true, clock = { at }, settingsFile = File.createTempFile("settings", ".preferences_pb").also { it.delete() })
            c.settings.setPrayerLocation(PrayerLocation("Riyadh", LatLng(24.7136, 46.6753), zone, "SA", PrayerLocation.Source.CITY), follow = false)
            c.settings.setMethod(Method.UMM_AL_QURA, byUser = false)
            val s = c.settings.settings.first()
            val activity = org.robolectric.Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).setup().get()
            val density = activity.resources.displayMetrics.density
            for (kind in kinds) {
                val (views, _) = PrayerWidgets.views(activity, c, s, kind, WidgetStyle.CREAM)
                // The sizes WidgetScreenshotTest draws (the launcher's usual cells).
                val w = ((if (kind == WidgetKind.SMALL) 170 else 340) * density).toInt()
                val h = (when (kind) { WidgetKind.LARGE, WidgetKind.TODAY_LARGE -> 380; WidgetKind.TODAY -> 180; else -> 170 } * density).toInt()
                val parent = FrameLayout(activity)
                parent.addView(views.apply(activity, parent), FrameLayout.LayoutParams(w, h))
                activity.setContentView(parent, ViewGroup.LayoutParams(w, h))
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                for (tv in timeViews(parent)) {
                    val layout = tv.layout ?: continue
                    val room = tv.width - tv.totalPaddingLeft - tv.totalPaddingRight
                    val why = when {
                        layout.lineCount > 1 -> "wraps"
                        layout.getEllipsisCount(0) > 0 -> "ellipsized"
                        layout.getLineWidth(0) > room + 1 -> "${layout.getLineWidth(0).toInt()} px wide in $room px"
                        else -> null
                    }
                    if (why != null) problems += "$lang ${kind.name} ${activity.resources.getResourceEntryName(tv.id)}: \"${tv.text}\" $why"
                }
            }
        }
        assertTrue("Times cut off:\n" + problems.joinToString("\n"), problems.isEmpty())
    }
}
