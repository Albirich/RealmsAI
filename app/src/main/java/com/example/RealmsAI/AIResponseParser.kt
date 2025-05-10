package com.example.RealmsAI.ai

import android.os.Handler
import android.os.Looper
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.ChatAdapter
import com.example.RealmsAI.ChatMessage
import com.example.RealmsAI.models.ParsedMessage

class AIResponseParser(
    private val chatAdapter: ChatAdapter,
    private val chatRecycler: RecyclerView,
    private val updateAvatar: (speakerId: String, emotion: String) -> Unit,
    private val loadName: (speakerId: String) -> String
) {
    private val handler = Handler(Looper.getMainLooper())
    var activeTokens: List<String> = emptyList()

    fun handle(raw: String) {
        val parsed = parseAIOutput(raw)
        var delaySoFar = 0L

        parsed.forEach { pm ->
            // map speed code → ms
            val thisDelay = when (pm.speed) {
                1    -> 200L
                2    -> 2500L
                else -> 800L
            }
            delaySoFar += thisDelay

            handler.postDelayed({
                chatAdapter.addMessage(
                    ChatMessage(loadName(pm.speakerId), pm.text)
                )
                updateAvatar(pm.speakerId, pm.emotion)
            }, delaySoFar)
        }

        handler.postDelayed({
            chatRecycler.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }, delaySoFar + 100)
    }

    private fun parseAIOutput(raw: String): List<ParsedMessage> {
        // One bracket, three comma‐separated values: slot, pose, speed
        val re = Regex("""\[(B\d+),(\w+),(\d+)\]\s*["“]?(.+?)["”]?$""")

        return raw
            .lineSequence()
            .mapNotNull { line ->
                re.matchEntire(line.trim())?.destructured?.let { (slot, pose, speed, text) ->
                    ParsedMessage(
                        speakerId = slot,          // e.g. "B1"
                        emotion   = pose,          // e.g. "thinking"
                        speed     = speed.toInt(), // 0, 1 or 2
                        text      = text.trim()
                    )
                }
            }
            .toList()
    }

}
