package com.example.RealmsAI

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.toMutableStateList
import com.example.RealmsAI.models.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ChatCreationActivity : AppCompatActivity() {

    private lateinit var titleEt: EditText
    private lateinit var descEt: EditText
    private lateinit var secretDescEt: EditText
    private lateinit var firstMsgEt: EditText
    private lateinit var sfwSwitch: Switch
    private lateinit var privateSwitch: Switch
    private lateinit var tagsEt: EditText
    private lateinit var createBtn: Button
    private lateinit var relationshipBtn: Button
    private lateinit var btnLoadCollections: Button
    private lateinit var btnOpenMap: Button

    private var selectedCharacters: MutableList<CharacterProfile> = mutableListOf()
    private var loadedAreas: MutableList<Area> = mutableListOf()
    private var characterToAreaMap: MutableMap<String, String> = mutableMapOf()
    private var characterToLocationMap: MutableMap<String, String> = mutableMapOf()
    private var chatRelationships: MutableList<Relationship> = mutableListOf()
    private var editingChatId: String? = null
    private var modeSettings: MutableMap<String, String> = mutableMapOf()



    private val gson = Gson()

    private val chatMapLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val charactersJson = result.data!!.getStringExtra("CHARACTERS_JSON")
                val areasJson = result.data!!.getStringExtra("AREAS_JSON")
                val assignmentJson = result.data!!.getStringExtra("CHARACTER_TO_AREA_JSON")
                if (charactersJson != null) {
                    val charListType = object : TypeToken<List<CharacterProfile>>() {}.type
                    selectedCharacters = gson.fromJson(charactersJson, charListType)
                }
                if (areasJson != null) {
                    val areaListType = object : TypeToken<List<Area>>() {}.type
                    loadedAreas = gson.fromJson(areasJson, areaListType)
                }
                if (assignmentJson != null) {
                    val mapType = object : TypeToken<Map<String, String>>() {}.type
                    characterToAreaMap = gson.fromJson(assignmentJson, mapType)
                }
            }
        }

    private val collectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val charactersJson = result.data!!.getStringExtra("CHARACTER_LIST_JSON") ?: return@registerForActivityResult
                val charListType = object : com.google.gson.reflect.TypeToken<List<CharacterProfile>>() {}.type
                selectedCharacters = gson.fromJson(charactersJson, charListType)
                // Optionally update a UI preview here!
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_chat)

        // UI Bindings
        titleEt = findViewById(R.id.titleEditText)
        descEt = findViewById(R.id.descriptionEditText)
        secretDescEt = findViewById(R.id.secretDescriptionEditText)
        firstMsgEt = findViewById(R.id.firstMessageEditText)
        sfwSwitch = findViewById(R.id.sfwSwitch)
        privateSwitch = findViewById(R.id.privateSwitch)
        tagsEt = findViewById(R.id.tagsEditText)
        createBtn = findViewById(R.id.createChatButton)
        relationshipBtn = findViewById(R.id.chatrelationshipBtn)
        btnLoadCollections = findViewById(R.id.btnLoadCollections)
        btnOpenMap = findViewById(R.id.btnOpenMap)
        var enabledModes = mutableListOf<String>()
        if (findViewById<CheckBox>(R.id.checkboxRPG).isChecked) enabledModes.add("rpg")
        if (findViewById<CheckBox>(R.id.checkboxVisualNovel).isChecked) enabledModes.add("visual_novel")
        if (findViewById<CheckBox>(R.id.checkboxGodMode).isChecked) enabledModes.add("god_mode")
        val checkboxRPG = findViewById<CheckBox>(R.id.checkboxRPG)
        val rpgButton = findViewById<Button>(R.id.rpgButton)
        val checkboxVN = findViewById<CheckBox>(R.id.checkboxVisualNovel)
        val VNButton = findViewById<Button>(R.id.visualNoveButton)
        val checkboxGodMode = findViewById<CheckBox>(R.id.checkboxGodMode)
        val godModeButton = findViewById<Button>(R.id.godModeButton)

        // --- Check if editing a chat ---
        val chatProfileJson = intent.getStringExtra("CHAT_PROFILE_JSON")
        editingChatId = intent.getStringExtra("CHAT_EDIT_ID") // Track edit mode for save

        if (chatProfileJson != null) {
            val profile = gson.fromJson(chatProfileJson, ChatProfile::class.java)
            // Populate UI fields
            titleEt.setText(profile.title)
            descEt.setText(profile.description)
            secretDescEt.setText(profile.secretDescription)
            firstMsgEt.setText(profile.firstmessage)
            sfwSwitch.isChecked = profile.sfwOnly
            privateSwitch.isChecked = profile.private
            tagsEt.setText(profile.tags.joinToString(", "))
            // Store areas and characterToArea
            loadedAreas = profile.areas.toMutableList()
            characterToAreaMap = profile.characterToArea.toMutableMap()
            chatRelationships = profile.relationships.toMutableList()

            // Load full character objects by ID for UI use (avatars/relationships)
            if (profile.characterIds.isNotEmpty()) {
                loadCharactersFromFirestoreByIds(profile.characterIds) { chars ->
                    selectedCharacters = chars.toMutableList()
                }
            }

            val infoButtonChatGameMode: ImageButton = findViewById(R.id.infoButtonChatGameMode)
            infoButtonChatGameMode.setOnClickListener {
                AlertDialog.Builder(this@ChatCreationActivity)
                    .setTitle("Game Modes")
                    .setMessage("Choose what special rules you want to add. Modes are additive so you can mix and match them\n" +
                            "RPG Mode - gives dice rolls, character sheets, light rpg rules\n" +
                            "VN Mode - gives relationship levels that evolve the way characters interact\n" +
                            "God Mode - removes the need for the user to have a character, all their messages will be system messages (Not implemented yet)")
                    .setPositiveButton("OK", null)
                    .show()
            }

            val infoButtonChatCreation: ImageButton = findViewById(R.id.infoButtonChatCreation)
            infoButtonChatCreation.setOnClickListener {
                AlertDialog.Builder(this@ChatCreationActivity)
                    .setTitle("Relationships")
                    .setMessage( "Use character#, # being the position in the character list you want to refer to. if the character in that position is replaced the name will be replaced.")
                    .setPositiveButton("OK", null)
                    .show()
            }
            // Set modeSettings from loaded profile!
            modeSettings = profile.modeSettings.toMutableMap()
            enabledModes = profile.enabledModes.toMutableStateList()

            // If rpg settings exist, check RPG mode
            if ("rpg" in enabledModes) {
                checkboxRPG.isChecked = true
                rpgButton.isEnabled = true
                rpgButton.visibility = View.VISIBLE
            } else {
                checkboxRPG.isChecked = false
                rpgButton.isEnabled = false
                rpgButton.visibility = View.GONE
            }

            if ("visual_novel" in enabledModes) {
                checkboxVN.isChecked = true
                VNButton.isEnabled = true
                VNButton.visibility = View.VISIBLE
            } else {
                checkboxVN.isChecked = false
                VNButton.isEnabled = false
                VNButton.visibility = View.GONE
            }
        }

        // Load collections (multi-select via CharacterSelectionActivity)
        btnLoadCollections.setOnClickListener {
            val intent = Intent(this, ChatCollectionActivity::class.java)
            intent.putStringArrayListExtra("preSelectedIds", ArrayList(selectedCharacters.map { it.id }))
            collectionLauncher.launch(intent)
        }

        // Open Map Builder (ChatMapActivity)
        btnOpenMap.setOnClickListener {
            val intent = Intent(this, ChatMapActivity::class.java)
            intent.putExtra("CHARACTER_LIST_JSON", gson.toJson(selectedCharacters))
            intent.putExtra("AREA_LIST_JSON", gson.toJson(loadedAreas))
            chatMapLauncher.launch(intent)
        }

        // Relationships (launches ChatRelationshipActivity)
        relationshipBtn.setOnClickListener {
            // If new, build from selectedCharacters; if editing, use chatRelationships
            if (chatRelationships.isEmpty()) {
                chatRelationships = selectedCharacters
                    .flatMap { char ->
                        (char.relationships ?: emptyList()).map { rel ->
                            rel.copy(fromId = char.id)
                        }
                    }
                    .toMutableList()
            }
            val previews = selectedCharacters.map {
                ParticipantPreview(it.id, it.name, it.avatarUri ?: "")
            }
            val relJson = gson.toJson(chatRelationships)
            val intent = Intent(this, ChatRelationshipActivity::class.java)
            intent.putExtra("PARTICIPANTS_JSON", Gson().toJson(previews))
            intent.putExtra("RELATIONSHIPS_JSON", relJson)
            startActivityForResult(intent, 8080)
        }

        checkboxRPG.setOnCheckedChangeListener { _, isChecked ->
            rpgButton.isEnabled = isChecked
            rpgButton.visibility = View.VISIBLE
            if (!isChecked) {
                modeSettings.remove("rpg")
                rpgButton.visibility = View.GONE
            }
        }
        rpgButton.setOnClickListener {
            val intent = Intent(this, RPGSettingsActivity::class.java)
            intent.putExtra("CURRENT_SETTINGS_JSON", modeSettings["rpg"] ?: "")
            intent.putExtra("MURDER_SETTINGS_JSON", modeSettings["murder"] ?: "")
            intent.putExtra("SELECTED_CHARACTERS_JSON", gson.toJson(selectedCharacters))
            intent.putExtra("AREAS_JSON", gson.toJson(loadedAreas))
            startActivityForResult(intent, REQUEST_CODE_RPG_SETTINGS)
        }

        checkboxVN.setOnCheckedChangeListener { _, isChecked ->
            VNButton.isEnabled = isChecked
            VNButton.visibility = View.VISIBLE
            if (isChecked) {
                modeSettings["visual_novel"] = "true"
            } else {
                modeSettings.remove("visual_novel")
                VNButton.visibility = View.GONE
            }
        }

        VNButton.setOnClickListener {
            val intent = Intent(this, VNSettingsActivity::class.java)
            Log.d("VN_SETTINGS", "Sending: ${modeSettings["vn"]}")
            intent.putExtra("CURRENT_SETTINGS_JSON", modeSettings["vn"] ?: "")
            intent.putExtra("SELECTED_CHARACTERS_JSON", gson.toJson(selectedCharacters))
            intent.putExtra("AREAS_JSON", gson.toJson(loadedAreas))
            startActivityForResult(intent, REQUEST_CODE_VN_SETTINGS)
        }

        checkboxGodMode.setOnCheckedChangeListener { _, isChecked ->
            godModeButton.isEnabled = isChecked
            godModeButton.visibility = View.VISIBLE
            if (!isChecked) {
                modeSettings.remove("god_mode")
                godModeButton.visibility = View.GONE
            }
        }

        godModeButton.setOnClickListener {
            Toast.makeText(this, "God Mode settings is not created yet", Toast.LENGTH_SHORT).show()
        }

        // Create or Save Chat button
        createBtn.setOnClickListener {
            if (titleEt.text.isNullOrBlank()) {
                titleEt.error = "Title required"
                return@setOnClickListener
            }
            saveAndLaunchChat()
        }
    }
    companion object {
        private const val REQUEST_CODE_RPG_SETTINGS = 1001
        private const val REQUEST_CODE_VN_SETTINGS = 1002
        private const val REQUEST_CODE_GOD_MODE_SETTINGS = 1003
        // ...add more if you have more mode settings screens
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // For ChatRelationshipActivity
        if (requestCode == 8080 && resultCode == Activity.RESULT_OK && data != null) {
            val relationshipsJson = data.getStringExtra("RELATIONSHIPS_JSON")
            if (relationshipsJson != null) {
                val relType = object : TypeToken<List<Relationship>>() {}.type
                chatRelationships = gson.fromJson(relationshipsJson, relType)
            }
        }
        when (requestCode) {
            REQUEST_CODE_RPG_SETTINGS -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    data.getStringExtra("RPG_SETTINGS_JSON")?.let { modeSettings["rpg"] = it }
                    data.getStringExtra("MURDER_SETTINGS_JSON")?.let { modeSettings["murder"] = it } // <-- store it
                }
            }
            REQUEST_CODE_VN_SETTINGS -> {
                val updatedSettingsJson = data?.getStringExtra("VN_SETTINGS_JSON")
                if (!updatedSettingsJson.isNullOrBlank()) {
                    modeSettings["vn"] = updatedSettingsJson
                    Log.d("VN_SETTINGS", "Received: $updatedSettingsJson")
                }
            }
            REQUEST_CODE_GOD_MODE_SETTINGS -> {
                // handle Visual Novel settings result
            }
        }
    }

    private fun saveAndLaunchChat() {
        val chatId = editingChatId ?: System.currentTimeMillis().toString()
        val title = titleEt.text.toString().trim()
        val desc = descEt.text.toString().trim()
        val secretDesc = secretDescEt.text.toString().trim()
        val firstMsg = firstMsgEt.text.toString().trim()
        val tags = tagsEt.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val sfwOnly = sfwSwitch.isChecked
        val private = privateSwitch.isChecked
        val enabledModes = mutableListOf<String>()
        if (findViewById<CheckBox>(R.id.checkboxRPG).isChecked) enabledModes.add("rpg")
        if (findViewById<CheckBox>(R.id.checkboxVisualNovel).isChecked) enabledModes.add("visual_novel")
        if (findViewById<CheckBox>(R.id.checkboxGodMode).isChecked) enabledModes.add("god_mode")

        val authorId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        val profile = ChatProfile(
            id = chatId,
            title = title,
            description = desc,
            secretDescription = secretDesc,
            firstmessage = firstMsg,
            mode = "SANDBOX",
            enabledModes = enabledModes,
            modeSettings = modeSettings,
            areas = loadedAreas,
            characterIds = selectedCharacters.map { it.id },
            characterToArea = characterToAreaMap,
            characterToLocation = characterToLocationMap,
            relationships = chatRelationships,
            rating = 0f,
            timestamp = Timestamp.now(),
            author = authorId,
            tags = tags,
            sfwOnly = sfwOnly,
            private = private
        )

        val chatData = mapOf(
            "id" to profile.id,
            "title" to profile.title,
            "description" to profile.description,
            "secretDescription" to profile.secretDescription,
            "firstmessage" to profile.firstmessage,
            "mode" to profile.mode,
            "enabledModes" to profile.enabledModes,
            "modeSettings" to profile.modeSettings,
            "areas" to profile.areas.map { area ->
                mapOf(
                    "id" to area.id,
                    "name" to area.name,
                    "locations" to area.locations.map { loc ->
                        mapOf("id" to loc.id, "name" to loc.name, "uri" to loc.uri)
                    }
                )
            },
            "characterIds" to profile.characterIds,
            "characterToArea" to profile.characterToArea,
            "characterToLocation" to profile.characterToLocation,
            "relationships" to profile.relationships,
            "rating" to profile.rating,
            "timestamp" to Timestamp.now(),
            "author" to authorId,
            "tags" to profile.tags,
            "sfwOnly" to profile.sfwOnly,
            "private" to profile.private,
            "lastMessage" to "",
            "lastTimestamp" to FieldValue.serverTimestamp()
        )

        FirebaseFirestore.getInstance()
            .collection("chats")
            .document(chatId)
            .set(chatData)
            .addOnSuccessListener {
                Toast.makeText(this, "Chat saved!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, CreationHubActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Could not save chat: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun loadCharactersFromFirestoreByIds(ids: List<String>, onLoaded: (List<CharacterProfile>) -> Unit) {
        if (ids.isEmpty()) { onLoaded(emptyList()); return }
        FirebaseFirestore.getInstance()
            .collection("characters")
            .whereIn("id", ids)
            .get()
            .addOnSuccessListener { snap ->
                val chars = snap.documents.mapNotNull { it.toObject(CharacterProfile::class.java) }
                onLoaded(chars)
            }
            .addOnFailureListener { onLoaded(emptyList()) }
    }
}
