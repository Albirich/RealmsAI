package com.example.RealmsAI

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.adapters.CollectionAdapter.CharacterRowAdapter
import com.example.RealmsAI.models.Area
import com.example.RealmsAI.models.CharacterProfile
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ChatMapActivity : AppCompatActivity() {

    private lateinit var characterRecycler: RecyclerView
    private lateinit var areaRecycler: RecyclerView
    private lateinit var addAreaButton: MaterialButton
    private lateinit var doneButton: MaterialButton

    private val selectedCharacters = mutableListOf<CharacterProfile>()
    private val loadedAreas = mutableListOf<Area>()

    // characterId -> areaId
    private val characterToAreaMap = mutableMapOf<String, String>()
    // areaId -> color
    private val areaColors = mutableMapOf<String, Int>()
    // Colors to cycle through
    private val predefinedColors = listOf(
        Color.RED, Color.BLUE, Color.GREEN, Color.MAGENTA, Color.CYAN, Color.YELLOW, Color.LTGRAY
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_map)

        characterRecycler = findViewById(R.id.characterRecycler)
        areaRecycler = findViewById(R.id.areaRecycler)
        addAreaButton = findViewById(R.id.addAreaButton)
        doneButton = findViewById(R.id.doneButton)

        // Load characters from JSON (from ChatCollectionActivity)
        intent.getStringExtra("CHARACTER_LIST_JSON")?.let { json ->
            val listType = object : TypeToken<List<CharacterProfile>>() {}.type
            selectedCharacters.addAll(Gson().fromJson(json, listType))
        }

        // Load areas from JSON (could be empty initially)
        intent.getStringExtra("AREA_LIST_JSON")?.let { json ->
            val listType = object : TypeToken<List<Area>>() {}.type
            loadedAreas.addAll(Gson().fromJson(json, listType))
        }

        // Assign area colors (in order loaded)
        loadedAreas.forEachIndexed { idx, area ->
            areaColors[area.id] = predefinedColors[idx % predefinedColors.size]
        }

        // Character Row Adapter (with border color based on area assignment)
        characterRecycler.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        characterRecycler.adapter = CharacterRowAdapter(
            characters = selectedCharacters,
            onClick = { character -> showAreaSelectionPopup(character) }
        ) { character, itemView ->
            // Here you set the border color based on area assignment
            val areaId = characterToAreaMap[character.id]
            val borderColor = areaId?.let { areaColors[it] } ?: Color.TRANSPARENT

            if (itemView is com.google.android.material.card.MaterialCardView) {
                itemView.strokeColor = borderColor
                itemView.strokeWidth = if (borderColor != Color.TRANSPARENT) 8 else 0
            } else {
                itemView.setBackgroundColor(borderColor)
            }
        }

        // Area Adapter (readonly, no buttons)
        areaRecycler.layoutManager = LinearLayoutManager(this)
        areaRecycler.adapter = AreaAdapter(
            areas = loadedAreas,
            onPickImage = { _, _ -> },
            onDeleteLocation = { _, _ -> },
            readonly = true
        )

        addAreaButton.setOnClickListener {
            loadAreasForPicker { userAreas, defaultAreas ->
                showDualAreaPickerDialog(userAreas, defaultAreas) { pickedArea ->
                    // Prevent duplicate areas by name or ID
                    if (loadedAreas.any { it.name == pickedArea.name }) {
                        Toast.makeText(this, "Area already added.", Toast.LENGTH_SHORT).show()
                    } else {
                        val newArea = pickedArea.copy(
                            id = java.util.UUID.randomUUID().toString(),
                            creatorId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "",
                            locations = pickedArea.locations.map { it.copy(id = java.util.UUID.randomUUID().toString()) }.toMutableList()
                        )
                        loadedAreas.add(newArea)
                        areaColors[newArea.id] = predefinedColors[loadedAreas.size % predefinedColors.size]
                        areaRecycler.adapter?.notifyItemInserted(loadedAreas.size - 1)
                    }
                }
            }
        }

        doneButton.setOnClickListener {
            // Prepare data to send back
            val areasJson = Gson().toJson(loadedAreas)
            val charactersJson = Gson().toJson(selectedCharacters)
            val assignmentJson = Gson().toJson(characterToAreaMap)

            val resultIntent = Intent().apply {
                putExtra("AREAS_JSON", areasJson)
                putExtra("CHARACTERS_JSON", charactersJson)
                putExtra("CHARACTER_TO_AREA_JSON", assignmentJson)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }

    }

    private fun showAreaSelectionPopup(character: CharacterProfile) {
        val popup = PopupMenu(this, characterRecycler)
        loadedAreas.forEachIndexed { idx, area ->
            popup.menu.add(0, idx, 0, area.name)
        }
        popup.setOnMenuItemClickListener { item: MenuItem ->
            val area = loadedAreas[item.itemId]
            characterToAreaMap[character.id] = area.id
            characterRecycler.adapter?.notifyDataSetChanged()
            true
        }
        popup.show()
    }

    private fun loadAreasForPicker(callback: (userAreas: List<Area>, defaultAreas: List<Area>) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val userAreas = mutableListOf<Area>()
        val defaultAreas = mutableListOf<Area>()

        db.collection("areas")
            .whereEqualTo("creatorId", userId)
            .get()
            .addOnSuccessListener { userSnap ->
                userAreas.addAll(userSnap.documents.mapNotNull { it.toObject(Area::class.java) })
                db.collection("areas")
                    .whereEqualTo("creatorId", "123")
                    .get()
                    .addOnSuccessListener { defaultSnap ->
                        defaultAreas.addAll(defaultSnap.documents.mapNotNull { it.toObject(Area::class.java) })
                        callback(userAreas, defaultAreas)
                    }
                    .addOnFailureListener { callback(userAreas, emptyList()) }
            }
            .addOnFailureListener {
                db.collection("areas")
                    .whereEqualTo("creatorId", "123")
                    .get()
                    .addOnSuccessListener { defaultSnap ->
                        callback(emptyList(), defaultSnap.documents.mapNotNull { it.toObject(Area::class.java) })
                    }
                    .addOnFailureListener { callback(emptyList(), emptyList()) }
            }
    }

    private fun showDualAreaPickerDialog(
        userAreas: List<Area>,
        defaultAreas: List<Area>,
        onPick: (Area) -> Unit
    ) {
        val areaRefs = mutableListOf<Area>()
        val displayNames = mutableListOf<String>()

        if (userAreas.isNotEmpty()) {
            displayNames.add("— My Areas —")
            userAreas.forEach {
                displayNames.add("  ${it.name.ifBlank { "(Unnamed Area)" }}")
                areaRefs.add(it)
            }
        }
        if (defaultAreas.isNotEmpty()) {
            if (displayNames.isNotEmpty()) displayNames.add("— Default Areas —")
            defaultAreas.forEach {
                displayNames.add("  ${it.name.ifBlank { "(Unnamed Area)" }}")
                areaRefs.add(it)
            }
        }

        if (areaRefs.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("No Areas Found")
                .setMessage("No areas were found to load.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Pick Area")
            .setItems(displayNames.filter { !it.startsWith("—") }.toTypedArray()) { _, which ->
                onPick(areaRefs[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
