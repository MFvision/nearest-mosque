package sa.zood.nearmosque.ui.prayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sa.zood.nearmosque.AppContainer
import sa.zood.nearmosque.core.AsrMadhab
import sa.zood.nearmosque.core.City
import sa.zood.nearmosque.core.DayArc
import sa.zood.nearmosque.core.DaySchedule
import sa.zood.nearmosque.core.HighLatRule
import sa.zood.nearmosque.core.LatLng
import sa.zood.nearmosque.core.Method
import sa.zood.nearmosque.core.PolarRule
import sa.zood.nearmosque.core.PrayerCalculator
import sa.zood.nearmosque.core.PrayerEvent
import sa.zood.nearmosque.core.PrayerMoment
import sa.zood.nearmosque.core.Qibla
import sa.zood.nearmosque.core.Upcoming
import sa.zood.nearmosque.core.Zones
import sa.zood.nearmosque.core.moment
import sa.zood.nearmosque.data.AppSettings
import sa.zood.nearmosque.data.PrayerLocation
import sa.zood.nearmosque.platform.LocationPermission
import sa.zood.nearmosque.platform.ReminderScheduler
import sa.zood.nearmosque.ui.theme.SkyPeriod
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong

data class PrayerUi(
    val settings: AppSettings? = null,
    val location: PrayerLocation? = null,
    val now: Instant = Instant.EPOCH,
    val today: DaySchedule? = null,
    val tomorrow: DaySchedule? = null,
    val next: Upcoming? = null,
    /** What the header shows: the countdown to the next Adhan, the wait for the iqama, or the prayer itself. */
    val moment: PrayerMoment? = null,
    /** The prayer period in progress (for the sky and the arc), null before today's Fajr. */
    val current: PrayerEvent? = null,
    val fajrEndsAt: Instant? = null,
    val qiblaBearing: Double? = null,
    val qiblaDistance: Double? = null,
    val sky: SkyPeriod = SkyPeriod.NIGHT,
    /** Sunrise-to-Maghrib (or night) path for the sky card; null without a location or on polar days. */
    val arc: DayArc? = null,
    /** Nearest downloaded mosque to the phone (or to the prayer city). */
    val nearestMosque: sa.zood.nearmosque.core.RankedMosque? = null,
    /** The prayer city's weather for the sky, when "Sky follows the weather" is on and a recent forecast is cached. */
    val weather: sa.zood.nearmosque.core.WeatherNow? = null,
)

sealed interface LocateState {
    data object Idle : LocateState
    data object Locating : LocateState
    data object Denied : LocateState
    data object NoFix : LocateState
    data object LocationOff : LocateState
}

class PrayerViewModel(private val c: AppContainer) : ViewModel() {
    private val calc = c.calculator
    private var cacheKey: Any? = null
    private var cached: List<DaySchedule> = emptyList()

    private val ticker = flow {
        while (true) {
            emit(c.clock())
            // Wake on the next whole second; the countdown is recomputed from instants each time.
            delay(1000 - (c.clock().toEpochMilli() % 1000))
        }
    }

    private val nearest = MutableStateFlow<sa.zood.nearmosque.core.RankedMosque?>(null)
    private var nearestKey: String? = null

    // Re-read every tick: the weather falls back to the normal sky by itself once the forecast is too old.
    val ui: StateFlow<PrayerUi> = combine(c.settings.settings, ticker, nearest, c.weather.cache) { s, now, n, wc ->
        build(s, now).copy(nearestMosque = n, weather = weatherNow(s, wc, now))
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrayerUi())

    /** Looks up the nearest downloaded mosque when the phone (or the prayer city) has moved ~100 m. */
    fun refreshNearestMosque(lang: String) {
        viewModelScope.launch {
            c.ready.first { it }
            val center = c.devicePosition.value?.location ?: c.settings.settings.first().prayerLocation?.location ?: return@launch
            val key = "${(center.latitude * 1000).roundToLong()}/${(center.longitude * 1000).roundToLong()}"
            if (key == nearestKey) return@launch
            nearestKey = key
            val r = c.mosques.nearest(center, 25_000.0, lang)
            nearest.value = (r as? sa.zood.nearmosque.data.MosqueResult.Found)?.items?.firstOrNull()
        }
    }

    private val _locate = MutableStateFlow<LocateState>(LocateState.Idle)
    val locate: StateFlow<LocateState> = _locate.asStateFlow()

    private fun build(s: AppSettings, now: Instant): PrayerUi {
        val loc = s.prayerLocation ?: return PrayerUi(settings = s, now = now)
        val zone = loc.zoneId
        val today = now.atZone(zone).toLocalDate()
        val key = listOf(loc.location, zone, s.prayer, today)
        if (key != cacheKey) {
            cached = (-1L..1L).map { calc.schedule(loc.location, today.plusDays(it), zone, s.prayer) }
            cacheKey = key
        }
        val moment = calc.moment(cached, now, s.iqama)
        val next = moment?.next
        val todaySchedule = cached[1]
        val current = PrayerEvent.entries.lastOrNull { e -> todaySchedule[e]?.let { !it.isAfter(now) } == true }
            ?: if (cached[0][PrayerEvent.ISHA] != null) PrayerEvent.ISHA else null
        return PrayerUi(
            settings = s, location = loc, now = now, today = todaySchedule, tomorrow = cached[2], next = next, moment = moment,
            current = current, fajrEndsAt = calc.fajrEndsAt(todaySchedule, now),
            qiblaBearing = Qibla.bearing(loc.location), qiblaDistance = Qibla.distanceMeters(loc.location),
            sky = SkyPeriod.at(now, todaySchedule[PrayerEvent.SUNRISE], current, hasLocation = true),
            arc = DayArc.at(now, cached),
        )
    }

    fun chooseCity(city: City, displayName: String) = viewModelScope.launch {
        val s = c.settings.settings.first()
        // A zone this phone's tz data doesn't know: the phone's own, and the time zone card asks.
        val zone = Zones.of(city.zoneId)
        c.settings.setPrayerLocation(
            PrayerLocation(
                displayName, city.location, zone ?: ZoneId.systemDefault(), city.countryCode, PrayerLocation.Source.CITY,
                zoneConfirmed = zone != null,
            ),
            follow = false,
        )
        if (!s.methodChosenByUser) c.settings.setMethod(PrayerCalculator.suggestedMethod(city.countryCode), byUser = false)
    }

    /**
     * Called after the permission dialog (with [asked] false, nothing opens the location settings); uses a fresh fix,
     * falls back to a recent one, never to a default city. "Follow my location" refreshes by itself each time the
     * app comes to the screen (MainActivity, AppContainer.refreshFollowedPlace).
     */
    fun useDeviceLocation(nameForCurrent: String, asked: Boolean = true) = viewModelScope.launch {
        if (c.location.permission() == LocationPermission.DENIED) { _locate.value = LocateState.Denied; return@launch }
        if (!c.location.isLocationEnabled()) {
            _locate.value = LocateState.LocationOff
            if (asked) c.location.openLocationSettings()
            return@launch
        }
        _locate.value = LocateState.Locating
        val fix = c.location.currentPosition() ?: c.location.lastKnown(30 * 60_000L)
        if (fix == null) { _locate.value = LocateState.NoFix; return@launch }
        c.followFix(fix, nameForCurrent)
        _locate.value = LocateState.Idle
    }

    fun confirmZone() = viewModelScope.launch {
        val s = c.settings.settings.first()
        s.prayerLocation?.let { c.settings.setPrayerLocation(it.copy(zoneConfirmed = true), s.followDevice) }
    }

    fun setZone(zone: ZoneId) = viewModelScope.launch {
        val s = c.settings.settings.first()
        s.prayerLocation?.let { c.settings.setPrayerLocation(it.copy(zoneId = zone, zoneConfirmed = true), s.followDevice) }
    }

    fun setOnboarded(on: Boolean) = viewModelScope.launch { c.settings.setOnboarded(on) }
    fun setOnlineSearch(on: Boolean) = viewModelScope.launch { c.settings.setOnlineSearch(on) }
    fun setSkyWeather(on: Boolean) = viewModelScope.launch { c.settings.setSkyWeather(on) }

    fun clearLocateState() { _locate.value = LocateState.Idle }

    fun setMethod(m: Method) = viewModelScope.launch { c.settings.setMethod(m, byUser = true) }
    fun setMadhab(m: AsrMadhab) = viewModelScope.launch { c.settings.setMadhab(m) }
    fun setHighLat(r: HighLatRule) = viewModelScope.launch { c.settings.setHighLatitudeRule(r) }
    fun setPolar(r: PolarRule) = viewModelScope.launch { c.settings.setPolarRule(r) }
    fun setRamadanIsha(on: Boolean) = viewModelScope.launch { c.settings.setRamadanIsha(on) }
    fun setHijriAdjustment(d: Int) = viewModelScope.launch { c.settings.setHijriAdjustment(d) }
    fun setOffset(e: PrayerEvent, m: Int) = viewModelScope.launch { c.settings.setOffset(e, m) }

    // The alarms follow every saved change by themselves (NearMosqueApp); a rebuild is asked for here only when a
    // permission changed, which no setting shows.
    fun setReminder(e: PrayerEvent, on: Boolean) = viewModelScope.launch {
        c.settings.setReminder(e, on)
    }

    fun rescheduleReminders(context: android.content.Context) = viewModelScope.launch(Dispatchers.Default) {
        ReminderScheduler(context).reschedule(c.settings.settings.first())
    }

    /** Saves the Adhan and Friday choices, without asking for notifications (the onboarding asks once, at the end). */
    fun setAlerts(a: sa.zood.nearmosque.core.AlertSettings) = viewModelScope.launch {
        c.settings.setAlerts(a)
    }

    /** Silent during prayer; Do Not Disturb access is asked for by the caller. */
    fun setSilence(context: android.content.Context, s: sa.zood.nearmosque.core.SilenceSettings) = viewModelScope.launch {
        c.settings.setSilence(s)
        withContext(Dispatchers.Default) { sa.zood.nearmosque.platform.PrayerSilence(context).sync(c.settings.settings.first()) }
    }

    /**
     * "Notify me at prayer times": the five prayers' bells, only for someone who has none yet (nothing is turned off),
     * then a rebuild in any case, since it follows the answer to the notification permission.
     */
    fun enablePrayerBells(context: android.content.Context) = viewModelScope.launch {
        if (c.settings.settings.first().reminders.isEmpty()) c.settings.setReminders(PrayerEvent.prayers.toSet())
        withContext(Dispatchers.Default) { ReminderScheduler(context).reschedule(c.settings.settings.first()) }
    }

    companion object {
        /**
         * What the sky shows: only with the switch on, for the prayer city (with "Use my location", the city near
         * the device: PrayerLocation.weatherLocation), from a cache MET confirmed recently.
         */
        fun weatherNow(s: AppSettings, cache: sa.zood.nearmosque.core.WeatherCache?, now: Instant): sa.zood.nearmosque.core.WeatherNow? {
            val place = s.prayerLocation?.weatherLocation ?: return null
            if (!s.skyWeather) return null
            return sa.zood.nearmosque.core.Weather.now(cache, sa.zood.nearmosque.core.Weather.key(place), now)
        }

        fun dateOf(instant: Instant, zone: ZoneId): LocalDate = instant.atZone(zone).toLocalDate()
        fun isSameLocation(a: LatLng?, b: LatLng?) = a == b
    }
}
