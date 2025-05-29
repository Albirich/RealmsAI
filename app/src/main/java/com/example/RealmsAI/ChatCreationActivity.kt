package com.example.RealmsAI

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.RealmsAI.models.CharacterProfile
import com.example.RealmsAI.models.ChatMode
import com.example.RealmsAI.models.ChatProfile
import com.example.RealmsAI.models.Relationship
import com.google.firebase.Timestamp
import com.google.gson.Gson
import org.json.JSONObject
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class ChatCreationActivity : AppCompatActivity() {

    // UI widgets
    private lateinit var titleEt: EditText
    private lateinit var modeSpinner: Spinner
    private lateinit var descEt: EditText
    private lateinit var firstMsgEt: EditText
    private lateinit var bgButton: ImageButton
    private lateinit var bgRecycler: RecyclerView
    private lateinit var sfwSwitch: Switch
    private lateinit var charSlots: List<ImageButton>
    private lateinit var tagsEt: EditText
    private lateinit var createBtn: Button
    private lateinit var relationshipBtn: Button

    // State
    private var selectedBackgroundUri: Uri? = null
    private var selectedBackgroundResId: Int? = null
    private lateinit var bgPicker: ActivityResultLauncher<String>

    private val presetBackgrounds = listOf(
        R.drawable.bg_beach,
        R.drawable.bg_castle,
        R.drawable.bg_comedy_club,
        R.drawable.bg_forest,
        R.drawable.bg_mountain_path,
        R.drawable.bg_newsroom,
        R.drawable.bg_office,
        R.drawable.bg_space,
        R.drawable.bg_woods
    )

    private val selectedCharIds = MutableList<String?>(6) { null }
    private var currentCharSlot = 0
    private lateinit var charSelectLauncher: ActivityResultLauncher<Intent>
    private var allLoadedCharacters: List<CharacterProfile> = emptyList()

    // Relationships for the chat
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
        bgButton = findViewById(R.id.backgroundButton)
        bgRecycler = findViewById(R.id.backgroundRecycler)
        sfwSwitch = findViewById(R.id.sfwSwitch)
        tagsEt = findViewById(R.id.tagsEditText)
        createBtn = findViewById(R.id.createChatButton)
        relationshipBtn = findViewById(R.id.chatrelationshipBtn)

        charSlots = listOf(
            findViewById(R.id.charButton1),
            findViewById(R.id.charButton2),
            findViewById(R.id.charButton3),
            findViewById(R.id.charButton4),
            findViewById(R.id.charButton5),
            findViewById(R.id.charButton6)
        )

        // Spinner setup
        ArrayAdapter.createFromResource(
            this, R.array.chat_creation_modes,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            modeSpinner.adapter = adapter
        }

        // Background picker
        bgPicker = registerForActivityResult(GetContent()) { uri: Uri? ->
            uri?.let {
                selectedBackgroundUri = it
                selectedBackgroundResId = null
                bgButton.setImageURI(it)
            }
        }
        bgButton.setOnClickListener {
            bgPicker.launch("image/*")
        }

        // Preset backgrounds recycler
        bgRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        bgRecycler.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = presetBackgrounds.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                object : RecyclerView.ViewHolder(ImageView(parent.context).apply {
                    val dps = (64 * resources.displayMetrics.density).toInt()
                    layoutParams = ViewGroup.LayoutParams(dps, dps)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setPadding(8, 8, 8, 8)
                }) {}

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {
                val resId = presetBackgrounds[pos]
                (holder.itemView as ImageView).apply {
                    setImageResource(resId)
                    setOnClickListener {
                        selectedBackgroundUri = null
                        selectedBackgroundResId = resId
                        bgButton.setImageResource(resId)
                    }
                }
            }
        }

        // 5) Load all characters from Firestore and store in allLoadedCharacters
        loadCharactersFromFirestore(
            onLoaded = { chars ->
                allLoadedCharacters = chars
            }
        )

        // Character slot launcher
        charSelectLauncher = registerForActivityResult(StartActivityForResult()) { res ->
            if (res.resultCode == RESULT_OK) {
                val list = res.data?.getStringArrayListExtra("SELECTED_CHARS")
                Log.d("ChatCreation", "Returned selected chars: $list")
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

        // Relationship button click launches ChatRelationshipActivity with current chars & relationships
        relationshipBtn.setOnClickListener {
            val safeCharIds = selectedCharIds.filterNotNull()
            chatRelationships = aggregateRelationshipsFromSelectedChars() // <-- important

            val intent = Intent(this, ChatRelationshipActivity::class.java)
            intent.putStringArrayListExtra("PARTICIPANT_IDS", ArrayList(safeCharIds))
            intent.putExtra("RELATIONSHIPS_JSON", Gson().toJson(chatRelationships))
            startActivityForResult(intent, RELATIONSHIP_REQ_CODE)
        }

        createBtn.setOnClickListener {
            val title = titleEt.text.toString().trim()
            Log.d("ChatCreation", "Title entered: '$title'")
            if (title.isEmpty()) {
                Log.d("ChatCreation", "Validation failed: title empty")
                Toast.makeText(this, "Chat title is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Similar for other fields...
            Log.d("ChatCreation", "Validation passed, saving chat")
            saveAndLaunchChat()
        }


    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RELATIONSHIP_REQ_CODE && resultCode == Activity.RESULT_OK && data != null) {
            val relJson = data.getStringExtra("RELATIONSHIPS_JSON")
            if (!relJson.isNullOrEmpty()) {
                chatRelationships = Gson().fromJson(relJson, Array<Relationship>::class.java).toMutableList()
                Toast.makeText(this, "Loaded ${chatRelationships.size} chat relationships", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveAndLaunchChat() {
        val chatId = System.currentTimeMillis().toString()
        val title = titleEt.text.toString().trim().takeIf { it.isNotEmpty() } ?: run {
            titleEt.error = "Required"
            return
        }
        val desc = descEt.text.toString().trim()
        val firstMsg = firstMsgEt.text.toString().trim()
        val tags = tagsEt.text.toString().split(",").map(String::trim).filter(String::isNotEmpty)
        val sfwOnly = sfwSwitch.isChecked
        val modeLabel = modeSpinner.selectedItem as String
        val mode = ChatMode.valueOf(modeLabel.uppercase().replace(' ', '_'))
        val bgUriString = selectedBackgroundUri?.toString()
            ?: selectedBackgroundResId?.let { "android.resource://${packageName}/$it" }
        val chars = selectedCharIds.filterNotNull()
        val bgResId = selectedBackgroundResId
        val authorId = getSharedPreferences("user", MODE_PRIVATE).getString("userId", "") ?: ""

        val profile = ChatProfile(
            id = chatId,
            title = title,
            description = desc,
            tags = tags,
            firstmessage = firstMsg,
            mode = mode,
            backgroundUri = bgUriString,
            backgroundResId = bgResId,
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
            "backgroundUri" to (bgUriString ?: ""),
            "backgroundResId" to bgResId,
            "sfwOnly" to sfwOnly,
            "characterIds" to profile.characterIds,
            "author" to profile.author,
            "timestamp" to Timestamp.now(),
            "lastMessage" to "",
            "lastTimestamp" to FieldValue.serverTimestamp(),
            "relationships" to chatRelationships
        )

        FirebaseFirestore.getInstance()
            .collection("chats")
            .document(chatId)
            .set(chatData)
            .addOnSuccessListener {
                Log.d("ChatCreation", "New chat persisted to Firestore: $chatId")
                Toast.makeText(this, "Chat created!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, CreationHubActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Log.e("ChatCreation", "Failed to persist chat", e)
                Toast.makeText(this, "Could not create chat", Toast.LENGTH_SHORT).show()
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

    /** Helper to pull the saved avatarUri from a Character’s prefs entry */
    fun loadAvatarUriForCharacter(charId: String): String {
        val prefs = getSharedPreferences("characters", Context.MODE_PRIVATE)
        val json = prefs.getString(charId, null)
        Log.d("ChatCreation", "Loading avatar for $charId: $json")
        return if (json != null) JSONObject(json).optString("avatarUri", "") else ""
    }
    private fun aggregateRelationshipsFromSelectedChars(): MutableList<Relationship> {
        val selectedChars = allLoadedCharacters.filter { selectedCharIds.contains(it.id) }
        val combined = mutableListOf<Relationship>()
        selectedChars.forEach { char ->
            combined.addAll(char.relationships)
        }
        return combined
    }
}
