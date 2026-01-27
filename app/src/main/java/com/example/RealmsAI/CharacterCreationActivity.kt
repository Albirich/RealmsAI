package com.example.RealmsAI

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.RealmsAI.MainActivity
import com.example.RealmsAI.models.Area
import com.example.RealmsAI.models.CharacterProfile
import com.example.RealmsAI.models.DialogueExample
import com.example.RealmsAI.models.Outfit
import com.example.RealmsAI.models.Relationship
import com.google.android.material.button.MaterialButton
import com.google.common.reflect.TypeToken
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream

class CharacterCreationActivity : AppCompatActivity() {
    // UI
    private lateinit var avatarView: ImageView
    private lateinit var relationshipBtn: Button
    private lateinit var wardrobeButton: MaterialButton
    private lateinit var bubbleColorSpinner: Spinner
    private lateinit var textColorSpinner: Spinner
    private lateinit var submitBtn: MaterialButton
    private lateinit var addAreaButton: Button


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
        "Blue" to "#2196F3",
        "Green" to "#4CAF50",
        "Orange" to "#FF9800",
        "Pink" to "#e86cbe",
        "Purple" to "#c778f5",
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
        "Red" to "#ce0202",
        "Yellow" to "#cdd54b"
    )

    // Launcher
    private lateinit var wardrobeLauncher: ActivityResultLauncher<Intent>
    private lateinit var avatarPicker: ActivityResultLauncher<String>

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
        "You are accused of a crime you didn't commit.",
        "A stranger offers you a rare flower.",
        "You trip and fall in front of your crush.",
        "Someone insults your outfit.",
        "You find a lost child in the market.",
        "You are cornered by an enemy in an alley.",
        "A merchant tries to overcharge you significantly.",
        "You wake up in a strange place with no memory.",
        "Your best friend betrays you.",
        "You are asked to lead a dangerous mission.",
        "You discover a hidden treasure chest.",
        "Someone confesses their love to you.",
        "You are challenged to a duel.",
        "You see someone stealing bread to survive.",
        "A mysterious figure hands you a sealed letter."
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
            // Pick 3 random prompts
            val randomPrompts = scenarioPrompts.shuffled().take(3)
            randomPrompts.forEach { prompt ->
                addDialogueRow(prompt, "")
            }
            Toast.makeText(this, "Added 3 scenarios. Fill in the responses!", Toast.LENGTH_SHORT).show()
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
                if (!areasJson.isNullOrBlank()) {
                    characterAreas = Gson().fromJson(areasJson, Array<Area>::class.java).toMutableList()
                }
                if (!characterToAreaJson.isNullOrBlank()) {
                    val mapType = object : TypeToken<Map<String, String>>() {}.type
                    val map = Gson().fromJson<Map<String, String>>(characterToAreaJson, mapType)
                    assignedAreaId = map[currentCharacter.id]
                }
                // Update your UI if needed (show background, etc)
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
                if (isVisible) R.drawable.ic_expand_more else R.drawable.ic_expand_less
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
        val charJson = intent.getStringExtra("CHAR_PROFILE_JSON")
        if (editCharId != null && !charJson.isNullOrBlank()) {
            val profile = Gson().fromJson(charJson, CharacterProfile::class.java)
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

        selectTagsBtn.setOnClickListener {
            showTagSelectionDialog()
        }


        // Submit
        // Inside onCreate...

        submitBtn.setOnClickListener {
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
            val universe = universeEt.text.toString().trim()

            // 2. Basic Validation
            if (name.isEmpty()) return@setOnClickListener toast("Name required")
            if (originalAvatarUrl.isNullOrBlank() && !avatarChanged) return@setOnClickListener toast("Pick an avatar")

            // 3. Define the actual save action (to be called after checks pass)
            val performSave = { isPrivateOverride: Boolean? ->
                // If user chose "Save as Public" in dialog, force switch off
                if (isPrivateOverride == false) {
                    privateSwitch.isChecked = false
                }

                showProgressDialog()
                saveCharacterAndReturnToHub(
                    editCharId,
                    name, bio, personality, privateDesc, backstory, soloScenario, greeting,
                    age, height, weight, eyeColor, hairColor, physicalDesc, abilities, gender,
                    bubbleColor, textColor, universe
                )
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
        bubbleColor: String,
        textColor: String,
        universe: String

    ) {
        val charId = editCharId ?: System.currentTimeMillis().toString()
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
                    // Map of outfitName -> Map<poseName, url>
                    val outfitsMap = mutableMapOf<String, MutableMap<String, String>>()
                    for ((outfitName, poseName, url) in poseTriples) {
                        val poseMap = outfitsMap.getOrPut(outfitName) { mutableMapOf() }
                        poseMap[poseName] = url
                    }
                    // Update poseSlots with uploaded URLs (leave remote URLs as-is)
                    val updatedOutfits = outfitsList.map { outfit ->
                        val poseMap = outfitsMap[outfit.name] ?: emptyMap()
                        val updatedPoseSlots = outfit.poseSlots.map { poseSlot ->
                            val uploadedUrl = poseMap[poseSlot.name]
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
                    val commonFields: Map<String, Any?> = mapOf(
                        "id" to charId,
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
                        "exampleDialogue" to dialogueList,
                        "eyeColor" to eyeColor,
                        "hairColor" to hairColor,
                        "bubbleColor" to bubbleColor,
                        "textColor" to textColor,
                        "author" to currentUserId,
                        "tags" to currentTags,
                        "universe" to universe,
                        "avatarUri" to avatarUrl,
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
                        "sfwOnly" to isSfwOnly,
                        "private" to privateSwitch.isChecked,
                        "outfits" to updatedOutfits
                    )
                    val createFields = commonFields + mapOf(
                        // only set createdAt when creating
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    val docRef = firestore.collection("characters").document(charId)

                    if (isEdit) {
                        // Don’t include createdAt here!
                        docRef.update(commonFields.filterValues { it != null })
                            .addOnSuccessListener { dismissProgressDialog()
                                Toast.makeText(this, "Character saved!", Toast.LENGTH_SHORT).show()
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
                if (poseName.isBlank() || uriStr.isBlank() || uriStr.startsWith("http")) return@mapNotNull null
                val fileUri = Uri.parse(uriStr)
                val ext = java.io.File(fileUri.path ?: "").extension.ifBlank { "jpg" }
                val ref = storage.child("characters/$charId/poses/${outfit.name}/$poseName.$ext")
                val uploadTask = ref.putFile(fileUri)
                    .continueWithTask { t ->
                        if (!t.isSuccessful) throw t.exception!!
                        ref.downloadUrl
                    }
                    .continueWith { t -> Triple(outfit.name, poseName, t.result.toString()) }
                uploadTask
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
        genderEt.setText(profile.gender)
        backstoryEt.setText(profile.backstory)
        scenarioEt.setText(profile.soloScenario)
        greetingEt.setText(profile.greeting)

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

        // ... (Existing Logic) ...
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
        sfwSwitch.isChecked = profile.sfwOnly
        privateSwitch.isChecked = profile.private
        bubbleColorSpinner.setSelection(bubblecolorOptions.indexOfFirst { it.second == profile.bubbleColor }.takeIf { it >= 0 } ?: 0)
        textColorSpinner.setSelection(textcolorOptions.indexOfFirst { it.second == profile.textColor }.takeIf { it >= 0 } ?: 0)
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
