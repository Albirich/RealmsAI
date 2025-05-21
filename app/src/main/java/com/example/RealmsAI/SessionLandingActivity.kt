package com.example.RealmsAI

import android.R.attr.data
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.ai.buildSessionFacilitatorPrompt
import com.example.RealmsAI.models.ChatProfile
import com.example.RealmsAI.models.Relationship
import com.example.RealmsAI.models.SessionLandingProfile
import com.example.RealmsAI.models.SessionProfile
import com.example.RealmsAI.network.ModelClient
import com.google.common.reflect.TypeToken
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import kotlinx.coroutines.launch


class SessionLandingActivity : AppCompatActivity() {

    companion object {
        private const val SLOT_COUNT = 6
        private const val CHARACTER_PICKER_REQ = 1001
        private const val RELATIONSHIP_REQ_CODE = 2345 // or any unique int, e.g., 1002
    }

    // UI elements
    private lateinit var slotButtons: List<ImageButton>
    private lateinit var sfwToggle: Switch
    private lateinit var startSessionBtn: Button
    private lateinit var relationshipEditor: Button // stub for relationship editing

    // State
    private var selectedSlotIndex = 0
    private val selectedCharIds = MutableList<String?>(SLOT_COUNT) { null }

    // Dummy: Replace with actual relationship editing logic/adapter
    private var currentSessionRelationships = mutableListOf<Relationship>()

    // Auth/user
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    private val charSelectLauncher = registerForActivityResult(StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val list = res.data?.getStringArrayListExtra("SELECTED_CHARS") ?: return@registerForActivityResult
            selectedCharIds[selectedSlotIndex] = list.firstOrNull()
            updateSlotButtonAvatar(selectedSlotIndex) // <-- Only update the one that changed
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_landing) // Layout: add 10 slot buttons, sfw toggle, start button

        // Example: slotButtons[0] is P1, [1] is B1, etc.
        slotButtons = listOf(
            findViewById(R.id.charButton1),
            findViewById(R.id.charButton2),
            findViewById(R.id.charButton3),
            findViewById(R.id.charButton4),
            findViewById(R.id.charButton5),
            findViewById(R.id.charButton6),
        )

        sfwToggle = findViewById(R.id.sfwToggle)
        startSessionBtn = findViewById(R.id.startSessionBtn)
        relationshipEditor = findViewById(R.id.relationshipBtn)

        // Slot picker logic
        slotButtons.forEachIndexed { idx, btn ->
            btn.setOnClickListener {
                selectedSlotIndex = idx
                val intent = Intent(this, CharacterSelectionActivity::class.java)
                selectedCharIds[idx]?.let {
                    intent.putStringArrayListExtra("PRESELECTED_CHARS", arrayListOf(it))
                }
                charSelectLauncher.launch(intent)
            }
        }

        // (Optional) Fill slots from chat profile if editing
        val chatProfileJson = intent.getStringExtra("CHAT_PROFILE_JSON")
        if (!chatProfileJson.isNullOrBlank()) {
            val chatProfile = Gson().fromJson(chatProfileJson, ChatProfile::class.java)
            chatProfile.characterIds.forEachIndexed { idx, id ->
                if (idx < selectedCharIds.size) {
                    selectedCharIds[idx] = id
                    updateSlotButtonAvatar(idx) // <-- Prefill avatar for each slot on load!
                }
            }
        }


        // Relationship editor
        relationshipEditor.setOnClickListener {
            // TODO: show a relationship editing dialog/list
            // For now, we'll just log/append a dummy relationship
            currentSessionRelationships.add(
                Relationship(fromId = selectedCharIds[0] ?: "", toId = selectedCharIds[1] ?: "", type = "friend", description = "Met at the academy")
            )
            Toast.makeText(this, "Added dummy relationship!", Toast.LENGTH_SHORT).show()
        }
        val intent = Intent(this, SessionRelationshipActivity::class.java)
        intent.putStringArrayListExtra("PARTICIPANT_IDS", ArrayList(selectedCharIds.filterNotNull()))
        startActivityForResult(intent, RELATIONSHIP_REQ_CODE)

        fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)
            if (requestCode == RELATIONSHIP_REQ_CODE && resultCode == Activity.RESULT_OK) {
                val relJson = data?.getStringExtra("RELATIONSHIPS_JSON") ?: "[]"
                val relationships: List<Relationship> = Gson().fromJson(
                    relJson,
                    object : TypeToken<List<Relationship>>() {}.type
                )

            }
        }


        startSessionBtn.setOnClickListener {
            // Gather participants (remove nulls, only send slots with assigned character/user)
            val participants = selectedCharIds.filterNotNull()
            val sessionLandingProfile = SessionLandingProfile(
                relationships = currentSessionRelationships,
                participants = participants,
                sfwOnly = sfwToggle.isChecked
            )
            val sessionLandingJson = Gson().toJson(sessionLandingProfile)

            // Pass this sessionLandingJson (and the rest of your profiles) to your facilitator prompt builder
            // Example:
            val facilitatorPrompt = buildSessionFacilitatorPrompt(
                chatProfileJson = chatProfileJson ?: "",
                characterProfilesJson = "[]", // Replace with your real character profiles JSON
                personaProfileJson = "{}",    // Replace with actual persona profile JSON
                sessionLandingJson = sessionLandingJson,
                sfwOnly = sfwToggle.isChecked
            )
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            val characterId = selectedCharIds.firstOrNull { it != userId } ?: ""

            // Call runFacilitator as before
            runFacilitator(
                prompt = facilitatorPrompt,
                onResult = { sessionProfileJsonRaw ->
                    // Parse sessionProfile and extract all needed fields for the session doc
                    val sessionProfile = Gson().fromJson(sessionProfileJsonRaw, SessionProfile::class.java)
                    SessionManager.createSession(
                        sessionProfile = sessionProfile,
                        chatId = sessionProfile.chatId,          // <-- add this
                        userId = userId,
                        characterId = characterId,
                        onResult = { sessionId ->
                            startActivity(Intent(this, MainActivity::class.java).apply {
                                putExtra("SESSION_ID", sessionId)
                                putExtra("SESSION_PROFILE_JSON", sessionProfileJsonRaw)
                            })
                            finish()
                        },
                        onError = {
                            Toast.makeText(this, "Failed to start session.", Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                onError = { error ->
                    Toast.makeText(this, "Failed to start session: $error", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }



    private fun runFacilitator(
        prompt: String,
        onResult: (String) -> Unit,
        onError: (Throwable) -> Unit = {}
    ) {
        lifecycleScope.launch {
            try {
                val payload = org.json.JSONObject().apply {
                    put("model", "gpt-4.1-nano-2025-04-14")
                    put("messages", org.json.JSONArray().put(
                        org.json.JSONObject()
                            .put("role", "system")
                            .put("content", prompt)
                    ))
                    put("max_tokens", 300)
                }.toString()

                // Replace with your actual callModel usage
                val facJson = ModelClient.callModel(
                    promptJson = payload,
                    forFacilitator = true,
                    openAiKey = BuildConfig.OPENAI_API_KEY,
                    mixtralKey = BuildConfig.MIXTRAL_API_KEY
                )
                val facContentStr = facJson.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()

                if (facContentStr.isNotBlank()) {
                    onResult(facContentStr)
                } else {
                    onError(Exception("Facilitator returned blank content"))
                }
            } catch (e: Exception) {
                onError(e)
            }
        }

    }

    // Optional: Loads avatars for buttons if you want
    private fun updateSlotButtonAvatar(idx: Int) {
        val charId = selectedCharIds[idx] ?: return
        loadAvatarUriForCharacter(charId) { uri ->
            if (!uri.isNullOrBlank()) {
                slotButtons[idx].setImageURI(Uri.parse(uri))
            } else {
                // show placeholder
            }
        }
    }


    fun loadAvatarUriForCharacter(characterId: String, onUriLoaded: (String?) -> Unit) {
        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("characters").document(characterId)
            .get()
            .addOnSuccessListener { doc ->
                val url = doc.getString("avatarUri")
                onUriLoaded(url)
            }
            .addOnFailureListener { e ->
                onUriLoaded(null)
            }
    }

}
