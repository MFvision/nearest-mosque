package sa.zood.nearmosque.desktop

import sa.zood.nearmosque.core.AdhanSource
import sa.zood.nearmosque.core.AsrMadhab
import sa.zood.nearmosque.core.Method
import sa.zood.nearmosque.core.PrayerEvent
import java.io.File
import java.util.prefs.Preferences

/** What the person chose, kept in the Windows user registry (java.util.prefs); nothing leaves the PC. */
data class DesktopSettings(
    val cityId: Long? = null,
    val language: String = "en",
    val method: Method? = null,
    val madhab: AsrMadhab = AsrMadhab.SHAFI,
    val hijriAdjustment: Int = 0,
    val adhan: Boolean = true,
    val adhanSource: AdhanSource = AdhanSource.MAKKAH,
    val fajrAdhanSource: AdhanSource = AdhanSource.MAKKAH,
    val before10: Boolean = false,
    val sunrise: Boolean = false,
    /** Prayers whose Adhan is off (the notification still shows). */
    val silent: Set<PrayerEvent> = emptySet(),
)

object Store {
    private val prefs: Preferences = Preferences.userRoot().node("sa/zood/nearmosque")

    fun load(): DesktopSettings = DesktopSettings(
        cityId = prefs.getLong("cityId", -1).takeIf { it >= 0 },
        language = prefs.get("language", null) ?: Strings.systemLanguage(),
        method = prefs.get("method", null)?.let { runCatching { Method.valueOf(it) }.getOrNull() },
        madhab = runCatching { AsrMadhab.valueOf(prefs.get("madhab", "SHAFI")) }.getOrDefault(AsrMadhab.SHAFI),
        hijriAdjustment = prefs.getInt("hijriAdjustment", 0).coerceIn(-2, 2),
        adhan = prefs.getBoolean("adhan", true),
        adhanSource = runCatching { AdhanSource.valueOf(prefs.get("adhanSource", "MAKKAH")) }.getOrDefault(AdhanSource.MAKKAH),
        fajrAdhanSource = runCatching { AdhanSource.valueOf(prefs.get("fajrAdhanSource", "MAKKAH")) }.getOrDefault(AdhanSource.MAKKAH),
        before10 = prefs.getBoolean("before10", false),
        sunrise = prefs.getBoolean("sunrise", false),
        silent = prefs.get("silent", "").split(',').mapNotNull { runCatching { PrayerEvent.valueOf(it) }.getOrNull() }.toSet(),
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
        prefs.putBoolean("before10", s.before10)
        prefs.putBoolean("sunrise", s.sunrise)
        prefs.put("silent", s.silent.joinToString(",") { it.name })
        prefs.flush()
    }

    /** %LOCALAPPDATA%\NearMosque (downloaded books); the user's home folder elsewhere. */
    val dataDir: File by lazy {
        val base = System.getenv("LOCALAPPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".local/share")
        File(base, "NearMosque").apply { mkdirs() }
    }
}
