package sa.zood.nearmosque.ui.ask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import sa.zood.nearmosque.AppContainer
import sa.zood.nearmosque.core.Answer
import sa.zood.nearmosque.core.CommonQuestion
import sa.zood.nearmosque.data.ResolvedCitation
import sa.zood.nearmosque.data.SavedChat

data class Turn(
    val id: Long,
    val question: String,
    val answer: Answer? = null,
    val citations: List<ResolvedCitation> = emptyList(),
    val related: List<ResolvedCitation> = emptyList(),
    val library: List<ResolvedCitation> = emptyList(),
)

/** What the Ask pipeline is doing right now (each step is real). */
enum class AskStage(val label: Int) {
    SEARCHING(sa.zood.nearmosque.R.string.ask_stage_searching),
    READING(sa.zood.nearmosque.R.string.ask_stage_reading),
}

data class AskUi(
    val ready: Boolean = false,
    val common: List<CommonQuestion> = emptyList(),
    val turns: List<Turn> = emptyList(),
    val busy: Boolean = false,
    val stage: AskStage = AskStage.SEARCHING,
    val stagesDone: List<AskStage> = emptyList(),
)

/**
 * The conversation on screen. Only the questions are saved (ChatHistory, on this phone); nothing is
 * sent anywhere. Answers come from the local retriever; there is no on-device generator on Android in
 * this build, so every answer is the cited-search form, which is the required fallback anyway.
 */
class AskViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(AskUi())
    val ui: StateFlow<AskUi> = _ui.asStateFlow()
    val chats = c.chats.chats
    private var job: Job? = null
    private var nextId = 1L
    private var chatId = java.util.UUID.randomUUID().toString()

    init {
        viewModelScope.launch {
            c.ready.first { it }
            _ui.value = _ui.value.copy(ready = true, common = c.ask.commonQuestions())
        }
    }

    fun ask(text: String, lang: String) {
        val q = text.trim()
        if (q.isEmpty() || _ui.value.busy) return
        c.chats.record(q, chatId)
        job = viewModelScope.launch { askTyped(q, lang) }
    }

    fun askCommon(q: CommonQuestion, displayed: String, lang: String) {
        if (_ui.value.busy) return
        c.chats.record(displayed, chatId)
        job = viewModelScope.launch { run(displayed) { c.ask.answerFor(q, displayed, lang) } }
    }

    /** Opens a saved chat: its questions are asked again, in order, on the device. */
    fun open(chat: SavedChat, lang: String) {
        newConversation()
        chatId = chat.id
        job = viewModelScope.launch {
            chat.questions.forEach { q ->
                val common = _ui.value.common.firstOrNull { (it.question[lang] ?: it.question["en"]) == q }
                if (common != null) run(q) { c.ask.answerFor(common, q, lang) } else askTyped(q, lang)
            }
        }
    }

    fun deleteChat(id: String) = c.chats.delete(id)
    fun deleteAllChats() = c.chats.deleteAll()

    private suspend fun askTyped(q: String, lang: String) {
        val context = _ui.value.turns.takeLast(2).map { it.question }
        run(q) { c.ask.ask(q, context, lang) }
    }

    private suspend fun run(question: String, block: suspend () -> Answer) {
        val turn = Turn(nextId++, question)
        _ui.value = _ui.value.copy(turns = _ui.value.turns + turn, busy = true, stage = AskStage.SEARCHING, stagesDone = emptyList())
        try {
            val a = block()
            _ui.value = _ui.value.copy(stage = AskStage.READING, stagesDone = listOf(AskStage.SEARCHING))
            val cites = c.ask.resolve(a.citations)
            val related = c.ask.resolve(a.related)
            val library = c.ask.resolve(a.library)
            update(turn.id) { it.copy(answer = a, citations = cites, related = related, library = library) }
        } finally {
            _ui.value = _ui.value.copy(busy = false)
        }
    }

    fun stop() {
        job?.cancel()
        _ui.value = _ui.value.copy(busy = false, turns = _ui.value.turns.filter { it.answer != null })
    }

    fun newConversation() {
        job?.cancel()
        chatId = java.util.UUID.randomUUID().toString()
        _ui.value = _ui.value.copy(turns = emptyList(), busy = false)
    }

    suspend fun context(c: ResolvedCitation) = this.c.ask.context(c)

    private fun update(id: Long, f: (Turn) -> Turn) {
        _ui.value = _ui.value.copy(turns = _ui.value.turns.map { if (it.id == id) f(it) else it })
    }
}
