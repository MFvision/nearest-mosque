package sa.zood.nearmosque.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * A saved chat: only the questions, in order. Opening it asks them again on the device, so answers
 * always come from the books installed now. Never sent anywhere.
 */
data class SavedChat(val id: String, val updated: Long, val questions: List<String>) {
    val title: String get() = questions.firstOrNull().orEmpty()
}

/** Chats in files/chats.json, newest first (at most 50); excluded from backup and device transfer. */
class ChatHistory(private val file: File) {
    private val _chats = MutableStateFlow(load())
    val chats: StateFlow<List<SavedChat>> = _chats.asStateFlow()

    /** Adds a question to chat [id] (creating it), and moves the chat to the top. */
    @Synchronized
    fun record(question: String, id: String, now: Long = System.currentTimeMillis()) {
        val old = _chats.value.firstOrNull { it.id == id }
        val chat = SavedChat(id, now, old?.questions.orEmpty() + question)
        save((listOf(chat) + _chats.value.filter { it.id != id }).take(50))
    }

    @Synchronized fun delete(id: String) = save(_chats.value.filter { it.id != id })
    @Synchronized fun deleteAll() = save(emptyList())

    private fun save(list: List<SavedChat>) {
        _chats.value = list
        val arr = JSONArray()
        list.forEach { c -> arr.put(JSONObject().put("id", c.id).put("updated", c.updated).put("questions", JSONArray(c.questions))) }
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(file)
        }
    }

    private fun load(): List<SavedChat> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val qs = o.getJSONArray("questions")
            SavedChat(o.getString("id"), o.getLong("updated"), (0 until qs.length()).map { qs.getString(it) })
        }
    }.getOrDefault(emptyList())
}
