package com.example.RealmsAI

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject

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
        adapter = ChatPreviewAdapter(
            this,             // 👈 this = context
            initialList,
        ) { preview ->
            // when the user taps one preview, pull its raw JSON back out of prefs
            val prefs      = getSharedPreferences("chat_sessions", Context.MODE_PRIVATE)
            val allChatsStr= prefs.getString("all_chats", "{}") ?: "{}"
            val allChats   = JSONObject(allChatsStr)
            if (!allChats.has(preview.id)) {
                Toast.makeText(this, "Couldn’t find chat: ${preview.title}", Toast.LENGTH_SHORT).show()
                return@ChatPreviewAdapter
            }
            // grab the stored session object and re‐serialize it to JSON
            val chatObj    = allChats.getJSONObject(preview.id)
            val rawJson    = chatObj.toString()

            // launch MainActivity with that JSON
            Intent(this, MainActivity::class.java)
                .putExtra("CHAT_PROFILE_JSON", rawJson)
                .also { startActivity(it) }
        }
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
