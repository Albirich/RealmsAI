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

class ChatHubActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_hub)

        val previews = loadAllChatPreviews()

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

        // 3) Initialize RecyclerView + Adapter
        val recyclerView: RecyclerView = findViewById(R.id.chatHubRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 2)

        // Load existing chat previews (or public chat templates)
        val allPreviews = loadAllChatPreviews()

        // Create adapter with click handling
        val adapter = ChatPreviewAdapter(allPreviews) { preview ->
            val newChatId = System.currentTimeMillis().toString()
            saveChatSession(
                chatId = newChatId,
                title = preview.title,
                messages = listOf(ChatMessage("System", preview.description))
            )
            startActivity(Intent(this, MainActivity::class.java).apply {
                putExtra("CHAT_ID", newChatId)
                putExtra("CHAT_TITLE", preview.title)
                putExtra("AVATAR1_RES", preview.avatar1ResId)
                putExtra("AVATAR2_RES", preview.avatar2ResId)
            })
        }
        recyclerView.adapter = adapter

        // 4) Sort selection handling
        sortSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>, view: View?, position: Int, id: Long
            ) {
                val sorted = when (sortOptions[position]) {
                    "Latest" -> allPreviews.sortedByDescending { it.timestamp }
                    "Popular" -> allPreviews.sortedByDescending { it.rating }
                    "Hot" -> {
                        val now = System.currentTimeMillis()
                        allPreviews.sortedByDescending {
                            val hours = (now - it.timestamp).toDouble() / 3_600_000
                            it.rating / (hours + 1)
                        }
                    }
                    else /* Recommended */ -> allPreviews
                }
                adapter.updateList(sorted)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

    }
}
