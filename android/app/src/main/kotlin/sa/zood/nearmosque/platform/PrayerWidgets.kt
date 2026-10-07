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
import sa.zood.nearmosque.ui.prayer.prayerIcon
import java.time.Duration
import java.time.Instant

/** Widget looks (chosen when the widget is added, changeable by reconfiguring it). */
enum class WidgetStyle(
    val background: Int, val ink: Int, val secondary: Int, val accent: Int,
    /** Inner cards, chips and tiles; the highlighted row or tile and the text on it; the countdown bar. */
    val card: Int, val pill: Int, val onPill: Int, val progress: Int,
    val label: Int,
) {
    CREAM(R.drawable.widget_bg_cream, 0xFF1F3B57.toInt(), 0xFF5B6470.toInt(), 0xFF8A6A1C.toInt(),
        R.drawable.widget_card_light, R.drawable.widget_pill_cream, 0xFFFFFFFF.toInt(), R.id.progress_cream, R.string.style_cream),
    GREEN(R.drawable.widget_bg_green, 0xFFFFF8EC.toInt(), 0xFFCFE3D6.toInt(), 0xFFE7B65A.toInt(),
        R.drawable.widget_card_green, R.drawable.widget_pill_green, 0xFF0F3D2E.toInt(), R.id.progress_green, R.string.style_green),
    NIGHT(R.drawable.widget_bg_night, 0xFFFFFFFF.toInt(), 0xBFFFFFFF.toInt(), 0xFFD4A843.toInt(),
        R.drawable.widget_card_night, R.drawable.widget_pill_night, 0xFF081B29.toInt(), R.id.progress_night, R.string.style_night),
}

/** The widgets: next prayer (small, medium, large), the countdown bar, and today's prayers (medium, large). */
enum class WidgetKind(val layout: Int) {
    SMALL(R.layout.widget_small), MEDIUM(R.layout.widget_medium), LARGE(R.layout.widget_large),
    COUNTDOWN(R.layout.widget_countdown), TODAY(R.layout.widget_today), TODAY_LARGE(R.layout.widget_today_large),
}

/**
 * Home-screen widgets. Times come from the city and method chosen in the app, on the phone. Each prayer
 * shows its icon and a bell for its reminder; the dates are the weekday, Gregorian and Hijri. Each redraw
 * schedules the next one (after the coming prayer, at midnight, and every half hour while a countdown bar
 * is shown); the countdowns tick by themselves (Chronometer).
 */
object PrayerWidgets {
    const val ACTION_REFRESH = "sa.zood.nearmosque.widgets.REFRESH"
    private val providers = listOf(
        PrayerWidgetSmall::class.java to WidgetKind.SMALL, PrayerWidgetMedium::class.java to WidgetKind.MEDIUM,
        PrayerWidgetLarge::class.java to WidgetKind.LARGE, PrayerWidgetCountdown::class.java to WidgetKind.COUNTDOWN,
        PrayerWidgetToday::class.java to WidgetKind.TODAY, PrayerWidgetTodayLarge::class.java to WidgetKind.TODAY_LARGE,
    )
    private val rows = listOf(R.id.row0, R.id.row1, R.id.row2, R.id.row3, R.id.row4, R.id.row5)
    private val rowNames = listOf(R.id.row0_name, R.id.row1_name, R.id.row2_name, R.id.row3_name, R.id.row4_name, R.id.row5_name)
    private val rowTimes = listOf(R.id.row0_time, R.id.row1_time, R.id.row2_time, R.id.row3_time, R.id.row4_time, R.id.row5_time)
    private val rowIcons = listOf(R.id.row0_icon, R.id.row1_icon, R.id.row2_icon, R.id.row3_icon, R.id.row4_icon, R.id.row5_icon)
    private val rowBells = listOf(R.id.row0_bell, R.id.row1_bell, R.id.row2_bell, R.id.row3_bell, R.id.row4_bell, R.id.row5_bell)
    private val tiles = listOf(R.id.tile0, R.id.tile1, R.id.tile2, R.id.tile3, R.id.tile4, R.id.tile5)
    private val tileNames = listOf(R.id.tile0_name, R.id.tile1_name, R.id.tile2_name, R.id.tile3_name, R.id.tile4_name, R.id.tile5_name)
    private val tileTimes = listOf(R.id.tile0_time, R.id.tile1_time, R.id.tile2_time, R.id.tile3_time, R.id.tile4_time, R.id.tile5_time)
    private val tileMarks = listOf(R.id.tile0_mark, R.id.tile1_mark, R.id.tile2_mark, R.id.tile3_mark, R.id.tile4_mark, R.id.tile5_mark)
    private val tileIcons = listOf(R.id.tile0_icon, R.id.tile1_icon, R.id.tile2_icon, R.id.tile3_icon, R.id.tile4_icon, R.id.tile5_icon)
    private val tileBells = listOf(R.id.tile0_bell, R.id.tile1_bell, R.id.tile2_bell, R.id.tile3_bell, R.id.tile4_bell, R.id.tile5_bell)

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
        var next: Instant? = null
        var bar = false
        for ((cls, kind) in providers) {
            for (id in m.getAppWidgetIds(ComponentName(context, cls))) {
                val (v, at) = views(context, context.container, s, kind, style(context, id))
                next = at
                bar = bar || kind == WidgetKind.COUNTDOWN || kind == WidgetKind.LARGE
                m.updateAppWidget(id, v)
            }
        }
        val now = context.container.clock()
        val zone = s.prayerLocation?.zoneId
        val candidates = listOfNotNull(
            next?.plusSeconds(1),
            zone?.let { now.atZone(it).toLocalDate().plusDays(1).atStartOfDay(it).toInstant() },
            if (bar) now.plus(Duration.ofMinutes(30)) else null,
        )
        candidates.minOrNull()?.let { scheduleNext(context, it.toEpochMilli()) }
    }

    /** Text colour at 45% for times already passed. */
    private fun faded(color: Int) = (color and 0x00FFFFFF) or (0x73 shl 24)

    /** The widget's views for [s] at the container's clock, and the time of the next prayer. */
    fun views(context: Context, c: sa.zood.nearmosque.AppContainer, s: AppSettings, kind: WidgetKind, st: WidgetStyle): Pair<RemoteViews, Instant?> {
        val now = c.clock()
        val loc = s.prayerLocation
        val days = loc?.let { l ->
            val today = now.atZone(l.zoneId).toLocalDate()
            (-1L..1L).map { c.calculator.schedule(l.location, today.plusDays(it), l.zoneId, s.prayer) }
        }.orEmpty()
        val next = if (days.isEmpty()) null else c.calculator.nextPrayer(days, now)
        val v = RemoteViews(context.packageName, kind.layout)
        // Views a layout does not have are skipped by RemoteViews, so every widget is filled the same way.
        v.setInt(R.id.widget_root, "setBackgroundResource", st.background)
        v.setInt(R.id.widget_mark, "setColorFilter", st.accent)
        val open = Intent(Intent.ACTION_VIEW, Uri.parse("nearmosque://prayer"), context, MainActivity::class.java)
        v.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        if (loc == null || next == null) {
            v.setTextViewText(R.id.widget_name, context.getString(R.string.widget_choose_city))
            v.setTextColor(R.id.widget_name, st.ink)
            for (id in listOf(R.id.widget_time, R.id.widget_countdown, R.id.widget_bell, R.id.widget_icon, R.id.widget_day, R.id.widget_left_label,
                R.id.widget_title, st.progress, R.id.widget_timer, R.id.widget_weekday, R.id.widget_gregorian, R.id.widget_hijri, R.id.widget_pin,
                R.id.widget_city) + rows + tiles) {
                v.setViewVisibility(id, View.GONE)
            }
            return v to null
        }
        val zone = loc.zoneId
        val date = now.atZone(zone).toLocalDate()
        val today = days[1]
        val current = PrayerEvent.entries.lastOrNull { e -> today[e]?.let { !it.isAfter(now) } ?: false }
        fun isPast(e: PrayerEvent) = today[e]?.let { !it.isAfter(now) } == true && e != current
        val accentTitle = kind == WidgetKind.TODAY || kind == WidgetKind.TODAY_LARGE
        /** A prayer's reminder bell (none for sunrise), drawn on a highlighted background when [onPill]. */
        fun bell(id: Int, e: PrayerEvent, onPill: Boolean) {
            if (!e.isPrayer) { v.setViewVisibility(id, View.INVISIBLE); return }
            val on = e in s.reminders
            v.setImageViewResource(id, if (on) R.drawable.ic_bell else R.drawable.ic_bell_off)
            v.setInt(id, "setColorFilter", when { onPill -> st.onPill; on -> st.accent; else -> faded(st.secondary) })
            v.setContentDescription(id, context.getString(if (on) R.string.reminder_on_a11y else R.string.reminder_off_a11y, context.getString(Format.prayerName(e))))
        }

        // The next prayer: name and icon, time, reminder bell, the day, time left.
        v.setTextViewText(R.id.widget_name, context.getString(Format.prayerName(next.event)))
        v.setTextColor(R.id.widget_name, if (accentTitle) st.accent else st.ink)
        v.setImageViewResource(R.id.widget_icon, prayerIcon(next.event))
        v.setInt(R.id.widget_icon, "setColorFilter", st.accent)
        v.setTextViewText(R.id.widget_time, Format.time(context, next.at, zone))
        v.setTextColor(R.id.widget_time, st.ink)
        val on = next.event in s.reminders
        v.setImageViewResource(R.id.widget_bell, if (on) R.drawable.ic_bell else R.drawable.ic_bell_off)
        v.setInt(R.id.widget_bell, "setColorFilter", if (on) st.accent else st.secondary)
        v.setContentDescription(R.id.widget_bell, context.getString(if (on) R.string.reminder_on_a11y else R.string.reminder_off_a11y, context.getString(Format.prayerName(next.event))))
        val weekday = Format.weekday(context, date)
        v.setTextViewText(R.id.widget_day, if (kind == WidgetKind.COUNTDOWN) "$weekday, ${Format.dayMonth(context, date)}" else "$weekday · ${Format.dayMonth(context, date)}")
        v.setTextColor(R.id.widget_day, st.secondary)
        v.setTextViewText(R.id.widget_left_label, context.getString(R.string.widget_remaining))
        v.setTextColor(R.id.widget_left_label, if (accentTitle) st.accent else st.secondary)
        val left = Duration.between(now, next.at).toMillis().coerceAtLeast(0)
        v.setChronometer(R.id.widget_countdown, SystemClock.elapsedRealtime() + left, null, true)
        v.setChronometerCountDown(R.id.widget_countdown, true)
        v.setTextColor(R.id.widget_countdown, st.ink)
        if (kind == WidgetKind.MEDIUM || kind == WidgetKind.COUNTDOWN || kind == WidgetKind.LARGE) v.setInt(R.id.next_card, "setBackgroundResource", st.card)

        // Countdown bar.
        v.setTextViewText(R.id.widget_title, context.getString(R.string.next_prayer))
        v.setTextColor(R.id.widget_title, st.ink)
        v.setViewVisibility(st.progress, View.VISIBLE)
        val start = next.periodStart
        val fraction = if (start != null && next.at.isAfter(start)) Duration.between(start, now).toMillis().toDouble() / Duration.between(start, next.at).toMillis() else 0.0
        v.setProgressBar(st.progress, 1000, (fraction.coerceIn(0.0, 1.0) * 1000).toInt(), false)
        v.setInt(R.id.widget_timer, "setColorFilter", st.secondary)

        // Today: dates, city, chips and the five tiles.
        v.setTextViewText(R.id.widget_weekday, weekday)
        v.setTextColor(R.id.widget_weekday, st.accent)
        v.setTextViewText(R.id.widget_gregorian, Format.gregorianNumeric(context, date))
        v.setTextColor(R.id.widget_gregorian, st.ink)
        v.setTextViewText(R.id.widget_hijri, Format.hijriNumeric(context, date, s.prayer.hijriAdjustmentDays).orEmpty())
        v.setTextColor(R.id.widget_hijri, st.ink)
        v.setInt(R.id.widget_pin, "setColorFilter", st.accent)
        v.setTextViewText(R.id.widget_city, loc.name)
        v.setTextColor(R.id.widget_city, st.ink)
        if (accentTitle) {
            v.setInt(R.id.widget_time, "setBackgroundResource", st.card)
            v.setInt(R.id.widget_countdown, "setBackgroundResource", st.card)
        }
        // Five tiles (medium) or six with sunrise (large).
        val tileEvents = if (kind == WidgetKind.TODAY_LARGE) PrayerEvent.entries else PrayerEvent.prayers
        tileEvents.forEachIndexed { i, e ->
            val at = today[e]
            val isNext = !next.isTomorrow && next.event == e
            val color = when {
                isNext -> st.onPill
                isPast(e) -> faded(st.ink)
                else -> st.ink
            }
            val parts = at?.let { Format.timeParts(context, it, zone) }
            v.setTextViewText(tileNames[i], context.getString(Format.prayerName(e)))
            v.setTextViewText(tileTimes[i], parts?.first ?: "—")
            v.setTextViewText(tileMarks[i], parts?.second.orEmpty())
            v.setViewVisibility(tileMarks[i], if (parts?.second != null) View.VISIBLE else View.GONE)
            for (id in listOf(tileNames[i], tileTimes[i], tileMarks[i])) v.setTextColor(id, color)
            v.setInt(tiles[i], "setBackgroundResource", if (isNext) st.pill else st.card)
            v.setImageViewResource(tileIcons[i], prayerIcon(e))
            v.setInt(tileIcons[i], "setColorFilter", if (isNext) st.onPill else st.accent)
            bell(tileBells[i], e, isNext)
        }

        // Medium: the six times, past ones faded, the current period highlighted.
        PrayerEvent.entries.forEachIndexed { i, e ->
            val at = today[e]
            val now0 = current == e
            val color = when {
                now0 -> st.onPill
                isPast(e) -> faded(st.ink)
                else -> st.ink
            }
            v.setTextViewText(rowNames[i], context.getString(Format.prayerName(e)))
            v.setTextViewText(rowTimes[i], at?.let { Format.time(context, it, zone) } ?: "—")
            v.setTextColor(rowNames[i], color)
            v.setTextColor(rowTimes[i], color)
            v.setInt(rows[i], "setBackgroundResource", if (now0) st.pill else R.drawable.widget_row_none)
            v.setImageViewResource(rowIcons[i], prayerIcon(e))
            v.setInt(rowIcons[i], "setColorFilter", if (now0) st.onPill else if (isPast(e)) faded(st.accent) else st.accent)
            bell(rowBells[i], e, now0)
        }
        return v to next.at
    }

    /** An inexact alarm for the next redraw (no exact-alarm permission needed for widgets). */
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

/** Next prayer with its icon, bell, day and countdown. */
class PrayerWidgetSmall : PrayerWidgetProvider()

/** Next prayer and today's six times. */
class PrayerWidgetMedium : PrayerWidgetProvider()

/** Next prayer with the city, dates and bar, and today's six times with icons and bells. */
class PrayerWidgetLarge : PrayerWidgetProvider()

/** A bar that fills up until the next prayer. */
class PrayerWidgetCountdown : PrayerWidgetProvider()

/** Today's five prayers with the dates, the next prayer and the city. */
class PrayerWidgetToday : PrayerWidgetProvider()

/** Today's six times as tiles with icons and bells, with the dates, the next prayer and the city. */
class PrayerWidgetTodayLarge : PrayerWidgetProvider()
