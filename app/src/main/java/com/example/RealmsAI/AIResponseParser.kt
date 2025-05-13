package com.example.RealmsAI.ai

import android.os.Handler
import android.os.Looper
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.ChatAdapter
import com.example.RealmsAI.models.ChatMessage
import com.example.RealmsAI.models.ChatMode
import com.example.RealmsAI.SessionManager

class AIResponseParser(
    private val chatAdapter: ChatAdapter,
    private val chatRecycler: RecyclerView,
    private val updateAvatar: (speakerId: String, emotion: String) -> Unit,
    private val loadName:     (speakerId: String) -> String,
    private val chatId:       String,
    private val sessionId:    String,
    var chatMode: ChatMode             // ← NEW: know which mode we’re in
) {
    private val handler = Handler(Looper.getMainLooper())

    fun handle(raw: String) {
        when (chatMode) {
            ChatMode.ONE_ON_ONE ->    handleOneOnOne(raw)
            ChatMode.SANDBOX ->       handleSandbox(raw)
            ChatMode.RPG ->           handleRpg(raw)
            ChatMode.VISUAL_NOVEL  ->           handleVn(raw)
            ChatMode.GOD  ->           handleGod(raw)
        }
    }
    private val oneOnOneRe = Regex("""\[\s*(N0|B\d+)\s*,\s*(\w+)\s*\]\s*["“]?(.+?)["”]?$""")
    private val oneOnOneCharacterName: String? = null

    //────── ONE-ON-ONE ──────
    fun handleOneOnOne(raw: String) {raw
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { line ->
            oneOnOneRe.matchEntire(line)?.destructured?.let { (slot, emotion, text) ->
                // pick a sender name
                val senderName = when {
                    slot == "N0" -> "Narrator"
                    oneOnOneCharacterName != null -> oneOnOneCharacterName
                    else -> loadName(slot)  // fallback
                }

                // show the bubble + persist
                handler.post {
                    val msg = ChatMessage(sender = senderName, messageText = text.trim())
                    chatAdapter.addMessage(msg)
                    SessionManager.sendMessage(chatId, sessionId, msg)
                    chatRecycler.scrollToPosition(chatAdapter.itemCount - 1)
                }

                // update the avatar if you like:
                handler.post { updateAvatar(slot, emotion) }
            }
        }
    }



    //────── SANDBOX / GROUP ──────
    private fun handleSandbox(raw: String) {
        val re2 = Regex("""\[(N0|B\d+),(\w+),(\d+)\]\s*["“]?(.+?)["”]?$""")
        re2.findAll(raw).forEach { match ->
            val (slot, emo, spd, txt) = match.destructured
            val name = loadName(slot)
            val msg  = ChatMessage(sender = name, messageText = txt.trim())
            postMessage(msg, emo, spd.toInt())
        }
    }


    //────── RPG MODE ──────
    private fun handleRpg(raw: String) {
        val re3 = Regex("""\[(B\d+),(\w+),(\d+)\]\s*["“]?(.+?)["”]?$""")
        re3.findAll(raw).forEach { match ->
            val (slot, emo, spd, txt) = match.destructured
            val name = loadName(slot)
            val msg  = ChatMessage(sender = name, messageText = txt.trim())
            updateGameStateFromRpgText(txt)
            postMessage(msg, emo, spd.toInt())
        }
    }

    //────── VISUAL NOVEL MODE ──────
    private fun handleVn(raw: String) {
        val re3 = Regex("""\[(B\d+),(\w+),(\d+)\]\s*["“]?(.+?)["”]?$""")
        re3.findAll(raw).forEach { match ->
            val (slot, emo, spd, txt) = match.destructured
            val name = loadName(slot)
            val msg  = ChatMessage(sender = name, messageText = txt.trim())
            updateGameStateFromRpgText(txt)
            postMessage(msg, emo, spd.toInt())
        }
    }

    //────── VISUAL NOVEL MODE ──────
    private fun handleGod(raw: String) {
        val re3 = Regex("""\[(B\d+),(\w+),(\d+)\]\s*["“]?(.+?)["”]?$""")
        re3.findAll(raw).forEach { match ->
            val (slot, emo, spd, txt) = match.destructured
            val name = loadName(slot)
            val msg  = ChatMessage(sender = name, messageText = txt.trim())
            updateGameStateFromRpgText(txt)
            postMessage(msg, emo, spd.toInt())
        }
    }

    //────── COMMON POSTING ──────
    private fun postMessage(
        chatMsg:    ChatMessage,
        emotion:    String,
        speedCode:  Int = 0
    ) {
        // choose delay based on speedCode (or constant for one-on-one)
        val delay = when (chatMode) {
            ChatMode.ONE_ON_ONE -> 100L
            else                -> when(speedCode) {
                1 -> 200L
                2 -> 800L
                else -> 500L
            }
        }
        handler.postDelayed({
            updateAvatar(chatMsg.sender, emotion)
            chatAdapter.addMessage(chatMsg)
            SessionManager.sendMessage(chatId, sessionId, chatMsg)
            chatRecycler.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }, delay)
    }

    // Example stubs for RPG/VN state updates
    private fun updateGameStateFromRpgText(txt: String) {
        // parse “lost X HP” and call your UI/game‐state updater
    }
    private fun updateRelationshipsFromVnText(txt: String) {
        // parse “*affection +1*” etc.
    }
}
