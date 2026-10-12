package sa.zood.nearmosque.desktop

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import sa.zood.nearmosque.core.Answer
import sa.zood.nearmosque.core.AnswerComposer
import sa.zood.nearmosque.core.CommonHadith
import sa.zood.nearmosque.core.CommonQuestion
import sa.zood.nearmosque.core.CommonQuestionsFile
import sa.zood.nearmosque.core.InMemoryChunkStore
import sa.zood.nearmosque.core.PackJson
import sa.zood.nearmosque.core.Retriever
import sa.zood.nearmosque.core.SourceChunk
import sa.zood.nearmosque.core.TextNormalizer

/**
 * Ask on the PC: the same cited retrieval as the phone apps (android/core), over the books that come with the
 * app: the Qur'an (Arabic with Pickthall's English), the HadeethEnc hadith in Arabic and English, and the common
 * questions. Everything is searched on the PC; nothing is sent anywhere.
 */
object AskEngine {
    private fun chunks(name: String): List<SourceChunk> =
        Strings.resource(name).lineSequence().filter { it.isNotBlank() }
            .mapNotNull { runCatching { PackJson.decodeFromString(SourceChunk.serializer(), it) }.getOrNull() }.toList()

    private val quran: List<SourceChunk> by lazy { chunks("books/quran.jsonl") }
    private val hadith: List<SourceChunk> by lazy { chunks("books/hadith-en.jsonl") + chunks("books/hadith-ar.jsonl") }
    private val byId: Map<String, SourceChunk> by lazy { (quran + hadith).associateBy { it.id } }

    val questions: List<CommonQuestion> by lazy {
        PackJson.decodeFromString(CommonQuestionsFile.serializer(), Strings.resource("books/common-questions.json")).questions
    }

    private val retriever: Retriever by lazy {
        val stop = PackJson.decodeFromString<Map<String, JsonElement>>(Strings.resource("books/stopwords.json"))
            .filterKeys { !it.startsWith("_") }
            .mapValues { (_, v) -> (v as JsonArray).map { (it as JsonPrimitive).content } }
        val store = InMemoryChunkStore(quran.map { it to TextNormalizer.tokens(it.original.text + " " + it.translations.joinToString(" ") { t -> t.text }) })
        Retriever(store, questions, stop)
    }

    /** Loads the books (a few seconds), so the first question is answered at once. */
    fun warmUp() { retriever }

    fun ask(question: String): Answer = AnswerComposer.compose(question, retriever.retrieve(question))

    fun chunk(id: String): SourceChunk? = byId[id]

    /** The hadith a common answer quotes, in the reader's language when there is one, else English, else Arabic. */
    fun hadithFor(q: CommonQuestion, lang: String): List<SourceChunk> =
        CommonHadith.resolve(q, lang, byId.keys).mapNotNull { byId[it] }

    fun question(q: CommonQuestion, lang: String) = q.question[lang] ?: q.question["en"] ?: q.id
    fun summary(q: CommonQuestion, lang: String) = q.summary[lang] ?: q.summary["en"].orEmpty()

    /** "Al-Baqarah 2:23" for a verse, the hadith's source for a hadith. */
    fun reference(c: SourceChunk): String = when {
        c.surah != null -> "${c.sectionName("nameTranslit") ?: ""} ${c.surah}:${c.ayah}".trim()
        else -> listOfNotNull(c.sectionName("source"), c.sectionName("grade")).joinToString(" · ")
    }
}
