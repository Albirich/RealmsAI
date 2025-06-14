package com.example.RealmsAI

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var modeSpinner: Spinner
    private lateinit var tagsEt: EditText
    private lateinit var createBtn: Button
    private lateinit var relationshipBtn: Button
    private lateinit var btnLoadCollections: Button
    private lateinit var btnOpenMap: Button

    private var selectedCharacters: MutableList<CharacterProfile> = mutableListOf()
    private var loadedAreas: MutableList<Area> = mutableListOf()
    private var characterToAreaMap: MutableMap<String, String> = mutableMapOf()
    private var chatRelationships: MutableList<Relationship> = mutableListOf()

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
                // Optionally, update a UI preview here!
            }
        }

    private val collectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val charactersJson = result.data!!.getStringExtra("CHARACTER_LIST_JSON") ?: return@registerForActivityResult
                val charListType = object : com.google.gson.reflect.TypeToken<List<CharacterProfile>>() {}.type
                selectedCharacters = gson.fromJson(charactersJson, charListType)
                // TODO: Optionally update a UI preview here!
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
        modeSpinner = findViewById(R.id.modeSpinner)
        tagsEt = findViewById(R.id.tagsEditText)
        createBtn = findViewById(R.id.createChatButton)
        relationshipBtn = findViewById(R.id.chatrelationshipBtn)
        btnLoadCollections = findViewById(R.id.btnLoadCollections)
        btnOpenMap = findViewById(R.id.btnOpenMap)

        // Spinner setup
        ArrayAdapter.createFromResource(
            this, R.array.chat_creation_modes, android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            modeSpinner.adapter = adapter
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
            val previews = selectedCharacters.map {
                ParticipantPreview(it.id, it.name, it.avatarUri ?: "")
            }
            val relJson = gson.toJson(chatRelationships)
            val intent = Intent(this, ChatRelationshipActivity::class.java)
            intent.putExtra("PARTICIPANTS_JSON", Gson().toJson(previews))
            intent.putExtra("RELATIONSHIPS_JSON", relJson)
            startActivityForResult(intent, 8080)
        }

        // Create Chat button
        createBtn.setOnClickListener {
            if (titleEt.text.isNullOrBlank()) {
                titleEt.error = "Title required"
                return@setOnClickListener
            }
            saveAndLaunchChat()
        }
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
    }

    private fun saveAndLaunchChat() {
        val chatId = System.currentTimeMillis().toString()
        val title = titleEt.text.toString().trim()
        val desc = descEt.text.toString().trim()
        val secretDesc = secretDescEt.text.toString().trim()
        val firstMsg = firstMsgEt.text.toString().trim()
        val tags = tagsEt.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val sfwOnly = sfwSwitch.isChecked
        val modeLabel = modeSpinner.selectedItem as String
        val mode = ChatMode.valueOf(modeLabel.uppercase().replace(' ', '_'))
        val authorId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        val profile = ChatProfile(
            id = chatId,
            title = title,
            description = desc,
            secretDescription = secretDesc,
            firstmessage = firstMsg,
            mode = mode,
            areas = loadedAreas,
            characterIds = selectedCharacters.map { it.id },
            characterToArea = characterToAreaMap,
            relationships = chatRelationships,
            rating = 0f,
            timestamp = Timestamp.now(),
            author = authorId,
            tags = tags,
            sfwOnly = sfwOnly
        )

        val chatData = mapOf(
            "id" to profile.id,
            "title" to profile.title,
            "description" to profile.description,
            "secretDescription" to profile.secretDescription,
            "firstmessage" to profile.firstmessage,
            "mode" to profile.mode.name,
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
            "relationships" to profile.relationships,
            "rating" to profile.rating,
            "timestamp" to Timestamp.now(),
            "author" to authorId,
            "tags" to profile.tags,
            "sfwOnly" to profile.sfwOnly,
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

    private fun loadCharactersFromFirestore(onLoaded: (List<CharacterProfile>) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("characters")
            .get()
            .addOnSuccessListener { snapshot ->
                val chars = snapshot.documents.mapNotNull { it.toObject(CharacterProfile::class.java) }
                onLoaded(chars)
            }
    }
}
