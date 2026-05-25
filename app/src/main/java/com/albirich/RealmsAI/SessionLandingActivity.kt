package com.albirich.RealmsAI

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
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
import com.albirich.RealmsAI.FirestoreClient.db
import com.albirich.RealmsAI.adapters.CollectionAdapter.CharacterRowAdapter
import com.albirich.RealmsAI.ai.Director
import com.albirich.RealmsAI.ai.Facilitator
import com.albirich.RealmsAI.ai.PromptBuilder
import com.albirich.RealmsAI.models.*
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
import com.albirich.RealmsAI.models.CharacterProfile
import com.albirich.RealmsAI.models.ModeSettings.VNSettings
import com.google.common.reflect.TypeToken
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
    private lateinit var updateButton: Button

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
    private var lobbyListener: ListenerRegistration? = null
    private var isProceedingToGame = false

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
    private val REQ_VN_SETTINGS = 202
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
    private var modelToUse = "nvidia"

    // stuff for updating chars
    private val pendingUpdateDeltas = mutableMapOf<String, UpdateDelta>()  // baseId -> delta
    private var needsUpdateIds: MutableSet<String> = mutableSetOf()

    data class UpdateDelta(
        val visualsChanged: Boolean,
        val personalityChanged: Boolean,
        val secretChanged: Boolean,
        val linksChanged: Boolean
    ) {
        val hasChanges get() = visualsChanged || personalityChanged || secretChanged || linksChanged
    }

    data class SessionUpdateDelta(
        val descriptionChanged: Boolean,
        val rosterChanged: Boolean,
        val worldChanged: Boolean,
        val settingsChanged: Boolean
    ) {
        val hasChanges get() = descriptionChanged || rosterChanged || worldChanged || settingsChanged
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


        // --- Get intents ---
        val sessionProfileJson = intent.getStringExtra("SESSION_PROFILE_JSON")
        val chatProfileJson = intent.getStringExtra("CHAT_PROFILE_JSON")
        val characterProfilesJson = intent.getStringExtra("CHARACTER_PROFILES_JSON")
        val inviteProfilesJson = intent.getStringExtra("INVITE_PROFILE_JSON")

        // NEW: Grab the ID directly
        val intentSessionId = intent.getStringExtra("SESSION_ID")

        var enteredFrom = ""

        val isFromInvite = intent.getBooleanExtra("FROM_INVITE", false)

        when {
            // 1. LOAD FROM LIVE DATABASE (Used for Hub loads AND Invites!)
            intentSessionId != null && sessionProfileJson == null -> {
                Log.d("sessionlanding_debug", "Loading Live Session ID: $intentSessionId")
                lobbySessionId = intentSessionId

                // If they came from an invite, hide host buttons immediately
                if (isFromInvite) {
                    enteredFrom = "Invite"
                    addPlayerButton.visibility = View.GONE
                    startSessionBtn.visibility = View.GONE
                } else {
                    enteredFrom = "Sessionhub"
                }

                FirebaseFirestore.getInstance().collection("sessions").document(intentSessionId).get()
                    .addOnSuccessListener { doc ->
                        if (!doc.exists()) {
                            Toast.makeText(this, "Session not found.", Toast.LENGTH_LONG).show()
                            finish()
                            return@addOnSuccessListener
                        }

                        val loadedProfile = doc.toObject(SessionProfile::class.java)
                        if (loadedProfile != null) {
                            sessionProfile = loadedProfile
                            chatProfile = null
                            relationships = sessionProfile?.slotRoster?.flatMap { it.relationships }?.toMutableList() ?: mutableListOf()
                            loadedAreas = sessionProfile?.areas?.toMutableList() ?: mutableListOf()

                            displaySession(sessionProfile!!)
                            bindModeJumpButtons(lobbySessionId!!, sessionProfile)
                            startLobbyListener()
                        } else {
                            finish()
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to load session.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
            }

            // 2. From sessionHub (History with JSON)
            sessionProfileJson != null -> {
                Log.d("sessionlanding_debug", "session history load")
                sessionProfile = Gson().fromJson(sessionProfileJson, SessionProfile::class.java)
                val local = Gson().fromJson(sessionProfileJson, SessionProfile::class.java)
                lobbySessionId = sessionProfile!!.sessionId
                chatProfile = null
                relationships =
                    sessionProfile?.slotRoster?.flatMap { it.relationships }?.toMutableList()
                        ?: mutableListOf()
                displaySession(sessionProfile!!)

                // Still try to fetch live updates, but we have local data to show immediately
                FirebaseFirestore.getInstance()
                    .collection("sessions")
                    .document(lobbySessionId!!)
                    .get()
                    .addOnSuccessListener { snap ->
                        val live = snap.toObject(SessionProfile::class.java)
                        val merged = (live ?: local)
                        sessionProfile = merged
                        bindModeJumpButtons(lobbySessionId!!, merged)
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
                Log.d(
                    "InviteDebug",
                    "InviteProfile.sessionDescription = ${inviteProfile?.sessionDescription}"
                )
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
                    charRecycler.layoutManager =
                        LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
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
                            if (live == null) Log.w(
                                "Session",
                                "Live session missing, using local cache"
                            )
                        }
                        sessionProfile = merged
                        bindModeJumpButtons(lobbySessionId!!, merged)
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
                Log.d("sessionlanding_debug", "chat loaded")

                val sessionId = FirebaseFirestore.getInstance().collection("sessions").document().id

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
                        areas = loadedAreas,
                        sessionId = sessionId, // Add named argument here
                        characterToLorebooks = chatProfile?.characterToLorebooks ?: emptyMap() // <-- ADDED
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

                    val cleanedAssignments = slots.mapIndexed { idx, slot -> "character${idx + 1}" to slot.name }.toMap()
                    val cleanDesc = substitutePlaceholders(chatProfile?.description ?: "", cleanedAssignments)
                    val cleanSecretDesc = substitutePlaceholders(chatProfile?.secretDescription ?: "", cleanedAssignments)
                    val cleanGreeting = substitutePlaceholders(chatProfile?.firstmessage ?: "", cleanedAssignments)

                    // 5. Create and save SessionProfile immediately!
                    sessionProfile = SessionProfile(
                        sessionId = sessionId,
                        chatId = chatProfile?.id ?: "",
                        title = chatProfile?.title ?: "Session",
                        sessionDescription = cleanDesc,
                        secretDescription = cleanSecretDesc,
                        initialGreeting = cleanGreeting,
                        chatMode = chatMode,
                        startedAt = com.google.firebase.Timestamp.now(),
                        sfwOnly = chatProfile?.sfwOnly ?: true,
                        sessionSummary = chatProfile?.description ?: "",
                        userMap = userMap,
                        userList = listOf(userId),
                        userAssignments = emptyMap(),
                        slotRoster = slots,
                        characterIds = chatProfile?.characterIds ?: emptyList(),
                        areas = loadedAreas,
                        history = emptyList(),
                        currentAreaId = loadedAreas.firstOrNull()?.name ?: "Default",
                        relationships = relationships,
                        events = chatProfile?.events ?: emptyList(),
                        multiplayer = false,
                        enabledModes = chatProfile?.enabledModes?.toMutableList()
                            ?: mutableListOf(),
                        modeSettings = chatProfile?.modeSettings?.toMutableMap() ?: mutableMapOf(),
                        globalLorebookIds = chatProfile?.globalLorebookIds ?: emptyList()
                    )

                    saveSessionProfile(sessionProfile!!, sessionId) { success ->
                        if (success) {
                            lobbySessionId = sessionId
                            startLobbyListener() // Only listen once we know it's on the server
                        }
                    }
                    bindModeJumpButtons(sessionId, sessionProfile)
                    // Now update UI and any further logic with this sessionProfile.
                    displaySession(sessionProfile!!)
                    lobbySessionId = sessionId
                    Log.d("sessionlanding_debug", "chat loading lobbySessionID = $lobbySessionId")
                }
                enteredFrom = "Chathub"
                // REPLACE WITH THIS:
                if (lobbySessionId != null) {
                    startLobbyListener()
                }
            }

            // From CharacterHub (list of characters)
            characterProfilesJson != null -> {
                Log.d("sessionlanding_debug", "character loaded")

                val sessionId = FirebaseFirestore.getInstance().collection("sessions").document().id
                characterProfiles = try {
                    Gson().fromJson(characterProfilesJson, Array<CharacterProfile>::class.java)
                        .toList()
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
                val profile = characterProfiles.firstOrNull()

                val savedArea = loadedAreas.find { it.id == profile?.startingAreaId }
                val savedLoc = savedArea?.locations?.find { it.id == profile?.startingLocationId }

                // If found, use the names. If not, fallback to the first item in the list like before.
                val defaultArea = (savedArea?.name ?: loadedAreas.firstOrNull()?.name ?: "Default").trim()
                val defaultLocation = (savedLoc?.name ?: loadedAreas.firstOrNull()?.locations?.firstOrNull()?.name ?: "Entrance").trim()

                // 2. Build slot roster
                val slots = characterProfiles.map { profile ->
                    val slotId = UUID.randomUUID().toString()

                    // ONE CLEAN LINE!
                    transferWardrobeToSession(sessionId, slotId, profile.id)

                    profile.toSlotProfile(
                        relationships = relationships.filter { it.fromId == profile.id },
                        slotId = slotId
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
                sessionProfile = SessionProfile(
                    sessionId = sessionId,
                    chatId = "", // No chatProfile, so probably blank
                    title = characterProfiles.firstOrNull()?.name ?: "Session",
                    sessionDescription = characterProfiles.firstOrNull()?.soloScenario ?: "",
                    secretDescription = "",
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
                    events = characterProfiles.firstOrNull()?.events ?: emptyList(),
                    multiplayer = false,
                    globalLorebookIds = chatProfile?.globalLorebookIds ?: emptyList()
                )
                saveSessionProfile(sessionProfile!!, sessionId) { success ->
                    if (success) {
                        lobbySessionId = sessionId
                        startLobbyListener() // Only listen once we know it's on the server
                    } else {
                        Toast.makeText(this, "Failed to initialize lobby.", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
                // Update UI/lobby
                // val playerIds = sessionProfile?.userMap?.keys?.toList() ?: emptyList()
                // playerRecycler.layoutManager =
                //     LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
                // playerRecycler.adapter = PlayerSlotAdapter(playerIds, onUserClick = { userId ->
                //     Log.d("PlayerClick", "User clicked: $userId")
                // })
                displaySession(sessionProfile!!)
                lobbySessionId = sessionId
                bindModeJumpButtons(sessionId, sessionProfile)
                enteredFrom = "Characterhub"
                // REPLACE WITH THIS:
                if (lobbySessionId != null) {
                    startLobbyListener()
                }
            }

            else -> {
                Toast.makeText(
                    this,
                    "Error: No session/chat/character data found.",
                    Toast.LENGTH_LONG
                ).show()
                finish()


                return
            }
        }

        if (!lobbySessionId.isNullOrBlank() && enteredFrom == "Invite") {
            val sessionId = lobbySessionId!!
            FirebaseFirestore.getInstance().collection("sessions")
                .document(sessionId)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null && snapshot.exists()) {
                        // Safely default to FALSE so they can sit in the lobby
                        val isBuilding = snapshot.getBoolean("isBuilding") ?: false
                        val sessionStarted = snapshot.getBoolean("started") ?: false
                        val readyCount = (snapshot.get("readyCount") as? Number)?.toInt() ?: 0
                        val totalSlots = sessionProfile?.slotRoster?.size ?: 0

                        // 1. The Host clicked start! Show the dialog (only once)
                        if (isBuilding && !buildDialogShown) {
                            showCharacterProgressDialog(totalSlots)
                            buildDialogShown = true
                        }

                        // 2. The AI is working. Update the progress bar!
                        if (isBuilding && buildDialogShown) {
                            updateCharacterProgress(readyCount, totalSlots)
                        }

                        // 3. The AI finished! Dismiss dialog and jump into the game.
                        if (sessionStarted && !isProceedingToGame) {
                            logSessionEngagement(isHost = false)
                            isProceedingToGame = true

                            FirebaseFirestore.getInstance()
                                .collection("sessions")
                                .document(sessionId)
                                .get()
                                .addOnSuccessListener { doc ->
                                    val finalSession = doc.toObject(SessionProfile::class.java)
                                    if (finalSession != null) {
                                        dismissCharacterProgressDialog()

                                        val intent = Intent(this@SessionLandingActivity, MainActivity::class.java)
                                        intent.putExtra("SESSION_ID", finalSession.sessionId)
                                        intent.putExtra("ENTRY_MODE", "GUEST")
                                        startActivity(intent)
                                        finish()
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
        val infoButtonSessionCharacters: ImageButton =
            findViewById(R.id.infoButtonSessionCharacters)
        infoButtonSessionCharacters.setOnClickListener {
            AlertDialog.Builder(this@SessionLandingActivity)
                .setTitle("Characters")
                .setMessage(
                    "You can add up to 50 characters. Click and hold a character for more options.\n" +
                            "Profile takes you to the character's profile.\n" +
                            "Replace will replace the character in that position (good for switching another characters relationships with the new character).\n" +
                            "Remove removes the character from the list.\n" +
                            "Save will copy the character to your character list as if you created them."
                )
                .setPositiveButton("OK", null)
                .show()
        }

        addcharacter.setOnClickListener {
            val currentRosterSize = sessionProfile?.slotRoster?.size ?: 0
            val isOneOnOne = sessionProfile?.chatMode.equals("ONEONONE", ignoreCase = true)

            // 1. Check if the Host is Premium
            val hostId = sessionProfile?.userList?.firstOrNull() ?: return@setOnClickListener

            FirebaseFirestore.getInstance().collection("users").document(hostId).get()
                .addOnSuccessListener { userDoc ->
                    val hostIsPremium = userDoc.getBoolean("isPremium") ?: false
                    val maxAllowed = if (hostIsPremium) 50 else 20

                    // 2. Enforce the Limits
                    if (currentRosterSize >= maxAllowed && !isOneOnOne) {
                        Toast.makeText(
                            this,
                            "The Host's plan only allows $maxAllowed characters per session.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@addOnSuccessListener
                    }

                    if (currentRosterSize >= 2 && isOneOnOne) {
                        Toast.makeText(
                            this,
                            "You can only have 2 characters in One on One sessions.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@addOnSuccessListener
                    }

                    // 3. Proceed to selection
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

                            val cleanedRelationships =
                                replaceRelationshipPlaceholders(filteredRelationships, assignments)

                            val intent = Intent(this, SessionRelationshipActivity::class.java)
                            intent.putStringArrayListExtra("PARTICIPANT_IDS", ArrayList(allIds))
                            intent.putExtra(
                                "RELATIONSHIPS_JSON",
                                Gson().toJson(cleanedRelationships)
                            )
                            startActivityForResult(intent, 102)
                        } else {
                            Toast.makeText(this, "Session data not found.", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
            } else {
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

        updateButton = findViewById(R.id.updateButton)

        // --- Start Session ---
        startSessionBtn.setOnClickListener {
            // --- Make sure player has a character first ---
            val rosterCount = sessionProfile?.slotRoster?.size ?: 0
            if (rosterCount < 2) {
                Toast.makeText(
                    this,
                    "You need at least 2 characters to start a session.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (enteredFrom == "Sessionhub" && sessionProfile != null) {
                isProceedingToGame = true // <--- ADD THIS
                val intent = Intent(this, MainActivity::class.java)
                // intent.putExtra("SESSION_PROFILE_JSON", Gson().toJson(sessionProfile))
                intent.putExtra("SESSION_ID", sessionProfile?.sessionId)
                intent.putExtra("ENTRY_MODE", "LOAD")
                startActivity(intent)
                finish()
                return@setOnClickListener
            } else {
                Log.d("session_start", "ready")
                checkPermissions()
            }
        }
    }

    private fun checkPermissions() {
        Log.d("session_start", "checking permissions")
        val rosterCount = sessionProfile?.slotRoster?.size ?: 0

        // Show a temporary loading indicator while we check permissions
        val loadingDialog = AlertDialog.Builder(this)
            .setMessage("Checking Host permissions...")
            .setCancelable(false)
            .create()
        loadingDialog.show()

        lifecycleScope.launch {
            try {
                val db = FirebaseFirestore.getInstance()
                val currentUserId = FirebaseAuth.getInstance().currentUser!!.uid

                // 1. Fetch ONLY the Host's Premium status (Cheaper and faster!)
                val userDoc = db.collection("users").document(currentUserId).get().await()
                val hostIsPremium = userDoc.getBoolean("isPremium") ?: false

                // 2. Check for Active Modes
                val currentModes = (sessionProfile?.enabledModes ?: mutableListOf()) +
                        (chatProfile?.enabledModes ?: mutableListOf())
                val hasSpecialModes =
                    currentModes.any { it == "rpg" || it == "visual_novel" || it == "god_mode" }

                loadingDialog.dismiss() // Check complete

                // --- CHECK 1: CHARACTER LIMITS ---
                val charLimit = if (hostIsPremium) 50 else 20

                if (rosterCount > charLimit) {
                    AlertDialog.Builder(this@SessionLandingActivity)
                        .setTitle("Character Limit Exceeded")
                        .setMessage("You have $rosterCount characters, but the Free tier limit is $charLimit.\n\nReduce characters or Upgrade to Premium to host larger lobbies.")
                        .setPositiveButton("Upgrade") { _, _ ->
                            startActivity(
                                Intent(
                                    this@SessionLandingActivity,
                                    UpgradeActivity::class.java
                                )
                            )
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    return@launch
                }

                // --- CHECK 2: SPECIAL MODES ---
                if (!hostIsPremium && hasSpecialModes) {
                    // The Host is free, but trying to start a session with premium modes
                    withContext(Dispatchers.Main) {
                        AlertDialog.Builder(this@SessionLandingActivity)
                            .setTitle("Premium Features Detected")
                            .setMessage(
                                "This session uses Special Modes (RPG, VN, etc.) which require the Host to have a Premium plan.\n\n" +
                                        "You can Upgrade to unlock these modes for everyone, or start a Standard Session (Sandbox)."
                            )
                            .setPositiveButton("Start Standard Session") { _, _ ->
                                // PROCEED: Strip modes and continue
                                startNewSession(stripModes = true)
                            }
                            .setNeutralButton("Upgrade") { _, _ ->
                                startActivity(Intent(this@SessionLandingActivity, UpgradeActivity::class.java))
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                    return@launch // Stop here, wait for dialog choice
                }

                // If we get here, the Host is Premium OR no special modes are active.
                Log.d("session_start", "permissions good starting session")
                startNewSession(stripModes = false)

            } catch (e: Exception) {
                loadingDialog.dismiss()
                Log.e("SessionStart", "Error checking permissions", e)
                Toast.makeText(
                    this@SessionLandingActivity,
                    "Error starting session. Check connection.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun startNewSession(stripModes: Boolean) {
        val rosterCount = sessionProfile?.slotRoster?.size ?: 0
        Log.d("session_start", "harmonizing $rosterCount characters")
        showCharacterProgressDialog(sessionProfile?.slotRoster!!.size)
        buildDialogShown = true
        lifecycleScope.launch {
            try {
                val userId = FirebaseAuth.getInstance().currentUser!!.uid

                // Fetch "isPremium" directly from Firestore to prevent cheating
                val userDoc =
                    FirebaseFirestore.getInstance().collection("users").document(userId).get()
                        .await()
                val isPremium = userDoc.getBoolean("isPremium") ?: false

                val sessionId = lobbySessionId ?: error("Session ID missing!")
                val docRef = db.collection("sessions").document(sessionId)

                // Check if document exists before starting the expensive AI loop
                val docSnap = docRef.get().await()
                if (!docSnap.exists()) {
                    withContext(Dispatchers.Main) {
                        dismissCharacterProgressDialog()
                        Toast.makeText(
                            this@SessionLandingActivity,
                            "Session no longer exists.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }
                // DEFINE LIMITS
                val charLimit = if (isPremium) 50 else 20 // Tweak these numbers as needed


                if (rosterCount > charLimit) {
                    // Creating the popup
                    AlertDialog.Builder(this@SessionLandingActivity)
                        .setTitle("Character Limit Exceeded")
                        .setMessage("You have $rosterCount characters, but your plan limit is $charLimit.\n\nPlease remove some characters or upgrade to Premium.")
                        .setPositiveButton("Upgrade") { _, _ ->
                            // showPaywallDialog() // Uncomment when you have the paywall function here
                        }
                        .setNegativeButton("OK", null)
                        .show()
                    return@launch // STOP EXECUTION
                }
                logSessionEngagement(isHost = true)

                // --- 4. START THE BUILD PROCESS ---
                showCharacterProgressDialog(sessionProfile?.slotRoster!!.size)
                buildDialogShown = true
                Log.d("session_start", "starting session as Create")
                lobbySessionId = sessionId

                val sanitizedAreas = loadedAreas.map { area ->
                    area.copy(
                        name = area.name.trim(),
                        locations = area.locations.map { loc ->
                            loc.copy(name = loc.name.trim())
                        }.toMutableList()
                    )
                }

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
                val rpgSettingsJson = (chatProfile?.modeSettings?.get("rpg") as? String)
                    ?: (sessionProfile?.modeSettings?.get("rpg") as? String)

                val murderSettingsJson = (chatProfile?.modeSettings?.get("murder") as? String)
                    ?: (sessionProfile?.modeSettings?.get("murder") as? String)

                var rpgSettings: ModeSettings.RPGSettings? = rpgSettingsJson?.let {
                    try {
                        gson.fromJson(it, ModeSettings.RPGSettings::class.java)
                    } catch (_: Exception) {
                        null
                    }
                }
                var murderSettings: ModeSettings.MurderSettings? = murderSettingsJson?.let {
                    try {
                        gson.fromJson(it, ModeSettings.MurderSettings::class.java)
                    } catch (_: Exception) {
                        null
                    }
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
                        Log.d("session_start", "seeded: $murderSettings")
                    } else {
                        Log.d("session_start", "randomizeKillers disabled; using creator roles")
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
                Log.d("session_start", "ready to harmonize")
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
                        allAreas = sanitizedAreas,
                        charToAreaMap = areaMap,
                        charToLocationMap = locMap,
                        sessionProfile = sessionProfile!!
                    )
                    harmonizedSlots.add(h)

                    withContext(Dispatchers.Main) {
                        updateCharacterProgress(i + 1, slots.size)
                    }

                    // Use a safe update here
                    docRef.update("readyCount", i + 1)
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
                    SessionUser(
                        userId = uid,
                        username = usernames[uid] ?: "Player",
                        personaIds = emptyList(), // We don't care about this anymore
                        activeSlotId = null,      // Force them to pick in the session!
                        bubbleColor = "#CCCCCC",
                        textColor = "#000000"
                    )
                }


                // Now build fixedProfile with updated modeSettings (Respect the Switch!)
                val finalEnabledModes = if (stripModes) {
                    mutableListOf<String>()
                } else {
                    // Prefer the active session profile so we don't overwrite user toggles!
                    sessionProfile?.enabledModes?.toMutableList()
                        ?: chatProfile?.enabledModes?.toMutableList()
                        ?: mutableListOf()
                }

                // 2. Build Mode Settings Map
                val modeSettingsMap = if (stripModes) {
                    // STRIP MODE: Send empty map. MainActivity will see no settings and default to Sandbox.
                    mutableMapOf<String, Any>()
                } else {
                    // NORMAL MODE: Populate from profile
                    mutableMapOf<String, Any>().apply {
                        chatProfile?.modeSettings?.forEach { (k, v) -> this[k] = v }
                        // Add calculated RPG/Murder settings if they exist
                        rpgSettings?.let { this["rpg"] = gson.toJson(it) }
                        murderSettings?.let { this["murder"] = gson.toJson(it) }
                    }
                }

                // 3. Construct Profile
                val currentAct = if (stripModes) 0 else (rpgSettings?.currentAct ?: 0)
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

                val characterIds =
                    sessionProfile?.characterIds ?: chatProfile?.characterIds ?: emptyList()
                val cleaned =
                    cleanSessionDescriptionsAndRelationships(sessionProfile!!, cleanedAssignments)
                val finalGreeting = if (chatProfile != null) {
                    chatProfile?.firstmessage ?: ""
                } else {
                    harmonizedSlots.firstOrNull()?.greeting ?: ""
                }
                val cleanedGreeting = substitutePlaceholders(finalGreeting, cleanedAssignments)
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
                    initialGreeting = cleanedGreeting,
                    userMap = userMap,
                    userList = userlist,
                    userAssignments = userAssignments,
                    slotRoster = harmonizedSlots,
                    areas = sanitizedAreas,
                    history = emptyList(),
                    currentAreaId = null,
                    relationships = cleanedRelationships,
                    events = chatProfile?.events ?: emptyList(),
                    multiplayer = currentIsMultiplayer,
                    acts = parsedActs,
                    currentAct = currentAct,
                    enabledModes = finalEnabledModes,
                    modeSettings = modeSettingsMap,
                    characterIds = characterIds
                )

                val finalizedProfile = fixedProfile.copy(slotRoster = harmonizedSlots.toList())

                // Save session and update Firestore
                saveSessionProfile(finalizedProfile, sessionId)
                docRef.update(
                    mapOf(
                        "isBuilding" to FieldValue.delete(),
                        "started" to true,
                        "readyCount" to FieldValue.delete()
                    )
                ).await()

                withContext(Dispatchers.Main) {
                    dismissCharacterProgressDialog()
                    buildDialogShown = false
                    isProceedingToGame = true

                    val intent =
                        Intent(this@SessionLandingActivity, MainActivity::class.java).apply {
                            putExtra("SESSION_ID", sessionId)
                            putExtra("ENTRY_MODE", "CREATE")

                            val localChatProfile = chatProfile
                            val greeting = if (localChatProfile != null) {
                                localChatProfile.firstmessage ?: ""
                            } else {
                                fixedProfile.slotRoster.firstOrNull()?.greeting ?: ""
                            }
                            Log.d("greeting check", "sessionlanding greeting: $greeting ")

                            Log.d("greeting check", "sessionlanding greeting from local chat ${localChatProfile?.firstmessage}")
                            val cleanedGreeting =
                                substitutePlaceholders(greeting, cleanedAssignments)
                            intent.putExtra("GREETING", cleanedGreeting)
                            Log.d("greeting", "cleanedGreeting = $cleanedGreeting")
                        }
                    startActivity(intent)
                    finish()
                }
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

    private fun bindModeJumpButtons(sessionId: String, session: SessionProfile?) {
        val rpgBtn = findViewById<Button>(R.id.btnRpgSettings)
        val vnBtn = findViewById<Button>(R.id.btnVnSettings)
        val godToggle = findViewById<Switch>(R.id.godToggle)
        val godLayout = findViewById<LinearLayout>(R.id.godLayout)
        val godOutline = findViewById<ImageView>(R.id.godoutline)

        val modes = session?.enabledModes ?: emptyList()
        fun hasMode(k: String) = modes.any { it.equals(k, ignoreCase = true) }
        fun hasChatMode(k: String) = modes.any { it.equals(k, ignoreCase = true) }

        val rpgEnabled = hasMode("rpg")
        val vnEnabled = hasMode("vn") || hasMode("visual_novel")
        val isOneOnOne = session?.chatMode == "ONEONONE"


        rpgBtn.visibility = if (rpgEnabled) View.VISIBLE else View.GONE
        vnBtn.visibility = if (vnEnabled) View.VISIBLE else View.GONE
        if (isOneOnOne) {
            godLayout.visibility = View.GONE
            godOutline.visibility = View.GONE
        } else {
            godLayout.visibility = View.VISIBLE
            godOutline.visibility = View.VISIBLE
        }

        val selectedCharsJson = gson.toJson(
            (session?.slotRoster ?: emptyList()).map { slot ->
                CharacterProfile(
                    id = slot.baseCharacterId
                        ?: "placeholder-${slot.slotId}",   // important: base id
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
        val vnSettingsRaw = session?.modeSettings?.get("vn") as? String

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

        godToggle.setOnCheckedChangeListener(null) // Clear listener temporarily so we don't trigger it
        godToggle.isChecked = hasMode("god_mode")

        // 3. Listen for the user actually clicking the switch!
        godToggle.setOnCheckedChangeListener { _, isChecked ->
            sessionProfile?.let { currentSession ->
                // Convert to MutableList so we don't get that crash we fixed earlier
                val updatedModes = currentSession.enabledModes.toMutableList()

                if (isChecked) {
                    if (!updatedModes.contains("god_mode")) updatedModes.add("god_mode")
                } else {
                    updatedModes.remove("god_mode")
                }

                // Update the local session profile
                sessionProfile = currentSession.copy(enabledModes = updatedModes)

                // Push the update to Firestore!
                FirebaseFirestore.getInstance().collection("sessions").document(sessionId)
                    .update("enabledModes", updatedModes)
                    .addOnSuccessListener {
                        val statusStr = if (isChecked) "enabled" else "disabled"
                        Toast.makeText(this@SessionLandingActivity, "God Mode $statusStr", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this@SessionLandingActivity, "Failed to update God Mode", Toast.LENGTH_SHORT).show()
                        // Revert the switch visually if the database save fails
                        godToggle.setOnCheckedChangeListener(null)
                        godToggle.isChecked = !isChecked
                        bindModeJumpButtons(sessionId, sessionProfile) // Re-bind
                    }
            }
        }
    }

    private fun buildSlotRosterFromIds(
        orderedIds: List<String>,
        loadedProfiles: List<CharacterProfile>,
        areas: List<Area>,
        sessionId: String,
        characterToLorebooks: Map<String, List<String>> = emptyMap()
    ): List<SlotProfile> {
        val byId = loadedProfiles.associateBy { it.id }

        val defaultArea = (areas.firstOrNull()?.name ?: "Default").trim()
        val defaultLoc = (areas.firstOrNull()?.locations?.firstOrNull()?.name ?: "Entrance").trim()

        return orderedIds.mapIndexed { index, id ->
            val slotId = "slot-${index + 1}-${System.currentTimeMillis()}"

            val prof = byId[id]
            val extraLore = characterToLorebooks[id] ?: emptyList()

            if (prof != null) {
                // ONE CLEAN LINE!
                transferWardrobeToSession(sessionId, slotId, prof.id)

                prof.toSlotProfile(
                    relationships = emptyList(),
                    slotId = slotId,
                    extraLorebookIds = extraLore
                ).copy(
                    lastActiveArea = defaultArea,
                    lastActiveLocation = defaultLoc
                )
            } else {
                makePlaceholderSlot(index + 1).copy(
                    slotId = slotId,
                    lastActiveArea = defaultArea,
                    lastActiveLocation = defaultLoc,
                    name = "character${index + 1}"
                )
            }
        }
    }

    private fun transferWardrobeToSession(sessionId: String, slotId: String, baseCharacterId: String, type: String = "character") {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = FirebaseFirestore.getInstance()

                // Route to the correct collection!
                val collectionName = if (type == "persona") "personas" else "characters"

                // 1. Fetch ALL outfit documents for this character/persona
                val baseWardrobeDocs = db.collection(collectionName)
                    .document(baseCharacterId)
                    .collection("wardrobes")
                    .get()
                    .await()

                if (!baseWardrobeDocs.isEmpty) {
                    val batch = db.batch()
                    val sessionWardrobeRef = db.collection("sessions").document(sessionId).collection("wardrobes")

                    // 2. Copy each one to the Session
                    for (wardrobeDoc in baseWardrobeDocs.documents) {
                        val outfit = wardrobeDoc.toObject(Outfit::class.java) ?: continue

                        // Create a unique ID for the session wardrobe: "baseId_outfitName"
                        val safeOutfitName = outfit.name.replace("[^a-zA-Z0-9]".toRegex(), "_")
                        val sessionDocRef = sessionWardrobeRef.document("${baseCharacterId}_$safeOutfitName")

                        // Save the outfit, but tack on the baseCharacterId so we can search for it!
                        val dataToSave = mapOf(
                            "baseCharacterId" to baseCharacterId,
                            "outfit" to outfit
                        )
                        batch.set(sessionDocRef, dataToSave)
                    }

                    batch.commit().await()
                }
            } catch(e: Exception) {
                Log.e("Wardrobe", "Failed to transfer wardrobe math for $baseCharacterId", e)
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

        val prompt = PromptBuilder.buildMysteryTimelineTextPrompt(
            slots,
            rpgSettings,
            murderSettings,
            sessionProfile
        )

        // 1. Get the bucket (and return an empty bucket if it crashes)
        val apiResult = try {
            Facilitator.callActivationAI(prompt, BuildConfig.OPENAI_API_KEY, "nvidia",
                null, null, null)
        } catch (e: Exception) {
            Log.e("MysteryTimeline", "AI call failed", e)
            com.albirich.RealmsAI.ai.AiResponseData(null, 0L) // Return an empty bucket
        }

        // 2. Open the bucket and pull out the text
        val raw = apiResult.content ?: ""
        val tokens = apiResult.totalTokens

        // (Optional) Since you pay for these tokens too, you can log them!
        // logMonthlyAnalytics("nvidia", sessionProfile.chatMode, sessionProfile.chatId, if (raw.isEmpty()) "timeout" else "success", tokens)

        // Now 'raw' is a standard String again, so these functions work perfectly!
        Log.d("timeline", "raw: ${raw.take(600)}")

        // Extract JSON safely
        val jsonStart = raw.indexOf('{')
        // ... (the rest of your code stays exactly the same!)
        val jsonEnd = raw.lastIndexOf('}')
        val json = if (jsonStart != -1 && jsonEnd > jsonStart) raw.substring(
            jsonStart,
            jsonEnd + 1
        ) else raw

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
        charToLocationMap: Map<String, String>,
        sessionProfile: SessionProfile
    ): SlotProfile = withContext(Dispatchers.IO) {
        Log.d("session_start", "harmonizing ${profile.name}")
        val roleForThisChar = rpgSettings
            ?.characters
            ?.firstOrNull { it.characterId == profile.id }
            ?.role

        // --- 1. Format Available Locations for the AI ---
        val availableLocationsStr = allAreas.joinToString("\n") { area ->
            "- Area: \"${area.name}\" (Contains: ${area.locations.joinToString { "\"${it.name}\"" }})"
        }

        val simplifiedRosterStr = sessionProfile.slotRoster.joinToString(separator = "\n\n") { slot ->
            """
    - Name: ${slot.name}
      Summary: ${slot.summary}
      Personality: ${slot.personality}
      Private Description: ${slot.privateDescription}
    """.trimIndent()
        }

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

        // --- 2. Update Prompt with Location Instructions ---
        val aiPrompt = """
    You are an expert RPG character profile editor and summarizer.
    $murderContext
    
    AVAILABLE LOCATIONS:
    $availableLocationsStr

    Given the following:
    - The character's full profile (name, summary, personality, private description, physical description, abilities, backstory, etc.).
    - The full, correct list of this character's session relationships (by toName, type, etc.).
    and the session info:
    description ${sessionProfile.sessionDescription}
    secret description ${sessionProfile.secretDescription}
    Other characters in this session:
    $simplifiedRosterStr
    
    Your job:
    1. Rewrite the character's backstory as a series of short memories, each with 4-10 descriptive tags (characters, themes, events, etc).
       - add any new info from the session info
       - make up information about how they relate to some of the other characters in the session.
    2. Write a dense, factual public directory summary (AS CLOSE TO 200 CHARACTERS AS POSSIBLE). 
       - Make a condensed, but thorough summary to describe important things, included but not limited to:
            - physical info
            - what theyre known for
            - nicknames (if any)
            - any factions they belong to.
       - Do NOT use filler words or flowery language. 
    3. If the character fits logically into one of the AVAILABLE LOCATIONS provided above, assign them that "area" and "location". 
       - Choose based on their job, personality, or role (e.g., a Maid goes to the Kitchen, a Guard goes to the Gate).
       - You MUST pick exact names from the list provided.
    4. Rewrite their background using the new session info, any contradictions use the session profile over the characters previous background. 
       - up to 1000 characters
       - make them fit in the world from the session description
    
    Return only a single JSON object MUST have these fields:
    {
      "memories": [
        { "tags": ["sasuke", "childhood"], "text": "Naruto met Sasuke in the academy and they became rivals." },
        { "tags": ["teamwork", "sakura", "sasuke"], "text": "The three learned to work together on their first mission." }
      ],
      "condensed_summary": "<short vivid summary>",
      "assigned_area": "Area Name From List",
      "assigned_location": "Location Name From List",
      "background": "a new background that fits the sessionprofile"
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

        var modelToUse = "openai" // Ensure a default is set for the sfw check!

        // 1. Get the BUCKET
        val apiResult = if (sessionProfile.sfwOnly == true) {
            Facilitator.callOpenAiApi(
                prompt = aiPrompt,
                BuildConfig.OPENAI_API_KEY,
                modelToUse,
                null, null, null
            )
        } else {
            modelToUse = "mistral_small"
            Facilitator.callMixtralApi(
                aiPrompt,
                BuildConfig.MIXTRAL_API_KEY,
                modelToUse,
                null, null, null
            )
        }

        // 2. Open the BUCKET and get the String!
        val aiResponse = apiResult.content ?: ""
        val tokensUsed = apiResult.totalTokens

        // (Optional) Log the tokens since Harmonization is a massive prompt that costs you money!
        // logMonthlyAnalytics(modelToUse, "SYSTEM", sessionProfile.sessionId, if(aiResponse.isEmpty()) "timeout" else "success", tokensUsed)

        Log.d("session_start", "${profile.name} has been harmonized. summary ${aiResponse}")

        // --- 3. Update Result Class to capture location ---
        data class HCResult(
            val memories: List<TaggedMemory>,
            val condensed_summary: String,
            val assigned_area: String?,
            val assigned_location: String?,
            val background: String?
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
        val actualCurrentOutfit =
            if (profile.currentOutfit.isNullOrBlank()) defaultOutfit else profile.currentOutfit
        val activeOutfitObj = outfits.find { it.name == actualCurrentOutfit } ?: outfits.firstOrNull()
        val actualCurrentPose = activeOutfitObj?.poseSlots?.firstOrNull()?.name ?: ""

        // --- 4. Resolve Location Logic ---
        // A. Check for manually assigned location from Map
        val assignedAreaId = charToAreaMap[profile.id]
        val assignedLocationId = charToLocationMap[profile.id]

        val areaObj = allAreas.find { it.id == assignedAreaId }
        val locationObj = areaObj?.locations?.find { it.id == assignedLocationId }

        val manualAreaName = areaObj?.name
        val manualLocationName = locationObj?.name

        // B. Fallback: Use AI suggestion if manual is missing
        val suggestedArea = if (!manualAreaName.isNullOrBlank()) manualAreaName else result.assigned_area?.trim()
        val suggestedLocation = if (!manualLocationName.isNullOrBlank()) manualLocationName else result.assigned_location?.trim()

        // C. THE SAFETY NET: Validate against the master list to catch hallucinations or nulls
        val isValidArea = allAreas.any { it.name == suggestedArea }
        val isValidLocation = allAreas.find { it.name == suggestedArea }?.locations?.any { it.name == suggestedLocation } ?: false

        val (finalArea, finalLocation) = if (isValidArea && isValidLocation) {
            suggestedArea to suggestedLocation
        } else {
            // Redirect to "Unknown" if the AI made up a room or the data is null
            "Unknown" to "Unknown"
        }

        val slotId = UUID.randomUUID().toString()

        // --- 4.5 NEW RAG SYSTEM: Embed and Save Background Memories ---
        val db = FirebaseFirestore.getInstance()
        val memoryRef = db.collection("sessions")
            .document(sessionProfile.sessionId)
            .collection("character_memories")

        backgroundMemories.forEach { mem ->
            try {
                // Embed the text so it's searchable
                val vector = Director.getEmbedding(mem.text, BuildConfig.OPENAI_API_KEY)

                // Link it to this specific character and add the vector
                val memToSave = mem.copy(
                    slotId = slotId,
                    embedding = vector
                )

                // Push directly to the standalone subcollection
                memoryRef.document(memToSave.id).set(memToSave)
            } catch(e: Exception) {
                Log.e("Harmonize", "Failed to save background memory to RAG", e)
            }
        }

        val matchingRpgCharacter = rpgSettings?.characters?.find { it.characterId == profile.id }

        fun calcHp(stats: Map<String, Int>, rpgClass: String): Int {
            val base = 10
            val bonus = (stats["resolve"] ?: 0) + (stats["strength"] ?: 0)
            return base + bonus
        }

        fun calcMaxHp(stats: Map<String, Int>, rpgClass: String): Int = calcHp(stats, rpgClass)

        fun calcDefense(stats: Map<String, Int>, rpgClass: String): Int {
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

        val hp = matchingRpgCharacter?.hp
            ?: calcHp(rpgStats, matchingRpgCharacter?.characterClass?.name ?: "")
        val maxHp = matchingRpgCharacter?.maxHp
            ?: calcMaxHp(rpgStats, matchingRpgCharacter?.characterClass?.name ?: "")
        val defense = matchingRpgCharacter?.defense
            ?: calcDefense(rpgStats, matchingRpgCharacter?.characterClass?.name ?: "")
        val links: List<CharacterLink> = profile.linkedToMap.values.flatten()

        val vnSettingsJson = sessionProfile?.modeSettings?.get("vn") as? String
        val vnSettings = if (!vnSettingsJson.isNullOrBlank())
            Gson().fromJson(vnSettingsJson, VNSettings::class.java)
        else null

        val vnRelMap = mutableMapOf<String, ModeSettings.VNRelationship>()
        vnSettings?.characterBoards
            ?.get(currentSlotKey)
            ?.forEach { (toSlotKey, rel) ->
                if (rel.fromSlotKey != currentSlotKey || rel.toSlotKey != toSlotKey) {
                    vnRelMap[toSlotKey] =
                        rel.copy(fromSlotKey = currentSlotKey, toSlotKey = toSlotKey)
                } else {
                    vnRelMap[toSlotKey] = rel
                }
            }

        sessionProfile?.modeSettings?.set("vn", Gson().toJson(vnSettings))

        val roleName = matchingRpgCharacter?.role?.name ?: ""

        Log.d("session_start", "done harmonizing ${profile.name}")
        // Return the full SlotProfile with final Area/Location
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
            pose = actualCurrentPose,
            sfwOnly = profile.sfwOnly,
            relationships = sessionRelationships,
            lastActiveArea = finalArea,
            lastActiveLocation = finalLocation,
            lastSynced = Timestamp.now(),
            profileType = profile.profileType,
            typing = false,
            backstory = result.background ?: profile.backstory,
            // memories = backgroundMemories,
            rpgClass = matchingRpgCharacter?.characterClass?.name ?: "",
            stats = rpgStats,
            equipment = matchingRpgCharacter?.equipment ?: emptyList(),
            hp = hp,
            maxHp = maxHp,
            defense = defense,
            linkedTo = links,
            vnRelationships = vnRelMap,
            hiddenRoles = roleName,
            exampleDialogue = profile.exampleDialogue,
            moreInfo = moreInfo.takeIf { it.isNotBlank() },
            lorebookIds = profile.lorebookIds ?: emptyList()
        )

    }

    private fun updateDescriptionsFromTemplate(profile: SessionProfile, onComplete: (SessionProfile) -> Unit) {
        val chatId = profile.chatId
        if (chatId.isBlank() || profile.chatMode == "ONEONONE") {
            onComplete(profile)
            return
        }

        // Use cached template if we already have it
        if (chatProfile != null) {
            onComplete(applyTemplate(profile, chatProfile!!))
            return
        }

        // Fetch template if it's missing (e.g., loaded from Hub history)
        FirebaseFirestore.getInstance().collection("chats").document(chatId).get()
            .addOnSuccessListener { doc ->
                val template = doc.toObject(ChatProfile::class.java)
                if (template != null) {
                    chatProfile = template
                    onComplete(applyTemplate(profile, template))
                } else {
                    onComplete(profile)
                }
            }
            .addOnFailureListener {
                onComplete(profile)
            }
    }

    private fun applyTemplate(profile: SessionProfile, template: ChatProfile): SessionProfile {
        val cleanedAssignments = profile.slotRoster.mapIndexed { idx, slot ->
            "character${idx + 1}" to slot.name
        }.toMap()

        val cleanDesc = substitutePlaceholders(template.description, cleanedAssignments)
        val cleanSecretDesc = substitutePlaceholders(template.secretDescription, cleanedAssignments)
        val cleanGreeting = substitutePlaceholders(template.firstmessage ?: "", cleanedAssignments)

        return profile.copy(
            sessionDescription = cleanDesc,
            secretDescription = cleanSecretDesc,
            sessionSummary = cleanDesc, // Keeps the summary in sync
            initialGreeting = cleanGreeting
        )
    }

    private fun showCharacterProgressDialog(total: Int) {
        val view = layoutInflater.inflate(R.layout.dialog_progress_character, null)
        progressTextView = view.findViewById(R.id.progressText)
        progressBar = view.findViewById(R.id.progressBar)
        progressBar?.max = total
        progressBar?.progress = 0
        progressTextView?.text = "Processing character 0/$total. \nThis will take a while.\nPlease don’t close the app."
        progressDialog = AlertDialog.Builder(this)
            .setTitle("Preparing Session")
            .setView(view)
            .setCancelable(false)
            .show()
    }

    private fun updateCharacterProgress(current: Int, total: Int) {
        progressBar?.progress = current
        progressTextView?.text =
            "Updating characters for the session: $current/$total done.\nThis will take a while.\nPlease don’t close the app."
    }

    private fun dismissCharacterProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
        progressBar = null
        progressTextView = null
    }

    // -- Helper: display a loaded session
    private fun displaySession(session: SessionProfile) {
        // Prefer userList, fallback to userMap keys
        val playerIds = session.userList.takeIf { it.isNotEmpty() }
            ?: session.userMap.keys.toList()

        if (playerRecycler.adapter == null) {
            // First time setup
            playerRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            playerRecycler.adapter = PlayerSlotAdapter(playerIds,
                onUserClick = { clickedUserId ->

                    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return@PlayerSlotAdapter
                    val isHost = session.userList.firstOrNull() == currentUserId
                    val isSelf = clickedUserId == currentUserId

                    // 1. Build the dynamic menu
                    val menuItems = mutableListOf("Profile")

                    if (!isSelf) {
                        menuItems.add("Add Friend")
                        if (isHost) {
                            menuItems.add("Remove Player")
                            menuItems.add("Promote to Host")
                        }
                    }

                    // 2. Show the Dialog
                    AlertDialog.Builder(this)
                        .setTitle("Player Options")
                        .setItems(menuItems.toTypedArray()) { _, which ->
                            when (menuItems[which]) {
                                "Profile" -> {
                                    val intent = Intent(this, DisplayProfileActivity::class.java)
                                    intent.putExtra("USER_ID", clickedUserId)
                                    startActivity(intent)
                                }

                                "Add Friend" -> {
                                    sendFriendRequest(fromId = currentUserId, toId = clickedUserId)
                                }

                                "Remove Player" -> {
                                    val db = FirebaseFirestore.getInstance()
                                    val currentSession = sessionProfile ?: return@setItems

                                    // Strip them from the lists
                                    val newUserList = currentSession.userList.toMutableList().apply { remove(clickedUserId) }
                                    val newUserMap = currentSession.userMap.toMutableMap().apply { remove(clickedUserId) }

                                    db.collection("sessions").document(currentSession.sessionId)
                                        .update(
                                            mapOf(
                                                "userList" to newUserList,
                                                "userMap" to newUserMap
                                            )
                                        )
                                        .addOnSuccessListener {
                                            Toast.makeText(this, "Player removed.", Toast.LENGTH_SHORT).show()
                                        }
                                }

                                "Promote to Host" -> {
                                    val db = FirebaseFirestore.getInstance()
                                    val currentSession = sessionProfile ?: return@setItems

                                    // Extract and move them to Index 0
                                    val newUserList = currentSession.userList.toMutableList()
                                    newUserList.remove(clickedUserId)
                                    newUserList.add(0, clickedUserId)

                                    db.collection("sessions").document(currentSession.sessionId)
                                        .update("userList", newUserList)
                                        .addOnSuccessListener {
                                            Toast.makeText(this, "Host transferred!", Toast.LENGTH_SHORT).show()
                                            // The Lobby Listener will automatically hide your "Start Session" button!
                                        }
                                }
                            }
                        }
                        .show()
                })
        } else {
            // Live-update if a friend joins the lobby!
            (playerRecycler.adapter as PlayerSlotAdapter).setUserIds(playerIds)
        }
        // Character Roster
        charRecycler.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 4)
        charRecycler.adapter = CharacterRowAdapter(
            session.slotRoster.map { it.toCharacterProfile() },
            onClick = { character ->
                val context = this

                // 1. Determine if this specific character needs an update
                val needsUpdate = needsUpdateIds.contains(character.id)

                // 2. Build the menu list dynamically
                val menuItems = mutableListOf("Profile", "Replace", "Remove", "Add to Collection")
                if (needsUpdate) {
                    menuItems.add("Update")
                }

                AlertDialog.Builder(context)
                    .setTitle(character.name)
                    .setItems(menuItems.toTypedArray()) { _, which ->
                        // 3. Match the clicked string to ensure logic stays correct regardless of index
                        when (menuItems[which]) {
                            "Profile" -> {
                                if (character.private == true) {
                                    Toast.makeText(
                                        context,
                                        "This character is private.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    context.startActivity(
                                        Intent(context, CharacterProfileActivity::class.java)
                                            .putExtra("characterId", character.id)
                                    )
                                }
                            }

                            "Replace" -> {
                                val slot =
                                    sessionProfile?.slotRoster?.firstOrNull { it.baseCharacterId == character.id }
                                        ?: sessionProfile?.slotRoster?.firstOrNull { "placeholder-${it.slotId}" == character.id }

                                if (slot == null) return@setItems

                                AlertDialog.Builder(context)
                                    .setTitle("Character Replacement")
                                    .setItems(
                                        arrayOf(
                                            "Full Replacement (Fresh Start)",
                                            "Substitution (Keep Memories & History)"
                                        )
                                    ) { _, choice ->
                                        val intent = Intent(context, CharacterSelectionActivity::class.java).apply {
                                            putExtra("TEMP_SELECTION_MODE", true)
                                            putExtra("MAX_SELECT", 1)
                                            putExtra("replaceSlotId", slot.slotId)
                                            putExtra("oldBaseCharacterId", slot.baseCharacterId ?: "")
                                            putExtra("SUBSTITUTION_MODE", choice == 1)
                                        }
                                        context.startActivityForResult(intent, 103)
                                    }.show()
                            }

                            "Remove" -> {
                                removeSlotForCharacter(character.id)
                            }

                            "Add to Collection" -> {
                                checkPremiumStatus {
                                    if (character.private == true) {
                                        Toast.makeText(
                                            context,
                                            "This character is private.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        saveCharacterAsUser(character)
                                    }
                                }
                            }

                            "Update" -> {
                                val delta = pendingUpdateDeltas[character.id]
                                if (delta != null) {
                                    showCharacterUpdateDialog(character.id, delta)
                                } else {
                                    // Fallback if delta isn't found
                                    applyCharacterUpdate(
                                        character.id,
                                        updateVisuals = true,
                                        updatePersonality = true,
                                        updateSecret = true,
                                        updateLinks = true
                                    )
                                }
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

    private fun sendFriendRequest(fromId: String, toId: String) {
        val usersRef = db.collection("users")
        val fromDoc = usersRef.document(fromId)
        val toDoc = usersRef.document(toId)
        toDoc.get().addOnSuccessListener { doc ->
            val profile = doc.toObject(UserProfile::class.java)
            if (profile != null) {
                when {
                    profile.friends.contains(fromId) -> {
                        Toast.makeText(this, "You are already friends!", Toast.LENGTH_SHORT).show()
                    }
                    profile.pendingFriends.contains(fromId) -> {
                        Toast.makeText(this, "Request already sent!", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        // Add fromId to pendingFriends in recipient profile
                        toDoc.update("pendingFriends", profile.pendingFriends + fromId)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Friend request sent!", Toast.LENGTH_SHORT).show()
                                sendFriendRequestMessage(fromId, toId)
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Failed to send friend request.", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
            } else {
                Toast.makeText(this, "User not found.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendFriendRequestMessage(fromId: String, toId: String) {
        val db = FirebaseFirestore.getInstance()
        val messagesCollection = db.collection("users").document(toId).collection("messages")

        val newMessageRef = messagesCollection.document()
        db.collection("users").document(fromId).get()
            .addOnSuccessListener { fromDoc ->
                val senderName = fromDoc.getString("name") ?: "(unknown)"
                val message = DirectMessage(
                    id = newMessageRef.id,
                    from = fromId,
                    to = toId,
                    text = "You have a new friend request from: $senderName",
                    timestamp = com.google.firebase.Timestamp.now(),
                    status = MessageStatus.UNOPENED,
                    type = MessageType.FRIEND_REQUEST
                )
                newMessageRef.set(message)
                    .addOnSuccessListener {
                        // Optional: Toast or log here if you want
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to notify user: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
    }

    private fun checkPremiumStatus(onPremium: () -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                if (doc.getBoolean("isPremium") == true) {
                    onPremium()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Premium Feature")
                        .setMessage("Saving characters to your collection allows you to edit and customize them.\n\nThis is a Premium feature.")
                        .setPositiveButton("Upgrade") { _, _ ->
                            startActivity(Intent(this, UpgradeActivity::class.java))
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
    }

    private fun saveCharacterAsUser(character: CharacterProfile) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val newChar = character.copy(
            id = UUID.randomUUID().toString(),
            author = userId,
            privateDescription = "",
            originalId = character.id
        )
        db.collection("characters").document(newChar.id)
            .set(newChar)
            .addOnSuccessListener {
                Toast.makeText(this, "Character saved to your collection!", Toast.LENGTH_SHORT)
                    .show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save character.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun shiftVNSettingsOnRemove(vnJson: String?, removedIndex: Int): String? {
        if (vnJson.isNullOrBlank()) return null
        val vn = try { Gson().fromJson(vnJson, ModeSettings.VNSettings::class.java) } catch (e: Exception) { return vnJson }

        val newBoards = mutableMapOf<String, MutableMap<String, ModeSettings.VNRelationship>>()

        vn.characterBoards.forEach { (oldFromKey, boardByToKey) ->
            // Extract the integer index (e.g., "character4" -> 3)
            val oldFromIdx = oldFromKey.removePrefix("character").toIntOrNull()?.minus(1) ?: return@forEach

            // 1. Skip the removed character's outgoing relationships entirely
            if (oldFromIdx == removedIndex) return@forEach

            // 2. Shift the 'from' key down if it was AFTER the removed character
            val newFromKey = if (oldFromIdx > removedIndex) "character${oldFromIdx}" else oldFromKey
            newBoards[newFromKey] = mutableMapOf()

            boardByToKey.forEach { (oldToKey, rel) ->
                val oldToIdx = oldToKey.removePrefix("character").toIntOrNull()?.minus(1) ?: return@forEach

                // 3. Skip any relationships pointing TO the removed character
                if (oldToIdx == removedIndex) return@forEach

                // 4. Shift the 'to' key down if it was AFTER the removed character
                val newToKey = if (oldToIdx > removedIndex) "character${oldToIdx}" else oldToKey

                // 5. Update the internal target keys inside the relationship levels!
                val newLevels = rel.levels.map { lvl ->
                    lvl.copy(targetSlotKey = newToKey)
                }.toMutableList()

                newBoards[newFromKey]!![newToKey] = rel.copy(
                    fromSlotKey = newFromKey,
                    toSlotKey = newToKey,
                    levels = newLevels
                )
            }
        }

        return Gson().toJson(vn.copy(characterBoards = newBoards))
    }

    private fun wipeVNSettingsForSlot(vnJson: String?, wipedIndex: Int): String? {
        if (vnJson.isNullOrBlank()) return null
        val vn = try { Gson().fromJson(vnJson, ModeSettings.VNSettings::class.java) } catch (e: Exception) { return vnJson }

        val wipedSlotKey = "character${wipedIndex + 1}"
        val newBoards = vn.characterBoards.toMutableMap()

        // 1. Delete their feelings toward everyone else
        newBoards.remove(wipedSlotKey)

        // 2. Delete everyone else's feelings toward them
        newBoards.keys.forEach { fromKey ->
            val updatedBoard = newBoards[fromKey]?.toMutableMap()
            if (updatedBoard != null) {
                updatedBoard.remove(wipedSlotKey)
                newBoards[fromKey] = updatedBoard
            }
        }

        return Gson().toJson(vn.copy(characterBoards = newBoards))
    }

    private fun removeSlotForCharacter(baseCharacterId: String) {
        val sessionId = lobbySessionId ?: return
        val before = sessionProfile?.slotRoster.orEmpty()

        // 1. Find the INDEX and the target! (Replaced firstOrNull)
        val removedIndex = before.indexOfFirst { it.baseCharacterId == baseCharacterId }
        val target = before.getOrNull(removedIndex)

        if (target == null || removedIndex == -1) {
            Toast.makeText(this, "Couldn't find a matching slot.", Toast.LENGTH_SHORT).show()
            return
        }

        val after = before.filterNot { it.slotId == target.slotId }
        if (after.size == before.size) {
            Toast.makeText(this, "No change (slot not found by id).", Toast.LENGTH_SHORT).show()
            return
        }

        // --- 2. VN SHIFT LOGIC ---
        val currentVnJson = sessionProfile?.modeSettings?.get("vn") as? String
        val shiftedVnJson = shiftVNSettingsOnRemove(currentVnJson, removedIndex)

        val newModeSettings = sessionProfile?.modeSettings?.toMutableMap() ?: mutableMapOf()
        if (shiftedVnJson != null) {
            newModeSettings["vn"] = shiftedVnJson
        }
        // -------------------------

        // 3. Add the shifted modeSettings to the temp profile!
        val tempProfile = sessionProfile?.copy(
            slotRoster = after,
            modeSettings = newModeSettings
        ) ?: return

        updateDescriptionsFromTemplate(tempProfile) { finalProfile ->
            FirebaseFirestore.getInstance()
                .collection("sessions").document(sessionId)
                .update(
                    mapOf(
                        "slotRoster" to finalProfile.slotRoster,
                        "modeSettings" to finalProfile.modeSettings, // <-- CRITICAL: Saves the shifted VN JSON!
                        "sessionDescription" to finalProfile.sessionDescription,
                        "secretDescription" to finalProfile.secretDescription,
                        "initialGreeting" to finalProfile.initialGreeting,
                        "sessionSummary" to finalProfile.sessionSummary
                    )
                )
                .addOnSuccessListener {
                    sessionProfile = finalProfile
                    displaySession(finalProfile) // Update the textviews!
                    Toast.makeText(this, "Character removed.", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to remove: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    fun cleanSessionDescriptionsAndRelationships(
        sessionProfile: SessionProfile,
        slotAssignments: Map<String, String>
    ): CleanedData {
        val cleanedDescription =
            substitutePlaceholders(sessionProfile.sessionDescription, slotAssignments)
        val cleanedSecretDescription =
            substitutePlaceholders(sessionProfile.secretDescription!!, slotAssignments)
        val cleanedRelationships =
            replaceRelationshipPlaceholders(sessionProfile.relationships, slotAssignments)
        return CleanedData(cleanedDescription, cleanedSecretDescription, cleanedRelationships)
    }

    // --- Firestore: load personas
    private fun loadPersonasFromFirestore(onLoaded: (List<PersonaProfile>) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("personas")
            .whereEqualTo("author", FirebaseAuth.getInstance().currentUser?.uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val personas =
                    snapshot.documents.mapNotNull { it.toObject(PersonaProfile::class.java) }
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
            101 -> { // Character/Persona selection result
                val ids = data.getStringArrayListExtra("selectedCharacterIds")
                if (ids.isNullOrEmpty()) return

                val rawId = ids[0]

                // Determine type based on prefix (CharacterSelectionActivity adds "persona:" to personas)
                val type = if (rawId.startsWith("persona:")) "persona" else "character"
                val id = rawId.removePrefix("persona:")

                if (sessionProfile?.slotRoster?.size ?: 0 < 50) {
                    if (sessionProfile?.slotRoster?.any { it.baseCharacterId == id } == true) {
                        Toast.makeText(
                            this,
                            "That character/persona is already in the session.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    }

                    val collection = if (type == "character") "characters" else "personas"
                    FirebaseFirestore.getInstance().collection(collection).document(id).get()
                        .addOnSuccessListener { doc ->
                            val profileRelationships = when (type) {
                                "character" -> doc.toObject(CharacterProfile::class.java)?.relationships
                                    ?: emptyList()

                                "persona" -> doc.toObject(PersonaProfile::class.java)?.relationships
                                    ?: emptyList()

                                else -> emptyList()
                            }

                            // Merge relationships (avoid duplicates)
                            val updatedRelationships =
                                (sessionProfile?.relationships ?: emptyList()).toMutableList()
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
                                    addProfileToSlotRoster(type, id)
                                }
                        }
                } else {
                    Toast.makeText(
                        this,
                        "Character/Persona slot limit reached.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            102 -> {
                val relationshipsJson = data.getStringExtra("RELATIONSHIPS_JSON") ?: "[]"
                relationships = Gson().fromJson(relationshipsJson, Array<Relationship>::class.java)
                    .toMutableList()
                val sessionId = lobbySessionId
                if (sessionId != null) {
                    FirebaseFirestore.getInstance().collection("sessions")
                        .document(sessionId)
                        .update("relationships", relationships)
                }
            }

            103 -> {
                val isSubMode = data.getBooleanExtra("SUBSTITUTION_MODE", false)
                val replaceSlotId = data.getStringExtra("replaceSlotId") ?: return
                val oldBaseCharacterId = data.getStringExtra("oldBaseCharacterId")

                // --- THE NEW PARSING LOGIC ---
                val ids = data.getStringArrayListExtra("selectedCharacterIds")
                if (ids.isNullOrEmpty()) return

                val rawId = ids[0]
                val type = if (rawId.startsWith("persona:")) "persona" else "character"
                val id = rawId.removePrefix("persona:")
                // -----------------------------

                val oldName = sessionProfile?.slotRoster?.find { it.slotId == replaceSlotId }?.name ?: ""
                val sessionId = lobbySessionId ?: return
                val db = FirebaseFirestore.getInstance()

                db.collection(if (type == "character") "characters" else "personas").document(id)
                    .get()
                    .addOnSuccessListener { doc ->

                        // ⬇️ Make the types explicit + null-safe
                        val newProfile: CharacterProfile
                        val newRelationships: List<Relationship>

                        when (type) {
                            "character" -> {
                                val prof = doc.toObject(CharacterProfile::class.java)
                                    ?: return@addOnSuccessListener
                                newProfile = prof
                                newRelationships = prof.relationships ?: emptyList()
                            }

                            "persona" -> {
                                val persona = doc.toObject(PersonaProfile::class.java)
                                    ?: return@addOnSuccessListener
                                val prof = persona.toCharacterProfile()
                                newProfile = prof
                                newRelationships = persona.relationships ?: emptyList()
                            }

                            else -> return@addOnSuccessListener
                        }
                        val oldRoster = sessionProfile?.slotRoster ?: return@addOnSuccessListener
                        val oldSlot = oldRoster.firstOrNull { it.slotId == replaceSlotId }
                            ?: return@addOnSuccessListener

                        // Optional: prevent duplicates except for the slot we’re replacing
                        if (oldRoster.any { it.baseCharacterId == newProfile.id && it.slotId != replaceSlotId }) {
                            Toast.makeText(
                                this,
                                "That character is already in the session.",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@addOnSuccessListener
                        }

                        val newSlot = if (isSubMode) {
                            // --- SILENT SUBSTITUTION MODE ---
                            oldSlot.copy(
                                baseCharacterId = newProfile.id,
                                name = newProfile.name,
                                personality = newProfile.personality ?: "",
                                summary = newProfile.summary ?: "",
                                privateDescription = newProfile.privateDescription ?: "",
                                avatarUri = newProfile.avatarUri ?: "",
                                exampleDialogue = newProfile.exampleDialogue,
                                userReplaced = true,
                                linkedTo = newProfile.linkedToMap.values.flatten() // <--- ADD THIS
                            )
                        } else {
                            saveWardrobeToSubcollection(sessionId, replaceSlotId, newProfile.id, newProfile.outfits ?: emptyList())
                            oldSlot.copy(
                                baseCharacterId = newProfile.id,
                                name = newProfile.name,
                                memories = emptyList(),
                                summary = newProfile.summary ?: "",
                                personality = newProfile.personality ?: "",
                                privateDescription = newProfile.privateDescription ?: "",
                                greeting = newProfile.greeting ?: "",
                                avatarUri = newProfile.avatarUri ?: "",
                                outfits = newProfile.outfits ?: emptyList(),
                                currentOutfit = newProfile.currentOutfit ?: "",
                                sfwOnly = newProfile.sfwOnly,
                                relationships = emptyList(),
                                userReplaced = true,
                                isPlaceholder = false,
                                linkedTo = newProfile.linkedToMap.values.flatten()
                            )
                        }

                        val updatedRelationships =
                            (sessionProfile?.relationships ?: emptyList()).toMutableList()
                        if (!oldBaseCharacterId.isNullOrBlank()) {
                            updatedRelationships.removeAll { it.fromId == oldBaseCharacterId }
                        }
                        // ✅ newRelationships is a List<Relationship> now, so map() is available
                        updatedRelationships += newRelationships.map { it.copy(fromId = newProfile.id) }

                        // --- VN WIPE LOGIC FOR FULL RESET ---
                        val replaceIndex = oldRoster.indexOfFirst { it.slotId == replaceSlotId }
                        val newModeSettings = sessionProfile?.modeSettings?.toMutableMap() ?: mutableMapOf()

                        if (!isSubMode && replaceIndex != -1) {
                            val currentVnJson = newModeSettings["vn"] as? String
                            val wipedVnJson = wipeVNSettingsForSlot(currentVnJson, replaceIndex)
                            if (wipedVnJson != null) newModeSettings["vn"] = wipedVnJson
                        }
                        // ------------------------------------

                        val newRoster = oldRoster.map { if (it.slotId == replaceSlotId) newSlot else it }

                        val updates = mutableMapOf<String, Any>("slotRoster" to newRoster)

                        if (isSubMode && oldName.isNotBlank() && newProfile.name != oldName) {
                            val migratedHistory = sessionProfile?.history?.map { msg ->
                                msg.copy(
                                    text = msg.text.replace(
                                        oldName,
                                        newProfile.name,
                                        ignoreCase = true
                                    )
                                )
                            }
                            if (migratedHistory != null) updates["history"] = migratedHistory
                        }

                        val tempProfile = sessionProfile?.copy(slotRoster = newRoster) ?: return@addOnSuccessListener

                        updateDescriptionsFromTemplate(tempProfile) { finalProfile ->
                            val updates = mutableMapOf<String, Any>(
                                "slotRoster" to finalProfile.slotRoster,
                                "relationships" to updatedRelationships,
                                "sessionDescription" to finalProfile.sessionDescription,
                                "secretDescription" to (finalProfile.secretDescription ?: ""),
                                "initialGreeting" to (finalProfile.initialGreeting ?: ""),
                                "sessionSummary" to finalProfile.sessionSummary
                            )

                            // (Keep your existing history-migration code here...)
                            if (isSubMode && oldName.isNotBlank() && newProfile.name != oldName) {
                                val migratedHistory = sessionProfile?.history?.map { msg ->
                                    msg.copy(text = msg.text.replace(oldName, newProfile.name, ignoreCase = true))
                                }
                                if (migratedHistory != null) updates["history"] = migratedHistory
                            }

                            FirebaseFirestore.getInstance().collection("sessions")
                                .document(sessionId)
                                .update(updates)
                                .addOnSuccessListener {
                                    sessionProfile = finalProfile
                                    sessionProfile?.relationships = updatedRelationships
                                    displaySession(finalProfile) // Update the textviews!
                                    Toast.makeText(this, "Character replaced!", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
            }

            REQ_RPG_SETTINGS -> {
                val rpgJson = data.getStringExtra("RPG_SETTINGS_JSON")
                val murderJson = data.getStringExtra("MURDER_SETTINGS_JSON")
                val sessionId = lobbySessionId ?: return

                val updates = mutableMapOf<String, Any>()
                rpgJson?.let { updates["modeSettings.rpg"] = it }
                murderJson?.let { updates["modeSettings.murder"] = it }

                if (updates.isNotEmpty()) {
                    FirebaseFirestore.getInstance()
                        .collection("sessions").document(sessionId)
                        .update(updates)
                        .addOnSuccessListener {
                            // local mirror
                            rpgJson?.let { sessionProfile?.modeSettings?.set("rpg", it) }
                            murderJson?.let { sessionProfile?.modeSettings?.set("murder", it) }

                            // 🔥 Persist to character docs as requested
                            rpgJson?.let { persistRpgSheetsToCharacters(it) }

                            Toast.makeText(this, "RPG settings saved.", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(
                                this,
                                "Failed to save RPG settings.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
            }

            REQ_VN_SETTINGS -> {
                val vnJson = data.getStringExtra("VN_SETTINGS_JSON") ?: return
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
                    .addOnFailureListener {
                        Toast.makeText(
                            this,
                            "Failed to save VN settings.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
        }
    }

    private fun persistVnBoardsToCharacters(vnJson: String) {
        val vn = try {
            Gson().fromJson(vnJson, ModeSettings.VNSettings::class.java)
        } catch (_: Exception) {
            null
        }
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
                    mapOf(
                        "level" to lvl.level,
                        "threshold" to lvl.threshold,
                        "personality" to lvl.personality
                    )
                }

                toRef.id to mapOf(
                    "fromId" to fromRef.id,
                    "toId" to toRef.id,
                    "notes" to rel.notes,
                    "currentLevel" to rel.currentLevel,
                    "upTriggers" to rel.upTriggers,
                    "downTriggers" to rel.downTriggers,
                    "points" to rel.points,
                    "levels" to levelsList
                )
            }.toMap()

            if (boardData.isNotEmpty()) {
                val cref = db.collection("characters").document(fromRef.id)
                batch.set(
                    cref,
                    mapOf("vnBoard" to boardData),
                    com.google.firebase.firestore.SetOptions.merge()
                )
            }
        }

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(
                    this,
                    "VN boards saved to characters.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    "Failed to save VN boards.",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun persistRpgSheetsToCharacters(rpgJson: String) {
        val rpg = try {
            Gson().fromJson(rpgJson, ModeSettings.RPGSettings::class.java)
        } catch (_: Exception) {
            null
        }
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
                "strength" to rc.stats.strength,
                "agility" to rc.stats.agility,
                "intelligence" to rc.stats.intelligence,
                "charisma" to rc.stats.charisma,
                "resolve" to rc.stats.resolve
            )

            val className = rc.characterClass.name
            val hp = rc.hp ?: calcHp(statsMap, className)
            val maxHp = rc.maxHp ?: calcMaxHp(statsMap, className)
            val defense = rc.defense ?: calcDefense(statsMap, className)

            val rpgSheet = mapOf(
                "class" to className,
                "role" to rc.role.name,
                "stats" to statsMap,
                "equipment" to rc.equipment,
                "hp" to hp,
                "maxHp" to maxHp,
                "defense" to defense
            )

            val cref = db.collection("characters").document(baseId)
            batch.set(
                cref,
                mapOf("rpgSheet" to rpgSheet),
                com.google.firebase.firestore.SetOptions.merge()
            )
            writes++

            // Firestore batch safety
            if (writes >= 480) {
                batch.commit()
                batch = db.batch()
                writes = 0
            }
        }

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(
                    this,
                    "RPG sheets saved to characters.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    "Failed to save RPG sheets.",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }


    // --- Firestore: save session profile
    private fun saveSessionProfile(
        sessionProfile: SessionProfile,
        sessionId: String,
        onComplete: ((Boolean) -> Unit)? = null // Add this parameter
    ) {
        val db = FirebaseFirestore.getInstance()

        // Enforce the List type to prevent HashMap corruption
        val rosterAsList = sessionProfile.slotRoster.toList()
        val safeProfile = sessionProfile.copy(slotRoster = rosterAsList)

        db.collection("sessions")
            .document(sessionId)
            .set(safeProfile) // Use .set() to create the document if it's new
            .addOnSuccessListener {
                Log.d("Firestore", "Session created/updated safely: $sessionId")
                onComplete?.invoke(true) // Trigger the success callback
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Failed to save session: $e")
                onComplete?.invoke(false) // Trigger the failure callback
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

        // --- NEW: Rebuild the map from the flat list! ---
        val rebuiltLinkedToMap = this.linkedTo.groupBy { it.type }
            .mapValues { it.value.toMutableList() }
            .toMutableMap()

        return CharacterProfile(
            id = if (isPh) "placeholder-${this.slotId}" else this.baseCharacterId!!,
            abilities = this.abilities,
            name = this.name,
            summary = this.summary,
            personality = this.personality,
            privateDescription = this.privateDescription,
            backstory = "",
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
            profileType = this.profileType,
            exampleDialogue = this.exampleDialogue,
            lorebookIds = this.lorebookIds,
            linkedToMap = rebuiltLinkedToMap,
            creatorNotes = this.creatorNotes
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
        slotId: String = UUID.randomUUID().toString(),
        extraLorebookIds: List<String> = emptyList()
    ): SlotProfile {
        val initialLastSynced = this.lastTimestamp ?: this.createdAt ?: Timestamp.now()
        val combinedLorebooks = ((this.lorebookIds ?: emptyList()) + extraLorebookIds).distinct()
        val flattenedLinks = this.linkedToMap.values.flatten()
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
            outfits = this.outfits?.stripVectors() ?: emptyList(),
            currentOutfit = this.currentOutfit ?: "",
            sfwOnly = this.sfwOnly,
            relationships = relationships,
            profileType = this.profileType ?: "bot",
            lastSynced = Timestamp.now(),
            exampleDialogue = this.exampleDialogue,
            lorebookIds = combinedLorebooks,
            linkedTo = flattenedLinks,
            creatorNotes = this.creatorNotes
        )
    }

    private fun PersonaProfile.toSlotProfile(
        relationships: List<Relationship> = emptyList(),
        slotId: String = UUID.randomUUID().toString(),
        extraLorebookIds: List<String> = emptyList()
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
            physicalDescription = this.physicaldescription,
            abilities = "",
            eyeColor = this.eyes,
            hairColor = this.hair,
            bubbleColor = this.bubbleColor ?: "#CCCCCC",
            textColor = this.textColor ?: "#000000",
            avatarUri = this.avatarUri,
            outfits = this.outfits?.stripVectors() ?: emptyList(),
            currentOutfit = this.currentOutfit ?: "",
            sfwOnly = true,
            relationships = relationships,
            profileType = "player",
            lorebookIds = extraLorebookIds
        )
    }

    private fun sendInviteToFriend(friendId: String) {
        val db = FirebaseFirestore.getInstance()
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // 1. Ensure we have a session ID
        if (lobbySessionId == null) {
            lobbySessionId = db.collection("sessions").document().id
        }
        val sessionId = lobbySessionId!!

        // 2. CRITICAL: Save the lobby to the DB so the friend has something to join!
        // (This mimics your 'ensureSkeletonExists()' from the web version)
        val sessionUpdates = hashMapOf<String, Any>(
            "sessionId" to sessionId,
            "title" to titleView.text.toString(),
            "userList" to com.google.firebase.firestore.FieldValue.arrayUnion(currentUserId),
            // Save all the lobby data directly to the session document!
            "chatId" to (chatProfile?.id ?: ""),
            "sessionSummary" to sessionSummary,
            "chatMode" to chatMode,
            "sfwOnly" to sfwToggle.isChecked,
            "sessionDescription" to descriptionView.text.toString(),
            "secretDescription" to secretDesc,
            "relationships" to relationships,
            "areas" to loadedAreas,
            // Optional: If your session schema uses a specific field for characters in the lobby
            "characterIds" to characterProfiles.map { it.id },
            "started" to false,
            "isBuilding" to false
        )

        // Use SetOptions.merge() so we Upsert (Create if new, Update if already exists)
        db.collection("sessions").document(sessionId)
            .set(sessionUpdates, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {

                // 3. Send the LIGHTWEIGHT DM to their Inbox
                val messageId = db.collection("users").document(friendId).collection("messages").document().id

                val inviteMessage = mapOf(
                    "id" to messageId,
                    "from" to currentUserId,
                    "to" to friendId,
                    "text" to "${titleView.text}: You've been invited to join a session!",
                    "type" to "SESSION_INVITE",
                    "sessionId" to sessionId, // <-- This is all they need to join!
                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "status" to "UNOPENED"
                    // Notice: No giant JSON string here!
                )

                db.collection("users").document(friendId)
                    .collection("messages")
                    .document(messageId)
                    .set(inviteMessage)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Invite sent!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to send message: ${e.message}", Toast.LENGTH_SHORT).show()
                    }

            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save lobby state: ${e.message}", Toast.LENGTH_SHORT).show()
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

                        val adapter =
                            ArrayAdapter(this, android.R.layout.simple_spinner_item, friendNames)
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

    override fun onStart() {
        super.onStart()

        if (lobbySessionId != null) {
            startLobbyListener()
        } else {
            Log.d("SessionLanding", "onStart: lobbySessionId is null, waiting for async load...")
        }
    }

    private fun startLobbyListener() {
        val db = FirebaseFirestore.getInstance()
        Log.d("sessionlanding_debug", "onStart lobbySessionId = $lobbySessionId")

        lobbyListener = db.collection("sessions").document(lobbySessionId!!)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener

                if (snapshot != null && snapshot.exists()) {
                    sessionProfile = snapshot.toObject(SessionProfile::class.java)

                    // This MUST be called to trigger the RecyclerView refresh
                    updateRosterUI()

                } else {
                    Toast.makeText(this, "Session ended.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
    }

    private fun updateRosterUI() {
        if (sessionProfile != null) {
            displaySession(sessionProfile!!)
            checkForCharacterUpdates()
            checkForChatUpdates()
        }

        // HOST CHECK
        // (Assuming userList[0] is always the host)
        val hostId = sessionProfile?.userList?.firstOrNull()
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val isHost = hostId == currentUserId

        if (isHost) {
            startSessionBtn.visibility = View.VISIBLE
            // Optional: Change text based on state? e.g. "Start Session" vs "Resume"
        } else {
            startSessionBtn.visibility = View.GONE
            // Optional: Show a "Waiting for Host..." textview instead
        }

        // Update the progress dialog if we are syncing
        val totalSlots = sessionProfile?.slotRoster?.size ?: 0
        if (buildDialogShown && totalSlots > 0) {
            // If you track readyCount in the session, update it here too
        }
    }

    private fun isHost(): Boolean {
        val myId = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        // Always check the current sessionProfile, which handles the "Index 0" logic
        return sessionProfile?.userList?.firstOrNull() == myId
    }

    override fun onStop() {
        super.onStop()
        // Ensure all dialogs are dead if the activity stops
        progressDialog?.dismiss()
        lobbyListener?.remove()
    }

    private fun addProfileToSlotRoster(type: String, profileId: String) {
        val db = FirebaseFirestore.getInstance()
        val sessionId = lobbySessionId ?: return

        val collection = if (type == "character") "characters" else "personas"
        db.collection(collection).document(profileId).get()
            .addOnSuccessListener { doc ->
                val slotId = UUID.randomUUID().toString()
                transferWardrobeToSession(sessionId, slotId, profileId, type)
                val newSlot = when (type) {
                    "character" -> {
                        val characterProfile = doc.toObject(CharacterProfile::class.java) ?: return@addOnSuccessListener
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val db = FirebaseFirestore.getInstance()
                                val baseWardrobeDoc = db.collection("characters")
                                    .document(characterProfile.id)
                                    .collection("wardrobes")
                                    .document("default")
                                    .get()
                                    .await()

                                if (baseWardrobeDoc.exists()) {
                                    val gson = Gson()
                                    val outfitsJson = gson.toJson(baseWardrobeDoc.get("outfits"))
                                    val type = object : TypeToken<List<Outfit>>() {}.type
                                    val fullOutfits = gson.fromJson<List<Outfit>>(outfitsJson, type) ?: emptyList()

                                    // Now save the full math to the Session!
                                    saveWardrobeToSubcollection(sessionId, slotId, characterProfile.id, fullOutfits)
                                }
                            } catch(e: Exception) {
                                Log.e("Wardrobe", "Failed to transfer wardrobe math", e)
                            }
                        }
                        characterProfile.toSlotProfile(relationships = emptyList(), slotId = slotId)
                            .copy(profileType = "bot") // <--- ADD THIS
                    }
                    "persona" -> {
                        val personaProfile = doc.toObject(PersonaProfile::class.java) ?: return@addOnSuccessListener
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val db = FirebaseFirestore.getInstance()
                                val baseWardrobeDoc = db.collection("characters")
                                    .document(personaProfile.id)
                                    .collection("wardrobes")
                                    .document("default")
                                    .get()
                                    .await()

                                if (baseWardrobeDoc.exists()) {
                                    val gson = Gson()
                                    val outfitsJson = gson.toJson(baseWardrobeDoc.get("outfits"))
                                    val type = object : TypeToken<List<Outfit>>() {}.type
                                    val fullOutfits = gson.fromJson<List<Outfit>>(outfitsJson, type) ?: emptyList()

                                    // Now save the full math to the Session!
                                    saveWardrobeToSubcollection(sessionId, slotId, personaProfile.id, fullOutfits)
                                }
                            } catch(e: Exception) {
                                Log.e("Wardrobe", "Failed to transfer wardrobe math", e)
                            }
                        }
                        personaProfile.toCharacterProfile().toSlotProfile(relationships = emptyList(), slotId = slotId)
                            .copy(profileType = "bot") // <--- ADD THIS
                    }
                    else -> return@addOnSuccessListener
                }

                // Check for duplicates in local sessionProfile before sending update
                if (sessionProfile?.slotRoster?.any { it.baseCharacterId == profileId } == true) {
                    Toast.makeText(
                        this,
                        "That character/persona is already in the session.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@addOnSuccessListener
                }

                // Build updated roster
                val currentRoster = sessionProfile?.slotRoster?.toMutableList() ?: mutableListOf()
                currentRoster.add(newSlot)

                val tempProfile = sessionProfile?.copy(slotRoster = currentRoster) ?: return@addOnSuccessListener

                // Re-calculate the cleaned descriptions and save to Firestore
                updateDescriptionsFromTemplate(tempProfile) { finalProfile ->
                    db.collection("sessions").document(sessionId).update(
                        mapOf(
                            "slotRoster" to finalProfile.slotRoster,
                            "sessionDescription" to finalProfile.sessionDescription,
                            "secretDescription" to finalProfile.secretDescription,
                            "initialGreeting" to finalProfile.initialGreeting,
                            "sessionSummary" to finalProfile.sessionSummary
                        )
                    ).addOnSuccessListener {
                        // The lobbyListener handles the actual UI refresh, but we can notify the user
                        Toast.makeText(this, "Character added!", Toast.LENGTH_SHORT).show()
                    }.addOnFailureListener { e ->
                        Log.e("Firestore", "Failed to add slot: $e")
                    }
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
        if (isProceedingToGame) return
        val sessionId = intent.getStringExtra("SESSION_ID") ?: return
        // Prefer currentUserId if not provided
        val userId = intent.getStringExtra("USER_ID") ?: FirebaseAuth.getInstance().currentUser?.uid
        ?: return
        val profile = sessionProfile ?: return

        val user = profile.userMap[userId]
        if (user?.activeSlotId == null) {
            val newUserMap = profile.userMap.toMutableMap().apply { remove(userId) }
            val newUserList = profile.userList.toMutableList().apply { remove(userId) }
            val newUserAssignments = profile.userAssignments.toMutableMap().apply { remove(userId) }
            val sessionRef =
                FirebaseFirestore.getInstance().collection("sessions").document(sessionId)

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

    private fun List<Outfit>.stripVectors(): List<Outfit> {
        return this.map { outfit ->
            outfit.copy(
                poseSlots = outfit.poseSlots.map { pose ->
                    pose.copy(vector = null) // Drop the heavy 1536-number array!
                }.toMutableList()
            )
        }
    }

    private fun saveWardrobeToSubcollection(sessionId: String, slotId: String, baseCharacterId: String, outfits: List<Outfit>) {
        if (outfits.isEmpty()) return

        val db = FirebaseFirestore.getInstance()
        val wardrobeRef = db.collection("sessions")
            .document(sessionId)
            .collection("wardrobes")
            .document(baseCharacterId) // <--- CHANGED THIS FROM slotId!

        wardrobeRef.set(mapOf(
            "slotId" to slotId,
            "baseCharacterId" to baseCharacterId,
            "outfits" to outfits
        )).addOnFailureListener { e ->
            Log.e("Wardrobe", "Failed to save wardrobe for slot $slotId", e)
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
            needsUpdateIds.clear()
            pendingUpdateDeltas.clear()

            slots.forEach { slot ->
                val baseId = slot.baseCharacterId ?: return@forEach
                val base = baseMap[baseId] ?: return@forEach

                // Skip player-controlled personas (Architect logic applies to NPCs)
                // if (base.profileType == "player") return@forEach

                // Perform the deep comparison
                val delta = computeDelta(base, slot)

                if (delta.hasChanges) {
                    needsUpdateIds += baseId
                    pendingUpdateDeltas[baseId] = delta
                }
            }

            // Update UI highlights (e.g., the halo effect)
            (charRecycler.adapter as? CharacterRowAdapter)?.setHighlightIds(needsUpdateIds)
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
        // 1. Safe Avatar check
        val baseAvatar = base.avatarUri ?: ""
        val slotAvatar = slot.avatarUri ?: ""

        // 2. The Visual Hash
        val baseVisualHash = base.outfits?.joinToString("|") { outfit ->
            "${outfit.name}:" + outfit.poseSlots.joinToString(",") { pose -> "${pose.name}=${pose.uri ?: ""}" }
        } ?: ""

        val slotVisualHash = slot.outfits?.joinToString("|") { outfit ->
            "${outfit.name}:" + outfit.poseSlots.joinToString(",") { pose -> "${pose.name}=${pose.uri ?: ""}" }
        } ?: ""

        val visualsChanged = (baseAvatar != slotAvatar) || (baseVisualHash != slotVisualHash)

        // 3. Safe Personality check
        val personalityChanged = (base.personality?.trim() ?: "") != (slot.personality?.trim() ?: "") ||
                (base.abilities?.trim() ?: "") != (slot.abilities?.trim() ?: "") ||
                (base.greeting?.trim() ?: "") != (slot.greeting?.trim() ?: "")

        // 4. Safe Secret check
        val secretChanged = (base.privateDescription?.trim() ?: "") != (slot.privateDescription?.trim() ?: "")

        // --- NEW: 5. Safe Links check ---
        // Flatten both sides into a predictable string format: "targetId:type:trigger"
        val baseLinksStr = base.linkedToMap.values.flatten().map { "${it.targetId}:${it.type}:${it.trigger}" }.sorted().joinToString("|")
        val slotLinksStr = slot.linkedTo.map { "${it.targetId}:${it.type}:${it.trigger}" }.sorted().joinToString("|")
        val linksChanged = baseLinksStr != slotLinksStr

        return UpdateDelta(visualsChanged, personalityChanged, secretChanged, linksChanged)
    }

    private fun showCharacterUpdateDialog(baseCharacterId: String, delta: UpdateDelta) {
        val choices = mutableListOf<String>()
        val choiceKeys = mutableListOf<String>()

        if (delta.visualsChanged) {
            choices.add("Update Visuals (Outfits, Poses, Avatar)")
            choiceKeys.add("visuals")
        }
        if (delta.personalityChanged) {
            choices.add("Update Core Personality (Personality, Abilities, Greeting)")
            choiceKeys.add("personality")
        }
        if (delta.secretChanged) {
            choices.add("Update Secret Lore (Private Description)")
            choiceKeys.add("secret")
        }
        if (delta.linksChanged) { // <--- NEW
            choices.add("Update Character Links (Inseparable, Time Skip, etc.)")
            choiceKeys.add("links")
        }

        val selectedItems = BooleanArray(choices.size) { true }

        AlertDialog.Builder(this)
            .setTitle("Character Update Found")
            .setMultiChoiceItems(choices.toTypedArray(), selectedItems) { _, which, isChecked ->
                selectedItems[which] = isChecked
            }
            .setPositiveButton("Sync Selected") { _, _ ->
                applyCharacterUpdate(
                    baseCharacterId,
                    updateVisuals = choiceKeys.indexOf("visuals").let { if (it != -1) selectedItems[it] else false },
                    updatePersonality = choiceKeys.indexOf("personality").let { if (it != -1) selectedItems[it] else false },
                    updateSecret = choiceKeys.indexOf("secret").let { if (it != -1) selectedItems[it] else false },
                    updateLinks = choiceKeys.indexOf("links").let { if (it != -1) selectedItems[it] else false } // <--- NEW
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyCharacterUpdate(
        baseCharacterId: String,
        updateVisuals: Boolean,
        updatePersonality: Boolean,
        updateSecret: Boolean,
        updateLinks: Boolean // <--- NEW PARAMETER
    ) {
        val sessionId = lobbySessionId ?: return
        val db = FirebaseFirestore.getInstance()

        db.collection("characters").document(baseCharacterId).get()
            .addOnSuccessListener { doc ->
                val base = doc.toObject(CharacterProfile::class.java) ?: return@addOnSuccessListener

                // LAUNCH COROUTINE: We need to do background database reads!
                lifecycleScope.launch(Dispatchers.IO) {

                    // 1. TRANSFER THE HEAVY MATH DIRECTLY BETWEEN SUBCOLLECTIONS
                    if (updateVisuals) {
                        try {
                            val baseWardrobeDocs = db.collection("characters")
                                .document(baseCharacterId)
                                .collection("wardrobes")
                                .get()
                                .await()

                            if (!baseWardrobeDocs.isEmpty) {
                                val batch = db.batch()
                                val sessionWardrobeRef = db.collection("sessions").document(sessionId).collection("wardrobes")

                                for (wardrobeDoc in baseWardrobeDocs.documents) {
                                    val outfit = wardrobeDoc.toObject(Outfit::class.java) ?: continue
                                    val safeOutfitName = outfit.name.replace("[^a-zA-Z0-9]".toRegex(), "_")
                                    val sessionDocRef = sessionWardrobeRef.document("${baseCharacterId}_$safeOutfitName")

                                    val dataToSave = mapOf(
                                        "baseCharacterId" to baseCharacterId,
                                        "outfit" to outfit
                                    )
                                    batch.set(sessionDocRef, dataToSave)
                                }
                                batch.commit().await()
                            }
                        } catch(e: Exception) {
                            Log.e("Wardrobe", "Failed to transfer wardrobe math for $baseCharacterId", e)
                        }
                    }

                    // 2. SWITCH TO MAIN THREAD TO UPDATE UI AND ROSTER
                    withContext(Dispatchers.Main) {
                        val newRoster = sessionProfile?.slotRoster?.map { slot ->
                            if (slot.baseCharacterId != baseCharacterId) return@map slot

                            slot.copy(
                                // 1. Visuals
                                avatarUri = if (updateVisuals) base.avatarUri ?: slot.avatarUri else slot.avatarUri,
                                outfits = if (updateVisuals) base.outfits ?: slot.outfits else slot.outfits,
                                currentOutfit = if (updateVisuals) base.currentOutfit ?: slot.currentOutfit else slot.currentOutfit,

                                // 2. Personality
                                summary = if (updatePersonality) base.summary ?: slot.summary else slot.summary,
                                personality = if (updatePersonality) base.personality ?: slot.personality else slot.personality,
                                abilities = if (updatePersonality) base.abilities ?: slot.abilities else slot.abilities,
                                greeting = if (updatePersonality) base.greeting ?: slot.greeting else slot.greeting,
                                exampleDialogue = if (updatePersonality) base.exampleDialogue else slot.exampleDialogue,

                                // 3. Secrets
                                privateDescription = if (updateSecret) base.privateDescription ?: slot.privateDescription else slot.privateDescription,

                                // --- NEW: 4. Links ---
                                linkedTo = if (updateLinks) base.linkedToMap.values.flatten() else slot.linkedTo,

                                lastSynced = com.google.firebase.Timestamp.now()
                            )
                        } ?: return@withContext

                        // Sync to Firestore
                        db.collection("sessions").document(sessionId)
                            .update("slotRoster", newRoster.toList())
                            .addOnSuccessListener {
                                sessionProfile = sessionProfile?.copy(slotRoster = newRoster)
                                needsUpdateIds.remove(baseCharacterId)
                                displaySession(sessionProfile!!)
                                Toast.makeText(this@SessionLandingActivity, "Character partially updated!", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
            }
    }

    private fun checkForChatUpdates() {
        if (sessionProfile?.chatMode == "ONEONONE") return
        val chatId = sessionProfile?.chatId ?: return
        if (chatId.isBlank()) return

        val db = FirebaseFirestore.getInstance()

        db.collection("chats").document(chatId).get()
            .addOnSuccessListener { doc ->
                val template = doc.toObject(ChatProfile::class.java) ?: return@addOnSuccessListener
                val current = sessionProfile ?: return@addOnSuccessListener
                val cleanedAssignments = sessionProfile?.slotRoster!!.mapIndexed { idx, slot ->
                    "character${idx + 1}" to slot.name
                }.toMap()

                val cleanDesc = substitutePlaceholders(template.description, cleanedAssignments)
                val cleanSecretDesc = substitutePlaceholders(template.secretDescription, cleanedAssignments)

                // Perform deep comparison
                val descChanged =
                    cleanDesc != current.sessionDescription?.trim() ||
                            cleanSecretDesc != current.secretDescription?.trim()
                val worldChanged = (template.areas != current.areas)

                // Roster check: Does the template have IDs not in our slotRoster?
                val rosterChanged = (template.characterIds != current.characterIds)

                if (descChanged || worldChanged || rosterChanged) {
                    updateButton.visibility = View.VISIBLE
                    updateButton.setOnClickListener {
                        showChatUpdateDialog(template, descChanged, worldChanged, rosterChanged)
                    }
                }
            }
    }

    private fun showChatUpdateDialog(
        template: ChatProfile,
        desc: Boolean,
        world: Boolean,
        roster: Boolean
    ) {
        val choices = mutableListOf<String>()
        if (desc) choices.add("Update Lore & Descriptions")
        if (world) choices.add("Update Areas & Locations")
        if (roster) choices.add("Sync Character Roster")

        val selectedItems = BooleanArray(choices.size) { true }

        AlertDialog.Builder(this)
            .setTitle("World Updates Available")
            .setMultiChoiceItems(choices.toTypedArray(), selectedItems) { _, which, isChecked ->
                selectedItems[which] = isChecked
            }
            .setPositiveButton("Apply Selected") { _, _ ->
                applyPartialChatUpdate(
                    template,
                    updateDesc = desc && selectedItems.getOrNull(choices.indexOf("Update Lore & Descriptions")) ?: false,
                    updateWorld = world && selectedItems.getOrNull(choices.indexOf("Update Areas & Locations")) ?: false,
                    updateRoster = roster && selectedItems.getOrNull(choices.indexOf("Sync Character Roster")) ?: false
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyPartialChatUpdate(
        template: ChatProfile,
        updateDesc: Boolean,
        updateWorld: Boolean,
        updateRoster: Boolean
    ) {
        val current = sessionProfile ?: return
        val oldBlueprint = current.characterIds
        val newBlueprint = template.characterIds
        val newRoster = current.slotRoster.toMutableList()

        // 1. Identify what needs to be fetched
        val idsToFetch = mutableListOf<String>()
        val replacementIndices = mutableMapOf<Int, String>() // Index in Blueprint -> New ID
        val additionIds = mutableListOf<String>()           // New IDs appended to the blueprint

        if (updateRoster) {
            newBlueprint.forEachIndexed { index, newId ->
                val oldIdAtPos = oldBlueprint.getOrNull(index)

                if (newId != oldIdAtPos) {
                    // Architect changed or added a character at this position
                    if (index < oldBlueprint.size) {
                        // This is a REPLACEMENT of an existing template slot
                        val currentSlot = newRoster.getOrNull(index)
                        if (currentSlot != null && (currentSlot.isPlaceholder || !currentSlot.userReplaced)) {
                            idsToFetch.add(newId)
                            replacementIndices[index] = newId
                        }
                    } else {
                        // This is an APPEND: Architect added a character beyond the original count
                        idsToFetch.add(newId)
                        additionIds.add(newId)
                    }
                }
            }
        }

        // 2. Execution Block
        fun finalizeUpdate(fetchedRoster: List<SlotProfile> = emptyList()) {
            val fetchedMap = fetchedRoster.associateBy { it.baseCharacterId }

            // Apply Replacements
            replacementIndices.forEach { (index, newId) ->
                val profile = fetchedMap[newId]
                if (profile != null && index < newRoster.size) {
                    newRoster[index] = profile.copy(slotId = newRoster[index].slotId)
                }
            }

            // Apply Additions (Append to the end of the current roster)
            additionIds.forEach { id ->
                val profile = fetchedMap[id]
                if (profile != null) {
                    newRoster.add(profile)
                }
            }

            // Construct the Final Profile
            val updatedProfile = current.copy(
                sessionDescription = if (updateDesc) template.description else current.sessionDescription,
                secretDescription = if (updateDesc) template.secretDescription else current.secretDescription,
                areas = if (updateWorld) template.areas else current.areas,
                slotRoster = newRoster.toList(),
                characterIds = newBlueprint // Update blueprint to clear the update button
            )

            // 3. Final Save
            saveSessionProfile(updatedProfile, current.sessionId) { success ->
                if (success) {
                    sessionProfile = updatedProfile
                    updateButton.visibility = View.GONE
                    displaySession(updatedProfile)
                    Toast.makeText(this, "World successfully synced!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Handle Async vs Sync path
        if (idsToFetch.isNotEmpty()) {
            loadCharactersByIds(idsToFetch) { profiles ->
                val slots = profiles.map { it.toSlotProfile() }
                finalizeUpdate(slots)
            }
        } else {
            finalizeUpdate()
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

    val apiResult = try {
        Facilitator.callActivationAI(prompt, BuildConfig.OPENAI_API_KEY, "nvidia",
            null, null, null)
    } catch (e: Exception) {
        Log.e("MysteryTimeline", "AI call failed", e)
        com.albirich.RealmsAI.ai.AiResponseData(null, 0L) // Return an empty bucket
    }

    // 2. Open the bucket and pull out the text
    val raw = apiResult.content ?: ""
    val tokens = apiResult.totalTokens

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

private fun logSessionEngagement(isHost: Boolean) {
    val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

    val currentMonth = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault()).format(java.util.Date())
    val reportRef = db.collection("users").document(userId).collection("monthly_reports").document(currentMonth)

    // Pick the right bucket
    val fieldToUpdate = if (isHost) "sessionsHosted" else "sessionsJoined"

    val update = hashMapOf<String, Any>(
        fieldToUpdate to com.google.firebase.firestore.FieldValue.increment(1)
    )

    reportRef.set(update, com.google.firebase.firestore.SetOptions.merge())
        .addOnFailureListener { e -> android.util.Log.e("Analytics", "Failed to log engagement", e) }
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