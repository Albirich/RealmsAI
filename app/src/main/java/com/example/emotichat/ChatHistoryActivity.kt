package com.example.emotichat


import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.emotichat.ChatPreview

class ChatListActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chat_list_activity)

        injectMockChatsIfEmpty()      // populate prefs if empty
        val previews = loadAllChatPreviews()

        val recyclerView = findViewById<RecyclerView>(R.id.chatListRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = ChatPreviewAdapter(previews) { chat ->
            startActivity(Intent(this, MainActivity::class.java)
                .putExtra("CHAT_ID", chat.id))
        }
    }

    private fun injectMockChatsIfEmpty() {
        val prefs    = getSharedPreferences("chat_sessions", MODE_PRIVATE)
        val existing = prefs.getString("all_chats", null)
        if (!existing.isNullOrEmpty()) return

        val allChats = org.json.JSONObject()
        for (i in 1..5) {
            val chatId   = "chat_00$i"
            val title    = "Chat with Test Bot $i"
            val messages = org.json.JSONArray().apply {
                put(org.json.JSONObject().apply {
                    put("sender", "User")
                    put("messageText", "Hey Bot $i")
                    put("timeStamp", System.currentTimeMillis() - (i * 60_000))
                })
                put(org.json.JSONObject().apply {
                    put("sender", "Bot $i")
                    put("messageText", "Hello, human $i")
                    put("timeStamp", System.currentTimeMillis() - (i * 59_000))
                })
            }
            val chatObj = org.json.JSONObject().apply {
                put("title", title)
                put("lastUpdated", System.currentTimeMillis() - (i * 58_000))
                put("messages", messages)
            }
            allChats.put(chatId, chatObj)
        }

        prefs.edit().putString("all_chats", allChats.toString()).apply()
    }
}
