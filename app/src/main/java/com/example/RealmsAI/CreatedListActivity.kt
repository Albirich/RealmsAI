package com.example.RealmsAI

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson

class CreatedListActivity : BaseActivity() {

    // grab the current user’s UID so we can filter to “mine”
    private val currentUserId: String by lazy {
        getSharedPreferences("user", Context.MODE_PRIVATE)
            .getString("userId", "")!!
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_created_list)
        setupBottomNav()

        // 1) Spinner & RecyclerView
        val spinner  = findViewById<Spinner>(R.id.filterSpinner)
        val recycler = findViewById<RecyclerView>(R.id.createdRecycler)

        // 2) hookup spinner
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

        // 3) default view
        spinner.setSelection(0)
    }

    private fun showChats(rv: RecyclerView) {
        // two-column grid
        rv.layoutManager = GridLayoutManager(this, 2)

        // only my chats
        val mine = loadAllChatPreviews()
            .filter { it.author == currentUserId }

        // wire up adapter with click + long-click (delete)
        rv.adapter = ChatPreviewAdapter(
            this,
            mine,
            onClick = { preview ->
                // launch the chat
                val rawJson = getSharedPreferences("chats", Context.MODE_PRIVATE)
                    .getString(preview.id, null)
                if (rawJson == null) {
                    Toast.makeText(this,
                        "Couldn’t find chat data for ${preview.title}",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@ChatPreviewAdapter
                }
                Intent(this, MainActivity::class.java)
                    .putExtra("CHAT_PROFILE_JSON", rawJson)
                    .also(::startActivity)
            },
            onLongClick = { preview ->
                AlertDialog.Builder(this)
                    .setTitle("Delete chat “${preview.title}”?")
                    .setMessage("This will permanently remove it.")
                    .setPositiveButton("Delete") { _, _ ->
                        getSharedPreferences("chats", Context.MODE_PRIVATE)
                            .edit()
                            .remove(preview.id)
                            .apply()
                        showChats(rv)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
    }

    private fun showCharacters(rv: RecyclerView) {
        rv.layoutManager = GridLayoutManager(this, 2)

        // only my full profiles
        val myOwnCharacterPreviews = loadAllCharacterProfiles()
            .filter { it.author == currentUserId }          // only yours
            .map { cp ->
                CharacterPreview(
                    id         = cp.id,
                    name       = cp.name,
                    summary    = cp.summary.orEmpty(),
                    avatarUri  = cp.avatarUri,
                    avatarResId= cp.avatarResId,
                    author     = cp.author
                )
            }

        rv.adapter = CharacterPreviewAdapter(
            this,
            myOwnCharacterPreviews,
            onClick = { preview ->
                // edit flow
                val raw = getSharedPreferences("characters", Context.MODE_PRIVATE)
                    .getString(preview.id, null)
                if (raw == null) {
                    Toast.makeText(this,
                        "Couldn’t find character data for ${preview.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@CharacterPreviewAdapter
                }
                Intent(this, CharacterCreationActivity::class.java)
                    .putExtra("CHAR_EDIT_ID", preview.id)
                    .putExtra("CHAR_PROFILE_JSON", raw)
                    .also(::startActivity)
            },
            onLongClick = { preview ->
                AlertDialog.Builder(this)
                    .setTitle("Delete “${preview.name}”?")
                    .setMessage("This will permanently remove it.")
                    .setPositiveButton("Delete") {_,_ ->
                        getSharedPreferences("characters", MODE_PRIVATE)
                            .edit()
                            .remove(preview.id)
                            .apply()
                        // then refresh:
                        showCharacters(rv)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
    }
}
