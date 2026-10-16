package sa.zood.nearmosque.desktop

import sa.zood.nearmosque.core.AdhanSource
import sa.zood.nearmosque.core.AsrMadhab
import sa.zood.nearmosque.core.IqamaSettings
import sa.zood.nearmosque.core.Method
import sa.zood.nearmosque.core.PrayerEvent
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
    /** Open in the notification area (or minimized, where there is none) when the person signs in. */
    val startAtLogin: Boolean = true,
    /** The wait from each Adhan to the iqama, the prayer after it, and the notification at the iqama. */
    val iqama: IqamaSettings = IqamaSettings(),
)

object Store {
    private val prefs: Preferences =
        Platform.configDir?.let { FilePreferences(java.io.File(it, "settings.properties")) }
            ?: Preferences.userRoot().node("sa/zood/nearmosque")

    fun load(): DesktopSettings = DesktopSettings(
        cityId = prefs.getLong("cityId", -1).takeIf { it >= 0 },
        language = prefs.get("language", null) ?: Strings.systemLanguage(),
        method = prefs.get("method", null)?.let { runCatching { Method.valueOf(it) }.getOrNull() },
        madhab = runCatching { AsrMadhab.valueOf(prefs.get("madhab", "SHAFI")) }.getOrDefault(AsrMadhab.SHAFI),
        hijriAdjustment = prefs.getInt("hijriAdjustment", 0).coerceIn(-2, 2),
        adhan = prefs.getBoolean("adhan", true),
        adhanSource = runCatching { AdhanSource.valueOf(prefs.get("adhanSource", "MAKKAH")) }.getOrDefault(AdhanSource.MAKKAH),
        fajrAdhanSource = runCatching { AdhanSource.valueOf(prefs.get("fajrAdhanSource", "MAKKAH")) }.getOrDefault(AdhanSource.MAKKAH),
        shortAdhan = prefs.getBoolean("shortAdhan", false),
        before10 = prefs.getBoolean("before10", false),
        before5 = prefs.getBoolean("before5", false),
        sunrise = prefs.getBoolean("sunrise", false),
        silent = prefs.get("silent", "").split(',').mapNotNull { runCatching { PrayerEvent.valueOf(it) }.getOrNull() }.toSet(),
        onlineSearch = prefs.getBoolean("onlineSearch", true),
        startAtLogin = prefs.getBoolean("startAtLogin", true),
        iqama = IqamaSettings(
            enabled = prefs.getBoolean("iqama", true),
            // "FAJR=25,DHUHR=20,…"; a prayer not listed keeps its default wait.
            minutes = prefs.get("iqamaMinutes", "").split(',').mapNotNull { pair ->
                val (e, m) = pair.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
                val event = runCatching { PrayerEvent.valueOf(e) }.getOrNull() ?: return@mapNotNull null
                m.toIntOrNull()?.let { event to it.coerceIn(0, 120) }
            }.toMap(),
            prayerMinutes = prefs.getInt("iqamaPrayerMinutes", 15).coerceIn(0, 60),
            alert = prefs.getBoolean("iqamaAlert", true),
        ),
    )

    fun save(s: DesktopSettings) {
        if (s.cityId != null) prefs.putLong("cityId", s.cityId) else prefs.remove("cityId")
        prefs.put("language", s.language)
        if (s.method != null) prefs.put("method", s.method.name) else prefs.remove("method")
        prefs.put("madhab", s.madhab.name)
        prefs.putInt("hijriAdjustment", s.hijriAdjustment)
        prefs.putBoolean("adhan", s.adhan)
        prefs.put("adhanSource", s.adhanSource.name)
        prefs.put("fajrAdhanSource", s.fajrAdhanSource.name)
        prefs.putBoolean("shortAdhan", s.shortAdhan)
        prefs.putBoolean("before10", s.before10)
        prefs.putBoolean("before5", s.before5)
        prefs.putBoolean("sunrise", s.sunrise)
        prefs.put("silent", s.silent.joinToString(",") { it.name })
        prefs.putBoolean("onlineSearch", s.onlineSearch)
        prefs.putBoolean("startAtLogin", s.startAtLogin)
        prefs.putBoolean("iqama", s.iqama.enabled)
        prefs.put("iqamaMinutes", s.iqama.minutes.entries.joinToString(",") { "${it.key.name}=${it.value}" })
        prefs.putInt("iqamaPrayerMinutes", s.iqama.prayerMinutes)
        prefs.putBoolean("iqamaAlert", s.iqama.alert)
        // A settings store that cannot be written (a full disk, a locked file) keeps the choice for this run only.
        runCatching { prefs.flush() }
    }

    /** %LOCALAPPDATA%\NearMosque on Windows, ~/.local/share/near-mosque on Linux (downloaded books). */
    val dataDir: File by lazy { Platform.dataDir.apply { mkdirs() } }
}
