package com.example.emotichat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson

class ChatCreationActivity : BaseActivity() {

    // Holds whatever the user picked
    private var selectedBackgroundUri: Uri? = null
    private val selectedCharacterUris = MutableList<Uri?>(6) { null }

    // Launchers for picking from gallery
    private lateinit var backgroundPicker: ActivityResultLauncher<String>

    //character selection
    private lateinit var selectCharsLauncher: ActivityResultLauncher<Intent>
    private val selectedCharacterIds = mutableListOf<String>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_chat)

        // 0) REGISTER your pickers before you call setOnClickListener
        backgroundPicker = registerForActivityResult(GetContent()) { uri: Uri? ->
            uri?.let {
                selectedBackgroundUri = it
                findViewById<ImageButton>(R.id.backgroundButton).setImageURI(it)
            }
        }

        selectCharsLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val ids = result.data
                    ?.getStringArrayListExtra("SELECTED_CHARS")
                    ?: return@registerForActivityResult

                selectedCharacterIds.clear()
                selectedCharacterIds.addAll(ids)

                // TODO: update your 6 ImageButtons to show the avatars of those IDs
                ids.forEachIndexed { slotIndex, charId ->
                    // load avatarUri from prefs JSON, then:
                    findViewById<ImageButton>(
                        resources.getIdentifier("charButton${slotIndex+1}", "id", packageName)
                    ).setImageURI(Uri.parse(avatarUriFor(charId)))
                }
            }
        }


        // 1) Title, Description, Tags, First Message…
        val titleInput       = findViewById<EditText>(R.id.titleEditText)
        val descriptionInput = findViewById<EditText>(R.id.descriptionEditText)
        val tagsInput        = findViewById<EditText>(R.id.tagsEditText)
        val firstMsgInput    = findViewById<EditText>(R.id.firstMessageEditText)

        // 2) Mode spinner (unchanged)…
        val modeSpinner = findViewById<Spinner>(R.id.modeSpinner)
        ArrayAdapter.createFromResource(
            this, R.array.chat_creation_modes,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            modeSpinner.adapter = adapter
        }

        // 3) BACKGROUND BUTTON: now launches gallery
        findViewById<ImageButton>(R.id.backgroundButton).setOnClickListener {
            backgroundPicker.launch("image/*")
        }

        // 4) SFW toggle
        val sfwSwitch = findViewById<Switch>(R.id.sfwSwitch)

        // 5) CHARACTER BUTTONS: each launches its own picker
        (1..6).forEach { i ->
            findViewById<ImageButton>(resources.getIdentifier(
                "charButton$i","id", packageName))
                .setOnClickListener {
                    characterPickers[i-1].launch("image/*")
                }
        }

        // 6) CREATE CHAT: build profile → JSON → launch MainActivity
        findViewById<Button>(R.id.createChatButton).setOnClickListener {
            val profile = ChatProfile(

                id            = newChatId,
                title         = titleInput.text.toString(),
                description   = descriptionInput.text.toString(),
                tags          = tagsInput.text.toString().split(",").map { it.trim() },
                mode          = ChatMode.valueOf(modeSpinner.selectedItem as String),
                backgroundUri = selectedBackgroundUri?.toString(),
                sfwOnly       = sfwSwitch.isChecked,
                characterIds  = selectedCharacterUris.mapNotNull { it?.toString() },
                rating        = 0f,
                timestamp     = System.currentTimeMillis(),
                characterIds  = selectedCharIds
            )

            val json = Gson().toJson(profile)
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra("CHAT_PROFILE_JSON", json)
            )
        }
    }
}
