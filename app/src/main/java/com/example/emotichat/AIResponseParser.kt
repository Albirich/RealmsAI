package com.example.emotichat.ai

import android.os.Handler
import android.os.Looper
import androidx.recyclerview.widget.RecyclerView
import com.example.emotichat.ChatAdapter
import com.example.emotichat.ChatMessage
import com.example.emotichat.models.ParsedMessage
import com.example.emotichat.models.Timing
import org.json.JSONObject

/**
 * Takes a raw AI output string (with bracketed tags)
 * and feeds parsed messages into the given ChatAdapter.
 */
class AIResponseParser(
    private val chatAdapter: ChatAdapter,
    private val chatRecycler: RecyclerView,
    private val updateAvatar: (speakerId: String, emotion: String) -> Unit,
    private val loadName: (speakerId: String) -> String
) {
    private val handler = Handler(Looper.getMainLooper())

    /** Main entry: raw multi-line AI output → schedules display */
    fun handle(raw: String) {
        parseAIOutput(raw).forEach { pm ->
            schedule(pm)
        }
        // scroll once after the longest possible delay
        handler.postDelayed({
            chatRecycler.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }, 3000)
    }

    /** Parses all bracketed segments into ParsedMessage objects */
    private fun parseAIOutput(raw: String): List<ParsedMessage> {
        val re = Regex("""(\[[^\]]+\]\[[^\]]+\](?:\[[^\]]+\])?)\s*"([^"]+)"""")
        return re.findAll(raw).map { m ->
            val tags = m.groupValues[1]
            val text = m.groupValues[2]
            val parts = tags
                .removePrefix("[")
                .removeSuffix("]")
                .split("][")
            val (speaker, emotion) = parts[0]
                .split(",").map(String::trim)
                .let { it[0] to it[1] }
            val timing = when(parts[1].lowercase()) {
                "interrupt" -> Timing.INTERRUPT
                "delayed"   -> Timing.DELAYED
                else        -> Timing.NORMAL
            }
            val target = if (parts.size > 2) {
                parts[2].split(",").map(String::trim).let { it[0] to it[1] }
            } else null

            ParsedMessage(speaker, emotion, timing, text, target)
        }.toList()
    }

    /** Schedules a single ParsedMessage onto the adapter after the right delay */
    private fun schedule(pm: ParsedMessage) {
        val displayName = loadName(pm.speakerId)
        val chatMsg = ChatMessage(displayName, pm.text)

        val delayMillis = when(pm.timing) {
            Timing.INTERRUPT -> 0L
            Timing.NORMAL    -> 800L
            Timing.DELAYED   -> 2500L
        }

        handler.postDelayed({
            chatAdapter.addMessage(chatMsg)
            updateAvatar(pm.speakerId, pm.emotion)
        }, delayMillis)
    }
}
