package com.example.RealmsAI.ai

import android.content.ContentValues.TAG
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.ChatAdapter
import com.example.RealmsAI.models.ChatMessage
import com.example.RealmsAI.models.ChatMode
import com.example.RealmsAI.SessionManager
import com.example.RealmsAI.models.ParsedMessage
import com.google.firebase.Timestamp
import java.util.UUID

class AIResponseParser(
    private val chatAdapter: ChatAdapter,
    private val chatRecycler: RecyclerView,
    private val updateAvatar: (speakerId: String, emotion: String) -> Unit,
    private val onNewMessage: (speakerId: String, emotions: Map<String, String>) -> Unit,
    private val loadName: (speakerId: String) -> String,
    private val chatId: String,
    private val sessionId: String,

    var chatMode: ChatMode
) {
    private val handler = Handler(Looper.getMainLooper())
    fun handle(raw: String) {
        Log.d("AIResponseParser", "handle() called with chatMode: $chatMode")
        when (chatMode) {
            ChatMode.ONE_ON_ONE -> handleOneOnOne(raw)
            ChatMode.SANDBOX -> handleSandbox(raw)
            ChatMode.RPG -> handleRpg(raw)
            ChatMode.VISUAL_NOVEL -> handleVn(raw)
            ChatMode.GOD -> handleGod(raw)
        }
    }
    fun String.capitalizeWords(): String = split(" ").joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
    // ONE-ON-ONE handler
    private val oneOnOneRe = Regex("""\[\s*([\w\s]+)\s*,\s*(\w+)\s*,\s*(\d+)\s*]\s*["“]?(.+?)["”]?$""")

    fun handleOneOnOne(raw: String) {
        val parsedMessages = mutableListOf<ParsedMessage>()

        raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEachIndexed { index, line ->
                oneOnOneRe.matchEntire(line)?.destructured?.let { (slot, pose, timing, text) ->
                    val displayName = if (slot.equals("N0", ignoreCase = true)) "Narrator" else slot.capitalizeWords()
                    parsedMessages.add(
                        ParsedMessage(
                            speakerId = displayName,
                            emotion = pose,
                            speed = timing.toIntOrNull() ?: 0,
                            text = text.trim()
                        )
                    )
                }
            }

        handler.post {
            renderParsedWithSpeed(parsedMessages)
        }
    }


    // SANDBOX / GROUP handler
    private fun handleSandbox(raw: String) {
        val parsedMessages = mutableListOf<ParsedMessage>()
        val headerRegex = Regex("""\[\s*(\w+)\s*,\s*(\w+)(?:;\s*([^\]]+))?\]\s*(.*)""")
        // [B2,0; B1=happy, B3=surprised] Dialogue

        raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                val match = headerRegex.matchEntire(line)
                if (match != null) {
                    val slot = match.groupValues[1]
                    val emotion = match.groupValues[2]
                    val othersStr = match.groupValues[3]
                    val messageText = match.groupValues[4]

                    // Parse pose/emotion updates for others
                    val others = mutableMapOf<String, String>()
                    if (othersStr.isNotBlank()) {
                        othersStr.split(",").forEach { part ->
                            val (char, emo) = part.trim().split("=").map { it.trim() }
                            others[char] = emo
                        }
                    }

                    parsedMessages.add(
                        ParsedMessage(
                            speakerId = slot,
                            emotion = emotion,
                            speed = 0,
                            text = messageText,
                            others = others // <-- you will need to add this field to ParsedMessage
                        )
                    )
                } else {
                    // fallback for lines that don't match the header format
                    parsedMessages.add(
                        ParsedMessage(
                            speakerId = "N0",
                            emotion = "neutral",
                            speed = 0,
                            text = line,
                            others = emptyMap()
                        )
                    )
                }
            }

        handler.post {
            renderParsedWithSpeed(parsedMessages)
        }
    }


    private fun handleRpg(raw: String) {
        val re3 = Regex("""\[(B\d+),(\w+),(\d+)\]\s*["“]?(.+?)["”]?$""")
        val parsedMessages = mutableListOf<ParsedMessage>()

        re3.findAll(raw).forEach { match ->
            val (slot, emo, spd, txt) = match.destructured
            parsedMessages.add(
                ParsedMessage(
                    speakerId = slot,
                    emotion = emo,
                    speed = spd.toIntOrNull() ?: 0,
                    text = txt.trim()
                )
            )
            updateGameStateFromRpgText(txt)
        }

        handler.post {
            renderParsedWithSpeed(parsedMessages)
        }
    }


    // VN mode handler (same as RPG)
    private fun handleVn(raw: String) = handleRpg(raw)

    // GOD mode handler (same as RPG)
    private fun handleGod(raw: String) = handleRpg(raw)

    // Common postMessage with default speedCode = 0 and delay handling
    fun renderParsedWithSpeed(parsedMessages: List<ParsedMessage>) {
        var cumulativeDelay = 0L
        parsedMessages.forEach { parsedMessage ->
            val delayMs = when (chatMode) {
                ChatMode.ONE_ON_ONE -> 500L
                else -> (parsedMessage.speed * 400L).coerceAtLeast(400L)
            }
            cumulativeDelay += delayMs
            Log.d("DELAY", "Cumulative delay: $cumulativeDelay for ${parsedMessage.speakerId}")
            handler.postDelayed({

            val displayName = parsedMessage.speakerId // <- use the property from parsedMessage

            val chatMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                sender = displayName,
                messageText = parsedMessage.text
            )

            parsedMessage.others.forEach { (otherId, emo) ->
                updateAvatar(otherId, emo)
            }
                SessionManager.sendMessage(chatId, sessionId, chatMsg)
                chatRecycler.smoothScrollToPosition(chatAdapter.itemCount - 1)

                Log.d(TAG, "Posting [${displayName}] ${parsedMessage.text} with delay $cumulativeDelay")
        },cumulativeDelay)
    }
}





    // Placeholder: update game state (stub)
    private fun updateGameStateFromRpgText(txt: String) {
        // Implement your RPG state changes here
    }
}
