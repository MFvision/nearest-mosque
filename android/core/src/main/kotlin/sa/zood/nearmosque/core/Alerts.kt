package sa.zood.nearmosque.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/** Which recording plays for an Adhan. */
enum class AdhanSource {
    MAKKAH, MADINAH;

    /** Both mosques have a Fajr recording (shared/audio/CREDITS.md); kept so a future source without one can say so. */
    val hasFajr: Boolean get() = true
}

/** How the onboarding asks to be called to prayer ([AlertSettings.adhanChoice]); [key] is the shared name. */
enum class AdhanChoice(val key: String) {
    MAKKAH("makkah"), MADINAH("madinah"), SHORT("short"), BANNER_ONLY("banner_only"),
}

/** The language of the spoken reminders ("Ten minutes remain until the call to Maghrib prayer"). */
enum class VoiceLanguage {
    ARABIC, ENGLISH;

    companion object {
        /** Arabic for an Arabic interface, English otherwise. */
        fun forInterface(language: String) = if (language == "ar") ARABIC else ENGLISH
    }
}

/** Who recites the verses: Alafasy, or Al-Husary (Friday) and Abdul Basit (evening verse). */
enum class Reciter { ALAFASY, CLASSIC }

/**
 * For one prayer: which of the sounds and reminders apply to it. Each is also switched on or off for all
 * prayers in [AlertSettings]; a sound plays for a prayer only when both are on.
 */
data class PrayerSounds(
    val adhan: Boolean = true,
    val before10: Boolean = true,
    val before5: Boolean = true,
    val near: Boolean = true,
)

/**
 * What the user asked to be told, beyond the per-prayer bells. Every reminder and sound has its own switch;
 * none turns another on or off. Mirrors ios NMCore Alerts.swift.
 */
data class AlertSettings(
    /** Before the 10 and 5 minute reminders existed: one early reminder; moved to them when read ([migrated]). */
    val minutesBefore: Int = 0,
    /** Friday: a reminder [FRIDAY_LEAD_MIN] minutes before Dhuhr (Jumu'ah). */
    val friday: Boolean = false,
    /** In Ramadan: suhoor [SUHOOR_LEAD_MIN] minutes before Fajr, and iftar at Maghrib. */
    val ramadan: Boolean = false,
    /** An alarm (rings until stopped) [fajrAlarmMinutesBefore] minutes before Fajr; null = off. */
    val fajrAlarmMinutesBefore: Int? = null,
    /** Friday morning: a reminder to read Surat Al-Kahf. */
    val kahf: Boolean = false,
    /** Before sleep: Ar-Ra'd 13:28 recited, at this many minutes after midnight; null = off. */
    val bedtimeMinutes: Int? = null,
    /** The Adhan at Dhuhr, Asr, Maghrib and Isha, and which recording. */
    val adhan: Boolean = false,
    val adhanSource: AdhanSource = AdhanSource.MAKKAH,
    /** The Fajr Adhan, and which recording. */
    val fajrAdhan: Boolean = false,
    val fajrAdhanSource: AdhanSource = AdhanSource.MAKKAH,
    /** Only the first "Allahu akbar, Allahu akbar" instead of the whole Adhan (for every prayer). */
    val shortAdhan: Boolean = false,
    /** Spoken reminders 10 and 5 minutes before the prayer. */
    val before10: Boolean = false,
    val before5: Boolean = false,
    /** "The call to … prayer is approaching", [nearMinutes] before it. */
    val near: Boolean = false,
    val nearMinutes: Int = 15,
    /** The spoken reminders' language; null follows the interface. */
    val voice: VoiceLanguage? = null,
    /** Per prayer; a prayer not listed has everything on. */
    val perPrayer: Map<PrayerEvent, PrayerSounds> = emptyMap(),
    /** Friday only: Salli Ala Rasulillah, Al-Ahzab 33:56 recited, at this many minutes after midnight. */
    val salawat: Boolean = false,
    val salawatMinutes: Int = 11 * 60,
    /** Every day: a reminder to read the Qur'an, at this many minutes after midnight. */
    val quran: Boolean = false,
    val quranMinutes: Int = 20 * 60 + 30,
    val reciter: Reciter = Reciter.ALAFASY,
) {
    fun sounds(e: PrayerEvent): PrayerSounds = perPrayer[e] ?: PrayerSounds()

    fun withSounds(e: PrayerEvent, s: PrayerSounds) = copy(perPrayer = perPrayer + (e to s))

    enum class PrayerSound { ADHAN, BEFORE_10, BEFORE_5, NEAR }

    /** Whether this sound is on for this prayer (its own switch and the switch for all prayers). */
    fun isOn(e: PrayerEvent, sound: PrayerSound): Boolean {
        val s = sounds(e)
        return when (sound) {
            PrayerSound.ADHAN -> adhanPlays(e)
            PrayerSound.BEFORE_10 -> before10 && s.before10
            PrayerSound.BEFORE_5 -> before5 && s.before5
            PrayerSound.NEAR -> near && s.near
        }
    }

    /**
     * Turns a sound on or off for one prayer. Turning it on while it is off for all prayers turns it on for this
     * prayer only (the others in the same group are set off), so one switch is all it takes.
     */
    fun withPrayer(e: PrayerEvent, sound: PrayerSound, on: Boolean): AlertSettings {
        fun PrayerSounds.with(v: Boolean) = when (sound) {
            PrayerSound.ADHAN -> copy(adhan = v)
            PrayerSound.BEFORE_10 -> copy(before10 = v)
            PrayerSound.BEFORE_5 -> copy(before5 = v)
            PrayerSound.NEAR -> copy(near = v)
        }
        val masterOn = when (sound) {
            PrayerSound.ADHAN -> if (e == PrayerEvent.FAJR) fajrAdhan else adhan
            PrayerSound.BEFORE_10 -> before10
            PrayerSound.BEFORE_5 -> before5
            PrayerSound.NEAR -> near
        }
        var a = this
        if (on && !masterOn) {
            a = when (sound) {
                PrayerSound.ADHAN -> if (e == PrayerEvent.FAJR) a.copy(fajrAdhan = true) else a.copy(adhan = true)
                PrayerSound.BEFORE_10 -> a.copy(before10 = true)
                PrayerSound.BEFORE_5 -> a.copy(before5 = true)
                PrayerSound.NEAR -> a.copy(near = true)
            }
            // The Fajr Adhan has its own switch; the other Adhan switch covers Dhuhr to Isha.
            val group = if (sound == PrayerSound.ADHAN) {
                if (e == PrayerEvent.FAJR) listOf(PrayerEvent.FAJR) else PrayerEvent.prayers.filter { it != PrayerEvent.FAJR }
            } else PrayerEvent.prayers
            for (p in group) if (p != e) a = a.withSounds(p, a.sounds(p).with(false))
        }
        return a.withSounds(e, a.sounds(e).with(on))
    }

    /** The Adhan plays at this prayer (its switch, the prayer's own switch, and a recording for it). */
    fun adhanPlays(e: PrayerEvent): Boolean {
        if (!e.isPrayer || !sounds(e).adhan) return false
        return if (e == PrayerEvent.FAJR) fajrAdhan && fajrAdhanSource.hasFajr else adhan
    }

    /** The onboarding's four Adhan choices, read from the switches (one prayer's own switches do not change it). */
    val adhanChoice: AdhanChoice
        get() = when {
            !adhan && !fajrAdhan -> AdhanChoice.BANNER_ONLY
            shortAdhan -> AdhanChoice.SHORT
            adhanSource == AdhanSource.MADINAH -> AdhanChoice.MADINAH
            else -> AdhanChoice.MAKKAH
        }

    /**
     * These settings with [choice]: a mosque turns both Adhans on with its recording (the whole Adhan), the short
     * Adhan keeps the recordings, banner only turns both Adhans off and keeps the rest. [perPrayer] is never touched.
     */
    fun with(choice: AdhanChoice): AlertSettings = when (choice) {
        AdhanChoice.MAKKAH -> copy(adhan = true, fajrAdhan = true, adhanSource = AdhanSource.MAKKAH, fajrAdhanSource = AdhanSource.MAKKAH, shortAdhan = false)
        AdhanChoice.MADINAH -> copy(adhan = true, fajrAdhan = true, adhanSource = AdhanSource.MADINAH, fajrAdhanSource = AdhanSource.MADINAH, shortAdhan = false)
        AdhanChoice.SHORT -> copy(adhan = true, fajrAdhan = true, shortAdhan = true)
        AdhanChoice.BANNER_ONLY -> copy(adhan = false, fajrAdhan = false)
    }

    /** What the onboarding shows selected: Makkah for someone who has not set anything yet, else [adhanChoice]. */
    val onboardingChoice: AdhanChoice get() = if (this == AlertSettings()) AdhanChoice.MAKKAH else adhanChoice

    /** An old single early reminder as the matching new one (10, 5, or "approaching" at 15, 20 or 30). */
    fun migrated(): AlertSettings = when (minutesBefore) {
        0 -> this
        10 -> copy(minutesBefore = 0, before10 = true)
        5 -> copy(minutesBefore = 0, before5 = true)
        else -> copy(minutesBefore = 0, near = true, nearMinutes = if (minutesBefore in nearChoices) minutesBefore else 15)
    }

    companion object {
        const val FRIDAY_LEAD_MIN = 45
        const val SUHOOR_LEAD_MIN = 45
        const val KAHF_HOUR = 9
        const val DEFAULT_BEDTIME_MIN = 22 * 60 + 30
        val beforeChoices = listOf(0, 5, 10, 15, 20, 30)
        val nearChoices = listOf(15, 20, 30)
        val fajrAlarmChoices = listOf(0, 10, 20, 30, 45)
    }
}

enum class AlertKind { AT_PRAYER, BEFORE, FRIDAY, SUHOOR, IFTAR, FAJR_ALARM, ADHAN, BEFORE_10, BEFORE_5, NEAR, SALAWAT, QURAN, SLEEP, KAHF, IQAMA }

data class PlannedAlert(val kind: AlertKind, val event: PrayerEvent, val at: Instant, val date: LocalDate)

/**
 * Turns schedules into the alerts to schedule, in time order, after [now] (the platforms cap how many
 * they keep). [isRamadan] says whether a civil date falls in Ramadan (Hijri, with the user's adjustment).
 */
object AlertPlanner {
    fun plan(
        days: List<DaySchedule>, now: Instant, atPrayer: Set<PrayerEvent>, alerts: AlertSettings,
        iqama: IqamaSettings = IqamaSettings.OFF, isRamadan: (LocalDate) -> Boolean,
    ): List<PlannedAlert> {
        val out = mutableListOf<PlannedAlert>()
        for (d in days) {
            fun add(k: AlertKind, e: PrayerEvent, at: Instant) { out += PlannedAlert(k, e, at, d.date) }
            // On that day's clock, then in its zone: a clock change (summer time) does not move a reminder set for 20:30.
            fun atMinutes(minutes: Int) = d.date.atStartOfDay().plusMinutes(minutes.toLong()).atZone(d.zone).toInstant()
            for (e in PrayerEvent.prayers) {
                val at = d[e] ?: continue
                val s = alerts.sounds(e)
                if (alerts.adhanPlays(e)) add(AlertKind.ADHAN, e, at) else if (e in atPrayer) add(AlertKind.AT_PRAYER, e, at)
                if (e in atPrayer && alerts.minutesBefore > 0) add(AlertKind.BEFORE, e, at.minusSeconds(alerts.minutesBefore * 60L))
                if (alerts.before10 && s.before10) add(AlertKind.BEFORE_10, e, at.minusSeconds(600))
                if (alerts.before5 && s.before5) add(AlertKind.BEFORE_5, e, at.minusSeconds(300))
                if (alerts.near && s.near) add(AlertKind.NEAR, e, at.minusSeconds(alerts.nearMinutes * 60L))
                // The iqama, for a prayer that has an Adhan or a bell.
                if (iqama.enabled && iqama.alert && iqama.wait(e) > 0 && (alerts.adhanPlays(e) || e in atPrayer)) add(AlertKind.IQAMA, e, iqama.iqama(e, at))
            }
            // Sunrise (the end of Fajr time): a plain notification when its bell is on.
            if (PrayerEvent.SUNRISE in atPrayer) d[PrayerEvent.SUNRISE]?.let { add(AlertKind.AT_PRAYER, PrayerEvent.SUNRISE, it) }
            val friday = d.date.dayOfWeek == DayOfWeek.FRIDAY
            if (alerts.friday && friday) {
                d[PrayerEvent.DHUHR]?.let { add(AlertKind.FRIDAY, PrayerEvent.DHUHR, it.minusSeconds(AlertSettings.FRIDAY_LEAD_MIN * 60L)) }
            }
            if (alerts.ramadan && isRamadan(d.date)) {
                d[PrayerEvent.FAJR]?.let { add(AlertKind.SUHOOR, PrayerEvent.FAJR, it.minusSeconds(AlertSettings.SUHOOR_LEAD_MIN * 60L)) }
                d[PrayerEvent.MAGHRIB]?.let { add(AlertKind.IFTAR, PrayerEvent.MAGHRIB, it) }
            }
            alerts.fajrAlarmMinutesBefore?.let { m ->
                d[PrayerEvent.FAJR]?.let { add(AlertKind.FAJR_ALARM, PrayerEvent.FAJR, it.minusSeconds(m * 60L)) }
            }
            if (alerts.salawat && friday) add(AlertKind.SALAWAT, PrayerEvent.DHUHR, atMinutes(alerts.salawatMinutes))
            if (alerts.kahf && friday) add(AlertKind.KAHF, PrayerEvent.DHUHR, atMinutes(AlertSettings.KAHF_HOUR * 60))
            if (alerts.quran) add(AlertKind.QURAN, PrayerEvent.ISHA, atMinutes(alerts.quranMinutes))
            alerts.bedtimeMinutes?.let { add(AlertKind.SLEEP, PrayerEvent.ISHA, atMinutes(it)) }
        }
        return out.filter { it.at.isAfter(now) }.sortedWith(compareBy({ it.at }, { it.kind.ordinal }))
    }
}
