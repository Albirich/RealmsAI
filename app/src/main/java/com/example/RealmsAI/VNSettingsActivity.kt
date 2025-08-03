package com.example.RealmsAI

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.ModeSettings.VNSettings
import com.example.RealmsAI.models.Relationship
import com.example.RealmsAI.models.SessionProfile
import com.google.gson.Gson
import kotlin.jvm.java

class VNSettingsActivity : AppCompatActivity() {
    private lateinit var monogamyCheck: CheckBox
    private lateinit var monogamyLevel: EditText
    private lateinit var jealousyCheck: CheckBox
    private lateinit var mainCharModeCheck: CheckBox
    private lateinit var mainCharSpinner: Spinner
    private lateinit var relationshipList: RecyclerView
    private lateinit var saveButton: Button

    // Assume you pass the sessionProfile via intent or singleton
    private lateinit var sessionProfile: SessionProfile
    private var vnSettings: VNSettings? = null
    private var characterNames = listOf<String>()
    private var characterIds = listOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vn_settings)

        monogamyCheck = findViewById(R.id.monogamyCheck)
        monogamyLevel = findViewById(R.id.monogamyLevel)
        jealousyCheck = findViewById(R.id.jealousyCheck)
        mainCharModeCheck = findViewById(R.id.mainCharModeCheck)
        mainCharSpinner = findViewById(R.id.mainCharSpinner)
        relationshipList = findViewById(R.id.relationshipList)
        saveButton = findViewById(R.id.saveButton)


        val profileJson = intent.getStringExtra("SESSION_PROFILE_JSON")
        val vnSettingsJson = intent.getStringExtra("VN_SETTINGS_JSON")

        sessionProfile = Gson().fromJson(profileJson, SessionProfile::class.java)
        vnSettings = if (!vnSettingsJson.isNullOrBlank())
            Gson().fromJson(vnSettingsJson, VNSettings::class.java)
        else null
            characterNames = sessionProfile.slotRoster.map { it.name }
        characterIds = sessionProfile.slotRoster.map { it.slotId }

        // Monogamy Level only visible if checked
        monogamyCheck.setOnCheckedChangeListener { _, isChecked ->
            monogamyLevel.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // Main Character Mode options show up if checked
        mainCharModeCheck.setOnCheckedChangeListener { _, isChecked ->
            mainCharSpinner.visibility = if (isChecked) View.VISIBLE else View.GONE
            relationshipList.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // Main Character Spinner
        mainCharSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, characterNames)
        mainCharSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val mainCharId = characterIds[position]
                val otherChars = sessionProfile.slotRoster.filter { it.slotId != mainCharId }
                relationshipList.layoutManager = LinearLayoutManager(this@VNSettingsActivity)
                relationshipList.adapter = VNRelationshipAdapter(otherChars, mainCharId)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        saveButton.setOnClickListener {
            val monogamyEnabled = monogamyCheck.isChecked
            val monogamyLevelVal = monogamyLevel.text.toString().toIntOrNull()
            val jealousyEnabled = jealousyCheck.isChecked
            val mainCharMode = mainCharModeCheck.isChecked
            val mainCharId = if (mainCharMode) {
                val idx = mainCharSpinner.selectedItemPosition
                if (idx >= 0) characterIds[idx] else null
            } else null

            // Collect relationships from adapter if in mainCharMode
            val relationships = if (mainCharMode) {
                (relationshipList.adapter as? VNRelationshipAdapter)?.getRelationships() ?: emptyList()
            } else emptyList()

            // Save to profile/session
            val settings = VNSettings(
                monogamyEnabled = monogamyEnabled,
                monogamyLevel = monogamyLevelVal,
                jealousyEnabled = jealousyEnabled,
                mainCharMode = mainCharMode,
                mainCharId = mainCharId,
                relationships = relationships
            )
            // TODO: Save settings to sessionProfile/modeSettings (e.g. sessionProfile.modeSettings["vn"] = settings)
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == RESULT_OK && data != null) {
            val updatedJson = data.getStringExtra("UPDATED_RELATIONSHIP_JSON")
            val relIndex = data.getIntExtra("REL_INDEX", -1)
            val updatedRelationship = Gson().fromJson(updatedJson, Relationship::class.java)

        }
    }

}
