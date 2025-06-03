package com.example.RealmsAI

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.RealmsAI.models.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson

class ChatCreationActivity : AppCompatActivity() {

    // UI
    private lateinit var titleEt: EditText
    private lateinit var modeSpinner: Spinner
    private lateinit var descEt: EditText
    private lateinit var firstMsgEt: EditText
    private lateinit var sfwSwitch: Switch
    private lateinit var charSlots: List<ImageButton>
    private lateinit var tagsEt: EditText
    private lateinit var createBtn: Button
    private lateinit var relationshipBtn: Button
    private lateinit var addAreaButton: com.google.android.material.button.MaterialButton

    // Area state
    private var areaList: MutableList<Area> = mutableListOf()
    private lateinit var areaGalleryLauncher: ActivityResultLauncher<Intent>

    // Character state
    private val selectedCharIds = MutableList<String?>(6) { null }
    private var currentCharSlot = 0
    private lateinit var charSelectLauncher: ActivityResultLauncher<Intent>
    private var allLoadedCharacters: List<CharacterProfile> = emptyList()
    private var chatRelationships: MutableList<Relationship> = mutableListOf()

    companion object {
        private const val RELATIONSHIP_REQ_CODE = 8080
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_chat)

        // Bind views
        titleEt = findViewById(R.id.titleEditText)
        modeSpinner = findViewById(R.id.modeSpinner)
        descEt = findViewById(R.id.descriptionEditText)
        firstMsgEt = findViewById(R.id.firstMessageEditText)
        sfwSwitch = findViewById(R.id.sfwSwitch)
        tagsEt = findViewById(R.id.tagsEditText)
        createBtn = findViewById(R.id.createChatButton)
        relationshipBtn = findViewById(R.id.chatrelationshipBtn)
        addAreaButton = findViewById(R.id.addAreaButton)

        charSlots = listOf(
            findViewById(R.id.charButton1),
            findViewById(R.id.charButton2),
            findViewById(R.id.charButton3),
            findViewById(R.id.charButton4),
            findViewById(R.id.charButton5),
            findViewById(R.id.charButton6)
        )

        // Spinner setup (same)
        ArrayAdapter.createFromResource(
            this, R.array.chat_creation_modes,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            modeSpinner.adapter = adapter
        }

        // Load characters from Firestore
        loadCharactersFromFirestore(
            onLoaded = { chars -> allLoadedCharacters = chars }
        )

        // Character picker
        charSelectLauncher = registerForActivityResult(StartActivityForResult()) { res ->
            if (res.resultCode == RESULT_OK) {
                val list = res.data?.getStringArrayListExtra("SELECTED_CHARS")
                if (list.isNullOrEmpty()) {
                    Toast.makeText(this, "No characters selected", Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }
                selectedCharIds[currentCharSlot] = list.firstOrNull()
                val selectedId = selectedCharIds[currentCharSlot]
                val selectedCharacter = allLoadedCharacters.find { it.id == selectedId }
                val uri = selectedCharacter?.avatarUri ?: ""
                val imageView = charSlots[currentCharSlot]
                if (uri.isNotEmpty()) {
                    Glide.with(this)
                        .load(uri)
                        .placeholder(R.drawable.placeholder_avatar)
                        .error(R.drawable.placeholder_avatar)
                        .into(imageView)
                } else {
                    imageView.setImageResource(R.drawable.placeholder_avatar)
                }
            }
        }
        charSlots.forEachIndexed { idx, btn ->
            btn.setOnClickListener {
                currentCharSlot = idx
                val intent = Intent(this, CharacterSelectionActivity::class.java)
                selectedCharIds[idx]?.let {
                    intent.putStringArrayListExtra("PRESELECTED_CHARS", arrayListOf(it))
                }
                charSelectLauncher.launch(intent)
            }
        }

        // Relationship button
        relationshipBtn.setOnClickListener {
            val safeCharIds = selectedCharIds.filterNotNull()
            chatRelationships = aggregateRelationshipsFromSelectedChars()
            val intent = Intent(this, ChatRelationshipActivity::class.java)
            intent.putStringArrayListExtra("PARTICIPANT_IDS", ArrayList(safeCharIds))
            intent.putExtra("RELATIONSHIPS_JSON", Gson().toJson(chatRelationships))
            startActivityForResult(intent, RELATIONSHIP_REQ_CODE)
        }

        // --- AREA GALLERY MaterialButton ---
        areaGalleryLauncher = registerForActivityResult(StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val areasJson = result.data?.getStringExtra("EXTRA_AREAS_JSON")

                if (!areasJson.isNullOrBlank()) {
                    areaList = Gson().fromJson(areasJson, Array<Area>::class.java).toMutableList()
                }
            }
        }
        addAreaButton.setOnClickListener {
            val intent = Intent(this, BackgroundGalleryActivity::class.java)
            intent.putExtra("EXTRA_AREAS_JSON", Gson().toJson(areaList))
            areaGalleryLauncher.launch(intent)
        }

        // Edit
        val editChatId = intent.getStringExtra("CHAT_EDIT_ID")
        val chatJson = intent.getStringExtra("CHAT_PROFILE_JSON")
        if (!editChatId.isNullOrEmpty() && !chatJson.isNullOrEmpty()) {
            val chatProfile = Gson().fromJson(chatJson, ChatProfile::class.java)
            fillFormFromProfile(chatProfile)
        }

        // Create
        createBtn.setOnClickListener {
            val title = titleEt.text.toString().trim()
            if (title.isEmpty()) {
                titleEt.error = "Required"
                return@setOnClickListener
            }
            saveAndLaunchChat()
        }
    }

    private fun fillFormFromProfile(profile: ChatProfile) {
        titleEt.setText(profile.title)
        descEt.setText(profile.description)
        firstMsgEt.setText(profile.firstmessage)
        tagsEt.setText(profile.tags.joinToString(", "))
        sfwSwitch.isChecked = profile.sfwOnly
        val modeIndex = resources.getStringArray(R.array.chat_creation_modes)
            .indexOf(profile.mode.name.replace('_', ' ').capitalize())
        if (modeIndex >= 0) modeSpinner.setSelection(modeIndex)
        profile.characterIds.forEachIndexed { idx, charId -> selectedCharIds[idx] = charId }
        areaList = profile.areas.toMutableList()
        chatRelationships = profile.relationships.toMutableList()
    }

    private fun saveAndLaunchChat() {
        val chatId = System.currentTimeMillis().toString()
        val title = titleEt.text.toString().trim()
        val desc = descEt.text.toString().trim()
        val firstMsg = firstMsgEt.text.toString().trim()
        val tags = tagsEt.text.toString().split(",").map(String::trim).filter(String::isNotEmpty)
        val sfwOnly = sfwSwitch.isChecked
        val modeLabel = modeSpinner.selectedItem as String
        val mode = ChatMode.valueOf(modeLabel.uppercase().replace(' ', '_'))
        val chars = selectedCharIds.filterNotNull()
        val authorId = getSharedPreferences("user", MODE_PRIVATE).getString("userId", "") ?: ""

        val profile = ChatProfile(
            id = chatId,
            title = title,
            description = desc,
            tags = tags,
            firstmessage = firstMsg,
            mode = mode,
            areas = areaList,
            sfwOnly = sfwOnly,
            characterIds = chars,
            relationships = chatRelationships,
            rating = 0f,
            timestamp = Timestamp.now(),
            author = authorId
        )

        val chatData = mapOf(
            "id" to profile.id,
            "title" to profile.title,
            "description" to profile.description,
            "tags" to profile.tags,
            "mode" to profile.mode.name,
            "firstmessage" to profile.firstmessage,
            "sfwOnly" to sfwOnly,
            "characterIds" to profile.characterIds,
            "author" to profile.author,
            "timestamp" to Timestamp.now(),
            "lastMessage" to "",
            "lastTimestamp" to FieldValue.serverTimestamp(),
            "relationships" to chatRelationships,
            "areas" to areaList.map { area ->
                mapOf(
                    "id" to area.id,
                    "name" to area.name,
                    "locations" to area.locations.map { loc ->
                        mapOf("id" to loc.id, "name" to loc.name, "uri" to loc.uri)
                    }
                )
            }
        )

        FirebaseFirestore.getInstance()
            .collection("chats")
            .document(chatId)
            .set(chatData)
            .addOnSuccessListener {
                Toast.makeText(this, "Chat created!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, CreationHubActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Could not create chat: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    fun loadCharactersFromFirestore(
        onLoaded: (List<CharacterProfile>) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        val db = FirebaseFirestore.getInstance()
        db.collection("characters")
            .get()
            .addOnSuccessListener { snapshot ->
                val chars = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(CharacterProfile::class.java)
                }
                onLoaded(chars)
            }
            .addOnFailureListener(onError)
    }

    private fun aggregateRelationshipsFromSelectedChars(): MutableList<Relationship> {
        val selectedChars = allLoadedCharacters.filter { selectedCharIds.contains(it.id) }
        val combined = mutableListOf<Relationship>()
        selectedChars.forEach { char -> combined.addAll(char.relationships) }
        return combined
    }
}
