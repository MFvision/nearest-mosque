package sa.zood.nearmosque

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sa.zood.nearmosque.core.AnswerKind
import sa.zood.nearmosque.core.LatLng
import sa.zood.nearmosque.core.PackError
import sa.zood.nearmosque.core.PackVerifier
import sa.zood.nearmosque.data.MosqueResult
import sa.zood.nearmosque.data.ChunkScope
import sa.zood.nearmosque.data.RoomChunkStore
import sa.zood.nearmosque.data.countQuery
import sa.zood.nearmosque.data.ftsCountQuery
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class DataLayerTest {
    private lateinit var c: AppContainer
    private val root = File(System.getProperty("nm.root")!!)

    @Before
    fun setUp() {
        c = AppContainer(ApplicationProvider.getApplicationContext(), inMemoryDb = true, settingsFile = java.io.File.createTempFile("settings", ".preferences_pb").also { it.delete() })
        // Quran, mosques and cities only: the library packs (tens of MB) are installed once, in libraryCollections.
        runBlocking { assertTrue(c.packs.ensureBuiltins(core).isEmpty()) }
    }

    private val core: (sa.zood.nearmosque.core.PackManifest) -> Boolean = { !ChunkScope.isLibrary(it.id) }

    @Test
    fun builtinPacksInstallWithManifestCounts() = runBlocking {
        val installed = c.db.packs().all().associateBy { it.id }
        assertEquals(setOf("mosques.za-cape-town", "mosques.eg-cairo", "mosques.gb-london", "sources.quran-tanzil-pickthall"), installed.keys)
        for (p in installed.values) {
            val m = PackVerifier.parseManifest(p.manifestJson)
            assertEquals(p.id, m.recordCount, p.recordCount)
        }
        assertEquals(6236, books())
        assertTrue(c.db.sources().questions().size >= 8)
        // Every bundled library pack: manifest count = records in its file (installation is checked below).
        val langs = listOf("en", "ar", "ur", "tr", "id", "fr", "es")
        val library = langs.map { "islamhouse-$it" } + "binbaz-ar" + langs.map { "hadeethenc-$it" } + listOf("ur", "tr", "id", "fr", "es").map { "quranenc-$it" }
        for (dir in library) {
            val folder = File(root, "packs/sources/$dir")
            val m = PackVerifier.parseManifest(File(folder, "manifest.json").readText())
            assertEquals(dir, m.recordCount, File(folder, "chunks.jsonl").useLines { l -> l.count { it.isNotBlank() } })
            assertTrue(dir, ChunkScope.isLibrary(m.id))
        }
    }

    private fun books() = c.db.sources().rawInt(countQuery(ChunkScope.Books))

    /**
     * The library collections, installed once (English, Arabic and Urdu): IslamHouse, the Ibn Baz fatwas,
     * HadeethEnc hadiths and QuranEnc translations, each a separate collection searched in the reader's
     * language first.
     */
    @Test
    fun libraryCollections() = runBlocking {
        val wanted = setOf("en", "ar", "ur")
        assertTrue(c.packs.ensureBuiltins { ChunkScope.isLibrary(it.id) && ChunkScope.libraryLanguage(it.id) in wanted }.isEmpty())
        for (p in c.db.packs().all().filter { ChunkScope.isLibrary(it.id) }) {
            assertEquals(p.id, PackVerifier.parseManifest(p.manifestJson).recordCount, p.recordCount)
        }
        assertEquals(6236, books()) // library packs never change the Quran collection

        // IslamHouse in the interface language, with fallback.
        val en = c.ask.ask("What is Islam?", emptyList(), "en")
        assertTrue(en.library.isNotEmpty())
        assertTrue(en.library.none { it.startsWith("bb:") }) // answered well in English: no Arabic fatwas
        val items = c.ask.resolve(en.library.filter { it.startsWith("ih:en:") })
        assertTrue(items.any { it.chunk.anchor.contains("Islam", ignoreCase = true) })
        assertTrue(items.all { it.chunk.url!!.startsWith("https://islamhouse.com/en/") })
        assertTrue(c.ask.ask("ما هو الإسلام؟", emptyList(), "ar").library.all { it.startsWith("ih:ar:") || it.startsWith("bb:") || it.startsWith("he:ar:") })
        assertTrue(c.ask.ask("What is the capital of France?", emptyList(), "en").library.isEmpty())

        // Ibn Baz fatwas for Arabic questions; stems reach other word forms.
        val fatwas = c.ask.resolve(c.ask.ask("ما حكم تارك الصلاة؟", emptyList(), "ar").library.filter { it.startsWith("bb:") })
        assertTrue(fatwas.isNotEmpty())
        val f = fatwas.first().chunk
        assertEquals("binbaz", sa.zood.nearmosque.core.LibraryParts.publisher(f))
        assertTrue(f.url!!.startsWith("https://binbaz.org.sa/fatwas/"))
        assertEquals(listOf("title", "question", "answer"), sa.zood.nearmosque.core.LibraryParts.parts(f).map { it.kind })
        assertTrue(c.ask.ask("صلاته", emptyList(), "ar").library.isNotEmpty())

        // HadeethEnc and QuranEnc: parts stored once, version kept for the attribution.
        val hadiths = c.ask.resolve(c.ask.ask("Islam is built on five", emptyList(), "en").library.filter { it.startsWith("he:en:") })
        assertTrue(hadiths.isNotEmpty())
        val h = hadiths.first().chunk
        assertEquals("hadeethenc", sa.zood.nearmosque.core.LibraryParts.publisher(h))
        assertTrue(h.sectionName("version")!!.isNotBlank())
        assertTrue(sa.zood.nearmosque.core.LibraryParts.parts(h).any { it.kind == "hadith" && it.lang == "en" })
        val ur = c.ask.ask("نماز کی پابندی", emptyList(), "ur")
        assertTrue(ur.library.isNotEmpty())
        assertTrue(ur.library.all { it.startsWith("ih:ur:") || it.startsWith("he:ur:") || it.startsWith("qe:ur:") })
        val verse = c.ask.resolve(listOf("qe:ur:2:255")).single().chunk
        assertEquals("quran:2:255", verse.sectionName("verse"))
        assertEquals("translation", sa.zood.nearmosque.core.LibraryParts.parts(verse).first().kind)

        // Cross-language: an English question with few English hits is filled from Arabic via the lexicon.
        val cross = c.ask.ask("ruling on smoking", emptyList(), "en")
        assertTrue(cross.library.isNotEmpty())
    }

    @Test
    fun libraryLanguagesForTheInterface() {
        assertEquals(setOf("ar"), ChunkScope.libraryLanguages("ar"))
        assertEquals(setOf("ur", "ar", "en"), ChunkScope.libraryLanguages("ur"))
        assertEquals(setOf("en", "ar"), ChunkScope.libraryLanguages("en"))
    }

    /** The Room FTS4 store must give exactly the reference results (shared/fixtures/retrieval.json). */
    @Test
    fun retrievalThroughRoomFtsMatchesReference() = runBlocking {
        val r = sa.zood.nearmosque.core.Retriever(
            RoomChunkStore(c.db.sources()),
            c.ask.commonQuestions(),
            sa.zood.nearmosque.data.AskRepository.parseStopwords(File(root, "shared/content/stopwords.json").readText()),
        )
        val f = kotlinx.serialization.json.Json.parseToJsonElement(File(root, "shared/fixtures/retrieval.json").readText()).jsonObject
        val failures = mutableListOf<String>()
        for (case in f["cases"]!!.jsonArray.map { it.jsonObject }) {
            val ctx = (case["context"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList()
            val res = r.retrieve(case["q"]!!.jsonPrimitive.content, ctx)
            val ref = case["referenceResult"] as JsonObject
            val faq = ref["commonQuestionId"].let { if (it == null || it is JsonNull) null else it.jsonPrimitive.content }
            val passages = ref["passages"]!!.jsonArray.map { it.jsonPrimitive.content }
            if (res.kind.name.lowercase() != ref["kind"]!!.jsonPrimitive.content) failures += "${case["id"]}: kind ${res.kind}"
            if (res.commonQuestion?.id != faq) failures += "${case["id"]}: faq ${res.commonQuestion?.id}"
            if (res.passages.map { it.chunkId } != passages) failures += "${case["id"]}: ${res.passages.map { it.chunkId }} != $passages"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun answersCiteStoredPassagesAndUnknownsAreRefused() = runBlocking {
        val wudu = c.ask.ask("How do I perform wudu?", emptyList())
        assertEquals(AnswerKind.COMMON, wudu.kind)
        assertEquals("quran:5:6", wudu.citations.first())
        val resolved = c.ask.resolve(wudu.citations)
        assertTrue(resolved.first().chunk.original.text.contains('ّ')) // verbatim Arabic
        assertNotNull(resolved.first().documents["quran-en-pickthall"])
        val ctx = c.ask.context(resolved.first())
        assertEquals(listOf("quran:5:3", "quran:5:4", "quran:5:5", "quran:5:6", "quran:5:7", "quran:5:8", "quran:5:9"), ctx.map { it.id })
        val none = c.ask.ask("What is the capital of France?", emptyList())
        assertEquals(AnswerKind.INSUFFICIENT, none.kind)
        assertTrue(none.citations.isEmpty())
    }

    @Test
    fun removingABookPackRemovesChunksIndexAndAnswers() = runBlocking {
        c.packs.remove("sources.quran-tanzil-pickthall")
        assertEquals(0, books())
        assertEquals(0, c.db.sources().rawInt(ftsCountQuery("wash", ChunkScope.Books)))
        assertEquals(AnswerKind.INSUFFICIENT, c.ask.ask("neither slumber nor sleep", emptyList()).kind)
        c.packs.ensureBuiltins(core) // removed by the user: not reinstalled automatically
        assertEquals(0, books())
        c.packs.restoreBuiltins(core)
        assertEquals(6236, books())
    }

    @Test
    fun corruptUpdateLeavesInstalledVersionIntact() = runBlocking {
        val dir = File(root, "packs/mosques/za-cape-town")
        val manifest = PackVerifier.parseManifest(File(dir, "manifest.json").readText()).copy(version = 2)
        val before = c.db.packs().get(manifest.id)!!
        val err = runCatching { c.packs.install(manifest, builtin = true) { ByteArrayInputStream("{\"broken\":true}".toByteArray()) } }.exceptionOrNull()
        assertTrue(err is PackError.Checksum)
        assertEquals(before.version, c.db.packs().get(manifest.id)!!.version)
        val r = c.mosques.nearest(LatLng(-33.9258, 18.4232), 25_000.0, "en")
        assertTrue(r is MosqueResult.Found)
    }

    @Test
    fun mosqueResultsDistinguishEmptyCases() = runBlocking {
        val found = c.mosques.nearest(LatLng(-33.9258, 18.4232), 25_000.0, "en") as MosqueResult.Found
        assertEquals(found.items.sortedBy { it.distanceMeters }, found.items)
        assertTrue(found.coverage.contains("Cape Town"))
        // Mid-Atlantic: no pack covers it.
        assertTrue(c.mosques.nearest(LatLng(0.0, 0.0), 25_000.0, "en") is MosqueResult.AreaNotDownloaded)
        // Inside London's coverage but with a 30 m radius: covered, no records.
        val near = found.items.first().mosque.location
        val london = c.mosques.nearest(LatLng(51.62, -0.47), 30.0, "en")
        assertTrue(london.toString(), london is MosqueResult.NoRecordsInCoverage)
        assertNotNull(near)
    }

    @Test
    fun favoritesSurvivePackRemoval() = runBlocking {
        val found = c.mosques.nearest(LatLng(30.0626, 31.2497), 25_000.0, "ar") as MosqueResult.Found
        val id = found.items.first().mosque.sourceId
        c.mosques.setFavorite(id, true)
        c.packs.remove("mosques.eg-cairo")
        c.packs.restoreBuiltins(core)
        assertEquals(listOf(id), c.mosques.byIds(listOf(id)).map { it.sourceId })
    }
}
