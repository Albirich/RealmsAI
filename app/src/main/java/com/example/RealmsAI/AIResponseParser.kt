package com.example.RealmsAI.ai

import android.os.Handler
import android.os.Looper
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.ChatAdapter
import com.example.RealmsAI.ChatMessage
import com.example.RealmsAI.models.ParsedMessage
import com.example.RealmsAI.models.Timing

/**
 * Takes a raw AI output string (with bracketed tags)
 * and feeds parsed messages into the given ChatAdapter,
 * scheduling each line with its proper delay & emotion update.
 */
class AIResponseParser(
    private val chatAdapter: ChatAdapter,
    private val chatRecycler: RecyclerView,
    private val updateAvatar: (speakerId: String, emotion: String) -> Unit,
    private val loadName: (speakerId: String) -> String
) {
    private val handler = Handler(Looper.getMainLooper())
    var activeTokens: List<String> = emptyList()

    /** Public API: hand it the full raw AI response text. */
    fun handle(raw: String) {
        // 1) Parse everything out
        val allParsed = parseAIOutput(raw)

        // 2) Only keep the first two (if there are more)
        val parsed = allParsed.take(2)

        // 3) Schedule them back‐to‐back
        var cumulativeDelay = 0L
        parsed.forEach { pm ->
            // compute this message’s delay
            val thisDelay = when(pm.timing) {
                Timing.INTERRUPT -> 200L
                Timing.NORMAL    -> 800L
                Timing.DELAYED   -> 2500L
            }
            cumulativeDelay += thisDelay

            handler.postDelayed({
                // show the message
                chatAdapter.addMessage(
                    ChatMessage(loadName(pm.speakerId), pm.text)
                )
                // update that avatar’s emotion
                updateAvatar(pm.speakerId, pm.emotion)
            }, cumulativeDelay)
        }

        // 4) finally, scroll into view after the last one
        handler.postDelayed({
            chatRecycler.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }, cumulativeDelay + 100)
    }


    /** Breaks the raw string into a list of ParsedMessage data objects. */
    private fun parseAIOutput(raw: String): List<ParsedMessage> {
        // Regex grabs segments like [B1,angry][normal] "Text…"
        val re = Regex("""(\[[^\]]+\]\[[^\]]+\](?:\[[^\]]+\])?)\s*"([^"]+)"""")
        return re.findAll(raw).map { match ->
            val tags = match.groupValues[1]        // "[B1,angry][normal]" or with extra "[B2,sad]"
            val text = match.groupValues[2]        // the quoted message

            val parts = tags
                .removePrefix("[")
                .removeSuffix("]")
                .split("][")

            // first bracket: speaker & emotion
            val (speaker, emotion) = parts[0]
                .split(",")
                .map(String::trim)
                .let { it[0] to it[1] }

            // second bracket: timing
            val timing = when (parts[1].lowercase()) {
                "interrupt" -> Timing.INTERRUPT
                "delayed"   -> Timing.DELAYED
                else        -> Timing.NORMAL
            }

            // optional third bracket: a target for ripple effects
            val target = if (parts.size > 2) {
                parts[2].split(",").map(String::trim).let { it[0] to it[1] }
            } else {
                null
            }

            ParsedMessage(speaker, emotion, timing, text, target)
        }.toList()
    }
}

