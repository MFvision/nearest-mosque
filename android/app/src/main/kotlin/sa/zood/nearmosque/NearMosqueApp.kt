package sa.zood.nearmosque

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy

import kotlinx.coroutines.flow.map

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import sa.zood.nearmosque.core.PrayerCalculator
import sa.zood.nearmosque.data.AppDatabase
import sa.zood.nearmosque.data.AskRepository
import sa.zood.nearmosque.data.CityRepository
import sa.zood.nearmosque.data.MosqueRepository
import sa.zood.nearmosque.data.PackManager
import sa.zood.nearmosque.data.SettingsStore
import sa.zood.nearmosque.platform.DevicePosition
import sa.zood.nearmosque.platform.HeadingService
import sa.zood.nearmosque.platform.LocationService
import java.time.Instant

class NearMosqueApp : Application() {
    @Volatile private var started: AppContainer? = null

    /** Made at start, or at the first unlock after a restart (it reads storage that stays locked until then). */
    val container: AppContainer get() = started ?: start()

    override fun onCreate() {
        super.onCreate()
        // Before the first unlock after a restart only the alarms run (LockedBootReceiver, ReminderReceiver and the
        // Adhan player); everything else waits for the unlock.
        if (unlocked(this)) {
            start()
        } else {
            ContextCompat.registerReceiver(
                this,
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        unregisterReceiver(this)
                        start()
                    }
                },
                IntentFilter(Intent.ACTION_USER_UNLOCKED), ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    @Synchronized
    private fun start(): AppContainer {
        started?.let { return it }
        Appearance.apply(this)
        AppStyle.load(this)
        val container = AppContainer(this)
        started = container
        container.start()
        container.cities.preload()
        // Widgets follow the city, the calculation settings, the reminders (bells) and the waits for the iqama.
        container.scope.launch {
            container.settings.settings.map { listOf(it.prayerLocation, it.prayer, it.reminders, it.iqama) }.distinctUntilChanged().collect {
                sa.zood.nearmosque.platform.PrayerWidgets.refresh(this@NearMosqueApp)
            }
        }
        // Silent during prayer: applied at start (on now if a prayer is under way, off if it has ended) and
        // rebuilt whenever the city, the times, the iqama or the silence settings change.
        container.scope.launch {
            container.settings.settings.distinctUntilChangedBy { listOf(it.prayerLocation, it.prayer, it.iqama, it.silence) }.collect {
                sa.zood.nearmosque.platform.PrayerSilence(this@NearMosqueApp).sync(it)
            }
        }
        // The Adhan and the reminders: rebuilt at start (after a force stop or an update nothing else would) and
        // whenever the city, the times, the bells, the alerts or the iqama change, wherever they were changed.
        container.scope.launch {
            container.settings.settings.distinctUntilChangedBy { listOf(it.prayerLocation, it.prayer, it.reminders, it.alerts, it.iqama) }.collect {
                runCatching { sa.zood.nearmosque.platform.ReminderScheduler(this@NearMosqueApp).reschedule(it) }
            }
        }
        return container
    }

    companion object {
        /** False only before the first unlock after a restart. */
        fun unlocked(context: Context): Boolean = context.getSystemService(UserManager::class.java)?.isUserUnlocked != false
    }
}

/** Manual dependency container; tests construct it with an in-memory database and a fixed clock. */
class AppContainer(
    context: Context,
    inMemoryDb: Boolean = false,
    val clock: () -> Instant = Instant::now,
    settingsFile: java.io.File? = null,
    /** Live mosque results; tests pass a fake or a source that is always unavailable. */
    val online: sa.zood.nearmosque.data.OnlineMosqueSource = sa.zood.nearmosque.data.OverpassMosqueSource(),
    /** MET Norway for "Sky follows the weather"; tests pass a fake (nothing is fetched while the switch is off). */
    val weatherSource: sa.zood.nearmosque.data.WeatherSource = sa.zood.nearmosque.data.MetWeatherSource(),
) {
    private val app = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = AppDatabase.create(app, inMemoryDb)
    /** Saved Ask chats (questions only), on this phone. */
    val chats = sa.zood.nearmosque.data.ChatHistory(java.io.File(app.filesDir, if (inMemoryDb) "chats-test.json" else "chats.json"))
    val settings = settingsFile?.let { f ->
        SettingsStore(androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(produceFile = { f }))
    } ?: SettingsStore(app)
    val packs = PackManager(app, db)
    val cities = CityRepository(packs, scope)
    val mosques = MosqueRepository(db)
    /** The chosen city's weather for the sky (opt-in), cached in one small file; refreshed only while the app is open. */
    val weather = sa.zood.nearmosque.data.WeatherRepository(
        weatherSource,
        java.io.File(app.cacheDir, if (inMemoryDb) "weather-cache-test.json" else sa.zood.nearmosque.core.Weather.CACHE_FILE_NAME),
        clock,
    )
    /** Meaning-based library search; vectors are built in the background after the libraries install. */
    val semantic = sa.zood.nearmosque.data.SemanticIndexStore(java.io.File(app.filesDir, "semantic"), db) {
        app.assets.open("model.bin").use { it.readBytes() }
    }
    val ask = AskRepository(
        db, AskRepository.parseStopwords(packs.readAsset("stopwords.json")),
        sa.zood.nearmosque.core.Lexicon.parse(packs.readAsset("lexicon.json")),
        semantic,
    )
    /** Content for languages without bundled content, downloaded when the reader asks. */
    val downloads = sa.zood.nearmosque.data.ContentDownloads(
        packs, db,
        runCatching { sa.zood.nearmosque.core.RemoteCatalog.parse(packs.readAsset("remote-packs.json")) }.getOrDefault(sa.zood.nearmosque.core.RemoteCatalog.EMPTY),
        scope, afterInstall = { semantic.ensure() },
    )
    val location = LocationService(app)
    val heading = HeadingService(app)
    val calculator = PrayerCalculator()

    private val libraryMutex = kotlinx.coroutines.sync.Mutex()
    private val _ready = MutableStateFlow(false)
    /** True once built-in packs are installed (first launch takes a few seconds for the book index). */
    val ready: StateFlow<Boolean> = _ready

    /** Last device fix ("you are here"); never replaced by a map selection or a chosen city. */
    val devicePosition = MutableStateFlow<DevicePosition?>(null)

    /** [installLibraries] = false in tests that do not need the library packs (they take minutes to index). */
    /** False in tests: library packs are installed explicitly there. */
    var autoInstallLibraries = true
        private set

    fun start(installLibraries: Boolean = true) {
        autoInstallLibraries = installLibraries
        scope.launch {
            // Quran, mosques and cities first (the app is usable then); the libraries for the reader's
            // languages follow in the background (the large Arabic fatwa pack takes a while to index).
            runCatching { packs.ensureBuiltins { !sa.zood.nearmosque.data.ChunkScope.isLibrary(it.id) } }
            _ready.value = true
            if (installLibraries) ensureLibraries(sa.zood.nearmosque.ui.Format.languageCode(app))
        }
        devicePosition.value = location.lastKnown()
    }

    /** Built-in packs for the interface language [lang]: Quran, mosques, cities, and that language's libraries. */
    fun builtinsFor(lang: String): (sa.zood.nearmosque.core.PackManifest) -> Boolean {
        val wanted = sa.zood.nearmosque.data.ChunkScope.libraryLanguages(lang)
        return { m -> !sa.zood.nearmosque.data.ChunkScope.isLibrary(m.id) || sa.zood.nearmosque.data.ChunkScope.libraryLanguage(m.id) in wanted }
    }

    /** When "Use my location" last looked for the place (elapsed time since boot), 0 for not yet. */
    @Volatile private var followedAt = 0L

    /**
     * Saves [fix] as the prayer place for "Use my location": its time zone from the fix (the phone's as a hint), named
     * after a bundled city within 30 km, or else [nameForCurrent]. With [always] false nothing is saved when the place
     * has not moved (under 1 km, the same zone), so nothing is rebuilt for nothing.
     */
    suspend fun followFix(fix: DevicePosition, nameForCurrent: String, always: Boolean = true) {
        // The first fix is often city-level (network): fine for prayer times, but the mosque distances and the widgets'
        // nearest mosques start from "you are here" only with a street-level one (the Mosques tab finds its own).
        if ((fix.accuracyMeters ?: Float.MAX_VALUE) <= 100f) devicePosition.value = fix
        val resolver = cities.timeZones()
        val zone = kotlinx.coroutines.withContext(Dispatchers.Default) { resolver.resolve(fix.location, java.time.ZoneId.systemDefault()) }
        val s = settings.settings.first()
        val old = s.prayerLocation
        if (!always && s.followDevice && old != null && old.zoneId == zone.zoneId &&
            sa.zood.nearmosque.core.Geo.distanceMeters(old.location, fix.location) < 1000
        ) return
        val near = zone.nearestCity?.takeIf { (zone.distanceMeters ?: Double.MAX_VALUE) < 30_000 }
        settings.setPrayerLocation(
            sa.zood.nearmosque.data.PrayerLocation(
                near?.name ?: nameForCurrent, fix.location, zone.zoneId, zone.nearestCity?.countryCode, sa.zood.nearmosque.data.PrayerLocation.Source.DEVICE,
                zoneConfirmed = !zone.needsConfirmation, nearCity = near?.location,
            ),
            follow = true,
        )
        if (!s.methodChosenByUser) settings.setMethod(PrayerCalculator.suggestedMethod(zone.nearestCity?.countryCode), byUser = false)
    }

    /**
     * "Use my location" each time the app comes to the screen: a fresh fix at most every 30 minutes, after 2 when the
     * phone's time zone no longer matches the place's (a flight). Silent: it never asks and shows nothing without a fix.
     */
    suspend fun refreshFollowedPlace(nameForCurrent: String) {
        val s = settings.settings.first()
        if (!s.followDevice || location.permission() == sa.zood.nearmosque.platform.LocationPermission.DENIED || !location.isLocationEnabled()) return
        val now = android.os.SystemClock.elapsedRealtime()
        val zoneChanged = s.prayerLocation?.zoneId?.rules != java.time.ZoneId.systemDefault().rules
        if (followedAt != 0L && now - followedAt < (if (zoneChanged) 2 else 30) * 60_000L) return
        followedAt = now
        val fix = location.currentPosition() ?: location.lastKnown(30 * 60_000L) ?: return
        followFix(fix, nameForCurrent, always = false)
    }

    /** Installs the bundled library packs for [lang] (see ChunkScope.libraryLanguages) if missing. */
    fun ensureLibraries(lang: String) {
        if (!autoInstallLibraries) return
        val wanted = sa.zood.nearmosque.data.ChunkScope.libraryLanguages(lang)
        scope.launch {
            libraryMutex.withLock {
                runCatching {
                    packs.ensureBuiltins { sa.zood.nearmosque.data.ChunkScope.isLibrary(it.id) && sa.zood.nearmosque.data.ChunkScope.libraryLanguage(it.id) in wanted }
                }
                runCatching { semantic.ensure() }
            }
        }
    }
}

/** Light / dark / same as the phone (Settings). Kept in plain preferences so it applies before the first frame. */
object Appearance {
    const val SYSTEM = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    const val LIGHT = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
    const val DARK = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
    private const val KEY = "appearance"

    fun current(context: Context): Int = context.getSharedPreferences("ui", Context.MODE_PRIVATE).getInt(KEY, SYSTEM)

    fun apply(context: Context) = androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(current(context))

    fun set(context: Context, mode: Int) {
        context.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putInt(KEY, mode).apply()
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
    }
}

/**
 * One style for everything (Settings → App and widget style): the app's accent colour and every widget left on
 * "Same as app". The same five styles and colours as on iPhone (ios/Shared/StyleAccent.swift).
 */
object AppStyle {
    val all = listOf("cream", "green", "night", "teal", "sky")
    private const val KEY = "app_style"

    /** The chosen style; Compose reads it, so the accent changes at once. */
    private val state = androidx.compose.runtime.mutableStateOf("cream")
    val current: String get() = state.value

    fun load(context: Context) { state.value = read(context) }

    fun read(context: Context): String =
        context.getSharedPreferences("ui", Context.MODE_PRIVATE).getString(KEY, null)?.takeIf { it in all } ?: "cream"

    fun set(context: Context, style: String) {
        if (style !in all) return
        context.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putString(KEY, style).apply()
        state.value = style
        sa.zood.nearmosque.platform.PrayerWidgets.refresh(context)
    }

    /** Widgets read the saved choice (they may draw before the app has run). */
    fun widgetStyle(context: Context) = sa.zood.nearmosque.platform.WidgetStyle.of(read(context)) ?: sa.zood.nearmosque.platform.WidgetStyle.CREAM

    /** Accent on dark backgrounds (and the bright swatch). */
    fun dark(style: String): androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(
        when (style) { "green" -> 0xFF5FCB93; "night" -> 0xFF8FB8EA; "teal" -> 0xFF8CD9CC; "sky" -> 0xFF9CCDEB; else -> 0xFFD4A843 },
    )

    /** Accent for text on light backgrounds (4.5:1). */
    fun light(style: String): androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(
        when (style) { "green" -> 0xFF1E6B45; "night" -> 0xFF1F4F80; "teal" -> 0xFF1F6A62; "sky" -> 0xFF22618A; else -> 0xFF6E5414 },
    )
}

val Context.container: AppContainer get() = (applicationContext as NearMosqueApp).container

/** The container of the running UI (AppRoot provides it; tests pass their own). */
val LocalAppContainer = androidx.compose.runtime.staticCompositionLocalOf<AppContainer?> { null }

@androidx.compose.runtime.Composable
fun appContainer(): AppContainer = LocalAppContainer.current ?: androidx.compose.ui.platform.LocalContext.current.container
