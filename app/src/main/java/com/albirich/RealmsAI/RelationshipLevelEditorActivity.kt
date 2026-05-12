package com.albirich.RealmsAI

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.albirich.RealmsAI.models.ModeSettings
import com.albirich.RealmsAI.models.ModeSettings.VNRelationship
import com.albirich.RealmsAI.models.ModeSettings.RelationshipLevel
import com.google.gson.Gson
import com.albirich.RealmsAI.FacilitatorResponseParser.updateRelationshipLevel

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


        upTriggerEdit.filters = arrayOf(android.text.InputFilter.LengthFilter(100))
        downTriggerEdit.filters = arrayOf(android.text.InputFilter.LengthFilter(100))

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

        val infoButtonChatGameMode: ImageButton = findViewById(R.id.infoButtonRelationshipLevel)
        infoButtonChatGameMode.setOnClickListener {
            val messageHtml = "This will change their personality based on how much XP the character has with another character. These are character specific so you can have different triggers and personality changes per character.\n" +
                "\n <b>Up/Down Triggers</b> - They are checked when the character responds, it checks the recent chat history and sees if a character meets any of their own triggers, if so they add or subtract XP.\n" +
                "\n <b>Relationship level</b> - You can put as many as you want only one will be active at a time\n" +
                "\n <b>Threshold</b> - This is the MINIMUM XP needed to achieve this level. (ex: LVL 2 with a threshold of 10 will become level 2 the moment they reach 10 XP) Doesn't have to be equal.\n" +
                "\n <b>Personality</b> - This gets added to their personality, with weight on this description if it conflicts with their character personality descriptions.\n" +
                "\n <b>Set Starting Points</b> - If you want to start at a higher level, that way you can go below neutral (ex: If you want level 4 to be your starting points as a neutral outlook, you can have levels 1-3 be negative outlook)"

            AlertDialog.Builder(this@RelationshipLevelEditorActivity)
                .setTitle("Relationship Levels")
                .setMessage(HtmlCompat.fromHtml(messageHtml, HtmlCompat.FROM_HTML_MODE_LEGACY))
                .setPositiveButton("OK", null)
                .show()
        }

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


            levelsRecycler.scrollToPosition(levels.size - 1)
            adapter.notifyDataSetChanged()
        }

        saveButton.setOnClickListener {
            // commit pending edits
            currentFocus?.clearFocus()
            levelsRecycler.clearFocus()
            upTriggerEdit.clearFocus()
            downTriggerEdit.clearFocus()
            val upText = upTriggerEdit.text.toString().trim()
            val downText = downTriggerEdit.text.toString().trim()

            if (upText.length > 100) return@setOnClickListener toast("Up Trigger too long (Max 100)")
            if (downText.length > 100) return@setOnClickListener toast("Down Trigger too long (Max 100)")

            // Check if any level in the adapter list is too long
            if (levels.any { it.personality.length > 200 }) {
                Toast.makeText(this, "A level personality is too long (Max 200)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            rel.upTriggers = upText
            rel.downTriggers = downText
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

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}