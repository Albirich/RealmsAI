package com.example.emotichat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.gson.Gson
import java.util.UUID

class ChatCreationActivity : BaseActivity() {

    private var selectedBackgroundUri: Uri? = null
    private val selectedCharIds = MutableList<String?>(6) { null }
    private var currentSlotIndex = 0

    private lateinit var backgroundPicker: ActivityResultLauncher<String>
    private lateinit var selectCharLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_chat)

        // ——————————————
        // Background picker
        // ——————————————
        backgroundPicker = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let {
                selectedBackgroundUri = it
                findViewById<ImageButton>(R.id.backgroundButton)
                    .setImageURI(it)
            }
        }
        findViewById<ImageButton>(R.id.backgroundButton)
            .setOnClickListener { backgroundPicker.launch("image/*") }

        // ———————————————————————————
        // Character picker (6 slots)
        // ———————————————————————————
        selectCharLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val ids = result.data
                    ?.getStringArrayListExtra("SELECTED_CHARS") ?: return@registerForActivityResult
                val chosen = ids.firstOrNull() ?: return@registerForActivityResult
                selectedCharIds[currentSlotIndex] = chosen
                val uriString = loadAvatarUriForCharacter(chosen)
                findViewById<ImageButton>(
                    resources.getIdentifier(
                        "charButton${currentSlotIndex + 1}",
                        "id", packageName
                    )
                ).setImageURI(Uri.parse(uriString))
            }
        }
        (0 until 6).forEach { i ->
            findViewById<ImageButton>(
                resources.getIdentifier("charButton${i + 1}", "id", packageName)
            ).setOnClickListener {
                currentSlotIndex = i
                val intent = Intent(this, CharacterSelectionActivity::class.java)
                selectedCharIds[i]?.let { intent.putStringArrayListExtra(
                    "PRESELECTED_CHARS", arrayListOf(it)
                ) }
                selectCharLauncher.launch(intent)
            }
        }

        // ——————————————
        // Form fields & limits
        // ——————————————
        val titleInput       = findViewById<EditText>(R.id.titleEditText)
        val descriptionInput = findViewById<EditText>(R.id.descriptionEditText)
        val tagsInput        = findViewById<EditText>(R.id.tagsEditText)
        val firstMsgInput    = findViewById<EditText>(R.id.firstMessageEditText)
        val sfwSwitch        = findViewById<Switch>(R.id.sfwSwitch)
        val modeSpinner      = findViewById<Spinner>(R.id.modeSpinner)

        // Load current userId
        val me = getSharedPreferences("user", Context.MODE_PRIVATE)
            .getString("userId","")!!

        // Character/description limits
        descriptionInput.filters = arrayOf(InputFilter.LengthFilter(4000))
        firstMsgInput.filters    = arrayOf(InputFilter.LengthFilter(500))

        ArrayAdapter.createFromResource(
            this,
            R.array.chat_creation_modes,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
            )
            modeSpinner.adapter = adapter
        }

        // ——————————————
        // Create button
        // ——————————————
        findViewById<Button>(R.id.createChatButton).setOnClickListener {
            // 1) Generate a new ID
            val chatId = UUID.randomUUID().toString()

            // 2) Read inputs
            val selectedLabel = modeSpinner.selectedItem as String
            val mode          = parseChatMode(selectedLabel)
            val title         = titleInput.text.toString().trim()
            val description   = descriptionInput.text.toString().trim()
            val tags = tagsInput.text.toString()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val firstMsg = firstMsgInput.text.toString().trim()
            val bgUri    = selectedBackgroundUri?.toString()
            val sfwOnly  = sfwSwitch.isChecked
            val charIds  = selectedCharIds.filterNotNull()

            // 3) Build the ChatProfile
            val profile = ChatProfile(
                id            = chatId,
                title         = title,
                description   = description,
                tags          = tags,
                mode          = mode,
                backgroundUri = bgUri,
                sfwOnly       = sfwOnly,
                characterIds  = charIds,
                rating        = 0f,
                timestamp     = System.currentTimeMillis(),
                author      = me
            )

            // 4) Persist the (empty) session under this ID
            saveChatSession(
                chatId   = chatId,
                title    = profile.title,
                messages = emptyList(),
                author = me
            )

            // 5) Launch the chat screen
            Intent(this, MainActivity::class.java).apply {
                putExtra("CHAT_PROFILE_JSON", Gson().toJson(profile))
                putExtra("FIRST_MESSAGE", firstMsg)
            }.also { startActivity(it) }

            // 6) Finish so back won’t return here
            finish()
        }
    }

    /** Load an avatar URI for the given character ID from prefs */
    private fun loadAvatarUriForCharacter(charId: String): String {
        val prefs = getSharedPreferences("characters", MODE_PRIVATE)
        val json  = prefs.getString(charId, null) ?: return ""
        return org.json.JSONObject(json).optString("avatarUri", "")
    }

    /** Map your spinner label to the ChatMode enum */
    private fun parseChatMode(label: String): ChatMode = when (label) {
        "Sandbox"           -> ChatMode.SANDBOX
        "RPG Mode", "RPG"   -> ChatMode.RPG
        "Slow-Burn Mode"    -> ChatMode.SLOW_BURN
        "God Mode"          -> ChatMode.GOD
        "Visual Novel Mode" -> ChatMode.VISUAL_NOVEL
        else                -> ChatMode.SANDBOX
    }
}
