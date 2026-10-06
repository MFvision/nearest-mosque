package sa.zood.nearmosque.core

import java.time.Duration
import java.time.Instant

/**
 * The half of the day in progress, for the sky card's arc: daylight runs from sunrise to Maghrib (the
 * sun moves along the arc), night from Maghrib to the next sunrise (the moon). [fraction] is elapsed
 * time, not the sun's altitude. The prayers that fall inside the half sit on the arc as marks.
 * Mirrors ios NMCore DayArc.swift.
 */
data class DayArc(
    val isDay: Boolean,
    val start: Instant,
    val end: Instant,
    val fraction: Double,
    val marks: List<Mark>,
) {
    data class Mark(val event: PrayerEvent, val at: Instant, val fraction: Double)

    /** The event at the left end of the arc (sunrise by day, Maghrib by night) and at the right end. */
    val startEvent: PrayerEvent get() = if (isDay) PrayerEvent.SUNRISE else PrayerEvent.MAGHRIB
    val endEvent: PrayerEvent get() = if (isDay) PrayerEvent.MAGHRIB else PrayerEvent.SUNRISE

    companion object {
        /** [days] is yesterday, today and tomorrow. Null when a sunrise or Maghrib is missing (polar days). */
        fun at(now: Instant, days: List<DaySchedule>): DayArc? {
            if (days.size != 3) return null
            val (yesterday, today, tomorrow) = days
            val sunrise = today[PrayerEvent.SUNRISE] ?: return null
            val maghrib = today[PrayerEvent.MAGHRIB] ?: return null
            val isDay: Boolean
            val start: Instant
            val end: Instant
            when {
                now.isBefore(sunrise) -> { isDay = false; start = yesterday[PrayerEvent.MAGHRIB] ?: return null; end = sunrise }
                now.isBefore(maghrib) -> { isDay = true; start = sunrise; end = maghrib }
                else -> { isDay = false; start = maghrib; end = tomorrow[PrayerEvent.SUNRISE] ?: return null }
            }
            val total = Duration.between(start, end).toMillis().toDouble()
            if (total <= 0) return null
            fun frac(t: Instant) = (Duration.between(start, t).toMillis() / total).coerceIn(0.0, 1.0)
            val inner = if (isDay) listOf(PrayerEvent.DHUHR, PrayerEvent.ASR) else listOf(PrayerEvent.ISHA, PrayerEvent.FAJR)
            val marks = days.flatMap { d -> inner.mapNotNull { e -> d[e]?.takeIf { it.isAfter(start) && it.isBefore(end) }?.let { Mark(e, it, frac(it)) } } }
                .sortedBy { it.at }
            return DayArc(isDay, start, end, frac(now), marks)
        }
    }
}
