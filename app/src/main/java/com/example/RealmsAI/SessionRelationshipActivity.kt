package com.example.RealmsAI

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
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





        recyclerView = findViewById(R.id.relationshipRecycler)
        adapter = ParticipantRelationshipAdapter(participants, relationships,
            onAddRelationship = { fromId ->
                showAddRelationshipDialog(fromId)
            },
            onDeleteRelationship = { rel ->
                relationships.remove(rel)
                adapter.notifyDataSetChanged()
            }
        )
        recyclerView.adapter = adapter

        val ids = intent.getStringArrayListExtra("PARTICIPANT_IDS") ?: emptyList()
        fetchParticipantPreviews(ids) { loadedPreviews ->
            adapter = ParticipantRelationshipAdapter(
                participants,
                relationships,
                onAddRelationship = { fromId ->
                    showAddRelationshipDialog(fromId)
                },
                onDeleteRelationship = { rel ->
                    relationships.remove(rel)
                    adapter.refresh()
                }
            )

            recyclerView.adapter = adapter
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
            Toast.makeText(this, "No valid targets!", Toast.LENGTH_SHORT).show(); return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_add_relationship, null)
        val toSpinner = dialogView.findViewById<Spinner>(R.id.relationshipToSpinner)
        val typeSpinner = dialogView.findViewById<Spinner>(R.id.relationshipTypeSpinner)
        val summaryEdit = dialogView.findViewById<EditText>(R.id.relationshipSummaryEdit)

        toSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, targets.map { it.name })
        typeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, RELATIONSHIP_TYPES)

        AlertDialog.Builder(this)
            .setTitle("Add Relationship")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val toId = targets[toSpinner.selectedItemPosition].id
                val type = RELATIONSHIP_TYPES[typeSpinner.selectedItemPosition]
                val summary = summaryEdit.text.toString()
                relationships.add(Relationship(fromId, toId, type, summary))
                adapter.refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun fetchParticipantPreviews(ids: List<String>, onDone: (List<ParticipantPreview>) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val previews = mutableListOf<ParticipantPreview>()
        var count = 0
        ids.forEach { id ->
            db.collection("characters").document(id).get()
                .addOnSuccessListener { doc ->
                    val name = doc.getString("name") ?: id
                    val avatarUri = doc.getString("avatarUri") ?: ""
                    previews.add(ParticipantPreview(id, name, avatarUri))
                    count++
                    if (count == ids.size) {
                        onDone(previews)
                    }
                }
                .addOnFailureListener {
                    previews.add(ParticipantPreview(id, id, ""))
                    count++
                    if (count == ids.size) {
                        onDone(previews)
                    }
                }
        }
    }

}
