package com.example.RealmsAI

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.RealmsAI.ai.buildSessionFacilitatorPrompt
import com.example.RealmsAI.models.CharacterProfile
import com.example.RealmsAI.models.ChatProfile
import com.example.RealmsAI.models.PersonaProfile
import com.example.RealmsAI.models.Relationship
import com.example.RealmsAI.models.SessionLandingProfile
import com.example.RealmsAI.models.SessionProfile
import com.example.RealmsAI.network.ModelClient
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import kotlinx.coroutines.launch

class SessionLandingActivity : AppCompatActivity() {

    companion object {
        private const val SLOT_COUNT = 6
        private const val PERSONA_SLOT_COUNT = 1 // Set to more when multi-persona is supported
        private const val RELATIONSHIP_REQ_CODE = 2345
    }

    private lateinit var slotButtons: List<ImageButton>
    private lateinit var sfwToggle: Switch
    private lateinit var startSessionBtn: Button
    private lateinit var relationshipEditor: Button

    private var selectedSlotIndex = 0
    private val selectedCharIds = MutableList<String?>(SLOT_COUNT) { null }
    private val selectedPersonaIds = MutableList<String?>(PERSONA_SLOT_COUNT) { null }
    private lateinit var personaSelectLaunchers: List<ActivityResultLauncher<Intent>>
    private var currentSessionRelationships = mutableListOf<Relationship>()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // These will store loaded profile data after Firestore fetch
    private var loadedCharacterProfiles = listOf<CharacterProfile>()
    private var loadedPersonaProfiles = listOf<PersonaProfile>()

    private val charSelectLauncher = registerForActivityResult(StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val list = res.data?.getStringArrayListExtra("SELECTED_CHARS") ?: return@registerForActivityResult
            selectedCharIds[selectedSlotIndex] = list.firstOrNull()
            updateSlotButtonAvatar(selectedSlotIndex)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_landing)

        val chatProfileJson = intent.getStringExtra("CHAT_PROFILE_JSON")
        val characterJson = intent.getStringExtra("CHARACTER_PROFILE_JSON")

        val chatDescriptionView = findViewById<TextView>(R.id.chatDescription)
        val slotButtonsContainer = findViewById<ViewGroup>(R.id.characterscroll)
        slotButtons = listOf(
            findViewById(R.id.charButton1),
            findViewById(R.id.charButton2),
            findViewById(R.id.charButton3),
            findViewById(R.id.charButton4),
            findViewById(R.id.charButton5),
            findViewById(R.id.charButton6),
        )

        // --- Persona picker(s) ---
        personaSelectLaunchers = List(PERSONA_SLOT_COUNT) { personaIdx ->
            registerForActivityResult(StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val personaId = result.data?.getStringExtra("SELECTED_PERSONA_ID") ?: return@registerForActivityResult
                    selectedPersonaIds[personaIdx] = personaId
                    updatePersonaUI(personaIdx, personaId)
                }
            }
        }

        // Single persona UI example (expand as needed)
        val personaSlot = findViewById<LinearLayout>(R.id.personaPickerSlot)
        setPlaceholderPersonaUI(0)
        personaSlot.setOnClickListener {
            val intent = Intent(this, PersonaSelectionActivity::class.java)
            personaSelectLaunchers[0].launch(intent)
        }
        // Pre-fill from intent if needed
        val initialPersonaId = intent.getStringExtra("INITIAL_PERSONA_ID")
        if (!initialPersonaId.isNullOrBlank()) {
            selectedPersonaIds[0] = initialPersonaId
            updatePersonaUI(0, initialPersonaId)
        }

        sfwToggle = findViewById(R.id.sfwToggle)
        startSessionBtn = findViewById(R.id.startSessionBtn)
        relationshipEditor = findViewById(R.id.relationshipBtn)

        // --- Main logic: either chatProfile OR characterProfile ---
        val firestore = FirebaseFirestore.getInstance()
        var isOneOnOne = false
        var characterProfile: CharacterProfile? = null
        var chatProfile: ChatProfile? = null

        // ---- CASE 1: Group chat (chatProfile present) ----
        if (!chatProfileJson.isNullOrBlank()) {
            chatProfile = Gson().fromJson(chatProfileJson, ChatProfile::class.java)
            slotButtonsContainer.visibility = View.VISIBLE
            chatProfile.characterIds.forEachIndexed { idx, id ->
                if (idx < selectedCharIds.size) {
                    selectedCharIds[idx] = id
                    updateSlotButtonAvatar(idx)
                }
            }
            chatDescriptionView.text = chatProfile.description ?: "No description available."

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
        }
        // ---- CASE 2: One-on-one (character profile only) ----
        else if (!characterJson.isNullOrBlank()) {
            characterProfile = Gson().fromJson(characterJson, CharacterProfile::class.java)
            isOneOnOne = true
            slotButtonsContainer.visibility = View.GONE
            chatDescriptionView.text = characterProfile.personality.ifBlank { characterProfile.privateDescription }
            selectedCharIds[0] = characterProfile.id // slot 0 will be the single bot/partner
        } else {
            // Error fallback: neither provided
            slotButtonsContainer.visibility = View.GONE
            chatDescriptionView.text = "No chat or character data."
            Toast.makeText(this, "No chat or character data!", Toast.LENGTH_SHORT).show()
        }

        // Relationship editor (works for group only, does nothing in 1-on-1)
        relationshipEditor.setOnClickListener {
            val safeCharIds = selectedCharIds.filterNotNull()
            val safePersonaIds = selectedPersonaIds.filterNotNull()
            val allParticipantIds = ArrayList<String>()
            allParticipantIds.addAll(safeCharIds)
            allParticipantIds.addAll(safePersonaIds)

            Log.d("Relationship", "Selected char IDs: $safeCharIds")
            Log.d("Relationship", "Selected persona IDs: $safePersonaIds")
            Log.d("Relationship", "All participants: $allParticipantIds")

            val intent = Intent(this, SessionRelationshipActivity::class.java)
            intent.putStringArrayListExtra("PARTICIPANT_IDS", allParticipantIds)
            startActivityForResult(intent, RELATIONSHIP_REQ_CODE)
        }

        // ---- SESSION START: Gather everything you need ----
        startSessionBtn.setOnClickListener {
            val safeCharIds = selectedCharIds.filterNotNull()
            val safePersonaIds = selectedPersonaIds.filterNotNull()
            val sessionLandingProfile = SessionLandingProfile(
                relationships = currentSessionRelationships,
                participants = safeCharIds + safePersonaIds,
                sfwOnly = sfwToggle.isChecked
            )
            val sessionLandingJson = Gson().toJson(sessionLandingProfile)

            // Collect PersonaProfile(s)
            val personaIds = safePersonaIds
            val personaTasks = personaIds.map { personaId ->
                firestore.collection("personas").document(personaId).get()
            }

            // 1) GROUP CHAT: Fetch all character profiles, then personas, then send
            if (!chatProfileJson.isNullOrBlank()) {
                val characterIds = chatProfile!!.characterIds.filterNotNull()
                val characterTasks = characterIds.map { id -> firestore.collection("characters").document(id).get() }
                Tasks.whenAllSuccess<DocumentSnapshot>(characterTasks).addOnSuccessListener { docs ->
                    val rawProfiles = docs.mapNotNull { it.toObject(CharacterProfile::class.java) }
                    // **Inject relationships here**
                    loadedCharacterProfiles = rawProfiles.map { profile ->
                        profile.copy(
                            relationships = currentSessionRelationships.filter { it.fromId == profile.id }
                        )
                    }
                    val characterProfilesJson = Gson().toJson(loadedCharacterProfiles)

                    Tasks.whenAllSuccess<DocumentSnapshot>(personaTasks).addOnSuccessListener { personaDocs ->
                        val rawPersonas = personaDocs.mapNotNull { it.toObject(PersonaProfile::class.java) }
                        loadedPersonaProfiles = rawPersonas.map { profile ->
                            profile.copy(
                                relationships = currentSessionRelationships.filter { it.fromId == profile.id }
                            )
                        }
                        val personaProfilesJson = Gson().toJson(loadedPersonaProfiles)

                        sendPromptAndStartSession(
                            chatProfileJson = chatProfileJson,
                            characterProfilesJson = characterProfilesJson,
                            personaProfilesJson = personaProfilesJson,
                            sessionLandingJson = sessionLandingJson
                        )
                    }
                }
            }
            // 2) ONE-ON-ONE: Only one character profile (already in memory), fetch persona, then send
            else if (characterProfile != null) {
                loadedCharacterProfiles = listOf(
                    characterProfile.copy(
                        relationships = currentSessionRelationships.filter { it.fromId == characterProfile.id }
                    )
                )
                val characterProfilesJson = Gson().toJson(loadedCharacterProfiles)

                Tasks.whenAllSuccess<DocumentSnapshot>(personaTasks).addOnSuccessListener { personaDocs ->
                    loadedPersonaProfiles = personaDocs.mapNotNull { it.toObject(PersonaProfile::class.java) }
                        .map { profile ->
                            profile.copy(
                                relationships = currentSessionRelationships.filter { it.fromId == profile.id }
                            )
                        }
                    val personaProfilesJson = Gson().toJson(loadedPersonaProfiles)

                    sendPromptAndStartSession(
                        chatProfileJson = "",
                        characterProfilesJson = characterProfilesJson,
                        personaProfilesJson = personaProfilesJson,
                        sessionLandingJson = sessionLandingJson
                    )
                }
            }
        }
    }

    // Place onActivityResult as a **top-level override**, NOT inside onCreate!
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RELATIONSHIP_REQ_CODE && resultCode == Activity.RESULT_OK && data != null) {
            val relationshipsJson = data.getStringExtra("RELATIONSHIPS_JSON")
            val newRelationships = Gson().fromJson(relationshipsJson, Array<Relationship>::class.java).toList()
            currentSessionRelationships = newRelationships.toMutableList()
        }
    }

    private fun sendPromptAndStartSession(
        chatProfileJson: String,
        characterProfilesJson: String,
        personaProfilesJson: String,
        sessionLandingJson: String
    ) {
        Log.d("SessionLanding", "chatProfileJson: $chatProfileJson")
        Log.d("SessionLanding", "characterProfilesJson: $characterProfilesJson")
        Log.d("SessionLanding", "personaProfilesJson: $personaProfilesJson")
        Log.d("SessionLanding", "sessionLandingJson: $sessionLandingJson")

        val facilitatorPrompt = buildSessionFacilitatorPrompt(
            chatProfileJson = chatProfileJson,
            characterProfilesJson = characterProfilesJson,
            personaProfileJson = personaProfilesJson,
            sessionLandingJson = sessionLandingJson,
            sfwOnly = sfwToggle.isChecked
        )

        runFacilitator(
            prompt = facilitatorPrompt,
            onResult = { sessionProfileJsonRaw ->
                Log.d("FacilitatorPrompt", "Sending prompt to facilitator:\n$facilitatorPrompt")
                Log.d("FacilitatorResponse", sessionProfileJsonRaw)

                val safeCharIds = selectedCharIds.filterNotNull()
                val safePersonaIds = selectedPersonaIds.filterNotNull()
                val someListOfExtraPlayerUserIds = listOf(currentUserId)

                val allUserParticipants = mutableListOf<String>()
                allUserParticipants.add(currentUserId)
                allUserParticipants.addAll(someListOfExtraPlayerUserIds)

                val allCharacterAndPersonaIds = safeCharIds + safePersonaIds

                val sessionProfile = Gson().fromJson(sessionProfileJsonRaw, SessionProfile::class.java)
                SessionManager.createSession(
                    sessionProfile = sessionProfile,
                    chatProfileJson = chatProfileJson,
                    userId = currentUserId,
                    characterProfilesJson = characterProfilesJson,
                    characterId = selectedCharIds.firstOrNull { it != currentUserId } ?: "",
                    extraParticipants = allUserParticipants, // <<------ PASS YOURS HERE
                    onResult = { sessionId ->
                        startActivity(Intent(this, MainActivity::class.java).apply {
                            putExtra("CHAT_ID", sessionProfile.chatId)
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

    private fun setPlaceholderPersonaUI(personaIdx: Int) {
        val personaAvatar = findViewById<ImageView>(R.id.personaAvatar)
        val personaName = findViewById<TextView>(R.id.personaName)
        personaAvatar.setImageResource(R.drawable.placeholder_avatar)
        personaName.text = "Select Persona"
    }

    private fun updatePersonaUI(personaIdx: Int, personaId: String) {
        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("personas")
            .document(personaId)
            .get()
            .addOnSuccessListener { doc ->
                val persona = doc.toObject(PersonaProfile::class.java)
                if (persona != null) {
                    val personaAvatar = findViewById<ImageView>(R.id.personaAvatar)
                    val personaName = findViewById<TextView>(R.id.personaName)
                    val avatarUri = persona.avatarUri
                    if (!avatarUri.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(avatarUri)
                            .placeholder(R.drawable.placeholder_avatar)
                            .error(R.drawable.placeholder_avatar)
                            .into(personaAvatar)
                    } else {
                        personaAvatar.setImageResource(R.drawable.placeholder_avatar)
                    }
                    personaName.text = persona.name
                }
            }
            .addOnFailureListener {
                findViewById<ImageView>(R.id.personaAvatar).setImageResource(R.drawable.placeholder_avatar)
                findViewById<TextView>(R.id.personaName).text = "Unknown Persona"
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

    private fun updateSlotButtonAvatar(idx: Int) {
        val charId = selectedCharIds[idx] ?: return
        loadAvatarUriForCharacter(charId) { uri ->
            if (!uri.isNullOrBlank()) {
                slotButtons[idx].setImageURI(Uri.parse(uri))
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
            .addOnFailureListener {
                onUriLoaded(null)
            }
    }
}
