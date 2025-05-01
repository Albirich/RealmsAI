package com.example.emotichat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import java.util.UUID
import org.json.JSONObject


class ChatCreationActivity : BaseActivity() {

    // ─── Class‐level properties ─────────────────────────────────────────────────
    var selectedBgUri: String? = null

    // For your 6 character slots
    private var currentSlotIndex = 0
    private val selectedCharIds = MutableList<String?>(6) { null }

    // Launcher for your character picker
    private lateinit var selectCharLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_chat)

         fun loadAvatarUriForCharacter(charId: String): String {
            val prefs = getSharedPreferences("characters", Context.MODE_PRIVATE)
            val json  = prefs.getString(charId, null) ?: return ""
            return JSONObject(json).optString("avatarUri", "")
        }


// 1) Grab your form fields
        val titleEt       = findViewById<EditText>(R.id.titleEditText)
        val descEt        = findViewById<EditText>(R.id.descriptionEditText)
        val tagsEt        = findViewById<EditText>(R.id.tagsEditText)
        val firstMsgEt    = findViewById<EditText>(R.id.firstMessageEditText)
        val sfwSwitch     = findViewById<Switch>(R.id.sfwSwitch)
        val modeSpinner   = findViewById<Spinner>(R.id.modeSpinner)
        var currentSlotIndex = 0
        val selectedCharIds = MutableList<String?>(6) { null }
        lateinit var selectCharLauncher: ActivityResultLauncher<Intent>

        // ─── 2) Background picker ──────────────────────────────────────────────────
        val backgroundBtn     = findViewById<ImageButton>(R.id.backgroundButton)
        val backgroundRecycler = findViewById<RecyclerView>(R.id.backgroundRecycler)

        // 1) Preset URIs (these could also come from a JSON config or server):
        val presetUris = listOf(
            "android.resource://${packageName}/${R.drawable.bg_forest}",
            "android.resource://${packageName}/${R.drawable.bg_beach}",
            "android.resource://${packageName}/${R.drawable.bg_space}",
            "android.resource://${packageName}/${R.drawable.bg_castle}",
            "android.resource://${packageName}/${R.drawable.bg_comedy_club}",
            "android.resource://${packageName}/${R.drawable.bg_newsroom}",
            "android.resource://${packageName}/${R.drawable.bg_mountain_path}",
            "android.resource://${packageName}/${R.drawable.bg_woods}",
        )

        // 2) Attach the adapter
        backgroundRecycler.adapter = BackgroundAdapter(presetUris) { uriStr ->
            // when user taps a preset
            selectedBgUri = uriStr
            backgroundBtn.setImageURI(Uri.parse(uriStr))
        }

        // 3) Keep your existing “upload-your-own” launcher
        val bgLauncher = registerForActivityResult(GetContent()) { uri ->
            uri?.let {
                selectedBgUri = it.toString()
                backgroundBtn.setImageURI(it)
            }
        }
        backgroundBtn.setOnClickListener {
            // if you want upload to override presets:
            bgLauncher.launch("image/*")
        }

        // ─── 3) Character‐slot picker ───────────────────────────────────────────────
        // 3a) Register your launcher just once:
        selectCharLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val picked = result.data
                    ?.getStringArrayListExtra("SELECTED_CHARS")
                    ?: return@registerForActivityResult
                val chosenId = picked.firstOrNull() ?: return@registerForActivityResult

                // 3b) Save into your list
                selectedCharIds[currentSlotIndex] = chosenId

                // 3c) Load its saved avatar URI (SharedPrefs helper)
                val avatarUri = loadAvatarUriForCharacter(chosenId)

                // 3d) Find the correct ImageButton and set it
                val btnId = resources.getIdentifier(
                    "charButton${currentSlotIndex + 1}",
                    "id",
                    packageName
                )
                findViewById<ImageButton>(btnId)
                    .setImageURI(Uri.parse(avatarUri))
            }
        }

        // 3e) Wire up each of your 6 slots
        (0 until 6).forEach { slot ->
            val btnId = resources.getIdentifier("charButton${slot + 1}", "id", packageName)
            findViewById<ImageButton>(btnId).setOnClickListener {
                currentSlotIndex = slot
                // Optionally pass a “preselected” so the picker highlights existing choice
                val intent = Intent(this, CharacterSelectionActivity::class.java).apply {
                    selectedCharIds[slot]?.let {
                        putStringArrayListExtra("PRESELECTED_CHARS", arrayListOf(it))
                    }
                }
                selectCharLauncher.launch(intent)
            }
        }
        // 4) Populate your spinner
        ArrayAdapter.createFromResource(
            this, R.array.chat_creation_modes, android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            modeSpinner.adapter = adapter
        }

        // 5) Submit → build ChatProfile, save & launch
        findViewById<Button>(R.id.createChatButton).setOnClickListener {
            val chatId    = UUID.randomUUID().toString()
            val title     = titleEt.text.toString().trim()
            val desc      = descEt.text.toString().trim()
            val tags      = tagsEt.text.toString()
                .split(',').map(String::trim).filter(String::isNotEmpty)
            val firstMsg  = firstMsgEt.text.toString().trim()
            val sfw       = sfwSwitch.isChecked
            val modeLabel = modeSpinner.selectedItem as String
            val mode      = parseChatMode(modeLabel)
            val bgUri = selectedBgUri
            val chars = selectedCharIds.filterNotNull()

            // build profile
            val profile = ChatProfile(
                id            = chatId,
                title         = title,
                description   = desc,
                tags          = tags,
                mode          = mode,
                backgroundUri = bgUri,
                sfwOnly       = sfw,
                characterIds  = chars,
                rating        = 0f,
                timestamp     = System.currentTimeMillis(),
                author        = getCurrentUserId()
            )


            // launch main
            Intent(this, MainActivity::class.java).apply {
                putExtra("CHAT_PROFILE_JSON", Gson().toJson(profile))
                putExtra("FIRST_MESSAGE", firstMsg)
                putExtra("CHAT_AUTHOR", profile.author)
            }.also(::startActivity)

            finish()
        }
    }

    private fun parseChatMode(label: String): ChatMode = when(label) {
        "Sandbox"        -> ChatMode.SANDBOX
        "RPG Mode"       -> ChatMode.RPG
        "Slow-Burn Mode" -> ChatMode.SLOW_BURN
        "God Mode"       -> ChatMode.GOD
        "Visual Novel"   -> ChatMode.VISUAL_NOVEL
        else             -> ChatMode.SANDBOX
    }

    private fun getCurrentUserId(): String =
        getSharedPreferences("user", Context.MODE_PRIVATE)
            .getString("userId","")!!
}
