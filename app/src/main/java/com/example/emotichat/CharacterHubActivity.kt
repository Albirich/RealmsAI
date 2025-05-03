package com.example.emotichat

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.widget.addTextChangedListener


class CharacterHubActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_character_hub)
        setupBottomNav()

        // 1) Load all saved character profiles
        val allChars = loadAllCharacterProfiles()

        // 2) RecyclerView + adapter
        val recycler = findViewById<RecyclerView>(R.id.characterHubRecyclerView)
        recycler.layoutManager = GridLayoutManager(this, 2)
        val adapter = CharacterPreviewAdapter(allChars) { char ->
            // TODO: when clicked, start a 1:1 chat with this character
        }
        recycler.adapter = adapter

        // 3) Sort‐by Spinner
        val sortOptions = listOf("Name", "Recent", "Favorites")
        val spinner = findViewById<Spinner>(R.id.sortSpinner)
        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            sortOptions
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        spinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) { /* no‐op */ }
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                val sorted = when (sortOptions[pos]) {
                    "Name"      -> allChars.sortedBy     { it.name               }
                    "Recent"    -> allChars.sortedByDescending { it.createdAt ?: 0L }
                    //"Favorites" -> allChars.filter { it.isFavorite } // or however you flag favs
                    else        -> allChars
                }
                adapter.updateList(sorted)
            }
        }

        // 4) Search Bar
        val searchEdit = findViewById<EditText>(R.id.searchEditText)
        searchEdit.addTextChangedListener { text ->
            val filtered = allChars.filter {
                it.name.contains(text.toString(), ignoreCase = true)
                        || (it.summary ?: "").contains(text.toString(), ignoreCase = true)
            }
            adapter.updateList(filtered)
        }
    }
}