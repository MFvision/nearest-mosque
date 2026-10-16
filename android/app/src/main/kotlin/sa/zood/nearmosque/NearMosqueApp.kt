package sa.zood.nearmosque

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy

import kotlinx.coroutines.flow.map

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Appearance.apply(this)
        AppStyle.load(this)
        container = AppContainer(this)
        container.start()
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
    val cities = CityRepository(packs)
    val mosques = MosqueRepository(db)
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
