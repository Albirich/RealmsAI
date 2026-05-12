package com.albirich.RealmsAI

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.albirich.RealmsAI.models.*
import com.albirich.RealmsAI.models.ModeSettings.CharacterClass
import com.albirich.RealmsAI.models.ModeSettings.CharacterRole
import com.albirich.RealmsAI.models.ModeSettings.CharacterStats
import com.albirich.RealmsAI.models.ModeSettings.GMStyle
import com.albirich.RealmsAI.models.ModeSettings.RPAct
import com.albirich.RealmsAI.models.ModeSettings.RPGCharacter
import com.albirich.RealmsAI.models.ModeSettings.RPGGenre
import com.albirich.RealmsAI.models.ModeSettings.RPGSettings
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import androidx.core.widget.doAfterTextChanged

class RPGSettingsActivity : AppCompatActivity() {

    private lateinit var genreSpinner: Spinner
    private lateinit var characterRecyclerView: RecyclerView
    private lateinit var actsRecyclerView: RecyclerView
    private lateinit var addActButton: ImageButton
    private lateinit var gmStyleSpinner: Spinner
    private lateinit var gmDescriptionText: TextView

    private lateinit var characterAdapter: RPGCharacterAdapter
    private lateinit var actAdapter: RPGActAdapter
    private var selectedCharacters: MutableList<CharacterProfile> = mutableListOf()
    private lateinit var rpgCharacters: MutableList<RPGCharacter>
    private lateinit var perspectiveSwitch:Switch

    // Murder UI
    private lateinit var murderToggle: CheckBox
    private lateinit var actsGroup: View
    private lateinit var murderGroup: View
    private lateinit var perspectiveGroup: View
    private lateinit var weaponEdit: EditText
    private lateinit var sceneEdit: EditText
    private lateinit var cluesRecycler: RecyclerView
    private lateinit var addClueButton: ImageButton
    private lateinit var randomizeKillersSwitch: Switch
    private lateinit var clueAdapter: ClueAdapter
    // Hold murder mode settings separately from RPGSettings
    private var murderSettings = ModeSettings.MurderSettings()


    private val gson = Gson()

    // Data
    private var genres = RPGGenre.values().toList()
    private var allAreas: List<Area> = emptyList() // Provide from parent/intent!
    private var allCharacters: List<CharacterProfile> = emptyList()

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
        actsGroup = findViewById(R.id.actGroup)
        // --- Murder UI binds ---
        murderToggle = findViewById(R.id.murderToggle)
        murderGroup = findViewById(R.id.murderGroup)
        weaponEdit = findViewById(R.id.weaponEdit)
        sceneEdit = findViewById(R.id.sceneEdit)
        cluesRecycler = findViewById(R.id.cluesRecycler)
        addClueButton = findViewById(R.id.addClueButton)
        randomizeKillersSwitch = findViewById(R.id.randomizeKillersSwitch)
        perspectiveGroup = findViewById(R.id.perspectiveGroup)

        // Parse incoming settings if editing an existing chat
        val currentSettingsJson = intent.getStringExtra("CURRENT_SETTINGS_JSON")
        rpgSettings = if (!currentSettingsJson.isNullOrBlank() && currentSettingsJson.trim().startsWith("{")) {
            gson.fromJson(currentSettingsJson, RPGSettings::class.java)
        } else {
            RPGSettings()
        }

        intent.getStringExtra("MURDER_SETTINGS_JSON")?.let { json ->
            if (json.trim().startsWith("{")) {
                murderSettings = gson.fromJson(json, ModeSettings.MurderSettings::class.java)
            }
        }

        // 1. Load areas from JSON (Areas are small enough to stay in the Intent for now)
        val areasJson = intent.getStringExtra("AREAS_JSON")
        if (!areasJson.isNullOrEmpty()) {
            allAreas = gson.fromJson(areasJson, object : TypeToken<List<Area>>() {}.type)
        }

        // 2. Load characters instantly from the Cache!
        selectedCharacters.clear()
        selectedCharacters.addAll(ChatDataCache.selectedCharacters)

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

        setupMurderSection()


        randomizeKillersSwitch.isChecked = murderSettings.randomizeKillers
        randomizeKillersSwitch.setOnCheckedChangeListener { _, on ->
            murderSettings.randomizeKillers = on
        }

        weaponEdit.setText(murderSettings.weapon)
        sceneEdit.setText(murderSettings.sceneDescription)

        weaponEdit.doAfterTextChanged {
            murderSettings.weapon = it?.toString().orEmpty()
        }
        sceneEdit.doAfterTextChanged {
            murderSettings.sceneDescription = it?.toString().orEmpty()
        }

        perspectiveSwitch.setOnCheckedChangeListener { _, isChecked ->
            rpgSettings.perspective = if (isChecked) "onTable" else "aboveTable"

            // 1. Toggle Visibilities
            gmStyleSpinner.visibility = if (isChecked) View.VISIBLE else View.GONE
            gmDescriptionText.visibility = if (isChecked) View.VISIBLE else View.GONE

            // 2. Safely Re-anchor the Spacer using ConstraintSet
            val parentLayout = findViewById<ConstraintLayout>(R.id.rpgSettingsRootLayout) // Replace with your actual root ID!
            val constraintSet = androidx.constraintlayout.widget.ConstraintSet()

            constraintSet.clone(parentLayout)

            if (isChecked) {
                // Anchor spacer to the bottom of the gm description
                constraintSet.connect(
                    R.id.rpgsettingspacer1,
                    androidx.constraintlayout.widget.ConstraintSet.TOP,
                    R.id.gmStyleDescription,
                    androidx.constraintlayout.widget.ConstraintSet.BOTTOM
                )
            } else {
                // Anchor spacer to the bottom of the switch
                constraintSet.connect(
                    R.id.rpgsettingspacer1,
                    androidx.constraintlayout.widget.ConstraintSet.TOP,
                    R.id.perspectiveSwitch,
                    androidx.constraintlayout.widget.ConstraintSet.BOTTOM
                )
            }

            // Apply the new rules to the layout!
            constraintSet.applyTo(parentLayout)
        }

        gmStyleSpinner = findViewById(R.id.gmStyleSpinner)
        gmDescriptionText = findViewById<TextView>(R.id.gmStyleDescription)

        val infoButtonGenre: ImageButton = findViewById(R.id.infoButtonGenre)
        infoButtonGenre.setOnClickListener {
            AlertDialog.Builder(this@RPGSettingsActivity)
                .setTitle("Genres")
                .setMessage("The selected Genre will dictate what classes and rules there are for the session")
                .setPositiveButton("OK", null)
                .show()
        }

        val infoButtonActs: ImageButton = findViewById(R.id.infoButtonActs)
        infoButtonActs.setOnClickListener {
            AlertDialog.Builder(this@RPGSettingsActivity)
                .setTitle("Acts")
                .setMessage("This is whats given to the GM as notes for the current Act/Arch of the story. Only the current Act is shown so if you reference things from other Acts AI-GMs won't be able to understand.")
                .setPositiveButton("OK", null)
                .show()
        }
        val infoButtonMurderToggle: ImageButton = findViewById(R.id.infoButtonMurderToggle)
        infoButtonMurderToggle.setOnClickListener {
            AlertDialog.Builder(this@RPGSettingsActivity)
                .setTitle("Murder Mystery")
                .setMessage("Replaces acts with Murder Mystery options. One character is the murderer it's up to the others to find out who.")
                .setPositiveButton("OK", null)
                .show()
        }
        val infoButtonPerspective: ImageButton = findViewById(R.id.infoButtonPerspective)
        infoButtonPerspective.setOnClickListener {
            AlertDialog.Builder(this@RPGSettingsActivity)
                .setTitle("Perspective")
                .setMessage("Above the table: the characters in the game will play a ttrpg. One will have to be the GM\n" +
                        "On the table: the characters don't realize they are in a game. The activationAi handles GMing, narrating locations and dice results")
                .setPositiveButton("OK", null)
                .show()
        }
        val infoButtonRandomKiller: ImageButton = findViewById(R.id.infoButtonRandomKiller)
        infoButtonRandomKiller.setOnClickListener {
            AlertDialog.Builder(this@RPGSettingsActivity)
                .setTitle("Random Murder mode")
                .setMessage("This checkbox will make it so at the start of each Session the murder options are randomly filled by an ai")
                .setPositiveButton("OK", null)
                .show()
        }

        val gmOptions = GMStyle.values().map { it.displayName }
        val gmAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, gmOptions)
        gmStyleSpinner.adapter = gmAdapter

        // Set current value from settings
        val currentGMStyle = GMStyle.values().indexOfFirst { it.name == rpgSettings.gmStyle }
        if (currentGMStyle >= 0) gmStyleSpinner.setSelection(currentGMStyle)

        gmStyleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selected = GMStyle.values()[position]
                rpgSettings.gmStyle = selected.name
                gmDescriptionText.text = selected.description
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
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

            val finalActs = actAdapter.getActs()
            if (finalActs.any { it.summary.length > 1000 || it.goal.length > 100 }) {
                Toast.makeText(this, "One of your Acts exceeds the character limit.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val finalCharacters = characterAdapter.getCharacters()
            if (finalCharacters.any { it.equipment.size > 10 }) {
                Toast.makeText(this, "One of your Characters equipment exceeds the 10 item limit.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // --- NEW: ABOVE TABLE GM VALIDATION ---
            if (rpgSettings.perspective == "aboveTable") {
                // Count how many characters actually have the GM role
                val gmCount = finalCharacters.count { it.role == CharacterRole.GM }

                if (gmCount != 1) {
                    Toast.makeText(this, "Above Table mode requires exactly ONE character to be assigned as the GM.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
            }

            // validate via roles if murder is on
            if (murderSettings.enabled && !validateMurderByRoles(rpgSettings, murderSettings)) {
                Toast.makeText(this, "Assign exactly one TARGET and at least one VILLAIN, or enable Random Killer.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val result = Intent()
                .putExtra("RPG_SETTINGS_JSON", gson.toJson(rpgSettings))
                .putExtra("MURDER_SETTINGS_JSON", gson.toJson(murderSettings))
            setResult(Activity.RESULT_OK, result)
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

    private fun setupMurderSection() {
        val applyVisibility: (Boolean) -> Unit = { enabled ->
            actsGroup.visibility   = if (enabled) View.GONE else View.VISIBLE
            murderGroup.visibility = if (enabled) View.VISIBLE else View.GONE

            if (enabled) {
                rpgSettings.perspective = "onTable"
                perspectiveSwitch.isChecked = true
                perspectiveSwitch.isEnabled = false
                perspectiveGroup.visibility = View.GONE

            } else {
                perspectiveGroup.visibility = View.VISIBLE
                perspectiveSwitch.isEnabled = true
            }
            android.util.Log.d("MurderToggle", "enabled=$enabled acts=${actsGroup.visibility} murder=${murderGroup.visibility}")
        }

        // initial state
        murderToggle.isChecked = murderSettings.enabled
        applyVisibility(murderSettings.enabled)

        // listen
        murderToggle.setOnCheckedChangeListener { _, isOn ->
            murderSettings.enabled = isOn
            applyVisibility(isOn)
        }

        // randomize killers toggle
        randomizeKillersSwitch.isChecked = murderSettings.randomizeKillers
        randomizeKillersSwitch.setOnCheckedChangeListener { _, on ->
            murderSettings.randomizeKillers = on
        }

        // fields
        weaponEdit.setText(murderSettings.weapon)
        sceneEdit.setText(murderSettings.sceneDescription)

        // if KTX isn’t available, use TextWatcher
        weaponEdit.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { murderSettings.weapon = s?.toString().orEmpty() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        sceneEdit.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { murderSettings.sceneDescription = s?.toString().orEmpty() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // clues
        clueAdapter = ClueAdapter(
            items = murderSettings.clues,
            onEdit = { clue -> showClueDialog(clue) },
            onDelete = { clue ->
                murderSettings.clues.removeAll { it.id == clue.id }
                clueAdapter.notifyDataSetChanged()
            }
        )
        cluesRecycler.layoutManager = LinearLayoutManager(this)
        cluesRecycler.adapter = clueAdapter

        addClueButton.setOnClickListener { showClueDialog(null) }
    }


    private fun showClueDialog(existing: ModeSettings.MurderClue?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_clue, null)
        val titleEdit = dialogView.findViewById<EditText>(R.id.clueTitleEdit)
        val descEdit = dialogView.findViewById<EditText>(R.id.clueDescEdit)

        if (existing != null) {
            titleEdit.setText(existing.title)
            descEdit.setText(existing.description)
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Add Clue" else "Edit Clue")
            .setView(dialogView)
            .setPositiveButton("Save") { d, _ ->
                val t = titleEdit.text.toString().trim()
                val desc = descEdit.text.toString().trim()
                if (existing == null) {
                    murderSettings.clues.add(ModeSettings.MurderClue(title = t, description = desc))
                } else {
                    existing.title = t
                    existing.description = desc
                }
                clueAdapter.notifyDataSetChanged()
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun validateMurderByRoles(rpg: ModeSettings.RPGSettings, murder: ModeSettings.MurderSettings): Boolean {
        if (!murder.enabled) return true
        if (randomizeKillersSwitch.isChecked ) return true
        val roles = rpg.characters.map { it.role }
        val targetCount = roles.count { it == ModeSettings.CharacterRole.TARGET }
        val villainCount = roles.count { it == ModeSettings.CharacterRole.VILLAIN }
        val okTarget = (targetCount == 1)
        val okVillains = if (murder.randomizeKillers) true else villainCount >= 1
        return okTarget && okVillains
    }

    private class SimpleTextWatcher(
        val onChange: (String) -> Unit
    ) : android.text.TextWatcher {
        override fun afterTextChanged(s: android.text.Editable?) {
            onChange(s?.toString().orEmpty())
        }
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
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
            },
            onTheTable = rpgSettings.perspective == "onTable"
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
