package sa.zood.nearmosque.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import sa.zood.nearmosque.core.AdhanSource
import sa.zood.nearmosque.core.AlertKind
import sa.zood.nearmosque.core.AlertSettings
import sa.zood.nearmosque.core.AsrMadhab
import sa.zood.nearmosque.core.City
import sa.zood.nearmosque.core.HighLatRule
import sa.zood.nearmosque.core.HijriCalendar
import sa.zood.nearmosque.core.IqamaSettings
import sa.zood.nearmosque.core.Method
import sa.zood.nearmosque.core.PolarRule
import sa.zood.nearmosque.core.PrayerCalculator
import sa.zood.nearmosque.core.PrayerEvent
import sa.zood.nearmosque.core.PrayerMoment
import sa.zood.nearmosque.core.Qibla
import sa.zood.nearmosque.core.Reciter
import sa.zood.nearmosque.core.ScheduleStatus
import sa.zood.nearmosque.core.VoiceLanguage
import sa.zood.nearmosque.core.Weather
import sa.zood.nearmosque.core.WeatherNow
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

internal val Navy = Color(0xFF0B1220)
internal val Card = Color(0xFF16213A)
internal val Gold = Color(0xFFE2C275)
internal val Ink = Color(0xFFF2F4F8)
internal val Muted = Color(0xFFAEB7C7)

private enum class Screen { PRAYER, QIBLA, MOSQUES, ASK, BOOKS, SETTINGS }

/**
 * Near Mosque for Windows and Linux. Closing the window keeps the app in the notification area so the Adhan and
 * reminders still come; Quit in the tray menu ends it. On a desktop with no notification area (GNOME, for one)
 * the window is the app: closing it quits, and at sign-in it starts minimized instead of hidden.
 */
fun main(args: Array<String>) {
    PcZone.fixed = !System.getProperty("user.timezone").isNullOrEmpty()
    val minimized = "--minimized" in args
    val instance = SingleInstance(Store.dataDir)
    if (!instance.acquire()) {
        // Already running (perhaps since sign-in): bring its window up instead of a second Adhan.
        if (!minimized) instance.askToShow()
        return
    }
    app(minimized, instance)
}

private fun app(minimized: Boolean, instance: SingleInstance) = application {
    var settings by remember { mutableStateOf(Store.load().also { Strings.language = it.language }) }
    var city by remember { mutableStateOf(Cities.byId(settings.cityId) ?: Cities.guess()) }
    val hasTray = Platform.hasTray
    // Started at sign-in: stay in the notification area until the person opens the window (no tray: minimized).
    var visible by remember { mutableStateOf(!minimized || !hasTray) }
    // Bumped when another copy asks for the window, to bring it to the front.
    var raise by remember { mutableStateOf(0) }
    val clock = remember(city, settings) { city?.let { Clock(it, settings) } }
    val tray = rememberTrayState()
    // The last notification, shown inside the window when the desktop has no way to show it.
    var banner by remember { mutableStateOf<String?>(null) }
    // When the Fajr alarm started (System.nanoTime, so a change of the PC's clock does not move it): the window shows it
    // with Stop while it rings, and for Sound.ALARM_MAX when there is no sound device to ring it.
    var alarmSince by remember { mutableStateOf<Long?>(null) }
    // The notifications for the tray, handed to it one at a time (see drainToTray).
    val toTray = remember { Channel<Notification>(Channel.UNLIMITED) }
    fun send(title: String, body: String, urgent: Boolean) {
        val inApp = { banner = "$title · $body" }
        val type = if (urgent) Notification.Type.Warning else Notification.Type.None
        when (Platform.route) {
            Platform.Route.TRAY -> toTray.trySend(Notification(title, body, type))
            Platform.Route.NOTIFY_SEND -> Platform.notifySend(title, body, urgent) {
                if (hasTray) toTray.trySend(Notification(title, body, type)) else inApp()
            }
            Platform.Route.IN_APP -> inApp()
        }
    }
    val reminders = remember {
        Reminders { title, body -> send(title, body, urgent = false) }.apply {
            notifyAlarm = { title, body -> send(title, body, urgent = true) }
            // The alarm brings the window up (from the notification area too) with its Stop button.
            onAlarm = { alarmSince = System.nanoTime(); visible = true; raise++ }
        }
    }
    fun stopAlarm() {
        Sound.stop()
        alarmSince = null
    }
    var now by remember { mutableStateOf(Instant.now()) }
    val weather = WeatherClient.shared
    val scope = rememberCoroutineScope()
    // Start at sign-in could not be set (Windows' Run key read back is not what was asked): Settings says so.
    var startupFailed by remember { mutableStateOf(false) }
    val startupLock = remember { Mutex() }
    fun setStartup(on: Boolean) {
        scope.launch {
            startupLock.withLock {
                val ok = withContext(Dispatchers.IO) { Startup.set(on) }
                startupFailed = on && !ok
            }
        }
    }

    // Keep the start-at-sign-in entry in step with the setting (the installer may have moved the app).
    LaunchedEffect(Unit) {
        setStartup(settings.startAtLogin)
        // Sky follows the weather is off: no forecast is kept on the PC.
        if (!settings.skyWeather) weather.clear()
    }
    // The PC's time zone, read again every ten minutes: a laptop taken to another zone is offered a city there.
    var pcZone by remember { mutableStateOf<ZoneId?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            pcZone = withContext(Dispatchers.IO) { runCatching { PcZone.read() }.getOrNull() }
            delay(10 * 60_000L)
        }
    }
    val zoneMoved = zoneChange(pcZone, city, settings, now)
    // Also told once in a notification, as the window is often closed to the notification area.
    LaunchedEffect(zoneMoved?.id) {
        val z = zoneMoved ?: return@LaunchedEffect
        val c = city ?: return@LaunchedEffect
        send(t("zone_changed_title"), t("zone_changed_body", zoneName(z), c.displayName(Strings.language)), urgent = false)
    }
    TickLoop(clock, settings) { c, s ->
        now = Instant.now()
        // A slip in one tick (a broken sound device, a notification that cannot show) never stops the next.
        runCatching { reminders.tick(c, s, now) }
        // The alarm's card goes with its sound (Stop anywhere, another sound, the Fajr Adhan), or after the longest ring.
        alarmSince?.let { if (System.nanoTime() - it >= Sound.ALARM_MAX.toNanos() || !alarmShows()) alarmSince = null }
        if (instance.takeShowRequest()) { visible = true; raise++ }
    }

    fun update(s: DesktopSettings) {
        Strings.language = s.language
        if (s.startAtLogin != settings.startAtLogin) setStartup(s.startAtLogin)
        // The Fajr alarm turned off while it rings: it stops.
        if (s.fajrAlarmMinutesBefore == null && settings.fajrAlarmMinutesBefore != null && (alarmSince != null || Sound.alarm)) stopAlarm()
        // Sky follows the weather turned off: the forecast and its file go.
        if (!s.skyWeather && settings.skyWeather) weather.clear()
        settings = s
        Store.save(s)
    }

    if (hasTray) {
        Tray(
            icon = painterResource("icon.png"),
            state = tray,
            tooltip = trayStatus(clock, now),
            onAction = { visible = true; raise++ },
            menu = {
                Item(t("tab_prayer"), onClick = { visible = true; raise++ })
                Item(t("stop"), onClick = { stopAlarm() })
                // Close window keeps the app here (the Adhan still comes); Quit ends it.
                Item(t("tray_close_window"), enabled = visible, onClick = { visible = false })
                Item(t("tray_quit"), onClick = ::exitApplication)
            },
        )
        LaunchedEffect(Unit) { drainToTray(toTray) { tray.sendNotification(it) } }
    }

    if (visible) {
        // Without a tray, a start at sign-in shows the window minimized (the only copy, so it is never re-created).
        val windowState = rememberWindowState(size = DpSize(980.dp, 680.dp), isMinimized = minimized && !hasTray)
        Window(
            onCloseRequest = { if (hasTray) visible = false else exitApplication() },
            title = t("app_name"),
            icon = painterResource("icon.png"),
            state = windowState,
        ) {
            LaunchedEffect(raise) {
                if (raise > 0) {
                    windowState.isMinimized = false
                    // For the alarm, on top for a moment: Windows otherwise may only flash the taskbar button.
                    if (alarmSince != null) runCatching { window.isAlwaysOnTop = true; window.toFront(); window.isAlwaysOnTop = false }
                    window.toFront()
                    window.requestFocus()
                }
            }
            // Sky follows the weather: asked for only while this window is shown and not minimized (closed to the
            // notification area, the window and this loop are gone; the tick above never fetches), when the city
            // changes, and then as MET's Expires and the shared policy allow (about every half hour).
            val skyCity = city
            val skyOn = settings.skyWeather && skyCity != null
            LaunchedEffect(skyOn, skyCity?.id, windowState.isMinimized) {
                if (!skyOn || windowState.isMinimized) return@LaunchedEffect
                while (true) {
                    weather.refreshIfDue(skyCity.location)
                    delay(weather.millisUntilDue(skyCity.location))
                }
            }
            val weatherNow = if (skyOn) weather.now(skyCity.location, now) else null
            MaterialTheme(colorScheme = darkColorScheme(primary = Gold, background = Navy, surface = Card, onSurface = Ink)) {
                CompositionLocalProvider(LocalLayoutDirection provides if (Strings.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    // First run (or "Show welcome tour"): the onboarding fills the window; the reminders keep ticking,
                    // and the Fajr alarm's Stop and the in-app notices show above it as on every other screen.
                    if (!settings.onboarded) Box(Modifier.fillMaxSize()) {
                        OnboardingDesktop(
                            settings = settings,
                            city = city,
                            now = now,
                            onSettings = ::update,
                            onCity = { c -> city = c; update(settings.copy(cityId = c.id)) },
                            weather = weatherNow,
                            animate = Platform.animations,
                        )
                        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                            if (alarmSince != null && alarmShows()) AlarmCard(::stopAlarm)
                            banner?.let { NoticeBanner(it, onDismiss = { banner = null }) }
                        }
                    } else AppScreen(
                        settings = settings,
                        city = city,
                        clock = clock,
                        now = now,
                        onCity = { c -> city = c; update(settings.copy(cityId = c.id)) },
                        onSettings = ::update,
                        banner = banner,
                        onDismissBanner = { banner = null },
                        alarm = alarmSince != null && alarmShows(),
                        onStopAlarm = ::stopAlarm,
                        weather = weatherNow,
                        animate = Platform.animations,
                        noTray = !hasTray,
                        startupFailed = startupFailed,
                        onTestNotification = { clock?.let { reminders.test(it, settings) } },
                        zoneChange = zoneMoved,
                        onKeepCity = { z -> update(settings.copy(zoneKept = z.id)) },
                    )
                }
            }
        }
    }
}

/** The Fajr alarm rings, or could not ring for want of a sound device (its card then stays for its full time). */
private fun alarmShows() = Sound.alarm || Sound.alarmUnheard

/**
 * Hands the notifications in [queue] to the tray one at a time, [gapMillis] apart. Compose's TrayState passes one on
 * only while the tray is waiting for it, so a second sent in the same tick (a late Adhan and its iqama after the PC
 * wakes, Iftar and the Maghrib Adhan) would otherwise be dropped.
 */
internal suspend fun drainToTray(queue: ReceiveChannel<Notification>, gapMillis: Long = 1_500, show: (Notification) -> Unit) {
    for (n in queue) {
        show(n)
        delay(gapMillis)
    }
}

/**
 * Calls [pass] every [periodMillis] while in the composition, always with the latest [clock] and [settings]: a change
 * in Settings (a minute adjustment, the high-latitude or polar rule, Ramadan Isha, another city) reaches the Adhan
 * and the reminders on the next pass, as it reaches the screen, not after a restart.
 */
@Composable
internal fun TickLoop(clock: Clock?, settings: DesktopSettings, periodMillis: Long = 1_000, pass: (Clock?, DesktopSettings) -> Unit) {
    val currentClock by rememberUpdatedState(clock)
    val currentSettings by rememberUpdatedState(settings)
    val currentPass by rememberUpdatedState(pass)
    LaunchedEffect(Unit) {
        while (true) {
            currentPass(currentClock, currentSettings)
            delay(periodMillis)
        }
    }
}

@Composable
internal fun AppScreen(
    settings: DesktopSettings, city: City?, clock: Clock?, now: Instant, onCity: (City) -> Unit, onSettings: (DesktopSettings) -> Unit,
    banner: String? = null, onDismissBanner: () -> Unit = {}, alarm: Boolean = false, onStopAlarm: () -> Unit = {},
    weather: WeatherNow? = null, animate: Boolean = false, noTray: Boolean = false, startupFailed: Boolean = false,
    onTestNotification: (() -> Unit)? = null, zoneChange: ZoneId? = null, onKeepCity: (ZoneId) -> Unit = {},
) {
    var screen by remember { mutableStateOf(Screen.PRAYER) }
    var picking by remember { mutableStateOf(city == null) }
    Row(Modifier.fillMaxSize().background(Navy)) {
        NavigationRail(containerColor = Card) {
            Spacer(Modifier.height(12.dp))
            listOf(
                Screen.PRAYER to "tab_prayer", Screen.QIBLA to "qibla", Screen.MOSQUES to "tab_mosques",
                Screen.ASK to "tab_ask", Screen.BOOKS to "classics_title", Screen.SETTINGS to "settings",
            ).forEach { (s, key) ->
                NavigationRailItem(selected = screen == s, onClick = { screen = s }, icon = { Text(railIcon(s), fontSize = 20.sp) }, label = { Text(t(key), fontSize = 11.sp) })
            }
        }
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            // The Fajr alarm, on every screen, with Stop (also in the tray menu).
            if (alarm) AlarmCard(onStopAlarm)
            if (banner != null) NoticeBanner(banner, onDismissBanner)
            Box(Modifier.fillMaxSize()) { when {
                picking || clock == null -> CityPicker(onPick = { onCity(it); picking = false })
                screen == Screen.PRAYER -> PrayerScreen(
                    clock, now, onChangeCity = { picking = true },
                    onUseNearest = { onSettings(settings.copy(polarRule = PolarRule.NEAREST_LATITUDE)) },
                    sky = settings.skyWeather, weather = weather, animate = animate,
                    // Offered once (the desktop has no tour); either answer puts it away for good.
                    offerWeather = !settings.weatherOffered && !settings.skyWeather,
                    onOfferAnswer = { on -> onSettings(settings.copy(skyWeather = on || settings.skyWeather, weatherOffered = true)) },
                    noTray = noTray, zoneChange = zoneChange, onUseCity = onCity, onKeepCity = onKeepCity,
                )
                screen == Screen.QIBLA -> QiblaScreen(clock.city)
                screen == Screen.MOSQUES -> MosquesScreen(clock.city, settings.onlineSearch)
                screen == Screen.ASK -> AskScreen()
                screen == Screen.BOOKS -> BooksScreen()
                else -> SettingsScreen(
                    settings, clock, onSettings, onChangeCity = { picking = true },
                    startupFailed = startupFailed, onTestNotification = onTestNotification,
                )
            } }
        }
    }
}

@Composable
private fun AlarmCard(onStop: () -> Unit) {
    Surface(color = Gold, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("⏰  " + t("alerts_fajr_alarm"), color = Navy, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(onClick = onStop, colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = Gold)) {
                Text(t("alarm_stop"), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** An in-app notice (a reminder where the system shows none), until dismissed. */
@Composable
private fun NoticeBanner(text: String, onDismiss: () -> Unit) {
    Surface(color = Card, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = Gold, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("×", color = Muted, fontSize = 18.sp) }
        }
    }
}

private fun railIcon(s: Screen) = when (s) {
    Screen.PRAYER -> "🕌"
    Screen.QIBLA -> "🧭"
    Screen.MOSQUES -> "📍"
    Screen.ASK -> "✦"
    Screen.BOOKS -> "📚"
    Screen.SETTINGS -> "⚙"
}

@Composable
private fun PrayerScreen(
    clock: Clock, now: Instant, onChangeCity: () -> Unit, onUseNearest: () -> Unit,
    sky: Boolean = false, weather: WeatherNow? = null, animate: Boolean = false, offerWeather: Boolean = false, onOfferAnswer: (Boolean) -> Unit = {},
    noTray: Boolean = false, zoneChange: ZoneId? = null, onUseCity: (City) -> Unit = {}, onKeepCity: (ZoneId) -> Unit = {},
) {
    val today = clock.today(now)
    val moment = clock.moment(now)
    val iqama = clock.iqama
    val hijri = HijriCalendar.of(now.atZone(clock.zone).toLocalDate(), clock.settings.hijriAdjustmentDays)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        zoneChange?.let { ZoneCard(it, clock.city, onUseCity, onChangeCity, onKeepCity) }
        if (offerWeather) WeatherOfferCard(onOfferAnswer)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(clock.city.displayName(Strings.language), color = Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                if (hijri != null) Text("${hijri.day} / ${hijri.month} / ${hijri.year}", color = Muted)
                // Sky follows the weather: the temperature and the wind, nothing else (no line without fresh data).
                if (weather != null) {
                    val description = weatherDescription(weather, clock.city)
                    // The condition's icon first, so a clear sky (nearly invisible on the strip) still says what it is.
                    Row(Modifier.clearAndSetSemantics { contentDescription = description }, verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(weatherIconPath(weather.symbol.condition, SkyPeriod.of(clock, now).hasStars)), contentDescription = null,
                            tint = Muted, modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(weatherLine(weather), color = Muted, fontSize = 14.sp)
                    }
                }
                // Polar day or night with the nearest-latitude rule: the times are those at 48.5°, and the Adhan follows them.
                if (today.status is ScheduleStatus.Estimated) Text(t("estimated_badge"), color = Gold, fontSize = 13.sp)
                // No notification area: the window is the app, so closing it ends the Adhan too.
                if (noTray) Text(t("no_tray_window_note"), color = Muted, fontSize = 13.sp)
            }
            TextButton(onClick = onChangeCity) { Text(t("choose_city_title"), color = Gold) }
        }
        // No sunrise or sunset today: no times are invented unless the person picks the nearest-latitude rule.
        if (today.status == ScheduleStatus.Unavailable) PolarCard(onUseNearest)
        if (moment != null) MomentCard(moment, clock, now, sky, weather, animate)
        Surface(color = Card, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 8.dp)) {
                PrayerEvent.entries.forEach { e ->
                    val at = today[e]
                    val color = if (moment?.event == e) Gold else Ink
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t(prayerKey(e)), color = color, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        if (iqama.enabled && iqama.wait(e) > 0 && at != null) {
                            Text(t("iqama_at", clockText(iqama.iqama(e, at), clock.zone)), color = Muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp))
                        }
                        Text(at?.let { clockText(it, clock.zone) } ?: "—", color = color, fontSize = 18.sp)
                        if (e.isPrayer) TextButton(onClick = { Sound.play(Sound.adhanFile(e, Store.load())) }) { Text("▶", color = Muted) }
                        else Spacer(Modifier.width(56.dp))
                    }
                }
            }
        }
        if (Sound.playing != null) Button(onClick = { Sound.stop() }) { Text(t("stop")) }
    }
}

/** Times cannot be calculated for this date (the sun does not rise or set), with the one-click way out. */
@Composable
private fun PolarCard(onUseNearest: () -> Unit) {
    Surface(color = Card, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("polar_unavailable_title"), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(t("polar_unavailable_body"), color = Muted)
            Button(onClick = onUseNearest) { Text(t("polar_use_nearest")) }
        }
    }
}

/**
 * The PC is now on another time zone than the city's (a laptop taken abroad): that zone's city in one click, the city
 * search, or keep the city (not offered again for this zone).
 */
@Composable
private fun ZoneCard(zone: ZoneId, city: City, onUse: (City) -> Unit, onChoose: () -> Unit, onKeep: (ZoneId) -> Unit) {
    val here = remember(zone) { Cities.inZone(zone) }
    val name = city.displayName(Strings.language)
    Surface(color = Card, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("zone_changed_title"), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(t("zone_changed_body", zoneName(zone), name), color = Muted)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (here != null) Button(onClick = { onUse(here) }) { Text(t("zone_use_city", here.displayName(Strings.language))) }
                TextButton(onClick = onChoose) { Text(t("choose_city_title"), color = Gold) }
                TextButton(onClick = { onKeep(zone) }) { Text(t("zone_keep_city", name), color = Gold) }
            }
        }
    }
}

/** Sky follows the weather, offered once at the top of the prayer screen: off unless the person turns it on. */
@Composable
private fun WeatherOfferCard(onAnswer: (Boolean) -> Unit) {
    Surface(color = Card, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("sky_weather"), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(t("onb_weather_body"), color = Muted)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onAnswer(true) }) { Text(t("weather_offer_turn_on")) }
                TextButton(onClick = { onAnswer(false) }) { Text(t("weather_offer_not_now"), color = Gold) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { Platform.open(Weather.ATTRIBUTION_URL) }) { Text(t("sky_weather_attribution"), color = Muted, fontSize = 13.sp) }
            }
        }
    }
}

/**
 * The next Adhan with its countdown; after an Adhan, the countdown to the iqama; after the iqama, the prayer. With
 * Sky follows the weather on ([sky]), the card sits on the time-of-day sky with the city's [weather] (none: the
 * plain sky); off, it is the flat card as before.
 */
@Composable
private fun MomentCard(m: PrayerMoment, clock: Clock, now: Instant, sky: Boolean = false, weather: WeatherNow? = null, animate: Boolean = false) {
    val name = t(prayerKey(m.event))
    // How far the text reaches from the card's start edge (with its padding): the sky stays fully dimmed under it.
    var textWidth by remember { mutableStateOf(Float.MAX_VALUE) }
    Surface(color = Card, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Box {
            if (sky) SkyStrip(SkyPeriod.of(clock, now), weather, now, animate, Modifier.matchParentSize(), textWidth)
            Column(Modifier.onSizeChanged { textWidth = it.width.toFloat() }.padding(24.dp)) {
                when (m.stage) {
                    PrayerMoment.Stage.UPCOMING -> {
                        Text(t("next_prayer"), color = Muted)
                        Text(name, color = Gold, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                        Text(clockText(m.adhan, clock.zone) + "  ·  " + countdown(PrayerCalculator.remaining(now, m.adhan)), color = Ink, fontSize = 20.sp)
                        m.iqama?.let { Text(t("iqama_at", clockText(it, clock.zone)), color = Muted) }
                    }
                    PrayerMoment.Stage.TO_IQAMA -> {
                        Text(t("reminder_title", name), color = Gold, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                        Text(t("iqama_in") + "  " + countdown(PrayerCalculator.remaining(now, m.until)), color = Ink, fontSize = 20.sp)
                        m.iqama?.let { Text(t("iqama_at", clockText(it, clock.zone)), color = Muted) }
                    }
                    PrayerMoment.Stage.PRAYER -> {
                        Text(t("prayer_started", name), color = Gold, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                        Text(t("next_prayer") + "  ·  " + t(prayerKey(m.next.event)) + " " + clockText(m.next.at, clock.zone), color = Muted)
                    }
                }
            }
        }
    }
}

internal fun countdown(d: Duration): String {
    val h = d.toHours()
    val m = d.toMinutesPart()
    val s = d.toSecondsPart()
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** No compass on a PC: the Qibla from north, with its bearing and the distance to the Kaaba. */
@Composable
private fun QiblaScreen(city: City) {
    val bearing = Qibla.bearing(city.location)
    val km = Qibla.distanceMeters(city.location) / 1000
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(t("qibla"), color = Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Canvas(Modifier.size(320.dp)) {
            val r = size.minDimension / 2
            val c = Offset(size.width / 2, size.height / 2)
            drawCircle(Card, r, c)
            drawCircle(Gold.copy(alpha = 0.5f), r - 4, c, style = Stroke(3f))
            for (i in 0 until 72) rotate(i * 5f, c) {
                drawLine(Muted.copy(alpha = if (i % 18 == 0) 1f else 0.35f), Offset(c.x, c.y - r + 8), Offset(c.x, c.y - r + if (i % 18 == 0) 30 else 18), strokeWidth = if (i % 18 == 0) 4f else 2f)
            }
            rotate(bearing.toFloat(), c) {
                drawLine(Gold, c, Offset(c.x, c.y - r + 40), strokeWidth = 10f)
                drawCircle(Gold, 16f, Offset(c.x, c.y - r + 40))
            }
            drawCircle(Ink, 8f, c)
        }
        Text("N ↑", color = Muted)
        Text(t("qibla_bearing", bearing.roundToInt().toString()), color = Gold, fontSize = 22.sp)
        Text(t("qibla_distance", "%,d km".format(km.roundToInt())), color = Ink)
        Text(t("qibla_from_north_hint"), color = Muted)
    }
}

/** The city table, searched as you type; the largest cities first. The onboarding shows it without its title, with the [selected] city ticked. */
@Composable
internal fun CityPicker(onPick: (City) -> Unit, modifier: Modifier = Modifier.fillMaxSize(), showTitle: Boolean = true, selected: Long? = null) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { if (query.isBlank()) Cities.index.cities.sortedByDescending { it.population }.take(40) else Cities.index.search(query, 40) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showTitle) Text(t("choose_city_title"), color = Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(query, { query = it }, placeholder = { Text(t("search_city")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(results, key = { it.id }) { c ->
                val on = c.id == selected
                Surface(
                    color = Card, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().then(if (on) Modifier.border(2.dp, Gold, RoundedCornerShape(12.dp)) else Modifier).clickable { onPick(c) },
                ) {
                    Row(Modifier.padding(14.dp)) {
                        Text(c.displayName(Strings.language), color = Ink, modifier = Modifier.weight(1f))
                        Text(if (on) "✓  " + c.countryCode else c.countryCode, color = if (on) Gold else Muted)
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingsScreen(
    s: DesktopSettings, clock: Clock, onChange: (DesktopSettings) -> Unit, onChangeCity: () -> Unit, openPrayer: PrayerEvent? = null,
    startupFailed: Boolean = false, onTestNotification: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(t("settings"), color = Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Choice(t("language"), Strings.languages.firstOrNull { it.code == s.language }?.name ?: s.language, Strings.languages.map { it.name to { onChange(s.copy(language = it.code)) } })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(clock.city.displayName(Strings.language), color = Ink, modifier = Modifier.weight(1f))
            TextButton(onClick = onChangeCity) { Text(t("choose_city_title"), color = Gold) }
        }
        Text(t("calculation"), color = Gold, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Choice(t("calculation_method"), t("method_" + clock.settings.method.name), Method.entries.map { m -> t("method_" + m.name) to { onChange(s.copy(method = m)) } })
        if (s.method == null) Text(t("method_suggested"), color = Muted, fontSize = 13.sp)
        Choice(t("asr_method"), t(if (s.madhab == AsrMadhab.HANAFI) "asr_hanafi" else "asr_standard"), listOf(
            t("asr_standard") to { onChange(s.copy(madhab = AsrMadhab.SHAFI)) },
            t("asr_hanafi") to { onChange(s.copy(madhab = AsrMadhab.HANAFI)) },
        ))
        // As in the phone app's calculation sheet.
        val highLat = listOf(
            HighLatRule.AUTO to "hl_auto", HighLatRule.MIDDLE_OF_THE_NIGHT to "hl_middle",
            HighLatRule.SEVENTH_OF_THE_NIGHT to "hl_seventh", HighLatRule.TWILIGHT_ANGLE to "hl_twilight",
        )
        Choice(t("high_latitude_rule"), t(highLat.first { it.first == s.highLatitudeRule }.second), highLat.map { (r, key) -> t(key) to { onChange(s.copy(highLatitudeRule = r)) } })
        val polar = listOf(PolarRule.UNAVAILABLE to "polar_none", PolarRule.NEAREST_LATITUDE to "polar_nearest")
        Choice(t("polar_rule"), t(polar.first { it.first == s.polarRule }.second), polar.map { (r, key) -> t(key) to { onChange(s.copy(polarRule = r)) } })
        // The method in use (the suggested one when none is chosen).
        if (clock.settings.method == Method.UMM_AL_QURA) Toggle(t("ramadan_isha"), s.ramadanIsha) { onChange(s.copy(ramadanIsha = it)) }
        Choice(t("hijri_adjustment"), days(s.hijriAdjustment), (-2..2).map { d -> days(d) to { onChange(s.copy(hijriAdjustment = d)) } })
        Text(t("hijri_calendar_note"), color = Muted, fontSize = 13.sp)
        Text(t("minute_adjustments"), color = Gold, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        PrayerEvent.entries.forEach { e ->
            val v = s.offsets[e] ?: 0
            fun set(m: Int) = onChange(s.copy(offsets = (s.offsets + (e to m.coerceIn(-30, 30))).filterValues { it != 0 }))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(t(prayerKey(e)), color = Ink, modifier = Modifier.weight(1f))
                Stepper(offset(v), minusEnabled = v > -30, plusEnabled = v < 30, onMinus = { set(v - 1) }, onPlus = { set(v + 1) })
            }
        }
        Text(t("adhan_section"), color = Gold, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Toggle(t("play_adhan"), s.adhan) { onChange(s.copy(adhan = it)) }
        Choice(t("adhan_full"), sourceName(s.adhanSource), AdhanSource.entries.map { a -> sourceName(a) to { onChange(s.copy(adhanSource = a)) } })
        Choice(t("adhan_fajr"), sourceName(s.fajrAdhanSource), AdhanSource.entries.map { a -> sourceName(a) to { onChange(s.copy(fajrAdhanSource = a)) } })
        Choice(t("adhan_length"), t(if (s.shortAdhan) "adhan_short" else "adhan_length_full"), listOf(
            t("adhan_length_full") to { onChange(s.copy(shortAdhan = false)) },
            t("adhan_short") to { onChange(s.copy(shortAdhan = true)) },
        ))
        // The Adhan's notification and sound now, through the real path, with what hides the banner.
        onTestNotification?.let { test ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = test) { Text(t("test_notification")) }
                if (Sound.playing != null) TextButton(onClick = { Sound.stop() }) { Text(t("stop"), color = Gold) }
                Spacer(Modifier.weight(1f))
                if (Platform.isWindows) TextButton(onClick = { Platform.open("ms-settings:notifications") }) { Text(t("notification_settings"), color = Gold) }
            }
            Note(t(if (Platform.isWindows) "test_notification_note" else "test_notification_note_linux"))
        }
        // As in the phone app's Prayer & Audio screen, in the same order.
        Header(t("before_section"))
        Toggle(t("before10"), s.before10) { onChange(s.copy(before10 = it)) }
        Toggle(t("before5"), s.before5) { onChange(s.copy(before5 = it)) }
        Toggle(t("near"), s.near, note = t("near_note")) { onChange(s.copy(near = it)) }
        if (s.near) Indented {
            Choice(t("near_when"), t("alerts_minutes", s.nearMinutes), AlertSettings.nearChoices.map { m -> t("alerts_minutes", m) to { onChange(s.copy(nearMinutes = m)) } })
        }
        val voice = s.voice ?: VoiceLanguage.forInterface(s.language)
        fun voiceName(v: VoiceLanguage) = t(if (v == VoiceLanguage.ARABIC) "voice_arabic" else "voice_english")
        Choice(
            t("voice_language"), voiceName(voice), VoiceLanguage.entries.map { v -> voiceName(v) to { onChange(s.copy(voice = v)) } },
            preview = Sound.file(AlertKind.BEFORE_10, PrayerEvent.MAGHRIB, s.copy(voice = voice))!! to t("voice_language"),
        )
        Note(t("before_note"))
        Header(t("per_prayer_section"))
        val alerts = s.alerts()
        PrayerEvent.prayers.forEach { e ->
            PrayerRows(e, s, alerts, startOpen = e == openPrayer, onAdhan = { on -> onChange(s.withAdhan(e, on)) }) { sound, on ->
                onChange(s.withAlerts(alerts.withPrayer(e, sound, on)))
            }
        }
        Toggle(t("sunrise_notify"), s.sunrise) { onChange(s.copy(sunrise = it)) }
        Note(t("per_prayer_note"))
        Text(t("iqama_section"), color = Gold, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Toggle(t("iqama_show"), s.iqama.enabled) { onChange(s.copy(iqama = s.iqama.copy(enabled = it))) }
        if (s.iqama.enabled) {
            PrayerEvent.prayers.forEach { e ->
                Choice(t(prayerKey(e)), minutes(s.iqama.wait(e)), IqamaSettings.choices.map { m -> minutes(m) to { onChange(s.copy(iqama = s.iqama.withWait(e, m))) } })
            }
            Choice(t("iqama_prayer_minutes"), minutes(s.iqama.prayerMinutes), IqamaSettings.prayerChoices.map { m -> minutes(m) to { onChange(s.copy(iqama = s.iqama.copy(prayerMinutes = m))) } })
            Toggle(t("iqama_alert"), s.iqama.alert) { onChange(s.copy(iqama = s.iqama.copy(alert = it))) }
        }
        Text(t("iqama_note"), color = Muted, fontSize = 13.sp)
        Header(t("dhikr_section"))
        val evening = Sound.file(AlertKind.QURAN, PrayerEvent.ISHA, s)!!
        Toggle(t("salawat"), s.salawat, note = t("salawat_note"), preview = Sound.file(AlertKind.SALAWAT, PrayerEvent.DHUHR, s)!! to t("salawat")) {
            onChange(s.copy(salawat = it))
        }
        if (s.salawat) TimeChoice(t("reminder_time"), s.salawatMinutes) { onChange(s.copy(salawatMinutes = it)) }
        Toggle(t("quran_reminder"), s.quran, note = t("quran_reminder_note"), preview = evening to t("quran_reminder")) { onChange(s.copy(quran = it)) }
        if (s.quran) TimeChoice(t("reminder_time"), s.quranMinutes) { onChange(s.copy(quranMinutes = it)) }
        Toggle(t("alerts_sleep"), s.bedtimeMinutes != null, note = t("alerts_sleep_note"), preview = evening to t("alerts_sleep")) {
            onChange(s.copy(bedtimeMinutes = if (it) s.bedtimeMinutes ?: AlertSettings.DEFAULT_BEDTIME_MIN else null))
        }
        s.bedtimeMinutes?.let { m -> TimeChoice(t("alerts_sleep_time"), m) { onChange(s.copy(bedtimeMinutes = it)) } }
        Toggle(t("alerts_kahf"), s.kahf, note = t("alerts_kahf_note")) { onChange(s.copy(kahf = it)) }
        fun reciterName(r: Reciter) = t(if (r == Reciter.ALAFASY) "reciter_alafasy" else "reciter_classic")
        Choice(t("reciter"), reciterName(s.reciter), Reciter.entries.map { r -> reciterName(r) to { onChange(s.copy(reciter = r)) } })
        Header(t("more_reminders_section"))
        Toggle(t("alerts_friday"), s.friday, note = t("alerts_friday_note")) { onChange(s.copy(friday = it)) }
        Toggle(t("alerts_ramadan"), s.ramadan, note = t("alerts_ramadan_note")) { onChange(s.copy(ramadan = it)) }
        fun alarmName(m: Int?) = when (m) { null -> t("alerts_off"); 0 -> t("alerts_at_fajr"); else -> t("alerts_minutes", m) }
        Choice(t("alerts_fajr_alarm"), alarmName(s.fajrAlarmMinutesBefore), (listOf<Int?>(null) + AlertSettings.fajrAlarmChoices).map { m ->
            alarmName(m) to { onChange(s.copy(fajrAlarmMinutesBefore = m)) }
        })
        Note(t("audio_credits"))
        Text(t("tab_mosques"), color = Gold, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Toggle(t("online_search"), s.onlineSearch) { onChange(s.copy(onlineSearch = it)) }
        Text(t("online_search_body"), color = Muted, fontSize = 13.sp)
        if (Startup.available) {
            val linux = Platform.isLinux
            Toggle(t(if (linux) "start_at_login_linux" else "start_at_login"), s.startAtLogin) { onChange(s.copy(startAtLogin = it)) }
            Text(t(if (linux && !Platform.hasTray) "start_at_login_linux_note" else "start_at_login_note"), color = Muted, fontSize = 13.sp)
            if (startupFailed && s.startAtLogin) Text(t("start_at_login_failed"), color = Color(0xFFE8A0A0), fontSize = 13.sp)
        } else if (Startup.isStorePackage) {
            Text(t("start_at_login_store_note"), color = Muted, fontSize = 13.sp)
        }
        if (Platform.isLinux && !Platform.hasTray) Text(t("no_tray_note"), color = Muted, fontSize = 13.sp)
        // Its own section, after the start-at-sign-in and notification-area notes (which are not about the look).
        Header(t("appearance"))
        Toggle(t("sky_weather"), s.skyWeather, note = t("sky_weather_note")) { onChange(s.copy(skyWeather = it, weatherOffered = true)) }
        // CC BY 4.0 / NLOD: the source, credited and linked, under the switch.
        LinkRow(t("sky_weather_attribution"), "met.no", Weather.ATTRIBUTION_URL)
        // The first-run tour again: the window shows it as soon as this is off (nothing else changes).
        TextButton(onClick = { onChange(s.copy(onboarded = false)) }) { Text(t("onb_replay"), color = Gold) }
        // Who makes the app and how to reach them, as on the store pages and nearmosque.net.
        Text(t("about"), color = Gold, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        val ar = Strings.language == "ar"
        LinkRow(t("about_publisher"), Publisher.NAME, Publisher.COMPANY_URL)
        LinkRow(t("website"), Publisher.WEBSITE, "https://nearmosque.net/" + if (ar) "#ar" else "")
        LinkRow(t("about_support"), Publisher.EMAIL, "mailto:" + Publisher.EMAIL)
        LinkRow(t("about_phone"), Publisher.PHONE_DISPLAY, "tel:" + Publisher.PHONE)
        LinkRow(t("about_terms"), "nearmosque.net", "https://nearmosque.net/terms.html" + if (ar) "#ar" else "")
        LinkRow(t("privacy"), "nearmosque.net", "https://nearmosque.net/privacy.html" + if (ar) "#ar" else "")
        LinkRow(t("sources_licenses"), "nearmosque.net", "https://nearmosque.net/credits.html" + if (ar) "#ar" else "")
        appVersion?.let { Text(t("version_label", it), color = Muted, fontSize = 13.sp) }
        Text("© ${Publisher.YEAR} ${Publisher.NAME}. " + t("about_rights"), color = Muted, fontSize = 13.sp)
    }
}

/** The publisher's details (the same as on the store pages and nearmosque.net). */
private object Publisher {
    const val NAME = "AVA ai"
    const val COMPANY_URL = "https://avaai.sa"
    const val WEBSITE = "nearmosque.net"
    const val EMAIL = "app@vision.bz"
    const val PHONE = "+966579372403"
    /** Kept left to right inside Arabic text. */
    const val PHONE_DISPLAY = "\u2066+966 57 937 2403\u2069"
    const val YEAR = 2026
}

/** A row that opens a page, an email or a call in the PC's own apps. */
@Composable
private fun LinkRow(label: String, value: String, url: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ink, modifier = Modifier.weight(1f))
        TextButton(onClick = { Platform.open(url) }) { Text(value, color = Gold) }
    }
}

/** The app's version (written into the app's resources by the build). */
internal val appVersion: String? by lazy {
    runCatching { Strings.resource("version.txt").trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
        ?: System.getProperty("jpackage.app-version")
}

private fun minutes(m: Int) = t("minutes_value", m)

/** A minute adjustment: "0 min", "+2 min", "−5 min", the signed number kept left to right in Arabic. */
internal fun offset(m: Int): String {
    val text = t("minutes_value", kotlin.math.abs(m))
    if (m == 0) return text
    // The same formatting as the text above, so the number is found whatever digits the locale writes.
    val number = String.format("%d", kotlin.math.abs(m))
    if (number !in text) return t("minutes_value", m)
    return text.replaceFirst(number, "\u2066" + (if (m > 0) "+" else "−") + number + "\u2069")
}

/** A Hijri adjustment: "0", "+1", "−2" (kept left to right in Arabic). */
private fun days(d: Int) = "\u2066" + (if (d > 0) "+$d" else if (d < 0) "−${-d}" else "0") + "\u2069"

private fun sourceName(a: AdhanSource) = t(if (a == AdhanSource.MADINAH) "source_madinah" else "source_makkah")

/** A switch, with a line under its label where the phone app has one, and ▶ where it has a preview. */
@Composable
private fun Toggle(label: String, on: Boolean, note: String? = null, preview: Pair<String, String>? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Ink)
            note?.let { Text(it, color = Muted, fontSize = 13.sp) }
        }
        preview?.let { (sound, name) -> PreviewButton(sound, name) }
        Switch(on, onChange)
    }
}

@Composable
private fun Header(title: String) = Text(title, color = Gold, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

@Composable
private fun Note(text: String) = Text(text, color = Muted, fontSize = 13.sp)

@Composable
private fun Indented(content: @Composable () -> Unit) = Box(Modifier.padding(start = 16.dp)) { content() }

/** ▶ plays a recording (■ while it plays stops it). */
@Composable
private fun PreviewButton(sound: String, label: String) {
    val on = Sound.playing == sound
    val description = if (on) t("alarm_stop") else t("preview_play", label)
    TextButton(onClick = { if (on) Sound.stop() else Sound.play(sound) }, modifier = Modifier.semantics { contentDescription = description }) {
        Text(if (on) "■" else "▶", color = Gold)
    }
}

/** One prayer: its name, opened to its own switches (the Adhan, 10 and 5 minutes before, "approaching"). */
@Composable
private fun PrayerRows(
    e: PrayerEvent, s: DesktopSettings, alerts: AlertSettings, startOpen: Boolean, onAdhan: (Boolean) -> Unit,
    onChange: (AlertSettings.PrayerSound, Boolean) -> Unit,
) {
    var open by remember(e) { mutableStateOf(startOpen) }
    val name = t(prayerKey(e))
    Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = Ink, modifier = Modifier.weight(1f))
        Text(if (open) "▴" else "▾", color = Gold, modifier = Modifier.padding(horizontal = 12.dp))
    }
    if (open) Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // What will play, as the phone shows it: off for every prayer while the Adhan is off.
        Toggle(t("play_adhan"), alerts.isOn(e, AlertSettings.PrayerSound.ADHAN), onChange = onAdhan)
        Toggle(t("notif_minutes_until", 10, name), alerts.isOn(e, AlertSettings.PrayerSound.BEFORE_10)) { onChange(AlertSettings.PrayerSound.BEFORE_10, it) }
        Toggle(t("notif_minutes_until", 5, name), alerts.isOn(e, AlertSettings.PrayerSound.BEFORE_5)) { onChange(AlertSettings.PrayerSound.BEFORE_5, it) }
        Toggle(t("near") + " · " + t("alerts_minutes", s.nearMinutes), alerts.isOn(e, AlertSettings.PrayerSound.NEAR)) { onChange(AlertSettings.PrayerSound.NEAR, it) }
    }
}

/**
 * A time of day (minutes after midnight, in the city's time zone) in 15-minute steps; a saved time between the steps
 * stays in the list, which opens at it.
 */
@Composable
private fun TimeChoice(label: String, minutes: Int, onChange: (Int) -> Unit) = Indented {
    val choices = timeChoices(minutes)
    Choice(label, timeOfDay(minutes), choices.map { m -> timeOfDay(m) to { onChange(m) } }, selected = choices.indexOf(minutes))
}

/** Every quarter hour of the day, with [current] added where it falls between them. */
internal fun timeChoices(current: Int): List<Int> = ((0 until 24 * 60 step 15).toList() + current.coerceIn(0, 24 * 60 - 1)).distinct().sorted()

/** "8:30 PM" (or "٨:٣٠ م") for minutes after midnight, in the interface's language. */
internal fun timeOfDay(minutes: Int): String =
    java.time.LocalTime.of(minutes / 60 % 24, minutes % 60)
        .format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT).withLocale(java.util.Locale.forLanguageTag(Strings.language)))

/** − value +, for a number changed one step at a time. */
@Composable
private fun Stepper(value: String, minusEnabled: Boolean, plusEnabled: Boolean, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onMinus, enabled = minusEnabled) { Text("−", color = if (minusEnabled) Gold else Muted, fontSize = 18.sp) }
        Text(value, color = Ink, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 72.dp))
        TextButton(onClick = onPlus, enabled = plusEnabled) { Text("+", color = if (plusEnabled) Gold else Muted, fontSize = 18.sp) }
    }
}

/** A drop-down; with [selected] (an index in [options]) a long list opens at that option, shown in gold. */
@Composable
private fun Choice(label: String, value: String, options: List<Pair<String, () -> Unit>>, preview: Pair<String, String>? = null, selected: Int = -1) {
    var open by remember { mutableStateOf(false) }
    // A fresh position each time it opens: two options above the selected one (a menu item is 48 dp high).
    val itemPx = with(LocalDensity.current) { 48.dp.roundToPx() }
    val scroll = remember(open) { ScrollState((selected - 2).coerceAtLeast(0) * itemPx) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ink, modifier = Modifier.weight(1f))
        preview?.let { (sound, name) -> PreviewButton(sound, name) }
        Box {
            TextButton(onClick = { open = true }) { Text("$value ▾", color = Gold) }
            DropdownMenu(open, onDismissRequest = { open = false }, scrollState = scroll) {
                options.forEachIndexed { i, (name, pick) ->
                    DropdownMenuItem(text = { Text(name, color = if (i == selected) Gold else Color.Unspecified) }, onClick = { pick(); open = false })
                }
            }
        }
    }
}

@Composable
private fun BooksScreen() {
    val catalog = remember { Books.catalog }
    var open by remember { mutableStateOf<sa.zood.nearmosque.core.ClassicBook?>(null) }
    val book = open
    if (book != null) { BookScreen(book) { open = null }; return }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(t("classics_title"), color = Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Text(t("classics_note"), color = Muted)
        }
        catalog.byGroup.forEach { (group, books) ->
            item(key = "g-$group") { Text(t("classics_group_$group"), color = Gold, fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp)) }
            items(books, key = { it.id }) { b ->
                Surface(color = Card, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().clickable { open = b }) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Column(Modifier.padding(14.dp)) {
                            Text(b.title, color = Ink, fontSize = 17.sp)
                            Text(b.author, color = Muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookScreen(book: sa.zood.nearmosque.core.ClassicBook, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var version by remember { mutableStateOf(0) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            TextButton(onClick = onBack) { Text("‹ " + t("classics_title"), color = Gold) }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column {
                    Text(book.title, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    Text(book.author, color = Muted)
                    Text(t("classics_edition", book.edition), color = Muted, fontSize = 13.sp)
                }
            }
            if (failed) Text(t("library_download_failed"), color = Color(0xFFE8A0A0))
        }
        items(book.volumes, key = { it.file }) { v ->
            val saved = remember(version) { Books.isDownloaded(v) }
            Surface(color = Card, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (book.volumes.size > 1) t("classics_volume", v.volume) else t("classics_read"), color = Ink, modifier = Modifier.weight(1f))
                    Text(t("classics_pages", v.pages), color = Muted, modifier = Modifier.padding(horizontal = 12.dp))
                    Button(
                        enabled = busy == null,
                        colors = ButtonDefaults.buttonColors(containerColor = if (saved) Gold else Card),
                        onClick = {
                            failed = false
                            busy = v.file
                            scope.launch {
                                val ok = Books.open(v)
                                failed = !ok
                                busy = null
                                version++
                            }
                        },
                    ) { Text(if (busy == v.file) t("library_downloading") else t(if (saved) "classics_open" else "classics_download"), color = if (saved) Navy else Ink) }
                }
            }
        }
    }
}
