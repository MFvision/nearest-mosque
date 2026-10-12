package sa.zood.nearmosque.core

import kotlinx.serialization.Serializable

/** One printed volume of a classical book, as a PDF ([file]) of [bytes] with this SHA-256. */
@Serializable
data class BookVolume(val file: String, val volume: Int, val pages: Int, val bytes: Long, val sha256: String)

/** A classical book of the library (shared/books/catalog.json): the author's text from OpenITI, one PDF per volume. */
@Serializable
data class ClassicBook(
    val id: String,
    val group: String,
    val title: String,
    val author: String,
    val edition: String,
    val volumes: List<BookVolume>,
) {
    val bytes: Long get() = volumes.sumOf { it.bytes }
}

/**
 * The classical books the app offers (bundled with the app). Only the files it lists are downloaded, from [baseUrl],
 * and a file is kept only when its SHA-256 matches, so the host can serve nothing but these books.
 */
@Serializable
data class BookCatalog(val schemaVersion: Int, val baseUrl: String, val source: String = "", val books: List<ClassicBook> = emptyList()) {
    fun url(v: BookVolume): String = baseUrl + v.file

    /** The books by group, in [GROUPS] order (groups the catalog does not use are left out). */
    val byGroup: List<Pair<String, List<ClassicBook>>>
        get() = GROUPS.mapNotNull { g -> books.filter { it.group == g }.takeIf { it.isNotEmpty() }?.let { g to it } }

    companion object {
        val EMPTY = BookCatalog(1, "https://invalid/")
        /** The order the library shows the groups in. */
        val GROUPS = listOf("aqidah", "tafsir", "hadith", "fiqh", "sira", "tarikh", "raqaiq")

        fun parse(json: String): BookCatalog {
            val c = try {
                PackJson.decodeFromString(serializer(), json)
            } catch (e: Exception) {
                throw PackError.Format("books: ${e.message}")
            }
            if (c.schemaVersion > 1) throw PackError.NewerSchema(c.schemaVersion)
            if (!c.baseUrl.startsWith("https://") || !c.baseUrl.endsWith("/")) throw PackError.Format("books: base URL")
            val safe = Regex("[A-Za-z0-9._-]+")
            val hex = Regex("[0-9a-f]{64}")
            for (b in c.books) {
                if (!safe.matches(b.id) || b.volumes.isEmpty()) throw PackError.Format("books: ${b.id}")
                for (v in b.volumes) {
                    if (!v.file.endsWith(".pdf") || v.file.startsWith(".") || !safe.matches(v.file) || !hex.matches(v.sha256) || v.bytes <= 0) {
                        throw PackError.Format("books: unsafe file ${v.file}")
                    }
                }
            }
            return c
        }
    }
}
