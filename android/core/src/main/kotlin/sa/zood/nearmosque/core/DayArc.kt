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

    /** Which half to draw: the onboarding's Day | Night control can show the one not in progress. */
    enum class Half { DAY, NIGHT }

    /** The event at the left end of the arc (sunrise by day, Maghrib by night) and at the right end. */
    val startEvent: PrayerEvent get() = if (isDay) PrayerEvent.SUNRISE else PrayerEvent.MAGHRIB
    val endEvent: PrayerEvent get() = if (isDay) PrayerEvent.MAGHRIB else PrayerEvent.SUNRISE

    companion object {
        /** [days] is yesterday, today and tomorrow. Null when a sunrise or Maghrib is missing (polar days). */
        fun at(now: Instant, days: List<DaySchedule>): DayArc? {
            if (days.size != 3) return null
            val sunrise = days[1][PrayerEvent.SUNRISE] ?: return null
            val maghrib = days[1][PrayerEvent.MAGHRIB] ?: return null
            return at(now, days, if (!now.isBefore(sunrise) && now.isBefore(maghrib)) Half.DAY else Half.NIGHT)
        }

        /**
         * The [half] containing [now], or the coming one: by day today's sunrise to Maghrib (tomorrow's after Maghrib),
         * by night the night in progress (from Maghrib, or before sunrise), else tonight. [fraction] is clamped to 0…1,
         * so a coming half starts at 0.
         */
        fun at(now: Instant, days: List<DaySchedule>, half: Half): DayArc? {
            if (days.size != 3) return null
            val (yesterday, today, tomorrow) = days
            val sunrise = today[PrayerEvent.SUNRISE] ?: return null
            val maghrib = today[PrayerEvent.MAGHRIB] ?: return null
            val start: Instant
            val end: Instant
            when (half) {
                Half.DAY -> if (now.isBefore(maghrib)) {
                    start = sunrise; end = maghrib
                } else {
                    start = tomorrow[PrayerEvent.SUNRISE] ?: return null; end = tomorrow[PrayerEvent.MAGHRIB] ?: return null
                }
                Half.NIGHT -> if (now.isBefore(sunrise)) {
                    start = yesterday[PrayerEvent.MAGHRIB] ?: return null; end = sunrise
                } else {
                    start = maghrib; end = tomorrow[PrayerEvent.SUNRISE] ?: return null
                }
            }
            val isDay = half == Half.DAY
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
