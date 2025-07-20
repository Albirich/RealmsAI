package com.example.RealmsAI

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.CharacterPreviewAdapter
import com.example.RealmsAI.models.CharacterCollection
import com.example.RealmsAI.models.CharacterProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson

class ChatCollectionActivity : AppCompatActivity() {

    private val selectedCharacters = mutableListOf<CharacterProfile>()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CharacterPreviewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_collection)

        val recyclerView = findViewById<RecyclerView>(R.id.characterRecycler)
        recyclerView.layoutManager = GridLayoutManager(this, 2)

        val previews = selectedCharacters.map {
            CharacterPreview(
                id        = it.id,
                name      = it.name,
                summary   = it.summary.orEmpty(),
                avatarUri = it.avatarUri,
                avatarResId = it.avatarResId ?: R.drawable.placeholder_avatar,
                author    = it.author,
                rawJson   = Gson().toJson(it)
            )
        }
        adapter = CharacterPreviewAdapter(
            context = this,
            items = previews,
            itemLayoutRes = R.layout.character_preview_item,
            onClick = { /* Optional: define behavior */ },
            onLongClick = { /* Optional: maybe remove from list */ }
        )
        recyclerView.adapter = adapter


        findViewById<Button>(R.id.btnAddCollection).setOnClickListener {
            loadUserCollections { userCollections ->
                if (userCollections.isEmpty()) {
                    Toast.makeText(this, "No collections found!", Toast.LENGTH_SHORT).show()
                    return@loadUserCollections
                }
                showCollectionPickerDialog(userCollections) { pickedCollection ->
                    // Add pickedCollection's characters to your current list (or however you need)
                    addCollectionToChat(pickedCollection)
                }
            }
        }

        findViewById<Button>(R.id.btnAddIndividual).setOnClickListener {
            val intent = Intent(this, CharacterSelectionActivity::class.java)
            intent.putExtra("TEMP_SELECTION_MODE", true)
            intent.putExtra("from", "chat")
            intent.putExtra("INITIAL_COUNT", selectedCharacters.size)
            intent.putStringArrayListExtra("preSelectedIds", ArrayList(selectedCharacters.map { it.id }))
            startActivityForResult(intent, 1002)
        }

        findViewById<Button>(R.id.btnDone).setOnClickListener {
            val resultIntent = Intent().apply {
                putExtra("CHARACTER_LIST_JSON", Gson().toJson(selectedCharacters))
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun loadUserCollections(onLoaded: (List<CharacterCollection>) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("users").document(userId).collection("collections")
            .get()
            .addOnSuccessListener { result ->
                val collections = result.documents.mapNotNull { it.toObject(CharacterCollection::class.java) }
                onLoaded(collections)
            }
            .addOnFailureListener { onLoaded(emptyList()) }
    }

    private fun showCollectionPickerDialog(
        collections: List<CharacterCollection>,
        onPick: (CharacterCollection) -> Unit
    ) {
        val displayNames = collections.map { it.name.ifBlank { "(Unnamed Collection)" } }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select Collection")
            .setItems(displayNames.toTypedArray()) { _, which ->
                onPick(collections[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addCollectionToChat(collection: CharacterCollection) {
        // Filter out IDs already in the current selection
        val newIds = collection.characterIds.filter { newId ->
            selectedCharacters.none { it.id == newId }
        }
        if (newIds.isEmpty()) {
            Toast.makeText(this, "All characters already added.", Toast.LENGTH_SHORT).show()
            return
        }
        // Fetch profiles for the new IDs
        val db = FirebaseFirestore.getInstance()
        db.collection("characters")
            .whereIn("id", newIds)
            .get()
            .addOnSuccessListener { result ->
                val newProfiles = result.documents.mapNotNull { it.toObject(CharacterProfile::class.java) }
                selectedCharacters.addAll(newProfiles)
                updateCharacterPreview()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load characters.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateCharacterPreview() {
        val previews = selectedCharacters.map {
            CharacterPreview(
                id        = it.id,
                name      = it.name,
                summary   = it.summary.orEmpty(),
                avatarUri = it.avatarUri,
                avatarResId = it.avatarResId ?: R.drawable.placeholder_avatar,
                author    = it.author,
                rawJson   = Gson().toJson(it)
            )
        }
        adapter.updateList(previews)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return

        when (requestCode) {
            1001 -> { // From CollectionActivity
                val json = data.getStringExtra("CHARACTER_LIST_JSON") ?: return
                val newChars = Gson().fromJson(json, Array<CharacterProfile>::class.java).toList()
                selectedCharacters.addAll(newChars.filterNot { existing ->
                    selectedCharacters.any { it.id == existing.id }
                })
                adapter.notifyDataSetChanged()
            }
            1002 -> { // From CharacterSelectionActivity
                val ids = data.getStringArrayListExtra("selectedCharacterIds") ?: return
                val db = FirebaseFirestore.getInstance()
                db.collection("characters")
                    .whereIn("id", ids)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val newChars = snapshot.documents.mapNotNull { it.toObject(CharacterProfile::class.java) }
                        selectedCharacters.clear()
                        selectedCharacters.addAll(newChars)
                        adapter.notifyDataSetChanged()
                        updateCharacterPreview()
                    }
            }
        }
    }
}
