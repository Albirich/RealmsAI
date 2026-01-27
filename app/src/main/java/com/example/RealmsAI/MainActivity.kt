package com.example.RealmsAI

import ChatAdapter
import ChatAdapter.AdapterMode
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.RealmsAI.FirestoreClient.db
import com.example.RealmsAI.ai.Facilitator
import com.example.RealmsAI.ai.PromptBuilder
import com.example.RealmsAI.models.*
import com.example.RealmsAI.models.SlotProfile
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import android.text.TextWatcher
import android.text.Editable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import com.example.RealmsAI.FacilitatorResponseParser.Action
import com.example.RealmsAI.FacilitatorResponseParser.NewNPCData
import com.example.RealmsAI.FacilitatorResponseParser.updateRelationshipLevel
import com.example.RealmsAI.FacilitatorResponseParser.updateRelationshipsFromChanges
import com.example.RealmsAI.adapters.CollectionAdapter.CharacterRowAdapter
import com.example.RealmsAI.ai.Director
import com.example.RealmsAI.ai.PromptBuilder.buildDiceRoll
import com.example.RealmsAI.ai.PromptBuilder.buildMurderMysteryInfo
import com.example.RealmsAI.ai.PromptBuilder.buildMurdererInfo
import com.example.RealmsAI.ai.PromptBuilder.buildNPCGeneration
import com.example.RealmsAI.ai.PromptBuilder.buildVNPrompt
import com.example.RealmsAI.models.ModeSettings.RPGSettings
import com.example.RealmsAI.models.ModeSettings.VNRelationship
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.Query
import org.json.JSONArray
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val FREE_DAILY_LIMIT = 70
    }


//   val infoButtonWardrobe: ImageButton = findViewById(R.id.infoButtonWardrobe)
//   infoButtonCharRelationships.setOnClickListener {
//      AlertDialog.Builder(this@CharacterCreationActivity)
//          .setTitle("Relationships")
//          .setMessage("This allows you to give the character relationships that they will remember in chats")
//          .setPositiveButton("OK", null)
//          .show()
//      }

//    <include
//            android:id="@+id/infoButtonWardrobe"
//            layout="@layout/info_button"
//            android:layout_width="25dp"
//            android:layout_height="25dp"
//            android:layout_marginStart="8dp"
//            app:layout_constraintTop_toTopOf="@id/wardrobeButton"
//            app:layout_constraintBottom_toBottomOf="@id/wardrobeButton"
//            app:layout_constraintStart_toEndOf="@id/wardrobeButton"
//            android:layout_gravity="center_vertical" />

   
    // UI
    private lateinit var chatTitleView: TextView
    private lateinit var chatDescriptionView: TextView
    private lateinit var chatRecycler: RecyclerView
    private lateinit var rollRecyclerView: RecyclerView
    private lateinit var messageEditText: EditText
    private lateinit var sendButton: Button
    private lateinit var topOverlay: View
    private lateinit var toggleChatInputButton: ImageButton
    private lateinit var avatarViews: List<ImageView>
    private lateinit var chatInputGroup: View
    private lateinit var toggleButtonContainer: View
    private lateinit var rpgToggleContainer: View
    private lateinit var rollHistoryButton: ImageButton
    private lateinit var characterSheetButton: ImageButton
    private lateinit var backgroundImageView:ImageView
    private var isDescriptionExpanded = false
    private var lastMultiplayerValue: Boolean? = null
    private lateinit var resendButton: ImageButton
    private lateinit var typingIndicatorBar: LinearLayout
    private lateinit var toggleControlButton: ImageButton
    private lateinit var toggleChatButton: ImageButton
    private lateinit var moveButton: Button
    private lateinit var personaButton: Button
    private lateinit var optionButton: Button
    // Dice roll UI elements
    private lateinit var rollButton: ImageButton
    private lateinit var diceImageView: ImageView

    // State
    private lateinit var sessionProfile: SessionProfile
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var rollAdapter: ChatAdapter
    private val visibleAvatarSlotIds = MutableList(4) { "" }
    private val avatarSlotLocked = BooleanArray(4) { false }
    private var historyLoaded = false
    private var lastMessageId: String? = null
    private val processedMessageIds = mutableSetOf<String>()
    val maxSlots = 4
    val avatarSlotAssignments: MutableMap<Int, String?> = mutableMapOf(
        0 to null,
        1 to null,
        2 to null,
        3 to null
    )
    private var initialGreeting: String? = null
    private var initialGreetingSent: Boolean = false

    private var mySlotId: String? = null
    private var lastBackgroundArea: String? = null
    private var lastBackgroundLocation: String? = null

    private var sessionListener: ListenerRegistration? = null
    private var messagesListener: ListenerRegistration? = null
    private var personalHistoryListener: ListenerRegistration? = null
    private var myPersonalHistory: List<ChatMessage> = emptyList()
    private var lastTypingState = false
    private var ignoreTextWatcher = false

    private var typingTimeoutJob: Job? = null


    private lateinit var sessionId: String
    private lateinit var userId: String
    private lateinit var chatId: String

    private var currentUser: SessionUser? = null

    private var activationRound = 0
    private var maxActivationRounds = 3

    enum class ButtonState { SEND, INTERRUPT, WAITING }
    private var currentState: ButtonState = ButtonState.SEND
    private var aiJob: Job? = null


    // --- STATE VARIABLES ---
    private var spyingArea: String? = null
    private var spyingLocation: String? = null
    private var moveSelectedSlotId: String? = null // Who are we trying to move?
    private var forcedNextSpeakerId: String? = null // For the spinner
    // Helper to check if I am the Host (Index 0 in userList)
    private val isHost: Boolean
        get() = sessionProfile.userList.isNotEmpty() && sessionProfile.userList[0] == userId


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        userId = user.uid

        setContentView(R.layout.activity_main)

        // 1. Get Session ID (Essential)
        sessionId = intent.getStringExtra("SESSION_ID") ?: run {
            Toast.makeText(this, "Session ID missing", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        initialGreeting = intent.getStringExtra("GREETING")

        // 2. Check if we received the Profile (Legacy/Small) OR need to fetch (Safe)
        val profileJson = intent.getStringExtra("SESSION_PROFILE_JSON")

        if (!profileJson.isNullOrBlank()) {
            try {
                sessionProfile = Gson().fromJson(profileJson, SessionProfile::class.java)
                initializeChatInterface()
            } catch (e: Exception) {
                Log.e(TAG, "JSON parse failed, falling back to fetch", e)
                fetchSessionAndInit()
            }
        } else {
            // No JSON passed (The fix), so we fetch fresh data
            fetchSessionAndInit()
        }
    }

    private fun fetchSessionAndInit() {
        // Optional: Show a ProgressBar here
        FirebaseFirestore.getInstance().collection("sessions").document(sessionId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "Session not found", Toast.LENGTH_LONG).show()
                    finish()
                    return@addOnSuccessListener
                }

                val profile = doc.toObject(SessionProfile::class.java)
                if (profile != null) {
                    sessionProfile = profile
                    initializeChatInterface()
                } else {
                    Toast.makeText(this, "Failed to parse session", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading session: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
    }

    private fun initializeChatInterface() {
        // Find the current SessionUser (for name lookup, etc.)
        currentUser = sessionProfile.userMap[userId]

        // Bind UI
        chatTitleView = findViewById(R.id.chatTitle)
        chatDescriptionView = findViewById(R.id.chatDescription)
        chatRecycler = findViewById(R.id.chatRecyclerView)
        rollRecyclerView = findViewById(R.id.rollRecyclerView)
        messageEditText = findViewById(R.id.messageEditText)
        sendButton = findViewById(R.id.sendButton)
        topOverlay = findViewById(R.id.topOverlay)
        toggleChatInputButton = findViewById(R.id.toggleChatInputButton)
        backgroundImageView = findViewById(R.id.backgroundImageView)
        avatarViews = listOf(
            findViewById(R.id.botAvatar0ImageView),
            findViewById(R.id.botAvatar1ImageView),
            findViewById(R.id.botAvatar2ImageView),
            findViewById(R.id.botAvatar3ImageView)
        )
        chatInputGroup = findViewById(R.id.chatInputGroup)
        toggleButtonContainer = findViewById(R.id.toggleButtonContainer)
        resendButton = findViewById(R.id.resendButton)
        toggleControlButton = findViewById(R.id.toggleControlButton)
        toggleChatButton = findViewById(R.id.toggleChatButton)
        typingIndicatorBar = findViewById(R.id.typingIndicatorBar)
        moveButton = findViewById(R.id.controlMoveButton)
        personaButton = findViewById(R.id.controlPersonaButton)
        optionButton = findViewById(R.id.controlOptionsButton)
        rollButton = findViewById(R.id.rollButton)
        diceImageView = findViewById(R.id.diceImageView)
        rpgToggleContainer = findViewById(R.id.rpgToggleContainer)
        characterSheetButton = findViewById(R.id.characterSheetButton)
        rollHistoryButton = findViewById(R.id.rollHistoryButton)
        // UI display
        chatTitleView.text = sessionProfile.title
        val descriptionHeader = findViewById<LinearLayout>(R.id.descriptionHeader)
        val descriptionDropdown = findViewById<LinearLayout>(R.id.descriptionDropdown)
        val chatDescriptionView = findViewById<TextView>(R.id.chatDescription)
        val descriptionToggle = findViewById<ImageView>(R.id.descriptionToggle)
        chatId = sessionProfile.chatId

        val rootView = findViewById<View>(R.id.chatRoot)
        val avatarContainer = findViewById<View>(R.id.avatarContainer)
        val chatInputGroup = findViewById<View>(R.id.chatInputGroup)
        val toggleButtonContainer = findViewById<View>(R.id.toggleButtonContainer)
        val rpgToggleContainer = findViewById<View>(R.id.rpgToggleContainer)
        val controlBox = findViewById<View>(R.id.controlBox)
        val characterSheetBox = findViewById<View>(R.id.characterSheetBox)
        val rollHistoryBox = findViewById<View>(R.id.rollHistoryBox)

        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - rect.bottom

            // If more than 150px, keyboard is probably visible
            if (keypadHeight > 150) {
                // Move only avatar + chat input container up by keypad height (or customize)
                avatarContainer.translationY = -keypadHeight.toFloat() + 125f
                chatInputGroup.translationY = -keypadHeight.toFloat() + 125f
                toggleButtonContainer.translationY = -keypadHeight.toFloat() + 125f
                rpgToggleContainer.translationY = -keypadHeight.toFloat() + 125f
                backgroundImageView.translationY = -keypadHeight.toFloat() + 125f
            } else {
                avatarContainer.translationY = 0f
                chatInputGroup.translationY = 0f
                toggleButtonContainer.translationY = 0f
                rpgToggleContainer.translationY = 0f
                backgroundImageView.translationY = 0f
            }
        }
        chatDescriptionView.text = sessionProfile.sessionDescription
        descriptionDropdown.visibility = View.GONE

        descriptionHeader.setOnClickListener {
            isDescriptionExpanded = !isDescriptionExpanded
            if (isDescriptionExpanded) {
                descriptionDropdown.visibility = View.VISIBLE
                // Optional: Animate arrow down (expanded)
                descriptionToggle.setImageResource(R.drawable.ic_expand_less) // up arrow
            } else {
                descriptionDropdown.visibility = View.GONE
                // Optional: Animate arrow up (collapsed)
                descriptionToggle.setImageResource(R.drawable.ic_expand_more) // down arrow
            }
        }
        var activeSlotId = sessionProfile.userMap[userId]?.activeSlotId
        // Adapter setup (pass in modern fields)
        val isMultiplayer = sessionProfile.userMap.size > 1

        chatAdapter = ChatAdapter(
            mutableListOf(),
            { lastPos -> if (lastPos >= 0) chatRecycler.smoothScrollToPosition(lastPos) },
            sessionProfile.slotRoster,
            sessionProfile.userMap.values.toList(),
            userId,
            onEditMessage = { editedMessage, position ->
                lifecycleScope.launch {
                    // 1. Delete following messages
                    val deletedIds = saveEditedMessageAndDeleteFollowing(editedMessage, position)

                    // 2. Clean up histories
                    deleteFromSlotPersonalHistories(sessionId, deletedIds)

                    // 3. NEW: Delete memories associated with deleted messages
                    deleteMemoriesFromSession(deletedIds)

                    if (!mySlotId.isNullOrBlank()) {
                        addToPersonalHistoryFirestore(sessionId, mySlotId!!, editedMessage)
                    }

                    if (position < chatAdapter.itemCount) {
                        chatAdapter.removeMessagesFrom(position)
                        chatAdapter.insertMessageAt(position, editedMessage)
                    } else {
                        Log.w(TAG, "Skipped manual adapter update: Position $position out of bounds.")
                    }

                    sendToAI(editedMessage.text)
                }
            },
            onDeleteMessages = { fromPosition ->
                val targetMessage = chatAdapter.getMessageAt(fromPosition)
                val timestamp = targetMessage.timestamp
                if (timestamp != null) {
                    lifecycleScope.launch {
                        // 1. Delete message and following
                        val deletedIds = deleteMessageAndFollowing(targetMessage)

                        // 2. Clean up histories
                        deleteFromSlotPersonalHistories(sessionId, deletedIds)

                        // 3. NEW: Delete memories associated with deleted messages
                        deleteMemoriesFromSession(deletedIds)

                        chatAdapter.removeMessagesFrom(fromPosition)
                    }
                }
            },
            isMultiplayer = isMultiplayer
        )

        Log.d("loading in", "after setting up chatadapter laoding multiplayer check: ${sessionProfile.multiplayer}")
        chatRecycler.layoutManager = LinearLayoutManager(this)
        chatRecycler.adapter = chatAdapter

        // NEXT SPEAKER SPINNER SETUP
        val speakerSpinner = findViewById<Spinner>(R.id.nextSpeakerSpinner)
        val speakerContainer = findViewById<LinearLayout>(R.id.nextSpeakerContainer)

        // Show container only if NOT one-on-one (optional, but cleaner)
        if (sessionProfile.chatMode != "ONEONONE") {
            speakerContainer.visibility = View.VISIBLE
        }

        // Populate logic (call this whenever roster changes, or just in onCreate if roster is static)
        fun updateSpeakerList() {
            val speakers = listOf("Auto") + sessionProfile.slotRoster.map { it.name }
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, speakers)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            speakerSpinner.adapter = adapter
        }
        updateSpeakerList()

        speakerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 0) {
                    forcedNextSpeakerId = null
                } else {
                    // Find slot ID by name (offset by 1 due to "Auto")
                    forcedNextSpeakerId = sessionProfile.slotRoster.getOrNull(position - 1)?.slotId
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        rollAdapter = ChatAdapter(
            mutableListOf(),
            { /* scroll logic if needed */ },
            sessionProfile.slotRoster,
            sessionProfile.userMap.values.toList(),
            userId,
            onEditMessage = { _, _ -> }, // not needed for rolls
            onDeleteMessages = { pos -> /* delete logic */ },
            isMultiplayer = isMultiplayer,
            mode = AdapterMode.ROLL_HISTORY,
            onReRoll = { msg, pos ->
                rerollAndUpdateMessage(msg) }
        )
        rollRecyclerView.layoutManager = LinearLayoutManager(this)
        rollRecyclerView.adapter = rollAdapter

        // Hide all avatars, then show only those in use
        avatarViews.forEach { it.visibility = View.INVISIBLE }

        // New session or load old chat?
        when (intent.getStringExtra("ENTRY_MODE") ?: "CREATE") {

            "CREATE" -> {
                Log.d("entering", "entrymode: CREATE")
                chatAdapter.clearMessages()
                // Determine if all users have selected a character
                val allPlayersChosen = sessionProfile.userMap.values.all { it.activeSlotId != null }
                val greetingText = initialGreeting ?: ""
                if (allPlayersChosen) {
                    // --- ONE-ON-ONE SYNC LOGIC ---
                    if (sessionProfile.chatMode == "ONEONONE") {
                        val botSlot = sessionProfile.slotRoster.find { it.profileType != "player" }
                        val playerSlot = sessionProfile.slotRoster.find { it.profileType == "player" }

                        if (botSlot != null && playerSlot != null) {
                            // Teleport the player to the bot's location
                            val updatedRoster = sessionProfile.slotRoster.map { slot ->
                                if (slot.slotId == playerSlot.slotId) {
                                    slot.copy(
                                        lastActiveArea = botSlot.lastActiveArea,
                                        lastActiveLocation = botSlot.lastActiveLocation
                                    )
                                } else {
                                    slot
                                }
                            }
                            // Update local profile so the immediate AI call sees the correct location
                            sessionProfile = sessionProfile.copy(slotRoster = updatedRoster)

                            // Save to Firestore so it sticks
                            saveSessionProfile(sessionProfile, sessionId)
                        }
                    }
                    // All players have characters – send greeting immediately
                    updateButtonState(ButtonState.INTERRUPT)
                    if (greetingText.isNotBlank()) {
                        lifecycleScope.launch {
                            // Add greeting to each bot's personal history
                            sessionProfile.slotRoster
                                .filter { it.profileType == "bot" }
                                .forEach { botSlot ->
                                    val greetingMsg = ChatMessage(
                                        id = UUID.randomUUID().toString(),
                                        senderId = "system",
                                        text = greetingText,
                                        area = botSlot.lastActiveArea,
                                        location = botSlot.lastActiveLocation,
                                        timestamp = Timestamp.now(),
                                        visibility = false
                                    )
                                    addToPersonalHistoryFirestore(sessionId, botSlot.slotId, greetingMsg)
                                }
                        }
                        sendToAI(greetingText)  // Send initial greeting to AI:contentReference[oaicite:2]{index=2}:contentReference[oaicite:3]{index=3}
                    } else {
                        sendToAI("")  // Send empty prompt if no greeting text
                    }
                    initialGreetingSent = true  // Mark greeting as sent
                } else {
                    // Not everyone has a character yet – delay the greeting
                    updateButtonState(ButtonState.WAITING)  // Show "Waiting…" to disable sending
                    // (Optional: You could show a toast or message indicating waiting for all players)
                }
                updateRPGToggleVisibility()
            }
            "GUEST" ->{
                Log.d("entering", "entrymode: GUEST")
                updateButtonState(ButtonState.INTERRUPT)
                val activeSlotId = sessionProfile.userMap[userId]?.activeSlotId
                val playerSlot = sessionProfile.slotRoster.find { it.slotId == activeSlotId }
                val playerArea = playerSlot?.lastActiveArea
                val playerLocation = playerSlot?.lastActiveLocation
                val areaObj = sessionProfile.areas.find { it.id == playerArea || it.name == playerArea }
                val locationObj = areaObj?.locations?.find { it.id == playerLocation || it.name == playerLocation }
                val backgroundUrl = locationObj?.uri
                if (!backgroundUrl.isNullOrBlank()) {
                    Glide.with(this)
                        .load(backgroundUrl)
                        .into(findViewById<ImageView>(R.id.backgroundImageView))
                }
                val mySlotId = sessionProfile.userMap[userId]?.activeSlotId
                Log.d("loading in", "inside guest loading multiplayer check before: ${sessionProfile.multiplayer}")
                val updatedSessionProfile = sessionProfile.copy(multiplayer = true)

                db.collection("sessions")
                    .document(sessionId)
                    .get()
                    .addOnSuccessListener { document ->
                        val updatedProfile = document.toObject(SessionProfile::class.java)
                        if (updatedProfile != null) {
                            sessionProfile = updatedProfile
                            // ...other UI updates...
                            updateRPGToggleVisibility() // <-- HERE
                        }
                    }
                loadSessionHistory()
            }
            "LOAD" -> {
                Log.d("entering", "entrymode: LOAD")

                val activeSlotId = sessionProfile.userMap[userId]?.activeSlotId
                val playerSlot = sessionProfile.slotRoster.find { it.slotId == activeSlotId }
                val playerArea = playerSlot?.lastActiveArea
                val playerLocation = playerSlot?.lastActiveLocation
                val areaObj = sessionProfile.areas.find { it.id == playerArea || it.name == playerArea }
                val locationObj = areaObj?.locations?.find { it.id == playerLocation || it.name == playerLocation }
                val backgroundUrl = locationObj?.uri
                if (!backgroundUrl.isNullOrBlank()) {
                    Glide.with(this)
                        .load(backgroundUrl)
                        .into(findViewById<ImageView>(R.id.backgroundImageView))
                }
                // Use your helper:
                updateBackgroundIfChanged(playerArea, playerLocation, sessionProfile.areas)
                loadSessionHistory()
            }
        }

        val myUserId = FirebaseAuth.getInstance().currentUser?.uid
        val mySessionUser = sessionProfile?.userMap?.get(myUserId)
        if (mySessionUser?.activeSlotId == null) {
            showCharacterPickerDialog(sessionProfile!!.slotRoster) { selectedSlot ->
                // Now assign this slot to the user and update Firestore
                assignSlotToUser(myUserId!!, selectedSlot.slotId)
            }
        }

        mySlotId = sessionProfile.userMap[userId]?.activeSlotId
        // Toggle input visibility
        toggleChatInputButton.setOnClickListener {
            Log.d("togglebutton debug", "toggle chatinputbutton")
            val isVisible = chatInputGroup.visibility == View.VISIBLE
            chatInputGroup.visibility = if (isVisible) View.GONE else View.VISIBLE
        }

        val moveOptions = findViewById<ConstraintLayout>(R.id.moveOptions)
        val avatarOptions = findViewById<ConstraintLayout>(R.id.personaOptions)
        val optionsOptions = findViewById<ConstraintLayout>(R.id.optionsOptions)

        val moveBtn = findViewById<Button>(R.id.controlMoveButton)
        val avatarBtn = findViewById<Button>(R.id.controlPersonaButton)
        val optionsBtn = findViewById<Button>(R.id.controlOptionsButton)

        fun showMove() {
            moveOptions.visibility = View.VISIBLE
            avatarOptions.visibility = View.GONE
            optionsOptions.visibility = View.GONE

            val recycler = findViewById<RecyclerView>(R.id.moveMapRecycler)
            recycler.layoutManager = LinearLayoutManager(this)

            val stopSpyingBtn = findViewById<Button>(R.id.stopSpyingButton)
            stopSpyingBtn.visibility = if (spyingLocation != null) View.VISIBLE else View.GONE
            stopSpyingBtn.setOnClickListener {
                spyingArea = null
                spyingLocation = null
                stopSpyingBtn.visibility = View.GONE
                Toast.makeText(this, "Stopped Spying. Returning to character view.", Toast.LENGTH_SHORT).show()
                // Refresh views
                updateAvatarsFromSlots(sessionProfile.slotRoster, avatarSlotAssignments)
                showMove() // Refresh adapter highlighting
            }

            // Build Flattened List: Area Header -> Location Rows
            val items = mutableListOf<MoveMapItem>()
            sessionProfile.areas.forEach { area ->
                items.add(MoveMapItem.Header(area))
                area.locations.forEach { loc ->
                    val charsHere = sessionProfile.slotRoster.filter {
                        it.lastActiveArea == area.name && it.lastActiveLocation == loc.name
                    }
                    items.add(MoveMapItem.LocationRow(area, loc, charsHere))
                }
            }

            recycler.adapter = MoveMapAdapter(
                items,
                onCharacterClick = { slot ->
                    moveSelectedSlotId = slot.slotId
                    Toast.makeText(this, "Selected ${slot.name}. Tap a location to move them.", Toast.LENGTH_SHORT).show()
                    recycler.adapter?.notifyDataSetChanged()
                },
                onLocationClick = { area, loc ->
                    if (moveSelectedSlotId != null) {
                        // MOVE LOGIC
                        val updatedRoster = sessionProfile.slotRoster.map {
                            if (it.slotId == moveSelectedSlotId) {
                                it.copy(lastActiveArea = area.name, lastActiveLocation = loc.name)
                            } else it
                        }
                        sessionProfile = sessionProfile.copy(slotRoster = updatedRoster)
                        saveSessionProfile(sessionProfile, sessionId)

                        Toast.makeText(this, "Moved to ${loc.name}", Toast.LENGTH_SHORT).show()

                        // Reset selection and refresh
                        moveSelectedSlotId = null
                        showMove()
                    }
                },
                onSpyClick = { area, loc ->
                    spyingArea = area.name
                    spyingLocation = loc.name
                    stopSpyingBtn.visibility = View.VISIBLE
                    Toast.makeText(this, "Spying on ${loc.name}", Toast.LENGTH_SHORT).show()

                    // Trigger UI updates to show that room's content
                    updateAvatarsFromSlots(sessionProfile.slotRoster, avatarSlotAssignments)
                    updateBackgroundIfChanged(area.name, loc.name, sessionProfile.areas)
                    showMove() // Refresh adapter highlighting
                }
            )
        }

        fun showAvatar() {
            moveOptions.visibility = View.GONE
            avatarOptions.visibility = View.VISIBLE
            optionsOptions.visibility = View.GONE

            // setup Persona Options

            val personaOptions = findViewById<ConstraintLayout>(R.id.personaOptions)
            val characterSpinner = findViewById<Spinner>(R.id.characterSpinner)
            val lockSlot0 = findViewById<CheckBox>(R.id.lockSlot0)
            val lockSlot1 = findViewById<CheckBox>(R.id.lockSlot1)
            val lockSlot2 = findViewById<CheckBox>(R.id.lockSlot2)
            val lockSlot3 = findViewById<CheckBox>(R.id.lockSlot3)
            val lockCheckboxes = listOf(lockSlot0, lockSlot1, lockSlot2, lockSlot3)
            val takeControl = findViewById<Button>(R.id.takeControl)
            val setImage = findViewById<Button>(R.id.setImage)
            val outfitSpinner = findViewById<Spinner>(R.id.outfitSpinner)
            val personaPoseRecycler = findViewById<RecyclerView>(R.id.personaPoseRecycler)
            val slotSelectors = listOf(
                findViewById<ImageButton>(R.id.selectSlot0),
                findViewById<ImageButton>(R.id.selectSlot1),
                findViewById<ImageButton>(R.id.selectSlot2),
                findViewById<ImageButton>(R.id.selectSlot3)
            )

            val activeSlotId = sessionProfile.userMap[userId]?.activeSlotId
            val playerSlot = sessionProfile.slotRoster.find { it.slotId == activeSlotId }
            val currentArea = playerSlot?.lastActiveArea
            val currentLocation = playerSlot?.lastActiveLocation
            var selectedPose: PoseSlot? = null
            val avatarSlotLocked = BooleanArray(4) { false }



            val presentSlots = sessionProfile.slotRoster
            val characterNames = presentSlots.map { it.name }
            val characterAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, characterNames)
            characterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            characterSpinner.adapter = characterAdapter

            characterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                    val selectedSlot = presentSlots[position]

                    // A. Get Outfits
                    val outfits = selectedSlot.outfits ?: emptyList()
                    val outfitNames = if (outfits.isNotEmpty()) {
                        outfits.map { it.name }
                    } else {
                        listOf("Default") // Fallback if no outfits defined
                    }

                    // B. Setup Outfit Spinner Adapter
                    val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, outfitNames)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    outfitSpinner.adapter = adapter

                    // C. Pre-select "Current Outfit" if possible
                    val currentIndex = outfits.indexOfFirst { it.name == selectedSlot.currentOutfit }
                    if (currentIndex >= 0) {
                        outfitSpinner.setSelection(currentIndex)
                    }

                    // (Note: Setting the adapter automatically triggers outfitSpinner.onItemSelected,
                    // so we don't need to manually update the recycler here.)
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }

            // 2. OUTFIT SELECTION LISTENER
            outfitSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    // We need to know WHICH character is selected to get their outfits
                    val charPos = characterSpinner.selectedItemPosition
                    if (charPos < 0 || charPos >= presentSlots.size) return

                    val selectedSlot = presentSlots[charPos]
                    val outfits = selectedSlot.outfits ?: emptyList()

                    // Get Poses for this outfit
                    val poses = if (outfits.isNotEmpty() && position < outfits.size) {
                        outfits[position].poseSlots
                    } else {
                        emptyList()
                    }

                    // Update Recycler
                    personaPoseRecycler.layoutManager = LinearLayoutManager(
                        this@MainActivity,
                        LinearLayoutManager.HORIZONTAL,
                        false
                    )
                    personaPoseRecycler.adapter = PoseRowAdapter(poses) { pose ->
                        selectedPose = pose
                        // Optional: visual feedback
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>) {
                    // Clear recycler
                    personaPoseRecycler.adapter = null
                }
            }

            var selectedAvatarSlotIndex = 0 // default to first

            slotSelectors.forEachIndexed { i, btn ->
                btn.setOnClickListener {
                    selectedAvatarSlotIndex = i
                    slotSelectors.forEachIndexed { j, button ->
                        button.isSelected = (i == j)
                        if (i == j) {
                            button.setBackgroundResource(R.drawable.avatar_slot_selected_bg)
                        } else {
                            button.setBackgroundResource(android.R.color.transparent)
                        }
                    }
                }
            }

            lockCheckboxes.forEachIndexed { i, checkBox ->
                checkBox.setOnCheckedChangeListener { _, isChecked ->
                    avatarSlotLocked[i] = isChecked
                    // Optionally: Do something when a lock is toggled
                    Log.d("avatardebug", "Slot $i lock changed to $isChecked")
                }
            }

            setImage.setOnClickListener {
                val selectedCharacter = presentSlots[characterSpinner.selectedItemPosition]
                val poseName = selectedPose?.name   // assuming PoseSlot has a `name`
                val poseUri = selectedPose?.uri

                if (poseName != null && poseUri != null) {
                    // Update local UI immediately

                    Log.d("AvatarDebug1", "Setting avatar for slot $selectedAvatarSlotIndex to $poseUri")
                    Glide.with(this)
                        .load(poseUri)
                        .into(avatarViews[selectedAvatarSlotIndex])
                    avatarViews[selectedAvatarSlotIndex].visibility = View.VISIBLE

                    // Update sessionProfile slotRoster!
                    val slotIndex = sessionProfile.slotRoster.indexOfFirst { it.slotId == selectedCharacter.slotId }
                    if (slotIndex != -1) {
                        val slot = sessionProfile.slotRoster[slotIndex]
                        val updatedSlot = slot.copy(pose = poseName)  // or whatever structure you use!
                        val updatedSlotRoster = sessionProfile.slotRoster.toMutableList()
                        updatedSlotRoster[slotIndex] = updatedSlot
                        sessionProfile = sessionProfile.copy(slotRoster = updatedSlotRoster)

                        // Optionally: save to Firestore if needed
                        saveSessionProfile(sessionProfile, sessionId)
                    }
                } else {
                    Toast.makeText(this, "Select a pose first!", Toast.LENGTH_SHORT).show()
                }
            }

            takeControl.setOnClickListener {
                val selectedSlot = presentSlots[characterSpinner.selectedItemPosition]

                // --- NEW GUARD CLAUSE ---
                // Check if the selected character is already marked as a player
                if (selectedSlot.profileType == "player") {
                    Toast.makeText(this, "Character is already being controlled by a player", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                // ------------------------

                val oldActiveSlotId = sessionProfile.userMap[userId]?.activeSlotId

                // Update slotRoster with correct profile types
                val updatedSlotRoster = sessionProfile.slotRoster.map { slot ->
                    when (slot.slotId) {
                        oldActiveSlotId -> slot.copy(profileType = "bot") // old controlled: demote to bot
                        selectedSlot.slotId -> slot.copy(profileType = "player") // new controlled: promote to player
                        else -> slot
                    }
                }

                // Update userMap with new activeSlotId
                val updatedUserMap = sessionProfile.userMap.toMutableMap().apply {
                    this[userId] = this[userId]?.copy(activeSlotId = selectedSlot.slotId) ?: return@setOnClickListener
                }

                sessionProfile = sessionProfile.copy(
                    slotRoster = updatedSlotRoster,
                    userMap = updatedUserMap
                )
                saveSessionProfile(sessionProfile, sessionId)
                Toast.makeText(this, "You are now controlling ${selectedSlot.name}", Toast.LENGTH_SHORT).show()
            }
        }

        fun showOptions() {
            moveOptions.visibility = View.GONE
            avatarOptions.visibility = View.GONE
            optionsOptions.visibility = View.VISIBLE

            val resetTypingButton = findViewById<Button>(R.id.resetTyping)
            val returnHomeButton = findViewById<Button>(R.id.returnHome)
            val reportBugButton = findViewById<Button>(R.id.reportBug)
            val directorBtn = findViewById<Button>(R.id.directorBtn)
            val messageCounterTv = findViewById<TextView>(R.id.optionsMessageCounter)


            resetTypingButton.setOnClickListener {
                // Set all slot profiles typing = false
                sessionProfile = sessionProfile.copy(
                    slotRoster = sessionProfile.slotRoster.map { it.copy(typing = false) }
                )
                saveSessionProfile(sessionProfile, sessionId)
                Toast.makeText(this, "Typing reset for all characters!", Toast.LENGTH_SHORT).show()
                updateTypingIndicator()
            }

            returnHomeButton.setOnClickListener {
                val intent = Intent(this, ChatHubActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            }

            reportBugButton.setOnClickListener {
                val editText = EditText(this).apply {
                    hint = "Describe the issue or what you were doing…"
                    minLines = 3
                    maxLines = 6
                    setPadding(32, 32, 32, 32)
                }
                AlertDialog.Builder(this)
                    .setTitle("Report a Bug")
                    .setMessage("Please describe what happened or what you were doing. This will help us fix the issue!")
                    .setView(editText)
                    .setPositiveButton("Send") { _, _ ->
                        val userMessage = editText.text.toString()
                        sendBugReportEmail(userMessage)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            val rpgInstructionsButton = findViewById<Button>(R.id.RPGInstructions)
            rpgInstructionsButton.setOnClickListener {
                showInstructionDialog()
            }

            directorBtn.visibility = if (isHost) View.VISIBLE else View.GONE

            directorBtn.setOnClickListener {
                val currentActiveSlot = sessionProfile.userMap[userId]?.activeSlotId
                val isDirectorNow = currentActiveSlot == "narrator"

                if (isDirectorNow) {
                    // STOP Directing -> Show Picker (Existing Logic)
                    showCharacterPickerDialog(sessionProfile.slotRoster) { selectedSlot ->
                        // When picking a character, we should probably claim them as "player" too
                        val updatedRoster = sessionProfile.slotRoster.map { slot ->
                            if (slot.slotId == selectedSlot.slotId) slot.copy(profileType = "player") else slot
                        }
                        FirebaseFirestore.getInstance().collection("sessions").document(sessionId)
                            .update("slotRoster", updatedRoster)

                        updateUserActiveSlot(selectedSlot.slotId)
                        mySlotId = selectedSlot.slotId
                        Toast.makeText(this, "Returned to ${selectedSlot.name}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // --- START Directing (UPDATED) ---

                    // 1. Release your current character back to "bot" mode
                    if (currentActiveSlot != null && currentActiveSlot != "narrator") {
                        val updatedRoster = sessionProfile.slotRoster.map { slot ->
                            if (slot.slotId == currentActiveSlot) {
                                slot.copy(profileType = "bot") // <--- The Fix
                            } else {
                                slot
                            }
                        }

                        // Save the freed roster to Firestore
                        FirebaseFirestore.getInstance().collection("sessions").document(sessionId)
                            .update("slotRoster", updatedRoster)
                    }

                    // 2. Become Narrator
                    updateUserActiveSlot("narrator")
                    mySlotId = "narrator"
                    Toast.makeText(this, "Director Mode Active", Toast.LENGTH_LONG).show()
                }
            }

            // --- FETCH & DISPLAY COUNT ---
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            if (userId != null) {
                FirebaseFirestore.getInstance().collection("users").document(userId).get()
                    .addOnSuccessListener { doc ->
                        val count = doc.getLong("dailyMessageCount") ?: 0
                        val isPremium = doc.getBoolean("isPremium") ?: false

                        if (isPremium) {
                            messageCounterTv.text = ""
                            messageCounterTv.setTextColor(android.graphics.Color.parseColor("#FFD700")) // Gold
                        } else {
                            messageCounterTv.text = "Messages Today: $count / 70"
                            // Turn red if they are close to the limit
                            if (count >= 50) {
                                messageCounterTv.setTextColor(android.graphics.Color.RED)
                            } else {
                                messageCounterTv.setTextColor(android.graphics.Color.WHITE)
                            }
                        }
                    }
            }
        }

        moveBtn.setOnClickListener { showMove() }
        avatarBtn.setOnClickListener { showAvatar() }
        optionsBtn.setOnClickListener { showOptions() }

        updateRPGToggleVisibility()

        messageEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (ignoreTextWatcher) return
                if (!lastTypingState) setPlayerTyping(true)
                typingTimeoutJob?.cancel()
                typingTimeoutJob = lifecycleScope.launch {
                    delay(2000)
                    setPlayerTyping(false)
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        rollButton.setOnClickListener {
            val statNames = listOf("Strength", "Agility", "Intelligence", "Charisma", "Resolve")
            var selectedStatIndex = 0

            val modifierInput = EditText(this).apply {
                hint = "Extra modifier (default 0)"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
                setText("0")
                setSelectAllOnFocus(true)
            }

            AlertDialog.Builder(this)
                .setTitle("Roll a Stat")
                .setSingleChoiceItems(statNames.toTypedArray(), 0) { _, which ->
                    selectedStatIndex = which
                }
                .setView(modifierInput)
                .setPositiveButton("Roll") { _, _ ->
                    val chosenStat = statNames[selectedStatIndex]
                    val extraMod = modifierInput.text.toString().toIntOrNull() ?: 0
                    // Already have activeSlotId in scope
                    handleDiceRoll(activeSlotId!!, chosenStat, extraMod)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        resendButton.setOnClickListener {
            Log.d("togglebutton debug", "resend button")
            val lastMessage = chatAdapter.getMessages()
                .lastOrNull { it.senderId == sessionProfile.userMap[userId]?.activeSlotId }

            if (lastMessage != null) {
                checkMessageLimit {
                    // Resend same text but with visibility=false and blank text
                    val resendMsg = lastMessage.copy(
                        id = "System",
                        text = "",
                        visibility = false,
                        timestamp = com.google.firebase.Timestamp.now()
                    )
                    activationRound = 0
                    SessionManager.sendMessage(chatId, sessionId, resendMsg)
                    // (Optional: Trigger AI if you want this to start a new round)
                    sendToAI("")
                }
            }
        }

        val expandChatButton = findViewById<ImageButton>(R.id.expandChatButton)

        var isFullscreen = false

        expandChatButton.setOnClickListener {
            val params = chatInputGroup.layoutParams
            if (!isFullscreen) {
                // Expand to fill parent minus 50dp
                val parentHeight = rootView.height
                val newHeight = parentHeight - (50 * resources.displayMetrics.density).toInt()
                params.height = newHeight
                chatInputGroup.layoutParams = params
                isFullscreen = true
                expandChatButton.setImageResource(R.drawable.ic_fullscreen_exit)
            } else {
                // Collapse back to original height (example: 300dp)
                val collapsedHeight = (300 * resources.displayMetrics.density).toInt()
                params.height = collapsedHeight
                chatInputGroup.layoutParams = params
                isFullscreen = false
                expandChatButton.setImageResource(R.drawable.ic_fullscreen)
            }
        }
        // -- TOGGLING VIEWS
        toggleControlButton.setOnClickListener {
            Log.d("togglebutton debug", "toggle control button")
            chatInputGroup.visibility = View.GONE
            controlBox.visibility = View.VISIBLE
            toggleButtonContainer.visibility = View.GONE
            toggleChatButton.visibility = View.VISIBLE
            optionsOptions.visibility = View.VISIBLE
            characterSheetBox.visibility = View.GONE
            rollHistoryBox.visibility = View.GONE
            dockTogglesAbove(R.id.controlBox)
            showOptions()
        }

        toggleChatButton.setOnClickListener {
            Log.d("togglebutton debug", "toggle chat back on button")
            controlBox.visibility = View.GONE
            chatInputGroup.visibility = View.VISIBLE
            toggleButtonContainer.visibility = View.VISIBLE
            toggleChatButton.visibility = View.GONE
            characterSheetBox.visibility = View.GONE
            rollHistoryBox.visibility = View.GONE
            toggleControlButton.visibility = View.VISIBLE
            dockTogglesAbove(R.id.chatInputGroup)
        }

        // -- Charactersheet --
        characterSheetButton.setOnClickListener {
            characterSheetBox.visibility = View.VISIBLE
            controlBox.visibility = View.GONE
            toggleButtonContainer.visibility = View.GONE
            toggleChatButton.visibility = View.VISIBLE
            chatInputGroup.visibility = View.GONE
            rollHistoryBox.visibility = View.GONE
            dockTogglesAbove(R.id.characterSheetBox)
            val tabBar = findViewById<LinearLayout>(R.id.characterTabBar)
            tabBar.removeAllViews()

            // inline: is murder mode enabled?
            val murderEnabled = ((sessionProfile?.modeSettings?.get("murder") as? String)?.let {
                try { Gson().fromJson(it, ModeSettings.MurderSettings::class.java).enabled } catch (_: Exception) { false }
            }) == true

            // inline: your active slot id (use your global if you’ve got it)
            val myActiveSlotId = activeSlotId // fall back if you want: ?: sessionProfile?.userMap?.get(FirebaseAuth.getInstance().currentUser?.uid ?: "")?.activeSlotId

            sessionProfile?.slotRoster?.forEach { slotProfile ->
                val allowed = !murderEnabled || myActiveSlotId.isNullOrBlank() || slotProfile.slotId == myActiveSlotId

                val button = Button(this).apply {
                    text = slotProfile.name
                    isEnabled = allowed
                    alpha = if (allowed) 1f else 0.5f
                    setOnClickListener {
                        if (!allowed) {
                            Toast.makeText(this@MainActivity, "During murder-mystery, you can only open your own sheet.", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        showCharacterSheet(slotProfile)
                    }
                }
                tabBar.addView(button)
            }
        }

        // -- Roll History --
        rollHistoryButton.setOnClickListener {
            characterSheetBox.visibility = View.GONE
            toggleButtonContainer.visibility = View.GONE
            controlBox.visibility = View.GONE
            toggleChatButton.visibility = View.VISIBLE
            chatInputGroup.visibility = View.GONE
            rollHistoryBox.visibility = View.VISIBLE
            rollRecyclerView.visibility = View.VISIBLE
            dockTogglesAbove(R.id.rollHistoryBox)
            fetchAndShowRollHistory()
            Log.d("ROLL_DEBUG", "Starting roll history fetch for slot: $activeSlotId")
        }

        // Send button logic
        sendButton.setOnClickListener {
            if (currentState == ButtonState.SEND) {
                val text = messageEditText.text.toString().trim()
                if (text.isNotEmpty()) {

                    // WRAP THE SEND LOGIC WITH LIMIT CHECK
                    checkMessageLimit {
                        // --- DIRECTOR / SPY LOGIC START ---
                        val isDirector = mySlotId == "narrator"

                        // 1. Determine Sender & Location
                        val finalSenderId: String
                        val finalDisplayName: String
                        val targetArea: String?
                        val targetLocation: String?

                        if (isDirector) {
                            // DIRECTOR MODE: Must rely on Spy variables
                            if (spyingArea != null && spyingLocation != null) {
                                targetArea = spyingArea
                                targetLocation = spyingLocation
                                finalSenderId = "narrator"
                                finalDisplayName = "Narrator"
                            } else {
                                Toast.makeText(this, "Directors must SPY on a location to speak there.", Toast.LENGTH_SHORT).show()
                                return@checkMessageLimit // Abort sending
                            }
                        } else {
                            // REGULAR MODE: Use Player's physical location
                            val myChar = sessionProfile.slotRoster.find { it.slotId == mySlotId }
                            // Safety check
                            if (myChar == null) {
                                Toast.makeText(this, "No character selected.", Toast.LENGTH_SHORT).show()
                                return@checkMessageLimit
                            }
                            targetArea = myChar.lastActiveArea
                            targetLocation = myChar.lastActiveLocation
                            finalSenderId = mySlotId ?: ""
                            finalDisplayName = myChar.name
                        }

                        // 2. Update UI State (Do this before async calls)
                        updateButtonState(ButtonState.INTERRUPT)
                        setPlayerTyping(false)
                        activationRound = 0
                        messageEditText.text.clear()
                        ignoreTextWatcher = false

                        // 3. Construct Message with Overrides
                        val timestamp = com.google.firebase.Timestamp.now()
                        val newMessage = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            senderId = finalSenderId,
                            displayName = finalDisplayName,
                            text = text,
                            timestamp = timestamp,
                            area = targetArea,
                            location = targetLocation,
                            visibility = true
                        )

                        // 4. Send to Firestore
                        SessionManager.sendMessage(chatId, sessionId, newMessage)

                        // 5. Trigger AI Loop (Manually calling what sendMessageAndCallAI used to do)
                        // We pass the new message into the history so the AI sees it immediately
                        processActivationRound(text, sessionProfile.history + newMessage)

                        // --- DIRECTOR / SPY LOGIC END ---
                    }
                }
            } else if (currentState == ButtonState.INTERRUPT) {
                interruptAILoop()
                updateButtonState(ButtonState.SEND)
            }
        }
    }
    
    fun updateRPGToggleVisibility() {
        val isRPG = sessionProfile.modeSettings.containsKey("rpg") ||
                sessionProfile.enabledModes.contains("rpg")
        val isVN = sessionProfile.modeSettings.containsKey("vn") ||
                sessionProfile.enabledModes.contains("visual_novel")
        if (isRPG) {
            rpgToggleContainer.visibility = View.VISIBLE
            rollButton.visibility = View.VISIBLE
        } else {
            if (isVN){
                rpgToggleContainer.visibility = View.VISIBLE
                rollButton.visibility = View.GONE
                rollHistoryButton.visibility = View.GONE
            }else{
                rpgToggleContainer.visibility = View.GONE
                rollButton.visibility = View.GONE
            }
        }

        Log.d("rpg", "is this session an rpg? $isRPG")
    }

    private fun showInstructionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_instructions, null) // See XML below
        val spinner = dialogView.findViewById<Spinner>(R.id.instructionTargetSpinner)
        val instructionContainer = dialogView.findViewById<LinearLayout>(R.id.instructionContainer)

        // Data holding the edits before saving
        // Key: "global" or slotId
        val tempMap = mutableMapOf<String, MutableList<Instruction>>()

        // Initialize Map from current session data
        tempMap["global"] = sessionProfile.globalInstructions.toMutableList()
        sessionProfile.slotRoster.forEach { slot ->
            tempMap[slot.slotId] = slot.instructions.toMutableList()
        }

        // Setup Spinner
        val rosterOptions = sessionProfile.slotRoster.map { it.name to it.slotId }
        val spinnerItems = listOf("All Characters" to "global") + rosterOptions
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, spinnerItems.map { it.first })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        // Helper to save current UI rows into the tempMap
        var currentKey = "global"

        fun saveRowsToMap(key: String) {
            val newList = mutableListOf<Instruction>()
            for (i in 0 until instructionContainer.childCount) {
                val row = instructionContainer.getChildAt(i)
                val et = row.findViewById<EditText>(R.id.instructionText)
                val cb = row.findViewById<CheckBox>(R.id.tempCheckbox)
                val text = et.text.toString().trim()
                if (text.isNotEmpty()) {
                    newList.add(Instruction(text, cb.isChecked))
                }
            }
            tempMap[key] = newList
        }

        fun loadRowsFromMap(key: String) {
            instructionContainer.removeAllViews()
            val instructions = tempMap[key] ?: emptyList()

            // Create 5 rows
            for (i in 0 until 5) {
                val existing = instructions.getOrNull(i)
                val rowView = layoutInflater.inflate(R.layout.item_instruction_row, instructionContainer, false)
                val et = rowView.findViewById<EditText>(R.id.instructionText)
                val cb = rowView.findViewById<CheckBox>(R.id.tempCheckbox)

                if (existing != null) {
                    et.setText(existing.text)
                    cb.isChecked = existing.temporary
                }
                instructionContainer.addView(rowView)
            }
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                // Save previous
                saveRowsToMap(currentKey)

                // Load new
                val (_, newKey) = spinnerItems[position]
                currentKey = newKey
                loadRowsFromMap(newKey)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Initial Load
        loadRowsFromMap("global")

        AlertDialog.Builder(this)
            .setTitle("OOC Instructions")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                saveRowsToMap(currentKey) // Save the last open tab

                // Commit to SessionProfile
                val newRoster = sessionProfile.slotRoster.map { slot ->
                    slot.copy(instructions = tempMap[slot.slotId] ?: emptyList())
                }

                sessionProfile = sessionProfile.copy(
                    globalInstructions = tempMap["global"] ?: emptyList(),
                    slotRoster = newRoster
                )

                saveSessionProfile(sessionProfile, sessionId)
                Toast.makeText(this, "Instructions saved!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun cleanupTemporaryInstructions() {
        var changed = false

        // 1. Clean Global
        if (sessionProfile.globalInstructions.any { it.temporary }) {
            val newGlobal = sessionProfile.globalInstructions.filter { !it.temporary }
            sessionProfile = sessionProfile.copy(globalInstructions = newGlobal)
            changed = true
        }

        // 2. Clean Slots
        val newRoster = sessionProfile.slotRoster.map { slot ->
            if (slot.instructions.any { it.temporary }) {
                changed = true
                slot.copy(instructions = slot.instructions.filter { !it.temporary })
            } else {
                slot
            }
        }

        if (changed) {
            sessionProfile = sessionProfile.copy(slotRoster = newRoster)
            saveSessionProfile(sessionProfile, sessionId)
            Log.d("Instructions", "Cleaned up temporary instructions.")
        }
    }

    private fun showCharacterSheet(slot: SlotProfile) {
        val content = findViewById<LinearLayout>(R.id.characterSheetContent)
        content.removeAllViews()
        val sheet = layoutInflater.inflate(R.layout.item_character_sheet_fantasy, content, false)

        // ----- mode flags -----
        val rpgEnabled = (sessionProfile?.modeSettings?.get("rpg") as? String)?.isNotBlank() == true
        val murderEnabled = ((sessionProfile?.modeSettings?.get("murder") as? String)?.let {
            try { Gson().fromJson(it, ModeSettings.MurderSettings::class.java).enabled } catch (_: Exception) { false }
        }) == true
        val vnEnabled = (sessionProfile?.modeSettings?.get("vn") as? String)?.isNotBlank() == true

        // ----- required views (fail fast if layout ids don’t exist) -----
        fun <T: View> req(id: Int): T =
            sheet.findViewById<T>(id) ?: error("Missing view id in item_character_sheet_fantasy: ${resources.getResourceEntryName(id)}")

        val root = req<androidx.constraintlayout.widget.ConstraintLayout>(R.id.sheetRootColumn)   // <- make root a ConstraintLayout with this id
        val header = req<View>(R.id.headerBlock)
        val rpgSection = sheet.findViewById<View>(R.id.rpgSection)                        // optional
        val vnSection  = sheet.findViewById<View>(R.id.vnSection)                         // optional

        // MoreInfo may be either a container or just the TextView; try both gracefully
        val moreInfoBlock = sheet.findViewById<View>(R.id.moreInfoTV)
            ?: sheet.findViewById<View>(R.id.moreInfo)
        val moreInfoText  = sheet.findViewById<TextView>(R.id.moreInfo)                   // ok if null when using a container w/ inner TextView

        // ----- header visuals -----
        req<TextView>(R.id.nameView).text = slot.name
        slot.avatarUri?.takeIf { it.isNotBlank() }?.let { Glide.with(this).load(it).into(req(R.id.avatarView)) }

        // ----- RPG fill + visibility -----
        val hasRpg = rpgEnabled && (slot.rpgClass.isNotBlank() || slot.stats.isNotEmpty() || (slot.hp > 0 && slot.maxHp > 0))
        rpgSection?.apply {
            visibility = if (hasRpg) View.VISIBLE else View.GONE
            if (hasRpg) {
                sheet.findViewById<TextView>(R.id.classView)?.text   = "Class: ${slot.rpgClass}"
                sheet.findViewById<TextView>(R.id.hpView)?.text      = "HP: ${slot.hp}/${slot.maxHp}"
                sheet.findViewById<TextView>(R.id.defenseView)?.text = "Defense: ${slot.defense}"
                sheet.findViewById<TextView>(R.id.abilitiesView)?.text =
                    if (slot.abilities.isNotBlank()) slot.abilities else "None"

                sheet.findViewById<LinearLayout>(R.id.statusList)?.let { list ->
                    list.removeAllViews()
                    if (slot.statusEffects.isNotEmpty()) slot.statusEffects.forEach { s ->
                        list.addView(TextView(this@MainActivity).apply { text = s })
                    } else list.addView(TextView(this@MainActivity).apply { text = "None" })
                }
                sheet.findViewById<LinearLayout>(R.id.statsList)?.let { list ->
                    list.removeAllViews()
                    slot.stats.forEach { (k, v) -> list.addView(TextView(this@MainActivity).apply { text = "$k: $v" }) }
                }
                sheet.findViewById<LinearLayout>(R.id.equipmentList)?.let { list ->
                    list.removeAllViews()
                    slot.equipment.forEach { item -> list.addView(TextView(this@MainActivity).apply { text = item }) }
                }
            }
        }

        // --- More Info (role + timeline) ---
        val roleRaw = (slot.hiddenRoles ?: "").trim()
        val roleDisplay = when (roleRaw.uppercase()) {
            "VILLAIN" -> "killer"
            "TARGET"  -> "victim"
            // treat everyone else as "innocent" only when murder mode is on
            "HERO", "GM", "SIDEKICK", "" -> if (murderEnabled) "innocent" else ""
            else -> roleRaw.lowercase()
        }

        val timelineText = slot.moreInfo?.takeIf { it.isNotBlank() }
        val headerLine = if (roleDisplay.isNotBlank()) "Role: ${roleDisplay.replaceFirstChar { it.uppercase() }}" else null

// Compose: Role (if any) above timeline (or fallback)
        val composed = buildString {
            headerLine?.let { append(it).append('\n') }
            append(timelineText ?: "Nothing to add here.")
        }

        moreInfoText?.text = composed

// Show the section if we have a timeline OR (in murder mode) a role to display
        val showMoreInfo = (timelineText != null) || (murderEnabled && roleDisplay.isNotBlank())
        moreInfoBlock?.visibility = if (showMoreInfo) View.VISIBLE else View.GONE

        // ----- VN section -----
        val showVn = vnEnabled && !slot.vnRelationships.isNullOrEmpty()
        vnSection?.apply {
            visibility = if (showVn) View.VISIBLE else View.GONE
            if (showVn) {
                val vnList = sheet.findViewById<LinearLayout>(R.id.vnRelationshipsList)
                vnList?.removeAllViews()
                val byKey = (sessionProfile?.slotRoster ?: emptyList()).mapIndexed { idx, s ->
                    ModeSettings.SlotKeys.fromPosition(idx) to s
                }.toMap()
                slot.vnRelationships.forEach { (toKey, rel) ->
                    val other = byKey[toKey] ?: return@forEach
                    val maxL = rel.levels.maxOfOrNull { it.level } ?: 5
                    val filled = rel.currentLevel.coerceIn(0, maxL)
                    val hearts = "❤".repeat(filled) + "♡".repeat((maxL - filled).coerceAtLeast(0))
                    val row = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 8); gravity = Gravity.CENTER_VERTICAL
                    }
                    val icon = ImageView(this@MainActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(64, 64).apply { rightMargin = 12 }
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                    if (!other.avatarUri.isNullOrBlank()) Glide.with(this@MainActivity).load(other.avatarUri).circleCrop().into(icon)
                    else icon.setImageResource(R.drawable.placeholder_avatar)
                    val label = TextView(this@MainActivity).apply { text = "${other.name}  $hearts" }
                    row.addView(icon); row.addView(label)
                    vnList?.addView(row)
                }
            }
        }

        // ----- Constraint wiring: header top, then RPG -> MoreInfo -> VN -----
        fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
        val cs = androidx.constraintlayout.widget.ConstraintSet().apply { clone(root) }

        fun topToParent(id: Int, m: Int) { cs.clear(id, ConstraintSet.TOP); cs.connect(id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, dp(m)) }
        fun topToBottom(id: Int, anchor: Int, m: Int) { cs.clear(id, ConstraintSet.TOP); cs.connect(id, ConstraintSet.TOP, anchor, ConstraintSet.BOTTOM, dp(m)) }

        topToParent(R.id.headerBlock, 8)
        var anchor = R.id.headerBlock
        if (hasRpg && rpgSection != null) { topToBottom(R.id.rpgSection, anchor, 12); anchor = R.id.rpgSection }
        if (murderEnabled && moreInfoBlock != null) { topToBottom(moreInfoBlock.id, anchor, 12); anchor = moreInfoBlock.id }
        if (showVn && vnSection != null) { topToBottom(R.id.vnSection, anchor, 12) }

        cs.applyTo(root)

        // finally attach
        content.addView(sheet)
    }

    private fun Context.dp(n: Int): Int = (n * resources.displayMetrics.density).roundToInt()

    fun dockTogglesAbove(viewBelowId: Int) {
        val root = findViewById<ConstraintLayout>(R.id.chatRoot)
        val cs = ConstraintSet()
        cs.clone(root)
        cs.connect(R.id.toggleButtonContainer, ConstraintSet.BOTTOM, viewBelowId, ConstraintSet.TOP)
        cs.connect(R.id.toggleChatButton, ConstraintSet.BOTTOM, viewBelowId, ConstraintSet.TOP)
        cs.connect(R.id.rpgToggleContainer, ConstraintSet.BOTTOM, viewBelowId, ConstraintSet.TOP)
        cs.applyTo(root)
    }

    fun mapToChatMessage(map: Map<String, Any>): ChatMessage? {
        // Safely extract all needed fields, handle missing/nulls as appropriate
        val id = map["id"] as? String ?: return null
        val senderId = map["senderId"] as? String ?: "narrator"
        val text = map["text"] as? String ?: ""
        val messageType = map["messageType"] as? String ?: "message"        // ... extract the rest
        return ChatMessage(
            id = id,
            senderId = senderId,
            text = text,
            messageType = messageType,
        )
    }

    private fun fetchAndShowRollHistory() {
        val activeSlotId = sessionProfile.userMap[userId]?.activeSlotId ?: return
        Log.d("ROLL_DEBUG", "activeSlotId = $activeSlotId")
        val messagesRef  = FirebaseFirestore.getInstance()
            .collection("sessions")
            .document(sessionId)
            .collection("slotPersonalHistory")
            .document(activeSlotId)
            .collection("messages")

        messagesRef.get().addOnSuccessListener { querySnapshot ->
            Log.d("ROLL_DEBUG", "Found ${querySnapshot.size()} roll messages in Firestore subcollection!")
            val chatMessages = querySnapshot.documents.mapNotNull { doc ->
                doc.data?.let { mapToChatMessage(it) }
            }
            val rollMessages = chatMessages.filter { it.messageType == "roll" }
            rollAdapter.setMessages(rollMessages)
        }
    }

    fun rerollAndUpdateMessage(oldMessage: ChatMessage) {
    val slotId = oldMessage.senderId
    val slot = sessionProfile.slotRoster.find { it.slotId == slotId }
    if (slot == null) {
        Toast.makeText(this, "No character found for dice roll.", Toast.LENGTH_SHORT).show()
        return
    }
    val name = slot.name
    val area = slot.lastActiveArea
    val location = slot.lastActiveLocation
    val roll = (1..20).random()
    // Post result to chat
    val rollMessage = "$name rolled $roll"
    val chatMessage = ChatMessage(
        id = UUID.randomUUID().toString(),
        senderId = slotId,
        text = rollMessage,
        area = area,
        location = location,
        delay = 0,
        timestamp = Timestamp.now(),
        visibility = true,
        messageType = "roll"
    )
    SessionManager.sendMessage(chatId, sessionId, chatMessage)
}

    private fun determineNextSpeakerLocal(userText: String, chatHistory: List<ChatMessage>): String {
        // 0. FORCE OVERRIDE
        Log.d("ai_cycle", "determining speaker")
        val forced = forcedNextSpeakerId
        if (forced != null) {
            forcedNextSpeakerId = null
            runOnUiThread { findViewById<Spinner>(R.id.nextSpeakerSpinner)?.setSelection(0) }
            return forced
        }

        // 1. Determine Target Location (Spy vs Player)
        val activeSlotId = sessionProfile.userMap[userId]?.activeSlotId ?: return ""

        val targetArea: String?
        val targetLocation: String?

        // --- FIX STARTS HERE ---
        if (activeSlotId == "narrator") {
            // If Director: We MUST depend on the Spy variables
            targetArea = spyingArea
            targetLocation = spyingLocation

            if (targetArea == null || targetLocation == null) {
                Log.d("ai_cycle", "Director not spying on valid location; aborting speaker check.")
                return ""
            }
        } else {
            // If Player: Look up the slot
            val playerSlot = sessionProfile.slotRoster.find { it.slotId == activeSlotId } ?: return ""

            // Prioritize Spy location if set (allows player to 'look' into a room and trigger people there)
            // Otherwise use physical location
            targetArea = spyingArea ?: playerSlot.lastActiveArea
            targetLocation = spyingLocation ?: playerSlot.lastActiveLocation
        }
        // --- FIX ENDS HERE ---

        Log.d("ai_cycle", "getting speaker from $targetLocation in $targetArea")

        // 2. Who is here? (Exclude player and placeholders)
        val presentChars = sessionProfile.slotRoster.filter {
            it.profileType != "player" &&
                    !it.isPlaceholder &&
                    it.lastActiveArea == targetArea &&
                    it.lastActiveLocation == targetLocation
        }

        Log.d ("ai_cycle", "character check: ${presentChars.size}")
        if (presentChars.isEmpty()) return "narrator"

        // 3. Did user explicitly name someone?
        val mentionedChar = presentChars.find {
            userText.contains(it.name, ignoreCase = true)
        }
        if (mentionedChar != null) return mentionedChar.slotId

        // 4. Who spoke last?
        val lastSpeakerId = chatHistory.lastOrNull {
            it.senderId != activeSlotId && it.senderId != "narrator"
        }?.senderId

        val candidates = presentChars.filter { it.slotId != lastSpeakerId }

        Log.d ("ai_cycle", "character check: ${candidates.size}")

        // 5. Pick random
        return candidates.randomOrNull()?.slotId ?: presentChars.random().slotId
    }

    private suspend fun fetchRelevantMemories(text: String, charSlotId: String): List<TaggedMemory> {
        val char = sessionProfile.slotRoster.find { it.slotId == charSlotId } ?: return emptyList()
        if (char.memories.isEmpty()) return emptyList()

        // 1. Embed the User's Input (The "Query")
        val queryVector = Director.getEmbedding(text, BuildConfig.OPENAI_API_KEY)

        // If embedding fails (offline/error), fallback to recent memories
        if (queryVector.isEmpty()) return char.memories.takeLast(3)

        // 2. Score every memory
        return char.memories.map { mem ->
            val score = if (mem.embedding.isNotEmpty()) {
                Director.cosineSimilarity(queryVector, mem.embedding)
            } else {
                0.0 // Old memories without vectors get 0 relevance
            }
            mem to score
        }
            .sortedByDescending { it.second } // Highest score first
            .take(3) // Keep top 3
            .map { it.first }
    }

    private fun processActivationRound(input: String, chatHistory: List<ChatMessage>, retryCount: Int = 0) {
        // 1. Recursion Guard
        if (activationRound >= maxActivationRounds) {
            updateButtonState(ButtonState.SEND)
            return
        }
        activationRound++
        updateButtonState(ButtonState.INTERRUPT)
        Log.d("AI_CYCLE", "Round $activationRound (Local Logic)")

        aiJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 2. LOCAL LOGIC: Pick the next speaker
                val nextSlotId = determineNextSpeakerLocal(input, chatHistory)

                // Handle Narrator or "No one here"
                if (nextSlotId == "narrator" || nextSlotId.isBlank()) {
                    // (Optional: You can insert a narrator call here if you want logic for that,
                    // but for now we exit to avoid infinite loops of silence)
                    withContext(Dispatchers.Main) { updateButtonState(ButtonState.SEND) }
                    return@launch
                }
                // 3. Set Typing Indicator
                setSlotTyping(sessionId, nextSlotId, true)
                Log.d("ai_response", "$nextSlotId starting to type")

                // 4. RAG: Fetch Memories locally
                val relevantMemories = fetchRelevantMemories(input, nextSlotId)
                val memoriesMap = mapOf(nextSlotId to relevantMemories)

                // 5. Prepare Data for Prompt
                val slotProfile = sessionProfile.slotRoster.find { it.slotId == nextSlotId }!!
                Log.d("ai_response", "replying as: ${slotProfile.name}")
                val sceneArea = slotProfile.lastActiveArea ?: "Unknown"
                val sceneLocation = slotProfile.lastActiveLocation ?: "Unknown"

                val myPersonalHistory = fetchPersonalHistory(sessionId, nextSlotId)
                val historyString = buildHistoryString(myPersonalHistory.takeLast(10))

                val isStrictSfw = sessionProfile.sfwOnly == true ||
                        slotProfile.sfwOnly == true ||
                        (slotProfile.age ?: 18) < 18

                // Gather Poses
                val currentOutfit = slotProfile.outfits?.find { it.name == slotProfile.currentOutfit }
                val poses = currentOutfit?.poseSlots?.map { it.name } ?: emptyList()

                val playerSlot = sessionProfile.slotRoster.find { it.profileType == "player" }
                val playerArea = playerSlot?.lastActiveArea
                val playerLocation = playerSlot?.lastActiveLocation
                // Helper to normalize strings for comparison
                fun String.normalize(): String = this.trim().lowercase()

                val areaObj = sessionProfile.areas.find {
                    it.name.normalize() == sceneArea.normalize() || it.id == sceneArea
                }
                val locObj = areaObj?.locations?.find {
                    it.name.normalize() == sceneLocation.normalize() || it.id == sceneLocation
                }
                val locDescription = locObj?.description ?: ""

                val sceneSlotIds = sessionProfile.slotRoster
                    .filter { it.lastActiveArea == sceneArea && it.lastActiveLocation == sceneLocation }
                    .map { it.slotId }
                    .distinct()

                val sentinel = "DO NOT INCLUDE THIS CHARACTER IN THE POSES SECTION"
                val isNSFW = !isStrictSfw

                val condensedCharacterInfo = sessionProfile.slotRoster
                    .filter { it.lastActiveArea == sceneArea && it.lastActiveLocation == sceneLocation }
                    .associate { profile ->
                        val outfits = profile.outfits.orEmpty()
                        val currentName = profile.currentOutfit?.trim().orEmpty()
                        val chosenOutfit = outfits.firstOrNull { it.name.trim().equals(currentName, ignoreCase = true) }
                            ?: outfits.firstOrNull()

                        val poseSlots = (chosenOutfit?.poseSlots ?: outfits.flatMap { it.poseSlots }).orEmpty()
                        val (nsfwSlots, sfwSlots) = poseSlots.partition { it.nsfw }

                        val sfwNames = sfwSlots.map { it.name.trim() }.filter { it.isNotEmpty() }.distinct()
                        val nsfwNames = nsfwSlots.map { it.name.trim() }.filter { it.isNotEmpty() }.distinct()

                        val info = mutableMapOf<String, Any?>()
                        info["summary"] = profile.summary
                        info["pose"] = profile.pose  // String?


                        if (isNSFW) {
                            if (sfwNames.isEmpty() && nsfwNames.isEmpty()) {
                                info["available_poses"] = sentinel
                            } else {
                                info["available_poses"] = sfwNames
                                info["available_poses_nsfw"] = nsfwNames
                            }
                        } else {
                            info["available_poses"] = if (sfwNames.isEmpty()) sentinel else sfwNames
                        }

                        profile.slotId to info
                    }

                // 6. Build the Roleplay Prompt
                val roleplayPrompt = PromptBuilder.buildRoleplayPrompt(
                    slotProfile = slotProfile,
                    sessionProfile = sessionProfile,
                    modeSettings = sessionProfile.modeSettings,
                    sessionSummary = sessionProfile.sessionDescription + sessionProfile.secretDescription,
                    sceneSlotIds = sceneSlotIds,
                    condensedCharacterInfo = condensedCharacterInfo,
                    chatHistory = historyString,
                    memories = memoriesMap,
                    poses = poses,
                    locationDescription = locDescription
                )
                Log.d("ai_cycle", "using prompt: $roleplayPrompt")

                // 7. SFW / NSFW Routing (The Logic Check)

                val vnPrompt = buildVNPrompt(
                    slotProfile = slotProfile,
                    sessionProfile = sessionProfile
                )

                val NPCGenPrompt = buildNPCGeneration(sessionProfile.areas)
                val vnModeEnabled = sessionProfile.modeSettings["visual_novel"] == "true" ||
                        !(sessionProfile.modeSettings["vn"] as? String).isNullOrBlank()

                val vnCheckedPrompt = if (vnModeEnabled) {
                    roleplayPrompt + NPCGenPrompt + vnPrompt
                } else {
                    roleplayPrompt + NPCGenPrompt
                }


                val nsfwinstructions =  "/n - Be descriptive and explicit. The more sexual and erotic the better"
                val nsfwRejection = "\n - If responding to not safe for work messages, reject them while remaining in character."
                val nsfwRejectionPrompt = vnCheckedPrompt + nsfwRejection
                val nsfwPrompt = vnCheckedPrompt + nsfwinstructions

                val responseText = if (isStrictSfw) {
                    // OpenAI (Smart, Safe)
                    Facilitator.callOpenAiApi(nsfwRejectionPrompt, BuildConfig.OPENAI_API_KEY)
                } else {
                    // Mixtral (Unfiltered)
                    Facilitator.callMixtralApi(nsfwPrompt, BuildConfig.MIXTRAL_API_KEY)
                }

                // 8. Parse Response
                val result = FacilitatorResponseParser.parseRoleplayAIResponse(
                    responseText,
                    nextSlotId,
                    sessionProfile.slotRoster
                )
                Log.d("ai_response", "recieved: $result")
                // 9. Handle New Memories (UPDATED)
                val newMemory = result.newMemory
                if (newMemory != null) {
                    val vector = Director.getEmbedding(newMemory.text, BuildConfig.OPENAI_API_KEY)

                    // --- NEW LOGIC START ---
                    // Use the first message's ID as the Memory ID to link them
                    val firstMsgId = result.messages.firstOrNull()?.id ?: UUID.randomUUID().toString()
                    val allMsgIds = result.messages.map { it.id }
                    // --- NEW LOGIC END ---

                    val updatedRoster = sessionProfile.slotRoster.map { s ->
                        if (s.slotId == nextSlotId) {
                            val updatedMems = s.memories.toMutableList()
                            updatedMems.add(
                                TaggedMemory(
                                    id = firstMsgId,        // <--- LINKED ID
                                    tags = newMemory.tags,
                                    text = newMemory.text,
                                    messageIds = allMsgIds, // <--- LINKED ID LIST
                                    embedding = vector
                                )
                            )
                            s.copy(memories = updatedMems)
                        } else s
                    }
                    sessionProfile = sessionProfile.copy(slotRoster = updatedRoster)
                }


                handleRPGActionList(result.actions)

                // 10. Update UI & Save
                withContext(Dispatchers.Main) {
                    // Stop typing
                    setSlotTyping(sessionId, nextSlotId, false)

                    // Save Session Data (Memories, etc)
                    saveSessionProfile(sessionProfile, sessionId)


                    val enrichedMessages = result.messages.map { msg ->
                        val senderSlot = sessionProfile.slotRoster.find { it.slotId == msg.senderId }
                        // Filter empty poses from the map if necessary
                        val cleanedPose = msg.pose?.filterValues { !it.isNullOrBlank() }

                        msg.copy(
                            displayName = senderSlot?.name ?: "Bot",
                            area = senderSlot?.lastActiveArea,
                            location = senderSlot?.lastActiveLocation,
                            pose = cleanedPose,
                            visibility = true,
                            timestamp = msg.timestamp ?: com.google.firebase.Timestamp.now()
                        )
                    }
                    Log.d("ai_response", "enriched")

                    // Display Messages
                    saveMessagesSequentially(enrichedMessages, sessionId, chatId)
                    Log.d("ai_response", "saved")

                    Log.d ("ai_response", "$nextSlotId needs to stop typing")
                    setSlotTyping(sessionId, nextSlotId, false)
                    // 11. Recurse (Keep the conversation going)
                    if (activationRound < maxActivationRounds) {
                        val updatedHistory = chatAdapter.getMessages()
                        processActivationRound("", updatedHistory)
                        setSlotTyping(sessionId, nextSlotId, false)
                    } else {
                        updateButtonState(ButtonState.SEND)
                        setSlotTyping(sessionId, nextSlotId, false)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "ProcessActivationRound Failed", e)
                withContext(Dispatchers.Main) {
                    setSlotTyping(sessionId, "", false) // Reset all
                    updateButtonState(ButtonState.SEND)
                }
            }
        }
    }

    private fun processOneonOneRound(input: String, chatHistory: List<ChatMessage>, retryCount: Int = 0) {
        // 1. Recursion Guard
        if (activationRound >= maxActivationRounds) {
            updateButtonState(ButtonState.SEND)
            return
        }
        activationRound++
        updateButtonState(ButtonState.INTERRUPT)
        Log.d("AI_CYCLE", "Round $activationRound (One-on-One Logic)")

        aiJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 2. LOCAL LOGIC: Pick the next speaker (The only other person!)
                val characterSlot = sessionProfile.slotRoster.firstOrNull { it.profileType != "player" }

                if (characterSlot == null) {
                    Log.e("AI_CYCLE", "No character found for 1-on-1 session!")
                    withContext(Dispatchers.Main) { updateButtonState(ButtonState.SEND) }
                    return@launch
                }

                val nextSlotId = characterSlot.slotId

                // 3. Set Typing Indicator
                setSlotTyping(sessionId, nextSlotId, true)

                // 4. RAG: Fetch Memories locally
                val relevantMemories = fetchRelevantMemories(input, nextSlotId)
                val memoriesMap = mapOf(nextSlotId to relevantMemories)

                // 5. Prepare Data for Prompt
                val slotProfile = sessionProfile.slotRoster.find { it.slotId == nextSlotId }!!

                Log.d("ai_response", "replying as: ${slotProfile.name}")
                val sceneArea = slotProfile.lastActiveArea ?: "Unknown"
                val sceneLocation = slotProfile.lastActiveLocation ?: "Unknown"
                val myPersonalHistory = fetchPersonalHistory(sessionId, nextSlotId)
                val historyString = buildHistoryString(myPersonalHistory.takeLast(10))

                val isStrictSfw = sessionProfile.sfwOnly == true ||
                        slotProfile.sfwOnly == true ||
                        (slotProfile.age ?: 18) < 18

                // Gather Poses
                val currentOutfit = slotProfile.outfits?.find { it.name == slotProfile.currentOutfit }
                val poses = currentOutfit?.poseSlots?.map { it.name } ?: emptyList()

                // Calculate Location Description
                val playerSlot = sessionProfile.slotRoster.find { it.profileType == "player" }
                val playerArea = playerSlot?.lastActiveArea
                val playerLocation = playerSlot?.lastActiveLocation

                fun String.normalize(): String = this.trim().lowercase()

                val areaObj = sessionProfile.areas.find {
                    it.name.normalize() == sceneArea.normalize() || it.id == sceneArea
                }
                val locObj = areaObj?.locations?.find {
                    it.name.normalize() == sceneLocation.normalize() || it.id == sceneLocation
                }
                val locDescription = locObj?.description ?: ""

                val sceneSlotIds = sessionProfile.slotRoster
                    .filter { it.lastActiveArea == sceneArea && it.lastActiveLocation == sceneLocation }
                    .map { it.slotId }
                    .distinct()

                val sentinel = "DO NOT INCLUDE THIS CHARACTER IN THE POSES SECTION"
                val isNSFW = !isStrictSfw

                val condensedCharacterInfo = sessionProfile.slotRoster
                    .filter { it.lastActiveArea == sceneArea && it.lastActiveLocation == sceneLocation }
                    .associate { profile ->
                        val outfits = profile.outfits.orEmpty()
                        val currentName = profile.currentOutfit?.trim().orEmpty()
                        val chosenOutfit = outfits.firstOrNull { it.name.trim().equals(currentName, ignoreCase = true) }
                            ?: outfits.firstOrNull()

                        val poseSlots = (chosenOutfit?.poseSlots ?: outfits.flatMap { it.poseSlots }).orEmpty()
                        val (nsfwSlots, sfwSlots) = poseSlots.partition { it.nsfw }

                        val sfwNames = sfwSlots.map { it.name.trim() }.filter { it.isNotEmpty() }.distinct()
                        val nsfwNames = nsfwSlots.map { it.name.trim() }.filter { it.isNotEmpty() }.distinct()

                        val info = mutableMapOf<String, Any?>()
                        info["summary"] = profile.summary
                        info["pose"] = profile.pose

                        if (isNSFW) {
                            if (sfwNames.isEmpty() && nsfwNames.isEmpty()) {
                                info["available_poses"] = sentinel
                            } else {
                                info["available_poses"] = sfwNames
                                info["available_poses_nsfw"] = nsfwNames
                            }
                        } else {
                            info["available_poses"] = if (sfwNames.isEmpty()) sentinel else sfwNames
                        }

                        profile.slotId to info
                    }

                // 6. Build Prompt
                val roleplayPrompt = PromptBuilder.buildRoleplayPrompt(
                    slotProfile = slotProfile,
                    sessionProfile = sessionProfile,
                    modeSettings = sessionProfile.modeSettings,
                    sessionSummary = sessionProfile.sessionDescription + sessionProfile.secretDescription,
                    sceneSlotIds = sceneSlotIds,
                    condensedCharacterInfo = condensedCharacterInfo,
                    chatHistory = historyString,
                    memories = memoriesMap,
                    poses = poses,
                    locationDescription = locDescription
                )
                Log.d("ai_cycle", "One-on-One Prompt: $roleplayPrompt")

                // 7. VN / SFW Layering
                val vnPrompt = buildVNPrompt(
                    slotProfile = slotProfile,
                    sessionProfile = sessionProfile
                )

                val vnModeEnabled = sessionProfile.modeSettings["visual_novel"] == "true" ||
                        !(sessionProfile.modeSettings["vn"] as? String).isNullOrBlank()

                val vnCheckedPrompt = if (vnModeEnabled) {
                    roleplayPrompt + "\n\n" + vnPrompt
                } else {
                    roleplayPrompt
                }

                val nsfwinstructions =  "\n - Be descriptive and explicit. The more sexual and erotic the better"
                val nsfwRejection = "\n - If responding to not safe for work messages, reject them while remaining in character."
                val nsfwRejectionPrompt = vnCheckedPrompt + nsfwRejection
                val nsfwPrompt = vnCheckedPrompt + nsfwinstructions

                val responseText = if (isStrictSfw) {
                    Facilitator.callOpenAiApi(nsfwRejectionPrompt, BuildConfig.OPENAI_API_KEY)
                } else {
                    Facilitator.callMixtralApi(nsfwPrompt, BuildConfig.MIXTRAL_API_KEY)
                }

                // 8. Parse
                val result = FacilitatorResponseParser.parseRoleplayAIResponse(
                    responseText,
                    nextSlotId,
                    sessionProfile.slotRoster
                )
                Log.d("ai_response", "recieved: $result")

                // 9. Handle New Memories (UPDATED)
                val newMemory = result.newMemory
                if (newMemory != null) {
                    val vector = Director.getEmbedding(newMemory.text, BuildConfig.OPENAI_API_KEY)

                    // --- NEW LOGIC START ---
                    // Use the first message's ID as the Memory ID to link them
                    val firstMsgId = result.messages.firstOrNull()?.id ?: UUID.randomUUID().toString()
                    val allMsgIds = result.messages.map { it.id }
                    // --- NEW LOGIC END ---

                    val updatedRoster = sessionProfile.slotRoster.map { s ->
                        if (s.slotId == nextSlotId) {
                            val updatedMems = s.memories.toMutableList()
                            updatedMems.add(
                                TaggedMemory(
                                    id = firstMsgId,        // <--- LINKED ID
                                    tags = newMemory.tags,
                                    text = newMemory.text,
                                    messageIds = allMsgIds, // <--- LINKED ID LIST
                                    embedding = vector
                                )
                            )
                            s.copy(memories = updatedMems)
                        } else s
                    }
                    sessionProfile = sessionProfile.copy(slotRoster = updatedRoster)
                }

                handleRPGActionList(result.actions)

                setSlotTyping(sessionId, nextSlotId, false)
                // 10. Save & Update
                withContext(Dispatchers.Main) {
                    setSlotTyping(sessionId, nextSlotId, false)
                    saveSessionProfile(sessionProfile, sessionId)

                    val enrichedMessages = result.messages.map { msg ->
                        val senderSlot = sessionProfile.slotRoster.find { it.slotId == msg.senderId }
                        val cleanedPose = msg.pose?.filterValues { !it.isNullOrBlank() }

                        msg.copy(
                            displayName = senderSlot?.name ?: "Bot",
                            area = senderSlot?.lastActiveArea,
                            location = senderSlot?.lastActiveLocation,
                            pose = cleanedPose,
                            visibility = true,
                            timestamp = msg.timestamp ?: com.google.firebase.Timestamp.now()
                        )
                    }

                    saveMessagesSequentially(enrichedMessages, sessionId, chatId)

                    // 11. Recurse (FIXED: Calls processOneonOneRound, NOT ActivationRound)
                    if (activationRound < maxActivationRounds) {
                        val updatedHistory = chatAdapter.getMessages()
                        processOneonOneRound("", updatedHistory)
                        setSlotTyping(sessionId, nextSlotId, false)
                    } else {
                        updateButtonState(ButtonState.SEND)
                        setSlotTyping(sessionId, nextSlotId, false)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "ProcessOneonOneRound Failed", e)
                withContext(Dispatchers.Main) {
                    setSlotTyping(sessionId, "", false)
                    updateButtonState(ButtonState.SEND)
                }
            }
        }
    }

    private fun processAboveTableRound(input: String, chatHistory: List<ChatMessage>, retryCount: Int = 0, maxRetries: Int = 4) {
        // 1. Recursion Guard
        if (activationRound >= maxActivationRounds) {
            updateButtonState(ButtonState.SEND)
            return
        }
        activationRound++
        updateButtonState(ButtonState.INTERRUPT)
        Log.d("AI_CYCLE", "Round $activationRound (Local Logic)")

        aiJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 2. LOCAL LOGIC: Pick the next speaker
                val nextSlotId = determineNextSpeakerLocal(input, chatHistory)

                // Handle Narrator or "No one here"
                if (nextSlotId == "narrator" || nextSlotId.isBlank()) {
                    // (Optional: You can insert a narrator call here if you want logic for that,
                    // but for now we exit to avoid infinite loops of silence)
                    withContext(Dispatchers.Main) { updateButtonState(ButtonState.SEND) }
                    return@launch
                }
                // 3. Set Typing Indicator
                setSlotTyping(sessionId, nextSlotId, true)

                // 4. RAG: Fetch Memories locally
                val relevantMemories = fetchRelevantMemories(input, nextSlotId)
                val memoriesMap = mapOf(nextSlotId to relevantMemories)

                // 5. Prepare Data for Prompt
                val slotProfile = sessionProfile.slotRoster.find { it.slotId == nextSlotId }!!

                Log.d("ai_response", "replying as: ${slotProfile.name}")
                val sceneArea = slotProfile.lastActiveArea ?: "Unknown"
                val sceneLocation = slotProfile.lastActiveLocation ?: "Unknown"
                val myPersonalHistory = fetchPersonalHistory(sessionId, nextSlotId)
                val historyString = buildHistoryString(myPersonalHistory.takeLast(10))

                val isStrictSfw = sessionProfile.sfwOnly == true ||
                        slotProfile.sfwOnly == true ||
                        (slotProfile.age ?: 18) < 18

                // Gather Poses
                val currentOutfit = slotProfile.outfits?.find { it.name == slotProfile.currentOutfit }
                val poses = currentOutfit?.poseSlots?.map { it.name } ?: emptyList()

                val playerSlot = sessionProfile.slotRoster.find { it.profileType == "player" }

                // Helper to normalize strings for comparison
                fun String.normalize(): String = this.trim().lowercase()

                val areaObj = sessionProfile.areas.find {
                    it.name.normalize() == sceneArea.normalize() || it.id == sceneArea
                }
                val locObj = areaObj?.locations?.find {
                    it.name.normalize() == sceneLocation.normalize() || it.id == sceneLocation
                }
                val locDescription = locObj?.description ?: ""

                val sceneSlotIds = sessionProfile.slotRoster
                    .filter { it.lastActiveArea == sceneArea && it.lastActiveLocation == sceneLocation }
                    .map { it.slotId }
                    .distinct()

                val sentinel = "DO NOT INCLUDE THIS CHARACTER IN THE POSES SECTION"
                val isNSFW = !isStrictSfw

                val condensedCharacterInfo = sessionProfile.slotRoster
                    .filter { it.lastActiveArea == sceneArea && it.lastActiveLocation == sceneLocation }
                    .associate { profile ->
                        val outfits = profile.outfits.orEmpty()
                        val currentName = profile.currentOutfit?.trim().orEmpty()
                        val chosenOutfit = outfits.firstOrNull { it.name.trim().equals(currentName, ignoreCase = true) }
                            ?: outfits.firstOrNull()

                        val poseSlots = (chosenOutfit?.poseSlots ?: outfits.flatMap { it.poseSlots }).orEmpty()
                        val (nsfwSlots, sfwSlots) = poseSlots.partition { it.nsfw }

                        val sfwNames = sfwSlots.map { it.name.trim() }.filter { it.isNotEmpty() }.distinct()
                        val nsfwNames = nsfwSlots.map { it.name.trim() }.filter { it.isNotEmpty() }.distinct()

                        val info = mutableMapOf<String, Any?>()
                        info["summary"] = profile.summary
                        info["pose"] = profile.pose  // String?


                        if (isNSFW) {
                            if (sfwNames.isEmpty() && nsfwNames.isEmpty()) {
                                info["available_poses"] = sentinel
                            } else {
                                info["available_poses"] = sfwNames
                                info["available_poses_nsfw"] = nsfwNames
                            }
                        } else {
                            info["available_poses"] = if (sfwNames.isEmpty()) sentinel else sfwNames
                        }

                        profile.slotId to info
                    }

                // 6. Build the Roleplay Prompt
                val roleplayPrompt = PromptBuilder.buildRoleplayPrompt(
                    slotProfile = slotProfile,
                    sessionProfile = sessionProfile,
                    modeSettings = sessionProfile.modeSettings,
                    sessionSummary = sessionProfile.sessionDescription + sessionProfile.secretDescription,
                    sceneSlotIds = sceneSlotIds,
                    condensedCharacterInfo = condensedCharacterInfo,
                    chatHistory = historyString,
                    memories = memoriesMap,
                    poses = poses,
                    locationDescription = locDescription
                ) + buildDiceRoll()
                Log.d("ai_cycle", "using prompt: $roleplayPrompt")

                // 7. SFW / NSFW Routing (The Logic Check)

                val act = sessionProfile.acts.getOrNull(sessionProfile.currentAct)
                val gmPrompt = PromptBuilder.buildGMPrompt(
                    gmSlot = slotProfile,
                    act = act
                )


                val gmProfile = sessionProfile.slotRoster.find { it.hiddenRoles == "GM" }
                val playerPrompt = PromptBuilder.buildPlayerPrompt(
                    playerSlot = slotProfile,
                    gmSlot = gmProfile!!
                )

                val vnPrompt = buildVNPrompt(
                    slotProfile = slotProfile,
                    sessionProfile = sessionProfile
                )

                val vnModeEnabled = sessionProfile.modeSettings["visual_novel"] == "true" ||
                        !(sessionProfile.modeSettings["vn"] as? String).isNullOrBlank()

                val checkedroleplayPrompt = if (slotProfile == gmProfile){
                    PromptBuilder.buildRPGLiteRules() + roleplayPrompt + gmPrompt
                } else {
                    PromptBuilder.buildRPGLiteRules() + roleplayPrompt + playerPrompt
                }

                val vnCheckedPrompt = if (vnModeEnabled) {
                    checkedroleplayPrompt + "\n\n" + vnPrompt
                } else {
                    checkedroleplayPrompt
                }

                val nsfwinstructions =  "/n - Be descriptive and explicit. The more sexual and erotic the better"
                val nsfwRejection = "\n - If responding to not safe for work messages, reject them while remaining in character."
                val nsfwRejectionPrompt = vnCheckedPrompt + nsfwRejection
                val nsfwPrompt = vnCheckedPrompt + nsfwinstructions

                val responseText = if (isStrictSfw) {
                    // OpenAI (Smart, Safe)
                    Facilitator.callOpenAiApi(nsfwRejectionPrompt, BuildConfig.OPENAI_API_KEY)
                } else {
                    Facilitator.callMixtralApi(nsfwPrompt, BuildConfig.MIXTRAL_API_KEY)
                }

                // 8. Parse Response
                val result = FacilitatorResponseParser.parseRoleplayAIResponse(
                    responseText,
                    nextSlotId,
                    sessionProfile.slotRoster
                )
                Log.d("ai_response", "recieved: $result")
                // 9. Handle New Memories (UPDATED)
                val newMemory = result.newMemory
                if (newMemory != null) {
                    val vector = Director.getEmbedding(newMemory.text, BuildConfig.OPENAI_API_KEY)

                    // --- NEW LOGIC START ---
                    // Use the first message's ID as the Memory ID to link them
                    val firstMsgId = result.messages.firstOrNull()?.id ?: UUID.randomUUID().toString()
                    val allMsgIds = result.messages.map { it.id }
                    // --- NEW LOGIC END ---

                    val updatedRoster = sessionProfile.slotRoster.map { s ->
                        if (s.slotId == nextSlotId) {
                            val updatedMems = s.memories.toMutableList()
                            updatedMems.add(
                                TaggedMemory(
                                    id = firstMsgId,        // <--- LINKED ID
                                    tags = newMemory.tags,
                                    text = newMemory.text,
                                    messageIds = allMsgIds, // <--- LINKED ID LIST
                                    embedding = vector
                                )
                            )
                            s.copy(memories = updatedMems)
                        } else s
                    }
                    sessionProfile = sessionProfile.copy(slotRoster = updatedRoster)
                }

                handleRPGActionList(result.actions)
                setSlotTyping(sessionId, nextSlotId, false)

                // 10. Update UI & Save
                withContext(Dispatchers.Main) {
                    // Stop typing
                    setSlotTyping(sessionId, nextSlotId, false)

                    // Save Session Data (Memories, etc)
                    saveSessionProfile(sessionProfile, sessionId)

                    val enrichedMessages = result.messages.map { msg ->
                        val senderSlot = sessionProfile.slotRoster.find { it.slotId == msg.senderId }
                        // Filter empty poses from the map if necessary
                        val cleanedPose = msg.pose?.filterValues { !it.isNullOrBlank() }

                        msg.copy(
                            displayName = senderSlot?.name ?: "Bot",
                            area = senderSlot?.lastActiveArea,       // <--- Vital!
                            location = senderSlot?.lastActiveLocation, // <--- Vital!
                            pose = cleanedPose,
                            visibility = true,
                            timestamp = msg.timestamp ?: com.google.firebase.Timestamp.now()
                        )
                    }

                    // Display Messages
                    saveMessagesSequentially(enrichedMessages, sessionId, chatId)

                    // 11. Recurse (Keep the conversation going)
                    if (activationRound < maxActivationRounds) {
                        val updatedHistory = chatAdapter.getMessages()
                        processActivationRound("", updatedHistory)
                        setSlotTyping(sessionId, nextSlotId, false)
                    } else {
                        updateButtonState(ButtonState.SEND)
                        setSlotTyping(sessionId, nextSlotId, false)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "ProcessActivationRound Failed", e)
                withContext(Dispatchers.Main) {
                    setSlotTyping(sessionId, "", false) // Reset all
                    updateButtonState(ButtonState.SEND)
                }
            }
        }
    }

    private fun determineNextSpeakerOnTable(input: String, chatHistory: List<ChatMessage>): String {
        // 1. FORCED OVERRIDE
        val forced = forcedNextSpeakerId
        if (forced != null) {
            forcedNextSpeakerId = null
            runOnUiThread {
                findViewById<Spinner>(R.id.nextSpeakerSpinner)?.setSelection(0)
            }
            return forced
        }

        val activeSlotId = sessionProfile.userMap[userId]?.activeSlotId ?: return ""

        // --- FIX STARTS HERE ---
        // Don't try to look up "narrator" in the slot roster.
        val playerSlot = if (activeSlotId == "narrator") null else sessionProfile.slotRoster.find { it.slotId == activeSlotId }

        // Safety: If I'm a regular player but my slot is missing, abort.
        if (activeSlotId != "narrator" && playerSlot == null) return ""

        // 2. CHECK FOR TRIGGER: Movement
        // Only regular players trigger the "You entered the room" narration.
        // Directors spying on a room shouldn't trigger this just by sending a message.
        if (playerSlot != null) {
            val lastMsg = chatHistory.lastOrNull { it.senderId == activeSlotId }
            if (lastMsg != null) {
                if (lastMsg.area != playerSlot.lastActiveArea || lastMsg.location != playerSlot.lastActiveLocation) {
                    return "narrator"
                }
            }
        }
        // --- FIX ENDS HERE ---

        // 3. CHECK FOR TRIGGER: Dice Rolls
        // (Valid for Director too: if Director rolls, AI can interpret result)
        val lastSystemMsg = chatHistory.lastOrNull()
        if (lastSystemMsg?.senderId == "system" && lastSystemMsg.text.contains("rolled", ignoreCase = true)) {
            return "narrator"
        }

        // 4. CHECK FOR TRIGGER: Sensory Keywords
        // (Valid for Director too: Director types "What do I see?", AI describes spy room)
        val sensoryWords = listOf("look", "see", "search", "investigate", "listen", "smell", "spot", "check")
        if (sensoryWords.any { input.contains(it, ignoreCase = true) }) {
            return "narrator"
        }

        // 5. Delegate to Local Logic
        // (This now safely handles "narrator" + Spy variables because we fixed it earlier)
        return determineNextSpeakerLocal(input, chatHistory)
    }

    private fun processOnTableRPGRound(input: String, chatHistory: List<ChatMessage>, retryCount: Int = 0) {
        if (activationRound >= maxActivationRounds) {
            updateButtonState(ButtonState.SEND)
            return
        }
        activationRound++
        updateButtonState(ButtonState.INTERRUPT)
        Log.d("AI_CYCLE", "Round $activationRound (On Table Logic)")

        aiJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Decide Speaker (using the new Trigger Logic)
                val nextSlotId = determineNextSpeakerOnTable(input, chatHistory)

                if (nextSlotId.isBlank()) {
                    withContext(Dispatchers.Main) { updateButtonState(ButtonState.SEND) }
                    return@launch
                }

                // 2. Setup Context
                setSlotTyping(sessionId, nextSlotId, true)
                val relevantMemories = fetchRelevantMemories(input, nextSlotId) // RAG still works for GM!
                val memoriesMap = mapOf(nextSlotId to relevantMemories)

                val slotProfile = sessionProfile.slotRoster.find { it.slotId == nextSlotId }!!
                Log.d("ai_response", "replying as: ${slotProfile.name}")
                val sceneArea = slotProfile.lastActiveArea ?: "Unknown"
                val sceneLocation = slotProfile.lastActiveLocation ?: "Unknown"

                // 3. GM-Specific Context
                // If next is Narrator, we need to know the Scene info
                val playerSlot = sessionProfile.slotRoster.find { it.profileType == "player" }
                val areaName = playerSlot?.lastActiveArea
                val locName = playerSlot?.lastActiveLocation


                // Helper to normalize strings for comparison
                fun String.normalize(): String = this.trim().lowercase()

                val areaObj = sessionProfile.areas.find {
                    it.name.normalize() == sceneArea.normalize() || it.id == sceneArea
                }
                val locObj = areaObj?.locations?.find {
                    it.name.normalize() == sceneLocation.normalize() || it.id == sceneLocation
                }
                val locDescription = locObj?.description ?: ""

                val sceneSlotIds = sessionProfile.slotRoster
                    .filter { it.lastActiveArea == sceneArea && it.lastActiveLocation == sceneLocation }
                    .map { it.slotId }
                    .distinct()

                val sentinel = "DO NOT INCLUDE THIS CHARACTER IN THE POSES SECTION"

                val isStrictSfw = sessionProfile.sfwOnly == true ||
                        slotProfile.sfwOnly == true ||
                        (slotProfile.age ?: 18) < 18

                val isNSFW = !isStrictSfw


                val currentOutfit = slotProfile.outfits?.find { it.name == slotProfile.currentOutfit }
                val poses = currentOutfit?.poseSlots?.map { it.name } ?: emptyList()

                val condensedCharacterInfo = sessionProfile.slotRoster
                    .filter { it.lastActiveArea == sceneArea && it.lastActiveLocation == sceneLocation }
                    .associate { profile ->
                        val outfits = profile.outfits.orEmpty()
                        val currentName = profile.currentOutfit?.trim().orEmpty()
                        val chosenOutfit = outfits.firstOrNull { it.name.trim().equals(currentName, ignoreCase = true) }
                            ?: outfits.firstOrNull()

                        val poseSlots = (chosenOutfit?.poseSlots ?: outfits.flatMap { it.poseSlots }).orEmpty()
                        val (nsfwSlots, sfwSlots) = poseSlots.partition { it.nsfw }

                        val sfwNames = sfwSlots.map { it.name.trim() }.filter { it.isNotEmpty() }.distinct()
                        val nsfwNames = nsfwSlots.map { it.name.trim() }.filter { it.isNotEmpty() }.distinct()

                        val info = mutableMapOf<String, Any?>()
                        info["summary"] = profile.summary
                        info["pose"] = profile.pose  // String?


                        if (isNSFW) {
                            if (sfwNames.isEmpty() && nsfwNames.isEmpty()) {
                                info["available_poses"] = sentinel
                            } else {
                                info["available_poses"] = sfwNames
                                info["available_poses_nsfw"] = nsfwNames
                            }
                        } else {
                            info["available_poses"] = if (sfwNames.isEmpty()) sentinel else sfwNames
                        }

                        profile.slotId to info
                    }

                // 4. Build Prompt
                val historyString = buildHistoryString(chatHistory.takeLast(10))


                // VISIBLE CLUES/ITEMS: ${cluesInRoom.joinToString { it.title }}
                // BASE PROMPT
                var prompt = ""

                if (nextSlotId == "narrator") {
                    prompt = """
                    You are the Game Master/Narrator for a Tabletop RPG.
                    CURRENT SCENE: $areaName - $locName
                    SCENE DESCRIPTION: $locDescription
                    
                    RULES:
                    1. Describe the environment vividly.
                    2. If the user just rolled dice (see history), interpret the result (High=Success, Low=Fail).
                    3. If the user moved, describe the new area.
                    4. Keep it under 150 words.
                    5. Be neutral and fair.
                """.trimIndent()
                } else {
                    // It's an NPC speaking
                    val vnPrompt = buildVNPrompt(
                        slotProfile = slotProfile,
                        sessionProfile = sessionProfile
                    )
                    prompt = PromptBuilder.buildRoleplayPrompt(
                        slotProfile = slotProfile,
                        sessionProfile = sessionProfile,
                        modeSettings = sessionProfile.modeSettings,
                        sessionSummary = sessionProfile.sessionDescription,
                        sceneSlotIds = sceneSlotIds, // Optimize later
                        condensedCharacterInfo = condensedCharacterInfo,
                        chatHistory = historyString,
                        memories = memoriesMap,
                        poses = poses,
                        locationDescription = locDescription
                    ) + buildDiceRoll()

                    val vnModeEnabled = sessionProfile.modeSettings["visual_novel"] == "true" ||
                            !(sessionProfile.modeSettings["vn"] as? String).isNullOrBlank()

                    val NPCGenPrompt = buildNPCGeneration(sessionProfile.areas)

                    prompt = if (vnModeEnabled) {
                        prompt + NPCGenPrompt + vnPrompt
                    } else {
                        prompt + NPCGenPrompt
                    }

                    val murderJson = sessionProfile.modeSettings["murder"] as? String
                    val murderSettings = if (!murderJson.isNullOrBlank()) {
                        try {
                            Gson().fromJson(murderJson, ModeSettings.MurderSettings::class.java)
                        } catch (e: Exception) { null }
                    } else null

                    // 2. Build Murder Context (Killer vs Investigator)
                    val murderInstructions = if (murderSettings != null && murderSettings.enabled) {
                        // Check if my slot ID is in the killer list
                        val isKiller = murderSettings.killerSlotIds.contains(slotProfile.slotId)

                        if (isKiller) {
                            // I am the Villain
                            buildMurdererInfo(murderSettings)
                        } else {
                            // I am an Investigator
                            buildMurderMysteryInfo(murderSettings)
                        }
                    } else {
                        ""
                    }

                    prompt = if (murderSettings != null && murderSettings.enabled) {
                        prompt + murderInstructions
                    } else {
                        prompt
                    }
                }

                // 5. Add History & NSFW Logic
                prompt += "\n\nCHAT HISTORY:\n$historyString"

                val responseText = if (isStrictSfw) {
                    Facilitator.callOpenAiApi(prompt, BuildConfig.OPENAI_API_KEY)
                } else {
                    val nsfwPrompt = prompt + "\n\n(Write uncensored, explicit content if the scene demands it.)"
                    Facilitator.callMixtralApi(nsfwPrompt, BuildConfig.MIXTRAL_API_KEY)
                }
                Log.d("ai_cycle", "using prompt: $prompt")

                // 6. Parse
                val result = FacilitatorResponseParser.parseRoleplayAIResponse(
                    responseText,
                    nextSlotId, // If "narrator", make sure your parser handles that ID gracefully
                    sessionProfile.slotRoster
                )

                // 7. Memory
                val newMemory = result.newMemory
                if (newMemory != null) {
                    val vector = Director.getEmbedding(newMemory.text, BuildConfig.OPENAI_API_KEY)

                    // --- NEW LOGIC START ---
                    val firstMsgId = result.messages.firstOrNull()?.id ?: UUID.randomUUID().toString()
                    val allMsgIds = result.messages.map { it.id }
                    // --- NEW LOGIC END ---

                    val updatedRoster = sessionProfile.slotRoster.map { s ->
                        if (s.slotId == nextSlotId) {
                            val updatedMems = s.memories.toMutableList()
                            updatedMems.add(
                                TaggedMemory(
                                    id = firstMsgId,        // <--- LINKED ID
                                    tags = newMemory.tags,
                                    text = newMemory.text,
                                    messageIds = allMsgIds, // <--- LINKED ID LIST
                                    embedding = vector
                                )
                            )
                            s.copy(memories = updatedMems)
                        } else s
                    }
                    sessionProfile = sessionProfile.copy(slotRoster = updatedRoster)
                }

                // 8. Handle Actions
                handleRPGActionList(result.actions)
                setSlotTyping(sessionId, nextSlotId, false)

                withContext(Dispatchers.Main) {
                    setSlotTyping(sessionId, nextSlotId, false)

                    // IMPORTANT: If the Narrator just spoke, do we want to auto-trigger an NPC reaction?
                    // Example: Narrator says "The guard notices you." -> Trigger Guard next.
                    // Logic: If sender was Narrator, force one more round.
                    val sender = result.messages.firstOrNull()?.senderId

                    // Save Messages
                    saveMessagesSequentially(result.messages, sessionId, chatId)

                    if (activationRound < maxActivationRounds) {
                        // If Narrator spoke, maybe give the player a chance to react?
                        // Or if NPC spoke, keep going.
                        if (sender == "narrator") {
                            // Narrator usually passes back to player, unless immediate danger.
                            updateButtonState(ButtonState.SEND)
                        } else {
                            val updatedHistory = chatAdapter.getMessages()
                            processOnTableRPGRound("", updatedHistory)
                            setSlotTyping(sessionId, nextSlotId, false)
                        }
                    } else {
                        updateButtonState(ButtonState.SEND)

                        setSlotTyping(sessionId, nextSlotId, false)
                    }
                }

            } catch (e: Exception) {
                Log.e("OnTable", "Error", e)
                withContext(Dispatchers.Main) {
                    setSlotTyping(sessionId, "", false)
                    updateButtonState(ButtonState.SEND)
                }
            }
        }
    }

    fun showCharacterPickerDialog(
        slotRoster: List<SlotProfile>,
        onCharacterSelected: (SlotProfile) -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_character_picker, null)
        val spinner = dialogView.findViewById<Spinner>(R.id.characterSpinner)
        val confirmButton = dialogView.findViewById<Button>(R.id.confirmButton)

        // Detect Murder mode
        val murderEnabled: Boolean = try {
            val murderJson = sessionProfile?.modeSettings?.get("murder") as? String
            if (!murderJson.isNullOrBlank()) {
                Gson().fromJson(murderJson, ModeSettings.MurderSettings::class.java).enabled
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
        // Collect victims by hidden role (TARGET/VICTIM)
        var title = if (murderEnabled) {
            val victims = slotRoster
                .filter { s ->
                    val role = s.hiddenRoles?.uppercase() ?: ""
                    role == "TARGET" || role == "VICTIM"
                }
                .map { it.name }
            if (victims.isNotEmpty()) {
                val plural = if (victims.size > 1) "s are" else " is"
                "Please choose a character to take control of" + "\nMurder victim$plural ${victims.joinToString(", ")}."
            }else "Please choose a character to take control of"
        } else {
            // Build the base title
            "Please choose a character to take control of"
        }
        // Spinner data
        val characterNames = slotRoster.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, characterNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        confirmButton.setOnClickListener {
            val pos = spinner.selectedItemPosition
            if (pos in slotRoster.indices) {
                onCharacterSelected(slotRoster[pos])
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun saveSessionProfile(sessionProfile: SessionProfile, sessionId: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("sessions")
            .document(sessionId)
            .set(sessionProfile, SetOptions.merge())
            .addOnSuccessListener {
                Log.d("Firestore", "Session saved: $sessionId")
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Failed to save session: $e")
            }
    }

    private fun interruptAILoop() {
        aiJob?.cancel()
    }

    private fun loadSessionHistory() {
        SessionManager.loadHistory(sessionId, { messages ->
            runOnUiThread {
                chatAdapter.setMessages(messages)
                if (chatAdapter.itemCount > 0)
                    chatRecycler.scrollToPosition(chatAdapter.itemCount - 1)

                // Update avatars once after loading entire batch
                if (messages.isNotEmpty()) {
                    updateAvatarsFromSlots(sessionProfile.slotRoster, avatarSlotAssignments)
                }
                Log.d("rpg", "FULL PROFILE: $sessionProfile")
                Log.d("rpg", "DEBUG modeSettings: ${sessionProfile.modeSettings} enabledModes: ${sessionProfile.enabledModes}")
                updateRPGToggleVisibility()
            }
            historyLoaded = true
        }, { error ->
            Log.e(TAG, "history load failed", error)
        })
    }

    // 1. Update the loop
    fun handleRPGActionList(actions: List<Action>) {
        for (action in actions) {
            when (action.type) {
                "roll_dice" -> {
                    handleDiceRoll(action.slot, action.stat, action.mod)
                }
                "health_change" -> {
                    handleHealthChange(action.slot, action.mod)
                }
                "status_effect" -> {
                    handleStatusEffect(action.slot, action.stat, action.mod)
                }
                "new_npc" -> {
                    action.npc?.let { npcData ->
                        handleNewNPC(npcData)
                    }
                }
                "advance_act" -> {
                    advanceStoryAct()
                }
            }
        }
    }

    // 2. Add the handler function
    fun handleNewNPC(data: NewNPCData) {
        if (sessionProfile.slotRoster.size >= 20) {
            Log.d("NPC_GEN", "Roster full, ignoring new NPC.")
            return
        }

        // Generate a new SlotProfile
        val newSlotId = "npc-${UUID.randomUUID().toString().take(8)}"
        val newSlot = SlotProfile(
            slotId = newSlotId,
            baseCharacterId = "gen-${UUID.randomUUID()}", // Placeholder ID
            name = data.name,
            profileType = "npc",
            summary = data.summary,
            lastActiveArea = data.lastActiveArea,
            lastActiveLocation = data.lastActiveLocation,
            age = data.age,
            abilities = data.abilities,
            bubbleColor = data.bubbleColor,
            textColor = data.textColor,
            gender = data.gender,
            height = data.height,
            weight = data.weight,
            eyeColor = data.eyeColor,
            hairColor = data.hairColor,
            physicalDescription = data.physicalDescription,
            personality = data.personality,
            privateDescription = data.privateDescription,
            sfwOnly = data.sfwOnly,
            // Defaults
            memories = emptyList(),
            outfits = emptyList(), // No images for AI gen chars yet
            currentOutfit = "",
            relationships = emptyList(),
            hp = 10,
            maxHp = 10
        )

        // Update Session
        val updatedRoster = sessionProfile.slotRoster.toMutableList()
        updatedRoster.add(newSlot)
        sessionProfile = sessionProfile.copy(slotRoster = updatedRoster)

        // Save to Firestore
        saveSessionProfile(sessionProfile, sessionId)

        // Notify UI (optional system message)
        val sysMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            senderId = "system",
            text = "New Character Entered: ${data.name}",
            timestamp = com.google.firebase.Timestamp.now(),
            visibility = true,
            messageType = "event"
        )
        SessionManager.sendMessage(chatId, sessionId, sysMsg)

        Log.d("NPC_GEN", "Created new NPC: ${data.name} in ${data.lastActiveArea}")
    }

    fun handleDiceRoll(slotId: String, statName: String, extraMod: Int) {
        val slotProfile = sessionProfile.slotRoster.find { it.slotId == slotId } ?: return

        // Look up the stat value from the stats map using the lowercase key
        val statValue = slotProfile.stats[statName.lowercase()] ?: 0
        val statMod = statValue / 2  // Simple half-value modifier

        val d20 = (1..20).random()
        val total = d20 + statMod + extraMod
        val area = slotProfile.lastActiveArea
        val location = slotProfile.lastActiveLocation
        // Post result to chat
        val rollMessage = "${slotProfile.name} rolled $d20 + $statName (mod $statMod)" +
                (if (extraMod != 0) " + $extraMod (extra)" else "") +
                ". Total: $total"
        val chatMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            senderId = slotId,
            displayName = "Narrator",
            text = rollMessage,
            area = area,
            location = location,
            delay = 0,
            timestamp = Timestamp.now(),
            visibility = true,
            messageType = "roll"
        )
        SessionManager.sendMessage(chatId, sessionId, chatMessage)
        Log.d("airoll","this hsould post: $chatMessage")
    }

    fun handleHealthChange(slotId: String, hpChange: Int) {
        val targetSlot = sessionProfile.slotRoster.find { it.slotId == slotId }
        if (targetSlot != null) {
            targetSlot.hp += hpChange
            // Optional: clamp HP to min/max, update UI, etc.
            Log.d("RPG", "${targetSlot.name} HP changed by $hpChange, now ${targetSlot.hp}")
        }
    }

    fun handleStatusEffect(slotId: String, effect: String, mod: Int) {
        val targetSlot = sessionProfile.slotRoster.find { it.slotId == slotId }
        if (targetSlot != null) {
            if (mod > 0) {
                if (!targetSlot.statusEffects.contains(effect)) { // <--- Prevent duplicates
                    targetSlot.statusEffects.add(effect)
                    Log.d("RPG", "${targetSlot.name} gained status effect: $effect")
                }
            } else {
                targetSlot.statusEffects.remove(effect)
                Log.d("RPG", "${targetSlot.name} lost status effect: $effect")
            }
        }
    }

    fun advanceStoryAct() {
        val currentActIndex = sessionProfile.currentAct
        val acts = sessionProfile.acts ?: emptyList()

        // Safety Check: Is there a next act?
        if (currentActIndex >= acts.size - 1) {
            Log.d("ACT", "Campaign finished or no next act.")
            return
        }

        val nextActIndex = currentActIndex + 1
        val nextAct = acts[nextActIndex]

        // 1. Update Session Data
        // We update the local profile immediately so the next prompt uses the NEW Act info
        val updatedProfile = sessionProfile.copy(currentAct = nextActIndex)
        sessionProfile = updatedProfile
        saveSessionProfile(updatedProfile, sessionId)

        // 2. System Announcement (Visible to Players)
        val announceMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            senderId = "system",
            text = "ACT COMPLETED!\nStarting Act ${nextActIndex + 1}: ${nextAct.summary}",
            timestamp = com.google.firebase.Timestamp.now(),
            visibility = true,
            messageType = "event"
        )
        // We save this directly so it appears in the chat log
        SessionManager.sendMessage(chatId, sessionId, announceMsg)

        // 3. Trigger Narrator for Scene Setting
        // We launch a new coroutine to force the Narrator to speak *immediately*
        lifecycleScope.launch(Dispatchers.Main) {
            // Create a hidden prompt telling the Narrator to do their job
            val promptMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                senderId = "system",
                text = "The Act has just advanced. Based on the new Act Summary, describe the transition and the new scene to the players.",
                visibility = false
            )

            // Force the round processing (Narrator will pick this up because it's a system instruction)
            val currentHistory = chatAdapter.getMessages() + announceMsg
            processOnTableRPGRound("", currentHistory + promptMsg)
        }
    }

    fun checkMessageLimit(onLimitCheckPassed: () -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) return // Should not happen if logged in

        val userRef = FirebaseFirestore.getInstance().collection("users").document(user.uid)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(userRef)

            // Handle case where UserProfile doc might not exist yet (lazy creation)
            val isPremium = snapshot.getBoolean("isPremium") ?: false
            if (isPremium) return@runTransaction true // Premium users always pass

            val lastDate = snapshot.getString("lastMessageDate") ?: ""

            // Use SimpleDateFormat for API 23 compatibility
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val today = sdf.format(java.util.Date())

            val currentCount = if (lastDate == today) {
                // It's the same day, use stored count (default to 0 if null)
                snapshot.getLong("dailyMessageCount") ?: 0
            } else {
                // New day (or no date stored), reset to 0
                0
            }

            // Check if limit reached
            if (currentCount >= FREE_DAILY_LIMIT) {
                return@runTransaction false // Fail
            }

            // Increment count and update date
            transaction.set(
                userRef,
                mapOf(
                    "dailyMessageCount" to currentCount + 1,
                    "lastMessageDate" to today
                ),
                SetOptions.merge() // Merge so we don't wipe name/bio if they exist
            )
            return@runTransaction true // Pass

        }.addOnSuccessListener { allowed ->
            if (allowed) {
                onLimitCheckPassed()
            } else {
                showPaywallDialog()
            }
        }.addOnFailureListener { e ->
            Log.e("LimitCheck", "Failed to check limit", e)
            // Fail open: If internet blips, let them play to avoid frustration?
            // Or fail closed: onLimitCheckPassed() to allow.
            onLimitCheckPassed()
        }
    }

    // 3. The Dialog
    // In MainActivity.kt
    fun showPaywallDialog() {
        AlertDialog.Builder(this)
            .setTitle("Daily Limit Reached")
            .setMessage("You've used your free messages for today! Upgrade to Premium for unlimited roleplay.")
            .setPositiveButton("Upgrade") { _, _ ->
                // LAUNCH THE NEW ACTIVITY
                val intent = Intent(this, UpgradeActivity::class.java)
                startActivity(intent)
            }
            .setNegativeButton("Wait until tomorrow", null)
            .show()
    }

    private fun sendMessageAndCallAI(text: String) {
        val activeSlotId = sessionProfile.userMap[userId]?.activeSlotId
        if (activeSlotId.isNullOrBlank()) {
            Log.e("sendMessage", "No activeSlotId for user $userId. Message not sent.")
            Toast.makeText(this, "No character selected for this session.", Toast.LENGTH_SHORT).show()
            return
        }
        val personaSlot = sessionProfile.slotRoster.find { it.slotId == activeSlotId }
        val area = personaSlot?.lastActiveArea
        val location = personaSlot?.lastActiveLocation
        val senderLabel = personaSlot?.name ?: "user"
        val msg = ChatMessage(
            id = UUID.randomUUID().toString(),
            senderId = activeSlotId ?: "unknown",
            displayName = senderLabel,
            text = text,
            area = area,
            location = location,
            delay = 0,
            timestamp = Timestamp.now(),
            pose = null,
            imageUpdates = null,
            visibility = true
        )
        SessionManager.sendMessage(chatId, sessionId, msg)
        sendToAI(text)
    }

    private fun sendToAI(userInput: String) {
        val messages = chatAdapter.getMessages()
        var isOnTable = false
        var isAboveTable = false
        var isOneonOne = false
        val rpgSettingsJson = sessionProfile.modeSettings["rpg"] as? String
        if (!rpgSettingsJson.isNullOrBlank() && rpgSettingsJson.trim().startsWith("{")) {
            try {
                val rpgSettings = Gson().fromJson(rpgSettingsJson, RPGSettings::class.java)
                when (rpgSettings.perspective?.lowercase()) {
                    "ontable" -> isOnTable = true
                    "abovetable" -> isAboveTable = true
                }
                when (sessionProfile.chatMode){
                    "ONEONONE" -> isOneonOne = true
                }
            } catch (e: Exception) {
                Log.e("RPG", "Failed to parse rpgSettingsJson: $rpgSettingsJson", e)
            }
        }
        val slotIdsList = sessionProfile.slotRoster.joinToString(", ") { it.slotId }
        if (messages.isEmpty() && !initialGreeting.isNullOrBlank()) {
            val greetingMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                senderId = "system",
                text = initialGreeting!! + "\n if a characters lastActiveArea and/or lastActiveLocation = null, move them to an area and location. The locations should be used to make the story interesting.",
                delay = 0,
                timestamp = com.google.firebase.Timestamp.now(),
                imageUpdates = null,
                visibility = false
            )
            if (sessionProfile.chatMode == "ONEONONE") {
                isOneonOne = true
            }
            if (isOnTable) {
                Log.d("ai_cycle", "proccessing isOnTable")
                activationRound = 0
                processOnTableRPGRound(userInput, listOf(greetingMessage))
            }else if (isAboveTable){
                Log.d("ai_cycle", "proccessing isAboveTable")
                activationRound = 0
                processAboveTableRound(userInput, listOf(greetingMessage))
            } else if (isOneonOne) {
                Log.d("ai_cycle", "proccessing isOneonOne")
                activationRound = 0
                processOneonOneRound(userInput, listOf(greetingMessage))
            }else{
                    Log.d("ai_cycle", "proccessing normal roleplay")

                    activationRound = 0
                    processActivationRound(userInput, listOf(greetingMessage))
            }
        } else {
            if (isOnTable) {
                Log.d("ai_cycle", "proccessing isOnTable")
                activationRound = 0
                maxActivationRounds = 2
                processOnTableRPGRound(userInput, messages)
            }else if (isAboveTable){
                Log.d("ai_cycle", "proccessing isAboveTable")
                activationRound = 0
                maxActivationRounds = 2
                processAboveTableRound(userInput, messages)
            } else {
                Log.d("ai_cycle", "proccessing normal roleplay")
                activationRound = 0
                processActivationRound(userInput, messages)
            }
        }

    }

    // save each message in sequence, update avatars/backgrounds if present
    suspend fun saveMessagesSequentially(messages: List<ChatMessage>, sessionId: String, chatId: String) {
        for (msg in messages) {
            Log.d("MessageCreation", "Created message: $msg")
            delay(msg.delay.toLong())
            SessionManager.sendMessage(chatId, sessionId, msg)
            // The listener will handle updating the UI and personal histories
        }
    }

    fun buildHistoryString(messages: List<ChatMessage>): String {
        return messages.joinToString("\n") { msg ->
            val name = when {
                msg.senderId == "narrator" -> "Narrator"
                sessionProfile.slotRoster.any { s -> s.slotId == msg.senderId } ->
                    sessionProfile.slotRoster.find { it.slotId == msg.senderId }?.name ?: msg.senderId
                sessionProfile.userMap.containsKey(msg.senderId) ->
                    sessionProfile.userMap[msg.senderId]?.username ?: msg.senderId
                else -> msg.senderId
            }
            "$name: ${msg.text}"
        }
    }

    private fun updateButtonState(state: ButtonState) {
        currentState = state
        when (state) {
            ButtonState.SEND -> {
                sendButton.text = "Send"
                sendButton.isEnabled = true
            }
            ButtonState.INTERRUPT -> {
                sendButton.text = "Interrupt"
                sendButton.isEnabled = true
            }
            ButtonState.WAITING -> {
                sendButton.text = "Waiting…"
                sendButton.isEnabled = false
            }
        }
    }

    fun setSlotTyping(sessionId: String, slotId: String, typing: Boolean) {
        val db = FirebaseFirestore.getInstance()
        val sessionRef = db.collection("sessions").document(sessionId)

        sessionRef.get()
            .continueWithTask { task ->
                val sessionProfile = task.result?.toObject(SessionProfile::class.java)
                    ?: return@continueWithTask Tasks.forException(IllegalStateException("No session"))
                val updatedSlotRoster = sessionProfile.slotRoster.map { slot ->
                    if (slot.slotId == slotId) slot.copy(typing = typing) else slot
                }
                sessionRef.update("slotRoster", updatedSlotRoster)

            }
            .addOnSuccessListener {
                Log.d("Typing", "Typing status for $slotId set to $typing")
            }
            .addOnFailureListener { e ->
                Log.e("Typing", "Failed to set typing status for $slotId: $e")
            }
        updateTypingIndicator()
    }


    fun updateTypingIndicator() {
        typingIndicatorBar.removeAllViews()
        val typingSlots = sessionProfile.slotRoster.filter { it.typing == true }
        if (typingSlots.isEmpty()) {
            typingIndicatorBar.visibility = View.GONE
            return
        }
        typingIndicatorBar.visibility = View.VISIBLE

        for (slot in typingSlots) {
            val box = TextView(this)
            box.text = "..."
            box.setPadding(24, 6, 24, 6)
            box.textSize = 16f
            box.setBackgroundResource(R.drawable.typing_indicator_bg)

        // Color the bubble background
            try {
                box.background.setTint(Color.parseColor(slot.bubbleColor ?: "#FFFFFF"))
            } catch (e: Exception) {
                box.background.setTint(Color.LTGRAY)
            }

        // Color the "..." with their textColor
            try {
                box.setTextColor(Color.parseColor(slot.textColor ?: "#000000"))
            } catch (e: Exception) {
                box.setTextColor(Color.BLACK)
            }
            // Add a little margin between indicators
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(8, 0, 8, 0)
            box.layoutParams = params

            typingIndicatorBar.addView(box)
        }
    }

    fun assignAvatarSlot(senderId: String, avatarSlotAssignments: MutableMap<Int, String?>, avatarSlotLocked: BooleanArray, slotProfiles: List<SlotProfile>) {
        val maxSlots = avatarSlotAssignments.size

        // Check if sender is already in a locked slot
        val currentIndex = avatarSlotAssignments.entries.find { it.value == senderId }?.key ?: -1
        if (currentIndex >= 0 && avatarSlotLocked[currentIndex]) {
            // Already in a locked slot, do nothing
            return
        }

        // Look up sender profile
        val senderProfile = slotProfiles.find { it.slotId == senderId }
        val hasPose = senderProfile?.outfits?.any { it.poseSlots.isNotEmpty() } == true

        if (!hasPose) {
            // Sender has no poses, don't assign them to any avatar slot!
            Log.d("avatardebug", "Sender $senderId has no poses; not assigning to avatar slots.")
            return
        }

        avatarSlotAssignments.entries.forEach { (idx, value) ->
            if (value == senderId) avatarSlotAssignments[idx] = null
        }

        // Check if slot 0 is locked
        if (avatarSlotLocked[0]) {
            // Slot 0 is locked, can't move sender here. Just make sure they're in their current slot.
            if (currentIndex < 0) {
                // Sender not shown, find a free slot that's not locked
                for (i in 1 until maxSlots) {
                    if (!avatarSlotLocked[i] && avatarSlotAssignments[i] == null) {
                        avatarSlotAssignments[i] = senderId
                        return
                    }
                }
                // No unlocked slot available, do nothing
            }
            // Else: already in a slot (not 0), but 0 is locked, do nothing.
            return
        }

        // Slot 0 is not locked
        if (currentIndex == 0) return // Sender is already in slot 0

        // Shift everyone (except locked) up one (3->2, 2->1, 1->0) to make room for sender at slot 0
        for (i in (maxSlots - 1) downTo 1) {
            if (!avatarSlotLocked[i]) {
                avatarSlotAssignments[i] = if (avatarSlotLocked[i - 1]) avatarSlotAssignments[i] else avatarSlotAssignments[i - 1]
            }
        }
        // Place sender in slot 0
        avatarSlotAssignments[0] = senderId

        // Remove sender from old slot if needed
        if (currentIndex > 0) avatarSlotAssignments[currentIndex] = null
    }

    fun removeAvatarFromSlots(slotId: String, avatarSlotAssignments: MutableMap<Int, String?>) {
        val key = avatarSlotAssignments.entries.find { it.value == slotId }?.key
        if (key != null) avatarSlotAssignments[key] = null
    }

    fun assignSlotToUser(userId: String, slotId: String) {
        val db = FirebaseFirestore.getInstance()
        val sessionId = sessionProfile?.sessionId ?: return

        // Update local userMap
        sessionProfile?.userMap = sessionProfile?.userMap?.toMutableMap()?.apply {
            val user = get(userId)
            if (user != null) {
                put(userId, user.copy(activeSlotId = slotId))
            }
        } ?: return

        // Update slotRoster: set profileType = "player" for the chosen slotId
        sessionProfile?.slotRoster = sessionProfile?.slotRoster?.map { slot ->
            if (slot.slotId == slotId) {
                slot.copy(profileType = "player")
            } else slot
        } ?: return

        // Now update both fields in Firestore (atomically!)
        db.collection("sessions").document(sessionId)
            .update(
                mapOf(
                    "userMap" to sessionProfile!!.userMap,
                    "slotRoster" to sessionProfile!!.slotRoster
                )
            )
            .addOnSuccessListener { /* UI update if needed */ }
            .addOnFailureListener { /* Error handling */ }
    }

    fun updateAvatarsFromSlots(
        slotProfiles: List<SlotProfile>,
        avatarSlotAssignments: MutableMap<Int, String?>
    ) {
        if (isDestroyed || isFinishing) return
        Log.d("avatardebug", "updating: $avatarSlotAssignments")

        // Find player's area/location once
        val playerSlot = slotProfiles.find { it.slotId == mySlotId }
        val playerArea = playerSlot?.lastActiveArea
        val playerLocation = playerSlot?.lastActiveLocation

        val targetArea = spyingArea ?: slotProfiles.find { it.slotId == mySlotId }?.lastActiveArea
        val targetLocation = spyingLocation ?: slotProfiles.find { it.slotId == mySlotId }?.lastActiveLocation

        for ((index, slotId) in avatarSlotAssignments) {
            val view = avatarViews[index]
            if (slotId == null) {
                Glide.with(this).clear(view)
                view.setImageResource(R.drawable.silhouette)
                view.visibility = View.INVISIBLE
                continue // Use 'continue' instead of 'return@forEach' inside a for loop
            }

            val slotProfile = slotProfiles.find { it.slotId == slotId }
            if (slotProfile == null) {
                Glide.with(this).clear(view)
                view.setImageResource(R.drawable.silhouette)
                view.visibility = View.INVISIBLE
                continue
            }

            // Must be in the same place as the player
            if (slotProfile.lastActiveArea != targetArea ||
                slotProfile.lastActiveLocation != targetLocation
            ) {
                Log.d(
                    "avatardebug",
                    "${slotProfile.name} is at ${slotProfile.lastActiveArea}/${slotProfile.lastActiveLocation}, " +
                            "player $mySlotId is at $playerArea/$playerLocation — hiding"
                )
                removeAvatarFromSlots(slotId, avatarSlotAssignments)
                Glide.with(this).clear(view)
                view.setImageResource(R.drawable.silhouette)
                view.visibility = View.INVISIBLE
                continue
            }

            val poseName = slotProfile.pose?.trim().orEmpty()
            Log.d("avatardebug", "${slotProfile.name} wants pose='$poseName'")

            if (poseName.isBlank() || poseName.equals("clear", true) || poseName.equals("none", true)) {
                Glide.with(this).clear(view)
                view.setImageResource(R.drawable.silhouette)
                view.visibility = View.INVISIBLE
                continue
            }

            // --- Robust outfit & pose resolution ---
            val outfits = slotProfile.outfits.orEmpty()
            val currentOutfitName = slotProfile.currentOutfit?.trim().orEmpty()

            // try exact (case-insensitive) match; else first outfit
            val chosenOutfit = outfits.firstOrNull { it.name.trim().equals(currentOutfitName, true) }
                ?: outfits.firstOrNull()

            // pose candidates: chosen outfit if present, else all outfits
            val poseSlots = (chosenOutfit?.poseSlots ?: outfits.flatMap { it.poseSlots }).orEmpty()

            // find the pose (case-insensitive)
            val poseSlot = poseSlots.firstOrNull { it.name.trim().equals(poseName, true) }

            // Extra debug to confirm why things hide
            Log.d(
                "avatardebug",
                "slot=${slotProfile.slotId} currentOutfit='${slotProfile.currentOutfit}' " +
                        "outfits=${outfits.map { it.name }} chosen='${chosenOutfit?.name}' " +
                        "poses=${poseSlots.map { it.name }} hit='${poseSlot?.name}' url='${poseSlot?.uri}'"
            )

            val imageUrl = poseSlot?.uri
            if (!imageUrl.isNullOrBlank()) {
                Glide.with(this)
                    .load(imageUrl)
                    .placeholder(R.drawable.silhouette)
                    .into(view)
                view.visibility = View.VISIBLE
            } else {
                Glide.with(this).clear(view)
                view.setImageResource(R.drawable.silhouette)
                view.visibility = View.INVISIBLE
            }
        }
    }

    private suspend fun deleteMessagesAfter(sessionId: String, afterTimestamp: Timestamp): List<String> {
        val db = FirebaseFirestore.getInstance()
        val messagesRef = db.collection("sessions").document(sessionId).collection("messages")

        val snapshot = messagesRef.whereGreaterThan("timestamp", afterTimestamp).get().await()
        val deletedIds = mutableListOf<String>()

        // Delete documents and collect IDs
        for (doc in snapshot.documents) {
            deletedIds.add(doc.id)
            doc.reference.delete()
        }
        return deletedIds
    }

    private suspend fun deleteMessageAndFollowing(message: ChatMessage): List<String> {
        val db = FirebaseFirestore.getInstance()
        val messagesRef = db.collection("sessions").document(sessionId).collection("messages")

        // Delete the message itself
        messagesRef.document(message.id).delete().await()

        val deletedIds = mutableListOf(message.id)

        // Delete all following messages and add their IDs
        val afterTimestamp = message.timestamp
        if (afterTimestamp != null) {
            deletedIds.addAll(deleteMessagesAfter(sessionId, afterTimestamp))
        } else {
            Log.e("DELETE_DEBUG", "No timestamp on message, can't delete following messages")
        }
        return deletedIds
    }

    private fun deleteMemoriesFromSession(deletedMessageIds: List<String>) {
        if (deletedMessageIds.isEmpty()) return

        var hasChanges = false
        val updatedRoster = sessionProfile.slotRoster.map { slot ->
            val originalSize = slot.memories.size
            // Keep memory ONLY if its ID is NOT in deleted list AND it doesn't contain a deleted message ID
            val filteredMemories = slot.memories.filterNot { mem ->
                deletedMessageIds.contains(mem.id) || mem.messageIds.any { it in deletedMessageIds }
            }

            if (filteredMemories.size != originalSize) {
                hasChanges = true
                slot.copy(memories = filteredMemories)
            } else {
                slot
            }
        }

        if (hasChanges) {
            sessionProfile = sessionProfile.copy(slotRoster = updatedRoster)
            saveSessionProfile(sessionProfile, sessionId)
            Log.d("MemoryClean", "Deleted memories associated with messages: $deletedMessageIds")
        }
    }

    private suspend fun saveEditedMessageAndDeleteFollowing(message: ChatMessage, position: Int): List<String> {
        val db = FirebaseFirestore.getInstance()
        val messagesRef = db.collection("sessions").document(sessionId).collection("messages")

        // Save the edited message first
        messagesRef.document(message.id).set(message).await()

        val deletedIds = mutableListOf<String>()

        val afterTimestamp = message.timestamp
        if (afterTimestamp != null) {
            deletedIds.addAll(deleteMessagesAfter(sessionId, afterTimestamp))
        } else {
            Log.e("DELETE_DEBUG", "No timestamp on edited message, can't delete following messages")
        }

        return deletedIds
    }

    private suspend fun deleteFromSlotPersonalHistories(sessionId: String, deletedIds: List<String>) {
        val db = FirebaseFirestore.getInstance()
        val slotIds = sessionProfile.slotRoster.map { it.slotId }  // Use slotRoster IDs explicitly
        Log.d("DELETE_DEBUG", "Deleting from personal histories for message IDs: $deletedIds in slots: $slotIds")

        for (slotId in slotIds) {
            val messagesRef = db.collection("sessions").document(sessionId)
                .collection("slotPersonalHistory")
                .document(slotId)
                .collection("messages")

            for (msgId in deletedIds) {
                Log.d("DELETE_DEBUG", "Deleting message $msgId from slot $slotId")
                messagesRef.document(msgId).delete().await()
                Log.d("DELETE_DEBUG", "Deleted message $msgId from slot $slotId")
            }
        }
    }

    fun updateBackgroundIfChanged(area: String?, location: String?, areas: List<Area>) {
        if (area == lastBackgroundArea && location == lastBackgroundLocation) return

        lastBackgroundArea = area
        lastBackgroundLocation = location

        // Do your lookup and load as before
        fun String.normalize(): String = this.trim().lowercase().replace("\\s+".toRegex(), " ")

        val areaObj = areas.find { it.name.normalize() == area?.normalize() || it.id == area }
        val locationObj = areaObj?.locations?.find { it.name.normalize() == location?.normalize() || it.id == location }
        val backgroundUrl = locationObj?.uri

        runOnUiThread {
            if (!backgroundUrl.isNullOrBlank()) {
                Glide.with(this)
                    .load(backgroundUrl)
                    .into(findViewById<ImageView>(R.id.backgroundImageView))
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu) = menuInflater.inflate(R.menu.main_menu, menu).let { true }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.clear_chat -> {
            chatAdapter.clearMessages()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun setPlayerTyping(isTyping: Boolean) {
        val activeSlotId = sessionProfile.userMap[userId]?.activeSlotId ?: return
        if (lastTypingState == isTyping) return // Prevent redundant writes
        lastTypingState = isTyping

        setSlotTyping(sessionId, activeSlotId, isTyping)
    }

//   private fun updateLocationCharRecycler(area: Area, location: LocationSlot?) {
//        val locationChar = findViewById<RecyclerView>(R.id.locationChar)
//        if (location == null) {
//            locationChar.adapter = CharacterRowAdapter(emptyList(), onClick = {})
//            return
//        }
//        val presentChars = sessionProfile.slotRoster
//            .filter { it.lastActiveArea == area.name && it.lastActiveLocation == location.name }
//            .map { it.toCharacterProfileStub() } // <--- this is why it works in sessionlanding
//
//        locationChar.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
//        locationChar.adapter = CharacterRowAdapter(
//            presentChars,
//            onClick = { char -> Toast.makeText(this, "Clicked ${char.name}", Toast.LENGTH_SHORT).show() }
//        )
//    }

    fun SlotProfile.toCharacterProfileStub(): CharacterProfile {
        return CharacterProfile(
            id = this.slotId,
            name = this.name,
            avatarUri = this.avatarUri,
            // ... map other fields as needed
        )
    }

    override fun onStart() {
        super.onStart()

        // 1. SESSION LISTENER (Profile Updates)
        sessionListener = FirebaseFirestore.getInstance()
            .collection("sessions")
            .document(sessionId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                if (snapshot != null && snapshot.exists()) {
                    val updatedProfile = snapshot.toObject(SessionProfile::class.java) ?: return@addSnapshotListener

                    // Detect Role Switch (Player -> Director) for Auto-Spy
                    val newSlotId = updatedProfile.userMap[userId]?.activeSlotId
                    if (newSlotId == "narrator" && this.mySlotId != "narrator" && this.mySlotId != null && spyingArea == null) {
                        // User just became Director. Auto-spy on their previous location so the screen isn't black.
                        val oldSlot = updatedProfile.slotRoster.find { it.slotId == this.mySlotId }
                        if (oldSlot != null) {
                            spyingArea = oldSlot.lastActiveArea
                            spyingLocation = oldSlot.lastActiveLocation
                            Toast.makeText(this, "Director Mode: Viewing ${oldSlot.lastActiveLocation}", Toast.LENGTH_SHORT).show()
                        }
                    }

                    // Save Profile & Slot
                    sessionProfile = updatedProfile
                    this.mySlotId = newSlotId

                    val playerSlot = updatedProfile.slotRoster.find { it.slotId == this.mySlotId }
                    val playerArea = playerSlot?.lastActiveArea
                    val playerLocation = playerSlot?.lastActiveLocation

                    // Updates
                    updateTypingIndicator()

                    // Background: Use Spy location if Director/Spying, otherwise Player location
                    val targetArea = spyingArea ?: playerArea
                    val targetLocation = spyingLocation ?: playerLocation
                    updateBackgroundIfChanged(targetArea, targetLocation, updatedProfile.areas)

                    // Personal History (Only if not narrator, or handle narrator separately)
                    if (this.mySlotId != null && this.mySlotId != "narrator") {
                        listenToPersonalHistory(sessionId, this.mySlotId!!)
                    }

                    // Multiplayer Check
                    val newMultiplayer = updatedProfile.multiplayer
                    if (lastMultiplayerValue != null && lastMultiplayerValue != newMultiplayer) {
                        Log.d("MULTIPLAYER_CHANGE", "Multiplayer changed to $newMultiplayer")
                    }
                    lastMultiplayerValue = newMultiplayer

                    // Handle Bot Greetings
                    if (!initialGreeting.isNullOrBlank() && !initialGreetingSent) {
                        val allPlayersChosen = sessionProfile.userMap.values.all { it.activeSlotId != null }
                        if (allPlayersChosen) {
                            initialGreetingSent = true
                            updateButtonState(ButtonState.INTERRUPT)
                            val greetingText = initialGreeting!!
                            lifecycleScope.launch {
                                sessionProfile.slotRoster.filter { it.profileType == "bot" }.forEach { botSlot ->
                                    val greetingMsg = ChatMessage(
                                        id = UUID.randomUUID().toString(),
                                        senderId = "system",
                                        text = greetingText,
                                        area = botSlot.lastActiveArea,
                                        location = botSlot.lastActiveLocation,
                                        timestamp = com.google.firebase.Timestamp.now(),
                                        visibility = false
                                    )
                                    addToPersonalHistoryFirestore(sessionId, botSlot.slotId, greetingMsg)
                                }
                            }
                            historyLoaded = true
                            sendToAI(greetingText)
                        }
                    }
                }
            }

        // 2. MESSAGE LISTENER (Separated from Session Listener to fix duplicates/nesting)
        SessionManager.listenMessages(sessionId) { newMessage ->
            runOnUiThread {
                Log.d("messageupdating", "message updated")
                if (!historyLoaded) return@runOnUiThread
                val currentMessageId = newMessage.id ?: return@runOnUiThread

                            Log.d("messageupdating", "$currentMessageId")
                            if (processedMessageIds.contains(currentMessageId)) {
                                Log.d("MessageFilter", "Skipping duplicate message: $currentMessageId")
                                return@runOnUiThread
                            }
                            processedMessageIds.add(currentMessageId)
                            if (processedMessageIds.size > 20) { // Prevent memory leak, keep the last 20-50
                                processedMessageIds.remove(processedMessageIds.first())
                            }

                // VISIBILITY LOGIC
                val playerSlot = sessionProfile.slotRoster.find { it.slotId == mySlotId }

                // If I am Narrator/Spying, use that. If I am Player, use my body.
                // If I am Narrator and spyingArea is null (rare due to auto-spy), default to "view all" or "view none"?
                // Currently defaults to null (view none), which is why Auto-Spy is crucial.
                val currentArea = spyingArea ?: playerSlot?.lastActiveArea
                val currentLocation = spyingLocation ?: playerSlot?.lastActiveLocation

                val isRelevantToView = (newMessage.area == currentArea && newMessage.location == currentLocation)

                if (isRelevantToView) {
                    chatAdapter.addMessage(newMessage)
                    chatRecycler.scrollToPosition(chatAdapter.itemCount - 1)
                }

                // OUTFIT / POSE UPDATES
                if (!newMessage.outfit.isNullOrBlank() || !newMessage.pose.isNullOrEmpty()) {
                    val updatedRoster = sessionProfile.slotRoster.map { slot ->
                        var newSlot = slot
                        if (slot.slotId == newMessage.senderId && !newMessage.outfit.isNullOrBlank()) {
                            if (slot.outfits.any { it.name.equals(newMessage.outfit, ignoreCase = true) }) {
                                newSlot = newSlot.copy(currentOutfit = newMessage.outfit)
                            }
                        }
                        if (!newMessage.pose.isNullOrEmpty() && newMessage.pose.containsKey(slot.slotId)) {
                            newSlot = newSlot.copy(pose = newMessage.pose[slot.slotId] ?: slot.pose)
                        }
                        newSlot
                    }
                    sessionProfile = sessionProfile.copy(slotRoster = updatedRoster)
                }

                updateButtonState(ButtonState.SEND)
                lastMessageId = currentMessageId

                // SAVE TO HISTORIES & DICE ROLLS
                val recipients = sessionProfile.slotRoster.filter { slot ->
                    slot.lastActiveArea == newMessage.area && slot.lastActiveLocation == newMessage.location
                }

                recipients.forEach { slot ->
                    if (newMessage.messageType == "roll") {
                        lifecycleScope.launch {
                            diceImageView.visibility = View.VISIBLE
                            val result = extractRollFromText(newMessage.text)
                            repeat(10) {
                                val r = (1..20).random()
                                val resId = resources.getIdentifier("ic_d$r", "drawable", packageName)
                                if (resId != 0) diceImageView.setImageResource(resId)
                                delay(50)
                            }
                            val finalId = resources.getIdentifier("ic_d$result", "drawable", packageName)
                            if (finalId != 0) diceImageView.setImageResource(finalId)
                            delay(1000)
                            diceImageView.visibility = View.GONE
                            addToPersonalHistoryFirestore(sessionId, slot.slotId, newMessage)
                        }
                    } else {
                        addToPersonalHistoryFirestore(sessionId, slot.slotId, newMessage)
                    }
                }
            }
        }
    }

    fun extractRollFromText(text: String): Int? {
        // This regex finds the last number in the string
        val match = Regex("(\\d+)$").find(text)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    fun addToPersonalHistoryFirestore(sessionId: String, slotId: String, message: ChatMessage) {
        val db = FirebaseFirestore.getInstance()
        db.collection("sessions")
            .document(sessionId)
            .collection("slotPersonalHistory")
            .document(slotId)
            .collection("messages")
            .document(message.id)
            .set(message)
    }

    private var lastPersonalHistoryMessageId: String? = null

    fun listenToPersonalHistory(sessionId: String, slotId: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("sessions")
            .document(sessionId)
            .collection("slotPersonalHistory")
            .document(slotId)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val messages = snapshot.documents.mapNotNull { it.toObject(ChatMessage::class.java) }
                if (messages.isEmpty()) return@addSnapshotListener

                val newLastMsgId = messages.last().id

                // Only update UI if the last message is different
                if (lastPersonalHistoryMessageId == newLastMsgId) {
                    Log.d("AvatarDebug", "No new messages; skipping UI update.")
                    return@addSnapshotListener
                }
                lastPersonalHistoryMessageId = newLastMsgId

                myPersonalHistory = messages
                runOnUiThread {
                    chatAdapter.clearMessages()
                    messages.forEach { chatAdapter.addMessage(it) }
                    chatRecycler.scrollToPosition(messages.size - 1)
                    val lastMsg = messages.last()
                    assignAvatarSlot(lastMsg.senderId, avatarSlotAssignments, avatarSlotLocked, sessionProfile.slotRoster)

                    Log.d("avatardebug", "Preparing visibleAvatarSlotIds: $avatarSlotAssignments")
                    updateAvatarsFromSlots(sessionProfile.slotRoster, avatarSlotAssignments)
                }
            }
    }

    suspend fun fetchPersonalHistory(sessionId: String, slotId: String): List<ChatMessage> {
        val db = FirebaseFirestore.getInstance()
        val snapshot = db.collection("sessions")
            .document(sessionId)
            .collection("slotPersonalHistory")
            .document(slotId)
            .collection("messages")
            .orderBy("timestamp")
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.toObject(ChatMessage::class.java) }
    }

    private fun String.normalizeLoc() = this.trim().lowercase()

    fun sendBugReportEmail(userMessage: String) {
        val gson = Gson()
        val debugInfo = buildString {
            appendLine("User Message:\n$userMessage\n")
            appendLine("User ID: $userId")
            appendLine("Session ID: $sessionId")
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Device: ${android.os.Build.MODEL} (${android.os.Build.MANUFACTURER})")
            appendLine("\nSession Profile:")
            appendLine(gson.toJson(sessionProfile))
            appendLine("\nRecent Messages:")
            chatAdapter.getMessages().takeLast(10).forEach { msg ->
                appendLine("${msg.senderId}: ${msg.text}")
            }
        }
        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf("realmsai.report@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, "RealmsAI Bug Report")
            putExtra(Intent.EXTRA_TEXT, debugInfo)
        }
        startActivity(Intent.createChooser(emailIntent, "Send bug report via..."))
    }

    fun enforceMonogamy(
        relMap: MutableMap<String, VNRelationship>,
        monogamyLevel: Int
    ) {
        // Find the highest relationship level
        val maxLevel = relMap.values.maxOfOrNull { it.currentLevel } ?: 0
        var found = false

        for ((toId, rel) in relMap) {
            if (rel.currentLevel > monogamyLevel) {
                if (!found && rel.currentLevel == maxLevel) {
                    // Let this one be above the cap
                    found = true
                } else {
                    // Cap to monogamyLevel
                    val cappedThreshold = rel.levels.firstOrNull { it.level == monogamyLevel }?.threshold ?: 0
                    rel.points = minOf(rel.points, cappedThreshold)
                    updateRelationshipLevel(rel)  // <-- ensure the level is recalculated!
                }
            }
        }
    }

    private fun slotKeyFromIndex(idx0: Int): String =
        ModeSettings.SlotKeys.fromPosition(idx0) // 0-based -> "character{n}"

    private fun slotKeyForBaseId(baseId: String, roster: List<SlotProfile>): String? {
        val i = roster.indexOfFirst { it.baseCharacterId == baseId }
        return if (i >= 0) slotKeyFromIndex(i) else null
    }

    override fun onStop() {
        sessionListener?.remove()
        sessionListener = null
        messagesListener?.remove()
        messagesListener = null
        personalHistoryListener?.remove()
        personalHistoryListener = null
        super.onStop()
    }


    // --- MOVE MAP ADAPTER (Put this class inside MainActivity or separate file) ---
    inner class MoveMapAdapter(
        private val items: List<MoveMapItem>,
        private val onCharacterClick: (SlotProfile) -> Unit,
        private val onLocationClick: (Area, LocationSlot) -> Unit,
        private val onSpyClick: (Area, LocationSlot) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val TYPE_HEADER = 0
        private val TYPE_LOCATION = 1

        override fun getItemViewType(position: Int): Int =
            if (items[position] is MoveMapItem.Header) TYPE_HEADER else TYPE_LOCATION

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_HEADER) {
                val view = inflater.inflate(android.R.layout.simple_list_item_1, parent, false) // Simple text header
                view.setBackgroundColor(Color.DKGRAY)
                (view as TextView).setTextColor(Color.CYAN)
                object : RecyclerView.ViewHolder(view) {}
            } else {
                // Custom layout for Location row: [Name] [Spy Btn] [Recycler of Avatars]
                val view = LinearLayout(parent.context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    setPadding(16, 16, 16, 16)
                    background = ContextCompat.getDrawable(context, R.drawable.halo_shape) // Use your shape
                }
                LocationViewHolder(view)
            }
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            if (holder is LocationViewHolder && item is MoveMapItem.LocationRow) {
                holder.bind(item)
            } else if (item is MoveMapItem.Header) {
                (holder.itemView as TextView).text = "AREA: ${item.area.name}"
            }
        }

        inner class LocationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            // Programmatically building the row to avoid new XML file requirement
            val nameView = TextView(itemView.context).apply {
                textSize = 18f; setTextColor(Color.WHITE)
            }
            val spyBtn = Button(itemView.context, null, android.R.attr.buttonStyleSmall).apply {
                text = "Spy"
                textSize = 10f
            }
            val charList = RecyclerView(itemView.context).apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            }
            val topRow = LinearLayout(itemView.context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(nameView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(spyBtn)
            }

            init {
                (itemView as LinearLayout).addView(topRow)
                (itemView as LinearLayout).addView(charList)
            }

            fun bind(row: MoveMapItem.LocationRow) {
                nameView.text = row.location.name

                // Highlight if spying
                if (spyingArea == row.area.name && spyingLocation == row.location.name) {
                    nameView.setTextColor(Color.GREEN)
                    spyBtn.text = "Viewing"
                    spyBtn.isEnabled = false
                } else {
                    nameView.setTextColor(Color.WHITE)
                    spyBtn.text = "Spy"
                    spyBtn.isEnabled = true
                }

                // Populate Characters in this room
                charList.adapter = CharacterRowAdapter(
                    row.charsInRoom.map { it.toCharacterProfileStub() }, // Use your helper
                    onClick = { profile ->
                        val slot = sessionProfile.slotRoster.find { it.slotId == profile.id }
                        if (slot != null) onCharacterClick(slot)
                    }
                ) { profile, view ->
                    // Highlight selected character for moving
                    if (profile.id == moveSelectedSlotId) {
                        view.alpha = 1.0f
                        view.setBackgroundColor(Color.YELLOW)
                    } else {
                        view.alpha = 0.7f
                        view.setBackgroundColor(Color.TRANSPARENT)
                    }
                }

                spyBtn.setOnClickListener { onSpyClick(row.area, row.location) }
                itemView.setOnClickListener { onLocationClick(row.area, row.location) }
            }
        }
    }

    private fun updateUserActiveSlot(slotId: String?) {
        val userMap = sessionProfile.userMap.toMutableMap()
        val me = userMap[userId] ?: return

        // Update my entry
        userMap[userId] = me.copy(activeSlotId = slotId)

        // Push to Firestore
        FirebaseFirestore.getInstance().collection("sessions").document(sessionId)
            .update("userMap", userMap)
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update status", Toast.LENGTH_SHORT).show()
            }
    }

    // Helper Sealed Class
    sealed class MoveMapItem {
        data class Header(val area: Area) : MoveMapItem()
        data class LocationRow(val area: Area, val location: LocationSlot, val charsInRoom: List<SlotProfile>) : MoveMapItem()
    }
}
