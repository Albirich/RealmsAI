package com.example.RealmsAI

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.adapters.CollectionAdapter.CharacterRowAdapter
import com.example.RealmsAI.ai.Facilitator
import com.example.RealmsAI.models.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.collections.MutableList
import com.example.RealmsAI.models.CharacterProfile
import com.example.RealmsAI.models.ModeSettings.RPGSettings
import kotlin.jvm.java


class SessionLandingActivity : AppCompatActivity() {

    // --- Views ---
    private lateinit var charRecycler: RecyclerView
    private lateinit var playerRecycler: RecyclerView
    private lateinit var relationshipBtn: Button
    private lateinit var startSessionBtn: Button
    private lateinit var titleView: TextView
    private lateinit var descriptionView: TextView
    private lateinit var addPlayerButton: ImageButton
    private lateinit var addcharacter: ImageButton
    private lateinit var sfwToggle: Switch



    // --- State ---
    private var chatProfile: ChatProfile? = null
    private var inviteProfile: InviteProfile? = null
    private var characterProfiles: List<CharacterProfile> = emptyList()
    private var relationships: MutableList<Relationship> = mutableListOf()
    private var cleanedRelationships: MutableList<Relationship> = mutableListOf()
    private var userAssignments: MutableMap<String, String> = mutableMapOf()
    private var personaProfiles: List<PersonaProfile> = emptyList()
    private var sessionProfile: SessionProfile? = null
    private var secretDesc: String = ""
    private var cleanedSecretDescription: String = ""
    private val cleanedDescription: String = ""
    private var loadedAreas: List<Area> = emptyList()
    private var lobbySessionId: String? = null
    private var sessionListener: ListenerRegistration? = null
    private var buildDialogShown = false
    private var lastSlotRoster: List<SlotProfile>? = null

    private var progressDialog: AlertDialog? = null
    private var progressTextView: TextView? = null
    private var progressBar: ProgressBar? = null

    private var lastUserList: List<String>? = null
    private var lastUserAssignments: Map<String, String?>? = null

    private var sessionSummary = ""
    private val HOST_SLOT = "player1"
    private var chatMode = "SANDBOX"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_landing)

        // --- Find Views ---
        charRecycler = findViewById(R.id.charactersRecycler)
        playerRecycler = findViewById(R.id.playerRecycler)
        addcharacter = findViewById(R.id.addcharacter)
        relationshipBtn = findViewById(R.id.sessrelationshipBtn)
        startSessionBtn = findViewById(R.id.startSessionBtn)
        titleView = findViewById(R.id.sessionTitle)
        descriptionView = findViewById(R.id.chatDescription)
        sfwToggle = findViewById(R.id.sfwToggle)

        addPlayerButton = findViewById(R.id.addPlayerButton)
        addPlayerButton.setOnClickListener {
            showFriendInviteDialog()
        }

        // --- Get intents from previous screens ---
        val sessionProfileJson = intent.getStringExtra("SESSION_PROFILE_JSON")
        val chatProfileJson = intent.getStringExtra("CHAT_PROFILE_JSON")
        val characterProfilesJson = intent.getStringExtra("CHARACTER_PROFILES_JSON")
        val inviteProfilesJson = intent.getStringExtra("INVITE_PROFILE_JSON")
        var enteredFrom = ""
        // --- Load state depending on navigation ---
        when {
            // Editing/resuming
            sessionProfileJson != null -> {
                sessionProfile = Gson().fromJson(sessionProfileJson, SessionProfile::class.java)
                lobbySessionId = sessionProfile!!.sessionId
                chatProfile = null
                relationships = sessionProfile?.slotRoster?.flatMap { it.relationships }?.toMutableList() ?: mutableListOf()
                displaySession(sessionProfile!!)
                enteredFrom = "Sessionhub"
            }
            inviteProfilesJson != null -> {
                inviteProfile = Gson().fromJson(inviteProfilesJson, InviteProfile::class.java)
                Log.d("InviteDebug", "InviteProfile.title = ${inviteProfile?.title}")
                Log.d("InviteDebug", "InviteProfile.sessionDescription = ${inviteProfile?.sessionDescription}")
                Log.d("InviteDebug", "InviteProfile.userList1 = ${inviteProfile?.userList}")
                lobbySessionId = inviteProfile!!.sessionId
                titleView.text = inviteProfile?.title ?: "Session"
                descriptionView.text = inviteProfile?.sessionDescription ?: ""
                // Relationships
                relationships = inviteProfile?.relationships?.toMutableList() ?: mutableListOf()

                // SFW toggle (if present)
                findViewById<Switch>(R.id.sfwToggle)?.isChecked = inviteProfile?.sfwOnly ?: true

                // Load characters from Firestore by ID
                val charIds = inviteProfile?.characterIds ?: emptyList()
                loadCharactersByIds(charIds) { profiles ->
                    characterProfiles = profiles
                    Log.d("InviteDebug", "Loaded ${profiles.size} characters for invite: $charIds")

                    // Set up the character recycler view ONLY
                    charRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
                    charRecycler.adapter = CharacterRowAdapter(
                        profiles,
                        onClick = { /* preview or remove if you need */ }
                    )
                }

                Log.d("InviteDebug", "InviteProfile.userList2 = ${inviteProfile?.userList}")

                // Load other session settings (areas, etc) as needed
                loadedAreas = inviteProfile?.areas?.toMutableList() ?: mutableListOf()
                chatMode = inviteProfile?.chatMode ?: "SANDBOX"
                sessionSummary = inviteProfile?.sessionSummary ?: ""
                secretDesc = inviteProfile?.secretDescription ?: ""
                addPlayerButton.visibility = View.GONE
                startSessionBtn.visibility = View.GONE
                enteredFrom = "Invite"
                Log.d("InviteDebug", "InviteProfile.userList3 = ${inviteProfile?.userList}")
            }
            // From ChatHub (template + charIds)
            chatProfileJson != null -> {
                chatProfile = Gson().fromJson(chatProfileJson, ChatProfile::class.java)
                val charIds = chatProfile?.characterIds ?: emptyList()
                chatMode = "SANDBOX"
                relationships = chatProfile?.relationships?.toMutableList() ?: mutableListOf()
                loadedAreas = chatProfile?.areas?.toMutableList() ?: mutableListOf()
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

                // 1. Load character profiles
                loadCharactersByIds(charIds) { profiles ->
                    characterProfiles = profiles

                    // 2. Build slot roster with valid area/location for each
                    val defaultArea = loadedAreas.firstOrNull()?.name ?: "Default"
                    val defaultLocation = loadedAreas.firstOrNull()?.locations?.firstOrNull()?.name ?: "Entrance"
                    val slots = profiles.map { profile ->
                        profile.toSlotProfile(
                            relationships = relationships.filter { it.fromId == profile.id },
                            slotId = UUID.randomUUID().toString()
                        ).copy(
                            lastActiveArea = defaultArea,
                            lastActiveLocation = defaultLocation
                        )
                    }

                    // 3. Auto-assign player to first character (for solo), or use persona selection UI
                    val playerSlot = slots.firstOrNull()  // solo: just use the first

                    // 4. Build userMap
                    val userMap = mapOf(
                        userId to SessionUser(
                            userId = userId,
                            username = "Player",
                            personaIds = emptyList(),
                            activeSlotId = null,
                            bubbleColor = playerSlot?.bubbleColor ?: "#CCCCCC",
                            textColor = playerSlot?.textColor ?: "#000000"
                        )
                    )

                    // 5. Create and save SessionProfile immediately!
                    val sessionId = FirebaseFirestore.getInstance().collection("sessions").document().id
                    sessionProfile = SessionProfile(
                        sessionId = sessionId,
                        chatId = chatProfile?.id ?: "",
                        title = chatProfile?.title ?: "Session",
                        sessionDescription = chatProfile?.description ?: "",
                        secretDescription = chatProfile?.secretDescription ?: "",
                        chatMode = chatMode,
                        startedAt = com.google.firebase.Timestamp.now(),
                        sfwOnly = chatProfile?.sfwOnly ?: true,
                        sessionSummary = chatProfile?.description ?: "",
                        userMap = userMap,
                        userList = listOf(userId),
                        userAssignments = emptyMap(),
                        slotRoster = slots,
                        areas = loadedAreas,
                        history = emptyList(),
                        currentAreaId = defaultArea,
                        relationships = relationships,
                        multiplayer = false,
                        modeSettings = chatProfile?.modeSettings?.toMutableMap() ?: mutableMapOf()
                    )

                    saveSessionProfile(sessionProfile!!, sessionId)
                    // Now update UI and any further logic with this sessionProfile.
                    displaySession(sessionProfile!!)
                    lobbySessionId = sessionId
                }
                enteredFrom = "Chathub"
            }
            // From CharacterHub (list of characters)
            characterProfilesJson != null -> {
                    characterProfiles = try {
                        Gson().fromJson(characterProfilesJson, Array<CharacterProfile>::class.java).toList()
                    } catch (e: Exception) {
                        listOf(Gson().fromJson(characterProfilesJson, CharacterProfile::class.java))
                    }
                    loadedAreas = characterProfiles
                        .flatMap { it.areas ?: emptyList() }
                        .distinctBy { it.id }
                        .toMutableList()
                    chatMode = "ONEONONE"
                    relationships = characterProfiles.flatMap { it.relationships }.toMutableList()

                    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

                    // 1. Choose defaults for area and location (for solo, pick first)
                    val defaultArea = loadedAreas.firstOrNull()?.name ?: "Default"
                    val defaultLocation = loadedAreas.firstOrNull()?.locations?.firstOrNull()?.name ?: "Entrance"

                    // 2. Build slot roster, ensuring all slots have a valid starting location
                    val slots = characterProfiles.map { profile ->
                        profile.toSlotProfile(
                            relationships = relationships.filter { it.fromId == profile.id },
                            slotId = UUID.randomUUID().toString()
                        ).copy(
                            lastActiveArea = defaultArea,
                            lastActiveLocation = defaultLocation
                        )
                    }

                    // 3. Auto-assign user to the first slot (for solo)
                    val playerSlot = slots.firstOrNull()

                    // 4. Build userMap
                val userMap = mapOf(
                    userId to SessionUser(
                        userId = userId,
                        username = "Player",
                        personaIds = emptyList(),
                        activeSlotId = null,
                        bubbleColor = playerSlot?.bubbleColor ?: "#CCCCCC",
                        textColor = playerSlot?.textColor ?: "#000000"
                    )
                )
                // 5. Create and save SessionProfile!
                    val sessionId = FirebaseFirestore.getInstance().collection("sessions").document().id
                    sessionProfile = SessionProfile(
                        sessionId = sessionId,
                        chatId = "", // No chatProfile, so probably blank
                        title = characterProfiles.firstOrNull()?.name ?: "Session",
                        sessionDescription = characterProfiles.firstOrNull()?.backstory ?: "",
                        secretDescription = characterProfiles
                            .mapNotNull { it.privateDescription }
                            .filter { it.isNotBlank() }
                            .joinToString(separator = "\n---\n"),
                        chatMode = chatMode,
                        startedAt = com.google.firebase.Timestamp.now(),
                        sfwOnly = characterProfiles.firstOrNull()?.sfwOnly ?: true,
                        sessionSummary = characterProfiles
                            .mapNotNull { it.summary?.takeIf { it.isNotBlank() } }
                            .joinToString("\n"),
                        userMap = userMap,
                        userList = listOf(userId),
                        userAssignments = emptyMap(),
                        slotRoster = slots,
                        areas = loadedAreas,
                        history = emptyList(),
                        currentAreaId = defaultArea,
                        relationships = relationships,
                        multiplayer = false
                    )
                saveSessionProfile(sessionProfile!!, sessionId)
                // Update UI/lobby
                val playerIds = sessionProfile?.userMap?.keys?.toList() ?: emptyList()
                playerRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
                playerRecycler.adapter = PlayerSlotAdapter(playerIds, onUserClick = { userId ->
                    Log.d("PlayerClick", "User clicked: $userId")
                })
                displaySession(sessionProfile!!)
                lobbySessionId = sessionId
                enteredFrom = "Characterhub"
                }

                else -> {
                Toast.makeText(this, "Error: No session/chat/character data found.", Toast.LENGTH_LONG).show()
                finish()


                return
            }
        }

        fun isHost(): Boolean {
            val myId = FirebaseAuth.getInstance().currentUser?.uid
            return inviteProfile?.userList?.getOrNull(0) == myId
        }

        if (!lobbySessionId.isNullOrBlank() && enteredFrom == "Invite"){
            val sessionId = lobbySessionId!!
            FirebaseFirestore.getInstance().collection("sessions")
                .document(sessionId)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null && snapshot.exists()) {
                        val isBuilding = snapshot.getBoolean("isBuilding") ?: false
                        val sessionStarted = snapshot.getBoolean("started") ?: false

                        // How many slots/characters need prepping?
                        val assignedPersonaIds = userAssignments.values.filterNotNull().toSet()
                        val totalSlots = sessionProfile?.slotRoster?.size ?: 0
                        showCharacterProgressDialog(totalSlots)
                        Log.d("DEBUG", "characterProfiles: ${characterProfiles.map { it.name to it.id }}")
                        Log.d("DEBUG", "personaProfiles: ${assignedPersonaIds}}")

                        // You could update progress here, e.g.:
                        val readyCount = (snapshot.get("readyCount") as? Number)?.toInt()
                        if (isBuilding && readyCount != null && buildDialogShown) {
                            updateCharacterProgress(readyCount, totalSlots)
                        }

                        // Dismiss dialog when session starts
                        if (sessionStarted) {
                            FirebaseFirestore.getInstance()
                                .collection("sessions")
                                .document(sessionId)
                                .get()
                                .addOnSuccessListener { doc ->
                                    val sessionProfile = doc.toObject(SessionProfile::class.java)
                                    if (sessionProfile != null) {
                                        dismissCharacterProgressDialog()
                                        val intent = Intent(this@SessionLandingActivity, MainActivity::class.java)
                                        intent.putExtra("SESSION_PROFILE_JSON", Gson().toJson(sessionProfile))
                                        intent.putExtra("SESSION_ID", sessionProfile.sessionId)
                                        intent.putExtra("ENTRY_MODE", "GUEST")
                                        Log.d("loading_in_sessionlanding", "checking what guest is sending. ${sessionProfile.multiplayer}")
                                        startActivity(intent)
                                        finish()
                                    } else {
                                        // handle error
                                    }
                                }
                        }
                    }
                }
        }

        addcharacter.setOnClickListener {
            if (sessionProfile?.slotRoster!!.size >= 20) {
                Toast.makeText(this, "You can only have 20 characters/personas per session.", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(this, CharacterAdditionActivity::class.java)
                startActivityForResult(intent, 101)
            }
        }

        // --- Relationship button ---
        relationshipBtn.setOnClickListener {
            val sessionId = lobbySessionId
        if (!sessionId.isNullOrBlank()) {
            // Load from Firestore for active session
            FirebaseFirestore.getInstance().collection("sessions")
                .document(sessionId)
                .get()
                .addOnSuccessListener { doc ->
                    val sessionProfile = doc.toObject(SessionProfile::class.java)
                    if (sessionProfile != null) {
                        // 1. Build the list of participant IDs (character/persona IDs for this lobby)
                        //   -- Don't use slotRoster, use whatever your local assignment is:
                        val allIds = characterProfiles.map { it.id }.toMutableList()
                        val playerPersonaId = userAssignments[HOST_SLOT]
                        if (!playerPersonaId.isNullOrBlank()) allIds.add(playerPersonaId)

                        // 2. Filter the relationships to only include those where fromId is in allIds
                        val filteredRelationships = sessionProfile.relationships.filter { rel ->
                            allIds.contains(rel.fromId)
                        }

                        Log.d("InviteDebug", "Using PARTICIPANT_IDS (local): $allIds")
                        Log.d(
                            "InviteDebug",
                            "Filtered relationships: ${filteredRelationships.size}"
                        )

                        val assignments = sessionProfile.slotRoster.mapIndexed { idx, slot ->
                            "character${idx + 1}" to slot.name
                        }.toMap()

                        val cleanedRelationships = replaceRelationshipPlaceholders(filteredRelationships, assignments)

                        val intent = Intent(this, SessionRelationshipActivity::class.java)
                        intent.putStringArrayListExtra("PARTICIPANT_IDS", ArrayList(allIds))
                        intent.putExtra("RELATIONSHIPS_JSON", Gson().toJson(cleanedRelationships))
                        startActivityForResult(intent, 102)
                    } else {
                        Toast.makeText(this, "Session data not found.", Toast.LENGTH_SHORT).show()
                    }
                }
        }else {
                // Local fallback
                val allIds = characterProfiles.map { it.id }.toMutableList()
                val playerPersonaId = userAssignments[HOST_SLOT]
                if (!playerPersonaId.isNullOrBlank()) allIds.add(playerPersonaId)
                Log.d("InviteDebug", "Using PARTICIPANT_IDS local: $allIds")
                val intent = Intent(this, SessionRelationshipActivity::class.java)
                intent.putStringArrayListExtra("PARTICIPANT_IDS", ArrayList(allIds))
                intent.putExtra("RELATIONSHIPS_JSON", Gson().toJson(relationships))
                startActivityForResult(intent, 102)
            }
        }

        // --- Start Session ---
        startSessionBtn.setOnClickListener {
            if (enteredFrom == "Sessionhub" && sessionProfile != null) {
                // Just launch MainActivity immediately!
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("SESSION_PROFILE_JSON", Gson().toJson(sessionProfile))
                intent.putExtra("SESSION_ID", sessionProfile?.sessionId)
                intent.putExtra("ENTRY_MODE", "LOAD")
                startActivity(intent)
                finish()
                return@setOnClickListener
            }
            showCharacterProgressDialog(sessionProfile?.slotRoster!!.size)
            buildDialogShown = true
            lifecycleScope.launch {
                try {
                    Log.d("saving", "starting session as Create")

                    val userId = FirebaseAuth.getInstance().currentUser!!.uid
                    val sessionId = lobbySessionId ?: error("Session ID must be set before starting session!")
                    lobbySessionId = sessionId

                    // Build persona profile lookup
                    val personaProfilesMap = personaProfiles.associateBy { it.id }
                    // Prepare relationships with placeholder substitution

                    // Set Firestore "isBuilding" to true (show build dialog to everyone)
                    val db = FirebaseFirestore.getInstance()
                    db.collection("sessions")
                        .document(sessionId)
                        .update("isBuilding", true)

                    // ==== Harmonize all slots ====
                    val slots = sessionProfile?.slotRoster?.toMutableList() ?: mutableListOf()

                    // 1. Add slots for all character profiles
                    val harmonizedSlots = mutableListOf<SlotProfile>()
                    for ((index, slot) in slots.withIndex()) {
                        val harmonized = harmonizeAndCondenseSlot(slot.toCharacterProfile(), relationships)
                        harmonizedSlots.add(harmonized)
                        updateCharacterProgress(index + 1, slots.size)

                        // --- Write progress to Firestore so guests see it live! ---
                        db.collection("sessions").document(sessionId)
                            .update("readyCount", index + 1)
                    }


                    // Build current user and player lists
                    val userlist = lastUserList ?: sessionProfile?.userList ?: listOf(userId)
                    val usernames = fetchUsernamesForIds(userlist)

                    // === Build userMap ===
                    val userMap = userlist.associateWith { uid ->
                        // Find slotId for this user
                        val slotIdx = userlist.indexOf(uid)
                        val mySlotId = "player${slotIdx + 1}"
                        val personaId = userAssignments[mySlotId]
                        val personaProfile = personaProfilesMap[personaId]
                        val personaSlot = slots.find { it.baseCharacterId == personaId && it.profileType == "player" && it.slotId == mySlotId }
                        val resolvedActiveSlotId = personaSlot?.slotId

                        SessionUser(
                            userId = uid,
                            username = usernames[uid] ?: "Player",
                            personaIds = listOfNotNull(personaId),
                            activeSlotId = resolvedActiveSlotId,
                            bubbleColor = personaProfile?.bubbleColor ?: "#CCCCCC",
                            textColor = personaProfile?.textColor ?: "#000000"
                        )
                    }

                    // Build the session profile
                    val startedAt = sessionProfile?.startedAt ?: com.google.firebase.Timestamp.now()
                    val currentIsMultiplayer = sessionProfile?.multiplayer ?: false
                    val rpgSettingsJson = chatProfile?.modeSettings?.get("rpg")
                    val rpgSettings = if (!rpgSettingsJson.isNullOrBlank() && rpgSettingsJson.trim().startsWith("{")) {
                        Gson().fromJson(rpgSettingsJson, RPGSettings::class.java)
                    } else null
                    val parsedActs = rpgSettings?.acts?.mapIndexed { index, act ->
                        RPGAct(
                            actNumber = index,
                            summary = act.summary,
                            goal = act.goal,
                            areaId = act.areaId
                        )
                    } ?: emptyList()
                    val cleanedAssignments = sessionProfile?.slotRoster!!.mapIndexed { idx, slot ->
                        "character${idx + 1}" to slot.name // or whatever placeholder syntax you use
                    }.toMap()

                    val cleaned = cleanSessionDescriptionsAndRelationships(sessionProfile!!, cleanedAssignments)
                    val currentAct = rpgSettings?.currentAct ?: 0
                    val fixedProfile = SessionProfile(
                        sessionId = sessionId,
                        chatId = chatProfile?.id ?: "",
                        title = titleView.text.toString(),
                        sessionDescription = cleaned.cleanedDescription,
                        secretDescription = cleaned.cleanedSecretDescription,
                        chatMode = chatMode,
                        startedAt = startedAt,
                        sfwOnly = sfwToggle.isChecked,
                        sessionSummary = sessionSummary,
                        userMap = userMap,
                        userList = userlist,
                        userAssignments = userAssignments,
                        slotRoster = harmonizedSlots,
                        areas = loadedAreas,
                        history = emptyList(),
                        currentAreaId = null,
                        relationships = cleanedRelationships,
                        multiplayer = currentIsMultiplayer,
                        acts = parsedActs,
                        currentAct = currentAct,
                        modeSettings = chatProfile?.modeSettings?.toMutableMap() ?: mutableMapOf()

                    )

                    // Save session and update Firestore
                    saveSessionProfile(fixedProfile, sessionId)
                    db.collection("sessions").document(sessionId)
                        .update(mapOf("isBuilding" to FieldValue.delete(), "started" to true, "readyCount" to FieldValue.delete()))

                    // Clean up dialog and launch MainActivity
                    dismissCharacterProgressDialog()
                    buildDialogShown = false

                    val intent = Intent(this@SessionLandingActivity, MainActivity::class.java)
                    intent.putExtra("SESSION_PROFILE_JSON", Gson().toJson(fixedProfile))
                    intent.putExtra("SESSION_ID", sessionId)
                    intent.putExtra("ENTRY_MODE", "CREATE")

                    val localChatProfile = chatProfile
                    val greeting = if (localChatProfile != null) {
                        localChatProfile.firstmessage ?: ""
                    } else {
                        fixedProfile.slotRoster.firstOrNull()?.greeting ?: ""
                    }
                    val cleanedGreeting = substitutePlaceholders(greeting, cleanedAssignments)
                    intent.putExtra("GREETING", cleanedGreeting)

                    startActivity(intent)
                    finish()
                } catch (e: Exception) {
                    Log.e("SessionLanding", "Failed to create session", e)
                    dismissCharacterProgressDialog()
                    buildDialogShown = false
                    Toast.makeText(
                        this@SessionLandingActivity,
                        "Error: Unable to create session.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private suspend fun harmonizeAndCondenseSlot(
        profile: CharacterProfile,
        relationships: List<Relationship>
    ): SlotProfile = withContext(Dispatchers.IO) {
        val sessionRelationships = relationships.filter { it.fromId == profile.id }
        val aiPrompt = """
        You are an expert RPG character profile editor and summarizer.
        Given the following:
        - The character's full profile (name, summary, personality, private description, physical description, abilities, backstory, etc.).
        - The full, correct list of this character's session relationships (by toName, type, etc.).
        Your job:
        1. Rewrite the character's backstory as a series of short memories, each with 2-4 descriptive tags (characters, themes, events, etc).
        2. Write a condensed summary (1–2 vivid sentences, <100 tokens) combining their summary, personality, and privateDescription.
        Return only a single JSON object with these fields:
        {
          "memories": [
            { "tags": ["sasuke", "childhood"], "text": "Naruto met Sasuke in the academy and they became rivals." },
            { "tags": ["teamwork", "sakura", "sasuke"], "text": "The three learned to work together on their first mission." }
          ],
          "condensed_summary": "<short vivid summary>"
        }
        CHARACTER PROFILE:
        Name: ${profile.name}
        Summary: ${profile.summary}
        Personality: ${profile.personality}
        Private Description: ${profile.privateDescription}
        Physical Description: ${profile.physicalDescription}
        Abilities: ${profile.abilities}
        Backstory: ${profile.backstory}
        RELATIONSHIPS (current session only):
        ${sessionRelationships.joinToString(separator = "\n") { "- ${it.type} to ${it.toName}" }}
    """.trimIndent()

        val aiResponse = Facilitator.callOpenAiApi(
            prompt = aiPrompt,
            key = BuildConfig.OPENAI_API_KEY
        )

        data class HCResult(
            val memories: List<TaggedMemory>,
            val condensed_summary: String
        )

        val jsonStart = aiResponse.indexOf('{')
        val jsonEnd = aiResponse.lastIndexOf('}')
        val cleanJson =
            if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
                aiResponse.substring(jsonStart, jsonEnd + 1)
            } else aiResponse

        val result = Gson().fromJson(cleanJson, HCResult::class.java)
        val backgroundMemories = result.memories.map {
            it.copy(
                id = UUID.randomUUID().toString(),
                messageIds = listOf("background")
            )
        }
        val outfits = profile.outfits ?: emptyList()
        val defaultOutfit = outfits.firstOrNull()?.name ?: ""
        val actualCurrentOutfit = if (profile.currentOutfit.isNullOrBlank()) defaultOutfit else profile.currentOutfit
        val startArea = null // You can set this if you want, or pull from chatProfile/area logic
        val slotId = UUID.randomUUID().toString()
        val rpgSettingsJson = sessionProfile?.modeSettings["rpg"]
        val rpgSettings = if (!rpgSettingsJson.isNullOrBlank() && rpgSettingsJson.trim().startsWith("{"))
            Gson().fromJson(rpgSettingsJson, ModeSettings.RPGSettings::class.java)
        else null
        val matchingRpgCharacter = rpgSettings?.characters?.find { it.characterId == profile.id }

        fun calcHp(stats: Map<String, Int>, rpgClass: String): Int {
            // Simple example, tweak as you like
            val base = 10
            val bonus = (stats["resolve"] ?: 0) + (stats["strength"] ?: 0)
            return base + bonus
        }
        fun calcMaxHp(stats: Map<String, Int>, rpgClass: String): Int = calcHp(stats, rpgClass)

        fun calcDefense(stats: Map<String, Int>, rpgClass: String): Int {
            // Example: defense = strength/2 + resolve/2
            val str = (stats["strength"] ?: 0)
            val res = (stats["resolve"] ?: 0)
            return (str / 2) + (res / 2)
        }
        val rpgStats = matchingRpgCharacter?.stats?.let {
            mapOf(
                "strength" to it.strength,
                "agility" to it.agility,
                "intelligence" to it.intelligence,
                "charisma" to it.charisma,
                "resolve" to it.resolve
            )
        } ?: emptyMap()

        // Calculate hp/defense if not set in RPGCharacter
        val hp = matchingRpgCharacter?.hp
            ?: calcHp(rpgStats, matchingRpgCharacter?.characterClass?.name ?: "")
        val maxHp = matchingRpgCharacter?.maxHp
            ?: calcMaxHp(rpgStats, matchingRpgCharacter?.characterClass?.name ?: "")
        val defense = matchingRpgCharacter?.defense
            ?: calcDefense(rpgStats, matchingRpgCharacter?.characterClass?.name ?: "")
        val links: List<CharacterLink> = rpgSettings?.linkedToMap?.get(profile.id) ?: emptyList()
        // Return the full SlotProfile
        SlotProfile(
            slotId = slotId,
            baseCharacterId = profile.id,
            name = profile.name,
            summary = result.condensed_summary,
            personality = profile.personality,
            privateDescription = profile.privateDescription,
            abilities = profile.abilities,
            greeting = profile.greeting,
            age = profile.age,
            height = profile.height,
            weight = profile.weight,
            gender = profile.gender,
            physicalDescription = profile.physicalDescription,
            eyeColor = profile.eyeColor,
            hairColor = profile.hairColor,
            bubbleColor = profile.bubbleColor,
            textColor = profile.textColor,
            avatarUri = profile.avatarUri,
            outfits = outfits,
            currentOutfit = actualCurrentOutfit,
            sfwOnly = profile.sfwOnly,
            relationships = sessionRelationships,
            lastActiveArea = startArea,
            lastActiveLocation = null,
            profileType = profile.profileType,
            typing = false,
            memories = backgroundMemories,
            rpgClass = matchingRpgCharacter?.characterClass?.name ?: "",
            stats = rpgStats,
            equipment = matchingRpgCharacter?.equipment ?: emptyList(),
            hp = hp,
            maxHp = maxHp,
            defense = defense,
            hiddenRoles = matchingRpgCharacter?.role?.name ?: "",
            linkedTo = links
        )
    }

    private fun showCharacterProgressDialog(total: Int) {
        val view = layoutInflater.inflate(R.layout.dialog_progress_character, null)
        progressTextView = view.findViewById(R.id.progressText)
        progressBar = view.findViewById(R.id.progressBar)
        progressBar?.max = total
        progressBar?.progress = 0
        progressTextView?.text = "Processing character 0/$total"
        progressDialog = AlertDialog.Builder(this)
            .setTitle("Preparing Session")
            .setView(view)
            .setCancelable(false)
            .show()
    }

    private fun updateCharacterProgress(current: Int, total: Int) {
        progressBar?.progress = current
        progressTextView?.text = "Updating characters for the session: $current/$total done.\nPlease don’t close the app."
    }

    private fun dismissCharacterProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
        progressBar = null
        progressTextView = null
    }

    // -- Helper: display a loaded session
    private fun displaySession(session: SessionProfile) {
        charRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        charRecycler.adapter = CharacterRowAdapter(
            session.slotRoster.map { it.toCharacterProfile() },
            onClick = { character ->
                val context = this
                AlertDialog.Builder(context)
                    .setTitle(character.name)
                    .setItems(arrayOf("Profile", "Replace", "Remove", "Save")) { _, which ->
                        when (which) {
                            0 -> { // Profile
                                // If it's private, you might want to block here
                                if (character.private == true) {
                                    Toast.makeText(context, "This character is private.", Toast.LENGTH_SHORT).show()
                                } else {
                                    context.startActivity(
                                        Intent(context, CharacterProfileActivity::class.java)
                                            .putExtra("characterId", character.id)
                                    )
                                }
                            }
                            1 -> { // Replace
                                // Launch CharacterAdditionActivity for replacement
                                context.startActivityForResult(
                                    Intent(context, CharacterAdditionActivity::class.java)
                                        .putExtra("replaceCharacterId", character.id)
                                        .putExtra("entry", "Replace"),
                                    103 // or any unique requestCode
                                )
                            }
                            2 -> { // Remove
                                // Remove the character from the session’s slotRoster
                                removeCharacterFromSlotRoster(character.id)
                            }
                            3 -> { // Save
                                // Copy character, set current user as creator/author, and save as new character
                                if (character.private == true) {
                                    Toast.makeText(context, "This character is private.", Toast.LENGTH_SHORT).show()
                                } else {
                                    saveCharacterAsUser(character)
                                }
                            }
                        }
                    }
                    .show()
            }
        )

        titleView.text = session.title
        descriptionView.text = session.sessionDescription
    }

    private fun saveCharacterAsUser(character: CharacterProfile) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val newChar = character.copy(
            id = UUID.randomUUID().toString(),
            author = userId
        )
        db.collection("characters").document(newChar.id)
            .set(newChar)
            .addOnSuccessListener {
                Toast.makeText(this, "Character saved to your collection!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save character.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun removeCharacterFromSlotRoster(characterId: String) {
        val sessionId = lobbySessionId ?: return
        val db = FirebaseFirestore.getInstance()
        val newSlotRoster = sessionProfile?.slotRoster?.filter { it.baseCharacterId != characterId }
        db.collection("sessions").document(sessionId)
            .update("slotRoster", newSlotRoster)
            .addOnSuccessListener {
                sessionProfile?.slotRoster = newSlotRoster ?: emptyList()
                displaySession(sessionProfile!!) // Refreshes the recycler
                Toast.makeText(this, "Character removed.", Toast.LENGTH_SHORT).show()
            }
    }

    fun cleanSessionDescriptionsAndRelationships(
        sessionProfile: SessionProfile,
        slotAssignments: Map<String, String>
    ): CleanedData {
        val cleanedDescription = substitutePlaceholders(sessionProfile.sessionDescription, slotAssignments)
        val cleanedSecretDescription = substitutePlaceholders(sessionProfile.secretDescription!!, slotAssignments)
        val cleanedRelationships = replaceRelationshipPlaceholders(sessionProfile.relationships, slotAssignments)
        return CleanedData(cleanedDescription, cleanedSecretDescription, cleanedRelationships)
    }


    // --- Firestore: load personas
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

    // --- Firestore: load characters by id
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

    // --- ActivityResult: personas/relationships
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        when (requestCode) {
            101 -> { // Persona selection result
                val type = data.getStringExtra("SELECTED_TYPE") // "character" or "persona"
                val id = data.getStringExtra("SELECTED_ID") ?: return

                if (sessionProfile?.slotRoster?.size ?: 0 < 20) {
                    if (sessionProfile?.slotRoster?.any { it.baseCharacterId == id } == true) {
                        Toast.makeText(this, "That character/persona is already in the session.", Toast.LENGTH_SHORT).show()
                        return
                    }

                    val collection = if (type == "character") "characters" else "personas"
                    FirebaseFirestore.getInstance().collection(collection).document(id).get()
                        .addOnSuccessListener { doc ->
                            val profileRelationships = when (type) {
                                "character" -> doc.toObject(CharacterProfile::class.java)?.relationships ?: emptyList()
                                "persona" -> doc.toObject(PersonaProfile::class.java)?.relationships ?: emptyList()
                                else -> emptyList()
                            }

                            // Merge relationships (avoid duplicates)
                            val updatedRelationships = (sessionProfile?.relationships ?: emptyList()).toMutableList()
                            profileRelationships.forEach { newRel ->
                                if (updatedRelationships.none { it.fromId == newRel.fromId && it.toName == newRel.toName && it.type == newRel.type }) {
                                    updatedRelationships.add(newRel)
                                }
                            }

                            // Update relationships in Firestore
                            val sessionId = lobbySessionId ?: return@addOnSuccessListener
                            FirebaseFirestore.getInstance().collection("sessions")
                                .document(sessionId)
                                .update("relationships", updatedRelationships)
                                .addOnSuccessListener {
                                    sessionProfile?.relationships = updatedRelationships
                                    // Now add the profile to the slot roster
                                    addProfileToSlotRoster(type ?: "character", id)
                                }
                        }
                } else {
                    Toast.makeText(this, "Character/Persona slot limit reached.", Toast.LENGTH_SHORT).show()
                }
            }
            102 -> {
                val relationshipsJson = data.getStringExtra("RELATIONSHIPS_JSON") ?: "[]"
                relationships = Gson().fromJson(relationshipsJson, Array<Relationship>::class.java).toMutableList()
                val sessionId = lobbySessionId
                if (sessionId != null) {
                    FirebaseFirestore.getInstance().collection("sessions")
                        .document(sessionId)
                        .update("relationships", relationships)
                }
            }
            103 -> { // Replacement logic
                val type = data.getStringExtra("SELECTED_TYPE") // "character" or "persona"
                val id = data.getStringExtra("SELECTED_ID") ?: return
                val replaceCharacterId = data.getStringExtra("replaceCharacterId") ?: return

                val sessionId = lobbySessionId ?: return

                val collection = if (type == "character") "characters" else "personas"
                FirebaseFirestore.getInstance().collection(collection).document(id).get()
                    .addOnSuccessListener { doc ->
                        val (newProfile, newRelationships) = when (type) {
                            "character" -> {
                                val profile = doc.toObject(CharacterProfile::class.java) ?: return@addOnSuccessListener
                                profile to profile.relationships
                            }
                            "persona" -> {
                                val profile = doc.toObject(PersonaProfile::class.java) ?: return@addOnSuccessListener
                                profile.toCharacterProfile() to profile.relationships
                            }
                            else -> return@addOnSuccessListener
                        }
                        val newSlot = newProfile.toSlotProfile(
                            relationships = emptyList(), // you will assign actual relationships via sessionProfile after updating
                            slotId = UUID.randomUUID().toString()
                        )

                        // --- Relationships update ---
                        val oldRelationships = sessionProfile?.relationships?.toMutableList() ?: mutableListOf()
                        // Remove all relationships with fromId == replaceCharacterId
                        oldRelationships.removeAll { it.fromId == replaceCharacterId }
                        // Add new character's relationships (update their fromId if needed)
                        val relsToAdd = newRelationships.map { it.copy(fromId = newProfile.id) }
                        oldRelationships.addAll(relsToAdd)

                        // --- Replace the slot in the roster ---
                        val oldRoster = sessionProfile?.slotRoster ?: return@addOnSuccessListener
                        val newRoster = oldRoster.map {
                            if (it.baseCharacterId == replaceCharacterId) newSlot else it
                        }

                        // --- Update Firestore ---
                        FirebaseFirestore.getInstance().collection("sessions")
                            .document(sessionId)
                            .update(
                                mapOf(
                                    "slotRoster" to newRoster,
                                    "relationships" to oldRelationships
                                )
                            )
                            .addOnSuccessListener {
                                sessionProfile?.slotRoster = newRoster
                                sessionProfile?.relationships = oldRelationships
                                displaySession(sessionProfile!!)
                                Toast.makeText(this, "Character replaced!", Toast.LENGTH_SHORT).show()
                            }
                    }
            }
        }
    }

    // --- Firestore: save session profile
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

    // Name replacements
    fun replaceRelationshipPlaceholders(
        relationships: List<Relationship>,
        assignments: Map<String, String>
    ): List<Relationship> {
        return relationships.map { rel ->
            var newToName = rel.toName
            assignments.forEach { (placeholder, actualName) ->
                if (actualName.isNotBlank()) {
                    // Replace {character4} if present
                    newToName = newToName.replace("{$placeholder}", actualName, ignoreCase = true)
                    // Replace character4 as well
                    newToName = newToName.replace(placeholder, actualName, ignoreCase = true)
                }
            }
            rel.copy(toName = newToName)
        }
    }


    fun substitutePlaceholders(text: String, assignments: Map<String, String>): String {
        var result = text
        assignments.forEach { (slot, name) ->
            if (name.isBlank()) return@forEach
            result = result.replace(slot, name, ignoreCase = true)
        }
        return result
    }

    // -- Utility: SlotProfile to CharacterProfile for preview
    fun SlotProfile.toCharacterProfile(): CharacterProfile {
        return CharacterProfile(
            id = this.baseCharacterId,
            abilities = this.abilities,
            name = this.name,
            summary = this.summary,
            personality = this.personality,
            privateDescription = this.privateDescription,
            backstory = "", // SlotProfile doesn't have backstory directly
            greeting = this.greeting,
            author = "",
            tags = emptyList(),
            avatarUri = this.avatarUri,
            areas = emptyList(),
            outfits = this.outfits,
            currentOutfit = this.currentOutfit,
            createdAt = null,
            height = this.height,
            weight = this.weight,
            age = this.age,
            eyeColor = this.eyeColor,
            hairColor = this.hairColor,
            physicalDescription = this.physicalDescription,
            gender = this.gender,
            relationships = this.relationships,
            bubbleColor = this.bubbleColor,
            textColor = this.textColor,
            sfwOnly = this.sfwOnly,
            profileType = this.profileType
        )
    }

    fun PersonaProfile.toCharacterProfile(): CharacterProfile {
        return CharacterProfile(
            id = this.id,
            name = this.name,
            summary = "",
            personality = "",
            privateDescription = "",
            backstory = "",
            greeting = "",
            age = this.age,
            height = this.height,
            weight = this.weight,
            gender = this.gender,
            physicalDescription = this.physicaldescription,
            abilities = "",
            eyeColor = this.eyes,
            hairColor = this.hair,
            bubbleColor = this.bubbleColor,
            textColor = this.textColor,
            avatarUri = this.avatarUri,
            outfits = this.outfits,
            currentOutfit = this.currentOutfit,
            sfwOnly = true,
            relationships = this.relationships,
            profileType = "player"
        )
    }

    private fun CharacterProfile.toSlotProfile(
        relationships: List<Relationship> = emptyList(),
        slotId: String = UUID.randomUUID().toString()
    ): SlotProfile {
        return SlotProfile(
            slotId = slotId,
            baseCharacterId = this.id,
            name = this.name,
            summary = this.summary ?: "",
            personality = this.personality ?: "",
            privateDescription = this.privateDescription ?: "",
            greeting = this.greeting ?: "",
            age = this.age,
            height = this.height ?: "",
            weight = this.weight ?: "",
            gender = this.gender ?: "",
            physicalDescription = this.physicalDescription ?: "",
            abilities = this.abilities ?: "",
            eyeColor = this.eyeColor ?: "",
            hairColor = this.hairColor ?: "",
            bubbleColor = this.bubbleColor ?: "#CCCCCC",
            textColor = this.textColor ?: "#000000",
            avatarUri = this.avatarUri ?: "",
            outfits = this.outfits ?: emptyList(),
            currentOutfit = this.currentOutfit ?: "",
            sfwOnly = this.sfwOnly,
            relationships = relationships,
            profileType = this.profileType ?: "bot"
        )
    }

    private fun PersonaProfile.toSlotProfile(
        relationships: List<Relationship> = emptyList(),
        slotId: String = UUID.randomUUID().toString()
    ): SlotProfile {
        return SlotProfile(
            slotId = slotId,
            baseCharacterId = this.id,
            name = this.name,
            summary = "",
            personality = "",
            privateDescription = "",
            greeting = "",
            age = this.age ?: 0,
            height = this.height ?: "",
            weight = this.weight ?: "",
            gender = this.gender ?: "",
            physicalDescription =  this.physicaldescription,
            abilities = "",
            eyeColor =  this.eyes,
            hairColor = this.hair,
            bubbleColor = this.bubbleColor ?: "#CCCCCC",
            textColor = this.textColor ?: "#000000",
            avatarUri = this.avatarUri,
            outfits = this.outfits ?: emptyList(),
            currentOutfit = this.currentOutfit ?: "",
            sfwOnly = true,
            relationships = relationships,
            profileType = "player"
        )
    }

    private fun sendInviteToFriend(friendId: String) {
        val db = FirebaseFirestore.getInstance()
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (lobbySessionId == null) {
            lobbySessionId = db.collection("sessions").document().id
        }
        val sessionId = lobbySessionId!!
                val inviteProfile = InviteProfile(
                    sessionId = sessionId,
                    title = titleView.text.toString(),
                    userList = listOf(currentUserId),
                    characterIds = characterProfiles.map { it.id },
                    relationships = relationships,
                    chatId = chatProfile?.id ?: "",
                    sessionSummary = sessionSummary,
                    chatMode = chatMode,
                    areas = loadedAreas,
                    sfwOnly = sfwToggle.isChecked,
                    isBuilding = true,
                    started = false,
                    sessionDescription = descriptionView.text.toString(),
                    secretDescription = secretDesc
                )
                val inviteJson = Gson().toJson(inviteProfile)

                // --- Send the invite message ---
                val messageId = db.collection("users").document(friendId)
                    .collection("messages").document().id

                val inviteMessage = mapOf(
                    "id" to messageId,
                    "from" to currentUserId,
                    "to" to friendId,
                    "text" to "${sessionProfile?.title}: You've been invited to join a session!",
                    "type" to "SESSION_INVITE",
                    "sessionId" to sessionId,
                    "inviteProfileJson" to inviteJson,
                    "timestamp" to Timestamp.now(),
                    "status" to MessageStatus.UNOPENED
                )

                db.collection("users").document(friendId)
                    .collection("messages")
                    .document(messageId)
                    .set(inviteMessage)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Invite sent!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
    }

    private fun showFriendInviteDialog() {
        val db = FirebaseFirestore.getInstance()
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("users").document(currentUserId).get()
            .addOnSuccessListener { doc ->
                val friends = doc.get("friends") as? List<String> ?: emptyList()
                if (friends.isEmpty()) {
                    Toast.makeText(this, "No friends to invite. :(", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                db.collection("users").whereIn(FieldPath.documentId(), friends)
                    .get()
                    .addOnSuccessListener { snap ->
                        val friendNames = snap.documents.map {
                            it.getString("name") ?: it.getString("handle") ?: it.id
                        }
                        val friendIds = snap.documents.map { it.id }

                        val dialogView = layoutInflater.inflate(R.layout.dialog_invite_friend, null)
                        val friendsSpinner = dialogView.findViewById<Spinner>(R.id.friendsSpinner)
                        val sendButton = dialogView.findViewById<Button>(R.id.sendButton)
                        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)

                        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, friendNames)
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        friendsSpinner.adapter = adapter

                        val dialog = AlertDialog.Builder(this)
                            .setTitle("Invite a Friend")
                            .setView(dialogView)
                            .setCancelable(true)
                            .create()

                        sendButton.setOnClickListener {
                            val selectedPosition = friendsSpinner.selectedItemPosition
                            if (selectedPosition == AdapterView.INVALID_POSITION) {
                                Toast.makeText(this, "Select a friend.", Toast.LENGTH_SHORT).show()
                                return@setOnClickListener
                            }
                            val toId = friendIds[selectedPosition]
                            sendInviteToFriend(toId)
                            dialog.dismiss()
                        }

                        cancelButton.setOnClickListener { dialog.dismiss() }
                        dialog.show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to load friends.", Toast.LENGTH_SHORT).show()
                    }
            }
    }

    private fun isHost(): Boolean {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val hostId = inviteProfile?.userList?.firstOrNull() ?: sessionProfile?.userList?.firstOrNull()
        return currentUserId != null && currentUserId == hostId
    }


    override fun onStart() {
        super.onStart()

        val sessionId = lobbySessionId
        if (!sessionId.isNullOrBlank()) {
            sessionListener = FirebaseFirestore.getInstance()
                .collection("sessions")
                .document(sessionId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                    val sessionProfile = snapshot.toObject(SessionProfile::class.java) ?: return@addSnapshotListener

                    val userListChanged = lastUserList == null || sessionProfile.userList != lastUserList
                    if (userListChanged) {
                        Log.d("SessionListener", "User list changed: ${sessionProfile.userList}")
                        (playerRecycler.adapter as? PlayerSlotAdapter)?.setUserIds(sessionProfile.userList)
                        lastUserList = sessionProfile.userList
                        lastUserAssignments = null
                    }

                    val slotRosterChanged = lastSlotRoster == null || sessionProfile.slotRoster != lastSlotRoster
                    if (slotRosterChanged) {
                        Log.d("SessionListener", "Slot roster changed: ${sessionProfile.slotRoster.map { it.name }}")
                        displaySession(sessionProfile)
                        lastSlotRoster = sessionProfile.slotRoster

                        // REBUILD assignments map from sessionProfile
                        val cleanedAssignments = sessionProfile.slotRoster.mapIndexed { idx, slot ->
                            "character${idx + 1}" to slot.name // or whatever placeholder syntax you use
                        }.toMap()
                        val cleaned = cleanSessionDescriptionsAndRelationships(sessionProfile, cleanedAssignments)
                        descriptionView.text = cleaned.cleanedDescription
                    }
                }
        }
    }


    private fun addProfileToSlotRoster(type: String, profileId: String) {
        val db = FirebaseFirestore.getInstance()
        val sessionId = lobbySessionId ?: return

        val collection = if (type == "character") "characters" else "personas"
        db.collection(collection).document(profileId).get()
            .addOnSuccessListener { doc ->
                val newSlot = when (type) {
                    "character" -> {
                        val characterProfile = doc.toObject(CharacterProfile::class.java) ?: return@addOnSuccessListener
                        characterProfile.toSlotProfile(
                            relationships = emptyList(),
                            slotId = UUID.randomUUID().toString()
                        )
                    }
                    "persona" -> {
                        val personaProfile = doc.toObject(PersonaProfile::class.java) ?: return@addOnSuccessListener
                        personaProfile.toCharacterProfile().toSlotProfile(
                            relationships = emptyList(),
                            slotId = UUID.randomUUID().toString()
                        )
                    }
                    else -> return@addOnSuccessListener
                }

                // Check for duplicates in local sessionProfile before sending update
                if (sessionProfile?.slotRoster?.any { it.baseCharacterId == profileId } == true) {
                    Toast.makeText(this, "That character/persona is already in the session.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Save just the new slot using arrayUnion (merge)
                db.collection("sessions")
                    .document(sessionId)
                    .update("slotRoster", FieldValue.arrayUnion(newSlot))
                    .addOnSuccessListener {
                        // Also update your local sessionProfile
                        sessionProfile?.slotRoster = sessionProfile?.slotRoster?.plus(newSlot) ?: listOf(newSlot)
                        // Refresh the UI if needed
                        Toast.makeText(this, "Character added!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Log.e("Firestore", "Failed to add slot: $e")
                    }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Failed to load $type profile: $e")
            }
    }


    fun writeGuestPresence(sessionId: String, userId: String) {
        val db = FirebaseFirestore.getInstance()
        val sessionRef = db.collection("sessions").document(sessionId)

        sessionRef.update("userList", com.google.firebase.firestore.FieldValue.arrayUnion(userId))
            .addOnSuccessListener {
                Log.d("Presence", "Guest $userId presence written to session $sessionId")
            }
            .addOnFailureListener { e ->
                Log.e("Presence", "Failed to write guest presence", e)
            }
    }

    suspend fun fetchUsernamesForIds(userIds: List<String>): Map<String, String> {
        val db = FirebaseFirestore.getInstance()
        val usernames = mutableMapOf<String, String>()
        if (userIds.isEmpty()) return usernames

        // Firestore max 'whereIn' is 10 IDs, so batch if needed (but you can skip batching if small)
        db.collection("users")
            .whereIn(FieldPath.documentId(), userIds)
            .get()
            .addOnSuccessListener { snap ->
                for (doc in snap.documents) {
                    val name = doc.getString("name") ?: doc.getString("handle") ?: "Player"
                    usernames[doc.id] = name
                }
            }
            .await()
        return usernames
    }

    fun buildAssignmentsMap(
        userAssignments: Map<String, String>,
        personaProfilesMap: Map<String, PersonaProfile>,
        currentUserPersonaId: String?
    ): Map<String, String> {
        val assignments = mutableMapOf<String, String>()
        userAssignments.forEach { (slotKey, personaId) ->
            val personaName = personaProfilesMap[personaId]?.name ?: "Player"
            val idx = slotKey.filter { it.isDigit() }.toIntOrNull() ?: 1

            assignments[slotKey] = personaName
            assignments["player$idx"] = personaName
            assignments["user$idx"] = personaName
        }
        // For host or current player:
        if (currentUserPersonaId != null) {
            val currentName = personaProfilesMap[currentUserPersonaId]?.name ?: "Player"
            assignments["player"] = currentName
            assignments["user"] = currentName

            assignments["you"] = currentName
        }
        return assignments
    }

    override fun onBackPressed() {
        cleanUpSessionIfNeeded()
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionListener?.remove()
        // Only run this if Activity is finishing (not just rotating screen):
        if (isFinishing) cleanUpSessionIfNeeded()
    }

    private fun cleanUpSessionIfNeeded() {
        val sessionId = intent.getStringExtra("SESSION_ID") ?: return
        // Prefer currentUserId if not provided
        val userId = intent.getStringExtra("USER_ID") ?: FirebaseAuth.getInstance().currentUser?.uid ?: return
        val profile = sessionProfile ?: return

        val user = profile.userMap[userId]
        if (user?.activeSlotId == null) {
            val newUserMap = profile.userMap.toMutableMap().apply { remove(userId) }
            val newUserList = profile.userList.toMutableList().apply { remove(userId) }
            val newUserAssignments = profile.userAssignments.toMutableMap().apply { remove(userId) }
            val sessionRef = FirebaseFirestore.getInstance().collection("sessions").document(sessionId)

            if (newUserList.isEmpty()) {
                sessionRef.delete()
            } else {
                sessionRef.update(
                    mapOf(
                        "userMap" to newUserMap,
                        "userList" to newUserList,
                        "userAssignments" to newUserAssignments
                    )
                )
            }
        }
    }
}
data class CleanedData(
    val cleanedDescription: String,
    val cleanedSecretDescription: String,
    val cleanedRelationships: List<Relationship>
)
