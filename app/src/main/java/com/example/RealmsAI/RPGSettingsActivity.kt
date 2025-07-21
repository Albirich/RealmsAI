package com.example.RealmsAI

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.*
import com.example.RealmsAI.models.ModeSettings.CharacterClass
import com.example.RealmsAI.models.ModeSettings.CharacterRole
import com.example.RealmsAI.models.ModeSettings.CharacterStats
import com.example.RealmsAI.models.ModeSettings.RPAct
import com.example.RealmsAI.models.ModeSettings.RPGCharacter
import com.example.RealmsAI.models.ModeSettings.RPGGenre
import com.example.RealmsAI.models.ModeSettings.RPGSettings
import com.google.common.reflect.TypeToken
import com.google.gson.Gson

class RPGSettingsActivity : AppCompatActivity() {

    private lateinit var genreSpinner: Spinner
    private lateinit var characterRecyclerView: RecyclerView
    private lateinit var actsRecyclerView: RecyclerView
    private lateinit var addActButton: ImageButton

    private lateinit var characterAdapter: RPGCharacterAdapter
    private lateinit var actAdapter: RPGActAdapter
    private var selectedCharacters: MutableList<CharacterProfile> = mutableListOf()
    private lateinit var rpgCharacters: MutableList<RPGCharacter>
    private lateinit var perspectiveSwitch:Switch

    private val gson = Gson()

    // Data
    private var genres = RPGGenre.values().toList()
    private var allAreas: List<Area> = emptyList() // Provide from parent/intent!
    private var allCharacters: List<CharacterProfile> = emptyList() // Provide from parent/intent!

    private var rpgSettings: RPGSettings = RPGSettings(
        genre = RPGGenre.FANTASY,
        characters = mutableListOf(),
        acts = mutableListOf(),
        currentAct = 0,
        linkedToMap = mutableMapOf()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rpg_settings)

        genreSpinner = findViewById(R.id.genreSpinner)
        characterRecyclerView = findViewById(R.id.characterRecyclerView)
        actsRecyclerView = findViewById(R.id.actsRecyclerView)
        addActButton = findViewById(R.id.addActButton)
        perspectiveSwitch = findViewById(R.id.perspectiveSwitch)

        // Parse incoming settings if editing an existing chat
        val currentSettingsJson = intent.getStringExtra("CURRENT_SETTINGS_JSON")
        if (!currentSettingsJson.isNullOrBlank() && currentSettingsJson.trim().startsWith("{")) {
            rpgSettings = gson.fromJson(currentSettingsJson, RPGSettings::class.java)
        } else {
            rpgSettings = RPGSettings()
        }
        val selectedCharactersJson = intent.getStringExtra("SELECTED_CHARACTERS_JSON")
        val areasJson = intent.getStringExtra("AREAS_JSON")
        if (!areasJson.isNullOrEmpty()) {
            allAreas = gson.fromJson(areasJson, object : TypeToken<List<Area>>() {}.type)
        }
        selectedCharacters = gson.fromJson(
            selectedCharactersJson, object : TypeToken<List<CharacterProfile>>() {}.type
        )
        perspectiveSwitch.isChecked = (rpgSettings.perspective == "onTable")
        perspectiveSwitch.setOnCheckedChangeListener { _, isChecked ->
            rpgSettings.perspective = if (isChecked) "onTable" else "aboveTable"
        }

        rpgSettings = if (!currentSettingsJson.isNullOrEmpty() && currentSettingsJson.trim().startsWith("{")) {
            gson.fromJson(currentSettingsJson, RPGSettings::class.java) ?: RPGSettings(
                genre = RPGGenre.FANTASY,
                characters = mutableListOf(),
                acts = mutableListOf(),
                currentAct = 0
            )
        } else {
            RPGSettings(
                genre = RPGGenre.FANTASY,
                characters = mutableListOf(),
                acts = mutableListOf(),
                currentAct = 0
            )
        }

        perspectiveSwitch.setOnCheckedChangeListener { _, isChecked ->
            rpgSettings.perspective = if (isChecked) "onTable" else "aboveTable"
        }

        val charMap = rpgSettings.characters.associateBy { it.characterId }
        rpgCharacters = selectedCharacters.map { profile ->
            charMap[profile.id] ?: RPGCharacter(
                characterId = profile.id,
                name = profile.name,
                role = CharacterRole.HERO,
                characterClass = CharacterClass.WARRIOR,
                stats = CharacterStats(6, 6, 6, 6, 6),
                equipment = listOf()
            )
        }.toMutableList()

        setupGenreSpinner()
        setupCharacterRecycler()
        setupActsRecycler()

        addActButton.setOnClickListener {
            val newAct = RPAct(
                summary = "",
                goal = "",
                areaId = allAreas.firstOrNull()?.id ?: ""
            )
            actAdapter.addAct(newAct)
        }

        // Save/Done button logic: return data to parent activity
        findViewById<Button>(R.id.saveRpgSettingsButton).setOnClickListener {
            rpgSettings.characters = characterAdapter.getCharacters().toMutableList()
            rpgSettings.acts = actAdapter.getActs().toMutableList()

            // ---- FILTER linkedToMap here ----
            val currentCharacters = rpgSettings.characters
            val allowedSidekickIds = currentCharacters
                .filter { it.role == CharacterRole.SIDEKICK }
                .map { it.characterId }
                .toSet()
            rpgSettings.linkedToMap = rpgSettings.linkedToMap
                .filterKeys { allowedSidekickIds.contains(it) }
                .toMutableMap()
            val resultIntent = Intent()
            resultIntent.putExtra("RPG_SETTINGS_JSON", gson.toJson(rpgSettings))
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }

    }

    private fun setupGenreSpinner() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            genres.map { it.name.replace('_', ' ').capitalize() }
        )
        genreSpinner.adapter = adapter
        genreSpinner.setSelection(genres.indexOf(rpgSettings.genre))
        genreSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View, position: Int, id: Long) {
                rpgSettings.genre = genres[position]
                characterAdapter.setGenre(genres[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupCharacterRecycler() {
        characterAdapter = RPGCharacterAdapter(
            characterProfiles = selectedCharacters,
            rpgCharacters = rpgCharacters,
            genre = rpgSettings.genre,
            linkedToMap = rpgSettings.linkedToMap,
            onLinkedToMapUpdate = { charId, linkOrNull ->
                val list = rpgSettings.linkedToMap.getOrPut(charId) { mutableListOf() }
                if (linkOrNull == null) {
                    list.removeAll { it.type == "sidekickTo" }
                } else {
                    list.removeAll { it.type == linkOrNull.type }
                    list.add(linkOrNull)
                }
                rpgSettings.linkedToMap[charId] = list
            }
        )
        characterRecyclerView.layoutManager = LinearLayoutManager(this)
        characterRecyclerView.adapter = characterAdapter
    }


    private fun setupActsRecycler() {
        actAdapter = RPGActAdapter(rpgSettings.acts, allAreas)
        actsRecyclerView.layoutManager = LinearLayoutManager(this)
        actsRecyclerView.adapter = actAdapter
    }

}
