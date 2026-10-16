package sa.zood.nearmosque.core

import java.time.Instant

/**
 * The time between the Adhan and the iqama, and the prayer after it. These are the usual waits (as in mosques in
 * Saudi Arabia), not any mosque's own timetable; the user can change each one. Mirrors NMCore Iqama.swift.
 */
data class IqamaSettings(
    /** Show the wait for the iqama after each Adhan; off goes straight from one Adhan to the countdown to the next. */
    val enabled: Boolean = true,
    /** Minutes from the Adhan to the iqama, per prayer; a prayer not listed has its default. */
    val minutes: Map<PrayerEvent, Int> = emptyMap(),
    /** Minutes after the iqama shown as the prayer itself, before the countdown to the next prayer. */
    val prayerMinutes: Int = 15,
    /** A notification at the iqama. */
    val alert: Boolean = true,
) {
    /** Minutes from this prayer's Adhan to its iqama (0 for sunrise, which has none). */
    fun wait(e: PrayerEvent): Int = if (!e.isPrayer) 0 else maxOf(0, minutes[e] ?: defaults[e] ?: 0)

    fun withWait(e: PrayerEvent, m: Int) = copy(minutes = minutes + (e to m))

    /** The iqama of a prayer whose Adhan is at [adhan]. */
    fun iqama(e: PrayerEvent, adhan: Instant): Instant = adhan.plusSeconds(wait(e) * 60L)

    companion object {
        val defaults = mapOf(PrayerEvent.FAJR to 25, PrayerEvent.DHUHR to 20, PrayerEvent.ASR to 20, PrayerEvent.MAGHRIB to 10, PrayerEvent.ISHA to 20)
        val choices = listOf(5, 10, 15, 20, 25, 30, 35, 40, 45)
        val prayerChoices = listOf(5, 10, 15, 20, 30)
        val OFF = IqamaSettings(enabled = false)
    }
}

/**
 * What the prayer times show at a moment: the countdown to the next Adhan, the wait for the iqama after an Adhan,
 * or the prayer itself after the iqama.
 */
data class PrayerMoment(
    val stage: Stage,
    /** The prayer counted down to (UPCOMING) or the one being called and prayed (TO_IQAMA, PRAYER). */
    val event: PrayerEvent,
    val adhan: Instant,
    /** Its iqama (null while upcoming with the iqama off). */
    val iqama: Instant?,
    /** When this stage ends: the Adhan (UPCOMING), the iqama (TO_IQAMA), the end of the prayer (PRAYER). */
    val until: Instant,
    /** When this stage began: the previous prayer's end (UPCOMING), the Adhan (TO_IQAMA), the iqama (PRAYER). */
    val since: Instant?,
    /** The next Adhan after now, whatever the stage. */
    val next: Upcoming,
) {
    enum class Stage { UPCOMING, TO_IQAMA, PRAYER }
}

private fun minOf(a: Instant, b: Instant) = if (a.isBefore(b)) a else b

/**
 * What to show at [now]: within a prayer's wait for the iqama, or its prayer time, that prayer; otherwise the
 * countdown to the next Adhan. With the iqama off this is always the countdown.
 */
fun PrayerCalculator.moment(days: List<DaySchedule>, now: Instant, iqama: IqamaSettings): PrayerMoment? {
    val next = nextPrayer(days, now) ?: return null
    val prayers = days.flatMap { d -> PrayerEvent.prayers.mapNotNull { e -> d[e]?.let { e to it } } }.sortedBy { it.second }
    val last = prayers.lastOrNull { !it.second.isAfter(now) }
    if (iqama.enabled && last != null) {
        val (e, adhan) = last
        val iq = iqama.iqama(e, adhan)
        val end = iq.plusSeconds(iqama.prayerMinutes * 60L)
        // Never past the next Adhan, whatever the waits are set to.
        val toIqamaEnd = minOf(iq, next.at)
        if (now.isBefore(toIqamaEnd) && iq.isAfter(adhan)) {
            return PrayerMoment(PrayerMoment.Stage.TO_IQAMA, e, adhan, iq, toIqamaEnd, adhan, next)
        }
        val prayerEnd = minOf(end, next.at)
        if (now.isBefore(prayerEnd) && end.isAfter(iq)) {
            return PrayerMoment(PrayerMoment.Stage.PRAYER, e, adhan, iq, prayerEnd, iq, next)
        }
    }
    val start = next.periodStart
    val prev = start?.let { s -> prayers.lastOrNull { it.second == s } }
    val since = if (iqama.enabled && start != null && prev != null) {
        minOf(next.at, iqama.iqama(prev.first, start).plusSeconds(iqama.prayerMinutes * 60L))
    } else start
    return PrayerMoment(
        PrayerMoment.Stage.UPCOMING, next.event, next.at,
        if (iqama.enabled) iqama.iqama(next.event, next.at) else null, next.at, since, next,
    )
}

/** Every moment the stage changes (each Adhan, iqama and end of prayer) after [now], in order. */
fun stageChanges(days: List<DaySchedule>, now: Instant, iqama: IqamaSettings): List<Instant> {
    val out = mutableSetOf<Instant>()
    for (d in days) for (e in PrayerEvent.prayers) {
        val adhan = d[e] ?: continue
        out += adhan
        if (!iqama.enabled) continue
        val iq = iqama.iqama(e, adhan)
        out += iq
        out += iq.plusSeconds(iqama.prayerMinutes * 60L)
    }
    return out.filter { it.isAfter(now) }.sorted()
}
