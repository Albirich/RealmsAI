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

        // Load data from intent
        val charactersJson = intent.getStringExtra("SELECTED_CHARACTERS_JSON")
        selectedCharacters = Gson().fromJson(charactersJson, Array<CharacterProfile>::class.java).toList()
        characterNames = selectedCharacters.map { it.name }
        characterIds = selectedCharacters.map { it.id }

        val currentSettingsJson = intent.getStringExtra("CURRENT_SETTINGS_JSON")
        vnSettings = if (currentSettingsJson.isNullOrBlank()) VNSettings()
        else Gson().fromJson(currentSettingsJson, VNSettings::class.java)

        normalizeMainCharIdFromPlaceholder()
        healVnSettingsForRoster()

        monogamyCheck = findViewById(R.id.monogamyCheck)
        monogamyLevel = findViewById(R.id.monogamyLevel)
        jealousyCheck = findViewById(R.id.jealousyCheck)
        mainCharModeCheck = findViewById(R.id.mainCharModeCheck)
        mainCharSpinner = findViewById(R.id.mainCharSpinner)
        relationshipBoardList = findViewById(R.id.relationshipList) // Make sure your XML uses this id!
        saveButton = findViewById(R.id.saveButton)

        android.util.Log.d("VNSettings",
            "mainCharId=${vnSettings.mainCharId} roster=${characterIds.joinToString()}")

        mainCharSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            characterNames
        )

        val selIdx = vnSettings.mainCharId?.let { characterIds.indexOf(it) } ?: -1
        if (selIdx >= 0) mainCharSpinner.setSelection(selIdx)


        // now render
        setupRelationshipBoards(mainCharModeCheck.isChecked)

        // set selection safely
        val idx = vnSettings.mainCharId?.let { characterIds.indexOf(it) } ?: -1
        if (idx >= 0) mainCharSpinner.setSelection(idx)

        // Restore toggles/fields
        monogamyCheck.isChecked = vnSettings.monogamyEnabled
        monogamyLevel.setText(vnSettings.monogamyLevel?.toString() ?: "")
        jealousyCheck.isChecked = vnSettings.jealousyEnabled
        mainCharModeCheck.isChecked = vnSettings.mainCharMode
        mainCharSpinner.visibility = if (vnSettings.mainCharMode) View.VISIBLE else View.GONE

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
        val infoButtonvnSettings: ImageButton = findViewById(R.id.infoButtonvnsettings)
        infoButtonvnSettings.setOnClickListener {
            AlertDialog.Builder(this@VNSettingsActivity)
                .setTitle("Main Character Mode")
                .setMessage("Click on a character to open the relationship levels.\n Sets how the character acts with and around other characters.")
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
        val idx = list.indexOfFirst { it.id == profile.id }.let { if (it < 0) 0 else it }
        return ModeSettings.SlotKeys.fromPosition(idx)
    }

    private fun setupRelationshipBoards(mainCharMode: Boolean) {
        if (selectedCharacters.isEmpty()) {
            relationshipBoardList.visibility = View.GONE
            return
        }
        relationshipBoardList.visibility = View.VISIBLE

        if (mainCharMode) {
            val mcId = vnSettings.mainCharId
                ?.takeIf { id -> selectedCharacters.any { it.id == id } }
                ?: selectedCharacters.first().id
            if (vnSettings.mainCharId != mcId) vnSettings.mainCharId = mcId

            val mainChar  = selectedCharacters.first { it.id == mcId }
            val otherChars = selectedCharacters.filter { it.id != mcId }
            val mainKey   = slotKeyFor(mainChar, selectedCharacters)

            relationshipBoardList.layoutManager =
                LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

            relationshipBoardList.adapter = VNRelAvatarAdapter_OthersToMain(
                otherCharacters = otherChars,
                selectedCharacters = selectedCharacters,
                mainSlotKey = mainKey,
                characterBoards = vnSettings.characterBoards
            ) { rel, otherChar ->
                val otherKey = slotKeyFor(otherChar, selectedCharacters)
                val fixed = if (rel.fromSlotKey != otherKey || rel.toSlotKey != mainKey)
                    rel.copy(fromSlotKey = otherKey, toSlotKey = mainKey) else rel
                vnSettings.characterBoards.getOrPut(otherKey) { mutableMapOf() }[mainKey] = fixed
                launchRelationshipEditor(fixed, otherChar, mainChar)
            }
        } else {
            relationshipBoardList.layoutManager = LinearLayoutManager(this)
            relationshipBoardList.adapter = VNRelationshipBoardAdapter(
                characters = selectedCharacters,
                selectedCharacters = selectedCharacters,
                characterBoardsBySlot = vnSettings.characterBoards
            ) { rel, fromChar, toChar ->
                val fromKey = slotKeyFor(fromChar, selectedCharacters)
                val toKey   = slotKeyFor(toChar, selectedCharacters)
                if (rel.fromSlotKey.isBlank() || rel.toSlotKey.isBlank()) {
                    val fixed = rel.copy(fromSlotKey = fromKey, toSlotKey = toKey)
                    vnSettings.characterBoards.getOrPut(fromKey) { mutableMapOf() }[toKey] = fixed
                }
                launchRelationshipEditor(rel, fromChar, toChar)
            }
        }
    }

    private fun healVnSettingsForRoster() {
        if (selectedCharacters.isEmpty()) return

        // Valid slot keys for current roster size: character1..characterN
        val validKeys = ModeSettings.SlotKeys.ALL.take(selectedCharacters.size).toSet()

        // Compute (and persist) a valid main char id/key if in main-char mode
        val mainKey: String? = if (vnSettings.mainCharMode) {
            val mcId = vnSettings.mainCharId
                ?.takeIf { id -> selectedCharacters.any { it.id == id } }
                ?: selectedCharacters.first().id
            if (vnSettings.mainCharId != mcId) vnSettings.mainCharId = mcId
            val mainChar = selectedCharacters.first { it.id == mcId }
            slotKeyFor(mainChar, selectedCharacters)
        } else null

        val newBoards = mutableMapOf<String, MutableMap<String, ModeSettings.VNRelationship>>()

        vnSettings.characterBoards.forEach { (fromKey, inner) ->
            // Drop rows from non-existent slots (e.g., character4 when only 3 exist)
            if (fromKey !in validKeys) return@forEach

            val toMap = newBoards.getOrPut(fromKey) { mutableMapOf() }

            inner.forEach { (toKey, rel) ->
                // If 'to' slot is invalid, in main-char mode route to main; else skip
                val mappedTo: String = when {
                    toKey in validKeys -> toKey
                    mainKey != null    -> mainKey
                    else               -> return@forEach
                }

                // Fix relationship keys and level.targetSlotKey
                val fixedLevels = rel.levels.map {
                    if (it.targetSlotKey != mappedTo) it.copy(targetSlotKey = mappedTo) else it
                }

                val fixed = rel.copy(
                    fromSlotKey = fromKey,
                    toSlotKey = mappedTo,
                    levels = fixedLevels.toMutableList()
                )

                toMap[mappedTo] = fixed
            }

            // Ensure a (from -> main) entry exists in main-char mode
            if (mainKey != null && mainKey !in toMap) {
                toMap[mainKey] = ModeSettings.VNRelationship(
                    fromSlotKey = fromKey,
                    toSlotKey = mainKey,
                    levels = mutableListOf(),
                    upTriggers = "",
                    downTriggers = "",
                    points = 0,
                    notes = "",
                    currentLevel = 0
                )
            }
        }

        vnSettings.characterBoards = newBoards
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

    private fun normalizeMainCharIdFromPlaceholder() {
        if (!vnSettings.mainCharMode) return

        // If no roster, there's nothing we can select.
        if (selectedCharacters.isEmpty()) {
            vnSettings.mainCharId = null
            return
        }

        val rosterIds = characterIds.toSet()
        val saved = vnSettings.mainCharId

        // Already valid? keep it
        if (saved != null && saved in rosterIds) return

        // Try to extract slot index from either placeholder format:
        // 1) "placeholder-slot-4-<ts>" (slot number is 4 → index 3)
        // 2) "placeholder:3:<ts>"      (index is 3 → index 3)
        fun extractIndexFromPlaceholder(id: String?): Int? {
            if (id.isNullOrBlank()) return null

            // Format 1: placeholder-slot-<n>-<ts>
            Regex("""^placeholder-slot-(\d+)-""")
                .find(id)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { n ->
                    return (n - 1).coerceAtLeast(0)
                }

            // Format 2: placeholder:<idx>:<ts>
            Regex("""^placeholder:(\d+):""")
                .find(id)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { idx ->
                    return idx.coerceAtLeast(0)
                }

            return null
        }

        val slotIndex = extractIndexFromPlaceholder(saved)
        val fallbackId = slotIndex
            ?.let { idx -> selectedCharacters.getOrNull(idx)?.id }
            ?: selectedCharacters.first().id

        vnSettings.mainCharId = fallbackId
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
