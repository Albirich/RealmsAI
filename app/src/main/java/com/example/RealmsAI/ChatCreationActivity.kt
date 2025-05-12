package com.example.RealmsAI

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

    // for character‐slot picking
    private val selectedCharIds = MutableList<String?>(6) { null }
    private var currentCharSlot = 0
    private lateinit var charSelectLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // **NO** bottom nav here
        setContentView(R.layout.activity_create_chat)

        // 1) bind all views
        titleEt = findViewById(R.id.titleEditText)
        modeSpinner = findViewById(R.id.modeSpinner)
        descEt = findViewById(R.id.descriptionEditText)
        firstMsgEt = findViewById(R.id.firstMessageEditText)
        bgButton = findViewById(R.id.backgroundButton)
        bgRecycler = findViewById(R.id.backgroundRecycler)
        sfwSwitch = findViewById(R.id.sfwSwitch)
        tagsEt = findViewById(R.id.tagsEditText)
        createBtn = findViewById(R.id.createChatButton)

        charSlots = listOf(
            findViewById(R.id.charButton1),
            findViewById(R.id.charButton2),
          //  findViewById(R.id.charButton3),
          //  findViewById(R.id.charButton4),
          //  findViewById(R.id.charButton5),
          //  findViewById(R.id.charButton6)
        )

        // 2) Spinner: load your array from resources
        ArrayAdapter.createFromResource(
            this, R.array.chat_creation_modes,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            modeSpinner.adapter = adapter
        }

        // 3) Custom gallery‐picker for background
        bgPicker = registerForActivityResult(GetContent()) { uri: Uri? ->
            uri?.let {
                selectedBackgroundUri = null
                selectedBackgroundResId = R.drawable.bg_forest
                bgButton.setImageResource(R.drawable.bg_forest)
            }
        }
        bgButton.setOnClickListener {
            bgPicker.launch("image/*")
        }

        // 4) Preset backgrounds recycler
        bgRecycler.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
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
                        // choose this preset
                        selectedBackgroundUri = null
                        selectedBackgroundResId = resId
                        bgButton.setImageResource(resId)
                    }
                }
            }
        }

        // 5) Character‐slot launcher
        charSelectLauncher = registerForActivityResult(StartActivityForResult()) { res ->
            if (res.resultCode == RESULT_OK) {
                val list = res.data
                    ?.getStringArrayListExtra("SELECTED_CHARS")
                    ?: return@registerForActivityResult
                selectedCharIds[currentCharSlot] = list.firstOrNull()
                // load its avatar right on the slot button
                selectedCharIds[currentCharSlot]?.let { id ->
                    val uri = loadAvatarUriForCharacter(id)
                    charSlots[currentCharSlot].setImageURI(Uri.parse(uri))
                }
            }
        }
        charSlots.forEachIndexed { idx, btn ->
            btn.setOnClickListener {
                currentCharSlot = idx
                val intent = Intent(this, CharacterSelectionActivity::class.java)
                // pre-select existing if any
                selectedCharIds[idx]?.let {
                    intent.putStringArrayListExtra(
                        "PRESELECTED_CHARS", arrayListOf(it)
                    )
                }
                charSelectLauncher.launch(intent)
            }
        }

        fun saveAndLaunchChat() {
            // 6.1) Read all your inputs
            val chatId = System.currentTimeMillis().toString()
            val title = titleEt.text.toString().trim().takeIf { it.isNotEmpty() } ?: run {
                titleEt.error = "Required"; return
            }
            val desc = descEt.text.toString().trim()
            val firstMsg = firstMsgEt.text.toString().trim()
            val tags = tagsEt.text.toString()
                .split(",").map(String::trim).filter(String::isNotEmpty)
            val sfwOnly = sfwSwitch.isChecked
            val modeLabel = modeSpinner.selectedItem as String
            val mode = ChatMode.valueOf(modeLabel.uppercase().replace(' ', '_'))

            // 6.2) Pick your background‐URI string
            val bgUriString = selectedBackgroundUri?.toString()
                ?: selectedBackgroundResId?.let { resId ->
                    // encode it as an android.resource URI the loader can understand:
                    "android.resource://${packageName}/$resId"
                }

            // 6.3) Pull only non-null character IDs
            val chars = selectedCharIds.filterNotNull()

            val bgResId = selectedBackgroundResId

            // 6.4) Build your profile
            Log.d("ChatCreation", "DEBUG: title=$title, desc=$desc")
            Log.d("ChatCreation", "Creating chat with title=$title")

            val profile = ChatProfile(
                id = chatId,
                title = title,
                description = desc,
                tags = tags,
                mode = mode,
                backgroundUri   = bgUriString,
                backgroundResId = bgResId,
                sfwOnly = sfwOnly,
                characterIds = chars,
                rating = 0f,
                timestamp = System.currentTimeMillis(),
                author = getSharedPreferences("user", Context.MODE_PRIVATE)
                    .getString("userId", "")!!
            )

            // 6.5) Persist to prefs

            val chatData = mapOf(
                "title"         to profile.title,
                "description"   to profile.description,
                "tags"          to profile.tags,
                "mode"          to profile.mode.name,
                "characterIds"  to profile.characterIds,
                "author"        to profile.author,
                "timestamp"     to FieldValue.serverTimestamp(),
                "lastMessage"   to "",                         // no messages yet
                "lastTimestamp" to FieldValue.serverTimestamp()
            )

            val db = FirebaseFirestore.getInstance()
            db.collection("chats")
                .document(chatId)
                .set(chatData)
                .addOnSuccessListener {
                    Log.d("ChatCreation", "New chat persisted to Firestore: $chatId")

                    // Now create a fresh session for this chat:
                    SessionManager.getOrCreateSessionFor(
                        chatId,
                        onResult = { sessionId ->
                            Log.d("ChatCreation", "New session for $chatId → $sessionId")
                            // Pass both IDs into MainActivity
                            val intent = Intent(this, MainActivity::class.java).apply {
                                putExtra("CHAT_ID", chatId)
                                putExtra("SESSION_ID", sessionId)
                                putExtra("CHAT_PROFILE_JSON", Gson().toJson(profile))
                            }
                            startActivity(intent)
                            finish()
                        },
                        onError = { e ->
                            Log.e("ChatCreation", "Failed to create session", e)
                            Toast.makeText(this, "Could not start chat session", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                .addOnFailureListener { e ->
                    Log.e("ChatCreation", "Failed to persist chat", e)
                    Toast.makeText(this, "Could not create chat", Toast.LENGTH_SHORT).show()
                }

            finish()
        }


        // 6) Finally: “Create Chat” button
        createBtn.setOnClickListener {
            // 1) Gather your inputs
            val title = titleEt.text.toString().trim()
            val desc = descEt.text.toString().trim()
            val firstMsg = firstMsgEt.text.toString().trim()
            val chars = selectedCharIds.filterNotNull()
            val bgUri = selectedBackgroundUri?.toString()
            val bgResId = selectedBackgroundResId

            // 2) Early‐return validation
            when {
                title.isEmpty() -> {
                    Toast.makeText(this, "Chat title is required", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                desc.isEmpty() -> {
                    Toast.makeText(this, "Please enter a description", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                firstMsg.isEmpty() -> {
                    Toast.makeText(this, "You must supply an opening message", Toast.LENGTH_SHORT)
                        .show()
                    return@setOnClickListener
                }

                chars.isEmpty() -> {
                    Toast.makeText(this, "Please select at least one character", Toast.LENGTH_SHORT)
                        .show()
                    return@setOnClickListener
                }
                // **THIS** is now a proper when‐branch, not an `if` inside your `when`
                (bgUri == null && bgResId == null) -> {
                    Toast.makeText(this, "Please select a background", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                else -> {
                    // all validations passed, fall through to saving
                }
            }

            // 3) Everything’s good – now save & launch
            saveAndLaunchChat()
        }

    }


    /** Helper to pull the saved avatarUri from a Character’s prefs entry */
    fun loadAvatarUriForCharacter(charId: String): String {
        val prefs = getSharedPreferences("characters", Context.MODE_PRIVATE)
        val json  = prefs.getString(charId, null) ?: return ""
        return JSONObject(json).optString("avatarUri","")
    }
}
