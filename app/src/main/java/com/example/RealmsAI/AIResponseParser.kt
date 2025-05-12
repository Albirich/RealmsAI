package com.example.RealmsAI.ai

import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.ChatAdapter
import com.example.RealmsAI.ChatMessage
import com.example.RealmsAI.SessionManager
import com.example.RealmsAI.models.ParsedMessage

/**
 * Parses raw AI output tagged with slot/emotion/speed tokens,
 * updates avatars, displays each parsed message in the adapter,
 * persists to Firestore, and scrolls the RecyclerView.
 */
class AIResponseParser(
    private val chatAdapter: ChatAdapter,
    private val chatRecycler: RecyclerView,
    private val updateAvatar: (speakerId: String, emotion: String) -> Unit,
    private val loadName: (speakerId: String) -> String,
    private val chatId: String,
    private val sessionId: String
) {
    private val handler = Handler(Looper.getMainLooper())

    // Regex: [N0|B# , emotion , speed] “text”
    private val re = Regex(
        """\[\s*(N0|B\d+)\s*,\s*(\w+)\s*,\s*(\d+)\s*\]\s*["“]?(.+?)["”]?$"""
    )

    /**
     * Handle the raw response string from the AI.
     */
    fun handle(raw: String) {
        val parsed = parseAIOutput(raw)
        var cumulativeDelay = 0L

        parsed.forEach { pm ->
            // Determine delay per speed code
            val delayMs = when (pm.speed) {
                1    -> 200L
                2    -> 800L
                else -> 500L
            }
            cumulativeDelay += delayMs

            handler.postDelayed({
                // 1) Update avatar image
                updateAvatar(pm.speakerId, pm.emotion)

                // 2) Build and display ChatMessage
                val name = loadName(pm.speakerId)
                val chatMsg = ChatMessage(sender = name, messageText = pm.text)
                chatAdapter.addMessage(chatMsg)

                // 3) Persist to Firestore
                SessionManager.sendMessage(chatId, sessionId, chatMsg)

                // 4) Scroll to bottom
                chatRecycler.smoothScrollToPosition(chatAdapter.itemCount - 1)
            }, cumulativeDelay)
        }
    }

    /**
     * Parses the raw string into a list of ParsedMessage objects.
     */
    private fun parseAIOutput(raw: String): List<ParsedMessage> =
        raw.lineSequence()
            .mapNotNull { line ->
                re.matchEntire(line.trim())
                    ?.destructured
                    ?.let { (slot, emotion, speedStr, text) ->
                        ParsedMessage(
                            speakerId = slot,
                            emotion   = emotion,
                            speed     = speedStr.toIntOrNull() ?: 0,
                            text      = text.trim()
                        )
                    }
            }
            .toList()
}
