package com.albirich.RealmsAI

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
import com.albirich.RealmsAI.FirestoreClient.db
import com.albirich.RealmsAI.ai.Facilitator
import com.albirich.RealmsAI.ai.PromptBuilder
import com.albirich.RealmsAI.models.*
import com.albirich.RealmsAI.models.SlotProfile
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
import com.albirich.RealmsAI.FacilitatorResponseParser.Action
import com.albirich.RealmsAI.FacilitatorResponseParser.NewNPCData
import com.albirich.RealmsAI.FacilitatorResponseParser.updateRelationshipLevel
import com.albirich.RealmsAI.adapters.CollectionAdapter.CharacterRowAdapter
import com.albirich.RealmsAI.ai.Director
import com.albirich.RealmsAI.ai.PromptBuilder.buildDiceRoll
import com.albirich.RealmsAI.ai.PromptBuilder.buildGodModePrompt
import com.albirich.RealmsAI.ai.PromptBuilder.buildMurderMysteryInfo
import com.albirich.RealmsAI.ai.PromptBuilder.buildMurdererInfo
import com.albirich.RealmsAI.ai.PromptBuilder.buildNPCGeneration
import com.albirich.RealmsAI.ai.PromptBuilder.buildVNPrompt
import com.albirich.RealmsAI.models.ModeSettings.CharacterClass
import com.albirich.RealmsAI.models.ModeSettings.RPGSettings
import com.albirich.RealmsAI.models.ModeSettings.VNRelationship
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
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
//    private lateinit var chatTitleView: TextView
//    private lateinit var chatDescriptionView: TextView
    private lateinit var chatRecycler: RecyclerView
    private lateinit var rollRecyclerView: RecyclerView
    private lateinit var messageEditText: EditText
    private lateinit var sendButton: Button
    private lateinit var topOverlay: View
    private lateinit var toggleChatInputButton: ImageButton
    private lateinit var anchorViews: List<List<ImageView>>
    private lateinit var chatInputGroup: View
    private lateinit var toggleButtonContainer: View
    private lateinit var rpgToggleContainer: View
    private lateinit var rollHistoryButton: ImageButton
    private lateinit var characterSheetButton: ImageButton
    private lateinit var backgroundImageView:ImageView
    private var isDescriptionExpanded = false
    private var lastMultiplayerValue: Boolean? = null
    private lateinit var resendButton: ImageButton
    private lateinit var typingIndicatorBar: android.view.ViewGroup
    private lateinit var toggleControlButton: ImageButton
    private lateinit var toggleChatButton: ImageButton
    private lateinit var moveButton: Button
    private lateinit var personaButton: Button
    private lateinit var optionButton: Button
    // Dice roll UI elements
    private lateinit var rollButton: ImageButton
    private lateinit var diceImageView: ImageView
    private val expandedAreaIds = mutableSetOf<String>()

    // State
    private lateinit var sessionProfile: SessionProfile
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var rollAdapter: ChatAdapter
    private val visibleAvatarSlotIds = MutableList(4) { "" }
    private val avatarSlotLocked = BooleanArray(4) { false }
    private var historyLoaded = false
    private var lastMessageId: String? = null
    private val processedMessageIds = mutableSetOf<String>()

    private var apiRoute: String? = ""
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
    private lateinit var nextSlotId: String
    private lateinit var previousSpeakerId: String

    private var currentUser: SessionUser? = null

    private var activationRound = 0
    private var maxActivationRounds = 3

    enum class ButtonState { SEND, INTERRUPT, WAITING }
    private var currentState: ButtonState = ButtonState.SEND
    private var aiJob: Job? = null


    // --- STATE VARIABLES ---
    private var spyingArea: String? = null
    private var spyingLocation: String? = null
    private var moveSelectedSlotId: String? = null
    private var forcedNextSpeakerId: String? = null
    private var isColorBlindMode = false

    // Helper to check if I am the Host (Index 0 in userList)
    private val isHost: Boolean
        get() = sessionProfile.userList.isNotEmpty() && sessionProfile.userList[0] == userId
    private val cachedLorebooks = mutableMapOf<String, Lorebook>()

    private val bubblecolorOptions = listOf(
        "Black" to "#000000",
        "Blue" to "#2196F3",
        "Green" to "#4CAF50",
        "Orange" to "#FF9800",
        "Pink" to "#e86cbe",
        "Purple" to "#c778f5",
        "Red" to "#ce0202",
        "White" to "#FFFFFF",
        "Yellow" to "#FFEB3B"
    )
    private val textcolorOptions = listOf(
        "Black" to "#000000",
        "Blue" to "#213af3",
        "Green" to "#098217",
        "Orange" to "#cd6a00",
        "Pink" to "#E91E63",
        "Purple" to "#A200FF",
        "Red" to "#970606",
        "White" to "#e3dfdf",
        "Yellow" to "#cdd54b"
    )

    // Friendly Name -> Internal ID
    private val availableModels = mapOf(
        //"MythoLite" to "mytholite",
        //"MythoMax" to "mythomax",
        //"Weaver Alpha" to "weaver-alpha",
        //"Magnum 72B v4" to "magnum-72b-v4",
        //"Goliath 120b" to "goliath-120b",
        "DeepSeek- V4" to "deepseek",
        "Grok 4.1" to "grok4.1",
        "Openai gpt-oss-120b" to "openai",
        "Gemini 2.5" to "gemini",
        "StepFun3.5" to "step3.5",
        "Z-AI GLM5" to "z-ai",
        "Acree" to "acree",
        "Nemo (12B)" to "nemo",
        "Xiaomi" to "xiaomi",
        "Gemini 3.1" to "gemini3.1",
        "Mistral Small" to "mistral_small",
        // "Hunter Alpha" to "hunter_alpha",
        "Moonshot" to "moonshotai",
        "Mistral Medium" to "mistral_med",
        "Minimax" to "minimax",
        "Nemotron3" to "nvidia",
        "OpenAI GPT-4o" to "openai-gpt-4o",
        "Haiku3" to "claude",
        // "Qwen3" to "qwen"
    )

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
//        chatTitleView = findViewById(R.id.chatTitle)
//        chatDescriptionView = findViewById(R.id.chatDescription)
        chatRecycler = findViewById(R.id.chatRecyclerView)
        rollRecyclerView = findViewById(R.id.rollRecyclerView)
        messageEditText = findViewById(R.id.messageEditText)
        sendButton = findViewById(R.id.sendButton)
        topOverlay = findViewById(R.id.topOverlay)
        toggleChatInputButton = findViewById(R.id.toggleChatInputButton)
        backgroundImageView = findViewById(R.id.backgroundImageView)

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
        // chatTitleView.text = sessionProfile.title
        // val descriptionHeader = findViewById<LinearLayout>(R.id.descriptionHeader)
        // val descriptionDropdown = findViewById<LinearLayout>(R.id.descriptionDropdown)
        // val chatDescriptionView = findViewById<TextView>(R.id.chatDescription)
        // val descriptionToggle = findViewById<ImageView>(R.id.descriptionToggle)
        chatId = sessionProfile.chatId

        val rootView = findViewById<View>(R.id.chatRoot)
        val avatarContainer = findViewById<View>(R.id.avatarContainer)
        anchorViews = listOf(
            // Anchor 0 (Front Left)
            listOf<ImageView>(
                findViewById(R.id.anchor0_depth1),
                findViewById(R.id.anchor0_depth2),
                findViewById(R.id.anchor0_depth3),
                findViewById(R.id.anchor0_depth4)
            ),
            // Anchor 1 (Front Right)
            listOf<ImageView>(
                findViewById(R.id.anchor1_depth1),
                findViewById(R.id.anchor1_depth2),
                findViewById(R.id.anchor1_depth3),
                findViewById(R.id.anchor1_depth4)
            ),
            // Anchor 2 (Back Left)
            listOf<ImageView>(
                findViewById(R.id.anchor2_depth1),
                findViewById(R.id.anchor2_depth2),
                findViewById(R.id.anchor2_depth3),
                findViewById(R.id.anchor2_depth4)
            ),
            // Anchor 3 (Back Right)
            listOf<ImageView>(
                findViewById(R.id.anchor3_depth1),
                findViewById(R.id.anchor3_depth2),
                findViewById(R.id.anchor3_depth3),
                findViewById(R.id.anchor3_depth4)
            )
        )
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
//        chatDescriptionView.text = sessionProfile.sessionDescription
//        descriptionDropdown.visibility = View.GONE
//
//        descriptionHeader.setOnClickListener {
//            isDescriptionExpanded = !isDescriptionExpanded
//            if (isDescriptionExpanded) {
//                descriptionDropdown.visibility = View.VISIBLE
//                // Optional: Animate arrow down (expanded)
//                descriptionToggle.setImageResource(R.drawable.ic_expand_less) // up arrow
//            } else {
//                descriptionDropdown.visibility = View.GONE
//                // Optional: Animate arrow up (collapsed)
//                descriptionToggle.setImageResource(R.drawable.ic_expand_more) // down arrow
//            }
//        }
        var activeSlotId = sessionProfile.userMap[userId]?.activeSlotId
        // Adapter setup (pass in modern fields)
        val isMultiplayer = sessionProfile.userMap.size > 1

        chatAdapter = ChatAdapter(
            mutableListOf(),
            { lastPos -> if (lastPos >= 0) chatRecycler.smoothScrollToPosition(lastPos) },
            sessionProfile.slotRoster,
            sessionProfile.userMap.values.toList(),
            userId,

            // --- 1. THE EXISTING "REWIND & RESEND" EDIT ---
            onEditMessage = { editedMessage, position ->
                checkServerStatusBeforeSending {
                    checkMessageLimit {
                        lifecycleScope.launch {
                            try {
                                val deletedIds = saveEditedMessageAndDeleteFollowing(editedMessage, position)
                                deleteFromSlotPersonalHistories(sessionId, deletedIds)
                                deleteMemoriesFromSession(deletedIds)

                                if (!mySlotId.isNullOrBlank()) {
                                    addToPersonalHistoryFirestore(sessionId, mySlotId!!, editedMessage)
                                }

                                if (position < chatAdapter.itemCount) {
                                    chatAdapter.removeMessagesFrom(position)
                                    chatAdapter.insertMessageAt(position, editedMessage)
                                }
                                sendToAI(editedMessage.text)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing edit", e)
                            }
                        }
                    }
                }
            },

            // --- 2. NEW: "INLINE EDIT" (Just change text, don't rewind) ---
            onInlineEditMessage = { editedMessage, position ->
                lifecycleScope.launch {
                    try {
                        // 1. Update in the main session history
                        updateMessageTextInFirestore(sessionId, editedMessage)

                        // 2. Update in all slot personal histories
                        updateMessageInSlotPersonalHistories(sessionId, editedMessage)

                        // 3. Update the UI
                        if (position < chatAdapter.itemCount) {
                            // You might need to add this simple helper function to your ChatAdapter!
                            chatAdapter.updateMessageAt(position, editedMessage)
                        }
                        Toast.makeText(this@MainActivity, "Message updated!", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error inline editing", e)
                        Toast.makeText(this@MainActivity, "Failed to edit message.", Toast.LENGTH_SHORT).show()
                    }
                }
            },

            // --- 3. NEW: PIN MESSAGE ---
            onPinMessage = { messageToPin ->
                val db = FirebaseFirestore.getInstance()

                // Add the message ID to the pinnedMessages array
                db.collection("sessions").document(sessionId)
                    .update("pinnedMessages", FieldValue.arrayUnion(messageToPin.id))
                    .addOnSuccessListener {
                        Toast.makeText(this@MainActivity, "Message Pinned!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        // Fallback: If the array doesn't exist yet, create it!
                        db.collection("sessions").document(sessionId)
                            .update("pinnedMessages", listOf(messageToPin.id))
                    }
            },

            // --- 4. THE EXISTING DELETE ---
            onDeleteMessages = { fromPosition ->
                val targetMessage = chatAdapter.getMessageAt(fromPosition)
                if (targetMessage.timestamp != null) {
                    lifecycleScope.launch {
                        val deletedIds = deleteMessageAndFollowing(targetMessage)
                        deleteFromSlotPersonalHistories(sessionId, deletedIds)
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
            onEditMessage = { _, _ -> },
            onInlineEditMessage = { _, _ -> },
            onPinMessage = { messageToPin ->
                val db = FirebaseFirestore.getInstance()

                // Add the message ID to the pinnedMessages array
                db.collection("sessions").document(sessionId)
                    .update("pinnedMessages", FieldValue.arrayUnion(messageToPin.id))
                    .addOnSuccessListener {
                        Toast.makeText(this@MainActivity, "Message Pinned!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        // Fallback: If the array doesn't exist yet, create it!
                        db.collection("sessions").document(sessionId)
                            .update("pinnedMessages", listOf(messageToPin.id))
                    }
            },
            onDeleteMessages = { fromPosition ->
                val targetMessage = rollAdapter.getMessageAt(fromPosition)
                if (targetMessage.timestamp != null) {
                    lifecycleScope.launch {
                        val deletedIds = deleteMessageAndFollowing(targetMessage)
                        deleteFromSlotPersonalHistories(sessionId, deletedIds)
                        deleteMemoriesFromSession(deletedIds)
                        rollAdapter.removeMessagesFrom(fromPosition)
                    }
                }
            },
            isMultiplayer = isMultiplayer,
            mode = AdapterMode.ROLL_HISTORY,
            onReRoll = { msg, pos ->
                rerollAndUpdateMessage(msg) }
        )

        rollRecyclerView.layoutManager = LinearLayoutManager(this)
        rollRecyclerView.adapter = rollAdapter

        // Hide all avatars, then show only those in use
        anchorViews.flatten().forEach { it.visibility = View.INVISIBLE }

        // New session or load old chat?
        when (intent.getStringExtra("ENTRY_MODE") ?: "CREATE") {

            "CREATE" -> {
                Log.d("entering", "entrymode: CREATE")
                chatAdapter.clearMessages()
                // Determine if all users have selected a character
                val allPlayersChosen = sessionProfile.userMap.values.all { it.activeSlotId != null }

                initialGreeting = intent.getStringExtra("GREETING") ?: sessionProfile.initialGreeting
                Log.d("greeting check", "greeting = $initialGreeting")
                val greetingText = initialGreeting
                Log.d("greeting check", "greeting text is $greetingText")
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
                    var updatedRoster = sessionProfile.slotRoster
                    // We use a Map to keep track of how many active characters are in each room
                    val activeCountPerLoc = mutableMapOf<Pair<String?, String?>, Int>()

                    // Sort players first so they always get priority for the 10 active slots!
                    val prioritizedSlots = updatedRoster.sortedByDescending { it.profileType == "player" }

                    val finalizedRoster = prioritizedSlots.map { slot ->
                        if (slot.activityStatus) {
                            val locKey = Pair(slot.lastActiveArea, slot.lastActiveLocation)
                            val currentActive = activeCountPerLoc[locKey] ?: 0

                            if (currentActive < 10) {
                                activeCountPerLoc[locKey] = currentActive + 1
                                slot // Keep active
                            } else {
                                slot.copy(activityStatus = false) // Force to background!
                            }
                        } else {
                            slot
                        }
                    }

                    // Re-sort back to the original order (optional, but keeps the UI clean)
                    val originalOrderIds = updatedRoster.map { it.slotId }
                    updatedRoster = finalizedRoster.sortedBy { originalOrderIds.indexOf(it.slotId) }

                    // Update local profile and save to Firestore
                    sessionProfile = sessionProfile.copy(slotRoster = updatedRoster)
                    saveSessionProfile(sessionProfile, sessionId)
                    // All players have characters – send greeting immediately
                    updateButtonState(ButtonState.INTERRUPT)
                    if (greetingText!!.isNotBlank()) {
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
                    updateButtonState(ButtonState.WAITING)
                }
                updateRPGToggleVisibility()
            }
            "GUEST" ->{
                Log.d("entering", "entrymode: GUEST")
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
        Log.d("Entry_mode", "mode: ${sessionProfile.chatMode}")
        val myUserId = FirebaseAuth.getInstance().currentUser?.uid
        val mySessionUser = sessionProfile?.userMap?.get(myUserId)
        val godMode = sessionProfile.enabledModes.contains("god_mode")
        if (mySessionUser?.activeSlotId == null) {
            if (godMode && isHost) {
                assignSlotToUser(myUserId!!, "narrator")
            } else {
                showCharacterPickerDialog(sessionProfile!!.slotRoster) { selectedSlot ->
                    assignSlotToUser(myUserId!!, selectedSlot.slotId)
                }
            }
        }

        mySlotId = sessionProfile.userMap[userId]?.activeSlotId
        // Toggle input visibility
        toggleChatInputButton.setOnClickListener {
            Log.d("togglebutton debug", "toggle chatinputbutton")
            val isVisible = chatInputGroup.visibility == View.VISIBLE
            chatInputGroup.visibility = if (isVisible) View.GONE else View.VISIBLE
        }

        typingIndicatorBar?.visibility = View.GONE

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

            val voidArea = Area(id = "void_id", name = "Unknown")
            val starFieldLoc = LocationSlot(id = "star_field_id", name = "Unknown", description = "The space between worlds.")

            items.add(MoveMapItem.Header(voidArea))

            if (expandedAreaIds.contains(voidArea.id)) {
                val charsInLimbo = sessionProfile.slotRoster.filter {
                    it.lastActiveArea == "Unknown" || it.lastActiveLocation == "Unknown"
                }
                items.add(MoveMapItem.LocationRow(voidArea, starFieldLoc, charsInLimbo))
            }

            sessionProfile.areas.forEach { area ->
                items.add(MoveMapItem.Header(area))

                // ONLY add locations if this area is expanded
                if (expandedAreaIds.contains(area.id)) {
                    area.locations.forEach { loc ->
                        val charsHere = sessionProfile.slotRoster.filter {
                            it.lastActiveArea == area.name && it.lastActiveLocation == loc.name
                        }
                        items.add(MoveMapItem.LocationRow(area, loc, charsHere))
                    }
                }
            }

            recycler.adapter = MoveMapAdapter(
                items,
                onHeaderClick = { area ->
                    // Toggle Logic: If in set, remove it; if not, add it
                    if (expandedAreaIds.contains(area.id)) {
                        expandedAreaIds.remove(area.id)
                    } else {
                        expandedAreaIds.add(area.id)
                    }
                    showMove() // Refresh the list with new items
                },
                onCharacterClick = { slot ->
                    moveSelectedSlotId = slot.slotId
                    Toast.makeText(this, "Selected ${slot.name}. Tap a location to move them.", Toast.LENGTH_SHORT).show()
                    recycler.adapter?.notifyDataSetChanged()
                },
                onLocationClick = { area, loc ->
                    if (moveSelectedSlotId != null) {

                        // 1. Count how many ACTIVE characters are ALREADY in the destination room
                        val activeInDestination = sessionProfile.slotRoster.count {
                            it.lastActiveArea == area.name &&
                                    it.lastActiveLocation == loc.name &&
                                    it.activityStatus &&
                                    it.slotId != moveSelectedSlotId // Exclude the moving char just in case
                        }

                        // 2. Find the character being moved
                        val movingChar = sessionProfile.slotRoster.find { it.slotId == moveSelectedSlotId }
                        val movingCharName = movingChar?.name ?: "Character"

                        // NEW: Grab the IDs of any sleeping components fused inside this character
                        val componentIds = movingChar?.linkedTo
                            ?.filter { it.type.lowercase() == "unfuse" }
                            ?.map { it.targetId }
                            ?.toSet() ?: emptySet()

                        // 3. Determine their new status (Deactivate if 10+, otherwise keep their current status)
                        val willBeForcedInactive = activeInDestination >= 10
                        val newStatus = if (willBeForcedInactive) false else (movingChar?.activityStatus ?: true)

                        // 4. MOVE LOGIC
                        val updatedRoster = sessionProfile.slotRoster.map { slot ->
                            if (slot.slotId == moveSelectedSlotId) {
                                // Move the main character
                                slot.copy(
                                    lastActiveArea = area.name,
                                    lastActiveLocation = loc.name,
                                    activityStatus = newStatus
                                )
                            } else if (slot.slotId in componentIds) {
                                // Move the sleeping components alongside them!
                                // (We don't touch their activityStatus so they stay asleep)
                                slot.copy(
                                    lastActiveArea = area.name,
                                    lastActiveLocation = loc.name
                                )
                            } else {
                                slot
                            }
                        }

                        sessionProfile = sessionProfile.copy(slotRoster = updatedRoster)
                        saveSessionProfile(sessionProfile, sessionId)

                        // 5. User Feedback
                        if (willBeForcedInactive && movingChar?.activityStatus == true) {
                            Toast.makeText(this, "Moved to ${loc.name}. Room is full, so $movingCharName was moved to the background.", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "Moved to ${loc.name}", Toast.LENGTH_SHORT).show()
                        }

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

            val initialSelection = presentSlots.indexOfFirst { it.slotId == activeSlotId }

            if (initialSelection >= 0) {
                // Set 'false' for animate to avoid unwanted triggering during init
                characterSpinner.setSelection(initialSelection, false)
            }

            var selectedAvatarSlotIndex = -1 // default to first

            slotSelectors.forEachIndexed { i, btn ->
                btn.setOnClickListener {
                    // If they click the already selected one, unselect it
                    selectedAvatarSlotIndex = if (selectedAvatarSlotIndex == i) -1 else i

                    slotSelectors.forEachIndexed { j, button ->
                        val isCurrent = (selectedAvatarSlotIndex == j)
                        button.isSelected = isCurrent
                        if (isCurrent) {
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
                val poseName = selectedPose?.name
                val outfitName = outfitSpinner.selectedItem?.toString()

                if (poseName != null) {
                    // --- STEP 1: Update the SlotProfile data ---
                    val updatedSlotRoster = sessionProfile.slotRoster.map { slot ->
                        if (slot.slotId == selectedCharacter.slotId) {
                            slot.copy(
                                pose = poseName,
                                currentOutfit = outfitName ?: slot.currentOutfit
                            )
                        } else slot
                    }

                    // --- STEP 2: Handle UI Lineup ---
                    if (selectedAvatarSlotIndex != -1) {
                        // A slot IS selected: Force this character into that index
                        // and remove them from any other index they might be in
                        avatarSlotAssignments.entries.forEach { if (it.value == selectedCharacter.slotId) it.setValue(null) }

                        // If the slot was locked, we honor the user's force-set and keep it locked
                        avatarSlotAssignments[selectedAvatarSlotIndex] = selectedCharacter.slotId
                    }
                    // If selectedAvatarSlotIndex == -1, we do NOTHING to the assignments.
                    // updateAvatarsFromSlots will naturally pick up the new pose/outfit
                    // if they are already on screen.

                    // --- STEP 3: Sync and Refresh ---
                    sessionProfile = sessionProfile.copy(slotRoster = updatedSlotRoster)
                    saveSessionProfile(sessionProfile, sessionId)

                    // Refresh the ImageViews
                    updateAvatarsFromSlots(sessionProfile.slotRoster, avatarSlotAssignments)

                    Toast.makeText(this, "Avatar updated!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Select a pose first!", Toast.LENGTH_SHORT).show()
                }
            }

            takeControl.setOnClickListener {
                val selectedSlot = presentSlots[characterSpinner.selectedItemPosition]

                // --- GUARD CLAUSE ---
                if (selectedSlot.profileType == "player") {
                    Toast.makeText(this, "Character is already being controlled by a player", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val oldActiveSlotId = sessionProfile.userMap[userId]?.activeSlotId

                // 1. Update slotRoster with correct profile types
                val updatedSlotRoster = sessionProfile.slotRoster.map { slot ->
                    when (slot.slotId) {
                        oldActiveSlotId -> slot.copy(profileType = "bot") // old controlled: demote to bot
                        selectedSlot.slotId -> slot.copy(profileType = "player") // new controlled: promote to player
                        else -> slot
                    }
                }

                // 2. THE FIX: Targeted Firestore Update
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val updates = mapOf(
                    "slotRoster" to updatedSlotRoster,
                    "userMap.$userId.activeSlotId" to selectedSlot.slotId // Surgical dot-notation update!
                )

                db.collection("sessions").document(sessionId)
                    .update(updates)
                    .addOnSuccessListener {
                        // 3. Manually update the local state ONLY after the DB confirms the save
                        val updatedUserMap = sessionProfile.userMap.toMutableMap()
                        val currentUser = updatedUserMap[userId]
                        if (currentUser != null) {
                            updatedUserMap[userId] = currentUser.copy(activeSlotId = selectedSlot.slotId)
                        }

                        sessionProfile = sessionProfile.copy(
                            slotRoster = updatedSlotRoster,
                            userMap = updatedUserMap
                        )

                        Toast.makeText(this, "You are now controlling ${selectedSlot.name}", Toast.LENGTH_SHORT).show()

                        // Note: If this is inside a dialog, you probably want to call dialog.dismiss() here!
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to take control: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
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
            val changeTitleBtn = findViewById<Button>(R.id.titleBtn)
            val pinnedMessagesBtn = findViewById<Button>(R.id.pinnedMessagesBtn)
            val cloneBtn = findViewById<Button>(R.id.cloneBtn)
            val messageCounterTv = findViewById<TextView>(R.id.optionsMessageCounter)
            val modelSpinner = findViewById<Spinner>(R.id.modelSpinner)
            val modelNames = availableModels.keys.toList()
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modelNames)
            val colorblindModeBtn = findViewById<Button>(R.id.colorblindModeBtn)
            val timeskipBtn = findViewById<Button>(R.id.timeskipBtn)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            modelSpinner.adapter = adapter

            // 1. Find the correct index FIRST
            val currentId = sessionProfile.aiModel ?: "Grok 4.1" // Use your actual default
            val currentKey = availableModels.entries.find { it.value == currentId }?.key ?: "Grok 4.1"
            val initialPosition = modelNames.indexOf(currentKey)

            // 2. Set the selection BEFORE the listener to avoid the "Loop of Death"
            if (initialPosition >= 0) {
                modelSpinner.setSelection(initialPosition, false)
            }

            modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                    val selectedId = availableModels[modelNames[position]] ?: "Grok 4.1"

                    // Use a local variable to prevent race conditions during the save
                    val currentIdInProfile = sessionProfile?.aiModel ?: "Grok 4.1"

                    if (currentIdInProfile != selectedId) {
                        val sessionId = sessionId ?: return
                        val db = FirebaseFirestore.getInstance()

                        // 1. Update the local object
                        sessionProfile = sessionProfile.copy(aiModel = selectedId)

                        // 2. Push directly to the specific field in Firebase
                        db.collection("sessions").document(sessionId)
                            .update("aiModel", selectedId)
                            .addOnSuccessListener {
                                Log.d("model_debug", "Firebase updated: $selectedId")
                                Toast.makeText(this@MainActivity, "Model: ${modelNames[position]}", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                Log.e("model_debug", "Firebase failed to update", e)
                            }
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>) {}
            }

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
            val godMode = (sessionProfile.enabledModes.contains("god_mode"))
            directorBtn.visibility = if (isHost && !godMode) View.VISIBLE else View.INVISIBLE

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
                        Toast.makeText(this, "Returned to ${selectedSlot.name}", Toast.LENGTH_SHORT)
                            .show()
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

            cloneBtn.setOnClickListener {
                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener

                // 1. Create a prompt for the new timeline's name
                val editText = EditText(this).apply {
                    hint = "Enter a title for the branched chat..."
                    setText("Branch of ${sessionProfile.title}")
                    setPadding(32, 32, 32, 32)
                }

                AlertDialog.Builder(this)
                    .setTitle("Branch Timeline?")
                    .setMessage("This will create a private copy of this chat history, dropping all other players. What do you want to name it?")
                    .setView(editText)
                    .setPositiveButton("Branch") { _, _ ->
                        val newTitle = editText.text.toString().ifBlank { "Branched Chat" }
                        optionsOptions.visibility = View.GONE // Hide the menu
                        branchCurrentSession(newTitle, currentUserId)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            val eventsBtn = findViewById<Button>(R.id.eventsBtn)

            // Only the host gets to play God with events
            eventsBtn.visibility = if (isHost) View.VISIBLE else View.GONE

            eventsBtn.setOnClickListener {
                showEventsDialog()
            }

            changeTitleBtn.setOnClickListener {
                showTitleChangeDialog()
            }

            pinnedMessagesBtn.setOnClickListener {
                val pinnedIds = sessionProfile.pinnedMessages ?: emptyList()

                if (pinnedIds.isEmpty()) {
                    Toast.makeText(this, "No pinned messages yet. Long-press a message in chat to pin it!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Pull the actual messages from the adapter so we know their exact positions
                val currentMessages = chatAdapter.getMessages()
                val pinnedMsgs = currentMessages.filter { it.id in pinnedIds }

                if (pinnedMsgs.isEmpty()) {
                    Toast.makeText(this, "Pinned messages aren't loaded in the current view.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Format a nice preview list for the dialog
                val displayNames = pinnedMsgs.map { msg ->
                    val sender = msg.displayName ?: "Player"
                    val snippet = if (msg.text.length > 40) msg.text.take(37) + "..." else msg.text
                    "$sender: $snippet"
                }

                AlertDialog.Builder(this)
                    .setTitle("Pinned Messages")
                    .setItems(displayNames.toTypedArray()) { _, which ->
                        val selectedMsg = pinnedMsgs[which]
                        val position = currentMessages.indexOf(selectedMsg)

                        // --- NEW: SECONDARY OPTIONS MENU ---
                        AlertDialog.Builder(this)
                            .setTitle("Options")
                            .setItems(arrayOf("Jump to Message", "Give Memory to Character", "Remove Memory from Character", "Unpin Global")) { _, actionIndex ->
                                when (actionIndex) {
                                    0 -> {
                                        // 1. JUMP TO MESSAGE
                                        if (position >= 0) {
                                            optionsOptions.visibility = View.GONE
                                            val chatRecycler = findViewById<RecyclerView>(R.id.chatRecyclerView)
                                            chatRecycler.smoothScrollToPosition(position)
                                        }
                                    }
                                    1 -> {
                                        // 2. GIVE TO CHARACTER
                                        val roster = sessionProfile.slotRoster
                                        if (roster.isEmpty()) return@setItems

                                        val charNames = roster.map { it.name }.toTypedArray()
                                        AlertDialog.Builder(this)
                                            .setTitle("Who should remember this?")
                                            .setItems(charNames) { _, charIndex ->
                                                val targetSlot = roster[charIndex]
                                                val currentPins = targetSlot.pinnedMessages ?: emptyList()

                                                if (currentPins.contains(selectedMsg.id)) {
                                                    Toast.makeText(this, "${targetSlot.name} already remembers this.", Toast.LENGTH_SHORT).show()
                                                    return@setItems
                                                }
                                                if (currentPins.size >= 5) {
                                                    Toast.makeText(this, "${targetSlot.name} has the max 5 memories. Unpin one first!", Toast.LENGTH_LONG).show()
                                                    return@setItems
                                                }

                                                val updatedRoster = roster.map { slot ->
                                                    if (slot.slotId == targetSlot.slotId) {
                                                        slot.copy(pinnedMessages = (currentPins + selectedMsg.id).toMutableList())
                                                    } else slot
                                                }

                                                sessionProfile = sessionProfile.copy(slotRoster = updatedRoster)
                                                val sessionId = intent.getStringExtra("SESSION_ID") ?: return@setItems
                                                FirebaseFirestore.getInstance().collection("sessions").document(sessionId)
                                                    .update("slotRoster", updatedRoster)
                                                    .addOnSuccessListener { Toast.makeText(this, "Memory given to ${targetSlot.name}!", Toast.LENGTH_SHORT).show() }
                                            }
                                            .setNegativeButton("Cancel", null)
                                            .show()
                                    }
                                    2 -> {
                                        // 3. REMOVE FROM CHARACTER (THE NEW FIX!)
                                        val roster = sessionProfile.slotRoster
                                        // Find only the characters who currently have this pinned
                                        val rememberingSlots = roster.filter { it.pinnedMessages?.contains(selectedMsg.id) == true }

                                        if (rememberingSlots.isEmpty()) {
                                            Toast.makeText(this, "No characters currently remember this.", Toast.LENGTH_SHORT).show()
                                            return@setItems
                                        }

                                        val charNames = rememberingSlots.map { it.name }.toTypedArray()
                                        AlertDialog.Builder(this)
                                            .setTitle("Make who forget?")
                                            .setItems(charNames) { _, charIndex ->
                                                val targetSlot = rememberingSlots[charIndex]
                                                val currentPins = targetSlot.pinnedMessages ?: emptyList()

                                                // Remove the ID from their list
                                                val updatedPins = currentPins.filter { it != selectedMsg.id }
                                                val updatedRoster = roster.map { slot ->
                                                    if (slot.slotId == targetSlot.slotId) {
                                                        slot.copy(pinnedMessages = updatedPins.toMutableList())
                                                    } else slot
                                                }

                                                sessionProfile = sessionProfile.copy(slotRoster = updatedRoster)
                                                val sessionId = intent.getStringExtra("SESSION_ID") ?: return@setItems
                                                FirebaseFirestore.getInstance().collection("sessions").document(sessionId)
                                                    .update("slotRoster", updatedRoster)
                                                    .addOnSuccessListener { Toast.makeText(this, "${targetSlot.name} forgot this memory.", Toast.LENGTH_SHORT).show() }
                                            }
                                            .setNegativeButton("Cancel", null)
                                            .show()
                                    }
                                    3 -> {
                                        // 4. UNPIN GLOBAL
                                        val updatedPins = sessionProfile.pinnedMessages?.filter { it != selectedMsg.id } ?: emptyList()
                                        sessionProfile = sessionProfile.copy(pinnedMessages = updatedPins.toMutableList())

                                        val sessionId = intent.getStringExtra("SESSION_ID") ?: return@setItems
                                        FirebaseFirestore.getInstance().collection("sessions").document(sessionId)
                                            .update("pinnedMessages", updatedPins)
                                            .addOnSuccessListener { Toast.makeText(this, "Unpinned globally.", Toast.LENGTH_SHORT).show() }
                                    }
                                }
                            }
                            .show()
                        // -----------------------------------
                    }
                    .setPositiveButton("Close", null)
                    .show()
            }

            // Initial UI state
            colorblindModeBtn.text = if (isColorBlindMode) "Colorblind: ON" else "Colorblind: OFF"

            colorblindModeBtn.setOnClickListener {
                isColorBlindMode = !isColorBlindMode

                // 1. Update Button UI
                colorblindModeBtn.text = if (isColorBlindMode) "Colorblind: ON" else "Colorblind: OFF"

                // 2. Update Chat Adapter
                chatAdapter.setColorBlindMode(isColorBlindMode)

                // 3. Update Roll Adapter (if visible)
                rollAdapter.setColorBlindMode(isColorBlindMode)

                Toast.makeText(this, "High Contrast ${if (isColorBlindMode) "Enabled" else "Disabled"}", Toast.LENGTH_SHORT).show()
            }

            timeskipBtn.setOnClickListener {
                showTimeSkipDialog()
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
                checkServerStatusBeforeSending {
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
                        distributeMessageAndMemories(resendMsg)
                        // (Optional: Trigger AI if you want this to start a new round)
                        sendToAI("")
                    }
                }
            } else {
                checkServerStatusBeforeSending {
                    checkMessageLimit {
                        val greetingText = sessionProfile.initialGreeting
                        // Resend same text but with visibility=false and blank text
                        if (greetingText!!.isNotBlank()) {
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
                        }
                    }
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

            // Build the tabs!
            refreshCharacterTabs()
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

                    // 1. FIRST GATE: Is the AI awake?
                    checkServerStatusBeforeSending {

                        // 2. SECOND GATE: Do they have quota left?
                        checkMessageLimit {

                            // --- DIRECTOR / SPY LOGIC START ---
                            val isDirector = mySlotId == "narrator"

                            // 1. Determine Sender & Location
                            val finalSenderId: String
                            val finalDisplayName: String
                            val targetArea: String?
                            val targetLocation: String?
                            val myChar = sessionProfile?.slotRoster?.find { it.slotId == mySlotId }

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

                            // 2. Update UI State IMMEDIATELY so the app feels snappy
                            updateButtonState(ButtonState.INTERRUPT)
                            setPlayerTyping(false)
                            activationRound = 0
                            messageEditText.text.clear()
                            ignoreTextWatcher = false

                            // LAUNCH COROUTINE FOR VECTOR MATH & DB CALLS
                            lifecycleScope.launch {
                                var matchedPoseName: String? = null

                                // 3. Extract Player Pose (Skip if they are the Narrator!)
                                if (!isDirector && myChar != null) {
                                    val actionText = extractActionText(text)

                                    if (actionText != null) {
                                        val actionVector = getVectorForText(text)
                                        val activeSessionId = sessionProfile?.sessionId

                                        if (actionVector != null && activeSessionId != null) {
                                            val wardrobeOutfits = fetchWardrobeForBaseId(sessionId, myChar.baseCharacterId!!)
                                            Log.d("PosingEngine", "STEP 3: Fetched ${wardrobeOutfits.size} outfits from DB for ${myChar.name}")
                                            val currentOutfit = wardrobeOutfits.find { it.name == myChar.currentOutfit }

                                            if (currentOutfit != null) {
                                                var bestMatchName: String? = null
                                                var highestScore = -1.0

                                                for (pose in currentOutfit.poseSlots) {
                                                    if (pose.vector != null) {
                                                        val score = calculateCosineSimilarity(actionVector, pose.vector!!)
                                                        if (score > highestScore) {
                                                            highestScore = score
                                                            bestMatchName = pose.name
                                                        }
                                                    }
                                                }
                                                matchedPoseName = bestMatchName
                                                Log.d("PosingEngine", "Player matched '$actionText' to pose: $matchedPoseName (Score: $highestScore)")
                                            }
                                        }
                                    }
                                }

                                // 4. Construct Message with the new Pose
                                val timestamp = com.google.firebase.Timestamp.now()
                                val newMessage = ChatMessage(
                                    id = UUID.randomUUID().toString(),
                                    senderId = finalSenderId,
                                    displayName = finalDisplayName,
                                    text = text,
                                    timestamp = timestamp,
                                    area = targetArea,
                                    location = targetLocation,
                                    visibility = true,
                                    pose = matchedPoseName // <--- INJECT THE MATH WINNER HERE
                                )

                                // 5. Send to Firestore
                                val activeSessionId = sessionProfile?.sessionId ?: return@launch
                                val activeChatId = sessionProfile?.chatId ?: ""
                                SessionManager.sendMessage(activeChatId, activeSessionId, newMessage)
                                // 6. Memory Check
                                distributeMessageAndMemories(newMessage)
                                // 6. Trigger AI Loop
                                val userInput = newMessage.text
                                sendToAI(userInput)
                            }
                        }
                    }
                }
            } else if (currentState == ButtonState.INTERRUPT) {
                interruptAILoop()
                updateButtonState(ButtonState.SEND)
            }
        }
    }

    private fun showTimeSkipDialog() {
        val currentRoster = sessionProfile?.slotRoster ?: return

        // 1. Scan the room for all unique Time Skip durations
        val availableSkips = mutableSetOf<Int>()
        currentRoster.forEach { slot ->
            slot.linkedTo
                .filter { it.type.equals("time skip", true) }
                .forEach { link ->
                    val years = link.trigger.toIntOrNull()
                    if (years != null) {
                        availableSkips.add(years)
                    }
                }
        }

        if (availableSkips.isEmpty()) {
            Toast.makeText(this, "No characters in this scene have Time Skip links.", Toast.LENGTH_SHORT).show()
            return
        }

        val sortedSkips = availableSkips.sorted()

        // 2. Build the visual container dynamically
        val mainContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }

        val scrollView = ScrollView(this).apply {
            addView(mainContainer)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Initiate Time Skip")
            .setView(scrollView)
            .setNegativeButton("Cancel", null)
            .create()

        // 3. Build a visual row for each Time Skip option
        sortedSkips.forEach { targetYear ->

            // --- Row Container ---
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(0, 0, 0, 60) // Space between options
            }

            // --- Header Text ---
            val header = TextView(this).apply {
                text = "$targetYear Years Later..."
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 16)
            }
            row.addView(header)

            // --- Avatar Row ---
            val avatarsLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val horizontalScroll = HorizontalScrollView(this).apply {
                addView(avatarsLayout)
                isHorizontalScrollBarEnabled = false
            }
            row.addView(horizontalScroll)

            // --- Figure out who is transforming ---
            var transformCount = 0
            currentRoster.forEach { slot ->
                // Find highest valid skip for this character at THIS target year
                val validSkips = slot.linkedTo
                    .filter { it.type.equals("time skip", true) }
                    .filter { (it.trigger.toIntOrNull() ?: 0) <= targetYear }

                val highestSkip = validSkips.maxByOrNull { it.trigger.toIntOrNull() ?: 0 }

                if (highestSkip != null) {
                    transformCount++

                    // Build their avatar image
                    val imageView = ImageView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(140, 140).apply {
                            setMargins(0, 0, 20, 20)
                        }
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }

                    val avatarUrl = highestSkip.targetAvatar // Grab the URL from the link!

                    if (!avatarUrl.isNullOrBlank()) {
                        Glide.with(this)
                            .load(avatarUrl)
                            .placeholder(R.drawable.silhouette)
                            .circleCrop() // Makes the icons nice and round!
                            .into(imageView)
                    } else {
                        imageView.setImageResource(R.drawable.silhouette)
                    }

                    avatarsLayout.addView(imageView)
                }
            }

            // --- Execution Button ---
            val executeButton = Button(this).apply {
                text = "Fast Forward ($transformCount Characters)"
                setOnClickListener {
                    dialog.dismiss()
                    executeTimeSkip(targetYear)
                }
            }
            row.addView(executeButton)

            mainContainer.addView(row)
        }

        dialog.show()
    }

    private fun executeTimeSkip(yearsPassed: Int) {
        val sessionId = sessionProfile?.sessionId ?: return
        val currentRoster = sessionProfile?.slotRoster?.toMutableList() ?: return

        // 1. Figure out who is transforming and into who!
        val transformations = mutableMapOf<String, String>() // Maps slotId -> new BaseCharacterId

        currentRoster.forEach { slot ->
            // Find all time skips for this character that are <= the selected years
            val validSkips = slot.linkedTo
                .filter { it.type.equals("time skip", true) }
                .filter { (it.trigger.toIntOrNull() ?: 0) <= yearsPassed }

            if (validSkips.isNotEmpty()) {
                // Grab the one with the highest number (e.g. Kakashi grabs 3, Naruto grabs 4)
                val highestSkip = validSkips.maxByOrNull { it.trigger.toIntOrNull() ?: 0 }
                if (highestSkip != null) {
                    transformations[slot.slotId] = highestSkip.targetId
                }
            }
        }

        if (transformations.isEmpty()) return

        // Show a loading dialog because we have to pull from Firestore
        Toast.makeText(this, "Fast forwarding $yearsPassed years...", Toast.LENGTH_SHORT).show()

        val db = FirebaseFirestore.getInstance()
        val newCharacterIds = transformations.values.distinct()

        // 2. Fetch the future versions of the characters!
        db.collection("characters")
            .whereIn(FieldPath.documentId(), newCharacterIds)
            .get()
            .addOnSuccessListener { snap ->
                val futureProfiles = snap.documents.mapNotNull { it.toObject(CharacterProfile::class.java) }
                val profileMap = futureProfiles.associateBy { it.id }

                // 3. Perform the Silent Substitution
                var rosterChanged = false
                for (i in currentRoster.indices) {
                    val slot = currentRoster[i]
                    val futureId = transformations[slot.slotId]
                    val futureProfile = profileMap[futureId]

                    if (futureProfile != null) {
                        rosterChanged = true
                        // We overwrite their visual/personality data, but KEEP their slotId, Area, Location, and History
                        currentRoster[i] = slot.copy(
                            baseCharacterId = futureProfile.id,
                            name = futureProfile.name,
                            summary = futureProfile.summary ?: "",
                            personality = futureProfile.personality ?: "",
                            privateDescription = futureProfile.privateDescription ?: "",
                            avatarUri = futureProfile.avatarUri ?: "",
                            outfits = futureProfile.outfits ?: emptyList(),
                            currentOutfit = futureProfile.currentOutfit ?: "",
                            // Update their links so if they Time Skip AGAIN, they know where to go!
                            linkedTo = futureProfile.linkedToMap.values.flatten()
                        )
                    }
                }

                if (rosterChanged) {
                    // 4. Inject a System message into the Chat History
                    val systemMessage = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        senderId = "system",
                        text = "*** $yearsPassed years have passed... ***",
                        timestamp = Timestamp.now(),
                        messageType = "event"
                    )

                    val newHistory = (sessionProfile?.history ?: emptyList()) + systemMessage

                    // 5. Save the new future to Firestore!
                    db.collection("sessions").document(sessionId)
                        .update(
                            mapOf(
                                "slotRoster" to currentRoster,
                                "history" to newHistory
                            )
                        )
                        .addOnSuccessListener {
                            Toast.makeText(this, "Time Skip Complete!", Toast.LENGTH_SHORT).show()
                        }
                }
            }
    }

    private fun branchCurrentSession(newTitle: String, currentUserId: String) {
        val db = FirebaseFirestore.getInstance()
        val newSessionId = java.util.UUID.randomUUID().toString()
        val oldSessionId = sessionId ?: return

        // 1. Filter the user map to ONLY include the current user
        val currentUserData = sessionProfile.userMap[currentUserId]
        val newUserMap = if (currentUserData != null) {
            mapOf(currentUserId to currentUserData)
        } else {
            emptyMap()
        }

        // 2. Build the cloned profile with the exact overrides you requested
        val clonedProfile = sessionProfile.copy(
            sessionId = newSessionId,
            title = newTitle,
            userList = listOf(currentUserId),
            userMap = newUserMap,
            startedAt = com.google.firebase.Timestamp.now()
        )

        // Show a loading dialog because copying subcollections takes a second
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Branching Timeline...")
            .setMessage("Copying memories and chat history. Please wait...")
            .setCancelable(false)
            .show()

        // 3. Save the new main document
        db.collection("sessions").document(newSessionId).set(clonedProfile)
            .addOnSuccessListener {

                // 4. Copy the Chat History Subcollection
                // NOTE: If your messages subcollection is named something other than "messages", change it here!
                db.collection("sessions").document(oldSessionId).collection("messages").get()
                    .addOnSuccessListener { messagesSnap ->
                        if (!messagesSnap.isEmpty) {
                            val batch = db.batch()
                            val newMessagesRef = db.collection("sessions").document(newSessionId).collection("messages")

                            // Queue up every single message to be copied into the new database location
                            for (msgDoc in messagesSnap.documents) {
                                // Using the same msgDoc.id keeps the timestamps and sorting perfectly intact
                                batch.set(newMessagesRef.document(msgDoc.id), msgDoc.data!!)
                            }

                            // Commit the massive copy operation
                            batch.commit().addOnSuccessListener {
                                progressDialog.dismiss()
                                Toast.makeText(this, "Timeline branched successfully!", Toast.LENGTH_SHORT).show()

                            //    // Automatically jump the user into their brand new timeline
                            //    val intent = Intent(this, MainActivity::class.java)
                            //    intent.putExtra("SESSION_ID", newSessionId)
                            //    startActivity(intent)
                            //    finish() // Close the current chat
                            }
                        } else {
                            // If the chat was completely empty, just jump right in
                            progressDialog.dismiss()
                            Toast.makeText(this, "Timeline branched successfully!", Toast.LENGTH_SHORT).show()

                        //    val intent = Intent(this, MainActivity::class.java)
                        //    intent.putExtra("SESSION_ID", newSessionId)
                        //    startActivity(intent)
                        //    finish()
                        }
                    }
                    .addOnFailureListener { e ->
                        progressDialog.dismiss()
                        Toast.makeText(this, "Failed to copy messages: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(this, "Failed to create new session: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun checkServerStatusBeforeSending(onOnline: () -> Unit) {
        val db = FirebaseFirestore.getInstance()

        db.collection("admin").document("server_status").get()
            .addOnSuccessListener { doc ->
                // Default to true if the document doesn't exist yet
                val isOnline = doc.getBoolean("isOnline") ?: true
                val offlineMsg = doc.getString("offlineMessage")
                    ?: "The AI servers are currently resting! You can still manage characters and read past chats, but new messages are paused."

                if (!isOnline) {
                    // Soft lock: Just show the message, don't close the app!
                    AlertDialog.Builder(this)
                        .setTitle("Beta Offline")
                        .setMessage(offlineMsg)
                        .setPositiveButton("Got it", null)
                        .show()
                } else {
                    // Server is awake, proceed!
                    onOnline()
                }
            }
            .addOnFailureListener {
                // Failsafe: If the database read blips, let them try to send anyway
                onOnline()
            }
    }

    fun updateRPGToggleVisibility() {
        val isRPG = sessionProfile.modeSettings.containsKey("rpg")
        val isVN = sessionProfile.modeSettings.containsKey("vn")
        if (isRPG) {
            rollHistoryButton.visibility = View.VISIBLE
            rollButton.visibility = View.VISIBLE
        } else {
            if (isVN){
                rollHistoryButton.visibility = View.VISIBLE
                rollButton.visibility = View.GONE
                rollHistoryButton.visibility = View.GONE
            }else{
                rollHistoryButton.visibility = View.GONE
                rollButton.visibility = View.GONE
            }
        }

        Log.d("rpg", "is this session an rpg? $isRPG")
    }

    private fun showTitleChangeDialog() {
        // 1. Inflate your custom XML
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_title, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.newName)

        // 2. Pre-fill with the current session title (or fallback to base title)
        val currentTitle = sessionProfile?.sessionTitle?.takeIf { it.isNotBlank() }
            ?: sessionProfile?.title
            ?: ""

        nameInput.setText(currentTitle)

        // Put the cursor at the end of the text
        nameInput.setSelection(currentTitle.length)

        // 3. Build and show the Dialog
        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val updatedTitle = nameInput.text.toString().trim()

                if (updatedTitle.isNotBlank() && updatedTitle != currentTitle) {
                    saveNewSessionTitle(updatedTitle)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveNewSessionTitle(newTitle: String) {
        val sessionId = sessionProfile?.sessionId ?: return
        val db = FirebaseFirestore.getInstance()

        // Update Firestore
        db.collection("sessions").document(sessionId)
            .update("sessionTitle", newTitle)
            .addOnSuccessListener {
                // Update local state
                sessionProfile = sessionProfile.copy(sessionTitle = newTitle)

                Toast.makeText(this, "Session renamed to: $newTitle", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to rename session: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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

    private fun showLinkedCharactersDialog(slot: SlotProfile) {
        val dialogView = android.widget.ScrollView(this).apply {
            setPadding(48, 32, 48, 32)
        }
        val listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        dialogView.addView(listLayout)

        val activeLinks = slot.linkedTo.filter { it.type.lowercase() != "time skip" }

        if (activeLinks.isEmpty()) {
            listLayout.addView(TextView(this).apply {
                text = "No active links available."
                setTextColor(android.graphics.Color.GRAY)
            })
        }

        activeLinks.forEach { link ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 32) }
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val avatar = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(120, 120).apply { setMargins(0, 0, 24, 0) }
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            if (link.targetAvatar.isNotBlank()) {
                Glide.with(this).load(link.targetAvatar).circleCrop().into(avatar)
            } else {
                avatar.setImageResource(R.drawable.placeholder_avatar)
            }

            val textTv = TextView(this).apply {
                val triggerText = link.trigger.ifBlank { "Passive" }
                text = "${link.targetName} - $triggerText"
                textSize = 15f
                setTextColor(android.graphics.Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(0, 0, 16, 0)
            }

            row.addView(avatar)
            row.addView(textTv)

            // --- THE NEW UNSUMMON LOGIC ---
            val originalActionType = link.type.lowercase()
            var displayActionType = originalActionType

            // Check if they are already here!
            if (originalActionType == "summon") {
                val isPresent = sessionProfile?.slotRoster?.any {
                    it.baseCharacterId == link.targetId || it.slotId == link.targetId
                } == true

                if (isPresent) {
                    displayActionType = "unsummon"
                }
            }

            val isActionable = displayActionType in listOf("switch", "possession", "summon", "unsummon", "fusion", "unfuse")

            if (isActionable) {
                val actionBtn = Button(this).apply {
                    text = displayActionType.uppercase()
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                row.addView(actionBtn)
            } else {
                val typeTv = TextView(this).apply {
                    text = displayActionType.uppercase()
                    textSize = 12f
                    setTypeface(null, android.graphics.Typeface.ITALIC)
                    setTextColor(android.graphics.Color.GRAY)
                    setPadding(16, 0, 0, 0)
                }
                row.addView(typeTv)
            }

            listLayout.addView(row)
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("${slot.name}'s Links")
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .create()

        // Wire up the click listeners and pass the Action Type directly from the button!
        for (i in 0 until listLayout.childCount) {
            val row = listLayout.getChildAt(i) as? LinearLayout
            val btn = row?.getChildAt(2) as? Button
            if (btn != null) {
                val activeLinksIndex = if (activeLinks.isEmpty()) -1 else i
                if (activeLinksIndex >= 0) {
                    val link = activeLinks[activeLinksIndex]
                    btn.setOnClickListener {
                        // Grab whatever the button currently says (SUMMON vs UNSUMMON)
                        val actionToExecute = btn.text.toString().lowercase()
                        executeLinkAction(link, slot, dialog, actionToExecute)
                    }
                }
            }
        }

        dialog.show()
    }

    private fun executeLinkAction(link: CharacterLink, slotToUpdate: SlotProfile, linkDialog: AlertDialog, actionType: String) {

        if (actionType == "switch" || actionType == "possession") {
            // Lock the UI so they don't spam click it
            val progressDialog = androidx.appcompat.app.AlertDialog.Builder(this)
                .setMessage("Executing transformation...")
                .setCancelable(false)
                .show()

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val db = FirebaseFirestore.getInstance()
                    val sessionIdStr = sessionProfile?.sessionId ?: return@launch

                    // 1. Fetch Target Character
                    val targetDoc = db.collection("characters").document(link.targetId).get().await()
                    if (!targetDoc.exists()) {
                        withContext(Dispatchers.Main) {
                            progressDialog.dismiss()
                            Toast.makeText(this@MainActivity, "Linked character no longer exists in the database.", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }
                    val charData = targetDoc.toObject(CharacterProfile::class.java) ?: return@launch

                    // 2. Fetch Wardrobe (Removing vectors to save RAM)
                    val wardrobeSnap = db.collection("characters").document(link.targetId).collection("wardrobes").get().await()
                    val cleanOutfits = wardrobeSnap.documents.mapNotNull { d ->
                        val outfit = d.toObject(Outfit::class.java)
                        outfit?.copy(poseSlots = outfit.poseSlots.map { it.copy(vector = null) }.toMutableList())
                    }

                    // 3. Batch Setup
                    val batch = db.batch()

                    // Copy clothes to the session, tied to the current Slot ID!
                    val sessionWardrobeRef = db.collection("sessions").document(sessionIdStr).collection("wardrobes")
                    cleanOutfits.forEach { outfit ->
                        val safeOutfitName = outfit.name.replace("\\s+".toRegex(), "_").ifBlank { "default" }
                        val docName = "${slotToUpdate.slotId}_$safeOutfitName"
                        batch.set(sessionWardrobeRef.document(docName), outfit)
                    }

                    // 4. Update Roster (Hot-Swap Identity)
                    var oldName = ""
                    var finalArea = "Unknown"
                    var finalLocation = "Unknown"

                    val updatedRoster = sessionProfile?.slotRoster?.map { slot ->
                        if (slot.slotId == slotToUpdate.slotId) {
                            oldName = slot.name

                            slot.copy(
                                // Overwrite Identity & Brain
                                baseCharacterId = charData.id,
                                name = charData.name,
                                summary = charData.summary ?: "",
                                personality = charData.personality ?: "",
                                privateDescription = charData.privateDescription ?: "",
                                backstory = charData.backstory ?: "",
                                abilities = charData.abilities ?: "",
                                exampleDialogue = charData.exampleDialogue,

                                // Overwrite Physical
                                gender = charData.gender ?: "",
                                height = charData.height ?: "",
                                weight = charData.weight ?: "",
                                eyeColor = charData.eyeColor ?: "",
                                hairColor = charData.hairColor ?: "",
                                physicalDescription = charData.physicalDescription ?: "",

                                // Overwrite Meta
                                avatarUri = charData.avatarUri ?: "",
                                bubbleColor = charData.bubbleColor ?: slot.bubbleColor,
                                textColor = charData.textColor ?: slot.textColor,
                                sfwOnly = charData.sfwOnly,
                                lorebookIds = charData.lorebookIds ?: emptyList(),

                                // Give the new form the ability to switch back!
                                linkedTo = charData.linkedToMap.values.flatten(),

                                // Overwrite Wardrobe
                                outfits = cleanOutfits,
                                currentOutfit = charData.currentOutfit ?: ""
                            )
                        } else {
                            slot
                        }
                    } ?: return@launch

                    // Save Roster
                    val sessionRef = db.collection("sessions").document(sessionIdStr)
                    batch.update(sessionRef, "slotRoster", updatedRoster)

                    // 5. Generate System Message
                    val sysMsgId = "sys-${UUID.randomUUID()}"
                    val sysMsg = mapOf(
                        "id" to sysMsgId,
                        "senderId" to "system",
                        "role" to "system",
                        "name" to "System",
                        "text" to "[TRANSFORMATION] A shift has occurred! $oldName is now ${charData.name}.",
                        "timestamp" to FieldValue.serverTimestamp(),
                        "visibility" to true,
                        "type" to "event", // or messageType depending on your schema
                        "bubbleColor" to "#4c1d95",
                        "textColor" to "#ffffff"
                    )

                    batch.set(sessionRef.collection("messages").document(sysMsgId), sysMsg)

                    // Fan out message to room
                    val peopleInRoom = updatedRoster.filter {
                        it.lastActiveArea == finalArea && it.lastActiveLocation == finalLocation
                    }
                    val slotsToUpdate = peopleInRoom.map { it.slotId } + "narrator"

                    slotsToUpdate.forEach { sId ->
                        batch.set(sessionRef.collection("slotPersonalHistory").document(sId).collection("messages").document(sysMsgId), sysMsg)
                    }

                    // 6. Commit everything!
                    batch.commit().await()

                    // 7. Close Modal & Cleanup on Main Thread
                    withContext(Dispatchers.Main) {
                        sessionProfile?.slotRoster = updatedRoster
                        chatAdapter?.updateSlotProfiles(updatedRoster) // Tell the chat UI the name/avatar changed

                        // THE FIX: If the character sheet is open, refresh the tabs and auto-open the new form!
                        val sheetBox = findViewById<View>(R.id.characterSheetBox)
                        if (sheetBox != null && sheetBox.visibility == View.VISIBLE) {
                            refreshCharacterTabs(slotToUpdate.slotId)
                        }

                        progressDialog.dismiss()
                        linkDialog.dismiss()
                        Toast.makeText(this@MainActivity, "Transformation complete!", Toast.LENGTH_SHORT).show()
                    }

                } catch (e: Exception) {
                    Log.e("LinkEngine", "Failed to execute switch:", e)
                    withContext(Dispatchers.Main) {
                        progressDialog.dismiss()
                        Toast.makeText(this@MainActivity, "Failed to execute transformation.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        else if (actionType == "summon") {
            val progressDialog = androidx.appcompat.app.AlertDialog.Builder(this)
                .setMessage("Summoning character...")
                .setCancelable(false)
                .show()

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val db = FirebaseFirestore.getInstance()
                    val sessionIdStr = sessionProfile?.sessionId ?: return@launch
                    val currentRoster = sessionProfile?.slotRoster?.toList()
                        ?: return@launch // <-- toList() for safety

                    if (currentRoster.size >= 50) {
                        withContext(Dispatchers.Main) {
                            progressDialog.dismiss()
                            Toast.makeText(
                                this@MainActivity,
                                "Roster is full (Max 50). Cannot summon.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@launch
                    }

                    val targetArea = slotToUpdate.lastActiveArea
                    val targetLocation = slotToUpdate.lastActiveLocation
                    val batch = db.batch()
                    val sessionRef = db.collection("sessions").document(sessionIdStr)

                    var summonedName = link.targetName
                    val newRoster: List<SlotProfile> // We will store the BRAND NEW list here

                    val existingIndex =
                        currentRoster.indexOfFirst { it.baseCharacterId == link.targetId || it.slotId == link.targetId }

                    if (existingIndex >= 0) {
                        // A. THEY EXIST: Move them to the room using a NEW list
                        val existingSlot = currentRoster[existingIndex]
                        summonedName = existingSlot.name

                        // .mapIndexed generates a completely new list in memory
                        newRoster = currentRoster.mapIndexed { index, slot ->
                            if (index == existingIndex) {
                                slot.copy(
                                    lastActiveArea = targetArea,
                                    lastActiveLocation = targetLocation,
                                    activityStatus = true
                                )
                            } else {
                                slot
                            }
                        }
                        batch.update(sessionRef, "slotRoster", newRoster)
                    } else {
                        // B. BRAND NEW SUMMON: Fetch and build them
                        val targetDoc =
                            db.collection("characters").document(link.targetId).get().await()
                        if (!targetDoc.exists()) {
                            withContext(Dispatchers.Main) {
                                progressDialog.dismiss()
                                Toast.makeText(
                                    this@MainActivity,
                                    "Summon target no longer exists.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            return@launch
                        }
                        val charData =
                            targetDoc.toObject(CharacterProfile::class.java) ?: return@launch
                        summonedName = charData.name

                        val wardrobeSnap = db.collection("characters").document(link.targetId)
                            .collection("wardrobes").get().await()
                        val cleanOutfits = wardrobeSnap.documents.mapNotNull { d ->
                            val outfit = d.toObject(Outfit::class.java)
                            outfit?.copy(poseSlots = outfit.poseSlots.map { it.copy(vector = null) }
                                .toMutableList())
                        }

                        val sessionWardrobeRef = sessionRef.collection("wardrobes")
                        cleanOutfits.forEach { outfit ->
                            val safeOutfitName =
                                outfit.name.replace("\\s+".toRegex(), "_").ifBlank { "default" }
                            val docName = "${charData.id}_$safeOutfitName"
                            batch.set(sessionWardrobeRef.document(docName), outfit)
                        }

                        val newSlot = SlotProfile(
                            slotId = charData.id,
                            baseCharacterId = charData.id,
                            name = charData.name,
                            summary = charData.summary ?: "",
                            personality = charData.personality ?: "",
                            privateDescription = charData.privateDescription ?: "",
                            backstory = charData.backstory ?: "",
                            abilities = charData.abilities ?: "",
                            exampleDialogue = charData.exampleDialogue,
                            gender = charData.gender ?: "",
                            height = charData.height ?: "",
                            weight = charData.weight ?: "",
                            eyeColor = charData.eyeColor ?: "",
                            hairColor = charData.hairColor ?: "",
                            physicalDescription = charData.physicalDescription ?: "",
                            avatarUri = charData.avatarUri ?: "",
                            bubbleColor = charData.bubbleColor ?: "#CCCCCC",
                            textColor = charData.textColor ?: "#000000",
                            sfwOnly = charData.sfwOnly,
                            lorebookIds = charData.lorebookIds ?: emptyList(),
                            linkedTo = charData.linkedToMap.values.flatten(),
                            outfits = cleanOutfits,
                            currentOutfit = charData.currentOutfit ?: "",
                            lastActiveArea = targetArea,
                            lastActiveLocation = targetLocation,
                            activityStatus = true,
                            lastSynced = com.google.firebase.Timestamp.now()
                        )

                        // The "+" operator instantly generates a brand new list!
                        newRoster = currentRoster + newSlot
                        batch.update(sessionRef, "slotRoster", newRoster)
                    }

                    // 3. Generate the System Message
                    val sysMsgId = "sys-${UUID.randomUUID()}"
                    val sysMsg = mapOf(
                        "id" to sysMsgId,
                        "senderId" to "system",
                        "role" to "system",
                        "name" to "System",
                        "text" to "[SUMMON] A rift opens! ${slotToUpdate.name} has summoned $summonedName to the area.",
                        "area" to targetArea,
                        "location" to targetLocation,
                        "timestamp" to FieldValue.serverTimestamp(),
                        "visibility" to true,
                        "type" to "event",
                        "bubbleColor" to "#10b981",
                        "textColor" to "#ffffff"
                    )

                    batch.set(sessionRef.collection("messages").document(sysMsgId), sysMsg)

                    // 4. Fan out message to everyone in the room (USING THE NEW ROSTER!)
                    val peopleInRoom = newRoster.filter {
                        it.lastActiveArea == targetArea && it.lastActiveLocation == targetLocation
                    }
                    val slotsToUpdate = peopleInRoom.map { it.slotId } + "narrator"

                    slotsToUpdate.forEach { sId ->
                        batch.set(
                            sessionRef.collection("slotPersonalHistory").document(sId)
                                .collection("messages").document(sysMsgId), sysMsg
                        )
                    }

                    batch.commit().await()

                    // 5. UI Cleanup
                    withContext(Dispatchers.Main) {
                        sessionProfile =
                            sessionProfile.copy(slotRoster = newRoster) // Hard-update local profile
                        chatAdapter?.updateSlotProfiles(newRoster)

                        // Force the tabs to refresh so the summoned character's tab instantly appears!
                        val sheetBox = findViewById<View>(R.id.characterSheetBox)
                        if (sheetBox != null && sheetBox.visibility == View.VISIBLE) {
                            refreshCharacterTabs(slotToUpdate.slotId)
                        }

                        progressDialog.dismiss()
                        linkDialog.dismiss()
                        Toast.makeText(
                            this@MainActivity,
                            "$summonedName summoned successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                } catch (e: Exception) {
                    Log.e("LinkEngine", "Failed to execute summon:", e)
                    withContext(Dispatchers.Main) {
                        progressDialog.dismiss()
                        Toast.makeText(
                            this@MainActivity,
                            "Failed to execute summon.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
        else if (actionType == "unsummon") {
            val progressDialog = androidx.appcompat.app.AlertDialog.Builder(this)
                .setMessage("Dismissing character...")
                .setCancelable(false)
                .show()

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val db = FirebaseFirestore.getInstance()
                    val sessionIdStr = sessionProfile?.sessionId ?: return@launch
                    val currentRoster = sessionProfile?.slotRoster?.toMutableList() ?: return@launch

                    val existingIndex = currentRoster.indexOfFirst {
                        it.baseCharacterId == link.targetId || it.slotId == link.targetId
                    }

                    if (existingIndex == -1) {
                        withContext(Dispatchers.Main) {
                            progressDialog.dismiss()
                            Toast.makeText(
                                this@MainActivity,
                                "Character is not currently summoned.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@launch
                    }

                    val dismissedName = currentRoster[existingIndex].name
                    val targetArea = slotToUpdate.lastActiveArea
                    val targetLocation = slotToUpdate.lastActiveLocation

                    // 1. Create a BRAND NEW list without the character so Firebase sees the difference
                    val newRoster =
                        currentRoster.filterIndexed { index, _ -> index != existingIndex }

                    // 2. Update Firestore Roster
                    val batch = db.batch()
                    val sessionRef = db.collection("sessions").document(sessionIdStr)
                    batch.update(sessionRef, "slotRoster", newRoster)

                    // 3. Generate System Message
                    val sysMsgId = "sys-${UUID.randomUUID()}"
                    val sysMsg = mapOf(
                        "id" to sysMsgId,
                        "senderId" to "system",
                        "role" to "system",
                        "name" to "System",
                        "text" to "[UNSUMMON] ${slotToUpdate.name} has dismissed $dismissedName. They fade away.",
                        "area" to targetArea,
                        "location" to targetLocation,
                        "timestamp" to FieldValue.serverTimestamp(),
                        "visibility" to true,
                        "type" to "event",
                        "bubbleColor" to "#ef4444", // A crimson red to show dismissal
                        "textColor" to "#ffffff"
                    )

                    batch.set(sessionRef.collection("messages").document(sysMsgId), sysMsg)

                    // 4. Fan out message to everyone in the room
                    val peopleInRoom = currentRoster.filter {
                        it.lastActiveArea == targetArea && it.lastActiveLocation == targetLocation
                    }
                    val slotsToUpdate = peopleInRoom.map { it.slotId } + "narrator"

                    slotsToUpdate.forEach { sId ->
                        batch.set(
                            sessionRef.collection("slotPersonalHistory").document(sId)
                                .collection("messages").document(sysMsgId), sysMsg
                        )
                    }

                    // 5. Commit everything!
                    batch.commit().await()

                    // 6. UI Cleanup
                    withContext(Dispatchers.Main) {
                        sessionProfile = sessionProfile.copy(slotRoster = newRoster)
                        chatAdapter?.updateSlotProfiles(newRoster)

                        val sheetBox = findViewById<View>(R.id.characterSheetBox)
                        if (sheetBox != null && sheetBox.visibility == View.VISIBLE) {
                            refreshCharacterTabs(slotToUpdate.slotId)
                        }

                        progressDialog.dismiss()
                        linkDialog.dismiss()
                        Toast.makeText(
                            this@MainActivity,
                            "$dismissedName unsummoned.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                } catch (e: Exception) {
                    Log.e("LinkEngine", "Failed to execute unsummon:", e)
                    withContext(Dispatchers.Main) {
                        progressDialog.dismiss()
                        Toast.makeText(this@MainActivity, "Failed to unsummon.", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
        }
        else if (actionType == "fusion") {
            val progressDialog = androidx.appcompat.app.AlertDialog.Builder(this)
                .setMessage("Initiating Fusion...")
                .setCancelable(false)
                .show()

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val db = FirebaseFirestore.getInstance()
                    val sessionIdStr = sessionProfile?.sessionId ?: return@launch
                    val currentRoster = sessionProfile?.slotRoster?.toList() ?: return@launch

                    val targetArea = slotToUpdate.lastActiveArea
                    val targetLocation = slotToUpdate.lastActiveLocation

                    // 1. Find ALL active components in the roster who share this fusion target
                    val componentSlots = currentRoster.filter { slot ->
                        slot.activityStatus && slot.linkedTo.any { it.type.lowercase() == "fusion" && it.targetId == link.targetId }
                    }

                    if (componentSlots.isEmpty()) {
                        withContext(Dispatchers.Main) { progressDialog.dismiss() }
                        return@launch
                    }

                    val componentNames = componentSlots.joinToString(", ") { it.name }
                    val componentIds = componentSlots.map { it.slotId }.toSet()

                    // 2. Build the dynamic "Unfuse" links to inject into the Fused character
                    val unfuseLinks = componentSlots.map { comp ->
                        CharacterLink(
                            targetId = comp.slotId, // Use slotId so we know EXACTLY who to wake up later
                            targetName = comp.name,
                            targetAvatar = comp.avatarUri?: "",
                            type = "unfuse",
                            trigger = "Split back into components"
                        )
                    }

                    val batch = db.batch()
                    val sessionRef = db.collection("sessions").document(sessionIdStr)
                    var fusedName = link.targetName
                    val newRoster: List<SlotProfile>

                    val existingIndex =
                        currentRoster.indexOfFirst { it.baseCharacterId == link.targetId || it.slotId == link.targetId }

                    if (existingIndex >= 0) {
                        // A. FUSED CHARACTER ALREADY IN ROSTER (Re-fusion)
                        val existingSlot = currentRoster[existingIndex]
                        fusedName = existingSlot.name

                        // Combine existing non-unfuse links with the fresh unfuse links
                        val updatedLinks =
                            existingSlot.linkedTo.filter { it.type.lowercase() != "unfuse" } + unfuseLinks

                        newRoster = currentRoster.mapIndexed { index, slot ->
                            if (index == existingIndex) {
                                // Wake up the fused character
                                slot.copy(
                                    lastActiveArea = targetArea,
                                    lastActiveLocation = targetLocation,
                                    activityStatus = true,
                                    linkedTo = updatedLinks
                                )
                            } else if (slot.slotId in componentIds) {
                                // Put the components to sleep
                                slot.copy(
                                    activityStatus = false,
                                    lastActiveArea = targetArea,
                                    lastActiveLocation = targetLocation
                                )
                            } else {
                                slot
                            }
                        }
                        batch.update(sessionRef, "slotRoster", newRoster)

                    } else {
                        // B. BRAND NEW FUSION: Fetch and Build
                        if (currentRoster.size >= 50) {
                            withContext(Dispatchers.Main) {
                                progressDialog.dismiss()
                                Toast.makeText(
                                    this@MainActivity,
                                    "Roster is full (Max 50). Cannot fuse.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            return@launch
                        }

                        val targetDoc =
                            db.collection("characters").document(link.targetId).get().await()
                        if (!targetDoc.exists()) {
                            withContext(Dispatchers.Main) {
                                progressDialog.dismiss()
                                Toast.makeText(
                                    this@MainActivity,
                                    "Fusion target no longer exists.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            return@launch
                        }
                        val charData =
                            targetDoc.toObject(CharacterProfile::class.java) ?: return@launch
                        fusedName = charData.name

                        // Fetch Wardrobe
                        val wardrobeSnap = db.collection("characters").document(link.targetId)
                            .collection("wardrobes").get().await()
                        val cleanOutfits = wardrobeSnap.documents.mapNotNull { d ->
                            val outfit = d.toObject(Outfit::class.java)
                            outfit?.copy(poseSlots = outfit.poseSlots.map { it.copy(vector = null) }
                                .toMutableList())
                        }

                        val sessionWardrobeRef = sessionRef.collection("wardrobes")
                        cleanOutfits.forEach { outfit ->
                            val safeOutfitName =
                                outfit.name.replace("\\s+".toRegex(), "_").ifBlank { "default" }
                            val docName = "${charData.id}_$safeOutfitName"
                            batch.set(sessionWardrobeRef.document(docName), outfit)
                        }

                        // Combine DB links with dynamic unfuse links
                        val combinedLinks = charData.linkedToMap.values.flatten() + unfuseLinks

                        val newSlot = SlotProfile(
                            slotId = charData.id,
                            baseCharacterId = charData.id,
                            name = charData.name,
                            summary = charData.summary ?: "",
                            personality = charData.personality ?: "",
                            privateDescription = charData.privateDescription ?: "",
                            backstory = charData.backstory ?: "",
                            abilities = charData.abilities ?: "",
                            exampleDialogue = charData.exampleDialogue,
                            gender = charData.gender ?: "",
                            height = charData.height ?: "",
                            weight = charData.weight ?: "",
                            eyeColor = charData.eyeColor ?: "",
                            hairColor = charData.hairColor ?: "",
                            physicalDescription = charData.physicalDescription ?: "",
                            avatarUri = charData.avatarUri ?: "",
                            bubbleColor = charData.bubbleColor ?: "#CCCCCC",
                            textColor = charData.textColor ?: "#000000",
                            sfwOnly = charData.sfwOnly,
                            lorebookIds = charData.lorebookIds ?: emptyList(),
                            linkedTo = combinedLinks, // <-- Injecting the unfuse links here!
                            outfits = cleanOutfits,
                            currentOutfit = charData.currentOutfit ?: "",
                            lastActiveArea = targetArea,
                            lastActiveLocation = targetLocation,
                            activityStatus = true,
                            lastSynced = com.google.firebase.Timestamp.now()
                        )

                        // Put components to sleep, update location, then append the new Fused character!
                        val sleptRoster = currentRoster.map { slot ->
                            if (slot.slotId in componentIds) {
                                slot.copy(
                                    activityStatus = false,
                                    lastActiveArea = targetArea,
                                    lastActiveLocation = targetLocation
                                )
                            } else {
                                slot
                            }
                        }
                        newRoster = sleptRoster + newSlot
                        batch.update(sessionRef, "slotRoster", newRoster)
                    }

                    // 3. Generate the System Message
                    val sysMsgId = "sys-${UUID.randomUUID()}"
                    val sysMsg = mapOf(
                        "id" to sysMsgId,
                        "senderId" to "system",
                        "role" to "system",
                        "name" to "System",
                        "text" to "[FUSION] A blinding light engulfs the area! $componentNames have fused together to become $fusedName!",
                        "area" to targetArea,
                        "location" to targetLocation,
                        "timestamp" to FieldValue.serverTimestamp(),
                        "visibility" to true,
                        "type" to "event",
                        "bubbleColor" to "#fbbf24", // Brilliant gold for fusion!
                        "textColor" to "#000000"
                    )

                    batch.set(sessionRef.collection("messages").document(sysMsgId), sysMsg)

                    // 4. Fan out message to everyone in the room (USING THE NEW ROSTER!)
                    val peopleInRoom = newRoster.filter {
                        it.lastActiveArea == targetArea && it.lastActiveLocation == targetLocation
                    }
                    val slotsToUpdate = peopleInRoom.map { it.slotId } + "narrator"

                    slotsToUpdate.forEach { sId ->
                        batch.set(
                            sessionRef.collection("slotPersonalHistory").document(sId)
                                .collection("messages").document(sysMsgId), sysMsg
                        )
                    }

                    batch.commit().await()

                    // 5. UI Cleanup
                    withContext(Dispatchers.Main) {
                        sessionProfile = sessionProfile.copy(slotRoster = newRoster)
                        chatAdapter?.updateSlotProfiles(newRoster)

                        val sheetBox = findViewById<View>(R.id.characterSheetBox)
                        if (sheetBox != null && sheetBox.visibility == View.VISIBLE) {
                            // We pass link.targetId so it automatically opens the Fused character's sheet!
                            refreshCharacterTabs(link.targetId)
                        }

                        progressDialog.dismiss()
                        linkDialog.dismiss()
                        Toast.makeText(this@MainActivity, "Fusion complete!", Toast.LENGTH_SHORT)
                            .show()
                    }

                } catch (e: Exception) {
                    Log.e("LinkEngine", "Failed to execute fusion:", e)
                    withContext(Dispatchers.Main) {
                        progressDialog.dismiss()
                        Toast.makeText(
                            this@MainActivity,
                            "Failed to execute fusion.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
        else if (actionType == "unfuse") {
            val progressDialog = androidx.appcompat.app.AlertDialog.Builder(this)
                .setMessage("Reversing fusion...")
                .setCancelable(false)
                .show()

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val db = FirebaseFirestore.getInstance()
                    val sessionIdStr = sessionProfile?.sessionId ?: return@launch
                    val currentRoster = sessionProfile?.slotRoster?.toList() ?: return@launch

                    val fusedIndex = currentRoster.indexOfFirst { it.slotId == slotToUpdate.slotId }
                    if (fusedIndex == -1) {
                        withContext(Dispatchers.Main) { progressDialog.dismiss() }
                        return@launch
                    }

                    // 1. Gather all component IDs that need to be woken up
                    // (We grab ALL unfuse links on this character so they all pop out at once)
                    val componentIds = slotToUpdate.linkedTo
                        .filter { it.type.lowercase() == "unfuse" }
                        .map { it.targetId }
                        .toSet()

                    if (componentIds.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            progressDialog.dismiss()
                            Toast.makeText(
                                this@MainActivity,
                                "No fusion components found.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@launch
                    }

                    val targetArea = slotToUpdate.lastActiveArea
                    val targetLocation = slotToUpdate.lastActiveLocation

                    // 2. Build the NEW Roster (Avoid Reference Trap!)
                    // Remove the fused character, and wake up the components in the current location
                    val newRoster = currentRoster
                        .filterIndexed { index, _ -> index != fusedIndex } // Delete Fused Slot
                        .map { slot ->
                            if (slot.slotId in componentIds) {
                                // Wake them up exactly where the fusion ended!
                                slot.copy(
                                    activityStatus = true,
                                    lastActiveArea = targetArea,
                                    lastActiveLocation = targetLocation
                                )
                            } else {
                                slot
                            }
                        }

                    // Get the names of everyone who just woke up for the system message
                    val awokenNames = newRoster.filter { it.slotId in componentIds }
                        .joinToString(", ") { it.name }

                    // 3. Update Firestore
                    val batch = db.batch()
                    val sessionRef = db.collection("sessions").document(sessionIdStr)
                    batch.update(sessionRef, "slotRoster", newRoster)

                    // 4. Generate the System Message
                    val sysMsgId = "sys-${UUID.randomUUID()}"
                    val sysMsg = mapOf(
                        "id" to sysMsgId,
                        "senderId" to "system",
                        "role" to "system",
                        "name" to "System",
                        "text" to "[UNFUSE] The fusion breaks apart! ${slotToUpdate.name} splits back into $awokenNames.",
                        "area" to targetArea,
                        "location" to targetLocation,
                        "timestamp" to FieldValue.serverTimestamp(),
                        "visibility" to true,
                        "type" to "event",
                        "bubbleColor" to "#06b6d4", // Cool cyan color for reversing the fusion
                        "textColor" to "#ffffff"
                    )

                    batch.set(sessionRef.collection("messages").document(sysMsgId), sysMsg)

                    // 5. Fan out message to everyone in the room
                    val peopleInRoom = newRoster.filter {
                        it.lastActiveArea == targetArea && it.lastActiveLocation == targetLocation
                    }
                    val slotsToUpdate = peopleInRoom.map { it.slotId } + "narrator"

                    slotsToUpdate.forEach { sId ->
                        batch.set(
                            sessionRef.collection("slotPersonalHistory").document(sId)
                                .collection("messages").document(sysMsgId), sysMsg
                        )
                    }

                    batch.commit().await()

                    // 6. UI Cleanup
                    withContext(Dispatchers.Main) {
                        sessionProfile = sessionProfile.copy(slotRoster = newRoster)
                        chatAdapter?.updateSlotProfiles(newRoster)

                        val sheetBox = findViewById<View>(R.id.characterSheetBox)
                        if (sheetBox != null && sheetBox.visibility == View.VISIBLE) {
                            // Automatically open the sheet of the first component that popped out
                            refreshCharacterTabs(componentIds.firstOrNull())
                        }

                        progressDialog.dismiss()
                        linkDialog.dismiss()
                        Toast.makeText(
                            this@MainActivity,
                            "Separated into $awokenNames!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                } catch (e: Exception) {
                    Log.e("LinkEngine", "Failed to execute unfuse:", e)
                    withContext(Dispatchers.Main) {
                        progressDialog.dismiss()
                        Toast.makeText(
                            this@MainActivity,
                            "Failed to execute unfuse.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
        else {
            Toast.makeText(this, "The ${actionType.uppercase()} mechanic is coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshCharacterTabs(slotIdToOpen: String? = null) {
        val tabBar = findViewById<LinearLayout>(R.id.characterTabBar)
        tabBar.removeAllViews()

        // inline: is murder mode enabled?
        val murderEnabled = ((sessionProfile?.modeSettings?.get("murder") as? String)?.let {
            try { Gson().fromJson(it, ModeSettings.MurderSettings::class.java).enabled } catch (_: Exception) { false }
        }) == true

        // inline: your active slot id
        val myActiveSlotId = sessionProfile?.userMap?.get(FirebaseAuth.getInstance().currentUser?.uid ?: "")?.activeSlotId

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

            // Automatically open the sheet if this is the one we just transformed!
            if (slotProfile.slotId == slotIdToOpen && allowed) {
                showCharacterSheet(slotProfile)
            }
        }
    }

    private fun showCharacterSheet(slot: SlotProfile) {
        val content = findViewById<LinearLayout>(R.id.characterSheetContent)
        content.removeAllViews()
        val sheet = layoutInflater.inflate(R.layout.item_character_sheet_fantasy, content, false)

        // ----- mode flags -----
        val godMode = sessionProfile?.enabledModes?.contains("god_mode") == true
        val isGodModeHost = isHost && godMode // <-- THE MASTER KEY
        val rpgEnabled = (sessionProfile?.modeSettings?.get("rpg") as? String)?.isNotBlank() == true
        val murderEnabled = ((sessionProfile?.modeSettings?.get("murder") as? String)?.let {
            try { Gson().fromJson(it, ModeSettings.MurderSettings::class.java).enabled } catch (_: Exception) { false }
        }) == true
        val vnEnabled = (sessionProfile?.modeSettings?.get("vn") as? String)?.isNotBlank() == true
        val isSfwOnly = sessionProfile?.sfwOnly == true

        // ----- UNIVERSAL SAVE HELPER -----
        // This cleanly handles updating the roster and Firestore for ANY field change
        fun saveSlotField(copyAction: (SlotProfile) -> SlotProfile) {
            val updatedSlot = copyAction(slot)
            val updatedRoster = sessionProfile?.slotRoster?.map {
                if (it.slotId == slot.slotId) updatedSlot else it
            }
            if (updatedRoster != null) {
                sessionProfile?.slotRoster = updatedRoster
                FirebaseFirestore.getInstance().collection("sessions").document(sessionId)
                    .update("slotRoster", updatedRoster)
                    .addOnSuccessListener {
                        Toast.makeText(this@MainActivity, "${slot.name} updated!", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        // ----- required views -----
        fun <T: View> req(id: Int): T =
            sheet.findViewById<T>(id) ?: error("Missing view id in item_character_sheet_fantasy: ${resources.getResourceEntryName(id)}")

        val root = req<androidx.constraintlayout.widget.ConstraintLayout>(R.id.sheetRootColumn)
        val modelSpinner = sheet.findViewById<Spinner>(R.id.modelSpinner)
        val aiSettingsSection = sheet.findViewById<View>(R.id.aiSettingsSection)

        // 1. Define your models. Position 0 is ALWAYS the "Clear/Null" option.
        val spinnerDisplayNames = mutableListOf("Default (Session Model)")
        spinnerDisplayNames.addAll(availableModels.keys)
        val spinnerModelIds = mutableListOf<String?>(null)
        spinnerModelIds.addAll(availableModels.values)

        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, spinnerDisplayNames)
        modelSpinner.adapter = spinnerAdapter
        val currentModelIndex = spinnerModelIds.indexOf(slot.modelId).takeIf { it >= 0 } ?: 0
        modelSpinner.setSelection(currentModelIndex)

        modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedModelId = spinnerModelIds[position]
                if (slot.modelId != selectedModelId) {
                    saveSlotField { it.copy(modelId = selectedModelId) }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // ----- AI PARAMETER BOXES -----
        val tempEdit = sheet.findViewById<EditText>(R.id.tempEditBox)
        val topKEdit = sheet.findViewById<EditText>(R.id.topKEditBox)
        val topPEdit = sheet.findViewById<EditText>(R.id.topPEditBox)

        tempEdit.setText(slot.temperature?.toString() ?: "")
        topKEdit.setText(slot.topK?.toString() ?: "")
        topPEdit.setText(slot.topP?.toString() ?: "")

        fun saveAiParams() {
            val newTemp = tempEdit.text.toString().toFloatOrNull()
            val newTopK = topKEdit.text.toString().toIntOrNull()
            val newTopP = topPEdit.text.toString().toFloatOrNull()

            if (slot.temperature != newTemp || slot.topK != newTopK || slot.topP != newTopP) {
                slot.temperature = newTemp
                slot.topK = newTopK
                slot.topP = newTopP
                saveSlotField { it.copy(temperature = newTemp, topK = newTopK, topP = newTopP) }
            }
        }

        val focusListener = View.OnFocusChangeListener { _, hasFocus -> if (!hasFocus) saveAiParams() }
        tempEdit.onFocusChangeListener = focusListener
        topKEdit.onFocusChangeListener = focusListener
        topPEdit.onFocusChangeListener = focusListener

        val editorActionListener = TextView.OnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT) {
                saveAiParams()
                tempEdit.clearFocus(); topKEdit.clearFocus(); topPEdit.clearFocus()
                val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(tempEdit.windowToken, 0)
                true
            } else false
        }
        tempEdit.setOnEditorActionListener(editorActionListener)
        topKEdit.setOnEditorActionListener(editorActionListener)
        topPEdit.setOnEditorActionListener(editorActionListener)

        // ----- COLOR SETTINGS BOXES -----
        val bubbleColorSpinner = sheet.findViewById<Spinner>(R.id.bubbleColorSpinner)
        val textColorSpinner = sheet.findViewById<Spinner>(R.id.textColorSpinner)

        val bubbleAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, bubblecolorOptions.map { it.first })
        val textAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, textcolorOptions.map { it.first })

        bubbleColorSpinner.adapter = bubbleAdapter
        textColorSpinner.adapter = textAdapter

        val currentBubbleIndex = bubblecolorOptions.indexOfFirst { it.second.equals(slot.bubbleColor, ignoreCase = true) }.takeIf { it >= 0 } ?: 0
        val currentTextIndex = textcolorOptions.indexOfFirst { it.second.equals(slot.textColor, ignoreCase = true) }.takeIf { it >= 0 } ?: 0

        bubbleColorSpinner.setSelection(currentBubbleIndex, false)
        textColorSpinner.setSelection(currentTextIndex, false)

        bubbleColorSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedHex = bubblecolorOptions[position].second
                if (slot.bubbleColor != selectedHex) {
                    slot.bubbleColor = selectedHex
                    saveSlotField { it.copy(bubbleColor = selectedHex) }
                    chatAdapter.updateSlotProfiles(sessionProfile?.slotRoster ?: emptyList())
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        textColorSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedHex = textcolorOptions[position].second
                if (slot.textColor != selectedHex) {
                    slot.textColor = selectedHex
                    saveSlotField { it.copy(textColor = selectedHex) }
                    chatAdapter.updateSlotProfiles(sessionProfile?.slotRoster ?: emptyList())
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        val header = req<View>(R.id.headerBlock)
        val loreSection = sheet.findViewById<View>(R.id.loreSection)
        val secretSection = sheet.findViewById<View>(R.id.secretSection)
        val rpgSection = sheet.findViewById<View>(R.id.rpgSection)
        val vnSection  = sheet.findViewById<View>(R.id.vnSection)
        val moreInfoBlock = sheet.findViewById<View>(R.id.moreInfoTV) ?: sheet.findViewById<View>(R.id.moreInfo)
        val moreInfoText  = sheet.findViewById<TextView>(R.id.moreInfo)

        // ----- 1. Header Visuals -----
        req<TextView>(R.id.nameView).text = slot.name
        slot.avatarUri?.takeIf { it.isNotBlank() }?.let { Glide.with(this).load(it).into(req(R.id.avatarView)) }

        val controllerNameView = req<TextView>(R.id.controllerName)

        if (slot.profileType == "player") {
            // Search the userMap for whoever has this slot claimed
            val controllingUser = sessionProfile?.userMap?.values?.find { it.activeSlotId == slot.slotId }

            if (controllingUser != null) {
                controllerNameView.text = "${controllingUser.username}"
                controllerNameView.setTextColor(android.graphics.Color.parseColor("#4CAF50")) // Optional: Give players a nice green color
            } else {
                // Fallback just in case the user disconnected or hasn't fully claimed it yet
                controllerNameView.text = "Player (Unassigned)"
                controllerNameView.setTextColor(android.graphics.Color.parseColor("#AAAAAA"))
            }
        } else {
            // If it's a bot, locked_bot, hidden_bot, etc.
            controllerNameView.text = "AI Controlled"
            controllerNameView.setTextColor(android.graphics.Color.parseColor("#1b7df5"))
        }

        val activeBtn = sheet.findViewById<Button>(R.id.activeBtn)

        fun updateActiveBtnVisuals(isActive: Boolean) {
            if (isActive) {
                activeBtn?.text = "Active"
                activeBtn?.alpha = 1.0f
            } else {
                activeBtn?.text = "Background"
                activeBtn?.alpha = 0.5f
            }
        }

        var currentActivityStatus = slot.activityStatus
        updateActiveBtnVisuals(currentActivityStatus)

        activeBtn?.setOnClickListener {
            val willBeActive = !currentActivityStatus

            if (willBeActive) {
                val activeInRoom = sessionProfile?.slotRoster?.count {
                    it.lastActiveArea == slot.lastActiveArea &&
                            it.lastActiveLocation == slot.lastActiveLocation &&
                            it.activityStatus &&
                            it.slotId != slot.slotId
                } ?: 0

                if (activeInRoom >= 10) {
                    Toast.makeText(this, "Cannot activate: This room already has 10 active characters.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            currentActivityStatus = willBeActive
            updateActiveBtnVisuals(currentActivityStatus)
            saveSlotField { it.copy(activityStatus = currentActivityStatus) }
        }

        val manageMemoriesBtn = sheet.findViewById<Button>(R.id.manageMemoriesBtn)
        manageMemoriesBtn.setOnClickListener { showMemoryVaultDialog(slot) }

        val searchLoreBtn = sheet.findViewById<Button>(R.id.searchLoreBtn)
        searchLoreBtn.setOnClickListener { openMindPalaceSearch(slot) }

        val linkedCharactersBtn = sheet.findViewById<Button>(R.id.linkedCharactersBtn)
        val hasLinks = slot.linkedTo.isNotEmpty()

        if (hasLinks) {
            linkedCharactersBtn?.visibility = View.VISIBLE
            linkedCharactersBtn?.setOnClickListener {
                showLinkedCharactersDialog(slot)
            }
        } else {
            linkedCharactersBtn?.visibility = View.GONE
        }

        // ----- GOD MODE EDITABLE TEXT ENGINE -----
        // This helper automatically locks fields for players, and unlocks them for the God Mode Host
        fun setupEditableField(textId: Int, headerId: Int?, value: String?, onSave: (String) -> Unit) {
            val hasText = !value.isNullOrBlank()
            val isVisible = hasText || isGodModeHost // God Mode host always sees the field so they can add to it

            val textView = sheet.findViewById<TextView>(textId)
            val headerView = headerId?.let { sheet.findViewById<View>(it) }

            textView?.visibility = if (isVisible) View.VISIBLE else View.GONE
            headerView?.visibility = if (isVisible) View.VISIBLE else View.GONE

            textView?.text = value ?: ""

            // Check if you remembered to change the XML to EditText!
            if (textView is EditText) {
                if (!isGodModeHost) {
                    // Lock it down for normal players
                    textView.isFocusable = false
                    textView.isFocusableInTouchMode = false
                    textView.isCursorVisible = false
                    textView.setBackgroundResource(android.R.color.transparent)
                } else {
                    // Unlock for the GM!
                    textView.isFocusable = true
                    textView.isFocusableInTouchMode = true
                    textView.isCursorVisible = true
                    textView.setBackgroundResource(android.R.drawable.edit_text) // Give it a subtle background so they know they can click it

                    textView.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                        if (!hasFocus) {
                            val newText = textView.text.toString().trim()
                            if (newText != (value ?: "").trim()) {
                                onSave(newText)
                            }
                        }
                    }
                }
            }
        }

        sheet.findViewById<TextView>(R.id.sheetCreatorNotes).apply {
            text = "Creator Note: ${slot.creatorNotes}"
            visibility = if (slot.creatorNotes != null) View.VISIBLE else View.GONE
        }

        // Apply the Editable Engine to all lore blocks!
        setupEditableField(R.id.summaryView, null, slot.summary) { newText ->
            saveSlotField { it.copy(summary = newText) }
        }
        setupEditableField(R.id.personalityText, R.id.personalityHeader, slot.personality) { newText ->
            saveSlotField { it.copy(personality = newText) }
        }
        setupEditableField(R.id.backstoryText, R.id.backstoryHeader, slot.backstory) { newText ->
            saveSlotField { it.copy(backstory = newText) }
        }
        setupEditableField(R.id.loreAbilitiesText, R.id.loreAbilitiesHeader, slot.abilities) { newText ->
            saveSlotField { it.copy(abilities = newText) }
        }
        setupEditableField(R.id.physicalDescText, null, slot.physicalDescription) { newText ->
            saveSlotField { it.copy(physicalDescription = newText) }
        }

        val hasLore = !slot.summary.isNullOrBlank() || !slot.personality.isNullOrBlank() || !slot.backstory.isNullOrBlank() || !slot.abilities.isNullOrBlank() || isGodModeHost
        loreSection?.visibility = if (hasLore) View.VISIBLE else View.GONE

        // ----- 2. Physical Details (Static Line) -----
        val physDetails = buildString {
            if (!slot.gender.isNullOrBlank()) append("${slot.gender} • ")
            if (slot.age != null && slot.age != 0) append("Age: ${slot.age} • ")
            if (!slot.height.isNullOrBlank()) append("${slot.height} ")
            if (!slot.weight.isNullOrBlank()) append("/ ${slot.weight} • ")
            if (!slot.hairColor.isNullOrBlank()) append("Hair: ${slot.hairColor} • ")
            if (!slot.eyeColor.isNullOrBlank()) append("Eyes: ${slot.eyeColor}")
        }.trimEnd(' ', '•')

        sheet.findViewById<TextView>(R.id.physicalDetailsText)?.apply {
            text = physDetails
            visibility = if (physDetails.isNotBlank()) View.VISIBLE else View.GONE
        }

        // ----- 4. Secret Section (The NSFW Vault) -----
        val hasSecret = !slot.privateDescription.isNullOrBlank() || isGodModeHost
        if (!isSfwOnly && hasSecret) {
            secretSection?.visibility = View.VISIBLE
            val revealBtn = sheet.findViewById<Button>(R.id.revealSecretBtn)

            // Use the editable engine for the secret text too!
            setupEditableField(R.id.secretText, null, slot.privateDescription) { newText ->
                saveSlotField { it.copy(privateDescription = newText) }
            }

            revealBtn?.setOnClickListener {
                revealBtn.visibility = View.GONE
                sheet.findViewById<TextView>(R.id.secretText)?.visibility = View.VISIBLE
            }

            // If God Mode, just reveal it immediately so they don't have to click the button to edit
            if (isGodModeHost) {
                revealBtn?.visibility = View.GONE
                sheet.findViewById<TextView>(R.id.secretText)?.visibility = View.VISIBLE
            }

        } else {
            secretSection?.visibility = View.GONE
        }

        // ----- 5. RPG fill + visibility -----
        val hasRpg = rpgEnabled && (slot.rpgClass.isNotBlank() || slot.stats.isNotEmpty() || (slot.hp > 0 && slot.maxHp > 0))
        rpgSection?.apply {
            visibility = if (hasRpg) View.VISIBLE else View.GONE
            if (hasRpg) {
                sheet.findViewById<TextView>(R.id.classView)?.text   = "Class: ${slot.rpgClass}"

                // --- DYNAMIC CLASS BONUS LOOKUP ---
                val savedClassString = slot.rpgClass ?: ""
                val matchingEnum = CharacterClass.values().find { enum ->
                    enum.name.replace('_', ' ').equals(savedClassString, ignoreCase = true)
                }
                val mechanicalBonus = matchingEnum?.mechanicalBonus?.takeIf { it.isNotBlank() } ?: "No specific bonus."

                // Inject it right into your new TextView!
                sheet.findViewById<TextView>(R.id.abilitiesView).text = "Bonus: $mechanicalBonus"

                sheet.findViewById<TextView>(R.id.hpView)?.text      = "HP: ${slot.hp}/${slot.maxHp}"
                sheet.findViewById<TextView>(R.id.defenseView)?.text = "Defense: ${slot.defense}"

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

        // ----- 6. More Info (Role + Timeline) -----
        val roleRaw = (slot.hiddenRoles ?: "").trim()
        val roleDisplay = when (roleRaw.uppercase()) {
            "VILLAIN" -> "killer"
            "TARGET"  -> "victim"
            "HERO", "GM", "SIDEKICK", "" -> if (murderEnabled) "innocent" else ""
            else -> roleRaw.lowercase()
        }

        val timelineText = slot.moreInfo?.takeIf { it.isNotBlank() }
        val headerLine = if (roleDisplay.isNotBlank()) "Role: ${roleDisplay.replaceFirstChar { it.uppercase() }}" else null
        val composed = buildString {
            headerLine?.let { append(it).append('\n') }
            append(timelineText ?: "Nothing to add here.")
        }
        moreInfoText?.text = composed

        val showMoreInfo = (timelineText != null) || (murderEnabled && roleDisplay.isNotBlank())
        moreInfoBlock?.visibility = if (showMoreInfo) View.VISIBLE else View.GONE

        // ----- 7. VN section -----
        val showVn = vnEnabled && !slot.vnRelationships.isNullOrEmpty()
        vnSection?.apply {
            visibility = if (showVn) View.VISIBLE else View.GONE
            if (showVn) {
                val vnList = sheet.findViewById<LinearLayout>(R.id.vnRelationshipsList)
                vnList?.removeAllViews()
                val byKey = (sessionProfile?.slotRoster ?: emptyList()).mapIndexed { idx, s -> ModeSettings.SlotKeys.fromPosition(idx) to s }.toMap()
                slot.vnRelationships.forEach { (toKey, rel) ->
                    val other = byKey[toKey] ?: return@forEach
                    val maxL = rel.levels.maxOfOrNull { it.level } ?: 5
                    val filled = rel.currentLevel.coerceIn(0, maxL)
                    val hearts = "❤".repeat(filled) + "♡".repeat((maxL - filled).coerceAtLeast(0))
                    val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 8); gravity = Gravity.CENTER_VERTICAL }
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

        // ----- Constraint Wiring Engine -----
        fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
        val cs = androidx.constraintlayout.widget.ConstraintSet().apply { clone(root) }
        fun topToBottom(id: Int, anchor: Int, m: Int) { cs.clear(id, ConstraintSet.TOP); cs.connect(id, ConstraintSet.TOP, anchor, ConstraintSet.BOTTOM, dp(m)) }

        // Automatically stack visible sections!
        var anchor = R.id.searchLoreBtn

        if (hasLinks && linkedCharactersBtn != null) {
            topToBottom(linkedCharactersBtn.id, anchor, 0)
            anchor = linkedCharactersBtn.id
        }

        // 2. Stack the Lore Section
        if (loreSection != null && loreSection.visibility == View.VISIBLE) {
            topToBottom(loreSection.id, anchor, 12)
            anchor = loreSection.id
        }
        if (secretSection != null && secretSection.visibility == View.VISIBLE) {
            topToBottom(secretSection.id, anchor, 12)
            anchor = secretSection.id
        }
        if (hasRpg && rpgSection != null) {
            topToBottom(rpgSection.id, anchor, 12)
            anchor = rpgSection.id
        }
        if (showMoreInfo && moreInfoBlock != null) {
            topToBottom(moreInfoBlock.id, anchor, 12)
            anchor = moreInfoBlock.id
        }
        if (showVn && vnSection != null) {
            topToBottom(vnSection.id, anchor, 12)
        }

        cs.applyTo(root)
        content.addView(sheet)
    }

    private fun Context.dp(n: Int): Int = (n * resources.displayMetrics.density).roundToInt()

    private fun showMemoryVaultDialog(slot: SlotProfile) {
        val db = FirebaseFirestore.getInstance()
        val sessionId = sessionId ?: return

        // 1. Build the UI Programmatically
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        val addBtn = Button(this).apply {
            text = "+ Add New Memory"
            setOnClickListener {
                // We will pass a callback to refresh this dialog!
                showAddMemoryDialog(slot) { showMemoryVaultDialog(slot) }
            }
        }
        container.addView(addBtn)

        val scrollContainer = ScrollView(this)
        val listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        scrollContainer.addView(listLayout)
        container.addView(scrollContainer)

        val dialog = AlertDialog.Builder(this)
            .setTitle("${slot.name}'s Memories")
            .setView(container)
            .setPositiveButton("Done", null)
            .create()

        // 2. Fetch and populate the list
        listLayout.addView(TextView(this).apply { text = "\nLoading memories..." })

        db.collection("sessions").document(sessionId)
            .collection("character_memories")
            .whereEqualTo("slotId", slot.slotId)
            .get()
            .addOnSuccessListener { snap ->
                listLayout.removeAllViews()
                val memories = snap.toObjects(TaggedMemory::class.java)

                if (memories.isEmpty()) {
                    listLayout.addView(TextView(this).apply { text = "\nNo core memories established." })
                } else {
                    for (mem in memories) {
                        val memoryCard = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(0, 24, 0, 24)

                            addView(TextView(context).apply {
                                text = "• ${mem.text}"
                                textSize = 16f
                                setTextColor(Color.WHITE)
                            })

                            // Edit/Delete prompt hint
                            addView(TextView(context).apply {
                                text = "Tap to edit or delete"
                                textSize = 12f
                                setTextColor(Color.GRAY)
                            })

                            setOnClickListener {
                                dialog.dismiss() // Close the vault temporarily
                                showEditMemoryDialog(slot, mem) { showMemoryVaultDialog(slot) }
                            }
                        }
                        listLayout.addView(memoryCard)

                        // Add a divider
                        val divider = View(this).apply {
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2)
                            setBackgroundColor(Color.DKGRAY)
                        }
                        listLayout.addView(divider)
                    }
                }
            }

        dialog.show()
    }

    private fun showAddMemoryDialog(slot: SlotProfile, onComplete: () -> Unit) {
        val input = EditText(this).apply {
            hint = "e.g., Learns that the King is a traitor."
            setPadding(32, 32, 32, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("Add Core Memory")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotBlank()) saveMemoryWithEmbedding(slot, null, text, onComplete)
            }
            .setNegativeButton("Cancel") { _, _ -> onComplete() }
            .show()
    }

    private fun showEditMemoryDialog(slot: SlotProfile, mem: TaggedMemory, onComplete: () -> Unit) {
        val input = EditText(this).apply {
            setText(mem.text)
            setPadding(32, 32, 32, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("Edit Memory")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotBlank() && text != mem.text) {
                    saveMemoryWithEmbedding(slot, mem, text, onComplete)
                } else {
                    onComplete()
                }
            }
            .setNeutralButton("Delete") { _, _ ->
                FirebaseFirestore.getInstance().collection("sessions").document(sessionId!!)
                    .collection("character_memories").document(mem.id).delete()
                    .addOnSuccessListener { onComplete() }
            }
            .setNegativeButton("Cancel") { _, _ -> onComplete() }
            .show()
    }

    private fun saveMemoryWithEmbedding(slot: SlotProfile, existingMem: TaggedMemory?, newText: String, onComplete: () -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val sessionId = sessionId ?: return

        Toast.makeText(this, "Embedding memory...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val vector = Director.getEmbedding(newText, BuildConfig.OPENAI_API_KEY)

                val memDoc = existingMem?.copy(text = newText, embedding = vector) ?: TaggedMemory(
                    id = java.util.UUID.randomUUID().toString(),
                    slotId = slot.slotId,
                    text = newText,
                    embedding = vector,
                    tags = listOf("manual_entry")
                )

                db.collection("sessions").document(sessionId)
                    .collection("character_memories").document(memDoc.id)
                    .set(memDoc).await()

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Memory Secured!", Toast.LENGTH_SHORT).show()
                    onComplete() // This re-opens the Vault Dialog so they see the update!
                }
            } catch (e: Exception) {
                Log.e("MemoryEngine", "Failed to embed memory", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed to save memory.", Toast.LENGTH_SHORT).show()
                    onComplete()
                }
            }
        }
    }

    fun dockTogglesAbove(viewBelowId: Int) {
        val root = findViewById<ConstraintLayout>(R.id.chatRoot)
        val cs = ConstraintSet()
        cs.clone(root)
        cs.connect(R.id.toggleButtonContainer, ConstraintSet.BOTTOM, viewBelowId, ConstraintSet.TOP)
        cs.connect(R.id.toggleChatButton, ConstraintSet.BOTTOM, viewBelowId, ConstraintSet.TOP)
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

        Log.d("ai_cycle", "getting speaker from $targetLocation in $targetArea")

        // 2. Who is here? (Exclude player and placeholders)
        val presentChars = sessionProfile.slotRoster.filter {
            it.profileType != "player" &&
                    !it.isPlaceholder &&
                    it.lastActiveArea == targetArea &&
                    it.lastActiveLocation == targetLocation &&
                    it.activityStatus
        }

        Log.d ("ai_cycle", "character check: ${presentChars.size}")
        if (presentChars.isEmpty()) return "narrator"

        // 3. Did user explicitly name someone?
        val mentionedChar = presentChars.find {
            userText.contains(it.name, ignoreCase = true)
        }
        if (mentionedChar != null) return mentionedChar.slotId

        // 4. Who spoke last?
        val lastSpeakerId = previousSpeakerId

        Log.d("ai_cycle", "last speaker is $lastSpeakerId")

        val candidates = presentChars.filter { it.slotId != lastSpeakerId }

        Log.d ("ai_cycle", "character check: ${candidates.size}")

        // 5. Pick random
        return candidates.randomOrNull()?.slotId ?: activeSlotId
    }

    private fun fetchRelevantLore(queryVector: List<Double>, activeLorebookIds: List<String>): String {
        if (activeLorebookIds.isEmpty()) return ""

        // 1. Pull only the entries from books that are active for THIS specific character/message
        val allActiveEntries = mutableListOf<LoreEntry>()
        for (id in activeLorebookIds) {
            cachedLorebooks[id]?.entries?.let { allActiveEntries.addAll(it) }
        }

        if (allActiveEntries.isEmpty()) return ""

        // 2. Separate "Always On" from standard RAG entries
        val alwaysOnEntries = allActiveEntries.filter { it.alwaysOn }
        val triggeredEntries = allActiveEntries.filter { !it.alwaysOn && !it.embedding.isNullOrEmpty() }

        // 3. Score the triggered entries against the user's message
        val scoredEntries = triggeredEntries.map { entry ->
            val score = if (queryVector.isNotEmpty()) {
                Director.cosineSimilarity(queryVector, entry.embedding!!)
            } else {
                0.0
            }
            entry to score
        }
            .filter { it.second > 0.35 } // Accuracy Threshold
            .sortedByDescending { it.second }
            .take(3) // Top 3 hits
            .map { it.first }

        // 4. Combine and Cap Memory Budget (Max ~2500 characters)
        val finalLoreList = mutableListOf<String>()
        var currentLength = 0
        val MAX_LORE_LENGTH = 2500

        for (entry in alwaysOnEntries) {
            if (currentLength + entry.content.length <= MAX_LORE_LENGTH) {
                finalLoreList.add("- ${entry.name}: ${entry.content}")
                currentLength += entry.content.length
            }
        }

        for (entry in scoredEntries) {
            if (currentLength + entry.content.length <= MAX_LORE_LENGTH) {
                finalLoreList.add("- ${entry.name}: ${entry.content}")
                currentLength += entry.content.length
            }
        }

        return if (finalLoreList.isNotEmpty()) {
            "\n[WORLD & CHARACTER LORE]\n" + finalLoreList.joinToString("\n") + "\n"
        } else {
            ""
        }
    }

    private suspend fun fetchRelevantMemories(
        queryVector: List<Double>, // <-- Pass the pre-calculated vector here!
        charSlotId: String,
        sessionId: String          // Ensure this is passed if it's not a global variable!
    ): List<TaggedMemory> {
        // 1. Fetch this specific character's memories from the Subcollection
        val snapshot = FirebaseFirestore.getInstance()
            .collection("sessions").document(sessionId)
            .collection("character_memories")
            .whereEqualTo("slotId", charSlotId)
            .get()
            .await()

        if (snapshot.isEmpty) return emptyList()

        val memories = snapshot.documents.mapNotNull { it.toObject(TaggedMemory::class.java) }
        if (memories.isEmpty()) return emptyList()

        // If embedding fails (offline/error), fallback to recent memories
        if (queryVector.isEmpty()) return memories.takeLast(3)

        // 3. Score every memory
        return memories.map { mem ->
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
                nextSlotId = determineNextSpeakerLocal(input, chatHistory)
                Log.d("ai_cycle","new next slot: $nextSlotId")
                // Handle Narrator or "No one here"
                if (nextSlotId == "narrator" || nextSlotId.isBlank()) {
                    // (Optional: You can insert a narrator call here if you want logic for that,
                    // but for now we exit to avoid infinite loops of silence)
                    withContext(Dispatchers.Main) { updateButtonState(ButtonState.SEND) }
                    return@launch
                }
                
                val activeSlotId = sessionProfile.userMap[userId]?.activeSlotId 
                
                if (nextSlotId == activeSlotId){
                    withContext(Dispatchers.Main) {
                        updateButtonState(ButtonState.SEND)
                        // Move the toast INSIDE this block
                        Toast.makeText(this@MainActivity, "Control returned to you", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }


                // 3. Set Typing Indicator
                setSlotTyping(sessionId, nextSlotId, true)
                Log.d("ai_response", "$nextSlotId starting to type")



                // 5. Prepare Data for Prompt
                val slotProfile = sessionProfile.slotRoster.find { it.slotId == nextSlotId }!!
                Log.d("ai_response", "replying as: ${slotProfile.name}")
                val sceneArea = slotProfile.lastActiveArea ?: "Unknown"
                val sceneLocation = slotProfile.lastActiveLocation ?: "Unknown"

                // 1. Determine if this is a Fusion (Hive Mind) and gather all relevant Slot IDs
                val componentSlotIds = slotProfile.linkedTo
                    .filter { it.type.lowercase() == "unfuse" }
                    .map { it.targetId }

                // The list of IDs to query: The Fused Character + All Component Characters
                val allRelevantSlotIds = (listOf(slotProfile.slotId) + componentSlotIds).distinct()

                // 2. Fetch Personal History for ALL components and sort by time
                val combinedHistory = allRelevantSlotIds.flatMap { sId ->
                    fetchPersonalHistory(sessionId, sId) // Assuming this returns a List<Message>
                }.sortedBy { it.timestamp?.toDate()?.time ?: 0L } // Sort chronologically so the context makes sense
                    .takeLast(10) // Take the 10 most recent across all minds

                val historyString = buildHistoryString(combinedHistory)

                // 3. RAG: Fetch Memories locally
                val queryVector = Director.getEmbedding(input, BuildConfig.OPENAI_API_KEY)

                // 4. Gather Lorebooks: Global + Fused Character + Sleeping Components
                val componentLorebooks = sessionProfile.slotRoster
                    .filter { it.slotId in componentSlotIds }
                    .flatMap { it.lorebookIds }
                val activeLorebookIds = (sessionProfile.globalLorebookIds + slotProfile.lorebookIds + componentLorebooks).distinct()

                // 5. Fetch Relevant Memories for ALL minds concurrently!
                val relevantMemoriesAsync = async {
                    allRelevantSlotIds.flatMap { sId ->
                        fetchRelevantMemories(queryVector, sId, sessionId)
                    }
                    // Note: If fetchRelevantMemories returns top 5, this will return 5 per component.
                    // The AI will get a massive memory injection of everyone involved!
                }
                val relevantLoreAsync = async { fetchRelevantLore(queryVector, activeLorebookIds) }

                val relevantMemories = relevantMemoriesAsync.await()
                val relevantLoreString = relevantLoreAsync.await()

                // Map it all back to the current acting slot (The Fused Character)
                val memoriesMap = mapOf(slotProfile.slotId to relevantMemories)

                val isStrictSfw = sessionProfile.sfwOnly == true ||
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

                // 1. LOCAL PRESENCE: Who is here and what do they look like?
                val namesInRoom = sessionProfile.slotRoster
                    .filter {
                        it.lastActiveArea == sceneArea &&
                                it.lastActiveLocation == sceneLocation &&
                                it.slotId != nextSlotId
                    }
                    .map { otherChar ->
                        // Grab THEIR active outfit
                        val theirOutfit = otherChar.outfits?.find { it.name.equals(otherChar.currentOutfit, ignoreCase = true) }

                        // Find their effective appearance
                        val theirAppearance = theirOutfit?.physicalDescOverride?.takeIf { it.isNotBlank() }
                            ?: otherChar.physicalDescription

                        val theirPronouns = otherChar.gender

                        val theirHeight = theirOutfit?.heightOverride?.takeIf { it.isNotBlank() }
                            ?: otherChar.height

                        val theirWeight = theirOutfit?.weightOverride?.takeIf { it.isNotBlank() }
                            ?: otherChar.weight

                        val theirEyes = theirOutfit?.eyeColorOverride?.takeIf { it.isNotBlank() }
                            ?: otherChar.eyeColor

                        val theirHair = theirOutfit?.hairColorOverride?.takeIf { it.isNotBlank() }
                            ?: otherChar.hairColor

                        // Keep it brief so we don't blow up the context window.
                        // E.g., "Goku (Appearance: Glowing blonde hair, teal eyes. Status: Active)"
                        val statusText = if (otherChar.activityStatus) "Active" else "Quietly in background"

                        "${otherChar.name} (Appearance: $theirAppearance | Pronouns: $theirPronouns | Height: $theirHeight | Weight: $theirWeight | Eye Color: $theirEyes | Hair Color: $theirHair | Status: $statusText)"
                    }.joinToString("\n")

                // 2. GLOBAL KNOWLEDGE: The dense facts for ACTIVE characters only
                val worldCompendium = sessionProfile.slotRoster
                    .filter { it.activityStatus }
                    .joinToString("\n") { profile ->
                        "- ${profile.name}: ${profile.summary}"
                    }

                val personality = if(isStrictSfw){
                    slotProfile.personality
                }else{
                    slotProfile.personality + "\n Only use Secrets when its appropriate.\n Secrets:" + slotProfile.privateDescription
                }

                val sessionSummary = buildString {
                    // 1. Always add the base description
                    append(sessionProfile.sessionDescription)

                    // 2. Add the secret description if it's allowed and exists
                    if (!isStrictSfw && sessionProfile.secretDescription!!.isNotBlank()) {
                        append("\n\n").append(sessionProfile.secretDescription)
                    }

                    // 3. Inject the Active Event with the "Flare Gun" formatting
                    if (sessionProfile.currentEvent.isNotBlank()) {
                        append("\n\n*** URGENT SCENARIO UPDATE ***\n")
                        append("The following event is happening RIGHT NOW: ${sessionProfile.currentEvent}\n")
                        append("All characters MUST react to this event immediately in their next response.")
                    }
                }

                val characterPinnedIds = slotProfile.pinnedMessages ?: emptyList()
                val pinnedMessagesStr = if (characterPinnedIds.isNotEmpty()) {
                    // Look through the session history for these IDs
                    val pinnedMsgs = sessionProfile.history.filter { msg ->
                        characterPinnedIds.contains(msg.id)
                    }

                    if (pinnedMsgs.isNotEmpty()) {
                        buildString {
                            append("CORE MEMORIES (CRITICAL CONTEXT - You MUST remember and reference these if relevant):\n")
                            pinnedMsgs.forEach { msg ->
                                val senderName = msg.displayName ?: "System"
                                append("- [$senderName]: \"${msg.text}\"\n")
                            }
                        }
                    } else ""
                } else ""

                Log.d("ai_cycle", "personality: $personality")
                // 6. Build the Roleplay Prompt
                val roleplayPrompt = PromptBuilder.buildRoleplayPrompt(
                    slotProfile = slotProfile,
                    sessionProfile = sessionProfile,
                    personality = personality,
                    modeSettings = sessionProfile.modeSettings,
                    sessionSummary = sessionSummary,
                    sceneSlotIds = sceneSlotIds,
                    condensedCharacterInfo = worldCompendium,
                    currentScene = namesInRoom,
                    chatHistory = historyString,
                    memories = memoriesMap,
                    relevantLoreString = relevantLoreString,
                    // poses = poses,
                    locationDescription = locDescription,
                    pinnedMessagesString = pinnedMessagesStr
                )

                // 7. game mode Routing

                val vnPrompt = buildVNPrompt(
                    slotProfile = slotProfile,
                    sessionProfile = sessionProfile
                )


                val godPrompt = buildGodModePrompt()

                val godModeEnable = sessionProfile.enabledModes.contains("god_mode")

                val godModeCheckedPrompt = if (godModeEnable){
                    roleplayPrompt + godPrompt
                } else {
                    roleplayPrompt
                }

                val NPCGenPrompt = buildNPCGeneration(sessionProfile.areas)
                // Delete the modeSettings JSON check and use this:
                val vnModeEnabled = sessionProfile.enabledModes.contains("visual_novel") ||
                        sessionProfile.enabledModes.contains("vn")

                val vnCheckedPrompt = if (vnModeEnabled) {
                    godModeCheckedPrompt + NPCGenPrompt + vnPrompt
                } else {
                    godModeCheckedPrompt + NPCGenPrompt
                }

                val nsfwinstructions =  "\n - Be descriptive and explicit. The more sexual and erotic the better." +
                        "\n - ONLY get sexual when its APPROPRIATE in context of the conversation."
                val nsfwRejection = "\n - If responding to not safe for work messages, reject them while remaining in character."
                val nsfwRejectionPrompt = vnCheckedPrompt + nsfwRejection
                val nsfwPrompt = vnCheckedPrompt + nsfwinstructions

                val selectedModelId = slotProfile.modelId ?: sessionProfile.aiModel ?: "deepseek"
                Log.d("Model_debug", "using model: $selectedModelId the profile is ${sessionProfile.title} using ${sessionProfile.aiModel}")
                when {
                    // Branch A: Existing Strict SFW logic
                    isStrictSfw -> {
                        Log.d("ai_cycle", "$selectedModelId branch a prompt: $nsfwRejectionPrompt")
                    }

                    // Branch B: The new OpenAI Experiment (using nsfwPrompt)
                    selectedModelId == "openai-gpt-4o" -> {
                        Log.d("ai_cycle", "$selectedModelId branch b prompt: $nsfwPrompt")
                    }

                    // Branch C: Standard Unfiltered / Mixtral Path
                    else -> {
                        Log.d("ai_cycle", " $selectedModelId branch c prompt: $nsfwPrompt")
                    }
                }
                val finalPrompt = if (isStrictSfw){
                    nsfwRejectionPrompt
                }else{
                    nsfwPrompt
                }

                val apiRoute = getApiRoute(selectedModelId)
                val temp = slotProfile.temperature
                val topK = slotProfile.topK
                val topP = slotProfile.topP

                val responseText = withTimeoutOrNull(60_000L) {
                    when (apiRoute) {
                        "openRouter" -> {
                            Facilitator.callMixtralApi(
                                finalPrompt,
                                BuildConfig.MIXTRAL_API_KEY,
                                selectedModelId,
                                temp, topK, topP
                            )
                        }
                        "mancer" -> {
                            // NEW ROUTE!
                            Facilitator.callMancerApi(
                                finalPrompt,
                                BuildConfig.MANCER_API_KEY,
                                selectedModelId,
                                temp, topK, topP
                            )
                        }
                        "openAI" -> {
                            Facilitator.callOpenAiApi(
                                finalPrompt,
                                BuildConfig.OPENAI_API_KEY,
                                selectedModelId,
                                temp, topK, topP
                            )
                        }
                        else -> null
                    }
                }


                Log.d("ai_response", "raw response: $responseText")

                if (responseText == null) {
                    Log.e("AI_CYCLE", "The AI request timed out after 60 seconds.")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "The AI took too long to respond. Please try again.", Toast.LENGTH_SHORT).show()
                        setSlotTyping(sessionId, nextSlotId, false)
                        updateButtonState(ButtonState.SEND)
                    }
                    return@launch
                }

                // 8. Parse Response
                val result = FacilitatorResponseParser.parseRoleplayAIResponse(
                    responseText,
                    nextSlotId,
                    sessionProfile.slotRoster
                )
                withContext(Dispatchers.Main) { updateButtonState(ButtonState.WAITING) }

                // bad response check
                if (result.messages == null) {
                    Log.e("AI_CYCLE", "The AI response is bad.")

                    var isPremium = false
                    val userId = FirebaseAuth.getInstance().currentUser?.uid

                    // If it's round 1, we need to know their premium status to show the right Toast
                    if (activationRound == 1 && userId != null) {
                        try {
                            val db = FirebaseFirestore.getInstance()
                            // .await() safely pauses the coroutine here on the IO thread without blocking the UI
                            val snapshot = db.collection("users").document(userId).get().await()
                            isPremium = snapshot.getBoolean("isPremium") ?: false
                        } catch (e: Exception) {
                            Log.e("Billing", "Failed to check premium status: ${e.message}")
                        }
                    }

                    // Switch to Main thread STRICTLY for UI updates
                    withContext(Dispatchers.Main) {
                        val toastMsg = if (activationRound == 1 && !isPremium) {
                            "The AI response was malformed. Your message was not charged."

                        } else {
                            "The AI response was malformed." // Premium users or subsequent rounds see this
                        }
                        Toast.makeText(this@MainActivity, toastMsg, Toast.LENGTH_SHORT).show()
                        setSlotTyping(sessionId, nextSlotId, false)
                        updateButtonState(ButtonState.SEND)
                    }

                    // ABORT the rest of the round entirely. No DB writes, no billing!
                    return@launch
                }

                if (activationRound == 1) {
                    val userId = FirebaseAuth.getInstance().currentUser?.uid
                    if (userId != null) {
                        val db = FirebaseFirestore.getInstance()
                        val userRef = db.collection("users").document(userId)
                        val today = Timestamp.now()

                        // Run this in the background (IO context) without blocking the UI
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                db.runTransaction { transaction ->
                                    val snapshot = transaction.get(userRef)

                                    // Double-check they didn't just go over limit in a race condition
                                    // (Optional, but good for security)
                                    val isPremium = snapshot.getBoolean("isPremium") ?: false
                                    if (!isPremium) {
                                        val currentCount =
                                            snapshot.getLong("dailyMessageCount") ?: 0

                                        transaction.set(
                                            userRef,
                                            mapOf(
                                                "dailyMessageCount" to currentCount + 1,
                                                "lastMessageDate" to today
                                            ),
                                            SetOptions.merge()
                                        )
                                        Log.d("Billing", "User charged for Round 1 response.")
                                    }
                                }.await()
                            } catch (e: Exception) {
                                Log.e("Billing", "Failed to update message count: ${e.message}")
                            }
                        }
                    }
                }

                Log.d("ai_response", "recieved: $result")

                if (result.relationshipChanges.isNotEmpty()) {
                    // 1. Apply the point changes and recalculate levels locally
                    FacilitatorResponseParser.applyVNChanges(
                        sessionProfile = sessionProfile,
                        changes = result.relationshipChanges
                    )

                    // 2. Push the updated relationships back to Firestore!
                    FirebaseFirestore.getInstance()
                        .collection("sessions")
                        .document(sessionProfile.sessionId) // Make sure you have the session ID
                        .update("slotRoster", sessionProfile.slotRoster)
                        .addOnSuccessListener {
                            Log.d(
                                "VN_UPDATE",
                                "Successfully saved ${result.relationshipChanges.size} relationship changes to Firestore!"
                            )
                        }
                        .addOnFailureListener { e ->
                            Log.e("VN_UPDATE", "Failed to save relationship changes", e)
                        }
                }

                // Define the variables here so the Main thread can see them!
                var pendingDiceRoll: Action? = null
                var pendingGodQuestion: String? = null

                for (action in result.actions) {
                    when (action.type) {
                        "health_change" -> handleHealthChange(action.slot, action.mod ?: 0)
                        "status_effect" -> handleStatusEffect(action.slot, action.stat ?: "", action.mod ?: 1)
                        "new_npc" -> {
                            action.npc?.let { npcData -> handleNewNPC(npcData) }
                        }
                        "roll_dice" -> pendingDiceRoll = action

                        "advance_act" -> advanceStoryAct()
                        "ask_god" -> pendingGodQuestion = action.question ?: action.stat
                    }
                }

                // 10. Update UI & Save
                withContext(Dispatchers.Main) {
                    // Stop typing
                    setSlotTyping(sessionId, nextSlotId, false)

                    // Save Session Data (Memories, etc)
                    saveSessionProfile(sessionProfile, sessionId)

                    val enrichedMessages = result.messages.map { msg ->
                        val senderSlot = sessionProfile.slotRoster.find { it.slotId == msg.senderId }

                        // 1. Check if they did an action
                        val actionText = extractActionText(msg.text)
                        var newPoseName: String? = null

                        if (actionText != null && senderSlot != null) {
                            // 2. Fetch the math for the action
                            val actionVector = getVectorForText(msg.text)

                            if (actionVector != null) {
                                // 3. We need to fetch their Wardrobe Subcollection here!
                                val senderBaseId = senderSlot.baseCharacterId!!
                                val wardrobeOutfits = fetchWardrobeForBaseId(sessionId, senderBaseId)
                                Log.d("PosingEngine", "STEP 3: Fetched ${wardrobeOutfits.size} outfits from DB for ${senderSlot.name}")

                                // 4. Find their current outfit
                                val currentOutfit = wardrobeOutfits.find { it.name == senderSlot.currentOutfit }

                                if (currentOutfit != null) {
                                    // 5. Do the math! Find the pose with the highest similarity score
                                    var bestMatchName: String? = null
                                    var highestScore = -1.0

                                    for (pose in currentOutfit.poseSlots) {
                                        if (pose.vector != null) {
                                            val score = calculateCosineSimilarity(actionVector, pose.vector!!)
                                            if (score > highestScore) {
                                                highestScore = score
                                                bestMatchName = pose.name
                                            }
                                        }
                                    }

                                    // 6. Is it close enough?
                                    if (highestScore >= 0.35) {
                                        newPoseName = bestMatchName
                                        Log.d("PosingEngine", "STEP 6: WINNER! Matched to: $newPoseName (Score: $highestScore)")
                                    } else {
                                        Log.d("PosingEngine", "STEP 6: FAILED THRESHOLD. Best was $bestMatchName but score was only $highestScore. Keeping current pose.")
                                        newPoseName = msg.pose // Fallback to whatever they are currently wearing
                                    }

                                }
                            }
                        }

                        msg.copy(
                            displayName = senderSlot?.name ?: "Bot",
                            area = senderSlot?.lastActiveArea,
                            location = senderSlot?.lastActiveLocation,
                            // IF we found a new pose, save it to the message so the UI can update!
                            pose = newPoseName ?: senderSlot?.pose,
                            visibility = true,
                            timestamp = msg.timestamp ?: com.google.firebase.Timestamp.now()
                        )
                    }
                    Log.d("ai_response", "enriched")

                    // Display Messages
                    saveMessagesSequentially(enrichedMessages, sessionId, chatId)
                    Log.d("ai_response", "saved")

                    previousSpeakerId = nextSlotId
                    Log.d ("ai_response", "$nextSlotId needs to stop typing")
                    setSlotTyping(sessionId, nextSlotId, false)

                    // --- 11. RESOLVE GOD MODE / DICE INTERCEPTS ---
                    // --- 12. RESOLVE GOD MODE / DICE INTERCEPTS ---
                    if (pendingDiceRoll != null) {
                        // PRIORITY 1: DICE ROLL
                        Log.d("ai_response", "Intercepted by Dice Roll!")

                        // Add Elvis operators (?:) to safely unwrap the nullables!
                        val safeStat = pendingDiceRoll!!.stat ?: "unknown"
                        val safeMod = pendingDiceRoll!!.mod ?: 0

                        handleDiceRoll(pendingDiceRoll!!.slot, safeStat, safeMod)

                        updateButtonState(ButtonState.SEND)
                        // WE DO NOT RECURSE. Control returns to User/GM.

                    } else if (pendingGodQuestion != null && pendingGodQuestion.isNotBlank()) {
                        // PRIORITY 2: NARRATIVE QUESTION
                        Log.d("ai_response", "Intercepted by God Question!")

                        val questionMessage = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            senderId = "narrator",
                            displayName = "GM Prompt",
                            text = pendingGodQuestion,
                            area = sessionProfile.slotRoster.find { it.slotId == nextSlotId }?.lastActiveArea ?: "Unknown",
                            location = sessionProfile.slotRoster.find { it.slotId == nextSlotId }?.lastActiveLocation ?: "Unknown",
                            delay = 0,
                            timestamp = com.google.firebase.Timestamp.now(),
                            visibility = true,
                            messageType = "godMsg" // Special tag for ChatAdapter styling!
                        )

                        // Add to UI and DB
                        chatAdapter.addMessage(questionMessage)
                        SessionManager.sendMessage(chatId, sessionId, questionMessage)

                        updateButtonState(ButtonState.SEND)
                        // WE DO NOT RECURSE. Control returns to User/GM.

                    } else {
                        // PRIORITY 3: NO INTERCEPTS - KEEP THE CONVERSATION GOING
                        if (activationRound < maxActivationRounds) {
                            val updatedHistory = chatAdapter.getMessages()
                            processActivationRound("", updatedHistory)
                        } else {
                            updateButtonState(ButtonState.SEND)
                        }
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


                nextSlotId = characterSlot.slotId

                // 3. Set Typing Indicator
                setSlotTyping(sessionId, nextSlotId, true)

                val slotProfile = sessionProfile.slotRoster.find { it.slotId == nextSlotId }!!

                // 4. RAG: Fetch Memories locally
                val queryVector = Director.getEmbedding(input, BuildConfig.OPENAI_API_KEY)

                val activeLorebookIds = (sessionProfile.globalLorebookIds + slotProfile.lorebookIds).distinct()

                val relevantMemoriesAsync = async { fetchRelevantMemories(queryVector, slotProfile.slotId, sessionId) }
                val relevantLoreAsync = async { fetchRelevantLore(queryVector, activeLorebookIds) }

                val relevantMemories = relevantMemoriesAsync.await()
                val relevantLoreString = relevantLoreAsync.await()

                val memoriesMap = mapOf(nextSlotId to relevantMemories)

                // 5. Prepare Data for Prompt

                Log.d("ai_response", "replying as: ${slotProfile.name}")
                val sceneArea = slotProfile.lastActiveArea ?: "Unknown"
                val sceneLocation = slotProfile.lastActiveLocation ?: "Unknown"
                val myPersonalHistory = fetchPersonalHistory(sessionId, nextSlotId)
                val historyString = buildHistoryString(myPersonalHistory.takeLast(10))

                val isStrictSfw = sessionProfile.sfwOnly == true ||
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


                // 1. LOCAL PRESENCE: Just the names of people in the exact same room
                val namesInRoom = sessionProfile.slotRoster
                    .filter {
                        it.lastActiveArea == sceneArea &&
                                it.lastActiveLocation == sceneLocation &&
                                it.slotId != nextSlotId
                    }
                    .map { otherChar ->
                        // Grab THEIR active outfit
                        val theirOutfit = otherChar.outfits?.find { it.name.equals(otherChar.currentOutfit, ignoreCase = true) }

                        // Find their effective appearance
                        val theirAppearance = theirOutfit?.physicalDescOverride?.takeIf { it.isNotBlank() }
                            ?: otherChar.physicalDescription

                        val theirPronouns = otherChar.gender

                        val theirHeight = theirOutfit?.heightOverride?.takeIf { it.isNotBlank() }
                            ?: otherChar.height

                        val theirWeight = theirOutfit?.weightOverride?.takeIf { it.isNotBlank() }
                            ?: otherChar.weight

                        val theirEyes = theirOutfit?.eyeColorOverride?.takeIf { it.isNotBlank() }
                            ?: otherChar.eyeColor

                        val theirHair = theirOutfit?.hairColorOverride?.takeIf { it.isNotBlank() }
                            ?: otherChar.hairColor

                        // Keep it brief so we don't blow up the context window.
                        // E.g., "Goku (Appearance: Glowing blonde hair, teal eyes. Status: Active)"
                        val statusText = if (otherChar.activityStatus) "Active" else "Quietly in background"

                        "${otherChar.name} (Appearance: $theirAppearance | Pronouns: $theirPronouns | Height: $theirHeight | Weight: $theirWeight | Eye Color: $theirEyes | Hair Color: $theirHair | Status: $statusText)"
                    }.joinToString("\n")

                // 2. GLOBAL KNOWLEDGE: The dense facts for ACTIVE characters only
                val worldCompendium = sessionProfile.slotRoster
                    .filter { it.activityStatus }
                    .joinToString("\n") { profile ->
                        "- ${profile.name}: ${profile.summary}"
                    }

                val personality = if(isStrictSfw){
                    slotProfile.personality
                }else{
                    slotProfile.personality + "\n\n Only use Secrets when its appropriate. \n\n Secrets:" + slotProfile.privateDescription
                }

                val sessionSummary = buildString {
                    // 1. Always add the base description
                    append(sessionProfile.sessionDescription)

                    // 2. Add the secret description if it's allowed and exists
                    if (!isStrictSfw && sessionProfile.secretDescription!!.isNotBlank()) {
                        append("\n\n").append(sessionProfile.secretDescription)
                    }

                    // 3. Inject the Active Event with the "Flare Gun" formatting
                    if (sessionProfile.currentEvent.isNotBlank()) {
                        append("\n\n*** URGENT SCENARIO UPDATE ***\n")
                        append("The following event is happening RIGHT NOW: ${sessionProfile.currentEvent}\n")
                        append("All characters MUST react to this event immediately in their next response.")
                    }
                }

                val characterPinnedIds = slotProfile.pinnedMessages ?: emptyList()
                val pinnedMessagesStr = if (characterPinnedIds.isNotEmpty()) {
                    // Look through the session history for these IDs
                    val pinnedMsgs = sessionProfile.history.filter { msg ->
                        characterPinnedIds.contains(msg.id)
                    }

                    if (pinnedMsgs.isNotEmpty()) {
                        buildString {
                            append("CORE MEMORIES (CRITICAL CONTEXT - You MUST remember and reference these if relevant):\n")
                            pinnedMsgs.forEach { msg ->
                                val senderName = msg.displayName ?: "System"
                                append("- [$senderName]: \"${msg.text}\"\n")
                            }
                        }
                    } else ""
                } else ""

                Log.d("ai_cycle", "personality: $personality")
                // 6. Build the Roleplay Prompt
                val roleplayPrompt = PromptBuilder.buildRoleplayPrompt(
                    slotProfile = slotProfile,
                    sessionProfile = sessionProfile,
                    personality = personality,
                    modeSettings = sessionProfile.modeSettings,
                    sessionSummary = sessionSummary,
                    sceneSlotIds = sceneSlotIds,
                    condensedCharacterInfo = worldCompendium,
                    currentScene = namesInRoom,
                    chatHistory = historyString,
                    memories = memoriesMap,
                    relevantLoreString = relevantLoreString,
                    // poses = poses,
                    locationDescription = locDescription,
                    pinnedMessagesString = pinnedMessagesStr
                )

                // 7. VN / SFW Layering
                val vnPrompt = buildVNPrompt(
                    slotProfile = slotProfile,
                    sessionProfile = sessionProfile
                )

                val godPrompt = buildGodModePrompt()

                val godModeEnable = sessionProfile.enabledModes.contains("god_mode")

                val godModeCheckedPrompt = if (godModeEnable){
                    roleplayPrompt + godPrompt
                } else {
                    roleplayPrompt
                }

                // Delete the modeSettings JSON check and use this:
                val vnModeEnabled = sessionProfile.enabledModes.contains("visual_novel") ||
                        sessionProfile.enabledModes.contains("vn")

                val vnCheckedPrompt = if (vnModeEnabled) {
                    godModeCheckedPrompt + "\n\n" + vnPrompt
                } else {
                    godModeCheckedPrompt
                }

                val nsfwinstructions =  "\n - Be descriptive and explicit. The more sexual and erotic the better"
                val nsfwRejection = "\n - If responding to not safe for work messages, reject them while remaining in character."
                val nsfwRejectionPrompt = vnCheckedPrompt + nsfwRejection + "\n You also roleplay any NPC's as needed. Describe what they do and say"
                val nsfwPrompt = vnCheckedPrompt + nsfwinstructions + "\n You also roleplay any NPC's as needed. Describe what they do and say"

                val selectedModelId = slotProfile.modelId ?: sessionProfile.aiModel ?: "deepseek"


                when {
                    // Branch A: Existing Strict SFW logic
                    isStrictSfw -> {
                        Log.d("ai_cycle", "$selectedModelId branch a prompt: $nsfwRejectionPrompt")
                    }

                    // Branch B: The new OpenAI Experiment (using nsfwPrompt)
                    selectedModelId == "openai-gpt-4o" -> {
                        Log.d("ai_cycle", "$selectedModelId branch b prompt: $nsfwPrompt")
                    }

                    // Branch C: Standard Unfiltered / Mixtral Path
                    else -> {
                        Log.d("ai_cycle", " $selectedModelId branch c prompt: $nsfwPrompt")
                    }
                }

                val finalPrompt = if (isStrictSfw){
                    nsfwRejectionPrompt
                }else{
                    nsfwPrompt
                }

                val apiRoute = getApiRoute(selectedModelId)
                val temp = slotProfile.temperature
                val topK = slotProfile.topK
                val topP = slotProfile.topP

                val responseText = withTimeoutOrNull(60_000L) {
                    when (apiRoute) {
                        "openRouter" -> {
                            Facilitator.callMixtralApi(
                                finalPrompt,
                                BuildConfig.MIXTRAL_API_KEY,
                                selectedModelId,
                                temp, topK, topP
                            )
                        }
                        "mancer" -> {
                            // NEW ROUTE!
                            Facilitator.callMancerApi(
                                finalPrompt,
                                BuildConfig.MANCER_API_KEY,
                                selectedModelId,
                                temp, topK, topP
                            )
                        }
                        "openAI" -> {
                            Facilitator.callOpenAiApi(
                                finalPrompt,
                                BuildConfig.OPENAI_API_KEY,
                                selectedModelId,
                                temp, topK, topP
                            )
                        }
                        else -> null
                    }
                }
                Log.d("ai_response", "Ai raw: $responseText")

                if (responseText == null) {
                    Log.e("AI_CYCLE", "The AI request timed out after 60 seconds.")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "The AI took too long to respond. Please try again.", Toast.LENGTH_SHORT).show()
                        setSlotTyping(sessionId, nextSlotId, false)
                        updateButtonState(ButtonState.SEND)
                    }
                    return@launch
                }

                // 8. Parse
                val result = FacilitatorResponseParser.parseRoleplayAIResponse(
                    responseText,
                    nextSlotId,
                    sessionProfile.slotRoster
                )
                withContext(Dispatchers.Main) { updateButtonState(ButtonState.WAITING) }

                // bad response check
                if (result.messages == null) {
                    Log.e("AI_CYCLE", "The AI response is bad.")

                    var isPremium = false
                    val userId = FirebaseAuth.getInstance().currentUser?.uid

                    // If it's round 1, we need to know their premium status to show the right Toast
                    if (activationRound == 1 && userId != null) {
                        try {
                            val db = FirebaseFirestore.getInstance()
                            // .await() safely pauses the coroutine here on the IO thread without blocking the UI
                            val snapshot = db.collection("users").document(userId).get().await()
                            isPremium = snapshot.getBoolean("isPremium") ?: false
                        } catch (e: Exception) {
                            Log.e("Billing", "Failed to check premium status: ${e.message}")
                        }
                    }

                    // Switch to Main thread STRICTLY for UI updates
                    withContext(Dispatchers.Main) {
                        val toastMsg = if (activationRound == 1 && !isPremium) {
                            "The AI response was malformed. Your message was not charged."

                        } else {
                            "The AI response was malformed." // Premium users or subsequent rounds see this
                        }
                        Toast.makeText(this@MainActivity, toastMsg, Toast.LENGTH_SHORT).show()
                        setSlotTyping(sessionId, nextSlotId, false)
                        updateButtonState(ButtonState.SEND)
                    }

                    // ABORT the rest of the round entirely. No DB writes, no billing!
                    return@launch
                }

                if (activationRound == 1) {
                    val userId = FirebaseAuth.getInstance().currentUser?.uid
                    if (userId != null) {
                        val db = FirebaseFirestore.getInstance()
                        val userRef = db.collection("users").document(userId)
                        val today = Timestamp.now()

                        // Run this in the background (IO context) without blocking the UI
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                db.runTransaction { transaction ->
                                    val snapshot = transaction.get(userRef)

                                    // Double-check they didn't just go over limit in a race condition
                                    // (Optional, but good for security)
                                    val isPremium = snapshot.getBoolean("isPremium") ?: false
                                    if (!isPremium) {
                                        val currentCount =
                                            snapshot.getLong("dailyMessageCount") ?: 0

                                        transaction.set(
                                            userRef,
                                            mapOf(
                                                "dailyMessageCount" to currentCount + 1,
                                                "lastMessageDate" to today
                                            ),
                                            SetOptions.merge()
                                        )
                                        Log.d("Billing", "User charged for Round 1 response.")
                                    }
                                }.await()
                            } catch (e: Exception) {
                                Log.e("Billing", "Failed to update message count: ${e.message}")
                            }
                        }
                    }
                }
                Log.d("ai_response", "recieved: $result")

                // Define the variables here so the Main thread can see them!
                var pendingDiceRoll: Action? = null
                var pendingGodQuestion: String? = null

                for (action in result.actions) {
                    when (action.type) {
                        "health_change" -> handleHealthChange(action.slot, action.mod ?: 0)
                        "status_effect" -> handleStatusEffect(action.slot, action.stat ?: "", action.mod ?: 1)
                        "new_npc" -> {
                            action.npc?.let { npcData -> handleNewNPC(npcData) }
                        }
                        "roll_dice" -> pendingDiceRoll = action
                        "advance_act" -> advanceStoryAct()
                        "ask_god" -> pendingGodQuestion = action.question ?: action.stat
                    }
                }

                setSlotTyping(sessionId, nextSlotId, false)
                // 10. Save & Update
                withContext(Dispatchers.Main) {
                    setSlotTyping(sessionId, nextSlotId, false)
                    saveSessionProfile(sessionProfile, sessionId)

                    val enrichedMessages = result.messages.map { msg ->
                        val senderSlot = sessionProfile.slotRoster.find { it.slotId == msg.senderId }

                        // 1. Check if they did an action
                        val actionText = extractActionText(msg.text)
                        Log.d("PosingEngine", "STEP 1: Extracted Action = $actionText")

                        var newPoseName: String? = null

                        if (actionText != null && senderSlot != null) {
                            // 2. Fetch the math for the action
                            val actionVector = getVectorForText(msg.text)
                            Log.d("PosingEngine", "STEP 2: Vector generated successfully? = ${actionVector != null}")

                            if (actionVector != null) {
                                // 3. Fetch Wardrobe Subcollection
                                val senderBaseId = senderSlot.baseCharacterId!!
                                val wardrobeOutfits = fetchWardrobeForBaseId(sessionId, senderBaseId)
                                Log.d("PosingEngine", "STEP 3: Fetched ${wardrobeOutfits.size} outfits from DB for ${senderSlot.name}")

                                // 4. Find their current outfit (Check AI's choice first, ignore casing!)
                                val targetOutfitName = msg.outfit ?: senderSlot.currentOutfit
                                val currentOutfit = wardrobeOutfits.find { it.name.equals(targetOutfitName, ignoreCase = true) }
                                Log.d("PosingEngine", "STEP 4: Looking for outfit '$targetOutfitName' - Found? = ${currentOutfit != null}")

                                if (currentOutfit != null) {
                                    // 5. Do the math!
                                    var bestMatchName: String? = null
                                    var highestScore = -1.0

                                    Log.d("PosingEngine", "STEP 5: Math Time! Checking ${currentOutfit.poseSlots.size} poses...")
                                    for (pose in currentOutfit.poseSlots) {
                                        if (pose.vector != null) {
                                            val score = calculateCosineSimilarity(actionVector, pose.vector!!)
                                            Log.d("PosingEngine", "   -> Compared to '${pose.name}' (Score: $score)")
                                            if (score > highestScore) {
                                                highestScore = score
                                                bestMatchName = pose.name
                                            }
                                        } else {
                                            Log.d("PosingEngine", "   -> Skipped '${pose.name}' (Vector was NULL in DB!)")
                                        }
                                    }

                                    // 6. Is it close enough?
                                    if (highestScore >= 0.35) {
                                        newPoseName = bestMatchName
                                        Log.d("PosingEngine", "STEP 6: WINNER! Matched to: $newPoseName (Score: $highestScore)")
                                    } else {
                                        Log.d("PosingEngine", "STEP 6: FAILED THRESHOLD. Best was $bestMatchName but score was only $highestScore. Keeping current pose.")
                                        newPoseName = msg.pose // Fallback to whatever they are currently wearing
                                    }
                                }
                            }
                        } else {
                            Log.d("PosingEngine", "FAILED AT STEP 1: actionText=$actionText, senderSlot=${senderSlot?.name}")
                        }

                        msg.copy(
                            displayName = senderSlot?.name ?: "Bot",
                            area = senderSlot?.lastActiveArea,
                            location = senderSlot?.lastActiveLocation,
                            pose = newPoseName ?: senderSlot?.pose,
                            visibility = true,
                            timestamp = msg.timestamp ?: com.google.firebase.Timestamp.now()
                        )
                    }
                    Log.d("ai_response", "enriched")

                    // Display Messages
                    saveMessagesSequentially(enrichedMessages, sessionId, chatId)

                    previousSpeakerId = nextSlotId
                    updateButtonState(ButtonState.SEND)
                    setSlotTyping(sessionId, nextSlotId, false)
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
        Log.d("AI_CYCLE", "Round $activationRound (above the table)")

        aiJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 2. LOCAL LOGIC: Pick the next speaker
                nextSlotId = determineNextSpeakerLocal(input, chatHistory)

                // Handle Narrator or "No one here"
                if (nextSlotId == "narrator" || nextSlotId.isBlank()) {
                    // (Optional: You can insert a narrator call here if you want logic for that,
                    // but for now we exit to avoid infinite loops of silence)
                    withContext(Dispatchers.Main) { updateButtonState(ButtonState.SEND) }
                    return@launch
                }
                val activeSlotId = sessionProfile.userMap[userId]?.activeSlotId

                if (nextSlotId == activeSlotId){
                    withContext(Dispatchers.Main) {
                        updateButtonState(ButtonState.SEND)
                        // Move the toast INSIDE this block
                        Toast.makeText(this@MainActivity, "Control returned to you", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // 3. Set Typing Indicator
                setSlotTyping(sessionId, nextSlotId, true)
                val slotProfile = sessionProfile.slotRoster.find { it.slotId == nextSlotId }!!

                // 4. RAG: Fetch Memories locally
                // 1. Determine if this is a Fusion (Hive Mind) and gather all relevant Slot IDs
                val componentSlotIds = slotProfile.linkedTo
                    .filter { it.type.lowercase() == "unfuse" }
                    .map { it.targetId }

                // The list of IDs to query: The Fused Character + All Component Characters
                val allRelevantSlotIds = (listOf(slotProfile.slotId) + componentSlotIds).distinct()

                // 2. Fetch Personal History for ALL components and sort by time
                val combinedHistory = allRelevantSlotIds.flatMap { sId ->
                    fetchPersonalHistory(sessionId, sId) // Assuming this returns a List<Message>
                }.sortedBy { it.timestamp?.toDate()?.time ?: 0L } // Sort chronologically so the context makes sense
                    .takeLast(10) // Take the 10 most recent across all minds

                val historyString = buildHistoryString(combinedHistory)

                // 3. RAG: Fetch Memories locally
                val queryVector = Director.getEmbedding(input, BuildConfig.OPENAI_API_KEY)

                // 4. Gather Lorebooks: Global + Fused Character + Sleeping Components
                val componentLorebooks = sessionProfile.slotRoster
                    .filter { it.slotId in componentSlotIds }
                    .flatMap { it.lorebookIds }
                val activeLorebookIds = (sessionProfile.globalLorebookIds + slotProfile.lorebookIds + componentLorebooks).distinct()

                // 5. Fetch Relevant Memories for ALL minds concurrently!
                val relevantMemoriesAsync = async {
                    allRelevantSlotIds.flatMap { sId ->
                        fetchRelevantMemories(queryVector, sId, sessionId)
                    }
                    // Note: If fetchRelevantMemories returns top 5, this will return 5 per component.
                    // The AI will get a massive memory injection of everyone involved!
                }
                val relevantLoreAsync = async { fetchRelevantLore(queryVector, activeLorebookIds) }

                val relevantMemories = relevantMemoriesAsync.await()
                val relevantLoreString = relevantLoreAsync.await()

                // Map it all back to the current acting slot (The Fused Character)
                val memoriesMap = mapOf(slotProfile.slotId to relevantMemories)

                // 5. Prepare Data for Prompt

                Log.d("ai_response", "replying as: ${slotProfile.name}")
                val sceneArea = slotProfile.lastActiveArea ?: "Unknown"
                val sceneLocation = slotProfile.lastActiveLocation ?: "Unknown"

                val isStrictSfw = sessionProfile.sfwOnly == true ||
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

                // 1. LOCAL PRESENCE: Just the names of people in the exact same room
                val namesInRoom = sessionProfile.slotRoster
                    .filter {
                        it.lastActiveArea == sceneArea &&
                                it.lastActiveLocation == sceneLocation &&
                                it.slotId != nextSlotId
                    }
                    .map { otherChar ->
                        // Grab THEIR active outfit
                        val theirOutfit = otherChar.outfits?.find { it.name.equals(otherChar.currentOutfit, ignoreCase = true) }

                        // Find their effective appearance
                        val theirAppearance = theirOutfit?.physicalDescOverride?.takeIf { it.isNotBlank() }
                            ?: otherChar.physicalDescription

                        val theirPronouns = otherChar.gender

                        val theirHeight = theirOutfit?.heightOverride?.takeIf { it.isNotBlank() }
                            ?: otherChar.height

                        val theirWeight = theirOutfit?.weightOverride?.takeIf { it.isNotBlank() }
                            ?: otherChar.weight

                        val theirEyes = theirOutfit?.eyeColorOverride?.takeIf { it.isNotBlank() }
                            ?: otherChar.eyeColor

                        val theirHair = theirOutfit?.hairColorOverride?.takeIf { it.isNotBlank() }
                            ?: otherChar.hairColor

                        // Keep it brief so we don't blow up the context window.
                        // E.g., "Goku (Appearance: Glowing blonde hair, teal eyes. Status: Active)"
                        val statusText = if (otherChar.activityStatus) "Active" else "Quietly in background"

                        "${otherChar.name} (Appearance: $theirAppearance | Pronouns: $theirPronouns | Height: $theirHeight | Weight: $theirWeight | Eye Color: $theirEyes | Hair Color: $theirHair | Status: $statusText)"
                    }.joinToString("\n")

                // 2. GLOBAL KNOWLEDGE: The dense facts for ACTIVE characters only
                val worldCompendium = sessionProfile.slotRoster
                    .filter { it.activityStatus }
                    .joinToString("\n") { profile ->
                        "- ${profile.name}: ${profile.summary}"
                    }

                val personality = if(isStrictSfw){
                    slotProfile.personality
                }else{
                    slotProfile.personality + "\n\n Only use Secrets when its appropriate. \n\n Secrets:" + slotProfile.privateDescription
                }

                val sessionSummary = buildString {
                    // 1. Always add the base description
                    append(sessionProfile.sessionDescription)

                    // 2. Add the secret description if it's allowed and exists
                    if (!isStrictSfw && sessionProfile.secretDescription!!.isNotBlank()) {
                        append("\n\n").append(sessionProfile.secretDescription)
                    }

                    // 3. Inject the Active Event with the "Flare Gun" formatting
                    if (sessionProfile.currentEvent.isNotBlank()) {
                        append("\n\n*** URGENT SCENARIO UPDATE ***\n")
                        append("The following event is happening RIGHT NOW: ${sessionProfile.currentEvent}\n")
                        append("All characters MUST react to this event immediately in their next response.")
                    }
                }

                val characterPinnedIds = slotProfile.pinnedMessages ?: emptyList()
                val pinnedMessagesStr = if (characterPinnedIds.isNotEmpty()) {
                    // Look through the session history for these IDs
                    val pinnedMsgs = sessionProfile.history.filter { msg ->
                        characterPinnedIds.contains(msg.id)
                    }

                    if (pinnedMsgs.isNotEmpty()) {
                        buildString {
                            append("CORE MEMORIES (CRITICAL CONTEXT - You MUST remember and reference these if relevant):\n")
                            pinnedMsgs.forEach { msg ->
                                val senderName = msg.displayName ?: "System"
                                append("- [$senderName]: \"${msg.text}\"\n")
                            }
                        }
                    } else ""
                } else ""

                Log.d("ai_cycle", "personality: $personality")
                // 6. Build the Roleplay Prompt
                val roleplayPrompt = PromptBuilder.buildRoleplayPrompt(
                    slotProfile = slotProfile,
                    sessionProfile = sessionProfile,
                    personality = personality,
                    modeSettings = sessionProfile.modeSettings,
                    sessionSummary = sessionSummary,
                    sceneSlotIds = sceneSlotIds,
                    condensedCharacterInfo = worldCompendium,
                    currentScene = namesInRoom,
                    chatHistory = historyString,
                    memories = memoriesMap,
                    relevantLoreString = relevantLoreString,
                    // poses = poses,
                    locationDescription = locDescription,
                    pinnedMessagesString = pinnedMessagesStr
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

                val godPrompt = buildGodModePrompt()

                val godModeEnable = sessionProfile.enabledModes.contains("god_mode")

                val godModeCheckedPrompt = if (godModeEnable){
                    roleplayPrompt + godPrompt
                } else {
                    roleplayPrompt
                }

                // Delete the modeSettings JSON check and use this:
                val vnModeEnabled = sessionProfile.enabledModes.contains("visual_novel") ||
                        sessionProfile.enabledModes.contains("vn")

                val checkedroleplayPrompt = if (slotProfile == gmProfile){
                    PromptBuilder.buildRPGLiteRules() + gmPrompt + godModeEnable
                } else {
                    PromptBuilder.buildRPGLiteRules() + playerPrompt + godModeCheckedPrompt
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

                val selectedModelId = slotProfile.modelId ?: sessionProfile.aiModel ?: "deepseek"

                val finalPrompt = if (isStrictSfw){
                    nsfwRejectionPrompt
                }else{
                    nsfwPrompt
                }

                val apiRoute = getApiRoute(selectedModelId)
                val temp = slotProfile.temperature
                val topK = slotProfile.topK
                val topP = slotProfile.topP

                val responseText = withTimeoutOrNull(60_000L) {
                    when (apiRoute) {
                        "openRouter" -> {
                            Facilitator.callMixtralApi(
                                finalPrompt,
                                BuildConfig.MIXTRAL_API_KEY,
                                selectedModelId,
                                temp, topK, topP
                            )
                        }
                        "mancer" -> {
                            // NEW ROUTE!
                            Facilitator.callMancerApi(
                                finalPrompt,
                                BuildConfig.MANCER_API_KEY,
                                selectedModelId,
                                temp, topK, topP
                            )
                        }
                        "openAI" -> {
                            Facilitator.callOpenAiApi(
                                finalPrompt,
                                BuildConfig.OPENAI_API_KEY,
                                selectedModelId,
                                temp, topK, topP
                            )
                        }
                        else -> null
                    }
                }

                if (responseText == null) {
                    Log.e("AI_CYCLE", "The AI request timed out after 60 seconds.")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "The AI took too long to respond. Please try again.", Toast.LENGTH_SHORT).show()
                        setSlotTyping(sessionId, nextSlotId, false)
                        updateButtonState(ButtonState.SEND)
                    }
                    return@launch
                }

                // 8. Parse Response
                val result = FacilitatorResponseParser.parseRoleplayAIResponse(
                    responseText,
                    nextSlotId,
                    sessionProfile.slotRoster
                )
                withContext(Dispatchers.Main) { updateButtonState(ButtonState.WAITING) }
                // bad response check
                if (result.messages == null) {
                    Log.e("AI_CYCLE", "The AI response is bad.")

                    var isPremium = false
                    val userId = FirebaseAuth.getInstance().currentUser?.uid

                    // If it's round 1, we need to know their premium status to show the right Toast
                    if (activationRound == 1 && userId != null) {
                        try {
                            val db = FirebaseFirestore.getInstance()
                            // .await() safely pauses the coroutine here on the IO thread without blocking the UI
                            val snapshot = db.collection("users").document(userId).get().await()
                            isPremium = snapshot.getBoolean("isPremium") ?: false
                        } catch (e: Exception) {
                            Log.e("Billing", "Failed to check premium status: ${e.message}")
                        }
                    }

                    // Switch to Main thread STRICTLY for UI updates
                    withContext(Dispatchers.Main) {
                        val toastMsg = if (activationRound == 1 && !isPremium) {
                            "The AI response was malformed. Your message was not charged."

                        } else {
                            "The AI response was malformed." // Premium users or subsequent rounds see this
                        }
                        Toast.makeText(this@MainActivity, toastMsg, Toast.LENGTH_SHORT).show()
                        setSlotTyping(sessionId, nextSlotId, false)
                        updateButtonState(ButtonState.SEND)
                    }

                    // ABORT the rest of the round entirely. No DB writes, no billing!
                    return@launch
                }

                if (activationRound == 1) {
                    val userId = FirebaseAuth.getInstance().currentUser?.uid
                    if (userId != null) {
                        val db = FirebaseFirestore.getInstance()
                        val userRef = db.collection("users").document(userId)
                        val today = Timestamp.now()

                        // Run this in the background (IO context) without blocking the UI
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                db.runTransaction { transaction ->
                                    val snapshot = transaction.get(userRef)

                                    // Double-check they didn't just go over limit in a race condition
                                    // (Optional, but good for security)
                                    val isPremium = snapshot.getBoolean("isPremium") ?: false
                                    if (!isPremium) {
                                        val currentCount =
                                            snapshot.getLong("dailyMessageCount") ?: 0

                                        transaction.set(
                                            userRef,
                                            mapOf(
                                                "dailyMessageCount" to currentCount + 1,
                                                "lastMessageDate" to today
                                            ),
                                            SetOptions.merge()
                                        )
                                        Log.d("Billing", "User charged for Round 1 response.")
                                    }
                                }.await()
                            } catch (e: Exception) {
                                Log.e("Billing", "Failed to update message count: ${e.message}")
                            }
                        }
                    }
                }
                Log.d("ai_response", "recieved: $result")

                // Define the variables here so the Main thread can see them!
                var pendingDiceRoll: Action? = null
                var pendingGodQuestion: String? = null

                for (action in result.actions) {
                    when (action.type) {
                        "health_change" -> handleHealthChange(action.slot, action.mod ?: 0)
                        "status_effect" -> handleStatusEffect(action.slot, action.stat ?: "", action.mod ?: 1)
                        "new_npc" -> {
                            action.npc?.let { npcData -> handleNewNPC(npcData) }
                        }
                        "roll_dice" -> pendingDiceRoll = action
                        "advance_act" -> advanceStoryAct()
                        "ask_god" -> pendingGodQuestion = action.question ?: action.stat
                    }
                }

                setSlotTyping(sessionId, nextSlotId, false)

                // 10. Update UI & Save
                withContext(Dispatchers.Main) {
                    // Stop typing
                    setSlotTyping(sessionId, nextSlotId, false)

                    // --- 12. RESOLVE GOD MODE / DICE INTERCEPTS ---
                    if (pendingDiceRoll != null) {
                        // PRIORITY 1: DICE ROLL
                        Log.d("ai_response", "Intercepted by Dice Roll!")

                        // Add Elvis operators (?:) to safely unwrap the nullables!
                        val safeStat = pendingDiceRoll!!.stat ?: "unknown"
                        val safeMod = pendingDiceRoll!!.mod ?: 0

                        handleDiceRoll(pendingDiceRoll!!.slot, safeStat, safeMod)

                        updateButtonState(ButtonState.SEND)
                        // WE DO NOT RECURSE. Control returns to User/GM.

                    } else if (pendingGodQuestion != null && pendingGodQuestion.isNotBlank()) {
                        // PRIORITY 2: NARRATIVE QUESTION
                        Log.d("ai_response", "Intercepted by God Question!")

                        val questionMessage = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            senderId = "narrator",
                            displayName = "GM Prompt",
                            text = pendingGodQuestion,
                            area = sessionProfile.slotRoster.find { it.slotId == nextSlotId }?.lastActiveArea
                                ?: "Unknown",
                            location = sessionProfile.slotRoster.find { it.slotId == nextSlotId }?.lastActiveLocation
                                ?: "Unknown",
                            delay = 0,
                            timestamp = com.google.firebase.Timestamp.now(),
                            visibility = true,
                            messageType = "godMsg" // Special tag for ChatAdapter styling!
                        )
                    }

                    // Save Session Data (Memories, etc)
                    saveSessionProfile(sessionProfile, sessionId)

                    val enrichedMessages = result.messages.map { msg ->
                        val senderSlot = sessionProfile.slotRoster.find { it.slotId == msg.senderId }

                        // 1. Check if they did an action
                        val actionText = extractActionText(msg.text)
                        var newPoseName: String? = null

                        if (actionText != null && senderSlot != null) {
                            // 2. Fetch the math for the action
                            val actionVector = getVectorForText(msg.text)

                            if (actionVector != null) {
                                // 3. We need to fetch their Wardrobe Subcollection here!
                                val senderBaseId = senderSlot.baseCharacterId!!
                                val wardrobeOutfits = fetchWardrobeForBaseId(sessionId, senderBaseId)
                                Log.d("PosingEngine", "STEP 3: Fetched ${wardrobeOutfits.size} outfits from DB for ${senderSlot.name}")

                                // 4. Find their current outfit
                                val currentOutfit = wardrobeOutfits.find { it.name == senderSlot.currentOutfit }

                                if (currentOutfit != null) {
                                    // 5. Do the math! Find the pose with the highest similarity score
                                    var bestMatchName: String? = null
                                    var highestScore = -1.0

                                    for (pose in currentOutfit.poseSlots) {
                                        if (pose.vector != null) {
                                            val score = calculateCosineSimilarity(actionVector, pose.vector!!)
                                            if (score > highestScore) {
                                                highestScore = score
                                                bestMatchName = pose.name
                                            }
                                        }
                                    }

                                    // 6. Is it close enough?
                                    if (highestScore >= 0.35) {
                                        newPoseName = bestMatchName
                                        Log.d("PosingEngine", "STEP 6: WINNER! Matched to: $newPoseName (Score: $highestScore)")
                                    } else {
                                        Log.d("PosingEngine", "STEP 6: FAILED THRESHOLD. Best was $bestMatchName but score was only $highestScore. Keeping current pose.")
                                        newPoseName = msg.pose // Fallback to whatever they are currently wearing
                                    }
                                }
                            }
                        }

                        msg.copy(
                            displayName = senderSlot?.name ?: "Bot",
                            area = senderSlot?.lastActiveArea,
                            location = senderSlot?.lastActiveLocation,
                            // IF we found a new pose, save it to the message so the UI can update!
                            pose = newPoseName ?: senderSlot?.pose,
                            visibility = true,
                            timestamp = msg.timestamp ?: com.google.firebase.Timestamp.now()
                        )
                    }
                    Log.d("ai_response", "enriched")

                    // Display Messages
                    saveMessagesSequentially(enrichedMessages, sessionId, chatId)

                    previousSpeakerId = nextSlotId

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
                // 1. Decide Speaker
                val nextSlotId = determineNextSpeakerOnTable(input, chatHistory)

                if (nextSlotId.isBlank()) {
                    withContext(Dispatchers.Main) { updateButtonState(ButtonState.SEND) }
                    return@launch
                }

                val activeSlotId = sessionProfile.userMap[userId]?.activeSlotId
                if (nextSlotId == activeSlotId){
                    withContext(Dispatchers.Main) {
                        updateButtonState(ButtonState.SEND)
                        Toast.makeText(this@MainActivity, "Control returned to you", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // 2. Setup Context
                setSlotTyping(sessionId, nextSlotId, true)

                val slotProfile = sessionProfile.slotRoster.find { it.slotId == nextSlotId }!!
                val sceneArea = slotProfile.lastActiveArea ?: "Unknown"
                val sceneLocation = slotProfile.lastActiveLocation ?: "Unknown"

                // 1. Determine if this is a Fusion (Hive Mind) and gather all relevant Slot IDs
                val componentSlotIds = slotProfile.linkedTo
                    .filter { it.type.lowercase() == "unfuse" }
                    .map { it.targetId }

                // The list of IDs to query: The Fused Character + All Component Characters
                val allRelevantSlotIds = (listOf(slotProfile.slotId) + componentSlotIds).distinct()

                // 2. Fetch Personal History for ALL components and sort by time
                val combinedHistory = allRelevantSlotIds.flatMap { sId ->
                    fetchPersonalHistory(sessionId, sId) // Assuming this returns a List<Message>
                }.sortedBy { it.timestamp?.toDate()?.time ?: 0L } // Sort chronologically so the context makes sense
                    .takeLast(10) // Take the 10 most recent across all minds

                val historyString = buildHistoryString(combinedHistory)

                // 3. RAG: Fetch Memories locally
                val queryVector = Director.getEmbedding(input, BuildConfig.OPENAI_API_KEY)

                // 4. Gather Lorebooks: Global + Fused Character + Sleeping Components
                val componentLorebooks = sessionProfile.slotRoster
                    .filter { it.slotId in componentSlotIds }
                    .flatMap { it.lorebookIds }
                val activeLorebookIds = (sessionProfile.globalLorebookIds + slotProfile.lorebookIds + componentLorebooks).distinct()

                // 5. Fetch Relevant Memories for ALL minds concurrently!
                val relevantMemoriesAsync = async {
                    allRelevantSlotIds.flatMap { sId ->
                        fetchRelevantMemories(queryVector, sId, sessionId)
                    }
                    // Note: If fetchRelevantMemories returns top 5, this will return 5 per component.
                    // The AI will get a massive memory injection of everyone involved!
                }
                val relevantLoreAsync = async { fetchRelevantLore(queryVector, activeLorebookIds) }

                val relevantMemories = relevantMemoriesAsync.await()
                val relevantLoreString = relevantLoreAsync.await()

                // Map it all back to the current acting slot (The Fused Character)
                val memoriesMap = mapOf(slotProfile.slotId to relevantMemories)

                // 3. GM-Specific Context
                val playerSlot = sessionProfile.slotRoster.find { it.profileType == "player" }
                val areaName = playerSlot?.lastActiveArea
                val locName = playerSlot?.lastActiveLocation

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

                val isStrictSfw = sessionProfile.sfwOnly == true ||
                        (slotProfile.age ?: 18) < 18

                val currentOutfit = slotProfile.outfits?.find { it.name == slotProfile.currentOutfit }
                val poses = currentOutfit?.poseSlots?.map { it.name } ?: emptyList()

                // 4. Build Final Prompt Safely
                // 4. Build Final Prompt Safely
                val lastPrompt = if (nextSlotId == "narrator") {

                    // 1. Get the GM Style from the RPG Settings
                    val rpgJson = sessionProfile.modeSettings["rpg"] as? String
                    val gmStyle = if (!rpgJson.isNullOrBlank()) {
                        try {
                            Gson().fromJson(rpgJson, ModeSettings.RPGSettings::class.java).gmStyle
                        } catch (e: Exception) { "HOST" }
                    } else { "HOST" }

                    // 2. Build Condensed Character Info (Map<slotId, summary>)
                    val condensedInfoMap = sessionProfile.slotRoster
                        .filter { it.activityStatus }
                        .associate { it.slotId to it.summary }

                    // 3. Build Locations Map (Map<"Area - Location", List<Names>>)
                    val locationsMap = sessionProfile.slotRoster
                        .groupBy { "${it.lastActiveArea} - ${it.lastActiveLocation}" }
                        .mapValues { entry -> entry.value.map { it.name } }

                    // 4. Get valid next slots & last speaker
                    val validNextSlots = sessionProfile.slotRoster.map { it.slotId }
                    val lastNonNarrator = chatHistory.lastOrNull { it.senderId != "narrator" }?.senderId

                    // 5. CALL YOUR ACTUAL PROMPT BUILDER
                    PromptBuilder.buildOnTableGMPrompt(
                        activeSlotId = activeSlotId,
                        sessionSummary = sessionProfile.sessionDescription ?: "",
                        locations = locationsMap,
                        sessionProfile = sessionProfile,
                        condensedCharacterInfo = condensedInfoMap,
                        lastNonNarratorId = lastNonNarrator,
                        validNextSlotIds = validNextSlots,
                        chatHistory = historyString,
                        gmStyle = gmStyle
                    )

                } else {
                    val vnPrompt = buildVNPrompt(slotProfile = slotProfile, sessionProfile = sessionProfile)

                    // 1. LOCAL PRESENCE: Just the names of people in the exact same room
                    val namesInRoom = sessionProfile.slotRoster
                        .filter {
                            it.lastActiveArea == sceneArea &&
                                    it.lastActiveLocation == sceneLocation &&
                                    it.slotId != nextSlotId
                        }
                        .map { otherChar ->
                            // Grab THEIR active outfit
                            val theirOutfit = otherChar.outfits?.find { it.name.equals(otherChar.currentOutfit, ignoreCase = true) }

                            // Find their effective appearance
                            val theirAppearance = theirOutfit?.physicalDescOverride?.takeIf { it.isNotBlank() }
                                ?: otherChar.physicalDescription

                            val theirPronouns = otherChar.gender

                            val theirHeight = theirOutfit?.heightOverride?.takeIf { it.isNotBlank() }
                                ?: otherChar.height

                            val theirWeight = theirOutfit?.weightOverride?.takeIf { it.isNotBlank() }
                                ?: otherChar.weight

                            val theirEyes = theirOutfit?.eyeColorOverride?.takeIf { it.isNotBlank() }
                                ?: otherChar.eyeColor

                            val theirHair = theirOutfit?.hairColorOverride?.takeIf { it.isNotBlank() }
                                ?: otherChar.hairColor

                            // Keep it brief so we don't blow up the context window.
                            // E.g., "Goku (Appearance: Glowing blonde hair, teal eyes. Status: Active)"
                            val statusText = if (otherChar.activityStatus) "Active" else "Quietly in background"

                            "${otherChar.name} (Appearance: $theirAppearance | Pronouns: $theirPronouns | Height: $theirHeight | Weight: $theirWeight | Eye Color: $theirEyes | Hair Color: $theirHair | Status: $statusText)"
                        }.joinToString("\n")

                    // 2. GLOBAL KNOWLEDGE: The dense facts for ACTIVE characters only
                    val worldCompendium = sessionProfile.slotRoster
                        .filter { it.activityStatus }
                        .joinToString("\n") { profile ->
                            "- ${profile.name}: ${profile.summary}"
                        }
                    val personality = if(isStrictSfw){
                        slotProfile.personality
                    }else{
                        slotProfile.personality + "\n\n Only use Secrets when its appropriate. \n\n Secrets:" + slotProfile.privateDescription
                    }

                    val sessionSummary = buildString {
                        // 1. Always add the base description
                        append(sessionProfile.sessionDescription)

                        // 2. Add the secret description if it's allowed and exists
                        if (!isStrictSfw && sessionProfile.secretDescription!!.isNotBlank()) {
                            append("\n\n").append(sessionProfile.secretDescription)
                        }

                        // 3. Inject the Active Event with the "Flare Gun" formatting
                        if (sessionProfile.currentEvent.isNotBlank()) {
                            append("\n\n*** URGENT SCENARIO UPDATE ***\n")
                            append("The following event is happening RIGHT NOW: ${sessionProfile.currentEvent}\n")
                            append("All characters MUST react to this event immediately in their next response.")
                        }
                    }

                    val characterPinnedIds = slotProfile.pinnedMessages ?: emptyList()
                    val pinnedMessagesStr = if (characterPinnedIds.isNotEmpty()) {
                        // Look through the session history for these IDs
                        val pinnedMsgs = sessionProfile.history.filter { msg ->
                            characterPinnedIds.contains(msg.id)
                        }

                        if (pinnedMsgs.isNotEmpty()) {
                            buildString {
                                append("CORE MEMORIES (CRITICAL CONTEXT - You MUST remember and reference these if relevant):\n")
                                pinnedMsgs.forEach { msg ->
                                    val senderName = msg.displayName ?: "System"
                                    append("- [$senderName]: \"${msg.text}\"\n")
                                }
                            }
                        } else ""
                    } else ""

                    Log.d("ai_cycle", "personality: $personality")

                    val basePrompt = PromptBuilder.buildRoleplayPrompt(
                        slotProfile = slotProfile,
                        sessionProfile = sessionProfile,
                        personality = personality,
                        modeSettings = sessionProfile.modeSettings,
                        sessionSummary = sessionSummary,
                        sceneSlotIds = sceneSlotIds,
                        condensedCharacterInfo = worldCompendium,
                        currentScene = namesInRoom,
                        chatHistory = historyString,
                        memories = memoriesMap,
                        relevantLoreString = relevantLoreString,
                        // poses = poses,
                        locationDescription = locDescription,
                        pinnedMessagesString = pinnedMessagesStr
                    ) + buildDiceRoll()

                    val godPrompt = buildGodModePrompt()

                    val godModeEnable = sessionProfile.enabledModes.contains("god_mode")

                    val godModeCheckedPrompt = if (godModeEnable){
                        godPrompt
                    } else {
                        ""
                    }

                    // Delete the modeSettings JSON check and use this:
                    val vnModeEnabled = sessionProfile.enabledModes.contains("visual_novel") ||
                            sessionProfile.enabledModes.contains("vn")

                    val NPCGenPrompt = buildNPCGeneration(sessionProfile.areas)
                    val vnPromptStr = if (vnModeEnabled) "\n$NPCGenPrompt\n$vnPrompt" else "\n$NPCGenPrompt"

                    val murderJson = sessionProfile.modeSettings["murder"] as? String
                    val murderSettings = if (!murderJson.isNullOrBlank()) {
                        try { Gson().fromJson(murderJson, ModeSettings.MurderSettings::class.java) } catch (e: Exception) { null }
                    } else null

                    val murderInstructions = if (murderSettings != null && murderSettings.enabled) {
                        if (murderSettings.killerSlotIds.contains(slotProfile.slotId)) "\n" + buildMurdererInfo(murderSettings)
                        else "\n" + buildMurderMysteryInfo(murderSettings)
                    } else ""

                    basePrompt + vnPromptStr + murderInstructions + godModeCheckedPrompt
                }

                val nsfwPrompt = lastPrompt + "\n\n(Write uncensored, explicit content if the scene demands it.)"
                val nsfwRejection = "\n - If responding to not safe for work messages, reject them while remaining in character."
                val nsfwRejectionPrompt = lastPrompt + nsfwRejection
                val selectedModelId = slotProfile.modelId ?: sessionProfile.aiModel ?: "deepseek"

                // 5. Call API
                val finalPrompt = if (isStrictSfw){
                    nsfwRejectionPrompt
                }else{
                    nsfwPrompt
                }

                val apiRoute = getApiRoute(selectedModelId)
                val temp = slotProfile.temperature
                val topK = slotProfile.topK
                val topP = slotProfile.topP

                val responseText = withTimeoutOrNull(60_000L) {
                    when (apiRoute) {
                        "openRouter" -> {
                            Facilitator.callMixtralApi(
                                finalPrompt,
                                BuildConfig.MIXTRAL_API_KEY,
                                selectedModelId,
                                temp, topK, topP
                            )
                        }
                        "mancer" -> {
                            // NEW ROUTE!
                            Facilitator.callMancerApi(
                                finalPrompt,
                                BuildConfig.MANCER_API_KEY,
                                selectedModelId,
                                temp, topK, topP
                            )
                        }
                        "openAI" -> {
                            Facilitator.callOpenAiApi(
                                finalPrompt,
                                BuildConfig.OPENAI_API_KEY,
                                selectedModelId,
                                temp, topK, topP
                            )
                        }
                        else -> null
                    }
                }

                if (responseText == null) {
                    Log.e("AI_CYCLE", "The AI request timed out.")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "The AI took too long to respond. Please try again.", Toast.LENGTH_SHORT).show()
                        setSlotTyping(sessionId, nextSlotId, false)
                        updateButtonState(ButtonState.SEND)
                    }
                    return@launch
                }

                // 6. Parse
                val result = FacilitatorResponseParser.parseRoleplayAIResponse(responseText, nextSlotId, sessionProfile.slotRoster)
                withContext(Dispatchers.Main) { updateButtonState(ButtonState.WAITING) }

                // 7. Error Guard Clause
                if (result.messages == null) {
                    Log.e("AI_CYCLE", "The AI response is bad.")
                    var isPremium = false
                    val uid = FirebaseAuth.getInstance().currentUser?.uid

                    if (activationRound == 1 && uid != null) {
                        try {
                            val snapshot = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
                            isPremium = snapshot.getBoolean("isPremium") ?: false
                        } catch (e: Exception) { Log.e("Billing", "Failed to check premium status: ${e.message}") }
                    }

                    withContext(Dispatchers.Main) {
                        val toastMsg = if (activationRound == 1 && !isPremium) "The AI response was malformed. Your message was not charged."
                        else "The AI response was malformed."
                        Toast.makeText(this@MainActivity, toastMsg, Toast.LENGTH_SHORT).show()
                        setSlotTyping(sessionId, nextSlotId, false)
                        updateButtonState(ButtonState.SEND)
                    }
                    return@launch // Abort!
                }

                // 8. Billing
                if (activationRound == 1) {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                    if (uid != null) {
                        val db = FirebaseFirestore.getInstance()
                        val userRef = db.collection("users").document(uid)
                        try {
                            db.runTransaction { transaction ->
                                val snapshot = transaction.get(userRef)
                                val isPremium = snapshot.getBoolean("isPremium") ?: false
                                if (!isPremium) {
                                    val currentCount = snapshot.getLong("dailyMessageCount") ?: 0
                                    transaction.set(userRef, mapOf("dailyMessageCount" to currentCount + 1, "lastMessageDate" to Timestamp.now()), SetOptions.merge())
                                }
                            }.await()
                        } catch (e: Exception) { Log.e("Billing", "Failed to update message count: ${e.message}") }
                    }
                }

                val baseTimeMs = System.currentTimeMillis()

                // We MUST attach the area/location so the UI knows to show it!
                val enrichedMessages = result.messages.map { msg ->
                    val senderSlot = sessionProfile.slotRoster.find { it.slotId == msg.senderId }

                    // 1. Check if they did an action
                    val actionText = extractActionText(msg.text)
                    var newPoseName: String? = null

                    if (actionText != null && senderSlot != null) {
                        // 2. Fetch the math for the action
                        val actionVector = getVectorForText(msg.text)

                        if (actionVector != null) {
                            // 3. We need to fetch their Wardrobe Subcollection here!
                            val senderBaseId = senderSlot.baseCharacterId!!
                            val wardrobeOutfits = fetchWardrobeForBaseId(sessionId, senderBaseId)
                            Log.d("PosingEngine", "STEP 3: Fetched ${wardrobeOutfits.size} outfits from DB for ${senderSlot.name}")

                            // 4. Find their current outfit
                            val currentOutfit = wardrobeOutfits.find { it.name == senderSlot.currentOutfit }

                            if (currentOutfit != null) {
                                // 5. Do the math! Find the pose with the highest similarity score
                                var bestMatchName: String? = null
                                var highestScore = -1.0

                                for (pose in currentOutfit.poseSlots) {
                                    if (pose.vector != null) {
                                        val score = calculateCosineSimilarity(actionVector, pose.vector!!)
                                        if (score > highestScore) {
                                            highestScore = score
                                            bestMatchName = pose.name
                                        }
                                    }
                                }

                                // 6. Is it close enough?
                                if (highestScore >= 0.35) {
                                    newPoseName = bestMatchName
                                    Log.d("PosingEngine", "STEP 6: WINNER! Matched to: $newPoseName (Score: $highestScore)")
                                } else {
                                    Log.d("PosingEngine", "STEP 6: FAILED THRESHOLD. Best was $bestMatchName but score was only $highestScore. Keeping current pose.")
                                    newPoseName = msg.pose // Fallback to whatever they are currently wearing
                                }
                            }
                        }
                    }

                    msg.copy(
                        displayName = senderSlot?.name ?: "Bot",
                        area = senderSlot?.lastActiveArea,
                        location = senderSlot?.lastActiveLocation,
                        pose = newPoseName ?: senderSlot?.pose,
                        visibility = true,
                        timestamp = msg.timestamp ?: com.google.firebase.Timestamp.now()
                    )
                }
                Log.d("ai_response", "enriched")

                // Display Messages
                saveMessagesSequentially(enrichedMessages, sessionId, chatId)

                // --- 10. RPG ACTION HANDLING & EXTRACTION ---
                var pendingDiceRoll: Action? = null
                var pendingGodQuestion: String? = null

                for (action in result.actions) {
                    when (action.type) {
                        "health_change" -> handleHealthChange(action.slot, action.mod ?: 0)
                        "status_effect" -> handleStatusEffect(action.slot, action.stat ?: "", action.mod ?: 1)
                        "new_npc" -> {
                            action.npc?.let { npcData -> handleNewNPC(npcData) }
                        }
                        "roll_dice" -> pendingDiceRoll = action
                        "ask_god" -> pendingGodQuestion = action.question ?: action.stat

                        // --- THE NEW GM ACTIONS ---
                        "move_character" -> {
                            if (action.area != null && action.location != null) {
                                handleCharacterMovement(action.slot, action.area, action.location)
                            }
                        }
                        "force_next_speaker" -> {
                            // This sets the global variable so your determineNextSpeakerOnTable
                            // intercepts it on the next cycle!
                            forcedNextSpeakerId = action.slot
                        }
                    }
                }

                // 11. Final Save & UI Update
                previousSpeakerId = nextSlotId

                withContext(Dispatchers.Main) {
                    setSlotTyping(sessionId, nextSlotId, false)

                    // --- 12. RESOLVE GOD MODE / DICE INTERCEPTS ---
                    if (pendingDiceRoll != null) {
                        // PRIORITY 1: DICE ROLL
                        Log.d("ai_response", "Intercepted by Dice Roll!")

                        // Add Elvis operators (?:) to safely unwrap the nullables!
                        val safeStat = pendingDiceRoll!!.stat ?: "unknown"
                        val safeMod = pendingDiceRoll!!.mod ?: 0

                        handleDiceRoll(pendingDiceRoll!!.slot, safeStat, safeMod)

                        updateButtonState(ButtonState.SEND)
                        // WE DO NOT RECURSE. Control returns to User/GM.

                    } else if (pendingGodQuestion != null && pendingGodQuestion.isNotBlank()) {
                        // PRIORITY 2: NARRATIVE QUESTION
                        Log.d("ai_response", "Intercepted by God Question!")

                        val questionMessage = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            senderId = "narrator",
                            displayName = "GM Prompt",
                            text = pendingGodQuestion,
                            area = sessionProfile.slotRoster.find { it.slotId == nextSlotId }?.lastActiveArea ?: "Unknown",
                            location = sessionProfile.slotRoster.find { it.slotId == nextSlotId }?.lastActiveLocation ?: "Unknown",
                            delay = 0,
                            timestamp = com.google.firebase.Timestamp.now(),
                            visibility = true,
                            messageType = "godMsg" // Special tag for ChatAdapter styling!
                        )

                        // Add to UI and DB
                        chatAdapter.addMessage(questionMessage)
                        SessionManager.sendMessage(chatId, sessionId, questionMessage)

                        updateButtonState(ButtonState.SEND)
                        // WE DO NOT RECURSE. Control returns to User/GM.

                    } else {
                        // PRIORITY 3: NO INTERCEPTS - KEEP THE CONVERSATION GOING
                        if (activationRound < maxActivationRounds) {
                            val sender = result.messages.firstOrNull()?.senderId

                            if (sender == "narrator") {
                                updateButtonState(ButtonState.SEND)
                            } else {
                                val updatedHistory = chatAdapter.getMessages()
                                processOnTableRPGRound("", updatedHistory)
                            }
                        } else {
                            updateButtonState(ButtonState.SEND)
                        }
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

    private fun syncLorebookCache() {
        // 1. Collect ALL possible lorebook IDs used in this session
        val allRequiredIds = mutableSetOf<String>()

        // Add Global Chat Lorebooks
        allRequiredIds.addAll(sessionProfile.globalLorebookIds)

        // Add Personal Character Lorebooks
        sessionProfile.slotRoster.forEach { slot ->
            allRequiredIds.addAll(slot.lorebookIds)
        }

        // 2. Check what we are missing
        val newIdsToFetch = allRequiredIds - cachedLorebooks.keys
        if (newIdsToFetch.isEmpty()) return // We already have everything!

        // 3. Fetch the missing books in the background
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = FirebaseFirestore.getInstance()
                // Firestore whereIn limits to 10, so we chunk them
                val chunks = newIdsToFetch.toList().chunked(10)

                for (chunk in chunks) {
                    val snap = db.collection("lorebooks")
                        .whereIn(FieldPath.documentId(), chunk)
                        .get()
                        .await()

                    for (doc in snap.documents) {
                        val book = doc.toObject(Lorebook::class.java)
                        if (book != null) {
                            cachedLorebooks[book.id] = book
                        }
                    }
                }
                Log.d("RAG_ENGINE", "Lorebook cache updated! Total books in memory: ${cachedLorebooks.size}")
            } catch (e: Exception) {
                Log.e("RAG_ENGINE", "Failed to sync lorebooks", e)
            }
        }
    }

    private fun saveSessionProfile(sessionProfile: SessionProfile, sessionId: String) {
        val db = FirebaseFirestore.getInstance()

        // Explicitly cast to List one last time before the write
        val rosterAsList = sessionProfile.slotRoster.toList()

        db.collection("sessions")
            .document(sessionId)
            .update("slotRoster", rosterAsList) // Force the Array type
            .addOnSuccessListener {
                Log.d("Firestore", "MainActivity updated roster safely")
            }
    }

    private fun interruptAILoop() {
        aiJob?.cancel()
        lifecycleScope.launch {
            if (::nextSlotId.isInitialized) {
                setSlotTyping(sessionId, nextSlotId, false)
            }
        }
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

    private fun handleCharacterMovement(slotId: String, newAreaName: String, newLocationName: String) {
        val currentSession = sessionProfile ?: return
        val sessionId = currentSession.sessionId

        if (sessionId.isBlank()) return

        // 1. VALIDATION: Check if the Area exists (ignoring upper/lower case mistakes from the AI)
        val targetArea = currentSession.areas.find {
            it.name.trim().equals(newAreaName.trim(), ignoreCase = true)
        }

        if (targetArea == null) {
            Log.w("MovementEngine", "Rejected: AI tried to move $slotId to non-existent area '$newAreaName'")
            return
        }

        // 2. VALIDATION: Check if the Location exists inside that Area
        val targetLocation = targetArea.locations.find {
            it.name.trim().equals(newLocationName.trim(), ignoreCase = true)
        }

        if (targetLocation == null) {
            Log.w("MovementEngine", "Rejected: AI tried to move $slotId to non-existent location '$newLocationName' in ${targetArea.name}")
            return
        }

        // 3. APPLY THE MOVE: Update the specific slot in the roster
        val updatedRoster = currentSession.slotRoster.map { slot ->
            if (slot.slotId == slotId) {
                slot.copy(
                    lastActiveArea = targetArea.name, // Use the official DB name, not the AI's string
                    lastActiveLocation = targetLocation.name,
                    lastSynced = com.google.firebase.Timestamp.now()
                )
            } else {
                slot
            }
        }

        // 4. SAVE TO FIRESTORE
        FirebaseFirestore.getInstance().collection("sessions").document(sessionId)
            .update("slotRoster", updatedRoster)
            .addOnSuccessListener {
                // Update the local variable so the next AI cycle sees the new location!
                sessionProfile = currentSession.copy(slotRoster = updatedRoster)
                Log.d("MovementEngine", "Successfully moved $slotId to ${targetArea.name} - ${targetLocation.name}")

                // Optional: You could trigger an adapter refresh here if your UI needs to show
                // the location change immediately outside of the chat bubbles.
            }
            .addOnFailureListener { e ->
                Log.e("MovementEngine", "Failed to save movement to database", e)
            }
    }

    // 2. Add the handler function
    fun handleNewNPC(data: NewNPCData): ChatMessage? { // <-- 1. Added return type
        if (sessionProfile.slotRoster.size >= 20) {
            Log.d("NPC_GEN", "Roster full, ignoring new NPC.")
            return null // <-- 2. Return null instead of just 'return'
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
            outfits = emptyList(),
            currentOutfit = "",
            relationships = emptyList(),
            hp = 10,
            maxHp = 10,
            pinnedMessages = mutableListOf()
        )

        // Update Session
        val updatedRoster = sessionProfile.slotRoster.toMutableList()
        updatedRoster.add(newSlot)
        sessionProfile = sessionProfile.copy(slotRoster = updatedRoster)

        // Save to Firestore
        saveSessionProfile(sessionProfile, sessionId)

        Log.d("NPC_GEN", "Created new NPC: ${data.name} in ${data.lastActiveArea}")

        // Notify UI (optional system message)
        val sysMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            senderId = "system",
            text = "New Character Entered: ${data.name}",
            timestamp = com.google.firebase.Timestamp.now(),
            visibility = true,
            messageType = "event"
        )

        // 3. Return the message to the main loop instead of sending it directly!
        return sysMsg
    }

    fun handleDiceRoll(slotId: String, statName: String, extraMod: Int) {
        val slotProfile = sessionProfile.slotRoster.find { it.slotId == slotId } ?: return

        val statValue = slotProfile.stats[statName.lowercase()] ?: 0
        val statMod = statValue / 2  // Simple half-value modifier
        val totalModifier = statMod + extraMod

        val isGodMode = sessionProfile.modeSettings.containsKey("god_mode")

        if (isGodMode) {
            // --- GOD MODE INTERCEPT ---
            val dialogView = layoutInflater.inflate(R.layout.dialog_god_mode_dice, null)
            val promptText = dialogView.findViewById<TextView>(R.id.dicePromptText)
            val manualInput = dialogView.findViewById<EditText>(R.id.manualDiceInput)
            val btnRollFairly = dialogView.findViewById<Button>(R.id.btnRollFairly)

            promptText.text = "${slotProfile.name} is rolling $statName (Modifier: +$totalModifier)"

            val dialog = AlertDialog.Builder(this)
                .setTitle("God Mode: Dice Override")
                .setView(dialogView)
                .setPositiveButton("Declare Result") { _, _ ->
                    // Grab what they typed, default to just the modifier if blank
                    val finalTotal = manualInput.text.toString().toIntOrNull() ?: totalModifier
                    val resultText = "GM DECLARES: ${slotProfile.name} rolls for $statName. Total: $finalTotal"

                    // THE FIX: Trigger the animation loop instead of sending instantly!
                    playDiceAnimationAndSend(slotId, resultText, slotProfile.lastActiveArea, slotProfile.lastActiveLocation)
                }
                .setNegativeButton("Cancel", null)
                .create()

            btnRollFairly.setOnClickListener {
                val naturalRoll = (1..20).random()
                manualInput.setText((naturalRoll + totalModifier).toString())
                Toast.makeText(this, "Rolled a $naturalRoll!", Toast.LENGTH_SHORT).show()
            }

            dialog.show()

        } else {
            // --- STANDARD RPG ROLL ---
            val d20 = (1..20).random()
            val total = d20 + totalModifier

            val rollMessage = "${slotProfile.name} rolled $d20 + $statName (mod $statMod)" +
                    (if (extraMod != 0) " + $extraMod (extra)" else "") +
                    ". Total: $total"

            // THE FIX: Trigger the animation loop instead of sending instantly!
            playDiceAnimationAndSend(slotId, rollMessage, slotProfile.lastActiveArea, slotProfile.lastActiveLocation)
        }
    }

    private fun playDiceAnimationAndSend(slotId: String, resultText: String, area: String?, location: String?) {
        lifecycleScope.launch(Dispatchers.Main) {
            val diceImageView = findViewById<ImageView>(R.id.diceImageView)
            diceImageView.visibility = View.VISIBLE

            // Extract the result from the text to show the final face
            val result = extractRollFromText(resultText)

            // 1. Play the tumbling animation instantly!
            repeat(10) {
                val r = (1..20).random()
                val resId = resources.getIdentifier("ic_d$r", "drawable", packageName)
                if (resId != 0) diceImageView.setImageResource(resId)
                delay(50)
            }

            // 2. Show the final result
            val finalId = resources.getIdentifier("ic_d$result", "drawable", packageName)
            if (finalId != 0) diceImageView.setImageResource(finalId)

            // 3. Pause so the user can read it
            delay(1000)

            // 4. Hide the dice
            diceImageView.visibility = View.GONE

            // 5. POST THE RESULT TO THE CHAT
            // (This triggers the DB, which triggers the listener to actually show the text)
            sendSystemMessageToChat(slotId, resultText, area, location)
        }
    }

    fun handleHealthChange(slotId: String, hpChange: Int) {
        var hpChanged = false
        var newHp = 0
        var charName = ""

        val updatedRoster = sessionProfile.slotRoster.map { slot ->
            if (slot.slotId == slotId) {
                hpChanged = true
                charName = slot.name
                newHp = slot.hp + hpChange
                // Optional: Clamp HP so it doesn't go below 0 or above maxHp
                if (newHp < 0) newHp = 0
                if (newHp > slot.maxHp) newHp = slot.maxHp

                slot.copy(hp = newHp)
            } else slot
        }

        if (hpChanged) {
            sessionProfile = sessionProfile.copy(slotRoster = updatedRoster)
            saveSessionProfile(sessionProfile, sessionId)
            Log.d("RPG", "$charName HP changed by $hpChange, now $newHp")
        }
    }

    fun handleStatusEffect(slotId: String, effect: String, mod: Int) {
        var effectChanged = false
        var charName = ""

        val updatedRoster = sessionProfile.slotRoster.map { slot ->
            if (slot.slotId == slotId) {
                effectChanged = true
                charName = slot.name

                // Create a mutable copy of their current effects
                val newEffects = slot.statusEffects.toMutableList()

                if (mod > 0) {
                    if (!newEffects.contains(effect)) newEffects.add(effect)
                } else {
                    newEffects.remove(effect)
                }

                slot.copy(statusEffects = newEffects)
            } else slot
        }

        if (effectChanged) {
            sessionProfile = sessionProfile.copy(slotRoster = updatedRoster)
            saveSessionProfile(sessionProfile, sessionId)
            Log.d("RPG", "$charName status effect updated: $effect (mod: $mod)")
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
        if (user == null) {
            // Fallback for safety
            onLimitCheckPassed()
            return
        }

        val userRef = FirebaseFirestore.getInstance().collection("users").document(user.uid)

        // Just do a simple get(), no transaction needed for a read!
        userRef.get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    // First time user, they pass
                    onLimitCheckPassed()
                    return@addOnSuccessListener
                }

                val isPremium = snapshot.getBoolean("isPremium") ?: false
                if (isPremium) {
                    onLimitCheckPassed()
                    return@addOnSuccessListener
                }

                // --- THE FIX: Safely retrieve the field without assuming it's a String ---
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                val today = sdf.format(java.util.Date())

                val lastDateObj = snapshot.get("lastMessageDate")
                val lastDate = when (lastDateObj) {
                    is String -> lastDateObj // It's already a String, perfect!
                    is com.google.firebase.Timestamp -> sdf.format(lastDateObj.toDate()) // It's a Timestamp, convert it!
                    else -> "" // It's missing or some other weird type, default to empty
                }
                // --------------------------------------------------------------------------

                val currentCount = if (lastDate == today) {
                    // Also safely cast the number just in case it was saved as an Int instead of a Long
                    (snapshot.get("dailyMessageCount") as? Number)?.toLong() ?: 0L
                } else {
                    0L
                }

                if (currentCount >= FREE_DAILY_LIMIT) {
                    showPaywallDialog() // They hit the limit
                } else {
                    onLimitCheckPassed() // They pass
                }
            }
            .addOnFailureListener { e ->
                Log.e("LimitCheck", "Failed to check limit, failing open", e)
                onLimitCheckPassed() // Let them play if network fails
            }
    }

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

    private fun sendSystemMessageToChat(slotId: String, text: String, area: String?, location: String?, msgType: String = "roll") {
        val chatMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            senderId = slotId,
            displayName = "Narrator",
            text = text,
            area = area ?: "Unknown",
            location = location ?: "Unknown",
            delay = 0,
            timestamp = Timestamp.now(),
            visibility = true,
            messageType = msgType
        )

        // Add to UI immediately if needed
        // chatAdapter.addMessage(chatMessage)

        // ACTUALLY SEND IT TO FIREBASE!
        SessionManager.sendMessage(chatId, sessionId, chatMessage)
    }

    private fun getApiRoute(modelId: String): String {
        return when (modelId) {
            // Mancer Models
            "mytholite", "mythomax", "weaver-alpha", "magnum-72b-v4", "goliath-120b" -> "mancer"

            // OpenAI Models
            "openai", "openai-gpt-4o" -> "openAI"

            // OpenRouter Models (Grouped to save space!)
            "deepseek", "grok4.1", "gemini", "z-ai", "acree", "nemo",
            "xiaomi", "mistral_small", "moonshotai", "mistral_med",
            "minimax", "claude" -> "openRouter"

            // Default Fallback
            else -> "openRouter"
        }
    }

    private fun sendToAI(userInput: String) {
        val messages = chatAdapter.getMessages()
        var isOnTable = false
        var isAboveTable = false
        var isOneonOne = false
        val activeSlotId = sessionProfile.userMap[userId]?.activeSlotId
        Log.d("Entry_mode", "${sessionProfile.chatMode}")
        previousSpeakerId = activeSlotId!!
        val rpgSettingsJson = sessionProfile.modeSettings["rpg"] as? String
        if (!rpgSettingsJson.isNullOrBlank() && rpgSettingsJson.trim().startsWith("{")) {
            try {
                val rpgSettings = Gson().fromJson(rpgSettingsJson, RPGSettings::class.java)
                when (rpgSettings.perspective?.lowercase()) {
                    "ontable" -> isOnTable = true
                    "abovetable" -> isAboveTable = true
                }
            } catch (e: Exception) {
                Log.e("RPG", "Failed to parse rpgSettingsJson: $rpgSettingsJson", e)
            }
        }
        if (sessionProfile.chatMode == "ONEONONE"){
            isOneonOne = true
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
                Log.d("ai_cycle", "proccessing On Table")
                activationRound = 0
                maxActivationRounds = 2
                processOnTableRPGRound(userInput, messages)
            }else if (isAboveTable){
                Log.d("ai_cycle", "proccessing Above Table")
                activationRound = 0
                maxActivationRounds = 2
                processAboveTableRound(userInput, messages)
            } else if (isOneonOne) {
                Log.d("ai_cycle", "proccessing OneonOne")
                activationRound = 0
                maxActivationRounds = 1
                processOneonOneRound(userInput, messages)
            }else{
                Log.d("ai_cycle", "proccessing normal roleplay")
                activationRound = 0
                processActivationRound(userInput, messages)
            }
        }

    }

    private fun extractActionText(message: String): String? {
        // Regex looks for anything between asterisks: *crosses arms and sighs*
        val regex = Regex("\\*(.*?)\\*")
        val match = regex.find(message)
        return match?.groupValues?.get(1) // Returns just the text inside, or null
    }

    private suspend fun getVectorForText(text: String): List<Double>? = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val apiKey = BuildConfig.OPENAI_API_KEY
            val mediaType = "application/json; charset=utf-8".toMediaType()

            val jsonBody = JSONObject().apply {
                put("model", "text-embedding-3-small")
                put("input", text)
            }

            val request = Request.Builder()
                .url("https://api.openai.com/v1/embeddings")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(jsonBody.toString().toRequestBody(mediaType))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val jsonResponse = JSONObject(responseBody)
                    val embeddingArray = jsonResponse.getJSONArray("data")
                        .getJSONObject(0)
                        .getJSONArray("embedding")

                    val vectorList = mutableListOf<Double>()
                    for (i in 0 until embeddingArray.length()) {
                        vectorList.add(embeddingArray.getDouble(i))
                    }
                    return@withContext vectorList
                }
            }
        } catch (e: Exception) {
            Log.e("Embeddings", "Failed to fetch vector for text: $text", e)
        }
        return@withContext null
    }

    private fun calculateCosineSimilarity(vecA: List<Double>, vecB: List<Double>): Double {
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in vecA.indices) {
            dotProduct += vecA[i] * vecB[i]
            normA += vecA[i] * vecA[i]
            normB += vecB[i] * vecB[i]
        }
        return if (normA == 0.0 || normB == 0.0) 0.0 else dotProduct / (Math.sqrt(normA) * Math.sqrt(normB))
    }

    private suspend fun fetchWardrobeForBaseId(sessionId: String, baseCharacterId: String): List<Outfit> = withContext(Dispatchers.IO) {
        try {
            val db = FirebaseFirestore.getInstance()

            // Query the subcollection for ALL documents matching this character's ID!
            val docs = db.collection("sessions")
                .document(sessionId)
                .collection("wardrobes")
                .whereEqualTo("baseCharacterId", baseCharacterId)
                .get()
                .await()

            if (!docs.isEmpty) {
                val outfitList = mutableListOf<Outfit>()
                val gson = Gson()

                for (doc in docs.documents) {
                    // Because we saved it inside a map ("outfit" to outfit), we extract it here
                    val outfitJson = gson.toJson(doc.get("outfit"))
                    val outfit = gson.fromJson(outfitJson, Outfit::class.java)
                    if (outfit != null) {
                        outfitList.add(outfit)
                    }
                }
                return@withContext outfitList
            }
        } catch (e: Exception) {
            Log.e("PosingEngine", "Failed to fetch wardrobe for baseId $baseCharacterId", e)
        }
        return@withContext emptyList()
    }

    suspend fun saveMessagesSequentially(messages: List<ChatMessage>, sessionId: String, chatId: String) {
        val db = FirebaseFirestore.getInstance()
        var rosterUpdated = false
        var currentRoster = sessionProfile.slotRoster.toMutableList()

        for (msg in messages) {
            Log.d("MessageCreation", "Created message: $msg")
            delay(msg.delay.toLong())
            SessionManager.sendMessage(chatId, sessionId, msg)
            cleanupTemporaryInstructions()
            distributeMessageAndMemories(msg)
        }

        // Save the updated counters back to the session if they changed
        if (rosterUpdated) {
            sessionProfile = sessionProfile.copy(slotRoster = currentRoster)
            // Use an async update so we don't slow down the chat loop
            db.collection("sessions").document(sessionId)
                .update("slotRoster", currentRoster)
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

    suspend fun setSlotTyping(sessionId: String, slotId: String, typing: Boolean) {
        val updatedRoster = sessionProfile.slotRoster.map {
            if (it.slotId == slotId) it.copy(typing = typing) else it
        }
        try {
            db.collection("sessions").document(sessionId)
                .update("slotRoster", updatedRoster.toList())
                .await() // WAIT for the server to acknowledge
        } catch (e: Exception) {
            Log.e("Typing", "Failed to set typing: ${e.message}")
        }
    }

    fun updateTypingIndicator() {
        if (!::typingIndicatorBar.isInitialized) return

        typingIndicatorBar.removeAllViews()
        val typingSlots = sessionProfile.slotRoster.filter { it.typing == true }
        if (typingSlots.isEmpty()) {
            typingIndicatorBar?.visibility = View.GONE
            return
        }
        typingIndicatorBar?.visibility = View.VISIBLE

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

    fun assignAvatarSlot(
        senderId: String,
        avatarSlotAssignments: MutableMap<Int, String?>,
        avatarSlotLocked: BooleanArray,
        slotProfiles: List<SlotProfile>
    ) {
        val maxSlots = avatarSlotAssignments.size

        // 1. STABILITY CHECK:
        val currentIndex = avatarSlotAssignments.entries.find { it.value == senderId }?.key ?: -1

        if (currentIndex != -1) {
            return
        }

        // 2. CHECK: Does sender even have poses?
        val senderProfile = slotProfiles.find { it.slotId == senderId }
        val hasPose = senderProfile?.outfits?.any { it.poseSlots.isNotEmpty() } == true
        if (!hasPose) return

        // 3. COLLECT THE "FLOATERS"
        val floatingChars = mutableListOf<String>()

        for (i in 0 until maxSlots) {
            val charId = avatarSlotAssignments[i]

            // If locked, they stay.
            if (avatarSlotLocked[i]) continue

            // Note: We don't need to check `if (charId == senderId)` here anymore
            // because we already returned at Step 1 if the sender was present.

            if (charId != null) {
                floatingChars.add(charId)
            }
        }

        // 4. BUILD THE NEW LINEUP (Newcomer gets Slot 0)
        val newLineup = ArrayDeque<String>()
        newLineup.add(senderId)
        newLineup.addAll(floatingChars)

        // 5. FILL THE UNLOCKED SLOTS
        for (i in 0 until maxSlots) {
            if (avatarSlotLocked[i]) continue

            if (newLineup.isNotEmpty()) {
                avatarSlotAssignments[i] = newLineup.removeFirst()
            } else {
                avatarSlotAssignments[i] = null
            }
        }
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

        val toRemove = mutableListOf<Int>()
        val playerSlot = slotProfiles.find { it.slotId == mySlotId }
        val targetArea = spyingArea ?: playerSlot?.lastActiveArea
        val targetLocation = spyingLocation ?: playerSlot?.lastActiveLocation

        // To prevent clones, we track who has been assigned a rendering spot
        val renderedCharacterIds = mutableSetOf<String>()

        // We need to know who is actively holding a slot on screen right now
        val activeSlotIds = avatarSlotAssignments.values.filterNotNull()

        // ==========================================
        // --- PHASE 1: FIND THE ANCHORS ---
        // ==========================================
        val anchors = mutableMapOf<Int, SlotProfile>()

        // Pre-calculate who is in what slot to resolve mutual standoffs
        val activeBaseIdToIndex = mutableMapOf<String, Int>()
        for ((index, slotId) in avatarSlotAssignments) {
            if (slotId != null) {
                val baseId = slotProfiles.find { it.slotId == slotId }?.baseCharacterId
                if (baseId != null) activeBaseIdToIndex[baseId] = index
            }
        }

        for ((index, primarySlotId) in avatarSlotAssignments) {
            val depthViews = anchorViews[index]
            depthViews.forEach { hideAvatar(it) } // Hide all layers by default

            if (primarySlotId == null) {
                toRemove.add(index)
                continue
            }

            val profile = slotProfiles.find { it.slotId == primarySlotId }
            if (profile == null || profile.baseCharacterId == null) {
                toRemove.add(index)
                continue
            }

            // Location Check
            val charArea = profile.lastActiveArea?.trim()
            val charLoc = profile.lastActiveLocation?.trim()
            val tArea = targetArea?.trim()
            val tLoc = targetLocation?.trim()

            if (!charArea.equals(tArea, true) || !charLoc.equals(tLoc, true)) {
                toRemove.add(index)
                continue
            }

            val myBaseId = profile.baseCharacterId

            // Magnet Check: Do they abandon their slot to follow someone else on screen?
            val targetToJoin = profile.linkedTo.find { link ->
                val targetBaseId = link.targetId
                val targetIndex = activeBaseIdToIndex[targetBaseId]

                if (targetIndex != null && link.type.equals("inseparable", true)) {
                    // The target is ALSO on screen right now!
                    // We need to check if it's a mutual link (they also link to me)
                    val targetProfile = slotProfiles.find { it.baseCharacterId == targetBaseId }
                    val isMutual = targetProfile?.linkedTo?.any {
                        it.type.equals("inseparable", true) && it.targetId == myBaseId
                    } == true

                    if (isMutual) {
                        // MUTUAL STANDOFF: We both want to join each other.
                        // Tie-breaker: The one with the LOWER seat index wins and becomes the Anchor.
                        index > targetIndex
                    } else {
                        // ONE-WAY: I want to join them, they don't explicitly link back. I abandon my seat.
                        true
                    }
                } else {
                    false
                }
            }

            if (targetToJoin != null) {
                toRemove.add(index) // Magnet engaged! Abandon slot to join them.
            } else {
                anchors[index] = profile // Stand ground as an Anchor!
                renderedCharacterIds.add(myBaseId) // Mark as rendered so clones don't spawn
            }
        }

        // ==========================================
        // --- PHASE 2: BUILD AND RENDER THE CLUSTERS ---
        // ==========================================
        for ((index, anchorProfile) in anchors) {
            val depthViews = anchorViews[index]
            val cluster = mutableListOf(anchorProfile)

            // Find followers in the room
            val followers = slotProfiles.filter { other ->
                !renderedCharacterIds.contains(other.baseCharacterId) &&
                        other.lastActiveArea?.trim().equals(targetArea?.trim(), true) &&
                        other.lastActiveLocation?.trim().equals(targetLocation?.trim(), true) &&
                        other.linkedTo.any { it.type.equals("inseparable", true) && it.targetId == anchorProfile.baseCharacterId }
            }

            followers.forEach { follower ->
                cluster.add(follower)
                follower.baseCharacterId?.let { renderedCharacterIds.add(it) }
            }

            // Sort by depth using the String 'trigger'
            cluster.sortBy { profile ->
                if (profile.slotId == anchorProfile.slotId) {
                    val followerLink = followers.firstNotNullOfOrNull { f -> f.linkedTo.find { it.targetId == profile.baseCharacterId } }
                    followerLink?.trigger?.toIntOrNull() ?: 4
                } else {
                    profile.linkedTo.find { it.targetId == anchorProfile.baseCharacterId }?.trigger?.toIntOrNull() ?: 3
                }
            }

            // --- RENDER USING YOUR ORIGINAL POSE LOGIC ---
            cluster.take(4).forEachIndexed { depthIndex, profileToRender ->
                val viewToUse = depthViews[depthIndex]
                val requestedPose = profileToRender.pose?.trim().orEmpty()

                if (requestedPose.equals("clear", true) || requestedPose.equals("none", true) || requestedPose.equals("hide", true)) {
                    return@forEachIndexed
                }

                val outfits = profileToRender.outfits.orEmpty()
                val currentOutfitName = profileToRender.currentOutfit?.trim().orEmpty()

                val chosenOutfit = outfits.firstOrNull { it.name.trim().equals(currentOutfitName, true) } ?: outfits.firstOrNull()
                val poseCandidates = (chosenOutfit?.poseSlots ?: outfits.flatMap { it.poseSlots }).orEmpty()

                var finalPoseSlot = poseCandidates.firstOrNull { it.name.trim().equals(requestedPose, true) }
                if (finalPoseSlot == null) finalPoseSlot = poseCandidates.firstOrNull()

                val imageUrl = finalPoseSlot?.uri
                if (!imageUrl.isNullOrBlank()) {
                    Glide.with(viewToUse.context)
                        .load(imageUrl)
                        .placeholder(R.drawable.silhouette)
                        .into(viewToUse)
                    viewToUse.visibility = View.VISIBLE
                }
            }
        }

        // Clean up abandoned slots
        toRemove.forEach { avatarSlotAssignments[it] = null }
    }

    // Helper to keep code clean
    private fun hideAvatar(view: ImageView) {
        Glide.with(this).clear(view)
        view.setImageResource(R.drawable.silhouette)
        view.visibility = View.INVISIBLE
    }

    private fun distributeMessageAndMemories(newMessage: ChatMessage) {
        val sessionId = sessionProfile.sessionId ?: return
        val db = FirebaseFirestore.getInstance()

        // 1. Find everyone in the room (The Sender + The Bystanders)
        val recipients = sessionProfile.slotRoster.filter { slot ->
            slot.lastActiveArea == newMessage.area && slot.lastActiveLocation == newMessage.location
        }

        var rosterUpdated = false
        val updatedRoster = sessionProfile.slotRoster.toMutableList()

        recipients.forEach { slot ->
            // 2. Save to Personal History (Guaranteed to only happen once per message now!)
            addToPersonalHistoryFirestore(sessionId, slot.slotId, newMessage)

            // 3. Memory Counter Logic
            if (newMessage.messageType == "message" || newMessage.messageType == "event" || newMessage.messageType.isBlank()) {
                val slotIndex = updatedRoster.indexOfFirst { it.slotId == slot.slotId }
                if (slotIndex != -1) {
                    val currentSlot = updatedRoster[slotIndex]
                    val newCount = currentSlot.memoryCounter + 1

                    if (newCount >= 10) {
                        // FIRE THE ENGINE!
                        consolidateMemory(currentSlot.slotId)
                        updatedRoster[slotIndex] = currentSlot.copy(memoryCounter = 0)
                        rosterUpdated = true
                    } else {
                        updatedRoster[slotIndex] = currentSlot.copy(memoryCounter = newCount)
                        rosterUpdated = true
                    }
                }
            }
        }

        // 4. Batch update the roster to Firestore
        if (rosterUpdated) {
            sessionProfile = sessionProfile.copy(slotRoster = updatedRoster)
            db.collection("sessions").document(sessionId)
                .update("slotRoster", updatedRoster)
        }
    }

    private fun consolidateMemory(slotId: String) {
        // Run completely in the background so it never interrupts the chat flow
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = FirebaseFirestore.getInstance()
                val slot = sessionProfile.slotRoster.find { it.slotId == slotId } ?: return@launch

                // 1. Fetch their recent personal history (Context!)
                val recentMessages = fetchPersonalHistory(sessionId, slotId).takeLast(15)
                if (recentMessages.size < 5) return@launch // Not enough context yet

                val transcript = buildHistoryString(recentMessages)

                // 2. Build the Consolidation Prompt
                val prompt = """
                    You are a cognitive memory summarizer. 
                    Below is a recent transcript from a roleplay. 
                    Your job is to extract the most important new developments, realizations, or relationship shifts specifically from the perspective of the character named "${slot.name}".
                    Any important dialogue that was given. 
                    Ignore trivial actions (like drinking or walking). Focus on lasting impact.
                    
                    Respond strictly in this JSON format:
                    {
                      "tags": ["1-3 keywords", "like", "betrayal"],
                      "text": "A dense, 1-2 sentence 3rd-person summary of what ${slot.name} learned or experienced."
                    }
                    
                    TRANSCRIPT:
                    $transcript
                """.trimIndent()

                // 3. Call the AI (Using your existing routing! You can hardcode a fast/cheap model here if you want)
                val modelToUse = sessionProfile.aiModel ?: "Grok 4.1"
                val apiRoute = getApiRoute(modelToUse)

                val rawResponse = when (apiRoute) {
                    "openRouter" -> Facilitator.callMixtralApi(prompt, BuildConfig.MIXTRAL_API_KEY, modelToUse, null, null, null)
                    "mancer" -> Facilitator.callMancerApi(prompt, BuildConfig.MANCER_API_KEY, modelToUse, null, null, null)
                    "openAI" -> Facilitator.callOpenAiApi(prompt, BuildConfig.OPENAI_API_KEY, modelToUse, null, null, null)
                    else -> null
                } ?: return@launch

                // 4. Parse the JSON
                val cleanJson = rawResponse.substringAfter("{").substringBeforeLast("}")
                val finalJson = "{$cleanJson}"

                // Temporary data class just for parsing
                data class TempMem(val tags: List<String>, val text: String)
                val parsedMemory = Gson().fromJson(finalJson, TempMem::class.java)

                // 5. Embed and Save!
                val vector = Director.getEmbedding(parsedMemory.text, BuildConfig.OPENAI_API_KEY)
                val msgIds = recentMessages.map { it.id }

                val memDoc = TaggedMemory(
                    id = UUID.randomUUID().toString(),
                    slotId = slotId,
                    tags = parsedMemory.tags,
                    text = parsedMemory.text,
                    messageIds = msgIds,
                    embedding = vector
                )

                db.collection("sessions").document(sessionId)
                    .collection("character_memories").document(memDoc.id)
                    .set(memDoc).await()

                Log.d("MemoryEngine", "Successfully consolidated memory for ${slot.name}: ${parsedMemory.text}")

            } catch (e: Exception) {
                Log.e("MemoryEngine", "Failed to consolidate memory for $slotId", e)
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

    private suspend fun deleteMemoriesFromSession(deletedMessageIds: List<String>) {
        if (deletedMessageIds.isEmpty()) return

        val db = FirebaseFirestore.getInstance()
        val memoryRef = db.collection("sessions").document(sessionId).collection("character_memories")

        for (msgId in deletedMessageIds) {
            try {
                // Find any memory that lists this deleted message ID in its array
                val snapshot = memoryRef.whereArrayContains("messageIds", msgId).get().await()

                for (doc in snapshot.documents) {
                    doc.reference.delete().await()
                }
            } catch (e: Exception) {
                Log.e("MemoryClean", "Failed to delete memory for msg: $msgId", e)
            }
        }
        Log.d("MemoryClean", "Cleaned up memories associated with messages: $deletedMessageIds")
    }

    private suspend fun updateMessageTextInFirestore(sessionId: String, updatedMessage: ChatMessage) {
        val db = FirebaseFirestore.getInstance()
        val sessionRef = db.collection("sessions").document(sessionId)

        // Safely read the history array, replace the one message, and write it back
        db.runTransaction { transaction ->
            val snapshot = transaction.get(sessionRef)
            val session = snapshot.toObject(SessionProfile::class.java) ?: return@runTransaction

            val newHistory = session.history.map { msg ->
                if (msg.id == updatedMessage.id) updatedMessage else msg
            }

            transaction.update(sessionRef, "history", newHistory)
        }.await()
    }

    private suspend fun updateMessageInSlotPersonalHistories(sessionId: String, updatedMessage: ChatMessage) {
        val db = FirebaseFirestore.getInstance()
        val slots = sessionProfile?.slotRoster ?: return

        // Loop through every character in the roster
        for (slot in slots) {
            val slotId = slot.slotId
            // NOTE: Adjust this path to match wherever you save personal histories!
            // This assumes you use a subcollection like: sessions/{sessionId}/slotHistories/{slotId}
            val historyRef = db.collection("sessions").document(sessionId)
                .collection("slotHistories").document(slotId)

            try {
                db.runTransaction { transaction ->
                    val snapshot = transaction.get(historyRef)
                    if (snapshot.exists()) {
                        // Assuming slot history is also saved as an array called 'history'
                        val currentHistory = snapshot.get("history") as? List<Map<String, Any>> ?: return@runTransaction

                        // If we find the message, we have to update it here too!
                        // (You may need to adapt this slightly depending on your exact slotHistory schema)
                    }
                }.await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update personal history for slot $slotId", e)
            }
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
        lifecycleScope.launch {
            setSlotTyping(sessionId, activeSlotId, isTyping)
        }
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

                    syncLorebookCache()

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

                // 1. Determine if the message belongs in the current room
                val playerSlot = sessionProfile.slotRoster.find { it.slotId == mySlotId }
                val currentArea = spyingArea ?: playerSlot?.lastActiveArea?.trim()
                val currentLocation = spyingLocation ?: playerSlot?.lastActiveLocation?.trim()
                val isRelevantToView = (newMessage.area == currentArea && newMessage.location == currentLocation)

                // 2. Check if this is a remote dice roll that needs animating
                val isRemoteRoll = (newMessage.messageType == "roll" && newMessage.senderId != mySlotId && isRelevantToView)

                if (isRemoteRoll) {
                    // --- REMOTE ROLL: ANIMATE FIRST, POST SECOND ---
                    lifecycleScope.launch(Dispatchers.Main) {
                        // 1. Show the dice
                        val diceImageView = findViewById<ImageView>(R.id.diceImageView)
                        diceImageView.visibility = View.VISIBLE
                        val result = extractRollFromText(newMessage.text)

                        // 2. Tumble
                        repeat(10) {
                            val r = (1..20).random()
                            val resId = resources.getIdentifier("ic_d$r", "drawable", packageName)
                            if (resId != 0) diceImageView.setImageResource(resId)
                            delay(50)
                        }

                        // 3. Show Result
                        val finalId = resources.getIdentifier("ic_d$result", "drawable", packageName)
                        if (finalId != 0) diceImageView.setImageResource(finalId)
                        delay(1000)

                        // 4. Hide Dice
                        diceImageView.visibility = View.GONE

                        // 5. FINALLY, POST THE MESSAGE TO THE CHAT!
                        chatAdapter.addMessage(newMessage)
                        chatRecycler.scrollToPosition(chatAdapter.itemCount - 1)
                    }
                } else {
                    // --- NORMAL MESSAGE (or our own roll): POST IMMEDIATELY ---
                    if (isRelevantToView) {
                        chatAdapter.addMessage(newMessage)
                        chatRecycler.scrollToPosition(chatAdapter.itemCount - 1)
                    }
                }

                if (!newMessage.outfit.isNullOrBlank() || !newMessage.pose.isNullOrBlank()) {
                    val db = FirebaseFirestore.getInstance()
                    val sessionRef = db.collection("sessions").document(sessionId)
                    val senderId = newMessage.senderId

                    sessionRef.get()
                        .continueWithTask { task ->
                            val session = task.result?.toObject(SessionProfile::class.java)
                                ?: return@continueWithTask Tasks.forException(IllegalStateException("No session"))

                            val updatedRoster = session.slotRoster.map { slot ->
                                if (slot.slotId == senderId) {
                                    var updatedSlot = slot

                                    // 1. Update Outfit if valid and changed
                                    if (!newMessage.outfit.isNullOrBlank() &&
                                        !newMessage.outfit.equals("null", ignoreCase = true) &&
                                        !newMessage.outfit.equals(slot.currentOutfit, ignoreCase = true)) {

                                        val outfitExists = slot.outfits.any { it.name.equals(newMessage.outfit, ignoreCase = true) }
                                        if (outfitExists) {
                                            updatedSlot = updatedSlot.copy(currentOutfit = newMessage.outfit)
                                        }
                                    }

                                    // 2. Update Pose
                                    if (!newMessage.pose.isNullOrBlank() &&
                                        !newMessage.pose.equals(slot.pose, ignoreCase = true)) {
                                        updatedSlot = updatedSlot.copy(pose = newMessage.pose)
                                    }

                                    updatedSlot
                                } else {
                                    slot
                                }
                            }

                            // Use toList() to prevent potential HashMap issues
                            sessionRef.update("slotRoster", updatedRoster.toList())
                        }
                        .addOnSuccessListener {
                            // Re-sync local sessionProfile to reflect the Firestore change
                            val updateSlot = sessionProfile.slotRoster.find { it.slotId == senderId }
                            Log.d("pose_debug", "Persistence success for ${updateSlot?.name}: Outfit=${newMessage.outfit}, Pose=${newMessage.pose}")
                            updateAvatarsFromSlots(sessionProfile.slotRoster, avatarSlotAssignments)
                        }
                        .addOnFailureListener { e ->
                            Log.e("pose_debug", "Failed to persist visual update: $e")
                        }
                }

                updateButtonState(ButtonState.SEND)
                lastMessageId = currentMessageId
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

    private fun openMindPalaceSearch(targetSlot: SlotProfile) {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_mind_palace, null)
        bottomSheetDialog.setContentView(view)

        val searchInput = view.findViewById<EditText>(R.id.searchQueryInput)
        val searchBtn = view.findViewById<ImageButton>(R.id.btnExecuteSearch)
        val progressBar = view.findViewById<ProgressBar>(R.id.searchProgressBar)
        val recycler = view.findViewById<RecyclerView>(R.id.searchResultsRecycler)

        val adapter = com.albirich.RealmsAI.adapters.SearchResultAdapter()
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        // Optional: Update the title so they know whose mind they are searching!
        view.findViewById<TextView>(R.id.dialogTitleTextView)?.text = "Search ${targetSlot.name}'s Codex"

        searchBtn.setOnClickListener {
            val query = searchInput.text.toString().trim()
            if (query.isEmpty()) return@setOnClickListener

            // UI Feedback
            progressBar.visibility = View.VISIBLE
            recycler.visibility = View.GONE

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // 1. Get the Vector for the Search Query
                    val queryVector = Director.getEmbedding(query, BuildConfig.OPENAI_API_KEY)
                    val rawResults = mutableListOf<SearchResult>()

                    if (queryVector.isNotEmpty()) {
                        // 2. Scan Cached Lorebooks (Instant)
                        // USE THE TARGET SLOT'S LOREBOOKS HERE
                        val activeLoreIds = (sessionProfile.globalLorebookIds + targetSlot.lorebookIds).distinct()
                        val activeEntries = activeLoreIds.flatMap { cachedLorebooks[it]?.entries ?: emptyList() }

                        for (entry in activeEntries) {
                            if (!entry.embedding.isNullOrEmpty()) {
                                val score = Director.cosineSimilarity(queryVector, entry.embedding!!)
                                // Lower the threshold slightly for manual searches so they get more hits
                                if (score > 0.30) {
                                    rawResults.add(SearchResult(entry.name, entry.content, "Lore", score))
                                }
                            }
                        }

                        // 3. Scan Character Memories (Requires Firestore read)
                        val snap = FirebaseFirestore.getInstance()
                            .collection("sessions").document(sessionId)
                            .collection("character_memories")
                            .whereEqualTo("slotId", targetSlot.slotId) // SEARCH THE TARGET'S MEMORIES!
                            .get()
                            .await()

                        for (doc in snap.documents) {
                            val mem = doc.toObject(TaggedMemory::class.java)
                            if (mem != null && mem.embedding.isNotEmpty()) {
                                val score = Director.cosineSimilarity(queryVector, mem.embedding)
                                if (score > 0.30) {
                                    val titleText = mem.tags.take(3).joinToString(", ") // Use tags as a makeshift title
                                    rawResults.add(SearchResult(titleText, mem.text, "Memory", score))
                                }
                            }
                        }
                    }

                    // 4. Sort by highest match and take the top 10
                    val topHits = rawResults.sortedByDescending { it.score }.take(10)

                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        recycler.visibility = View.VISIBLE

                        if (topHits.isEmpty()) {
                            Toast.makeText(this@MainActivity, "No relevant lore or memories found.", Toast.LENGTH_SHORT).show()
                        } else {
                            adapter.submitList(topHits)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MindPalace", "Search Failed", e)
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@MainActivity, "Search failed.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        bottomSheetDialog.show()
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

    inner class MoveMapAdapter(
        private val items: List<MoveMapItem>,
        private val onHeaderClick: (Area) -> Unit,
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
                val textView = holder.itemView as TextView
                val isExpanded = expandedAreaIds.contains(item.area.id)

                // Add a visual indicator for expansion state
                val icon = if (isExpanded) "[-] " else "[+] "
                textView.text = "$icon AREA: ${item.area.name}"

                // Trigger the toggle logic in MainActivity
                textView.setOnClickListener {
                    onHeaderClick(item.area)
                }
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

    private fun showEventsDialog() {
        val events = sessionProfile.events ?: emptyList()
        val options = mutableListOf<String>()

        // 1. Setup the Menu Options
        val hasActiveEvent = sessionProfile.currentEvent.isNotBlank()
        if (hasActiveEvent) {
            options.add("🛑 Clear Current Event")
        }

        options.add("✨ Generate Random Event (AI)")

        // Add the user's custom events
        options.addAll(events.map { it.title.ifBlank { "Unnamed Event" } })

        // 2. Build the Dialog
        AlertDialog.Builder(this)
            .setTitle(if (hasActiveEvent) "Event Active!" else "Trigger an Event")
            .setItems(options.toTypedArray()) { _, which ->
                val selection = options[which]
                when (selection) {
                    "🛑 Clear Current Event" -> clearCurrentEvent()
                    "✨ Generate Random Event (AI)" -> promptForAiEventVibe()
                    else -> {
                        // Calculate which event they tapped based on the menu offset
                        val offset = if (hasActiveEvent) 2 else 1
                        val selectedEvent = events[which - offset]
                        triggerScenarioEvent(selectedEvent)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun triggerScenarioEvent(event: ScenarioEvent) {
        val sessionId = sessionId ?: return
        val db = FirebaseFirestore.getInstance()

        // 1. FIRST GATE: Is the AI awake?
        checkServerStatusBeforeSending {

            // 2. SECOND GATE: Do they have quota left?
            checkMessageLimit {
                // 1. Update the SessionProfile so the AI gets the new system prompt
                sessionProfile = sessionProfile.copy(currentEvent = event.description)
                db.collection("sessions").document(sessionId)
                    .update("currentEvent", event.description)
                val activeSlotId = sessionProfile.userMap[userId]?.activeSlotId

                val targetArea: String?
                val targetLocation: String?

                // --- FIX STARTS HERE ---
                if (activeSlotId == "narrator") {
                    // If Director: We MUST depend on the Spy variables
                    targetArea = spyingArea
                    targetLocation = spyingLocation

                } else {
                    // If Player: Look up the slot
                    val playerSlot = sessionProfile.slotRoster.find { it.slotId == activeSlotId }

                    // Prioritize Spy location if set (allows player to 'look' into a room and trigger people there)
                    // Otherwise use physical location
                    targetArea = spyingArea ?: playerSlot?.lastActiveArea
                    targetLocation = spyingLocation ?: playerSlot?.lastActiveLocation
                }

                // 2. Inject the Narrator Message into the chat!
                if (event.narratorMessage.isNotBlank()) {
                    val narratorMsg = ChatMessage(
                        id = java.util.UUID.randomUUID().toString(),
                        area = targetArea,
                        location = targetLocation,
                        senderId = "narrator",
                        displayName = "Narrator",
                        text = event.narratorMessage,
                        timestamp = com.google.firebase.Timestamp.now()
                    )

                    // 2. Update UI State IMMEDIATELY so the app feels snappy

                    updateButtonState(ButtonState.INTERRUPT)
                    setPlayerTyping(false)
                    activationRound = 0
                    messageEditText.text.clear()
                    ignoreTextWatcher = false

                    // 3. Send to Firestore
                    val activeSessionId = sessionProfile?.sessionId
                    val activeChatId = sessionProfile?.chatId ?: ""
                    SessionManager.sendMessage(activeChatId, activeSessionId!!, narratorMsg)

                    // 4. Memory check
                    distributeMessageAndMemories(narratorMsg)

                    // 5. Trigger AI Loop
                    val userInput = narratorMsg.text
                    sendToAI(userInput)

                } else {
                    Toast.makeText(
                        this,
                        "Silent Event Triggered (No Narrator Msg)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun clearCurrentEvent() {
        val sessionId = sessionId ?: return
        sessionProfile = sessionProfile.copy(currentEvent = "")

        FirebaseFirestore.getInstance().collection("sessions").document(sessionId)
            .update("currentEvent", "")
        Toast.makeText(this, "Event Cleared. Returning to normal.", Toast.LENGTH_SHORT).show()
    }

    private fun promptForAiEventVibe() {
        val input = EditText(this).apply {
            hint = "e.g., Spooky, tavern brawl, a stranger arrives..."
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("Steer the Event")
            .setMessage("Give the AI a vibe, theme, or specific idea (optional). Leave blank for a completely random event.")
            .setView(input)
            .setPositiveButton("Generate") { _, _ ->
                val vibe = input.text.toString().trim()
                generateAiEvent(vibe)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun generateAiEvent(vibe: String = "") {
        Toast.makeText(this, "The Game Master is thinking...", Toast.LENGTH_LONG).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Build the GM Prompt
                val baseContext = sessionProfile.sessionDescription +
                        if (!sessionProfile.sfwOnly) "\n${sessionProfile.secretDescription}" else ""
                val currentEventList = sessionProfile.events
                    .map { event ->
                        // Format: "Name (Description)" or just "Name"
                        "${event.title}"
                    }

                val vibeInstruction = if (vibe.isNotBlank()) {
                    "\n\nCRITICAL INSTRUCTION: The Game Master specifically requested this theme/vibe for the event: \"$vibe\". You MUST build the event around this idea!"
                } else {
                    "\n\n CRITICAL INSTRUCTION: use the current scenario as a vibe to build an event around!"
                }

                val prompt = """
                    You are an expert Game Master for a roleplay scenario. 
                    The current scenario is: $baseContext
                    Do not make any in the current list: $currentEventList                    
                    $vibeInstruction
                 
                    Your task is to create ONE scene for the players to react to.
                    This will be after some length of time passed that you determine, could be seconds, an instant, weeks, months, a year, or whatever you need.
                    Do NOT roleplay any existing characters. Do NOT continue a conversation. 
                    
                    Respond strictly in the following JSON format:
                    {
                      "title": "Short title of the event (e.g., Demon Attack)",
                      "description": "A 1-2 sentence system instruction explaining the event for the AI.",
                      "narratorMessage": "The dramatic, descriptive action text that will be shown to the players in the chat to announce the event and set the scene, including the amount of time that has passed."
                    }
                """.trimIndent()
                Log.d("event", "generating prompt: $prompt")
                // 2. Call your AI API (Adjust this to match your OpenRouter/Facilitator implementation!)
                val modelToUse = sessionProfile.aiModel ?: "Grok 4.1"

                val apiRoute = getApiRoute(modelToUse)

                val rawResponse =
                    when (apiRoute) {
                        "openRouter" -> {
                            Facilitator.callMixtralApi(
                                prompt,
                                BuildConfig.MIXTRAL_API_KEY,
                                modelToUse,
                                null, null, null
                            )
                        }
                        "mancer" -> {
                            // NEW ROUTE!
                            Facilitator.callMancerApi(
                                prompt,
                                BuildConfig.MANCER_API_KEY,
                                modelToUse,
                                null, null, null
                            )
                        }
                        "openAI" -> {
                            Facilitator.callOpenAiApi(
                                prompt,
                                BuildConfig.OPENAI_API_KEY,
                                modelToUse,
                                null, null, null
                            )
                        }
                        else -> null
                    }

                if (rawResponse != null) {
                    // 3. Clean the response (LLMs love to wrap JSON in markdown blocks)
                    val cleanJson = rawResponse
                        .substringAfter("```json")
                        .substringBeforeLast("```")
                        .trim()

                    // 4. Parse the JSON
                    val generatedEvent = Gson().fromJson(cleanJson, ScenarioEvent::class.java)

                    // 5. Switch back to the Main Thread to update the UI and trigger the event
                    withContext(Dispatchers.Main) {
                        // Automatically give it a new UUID so it's a valid object
                        val newEvent = generatedEvent.copy(id = java.util.UUID.randomUUID().toString())

                        // Optional: Save this generated event to the session's event pool permanently
                        val updatedEvents = sessionProfile.events?.toMutableList() ?: mutableListOf()
                        updatedEvents.add(newEvent)
                        sessionProfile = sessionProfile.copy(events = updatedEvents)
                        FirebaseFirestore.getInstance().collection("sessions").document(sessionId!!)
                            .update("events", updatedEvents)

                        // 6. FIRE IT OFF!
                        triggerScenarioEvent(newEvent)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "The GM failed to come up with an idea.", Toast.LENGTH_SHORT).show()
                    }
                }

                Log.d("event", "Returned:$rawResponse")
            } catch (e: Exception) {
                Log.e("AiEventGenerator", "Error generating event", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed to parse AI Event.", Toast.LENGTH_SHORT).show()
                }
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

    sealed class MoveMapItem {
        data class Header(val area: Area) : MoveMapItem()
        data class LocationRow(val area: Area, val location: LocationSlot, val charsInRoom: List<SlotProfile>) : MoveMapItem()
    }
}
