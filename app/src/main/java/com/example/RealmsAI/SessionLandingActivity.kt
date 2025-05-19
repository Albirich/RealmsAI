package com.example.RealmsAI

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.ai.buildSessionFacilitatorPrompt
import com.example.RealmsAI.models.PersonaProfile
import com.example.RealmsAI.network.ModelClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import kotlinx.coroutines.launch
import org.json.JSONObject

class SessionLandingActivity : AppCompatActivity() {
    private lateinit var personaSlot: LinearLayout // Clickable persona row
    private lateinit var personaAvatar: ImageView
    private lateinit var personaNameTv: TextView

    private lateinit var chatNameTv: TextView
    private lateinit var chatDescTv: TextView

    private lateinit var sfwToggle: Switch
    private lateinit var startSessionBtn: Button
    private lateinit var characterScroll: RecyclerView

    private var selectedPersona: PersonaProfile? = null

    companion object {
        const val PERSONA_PICK_REQUEST = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_landing)
        Log.d("SessionLanding", "onCreate() called!")

        // 1. Bind views
        personaSlot = findViewById(R.id.personaPickerSlot)           // LinearLayout
        personaAvatar = findViewById(R.id.personaAvatar)             // ImageView inside slot
        personaNameTv = findViewById(R.id.personaName)               // TextView
        chatNameTv = findViewById(R.id.sessionTitle)                 // TextView
        chatDescTv = findViewById(R.id.chatDescription)              // TextView
        sfwToggle = findViewById(R.id.sfwToggle)
        startSessionBtn = findViewById(R.id.startSessionBtn)
        characterScroll = findViewById(R.id.characterScroll)

        // 2. Detect launch mode
        val characterProfileJson = intent.getStringExtra("CHARACTER_PROFILE_JSON") ?: ""
        Log.d("SessionLanding", "Received CHARACTER_PROFILE_JSON: ${characterProfileJson.take(500)}")

        val chatProfileJson = intent.getStringExtra("CHAT_PROFILE_JSON") ?: ""
        Log.d("SessionLanding", "Received CHAT_PROFILE_JSON: ${chatProfileJson.take(500)}")

        var chatId: String? = null
        var isOneOnOne = false
        var chatProfileObj: JSONObject? = null
        var characterProfileObj: JSONObject? = null

        when {
            chatProfileJson.isNotBlank() -> {
                chatProfileObj = JSONObject(chatProfileJson)
                isOneOnOne = chatProfileObj.optString("mode") == "ONE_ON_ONE"
                chatId = chatProfileObj.optString("id") // <-- Add this
            }
            characterProfileJson.isNotBlank() -> {
                characterProfileObj = JSONObject(characterProfileJson)
                isOneOnOne = true
                chatId = characterProfileObj.optString("id") // <-- Or whatever field your character profile uses as chat ID
            }
            else -> {
                Toast.makeText(this, "No chat or character data found!", Toast.LENGTH_LONG).show()
                finish()
                return
            }
        }

        // 3. Populate UI and hide/show elements
        if (isOneOnOne) {
            characterScroll.visibility = View.GONE
            val charName = characterProfileObj?.optString("name") ?: "Unknown Character"
            chatNameTv.text = charName
            // ... (rest of your code for description/personality)

            val summary = characterProfileObj?.optString("summary").orEmpty()
            val personality = characterProfileObj?.optString("personality").orEmpty()

            // Only add non-empty fields, each with a label
            val infoLines = mutableListOf<String>()
            if (personality.isNotBlank()) infoLines.add("Personality: $personality")
            if (summary.isNotBlank()) infoLines.add("Summary: $summary")

            chatDescTv.text = infoLines.joinToString("\n\n")
        }else {
                val title = chatProfileObj?.optString("title") ?: "Chat"
                chatNameTv.text = title
                val desc = chatProfileObj?.optString("description") ?: "No description."
                chatDescTv.text = desc
            }
        Log.d("SessionLanding", "Chat mode isOneOnOne=$isOneOnOne")


        // 4. Persona Picker
        personaSlot.setOnClickListener { showPersonaPicker() }

        // 5. Start Session
        startSessionBtn.setOnClickListener {
            val chatProfileJson = intent.getStringExtra("CHAT_PROFILE_JSON") ?: "{}"
            val characterProfilesJson = intent.getStringExtra("CHARACTER_PROFILE_JSON") ?: "[]"
            val userInput = "" // or get any initial user input or empty string

            runFacilitator(chatProfileJson, characterProfilesJson, userInput,
                onResult = { sessionProfileJsonRaw ->
                    Log.d("SessionLanding", "Facilitator session profile JSON:\n$sessionProfileJsonRaw")

                    // Parse facilitator JSON string to JSONObject
                    val sessionProfileObj = JSONObject(sessionProfileJsonRaw)

                    // Ensure chatId is included
                    val chatId = chatProfileObj?.optString("id")
                        ?: characterProfileObj?.optString("id")
                        ?: ""

                    if (chatId.isNotBlank() && !sessionProfileObj.has("id")) {
                        sessionProfileObj.put("id", chatId)
                    }

                    val updatedSessionProfileJson = sessionProfileObj.toString()

                    // Create or get session ID asynchronously, for example:
                    SessionManager.getOrCreateSessionFor(chatId,
                        onResult = { sessionId ->
                            val intent = Intent(this, MainActivity::class.java).apply {
                                putExtra("SESSION_PROFILE_JSON", updatedSessionProfileJson)
                                putExtra("SESSION_ID", sessionId)
                                putExtra("CHAT_ID", chatId)    // Explicit extra for safety
                            }
                            startActivity(intent)
                            finish()
                        },
                        onError = { error ->
                            Toast.makeText(this, "Couldn't start session: ${error.message}", Toast.LENGTH_LONG).show()
                        }
                    )
                },
                onError = { e ->
                    Toast.makeText(this, "Facilitator call failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            )

        }


    }

    // Persona picker
    private fun showPersonaPicker() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("personas")
            .get()
            .addOnSuccessListener { snap ->
                val personaList = snap.documents.mapNotNull { doc ->
                    doc.toObject(PersonaProfile::class.java)
                }
                if (personaList.isEmpty()) {
                    Toast.makeText(this, "No personas found!", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                val personaNames = personaList.map { it.name }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle("Select a Persona")
                    .setItems(personaNames) { _, which ->
                        setPersona(personaList[which])
                    }
                    .show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load personas: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun runFacilitator(
        chatProfileJson: String,
        characterProfilesJson: String,
        userInput: String,
        onResult: (sessionProfileJson: String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val prompt = buildSessionFacilitatorPrompt(chatProfileJson, characterProfilesJson, userInput)

        lifecycleScope.launch {
            try {
                val response = ModelClient.callModel(
                    promptJson = prompt,
                    forFacilitator = true,
                    openAiKey = BuildConfig.OPENAI_API_KEY,
                    mixtralKey = BuildConfig.MIXTRAL_API_KEY
                )
                // The response should be JSON string, so you can parse or just pass it through
                onResult(response.toString())
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    // Called when persona is picked
    private fun setPersona(persona: PersonaProfile) {
        selectedPersona = persona
        personaNameTv.text = persona.name
        // If you want to display the persona description in the main description field:
        // chatDescTv.text = persona.description
        if (persona.avatarUri.isNotBlank()) {
            personaAvatar.setImageURI(Uri.parse(persona.avatarUri))
        } else {
            personaAvatar.setImageResource(R.drawable.icon_01)
        }
    }
}
