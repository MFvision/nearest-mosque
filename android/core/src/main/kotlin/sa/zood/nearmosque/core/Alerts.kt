package sa.zood.nearmosque.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/** What the user asked to be told, beyond the per-prayer bells. Mirrors ios NMCore Alerts.swift. */
data class AlertSettings(
    /** Minutes before each enabled prayer for an early reminder; 0 = off. */
    val minutesBefore: Int = 0,
    /** Friday: a reminder [FRIDAY_LEAD_MIN] minutes before Dhuhr (Jumu'ah). */
    val friday: Boolean = false,
    /** In Ramadan: suhoor [SUHOOR_LEAD_MIN] minutes before Fajr, and iftar at Maghrib. */
    val ramadan: Boolean = false,
    /** An alarm (rings until stopped) [fajrAlarmMinutesBefore] minutes before Fajr; null = off. */
    val fajrAlarmMinutesBefore: Int? = null,
) {
    companion object {
        const val FRIDAY_LEAD_MIN = 45
        const val SUHOOR_LEAD_MIN = 45
        val beforeChoices = listOf(0, 5, 10, 15, 20, 30)
        val fajrAlarmChoices = listOf(0, 10, 20, 30, 45)
    }
}

enum class AlertKind { AT_PRAYER, BEFORE, FRIDAY, SUHOOR, IFTAR, FAJR_ALARM }

data class PlannedAlert(val kind: AlertKind, val event: PrayerEvent, val at: Instant, val date: LocalDate)

/**
 * Turns schedules into the alerts to schedule, in time order, after [now] (the platforms cap how many
 * they keep). [isRamadan] says whether a civil date falls in Ramadan (Hijri, with the user's adjustment).
 */
object AlertPlanner {
    fun plan(
        days: List<DaySchedule>, now: Instant, atPrayer: Set<PrayerEvent>, alerts: AlertSettings,
        isRamadan: (LocalDate) -> Boolean,
    ): List<PlannedAlert> {
        val out = mutableListOf<PlannedAlert>()
        for (d in days) {
            for (e in PrayerEvent.prayers) {
                val at = d[e] ?: continue
                if (e in atPrayer) {
                    out += PlannedAlert(AlertKind.AT_PRAYER, e, at, d.date)
                    if (alerts.minutesBefore > 0) out += PlannedAlert(AlertKind.BEFORE, e, at.minusSeconds(alerts.minutesBefore * 60L), d.date)
                }
            }
            if (alerts.friday && d.date.dayOfWeek == DayOfWeek.FRIDAY) {
                d[PrayerEvent.DHUHR]?.let { out += PlannedAlert(AlertKind.FRIDAY, PrayerEvent.DHUHR, it.minusSeconds(AlertSettings.FRIDAY_LEAD_MIN * 60L), d.date) }
            }
            if (alerts.ramadan && isRamadan(d.date)) {
                d[PrayerEvent.FAJR]?.let { out += PlannedAlert(AlertKind.SUHOOR, PrayerEvent.FAJR, it.minusSeconds(AlertSettings.SUHOOR_LEAD_MIN * 60L), d.date) }
                d[PrayerEvent.MAGHRIB]?.let { out += PlannedAlert(AlertKind.IFTAR, PrayerEvent.MAGHRIB, it, d.date) }
            }
            alerts.fajrAlarmMinutesBefore?.let { m ->
                d[PrayerEvent.FAJR]?.let { out += PlannedAlert(AlertKind.FAJR_ALARM, PrayerEvent.FAJR, it.minusSeconds(m * 60L), d.date) }
            }
        }
        return out.filter { it.at.isAfter(now) }.sortedWith(compareBy({ it.at }, { it.kind.ordinal }))
    }
}
