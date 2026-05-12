package com.albirich.RealmsAI

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.bumptech.glide.Glide
import com.albirich.RealmsAI.models.Area
import com.albirich.RealmsAI.models.CharacterProfile
import com.albirich.RealmsAI.models.DialogueExample
import com.albirich.RealmsAI.models.Outfit
import com.albirich.RealmsAI.models.Relationship
import com.google.android.material.button.MaterialButton
import com.google.common.reflect.TypeToken
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import androidx.lifecycle.lifecycleScope
import com.albirich.RealmsAI.models.CharacterLink
import com.albirich.RealmsAI.models.ScenarioEvent
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.jvm.java

class CharacterCreationActivity : AppCompatActivity() {
    // UI
    private lateinit var avatarView: ImageView
    private lateinit var relationshipBtn: Button
    private lateinit var wardrobeButton: MaterialButton
    private lateinit var bubbleColorSpinner: Spinner
    private lateinit var textColorSpinner: Spinner
    private lateinit var colorExample: TextView
    private lateinit var submitBtn: MaterialButton
    private lateinit var cardImportLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var addAreaButton: Button
    private var characterEvents: MutableList<ScenarioEvent> = mutableListOf()
    private var lorebookIds: MutableList<String> = mutableListOf()
    private lateinit var lorebooksBtn: Button
    private lateinit var lorebooksLauncher: ActivityResultLauncher<Intent>
    private var linkedCharacters: MutableList<CharacterLink> = mutableListOf()
    private lateinit var linkLauncher: ActivityResultLauncher<Intent>


    // EditTexts
    private lateinit var nameEt: EditText
    private lateinit var bioEt: EditText
    private lateinit var personalityEt: EditText
    private lateinit var privateDescEt: EditText
    private lateinit var ageEt: EditText
    private lateinit var heightEt: EditText
    private lateinit var weightEt: EditText
    private lateinit var eyeColorEt: EditText
    private lateinit var hairColorEt: EditText
    private lateinit var physicalDescEt: EditText
    private lateinit var genderEt: EditText
    private lateinit var backstoryEt: EditText
    private lateinit var abilitiesEt: EditText
    private lateinit var notesEt: EditText
    private lateinit var greetingEt: EditText
    private lateinit var scenarioEt: EditText
    // State
    private var progressDialog: AlertDialog? = null
    private var avatarUri: Uri? = null             // Only local uri if user picks new
    private var avatarChanged = false              // Tracks if avatar was re-picked
    private var originalAvatarUrl: String? = null  // Download url from firestore
    private var outfitsList: List<Outfit> = emptyList()
    private var relationships: List<Relationship> = emptyList()
    private val bubblecolorOptions = listOf(
        "Black" to "#000000",
        "Blue" to "#2196F3",
        "Green" to "#4CAF50",
        "Orange" to "#FF9800",
        "Pink" to "#e86cbe",
        "Purple" to "#c778f5",
        "Red" to "#ce0202",
        "White" to "#FFFFFF",
        "Yellow" to "#FFEB3B"
    )
    private val textcolorOptions = listOf(
        "Black" to "#000000",
        "Blue" to "#213af3",
        "Green" to "#098217",
        "Orange" to "#cd6a00",
        "Pink" to "#E91E63",
        "Purple" to "#A200FF",
        "Red" to "#970606",
        "White" to "#e3dfdf",
        "Yellow" to "#cdd54b"
    )

    // Launcher
    private lateinit var wardrobeLauncher: ActivityResultLauncher<Intent>
    private lateinit var avatarPicker: ActivityResultLauncher<String>
    private lateinit var eventsLauncher: ActivityResultLauncher<Intent>

    private lateinit var universeEt: EditText
    private lateinit var selectTagsBtn: Button
    private lateinit var selectedTagsTv: TextView

    private val availableTags = arrayOf(
        "Fantasy", "Sci-Fi", "Modern", "Male", "Female",
        "Non-Binary", "Monster", "Hero", "Villain", "OC",
        "Canon", "Tsundere", "Yandere", "Kuudere", "Dandere"
    )
    // Track selection state
    private val checkedTags = BooleanArray(availableTags.size)
    private val currentTags = mutableListOf<String>()

    private lateinit var dialogueContainer: LinearLayout
    private lateinit var generateDialogueBtn: Button
    private lateinit var addRowBtn: Button

    // The Scenario Bank
    private val scenarioPrompts = listOf(
        // Flirty / cute
        "Someone you like catches you staring—then smirks.",
        "You accidentally brush their hand and neither of you pulls away.",
        "They compliment you in a way that feels way too personal.",
        "You’re forced to share a small space—shoulder to shoulder.",
        "They lean in like they’re about to kiss you… then whisper a secret instead.",
        "They offer to fix something on you (hair, collar, glove) and take their time.",
        "A friend teases you about them—and it hits a little too close to home.",
        "They call you by a nickname you’ve never heard before… and it works.",
        "You catch them smiling at you when they think you aren’t looking.",
        "You dance with them—awkward at first, then dangerously natural.",
        
        // Social / comedy
        "You trip and fall in front of your crush.",
        "Someone insults your outfit.",
        "A friend dares you to do something humiliating in public.",
        "You accidentally call someone the wrong name—right to their face.",
        "You laugh at the worst possible moment.",
        "A stranger mistakes you for someone famous.",
        "You walk into the wrong room like you own it—everyone stares.",
        "You spill a drink on an important person.",
        "Your voice cracks mid-speech and the room goes quiet.",
        "Someone challenges you to a staring contest and won’t back down.",

        // Tension / confrontation
        "You are accused of a crime you didn't commit.",
        "You are cornered by an enemy in an alley.",
        "A rival publicly claims you’re a fraud.",
        "A guard demands to search your belongings—now.",
        "Someone threatens someone you care about unless you cooperate.",
        "You’re challenged to a duel in front of a crowd.",
        "A mercenary offers protection… for an ugly price.",
        "You catch someone following you—and they don’t deny it.",
        "A friend asks you to lie for them, and it could ruin you.",
        "You’re offered a deal that feels like a trap.",

        // Moral dilemmas
        "You see someone stealing bread to survive.",
        "A merchant tries to overcharge you significantly.",
        "You find a lost child in the market.",
        "You discover someone’s secret that could destroy them.",
        "You can save one person, but it means abandoning another.",
        "You find a purse of money—clearly stolen.",
        "You catch a friend doing something unforgivable.",
        "Someone begs you for help, but helping them is illegal.",
        "You’re asked to punish someone who might be innocent.",
        "A powerful person offers you favor in exchange for silence.",

        // Mystery / intrigue
        "A mysterious figure hands you a sealed letter.",
        "You wake up in a strange place with no memory.",
        "You discover a hidden treasure chest.",
        "You overhear a conversation you were never meant to hear.",
        "A note appears with your name on it—and no one admits writing it.",
        "You find a symbol carved into your door overnight.",
        "Someone you trust won’t meet your eyes and keeps changing the subject.",
        "A stranger knows something about your past that they shouldn’t.",
        "You’re invited to a meeting at midnight—no details given.",
        "You’re offered information… but only if you answer a riddle first.",

        // Relationships / emotional pressure
        "Someone confesses their love to you.",
        "Your best friend betrays you.",
        "Someone you hurt in the past returns—changed.",
        "A friend asks, 'Do you actually trust me?'",
        "You realize you’re jealous—and you hate that you are.",
        "You’re forced to work with someone you can’t stand.",
        "Someone gives you a gift that feels loaded with meaning.",
        "You’re asked to forgive someone who hasn’t apologized.",
        "A friend tells you they’re leaving for good—today.",
        "Someone you admire is disappointed in you.",

        // Adventure / mission hooks
        "You are asked to lead a dangerous mission.",
        "You’re hired for a job, but the client won’t reveal their identity.",
        "A map falls into your hands with one location circled in red.",
        "A storm forces you to take shelter somewhere you shouldn’t be.",
        "You find a key that fits nothing you own.",
        "A stranger offers you a rare flower.",
        "You witness something impossible—and no one believes you.",
        "You’re offered power if you swear an oath you can’t undo.",
        "You arrive too late: the place is destroyed and it’s your fault somehow.",
        "A ticking sound starts coming from your bag—and you didn’t pack anything that ticks."
    )

    private lateinit var sfwSwitch: Switch
    private lateinit var privateSwitch: Switch
    companion object {
        private const val RELATIONSHIP_REQ_CODE = 5001
        const val EXTRA_OUTFITS_JSON = "EXTRA_OUTFITS_JSON"
    }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private lateinit var areaMapLauncher: ActivityResultLauncher<Intent>
    private lateinit var currentCharacter: CharacterProfile


    private var characterAreas: MutableList<Area> = mutableListOf()
    private var assignedAreaId: String? = null
    private var assignedLocationId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_character)

        // Find Views
        avatarView = findViewById(R.id.avatarImageView)
        relationshipBtn = findViewById(R.id.charrelationshipBtn)
        wardrobeButton = findViewById(R.id.wardrobeButton)
        bubbleColorSpinner = findViewById(R.id.bubbleColorSpinner)
        textColorSpinner = findViewById(R.id.textColorSpinner)
        submitBtn = findViewById(R.id.charSubmitButton)
        nameEt = findViewById(R.id.characterNameInput)
        bioEt = findViewById(R.id.etSummary)
        personalityEt = findViewById(R.id.characterPersonalityInput)
        privateDescEt = findViewById(R.id.characterprivateDescriptionInput)
        ageEt = findViewById(R.id.ageEditText)
        heightEt = findViewById(R.id.heightEditText)
        weightEt = findViewById(R.id.weightEditText)
        eyeColorEt = findViewById(R.id.eyeColorEditText)
        hairColorEt = findViewById(R.id.hairColorEditText)
        physicalDescEt = findViewById(R.id.physicalDescriptionEditText)
        abilitiesEt = findViewById(R.id.abilitiesInput)
        notesEt = findViewById(R.id.notesInput)
        genderEt = findViewById(R.id.genderEditText)
        backstoryEt = findViewById(R.id.backstoryEditText)
        scenarioEt = findViewById(R.id.characterScenarioInput)
        greetingEt = findViewById(R.id.characterGreetingInput)
        sfwSwitch = findViewById(R.id.sfwSwitch)
        privateSwitch = findViewById(R.id.privateSwitch)
        addAreaButton = findViewById<MaterialButton>(R.id.addAreaButton)
        universeEt = findViewById(R.id.universeEditText)
        selectTagsBtn = findViewById(R.id.selectTagsButton)
        selectedTagsTv = findViewById(R.id.selectedTagsText)
        dialogueContainer = findViewById(R.id.dialogueContainer)
        generateDialogueBtn = findViewById(R.id.generateDialogueButton)
        addRowBtn = findViewById(R.id.addDialogueRowButton)


        // Color spinners show names but store hex
        val bubbleadapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, bubblecolorOptions.map { it.first })
        bubbleadapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        bubbleColorSpinner.adapter = bubbleadapter
        val textadapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, textcolorOptions.map { it.first })
        textadapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        textColorSpinner.adapter = textadapter

        colorExample = findViewById(R.id.colorExample)

        // --- Bubble Color Spinner Logic ---
        bubbleColorSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val hexColor = bubblecolorOptions[position].second
                colorExample.setBackgroundColor(android.graphics.Color.parseColor(hexColor))
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        // Set default to White (Index 7)
        bubbleColorSpinner.setSelection(7)
        // Also initialize the example view to match the white background
        colorExample.setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"))

        // --- Text Color Spinner Logic ---
        textColorSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val hexColor = textcolorOptions[position].second
                colorExample.setTextColor(android.graphics.Color.parseColor(hexColor))
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Preset avatar
        avatarView.setImageResource(R.drawable.placeholder_avatar)

        // Avatar Picker
        avatarPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                avatarUri = uri
                avatarChanged = true
                avatarView.setImageURI(uri)
            }
        }
        avatarView.setOnClickListener { avatarPicker.launch("image/*") }

        // Generate Random Scenarios
        generateDialogueBtn.setOnClickListener {
            // Pick 5 random prompts
            val randomPrompts = scenarioPrompts.shuffled().take(5)
            randomPrompts.forEach { prompt ->
                addDialogueRow(prompt, "")
            }
            Toast.makeText(this, "Added scenarios. Fill in the responses!", Toast.LENGTH_SHORT).show()
        }

        // Add Empty Row
        addRowBtn.setOnClickListener {
            addDialogueRow("", "")
        }

        // Wardrobe Launcher
        wardrobeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                result.data?.getStringExtra(EXTRA_OUTFITS_JSON)?.let { outfitsJson ->
                    outfitsList = Gson().fromJson(outfitsJson, Array<Outfit>::class.java).toList()
                }
            }
        }
        wardrobeButton.setOnClickListener {
            val intent = Intent(this, WardrobeActivity::class.java)
            val outfitsJson = Gson().toJson(outfitsList)
            intent.putExtra(EXTRA_OUTFITS_JSON, outfitsJson)
            val heightFeet = parseHeightToFeet(heightEt.text?.toString()) ?: 6f
            intent.putExtra("CHARACTER_HEIGHT_FEET", heightFeet)
            wardrobeLauncher.launch(intent)
        }

        // Background Picker
        areaMapLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val areasJson = result.data?.getStringExtra("AREAS_JSON")
                val characterToAreaJson = result.data?.getStringExtra("CHARACTER_TO_AREA_JSON")
                val characterToLocationJson = result.data?.getStringExtra("CHARACTER_TO_LOCATION_JSON") // <-- Catch Location Map

                if (!areasJson.isNullOrBlank()) {
                    characterAreas = Gson().fromJson(areasJson, Array<Area>::class.java).toMutableList()
                }

                if (!characterToAreaJson.isNullOrBlank()) {
                    val mapType = object : TypeToken<Map<String, String>>() {}.type
                    val areaMap = Gson().fromJson<Map<String, String>>(characterToAreaJson, mapType)
                    assignedAreaId = areaMap[currentCharacter.id]
                }

                if (!characterToLocationJson.isNullOrBlank()) {
                    val mapType = object : TypeToken<Map<String, String>>() {}.type
                    val locMap = Gson().fromJson<Map<String, String>>(characterToLocationJson, mapType)
                    assignedLocationId = locMap[currentCharacter.id]
                }
            }
        }

        findViewById<Button>(R.id.charBackgroundButton).setOnClickListener {
            // If currentCharacter is not set, build it now!
            if (!::currentCharacter.isInitialized) {
                currentCharacter = CharacterProfile(
                    id = System.currentTimeMillis().toString(),
                    name = nameEt.text.toString().trim(),
                    avatarUri = avatarUri?.toString(),
                )
            }
            val charJson = Gson().toJson(listOf(currentCharacter))
            val areaJson = Gson().toJson(characterAreas)
            val intent = Intent(this, ChatMapActivity::class.java)
            intent.putExtra("CHARACTER_LIST_JSON", charJson)
            intent.putExtra("AREA_LIST_JSON", areaJson)
            areaMapLauncher.launch(intent)
        }

        // 1. Initialize the Launcher
        eventsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val eventsJson = result.data?.getStringExtra(EventsActivity.EXTRA_EVENTS_JSON)
                if (!eventsJson.isNullOrBlank()) {
                    val eventListType = object : TypeToken<List<ScenarioEvent>>() {}.type
                    characterEvents = Gson().fromJson(eventsJson, eventListType)
                    Toast.makeText(this, "Saved ${characterEvents.size} events", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 2. Wire up the Button (Assuming you added an R.id.eventsbtn to your XML!)
        val eventsBtn = findViewById<Button>(R.id.eventsbtn)
        eventsBtn.setOnClickListener {
            val intent = Intent(this, EventsActivity::class.java)
            intent.putExtra(EventsActivity.EXTRA_EVENTS_JSON, Gson().toJson(characterEvents))
            eventsLauncher.launch(intent)
        }

        lorebooksLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val idsJson = result.data?.getStringExtra("LOREBOOK_IDS_JSON")
                if (!idsJson.isNullOrBlank()) {
                    val type = object : TypeToken<List<String>>() {}.type
                    lorebookIds = Gson().fromJson(idsJson, type)
                    Toast.makeText(this, "Saved ${lorebookIds.size} Lorebooks", Toast.LENGTH_SHORT).show()
                }
            }
        }

        lorebooksBtn = findViewById(R.id.charLorebooksBtn)
        lorebooksBtn.setOnClickListener {
            val intent = Intent(this, ChatLoreActivity::class.java)
            // Pass the current list so they can see what is already checked
            intent.putExtra("LOREBOOK_IDS_JSON", Gson().toJson(lorebookIds))
            lorebooksLauncher.launch(intent)
        }

        val charLinkBtn = findViewById<Button>(R.id.charLink)

        // 1. Set up the launcher to receive the modified list back
        linkLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val linksJson = result.data?.getStringExtra("CHARACTER_LINKS_JSON")
                if (!linksJson.isNullOrBlank()) {
                    val listType = object : TypeToken<List<CharacterLink>>() {}.type
                    linkedCharacters = Gson().fromJson(linksJson, listType)
                    Toast.makeText(this, "Saved ${linkedCharacters.size} Links", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Relationships button
        relationshipBtn.setOnClickListener {
            val intent = Intent(this, CharacterRelationshipActivity::class.java)
            intent.putExtra("RELATIONSHIPS_JSON", Gson().toJson(relationships))
            startActivityForResult(intent, RELATIONSHIP_REQ_CODE)
        }

        // --- Expand/Collapse Physical Info Section ---
        val physicalHeader: LinearLayout = findViewById(R.id.physicalInfoHeader)
        val physicalSection: LinearLayout = findViewById(R.id.physicalInfoSection)
        val physicalToggle: ImageView = findViewById(R.id.physicalInfoToggle)

        physicalHeader.setOnClickListener {
            val isVisible = physicalSection.visibility == View.VISIBLE
            physicalSection.visibility = if (isVisible) View.GONE else View.VISIBLE
            physicalToggle.setImageResource(
                if (isVisible) R.drawable.ic_expand_more_white else R.drawable.ic_expand_less_white
            )
            val root = findViewById<ConstraintLayout>(R.id.charCreate)
            val cs = ConstraintSet()
            cs.clone(root)

            if (isVisible) {
                // Section going to GONE, so connect header directly to bubbleTitle
                cs.connect(R.id.physicalInfoHeader, ConstraintSet.BOTTOM, R.id.bubbleTitle, ConstraintSet.TOP)
                cs.connect(R.id.bubbleTitle, ConstraintSet.TOP, R.id.physicalInfoHeader, ConstraintSet.BOTTOM)
            } else {
                // Section going to VISIBLE, so place it between header and bubbleTitle
                cs.connect(R.id.physicalInfoHeader, ConstraintSet.BOTTOM, R.id.physicalInfoSection, ConstraintSet.TOP)
                cs.connect(R.id.bubbleTitle, ConstraintSet.TOP, R.id.physicalInfoSection, ConstraintSet.BOTTOM)
            }
            cs.applyTo(root)
        }

        // --- Edit support: Fill from profile if present ---
        val editCharId = intent.getStringExtra("CHAR_EDIT_ID")
        val editOriginalId = intent.getStringExtra("CHAR_EDIT_ORIGINAL_ID")
        val charJson = intent.getStringExtra("CHAR_PROFILE_JSON")
        if (editCharId != null && !charJson.isNullOrBlank()) {
            val profile = Gson().fromJson(charJson, CharacterProfile::class.java)
            currentCharacter = profile
            fillFormFromProfile(profile)
        }
        val infoButtonWardrobe: ImageButton = findViewById(R.id.infoButtonWardrobe)
        infoButtonWardrobe.setOnClickListener {
            AlertDialog.Builder(this@CharacterCreationActivity)
                .setTitle("Wardrobe")
                .setMessage("This is used to add poses for the character.")
                .setPositiveButton("OK", null)
                .show()
        }
        val infoButtonCharRelationships: ImageButton = findViewById(R.id.infoButtonCharRelationships)
        infoButtonCharRelationships.setOnClickListener {
            AlertDialog.Builder(this@CharacterCreationActivity)
                .setTitle("Relationships")
                .setMessage("This allows you to give the character relationships that they will remember in chats")
                .setPositiveButton("OK", null)
                .show()
        }
        val infoButtonBackgroundGallery: ImageButton = findViewById(R.id.infoButtonBackgroundGallery)
        infoButtonBackgroundGallery.setOnClickListener {
            AlertDialog.Builder(this@CharacterCreationActivity)
                .setTitle("Backgrounds")
                .setMessage("This allows you to choose backgrounds for character 1 on 1 chats")
                .setPositiveButton("OK", null)
                .show()

        }
        val infoButtonEvents: ImageButton = findViewById(R.id.infoButtonEvent)
        infoButtonEvents.setOnClickListener {
            AlertDialog.Builder(this@CharacterCreationActivity)
                .setTitle("Events")
                .setMessage("This lets you make a list of random events for the chat")
                .setPositiveButton("OK", null)
                .show()

        }
        val infoButtonLorebooks: ImageButton = findViewById(R.id.infoButtonLorebooks)
        infoButtonLorebooks.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Lorebooks")
                .setMessage("Attach Lorebooks to give this character permanent world knowledge, magic systems, or extensive backstories.")
                .setPositiveButton("OK", null)
                .show()
        }
        val infoButtonLinks: ImageButton = findViewById(R.id.infoButtonLinks)
        infoButtonLinks.setOnClickListener {
            AlertDialog.Builder(this@CharacterCreationActivity)
                .setTitle("Links")
                .setMessage("This lets you make a Link this profile with another in various ways.")
                .setPositiveButton("OK", null)
                .show()

        }

        selectTagsBtn.setOnClickListener {
            showTagSelectionDialog()
        }

        charLinkBtn.setOnClickListener {
            val intent = Intent(this, CharacterLinkActivity::class.java)
            // Pass the current list so they can see what is already linked
            intent.putExtra("CHARACTER_LINKS_JSON", Gson().toJson(linkedCharacters))

            // Pass the current character's ID so we don't accidentally let them link to themselves!
            val charIdToSave = editCharId ?: "new_character"
            intent.putExtra("CURRENT_CHAR_ID", charIdToSave)

            linkLauncher.launch(intent)
        }

        val importCardBtn = findViewById<MaterialButton>(R.id.importCardButton)

        cardImportLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                importCharacterCard(uri)
            }
        }

        importCardBtn.setOnClickListener {
            // Allow them to pick either a PNG image card or a raw JSON file
            cardImportLauncher.launch(arrayOf("image/png", "application/json"))
        }

        submitBtn.setOnClickListener {
            if (FirebaseAuth.getInstance().currentUser == null) {
                Toast.makeText(this, "ERROR: You are not actually logged in!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            // 1. Capture all inputs
            val name = nameEt.text.toString().trim()
            val bio = bioEt.text.toString().trim()
            val personality = personalityEt.text.toString().trim()
            val privateDesc = privateDescEt.text.toString().trim()
            val backstory = backstoryEt.text.toString().trim()
            val age = ageEt.text.toString().toFloatOrNull() ?: 0.0f
            val height = heightEt.text.toString().trim()
            val weight = weightEt.text.toString().trim()
            val eyeColor = eyeColorEt.text.toString().trim()
            val hairColor = hairColorEt.text.toString().trim()
            val physicalDesc = physicalDescEt.text.toString().trim()
            val gender = genderEt.text.toString().trim()
            val soloScenario = scenarioEt.text.toString().trim()
            val greeting = greetingEt.text.toString().trim()
            val bubbleColor = bubblecolorOptions[bubbleColorSpinner.selectedItemPosition].second
            val textColor = textcolorOptions[textColorSpinner.selectedItemPosition].second
            val abilities = abilitiesEt.text.toString().trim()
            val creatorNotes = notesEt.text.toString().trim()
            val universe = universeEt.text.toString().trim()


            physicalDescEt.filters = arrayOf(android.text.InputFilter.LengthFilter(100))
            // 2. Basic Validation
            if (name.isEmpty()) return@setOnClickListener toast("Name required")
            if (originalAvatarUrl.isNullOrBlank() && !avatarChanged) return@setOnClickListener toast("Pick an avatar")
            if (name.length > 40) return@setOnClickListener toast("Name too long (Max 40)")
            if (bio.length > 80) return@setOnClickListener toast("Summary too long (Max 80)")
            if (personality.length > 1000) return@setOnClickListener toast("Personality too long (Max 1000)")
            if (privateDesc.length > 1000) return@setOnClickListener toast("Private info too long (Max 1000)")
            if (backstory.length > 1000) return@setOnClickListener toast("Backstory too long (Max 1000)")
            if (greeting.length > 250) return@setOnClickListener toast("Greeting too long (Max 250)")
            if (physicalDesc.length > 250) return@setOnClickListener toast("Physical Description too long (Max 250)")

            val dialogueList = mutableListOf<String>()
            for (i in 0 until dialogueContainer.childCount) {
                val row = dialogueContainer.getChildAt(i) as? EditText
                val text = row?.text.toString().trim()
                if (text.isNotEmpty()) {
                    if (text.length > 500) return@setOnClickListener toast("Dialogue row ${i+1} is too long")
                    dialogueList.add(text)
                }
            }

            // 3. Define the actual save action (to be called after checks pass)
            val performSave: (Boolean?) -> Unit = { isPrivateOverride ->
                if (isPrivateOverride == false) {
                    privateSwitch.isChecked = false
                }

                showProgressDialog()

                lifecycleScope.launch {
                    // 1. Fetch missing vectors
                    generateMissingVectors(outfitsList)

                    // 2. SAVE EACH OUTFIT AS ITS OWN DOCUMENT
                    val db = FirebaseFirestore.getInstance()
                    val charIdToSave = editCharId ?: System.currentTimeMillis().toString()

                    try {
                        // Use a Batch to save them all simultaneously
                        val batch = db.batch()
                        val wardrobeRef = db.collection("characters").document(charIdToSave).collection("wardrobes")

                        for (outfit in outfitsList) {
                            // Use the outfit name as the Document ID (remove spaces/slashes just to be safe for URLs)
                            val safeDocId = outfit.name.replace("[^a-zA-Z0-9]".toRegex(), "_").ifBlank { "unnamed_outfit" }
                            val docRef = wardrobeRef.document(safeDocId)

                            // We save the outfit object directly!
                            batch.set(docRef, outfit)
                        }

                        batch.commit().await()
                    } catch (e: Exception) {
                        Log.e("Wardrobe", "Failed to save wardrobe subcollection", e)
                    }

                    // 3. STRIP THE VECTORS FROM THE MAIN LIST!
                    outfitsList = outfitsList.stripVectors().toMutableList()

                    // 4. Switch back to Main thread to run your original save logic
                    withContext(Dispatchers.Main) {
                        // Because we cleared and added the stripped outfits to outfitsList,
                        // the main document and the Intent will be completely free of vectors!
                        saveCharacterAndReturnToHub(
                            charIdToSave, editOriginalId, // pass the generated ID
                            name, bio, personality, privateDesc, backstory, soloScenario, greeting,
                            age, height, weight, eyeColor, hairColor, physicalDesc, abilities, gender, creatorNotes,
                            bubbleColor, textColor, universe
                        )
                    }
                }
            }

            // 4. Run the Logic Check
            val isPrivate = privateSwitch.isChecked

            if (!isPrivate) {
                // Public characters are always free/unlimited
                performSave(null)
            } else {
                // It's Private - Check Limits
                attemptSaveLogic(editCharId, performSave)
            }
        }

        // --- BACK BUTTON INTERCEPTOR ---
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AlertDialog.Builder(this@CharacterCreationActivity)
                    .setTitle("Discard Progress?")
                    .setMessage("If you go back now, all of your unsaved character details will be lost. Are you sure?")
                    .setPositiveButton("Discard") { _, _ ->
                        // Actually close the activity and go back
                        finish()
                    }
                    .setNegativeButton("Keep Editing", null) // Do nothing, just dismiss the popup
                    .show()
            }
        })
    }


    // Add this helper anywhere in CharacterCreationActivity
    private fun parseHeightToFeet(raw: String?): Float? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim().lowercase()

        // 5'7" or 5' 7"
        Regex("""^\s*(\d+)\s*'\s*(\d+)\s*"?\s*$""").matchEntire(s)?.let {
            val ft = it.groupValues[1].toFloat()
            val inch = it.groupValues[2].toFloat()
            return ft + inch/12f
        }
        // 5 ft 7 in / 5ft7in
        Regex("""^\s*(\d+)\s*ft\s*(\d+)\s*in\s*$""").matchEntire(s)?.let {
            val ft = it.groupValues[1].toFloat()
            val inch = it.groupValues[2].toFloat()
            return ft + inch/12f
        }
        // 170 cm
        Regex("""^\s*(\d+(\.\d+)?)\s*cm\s*$""").matchEntire(s)?.let {
            val cm = it.groupValues[1].toFloat()
            return (cm / 30.48f)
        }
        // 5.5 ft
        Regex("""^\s*(\d+(\.\d+)?)\s*ft\s*$""").matchEntire(s)?.let {
            return it.groupValues[1].toFloat()
        }
        // 67 in
        Regex("""^\s*(\d+(\.\d+)?)\s*in\s*$""").matchEntire(s)?.let {
            val inches = it.groupValues[1].toFloat()
            return inches / 12f
        }
        return null
    }

    private fun addDialogueRow(prompt: String, response: String) {
        if (dialogueContainer.childCount >= 5) {
            Toast.makeText(this, "Maximum of 5 dialogue examples allowed.", Toast.LENGTH_SHORT).show()
            return
        }

        val view = layoutInflater.inflate(R.layout.item_dialogue_row, dialogueContainer, false)
        val promptEt = view.findViewById<EditText>(R.id.promptInput)
        val responseEt = view.findViewById<EditText>(R.id.responseInput)
        val removeBtn = view.findViewById<Button>(R.id.removeRowBtn)

        promptEt.setText(prompt)
        responseEt.setText(response)

        removeBtn.setOnClickListener {
            dialogueContainer.removeView(view)
        }

        dialogueContainer.addView(view)
    }

    private fun showTagSelectionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Select Tags")
            .setMultiChoiceItems(availableTags, checkedTags) { _, which, isChecked ->
                checkedTags[which] = isChecked
            }
            .setPositiveButton("OK") { _, _ ->
                updateTagsDisplay()
            }
            .setNeutralButton("Clear All") { _, _ ->
                checkedTags.fill(false)
                updateTagsDisplay()
            }
            .show()
    }

    private fun updateTagsDisplay() {
        currentTags.clear()
        availableTags.forEachIndexed { index, tag ->
            if (checkedTags[index]) currentTags.add(tag)
        }
        selectedTagsTv.text = if (currentTags.isEmpty()) "None selected" else currentTags.joinToString(", ")
    }

    private fun attemptSaveLogic(editCharId: String?, onPermissionGranted: (Boolean?) -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val PRIVATE_LIMIT = 5 // Set your limit for free users here

        showProgressDialog() // Show loading while checking DB

        // 1. Check if User is Premium
        db.collection("users").document(userId).get()
            .addOnSuccessListener { userDoc ->
                val isPremium = userDoc.getBoolean("isPremium") ?: false

                if (isPremium) {
                    // Premium users have no limits
                    dismissProgressDialog()
                    onPermissionGranted(null)
                } else {
                    // 2. User is Free - Count their PRIVATE characters
                    db.collection("characters")
                        .whereEqualTo("author", userId)
                        .whereEqualTo("private", true)
                        .get()
                        .addOnSuccessListener { snapshot ->
                            dismissProgressDialog()

                            // Calculate count
                            // Edge Case: If we are EDITING an existing private character,
                            // it shouldn't count as a "New" slot usage.
                            val isEditingExistingPrivate = editCharId != null &&
                                    snapshot.documents.any { it.id == editCharId }

                            // If editing existing, we subtract 1 from the count for comparison logic
                            // or just allow it if found.
                            val effectiveCount = if (isEditingExistingPrivate) snapshot.size() - 1 else snapshot.size()

                            if (effectiveCount >= PRIVATE_LIMIT) {
                                // Limit Reached
                                showPremiumLimitDialog(PRIVATE_LIMIT, onPermissionGranted)
                            } else {
                                // Within limits
                                onPermissionGranted(null)
                            }
                        }
                        .addOnFailureListener {
                            dismissProgressDialog()
                            toast("Failed to check limits. Try again.")
                        }
                }
            }
            .addOnFailureListener {
                dismissProgressDialog()
                toast("Connection error.")
            }
    }

    private fun showPremiumLimitDialog(limit: Int, onSaveCallback: (Boolean?) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Private Character Limit Reached")
            .setMessage("Free users can create up to $limit private characters.\n\nYou can Upgrade to Premium for unlimited storage, or save this character as Public for free.")
            .setPositiveButton("Upgrade") { _, _ ->
                // Launch Upgrade Activity
                startActivity(Intent(this, UpgradeActivity::class.java))
            }
            .setNeutralButton("Save as Public") { _, _ ->
                // Callback with 'false' to indicate forcing private=false
                onSaveCallback(false)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveCharacterAndReturnToHub(
        editCharId: String?,
        editOriginalId: String?,
        name: String,
        summary: String,
        personality: String,
        privateDescription: String,
        backstory: String,
        soloScenario: String,
        greeting: String,
        age: Float,
        height: String,
        weight: String,
        eyeColor: String,
        hairColor: String,
        physicalDescription: String,
        abilities: String,
        gender: String,
        creatorNotes: String,
        bubbleColor: String,
        textColor: String,
        universe: String

    ) {
        val charId = editCharId ?: System.currentTimeMillis().toString()
        val originalId = editOriginalId ?: charId
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val storage = FirebaseStorage.getInstance().reference
        val firestore = FirebaseFirestore.getInstance()

        // If editing, load old profile to compare pose images
        val oldProfileTask = if (editCharId != null)
            firestore.collection("characters").document(editCharId).get()
        else null

        val onSave = { avatarUrl: String, oldOutfits: List<Outfit>? ->
            // Delete removed poses if editing
            val deletedTasks = deleteOldPoseImages(charId, oldOutfits, outfitsList, storage)
            // Upload local pose images
            val poseUploadTasks = uploadPoseImages(charId, outfitsList, storage, contentResolver)
            com.google.android.gms.tasks.Tasks.whenAllSuccess<Any>(deletedTasks + poseUploadTasks)
                .addOnSuccessListener { results ->
                    val poseTriples = results.filterIsInstance<Triple<String, String, String>>()

                    // 1. Group the results by Outfit Name
                    val uploadedPosesByOutfit = poseTriples.groupBy { it.first } // Triple.first is Outfit Name

                    // 2. Reconstruct the list preserving original order
                    val updatedOutfits = outfitsList.map { outfit ->
                        // Find all uploaded poses belonging to THIS outfit name
                        val thisOutfitUploads = uploadedPosesByOutfit[outfit.name] ?: emptyList()
                        val poseUrlMap = thisOutfitUploads.associate { it.second to it.third } // poseName -> url

                        val updatedPoseSlots = outfit.poseSlots.map { poseSlot ->
                            val uploadedUrl = poseUrlMap[poseSlot.name]
                            // If we just uploaded it, use the new URL; otherwise keep the original (remote or local)
                            if (uploadedUrl != null) poseSlot.copy(uri = uploadedUrl) else poseSlot
                        }.toMutableList()

                        outfit.copy(poseSlots = updatedPoseSlots)
                    }
                    val dialogueList = mutableListOf<DialogueExample>()
                    for (i in 0 until dialogueContainer.childCount) {
                        val view = dialogueContainer.getChildAt(i)
                        val p = view.findViewById<EditText>(R.id.promptInput).text.toString().trim()
                        val r = view.findViewById<EditText>(R.id.responseInput).text.toString().trim()

                        // Only save if meaningful
                        if (p.isNotEmpty() && r.isNotEmpty()) {
                            dialogueList.add(DialogueExample(p, r))
                        }
                    }


                    val isSfwOnly = if (age < 18) true else sfwSwitch.isChecked
                    val isEdit = editCharId != null
                    val isCurrentlyPrivate = privateSwitch.isChecked

                    // 1. SAFELY EXTRACT STATE (No crashes!)
                    val wasAnnouncedPreviously = if (::currentCharacter.isInitialized) currentCharacter.announced else false
                    var shouldAnnounce = false

                    // 2. DO THE MATH
                    if (isEdit) {
                        if (!isCurrentlyPrivate && !wasAnnouncedPreviously) {
                            shouldAnnounce = true
                            if (::currentCharacter.isInitialized) {
                                currentCharacter.announced = true // Safely update local state if it exists
                            }
                        }
                    } else {
                        if (!isCurrentlyPrivate) {
                            shouldAnnounce = true
                        }
                    }

                    val formattedLinksMap = linkedCharacters.groupBy { it.targetId }

                    val commonFields: Map<String, Any?> = mapOf(
                        "id" to charId,
                        "originalId" to originalId,
                        "name" to name,
                        "summary" to summary,
                        "personality" to personality,
                        "privateDescription" to privateDescription,
                        "soloScenario" to soloScenario,
                        "greeting" to greeting,
                        "backstory" to backstory,
                        "age" to age,
                        "height" to height,
                        "weight" to weight,
                        "gender" to gender,
                        "physicalDescription" to physicalDescription,
                        "abilities" to abilities,
                        "creatorNotes" to creatorNotes,
                        "exampleDialogue" to dialogueList,
                        "eyeColor" to eyeColor,
                        "hairColor" to hairColor,
                        "bubbleColor" to bubbleColor,
                        "textColor" to textColor,
                        "author" to currentUserId,
                        "tags" to currentTags,
                        "universe" to universe,
                        "avatarUri" to avatarUrl,
                        "startingAreaId" to assignedAreaId,
                        "startingLocationId" to assignedLocationId,
                        "areas" to characterAreas.map { area ->
                            mapOf(
                                "id" to area.id,
                                "name" to area.name,
                                "locations" to area.locations.map { loc ->
                                    mapOf("id" to loc.id, "name" to loc.name, "uri" to loc.uri)
                                }
                            )
                        },
                        "lastTimestamp" to FieldValue.serverTimestamp(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "relationships" to relationships.map { it.copy(fromId = charId) },
                        "events" to characterEvents,
                        "lorebookIds" to lorebookIds,
                        "linkedToMap" to formattedLinksMap,
                        "sfwOnly" to isSfwOnly,
                        "private" to privateSwitch.isChecked,
                        "announced" to if (isEdit) (wasAnnouncedPreviously || shouldAnnounce) else shouldAnnounce,
                        "outfits" to updatedOutfits
                    )
                    val createFields = commonFields + mapOf(
                        // only set createdAt when creating
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    val docRef = firestore.collection("characters").document(charId)




                    if (isEdit) {
                        // CHANGE THIS:
                        docRef.set(commonFields.filterValues { it != null }, com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener {
                                dismissProgressDialog()
                                Toast.makeText(this@CharacterCreationActivity, "Character saved!", Toast.LENGTH_SHORT).show()

                                val intent = Intent(this, CreationHubActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                startActivity(intent)
                                finish() }
                            .addOnFailureListener { e ->
                                dismissProgressDialog()
                                toast("Failed to save character: ${e.message}")}
                    } else {
                        docRef.set(createFields)
                            .addOnSuccessListener { dismissProgressDialog()
                                if (shouldAnnounce) {

                                    val rawInfo = summary.trim()
                                    val previewText = if (rawInfo.length > 100) rawInfo.take(100) + "..." else rawInfo

                                    val feedEvent = com.albirich.RealmsAI.models.FeedEvent(
                                        authorId = currentUserId,
                                        type = com.albirich.RealmsAI.models.FeedEventType.NEW_CHARACTER,
                                        title = "Published a new Character: $name!",
                                        content = previewText,
                                        referenceId = charId,
                                        timestamp = null
                                    )
                                    firestore.collection("feed_events").document(feedEvent.id).set(feedEvent)
                                }
                                Toast.makeText(this, "Character saved!", Toast.LENGTH_SHORT).show()
                                val intent = Intent(this, CreationHubActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                startActivity(intent)
                                finish() }
                            .addOnFailureListener { e ->
                                dismissProgressDialog()
                                toast("Failed to save character: ${e.message}") }
                    }
                }
                .addOnFailureListener { e ->
                    dismissProgressDialog()
                    toast("Pose upload failed: ${e.message}")
                }
        }

        showProgressDialog()

        val doAvatar = { oldOutfits: List<Outfit>? ->
            uploadAvatarIfNeeded(
                avatarChanged, avatarUri, charId, contentResolver, storage,
                onComplete = { avatarUrl -> onSave(avatarUrl, oldOutfits) },
                onError = { e ->
                    dismissProgressDialog()
                    toast("Avatar upload failed: ${e.message}")
                },
                originalAvatarUrl = originalAvatarUrl
            )

        }

        // Run the chain
        if (oldProfileTask != null) {
            oldProfileTask.addOnSuccessListener { docSnap ->
                val oldProfile = docSnap.toObject(CharacterProfile::class.java)
                doAvatar(oldProfile?.outfits)
            }.addOnFailureListener {
                doAvatar(null)
            }
        } else {
            doAvatar(null)
        }
    }


    private fun uploadAvatarIfNeeded(
        avatarChanged: Boolean,
        avatarUri: Uri?,
        charId: String,
        contentResolver: ContentResolver,
        storage: StorageReference,
        onComplete: (String) -> Unit,
        onError: (Exception) -> Unit,
        originalAvatarUrl: String? = null
    ) {
        if (avatarChanged && avatarUri != null) {
            val ext = contentResolver.getType(avatarUri)
                ?.let { android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
                ?: "jpg"
            val avatarCache = java.io.File(cacheDir, "avatar_$charId.$ext")
            contentResolver.openInputStream(avatarUri)?.use { input ->
                java.io.FileOutputStream(avatarCache).use { output -> input.copyTo(output) }
            }
            val fileUri = Uri.fromFile(avatarCache)
            val ref = storage.child("characters/$charId/avatar.$ext")
            ref.putFile(fileUri)
                .continueWithTask { t -> if (!t.isSuccessful) throw t.exception!!; ref.downloadUrl }
                .addOnSuccessListener { uri -> onComplete(uri.toString()) }
                .addOnFailureListener { e -> onError(e) }
        } else {
            val fallbackUrl = originalAvatarUrl ?: ""
            onComplete(fallbackUrl)
        }
    }

    private fun deleteOldPoseImages(
        charId: String,
        oldOutfits: List<Outfit>?,
        newOutfits: List<Outfit>,
        storage: com.google.firebase.storage.StorageReference
    ): List<com.google.android.gms.tasks.Task<Void>> {
        val deletedTasks = mutableListOf<com.google.android.gms.tasks.Task<Void>>()
        if (oldOutfits != null) {
            val oldSet = oldOutfits.flatMap { o -> o.poseSlots.map { "${o.name}:${it.name}" } }.toSet()
            val newSet = newOutfits.flatMap { o -> o.poseSlots.map { "${o.name}:${it.name}" } }.toSet()
            val toDelete = oldSet - newSet
            oldOutfits.forEach { outfit ->
                outfit.poseSlots.forEach { pose ->
                    if ("${outfit.name}:${pose.name}" in toDelete && pose.uri != null && pose.uri!!.startsWith("http")) {
                        try {
                            val ref = storage.storage.getReferenceFromUrl(pose.uri!!)
                            deletedTasks.add(ref.delete())
                        } catch (_: Exception) { }
                    }
                }
            }
        }
        return deletedTasks
    }

    private fun uploadPoseImages(
        charId: String,
        outfits: List<Outfit>,
        storage: com.google.firebase.storage.StorageReference,
        contentResolver: android.content.ContentResolver
    ): List<com.google.android.gms.tasks.Task<Triple<String, String, String>>> {
        return outfits.flatMap { outfit ->
            outfit.poseSlots.mapNotNull { poseSlot ->
                val poseName = poseSlot.name.trim()
                val uriStr = poseSlot.uri?.trim() ?: ""

                // Skip if blank or if it's already an HTTP URL (already uploaded)
                if (poseName.isBlank() || uriStr.isBlank() || uriStr.startsWith("http")) return@mapNotNull null

                // --- NEW: Handle Base64 encoded images ---
                if (uriStr.startsWith("data:image")) {
                    // Extract the raw base64 data (everything after the comma)
                    val base64Data = uriStr.substringAfter(",")
                    val imageBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)

                    // Try to grab the extension (e.g., png from data:image/png)
                    val mimeType = uriStr.substringAfter("data:image/").substringBefore(";")
                    val ext = if (mimeType.isNotBlank()) mimeType else "png"

                    val ref = storage.child("characters/$charId/poses/${outfit.name}/$poseName.$ext")

                    // Upload using putBytes() instead of putFile()!
                    val uploadTask = ref.putBytes(imageBytes)
                        .continueWithTask { t ->
                            if (!t.isSuccessful) throw t.exception!!
                            ref.downloadUrl
                        }
                        .continueWith { t -> Triple(outfit.name, poseName, t.result.toString()) }

                    return@mapNotNull uploadTask
                }
                // --- Handle standard local URIs (content:// or file://) ---
                else {
                    val fileUri = Uri.parse(uriStr)
                    val ext = java.io.File(fileUri.path ?: "").extension.ifBlank { "jpg" }
                    val ref = storage.child("characters/$charId/poses/${outfit.name}/$poseName.$ext")

                    val uploadTask = ref.putFile(fileUri)
                        .continueWithTask { t ->
                            if (!t.isSuccessful) throw t.exception!!
                            ref.downloadUrl
                        }
                        .continueWith { t -> Triple(outfit.name, poseName, t.result.toString()) }

                    return@mapNotNull uploadTask
                }
            }
        }
    }


    private fun showProgressDialog() {
        progressDialog = AlertDialog.Builder(this)
            .setTitle("Creating Character")
            .setMessage("Please don’t close the app. Your character is being created…")
            .setCancelable(false)
            .show()
    }

    private fun dismissProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RELATIONSHIP_REQ_CODE && resultCode == Activity.RESULT_OK && data != null) {
            val relJson = data.getStringExtra("RELATIONSHIPS_JSON")
            if (!relJson.isNullOrEmpty()) {
                relationships = Gson().fromJson(relJson, Array<Relationship>::class.java).toList()
                toast("Loaded ${relationships.size} relationships")
            }
        }
    }

    private fun importCharacterCard(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = applicationContext.contentResolver
                val mimeType = contentResolver.getType(uri) ?: ""
                var jsonString: String? = null

                if (mimeType.contains("json")) {
                    // It's a raw JSON file, just read the text
                    contentResolver.openInputStream(uri)?.use { input ->
                        jsonString = input.bufferedReader().use { it.readText() }
                    }
                } else {
                    // IT'S A PNG CARD! Time for dark magic byte parsing.
                    contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readBytes()
                        var i = 8 // Skip the 8-byte PNG signature

                        while (i + 8 < bytes.size) {
                            // Read chunk length (4 bytes)
                            val length = java.nio.ByteBuffer.wrap(bytes, i, 4).int
                            // Read chunk type (4 bytes)
                            val type = String(bytes, i + 4, 4)

                            if (type == "tEXt") {
                                val chunkData = bytes.copyOfRange(i + 8, i + 8 + length)
                                val separatorIndex = chunkData.indexOf(0.toByte())

                                if (separatorIndex != -1) {
                                    val keyword = String(chunkData.copyOfRange(0, separatorIndex))
                                    if (keyword == "chara") {
                                        // We found the Tavern V2 payload!
                                        val base64Data = String(chunkData.copyOfRange(separatorIndex + 1, chunkData.size))
                                        jsonString = String(android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT))
                                        break
                                    }
                                }
                            }
                            // Move to next chunk (Length + Type + Data + 4-byte CRC)
                            i += 12 + length
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    if (jsonString != null) {
                        applyCardDataToUI(jsonString!!, uri, mimeType)
                    } else {
                        Toast.makeText(this@CharacterCreationActivity, "No Character Data found in this file.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CharacterCreationActivity, "Failed to parse card: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e("CardImport", "Error parsing card", e)
                }
            }
        }
    }

    private fun applyCardDataToUI(jsonString: String, fileUri: Uri, mimeType: String) {
        try {
            val card = Gson().fromJson(jsonString, TavernCard::class.java)
            val data = card.data ?: return

            // Map the imported fields to your EditTexts
            nameEt.setText(data.name ?: "")


            personalityEt.setText(data.description ?: "")
            greetingEt.setText(data.first_mes ?: "")
            scenarioEt.setText(data.scenario ?: "")
            notesEt.setText(data.creator_notes ?: "")

            // Parse message examples (Tavern formats this as a single string block like "<START>\n{{user}}: hi\n{{char}}: hello")
            val examples = data.mes_example ?: ""
            if (examples.isNotBlank()) {
                // Clear existing UI dialogue rows first to avoid clutter
                dialogueContainer.removeAllViews()

                // Just dump the whole block into a single example row for them to edit
                addDialogueRow("Example Block", examples)
            }

            // If they uploaded a PNG, automatically set it as the character's Avatar!
            if (mimeType.contains("image")) {
                avatarUri = fileUri
                avatarChanged = true
                avatarView.setImageURI(fileUri)
            }

            Toast.makeText(this, "Character Card Imported!", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Card format not recognized.", Toast.LENGTH_LONG).show()
            Log.e("CardImport", "GSON mapping failed", e)
        }
    }

    private fun fillFormFromProfile(profile: CharacterProfile) {
        nameEt.setText(profile.name)
        bioEt.setText(profile.summary)
        personalityEt.setText(profile.personality)
        privateDescEt.setText(profile.privateDescription)
        ageEt.setText(profile.age.toString())
        heightEt.setText(profile.height)
        weightEt.setText(profile.weight)
        eyeColorEt.setText(profile.eyeColor)
        hairColorEt.setText(profile.hairColor)
        physicalDescEt.setText(profile.physicalDescription)
        abilitiesEt.setText(profile.abilities)
        notesEt.setText(profile.creatorNotes)
        genderEt.setText(profile.gender)
        backstoryEt.setText(profile.backstory)
        scenarioEt.setText(profile.soloScenario)
        greetingEt.setText(profile.greeting)
        linkedCharacters = profile.linkedToMap.values.flatten().toMutableList()

        // Load Dialogue
        dialogueContainer.removeAllViews()
        profile.exampleDialogue.forEach { ex ->
            addDialogueRow(ex.prompt, ex.response)
        }

        // --- Universe ---
        universeEt.setText(profile.universe)

        // --- Restore Tags ---
        // Reset checks first
        checkedTags.fill(false)
        profile.tags.forEach { savedTag ->
            val index = availableTags.indexOfFirst { it.equals(savedTag, ignoreCase = true) }
            if (index >= 0) {
                checkedTags[index] = true
            }
        }
        updateTagsDisplay() // Helper function from previous step to update the TextView

        lorebookIds = profile.lorebookIds?.toMutableList() ?: mutableListOf()
        outfitsList = profile.outfits ?: emptyList()
        if (!profile.avatarUri.isNullOrBlank()) {
            avatarUri = Uri.parse(profile.avatarUri)
            originalAvatarUrl = profile.avatarUri
            Glide.with(this)
                .load(profile.avatarUri)
                .placeholder(R.drawable.placeholder_avatar)
                .error(R.drawable.placeholder_avatar)
                .into(avatarView)
        }
        relationships = profile.relationships ?: emptyList()
        characterEvents = profile.events?.toMutableList() ?: mutableListOf()
        sfwSwitch.isChecked = profile.sfwOnly
        privateSwitch.isChecked = profile.private
        bubbleColorSpinner.setSelection(bubblecolorOptions.indexOfFirst { it.second == profile.bubbleColor }.takeIf { it >= 0 } ?: 0)
        textColorSpinner.setSelection(textcolorOptions.indexOfFirst { it.second == profile.textColor }.takeIf { it >= 0 } ?: 0)
        colorExample.setBackgroundColor(android.graphics.Color.parseColor(profile.bubbleColor))
        colorExample.setTextColor(android.graphics.Color.parseColor(profile.textColor))
    }

    private suspend fun generateMissingVectors(outfits: List<Outfit>) = withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        // ⚠️ Replace with your actual key fetching logic if it's stored dynamically
        val apiKey = BuildConfig.OPENAI_API_KEY
        val mediaType = "application/json; charset=utf-8".toMediaType()

        for (outfit in outfits) {
            for (pose in outfit.poseSlots) {
                // ONLY embed if they don't already have a vector
                if (pose.vector == null) {
                    try {
                        val jsonBody = JSONObject().apply {
                            put("model", "text-embedding-3-small")
                            put("input", "Physical character roleplay pose: ${pose.name}. ${pose.description}")
                        }

                        val request = Request.Builder()
                            .url("https://api.openai.com/v1/embeddings")
                            .addHeader("Authorization", "Bearer $apiKey")
                            .post(jsonBody.toString().toRequestBody(mediaType))
                            .build()

                        val response = client.newCall(request).execute()
                        if (response.isSuccessful) {
                            val responseBody = response.body?.string()
                            if (responseBody != null) {
                                val jsonResponse = JSONObject(responseBody)
                                val dataArray = jsonResponse.getJSONArray("data")
                                val embeddingArray = dataArray.getJSONObject(0).getJSONArray("embedding")

                                // Convert JSON array into a Kotlin List<Double>
                                val vectorList = mutableListOf<Double>()
                                for (i in 0 until embeddingArray.length()) {
                                    vectorList.add(embeddingArray.getDouble(i))
                                }

                                // Save the math to the pose object in memory
                                pose.vector = vectorList
                                Log.d("Embeddings", "Successfully embedded pose: ${pose.name}")
                            }
                        } else {
                            Log.e("Embeddings", "API Error on ${pose.name}: ${response.code} - ${response.body?.string()}")
                        }
                    } catch (e: Exception) {
                        Log.e("Embeddings", "Crash embedding ${pose.name}", e)
                    }
                }
            }
        }
    }

    private fun List<Outfit>.stripVectors(): List<Outfit> {
        return this.map { outfit ->
            outfit.copy(
                poseSlots = outfit.poseSlots.map { pose ->
                    pose.copy(vector = null) // Drop the heavy math!
                }.toMutableList()
            )
        }
    }

    fun makeEditTextScrollable(editText: EditText) {
        editText.setScroller(Scroller(editText.context))
        editText.isVerticalScrollBarEnabled = true
        editText.movementMethod = ScrollingMovementMethod()
        editText.setOnTouchListener { v, event ->
            // Allow EditText to handle its own touch events (prevents ScrollView from intercepting)
            v.parent.requestDisallowInterceptTouchEvent(true)
            false
        }

    }
}

data class TavernCard(
    val spec: String?,
    val data: TavernCharacterData?
)

data class TavernCharacterData(
    val name: String?,
    val description: String?,
    val personality: String?,
    val first_mes: String?,
    val mes_example: String?,
    val scenario: String?,
    val creator_notes: String?
)