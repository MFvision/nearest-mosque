package sa.zood.nearmosque.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import sa.zood.nearmosque.MainActivity
import sa.zood.nearmosque.R
import sa.zood.nearmosque.container
import sa.zood.nearmosque.core.PrayerEvent
import sa.zood.nearmosque.data.AppSettings
import sa.zood.nearmosque.ui.Format
import java.time.Duration

/** Widget looks (chosen when the widget is added, changeable by reconfiguring it). */
enum class WidgetStyle(val background: Int, val ink: Int, val secondary: Int, val accent: Int, val row: Int, val label: Int) {
    CREAM(R.drawable.widget_bg_cream, 0xFF1F3B57.toInt(), 0xFF5B6470.toInt(), 0xFF8A6A1C.toInt(), R.drawable.widget_row_light, R.string.style_cream),
    GREEN(R.drawable.widget_bg_green, 0xFFFFF8EC.toInt(), 0xFFCFE3D6.toInt(), 0xFFE7B65A.toInt(), R.drawable.widget_row_dark, R.string.style_green),
    NIGHT(R.drawable.widget_bg_night, 0xFFFFFFFF.toInt(), 0xBFFFFFFF.toInt(), 0xFFD4A843.toInt(), R.drawable.widget_row_dark, R.string.style_night),
}

/**
 * Home-screen widgets: the next prayer with a live countdown (small) and today's five prayers (medium).
 * Times come from the city and method chosen in the app, on the phone. Each redraw schedules the next
 * one just after the coming prayer; the countdown ticks by itself (Chronometer).
 */
object PrayerWidgets {
    const val ACTION_REFRESH = "sa.zood.nearmosque.widgets.REFRESH"
    private val rows = listOf(R.id.row0, R.id.row1, R.id.row2, R.id.row3, R.id.row4)
    private val rowNames = listOf(R.id.row0_name, R.id.row1_name, R.id.row2_name, R.id.row3_name, R.id.row4_name)
    private val rowTimes = listOf(R.id.row0_time, R.id.row1_time, R.id.row2_time, R.id.row3_time, R.id.row4_time)

    private fun prefs(c: Context) = c.getSharedPreferences("widgets", Context.MODE_PRIVATE)
    fun style(c: Context, id: Int): WidgetStyle = WidgetStyle.entries.getOrElse(prefs(c).getInt("style_$id", 0)) { WidgetStyle.CREAM }
    fun setStyle(c: Context, id: Int, s: WidgetStyle) = prefs(c).edit().putInt("style_$id", s.ordinal).apply()
    fun forget(c: Context, ids: IntArray) = prefs(c).edit().apply { ids.forEach { remove("style_$it") } }.apply()

    /** Redraws every widget now (off the main thread). */
    fun refresh(context: Context, done: () -> Unit = {}) {
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val s = app.container.settings.settings.first()
                draw(app, s)
            } finally {
                done()
            }
        }
    }

    private fun draw(context: Context, s: AppSettings) {
        val m = AppWidgetManager.getInstance(context)
        var next: java.time.Instant? = null
        for ((cls, medium) in listOf(PrayerWidgetSmall::class.java to false, PrayerWidgetMedium::class.java to true)) {
            for (id in m.getAppWidgetIds(ComponentName(context, cls))) {
                val (v, at) = views(context, context.container, s, medium, style(context, id))
                next = at
                m.updateAppWidget(id, v)
            }
        }
        next?.let { scheduleNext(context, it.toEpochMilli() + 1_000) }
    }

    /** The widget's views for [s] at the container's clock, and the time of the next prayer. */
    fun views(context: Context, c: sa.zood.nearmosque.AppContainer, s: AppSettings, medium: Boolean, st: WidgetStyle): Pair<RemoteViews, java.time.Instant?> {
        val now = c.clock()
        val loc = s.prayerLocation
        val days = loc?.let { l ->
            val today = now.atZone(l.zoneId).toLocalDate()
            (-1L..1L).map { c.calculator.schedule(l.location, today.plusDays(it), l.zoneId, s.prayer) }
        }.orEmpty()
        val next = if (days.isEmpty()) null else c.calculator.nextPrayer(days, now)
        val v = RemoteViews(context.packageName, if (medium) R.layout.widget_medium else R.layout.widget_small)
        v.setInt(R.id.widget_root, "setBackgroundResource", st.background)
        v.setInt(R.id.widget_mark, "setColorFilter", st.accent)
        v.setTextColor(R.id.widget_city, st.secondary)
        v.setTextColor(R.id.widget_name, st.accent)
        v.setTextColor(R.id.widget_time, st.ink)
        v.setTextColor(R.id.widget_countdown, st.secondary)
        if (loc == null || next == null) {
            v.setTextViewText(R.id.widget_city, "")
            v.setTextViewText(R.id.widget_name, context.getString(R.string.widget_choose_city))
            v.setViewVisibility(R.id.widget_time, View.GONE)
            v.setViewVisibility(R.id.widget_countdown, View.GONE)
        } else {
            v.setTextViewText(R.id.widget_city, loc.name)
            v.setTextViewText(R.id.widget_name, context.getString(Format.prayerName(next.event)))
            v.setTextViewText(R.id.widget_time, Format.time(context, next.at, loc.zoneId))
            val left = Duration.between(now, next.at).toMillis().coerceAtLeast(0)
            v.setChronometer(R.id.widget_countdown, SystemClock.elapsedRealtime() + left, null, true)
            v.setChronometerCountDown(R.id.widget_countdown, true)
            if (medium) {
                val today = days[1]
                PrayerEvent.prayers.forEachIndexed { i, e ->
                    val at = today[e]
                    val isNext = !next.isTomorrow && next.event == e
                    v.setTextViewText(rowNames[i], context.getString(Format.prayerName(e)))
                    v.setTextViewText(rowTimes[i], at?.let { Format.time(context, it, loc.zoneId) } ?: "—")
                    val color = if (isNext) st.accent else st.ink
                    v.setTextColor(rowNames[i], color)
                    v.setTextColor(rowTimes[i], color)
                    v.setInt(rows[i], "setBackgroundResource", if (isNext) st.row else R.drawable.widget_row_none)
                }
            }
        }
        val open = Intent(Intent.ACTION_VIEW, Uri.parse("nearmosque://prayer"), context, MainActivity::class.java)
        v.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        return v to next?.at
    }

    /** An inexact alarm just after the next prayer (no exact-alarm permission needed for widgets). */
    private fun scheduleNext(context: Context, atMillis: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            context, 7701, Intent(context, PrayerWidgetSmall::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        am.set(AlarmManager.RTC, atMillis, pi)
    }
}

open class PrayerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        PrayerWidgets.refresh(context) { pending.finish() }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == PrayerWidgets.ACTION_REFRESH) {
            val pending = goAsync()
            PrayerWidgets.refresh(context) { pending.finish() }
        } else {
            super.onReceive(context, intent)
        }
    }

    override fun onDeleted(context: Context, ids: IntArray) = PrayerWidgets.forget(context, ids)
}

/** Next prayer with a countdown. */
class PrayerWidgetSmall : PrayerWidgetProvider()

/** Next prayer and today's times. */
class PrayerWidgetMedium : PrayerWidgetProvider()
