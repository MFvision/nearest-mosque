package sa.zood.nearmosque.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

/** Semantic search matches tools/semantic.py on shared/fixtures/semantic.json. */
class SemanticSearchTest {
    private val fixture = Fixtures.read("shared/fixtures/semantic.json").jsonObject
    private val model = StaticEmbedder.load(Fixtures.file("shared/semantic/model.bin").readBytes())

    @Test
    fun tokenizesLikeReference() {
        for (c in fixture["tokens"]!!.jsonArray.map { it.jsonObject }) {
            val text = c["text"]!!.jsonPrimitive.content
            assertEquals(text, c["ids"]!!.jsonArray.map { it.jsonPrimitive.int }, model.tokenize(text))
        }
    }

    @Test
    fun embedsLikeReference() {
        for (c in fixture["embeddings"]!!.jsonArray.map { it.jsonObject }) {
            val v = model.embed(c["text"]!!.jsonPrimitive.content)
            c["first"]!!.jsonArray.forEachIndexed { d, x -> assert(abs(v[d] - x.jsonPrimitive.double) < 1e-4) { "${c["text"]} dim $d" } }
            val q = c["quantized"]!!.jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray()
            assertArrayEquals(c["text"]!!.jsonPrimitive.content, q, StaticEmbedder.quantize(v))
        }
    }

    @Test
    fun ranksCorpusLikeReference() {
        val corpus = fixture["corpus"]!!.jsonArray.map { it.jsonObject }
        val ids = corpus.map { it["id"]!!.jsonPrimitive.content }
        val vectors = corpus.flatMap { d ->
            val parts = d["parts"]
            val section = if (parts == null || parts is JsonNull) JsonObject(emptyMap()) else buildJsonObject {
                put("parts", buildJsonArray { parts.jsonArray.forEach { add(buildJsonObject { put("kind", it.jsonPrimitive.content) }) } })
            }
            val chunk = SourceChunk(id = d["id"]!!.jsonPrimitive.content, seq = 1, anchor = "", section = section,
                original = TextPart(docId = "fixture", lang = "ar", text = d["text"]!!.jsonPrimitive.content))
            StaticEmbedder.quantize(model.embed(SemanticText.of(chunk))).toList()
        }.toByteArray()
        val index = VectorIndex(ids, vectors, model.dim)
        for (c in fixture["cases"]!!.jsonArray.map { it.jsonObject }) {
            val q = c["q"]!!.jsonPrimitive.content
            val got = index.search(model.embed(q), 3, 0.0)
            val want = c["expected"]!!.jsonArray.map { it.jsonArray }
            assertEquals(q, want.map { it[1].jsonPrimitive.content }, got.map { it.second })
            want.zip(got).forEach { (w, g) -> assert(abs(w[0].jsonPrimitive.double - g.first) < 1e-4) { "$q ${g.second}" } }
        }
    }

    @Test
    fun mergesLikeReference() {
        assertEquals(SemanticMerge.ASSIST, fixture["assist"]!!.jsonPrimitive.double, 0.0)
        assertEquals(SemanticMerge.ALONE, fixture["alone"]!!.jsonPrimitive.double, 0.0)
        for (c in fixture["merge"]!!.jsonArray.map { it.jsonObject }) {
            val words = c["words"]!!.jsonArray.map { it.jsonPrimitive.content }
            val sem = c["semantic"]!!.jsonArray.map { it.jsonArray[0].jsonPrimitive.double to it.jsonArray[1].jsonPrimitive.content }
            assertEquals(c["expected"]!!.jsonArray.map { it.jsonPrimitive.content }, SemanticMerge.merge(words, sem))
        }
    }
}
