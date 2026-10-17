package sa.zood.nearmosque.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import sa.zood.nearmosque.core.AdhanSource
import sa.zood.nearmosque.core.AsrMadhab
import sa.zood.nearmosque.core.HighLatRule
import sa.zood.nearmosque.core.IqamaSettings
import sa.zood.nearmosque.core.LatLng
import sa.zood.nearmosque.core.Method
import sa.zood.nearmosque.core.PolarRule
import sa.zood.nearmosque.core.PrayerEvent
import sa.zood.nearmosque.core.PrayerSettings
import sa.zood.nearmosque.core.PrayerSounds
import sa.zood.nearmosque.core.Reciter
import sa.zood.nearmosque.core.SilenceSettings
import sa.zood.nearmosque.core.VoiceLanguage
import java.time.ZoneId

/** The location prayer times are calculated for. Separate from the device position and map centre. */
data class PrayerLocation(
    val name: String,
    val location: LatLng,
    val zoneId: ZoneId,
    val countryCode: String?,
    val source: Source,
    val zoneConfirmed: Boolean = true,
    /** With [Source.DEVICE]: the nearest bundled city within 30 km (the one named), or null when none is that close. */
    val nearCity: LatLng? = null,
) {
    enum class Source { CITY, DEVICE }

    /**
     * Whose weather the sky shows ("Sky follows the weather" sends only a city's coordinates): the chosen city, or
     * with "Use my location" the city near the device; null when there is none (the sky stays normal).
     */
    val weatherLocation: LatLng? get() = if (source == Source.CITY) location else nearCity
}

data class AppSettings(
    val prayerLocation: PrayerLocation?,
    val followDevice: Boolean,
    val prayer: PrayerSettings,
    val methodChosenByUser: Boolean,
    val reminders: Set<PrayerEvent>,
    val mosqueView: String,
    val onboarded: Boolean = false,
    /** Live mosque results (OpenStreetMap via Overpass) added to downloaded data; disclosed in onboarding and Settings. */
    val onlineSearch: Boolean = true,
    /** Early, Friday and Ramadan reminders and the Fajr alarm. */
    val alerts: sa.zood.nearmosque.core.AlertSettings = sa.zood.nearmosque.core.AlertSettings(),
    /** The wait for the iqama after each Adhan, and the prayer after it. */
    val iqama: IqamaSettings = IqamaSettings(),
    /** Do Not Disturb during each prayer (off until the user turns it on). */
    val silence: SilenceSettings = SilenceSettings(),
    /** Sky follows the weather (opt-in): the chosen city's weather from MET Norway on the sky. Off until turned on. */
    val skyWeather: Boolean = false,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** [store] is injectable so tests get an isolated file (DataStore allows one instance per file). */
class SettingsStore(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.dataStore)

    private object K {
        val locName = stringPreferencesKey("loc_name")
        val locLat = doublePreferencesKey("loc_lat")
        val locLng = doublePreferencesKey("loc_lng")
        val locZone = stringPreferencesKey("loc_zone")
        val locCountry = stringPreferencesKey("loc_country")
        val locSource = stringPreferencesKey("loc_source")
        val locZoneConfirmed = booleanPreferencesKey("loc_zone_confirmed")
        val locCityLat = doublePreferencesKey("loc_city_lat")
        val locCityLng = doublePreferencesKey("loc_city_lng")
        val follow = booleanPreferencesKey("follow_device")
        val method = stringPreferencesKey("method")
        val methodByUser = booleanPreferencesKey("method_by_user")
        val madhab = stringPreferencesKey("madhab")
        val highLat = stringPreferencesKey("high_lat")
        val polar = stringPreferencesKey("polar")
        val ramadan = booleanPreferencesKey("ramadan_isha")
        val hijriAdj = intPreferencesKey("hijri_adj")
        val reminders = stringSetPreferencesKey("reminders")
        val bookmarks = stringSetPreferencesKey("library_bookmarks")
        val mosqueView = stringPreferencesKey("mosque_view")
        val onboarded = booleanPreferencesKey("onboarded")
        val onlineSearch = booleanPreferencesKey("online_search")
        /** Sky follows the weather; missing (older saves) = off. */
        val skyWeather = booleanPreferencesKey("sky_weather")
        val alertBefore = intPreferencesKey("alert_before")
        val alertFriday = booleanPreferencesKey("alert_friday")
        val alertRamadan = booleanPreferencesKey("alert_ramadan")
        /** Minutes before Fajr for the alarm; -1 (or missing) = off. */
        val alertFajr = intPreferencesKey("alert_fajr_alarm")
        val alertKahf = booleanPreferencesKey("alert_kahf")
        /** Minutes after midnight; -1 (or missing) = off. */
        val alertBedtime = intPreferencesKey("alert_bedtime")
        val adhan = booleanPreferencesKey("audio_adhan")
        val adhanSource = stringPreferencesKey("audio_adhan_source")
        val fajrAdhan = booleanPreferencesKey("audio_fajr_adhan")
        val fajrAdhanSource = stringPreferencesKey("audio_fajr_adhan_source")
        val shortAdhan = booleanPreferencesKey("audio_short_adhan")
        val before10 = booleanPreferencesKey("audio_before10")
        val before5 = booleanPreferencesKey("audio_before5")
        val near = booleanPreferencesKey("audio_near")
        val nearMinutes = intPreferencesKey("audio_near_minutes")
        val voice = stringPreferencesKey("audio_voice")
        /** "FAJR:1011,DHUHR:1111": adhan, 10 min, 5 min, approaching for each prayer listed. */
        val perPrayer = stringPreferencesKey("audio_per_prayer")
        val salawat = booleanPreferencesKey("audio_salawat")
        val salawatMinutes = intPreferencesKey("audio_salawat_minutes")
        val quran = booleanPreferencesKey("audio_quran")
        val quranMinutes = intPreferencesKey("audio_quran_minutes")
        val reciter = stringPreferencesKey("audio_reciter")
        val iqamaOn = booleanPreferencesKey("iqama_on")
        /** "FAJR:25,MAGHRIB:10": minutes from the Adhan to the iqama for each prayer listed. */
        val iqamaMinutes = stringPreferencesKey("iqama_minutes")
        val iqamaPrayer = intPreferencesKey("iqama_prayer_minutes")
        val iqamaAlert = booleanPreferencesKey("iqama_alert")
        val silenceOn = booleanPreferencesKey("silence_on")
        /** "ADHAN" or "IQAMA". */
        val silenceStart = stringPreferencesKey("silence_start")
        /** The prayers silenced (names); missing = all five. */
        val silencePrayers = stringSetPreferencesKey("silence_prayers")
        val silenceFriday = intPreferencesKey("silence_friday_minutes")
        fun offset(e: PrayerEvent) = intPreferencesKey("offset_${e.name}")
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        val loc = run {
            val lat = p[K.locLat]
            val lng = p[K.locLng]
            val zone = p[K.locZone]?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            val ll = LatLng.orNull(lat, lng)
            if (ll != null && zone != null) {
                PrayerLocation(
                    p[K.locName] ?: "", ll, zone, p[K.locCountry],
                    if (p[K.locSource] == "DEVICE") PrayerLocation.Source.DEVICE else PrayerLocation.Source.CITY,
                    p[K.locZoneConfirmed] ?: true,
                    LatLng.orNull(p[K.locCityLat], p[K.locCityLng]),
                )
            } else null
        }
        AppSettings(
            prayerLocation = loc,
            followDevice = p[K.follow] ?: false,
            prayer = PrayerSettings(
                method = p[K.method]?.let { runCatching { Method.valueOf(it) }.getOrNull() } ?: Method.MUSLIM_WORLD_LEAGUE,
                madhab = p[K.madhab]?.let { runCatching { AsrMadhab.valueOf(it) }.getOrNull() } ?: AsrMadhab.SHAFI,
                highLatitudeRule = p[K.highLat]?.let { runCatching { HighLatRule.valueOf(it) }.getOrNull() } ?: HighLatRule.AUTO,
                polarRule = p[K.polar]?.let { runCatching { PolarRule.valueOf(it) }.getOrNull() } ?: PolarRule.UNAVAILABLE,
                offsets = PrayerEvent.entries.associateWith { p[K.offset(it)] ?: 0 }.filterValues { it != 0 },
                ramadanIshaExtension = p[K.ramadan] ?: true,
                hijriAdjustmentDays = p[K.hijriAdj] ?: 0,
            ),
            methodChosenByUser = p[K.methodByUser] ?: false,
            reminders = p[K.reminders].orEmpty().mapNotNull { runCatching { PrayerEvent.valueOf(it) }.getOrNull() }.toSet(),
            mosqueView = p[K.mosqueView] ?: "compass",
            onboarded = p[K.onboarded] ?: false,
            onlineSearch = p[K.onlineSearch] ?: true,
            skyWeather = p[K.skyWeather] ?: false,
            alerts = sa.zood.nearmosque.core.AlertSettings(
                minutesBefore = p[K.alertBefore] ?: 0,
                friday = p[K.alertFriday] ?: false,
                ramadan = p[K.alertRamadan] ?: false,
                fajrAlarmMinutesBefore = p[K.alertFajr]?.takeIf { it >= 0 },
                kahf = p[K.alertKahf] ?: false,
                bedtimeMinutes = p[K.alertBedtime]?.takeIf { it >= 0 },
                adhan = p[K.adhan] ?: false,
                adhanSource = enumOr(p[K.adhanSource], AdhanSource.MAKKAH),
                fajrAdhan = p[K.fajrAdhan] ?: false,
                fajrAdhanSource = enumOr(p[K.fajrAdhanSource], AdhanSource.MAKKAH),
                // Saves from before the short Adhan have no key and keep the whole one.
                shortAdhan = p[K.shortAdhan] ?: false,
                before10 = p[K.before10] ?: false,
                before5 = p[K.before5] ?: false,
                near = p[K.near] ?: false,
                nearMinutes = p[K.nearMinutes] ?: 15,
                voice = p[K.voice]?.let { v -> VoiceLanguage.entries.firstOrNull { it.name == v } },
                perPrayer = decodePerPrayer(p[K.perPrayer]),
                salawat = p[K.salawat] ?: false,
                salawatMinutes = p[K.salawatMinutes] ?: (11 * 60),
                quran = p[K.quran] ?: false,
                quranMinutes = p[K.quranMinutes] ?: (20 * 60 + 30),
                reciter = enumOr(p[K.reciter], Reciter.ALAFASY),
            ).migrated(),
            // Saves from before the iqama have none of these keys and get the usual waits.
            iqama = IqamaSettings(
                enabled = p[K.iqamaOn] ?: true,
                minutes = decodeIqamaMinutes(p[K.iqamaMinutes]),
                prayerMinutes = p[K.iqamaPrayer] ?: 15,
                alert = p[K.iqamaAlert] ?: true,
            ),
            silence = SilenceSettings(
                enabled = p[K.silenceOn] ?: false,
                start = enumOr(p[K.silenceStart], SilenceSettings.Start.IQAMA),
                prayers = p[K.silencePrayers]?.let(::decodePrayers) ?: PrayerEvent.prayers.toSet(),
                fridayMinutes = p[K.silenceFriday]?.takeIf { it > 0 } ?: 60,
            ),
        )
    }

    suspend fun setSilence(s: SilenceSettings) = store.edit { p ->
        p[K.silenceOn] = s.enabled
        p[K.silenceStart] = s.start.name
        p[K.silencePrayers] = s.prayers.filter { it.isPrayer }.map { it.name }.toSet()
        p[K.silenceFriday] = s.fridayMinutes
    }

    suspend fun setIqama(q: IqamaSettings) = store.edit { p ->
        p[K.iqamaOn] = q.enabled
        p[K.iqamaMinutes] = encodeIqamaMinutes(q.minutes)
        p[K.iqamaPrayer] = q.prayerMinutes
        p[K.iqamaAlert] = q.alert
    }

    suspend fun setAlerts(a: sa.zood.nearmosque.core.AlertSettings) = store.edit { p ->
        p[K.alertBefore] = a.minutesBefore
        p[K.alertFriday] = a.friday
        p[K.alertRamadan] = a.ramadan
        p[K.alertFajr] = a.fajrAlarmMinutesBefore ?: -1
        p[K.alertKahf] = a.kahf
        p[K.alertBedtime] = a.bedtimeMinutes ?: -1
        p[K.adhan] = a.adhan
        p[K.adhanSource] = a.adhanSource.name
        p[K.fajrAdhan] = a.fajrAdhan
        p[K.fajrAdhanSource] = a.fajrAdhanSource.name
        p[K.shortAdhan] = a.shortAdhan
        p[K.before10] = a.before10
        p[K.before5] = a.before5
        p[K.near] = a.near
        p[K.nearMinutes] = a.nearMinutes
        a.voice?.let { p[K.voice] = it.name } ?: p.remove(K.voice)
        p[K.perPrayer] = encodePerPrayer(a.perPrayer)
        p[K.salawat] = a.salawat
        p[K.salawatMinutes] = a.salawatMinutes
        p[K.quran] = a.quran
        p[K.quranMinutes] = a.quranMinutes
        p[K.reciter] = a.reciter.name
    }

    companion object {
        private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
            enumValues<T>().firstOrNull { it.name == name } ?: fallback

        private fun bit(b: Boolean) = if (b) '1' else '0'

        fun encodePerPrayer(m: Map<PrayerEvent, PrayerSounds>): String = m.entries.joinToString(",") { (e, s) ->
            e.name + ":" + bit(s.adhan) + bit(s.before10) + bit(s.before5) + bit(s.near)
        }

        fun decodePerPrayer(v: String?): Map<PrayerEvent, PrayerSounds> = v.orEmpty().split(",").mapNotNull { part ->
            val (name, bits) = part.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
            val e = PrayerEvent.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
            if (bits.length != 4) return@mapNotNull null
            e to PrayerSounds(bits[0] == '1', bits[1] == '1', bits[2] == '1', bits[3] == '1')
        }.toMap()

        /** Prayer names saved as a set; unknown names and sunrise are dropped. */
        fun decodePrayers(names: Set<String>): Set<PrayerEvent> =
            names.mapNotNull { n -> PrayerEvent.entries.firstOrNull { it.name == n && it.isPrayer } }.toSet()

        fun encodeIqamaMinutes(m: Map<PrayerEvent, Int>): String = m.entries.joinToString(",") { (e, v) -> e.name + ":" + v }

        fun decodeIqamaMinutes(v: String?): Map<PrayerEvent, Int> = v.orEmpty().split(",").mapNotNull { part ->
            val (name, minutes) = part.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
            val e = PrayerEvent.entries.firstOrNull { it.name == name && it.isPrayer } ?: return@mapNotNull null
            e to (minutes.toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null)
        }.toMap()
    }

    suspend fun setPrayerLocation(loc: PrayerLocation, follow: Boolean) = store.edit { p ->
        p[K.locName] = loc.name
        p[K.locLat] = loc.location.latitude
        p[K.locLng] = loc.location.longitude
        p[K.locZone] = loc.zoneId.id
        loc.countryCode?.let { p[K.locCountry] = it } ?: p.remove(K.locCountry)
        p[K.locSource] = loc.source.name
        p[K.locZoneConfirmed] = loc.zoneConfirmed
        val city = loc.nearCity
        if (city != null) {
            p[K.locCityLat] = city.latitude
            p[K.locCityLng] = city.longitude
        } else {
            p.remove(K.locCityLat)
            p.remove(K.locCityLng)
        }
        p[K.follow] = follow
    }

    suspend fun setMethod(method: Method, byUser: Boolean) = store.edit {
        it[K.method] = method.name
        if (byUser) it[K.methodByUser] = true
    }

    suspend fun setMadhab(m: AsrMadhab) = store.edit { it[K.madhab] = m.name }
    suspend fun setHighLatitudeRule(r: HighLatRule) = store.edit { it[K.highLat] = r.name }
    suspend fun setPolarRule(r: PolarRule) = store.edit { it[K.polar] = r.name }
    suspend fun setRamadanIsha(on: Boolean) = store.edit { it[K.ramadan] = on }
    suspend fun setHijriAdjustment(days: Int) = store.edit { it[K.hijriAdj] = days.coerceIn(-2, 2) }
    suspend fun setOffset(e: PrayerEvent, minutes: Int) = store.edit { it[K.offset(e)] = minutes.coerceIn(-30, 30) }
    suspend fun setMosqueView(v: String) = store.edit { it[K.mosqueView] = v }

    /** Saved library items (chunk ids), kept on this device only. */
    val bookmarks: Flow<Set<String>> = store.data.map { it[K.bookmarks].orEmpty() }
    suspend fun setBookmark(id: String, on: Boolean) = store.edit { p ->
        val cur = p[K.bookmarks].orEmpty()
        p[K.bookmarks] = if (on) cur + id else cur - id
    }
    suspend fun setOnboarded(on: Boolean) = store.edit { it[K.onboarded] = on }
    suspend fun setOnlineSearch(on: Boolean) = store.edit { it[K.onlineSearch] = on }
    suspend fun setSkyWeather(on: Boolean) = store.edit { it[K.skyWeather] = on }
    /** The prayer bells all at once (the onboarding turns on the five prayers). */
    suspend fun setReminders(events: Set<PrayerEvent>) = store.edit { it[K.reminders] = events.map { e -> e.name }.toSet() }
    suspend fun setReminder(e: PrayerEvent, on: Boolean) = store.edit { p ->
        val cur = p[K.reminders].orEmpty().toMutableSet()
        if (on) cur += e.name else cur -= e.name
        p[K.reminders] = cur
    }
}
