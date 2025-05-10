package com.example.RealmsAI

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class CharacterHubActivity : BaseActivity() {

    private lateinit var adapter: CharacterPreviewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_character_hub)
        setupBottomNav()

        // 1) load all profiles
        val allChars = loadAllCharacterProfiles()

        // 2) Recycler + adapter…
        val recycler = findViewById<RecyclerView>(R.id.characterHubRecyclerView)
        recycler.layoutManager = GridLayoutManager(this, 2)
        adapter = CharacterPreviewAdapter(this, emptyList(), onClick = { preview ->
            val fullJson = preview.toFullProfileJson()

            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("chatId", preview.id)
                putExtra("CHAT_PROFILE_JSON", fullJson)
            }

            startActivity(intent)
        })
        recycler.adapter = adapter

        // 3) Sort spinner—declare it before your search listener!
        val spinnerSort = findViewById<Spinner>(R.id.sortSpinner)
        val searchEditText = findViewById<EditText>(R.id.searchEditText)
        val sortOptions = listOf("Name", "Recent")
        spinnerSort.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            sortOptions
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                refreshList(allChars, search = searchEditText.text.toString(), sortBy = sortOptions[pos])
            }
        }

        // 4) Search box
        searchEditText.addTextChangedListener { editable ->
            refreshList(
                allChars,
                search  = editable?.toString().orEmpty(),
                sortBy  = spinnerSort.selectedItem as String
            )
        }

        // 5) initial load
        refreshList(allChars, search = "", sortBy = sortOptions.first())
    }


    /**
     * Filters and sorts the full list, maps down to [CharacterPreview], and
     * pushes into the adapter in one go.
     */
    private fun refreshList(
        allChars: List<CharacterProfile>,
        search: String,
        sortBy: String
    ) {
        // a) filter
        val filtered = allChars.filter { cp ->
            cp.name.contains(search, ignoreCase = true) ||
                    (cp.summary ?: "").contains(search, ignoreCase = true)
        }

        // b) sort
        val sorted = when (sortBy) {
            "Name"   -> filtered.sortedBy { it.name }
            "Recent" -> filtered.sortedByDescending { it.createdAt }
            // "Favorites" -> filtered.filter { it.isFavorite } // if you support favorites
            else     -> filtered
        }

        // c) map to previews
        val previews = sorted.map { cp ->
            CharacterPreview(
                id          = cp.id,
                name        = cp.name,
                summary     = cp.summary.orEmpty(),
                avatarUri   = cp.avatarUri,
                avatarResId = cp.avatarResId,
                author      = cp.author
            )
        }

        // d) update adapter
        adapter.updateList(previews)
    }

    /**
     * Helps you pass the full JSON back into your chat screen.
     */
    private fun CharacterPreview.toFullProfileJson(): String {
        return getSharedPreferences("characters", MODE_PRIVATE)
            .getString(id, "{}")
            ?: "{}"
    }
}
