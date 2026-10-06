package sa.zood.nearmosque.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sa.zood.nearmosque.core.RemoteCatalog
import sa.zood.nearmosque.core.RemotePack
import sa.zood.nearmosque.core.RemotePacks
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Content for languages without bundled content (hadith, Quran translation, tafsir, library), downloaded
 * only when the reader taps Download. Only packs listed in the bundled catalog are fetched, and each is
 * checked against it before installation (see [RemoteCatalog]).
 */
class ContentDownloads(
    private val packs: PackManager,
    private val db: AppDatabase,
    val catalog: RemoteCatalog,
    private val scope: CoroutineScope,
    /** After a pack installs (builds the meaning-based search vectors). */
    private val afterInstall: suspend () -> Unit,
    private val get: (String) -> ByteArray = ::httpGet,
) {
    sealed interface State {
        data class Running(val done: Long, val total: Long) : State
        data object Done : State
        data object Failed : State
    }

    private val _state = MutableStateFlow<Map<String, State>>(emptyMap())
    /** Per language, while or after downloading in this session. */
    val state: StateFlow<Map<String, State>> = _state
    private val jobs = HashMap<String, Job>()

    /** Packs for [lang] that are not installed at the catalog's version. */
    suspend fun missing(lang: String): List<RemotePack> = withContext(Dispatchers.IO) {
        val installed = db.packs().all().associate { it.id to it.version }
        catalog.forLanguage(lang).filter { installed[it.id] != it.version }
    }

    fun download(lang: String) {
        if (jobs[lang]?.isActive == true) return
        jobs[lang] = scope.launch(Dispatchers.IO) {
            val todo = missing(lang)
            val total = todo.sumOf { it.bytes }
            var done = 0L
            set(lang, State.Running(0, total))
            try {
                for (p in todo) {
                    val fetched = RemotePacks.fetch(catalog, p, get = { url ->
                        get(url).also { done += it.size; set(lang, State.Running(done, total)) }
                    })
                    packs.installDownloaded(fetched)
                }
                runCatching { afterInstall() }
                set(lang, State.Done)
            } catch (e: Exception) {
                set(lang, State.Failed)
            }
        }
    }

    private fun set(lang: String, s: State) = _state.update { it + (lang to s) }

    companion object {
        private const val MAX_FILE = 200L * 1024 * 1024

        fun httpGet(url: String): ByteArray {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "NearMosque")
            try {
                if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
                return c.inputStream.use { input ->
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        if (out.size() > MAX_FILE) throw IOException("too large")
                    }
                    out.toByteArray()
                }
            } finally {
                c.disconnect()
            }
        }
    }
}
