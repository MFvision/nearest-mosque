package sa.zood.nearmosque.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Test

/** Library search matches tools/library_search.py on shared/fixtures/library-retrieval.json. */
class LibrarySearchTest {
    private val fixture = Fixtures.read("shared/fixtures/library-retrieval.json").jsonObject
    private val stopwords: Map<String, List<String>> = (Fixtures.read("shared/content/stopwords.json") as JsonObject)
        .filterValues { it is JsonArray }.mapValues { (_, v) -> v.jsonArray.map { it.jsonPrimitive.content } }
    private val lexicon = Lexicon.parse(Fixtures.file("shared/content/lexicon.json").readText())

    @Test
    fun stemsAndVariantsMatchReference() {
        for (case in fixture.getValue("stem").jsonArray) {
            val c = case.jsonArray
            val tok = TextNormalizer.tokens(c[0].jsonPrimitive.content).single()
            assertEquals(c[1].jsonPrimitive.content, tok)
            assertEquals(c[0].jsonPrimitive.content, c[2].jsonPrimitive.content, LibraryText.lightStem(tok))
            assertEquals(c[0].jsonPrimitive.content, c[3].jsonArray.map { it.jsonPrimitive.content }, LibraryText.variants(tok))
        }
    }

    @Test
    fun retrievalMatchesReference() {
        val docs = fixture.getValue("corpus").jsonArray.map { d ->
            val o = d.jsonObject
            Triple(o.getValue("id").jsonPrimitive.content, o.getValue("seq").jsonPrimitive.long, o.getValue("text").jsonPrimitive.content)
        }
        val r = LibraryRetriever(InMemoryLibraryStore(docs), LibraryText.stopwords(stopwords), lexicon)
        for (case in fixture.getValue("cases").jsonArray) {
            val o = case.jsonObject
            val q = o.getValue("q").jsonPrimitive.content
            val context = (o["context"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
            val expected = o.getValue("expected").jsonArray.map { it.jsonPrimitive.content }
            assertEquals(q, expected, r.retrieve(q, context).passages.map { it.chunkId })
        }
    }
}

class LibraryPartsTest {
    @Test
    fun partsSplitStoredText() {
        val line = """{"id":"bb:6","seq":1,"anchor":"T","section":{"publisher":"binbaz","parts":[{"kind":"title","lang":"ar"},{"kind":"question","lang":"ar"},{"kind":"answer","lang":"ar"}]},"original":{"docId":"binbaz-ar","lang":"ar","text":"T⁣Q?⁣A."}}"""
        val c = PackJson.decodeFromString(SourceChunk.serializer(), line)
        assertEquals(listOf("title", "question", "answer"), LibraryParts.parts(c).map { it.kind })
        assertEquals("Q?", LibraryParts.summary(c))
        // The separator is not part of any search token.
        assertEquals(listOf("t", "q", "a"), TextNormalizer.tokens(c.original.text))
        val plain = PackJson.decodeFromString(SourceChunk.serializer(), """{"id":"ih:en:1","seq":1,"anchor":"Title","original":{"docId":"d","lang":"en","text":"Title\nBody"}}""")
        assertEquals(emptyList<LibraryParts.Part>(), LibraryParts.parts(plain))
        assertEquals("Body", LibraryParts.summary(plain))
    }
}

class OtherSourcesTest {
    @Test
    fun linksCarryTheQuestionAndLanguage() {
        val l = OtherSources.links("ما حكم تارك الصلاة؟", "ar").associate { it.id to it.url }
        assertEquals("https://islamqa.info/ar/search?q=%D9%85%D8%A7%20%D8%AD%D9%83%D9%85%20%D8%AA%D8%A7%D8%B1%D9%83%20%D8%A7%D9%84%D8%B5%D9%84%D8%A7%D8%A9%D8%9F", l["islamqa"])
        assertEquals(true, l["binothaimeen"]!!.startsWith("https://www.google.com/search?q=site%3Abinothaimeen.net%20"))
        assertEquals("https://islamqa.info/en/search?q=prayer", OtherSources.links("prayer", "xx").first().url)
    }
}
