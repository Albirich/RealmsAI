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

    private val oneOnOneRe = Regex("""\[\s*([\w\s]+)\s*,\s*(\w+)\s*\]\s*["“]?(.+?)["”]?$""")
    private val oneOnOneCharacterName: String? = null

    // ONE-ON-ONE handler
    fun handleOneOnOne(raw: String) {
        val parsedMessages = mutableListOf<ParsedMessage>()

        raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEachIndexed { index, line ->
                oneOnOneRe.matchEntire(line)?.destructured?.let { (slot, emotion, text) ->
                    val displayName = if (slot.equals("N0", ignoreCase = true)) "Narrator" else slot.capitalizeWords()
                    parsedMessages.add(
                        ParsedMessage(
                            speakerId = displayName,
                            emotion = emotion,
                            speed = 0,
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
        val headerRegex = """\[(.*?),\s*(\w+)]""".toRegex()

        raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                val headerMatch = headerRegex.matchEntire(line)
                if (headerMatch != null) {
                    val (slot, emotion) = headerMatch.destructured
                    val messageText = line.substring(headerMatch.range.last + 1).trim()
                    parsedMessages.add(
                        ParsedMessage(
                            speakerId = slot,
                            emotion = emotion,
                            speed = 0,  // or parse speed if available
                            text = messageText
                        )
                    )
                } else {
                    // fallback for lines that don't match the header format
                    parsedMessages.add(
                        ParsedMessage(
                            speakerId = "N0",
                            emotion = "neutral",
                            speed = 0,
                            text = line
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

        parsedMessages.forEachIndexed { index, parsedMessage ->
            val delayMs = when (chatMode) {
                ChatMode.ONE_ON_ONE -> 100L
                else -> (parsedMessage.speed * 400L).coerceAtLeast(200L)
            }

            val displayName = parsedMessage.speakerId // <- use the property from parsedMessage

            val chatMsg = ChatMessage(sender = displayName, messageText = parsedMessage.text)

            cumulativeDelay += delayMs
            handler.postDelayed({
                updateAvatar(displayName, parsedMessage.emotion)
                chatAdapter.addMessage(chatMsg)
                SessionManager.sendMessage(chatId, sessionId, chatMsg)
                chatRecycler.smoothScrollToPosition(chatAdapter.itemCount - 1)
            }, cumulativeDelay)

            Log.d(TAG, "Posting [${displayName}] ${parsedMessage.text} with delay $cumulativeDelay")
        }
    }




    // Placeholder: update game state (stub)
    private fun updateGameStateFromRpgText(txt: String) {
        // Implement your RPG state changes here
    }
}
