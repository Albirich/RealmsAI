package com.example.emotichat

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class CharacterHubActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_character_hub)    // make sure this is the right layout
        setupBottomNav()

        // 1) Load all saved CharacterProfiles
        val allChars = loadAllCharacterProfiles()

        // 2) Wire up your RecyclerView
        findViewById<RecyclerView>(R.id.characterHubRecyclerView).apply {
            layoutManager = GridLayoutManager(this@CharacterHubActivity, 2)
            adapter = CharacterPreviewAdapter(allChars) { ch ->
                // On click → edit that character
                Intent(this@CharacterHubActivity, CharacterCreationActivity::class.java)
                    .putExtra("CHAR_EDIT_ID", ch.id)
                    .also(::startActivity)
            }
        }

        // 3) (Optional) If you still want a sort spinner / search bar…
        val sortSpinner: Spinner = findViewById(R.id.sortSpinner)
        val sortOptions = listOf("Name", "Recent", "Favorites")
        sortSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            sortOptions
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        val searchEditText: EditText = findViewById(R.id.searchEditText)
        // TODO: wire up your search to filter `allChars` and call adapter.updateList(...)
    }
}
