package com.albirich.RealmsAI

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.albirich.RealmsAI.models.ChatProfile
import com.albirich.RealmsAI.models.ParticipantPreview
import com.albirich.RealmsAI.models.Relationship
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson

class SessionRelationshipActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ParticipantRelationshipAdapter
    private val participants = mutableListOf<ParticipantPreview>()
    private val relationships = mutableListOf<Relationship>()
    companion object {
        const val REQUEST_EDIT_REL_LEVEL = 212
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_relationships)
        val fromScreen = intent.getStringExtra("SOURCE_SCREEN") ?: "CHAT_CREATION"

        val infoButtonChatRelationship: ImageButton = findViewById(R.id.infoButtonChatRelationship)
        infoButtonChatRelationship.setOnClickListener {
            AlertDialog.Builder(this@SessionRelationshipActivity)
                .setTitle("Relationships")
                .setMessage("When choosing a character for the relationship you enter a name, this will allow any character with that name to be recognized by the AI.")
                .setPositiveButton("OK", null)
                .show()
        }
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
                relationships, // Initial population
                onAddRelationshipClick = { fromId -> showAddRelationshipDialog(fromId) },
                onDeleteRelationship = { rel ->
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
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_relationship, null)
        val toNameEdit = dialogView.findViewById<EditText>(R.id.relationshipToNameEdit)
        val summaryEdit = dialogView.findViewById<EditText>(R.id.relationshipSummaryEdit)

        // --- NEW: Prevent the user from typing more than 150 characters! ---
        summaryEdit.filters = arrayOf(android.text.InputFilter.LengthFilter(150))

        // Grab the actual name of the character this relationship belongs to
        val fromCharacterName = participants.find { it.id == fromId }?.name ?: "this character"

        AlertDialog.Builder(this)
            // Title helps frame the sentence they are building!
            .setTitle("Relationship for $fromCharacterName")
            .setMessage("Format: [Name] is $fromCharacterName's [Description]")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val toName = toNameEdit.text.toString().trim()
                if (toName.isBlank()) {
                    Toast.makeText(this, "Please enter a name.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val summary = summaryEdit.text.toString().trim()
                if (summary.isBlank()) {
                    Toast.makeText(this, "Please enter a description.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // --- NEW: Safety check just in case they pasted massive text ---
                if (summary.length > 150) {
                    Toast.makeText(this, "Description cannot exceed 150 characters.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                // Create object. We pass an empty string for type so it doesn't break your existing data class.
                val newRel = Relationship(
                    fromId = fromId,
                    toName = toName,
                    type = "", // Ignored moving forward!
                    description = summary
                )

                // DIRECTLY UPDATE ADAPTER
                adapter.addRelationship(newRel)
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
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_EDIT_REL_LEVEL && resultCode == Activity.RESULT_OK && data != null) {
            val updatedJson = data.getStringExtra("UPDATED_RELATIONSHIP_JSON")
            val index = data.getIntExtra("REL_INDEX", -1)
            if (!updatedJson.isNullOrBlank() && index in relationships.indices) {
                val updatedRel = Gson().fromJson(updatedJson, Relationship::class.java)
                relationships[index] = updatedRel
                adapter.refresh()
            }
        }
    }
}
