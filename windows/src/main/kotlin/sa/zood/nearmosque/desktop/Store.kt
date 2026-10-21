package sa.zood.nearmosque.desktop

import sa.zood.nearmosque.core.AdhanChoice
import sa.zood.nearmosque.core.AdhanSource
import sa.zood.nearmosque.core.AlertSettings
import sa.zood.nearmosque.core.AsrMadhab
import sa.zood.nearmosque.core.HighLatRule
import sa.zood.nearmosque.core.IqamaSettings
import sa.zood.nearmosque.core.Method
import sa.zood.nearmosque.core.PolarRule
import sa.zood.nearmosque.core.PrayerEvent
import sa.zood.nearmosque.core.PrayerSounds
import sa.zood.nearmosque.core.Reciter
import sa.zood.nearmosque.core.VoiceLanguage
import java.io.File
import java.util.prefs.Preferences

/**
 * What the person chose, kept in the Windows user registry (java.util.prefs) or, on Linux, in
 * ~/.config/near-mosque/settings.properties; nothing leaves the PC.
 */
data class DesktopSettings(
    val cityId: Long? = null,
    val language: String = "en",
    val method: Method? = null,
    val madhab: AsrMadhab = AsrMadhab.SHAFI,
    val hijriAdjustment: Int = 0,
    /** How Fajr and Isha are found far from the equator, where twilight lasts all night in summer. */
    val highLatitudeRule: HighLatRule = HighLatRule.AUTO,
    /** On a date with no sunrise or sunset: no times, or the times at 48.5° (shown as estimated). */
    val polarRule: PolarRule = PolarRule.UNAVAILABLE,
    /** Minutes added to each time, sunrise included (-30..30; a zero is not kept), as on the phone. */
    val offsets: Map<PrayerEvent, Int> = emptyMap(),
    /** Umm al-Qura: Isha 30 minutes later in Ramadan. */
    val ramadanIsha: Boolean = true,
    val adhan: Boolean = true,
    val adhanSource: AdhanSource = AdhanSource.MAKKAH,
    val fajrAdhanSource: AdhanSource = AdhanSource.MAKKAH,
    /** Only the first "Allahu akbar, Allahu akbar" instead of the whole Adhan. */
    val shortAdhan: Boolean = false,
    val before10: Boolean = false,
    /** "Five minutes remain until …", spoken, like the ten-minute reminder. */
    val before5: Boolean = false,
    val sunrise: Boolean = false,
    /** Prayers whose Adhan is off (the notification still shows). */
    val silent: Set<PrayerEvent> = emptySet(),
    /** OpenStreetMap results added to the mosques that come with the app (the city's centre is sent, rounded). */
    val onlineSearch: Boolean = true,
    /**
     * Sky follows the weather (opt-in): the chosen city's weather on the sky behind the next-prayer card, with the
     * temperature and wind; the city rounded to about 1 km is sent to MET Norway while the window is shown.
     */
    val skyWeather: Boolean = false,
    /** The one-time offer of [skyWeather] at the top of the prayer screen was answered (either way). */
    val weatherOffered: Boolean = false,
    /** Open in the notification area (or minimized, where there is none) when the person signs in. */
    val startAtLogin: Boolean = true,
    /** The wait from each Adhan to the iqama, the prayer after it, and the notification at the iqama. */
    val iqama: IqamaSettings = IqamaSettings(),
    /** "The call to … prayer is approaching", [nearMinutes] (15, 20 or 30) before each prayer. */
    val near: Boolean = false,
    val nearMinutes: Int = 15,
    /** The spoken reminders' language; null follows the interface. */
    val voice: VoiceLanguage? = null,
    /**
     * Per prayer: the 10, 5 and "approaching" reminders (a prayer not listed has them all on). The Adhan bit is not
     * used here: [silent] says which prayers have no Adhan.
     */
    val perPrayer: Map<PrayerEvent, PrayerSounds> = emptyMap(),
    /** Friday: Jumu'ah, 45 minutes before Dhuhr. */
    val friday: Boolean = false,
    /** In Ramadan: suhoor 45 minutes before Fajr, iftar at Maghrib. */
    val ramadan: Boolean = false,
    /** Friday morning at 9: Surat Al-Kahf. */
    val kahf: Boolean = false,
    /** The Fajr alarm (rings until stopped), this many minutes before Fajr; null = off. */
    val fajrAlarmMinutesBefore: Int? = null,
    /** A verse before sleep, at this many minutes after midnight (the city's time); null = off. */
    val bedtimeMinutes: Int? = null,
    /** Friday: Salli Ala Rasulillah, at this many minutes after midnight. */
    val salawat: Boolean = false,
    val salawatMinutes: Int = 11 * 60,
    /** Every day: the Qur'an reading reminder, at this many minutes after midnight. */
    val quran: Boolean = false,
    val quranMinutes: Int = 20 * 60 + 30,
    val reciter: Reciter = Reciter.ALAFASY,
    /** The first-run tour (Onboarding.kt) was finished; Settings → "Show welcome tour" turns it off to show it again. */
    val onboarded: Boolean = false,
    /** The PC's time zone for which the person chose to keep [cityId] (no more offers to change it for that zone). */
    val zoneKept: String? = null,
)

/**
 * The shared core's reminder settings for these choices. The one Adhan switch covers Fajr too, and [DesktopSettings.silent]
 * takes the Adhan off a prayer; no single early reminder ([AlertSettings.minutesBefore] stays 0).
 */
fun DesktopSettings.alerts() = AlertSettings(
    friday = friday, ramadan = ramadan, fajrAlarmMinutesBefore = fajrAlarmMinutesBefore, kahf = kahf, bedtimeMinutes = bedtimeMinutes,
    adhan = adhan, adhanSource = adhanSource, fajrAdhan = adhan, fajrAdhanSource = fajrAdhanSource, shortAdhan = shortAdhan,
    before10 = before10, before5 = before5, near = near, nearMinutes = nearMinutes, voice = voice,
    perPrayer = PrayerEvent.prayers.associateWith { e -> (perPrayer[e] ?: PrayerSounds()).copy(adhan = e !in silent) },
    salawat = salawat, salawatMinutes = salawatMinutes, quran = quran, quranMinutes = quranMinutes, reciter = reciter,
)

/**
 * Takes back the 10, 5 and "approaching" switches (for all prayers and for each) after a change made on [alerts].
 * The Adhan bit is not kept (it always reads as on): [DesktopSettings.silent] holds that choice. A prayer with
 * everything on is left out, as it means the same.
 */
fun DesktopSettings.withAlerts(a: AlertSettings) = copy(
    before10 = a.before10, before5 = a.before5, near = a.near,
    perPrayer = a.perPrayer.filterKeys { it.isPrayer }.mapValues { it.value.copy(adhan = true) }.filterValues { it != PrayerSounds() },
)

/**
 * The Adhan on or off for one prayer. On while the Adhan is off for every prayer: on for this prayer only (the others
 * silent), as the phone's switch does; here one switch covers Fajr too.
 */
fun DesktopSettings.withAdhan(e: PrayerEvent, on: Boolean): DesktopSettings = when {
    !on -> copy(silent = silent + e)
    adhan -> copy(silent = silent - e)
    else -> copy(adhan = true, silent = PrayerEvent.prayers.toSet() - e)
}

/**
 * The onboarding's Adhan choice, through the shared core's mapping (AlertSettings.with): a mosque turns the Adhan on
 * with its recording for every prayer, Fajr included, the short Adhan keeps the recordings, banner only turns the
 * Adhan off. One prayer's own switch ([DesktopSettings.silent]) is never touched.
 */
fun DesktopSettings.withAdhanChoice(c: AdhanChoice): DesktopSettings {
    val a = alerts().with(c)
    return copy(adhan = a.adhan, adhanSource = a.adhanSource, fajrAdhanSource = a.fajrAdhanSource, shortAdhan = a.shortAdhan)
}

/**
 * What Continue on the onboarding's Adhan step keeps when nothing was tapped: the [shown] choice, saved only when the
 * settings do not already say it, so a separate Fajr recording chosen in Settings stays as it is.
 */
fun DesktopSettings.keepingShownAdhan(shown: AdhanChoice): DesktopSettings =
    if (alerts().adhanChoice == shown) this else withAdhanChoice(shown)

object Store {
    /** Opened on first use, so tests that pass their own store never touch the registry or ~/.config. */
    private val prefs: Preferences by lazy {
        Platform.configDir?.let { FilePreferences(java.io.File(it, "settings.properties")) }
            ?: Preferences.userRoot().node("sa/zood/nearmosque")
    }

    fun load(): DesktopSettings = load(prefs)

    /** Every key reads with its default: a missing, unknown or out-of-range value never stops the app. */
    internal fun load(prefs: Preferences): DesktopSettings = DesktopSettings(
        cityId = prefs.getLong("cityId", -1).takeIf { it >= 0 },
        language = prefs.get("language", null) ?: Strings.systemLanguage(),
        method = prefs.get("method", null)?.let { runCatching { Method.valueOf(it) }.getOrNull() },
        madhab = runCatching { AsrMadhab.valueOf(prefs.get("madhab", "SHAFI")) }.getOrDefault(AsrMadhab.SHAFI),
        hijriAdjustment = prefs.getInt("hijriAdjustment", 0).coerceIn(-2, 2),
        highLatitudeRule = runCatching { HighLatRule.valueOf(prefs.get("highLatRule", "AUTO")) }.getOrDefault(HighLatRule.AUTO),
        polarRule = runCatching { PolarRule.valueOf(prefs.get("polarRule", "UNAVAILABLE")) }.getOrDefault(PolarRule.UNAVAILABLE),
        // "FAJR=2,SUNRISE=-1,ISHA=5"; an event not listed is not moved.
        offsets = eventMinutes(prefs.get("offsets", ""), -30..30).filterValues { it != 0 },
        ramadanIsha = prefs.getBoolean("ramadanIsha", true),
        adhan = prefs.getBoolean("adhan", true),
        adhanSource = runCatching { AdhanSource.valueOf(prefs.get("adhanSource", "MAKKAH")) }.getOrDefault(AdhanSource.MAKKAH),
        fajrAdhanSource = runCatching { AdhanSource.valueOf(prefs.get("fajrAdhanSource", "MAKKAH")) }.getOrDefault(AdhanSource.MAKKAH),
        shortAdhan = prefs.getBoolean("shortAdhan", false),
        before10 = prefs.getBoolean("before10", false),
        before5 = prefs.getBoolean("before5", false),
        sunrise = prefs.getBoolean("sunrise", false),
        silent = prefs.get("silent", "").split(',').mapNotNull { runCatching { PrayerEvent.valueOf(it) }.getOrNull() }.toSet(),
        onlineSearch = prefs.getBoolean("onlineSearch", true),
        skyWeather = prefs.getBoolean("skyWeather", false),
        weatherOffered = prefs.getBoolean("weatherOffered", false),
        startAtLogin = prefs.getBoolean("startAtLogin", true),
        iqama = IqamaSettings(
            enabled = prefs.getBoolean("iqama", true),
            // "FAJR=25,DHUHR=20,…"; a prayer not listed keeps its default wait.
            minutes = eventMinutes(prefs.get("iqamaMinutes", ""), 0..120),
            prayerMinutes = prefs.getInt("iqamaPrayerMinutes", 15).coerceIn(0, 60),
            alert = prefs.getBoolean("iqamaAlert", true),
        ),
        near = prefs.getBoolean("near", false),
        nearMinutes = prefs.getInt("nearMinutes", 15).takeIf { it in AlertSettings.nearChoices } ?: 15,
        voice = prefs.get("voice", null)?.let { runCatching { VoiceLanguage.valueOf(it) }.getOrNull() },
        perPrayer = perPrayer(prefs.get("perPrayer", "")),
        friday = prefs.getBoolean("fridayReminder", false),
        ramadan = prefs.getBoolean("ramadanReminders", false),
        kahf = prefs.getBoolean("kahfReminder", false),
        fajrAlarmMinutesBefore = prefs.getInt("fajrAlarm", -1).takeIf { it in 0..120 },
        bedtimeMinutes = prefs.getInt("bedtime", -1).takeIf { it in DAY_MINUTES },
        salawat = prefs.getBoolean("salawat", false),
        salawatMinutes = prefs.getInt("salawatMinutes", 11 * 60).takeIf { it in DAY_MINUTES } ?: (11 * 60),
        quran = prefs.getBoolean("quranReminder", false),
        quranMinutes = prefs.getInt("quranMinutes", 20 * 60 + 30).takeIf { it in DAY_MINUTES } ?: (20 * 60 + 30),
        reciter = runCatching { Reciter.valueOf(prefs.get("reciter", "ALAFASY")) }.getOrDefault(Reciter.ALAFASY),
        // Settings saved before the tour existed (any key at all) belong to someone who already uses the app: no tour.
        onboarded = prefs.get("onboarded", null)?.toBooleanStrictOrNull() ?: runCatching { prefs.keys().isNotEmpty() }.getOrDefault(false),
        zoneKept = prefs.get("zoneKept", null)?.takeIf { it.isNotBlank() },
    )

    /** Minutes after midnight, for the times of day the person picks. */
    private val DAY_MINUTES = 0..1439

    fun save(s: DesktopSettings) = save(s, prefs)

    internal fun save(s: DesktopSettings, prefs: Preferences) {
        if (s.cityId != null) prefs.putLong("cityId", s.cityId) else prefs.remove("cityId")
        prefs.put("language", s.language)
        if (s.method != null) prefs.put("method", s.method.name) else prefs.remove("method")
        prefs.put("madhab", s.madhab.name)
        prefs.putInt("hijriAdjustment", s.hijriAdjustment)
        prefs.put("highLatRule", s.highLatitudeRule.name)
        prefs.put("polarRule", s.polarRule.name)
        prefs.put("offsets", eventMinutesText(s.offsets.mapValues { it.value.coerceIn(-30, 30) }.filterValues { it != 0 }))
        prefs.putBoolean("ramadanIsha", s.ramadanIsha)
        prefs.putBoolean("adhan", s.adhan)
        prefs.put("adhanSource", s.adhanSource.name)
        prefs.put("fajrAdhanSource", s.fajrAdhanSource.name)
        prefs.putBoolean("shortAdhan", s.shortAdhan)
        prefs.putBoolean("before10", s.before10)
        prefs.putBoolean("before5", s.before5)
        prefs.putBoolean("sunrise", s.sunrise)
        prefs.put("silent", s.silent.joinToString(",") { it.name })
        prefs.putBoolean("onlineSearch", s.onlineSearch)
        prefs.putBoolean("skyWeather", s.skyWeather)
        prefs.putBoolean("weatherOffered", s.weatherOffered)
        prefs.putBoolean("startAtLogin", s.startAtLogin)
        prefs.putBoolean("iqama", s.iqama.enabled)
        prefs.put("iqamaMinutes", eventMinutesText(s.iqama.minutes))
        prefs.putInt("iqamaPrayerMinutes", s.iqama.prayerMinutes)
        prefs.putBoolean("iqamaAlert", s.iqama.alert)
        prefs.putBoolean("near", s.near)
        prefs.putInt("nearMinutes", s.nearMinutes)
        if (s.voice != null) prefs.put("voice", s.voice.name) else prefs.remove("voice")
        prefs.put("perPrayer", perPrayerText(s.perPrayer, s.silent))
        prefs.putBoolean("fridayReminder", s.friday)
        prefs.putBoolean("ramadanReminders", s.ramadan)
        prefs.putBoolean("kahfReminder", s.kahf)
        prefs.putInt("fajrAlarm", s.fajrAlarmMinutesBefore ?: -1)
        prefs.putInt("bedtime", s.bedtimeMinutes ?: -1)
        prefs.putBoolean("salawat", s.salawat)
        prefs.putInt("salawatMinutes", s.salawatMinutes)
        prefs.putBoolean("quranReminder", s.quran)
        prefs.putInt("quranMinutes", s.quranMinutes)
        prefs.put("reciter", s.reciter.name)
        prefs.putBoolean("onboarded", s.onboarded)
        if (s.zoneKept != null) prefs.put("zoneKept", s.zoneKept) else prefs.remove("zoneKept")
        // A settings store that cannot be written (a full disk, a locked file) keeps the choice for this run only.
        runCatching { prefs.flush() }
    }

    /** "FAJR=25,DHUHR=20" → minutes per event, each clamped to [range]; a malformed pair or unknown event is skipped. */
    private fun eventMinutes(text: String, range: IntRange): Map<PrayerEvent, Int> = text.split(',').mapNotNull { pair ->
        val (e, m) = pair.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
        val event = runCatching { PrayerEvent.valueOf(e.trim()) }.getOrNull() ?: return@mapNotNull null
        m.trim().toIntOrNull()?.let { event to it.coerceIn(range) }
    }.toMap()

    private fun eventMinutesText(m: Map<PrayerEvent, Int>): String = m.entries.joinToString(",") { "${it.key.name}=${it.value}" }

    /**
     * "FAJR=1011,ASR=1101": for each prayer listed, the Adhan, 10 minutes, 5 minutes and "approaching" bits (the phone
     * app's layout). The Adhan bit is written from [silent] and not read back. A malformed entry is skipped.
     */
    private fun perPrayer(text: String): Map<PrayerEvent, PrayerSounds> = text.split(',').mapNotNull { pair ->
        val (e, bits) = pair.split('=').map { it.trim() }.takeIf { it.size == 2 } ?: return@mapNotNull null
        val event = runCatching { PrayerEvent.valueOf(e) }.getOrNull()?.takeIf { it.isPrayer } ?: return@mapNotNull null
        if (bits.length != 4 || bits.any { it != '0' && it != '1' }) return@mapNotNull null
        event to PrayerSounds(adhan = true, before10 = bits[1] == '1', before5 = bits[2] == '1', near = bits[3] == '1')
    }.toMap()

    private fun perPrayerText(m: Map<PrayerEvent, PrayerSounds>, silent: Set<PrayerEvent>): String =
        m.entries.filter { it.key.isPrayer }.joinToString(",") { (e, s) ->
            fun bit(b: Boolean) = if (b) "1" else "0"
            e.name + "=" + bit(e !in silent) + bit(s.before10) + bit(s.before5) + bit(s.near)
        }

    /** %LOCALAPPDATA%\NearMosque on Windows, ~/.local/share/near-mosque on Linux (downloaded books). */
    val dataDir: File by lazy { Platform.dataDir.apply { mkdirs() } }
}
