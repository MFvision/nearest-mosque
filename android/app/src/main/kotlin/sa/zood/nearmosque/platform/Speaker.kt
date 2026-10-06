package sa.zood.nearmosque.platform

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Reads text aloud with the phone's text-to-speech voices (on the device). One thing at a time:
 * starting another, or tapping again, stops the current one.
 */
object Speaker {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: (() -> Unit)? = null
    private val _speaking = MutableStateFlow<String?>(null)
    /** What is being read now (an answer's id). */
    val speaking: StateFlow<String?> = _speaking.asStateFlow()

    /** [parts] are (text, language tag) read in order. */
    fun toggle(context: Context, id: String, parts: List<Pair<String, String>>) {
        if (_speaking.value == id) { stop(); return }
        stop()
        _speaking.value = id
        val speak = {
            val t = tts
            if (t != null) {
                parts.filter { it.first.isNotBlank() }.forEachIndexed { i, (text, lang) ->
                    t.language = Locale.forLanguageTag(lang)
                    t.speak(text, TextToSpeech.QUEUE_ADD, null, "$id#$i#${parts.size}")
                }
            }
        }
        if (ready) speak() else {
            pending = speak
            if (tts == null) {
                tts = TextToSpeech(context.applicationContext) { status ->
                    ready = status == TextToSpeech.SUCCESS
                    if (ready) pending?.invoke() else _speaking.value = null
                    pending = null
                }.also { t ->
                    t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            val p = utteranceId?.split('#') ?: return
                            if (p.size == 3 && p[1].toIntOrNull() == (p[2].toIntOrNull() ?: 0) - 1) _speaking.value = null
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) { _speaking.value = null }
                    })
                }
            }
        }
    }

    fun stop() {
        tts?.stop()
        _speaking.value = null
    }
}
