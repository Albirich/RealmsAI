package com.example.RealmsAI

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.adapters.CollectionAdapter
import com.example.RealmsAI.models.CharacterCollection
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class CollectionActivity : AppCompatActivity() {

    private lateinit var collectionAdapter: CollectionAdapter
    private val collections = mutableListOf<CharacterCollection>()

    private val db = FirebaseFirestore.getInstance()
    private val userId get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    companion object {
        private const val CREATE_COLLECTION_REQUEST = 1001
        private const val EDIT_COLLECTION_REQUEST = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_collection)

        // Add new collection button
        findViewById<ImageButton>(R.id.btn_add_collection).setOnClickListener {
            promptNewCollectionName()
        }

        // Recycler setup
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_collections)
        collectionAdapter = CollectionAdapter(
            collections,
            onEditClicked = { collection -> launchCharacterSelectorEdit(collection) },
            onDeleteClicked = { loadCollections() } // ✅ Reload after delete
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = collectionAdapter

        loadCollections()
    }

    private fun promptNewCollectionName() {
        val input = EditText(this)
        input.hint = "Collection Name"

        AlertDialog.Builder(this)
            .setTitle("Create New Collection")
            .setView(input)
            .setPositiveButton("Next") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    launchCharacterSelectorCreate(name)
                } else {
                    Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launchCharacterSelectorCreate(collectionName: String) {
        val intent = Intent(this, CharacterSelectionActivity::class.java)
        intent.putExtra("mode", "create")
        intent.putExtra("collectionName", collectionName)
        intent.putExtra("selectionCap", 20)
        startActivityForResult(intent, CREATE_COLLECTION_REQUEST)
    }

    private fun launchCharacterSelectorEdit(collection: CharacterCollection) {
        val intent = Intent(this, CharacterSelectionActivity::class.java)
        intent.putExtra("mode", "edit")
        intent.putExtra("collectionId", collection.id)
        intent.putExtra("collectionName", collection.name)
        intent.putStringArrayListExtra("preSelectedIds", ArrayList(collection.characterIds))
        intent.putExtra("selectionCap", 20)
        startActivityForResult(intent, EDIT_COLLECTION_REQUEST)
    }

    private fun loadCollections() {
        if (userId.isEmpty()) return

        db.collection("users").document(userId).collection("collections")
            .get()
            .addOnSuccessListener { result ->
                collections.clear()
                for (doc in result) {
                    val collection = doc.toObject(CharacterCollection::class.java)
                    collections.add(collection)
                }
                collectionAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Log.e("CollectionActivity", "Error loading collections", e)
                Toast.makeText(this, "Failed to load collections", Toast.LENGTH_SHORT).show()
            }
    }

    private fun createNewCollection(name: String, characterIds: List<String>) {
        if (userId.isEmpty()) return

        val id = UUID.randomUUID().toString()
        val newCollection = CharacterCollection(id, name, characterIds)

        db.collection("users").document(userId)
            .collection("collections").document(id)
            .set(newCollection)
            .addOnSuccessListener {
                Toast.makeText(this, "Collection created", Toast.LENGTH_SHORT).show()
                collections.add(newCollection)
                collectionAdapter.notifyItemInserted(collections.size - 1)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to create collection", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateCollection(id: String, updatedIds: List<String>) {
        if (userId.isEmpty()) return

        db.collection("users").document(userId)
            .collection("collections").document(id)
            .update("characterIds", updatedIds)
            .addOnSuccessListener {
                Toast.makeText(this, "Collection updated", Toast.LENGTH_SHORT).show()
                loadCollections()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update collection", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != Activity.RESULT_OK || data == null) return

        when (requestCode) {
            CREATE_COLLECTION_REQUEST -> {
                val name = data.getStringExtra("collectionName") ?: return
                val selected = data.getStringArrayListExtra("selectedCharacterIds") ?: return
                createNewCollection(name, selected)
            }

            EDIT_COLLECTION_REQUEST -> {
                val id = data.getStringExtra("collectionId") ?: return
                val selected = data.getStringArrayListExtra("selectedCharacterIds") ?: return
                updateCollection(id, selected)
            }
        }
    }
}
