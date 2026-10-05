package sa.zood.nearmosque.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Books opened from the library, downloaded once (only when the user taps "Read in the app") and kept
 * in app storage so they open again offline. Only HTTPS files from IslamHouse (and pages from
 * binbaz.org.sa) are opened.
 */
class LibraryFiles(private val context: Context) {
    private val dir get() = File(context.filesDir, "library").apply { mkdirs() }

    fun local(itemKey: String, url: String): File = File(dir, fileName(itemKey, url))

    /** Downloaded files with the library item each belongs to (ih_en_123.pdf -> ih:en:123), newest first. */
    fun downloaded(): List<Pair<String, File>> = (dir.listFiles() ?: emptyArray())
        .filter { it.isFile && !it.name.endsWith(".part") }
        .sortedByDescending { it.lastModified() }
        .map { itemKey(it.name) to it }

    fun delete(file: File) { if (file.parentFile == dir) file.delete() }

    fun deleteAll() { dir.listFiles()?.forEach { it.delete() } }

    suspend fun fetch(url: String, itemKey: String, onProgress: (Float) -> Unit = {}): File = withContext(Dispatchers.IO) {
        require(isAllowed(url)) { "Not an IslamHouse file" }
        val target = local(itemKey, url)
        if (target.isFile && target.length() > 0) return@withContext target
        val tmp = File(target.path + ".part")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            if (!isAllowed(conn.url.toString())) throw IOException("Redirected away from IslamHouse")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (!tmp.renameTo(target)) throw IOException("Could not save the file")
            target
        } finally {
            conn.disconnect()
            tmp.delete()
        }
    }

    companion object {
        private val HOSTS = setOf("islamhouse.com", "d1.islamhouse.com", "d2.islamhouse.com", "www.islamhouse.com", "binbaz.org.sa", "www.binbaz.org.sa", "hadeethenc.com", "quranenc.com")

        fun isAllowed(url: String): Boolean = runCatching {
            val u = URI(url)
            u.scheme == "https" && (u.host in HOSTS || u.host.endsWith(".islamhouse.com"))
        }.getOrDefault(false)

        /** Inverse of [fileName] for library ids ("ih:en:123" is stored as "ih_en_123.pdf"). */
        fun itemKey(fileName: String): String = fileName.substringBeforeLast('.').split('_', limit = 3).joinToString(":")

        /** Stable, filesystem-safe name: the item id plus the file's extension. */
        fun fileName(itemKey: String, url: String): String {
            val ext = url.substringAfterLast('/').substringAfterLast('.', "bin").lowercase().filter { it.isLetterOrDigit() }.take(5).ifEmpty { "bin" }
            return itemKey.replace(Regex("[^A-Za-z0-9_-]"), "_") + "." + ext
        }
    }
}
