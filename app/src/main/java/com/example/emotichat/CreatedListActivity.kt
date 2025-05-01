package com.example.emotichat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.GridLayoutManager

class CreatedListActivity : BaseActivity() {

    // ← Add this
    private val currentUserId: String by lazy {
        getSharedPreferences("user", Context.MODE_PRIVATE)
            .getString("userId", "")!!
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
        rv.layoutManager = GridLayoutManager(this, 2)

        val prefs = getSharedPreferences("chats", MODE_PRIVATE)
        Log.d("DEBUG", "chat‐prefs keys = ${prefs.all.keys}")  // ← should show keys!

        val previews = loadAllChatPreviews()
        Log.d("DEBUG", "loaded ${previews.size} chat previews")

        rv.adapter = ChatPreviewAdapter(previews) { preview ->
            // …
        }
    }


    private fun showCharacters(rv: RecyclerView) {
        rv.layoutManager = GridLayoutManager(this, 2)
        val chars = loadAllCharacterProfiles()  // all profiles, each has avatarResId + summary
        rv.adapter = CharacterPreviewAdapter(chars) { ch ->
            startActivity(
                Intent(this, CharacterCreationActivity::class.java)
                    .putExtra("CHAR_EDIT_ID", ch.id)
            )
        }
    }


}
