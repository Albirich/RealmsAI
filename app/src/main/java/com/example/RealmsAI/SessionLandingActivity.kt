package com.example.RealmsAI

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.adapters.CollectionAdapter.CharacterRowAdapter
import com.example.RealmsAI.PlayerSlotAdapter
import com.example.RealmsAI.models.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson

class SessionLandingActivity : AppCompatActivity() {
    // Views
    private lateinit var charRecycler: RecyclerView
    private lateinit var playerRecycler: RecyclerView
    private lateinit var relationshipBtn: Button
    private lateinit var startSessionBtn: Button
    private lateinit var titleView: TextView
    private lateinit var descriptionView: TextView

    // State
    private var chatProfile: ChatProfile? = null
    private var characterProfiles: List<CharacterProfile> = emptyList()
    private var slotRoster: List<SlotInfo> = emptyList()
    private var playerSlots: MutableList<PlayerSlot> = mutableListOf()
    private var playerAssignments: MutableMap<String, String> = mutableMapOf()
    private var userAssignments: MutableMap<String, String> = mutableMapOf()
    private var userList: List<String> = emptyList()
    private var relationships: MutableList<Relationship> = mutableListOf()
    private var personaProfiles: List<PersonaProfile> = emptyList()

    private val HOST_SLOT = "player1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_landing)

        // Init views
        charRecycler = findViewById(R.id.charactersRecycler)
        playerRecycler = findViewById(R.id.playerRecycler)
        relationshipBtn = findViewById(R.id.sessrelationshipBtn)
        startSessionBtn = findViewById(R.id.startSessionBtn)
        titleView = findViewById(R.id.sessionTitle)
        descriptionView = findViewById(R.id.chatDescription)

        val sessionProfileJson = intent.getStringExtra("SESSION_PROFILE_JSON")
        val chatProfileJson = intent.getStringExtra("CHAT_PROFILE_JSON")
        val characterProfilesJson = intent.getStringExtra("CHARACTER_PROFILES_JSON")

        val isEditMode: Boolean
        val sessionProfile: SessionProfile
        val chatProfile: ChatProfile?

        if (sessionProfileJson != null) {
            // Editing/resuming existing session
            sessionProfile = Gson().fromJson(sessionProfileJson, SessionProfile::class.java)
            chatProfile = null
            isEditMode = true
            relationships = sessionProfile.relationships.toMutableList()
            // Load characters if not present (should always have slotRoster, but just in case)
            val charIds = sessionProfile.slotRoster.map { it.id }
            loadCharactersByIds(charIds) { profiles ->
                characterProfiles = profiles
                setupSlotRosterAndUI(profiles, sessionProfile)
            }
        } else if (chatProfileJson != null) {
            // New session from chat template
            chatProfile = Gson().fromJson(chatProfileJson, ChatProfile::class.java)
            sessionProfile = buildNewSessionProfileFromChatProfile(chatProfile)
            isEditMode = false
            relationships = chatProfile.relationships?.toMutableList() ?: mutableListOf()
            // Load characters by IDs from chat profile
            val charIds = chatProfile.characterIds
            loadCharactersByIds(charIds) { profiles ->
                characterProfiles = profiles
                setupSlotRosterAndUI(profiles, sessionProfile)
            }
        } else if (characterProfilesJson != null) {
            // Launch from CharacterHub with one or more profiles
            val characterProfilesJson = intent.getStringExtra("CHARACTER_PROFILES_JSON")
            characterProfiles = try {
                Gson().fromJson(characterProfilesJson, Array<CharacterProfile>::class.java).toList()
            } catch (e: Exception) {
                // Try single object as a fallback
                listOf(Gson().fromJson(characterProfilesJson, CharacterProfile::class.java))
            }
            sessionProfile = buildNewSessionProfileFromCharacterProfiles(characterProfiles)
            chatProfile = null
            isEditMode = false
            relationships = characterProfiles.firstOrNull()?.relationships?.toMutableList() ?: mutableListOf()
            setupSlotRosterAndUI(characterProfiles, sessionProfile)
        } else {
            Toast.makeText(this, "Error: No session/chat/character data found.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Player slot setup
        playerSlots.clear()
        playerSlots.add(PlayerSlot(slotId = "player1", name = "Player 1"))
        userAssignments[HOST_SLOT] = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        // Load personas and setup PlayerSlotAdapter
        loadPersonasFromFirestore { loadedPersonas ->
            personaProfiles = loadedPersonas
            playerRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            playerRecycler.adapter = PlayerSlotAdapter(playerSlots, personaProfiles) { slotId ->
                val intent = Intent(this, PersonaSelectionActivity::class.java)
                startActivityForResult(intent, 101)
            }
        }

        // Relationship button
        relationshipBtn.setOnClickListener {
            val allIds = mutableListOf<String>()
            val playerPersonaId = playerAssignments["player1"]
            if (!playerPersonaId.isNullOrBlank()) allIds.add(playerPersonaId) else allIds.add("player1")
            allIds.addAll(slotRoster.map { it.id })

            Log.d("REL_DEBUG", "Opening relationships with IDs: $allIds")
            val intent = Intent(this, SessionRelationshipActivity::class.java)
            intent.putStringArrayListExtra("PARTICIPANT_IDS", ArrayList(allIds))
            intent.putExtra("RELATIONSHIPS_JSON", Gson().toJson(relationships))
            if (chatProfile != null) {
                intent.putExtra("CHAT_PROFILE_JSON", Gson().toJson(chatProfile))
            }
            startActivityForResult(intent, 102)
        }

        // Start Session button
        startSessionBtn.setOnClickListener {
            val participantIds: List<String> = playerSlots.map { it.slotId } + slotRoster.map { it.slot }
            val userListNow = userAssignments.values.distinct()
            val sessionProfileToSend = sessionProfile.copy(
                participants = participantIds,
                playerAssignments = playerAssignments,
                userAssignments = userAssignments,
                userList = userListNow,
                relationships = relationships,
                slotRoster = slotRoster,
                personaProfiles = personaProfiles
            )

            saveSessionProfile(sessionProfileToSend, sessionProfile.sessionId)

            val intent = Intent(this, MainActivity::class.java)
            val greeting = chatProfile?.firstmessage
                ?: characterProfiles.firstOrNull()?.greeting
                ?: ""
            intent.putExtra("SESSION_PROFILE_JSON", Gson().toJson(sessionProfileToSend))
            intent.putExtra("SESSION_ID", sessionProfile.sessionId)
            intent.putExtra("GREETING", greeting)
            startActivity(intent)
            finish()
        }
    }

    // Build slot roster and update UI with characters
    private fun setupSlotRosterAndUI(profiles: List<CharacterProfile>, sessionProfile: SessionProfile) {
        slotRoster = profiles.mapIndexed { i, profile ->
            val currentOutfitName = profile.currentOutfit.ifBlank { profile.outfits.firstOrNull()?.name ?: "" }
            val currentOutfitObj = profile.outfits.find { it.name == currentOutfitName }
            val poses = currentOutfitObj?.poseSlots
                ?.filter { !it.name.isNullOrBlank() && !it.uri.isNullOrBlank() }
                ?.associate { it.name to it.uri!! }
                ?: emptyMap()

            SlotInfo(
                name = profile.name,
                slot = "B${i+1}",
                summary = profile.summary ?: "",
                id = profile.id,
                outfits = profile.outfits.map { it.name },
                currentOutfit = currentOutfitName,
                sfwOnly = profile.sfwOnly,
                relationships = profile.relationships,
                bubbleColor = profile.bubbleColor,
                textColor = profile.textColor,
                personality = profile.personality,
                backstory = profile.backstory,
                privateDescription = profile.privateDescription,
                poses = poses
            )
        }

        // CharacterRowAdapter for NPCs
        charRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        charRecycler.adapter = CharacterRowAdapter(
            profiles,
            onClick = {/* Optionally handle character selection */ }
        )

        // Title & Description
        titleView.text = sessionProfile.title.ifBlank {
            profiles.firstOrNull()?.name ?: "Session"
        }
        val first = profiles.firstOrNull()
        descriptionView.text = listOfNotNull(
            first?.personality?.takeIf { it.isNotBlank() },
            first?.backstory?.takeIf { it.isNotBlank() }
        ).joinToString("\n\n").ifBlank {
            sessionProfile.sessionDescription ?: ""
        }
    }

    // Load all personas for current user
    private fun loadPersonasFromFirestore(onLoaded: (List<PersonaProfile>) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("personas")
            .whereEqualTo("author", FirebaseAuth.getInstance().currentUser?.uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val personas = snapshot.documents.mapNotNull { it.toObject(PersonaProfile::class.java) }
                onLoaded(personas)
            }
            .addOnFailureListener { onLoaded(emptyList()) }
    }

    // Load character profiles by ID from Firestore
    private fun loadCharactersByIds(ids: List<String>, onLoaded: (List<CharacterProfile>) -> Unit) {
        if (ids.isEmpty()) return onLoaded(emptyList())
        FirebaseFirestore.getInstance()
            .collection("characters")
            .whereIn("id", ids)
            .get()
            .addOnSuccessListener { snap ->
                val chars = snap.documents.mapNotNull { it.toObject(CharacterProfile::class.java) }
                onLoaded(chars)
            }
            .addOnFailureListener { onLoaded(emptyList()) }
    }

    // Build session from chat template
    fun buildNewSessionProfileFromChatProfile(chatProfile: ChatProfile): SessionProfile {
        val hostUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val playerSlots = mutableListOf("player1")
        val userAssignments = mapOf("player1" to hostUid)
        val userList = userAssignments.values.distinct()
        val slotRoster = chatProfile.characterIds.mapIndexed { i, id ->
            SlotInfo(
                name = "", // name will be filled in after you load full profiles
                slot = "B${i+1}",
                summary = "",
                id = id,
                outfits = emptyList(),
                currentOutfit = "",
                sfwOnly = true,
                relationships = emptyList(),
                bubbleColor = "#CCCCCC",
                textColor = "#000000",
                personality = "",
                backstory = "",
                privateDescription = "",
                poses = emptyMap()
            )
        }

        val sessionId = java.util.UUID.randomUUID().toString()
        return SessionProfile(
            sessionId = sessionId,
            chatId = chatProfile.id,
            title = chatProfile.title,
            sessionDescription = chatProfile.description,
            chatMode = chatProfile.mode.name,
            sfwOnly = chatProfile.sfwOnly,
            participants = playerSlots + slotRoster.map { it.slot },
            playerAssignments = emptyMap(),
            userAssignments = userAssignments,
            userList = userList,
            relationships = chatProfile.relationships?.toList() ?: emptyList(),
            slotRoster = slotRoster,
            areas = chatProfile.areas ?: emptyList(),
            personaProfiles = emptyList()
        )
    }

    // Build session from direct character selection (list)
    fun buildNewSessionProfileFromCharacterProfiles(charProfiles: List<CharacterProfile>): SessionProfile {
        val hostUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val playerSlots = mutableListOf("player1")
        val userAssignments = mapOf("player1" to hostUid)
        val userList = userAssignments.values.distinct()
        val slotRoster = charProfiles.mapIndexed { i, profile ->
            val currentOutfitName = profile.currentOutfit.ifBlank { profile.outfits.firstOrNull()?.name ?: "" }
            val currentOutfitObj = profile.outfits.find { it.name == currentOutfitName }
            val poses = currentOutfitObj?.poseSlots
                ?.filter { !it.name.isNullOrBlank() && !it.uri.isNullOrBlank() }
                ?.associate { it.name to it.uri!! }
                ?: emptyMap()

            SlotInfo(
                name = profile.name,
                slot = "B${i + 1}",
                summary = profile.summary ?: "",
                id = profile.id,
                outfits = profile.outfits.map { it.name },
                currentOutfit = currentOutfitName,
                sfwOnly = profile.sfwOnly,
                relationships = profile.relationships,
                bubbleColor = profile.bubbleColor,
                textColor = profile.textColor,
                personality = profile.personality,
                backstory = profile.backstory,
                privateDescription = profile.privateDescription,
                poses = poses
            )
        }
        val sessionId = java.util.UUID.randomUUID().toString()
        return SessionProfile(
            sessionId = sessionId,
            chatId = charProfiles.firstOrNull()?.id ?: "",
            title = charProfiles.firstOrNull()?.name ?: "Session",
            sessionDescription = charProfiles.firstOrNull()?.backstory ?: "",
            chatMode = "SANDBOX",
            sfwOnly = true,
            participants = playerSlots + slotRoster.map { it.slot },
            playerAssignments = emptyMap(),
            userAssignments = userAssignments,
            userList = userList,
            relationships = charProfiles.firstOrNull()?.relationships?.toList() ?: emptyList(),
            slotRoster = slotRoster,
            areas = charProfiles.firstOrNull()?.areas ?: emptyList(),
            personaProfiles = emptyList()
        )
    }

    // --- Activity Result for Persona Selection & Relationships ---
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d("SessionLanding", "onActivityResult fired. requestCode=$requestCode resultCode=$resultCode data=$data")
        if (resultCode != RESULT_OK || data == null) return

        when (requestCode) {
            101 -> {
                val personaId = data.getStringExtra("SELECTED_PERSONA_ID") ?: return
                Log.d("SessionLanding", "Selected personaId: $personaId")
                val slot = playerSlots[0]
                playerAssignments[slot.slotId] = personaId
                (playerRecycler.adapter as? PlayerSlotAdapter)?.setPersonaForSlot(slot.slotId, personaId)
            }
            102 -> {
                val relationshipsJson = data.getStringExtra("RELATIONSHIPS_JSON") ?: "[]"
                relationships = Gson().fromJson(relationshipsJson, Array<Relationship>::class.java).toMutableList()
            }
        }
    }

    private fun saveSessionProfile(sessionProfile: SessionProfile, sessionId: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("sessions")
            .document(sessionId)
            .set(sessionProfile)
            .addOnSuccessListener {
                Log.d("Firestore", "Session saved: $sessionId")
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Failed to save session: $e")
            }
    }

}
