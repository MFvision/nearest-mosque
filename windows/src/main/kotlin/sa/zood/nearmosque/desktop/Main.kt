package sa.zood.nearmosque.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sa.zood.nearmosque.core.AdhanSource
import sa.zood.nearmosque.core.AsrMadhab
import sa.zood.nearmosque.core.City
import sa.zood.nearmosque.core.HijriCalendar
import sa.zood.nearmosque.core.Method
import sa.zood.nearmosque.core.PrayerCalculator
import sa.zood.nearmosque.core.PrayerEvent
import sa.zood.nearmosque.core.Qibla
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

internal val Navy = Color(0xFF0B1220)
internal val Card = Color(0xFF16213A)
internal val Gold = Color(0xFFE2C275)
internal val Ink = Color(0xFFF2F4F8)
internal val Muted = Color(0xFFAEB7C7)

private enum class Screen { PRAYER, QIBLA, MOSQUES, ASK, BOOKS, SETTINGS }

/**
 * Near Mosque for Windows. Closing the window keeps the app in the notification area so the Adhan and reminders
 * still come; Quit in the tray menu ends it.
 */
fun main(args: Array<String>) = application {
    var settings by remember { mutableStateOf(Store.load().also { Strings.language = it.language }) }
    var city by remember { mutableStateOf(Cities.byId(settings.cityId) ?: Cities.guess()) }
    // Started by Windows at sign-in: stay in the notification area until the person opens the window.
    var visible by remember { mutableStateOf("--minimized" !in args) }
    val clock = remember(city, settings) { city?.let { Clock(it, settings) } }
    val tray = rememberTrayState()
    val reminders = remember { Reminders { title, body -> tray.sendNotification(Notification(title, body)) } }
    var now by remember { mutableStateOf(Instant.now()) }

    // Keep the "Start with Windows" entry in step with the setting (the installer may have moved the app).
    LaunchedEffect(Unit) { Startup.set(settings.startAtLogin) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            reminders.tick(clock, settings, now)
            delay(1_000)
        }
    }

    fun update(s: DesktopSettings) {
        Strings.language = s.language
        if (s.startAtLogin != settings.startAtLogin) Startup.set(s.startAtLogin)
        settings = s
        Store.save(s)
    }

    Tray(
        icon = painterResource("icon.png"),
        state = tray,
        tooltip = t("app_name"),
        onAction = { visible = true },
        menu = {
            Item(t("tab_prayer"), onClick = { visible = true })
            Item(t("stop"), onClick = { Sound.stop() })
            Item(t("close"), onClick = ::exitApplication)
        },
    )

    if (visible) {
        Window(
            onCloseRequest = { visible = false },
            title = t("app_name"),
            icon = painterResource("icon.png"),
            state = rememberWindowState(size = DpSize(980.dp, 680.dp)),
        ) {
            MaterialTheme(colorScheme = darkColorScheme(primary = Gold, background = Navy, surface = Card, onSurface = Ink)) {
                CompositionLocalProvider(LocalLayoutDirection provides if (Strings.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    AppScreen(
                        settings = settings,
                        city = city,
                        clock = clock,
                        now = now,
                        onCity = { c -> city = c; update(settings.copy(cityId = c.id)) },
                        onSettings = ::update,
                    )
                }
            }
        }
    }
}

@Composable
internal fun AppScreen(settings: DesktopSettings, city: City?, clock: Clock?, now: Instant, onCity: (City) -> Unit, onSettings: (DesktopSettings) -> Unit) {
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
        Box(Modifier.fillMaxSize().padding(24.dp)) {
            when {
                picking || clock == null -> CityPicker(onPick = { onCity(it); picking = false })
                screen == Screen.PRAYER -> PrayerScreen(clock, now, onChangeCity = { picking = true })
                screen == Screen.QIBLA -> QiblaScreen(clock.city)
                screen == Screen.MOSQUES -> MosquesScreen(clock.city, settings.onlineSearch)
                screen == Screen.ASK -> AskScreen()
                screen == Screen.BOOKS -> BooksScreen()
                else -> SettingsScreen(settings, clock, onSettings, onChangeCity = { picking = true })
            }
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
private fun PrayerScreen(clock: Clock, now: Instant, onChangeCity: () -> Unit) {
    val today = clock.today(now)
    val next = clock.next(now)
    val hijri = HijriCalendar.of(now.atZone(clock.zone).toLocalDate(), clock.settings.hijriAdjustmentDays)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(clock.city.displayName(Strings.language), color = Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                if (hijri != null) Text("${hijri.day} / ${hijri.month} / ${hijri.year}", color = Muted)
            }
            TextButton(onClick = onChangeCity) { Text(t("choose_city_title"), color = Gold) }
        }
        if (next != null) {
            val left = PrayerCalculator.remaining(now, next.at)
            Surface(color = Card, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp)) {
                    Text(t("next_prayer"), color = Muted)
                    Text(t(prayerKey(next.event)), color = Gold, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text(clockText(next.at, clock.zone) + "  ·  " + countdown(left), color = Ink, fontSize = 20.sp)
                }
            }
        }
        Surface(color = Card, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 8.dp)) {
                PrayerEvent.entries.forEach { e ->
                    val at = today[e]
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t(prayerKey(e)), color = if (next?.event == e) Gold else Ink, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        Text(at?.let { clockText(it, clock.zone) } ?: "—", color = if (next?.event == e) Gold else Ink, fontSize = 18.sp)
                        if (e.isPrayer) TextButton(onClick = { Sound.play(Sound.adhanFile(e, Store.load())) }) { Text("▶", color = Muted) }
                        else Spacer(Modifier.width(56.dp))
                    }
                }
            }
        }
        if (Sound.playing != null) Button(onClick = { Sound.stop() }) { Text(t("stop")) }
    }
}

private fun countdown(d: Duration): String {
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

@Composable
private fun CityPicker(onPick: (City) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { if (query.isBlank()) Cities.index.cities.sortedByDescending { it.population }.take(40) else Cities.index.search(query, 40) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(t("choose_city_title"), color = Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(query, { query = it }, placeholder = { Text(t("search_city")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(results, key = { it.id }) { c ->
                Surface(color = Card, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().clickable { onPick(c) }) {
                    Row(Modifier.padding(14.dp)) {
                        Text(c.displayName(Strings.language), color = Ink, modifier = Modifier.weight(1f))
                        Text(c.countryCode, color = Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(s: DesktopSettings, clock: Clock, onChange: (DesktopSettings) -> Unit, onChangeCity: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(t("settings"), color = Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Choice(t("language"), Strings.languages.firstOrNull { it.code == s.language }?.name ?: s.language, Strings.languages.map { it.name to { onChange(s.copy(language = it.code)) } })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(clock.city.displayName(Strings.language), color = Ink, modifier = Modifier.weight(1f))
            TextButton(onClick = onChangeCity) { Text(t("choose_city_title"), color = Gold) }
        }
        Choice(t("calculation_method"), t("method_" + clock.settings.method.name), Method.entries.map { m -> t("method_" + m.name) to { onChange(s.copy(method = m)) } })
        if (s.method == null) Text(t("method_suggested"), color = Muted, fontSize = 13.sp)
        Choice(t("asr_method"), t(if (s.madhab == AsrMadhab.HANAFI) "asr_hanafi" else "asr_standard"), listOf(
            t("asr_standard") to { onChange(s.copy(madhab = AsrMadhab.SHAFI)) },
            t("asr_hanafi") to { onChange(s.copy(madhab = AsrMadhab.HANAFI)) },
        ))
        Text(t("adhan_section"), color = Gold, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Toggle(t("play_adhan"), s.adhan) { onChange(s.copy(adhan = it)) }
        Choice(t("adhan_full"), sourceName(s.adhanSource), AdhanSource.entries.map { a -> sourceName(a) to { onChange(s.copy(adhanSource = a)) } })
        Choice(t("adhan_fajr"), sourceName(s.fajrAdhanSource), AdhanSource.entries.map { a -> sourceName(a) to { onChange(s.copy(fajrAdhanSource = a)) } })
        Text(t("per_prayer_section"), color = Gold, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        PrayerEvent.prayers.forEach { e ->
            Toggle(t(prayerKey(e)), e !in s.silent) { on -> onChange(s.copy(silent = if (on) s.silent - e else s.silent + e)) }
        }
        Toggle(t("before10"), s.before10) { onChange(s.copy(before10 = it)) }
        Toggle(t("sunrise_notify"), s.sunrise) { onChange(s.copy(sunrise = it)) }
        Text(t("tab_mosques"), color = Gold, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Toggle(t("online_search"), s.onlineSearch) { onChange(s.copy(onlineSearch = it)) }
        Text(t("online_search_body"), color = Muted, fontSize = 13.sp)
        if (Startup.available) {
            Toggle(t("start_at_login"), s.startAtLogin) { onChange(s.copy(startAtLogin = it)) }
            Text(t("start_at_login_note"), color = Muted, fontSize = 13.sp)
        } else if (Startup.isStorePackage) {
            Text(t("start_at_login_store_note"), color = Muted, fontSize = 13.sp)
        }
    }
}

private fun sourceName(a: AdhanSource) = t(if (a == AdhanSource.MADINAH) "source_madinah" else "source_makkah")

@Composable
private fun Toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ink, modifier = Modifier.weight(1f))
        Switch(on, onChange)
    }
}

@Composable
private fun Choice(label: String, value: String, options: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ink, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }) { Text("$value ▾", color = Gold) }
            DropdownMenu(open, onDismissRequest = { open = false }) {
                options.forEach { (name, pick) -> DropdownMenuItem(text = { Text(name) }, onClick = { pick(); open = false }) }
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
