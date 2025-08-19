package com.example.RealmsAI

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.CharacterCollection
import com.example.RealmsAI.models.CharacterPreview
import com.example.RealmsAI.models.CharacterProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.gson.Gson

class CharacterHubActivity : BaseActivity() {
    private lateinit var sortSpinner: Spinner
    private lateinit var adapter: CharacterPreviewAdapter
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_character_hub)
        setupBottomNav()


        // 1) Find & configure RecyclerView
        val charsRv = findViewById<RecyclerView>(R.id.characterHubRecyclerView).apply {
            layoutManager = GridLayoutManager(this@CharacterHubActivity, 2)
        }

        adapter = CharacterPreviewAdapter(
            context = this,
            items = emptyList(),
            itemLayoutRes = R.layout.character_preview_item,
            onClick = { preview ->
                // Get user ID
                val userId = currentUserId
                if (userId == null) {
                    Toast.makeText(this, "You must be signed in to continue.", Toast.LENGTH_SHORT).show()
                    return@CharacterPreviewAdapter
                }
                startActivity(Intent(this, SessionLandingActivity::class.java).apply {
                    Log.d("Characterhubactivity", "it has an id of: ${preview.id}")
                    putExtra("CHARACTER_ID", preview.id)
                    putExtra("CHARACTER_PROFILES_JSON", preview.rawJson)
                })
            },
            onLongClick = { preview ->
                AlertDialog.Builder(this)
                    .setTitle(preview.name)
                    .setItems(arrayOf("Profile", "Creator", "Add to Collection")) { _, which ->
                        when (which) {
                            0 -> { // Profile
                                startActivity(
                                    Intent(this, CharacterProfileActivity::class.java)
                                        .putExtra("characterId", preview.id)
                                )
                            }
                            1 -> { // Creator
                                startActivity(
                                    Intent(this, DisplayProfileActivity::class.java)
                                        .putExtra("userId", preview.author)
                                )
                            }
                            2 -> { // Add to Collection
                                val userId = currentUserId
                                if (userId == null) {
                                    Toast.makeText(this, "Sign in first.", Toast.LENGTH_SHORT)
                                        .show()
                                    return@setItems
                                }
                                loadUserCollections { colls ->
                                    if (colls.isEmpty()) {
                                        // No collections yet → prompt to create
                                        promptNewCollectionName { name ->
                                            createCollection(name, userId) { newColl ->
                                                addCharacterToCollection(newColl.id, preview.id)
                                            }
                                        }
                                    } else {
                                        showCollectionPickerDialog(colls) { picked ->
                                            addCharacterToCollection(picked.id, preview.id)
                                        }
                                    }
                                }
                            }
                        }
                    }
                .show()
            }

        )

        charsRv.adapter = adapter

        // 3) Set up the Spinner ("Latest" vs. "Hot")
        sortSpinner = findViewById(R.id.sortSpinner)
        val options = listOf("Latest", "Hot")
        sortSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            options
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        sortSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                // pos==0 → sort by createdAt; pos==1 → sort by your “hotness” field (e.g. popularity)
                val field = if (pos == 0) "createdAt" else "popularity"
                showCharacters(charsRv, orderBy = field)
            }
        }

        // 4) Initial load = “Latest”
        showCharacters(charsRv, orderBy = "createdAt")
    }

    private fun loadUserCollections(onLoaded: (List<CharacterCollection>) -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) return onLoaded(emptyList())

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("collections")
            .orderBy("name") // optional
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.mapNotNull { doc ->
                    doc.toObject(CharacterCollection::class.java)?.copy(id = doc.id)
                }
                onLoaded(list)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load collections.", Toast.LENGTH_SHORT).show()
                onLoaded(emptyList())
            }
    }

    private fun showCollectionPickerDialog(
        collections: List<CharacterCollection>,
        onPicked: (CharacterCollection) -> Unit
    ) {
        val names = collections.map { it.name } + "New collection…"
        AlertDialog.Builder(this)
            .setTitle("Add to collection")
            .setItems(names.toTypedArray()) { _, idx ->
                if (idx == collections.size) {
                    // "New collection…"
                    promptNewCollectionName { name ->
                        val userId = currentUserId ?: return@promptNewCollectionName
                        createCollection(name, userId) { newColl ->
                            onPicked(newColl)
                        }
                    }
                } else {
                    onPicked(collections[idx])
                }
            }
            .show()
    }

    private fun promptNewCollectionName(onNamed: (String) -> Unit) {
        val input = android.widget.EditText(this).apply { hint = "Collection name" }
        AlertDialog.Builder(this)
            .setTitle("New collection")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isBlank()) {
                    Toast.makeText(this, "Name cannot be empty.", Toast.LENGTH_SHORT).show()
                } else onNamed(name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createCollection(
        name: String,
        userId: String,
        onCreated: (CharacterCollection) -> Unit
    ) {
        val data = hashMapOf(
            "name" to name,
            "characterIds" to emptyList<String>()
        )

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("collections")
            .add(data)
            .addOnSuccessListener { ref ->
                onCreated(CharacterCollection(id = ref.id, name = name, characterIds = emptyList()))
                Toast.makeText(this, "Collection created.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to create collection.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addCharacterToCollection(collectionId: String, characterId: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ref = FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("collections")
            .document(collectionId)

        ref.update("characterIds", com.google.firebase.firestore.FieldValue.arrayUnion(characterId))
            .addOnSuccessListener {
                Toast.makeText(this, "Added to collection.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to add to collection.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showCharacters(
        rv: RecyclerView,
        orderBy: String = "createdAt"
    ) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "You must be signed in to view characters.", Toast.LENGTH_SHORT).show()
            return
        }

        FirebaseFirestore.getInstance()
            .collection("characters")
            .whereNotEqualTo("private", true)
            .orderBy(orderBy, Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.mapNotNull { doc ->
                    // 1) Deserialize + inject doc ID
                    val cp = doc.toObject(CharacterProfile::class.java)
                        ?.copy(id = doc.id)
                        ?: return@mapNotNull null

                    // 2) Build preview
                    CharacterPreview(
                        id = cp.id,
                        name = cp.name,
                        summary = cp.summary.orEmpty(),
                        avatarUri = cp.avatarUri,
                        avatarResId = cp.avatarResId ?: R.drawable.placeholder_avatar,
                        author = cp.author,
                        rawJson = Gson().toJson(cp)
                    )

                }
                adapter.updateList(list)
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Failed to load characters: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}
