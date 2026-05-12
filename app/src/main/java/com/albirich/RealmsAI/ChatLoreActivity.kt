package com.albirich.RealmsAI

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.Group
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.albirich.RealmsAI.adapters.CollectionAdapter.CharacterRowAdapter
import com.albirich.RealmsAI.adapters.MiniLorebookAdapter
import com.albirich.RealmsAI.models.CharacterProfile
import com.albirich.RealmsAI.models.Lorebook
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ChatLoreActivity : AppCompatActivity() {

    // --- State ---
    private var isSingleMode = false
    private var allUserLorebooks = listOf<Lorebook>()

    // Data payloads we will return
    private var singleCharLoreIds = mutableListOf<String>()

    // For Chat Creation Mode
    private var globalLoreIds = mutableListOf<String>()
    private var chatCharacters = listOf<CharacterProfile>()
    private var charLoreMap = mutableMapOf<String, MutableList<String>>()
    private var selectedCharId: String? = null

    // --- Adapters ---
    private lateinit var globalAdapter: MiniLorebookAdapter
    private lateinit var charLoreAdapter: MiniLorebookAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_lore)

        // 1. Determine Mode from Intent
        val singleJson = intent.getStringExtra("LOREBOOK_IDS_JSON")
        if (singleJson != null) {
            isSingleMode = true
            val type = object : TypeToken<List<String>>() {}.type
            singleCharLoreIds = Gson().fromJson(singleJson, type) ?: mutableListOf()
        } else {
            // WE ARE IN CHAT CREATION MODE! Parse the global lists and the characters
            val globalJson = intent.getStringExtra("GLOBAL_LORE_JSON")
            val charMapJson = intent.getStringExtra("CHAR_LORE_MAP_JSON")
            chatCharacters = ChatDataCache.selectedCharacters.toList()

            if (!globalJson.isNullOrBlank()) {
                val type = object : TypeToken<List<String>>() {}.type
                globalLoreIds = Gson().fromJson(globalJson, type) ?: mutableListOf()
            }
            if (!charMapJson.isNullOrBlank()) {
                val type = object : TypeToken<Map<String, MutableList<String>>>() {}.type
                charLoreMap = Gson().fromJson(charMapJson, type) ?: mutableMapOf()
            }
            if (chatCharacters.isNotEmpty()) {
                selectedCharId = chatCharacters[0].id
                findViewById<TextView>(R.id.selectedCharLabel).text = "Equipped by: ${chatCharacters[0].name}"
            }
        }

        // 2. Setup UI Elements
        val globalGroup = findViewById<Group>(R.id.globalSectionGroup)
        val charPickerRecycler = findViewById<RecyclerView>(R.id.characterPickerRecycler)
        val selectedCharLabel = findViewById<TextView>(R.id.selectedCharLabel)

        if (isSingleMode) {
            globalGroup.visibility = View.GONE
            charPickerRecycler.visibility = View.GONE
            selectedCharLabel.text = "Equipped by: This Character"
        }

        // 3. Initialize Adapters
        setupAdapters()

        // 4. Fetch Lorebooks
        fetchAllLorebooks()

        // 5. Button Listeners
        findViewById<View>(R.id.addGlobalBtn).setOnClickListener {
            showLorebookPicker { pickedBook ->
                if (!globalLoreIds.contains(pickedBook.id)) {
                    globalLoreIds.add(pickedBook.id)
                    refreshAdapters()
                }
            }
        }

        findViewById<View>(R.id.addCharBtn).setOnClickListener {
            showLorebookPicker { pickedBook ->
                if (isSingleMode) {
                    if (!singleCharLoreIds.contains(pickedBook.id)) {
                        singleCharLoreIds.add(pickedBook.id)
                        refreshAdapters()
                    }
                } else {
                    val charId = selectedCharId ?: return@showLorebookPicker
                    val list = charLoreMap[charId] ?: mutableListOf()
                    if (!list.contains(pickedBook.id)) {
                        list.add(pickedBook.id)
                        charLoreMap[charId] = list
                        refreshAdapters()
                    }
                }
            }
        }

        findViewById<MaterialButton>(R.id.btnDone).setOnClickListener {
            saveAndReturn()
        }
    }

    private fun setupAdapters() {
        val globalRecycler = findViewById<RecyclerView>(R.id.globalLoreRecycler)
        val charRecycler = findViewById<RecyclerView>(R.id.charLoreRecycler)
        val pickerRecycler = findViewById<RecyclerView>(R.id.characterPickerRecycler) // Added

        globalRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        charRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        // ADDED: Set up the horizontal character picker
        if (!isSingleMode) {
            pickerRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

            // THE FIX: Explicitly name the parameters so Kotlin knows exactly what this lambda is for!
            pickerRecycler.adapter = CharacterRowAdapter(
                characters = chatCharacters,
                onClick = { clickedChar ->
                    // When they click a character, change the target and refresh the lorebooks shown!
                    selectedCharId = clickedChar.id
                    findViewById<TextView>(R.id.selectedCharLabel).text = "Equipped by: ${clickedChar.name}"
                    refreshAdapters()
                }
            )
        }

        globalAdapter = MiniLorebookAdapter(emptyList()) { bookToRemove ->
            globalLoreIds.remove(bookToRemove.id)
            refreshAdapters()
        }

        charLoreAdapter = MiniLorebookAdapter(emptyList()) { bookToRemove ->
            if (isSingleMode) {
                singleCharLoreIds.remove(bookToRemove.id)
            } else {
                selectedCharId?.let { charId ->
                    charLoreMap[charId]?.remove(bookToRemove.id)
                }
            }
            refreshAdapters()
        }

        globalRecycler.adapter = globalAdapter
        charRecycler.adapter = charLoreAdapter
    }

    private fun fetchAllLorebooks() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseFirestore.getInstance().collection("lorebooks")
            .whereEqualTo("creatorId", userId)
            .get()
            .addOnSuccessListener { snap ->
                allUserLorebooks = snap.documents.mapNotNull { it.toObject(Lorebook::class.java) }
                refreshAdapters()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load your Lorebooks.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun refreshAdapters() {
        // Map String IDs back to actual Lorebook objects for the UI
        val globalBooks = globalLoreIds.mapNotNull { id -> allUserLorebooks.find { it.id == id } }
        globalAdapter.updateList(globalBooks)

        val charBooks = if (isSingleMode) {
            singleCharLoreIds.mapNotNull { id -> allUserLorebooks.find { it.id == id } }
        } else {
            val list = charLoreMap[selectedCharId] ?: emptyList()
            list.mapNotNull { id -> allUserLorebooks.find { it.id == id } }
        }
        charLoreAdapter.updateList(charBooks)
    }

    private fun showLorebookPicker(onPicked: (Lorebook) -> Unit) {
        if (allUserLorebooks.isEmpty()) {
            Toast.makeText(this, "You haven't created any Lorebooks yet!", Toast.LENGTH_SHORT).show()
            return
        }

        val displayNames = allUserLorebooks.map { it.title.ifBlank { "Unnamed Lorebook" } }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select a Lorebook")
            .setItems(displayNames) { _, which ->
                onPicked(allUserLorebooks[which])
            }
            .show()
    }

    private fun saveAndReturn() {
        val resultIntent = Intent()

        if (isSingleMode) {
            resultIntent.putExtra("LOREBOOK_IDS_JSON", Gson().toJson(singleCharLoreIds))
        } else {
            resultIntent.putExtra("GLOBAL_LORE_JSON", Gson().toJson(globalLoreIds))
            resultIntent.putExtra("CHAR_LORE_MAP_JSON", Gson().toJson(charLoreMap))
        }

        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}