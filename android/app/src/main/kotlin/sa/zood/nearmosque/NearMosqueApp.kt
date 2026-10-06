package sa.zood.nearmosque

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
        container = AppContainer(this)
        container.start()
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

val Context.container: AppContainer get() = (applicationContext as NearMosqueApp).container

/** The container of the running UI (AppRoot provides it; tests pass their own). */
val LocalAppContainer = androidx.compose.runtime.staticCompositionLocalOf<AppContainer?> { null }

@androidx.compose.runtime.Composable
fun appContainer(): AppContainer = LocalAppContainer.current ?: androidx.compose.ui.platform.LocalContext.current.container
