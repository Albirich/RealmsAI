package com.example.RealmsAI

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
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
import com.example.RealmsAI.ai.PromptBuilder
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
import com.example.RealmsAI.models.ModeSettings.VNRelationship
import com.example.RealmsAI.models.ModeSettings.VNSettings
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
    private val gson = Gson()
    private val REQ_RPG_SETTINGS = 201
    private val REQ_VN_SETTINGS  = 202
    private fun calcHp(stats: Map<String, Int>, rpgClass: String): Int {
        val base = 10
        val bonus = (stats["resolve"] ?: 0) + (stats["strength"] ?: 0)
        return base + bonus
    }
    private fun calcMaxHp(stats: Map<String, Int>, rpgClass: String) = calcHp(stats, rpgClass)
    private fun calcDefense(stats: Map<String, Int>, rpgClass: String): Int {
        val str = (stats["strength"] ?: 0)
        val res = (stats["resolve"] ?: 0)
        return (str / 2) + (res / 2) + 8
    }
    private lateinit var mysteryMoreInfoByCharId: Map<String, String>

    // stuff for updating chars
    private val pendingUpdateDeltas = mutableMapOf<String, UpdateDelta>()  // baseId -> delta
    private var needsUpdateIds: MutableSet<String> = mutableSetOf()

    data class UpdateDelta(
        val summaryChanged: Boolean,
        val privateChanged: Boolean,
        val posesChanged: Boolean
    ) {
        val hasChanges get() = summaryChanged || privateChanged || posesChanged
    }

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
                val local = Gson().fromJson(sessionProfileJson, SessionProfile::class.java)
                lobbySessionId = sessionProfile!!.sessionId
                chatProfile = null
                relationships = sessionProfile?.slotRoster?.flatMap { it.relationships }?.toMutableList() ?: mutableListOf()
                displaySession(sessionProfile!!)
                FirebaseFirestore.getInstance()
                    .collection("sessions")
                    .document(lobbySessionId!!)
                    .get()
                    .addOnSuccessListener { snap ->
                        val live = snap.toObject(SessionProfile::class.java)
                        // Prefer live values if present; fall back to local
                        val merged = (live ?: local).also { sp ->
                            // keep anything you already displayed if live is missing it
                            if (live == null) Log.w("Session", "Live session missing, using local cache")
                        }
                        sessionProfile = merged
                        bindModeJumpButtons(lobbySessionId!!, merged)
                        checkForCharacterUpdates()
                    }
                    .addOnFailureListener { e ->
                        Log.e("Session", "Failed to fetch live session; using local cache", e)
                        bindModeJumpButtons(lobbySessionId!!, local)
                    }
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
                val local = Gson().fromJson(inviteProfilesJson, SessionProfile::class.java)
                FirebaseFirestore.getInstance()
                    .collection("sessions")
                    .document(lobbySessionId!!)
                    .get()
                    .addOnSuccessListener { snap ->
                        val live = snap.toObject(SessionProfile::class.java)
                        // Prefer live values if present; fall back to local
                        val merged = (live ?: local).also { sp ->
                            // keep anything you already displayed if live is missing it
                            if (live == null) Log.w("Session", "Live session missing, using local cache")
                        }
                        sessionProfile = merged
                        bindModeJumpButtons(lobbySessionId!!, merged)
                        checkForCharacterUpdates()
                    }
                    .addOnFailureListener { e ->
                        Log.e("Session", "Failed to fetch live session; using local cache", e)
                        bindModeJumpButtons(lobbySessionId!!, local)
                    }
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

                    // 2. Build slot roster PRESERVING ORDER and inserting placeholders
                    val slots = buildSlotRosterFromIds(
                        orderedIds = charIds,
                        loadedProfiles = profiles,
                        areas = loadedAreas
                    )

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
                        currentAreaId = loadedAreas.firstOrNull()?.name ?: "Default",
                        relationships = relationships,
                        multiplayer = false,
                        enabledModes = chatProfile?.enabledModes?.toMutableList()?:mutableListOf(),
                        modeSettings = chatProfile?.modeSettings?.toMutableMap() ?: mutableMapOf()
                    )

                    saveSessionProfile(sessionProfile!!, sessionId)

                    bindModeJumpButtons(sessionId, sessionProfile)
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
                bindModeJumpButtons(sessionId, sessionProfile)
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
                        val isBuilding = snapshot.getBoolean("isBuilding") ?: true
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

        val infoButtonSessionPlayers: ImageButton = findViewById(R.id.infoButtonSessionPlayers)
        infoButtonSessionPlayers.setOnClickListener {
            AlertDialog.Builder(this@SessionLandingActivity)
                .setTitle("Players")
                .setMessage("This is just a list of players in the game, trust me you're in there even if it doesn't show you.")
                .setPositiveButton("OK", null)
                .show()
        }
        val infoButtonSessionCharacters: ImageButton = findViewById(R.id.infoButtonSessionCharacters)
        infoButtonSessionCharacters.setOnClickListener {
            AlertDialog.Builder(this@SessionLandingActivity)
                .setTitle("Characters")
                .setMessage("You can add up to 20 characters. Click and hold a character for more options.\n" +
                        "Profile takes you to the character's profile.\n" +
                        "Replace will replace the character in that position (good for switching another characters relationships with the new character).\n" +
                        "Remove removes the character from the list.\n" +
                        "Save will copy the character to your character list as if you created them.")
                .setPositiveButton("OK", null)
                .show()
        }
        addcharacter.setOnClickListener {
            if (sessionProfile?.slotRoster!!.size >= 20) {
                Toast.makeText(this, "You can only have 20 characters/personas per session.", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(this, CharacterSelectionActivity::class.java)
                intent.putExtra("TEMP_SELECTION_MODE", true)
                intent.putExtra("MAX_SELECT", 1)
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
            // --- Make sure player has a character first ---
            val rosterCount = sessionProfile?.slotRoster?.size ?: 0
            if (rosterCount < 2) {
                Toast.makeText(this, "You need at least 2 characters to start a session.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

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

                    val gson = Gson()

                    // Pull settings from the *chatProfile* (you’re creating a session from that)
                    val rpgSettingsJson = chatProfile?.modeSettings?.get("rpg") as? String
                    val murderSettingsJson = chatProfile?.modeSettings?.get("murder") as? String

                    var rpgSettings: ModeSettings.RPGSettings? = rpgSettingsJson?.let {
                        try { gson.fromJson(it, ModeSettings.RPGSettings::class.java) } catch (_: Exception) { null }
                    }
                    var murderSettings: ModeSettings.MurderSettings? = murderSettingsJson?.let {
                        try { gson.fromJson(it, ModeSettings.MurderSettings::class.java) } catch (_: Exception) { null }
                    }
                    if (murderSettings?.enabled == true) {
                        // 1) Randomize only if requested
                        if (murderSettings?.randomizeKillers == true) {
                            val (rpgUpd, murderUpd) = generateMurderSetup(
                                slots = sessionProfile?.slotRoster ?: emptyList(),
                                rpgSettingsIn = rpgSettings,
                                murderIn = murderSettings,
                                sessionProfile = sessionProfile!!
                            )
                            rpgSettings = rpgUpd
                            murderSettings = murderUpd
                            Log.d("ai_response", "seeded: $murderSettings")
                        } else {
                            Log.d("ai_response", "randomizeKillers disabled; using creator roles")
                        }

                        // 2) Build timelines (single AI call → per-character map)
                        mysteryMoreInfoByCharId = generateMysteryTimelineStrings(
                            slots = sessionProfile?.slotRoster ?: emptyList(),
                            rpgSettings = rpgSettings,
                            murderSettings = murderSettings,
                            sessionProfile = sessionProfile!!
                        )
                    } else {
                        mysteryMoreInfoByCharId = emptyMap()
                    }
                    // ==== Harmonize all slots ====
                    val slots = sessionProfile?.slotRoster?.toMutableList() ?: mutableListOf()

                    val harmonizedSlots = mutableListOf<SlotProfile>()
                    for ((i, slot) in slots.withIndex()) {
                        val slotKey = ModeSettings.SlotKeys.fromPosition(i)
                        val idKey = slot.baseCharacterId ?: slot.slotId
                        val timelineText = mysteryMoreInfoByCharId[idKey].orEmpty()
                        val areaMap = chatProfile?.characterToArea ?: emptyMap()
                        val locMap = chatProfile?.characterToLocation ?: emptyMap()
                        val h = harmonizeAndCondenseSlot(
                            slot.toCharacterProfile(),
                            relationships,
                            rpgSettings,
                            murderSettings,
                            currentSlotKey = slotKey,
                            moreInfo = timelineText,
                            allAreas = loadedAreas,
                            charToAreaMap = areaMap,
                            charToLocationMap = locMap

                        )
                        harmonizedSlots.add(h)

                        updateCharacterProgress(i + 1, slots.size)
                        db.collection("sessions").document(sessionId).update("readyCount", i + 1)
                    }
                    Log.d("ai_response", "final harmonizedSlots size=${harmonizedSlots.size}")
                    // After the harmonize loop:
                    val baseToSlot: Map<String, String> = harmonizedSlots
                        .filter { !it.baseCharacterId.isNullOrBlank() }
                        .associate { it.baseCharacterId!! to it.slotId }

                    val targetBaseId = rpgSettings?.characters
                        ?.firstOrNull { it.role == ModeSettings.CharacterRole.TARGET }
                        ?.characterId

                    val victimSlotIdResolved = targetBaseId?.let { baseToSlot[it] }

                    val killerSlotIdsSet: MutableSet<String> = rpgSettings?.characters
                        ?.filter { it.role == ModeSettings.CharacterRole.VILLAIN }
                        ?.mapNotNull { baseToSlot[it.characterId] }
                        ?.toMutableSet() ?: mutableSetOf()

                    murderSettings = murderSettings?.copy(
                        victimSlotId = victimSlotIdResolved,
                        killerSlotIds = killerSlotIdsSet
                    )

                    Log.d("mystery", "victimSlotId=$victimSlotIdResolved killers=$killerSlotIdsSet")


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



                    // Now build fixedProfile with updated modeSettings
                    val modeSettingsMap: MutableMap<String, Any> = mutableMapOf<String, Any>().apply {
                        chatProfile?.modeSettings?.forEach { (k, v) -> this[k] = v }
                    }
                    // keep existing values, overwrite/insert the ones we just computed
                    rpgSettings?.let { modeSettingsMap["rpg"] = gson.toJson(it) }
                    murderSettings?.let { modeSettingsMap["murder"] = gson.toJson(it) }

                    // Build the session profile
                    val startedAt = sessionProfile?.startedAt ?: com.google.firebase.Timestamp.now()
                    val currentIsMultiplayer = sessionProfile?.multiplayer ?: false
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
                        enabledModes = chatProfile?.enabledModes?.toMutableList() ?: mutableListOf(),
                        modeSettings = modeSettingsMap
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

    private fun bindModeJumpButtons(sessionId: String, session: SessionProfile?) {
        val rpgBtn = findViewById<Button>(R.id.btnRpgSettings)
        val vnBtn  = findViewById<Button>(R.id.btnVnSettings)

        val modes = session?.enabledModes ?: emptyList()
        fun hasMode(k: String) = modes.any { it.equals(k, ignoreCase = true) }

        val rpgEnabled = hasMode("rpg")
        val vnEnabled  = hasMode("vn") || hasMode("visual_novel")

        rpgBtn.visibility = if (rpgEnabled) View.VISIBLE else View.GONE
        vnBtn.visibility  = if (vnEnabled)  View.VISIBLE else View.GONE

        val selectedCharsJson = gson.toJson(
            (session?.slotRoster ?: emptyList()).map { slot ->
                CharacterProfile(
                    id = slot.baseCharacterId ?: "placeholder-${slot.slotId}",   // important: base id
                    name = slot.name,
                    summary = slot.summary,
                    personality = slot.personality,
                    privateDescription = slot.privateDescription,
                    greeting = slot.greeting,
                    avatarUri = slot.avatarUri,
                    outfits = slot.outfits,
                    currentOutfit = slot.currentOutfit,
                    sfwOnly = slot.sfwOnly,
                    relationships = slot.relationships,
                    bubbleColor = slot.bubbleColor,
                    textColor = slot.textColor,
                    profileType = slot.profileType
                )
            }
        )

        val rpgSettingsRaw = session?.modeSettings?.get("rpg") as? String
        val vnSettingsRaw  = (session?.modeSettings?.get("vn") as? String)
            ?: (session?.modeSettings?.get("visual_novel") as? String)

        val areasJson = gson.toJson(session?.areas ?: emptyList<Area>())

        rpgBtn.setOnClickListener {
            if (!rpgEnabled) return@setOnClickListener
            val intent = Intent(this, RPGSettingsActivity::class.java).apply {
                putExtra("SESSION_ID", sessionId)
                rpgSettingsRaw?.let { putExtra("CURRENT_SETTINGS_JSON", it) }   // raw json string
                putExtra("SELECTED_CHARACTERS_JSON", selectedCharsJson)
                putExtra("AREAS_JSON", areasJson)
            }
            startActivityForResult(intent, REQ_RPG_SETTINGS)
        }

        vnBtn.setOnClickListener {
            if (!vnEnabled) return@setOnClickListener
            val intent = Intent(this, VNSettingsActivity::class.java).apply {
                putExtra("SESSION_ID", sessionId)
                vnSettingsRaw?.let { putExtra("CURRENT_SETTINGS_JSON", it) }     // raw json string
                putExtra("SELECTED_CHARACTERS_JSON", selectedCharsJson)
            }
            startActivityForResult(intent, REQ_VN_SETTINGS)
        }
    }

    private fun buildSlotRosterFromIds(
        orderedIds: List<String>,
        loadedProfiles: List<CharacterProfile>,
        areas: List<Area>
    ): List<SlotProfile> {
        val byId = loadedProfiles.associateBy { it.id }
        val defaultArea = areas.firstOrNull()?.name ?: "Default"
        val defaultLoc  = areas.firstOrNull()?.locations?.firstOrNull()?.name ?: "Entrance"

        return orderedIds.mapIndexed { index, id ->
            val slotId = "slot-${index + 1}-${System.currentTimeMillis()}"
            val prof = byId[id]
            if (prof != null) {
                prof.toSlotProfile(
                    relationships = emptyList(), // you merge session rels elsewhere
                    slotId = slotId
                ).copy(
                    lastActiveArea     = defaultArea,
                    lastActiveLocation = defaultLoc
                )
            } else {
                // No doc for this id → placeholder slot at the same position
                makePlaceholderSlot(index + 1).copy(
                    slotId = slotId,
                    lastActiveArea     = defaultArea,
                    lastActiveLocation = defaultLoc,
                    // optional: name that shows its slot number
                    name = "character${index + 1}"
                )
            }
        }
    }

    private suspend fun generateMysteryTimelineStrings(
        slots: List<SlotProfile>,
        rpgSettings: ModeSettings.RPGSettings?,
        murderSettings: ModeSettings.MurderSettings?,
        sessionProfile: SessionProfile
    ): Map<String, String> = withContext(Dispatchers.IO) {

        if (rpgSettings == null || murderSettings == null || !murderSettings.enabled) return@withContext emptyMap()

        val prompt = PromptBuilder.buildMysteryTimelineTextPrompt(slots, rpgSettings, murderSettings, sessionProfile)

        val raw = try {
            Facilitator.callActivationAI(prompt, BuildConfig.OPENAI_API_KEY)
        } catch (e: Exception) {
            Log.e("MysteryTimeline", "AI call failed", e)
            ""
        }

        Log.d("timeline", "raw: ${raw.take(600)}")

        // Extract JSON safely
        val jsonStart = raw.indexOf('{')
        val jsonEnd = raw.lastIndexOf('}')
        val json = if (jsonStart != -1 && jsonEnd > jsonStart) raw.substring(jsonStart, jsonEnd + 1) else raw

        val out = try {
            Gson().fromJson(json, AIMysteryTimelineTextOut::class.java)
        } catch (e: Exception) {
            Log.e("MysteryTimeline", "Bad JSON from AI", e)
            AIMysteryTimelineTextOut()
        }

        // Build resolver to convert ANY id (slotId or baseId) → baseCharacterId
        val anyToBase: Map<String, String> = buildMap {
            slots.forEach { s ->
                val base = s.baseCharacterId ?: s.slotId
                put(base, base)         // base -> base
                put(s.slotId, base)     // slot -> base
            }
            rpgSettings.characters.forEach { rc ->
                put(rc.characterId, rc.characterId) // ensure all base ids resolve
            }
        }

        // Normalize keys to baseCharacterId and trim text
        val map = out.characters.mapNotNull { c ->
            val base = anyToBase[c.characterId]
            if (base == null) {
                Log.w("timeline", "unknown id from AI: ${c.characterId}")
                null
            } else base to c.timelineText.trim()
        }.toMap()

        Log.d("timeline", "built ${map.size} timelines: ${map.keys}")
        map
    }

    private suspend fun harmonizeAndCondenseSlot(
        profile: CharacterProfile,
        relationships: List<Relationship>,
        rpgSettings: ModeSettings.RPGSettings?,
        murder: ModeSettings.MurderSettings?,
        currentSlotKey: String,
        moreInfo: String = "",
        allAreas: List<Area>,
        charToAreaMap: Map<String, String>,
        charToLocationMap: Map<String, String>
    ): SlotProfile = withContext(Dispatchers.IO) {
        val roleForThisChar = rpgSettings
            ?.characters
            ?.firstOrNull { it.characterId == profile.id }
            ?.role

        val murderContext = if (murder?.enabled == true) """
        MURDER MYSTERY CONTEXT:
        - Scene: ${murder.sceneDescription}
        - Weapon: ${murder.weapon}
        - Clues:
        ${murder.clues.joinToString("\n") { "- ${it.title}: ${it.description}" }}

        YOUR ROLE: ${roleForThisChar ?: ModeSettings.CharacterRole.HERO}
        Knowledge rules:
        - TARGET: is dead in the present; only appears in flashbacks/memories. No present-tense actions.
        - VILLAIN: never confess; you know how the murder was done and may plant misdirections.
        - HERO/OTHERS: you do not know the culprit; you can recall relevant memories and surface non-spoiler clues.
    """.trimIndent() else ""
        val sessionRelationships = relationships.filter { it.fromId == profile.id }
        val aiPrompt = """
        You are an expert RPG character profile editor and summarizer.
            $murderContext
        Given the following:
        - The character's full profile (name, summary, personality, private description, physical description, abilities, backstory, etc.).
        - The full, correct list of this character's session relationships (by toName, type, etc.).
        Your job:
        1. Rewrite the character's backstory as a series of short memories, each with 2-4 descriptive tags (characters, themes, events, etc).
        2. Write a condensed summary (1–2 vivid sentences, <100 tokens) combining their summary, background, moreinfo, personality, and privateDescription.
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
        Log.d("ai_response", "${profile.name} has been harmonized")

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
        val assignedAreaId = charToAreaMap[profile.id]
        val assignedLocationId = charToLocationMap[profile.id]

        // Find the actual objects so we can get their human-readable names
        val areaObj = allAreas.find { it.id == assignedAreaId }
        val locationObj = areaObj?.locations?.find { it.id == assignedLocationId }

        // Use the found name, or null if not assigned
        val startArea = areaObj?.name
        val startLocation = locationObj?.name
        val slotId = UUID.randomUUID().toString()

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
            return (str / 2) + (res / 2) + 8
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

        // 1) Pull VN settings JSON
        val vnSettingsJson = sessionProfile?.modeSettings?.get("vn") as? String
        val vnSettings = if (!vnSettingsJson.isNullOrBlank())
            Gson().fromJson(vnSettingsJson, VNSettings::class.java)
        else null

        // 2) Build the vnRelationships map for THIS slot (slot-keyed!)
        val vnRelMap = mutableMapOf<String, ModeSettings.VNRelationship>()
        vnSettings?.characterBoards
            ?.get(currentSlotKey)           // <-- use "characterN", not baseId
            ?.forEach { (toSlotKey, rel) ->
                // make sure the rel itself uses slot keys (defensive copy)
                if (rel.fromSlotKey != currentSlotKey || rel.toSlotKey != toSlotKey) {
                    vnRelMap[toSlotKey] = rel.copy(fromSlotKey = currentSlotKey, toSlotKey = toSlotKey)
                } else {
                    vnRelMap[toSlotKey] = rel
                }
            }

        // (optional) write back normalized settings (not required)
        sessionProfile?.modeSettings?.set("vn", Gson().toJson(vnSettings))

        // When building the SlotProfile, set role + hiddenRoles from updated rpgSettings:
        val roleName = matchingRpgCharacter?.role?.name ?: ""

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
            lastActiveLocation = startLocation,
            profileType = profile.profileType,
            typing = false,
            memories = backgroundMemories,
            rpgClass = matchingRpgCharacter?.characterClass?.name ?: "",
            stats = rpgStats,
            equipment = matchingRpgCharacter?.equipment ?: emptyList(),
            hp = hp,
            maxHp = maxHp,
            defense = defense,
            linkedTo = links,
            vnRelationships = vnRelMap,
            hiddenRoles = roleName,
            moreInfo = moreInfo.takeIf { it.isNotBlank() }
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
        progressTextView?.text = "Updating characters for the session: $current/$total done.\nThis will take a while.\nPlease don’t close the app."
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
                    .setItems(arrayOf("Profile", "Replace", "Remove", "Save", "Update")) { _, which ->
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
                                val slot = sessionProfile?.slotRoster?.firstOrNull { it.baseCharacterId == character.id }
                                // if character.id could be "placeholder-<slotId>", fall back to finding by that:
                                    ?: sessionProfile?.slotRoster?.firstOrNull { "placeholder-${it.slotId}" == character.id }

                                if (slot == null) {
                                    Toast.makeText(context, "Couldn’t resolve slot to replace.", Toast.LENGTH_SHORT).show()
                                    return@setItems
                                }

                                context.startActivityForResult(
                                    Intent(context, CharacterAdditionActivity::class.java)
                                        .putExtra("replaceSlotId", slot.slotId)
                                        .putExtra("oldBaseCharacterId", slot.baseCharacterId ?: "")
                                        .putExtra("entry", "Replace"),
                                    103
                                )
                            }
                            2 -> { // Remove
                                removeSlotForCharacter(character.id) // pass the baseCharacterId you're showing
                            }

                            3 -> { // Save
                                // Copy character, set current user as creator/author, and save as new character
                                if (character.private == true) {
                                    Toast.makeText(context, "This character is private.", Toast.LENGTH_SHORT).show()
                                } else {
                                    saveCharacterAsUser(character)
                                }
                            }
                            4 -> { // Update from base
                                applyCharacterUpdate(character.id)
                                needsUpdateIds.remove(character.id)
                                (charRecycler.adapter as? CharacterRowAdapter)?.notifyDataSetChanged()
                            }
                        }

                    }
                    .show()
            },
            onBindVisuals = { character, itemView ->
                val needsUpdate = needsUpdateIds.contains(character.id)
                val haloView = itemView.findViewById<ImageView>(R.id.update_halo)
                haloView.visibility = if (needsUpdate) View.VISIBLE else View.GONE
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

    private fun removeSlotForCharacter(baseCharacterId: String) {
        val sessionId = lobbySessionId ?: return
        val before = sessionProfile?.slotRoster.orEmpty()

        // Find the first matching roster entry for this character
        val target = before.firstOrNull { it.baseCharacterId == baseCharacterId }
        if (target == null) {
            Toast.makeText(this, "Couldn't find a matching slot.", Toast.LENGTH_SHORT).show()
            return
        }

        val after = before.filterNot { it.slotId == target.slotId }
        if (after.size == before.size) {
            Toast.makeText(this, "No change (slot not found by id).", Toast.LENGTH_SHORT).show()
            return
        }

        FirebaseFirestore.getInstance()
            .collection("sessions").document(sessionId)
            .update("slotRoster", after)
            .addOnSuccessListener {
                // Update local state & UI
                sessionProfile = sessionProfile?.copy(slotRoster = after)
                displaySession(sessionProfile!!) // refresh recycler
                Toast.makeText(this, "Character removed.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to remove: ${e.message}", Toast.LENGTH_SHORT).show()
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
            103 -> {
                val type = data.getStringExtra("SELECTED_TYPE") // "character" or "persona"
                val id   = data.getStringExtra("SELECTED_ID") ?: return
                val replaceSlotId      = data.getStringExtra("replaceSlotId") ?: return
                val oldBaseCharacterId = data.getStringExtra("oldBaseCharacterId")

                val sessionId = lobbySessionId ?: return
                val collection = if (type == "character") "characters" else "personas"

                FirebaseFirestore.getInstance().collection(collection).document(id).get()
                    .addOnSuccessListener { doc ->

                        // ⬇️ Make the types explicit + null-safe
                        val newProfile: CharacterProfile
                        val newRelationships: List<Relationship>

                        when (type) {
                            "character" -> {
                                val prof = doc.toObject(CharacterProfile::class.java) ?: return@addOnSuccessListener
                                newProfile = prof
                                newRelationships = prof.relationships ?: emptyList()
                            }
                            "persona" -> {
                                val persona = doc.toObject(PersonaProfile::class.java) ?: return@addOnSuccessListener
                                val prof = persona.toCharacterProfile()
                                newProfile = prof
                                newRelationships = persona.relationships ?: emptyList()
                            }
                            else -> return@addOnSuccessListener
                        }

                        val oldRoster = sessionProfile?.slotRoster ?: return@addOnSuccessListener
                        val oldSlot   = oldRoster.firstOrNull { it.slotId == replaceSlotId } ?: run {
                            Toast.makeText(this, "Slot not found.", Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }

                        // Optional: prevent duplicates except for the slot we’re replacing
                        if (oldRoster.any { it.baseCharacterId == newProfile.id && it.slotId != replaceSlotId }) {
                            Toast.makeText(this, "That character is already in the session.", Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }

                        val newSlot = oldSlot.copy(
                            baseCharacterId     = newProfile.id,
                            name                = newProfile.name,
                            summary             = newProfile.summary ?: "",
                            personality         = newProfile.personality ?: "",
                            privateDescription  = newProfile.privateDescription ?: "",
                            greeting            = newProfile.greeting ?: "",
                            avatarUri           = newProfile.avatarUri ?: "",
                            outfits             = newProfile.outfits ?: emptyList(),
                            currentOutfit       = newProfile.currentOutfit ?: "",
                            sfwOnly             = newProfile.sfwOnly,
                            relationships       = emptyList(),
                            isPlaceholder       = false
                        )

                        val updatedRelationships = (sessionProfile?.relationships ?: emptyList()).toMutableList()
                        if (!oldBaseCharacterId.isNullOrBlank()) {
                            updatedRelationships.removeAll { it.fromId == oldBaseCharacterId }
                        }
                        // ✅ newRelationships is a List<Relationship> now, so map() is available
                        updatedRelationships += newRelationships.map { it.copy(fromId = newProfile.id) }

                        val newRoster = oldRoster.map { if (it.slotId == replaceSlotId) newSlot else it }

                        FirebaseFirestore.getInstance().collection("sessions")
                            .document(sessionId)
                            .update(
                                mapOf(
                                    "slotRoster" to newRoster,
                                    "relationships" to updatedRelationships
                                )
                            )
                            .addOnSuccessListener {
                                sessionProfile?.slotRoster = newRoster
                                sessionProfile?.relationships = updatedRelationships
                                displaySession(sessionProfile!!)
                                Toast.makeText(this, "Character replaced!", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Failed to replace: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
            }
            REQ_RPG_SETTINGS -> {
                val rpgJson    = data.getStringExtra("RPG_SETTINGS_JSON")
                val murderJson = data.getStringExtra("MURDER_SETTINGS_JSON")
                val sessionId  = lobbySessionId ?: return

                val updates = mutableMapOf<String, Any>()
                rpgJson?.let    { updates["modeSettings.rpg"]    = it }
                murderJson?.let { updates["modeSettings.murder"] = it }

                if (updates.isNotEmpty()) {
                    FirebaseFirestore.getInstance()
                        .collection("sessions").document(sessionId)
                        .update(updates)
                        .addOnSuccessListener {
                            // local mirror
                            rpgJson?.let    { sessionProfile?.modeSettings?.set("rpg", it) }
                            murderJson?.let { sessionProfile?.modeSettings?.set("murder", it) }

                            // 🔥 Persist to character docs as requested
                            rpgJson?.let { persistRpgSheetsToCharacters(it) }

                            Toast.makeText(this, "RPG settings saved.", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { Toast.makeText(this, "Failed to save RPG settings.", Toast.LENGTH_SHORT).show() }
                }
            }

            REQ_VN_SETTINGS -> {
                val vnJson    = data.getStringExtra("VN_SETTINGS_JSON") ?: return
                val sessionId = lobbySessionId ?: return

                FirebaseFirestore.getInstance()
                    .collection("sessions").document(sessionId)
                    .update("modeSettings.vn", vnJson)
                    .addOnSuccessListener {
                        sessionProfile?.modeSettings?.set("vn", vnJson)

                        // 🔥 Persist to character docs as requested
                        persistVnBoardsToCharacters(vnJson)

                        Toast.makeText(this, "VN settings saved.", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { Toast.makeText(this, "Failed to save VN settings.", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun persistVnBoardsToCharacters(vnJson: String) {
        val vn = try { Gson().fromJson(vnJson, ModeSettings.VNSettings::class.java) } catch (_: Exception) { null }
        if (vn == null) return

        val roster = sessionProfile?.slotRoster.orEmpty()

        // Map slotKey -> (collection, id)
        data class DocRef(val col: String, val id: String)

        val slotKeyToRef: Map<String, DocRef> = roster.mapIndexedNotNull { index, slot ->
            val baseId = slot.baseCharacterId
            if (slot.isPlaceholder || baseId.isNullOrBlank()) null else {
                val col = if (slot.profileType == "player") "personas" else "characters"
                ModeSettings.SlotKeys.fromPosition(index) to DocRef(col, baseId)
            }
        }.toMap()

        val db = FirebaseFirestore.getInstance()
        val batch = db.batch()

        vn.characterBoards.forEach { (fromSlotKey, boardByToKey) ->
            val fromRef = slotKeyToRef[fromSlotKey] ?: return@forEach

            // 🚫 Only persist for CHARACTER docs (skip personas)
            if (fromRef.col != "characters") return@forEach

            // Build board only for CHARACTER targets as well
            val boardData: Map<String, Any> = boardByToKey.mapNotNull { (toSlotKey, rel) ->
                val toRef = slotKeyToRef[toSlotKey] ?: return@mapNotNull null
                if (toRef.col != "characters") return@mapNotNull null

                val levelsList = rel.levels.map { lvl ->
                    mapOf("level" to lvl.level, "threshold" to lvl.threshold, "personality" to lvl.personality)
                }

                toRef.id to mapOf(
                    "fromId"       to fromRef.id,
                    "toId"         to toRef.id,
                    "notes"        to rel.notes,
                    "currentLevel" to rel.currentLevel,
                    "upTriggers"   to rel.upTriggers,
                    "downTriggers" to rel.downTriggers,
                    "points"       to rel.points,
                    "levels"       to levelsList
                )
            }.toMap()

            if (boardData.isNotEmpty()) {
                val cref = db.collection("characters").document(fromRef.id)
                batch.set(cref, mapOf("vnBoard" to boardData), com.google.firebase.firestore.SetOptions.merge())
            }
        }

        batch.commit()
            .addOnSuccessListener { Toast.makeText(this, "VN boards saved to characters.", Toast.LENGTH_SHORT).show() }
            .addOnFailureListener { Toast.makeText(this, "Failed to save VN boards.", Toast.LENGTH_SHORT).show() }
    }

    private fun persistRpgSheetsToCharacters(rpgJson: String) {
        val rpg = try { Gson().fromJson(rpgJson, ModeSettings.RPGSettings::class.java) } catch (_: Exception) { null }
        if (rpg == null) return

        // Build lookups from the current session, skipping placeholders/nulls
        val slots = sessionProfile?.slotRoster.orEmpty()
        val slotIdToBaseId: Map<String, String> = slots
            .filter { it.baseCharacterId?.isNotBlank() == true && !it.isPlaceholder }
            .associate { it.slotId to it.baseCharacterId!! }

        val baseIdsInSession: Set<String> = slotIdToBaseId.values.toSet()
        if (baseIdsInSession.isEmpty()) return

        fun resolveBaseId(id: String): String? {
            // If id matches a slotId, map to baseId; else treat it as baseId if in session.
            return slotIdToBaseId[id] ?: id.takeIf { baseIdsInSession.contains(it) }
        }

        val db = FirebaseFirestore.getInstance()
        var batch = db.batch()
        var writes = 0

        rpg.characters.forEach { rc ->
            val baseId = resolveBaseId(rc.characterId) ?: return@forEach

            val statsMap = mapOf(
                "strength"     to rc.stats.strength,
                "agility"      to rc.stats.agility,
                "intelligence" to rc.stats.intelligence,
                "charisma"     to rc.stats.charisma,
                "resolve"      to rc.stats.resolve
            )

            val className = rc.characterClass.name
            val hp      = rc.hp      ?: calcHp(statsMap, className)
            val maxHp   = rc.maxHp   ?: calcMaxHp(statsMap, className)
            val defense = rc.defense ?: calcDefense(statsMap, className)

            val rpgSheet = mapOf(
                "class"     to className,
                "role"      to rc.role.name,
                "stats"     to statsMap,
                "equipment" to rc.equipment,
                "hp"        to hp,
                "maxHp"     to maxHp,
                "defense"   to defense
            )

            val cref = db.collection("characters").document(baseId)
            batch.set(cref, mapOf("rpgSheet" to rpgSheet), com.google.firebase.firestore.SetOptions.merge())
            writes++

            // Firestore batch safety
            if (writes >= 480) {
                batch.commit()
                batch = db.batch()
                writes = 0
            }
        }

        batch.commit()
            .addOnSuccessListener { Toast.makeText(this, "RPG sheets saved to characters.", Toast.LENGTH_SHORT).show() }
            .addOnFailureListener { Toast.makeText(this, "Failed to save RPG sheets.", Toast.LENGTH_SHORT).show() }
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
        val isPh = this.isPlaceholder || this.baseCharacterId.isNullOrBlank()
        return CharacterProfile(
            id = if (isPh) "placeholder-${this.slotId}" else this.baseCharacterId!!,
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
        val initialLastSynced = this.lastTimestamp ?: this.createdAt
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
            profileType = this.profileType ?: "bot",
            lastSynced = initialLastSynced
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

    fun makePlaceholderSlot(slotIndex: Int): SlotProfile = SlotProfile(
        slotId = "slot-$slotIndex-${System.currentTimeMillis()}",
        baseCharacterId = "placeholder",
        name = "Empty Slot",
        summary = "",
        personality = "",
        isPlaceholder = true,
        currentOutfit = "",
        // other fields default/blank
    )

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

    private fun checkForCharacterUpdates() {
        // 1) Collect only real baseIds (skip placeholders / blanks)
        val slots = sessionProfile?.slotRoster.orEmpty()
        val baseIds: List<String> = slots
            .filter { !it.isPlaceholder && !it.baseCharacterId.isNullOrBlank() }
            .map { it.baseCharacterId!! }
            .distinct()

        if (baseIds.isEmpty()) return

        val db = FirebaseFirestore.getInstance()

        // Firestore whereIn limit is 10 → chunk
        val chunks: List<List<String>> = baseIds.chunked(10)

        // Accumulators
        val baseMap = mutableMapOf<String, CharacterProfile>()
        needsUpdateIds.clear()
        pendingUpdateDeltas.clear()

        // Helper to finalize after all chunks complete
        fun finalizeAndNotify() {
            // Recompute from scratch each time
            needsUpdateIds.clear()
            pendingUpdateDeltas.clear()

            val characterBackedIds = baseMap.keys

            slots.forEach { slot ->
                val baseId = slot.baseCharacterId ?: return@forEach
                val base   = baseMap[baseId] ?: return@forEach
                val baseTs = base.lastTimestamp ?: base.createdAt
                val lastSynced = slot.lastSynced
                Log.d("update", "${slot.name} $base ${base.profileType} ${slot.lastSynced}")

                // Only use the time gate if BOTH timestamps exist
                val haveBothTimes = (baseTs != null && lastSynced != null)
                val timeSaysNewer = haveBothTimes && baseTs!!.toDate().after(lastSynced!!.toDate())
                val isPersona = base.profileType == "player"
                if (isPersona) {
                    Log.d("update", "skip persona slot=${slot.name}")
                    return@forEach
                }

                // Compute the actual content differences you care about
                val delta = computeDelta(base, slot)

                // Rule:
                // - If we CAN compare by time: require timeSaysNewer AND actual delta.
                // - If we CAN'T compare by time (e.g., lastSynced == null): rely ONLY on actual delta.
                val needsUpdate = !isPersona && (
                        if (haveBothTimes) (timeSaysNewer && delta.hasChanges)
                        else delta.hasChanges
                        )

                if (needsUpdate) {
                    needsUpdateIds += baseId
                    pendingUpdateDeltas[baseId] = delta
                }
            }

            // Update UI highlights no matter what (clears old halos when empty)
            (charRecycler.adapter as? CharacterRowAdapter)?.setHighlightIds(needsUpdateIds)

            if (needsUpdateIds.isNotEmpty()) {
                Toast.makeText(this, "Some characters have updates available.", Toast.LENGTH_SHORT).show()
            }

            // Debug logging
            slots.forEach { slot ->
                val baseId = slot.baseCharacterId
                val base = baseId?.let { baseMap[it] }
                val baseTs = base?.lastTimestamp ?: base?.createdAt
                Log.d("Update", "for ${slot.name} Base=$baseTs  Slot.lastSynced=${slot.lastSynced}  needs=${needsUpdateIds.contains(baseId)}")
            }
        }


        // 2) Query by DOCUMENT ID, not "id" field
        var remaining = chunks.size
        chunks.forEach { ids ->
            db.collection("characters")
                .whereIn(FieldPath.documentId(), ids)
                .get()
                .addOnSuccessListener { snap ->
                    snap.documents.forEach { doc ->
                        // Prefer doc.id as the key; also read payload into your model
                        val profile = doc.toObject(CharacterProfile::class.java) ?: return@forEach
                        // If your CharacterProfile.id might be missing/outdated, trust doc.id
                        baseMap[doc.id] = profile.copy(id = doc.id)
                    }
                }
                .addOnCompleteListener {
                    remaining--
                    if (remaining == 0) finalizeAndNotify()
                }
        }
    }

    private fun computeDelta(base: CharacterProfile, slot: SlotProfile): UpdateDelta {
        val summaryChanged = false // slot uses condensed summary by design
        val privateChanged = (base.privateDescription ?: "") != (slot.privateDescription ?: "")
        val posesChanged   = (base.outfits ?: emptyList()) != (slot.outfits ?: emptyList<Outfit>()) ||
                (base.currentOutfit ?: "") != (slot.currentOutfit ?: "")
        return UpdateDelta(summaryChanged, privateChanged, posesChanged)
    }

    private fun applyCharacterUpdate(baseCharacterId: String) {
        val sessionId = lobbySessionId ?: return
        val db = FirebaseFirestore.getInstance()

        db.collection("characters").document(baseCharacterId)
            .get()
            .addOnSuccessListener { doc ->
                val base = doc.toObject(CharacterProfile::class.java) ?: return@addOnSuccessListener
                val newLastSynced = Timestamp.now() // <<< fallback

                val newRoster = sessionProfile?.slotRoster?.map { slot ->
                    if (slot.baseCharacterId != baseCharacterId) return@map slot
                    slot.copy(
                        summary = base.summary ?: slot.summary,
                        privateDescription = base.privateDescription ?: slot.privateDescription,
                        outfits = base.outfits ?: slot.outfits,
                        currentOutfit = base.currentOutfit ?: slot.currentOutfit,
                        lastSynced = newLastSynced // <<< will now be non-null
                    )
                } ?: return@addOnSuccessListener

                db.collection("sessions")
                    .document(sessionId)
                    .update("slotRoster", newRoster)
                    .addOnSuccessListener {
                        sessionProfile?.slotRoster = newRoster
                        needsUpdateIds.remove(baseCharacterId)
                        (charRecycler.adapter as? CharacterRowAdapter)?.setHighlightIds(needsUpdateIds)
                        displaySession(sessionProfile!!)
                    }
            }
    }
}

private suspend fun generateMurderSetup(
    slots: List<SlotProfile>,
    rpgSettingsIn: ModeSettings.RPGSettings?,
    murderIn: ModeSettings.MurderSettings?,
    sessionProfile: SessionProfile
): Pair<ModeSettings.RPGSettings?, ModeSettings.MurderSettings?> = withContext(Dispatchers.IO) {

    // Only seed when enabled AND randomized
    if (rpgSettingsIn == null || murderIn == null || !murderIn.enabled || murderIn.randomizeKillers != true) {
        return@withContext rpgSettingsIn to murderIn
    }

    val prompt = PromptBuilder.buildMurderSeedingPrompt(slots, rpgSettingsIn, murderIn, sessionProfile)

    val raw = try {
        Facilitator.callActivationAI(prompt, BuildConfig.OPENAI_API_KEY)
    } catch (e: Exception) {
        Log.e("MurderSeed", "AI call failed", e); ""
    }

    // Extract JSON
    val jsonStart = raw.indexOf('{')
    val jsonEnd = raw.lastIndexOf('}')
    val json = if (jsonStart != -1 && jsonEnd > jsonStart) raw.substring(jsonStart, jsonEnd + 1) else raw

    val out = try {
        Gson().fromJson(json, AIMurderOut::class.java)
    } catch (e: Exception) {
        Log.e("MurderSeed", "Bad JSON from AI: $raw", e); AIMurderOut()
    }

    Log.d("ai_response", "raw output $out")

    // ---- Normalize ids to baseCharacterId (accept slotId or baseId) ----
    val idResolver: Map<String, String> = buildMap {
        // map base->base and slot->base
        slots.forEach { s ->
            val base = s.baseCharacterId ?: s.slotId
            put(base, base)
            put(s.slotId, base)
        }
        // make sure every RPG base id resolves
        rpgSettingsIn.characters.forEach { rc ->
            put(rc.characterId, rc.characterId)
        }
    }

    val aiRolesBase: List<AIMurderRole> = out.roles.mapNotNull { r ->
        val base = idResolver[r.characterId]
        if (base == null) {
            Log.w("MurderSeed", "Unknown id from AI: ${r.characterId}"); null
        } else AIMurderRole(base, r.role.uppercase())
    }

    // Validate AI roles
    val targets = aiRolesBase.filter { it.role == "TARGET" }.map { it.characterId }.distinct()
    val villains = aiRolesBase.filter { it.role == "VILLAIN" }.map { it.characterId }.distinct()
    val validRoles = (targets.size == 1 && villains.isNotEmpty())
    if (!validRoles) Log.e("MurderSeed", "AI roles invalid (targets=${targets.size}, villains=${villains.size}). Keeping existing roles.")

    // Apply to RPG (strictly from AI if valid; otherwise leave as-is)
    val forced = if (validRoles) aiRolesBase.associate { it.characterId to it.role } else emptyMap()

    val updatedRpg = rpgSettingsIn.copy(
        characters = rpgSettingsIn.characters.map { rc ->
            when (forced[rc.characterId]) {
                "TARGET"  -> rc.copy(role = ModeSettings.CharacterRole.TARGET)
                "VILLAIN" -> rc.copy(role = ModeSettings.CharacterRole.VILLAIN)
                else      -> rc
            }
        }.toMutableList()
    )

    // Update weapon/scene/clues
    val updatedMurder = murderIn.copy(
        weapon = (out.weapon.takeIf { it.isNotBlank() } ?: murderIn.weapon).take(60),
        sceneDescription = (out.scene.takeIf { it.isNotBlank() } ?: murderIn.sceneDescription).take(600),
        clues = if (out.clues.isNotEmpty()) {
            out.clues.map {
                ModeSettings.MurderClue(
                    id = UUID.randomUUID().toString(),
                    title = it.title.take(60),
                    description = it.description.take(300)
                )
            }.toMutableList()
        } else murderIn.clues
    )

    Log.d("MurderSeed", "Applied roles -> " + updatedRpg.characters.joinToString { "${it.characterId}:${it.role}" })
    Log.d("ai_response", "updating $updatedMurder")

    updatedRpg to updatedMurder
}

// --- For AI ---
private data class AIMurderRole(val characterId: String, val role: String)
private data class AIMurderClue(val title: String, val description: String)
private data class AIMurderOut(
    val roles: List<AIMurderRole> = emptyList(),
    val weapon: String = "",
    val scene: String = "",
    val clues: List<AIMurderClue> = emptyList()
)

data class CleanedData(
    val cleanedDescription: String,
    val cleanedSecretDescription: String,
    val cleanedRelationships: List<Relationship>
)

data class AIMysteryTimelineTextOut(
    val characters: List<PerCharTimelineText> = emptyList()
)

data class PerCharTimelineText(
    val characterId: String = "",
    val timelineText: String = "" // ← this is the entire plain-text timeline we’ll store
)