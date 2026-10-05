package sa.zood.nearmosque.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import sa.zood.nearmosque.core.Answer
import sa.zood.nearmosque.core.AnswerComposer
import sa.zood.nearmosque.core.CandidateChunk
import sa.zood.nearmosque.core.ChunkStore
import sa.zood.nearmosque.core.CityIndex
import sa.zood.nearmosque.core.CommonQuestion
import sa.zood.nearmosque.core.Geo
import sa.zood.nearmosque.core.LatLng
import sa.zood.nearmosque.core.Mosque
import sa.zood.nearmosque.core.MosqueCategory
import sa.zood.nearmosque.core.MosqueRanking
import sa.zood.nearmosque.core.PackJson
import sa.zood.nearmosque.core.PackManifest
import sa.zood.nearmosque.core.RankedMosque
import sa.zood.nearmosque.core.Retriever
import sa.zood.nearmosque.core.SourceChunk
import sa.zood.nearmosque.core.SourceDocument
import sa.zood.nearmosque.core.TimeZoneResolver

class CityRepository(private val packs: PackManager) {
    private val mutex = Mutex()
    private var index: CityIndex? = null

    suspend fun index(): CityIndex = mutex.withLock {
        index ?: withContext(Dispatchers.IO) { CityIndex.parse(packs.readAsset("cities/cities.tsv")) }.also { index = it }
    }

    suspend fun timeZones(): TimeZoneResolver = TimeZoneResolver(index())
}

/** Outcome of a nearest-mosque query; every empty case is distinct. */
sealed interface MosqueResult {
    data class Found(val items: List<RankedMosque>, val coverage: List<String>) : MosqueResult
    data class NoRecordsInCoverage(val coverage: String, val radiusMeters: Double) : MosqueResult
    data class AreaNotDownloaded(val installed: List<String>) : MosqueResult
}

class MosqueRepository(private val db: AppDatabase) {
    val favorites: Flow<Set<String>> = db.mosques().observeFavorites().map { list -> list.map { it.sourceId }.toSet() }

    suspend fun nearest(center: LatLng, radiusMeters: Double, lang: String): MosqueResult = withContext(Dispatchers.IO) {
        val packs = db.packs().all().filter { it.kind == "mosques" }.map { PackJson.decodeFromString(PackManifest.serializer(), it.manifestJson) }
        if (packs.isEmpty()) return@withContext MosqueResult.AreaNotDownloaded(emptyList())
        val box = Geo.boundingBox(center, radiusMeters)
        val ranges = when {
            box.minLng < -180.0 -> listOf(box.minLng + 360.0 to 180.0, -180.0 to box.maxLng)
            box.maxLng > 180.0 -> listOf(box.minLng to 180.0, -180.0 to box.maxLng - 360.0)
            else -> listOf(box.minLng to box.maxLng)
        }
        val rows = ranges.flatMap { (a, b) -> db.mosques().inBox(box.minLat, box.maxLat, a, b) }
        val ranked = MosqueRanking.rank(center, rows.mapNotNull { it.toMosque() }, radiusMeters)
        val covering = packs.filter { m ->
            val b = m.coverage?.bbox ?: return@filter false
            center.latitude in b.minLat..b.maxLat && center.longitude in b.minLng..b.maxLng
        }
        when {
            ranked.isNotEmpty() -> MosqueResult.Found(ranked, covering.map { it.coverage?.name ?: it.title(lang) })
            covering.isNotEmpty() -> MosqueResult.NoRecordsInCoverage(covering.first().coverage?.name ?: covering.first().title(lang), radiusMeters)
            else -> MosqueResult.AreaNotDownloaded(packs.map { it.coverage?.name ?: it.title(lang) })
        }
    }

    suspend fun byIds(ids: Collection<String>): List<Mosque> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) emptyList() else db.mosques().byIds(ids.toList()).mapNotNull { it.toMosque() }
    }

    suspend fun setFavorite(id: String, on: Boolean) = withContext(Dispatchers.IO) {
        if (on) db.mosques().addFavorite(FavoriteEntity(id, System.currentTimeMillis())) else db.mosques().removeFavorite(id)
    }

    suspend fun packManifest(packId: String): PackManifest? = withContext(Dispatchers.IO) {
        db.packs().get(packId)?.let { PackJson.decodeFromString(PackManifest.serializer(), it.manifestJson) }
    }
}

fun MosqueEntity.toMosque(): Mosque? {
    val loc = LatLng.orNull(lat, lng) ?: return null
    val names = runCatching { PackJson.decodeFromString(PackManager.namesSerializer, namesJson) }.getOrDefault(emptyMap())
    return Mosque(
        sourceId, packId, if (category == "prayer_space") MosqueCategory.PRAYER_SPACE else MosqueCategory.MOSQUE,
        names, loc, address, phone, website, openingHoursRaw, sourceTimestamp,
    )
}

/** Full-text candidates from Room's FTS4 table. Must be called off the main thread. */
class RoomChunkStore(private val dao: SourceDao, private val scope: ChunkScope = ChunkScope.Books) : ChunkStore {
    override val totalChunks: Int by lazy { dao.rawInt(countQuery(scope)) }
    override val averageLength: Double by lazy { dao.rawDouble(averageQuery(scope)) }
    override fun documentFrequency(term: String): Int = dao.rawInt(ftsCountQuery(term, scope))
    override fun candidates(terms: Collection<String>): List<CandidateChunk> =
        if (terms.isEmpty()) emptyList() else dao.candidates(candidatesQuery(terms, scope)).map {
            CandidateChunk(it.id, it.seq, if (it.searchText.isEmpty()) emptyList() else it.searchText.split(' '))
        }
}

data class ResolvedCitation(val chunk: SourceChunk, val packId: String, val documents: Map<String, SourceDocument>)

class AskRepository(private val db: AppDatabase, private val stopwords: Map<String, List<String>>) {
    private val mutex = Mutex()
    private var cached: Pair<Long, Retriever>? = null
    private var libraries: Map<String, Retriever> = emptyMap()
    private var questionsCache: List<CommonQuestion> = emptyList()
    private val libraryStopwords = sa.zood.nearmosque.core.libraryStopwords(stopwords)

    /** Rebuilt whenever the installed packs change (keyed by install timestamps). */
    private suspend fun retriever(): Retriever = mutex.withLock {
        val key = db.packs().all().filter { it.kind == "sources" }.sumOf { it.installedAt xor it.recordCount.toLong() }
        cached?.takeIf { it.first == key }?.second ?: run {
            questionsCache = db.sources().questions().map { PackJson.decodeFromString(CommonQuestion.serializer(), it.json) }
            libraries = db.sources().chunkPackIds().filter { ChunkScope.isLibrary(it) }.sorted()
                .associateWith { Retriever(RoomChunkStore(db.sources(), ChunkScope.Library(it)), emptyList(), libraryStopwords, sa.zood.nearmosque.core.Retriever.Gates.LIBRARY) }
            Retriever(RoomChunkStore(db.sources()), questionsCache, stopwords).also { cached = key to it }
        }
    }

    /**
     * Library items for the question: the libraries in the interface language first, then English and
     * Arabic; the first language with evidence wins. With several libraries in that language (IslamHouse
     * and the Ibn Baz fatwas in Arabic), their results are interleaved, strongest library first.
     */
    private suspend fun library(question: String, context: List<String>, lang: String): List<String> {
        retriever()
        for (l in listOf(lang, "en", "ar").distinct()) {
            val hits = libraries.filterKeys { ChunkScope.libraryLanguage(it) == l }.values
                .map { it.retrieve(question, context) }
                .filter { it.kind != sa.zood.nearmosque.core.AnswerKind.INSUFFICIENT && it.passages.isNotEmpty() }
            if (hits.isNotEmpty()) return interleave(hits.map { it.passages }).take(LIBRARY_RESULTS)
        }
        return emptyList()
    }

    suspend fun commonQuestions(): List<CommonQuestion> = withContext(Dispatchers.IO) {
        retriever()
        questionsCache
    }

    suspend fun ask(question: String, context: List<String>, lang: String = "en"): Answer = withContext(Dispatchers.IO) {
        AnswerComposer.compose(question, retriever().retrieve(question, context)).copy(library = library(question, context, lang))
    }

    suspend fun answerFor(q: CommonQuestion, displayQuestion: String, lang: String = "en"): Answer = withContext(Dispatchers.IO) {
        val r = retriever().retrieve(displayQuestion)
        // A tapped common question always shows that question's answer, plus any extra passages found.
        AnswerComposer.compose(displayQuestion, r.copy(kind = sa.zood.nearmosque.core.AnswerKind.COMMON, commonQuestion = q))
            .copy(library = library(displayQuestion, emptyList(), lang))
    }

    suspend fun resolve(ids: List<String>): List<ResolvedCitation> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        val docs = db.sources().documents().associate { it.id to PackJson.decodeFromString(SourceDocument.serializer(), it.json) }
        val rows = db.sources().chunks(ids).associateBy { it.id }
        ids.mapNotNull { id -> rows[id]?.let { ResolvedCitation(PackJson.decodeFromString(SourceChunk.serializer(), it.json), it.packId, docs) } }
    }

    suspend fun context(c: ResolvedCitation, around: Int = 3): List<SourceChunk> = withContext(Dispatchers.IO) {
        db.sources().range(c.packId, c.chunk.seq - around, c.chunk.seq + around).map { PackJson.decodeFromString(SourceChunk.serializer(), it.json) }
    }

    companion object {
        const val LIBRARY_RESULTS = 6

        /** Round-robin over ranked lists, starting with the list whose best passage scores highest. */
        fun interleave(lists: List<List<sa.zood.nearmosque.core.ScoredPassage>>): List<String> {
            val ordered = lists.sortedByDescending { it.first().score }
            val out = LinkedHashSet<String>()
            for (i in 0 until (ordered.maxOfOrNull { it.size } ?: 0)) ordered.forEach { l -> l.getOrNull(i)?.let { out.add(it.chunkId) } }
            return out.toList()
        }

        fun parseStopwords(json: String): Map<String, List<String>> {
            val obj = Json.parseToJsonElement(json) as kotlinx.serialization.json.JsonObject
            // Language lists plus `_domain`; the Retriever itself ignores keys starting with "_".
            return obj.filterValues { it is kotlinx.serialization.json.JsonArray }.mapValues { (_, v) ->
                (v as kotlinx.serialization.json.JsonArray).map { (it as kotlinx.serialization.json.JsonPrimitive).content }
            }
        }
    }
}
