package com.example.emotichat

import android.content.Context
import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

/**
 * BaseActivity centralizes shared UI (toolbar, bottom navigation)
 * and session persistence (save/load chat) for all screens.
 */
open class BaseActivity : AppCompatActivity() {

    //────────────────────────────────────────────────────────────────
    // 1) Layout Inflation Hook: install toolbar + nav after setContentView
    //────────────────────────────────────────────────────────────────
    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        setupBottomNav()
        // If you ever add a common Toolbar, call setupToolbar() here too
    }

    //────────────────────────────────────────────────────────────────
    // 2) Options Menu (CLEAR CHAT)
    //────────────────────────────────────────────────────────────────
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.clear_chat -> {
                // Default clear‐history action (overridden in some screens if desired)
                clearChatHistoryFromPrefs()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Clears the entire saved chat_sessions map.
     */
    private fun clearChatHistoryFromPrefs() {
        getSharedPreferences("chat_sessions", Context.MODE_PRIVATE)
            .edit()
            .remove("all_chats")
            .apply()
    }

    //────────────────────────────────────────────────────────────────
    // 3) Bottom Navigation Wiring
    //────────────────────────────────────────────────────────────────
    private fun setupBottomNav() {
        // Chats hub
        findViewById<ImageButton>(R.id.navChats)?.setOnClickListener {
            if (this !is ChatHubActivity) {
                startActivity(Intent(this, ChatHubActivity::class.java))
            }
        }
        // Characters hub
        findViewById<ImageButton>(R.id.navCharacters)?.setOnClickListener {
            if (this !is CharacterHubActivity) {
                startActivity(Intent(this, CharacterHubActivity::class.java))
            }
        }
        // Create hub
        findViewById<ImageButton>(R.id.navCreate)?.setOnClickListener {
            if (this !is CreationHubActivity) {
                startActivity(Intent(this, CreationHubActivity::class.java))
            }
        }
        // History
        findViewById<ImageButton>(R.id.navHistory)?.setOnClickListener {
            if (this !is HistoryActivity) {
                startActivity(Intent(this, HistoryActivity::class.java))
            }
        }
        // Profile
        findViewById<ImageButton>(R.id.navProfile)?.setOnClickListener {
            if (this !is ProfileActivity) {
                startActivity(Intent(this, ProfileActivity::class.java))
            }
        }
    }

    //────────────────────────────────────────────────────────────────
    // 4) Chat Session Persistence
    //────────────────────────────────────────────────────────────────
    /**
     * Saves (or updates) a chat session under `chatId` with given title/messages.
     */
    fun saveChatSession(
        chatId: String,
        title: String,
        messages: List<ChatMessage>
    ) {
        val prefs   = getSharedPreferences("chat_sessions", Context.MODE_PRIVATE)
        val editor  = prefs.edit()

        // Build JSON payload
        val chatObj = org.json.JSONObject().apply {
            put("title", title)
            put("lastUpdated", System.currentTimeMillis())

            val arr = org.json.JSONArray()
            messages.forEach { msg ->
                arr.put(org.json.JSONObject().apply {
                    put("sender",      msg.sender)
                    put("messageText", msg.messageText)
                    put("timeStamp",   msg.timeStamp)
                })
            }
            put("messages", arr)
        }

        // Merge into the full sessions map
        val allChatsStr = prefs.getString("all_chats", "{}")
        val allChats    = org.json.JSONObject(allChatsStr)
        allChats.put(chatId, chatObj)

        editor.putString("all_chats", allChats.toString())
            .apply()
    }

    /**
     * Loads the chat session for [chatId], or returns empty if it doesn’t exist.
     */
    fun loadChatSession(chatId: String): List<ChatMessage> {
        val prefs      = getSharedPreferences("chat_sessions", Context.MODE_PRIVATE)
        val jsonString = prefs.getString("all_chats", null) ?: return emptyList()
        val allChats   = org.json.JSONObject(jsonString)
        if (!allChats.has(chatId)) return emptyList()

        val chatObj    = allChats.getJSONObject(chatId)
        val arr        = chatObj.getJSONArray("messages")
        val messages   = mutableListOf<ChatMessage>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            messages.add(
                ChatMessage(
                    sender      = o.getString("sender"),
                    messageText = o.getString("messageText"),
                    timeStamp   = o.getLong("timeStamp")
                )
            )
        }
        return messages
    }
}
