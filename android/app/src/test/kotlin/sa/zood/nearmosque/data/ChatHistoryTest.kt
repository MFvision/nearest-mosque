package sa.zood.nearmosque.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChatHistoryTest {
    private val file = java.io.File.createTempFile("chats", ".json").also { it.delete() }

    @Test
    fun recordsQuestionsNewestChatFirstAndSurvivesReload() {
        val h = ChatHistory(file)
        h.record("What is Islam?", "a", now = 1)
        h.record("How do I pray?", "b", now = 2)
        h.record("What is wudu?", "a", now = 3)
        assertEquals(listOf("a", "b"), h.chats.value.map { it.id })
        assertEquals(listOf("What is Islam?", "What is wudu?"), h.chats.value[0].questions)
        val again = ChatHistory(file)
        assertEquals(h.chats.value, again.chats.value)
    }

    @Test
    fun deletesOneOrAll() {
        val h = ChatHistory(file)
        h.record("q1", "a"); h.record("q2", "b")
        h.delete("a")
        assertEquals(listOf("b"), h.chats.value.map { it.id })
        h.deleteAll()
        assertTrue(ChatHistory(file).chats.value.isEmpty())
    }

    @Test
    fun keepsAtMostFifty() {
        val h = ChatHistory(file)
        repeat(60) { h.record("q$it", "id$it", now = it.toLong()) }
        assertEquals(50, h.chats.value.size)
        assertEquals("id59", h.chats.value.first().id)
    }
}
