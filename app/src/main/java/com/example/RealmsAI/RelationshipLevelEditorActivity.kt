package com.example.RealmsAI

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.ModeSettings
import com.example.RealmsAI.models.ModeSettings.VNRelationship
import com.example.RealmsAI.models.ModeSettings.RelationshipLevel
import com.google.gson.Gson
import com.example.RealmsAI.FacilitatorResponseParser.updateRelationshipLevel

class RelationshipLevelEditorActivity : AppCompatActivity() {
    private lateinit var upTriggerEdit: EditText
    private lateinit var downTriggerEdit: EditText
    private lateinit var levelsRecycler: RecyclerView
    private lateinit var addLevelButton: Button
    private lateinit var startingPointsEdit: EditText
    private lateinit var saveButton: Button

    private val levels = mutableListOf<RelationshipLevel>()
    private var currentLevel = 0
    private lateinit var adapter: RelationshipLevelAdapter
    private lateinit var rel: VNRelationship

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_relationship_level_editor)

        upTriggerEdit = findViewById(R.id.upTriggerEdit)
        downTriggerEdit = findViewById(R.id.downTriggerEdit)
        levelsRecycler = findViewById(R.id.levelsRecycler)
        addLevelButton = findViewById(R.id.btnAddLevel)
        startingPointsEdit = findViewById(R.id.startingPointsEdit)
        saveButton = findViewById(R.id.btnSaveLevel)

        // Load relationship from intent
        val json = intent.getStringExtra("RELATIONSHIP_JSON")
        val fromId = intent.getStringExtra("FROM_ID") ?: ""
        val toId = intent.getStringExtra("TO_ID") ?: ""
        rel = if (json.isNullOrBlank()) VNRelationship(fromSlotKey = fromId, toSlotKey = toId)
        else Gson().fromJson(json, ModeSettings.VNRelationship::class.java)
        upTriggerEdit.setText(rel.upTriggers ?: "")
        downTriggerEdit.setText(rel.downTriggers ?: "")
        levels.addAll(rel.levels)
        currentLevel = rel.currentLevel

        adapter = RelationshipLevelAdapter(levels) { index ->
            levels.removeAt(index)
            adapter.notifyDataSetChanged()
        }
        levelsRecycler.layoutManager = LinearLayoutManager(this)
        levelsRecycler.adapter = adapter

        // Prefill "Starting points" from existing data
        val initialPoints = when {
            rel.points > 0 -> rel.points
            else -> rel.levels.firstOrNull { it.level == rel.currentLevel }?.threshold ?: 0
        }

        startingPointsEdit.setText(initialPoints.toString())
        // --- Add Level ---
        addLevelButton.setOnClickListener {
            val nextLevel = (levels.maxOfOrNull { it.level } ?: 0) + 1
            val targetKey = rel.toSlotKey   // 👈 ensure new level knows its target

            levels.add(
                RelationshipLevel(
                    level = nextLevel,
                    threshold = 0,
                    personality = "",
                    targetSlotKey = targetKey
                )
            )

            adapter.notifyDataSetChanged()
        }

        saveButton.setOnClickListener {
            // commit pending edits
            currentFocus?.clearFocus()
            levelsRecycler.clearFocus()
            upTriggerEdit.clearFocus()
            downTriggerEdit.clearFocus()

            rel.upTriggers = upTriggerEdit.text.toString()
            rel.downTriggers = downTriggerEdit.text.toString()
            rel.levels = levels

            val pointsText = startingPointsEdit.text.toString().toIntOrNull() ?: 0
            rel.points = pointsText
            updateRelationshipLevel(rel) // sets rel.currentLevel based on points and thresholds

            val data = Intent().apply {
                putExtra("UPDATED_RELATIONSHIP_JSON", Gson().toJson(rel))
                putExtra("REL_INDEX", intent.getIntExtra("REL_INDEX", -1))
            }
            setResult(RESULT_OK, data)
            finish()
        }
    }
}