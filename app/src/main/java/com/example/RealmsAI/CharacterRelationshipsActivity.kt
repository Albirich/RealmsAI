package com.example.RealmsAI

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.RELATIONSHIP_TYPES
import com.example.RealmsAI.models.Relationship
import com.example.RealmsAI.RelationshipLevelEditorActivity
import com.google.gson.Gson
import kotlin.jvm.java

class CharacterRelationshipActivity : AppCompatActivity() {

    private val relationships = mutableListOf<Relationship>()
    private lateinit var adapter: SimpleRelationshipAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_character_relationships)

        // Set up RecyclerView
        val recyclerView = findViewById<RecyclerView>(R.id.relationshipRecycler)
        adapter = SimpleRelationshipAdapter(
            relationships,
            onDelete = { rel ->
                relationships.remove(rel)
                adapter.notifyDataSetChanged()
            },
            onEditLevel = { rel, index ->
                val intent = Intent(this, RelationshipLevelEditorActivity::class.java)
                intent.putExtra("RELATIONSHIP_JSON", Gson().toJson(rel))
                intent.putExtra("REL_INDEX", index)
                startActivityForResult(intent, REQUEST_EDIT_RELATIONSHIP_LEVEL)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Load incoming relationships (if editing an existing character)
        intent.getStringExtra("RELATIONSHIPS_JSON")?.let { json ->
            if (json.isNotBlank()) {
                relationships.clear()
                relationships.addAll(
                    Gson().fromJson(json, Array<Relationship>::class.java).toList()
                )
                adapter.notifyDataSetChanged()
            }
        }

        // Add Relationship Button
        findViewById<Button>(R.id.btnAddCharRelationship).setOnClickListener {
            showAddRelationshipDialog()
        }

        // Done Button
        findViewById<Button>(R.id.btnDone).setOnClickListener {
            // Return to caller with relationships as JSON
            val data = Intent().apply {
                putExtra("RELATIONSHIPS_JSON", Gson().toJson(relationships))
            }
            setResult(Activity.RESULT_OK, data)
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_EDIT_RELATIONSHIP_LEVEL && resultCode == Activity.RESULT_OK) {
            val updatedJson = data?.getStringExtra("UPDATED_RELATIONSHIP_JSON")
            val index = data?.getIntExtra("REL_INDEX", -1) ?: -1

            if (!updatedJson.isNullOrBlank() && index in relationships.indices) {
                val updatedRel = Gson().fromJson(updatedJson, Relationship::class.java)
                relationships[index] = updatedRel
                adapter.notifyItemChanged(index)
            }
        }
    }

    companion object {
        const val REQUEST_EDIT_RELATIONSHIP_LEVEL = 211
    }

    private fun showAddRelationshipDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_relationship, null)
        val toNameEdit = dialogView.findViewById<android.widget.EditText>(R.id.relationshipToNameEdit)
        val typeSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.relationshipTypeSpinner)
        val summaryEdit = dialogView.findViewById<android.widget.EditText>(R.id.relationshipSummaryEdit)

        typeSpinner.adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            RELATIONSHIP_TYPES
        )

        AlertDialog.Builder(this)
            .setTitle("Add Relationship")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val toName = toNameEdit.text.toString().trim()
                val type = RELATIONSHIP_TYPES[typeSpinner.selectedItemPosition]
                val summary = summaryEdit.text.toString()
                if (toName.isNotEmpty()) {
                    relationships.add(
                        Relationship(fromId = "", toName = toName, type = type, description = summary)
                    )
                    adapter.notifyDataSetChanged()
                } else {
                    Toast.makeText(this, "Please enter a name.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
