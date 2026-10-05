package sa.zood.nearmosque.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "installed_pack")
data class InstalledPackEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val version: Int,
    val schemaVersion: Int,
    val manifestJson: String,
    val recordCount: Int,
    val bytes: Long,
    val installedAt: Long,
    val builtin: Boolean,
    /** Average token length of this pack's chunks (BM25), 0 for non-book packs. */
    val avgTokens: Double = 0.0,
)

@Entity(tableName = "mosque", indices = [Index("packId"), Index("lat", "lng")])
data class MosqueEntity(
    @PrimaryKey val sourceId: String,
    val packId: String,
    val category: String,
    val namesJson: String,
    val defaultName: String?,
    val lat: Double,
    val lng: Double,
    val address: String?,
    val phone: String?,
    val website: String?,
    val openingHoursRaw: String?,
    val sourceTimestamp: String?,
)

/** Favorites reference source ids only, so they survive pack updates and removal. */
@Entity(tableName = "favorite")
data class FavoriteEntity(@PrimaryKey val sourceId: String, val addedAt: Long)

@Entity(tableName = "source_document", indices = [Index("packId")])
data class SourceDocumentEntity(@PrimaryKey val id: String, val packId: String, val json: String)

@Entity(tableName = "source_chunk", indices = [Index("packId"), Index("seq")])
data class SourceChunkEntity(
    @PrimaryKey val id: String,
    val packId: String,
    val seq: Long,
    val anchor: String,
    /** The chunk exactly as shipped (original text verbatim). */
    val json: String,
    /** Normalized tokens, space separated; never displayed. */
    val searchText: String,
    val tokenCount: Int,
)

@Fts4(contentEntity = SourceChunkEntity::class)
@Entity(tableName = "source_chunk_fts")
data class SourceChunkFts(val searchText: String)

@Entity(tableName = "common_question", indices = [Index("packId")])
data class CommonQuestionEntity(@PrimaryKey val id: String, val packId: String, val position: Int, val json: String)

@Dao
interface PackDao {
    @Query("SELECT * FROM installed_pack ORDER BY kind, id")
    fun observeAll(): Flow<List<InstalledPackEntity>>

    @Query("SELECT * FROM installed_pack")
    suspend fun all(): List<InstalledPackEntity>

    @Query("SELECT * FROM installed_pack WHERE id = :id")
    suspend fun get(id: String): InstalledPackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(p: InstalledPackEntity)

    @Query("DELETE FROM installed_pack WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface MosqueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MosqueEntity>)

    @Query("DELETE FROM mosque WHERE packId = :packId")
    suspend fun deleteForPack(packId: String)

    @Query("SELECT * FROM mosque WHERE lat BETWEEN :minLat AND :maxLat AND lng BETWEEN :minLng AND :maxLng")
    suspend fun inBox(minLat: Double, maxLat: Double, minLng: Double, maxLng: Double): List<MosqueEntity>

    @Query("SELECT * FROM mosque WHERE sourceId IN (:ids)")
    suspend fun byIds(ids: List<String>): List<MosqueEntity>

    @Query("SELECT * FROM favorite")
    fun observeFavorites(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(f: FavoriteEntity)

    @Query("DELETE FROM favorite WHERE sourceId = :id")
    suspend fun removeFavorite(id: String)
}

@Dao
interface SourceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(items: List<SourceChunkEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocuments(items: List<SourceDocumentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(items: List<CommonQuestionEntity>)

    @Query("DELETE FROM source_chunk WHERE packId = :packId")
    suspend fun deleteChunks(packId: String)

    @Query("DELETE FROM source_document WHERE packId = :packId")
    suspend fun deleteDocuments(packId: String)

    @Query("DELETE FROM common_question WHERE packId = :packId")
    suspend fun deleteQuestions(packId: String)

    @RawQuery
    fun rawInt(query: SupportSQLiteQuery): Int

    @RawQuery
    fun rawDouble(query: SupportSQLiteQuery): Double

    @RawQuery
    fun candidates(query: SupportSQLiteQuery): List<CandidateRow>

    @Query("SELECT DISTINCT packId FROM source_chunk")
    fun chunkPackIds(): List<String>

    @Query("SELECT * FROM source_chunk WHERE id IN (:ids)")
    suspend fun chunks(ids: List<String>): List<SourceChunkEntity>

    @Query("SELECT * FROM source_chunk WHERE packId = :packId AND seq BETWEEN :from AND :to ORDER BY seq")
    suspend fun range(packId: String, from: Long, to: Long): List<SourceChunkEntity>

    @Query("SELECT id FROM source_chunk WHERE packId = :packId ORDER BY seq LIMIT :limit OFFSET :offset")
    suspend fun pageIds(packId: String, limit: Int, offset: Int): List<String>

    @Query("SELECT * FROM source_document")
    suspend fun documents(): List<SourceDocumentEntity>

    @Query("SELECT * FROM common_question ORDER BY packId, position")
    suspend fun questions(): List<CommonQuestionEntity>
}

data class CandidateRow(val id: String, val seq: Long, val searchText: String)

/**
 * Which chunks a retriever sees. Books (the Quran pack and other cited books) and each IslamHouse
 * library pack are separate collections with their own BM25 statistics, so adding the library never
 * changes how verses rank.
 */
sealed class ChunkScope(val where: String, val args: Array<Any>) {
    data object Books : ChunkScope(LIBRARY_PREFIXES.joinToString(" AND ") { "c.packId NOT LIKE '$it%'" }, emptyArray())
    class Library(packId: String) : ChunkScope("c.packId = ?", arrayOf(packId))

    companion object {
        /** Library collections (searched separately from the Quran): IslamHouse per language, Ibn Baz fatwas. */
        const val LIBRARY_PREFIX = "sources.islamhouse-"
        const val BINBAZ_PACK = "sources.binbaz-ar"
        val LIBRARY_PREFIXES = listOf(LIBRARY_PREFIX, "sources.binbaz-", "sources.hadeethenc-", "sources.quranenc-")
        fun libraryPack(lang: String) = LIBRARY_PREFIX + lang
        fun isLibrary(packId: String) = LIBRARY_PREFIXES.any { packId.startsWith(it) }
        /** Language of a library pack: the suffix after the last "-" (e.g. sources.binbaz-ar → ar). */
        fun libraryLanguage(packId: String) = packId.substringAfterLast('-')

        /**
         * Library languages installed for an interface language: that language and Arabic (the Ibn Baz
         * fatwas and the Arabic library), plus English as a fallback for the other languages. The other
         * languages stay bundled and install when the interface language changes.
         */
        fun libraryLanguages(ui: String): Set<String> = setOf(ui, "ar") + if (ui != "ar") setOf("en") else emptySet()
    }
}

/** Tokens are normalized letters/digits only; quoting keeps FTS operators from being interpreted. */
fun ftsTerm(t: String) = "\"" + t.replace("\"", "") + "\""

fun countQuery(scope: ChunkScope) = SimpleSQLiteQuery("SELECT COUNT(*) FROM source_chunk c WHERE ${scope.where}", scope.args)

fun averageQuery(scope: ChunkScope) = SimpleSQLiteQuery("SELECT COALESCE(AVG(c.tokenCount), 0) FROM source_chunk c WHERE ${scope.where}", scope.args)

fun ftsCountQuery(term: String, scope: ChunkScope) = SimpleSQLiteQuery(
    "SELECT COUNT(*) FROM source_chunk c JOIN source_chunk_fts f ON f.rowid = c.rowid WHERE source_chunk_fts MATCH ? AND ${scope.where}",
    arrayOf<Any>(ftsTerm(term), *scope.args),
)

fun candidatesQuery(terms: Collection<String>, scope: ChunkScope = ChunkScope.Books): SupportSQLiteQuery = SimpleSQLiteQuery(
    "SELECT c.id AS id, c.seq AS seq, c.searchText AS searchText FROM source_chunk c " +
        "JOIN source_chunk_fts f ON f.rowid = c.rowid WHERE source_chunk_fts MATCH ? AND ${scope.where}",
    arrayOf<Any>(terms.joinToString(" OR ") { ftsTerm(it) }, *scope.args),
)

/** FTS4 match for library variants: prefix query for 3+ letters (FTS4 puts the * inside the quotes). */
fun libraryMatch(variants: Collection<String>) = variants.joinToString(" OR ") {
    if (it.length >= sa.zood.nearmosque.core.LibraryText.PREFIX_MIN) "\"" + it.replace("\"", "") + "*\"" else ftsTerm(it)
}

fun libraryCountQuery(variants: Collection<String>, scope: ChunkScope) = SimpleSQLiteQuery(
    "SELECT COUNT(*) FROM source_chunk c JOIN source_chunk_fts f ON f.rowid = c.rowid WHERE source_chunk_fts MATCH ? AND ${scope.where}",
    arrayOf<Any>(libraryMatch(variants), *scope.args),
)

fun libraryCandidatesQuery(variants: Collection<String>, scope: ChunkScope): SupportSQLiteQuery = SimpleSQLiteQuery(
    "SELECT c.id AS id, c.seq AS seq, c.searchText AS searchText FROM source_chunk c " +
        "JOIN source_chunk_fts f ON f.rowid = c.rowid WHERE source_chunk_fts MATCH ? AND ${scope.where}",
    arrayOf<Any>(libraryMatch(variants), *scope.args),
)

@Database(
    entities = [
        InstalledPackEntity::class, MosqueEntity::class, FavoriteEntity::class,
        SourceDocumentEntity::class, SourceChunkEntity::class, SourceChunkFts::class, CommonQuestionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun packs(): PackDao
    abstract fun mosques(): MosqueDao
    abstract fun sources(): SourceDao

    companion object {
        fun create(context: Context, inMemory: Boolean = false): AppDatabase {
            val builder = if (inMemory) {
                // In-memory databases are only used by tests, which query from the test thread.
                Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries()
            } else {
                Room.databaseBuilder(context, AppDatabase::class.java, "near-mosque.db")
            }
            // Schema v1. Future versions add explicit Migration objects; packs are re-installable, but
            // favorites must be migrated, so destructive fallback is deliberately NOT enabled.
            return builder.build()
        }
    }
}
