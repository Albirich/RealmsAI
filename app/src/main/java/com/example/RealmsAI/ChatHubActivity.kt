package com.example.RealmsAI

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson

class ChatHubActivity : BaseActivity() {

    private lateinit var adapter: ChatPreviewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_hub)
        setupBottomNav()

        // 1) Set up your sort‐by spinner
        val spinner = findViewById<Spinner>(R.id.sortSpinner)
        ArrayAdapter.createFromResource(
            this,
            R.array.chat_hub_sort_options,                  // e.g. ["Latest","Popular","All"]
            android.R.layout.simple_spinner_item
        ).also { spinAdapter ->
            spinAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = spinAdapter
        }

        // 2) Build the RecyclerView + adapter
        val rv = findViewById<RecyclerView>(R.id.chatHubRecyclerView)
        rv.layoutManager = GridLayoutManager(this, 2)

        // 2a) Load your initial list of previews
        val initialList = loadAllChatPreviews()

        // --- FIXED: pass `this` then the list, then the click‐lambda ---
        adapter = ChatPreviewAdapter(
            context = this,
            chatList = initialList,
            onClick  = { preview ->
                // launch MainActivity directly with the ChatProfile we already loaded
                Intent(this, MainActivity::class.java).apply {
                    putExtra("chatId", preview.id)
                    putExtra("CHAT_PROFILE_JSON", Gson().toJson(preview.chatProfile))
                }.also(::startActivity)
            }
        )
        rv.adapter = adapter

        // 3) Wire up sorting
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                val choice = parent.getItemAtPosition(pos) as String
                val sorted = when (choice) {
                    "Latest"  -> loadAllChatPreviews().sortedByDescending { it.timestamp }
                    "Popular" -> loadAllChatPreviews().sortedByDescending { it.rating    }
                    else      -> loadAllChatPreviews()
                }
                adapter.updateList(sorted)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // refresh the list in case someone just created a new chat
        adapter.updateList(loadAllChatPreviews())
    }
}
