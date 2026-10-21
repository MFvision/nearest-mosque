package sa.zood.nearmosque.core

import java.time.DayOfWeek
import java.time.Instant

/**
 * Silencing the phone during each prayer: from the Adhan or the iqama until the end of the prayer, and on Friday from
 * the Adhan for the khutbah and the prayer. Android turns Do Not Disturb on and off itself (with the user's
 * permission); iOS lets no app change Focus, so there the times feed a Shortcut the user runs. Mirrors NMCore
 * Silence.swift.
 */
data class SilenceSettings(
    val enabled: Boolean = false,
    /** When the silence begins on ordinary days; it always ends with the prayer (the iqama plus its prayer minutes). */
    val start: Start = Start.IQAMA,
    /** The prayers silenced. */
    val prayers: Set<PrayerEvent> = PrayerEvent.prayers.toSet(),
    /** On Friday, minutes from the Dhuhr Adhan for the khutbah and the Friday prayer. */
    val fridayMinutes: Int = 60,
) {
    enum class Start { ADHAN, IQAMA }

    fun includes(e: PrayerEvent) = e.isPrayer && e in prayers

    fun with(e: PrayerEvent, on: Boolean) = copy(prayers = if (on) prayers + e else prayers - e)

    /**
     * Every silence that has not ended by [now] (the one under way first), in order. Each ends by the next prayer's
     * Adhan at the latest. With the setting off, none. [adhanSeconds] is how long the app's own Adhan plays at a
     * prayer (0 when it plays none): the silence starts only after it, so it never cuts that Adhan. [alertSeconds]
     * holds every start back a little, so an alert at that very minute (a bell, the iqama) sounds first.
     */
    fun windows(
        days: List<DaySchedule>, iqama: IqamaSettings, now: Instant,
        adhanSeconds: (PrayerEvent) -> Long = { 0L }, alertSeconds: Long = 0L,
    ): List<SilenceWindow> {
        if (!enabled) return emptyList()
        val all = days.flatMap { d -> PrayerEvent.prayers.mapNotNull { e -> d[e]?.let { Triple(e, it, d.date) } } }.sortedBy { it.second }
        val out = mutableListOf<SilenceWindow>()
        all.forEachIndexed { i, (e, adhan, date) ->
            if (!includes(e)) return@forEachIndexed
            val friday = e == PrayerEvent.DHUHR && date.dayOfWeek == DayOfWeek.FRIDAY
            val iq = iqama.iqama(e, adhan)
            var to = if (friday) adhan.plusSeconds(fridayMinutes * 60L) else iq.plusSeconds(iqama.prayerMinutes * 60L)
            if (i + 1 < all.size && all[i + 1].second.isBefore(to)) to = all[i + 1].second
            var from = if (friday || start == Start.ADHAN) adhan else iq
            from = maxOf(from.plusSeconds(alertSeconds), adhan.plusSeconds(adhanSeconds(e)))
            if (from.isAfter(to)) from = to
            if (!to.isAfter(from) || !to.isAfter(now)) return@forEachIndexed
            out += SilenceWindow(e, from, to, friday)
        }
        return out
    }

    /** The silence under way at [now], if any. */
    fun current(
        days: List<DaySchedule>, iqama: IqamaSettings, now: Instant,
        adhanSeconds: (PrayerEvent) -> Long = { 0L }, alertSeconds: Long = 0L,
    ): SilenceWindow? = windows(days, iqama, now, adhanSeconds, alertSeconds).firstOrNull { !it.start.isAfter(now) }

    companion object {
        val fridayChoices = listOf(45, 60, 75, 90)
    }
}

data class SilenceWindow(
    val event: PrayerEvent,
    val start: Instant,
    val end: Instant,
    /** The Friday prayer (Dhuhr on Friday, from its Adhan). */
    val friday: Boolean,
)
