package sa.zood.nearmosque.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import sa.zood.nearmosque.core.PackJson
import sa.zood.nearmosque.core.SemanticText
import sa.zood.nearmosque.core.SourceChunk
import sa.zood.nearmosque.core.StaticEmbedder
import sa.zood.nearmosque.core.VectorIndex
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Semantic vectors of the installed library collections (see core SemanticSearch). They are computed
 * on the phone from the installed records with the bundled model, once per pack install, and cached as
 * files in [dir]; nothing leaves the device. Until a collection's vectors exist, Ask uses word search only.
 */
class SemanticIndexStore(private val dir: File, private val db: AppDatabase, private val model: () -> ByteArray) {
    private val mutex = Mutex()
    private val embedder by lazy { StaticEmbedder.load(model()) }
    private val indexes = ConcurrentHashMap<String, VectorIndex>()

    /** Collections whose vectors are loaded. */
    val ready: Set<String> get() = indexes.keys

    fun index(packId: String): VectorIndex? = indexes[packId]

    fun embed(text: String): DoubleArray = embedder.embed(text)

    /** Loads or builds the vectors of every installed library pack and drops those of removed packs. */
    suspend fun ensure() = mutex.withLock {
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val packs = db.packs().all().filter { ChunkScope.isLibrary(it.id) }
            val names = packs.associateBy { fileName(it) }
            dir.listFiles()?.filter { it.name !in names }?.forEach { it.delete() }
            indexes.keys.retainAll(packs.map { it.id }.toSet())
            for ((name, p) in names) {
                if (indexes[p.id] != null && File(dir, name).exists()) continue
                val f = File(dir, name)
                val index = (if (f.exists()) runCatching { read(f) }.getOrNull() else null) ?: build(p.id).also { write(f, it) }
                indexes[p.id] = index
            }
        }
    }

    private fun fileName(p: InstalledPackEntity) = "${p.id}@${p.version}-${p.installedAt}.vec"

    private suspend fun build(packId: String): VectorIndex {
        val ids = ArrayList<String>()
        val vectors = java.io.ByteArrayOutputStream()
        val last = db.sources().maxSeq(packId) ?: 0
        var from = 0L
        while (from <= last) {
            for (row in db.sources().range(packId, from, from + BATCH - 1)) {
                val chunk = PackJson.decodeFromString(SourceChunk.serializer(), row.json)
                ids += row.id
                vectors.write(StaticEmbedder.quantize(embedder.embed(SemanticText.of(chunk))))
            }
            from += BATCH
        }
        return VectorIndex(ids, vectors.toByteArray(), embedder.dim)
    }

    private fun write(f: File, index: VectorIndex) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        DataOutputStream(tmp.outputStream().buffered()).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(index.dim)
            out.writeInt(index.ids.size)
            for (id in index.ids) out.writeUTF(id)
            out.write(index.vectorBytes())
        }
        tmp.renameTo(f)
    }

    private fun read(f: File): VectorIndex = DataInputStream(f.inputStream().buffered()).use { inp ->
        require(inp.readInt() == MAGIC)
        val dim = inp.readInt()
        val n = inp.readInt()
        val ids = List(n) { inp.readUTF() }
        val bytes = ByteArray(n * dim).also { inp.readFully(it) }
        VectorIndex(ids, bytes, dim)
    }

    companion object {
        private const val MAGIC = 0x4E4D5631 // "NMV1"
        private const val BATCH = 1000L
    }
}
