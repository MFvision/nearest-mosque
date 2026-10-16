package sa.zood.nearmosque.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sa.zood.nearmosque.core.BookCatalog
import sa.zood.nearmosque.core.BookVolume
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest

/**
 * The classical books (shared/content/books-catalog.json): each volume is downloaded once into the app's folder,
 * kept only if its SHA-256 matches the catalog, and opened in the PC's PDF reader (xdg-open on Linux).
 */
object Books {
    val catalog: BookCatalog by lazy { runCatching { BookCatalog.parse(Strings.resource("books-catalog.json")) }.getOrDefault(BookCatalog.EMPTY) }

    private val dir: File by lazy { File(Store.dataDir, "books").apply { mkdirs() } }
    private val http: HttpClient by lazy { HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build() }

    fun file(v: BookVolume) = File(dir, v.file)

    fun isDownloaded(v: BookVolume) = file(v).length() == v.bytes

    /** Downloads the volume if needed, then opens it. False if it could not be fetched or checked. */
    suspend fun open(v: BookVolume): Boolean = withContext(Dispatchers.IO) {
        val target = file(v)
        if (!isDownloaded(v)) {
            val url = catalog.url(v)
            if (!url.startsWith("https://raw.githubusercontent.com/MFvision/nearest-mosque-content/")) return@withContext false
            val tmp = File(dir, v.file + ".part")
            val ok = runCatching {
                val res = http.send(HttpRequest.newBuilder(URI(url)).GET().build(), HttpResponse.BodyHandlers.ofFile(tmp.toPath()))
                res.statusCode() in 200..299 && sha256(tmp) == v.sha256 && tmp.renameTo(target)
            }.getOrDefault(false)
            tmp.delete()
            if (!ok) return@withContext false
        }
        Platform.openFile(target)
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
