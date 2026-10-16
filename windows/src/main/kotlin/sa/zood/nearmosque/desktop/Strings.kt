package sa.zood.nearmosque.desktop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The app's texts, read from shared/i18n/strings.json (the same file the phone apps are generated from). */
object Strings {
    data class Language(val code: String, val name: String, val rtl: Boolean)

    private val table: Map<String, Map<String, String>> by lazy {
        val root = Json.parseToJsonElement(resource("strings.json")).jsonObject["strings"]!!.jsonObject
        // Entries are {language: text}; anything else (comments, notes) is skipped.
        root.mapNotNull { (k, v) ->
            (v as? JsonObject)?.let { o -> k to o.mapNotNull { (l, x) -> (x as? kotlinx.serialization.json.JsonPrimitive)?.let { l to it.content } }.toMap() }
        }.toMap()
    }

    val languages: List<Language> by lazy {
        Json.parseToJsonElement(resource("languages.json")).jsonObject["languages"]!!.let { it as kotlinx.serialization.json.JsonArray }
            .map { it.jsonObject }
            .filter { it["enabled"]?.jsonPrimitive?.boolean != false }
            .map { Language(it["code"]!!.jsonPrimitive.content, it["name"]!!.jsonPrimitive.content, it["rtl"]?.jsonPrimitive?.boolean == true) }
    }

    /** The language in use (a code from languages.json). */
    var language: String = "en"

    val isRtl: Boolean get() = languages.firstOrNull { it.code == language }?.rtl == true

    /**
     * Desktop texts not yet in shared/i18n/strings.json, in English and Arabic (other languages read English).
     * Once the shared file has a key, its translations are used instead of these.
     */
    internal val pending: Map<String, Map<String, String>> = mapOf(
    )

    fun t(key: String, vararg args: Any): String {
        val row = table[key] ?: pending[key] ?: return key
        val s = row[language] ?: row["en"] ?: key
        return if (args.isEmpty()) s else runCatching { String.format(s, *args) }.getOrDefault(s)
    }

    /** The system's language when the app offers it, else English. */
    fun systemLanguage(): String {
        val l = java.util.Locale.getDefault()
        val tag = l.toLanguageTag()
        return languages.firstOrNull { it.code.equals(tag, true) }?.code
            ?: languages.firstOrNull { it.code.equals(l.language, true) }?.code
            ?: "en"
    }

    fun resource(name: String): String =
        Strings::class.java.classLoader.getResourceAsStream(name)?.use { it.readBytes().decodeToString() }
            ?: error("missing resource $name")
}

fun t(key: String, vararg args: Any): String = Strings.t(key, *args)
