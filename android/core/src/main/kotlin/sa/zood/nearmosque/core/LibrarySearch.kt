package sa.zood.nearmosque.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.ln

/**
 * Library search (IslamHouse, Ibn Baz fatwas): light stemming, prefix matching and the multilingual
 * lexicon. Port of tools/library_search.py; results must match shared/fixtures/library-retrieval.json.
 * Quran search ([Retriever]) is separate and unchanged.
 */
object LibraryText {
    const val PREFIX_MIN = 3
    private val arSuffixes = listOf("ها", "ان", "ات", "ون", "ين", "يه", "ه", "ي")
    private val latinSuffixes = listOf("ing", "ers", "er", "ed")

    fun lightStem(tok: String): String {
        if (!TextNormalizer.containsArabicLetter(tok)) {
            for (s in latinSuffixes) if (tok.endsWith(s) && tok.length - s.length >= 4) return tok.dropLast(s.length)
            return tok
        }
        var t = tok
        for (s in arSuffixes) if (t.endsWith(s) && t.length - s.length >= 3) t = t.dropLast(s.length)
        return t
    }

    /** Forms a word is indexed and searched under (see tools/library_search.py `variants`). */
    fun variants(tok: String): List<String> {
        val out = mutableListOf(tok)
        fun add(x: String) { if (x !in out) out += x }
        val arabic = TextNormalizer.containsArabicLetter(tok)
        val bases = listOf(tok) + if (arabic && tok.startsWith("و") && tok.length > 3) listOf(tok.substring(1)) else emptyList()
        for (base in bases) {
            val st = lightStem(base)
            add(st)
            if (arabic && st != base && st.endsWith("ت") && st.length >= 4) add(st.dropLast(1))
        }
        return out
    }

    fun indexTokens(text: String): List<String> = TextNormalizer.tokens(text).flatMap(::variants)

    fun matches(token: String, v: String) = if (v.length >= PREFIX_MIN) token.startsWith(v) else token == v

    /** Query stopwords for the library: the shared list without the `_domain` words ("Islam", "Quran"...). */
    fun stopwords(stopwords: Map<String, List<String>>): Set<String> {
        val domain = stopwords["_domain"].orEmpty().flatMap { TextNormalizer.tokens(it) }.toSet()
        return stopwords.filterKeys { !it.startsWith("_") }.values.flatten()
            .map { TextNormalizer.tokens(it) }.filter { ts -> ts.none { it in domain } }.flatten().toSet()
    }
}

/** Equivalent words across languages (shared/content/lexicon.json). */
class Lexicon(groups: List<List<String>>) {
    private val groups: List<List<String>> = groups
    private val byStem = HashMap<String, MutableList<Int>>().apply {
        groups.forEachIndexed { i, g -> g.forEach { m -> getOrPut(LibraryText.lightStem(m)) { mutableListOf() }.add(i) } }
    }

    fun expansions(word: String): List<String> {
        val stem = LibraryText.lightStem(word)
        val out = ArrayList<String>()
        for (gi in byStem[stem].orEmpty()) for (m in groups[gi]) if (LibraryText.lightStem(m) != stem && m !in out) out += m
        return out
    }

    companion object {
        val EMPTY = Lexicon(emptyList())

        fun parse(json: String): Lexicon {
            val root = PackJson.parseToJsonElement(json) as JsonObject
            val groups = (root["groups"] as? JsonArray).orEmpty().mapNotNull { g ->
                val obj = g as JsonObject
                val members = sortedSetOf<String>()
                for (lang in obj.keys.sorted()) for (w in (obj[lang] as JsonArray)) {
                    val t = TextNormalizer.tokens(w.jsonPrimitive.content)
                    if (t.size == 1 && t[0].length >= 3) members += t[0]
                }
                members.toList().takeIf { it.size > 1 }
            }
            return Lexicon(groups)
        }
    }
}

/**
 * Library search storage: a word matches a token by prefix (3+ letters) or exactly. The retriever first
 * asks for ids per word (cheap), keeps the chunks that can pass the coverage gate, and only loads those.
 */
interface LibraryStore {
    val totalChunks: Int
    val averageLength: Double
    /** Ids of the chunks with a token matching any of [variants]. */
    fun ids(variants: List<String>): Set<String>
    /** Id, order and tokens of the given chunks. */
    fun rows(ids: Collection<String>): List<CandidateChunk>
    /** Token count (document length) of the given chunks, without loading their text. */
    fun lengths(ids: Collection<String>): Map<String, Int>
}

class InMemoryLibraryStore(docs: List<Triple<String, Long, String>>) : LibraryStore {
    private val items = docs.map { (id, seq, text) -> CandidateChunk(id, seq, LibraryText.indexTokens(text)) }
    private val byId = items.associateBy { it.id }
    override val totalChunks = items.size
    override val averageLength = if (items.isEmpty()) 0.0 else items.sumOf { it.tokens.size }.toDouble() / items.size
    override fun ids(variants: List<String>) = items.filter { c -> c.tokens.any { t -> variants.any { LibraryText.matches(t, it) } } }.map { it.id }.toSet()
    override fun rows(ids: Collection<String>) = ids.mapNotNull { byId[it] }
    override fun lengths(ids: Collection<String>) = ids.mapNotNull { id -> byId[id]?.let { id to it.tokens.size } }.toMap()
}

class LibraryRetriever(private val store: LibraryStore, private val stop: Set<String>, private val lexicon: Lexicon) {
    private class Term(val word: String, val variants: List<String>, val weight: Double, val owner: Int?) { var idf = 0.0; var ids: Set<String> = emptySet() }

    /** [limit]: results returned (Ask uses [MAX_PASSAGES]; search inside a collection asks for more). */
    fun retrieve(question: String, context: List<String> = emptyList(), limit: Int = MAX_PASSAGES): RetrievalResult {
        val content = ArrayList<String>()
        for (t in TextNormalizer.tokens(question)) if (t !in stop && t !in content) content += t
        val terms = ArrayList<Term>()
        val seen = HashSet<String>()
        fun add(word: String, weight: Double, owner: Int?) { if (seen.add(word)) terms += Term(word, LibraryText.variants(word), weight, owner) }
        content.forEachIndexed { i, w -> add(w, 1.0, i) }
        content.forEachIndexed { i, w -> lexicon.expansions(w).forEach { add(it, EXPANSION_WEIGHT, i) } }
        if (content.size < 4) context.forEach { prev -> TextNormalizer.tokens(prev).filter { it !in stop }.forEach { add(it, CONTEXT_WEIGHT, null) } }
        if (content.isEmpty() || store.totalChunks == 0) return RetrievalResult(AnswerKind.INSUFFICIENT, null, emptyList(), content)
        val n = store.totalChunks
        val avgdl = store.averageLength
        for (t in terms) {
            t.ids = store.ids(t.variants)
            val df = t.ids.size
            t.idf = ln(1 + (n - df + 0.5) / (df + 0.5))
        }
        // Only chunks covering enough of the question's words can pass the coverage gate: load just those.
        val owners = HashMap<String, MutableSet<Int>>()
        for (t in terms) { val o = t.owner ?: continue; for (id in t.ids) owners.getOrPut(id) { HashSet() }.add(o) }
        val need = kotlin.math.ceil(MIN_COVERAGE * content.size - 1e-9).toInt().coerceAtLeast(1)
        var candidateIds: Collection<String> = owners.filterValues { it.size >= need }.keys
        // Very common words (e.g. «صلاة» in 24,000 fatwas) can leave thousands of candidates: score them first
        // with an estimate from what the index already knows (which words they contain, their length,
        // term frequency taken as 1), then load and score exactly only the best [MAX_ROWS].
        if (candidateIds.size > MAX_ROWS) {
            val lengths = store.lengths(candidateIds)
            val weight = HashMap<String, Double>()
            for (t in terms) for (id in t.ids) if (id in lengths) weight.merge(id, t.weight * t.idf, Double::plus)
            candidateIds = lengths.entries.map { (id, dl) ->
                val cover = owners.getValue(id).size.toDouble() / content.size
                id to weight.getOrDefault(id, 0.0) * (K1 + 1) / (1 + K1 * (1 - B + B * dl / avgdl)) * cover
            }.sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first }).take(MAX_ROWS).map { it.first }
        }
        data class S(val id: String, val seq: Long, val score: Double, val coverage: Double)
        val scored = store.rows(candidateIds).mapNotNull { c ->
            val dl = c.tokens.size
            var s = 0.0
            val covered = HashSet<Int>()
            for (t in terms) {
                val tf = c.tokens.count { tok -> t.variants.any { LibraryText.matches(tok, it) } }
                if (tf == 0) continue
                s += t.weight * t.idf * tf * (K1 + 1) / (tf + K1 * (1 - B + B * dl / avgdl))
                t.owner?.let { covered += it }
            }
            val coverage = covered.size.toDouble() / content.size
            s *= coverage
            if (s >= MIN_SCORE && coverage >= MIN_COVERAGE) S(c.id, c.seq, s, coverage) else null
        }.sortedWith(compareByDescending<S> { it.score }.thenBy { it.seq })
        val passages = scored.take(limit).map { ScoredPassage(it.id, it.score, it.coverage) }
        return RetrievalResult(if (passages.isEmpty()) AnswerKind.INSUFFICIENT else AnswerKind.PASSAGES, null, passages, content)
    }

    companion object {
        const val K1 = 1.2
        const val B = 0.75
        const val EXPANSION_WEIGHT = 0.5
        const val CONTEXT_WEIGHT = 0.3
        const val MAX_PASSAGES = 5
        const val MIN_SCORE = 0.5
        const val MIN_COVERAGE = 0.6
        /** Candidates scored exactly per library and question (see retrieve). */
        const val MAX_ROWS = 400
    }
}

/**
 * Text parts of a library record (Ibn Baz fatwas, HadeethEnc hadiths, QuranEnc translations): stored
 * once in original.text, separated by U+2063, with kinds and languages in section.parts (see
 * tools/build_enc_packs.py). IslamHouse records have no parts.
 */
object LibraryParts {
    const val SEP = '⁣'

    data class Part(val kind: String, val lang: String, val text: String)

    fun parts(c: SourceChunk): List<Part> {
        val kinds = (c.section["parts"] as? JsonArray) ?: return emptyList()
        val texts = c.original.text.split(SEP)
        return kinds.mapIndexedNotNull { i, k ->
            val o = k as? JsonObject ?: return@mapIndexedNotNull null
            val text = texts.getOrNull(i)?.trim().orEmpty()
            if (text.isEmpty()) null else Part(o["kind"]?.jsonPrimitive?.content.orEmpty(), o["lang"]?.jsonPrimitive?.content ?: c.original.lang, text)
        }
    }

    /** One-paragraph preview: the question, hadith or translation; otherwise the text after the title. */
    fun summary(c: SourceChunk): String {
        val p = parts(c)
        if (p.isNotEmpty()) return (p.firstOrNull { it.kind != "title" } ?: p.first()).text
        return c.original.text.removePrefix(c.anchor).trim()
    }

    fun publisher(c: SourceChunk): String? = c.sectionName("publisher")
}

/**
 * Sites the app links to but does not copy (their terms reserve republishing): the question opens in the
 * site's own search, or a web search limited to the site, only when the user taps it.
 */
object OtherSources {
    data class Link(val id: String, val url: String)

    private val islamQaLanguages = setOf("ar", "en", "ur", "tr", "id", "fr", "es")

    fun links(question: String, lang: String): List<Link> {
        val q = java.net.URLEncoder.encode(question.trim(), "UTF-8").replace("+", "%20")
        val qa = if (lang in islamQaLanguages) lang else "en"
        fun site(host: String) = "https://www.google.com/search?q=" + java.net.URLEncoder.encode("site:$host ", "UTF-8").replace("+", "%20") + q
        return listOf(
            Link("islamqa", "https://islamqa.info/$qa/search?q=$q"),
            Link("dorar", "https://dorar.net/hadith/search?q=$q"),
            Link("binothaimeen", site("binothaimeen.net")),
            Link("alifta", site("alifta.gov.sa")),
        )
    }
}
