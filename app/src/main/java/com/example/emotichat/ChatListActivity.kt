package com.example.emotichat


import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView



class ChatListActivity  :   BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chat_list_activity)

        fun loadAllChatPreviews(): List<ChatPreview> {
            val prefs = getSharedPreferences("chat_sessions", MODE_PRIVATE)
            val allChatsStr = prefs.getString("all_chats", null) ?: return emptyList()
            val allChats = org.json.JSONObject(allChatsStr)

            val previews = mutableListOf<ChatPreview>()
            allChats.keys().forEach { chatId ->
                val chatObj = allChats.getJSONObject(chatId)
                val title = chatObj.getString("title")
                val messages = chatObj.getJSONArray("messages")

                // Grab the last message text for preview
                val lastMsgObj = messages.getJSONObject(messages.length() - 1)
                val lastText = lastMsgObj.getString("messageText")

                // For now we’ll show the first two bot avatars, or fall back if missing
                val avatar1 = if (chatObj.getJSONArray("messages").length() > 1)
                    R.drawable.icon_01 else R.drawable.icon_02
                val avatar2 = if (chatObj.getJSONArray("messages").length() > 2)
                    R.drawable.icon_02 else R.drawable.icon_01

                previews.add(ChatPreview(
                    chatId    = chatId,
                    title     = title,
                    lastMessage = lastText,
                    avatar1ResId = avatar1,
                    avatar2ResId = avatar2
                ))

            }

            previews.sortByDescending { preview ->
                // pull the JSONObject for that chatId directly:
                allChats
                    .getJSONObject(preview.chatId)
                    .getLong("lastUpdated")
            }
            return previews
        }

        // after setContentView...
        injectMockChatsIfEmpty()      // if you still want mocks
        val previews = loadAllChatPreviews()

        val recyclerView = findViewById<RecyclerView>(R.id.chatListRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 2) // 2 columns
        recyclerView.adapter = ChatPreviewAdapter(previews) { chat ->
            // Start MainActivity, passing the chatId
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("CHAT_ID", chat.chatId)
            startActivity(intent)
        }
    }


    fun injectMockChatsIfEmpty() {
        val prefs = getSharedPreferences("chat_sessions", MODE_PRIVATE)
        val existing = prefs.getString("all_chats", null)
        if (!existing.isNullOrEmpty()) return  // Don't overwrite real data

        val allChats = org.json.JSONObject()

        for (i in 1..5) {
            val chatId = "chat_00$i"
            val title = "Chat with Test Bot $i"
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
    class ChatListActivity : BaseActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.chat_list_activity)
            // … your existing setup …
        }
    }

}
