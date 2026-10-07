package sa.zood.nearmosque.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
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
import sa.zood.nearmosque.core.Qibla
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

/** The widgets: next prayer (small, medium, large), the countdown bar, the ring (small, medium, large), today's prayers
 *  (medium, large), the nearest mosques and Ask (small, medium). */
enum class WidgetKind(val layout: Int) {
    SMALL(R.layout.widget_small), MEDIUM(R.layout.widget_medium), LARGE(R.layout.widget_large),
    COUNTDOWN(R.layout.widget_countdown),
    RING_SMALL(R.layout.widget_ring_small), RING_MEDIUM(R.layout.widget_ring_medium), RING(R.layout.widget_ring),
    TODAY(R.layout.widget_today), TODAY_LARGE(R.layout.widget_today_large),
    MOSQUE_SMALL(R.layout.widget_mosque_small), MOSQUE(R.layout.widget_mosque), ASK_SMALL(R.layout.widget_ask_small), ASK(R.layout.widget_ask),
    QIBLA_SMALL(R.layout.widget_qibla_small), QIBLA(R.layout.widget_qibla), ACTIONS(R.layout.widget_actions),
    ;

    val isRing get() = this == RING_SMALL || this == RING_MEDIUM || this == RING
}

/** One of the nearest mosques: distance and direction (degrees from north) from where the phone or the prayer city is. */
data class WidgetMosque(val id: String, val name: String, val meters: Double, val bearing: Double)

/** What the mosque and Ask widgets show besides prayer times. */
data class WidgetExtras(val mosques: List<WidgetMosque> = emptyList(), val questions: List<String> = emptyList()) {
    /** Two suggested questions for today (they change every day). */
    fun todaysQuestions(day: Long): List<String> =
        if (questions.isEmpty()) emptyList() else listOf(questions[((2 * day) % questions.size).toInt()], questions[((2 * day + 1) % questions.size).toInt()]).distinct()

    companion object {
        suspend fun load(c: sa.zood.nearmosque.AppContainer, s: AppSettings, lang: String): WidgetExtras {
            val center = c.devicePosition.value?.location ?: s.prayerLocation?.location
            val mosques = center?.let { at ->
                (runCatching { c.mosques.nearest(at, 25_000.0, lang) }.getOrNull() as? sa.zood.nearmosque.data.MosqueResult.Found)?.items?.take(3)?.map { r ->
                    WidgetMosque(r.mosque.sourceId, r.mosque.displayName(lang) ?: "", r.distanceMeters, sa.zood.nearmosque.core.Geo.initialBearing(at, r.mosque.location))
                }
            }.orEmpty()
            val questions = runCatching { c.ask.commonQuestions() }.getOrDefault(emptyList()).mapNotNull { it.question[lang] ?: it.question["en"] }
            return WidgetExtras(mosques, questions)
        }
    }
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
        PrayerWidgetRingSmall::class.java to WidgetKind.RING_SMALL, PrayerWidgetRingMedium::class.java to WidgetKind.RING_MEDIUM,
        PrayerWidgetRing::class.java to WidgetKind.RING,
        PrayerWidgetToday::class.java to WidgetKind.TODAY, PrayerWidgetTodayLarge::class.java to WidgetKind.TODAY_LARGE,
        MosqueWidgetSmall::class.java to WidgetKind.MOSQUE_SMALL, MosqueWidget::class.java to WidgetKind.MOSQUE,
        AskWidgetSmall::class.java to WidgetKind.ASK_SMALL, AskWidget::class.java to WidgetKind.ASK,
        QiblaWidgetSmall::class.java to WidgetKind.QIBLA_SMALL, QiblaWidget::class.java to WidgetKind.QIBLA,
        ActionsWidget::class.java to WidgetKind.ACTIONS,
    )
    private val dirs = listOf(R.drawable.ic_dir_0, R.drawable.ic_dir_1, R.drawable.ic_dir_2, R.drawable.ic_dir_3,
        R.drawable.ic_dir_4, R.drawable.ic_dir_5, R.drawable.ic_dir_6, R.drawable.ic_dir_7)
    private val mqRows = listOf(R.id.mqrow0, R.id.mqrow1, R.id.mqrow2)
    private val mqNames = listOf(R.id.mqrow0_name, R.id.mqrow1_name, R.id.mqrow2_name)
    private val mqDirs = listOf(R.id.mqrow0_dir, R.id.mqrow1_dir, R.id.mqrow2_dir)
    private val mqDists = listOf(R.id.mqrow0_dist, R.id.mqrow1_dist, R.id.mqrow2_dist)
    private val mqIcons = listOf(R.id.mqrow0_icon, R.id.mqrow1_icon, R.id.mqrow2_icon)
    private val askRows = listOf(R.id.askq0, R.id.askq1)
    private val askTexts = listOf(R.id.askq0_text, R.id.askq1_text)
    private val askIcons = listOf(R.id.askq0_icon, R.id.askq1_icon)
    private val askGos = listOf(R.id.askq0_go, R.id.askq1_go)
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

    private suspend fun draw(context: Context, s: AppSettings) {
        val m = AppWidgetManager.getInstance(context)
        val extras = WidgetExtras.load(context.container, s, Format.languageCode(context))
        var next: Instant? = null
        var bar = false
        var ring = false
        for ((cls, kind) in providers) {
            for (id in m.getAppWidgetIds(ComponentName(context, cls))) {
                val (v, at) = views(context, context.container, s, kind, style(context, id), extras)
                // Mosque, Ask, Qibla and quick actions have no prayer time: keep the one a prayer widget gave.
                if (at != null) next = at
                bar = bar || kind == WidgetKind.COUNTDOWN || kind == WidgetKind.LARGE
                ring = ring || kind.isRing
                m.updateAppWidget(id, v)
            }
        }
        val now = context.container.clock()
        val zone = s.prayerLocation?.zoneId
        val candidates = listOfNotNull(
            next?.plusSeconds(1),
            zone?.let { now.atZone(it).toLocalDate().plusDays(1).atStartOfDay(it).toInstant() },
            if (bar) now.plus(Duration.ofMinutes(30)) else null,
            // The ring is a picture drawn here, so it moves on every 10 minutes.
            if (ring) now.plus(Duration.ofMinutes(10)) else null,
        )
        candidates.minOrNull()?.let { scheduleNext(context, it.toEpochMilli()) }
    }

    /** Text colour at 45% for times already passed. */
    private fun faded(color: Int) = (color and 0x00FFFFFF) or (0x73 shl 24)

    /** The widget's views for [s] at the container's clock, and the time of the next prayer. */
    fun views(
        context: Context, c: sa.zood.nearmosque.AppContainer, s: AppSettings, kind: WidgetKind, st: WidgetStyle,
        extras: WidgetExtras = WidgetExtras(),
    ): Pair<RemoteViews, Instant?> {
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
        v.setOnClickPendingIntent(R.id.widget_root, link(context, 0, "nearmosque://prayer"))
        when (kind) {
            WidgetKind.MOSQUE_SMALL, WidgetKind.MOSQUE -> { mosqueViews(context, v, s, kind, st, extras); return v to null }
            WidgetKind.ASK_SMALL, WidgetKind.ASK -> { askViews(context, v, kind, st, extras, now); return v to null }
            WidgetKind.QIBLA_SMALL, WidgetKind.QIBLA -> { qiblaViews(context, v, s, kind, st); return v to null }
            WidgetKind.ACTIONS -> { actionsViews(context, v, st); return v to null }
            else -> Unit
        }
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
        if (kind.isRing) {
            val px = ((if (kind == WidgetKind.RING) 260 else 150) * context.resources.displayMetrics.density).toInt()
            v.setImageViewBitmap(R.id.ring_image, ringBitmap(st, fraction.coerceIn(0.0, 1.0), px))
            v.setTextViewText(R.id.widget_left_label, context.getString(R.string.widget_until))
            v.setTextColor(R.id.widget_left_label, st.secondary)
        }

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

    /** North-up, from the prayer city: widgets cannot read the compass, so a tap opens the live compass. */
    private fun qiblaViews(context: Context, v: RemoteViews, s: AppSettings, kind: WidgetKind, st: WidgetStyle) {
        v.setOnClickPendingIntent(R.id.widget_root, link(context, 20, "nearmosque://qibla"))
        val loc = s.prayerLocation
        if (loc == null) {
            v.setTextViewText(R.id.qibla_caption, context.getString(R.string.widget_choose_city))
            v.setTextViewText(R.id.qibla_title, context.getString(R.string.widget_choose_city))
            v.setTextColor(R.id.qibla_caption, st.ink)
            v.setTextColor(R.id.qibla_title, st.ink)
            return
        }
        val b = Qibla.bearing(loc.location)
        val deg = Math.round(b).toInt() % 360
        val nf = java.text.NumberFormat.getIntegerInstance(context.resources.configuration.locales[0])
        val density = context.resources.displayMetrics.density
        if (kind == WidgetKind.QIBLA_SMALL) {
            v.setImageViewBitmap(R.id.qibla_dial, qiblaDial(st, b, (140 * density).toInt()))
            v.setTextViewText(R.id.qibla_caption, "${nf.format(deg)}° · ${loc.name}")
            v.setTextColor(R.id.qibla_caption, st.ink)
        } else {
            v.setImageViewBitmap(R.id.qibla_dial, qiblaHalfDial(st, b, (170 * density).toInt()))
            v.setTextViewText(R.id.qibla_title, context.getString(R.string.widget_kind_qibla))
            v.setTextColor(R.id.qibla_title, st.secondary)
            v.setTextViewText(R.id.qibla_degrees, "${nf.format(deg)}°")
            v.setTextColor(R.id.qibla_degrees, st.ink)
            v.setTextViewText(R.id.qibla_turn, if (b <= 180) context.getString(R.string.widget_qibla_right, nf.format(deg))
                else context.getString(R.string.widget_qibla_left, nf.format(360 - deg)))
            v.setTextColor(R.id.qibla_turn, st.ink)
            v.setTextViewText(R.id.qibla_distance, context.getString(R.string.qibla_distance, Format.distance(context, Qibla.distanceMeters(loc.location))))
            v.setTextColor(R.id.qibla_distance, st.secondary)
            v.setTextViewText(R.id.qibla_live, context.getString(R.string.widget_qibla_live))
            v.setTextColor(R.id.qibla_live, st.accent)
        }
        v.setContentDescription(R.id.qibla_dial, context.getString(R.string.qibla_bearing, nf.format(deg)))
    }

    /** Ask, the nearest mosque, and prayer times with the Qibla. */
    private fun actionsViews(context: Context, v: RemoteViews, st: WidgetStyle) {
        listOf(
            Triple(R.id.action_ask, R.drawable.ic_sparkle, R.string.tab_ask_short) to "nearmosque://ask",
            Triple(R.id.action_mosque, R.drawable.ic_tab_mosque, R.string.tab_mosques) to "nearmosque://mosques",
            Triple(R.id.action_qibla, R.drawable.ic_tab_prayer, R.string.tab_prayer) to "nearmosque://qibla",
        ).forEachIndexed { i, (ids, uri) ->
            val (root, icon, label) = ids
            val iconId = listOf(R.id.action_ask_icon, R.id.action_mosque_icon, R.id.action_qibla_icon)[i]
            val labelId = listOf(R.id.action_ask_label, R.id.action_mosque_label, R.id.action_qibla_label)[i]
            v.setInt(root, "setBackgroundResource", st.card)
            v.setImageViewResource(iconId, icon)
            v.setInt(iconId, "setBackgroundResource", st.pill)
            v.setInt(iconId, "setColorFilter", st.onPill)
            v.setTextViewText(labelId, context.getString(label))
            v.setTextColor(labelId, st.ink)
            v.setOnClickPendingIntent(root, link(context, 30 + i, uri))
        }
    }

    private fun pillColor(st: WidgetStyle) = when (st) {
        WidgetStyle.CREAM -> 0xFFC99A3A.toInt()
        WidgetStyle.GREEN -> 0xFFD9E6C8.toInt()
        WidgetStyle.NIGHT -> 0xFFD4A843.toInt()
    }

    /** A dark cube with its gold band, centred on ([cx], [cy]). */
    private fun drawKaaba(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val x0 = cx - size / 2
        val y0 = cy - size / 2
        fun pt(x: Float, y: Float) = Pair(x0 + x * size, y0 + y * size)
        fun poly(color: Int, vararg p: Pair<Float, Float>) {
            val path = android.graphics.Path()
            path.moveTo(p[0].first, p[0].second)
            for (q in p.drop(1)) path.lineTo(q.first, q.second)
            path.close()
            canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        }
        poly(0xFF121212.toInt(), pt(0.12f, 0.3f), pt(0.62f, 0.3f), pt(0.62f, 0.88f), pt(0.12f, 0.88f))
        poly(0xFF262626.toInt(), pt(0.62f, 0.3f), pt(0.88f, 0.18f), pt(0.88f, 0.74f), pt(0.62f, 0.88f))
        poly(0xFF3A3A3A.toInt(), pt(0.12f, 0.3f), pt(0.38f, 0.18f), pt(0.88f, 0.18f), pt(0.62f, 0.3f))
        poly(0xFFD9A842.toInt(), pt(0.12f, 0.4f), pt(0.62f, 0.4f), pt(0.62f, 0.47f), pt(0.12f, 0.47f))
        poly(0xFFA88028.toInt(), pt(0.62f, 0.4f), pt(0.88f, 0.28f), pt(0.88f, 0.35f), pt(0.62f, 0.47f))
    }

    private fun cardColor(st: WidgetStyle) = when (st) {
        WidgetStyle.CREAM -> 0xC7FFFFFF.toInt()
        WidgetStyle.GREEN -> 0x38000000
        WidgetStyle.NIGHT -> 0x1AFFFFFF
    }

    /** North-up dial: ticks, N at the top, a gold arrow to the Qibla with a glow and the Kaaba where it points. */
    private fun qiblaDial(st: WidgetStyle, bearing: Double, size: Int): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(b)
        val c = size / 2f
        val r = size / 2f - 2
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = cardColor(st)
        canvas.drawCircle(c, c, r, p)
        p.style = Paint.Style.STROKE
        for (i in 0 until 36) {
            val t = Math.toRadians(i * 10.0)
            val long = i % 9 == 0
            p.color = (st.secondary and 0x00FFFFFF) or ((if (long) 0xCC else 0x66) shl 24)
            p.strokeWidth = size * (if (long) 0.012f else 0.007f)
            val r1 = r - size * 0.015f
            val r2 = r - size * (if (long) 0.065f else 0.04f)
            canvas.drawLine(c + r1 * Math.sin(t).toFloat(), c - r1 * Math.cos(t).toFloat(), c + r2 * Math.sin(t).toFloat(), c - r2 * Math.cos(t).toFloat(), p)
        }
        p.style = Paint.Style.FILL
        p.color = st.accent
        p.textSize = size * 0.085f
        p.isFakeBoldText = true
        p.textAlign = Paint.Align.CENTER
        canvas.drawText("N", c, c - r + size * 0.115f, p)
        val a = Math.toRadians(bearing)
        val tr = r - size * 0.25f
        val tx = c + tr * Math.sin(a).toFloat()
        val ty = c - tr * Math.cos(a).toFloat()
        val gold = pillColor(st)
        p.color = (gold and 0x00FFFFFF) or (0x55 shl 24)
        canvas.drawCircle(tx, ty, size * 0.1f, p)
        p.style = Paint.Style.STROKE
        p.color = gold
        p.strokeWidth = size * 0.025f
        p.strokeCap = Paint.Cap.ROUND
        val er = tr - size * 0.07f
        canvas.drawLine(c, c, c + er * Math.sin(a).toFloat(), c - er * Math.cos(a).toFloat(), p)
        p.style = Paint.Style.FILL
        canvas.drawCircle(c, c, size * 0.035f, p)
        drawKaaba(canvas, tx, ty, size * 0.15f)
        return b
    }

    /** Half a dial facing the Qibla: the Kaaba at the top, a gold arrow to it, and N where north lies (held at the edge when behind). */
    private fun qiblaHalfDial(st: WidgetStyle, bearing: Double, width: Int): Bitmap {
        val height = (width * 0.62f).toInt()
        val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(b)
        val c = width / 2f
        val cy = height - width * 0.04f
        // Room above the top for the Kaaba's glow.
        val r = minOf(width / 2f - width * 0.07f, cy - width * 0.115f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.STROKE
        p.strokeWidth = width * 0.07f
        p.color = cardColor(st)
        canvas.drawArc(RectF(c - r, cy - r, c + r, cy + r), 180f, 180f, false, p)
        for (i in 0..12) {
            val t = Math.toRadians(i * 15.0 - 90)
            val major = i % 6 == 0
            p.color = (st.secondary and 0x00FFFFFF) or ((if (major) 0xE6 else 0x73) shl 24)
            p.strokeWidth = width * (if (major) 0.009f else 0.006f)
            val r1 = r - width * 0.02f
            val r2 = r + width * 0.02f
            canvas.drawLine(c + r1 * Math.sin(t).toFloat(), cy - r1 * Math.cos(t).toFloat(), c + r2 * Math.sin(t).toFloat(), cy - r2 * Math.cos(t).toFloat(), p)
        }
        val gold = pillColor(st)
        p.color = gold
        p.strokeWidth = width * 0.022f
        p.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(c, cy, c, cy - r + width * 0.11f, p)
        p.style = Paint.Style.FILL
        canvas.drawCircle(c, cy, width * 0.028f, p)
        p.color = (gold and 0x00FFFFFF) or (0x55 shl 24)
        canvas.drawCircle(c, cy - r, width * 0.11f, p)
        drawKaaba(canvas, c, cy - r, width * 0.14f)
        var rel = (-bearing) % 360
        if (rel > 180) rel -= 360 else if (rel < -180) rel += 360
        val n = Math.toRadians(rel.coerceIn(-90.0, 90.0))
        val nx = c + r * Math.sin(n).toFloat()
        val ny = cy - r * Math.cos(n).toFloat()
        p.color = st.accent
        canvas.drawCircle(nx, ny, width * 0.05f, p)
        p.color = st.onPill
        p.textSize = width * 0.055f
        p.isFakeBoldText = true
        p.textAlign = Paint.Align.CENTER
        canvas.drawText("N", nx, ny + width * 0.02f, p)
        return b
    }

    /** The ring: a track, the elapsed part of the prayer period clockwise from the top, and a dot at its end. */
    private fun ringBitmap(st: WidgetStyle, fraction: Double, size: Int): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(b)
        val stroke = size * 0.032f
        val dot = stroke * 1.25f
        val pad = dot + stroke * 0.5f
        val rect = RectF(pad, pad, size - pad, size - pad)
        val arcColor = if (st == WidgetStyle.CREAM) 0xFFC99A3A.toInt() else st.accent
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
        line.color = if (st == WidgetStyle.CREAM) 0xFFFFFFFF.toInt() else (st.ink and 0x00FFFFFF) or (0x29 shl 24)
        canvas.drawArc(rect, 0f, 360f, false, line)
        line.color = arcColor
        canvas.drawArc(rect, -90f, (fraction * 360).toFloat().coerceAtLeast(0.5f), false, line)
        val r = rect.width() / 2
        val a = Math.toRadians(fraction * 360)
        val cx = (size / 2f + r * Math.sin(a)).toFloat()
        val cy = (size / 2f - r * Math.cos(a)).toFloat()
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, cy, dot + stroke * 0.4f, fill)
        fill.color = arcColor
        canvas.drawCircle(cx, cy, dot, fill)
        return b
    }

    private fun link(context: Context, code: Int, uri: String): PendingIntent =
        PendingIntent.getActivity(context, code, Intent(Intent.ACTION_VIEW, Uri.parse(uri), context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun mosqueViews(context: Context, v: RemoteViews, s: AppSettings, kind: WidgetKind, st: WidgetStyle, x: WidgetExtras) {
        v.setInt(R.id.mq_icon, "setColorFilter", st.accent)
        v.setTextViewText(R.id.mq_title, context.getString(R.string.widget_kind_mosque))
        v.setTextColor(R.id.mq_title, st.secondary)
        v.setInt(R.id.widget_pin, "setColorFilter", st.secondary)
        v.setTextViewText(R.id.widget_city, s.prayerLocation?.name.orEmpty())
        v.setTextColor(R.id.widget_city, st.secondary)
        v.setOnClickPendingIntent(R.id.widget_root, link(context, 10, "nearmosque://mosques"))
        if (x.mosques.isEmpty()) {
            v.setTextViewText(R.id.mq_empty, context.getString(R.string.no_records_title))
            v.setTextColor(R.id.mq_empty, st.ink)
            v.setViewVisibility(R.id.mq_empty, View.VISIBLE)
            for (id in mqRows + listOf(R.id.mq0_name, R.id.mq0_row, R.id.mq_go)) v.setViewVisibility(id, View.GONE)
            return
        }
        fun dir(bearing: Double) = dirs[(((bearing % 360 + 360) % 360 + 22.5) / 45).toInt() % 8]
        // Small: the nearest one.
        val first = x.mosques[0]
        v.setTextViewText(R.id.mq0_name, first.name.ifEmpty { context.getString(R.string.mosque_unnamed) })
        v.setTextColor(R.id.mq0_name, st.ink)
        v.setImageViewResource(R.id.mq0_dir, dir(first.bearing))
        v.setInt(R.id.mq0_dir, "setColorFilter", st.accent)
        v.setTextViewText(R.id.mq0_dist, Format.distance(context, first.meters))
        v.setTextColor(R.id.mq0_dist, st.ink)
        v.setInt(R.id.mq_go_icon, "setColorFilter", st.accent)
        v.setTextViewText(R.id.mq_go_text, context.getString(R.string.directions))
        v.setTextColor(R.id.mq_go_text, st.accent)
        if (kind == WidgetKind.MOSQUE_SMALL) v.setOnClickPendingIntent(R.id.widget_root, link(context, 11, "nearmosque://mosque?id=${Uri.encode(first.id)}"))
        // Medium: up to three, each opening its page; the nearest highlighted.
        mqRows.forEachIndexed { i, row ->
            val m = x.mosques.getOrNull(i)
            if (m == null) { v.setViewVisibility(row, View.INVISIBLE); return@forEachIndexed }
            val hi = i == 0
            val color = if (hi) st.onPill else st.ink
            v.setTextViewText(mqNames[i], m.name.ifEmpty { context.getString(R.string.mosque_unnamed) })
            v.setTextColor(mqNames[i], color)
            v.setTextViewText(mqDists[i], Format.distance(context, m.meters))
            v.setTextColor(mqDists[i], color)
            v.setImageViewResource(mqDirs[i], dir(m.bearing))
            v.setInt(mqDirs[i], "setColorFilter", if (hi) st.onPill else st.accent)
            v.setInt(mqIcons[i], "setColorFilter", if (hi) st.onPill else st.accent)
            v.setInt(row, "setBackgroundResource", if (hi) st.pill else st.card)
            v.setOnClickPendingIntent(row, link(context, 20 + i, "nearmosque://mosque?id=${Uri.encode(m.id)}"))
        }
    }

    private fun askViews(context: Context, v: RemoteViews, kind: WidgetKind, st: WidgetStyle, x: WidgetExtras, now: Instant) {
        v.setOnClickPendingIntent(R.id.widget_root, link(context, 30, "nearmosque://ask"))
        v.setInt(R.id.ask_spark, "setColorFilter", st.accent)
        v.setTextViewText(R.id.ask_title, context.getString(R.string.widget_kind_ask))
        v.setTextColor(R.id.ask_title, st.ink)
        v.setTextViewText(R.id.ask_button, context.getString(R.string.tab_ask))
        v.setTextColor(R.id.ask_button, st.onPill)
        v.setInt(R.id.ask_button, "setBackgroundResource", st.pill)
        v.setInt(R.id.ask_bar, "setBackgroundResource", st.card)
        v.setTextViewText(R.id.ask_hint, context.getString(R.string.ask_placeholder))
        v.setTextColor(R.id.ask_hint, st.secondary)
        v.setOnClickPendingIntent(R.id.ask_bar, link(context, 31, "nearmosque://ask"))
        v.setTextViewText(R.id.ask_suggested, context.getString(R.string.widget_ask_suggested))
        v.setTextColor(R.id.ask_suggested, st.secondary)
        val qs = x.todaysQuestions(now.epochSecond / 86_400)
        askRows.forEachIndexed { i, row ->
            val q = qs.getOrNull(i)
            if (q == null) { v.setViewVisibility(row, View.INVISIBLE); return@forEachIndexed }
            v.setTextViewText(askTexts[i], q)
            v.setTextColor(askTexts[i], st.ink)
            v.setTextColor(askGos[i], st.secondary)
            v.setInt(askIcons[i], "setColorFilter", st.accent)
            v.setInt(row, "setBackgroundResource", st.card)
            v.setOnClickPendingIntent(row, link(context, 40 + i, "nearmosque://ask?q=${Uri.encode(q)}"))
        }
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

/** Time left to the next prayer on a ring. */
class PrayerWidgetRingSmall : PrayerWidgetProvider()

/** The ring and today's six times. */
class PrayerWidgetRingMedium : PrayerWidgetProvider()

/** A large ring and today's six times in two columns. */
class PrayerWidgetRing : PrayerWidgetProvider()

/** Today's five prayers with the dates, the next prayer and the city. */
class PrayerWidgetToday : PrayerWidgetProvider()

/** Today's six times as tiles with icons and bells, with the dates, the next prayer and the city. */
class PrayerWidgetTodayLarge : PrayerWidgetProvider()

/** The nearest mosque: name, distance and direction. */
class MosqueWidgetSmall : PrayerWidgetProvider()

/** The three nearest mosques, each opening its page. */
class MosqueWidget : PrayerWidgetProvider()

/** The Qibla from north on a dial. */
class QiblaWidgetSmall : PrayerWidgetProvider()

/** Half a dial facing the Qibla, with the angle from north and the distance to the Kaaba. */
class QiblaWidget : PrayerWidgetProvider()

/** Ask, the nearest mosque, and prayer times with the Qibla. */
class ActionsWidget : PrayerWidgetProvider()

/** Opens Ask. */
class AskWidgetSmall : PrayerWidgetProvider()

/** A question bar and two suggested questions for today. */
class AskWidget : PrayerWidgetProvider()
