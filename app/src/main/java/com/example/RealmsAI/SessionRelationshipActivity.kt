package com.example.RealmsAI

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
import com.example.RealmsAI.models.ChatProfile
import com.example.RealmsAI.models.ParticipantPreview
import com.example.RealmsAI.models.RELATIONSHIP_TYPES
import com.example.RealmsAI.models.Relationship
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson

class SessionRelationshipActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ParticipantRelationshipAdapter
    private val participants = mutableListOf<ParticipantPreview>()
    private val relationships = mutableListOf<Relationship>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_relationships)
        val fromScreen = intent.getStringExtra("SOURCE_SCREEN") ?: "CHAT_CREATION"

        recyclerView = findViewById(R.id.relationshipRecycler)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Get participant character IDs from intent (could be personas/characters/bots)
        val ids = intent.getStringArrayListExtra("PARTICIPANT_IDS") ?: emptyList()
        Log.d("Relationships", "IDs from intent: $ids")

        // Robustly load relationships: if chat profile present, load only those, else use whatever was passed in
        val chatProfileJson = intent.getStringExtra("CHAT_PROFILE_JSON") ?: ""
        val relationshipsJson = intent.getStringExtra("RELATIONSHIPS_JSON") ?: "[]"
        relationships.clear()

        if (chatProfileJson.isNotBlank()) {
            // Use chat profile's relationships only (ignore what was passed in)
            val chatProfile = Gson().fromJson(chatProfileJson, ChatProfile::class.java)
            relationships.addAll(chatProfile.relationships)
            Log.d("Relationships", "Loaded relationships from ChatProfile (${relationships.size})")
        } else {
            // Use provided relationships (likely from character, or previously edited)
            relationships.addAll(
                Gson().fromJson(relationshipsJson, Array<Relationship>::class.java).toList()
            )
            Log.d("Relationships", "Loaded relationships from RELATIONSHIPS_JSON (${relationships.size})")
        }

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
            setResult(Activity.RESULT_OK, data)
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

        typeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, RELATIONSHIP_TYPES)

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
     * Fetches character/persona previews from the global collections.
     */
    private fun fetchParticipantPreviewsGlobal(ids: List<String>, onDone: (List<ParticipantPreview>) -> Unit) {
        Log.d("REL_DEBUG", "fetchParticipantPreviewsGlobal IDs: $ids")
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
            Log.d("REL_DEBUG", "No IDs provided to fetchParticipantPreviewsGlobal!")
            onDone(emptyList())
            return
        }

        ids.forEach { id ->
            if (!id.isNullOrEmpty()) {
                db.collection("characters").document(id).get()
                    .addOnSuccessListener { charDoc ->
                        if (charDoc.exists()) {
                            val name = charDoc.getString("name") ?: id
                            val avatarUri = charDoc.getString("avatarUri") ?: ""
                            previews.add(ParticipantPreview(id, name, avatarUri))
                            checkDone()
                            Log.d("REL_DEBUG", "Added preview: ${name} / $id")

                        } else {
                            db.collection("personas").document(id).get()
                                .addOnSuccessListener { personaDoc ->
                                    if (personaDoc.exists()) {
                                        val name = personaDoc.getString("name") ?: id
                                        val avatarUri = personaDoc.getString("avatarUri") ?: ""
                                        previews.add(ParticipantPreview(id, name, avatarUri))
                                    }
                                    checkDone()
                                }
                                .addOnFailureListener { checkDone() }
                        }
                    }
                    .addOnFailureListener { checkDone() }
            } else {
                checkDone()
            }
        }
    }
}
