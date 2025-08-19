package com.example.RealmsAI

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.CharacterProfile
import com.example.RealmsAI.models.ModeSettings
import com.example.RealmsAI.models.ModeSettings.VNRelationship
import com.example.RealmsAI.models.ModeSettings.VNSettings
import com.google.gson.Gson
import kotlin.jvm.java

class VNSettingsActivity : AppCompatActivity() {
    private lateinit var monogamyCheck: CheckBox
    private lateinit var monogamyLevel: EditText
    private lateinit var jealousyCheck: CheckBox
    private lateinit var mainCharModeCheck: CheckBox
    private lateinit var mainCharSpinner: Spinner
    private lateinit var relationshipBoardList: RecyclerView
    private lateinit var saveButton: Button

    private lateinit var characterNames: List<String>
    private lateinit var characterIds: List<String>
    private lateinit var selectedCharacters: List<CharacterProfile>
    private lateinit var vnSettings: VNSettings
    private val EDIT_RELATIONSHIP_CODE = 501

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vn_settings)

        monogamyCheck = findViewById(R.id.monogamyCheck)
        monogamyLevel = findViewById(R.id.monogamyLevel)
        jealousyCheck = findViewById(R.id.jealousyCheck)
        mainCharModeCheck = findViewById(R.id.mainCharModeCheck)
        mainCharSpinner = findViewById(R.id.mainCharSpinner)
        relationshipBoardList = findViewById(R.id.relationshipList) // Make sure your XML uses this id!
        saveButton = findViewById(R.id.saveButton)

        // Load data from intent
        val charactersJson = intent.getStringExtra("SELECTED_CHARACTERS_JSON")
        selectedCharacters = Gson().fromJson(charactersJson, Array<CharacterProfile>::class.java).toList()
        characterNames = selectedCharacters.map { it.name }
        characterIds = selectedCharacters.map { it.id }

        val currentSettingsJson = intent.getStringExtra("CURRENT_SETTINGS_JSON")
        vnSettings = if (currentSettingsJson.isNullOrBlank()) VNSettings()
        else Gson().fromJson(currentSettingsJson, VNSettings::class.java)

        // Restore toggles/fields
        monogamyCheck.isChecked = vnSettings.monogamyEnabled
        monogamyLevel.setText(vnSettings.monogamyLevel?.toString() ?: "")
        jealousyCheck.isChecked = vnSettings.jealousyEnabled
        mainCharModeCheck.isChecked = vnSettings.mainCharMode
        mainCharSpinner.visibility = if (vnSettings.mainCharMode) View.VISIBLE else View.GONE

        // Spinner for Main Character selection
        mainCharSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, characterNames)
        if (vnSettings.mainCharId != null) {
            val idx = characterIds.indexOf(vnSettings.mainCharId)
            if (idx >= 0) mainCharSpinner.setSelection(idx)
        }

        // Show/hide UI fields based on toggles
        monogamyLevel.visibility = if (monogamyCheck.isChecked) View.VISIBLE else View.GONE
        mainCharSpinner.visibility = if (mainCharModeCheck.isChecked) View.VISIBLE else View.GONE

        monogamyCheck.setOnCheckedChangeListener { _, isChecked ->
            monogamyLevel.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        mainCharModeCheck.setOnCheckedChangeListener { _, isChecked ->
            mainCharSpinner.visibility = if (isChecked) View.VISIBLE else View.GONE
            vnSettings.mainCharMode = isChecked
            setupRelationshipBoards(isChecked)
        }

        // Main Char Mode Spinner: Update relationships on selection change
        mainCharSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (mainCharModeCheck.isChecked) {
                    vnSettings.mainCharId = characterIds[position]
                    setupRelationshipBoards(true)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Initial relationship board setup
        setupRelationshipBoards(mainCharModeCheck.isChecked)
        val infoButtonMonogamy: ImageButton = findViewById(R.id.infoButtonMonogamy)
        infoButtonMonogamy.setOnClickListener {
            AlertDialog.Builder(this@VNSettingsActivity)
                .setTitle("Monogamy Mode")
                .setMessage("This let's you set a level threshold. Only 1 relationship for each character can go above that level at a time. All other relationship levels will stop increasing at the threshold")
                .setPositiveButton("OK", null)
                .show()
        }
        val infoButtonJealousy: ImageButton = findViewById(R.id.infoButtonJealousy)
        infoButtonJealousy.setOnClickListener {
            AlertDialog.Builder(this@VNSettingsActivity)
                .setTitle("Jealousy")
                .setMessage("When a character gains relationship points with another character, all other characters in that location lose 1 relationship point.")
                .setPositiveButton("OK", null)
                .show()
        }
        val infoButtonMainCharacter: ImageButton = findViewById(R.id.infoButtonMainCharacter)
        infoButtonMainCharacter.setOnClickListener {
            AlertDialog.Builder(this@VNSettingsActivity)
                .setTitle("Main Character Mode")
                .setMessage("Choose a character and all other characters only have relationship levels with them. ")
                .setPositiveButton("OK", null)
                .show()
        }

        // Save logic
        saveButton.setOnClickListener {
            vnSettings.monogamyEnabled = monogamyCheck.isChecked
            vnSettings.monogamyLevel = monogamyLevel.text.toString().toIntOrNull()
            vnSettings.jealousyEnabled = jealousyCheck.isChecked
            vnSettings.mainCharId = if (mainCharModeCheck.isChecked) {
                val idx = mainCharSpinner.selectedItemPosition
                if (idx >= 0) characterIds[idx] else null
            } else null
            vnSettings.mainCharMode = mainCharModeCheck.isChecked

            // Collect any final changes from the adapters, if needed (adapters are editing vnSettings.characterBoards in place)

            val resultIntent = Intent()
            resultIntent.putExtra("VN_SETTINGS_JSON", Gson().toJson(vnSettings))
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun slotKeyFor(profile: CharacterProfile, list: List<CharacterProfile>): String {
        val idx = list.indexOfFirst { it.id == profile.id }
        return ModeSettings.SlotKeys.fromPosition(idx)
    }

    private fun setupRelationshipBoards(mainCharMode: Boolean) {
        relationshipBoardList.visibility = View.VISIBLE

        if (mainCharMode) {
            val mcId = vnSettings.mainCharId ?: characterIds.first()
            val mainChar = selectedCharacters.first { it.id == mcId }
            val otherChars = selectedCharacters.filter { it.id != mcId }

            val mainKey  = slotKeyFor(mainChar, selectedCharacters)

            relationshipBoardList.layoutManager =
                LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

            // Adapter specifically for "others -> main"
            relationshipBoardList.adapter = VNRelAvatarAdapter_OthersToMain(
                otherCharacters = otherChars,
                selectedCharacters = selectedCharacters,
                mainSlotKey = mainKey,
                characterBoards = vnSettings.characterBoards
            ) { rel, otherChar ->
                // rel.fromSlotKey == otherKey, rel.toSlotKey == mainKey
                launchRelationshipEditor(rel, otherChar, mainChar)
            }

        } else {
            relationshipBoardList.layoutManager = LinearLayoutManager(this)

            // FIX: pass selectedCharacters as the 2nd constructor arg
            relationshipBoardList.adapter = VNRelationshipBoardAdapter(
                characters = selectedCharacters,
                selectedCharacters = selectedCharacters,
                characterBoardsBySlot = vnSettings.characterBoards
            ) { rel, fromChar, toChar ->
                // Ensure the relationship is slot-keyed (safety net; usually already set)
                val fromKey = slotKeyFor(fromChar, selectedCharacters)
                val toKey   = slotKeyFor(toChar, selectedCharacters)
                if (rel.fromSlotKey.isBlank() || rel.toSlotKey.isBlank()) {
                    val fixed = rel.copy(fromSlotKey = fromKey, toSlotKey = toKey)
                    vnSettings.characterBoards
                        .getOrPut(fromKey) { mutableMapOf() }[toKey] = fixed
                }
                launchRelationshipEditor(rel, fromChar, toChar)
            }
        }
    }

    private fun launchRelationshipEditor(
        rel: VNRelationship,
        fromChar: CharacterProfile,
        toChar: CharacterProfile
    ) {
        val intent = Intent(this, RelationshipLevelEditorActivity::class.java)
        intent.putExtra("RELATIONSHIP_JSON", Gson().toJson(rel))
        intent.putExtra("FROM_ID", rel.fromSlotKey)
        intent.putExtra("TO_ID", rel.toSlotKey)
        intent.putExtra("FROM_NAME", fromChar.name)
        intent.putExtra("TO_NAME", toChar.name)
        startActivityForResult(intent, EDIT_RELATIONSHIP_CODE)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == EDIT_RELATIONSHIP_CODE && resultCode == RESULT_OK && data != null) {
            val updatedJson = data.getStringExtra("UPDATED_RELATIONSHIP_JSON")
            val updatedRel = Gson().fromJson(updatedJson, VNRelationship::class.java)
            // Save to the correct board
            val relBoard = vnSettings.characterBoards.getOrPut(updatedRel.fromSlotKey) { mutableMapOf() }
            relBoard[updatedRel.toSlotKey] = updatedRel
            setupRelationshipBoards(mainCharModeCheck.isChecked)
        }
    }
}
