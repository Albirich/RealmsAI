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
import com.example.RealmsAI.models.Relationship
import com.example.RealmsAI.models.RelationshipLevel
import com.google.gson.Gson

class RelationshipLevelEditorActivity : AppCompatActivity() {
    private lateinit var upTriggerEdit: EditText
    private lateinit var downTriggerEdit: EditText
    private lateinit var levelsRecycler: RecyclerView
    private lateinit var addLevelButton: Button
    private lateinit var currentLevelSpinner: Spinner
    private lateinit var saveButton: Button

    private val levels = mutableListOf<RelationshipLevel>()
    private var currentLevel = 0
    private lateinit var adapter: RelationshipLevelAdapter
    private lateinit var rel: Relationship

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_relationship_level_editor)

        upTriggerEdit = findViewById(R.id.upTriggerEdit)
        downTriggerEdit = findViewById(R.id.downTriggerEdit)
        levelsRecycler = findViewById(R.id.levelsRecycler)
        addLevelButton = findViewById(R.id.btnAddLevel)
        currentLevelSpinner = findViewById(R.id.currentLevelSpinner)
        saveButton = findViewById(R.id.btnSaveLevel)

        // Load relationship from intent
        val json = intent.getStringExtra("RELATIONSHIP_JSON")
        rel = Gson().fromJson(json, Relationship::class.java)
        upTriggerEdit.setText(rel.upTriggers ?: "")
        downTriggerEdit.setText(rel.downTriggers ?: "")
        levels.addAll(rel.levels)
        currentLevel = rel.currentLevel

        adapter = RelationshipLevelAdapter(levels) { index ->
            levels.removeAt(index)
            adapter.notifyDataSetChanged()
            updateCurrentLevelSpinner()
        }
        levelsRecycler.layoutManager = LinearLayoutManager(this)
        levelsRecycler.adapter = adapter

        addLevelButton.setOnClickListener {
            levels.add(RelationshipLevel(level = levels.size, threshold = 0, personality = ""))
            adapter.notifyDataSetChanged()
            updateCurrentLevelSpinner()
        }

        updateCurrentLevelSpinner()

        saveButton.setOnClickListener {
            rel.upTriggers = upTriggerEdit.text.toString()
            rel.downTriggers = downTriggerEdit.text.toString()
            rel.levels = levels
            rel.currentLevel = currentLevelSpinner.selectedItemPosition

            val data = Intent()
            data.putExtra("UPDATED_RELATIONSHIP_JSON", Gson().toJson(rel))
            data.putExtra("REL_INDEX", intent.getIntExtra("REL_INDEX", -1))
            setResult(RESULT_OK, data)
            finish()
        }
    }

    private fun updateCurrentLevelSpinner() {
        val levelsList = levels.map { "Level ${it.level}" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, levelsList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        currentLevelSpinner.adapter = adapter

        if (levels.isEmpty()) {
            currentLevelSpinner.isEnabled = false
            // Optionally set selection to -1 or 0 safely, or leave blank
            return
        }

        currentLevelSpinner.isEnabled = true
        currentLevelSpinner.setSelection(currentLevel.coerceIn(levels.indices))
    }

}