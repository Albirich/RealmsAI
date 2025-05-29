package com.example.RealmsAI

import android.R.attr.id
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.ParticipantPreview
import com.example.RealmsAI.models.RELATIONSHIP_TYPES
import com.example.RealmsAI.models.Relationship
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson

class ChatRelationshipActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ParticipantRelationshipAdapter
    private val participants = mutableListOf<ParticipantPreview>()
    private val relationships = mutableListOf<Relationship>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_relationships)

        recyclerView = findViewById(R.id.relationshipRecycler)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Get participant character IDs from intent (could be personas/characters/bots)
        val ids = intent.getStringArrayListExtra("PARTICIPANT_IDS") ?: emptyList()
        Log.d("Relationships", "IDs from intent: $ids")

        val relationshipsJson = intent.getStringExtra("RELATIONSHIPS_JSON") ?: "[]"
        relationships.clear()
        relationships.addAll(
            Gson().fromJson(relationshipsJson, Array<Relationship>::class.java).toList()
        )

        fetchParticipantPreviewsGlobal(ids) { loadedPreviews ->
            participants.clear()
            participants.addAll(loadedPreviews)
            Log.d("Relationships", "Participants after addAll: ${participants.map { it.name }}")
            adapter = ParticipantRelationshipAdapter(
                participants,
                relationships,
                onAddRelationship = { fromId -> showAddRelationshipDialog(fromId) },
                onDeleteRelationship = { rel ->
                    relationships.remove(rel)
                    adapter.refresh()
                }
            )
            recyclerView.adapter = adapter
            adapter.refresh()
            Log.d("Relationships", "Adapter size: ${adapter.itemCount}")
        }

        val btnDoneRelationships = findViewById<Button>(R.id.btnDoneRelationships)
        btnDoneRelationships.setOnClickListener {
            val data = Intent().apply {
                putExtra("RELATIONSHIPS_JSON", Gson().toJson(relationships))
            }
            setResult(RESULT_OK, data)
            finish()
        }
    }

    private fun showAddRelationshipDialog(fromId: String) {
        val targets = participants.filter { it.id != fromId }
        if (targets.isEmpty()) {
            Toast.makeText(this, "No valid targets!", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_add_relationship, null)
        val toNameEdit = dialogView.findViewById<EditText>(R.id.relationshipToNameEdit)
        val typeSpinner = dialogView.findViewById<Spinner>(R.id.relationshipTypeSpinner)
        val summaryEdit = dialogView.findViewById<EditText>(R.id.relationshipSummaryEdit)

        typeSpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, RELATIONSHIP_TYPES)

        AlertDialog.Builder(this)
            .setTitle("Add Relationship")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val toName = toNameEdit.text.toString().trim()
                if (toName.isBlank()) {
                    Toast.makeText(this, "Please enter a name.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val type = RELATIONSHIP_TYPES[typeSpinner.selectedItemPosition]
                val summary = summaryEdit.text.toString()
                relationships.add(Relationship(fromId, toName, type, summary))
                adapter.refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Fetches character previews from the global /characters collection.
     */
    private fun fetchParticipantPreviewsGlobal(
        ids: List<String>,
        onDone: (List<ParticipantPreview>) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()
        val previews = mutableListOf<ParticipantPreview>()
        var count = 0

        fun checkDone() {
            count++
            if (count == ids.size) {
                onDone(previews)
            }
        }

        if (ids.isEmpty()) {
            onDone(emptyList())
            return
        }

        // Move the log inside the forEach loop
        ids.forEach { id ->
            Log.d("Relationship", "Checking ID: $id in characters")
            db.collection("characters").document(id).get()
                .addOnSuccessListener { charDoc ->
                    if (charDoc.exists()) {
                        val name = charDoc.getString("name") ?: id
                        val avatarUri = charDoc.getString("avatarUri") ?: ""
                        previews.add(ParticipantPreview(id, name, avatarUri))
                    }
                    checkDone()
                }
                .addOnFailureListener { checkDone() }
        }
    }
}

