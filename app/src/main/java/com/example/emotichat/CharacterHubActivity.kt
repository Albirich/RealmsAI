package com.example.emotichat

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson

class CharacterHubActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_hub)

        // 1) Pull your userId once for use as the “author”
        val authorId = getSharedPreferences("user", MODE_PRIVATE)
            .getString("userId", "")!!

        // 2) Load your preview list
        val allPreviews = loadAllChatPreviews()

        // 3) Set up RecyclerView + adapter
        val recyclerView: RecyclerView = findViewById(R.id.chatHubRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = ChatPreviewAdapter(allPreviews) { preview ->

            // 4) Create a new session ID
            val newChatId = System.currentTimeMillis().toString()

            // 5) Persist an initial “System” message + author
            saveChatSession(
                chatId = newChatId,
                title = preview.title,
                messages = listOf(ChatMessage("System", preview.description)),
                author = authorId
            )

            // 6) Build ChatProfile from the preview, reusing its mode
            val profile = ChatProfile(
                id = newChatId,
                title = preview.title,
                description = preview.description,
                tags = emptyList(),
                mode = preview.mode,        // ← use preview.mode here
                backgroundUri = null,
                sfwOnly = false,
                characterIds = emptyList(),
                rating = preview.rating,
                timestamp = preview.timestamp
            )

            // 7) Kick off MainActivity with the profile & first message
            val json = Gson().toJson(profile)
            Intent(this, MainActivity::class.java).apply {
                putExtra("CHAT_PROFILE_JSON", json)
                putExtra("FIRST_MESSAGE", preview.description)
            }.also(::startActivity)
        }

        // 1) Wire up Sort Spinner
        val sortSpinner: Spinner = findViewById(R.id.sortSpinner)
        val sortOptions = listOf("Recommended", "Hot", "Latest", "Popular")
        sortSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            sortOptions
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        // 2) Wire up Search Bar (stub for MVP)
        val searchEditText: EditText = findViewById(R.id.searchEditText)
    }
}