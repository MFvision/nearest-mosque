package sa.zood.nearmosque.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.Normalizer
import kotlin.math.sqrt

/**
 * Meaning-based (semantic) library search: port of tools/semantic.py; results must match
 * shared/fixtures/semantic.json. A text's vector is the mean of its WordPiece token vectors from a
 * static embedding model (shared/semantic/model.bin); nothing neural runs on the phone.
 */
class StaticEmbedder private constructor(
    private val vocab: HashMap<String, Int>,
    val dim: Int,
    private val scales: FloatArray,
    private val weights: ByteArray,
) {
    private val longest = vocab.keys.maxOf { it.codePointCount(0, it.length) }

    /** WordPiece ids (greedy longest match, "##" continuation); words with an unknown piece are dropped. */
    fun tokenize(text: String): List<Int> {
        val ids = ArrayList<Int>()
        for (word in SemanticText.preTokenize(SemanticText.normalize(text))) {
            val cps = word.codePoints().toArray()
            if (cps.size > MAX_WORD_CHARS) continue
            val pieces = ArrayList<Int>()
            var start = 0
            var ok = true
            while (start < cps.size) {
                var end = minOf(cps.size, start + longest)
                var found = -1
                while (end > start) {
                    val sub = (if (start == 0) "" else "##") + String(cps, start, end - start)
                    val id = vocab[sub]
                    if (id != null) { found = id; break }
                    end--
                }
                if (found < 0) { ok = false; break }
                pieces += found
                start = end
            }
            if (ok) ids += pieces
        }
        return ids
    }

    /** Unit vector of the mean token vector (first [MAX_TOKENS] tokens); all zeros without tokens. */
    fun embed(text: String): DoubleArray {
        val v = DoubleArray(dim)
        for (i in tokenize(text).take(MAX_TOKENS)) {
            val s = scales[i].toDouble()
            val row = i * dim
            for (d in 0 until dim) v[d] += s * weights[row + d]
        }
        val n = sqrt(v.sumOf { it * it })
        if (n > 0) for (d in 0 until dim) v[d] /= n
        return v
    }

    companion object {
        const val MAX_TOKENS = 128
        const val MAX_WORD_CHARS = 100

        /** Reads model.bin (layout in tools/semantic.py). */
        fun load(bytes: ByteArray): StaticEmbedder {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(4).also { buf.get(it) }
            require(String(magic, Charsets.US_ASCII) == "NMSE" && buf.int == 1) { "not a semantic model" }
            val n = buf.int
            val dim = buf.int
            val vocab = HashMap<String, Int>(n * 2)
            var p = buf.position()
            for (i in 0 until n) {
                var e = p
                while (bytes[e] != '\n'.code.toByte()) e++
                vocab[String(bytes, p, e - p, Charsets.UTF_8)] = i
                p = e + 1
            }
            buf.position(p)
            val scales = FloatArray(n) { buf.float }
            val weights = bytes.copyOfRange(buf.position(), buf.position() + n * dim)
            return StaticEmbedder(vocab, dim, scales, weights)
        }

        /** Item vectors are stored as int8: round(x * 127), ties to even as in the reference. */
        fun quantize(v: DoubleArray): ByteArray = ByteArray(v.size) { Math.rint(v[it] * 127).coerceIn(-127.0, 127.0).toInt().toByte() }
    }
}

/** Text rules shared with tools/semantic.py: BERT normalizer and pre-tokenizer, and what part of a record is embedded. */
object SemanticText {
    private val semanticKinds = setOf("title", "question", "hadith", "translation")
    const val ITEM_CHARS = 600

    private fun isControl(cp: Int): Boolean {
        if (cp == '\t'.code || cp == '\n'.code || cp == '\r'.code) return false
        return when (Character.getType(cp).toByte()) {
            Character.CONTROL, Character.FORMAT, Character.SURROGATE, Character.PRIVATE_USE, Character.UNASSIGNED -> true
            else -> false
        }
    }

    private fun isWhitespace(cp: Int) = cp == ' '.code || cp == '\t'.code || cp == '\n'.code || cp == '\r'.code ||
        Character.getType(cp).toByte() == Character.SPACE_SEPARATOR

    private fun isPunctuation(cp: Int): Boolean {
        if (cp in 33..47 || cp in 58..64 || cp in 91..96 || cp in 123..126) return true
        return when (Character.getType(cp).toByte()) {
            Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION, Character.END_PUNCTUATION,
            Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION, Character.OTHER_PUNCTUATION -> true
            else -> false
        }
    }

    private fun isCjk(o: Int) = o in 0x4E00..0x9FFF || o in 0x3400..0x4DBF || o in 0x20000..0x2A6DF || o in 0x2A700..0x2B73F ||
        o in 0x2B740..0x2B81F || o in 0x2B820..0x2CEAF || o in 0xF900..0xFAFF || o in 0x2F800..0x2FA1F

    /** Clean text, space out CJK, strip accents (NFD without nonspacing marks), lowercase. */
    fun normalize(text: String): String {
        val sb = StringBuilder()
        text.codePoints().forEach { cp ->
            when {
                cp == 0 || cp == 0xFFFD || isControl(cp) -> Unit
                isWhitespace(cp) -> sb.append(' ')
                isCjk(cp) -> sb.append(' ').appendCodePoint(cp).append(' ')
                else -> sb.appendCodePoint(cp)
            }
        }
        val nfd = Normalizer.normalize(sb, Normalizer.Form.NFD)
        val out = StringBuilder()
        nfd.codePoints().forEach { if (Character.getType(it).toByte() != Character.NON_SPACING_MARK) out.appendCodePoint(it) }
        return out.toString().lowercase(java.util.Locale.ROOT)
    }

    /** Split on whitespace; every punctuation character is its own word. */
    fun preTokenize(text: String): List<String> {
        val words = ArrayList<String>()
        val cur = StringBuilder()
        fun flush() { if (cur.isNotEmpty()) { words += cur.toString(); cur.setLength(0) } }
        text.codePoints().forEach { cp ->
            when {
                isWhitespace(cp) -> flush()
                isPunctuation(cp) -> { flush(); words += String(Character.toChars(cp)) }
                else -> cur.appendCodePoint(cp)
            }
        }
        flush()
        return words
    }

    /** The part of a record that says what it is about: title plus question / hadith / translation. */
    fun of(chunk: SourceChunk): String {
        val kinds = chunk.section["parts"] as? JsonArray
        val text = chunk.original.text
        if (kinds != null && kinds.isNotEmpty()) {
            val parts = text.split(LibraryParts.SEP)
            return kinds.mapIndexedNotNull { i, k ->
                val kind = (k as? JsonObject)?.get("kind")?.jsonPrimitive?.content
                parts.getOrNull(i)?.trim()?.takeIf { kind in semanticKinds && it.isNotEmpty() }
            }.take(2).joinToString(" ")
        }
        val end = if (text.codePointCount(0, text.length) > ITEM_CHARS) text.offsetByCodePoints(0, ITEM_CHARS) else text.length
        return text.substring(0, end)
    }
}

/** Quantized item vectors of one collection, searched by cosine. */
class VectorIndex(val ids: List<String>, private val vectors: ByteArray, val dim: Int) {
    init { require(vectors.size == ids.size * dim) }

    /** The stored vectors, row by row (for caching). */
    fun vectorBytes(): ByteArray = vectors

    /** (score, id) best first, score >= [floor]; ties by id. */
    fun search(q: DoubleArray, k: Int, floor: Double): List<Pair<Double, String>> {
        if (q.all { it == 0.0 }) return emptyList()
        val hits = ArrayList<Pair<Double, String>>()
        for (n in ids.indices) {
            var s = 0.0
            val row = n * dim
            for (d in 0 until dim) s += q[d] * vectors[row + d]
            s /= 127.0
            if (s >= floor) hits += s to ids[n]
        }
        return hits.sortedWith(compareByDescending<Pair<Double, String>> { it.first }.thenBy { it.second }).take(k)
    }
}

/** How semantic hits join word search (gates measured in tools/semantic.py). */
object SemanticMerge {
    const val ASSIST = 0.45
    const val ALONE = 0.55
    const val K = 3

    /** New semantic hits that pass the gate go after the first three word-search results. */
    fun merge(words: List<String>, semantic: List<Pair<Double, String>>, limit: Int = 6): List<String> {
        val floor = if (words.isEmpty()) ALONE else ASSIST
        val extra = semantic.filter { it.first >= floor && it.second !in words }.map { it.second }.distinct().take(K)
        return (words.take(3) + extra + words.drop(3)).take(limit)
    }
}
