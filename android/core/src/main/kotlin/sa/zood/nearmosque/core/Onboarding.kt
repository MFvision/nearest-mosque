package sa.zood.nearmosque.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/**
 * The "First Light" onboarding: its steps, where Back and Skip go, its timings, the weather preview samples, the
 * coming Friday's times and the "Also in Near Mosque" list. The same on every platform (Android and the desktop use
 * this file; mirrors ios NMCore Onboarding.swift), so the order and the rules are tested once.
 */
enum class OnboardingStep(
    /** The step's name in tests and debug options; "where" for [PLACE]. */
    val key: String,
) {
    PROMISE("promise"), PLACE("where"), ASR("asr"), ADHAN("adhan"), FRIDAY("friday"), WEATHER("weather"), LOOK("look"),
    BUILDING("building"), DAY("day"), EXTRAS("extras"), MORE("more"), PRIVACY("privacy"), DONE("done");

    companion object {
        fun of(key: String?): OnboardingStep? = entries.firstOrNull { it.key == key }
    }
}

/** The steps for one platform: Windows and Linux have no app style or appearance setting, so no [OnboardingStep.LOOK]. */
class OnboardingFlow(val hasLook: Boolean = true) {
    val steps: List<OnboardingStep> = OnboardingStep.entries.filter { hasLook || it != OnboardingStep.LOOK }

    /** The position in [steps], or -1 for a step this flow does not have. */
    fun index(step: OnboardingStep): Int = steps.indexOf(step)

    fun next(step: OnboardingStep): OnboardingStep? = index(step).takeIf { it >= 0 }?.let { steps.getOrNull(it + 1) }

    /** The step Back returns to; none on the promise, while building and when done. Back from the day skips building. */
    fun back(step: OnboardingStep): OnboardingStep? {
        if (step == OnboardingStep.PROMISE || step == OnboardingStep.BUILDING || step == OnboardingStep.DONE) return null
        val i = index(step)
        if (i <= 0) return null
        return steps.subList(0, i).lastOrNull { it != OnboardingStep.BUILDING }
    }

    /**
     * Where Skip goes, changing nothing on the steps it passes: from the questions to building, from the showcase to
     * privacy (so the notification ask still comes). Null where there is no Skip.
     */
    fun skip(step: OnboardingStep): OnboardingStep? = when (step) {
        OnboardingStep.ASR, OnboardingStep.ADHAN, OnboardingStep.FRIDAY, OnboardingStep.WEATHER -> OnboardingStep.BUILDING
        OnboardingStep.LOOK -> if (hasLook) OnboardingStep.BUILDING else null
        OnboardingStep.EXTRAS, OnboardingStep.MORE -> OnboardingStep.PRIVACY
        else -> null
    }

    companion object {
        /** The "YOUR DAY" header shows from the place through privacy. */
        fun showsHeader(step: OnboardingStep): Boolean = step.ordinal in OnboardingStep.PLACE.ordinal..OnboardingStep.PRIVACY.ordinal

        /** The questions that shape the day: the place through the look. */
        fun isQuestion(step: OnboardingStep): Boolean = step.ordinal in OnboardingStep.PLACE.ordinal..OnboardingStep.LOOK.ordinal
    }
}

/** Timings in milliseconds (shared/design/tokens.json "motion" holds the springs). */
object OnboardingTiming {
    /** From an answer to the next step. */
    const val ADVANCE = 650
    const val BUILDING = 1700
    /** Between the building lines ticking to a check. */
    const val BUILDING_LINE = 450
    /** The Allow button's spinner shows at least this long, then the check holds. */
    const val ALLOW_SPINNER = 500
    const val ALLOW_HOLD = 450
    /** The weather preview changes condition this often, crossfading. */
    const val PREVIEW_CYCLE = 2600
    const val PREVIEW_FADE = 1100
    const val LEAVE = 200
    /** Between the children of a step as it enters. */
    const val STAGGER = 40
}

/**
 * Sample weather for the onboarding preview, shown only on the weather step and labelled "Preview": never sent for,
 * never stored, never shown as real.
 */
object WeatherPreview {
    val order = listOf(
        WeatherCondition.CLEAR, WeatherCondition.PARTLY_CLOUDY, WeatherCondition.CLOUDY, WeatherCondition.RAIN,
        WeatherCondition.THUNDERSTORM, WeatherCondition.FOG, WeatherCondition.SNOW,
    )

    /** The MET symbol code drawn for each condition. */
    fun code(c: WeatherCondition): String = when (c) {
        WeatherCondition.CLEAR -> "clearsky"
        WeatherCondition.PARTLY_CLOUDY -> "partlycloudy"
        WeatherCondition.CLOUDY -> "cloudy"
        WeatherCondition.RAIN -> "rain"
        WeatherCondition.THUNDERSTORM -> "rainandthunder"
        WeatherCondition.FOG -> "fog"
        WeatherCondition.SNOW -> "snow"
    }

    /** The sample temperature (°C) and wind (km/h). */
    fun sample(c: WeatherCondition): Pair<Double, Double> = when (c) {
        WeatherCondition.CLEAR -> 31.0 to 9.0
        WeatherCondition.PARTLY_CLOUDY -> 28.0 to 12.0
        WeatherCondition.CLOUDY -> 22.0 to 15.0
        WeatherCondition.RAIN -> 17.0 to 22.0
        WeatherCondition.THUNDERSTORM -> 19.0 to 34.0
        WeatherCondition.FOG -> 12.0 to 4.0
        WeatherCondition.SNOW -> -2.0 to 11.0
    }

    fun now(c: WeatherCondition, at: Instant): WeatherNow {
        val (temp, kmh) = sample(c)
        return WeatherNow(Weather.symbol(code(c)) ?: WeatherSymbol(c, 1.0), temp, kmh / 3.6, at)
    }
}

/** The coming Friday's reminder (AlertSettings.FRIDAY_LEAD_MIN before Dhuhr) and its Dhuhr. */
data class FridayTimes(val reminder: Instant, val dhuhr: Instant)

object FridayPreview {
    /**
     * Today when today is Friday and the reminder is still ahead, otherwise the next Friday (up to 8 days ahead). Null
     * when Dhuhr cannot be calculated that day.
     */
    fun next(location: LatLng, zone: ZoneId, now: Instant, settings: PrayerSettings, calculator: PrayerCalculator): FridayTimes? {
        val today = now.atZone(zone).toLocalDate()
        for (i in 0L..8L) {
            val date = today.plusDays(i)
            if (date.dayOfWeek != DayOfWeek.FRIDAY) continue
            val dhuhr = calculator.schedule(location, date, zone, settings)[PrayerEvent.DHUHR] ?: return null
            val reminder = dhuhr.minusSeconds(AlertSettings.FRIDAY_LEAD_MIN * 60L)
            if (reminder.isAfter(now)) return FridayTimes(reminder, dhuhr)
        }
        return null
    }
}

/** What "Also in Near Mosque" can list, in its order. Each platform says which it has. */
enum class OnboardingFeature {
    IQAMA, BEFORE_PRAYER, FAJR_ALARM, QUIET, QIBLA_COMPASS, QIBLA_NORTH, MOSQUES, LIBRARY, CLASSICS, HIJRI, TASBIH, START_AT_SIGN_IN;

    companion object {
        /**
         * The rest: the [available] features in this order, without those an earlier step already showed, and without
         * the north bearing beside a compass or the classical books beside the library that holds them.
         */
        fun more(available: Set<OnboardingFeature>, shownEarlier: Set<OnboardingFeature> = emptySet()): List<OnboardingFeature> =
            entries.filter { f ->
                f in available && f !in shownEarlier &&
                    !(f == QIBLA_NORTH && QIBLA_COMPASS in available) &&
                    !(f == CLASSICS && LIBRARY in available)
            }
    }
}
