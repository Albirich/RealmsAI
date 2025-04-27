package com.example.emotichat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.util.Log
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.gson.Gson

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
        // Pick your background
        // ——————————————
        backgroundPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                selectedBackgroundUri = it
                findViewById<ImageButton>(R.id.backgroundButton).setImageURI(it)
            }
        }

        // ———————————————————————————
        // Pick one of your characters
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
                        "id",
                        packageName
                    )
                ).setImageURI(Uri.parse(uriString))
            }
        }

        // Wire up each of the 6 character‐slots
        (0 until 6).forEach { i ->
            findViewById<ImageButton>(
                resources.getIdentifier("charButton${i + 1}", "id", packageName)
            ).setOnClickListener {
                currentSlotIndex = i
                val intent = Intent(this, CharacterSelectionActivity::class.java)
                selectedCharIds[i]?.let {
                    intent.putStringArrayListExtra("PRESELECTED_CHARS", arrayListOf(it))
                }
                selectCharLauncher.launch(intent)
            }
        }

        // Launch the background picker
        findViewById<ImageButton>(R.id.backgroundButton).setOnClickListener {
            backgroundPicker.launch("image/*")
        }

        // ——————————————
        // Form fields
        // ——————————————
        val titleInput       = findViewById<EditText>(R.id.titleEditText)
        val descriptionInput = findViewById<EditText>(R.id.descriptionEditText)
        val tagsInput        = findViewById<EditText>(R.id.tagsEditText)
        val firstMsgInput    = findViewById<EditText>(R.id.firstMessageEditText)
        val sfwSwitch        = findViewById<Switch>(R.id.sfwSwitch)
        val modeSpinner      = findViewById<Spinner>(R.id.modeSpinner)


        val maxProfileChars  = 4000
        val maxGreetingChars = 500
        descriptionInput.filters = arrayOf(InputFilter.LengthFilter(maxProfileChars))
        firstMsgInput.filters    = arrayOf(InputFilter.LengthFilter(maxGreetingChars))

        ArrayAdapter.createFromResource(
            this,
            R.array.chat_creation_modes,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            modeSpinner.adapter = adapter
        }

        // ——————————————
        // Submit
        // ——————————————
        findViewById<Button>(R.id.createChatButton).setOnClickListener {
            Log.d("ChatCreation", "▶️ CreateChat clicked")
            Toast.makeText(this, "Clicked Create!", Toast.LENGTH_SHORT).show()

            val newChatId     = System.currentTimeMillis().toString()
            val selectedLabel = modeSpinner.selectedItem as String
            val mode          = parseChatMode(selectedLabel)

            //  ———————————————————
            //  1) Build your ChatProfile
            //  ———————————————————
            val profile = ChatProfile(
                id            = newChatId,
                title         = titleInput.text.toString(),
                description   = descriptionInput.text.toString(),
                tags          = tagsInput.text
                    .toString()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() },
                mode          = mode,
                backgroundUri = selectedBackgroundUri?.toString(),
                sfwOnly       = sfwSwitch.isChecked,
                characterIds  = selectedCharIds.filterNotNull(),
                rating        = 0f,
                timestamp     = System.currentTimeMillis()
            )

            //  ——————————————————————————————————————————
            //  2) Grab the current userId and pass it along
            //  ——————————————————————————————————————————
            val authorId = getSharedPreferences("user", Context.MODE_PRIVATE)
                .getString("userId", "")!!

            //  ——————————————————————————————————————————
            //  3) Fire off to MainActivity, extras include author
            //  ——————————————————————————————————————————
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("CHAT_PROFILE_JSON", Gson().toJson(profile))
                putExtra("FIRST_MESSAGE", firstMsgInput.text.toString())
                putExtra("CHAT_AUTHOR", authorId)     // ← new
            }
            startActivity(intent)
            finish()
        }
    }

    private fun loadAvatarUriForCharacter(charId: String): String {
        val prefs = getSharedPreferences("characters", MODE_PRIVATE)
        val json  = prefs.getString(charId, null) ?: return ""
        val obj   = org.json.JSONObject(json)
        return obj.optString("avatarUri", "")
    }

    private fun parseChatMode(label: String): ChatMode = when (label) {
        "Sandbox"           -> ChatMode.SANDBOX
        "RPG Mode"          -> ChatMode.RPG
        "Slow-Burn Mode"    -> ChatMode.SLOW_BURN
        "God Mode"          -> ChatMode.GOD
        "Visual Novel Mode" -> ChatMode.VISUAL_NOVEL
        else                -> ChatMode.SANDBOX
    }
}
