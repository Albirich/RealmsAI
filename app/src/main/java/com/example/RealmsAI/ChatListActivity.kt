package com.example.RealmsAI

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import org.json.JSONObject

class ChatListActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_hub)
        setupBottomNav()

        // 1) Load both character-only and sandbox chat previews
        val previews = loadAllPreviews()

        // 2) Wire up RecyclerView
        val recyclerView = findViewById<RecyclerView>(R.id.createdRecycler)
        recyclerView.layoutManager = GridLayoutManager(this, 2)

        // 3) Attach your adapter — note: NO comma after `previews`, so your lambda is consumed as 'onClick'
        recyclerView.adapter = ChatPreviewAdapter(
            context     = this,
            chatList    = previews,
            onClick     = { preview ->
                // normal tap
                Intent(this, MainActivity::class.java)
                    .putExtra("chatId", preview.id)
                    .putExtra("CHAT_PROFILE_JSON", Gson().toJson(preview.chatProfile))
                    .also(::startActivity)
            },
            onLongClick = { preview ->
                // long-press behavior (e.g. delete)
                AlertDialog.Builder(this)
                    .setTitle("Delete chat “${preview.title}”?")
                    .setPositiveButton("Delete") { _, _ ->
                        getSharedPreferences("chats", MODE_PRIVATE)
                            .edit()
                            .remove(preview.id)
                            .apply()
                        // refresh your list here…
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

    }


    private fun loadAllPreviews(): List<ChatPreview> {
        val list = mutableListOf<ChatPreview>()

        // --- 1) One‐on‐one character chats ---
        val charPrefs = getSharedPreferences("characters", MODE_PRIVATE)
        charPrefs.all.forEach { (charId, jsonAny) ->
            val jsonStr = (jsonAny as? String) ?: return@forEach
            val obj = JSONObject(jsonStr)
            val name       = obj.optString("name")
            val description= "Chat with $name"    // or pull last message if you stored it
            // You may want to resolve the avatarUri string into a real drawable
            list += ChatPreview(
                id = charId,
                title = name,
                description = description,
                avatar1ResId = R.drawable.placeholder_character,
                avatar2ResId = 0,
                rating = 0f,
                timestamp = System.currentTimeMillis(),
                mode = ChatMode.SANDBOX,
                author = "system",
                chatProfile = ChatProfile(
                    id = charId,
                    title = name,
                    description = description,
                    characterIds = listOf(charId),
                    backgroundUri = null,
                    backgroundResId = null,
                    mode = ChatMode.SANDBOX,
                    rating = 0f,
                    timestamp = System.currentTimeMillis(),
                    author = "system",
                    tags = listOf("one-on-one"),
                    sfwOnly = true
                )
            )

        }

        // --- 2) Saved multi‐bot “Sandbox” chats ---
        val chatPrefs = getSharedPreferences("chat_sessions", MODE_PRIVATE)
        val allChatsStr = chatPrefs.getString("all_chats", "{}") ?: "{}"
        val allChats = JSONObject(allChatsStr)
        val keys = allChats.keys()
        while (keys.hasNext()) {
            val chatId  = keys.next()
            val chatObj = allChats.getJSONObject(chatId)
            val title   = chatObj.optString("title")
            val msgs    = chatObj.optJSONArray("messages")
            val snippet = if (msgs != null && msgs.length() > 0)
                msgs.getJSONObject(0).optString("messageText")
            else ""
            val updated = chatObj.optLong("lastUpdated", System.currentTimeMillis())
            // Use your real bot/avatar slots here if you stored them
            list += ChatPreview(
                id = chatId,
                title = title,
                description = snippet,
                avatar1ResId = R.drawable.icon_01,
                avatar2ResId = R.drawable.icon_01,
                rating = 0f,
                timestamp = updated,
                mode = chatObj.optString("mode")
                    .let { ChatMode.valueOf(it) },
                author = "system",
                tags = emptyList(),
                sfwOnly = true,
                chatProfile = null
            )
        }

        // --- 3) Sort newest-first and return ---
        return list.sortedByDescending { it.timestamp }
    }
}
