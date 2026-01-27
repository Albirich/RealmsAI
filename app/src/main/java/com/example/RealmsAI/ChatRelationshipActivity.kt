package com.example.RealmsAI

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.CharacterRelationshipActivity
import com.example.RealmsAI.models.ChatProfile
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

    companion object {
        const val REQUEST_EDIT_REL_LEVEL = 212
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_relationships)

        val infoButtonChatRelationship: ImageButton = findViewById(R.id.infoButtonChatRelationship)
        infoButtonChatRelationship.setOnClickListener {
            AlertDialog.Builder(this@ChatRelationshipActivity)
                .setTitle("Relationships")
                .setMessage("When choosing a character for the relationship you enter a name, this will allow any character with that name to be recognized by the AI.\n" +
                        "use character#, # being the position in the character list you want to refer to. if the character in that position is replaced the name will be replaced.")
                .setPositiveButton("OK", null)
                .show()
        }



        recyclerView = findViewById(R.id.relationshipRecycler)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Get participant IDs
        val participantsJson = intent.getStringExtra("PARTICIPANTS_JSON") ?: "[]"
        participants.clear()
        participants.addAll(Gson().fromJson(participantsJson, Array<ParticipantPreview>::class.java).toList())

        // See if this is an edit (if so, only use chat relationships)
        val chatProfileJson = intent.getStringExtra("CHAT_PROFILE_JSON") ?: ""
        val relationshipsJson = intent.getStringExtra("RELATIONSHIPS_JSON") ?: "[]"
        relationships.clear()

        if (chatProfileJson.isNotBlank()) {
            // If editing, load from chatProfile and ignore passed relationships
            val chatProfile = Gson().fromJson(chatProfileJson, ChatProfile::class.java)
            relationships.addAll(chatProfile.relationships)
            Log.d("Relationships", "Loaded relationships from ChatProfile (${relationships.size})")
        } else {
            // New chat, use whatever was passed (likely from selected characters or session edits)
            relationships.addAll(
                Gson().fromJson(relationshipsJson, Array<Relationship>::class.java).toList()
            )
            Log.d("Relationships", "Loaded relationships from RELATIONSHIPS_JSON (${relationships.size})")
        }

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
        // Note: We removed the 'targets' check because users type the name manually,
        // so they don't strictly need other participants in the list to create a relationship.

        val dialogView = layoutInflater.inflate(R.layout.dialog_add_relationship, null)
        val toNameEdit = dialogView.findViewById<EditText>(R.id.relationshipToNameEdit)
        val typeSpinner = dialogView.findViewById<Spinner>(R.id.relationshipTypeSpinner)
        val summaryEdit = dialogView.findViewById<EditText>(R.id.relationshipSummaryEdit)

        // Ensure types exist
        val types = RELATIONSHIP_TYPES.ifEmpty { listOf("Friend", "Enemy", "Neutral", "Family", "Romantic") }
        typeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)

        AlertDialog.Builder(this)
            .setTitle("Add Relationship")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val toName = toNameEdit.text.toString().trim()
                if (toName.isBlank()) {
                    Toast.makeText(this, "Please enter a name.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val type = if (typeSpinner.adapter.count > 0) types[typeSpinner.selectedItemPosition] else "Neutral"
                val summary = summaryEdit.text.toString().trim()

                // Create object
                val newRel = Relationship(
                    fromId = fromId,
                    toName = toName,
                    type = type,
                    description = summary // Ensure your class uses 'description', 'summary', or whatever field matches
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

        ids.forEach { id ->
            if (!id.isNullOrEmpty()) {
                db.collection("characters").document(id).get()
                    .addOnSuccessListener { charDoc ->
                        if (charDoc.exists()) {
                            val name = charDoc.getString("name") ?: id
                            val avatarUri = charDoc.getString("avatarUri") ?: ""
                            previews.add(ParticipantPreview(id, name, avatarUri))
                            checkDone()
                        } else {
                            // If you want persona support, add here like the other activity
                            checkDone()
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
