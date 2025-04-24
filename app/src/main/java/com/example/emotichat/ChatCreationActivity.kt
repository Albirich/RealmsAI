package com.example.emotichat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson

class ChatCreationActivity : BaseActivity() {

    private var selectedBackgroundUri: Uri? = null
    private val selectedCharIds = MutableList<String?>(6) { null }
    private var currentSlotIndex = 0

    private lateinit var backgroundPicker: ActivityResultLauncher<String>
    private lateinit var selectCharLauncher: ActivityResultLauncher<Intent>

    private fun loadAvatarUriForCharacter(charId: String): String {
        val prefs  = getSharedPreferences("characters", MODE_PRIVATE)
        val json   = prefs.getString(charId, null) ?: return ""
        val obj    = org.json.JSONObject(json)
        return obj.optString("avatarUri", "")
    }
    private fun parseChatMode(label: String): ChatMode = when(label) {
        "Sandbox"             -> ChatMode.SANDBOX
        "RPG Mode"            -> ChatMode.RPG
        "Slow-Burn Mode"      -> ChatMode.SLOW_BURN
        "God Mode"            -> ChatMode.GOD
        "Visual Novel Mode"   -> ChatMode.VISUAL_NOVEL
        else                   -> ChatMode.SANDBOX
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_chat)

        //
        // 1) Character‐picker launcher
        //
        selectCharLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val ids = result.data
                    ?.getStringArrayListExtra("SELECTED_CHARS")
                    ?: return@registerForActivityResult
                val chosen = ids.firstOrNull() ?: return@registerForActivityResult
                selectedCharIds[currentSlotIndex] = chosen

                // update the tapped slot’s ImageButton
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

        //
        // 2) Hook char slots 1–6
        //
        (0 until 6).forEach { i ->
            val btn = findViewById<ImageButton>(
                resources.getIdentifier("charButton${i + 1}", "id", packageName)
            )
            btn.setOnClickListener {
                currentSlotIndex = i
                val intent = Intent(this, CharacterSelectionActivity::class.java).apply {
                    // pre-select if already chosen
                    selectedCharIds[i]?.let {
                        putStringArrayListExtra("PRESELECTED_CHARS", arrayListOf(it))
                    }
                }
                selectCharLauncher.launch(intent)
            }
        }

        //
        // 3) Background picker
        //
        backgroundPicker = registerForActivityResult(GetContent()) { uri ->
            uri?.let {
                selectedBackgroundUri = it
                findViewById<ImageButton>(R.id.backgroundButton).setImageURI(it)
            }
        }
        findViewById<ImageButton>(R.id.backgroundButton).setOnClickListener {
            backgroundPicker.launch("image/*")
        }

        //
        // 4) Gather other inputs
        //
        val titleInput       = findViewById<EditText>(R.id.titleEditText)
        val descriptionInput = findViewById<EditText>(R.id.descriptionEditText)
        val tagsInput        = findViewById<EditText>(R.id.tagsEditText)
        val firstMsgInput    = findViewById<EditText>(R.id.firstMessageEditText)
        val sfwSwitch        = findViewById<Switch>(R.id.sfwSwitch)
        val modeSpinner      = findViewById<Spinner>(R.id.modeSpinner)

        // Populate spinner as before…
        ArrayAdapter.createFromResource(
            this,
            R.array.chat_creation_modes,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            modeSpinner.adapter = adapter
        }

        findViewById<Button>(R.id.createChatButton).setOnClickListener {
            // Logging to verify the click fires
            Log.d("ChatCreation", "▶️ CreateChat clicked")
            Toast.makeText(this, "Clicked Create!", Toast.LENGTH_SHORT).show()

            val newChatId     = System.currentTimeMillis().toString()
            val selectedLabel = modeSpinner.selectedItem as String
            val mode          = parseChatMode(selectedLabel)

            // Build the profile *using* the parsed mode, not valueOf(…) again
            val profile = ChatProfile(
                id            = newChatId,
                title         = titleInput.text.toString(),
                description   = descriptionInput.text.toString(),
                tags          = tagsInput.text.toString()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() },
                mode          = mode,                             // ← use `mode` here
                backgroundUri = selectedBackgroundUri?.toString(),
                sfwOnly       = sfwSwitch.isChecked,
                characterIds  = selectedCharIds.filterNotNull(),
                rating        = 0f,
                timestamp     = System.currentTimeMillis()
            )

            // Serialize & launch
            val json = Gson().toJson(profile)
            Intent(this, MainActivity::class.java).also {
                it.putExtra("CHAT_PROFILE_JSON", json)
                startActivity(it)
            }
            finish()
        }
    }
}
