package com.example.emotichat

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ChatHubActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_hub)


        // 1) Wire up Spinner (stub—no action for MVP)
        val sortSpinner: Spinner = findViewById(R.id.sortSpinner)
        ArrayAdapter.createFromResource(
            this,
            R.array.chat_hub_sort_options,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            sortSpinner.adapter = adapter
        }

        // 2) Search bar (stub—no filter for MVP)
        val searchEditText: EditText = findViewById(R.id.searchEditText)

        // 3) RecyclerView as a 2‑column grid
        val recyclerView: RecyclerView = findViewById(R.id.chatHubRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 2)

        // 4) Sample data (replace with real public-chats later)
        val sampleChats = listOf(
            ChatPreview("pub1", "Adventure Bot", "Let's explore!", R.drawable.icon_01, R.drawable.icon_02),
            ChatPreview("pub2", "Mystery Bot", "Can you solve it?", R.drawable.icon_02, R.drawable.icon_01),
            ChatPreview("pub3", "Comedy Bot", "Knock knock...", R.drawable.icon_01, R.drawable.icon_02),
            ChatPreview("pub4", "News Bot", "Here's today's headlines", R.drawable.icon_02, R.drawable.icon_01)
        )

        // …
        recyclerView.adapter = ChatPreviewAdapter(sampleChats) { preview ->
            val newChatId = System.currentTimeMillis().toString()
            // Save it using inherited method:
            saveChatSession(
                chatId   = newChatId,
                title    = preview.title,
                messages = listOf(ChatMessage("System", preview.lastMessage))
            )
            startActivity(Intent(this, MainActivity::class.java).apply {
                putExtra("CHAT_ID", newChatId)
                putExtra("CHAT_TITLE", preview.title)
                putExtra("AVATAR1_RES", preview.avatar1ResId)
                putExtra("AVATAR2_RES", preview.avatar2ResId)
            })
        }


    }
}
