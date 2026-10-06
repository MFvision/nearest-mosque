package sa.zood.nearmosque.core

import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.Inflater

/** One file of a downloadable pack: compressed ([bytes], [sha256]) and installed ([size]). */
@Serializable
data class RemoteFile(val path: String, val asset: String, val bytes: Long, val sha256: String, val size: Long)

@Serializable
data class RemotePack(
    val id: String,
    val language: String,
    val title: Map<String, String>,
    val version: Int,
    val recordCount: Int? = null,
    val manifestSha256: String,
    /** Download size (compressed). */
    val bytes: Long,
    /** Size once installed. */
    val installedBytes: Long,
    val files: List<RemoteFile>,
) {
    fun title(lang: String): String = title[lang] ?: title["en"] ?: id
}

/**
 * The content packs the app may download (shared/content/remote-packs.json, bundled with the app). Only
 * what it lists is ever downloaded, from [baseUrl], and a pack is installed only when its manifest matches
 * [RemotePack.manifestSha256]; the manifest then fixes the SHA-256 of every file. So the host (GitHub
 * releases now, another server later) can serve only exactly the content this app version was built with.
 */
@Serializable
data class RemoteCatalog(val schemaVersion: Int, val baseUrl: String, val packs: List<RemotePack> = emptyList()) {
    fun forLanguage(lang: String): List<RemotePack> = packs.filter { it.language == lang }

    companion object {
        val EMPTY = RemoteCatalog(1, "https://invalid/", emptyList())

        fun parse(json: String): RemoteCatalog {
            val c = try {
                PackJson.decodeFromString(serializer(), json)
            } catch (e: Exception) {
                throw PackError.Format("catalog: ${e.message}")
            }
            if (c.schemaVersion > 1) throw PackError.NewerSchema(c.schemaVersion)
            if (!c.baseUrl.startsWith("https://") || !c.baseUrl.endsWith("/")) throw PackError.Format("catalog: base URL")
            val safe = Regex("[A-Za-z0-9._-]+")
            if (c.packs.any { p -> p.files.any { !safe.matches(it.asset) || it.path.contains("..") || it.path.startsWith("/") } }) {
                throw PackError.Format("catalog: unsafe name")
            }
            return c
        }
    }
}

/** A downloaded pack, checked and ready for [PackVerifier.verify] and installation. */
class FetchedPack(val manifest: PackManifest, val files: Map<String, ByteArray>)

object RemotePacks {
    /**
     * Downloads every file of [pack] with [get] (asset URL to bytes), checks each compressed file's size and
     * SHA-256, inflates it, checks its installed size, and checks the manifest against the catalog. Throws
     * before anything is installed if any check fails. [progress] gets (files done, files in all).
     */
    fun fetch(catalog: RemoteCatalog, pack: RemotePack, get: (String) -> ByteArray, progress: (Int, Int) -> Unit = { _, _ -> }): FetchedPack {
        val files = HashMap<String, ByteArray>()
        pack.files.forEachIndexed { i, f ->
            val z = get(catalog.baseUrl + f.asset)
            if (z.size.toLong() != f.bytes || !sha256(z).equals(f.sha256, ignoreCase = true)) throw PackError.Checksum(f.asset)
            val raw = inflate(z, f.size)
            files[f.path] = raw
            progress(i + 1, pack.files.size)
        }
        val manifestBytes = files["manifest.json"] ?: throw PackError.Format("no manifest")
        if (!sha256(manifestBytes).equals(pack.manifestSha256, ignoreCase = true)) throw PackError.Checksum("manifest.json")
        val manifest = PackVerifier.parseManifest(manifestBytes.decodeToString())
        if (manifest.id != pack.id || manifest.version != pack.version || manifest.kind != "sources") throw PackError.Format("manifest does not match catalog")
        return FetchedPack(manifest, files)
    }

    /** Raw DEFLATE, refusing anything that inflates to more or fewer than [size] bytes. */
    fun inflate(data: ByteArray, size: Long): ByteArray {
        val inflater = Inflater(true)
        try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream(size.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
            val buf = ByteArray(64 * 1024)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buf, 0, n)
                if (out.size() > size) throw PackError.Format("inflated size")
            }
            if (out.size().toLong() != size) throw PackError.Format("inflated size")
            return out.toByteArray()
        } finally {
            inflater.end()
        }
    }

    fun sha256(data: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
}
