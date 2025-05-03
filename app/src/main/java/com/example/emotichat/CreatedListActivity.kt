package com.example.emotichat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.GridLayoutManager
import com.google.gson.Gson
import org.json.JSONArray
import org.json.JSONObject

class CreatedListActivity : BaseActivity() {
    private val currentUserId by lazy {
        getSharedPreferences("user", MODE_PRIVATE)
            .getString("userId","")!!
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_created_list)
        setupBottomNav()

        val spinner  = findViewById<Spinner>(R.id.filterSpinner)
        val recycler = findViewById<RecyclerView>(R.id.createdRecycler)

        val options = listOf("Chats", "Characters")
        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            options
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                when (options[pos]) {
                    "Chats"      -> showChats(recycler)
                    "Characters" -> showCharacters(recycler)
                }
            }
        }

    }


    private fun showChats(rv: RecyclerView) {
        // 1) two‐column grid
        rv.layoutManager = GridLayoutManager(this, 2)

        // 2) open the same prefs we saved into when creating a chat
        val prefs = getSharedPreferences("chats", Context.MODE_PRIVATE)

        // 3) only your own chat previews
        val mine = loadAllChatPreviews()
            .filter { it.author == currentUserId }

        // 4) wire up the RecyclerView
        rv.adapter = ChatPreviewAdapter(mine) { preview ->
            // 1) Read the raw JSON you originally saved under this preview’s ID
            val rawJson = getSharedPreferences("sessions", Context.MODE_PRIVATE)
                .getString(preview.id, null)
                ?: run {
                    Toast.makeText(this,
                        "Couldn’t find chat data for ${preview.title}",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@ChatPreviewAdapter
                }

            // 2) Build and launch your Intent with that JSON
            Intent(this, MainActivity::class.java)
                .putExtra("CHAT_PROFILE_JSON", rawJson)
                .also { startActivity(it) }
        }
    }

    private fun showCharacters(rv: RecyclerView) {
        rv.layoutManager = GridLayoutManager(this, 2)
        val mineChars = loadAllCharacterProfiles()  // all profiles, each has avatarResId + summary
            .filter { it.author == currentUserId }
        rv.adapter = CharacterPreviewAdapter(mineChars) { ch ->
            startActivity(
                Intent(this, CharacterCreationActivity::class.java)
                    .putExtra("CHAR_EDIT_ID", ch.id)
            )
        }
    }


}
