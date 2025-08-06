package com.example.RealmsAI

import ChatAdapter
import ChatAdapter.AdapterMode
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
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.example.RealmsAI.FacilitatorResponseParser.Action
import com.example.RealmsAI.FacilitatorResponseParser.updateRelationshipLevel
import com.example.RealmsAI.FacilitatorResponseParser.updateRelationshipsFromChanges
import com.example.RealmsAI.adapters.CollectionAdapter.CharacterRowAdapter
import com.example.RealmsAI.ai.PromptBuilder.buildDiceRoll
import com.example.RealmsAI.ai.PromptBuilder.buildVNPrompt
import com.example.RealmsAI.models.ModeSettings.RPGSettings
import com.example.RealmsAI.models.ModeSettings.VNRelationship
import org.json.JSONArray
import com.google.gson.JsonObject
import com.google.gson.JsonArray

class MainActivity : AppCompatActivity() {
    companion object { private const val TAG = "MainActivity" }

   
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

        // Load session profile
        sessionProfile = Gson().fromJson(
            intent.getStringExtra("SESSION_PROFILE_JSON") ?: "{}",
            SessionProfile::class.java
        )
        Log.d("loading in", "up top laoding multiplayer check: ${sessionProfile.multiplayer}")
        sessionId = intent.getStringExtra("SESSION_ID") ?: error("SESSION_ID missing")
        initialGreeting = intent.getStringExtra("GREETING")


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
                    val deletedIds = saveEditedMessageAndDeleteFollowing(editedMessage, position)
                    deleteFromSlotPersonalHistories(sessionId, deletedIds)
                    chatAdapter.removeMessagesFrom(position)
                    chatAdapter.insertMessageAt(position, editedMessage)
                    sendToAI(editedMessage.text)
                }
            },
            onDeleteMessages = { fromPosition ->
                val targetMessage = chatAdapter.getMessageAt(fromPosition)
                val timestamp = targetMessage.timestamp
                if (timestamp != null) {
                    lifecycleScope.launch {
                        val deletedIds = deleteMessageAndFollowing(targetMessage)
                        deleteFromSlotPersonalHistories(sessionId, deletedIds)
                        chatAdapter.removeMessagesFrom(fromPosition)
                    }
                }
            },
            isMultiplayer = isMultiplayer
        )

        Log.d("loading in", "after setting up chatadapter laoding multiplayer check: ${sessionProfile.multiplayer}")
        chatRecycler.layoutManager = LinearLayoutManager(this)
        chatRecycler.adapter = chatAdapter

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

            // 1. Setup Area Spinner
            val areaNames = sessionProfile.areas.map { it.name }
            val areaAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, areaNames)
            val areaSpinner = findViewById<Spinner>(R.id.areaSpinner)
            val locationSpinner = findViewById<Spinner>(R.id.locationSpinner)
            val moveConfirm = findViewById<Button>(R.id.moveConfirm)
            areaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            areaSpinner.adapter = areaAdapter
            // 2. Update Location Spinner when area changes
            areaSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                    val selectedArea = sessionProfile.areas[position]
                    val locationNames = selectedArea.locations.map { it.name }
                    val locationAdapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, locationNames)
                    locationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    locationSpinner.adapter = locationAdapter

                    // Optional: update character icons for this location right away
                    updateLocationCharRecycler(selectedArea, selectedArea.locations.firstOrNull())
                }
                override fun onNothingSelected(parent: AdapterView<*>) {}
            }

            // 3. Update character icons when location changes
            locationSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                    val areaPosition = areaSpinner.selectedItemPosition
                    if (areaPosition < 0) return
                    val selectedArea = sessionProfile.areas[areaPosition]
                    val selectedLocation = selectedArea.locations.getOrNull(position)
                    updateLocationCharRecycler(selectedArea, selectedLocation)
                }
                override fun onNothingSelected(parent: AdapterView<*>) {}
            }

            // 4. Move button logic (example)
            moveConfirm.setOnClickListener {
                val areaPosition = areaSpinner.selectedItemPosition
                val locationPosition = locationSpinner.selectedItemPosition
                if (areaPosition < 0 || locationPosition < 0) return@setOnClickListener

                val selectedArea = sessionProfile.areas[areaPosition]
                val selectedLocation = selectedArea.locations[locationPosition]

                val activeSlotId = sessionProfile.userMap[userId]?.activeSlotId
                val slotIndex = sessionProfile.slotRoster.indexOfFirst { it.slotId == activeSlotId }
                if (slotIndex == -1) {
                    Toast.makeText(this, "Active character not found!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val slotProfile = sessionProfile.slotRoster[slotIndex]
                val updatedSlot = slotProfile.copy(
                    lastActiveArea = selectedArea.name,
                    lastActiveLocation = selectedLocation.name
                )
                val updatedSlotRoster = sessionProfile.slotRoster.toMutableList()
                updatedSlotRoster[slotIndex] = updatedSlot

                sessionProfile = sessionProfile.copy(slotRoster = updatedSlotRoster)


                // Save the session profile to Firestore (if needed)
                saveSessionProfile(sessionProfile, sessionId)
                updateLocationCharRecycler(selectedArea, selectedLocation)
                val presentSlots = sessionProfile.slotRoster.filter {
                    it.lastActiveArea == selectedArea.name && it.lastActiveLocation == selectedLocation.name
                }

                for (i in 0 until 4) {
                    val avatarView = avatarViews.getOrNull(i)
                    val slot = presentSlots.getOrNull(i)
                    if (avatarView != null && slot != null) {
                        val outfit = slot.outfits?.find { it.name == slot.currentOutfit }
                        val poseName = slot.pose // This is the pose field in your SlotProfile
                        // Find pose by name if present, otherwise fallback to first pose
                        val pose = outfit?.poseSlots?.find { it.name.equals(poseName, ignoreCase = true) }
                            ?: outfit?.poseSlots?.firstOrNull()
                        val imageUrl = pose?.uri
                        Glide.with(this)
                            .load(imageUrl)
                            .placeholder(R.drawable.default_01)
                            .into(avatarView)
                        avatarView.visibility = View.VISIBLE
                    } else if (avatarView != null) {
                        avatarView.setImageDrawable(null)
                        avatarView.visibility = View.INVISIBLE
                    }
                }
                Toast.makeText(this, "Moved to ${selectedArea.name} - ${selectedLocation.name}", Toast.LENGTH_SHORT).show()
            }
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
                    val poses = selectedSlot.outfits
                        ?.find { it.name == selectedSlot.currentOutfit }
                        ?.poseSlots ?: emptyList()
                    personaPoseRecycler.layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
                    personaPoseRecycler.adapter = PoseRowAdapter(poses) { pose ->
                        // store selected pose in a var!
                        selectedPose = pose
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>) {}
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
                // Resend same text but with visibility=false and blank text
                val resendMsg = lastMessage.copy(
                    id = "System",
                    text = "",
                    visibility = false,
                    timestamp = com.google.firebase.Timestamp.now()
                )
                SessionManager.sendMessage(chatId, sessionId, resendMsg)
                // (Optional: Trigger AI if you want this to start a new round)
                sendToAI("")
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

            sessionProfile.slotRoster.forEach { slotProfile ->
                val button = Button(this).apply {
                    text = slotProfile.name
                    // Optionally set an avatar icon via setCompoundDrawablesWithIntrinsicBounds
                    setOnClickListener { showCharacterSheet(slotProfile) }
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
                    updateButtonState(ButtonState.INTERRUPT)
                    setPlayerTyping(false)
                    sendMessageAndCallAI(text)
                    messageEditText.text.clear()
                    ignoreTextWatcher = false
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
        if (isRPG) {
            rpgToggleContainer.visibility = View.VISIBLE
        } else {
            rpgToggleContainer.visibility = View.GONE
        }
        Log.d("rpg", "is this session an rpg? $isRPG")
    }

    private fun showCharacterSheet(slotProfile: SlotProfile) {
        val content = findViewById<LinearLayout>(R.id.characterSheetContent)
        content.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val sheetView = inflater.inflate(R.layout.item_character_sheet_fantasy, content, false)

        // Avatar
        val avatar = sheetView.findViewById<ImageView>(R.id.avatarView)
        if (!slotProfile.avatarUri.isNullOrBlank()) {
            Glide.with(this).load(slotProfile.avatarUri).into(avatar)
        }

        // Text fields
        sheetView.findViewById<TextView>(R.id.nameView).text = slotProfile.name
        sheetView.findViewById<TextView>(R.id.classView).text = "Class: ${slotProfile.rpgClass}"
        sheetView.findViewById<TextView>(R.id.hpView).text = "HP: ${slotProfile.hp}/${slotProfile.maxHp}"
        sheetView.findViewById<TextView>(R.id.defenseView).text = "Defense: ${slotProfile.defense}"

        // Abilities
        sheetView.findViewById<TextView>(R.id.abilitiesView).text =
            if (slotProfile.abilities.isNotBlank()) slotProfile.abilities else "None"

        // Status Effects
        val statusList = sheetView.findViewById<LinearLayout>(R.id.statusList)
        statusList.removeAllViews()
        if (slotProfile.statusEffects.isNotEmpty()) {
            slotProfile.statusEffects.forEach { status ->
                val statusView = TextView(this)
                statusView.text = status
                statusList.addView(statusView)
            }
        } else {
            val noneView = TextView(this)
            noneView.text = "None"
            statusList.addView(noneView)
        }

        // Stats
        val statsList = sheetView.findViewById<LinearLayout>(R.id.statsList)
        statsList.removeAllViews()
        slotProfile.stats.forEach { (stat, value) ->
            val statView = TextView(this)
            statView.text = "$stat: $value"
            statsList.addView(statView)
        }

        // Equipment
        val eqList = sheetView.findViewById<LinearLayout>(R.id.equipmentList)
        eqList.removeAllViews()
        slotProfile.equipment.forEach { item ->
            val itemView = TextView(this)
            itemView.text = item
            eqList.addView(itemView)
        }

        // Summary
        sheetView.findViewById<TextView>(R.id.summaryView).text =
            if (slotProfile.summary.isNotBlank()) slotProfile.summary else "No summary provided."

        content.addView(sheetView)
    }

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

    private fun processActivationRound(input: String, chatHistory: List<ChatMessage>, retryCount: Int = 0, maxRetries: Int = 3) {
        if (retryCount > maxRetries) {
            Log.w(TAG, "Max retries reached, aborting activation round")
            activationRound = 0
            return
        }

        if (activationRound >= maxActivationRounds) return
        activationRound++

        aiJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d("ai_cycle", "actually processing normal round")
                val timeoutMillis = 45_000L
                var nextSlot: String? = null
                val didComplete = withTimeoutOrNull(timeoutMillis) {
                    withContext(Dispatchers.Main) {
                        updateButtonState(ButtonState.INTERRUPT)
                        setPlayerTyping(false)
                    }
                    // Build activation prompt
                    val activeSlotId = sessionProfile.userMap[userId]?.activeSlotId
                    val locationMap: Map<String, List<String>> = sessionProfile.areas
                        .flatMap { area ->
                            area.locations.map { loc ->
                                val charsHere = sessionProfile.slotRoster.filter {
                                    it.lastActiveArea == area.name && it.lastActiveLocation == loc.name
                                }.map { it.slotId }
                                "${area.name} - ${loc.name}" to charsHere
                            }
                        }.toMap()

                    val condensedMap: Map<String, String> = sessionProfile.slotRoster.associate {
                        it.slotId to it.summary // or whatever your condensed field is
                    }
                    val lastNonNarratorId = chatHistory.lastOrNull { it.senderId != "narrator" }?.senderId
                    var playerSlot = sessionProfile.slotRoster.find { it.slotId == activeSlotId }
                    val coLocatedSlotIds = sessionProfile.slotRoster
                        .filter {
                            it.lastActiveArea == playerSlot?.lastActiveArea &&
                                    it.lastActiveLocation == playerSlot?.lastActiveLocation &&
                                    it.slotId != lastNonNarratorId &&
                                    !it.typing
                        }
                        .map { it.slotId }
                    val memoriesMap: Map<String, List<TaggedMemory>> =
                        sessionProfile.slotRoster
                            .filter { it.slotId in coLocatedSlotIds }
                            .associate { it.slotId to it.memories.takeLast(5) }

                    val historyString = buildHistoryString(chatHistory.takeLast(10))
                    val activationPrompt = PromptBuilder.buildActivationPrompt(
                        activeSlotId = activeSlotId,
                        sessionSummary = sessionProfile.sessionDescription + sessionProfile.secretDescription,
                        areas = if (sessionProfile.areas.isNullOrEmpty()) listOf("DM's") else sessionProfile.areas.map { it.name },
                        locations = locationMap,
                        condensedCharacterInfo = condensedMap, // Map: slotId → summary
                        lastNonNarratorId = lastNonNarratorId,
                        validNextSlotIds = coLocatedSlotIds,
                        memories = memoriesMap,
                        chatHistory = historyString
                    )
                    Log.d("Ai_CYCLE", "Mode: ${sessionProfile.chatMode}, number of rounds: $maxActivationRounds")
                    Log.d("AI_CYCLE", "Activation Prompt:\n$activationPrompt")
                    Log.d("AI_response", "facilitator history: $chatHistory")
                    withContext(Dispatchers.Main) { updateButtonState(ButtonState.INTERRUPT) }

                    // Call the AI
                    Log.d("AI_CYCLE", "Prompt length: ${activationPrompt.length}")
                    var activationResponse = Facilitator.callActivationAI(
                        prompt = activationPrompt,
                        apiKey = BuildConfig.OPENAI_API_KEY
                    )
                    ensureActive()
                    Log.d("AI_response", "Raw AI Response:\n$activationResponse")

                    val isRefusal = activationResponse.trim().startsWith("I'm sorry")
                            || activationResponse.contains("I can't assist", ignoreCase = true)
                            || activationResponse.isBlank()

                    if (isRefusal) {
                        if (sessionProfile.sfwOnly == true) {
                            withContext(Dispatchers.Main) {
                                AlertDialog.Builder(this@MainActivity)
                                    .setTitle("AI Stopped")
                                    .setMessage("The AI was unable to process this round and SFW mode is enabled, so we can't use fallback. Please try another action or message.")
                                    .setPositiveButton("OK", null)
                                    .show()
                                updateButtonState(ButtonState.SEND)
                            }
                            activationRound = 0
                            return@withTimeoutOrNull
                        } else {
                            Log.d("AI_CYCLE", "OpenAI Activation AI refused. Retrying with Mixtral.")
                            activationResponse = Facilitator.callMixtralApi(
                                activationPrompt,
                                BuildConfig.MIXTRAL_API_KEY
                            )
                            ensureActive()
                            Log.d("AI_response", "Mixtral fallback AI Response:\n$activationResponse")
                            val isMixtralRefusal = activationResponse.trim().startsWith("I'm sorry")
                                    || activationResponse.contains("I can't assist", ignoreCase = true)
                                    || activationResponse.isBlank()
                            if (isMixtralRefusal) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@MainActivity, "AI refused to process activation round. Try again.", Toast.LENGTH_SHORT).show()
                                    updateButtonState(ButtonState.SEND)
                                }
                                activationRound = 0
                                return@withTimeoutOrNull
                            }
                        }
                    }
                    val result = FacilitatorResponseParser.parseActivationAIResponse(
                        activationResponse,
                        sessionProfile.slotRoster
                    )

                    var isNSFW = result.isNSFW

                    if (sessionProfile.sfwOnly == true && isNSFW) {
                        // SFW session but the AI detected NSFW intent
                        withContext(Dispatchers.Main) {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("NSFW Detected")
                                .setMessage("The AI detected that your message or action is heading into NSFW territory, but this session is SFW only. Try rewriting you previous message. If you think the AI made a mistake you can resend your previous message without changing it.")
                                .setPositiveButton("OK", null)
                                .show()
                            updateButtonState(ButtonState.SEND)
                        }
                        activationRound = 0
                        return@withTimeoutOrNull
                    }
                    nextSlot = result.nextSlot
                    val updatedSlotRoster = result.updatedRoster
                    if ( result?.newNpcs?.isNotEmpty() == true) {
                        for (npc in  result.newNpcs) {
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, "New NPC created: ${npc.name}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    Log.d("AI_memory_ids", "Memory IDs from AI: ${result.memoryIds}")
                    Log.d("NPC_JSON_DEBUG", "npcObj = ${result.updatedRoster}")

                    sessionProfile = sessionProfile.copy(slotRoster = updatedSlotRoster)
                    saveSessionProfile(sessionProfile, sessionId)

                    playerSlot = sessionProfile.slotRoster.find { it.profileType == "player" }
                    val playerArea = playerSlot?.lastActiveArea
                    val playerLocation = playerSlot?.lastActiveLocation

                    // 3. NOW do your background lookup as before
                    fun String.normalize(): String = this.trim().lowercase().replace("\\s+".toRegex(), " ")

                    val areaObj = sessionProfile.areas.find {
                        it.name.normalize() == playerArea?.normalize() || it.id == playerArea
                    }
                    val locationObj = areaObj?.locations?.find {
                        it.name.normalize() == playerLocation?.normalize() || it.id == playerLocation
                    }


                    sessionProfile.areas.forEach { area ->
                        Log.d("debug", "AREA: ${area.name}")
                        area.locations.forEach { loc ->
                            Log.d("debug", "  -> LOCATION: '${loc.name}' (uri=${loc.uri})")
                        }
                    }
                    if (!nextSlot.isNullOrBlank()) {
                        if (nextSlot == "NARRATOR") {
                            Log.d("AI_CYCLE", "Narrator activated. Calling Narrator roleplay AI.")
                            val activeSlotId = sessionProfile.userMap[userId]?.activeSlotId
                            val playerSlot = sessionProfile.slotRoster.find { it.slotId == activeSlotId }
                            val playerArea = playerSlot?.lastActiveArea
                            val playerLocation = playerSlot?.lastActiveLocation
                            val condensedCharacterInfo = sessionProfile.slotRoster
                                .filter { it.lastActiveArea == playerArea && it.lastActiveLocation == playerLocation }
                                .associate { profile ->
                                    val outfit = profile.outfits.find { it.name == profile.currentOutfit }
                                    val availablePoses = outfit?.poseSlots?.map { it.name } ?: emptyList()
                                    profile.slotId to mapOf(
                                        "summary" to profile.summary,
                                        "pose" to profile.pose,
                                        "available_poses" to availablePoses
                                    )
                                }
                            val sceneSlotIds = sessionProfile.slotRoster
                                .filter { it.lastActiveArea == playerArea && it.lastActiveLocation == playerLocation }
                                .map { it.slotId }
                                .distinct()
                            val playerSlotId = sessionProfile.userMap[userId]?.activeSlotId
                            val historyString = if (playerSlotId != null) {
                                val myPersonalHistory = fetchPersonalHistory(sessionId, playerSlotId)
                                buildHistoryString(myPersonalHistory.takeLast(10))
                            } else {
                                Log.e("AI", "playerSlotId is null! Cannot fetch personal history.")
                                ""
                            }
                            val narratorPrompt = PromptBuilder.buildNarratorPrompt(
                                sessionSummary = sessionProfile.sessionDescription + sessionProfile.secretDescription,
                                area = playerArea,
                                location = playerLocation,
                                condensedCharacterInfo = condensedCharacterInfo,
                                sceneSlotIds = sceneSlotIds,
                                sessionProfile = sessionProfile,
                                chatHistory = historyString
                            )


                            val narratorResponse =
                                if (isNSFW)
                                    Facilitator.callMixtralApi(narratorPrompt, BuildConfig.MIXTRAL_API_KEY)
                                else
                                    Facilitator.callOpenAiApi(narratorPrompt, BuildConfig.OPENAI_API_KEY)

                            val narratorResult = try {
                                FacilitatorResponseParser.parseRoleplayAIResponse(narratorResponse, "narrator", sessionProfile.slotRoster)
                            } catch (e: Exception) {
                                Log.e("AI_CYCLE", "Malformed narrator response: $narratorResponse", e)
                                null
                            }

                            if (narratorResult == null || narratorResult.messages.isEmpty()) {
                                activationRound = 0
                                return@withTimeoutOrNull
                            }

                            val narratorMessages = narratorResult.messages.map { msg ->
                                msg.copy(
                                    senderId = "narrator",
                                    area = playerSlot?.lastActiveArea,
                                    location = playerSlot?.lastActiveLocation,
                                    visibility = true
                                )
                            }


                            withContext(Dispatchers.Main) {
                                lifecycleScope.launch {
                                    saveMessagesSequentially(narratorMessages, sessionId, chatId)
                                }
                            }

                            val updatedHistory = chatAdapter.getMessages()
                            if (activationRound < maxActivationRounds && isActive) {
                                processActivationRound("", updatedHistory)
                            }
                        }else {

                            val slotId = nextSlot ?: run {
                                Log.w("AI_CYCLE", "nextSlot is null, retrying.")
                                processActivationRound(input, chatHistory, retryCount + 1)
                                withContext(Dispatchers.Main) {
                                    updateButtonState(ButtonState.INTERRUPT)
                                }
                                return@withTimeoutOrNull
                            }
                            val slotProfile = sessionProfile.slotRoster.find { it.slotId == slotId }
                            if (slotProfile == null) {
                                Log.w("AI_CYCLE", "nextSlot is invalid, retrying.")
                                processAboveTableRound(input, chatHistory, retryCount + 1)
                                withContext(Dispatchers.Main) {
                                    updateButtonState(ButtonState.SEND)
                                }
                                return@withTimeoutOrNull
                            }
                            if (slotProfile.profileType == "player") {
                                Log.d("AI_CYCLE", "It's the user's turn. Do NOT generate an AI message for player slot.")
                                activationRound = 0
                                // Optionally, trigger user input UI here.
                                withContext(Dispatchers.Main) {
                                    updateButtonState(ButtonState.INTERRUPT)
                                }
                                return@withTimeoutOrNull
                            }
                            if (slotProfile.typing == true) {
                                Log.d(TAG, "Slot is typing, retrying activation round $retryCount")
                                processActivationRound(input, chatHistory, retryCount + 1)
                                withContext(Dispatchers.Main) {
                                    updateButtonState(ButtonState.INTERRUPT)
                                }
                                return@withTimeoutOrNull
                            }

                            withContext(Dispatchers.Main) {
                                updateButtonState(ButtonState.WAITING)
                            }
                            val activeSlotId = sessionProfile.userMap[userId]?.activeSlotId
                            sessionProfile = sessionProfile.copy(
                                slotRoster = sessionProfile.slotRoster.map { slot ->
                                    if (slot.slotId == nextSlot) slot.copy(typing = true)
                                    else slot
                                }
                            )
                            // Save to Firestore so others see the typing
                            saveSessionProfile(sessionProfile, sessionId)

                            val sceneSlotIds = sessionProfile.slotRoster
                                .filter { it.lastActiveArea == playerArea && it.lastActiveLocation == playerLocation }
                                .map { it.slotId }
                                .distinct()
                            val condensedCharacterInfo = sessionProfile.slotRoster
                                .filter { it.lastActiveArea == playerArea && it.lastActiveLocation == playerLocation }
                                .associate { profile ->
                                    val outfit = profile.outfits.find { it.name == profile.currentOutfit }
                                    val availablePoses = outfit?.poseSlots?.map { it.name } ?: "DO NOT INCLUDE THIS CHARACTER IN THE POSES SECTION"
                                    profile.slotId to mapOf(
                                        "summary" to profile.summary,
                                        "pose" to profile.pose,
                                        "available_poses" to availablePoses
                                    )
                                }
                            val memoryIds = result.memoryIds ?: emptyList()
                            val nextSlotProfile = sessionProfile.slotRoster.find { it.slotId == nextSlot }
                            val relevantMemories = nextSlotProfile?.memories?.filter { memoryIds.contains(it.id) } ?: emptyList()
                            val memoriesMap = mapOf(nextSlot!! to relevantMemories)
                            val myPersonalHistory = fetchPersonalHistory(sessionId, slotId)
                            val historyString = buildHistoryString(myPersonalHistory.takeLast(10))
                            val modeSettings = sessionProfile.modeSettings

                            val roleplayPrompt = PromptBuilder.buildRoleplayPrompt(
                                slotProfile = slotProfile,
                                modeSettings = modeSettings,
                                sessionSummary = sessionProfile.sessionDescription + sessionProfile.secretDescription,
                                sceneSlotIds = sceneSlotIds,
                                condensedCharacterInfo = condensedCharacterInfo,
                                chatHistory = historyString,
                                memories = memoriesMap
                            )

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
                            Log.d("AI_response", "History: $chatHistory")
                            val nsfwinstructions =  "/n - Be descriptive and explicit. The more sexual and erotic the better"

                            val nsfwPrompt = vnCheckedPrompt + nsfwinstructions

                            // Switch models as needed
                            val roleplayResponse =
                                if (isNSFW)
                                    Facilitator.callMixtralApi(nsfwPrompt, BuildConfig.MIXTRAL_API_KEY)
                                else
                                    Facilitator.callOpenAiApi(vnCheckedPrompt, BuildConfig.OPENAI_API_KEY)


                            ensureActive()
                            Log.d("ai_cycle", "Roleplay Prompt is: $vnCheckedPrompt")

                            // Parse AI output to ChatMessage list
                            val roleplayResult = try {
                                Log.e("ai_cycle", "sending this to parser $roleplayPrompt")
                                FacilitatorResponseParser.parseRoleplayAIResponse(roleplayResponse, nextSlot!!, sessionProfile.slotRoster)
                            } catch (e: Exception) {
                                Log.e("AI_CYCLE", "Malformed roleplay response: $roleplayResponse", e)
                                if (activeSlotId != null) {
                                    setSlotTyping(sessionId, nextSlot!!, false)
                                }
                                null
                            }

                            val speakerSlotId = roleplayResult?.messages?.firstOrNull()?.senderId ?: nextSlot
                            val speakerSlot = sessionProfile.slotRoster.find { it.slotId == speakerSlotId }
                            if (speakerSlot != null && roleplayResult?.relationshipChanges?.isNotEmpty() == true) {
                                val fromId = speakerSlot.baseCharacterId

                                // 1. Get the acting character's relationship map (vnRelationships)
                                val relMap = speakerSlot.vnRelationships

                                // 2. Apply all relationship changes to this map
                                updateRelationshipsFromChanges(relMap, roleplayResult.relationshipChanges)

                                // 3. Enforce monogamy if enabled
                                val vnSettingsJson = sessionProfile.modeSettings["vn"] as? String
                                val vnSettings = if (!vnSettingsJson.isNullOrBlank())
                                    Gson().fromJson(vnSettingsJson, ModeSettings.VNSettings::class.java)
                                else
                                    ModeSettings.VNSettings()

                                if (vnSettings.monogamyEnabled && vnSettings.monogamyLevel != null) {
                                    enforceMonogamy(
                                        relMap,
                                        vnSettings.monogamyLevel!!
                                    )
                                }

                                // 4. Trigger Jealousy
                                if (vnSettings.jealousyEnabled) {
                                    val jealousyPenalty = 1 // Or whatever penalty you want
                                    val toId = roleplayResult.relationshipChanges.first().toId // Assumes one per turn, adjust as needed

                                    // Lower relationship points for all other characters in the same location
                                    for (otherSlot in sessionProfile.slotRoster) {
                                        if (otherSlot.baseCharacterId != fromId &&
                                            otherSlot.lastActiveLocation == speakerSlot.lastActiveLocation) {

                                            val relMap = otherSlot.vnRelationships
                                            val rel = relMap[toId]
                                            if (rel != null) {
                                                rel.points -= jealousyPenalty
                                                updateRelationshipLevel(rel)
                                            }
                                        }
                                    }
                                }

                                // 5. Save back to sessionProfile
                                sessionProfile.slotRoster = sessionProfile.slotRoster.map { slot ->
                                    if (slot.slotId == speakerSlot.slotId) speakerSlot else slot
                                }
                            }

                            val filteredMessages = roleplayResult?.messages?.map { msg ->
                                val timestamp = msg.timestamp ?: com.google.firebase.Timestamp.now()
                                val senderSlotProfile = sessionProfile.slotRoster.find { it.slotId == nextSlot }
                                val cleanedPose = msg.pose?.filterValues { !it.isNullOrBlank() } // optional, if you want to clean out blanks
                                msg.copy(
                                    area = senderSlotProfile?.lastActiveArea,
                                    location = senderSlotProfile?.lastActiveLocation,
                                    visibility = true,
                                    timestamp = timestamp,
                                    pose = cleanedPose
                                )
                            }
                            Log.d("AI_response", "filtered messages: $filteredMessages")

                            val newMemory = roleplayResult?.newMemory
                            if (newMemory != null) {
                                // Find the area/location of the sender (assume first message in batch is the one that triggers the memory)
                                val mainMessage = roleplayResult.messages.firstOrNull()
                                val speakerSlotId = mainMessage?.senderId

                                val speakerSlot = sessionProfile.slotRoster.find { it.slotId == speakerSlotId }
                                val area = speakerSlot?.lastActiveArea
                                val location = speakerSlot?.lastActiveLocation

                                // Find all slotIds in that location
                                val presentSlots = sessionProfile.slotRoster.filter {
                                    it.lastActiveArea == area && it.lastActiveLocation == location
                                }

                                // For each, add the new memory
                                val updatedRoster = sessionProfile.slotRoster.map { slot ->
                                    if (presentSlots.any { it.slotId == slot.slotId }) {
                                        // Copy old memories and add new one
                                        val updatedMemories = slot.memories.toMutableList()
                                        updatedMemories.add(
                                            TaggedMemory(
                                                id = UUID.randomUUID().toString(),
                                                tags = newMemory.tags,
                                                text = newMemory.text,
                                                nsfw = newMemory.nsfw,
                                                messageIds = mainMessage?.let { listOf(it.id) } ?: emptyList()
                                            )
                                        )
                                        slot.copy(memories = updatedMemories)
                                    } else {
                                        slot
                                    }
                                }

                                // Update the sessionProfile with the new slotRoster
                                sessionProfile = sessionProfile.copy(slotRoster = updatedRoster)
                            }

                            withContext(Dispatchers.Main) {
                                saveMessagesSequentially(filteredMessages!!, sessionId, chatId)

                                if (!nextSlot.isNullOrBlank()) {
                                    setSlotTyping(sessionId, nextSlot!!, false)
                                    saveSessionProfile(sessionProfile, sessionId)
                                }

                                val updatedHistory = chatAdapter.getMessages()
                                if (activationRound < maxActivationRounds && isActive) {
                                    processActivationRound("", updatedHistory)
                                }
                            }

                        }
                    } else {
                        activationRound = 0
                        withContext(Dispatchers.Main) { updateButtonState(ButtonState.SEND) }
                    }
                    // --------------------------------------------------------
                    true // Mark that completion was successful
                }

                if (didComplete == null) {
                    Log.w("AI_CYCLE", "AI response timed out after 30s, ending activation loop.")
                    activationRound = 0

                    withContext(Dispatchers.Main) {
                        updateButtonState(ButtonState.SEND)
                        Toast.makeText(this@MainActivity, "AI timed out.", Toast.LENGTH_SHORT).show()
                    }

                    // Optional: reset typing flag for the slot that was active, if known
                    val timedOutSlotId = nextSlot
                    if (timedOutSlotId != null) {
                        sessionProfile = sessionProfile.copy(
                            slotRoster = sessionProfile.slotRoster.map { slot ->
                                if (slot.slotId == timedOutSlotId) slot.copy(typing = false)
                                else slot
                            }
                        )
                        if (nextSlot != null) {
                            setSlotTyping(sessionId, nextSlot!!, false)
                        }
                        saveSessionProfile(sessionProfile, sessionId)
                    }
                }
            } catch (e: CancellationException) {
                Log.i(TAG, "AI job cancelled cleanly.")
                withContext(Dispatchers.Main) { updateButtonState(ButtonState.INTERRUPT) }
            } catch (e: Exception) {
                Log.e(TAG, "AI call failed", e)
                withContext(Dispatchers.Main) { updateButtonState(ButtonState.INTERRUPT) }
            }
        }

    }

    private fun processAboveTableRound(input: String, chatHistory: List<ChatMessage>, retryCount: Int = 0, maxRetries: Int = 4) {
        if (retryCount > maxRetries) {
            Log.w(TAG, "Max retries reached, aborting activation round")
            activationRound = 0
            return
        }

        if (activationRound >= maxActivationRounds) return
        activationRound++

        aiJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d("ai_cycle", "actually processing abovetable round")
                val timeoutMillis = 45_000L
                var nextSlot: String? = null
                val didComplete = withTimeoutOrNull(timeoutMillis) {
                    withContext(Dispatchers.Main) {
                        updateButtonState(ButtonState.INTERRUPT)
                        setPlayerTyping(false)
                    }
                    // Build activation prompt
                    val activeSlotId = sessionProfile.userMap[userId]?.activeSlotId
                    val locationMap: Map<String, List<String>> = sessionProfile.areas
                        .flatMap { area ->
                            area.locations.map { loc ->
                                val charsHere = sessionProfile.slotRoster.filter {
                                    it.lastActiveArea == area.name && it.lastActiveLocation == loc.name
                                }.map { it.slotId }
                                "${area.name} - ${loc.name}" to charsHere
                            }
                        }.toMap()

                    val condensedMap: Map<String, String> = sessionProfile.slotRoster.associate {
                        it.slotId to it.summary // or whatever your condensed field is
                    }
                    val lastNonNarratorId = chatHistory.lastOrNull { it.senderId != "narrator" }?.senderId
                    var playerSlot = sessionProfile.slotRoster.find { it.slotId == activeSlotId }
                    val coLocatedSlotIds = sessionProfile.slotRoster
                        .filter {
                            it.lastActiveArea == playerSlot?.lastActiveArea &&
                                    it.lastActiveLocation == playerSlot?.lastActiveLocation &&
                                    !it.typing
                        }
                        .map { it.slotId }
                    val memoriesMap: Map<String, List<TaggedMemory>> =
                        sessionProfile.slotRoster
                            .filter { it.slotId in coLocatedSlotIds }
                            .associate { it.slotId to it.memories.takeLast(5) }

                    val historyString = buildHistoryString(chatHistory.takeLast(10))
                    var activationPrompt = PromptBuilder.buildActivationPrompt(
                        activeSlotId = activeSlotId,
                        sessionSummary = sessionProfile.sessionDescription + sessionProfile.secretDescription,
                        areas = if (sessionProfile.areas.isNullOrEmpty()) listOf("DM's") else sessionProfile.areas.map { it.name },
                        locations = locationMap,
                        condensedCharacterInfo = condensedMap,
                        lastNonNarratorId = lastNonNarratorId,
                        validNextSlotIds = coLocatedSlotIds,
                        memories = memoriesMap,
                        chatHistory = historyString
                    )
                    activationPrompt += "\nNever choose narrator as next_slot."
                    Log.d("Ai_CYCLE", "Mode: ${sessionProfile.chatMode}, number of rounds: $maxActivationRounds")
                    Log.d("AI_CYCLE", "Activation Prompt:\n$activationPrompt")
                    Log.d("AI_response", "facilitator history: $chatHistory")
                    withContext(Dispatchers.Main) { updateButtonState(ButtonState.INTERRUPT) }

                    // Call the AI
                    Log.d("AI_CYCLE", "Prompt length: ${activationPrompt.length}")
                    var activationResponse = Facilitator.callActivationAI(
                        prompt = activationPrompt,
                        apiKey = BuildConfig.OPENAI_API_KEY
                    )
                    ensureActive()
                    Log.d("AI_response", "Raw AI Response:\n$activationResponse")

                    val isRefusal = activationResponse.trim().startsWith("I'm sorry")
                            || activationResponse.contains("I can't assist", ignoreCase = true)
                            || activationResponse.isBlank()

                    if (isRefusal) {
                        if (sessionProfile.sfwOnly == true) {
                            withContext(Dispatchers.Main) {
                                AlertDialog.Builder(this@MainActivity)
                                    .setTitle("AI Stopped")
                                    .setMessage("The AI was unable to process this round and SFW mode is enabled, so we can't use fallback. Please try another action or message.")
                                    .setPositiveButton("OK", null)
                                    .show()
                                updateButtonState(ButtonState.SEND)
                            }
                            activationRound = 0
                            return@withTimeoutOrNull
                        } else {
                            Log.d("AI_CYCLE", "OpenAI Activation AI refused. Retrying with Mixtral.")
                            activationResponse = Facilitator.callMixtralApi(
                                activationPrompt,
                                BuildConfig.MIXTRAL_API_KEY
                            )
                            ensureActive()
                            Log.d("AI_response", "Mixtral fallback AI Response:\n$activationResponse")
                            val isMixtralRefusal = activationResponse.trim().startsWith("I'm sorry")
                                    || activationResponse.contains("I can't assist", ignoreCase = true)
                                    || activationResponse.isBlank()
                            if (isMixtralRefusal) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@MainActivity, "AI refused to process activation round. Try again.", Toast.LENGTH_SHORT).show()
                                    updateButtonState(ButtonState.SEND)
                                }
                                activationRound = 0
                                return@withTimeoutOrNull
                            }
                        }
                    }
                    val result = FacilitatorResponseParser.parseActivationAIResponse(
                        activationResponse,
                        sessionProfile.slotRoster
                    )

                    var isNSFW = result.isNSFW

                    if (sessionProfile.sfwOnly == true && isNSFW) {
                        // SFW session but the AI detected NSFW intent
                        withContext(Dispatchers.Main) {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("NSFW Detected")
                                .setMessage("The AI detected that your message or action is heading into NSFW territory, but this session is SFW only. Try rewriting you previous message. If you think the AI made a mistake you can resend your previous message without changing it.")
                                .setPositiveButton("OK", null)
                                .show()
                            updateButtonState(ButtonState.SEND)
                        }
                        activationRound = 0
                        return@withTimeoutOrNull
                    }
                    nextSlot = result.nextSlot
                    val updatedSlotRoster = result.updatedRoster
                    if ( result?.newNpcs?.isNotEmpty() == true) {
                        for (npc in  result.newNpcs) {
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, "New NPC created: ${npc.name}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    Log.d("AI_memory_ids", "Memory IDs from AI: ${result.memoryIds}")
                    Log.d("NPC_JSON_DEBUG", "npcObj = ${result.updatedRoster}")

                    sessionProfile = sessionProfile.copy(slotRoster = updatedSlotRoster)
                    saveSessionProfile(sessionProfile, sessionId)

                    playerSlot = sessionProfile.slotRoster.find { it.profileType == "player" }
                    val playerArea = playerSlot?.lastActiveArea
                    val playerLocation = playerSlot?.lastActiveLocation

                    // 3. NOW do your background lookup as before
                    fun String.normalize(): String = this.trim().lowercase().replace("\\s+".toRegex(), " ")

                    val areaObj = sessionProfile.areas.find {
                        it.name.normalize() == playerArea?.normalize() || it.id == playerArea
                    }
                    val locationObj = areaObj?.locations?.find {
                        it.name.normalize() == playerLocation?.normalize() || it.id == playerLocation
                    }


                    sessionProfile.areas.forEach { area ->
                        Log.d("debug", "AREA: ${area.name}")
                        area.locations.forEach { loc ->
                            Log.d("debug", "  -> LOCATION: '${loc.name}' (uri=${loc.uri})")
                        }
                    }
                    if (!nextSlot.isNullOrBlank()) {
                        if (nextSlot == "NARRATOR"){
                            Log.w("AI_CYCLE", "nextSlot is narrator, retrying.")
                            processAboveTableRound(input, chatHistory, retryCount + 1)
                            return@withTimeoutOrNull
                        }else {
                            val slotId = nextSlot ?: run {
                                Log.w("AI_CYCLE", "nextSlot is null, retrying.")
                                processAboveTableRound(input, chatHistory, retryCount + 1)
                                return@withTimeoutOrNull
                            }
                            val slotProfile = sessionProfile.slotRoster.find { it.slotId == slotId }
                            if (slotProfile == null) {
                                Log.d(TAG, "Slot is invalid, retrying activation round $retryCount")
                                processAboveTableRound(input, chatHistory, retryCount + 1)
                                return@withTimeoutOrNull  // <-- or return@launch depending on your coroutine builder
                            }
                            if (slotProfile.profileType == "player") {
                                Log.d("AI_CYCLE", "It's the user's turn. Do NOT generate an AI message for player slot.")
                                activationRound = 0
                                // Optionally, trigger user input UI here.
                                withContext(Dispatchers.Main) {
                                    updateButtonState(ButtonState.SEND)
                                }
                                return@withTimeoutOrNull
                            }
                            if (slotProfile.typing == true) {
                                Log.d(TAG, "Slot is typing, retrying activation round $retryCount")
                                processAboveTableRound(input, chatHistory, retryCount + 1)
                                return@withTimeoutOrNull
                            }

                            withContext(Dispatchers.Main) {
                                updateButtonState(ButtonState.WAITING)
                            }
                            val activeSlotId = sessionProfile.userMap[userId]?.activeSlotId
                            sessionProfile = sessionProfile.copy(
                                slotRoster = sessionProfile.slotRoster.map { slot ->
                                    if (slot.slotId == nextSlot) slot.copy(typing = true)
                                    else slot
                                }
                            )
                            // Save to Firestore so others see the typing
                            saveSessionProfile(sessionProfile, sessionId)

                            val sceneSlotIds = sessionProfile.slotRoster
                                .filter { it.lastActiveArea == playerArea && it.lastActiveLocation == playerLocation }
                                .map { it.slotId }
                                .distinct()
                            val condensedCharacterInfo = sessionProfile.slotRoster
                                .filter { it.lastActiveArea == playerArea && it.lastActiveLocation == playerLocation }
                                .associate { profile ->
                                    val outfit = profile.outfits.find { it.name == profile.currentOutfit }
                                    val availablePoses = outfit?.poseSlots?.map { it.name } ?: "DO NOT INCLUDE THIS CHARACTER IN THE POSES SECTION"
                                    profile.slotId to mapOf(
                                        "summary" to profile.summary,
                                        "pose" to profile.pose,
                                        "available_poses" to availablePoses
                                    )
                                }
                            val modeSettings = sessionProfile.modeSettings
                            val memoryIds = result.memoryIds ?: emptyList()
                            val nextSlotProfile = sessionProfile.slotRoster.find { it.slotId == nextSlot }
                            val relevantMemories = nextSlotProfile?.memories?.filter { memoryIds.contains(it.id) } ?: emptyList()
                            val memoriesMap = mapOf(nextSlot!! to relevantMemories)
                            val myPersonalHistory = fetchPersonalHistory(sessionId, slotId)
                            val historyString = buildHistoryString(myPersonalHistory.takeLast(10))


                            val roleplayPrompt = PromptBuilder.buildRoleplayPrompt(
                                slotProfile = slotProfile,
                                modeSettings = modeSettings,
                                sessionSummary = sessionProfile.sessionDescription + sessionProfile.secretDescription,
                                sceneSlotIds = sceneSlotIds,
                                condensedCharacterInfo = condensedCharacterInfo,
                                chatHistory = historyString,
                                memories = memoriesMap
                            )

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

                            val vnCheckedPrompt = if (vnModeEnabled) {
                                roleplayPrompt + "\n\n" + vnPrompt
                            } else {
                                roleplayPrompt
                            }

                            val nsfwinstructions =  "/n - Be descriptive and explicit. The more sexual and erotic the better"

                            val nsfwPrompt = vnCheckedPrompt + nsfwinstructions

                            Log.d("AI_response", "History: $chatHistory")
                            val rpgPrompt = if (slotProfile.hiddenRoles == "GM"){
                                if (isNSFW){
                                    nsfwPrompt + gmPrompt + PromptBuilder.buildRPGLiteRules()
                                }else {
                                    vnCheckedPrompt + gmPrompt + PromptBuilder.buildRPGLiteRules()
                                }
                            }else{
                                if (isNSFW){
                                    nsfwPrompt + playerPrompt + PromptBuilder.buildRPGLiteRules()
                                }else {
                                    vnCheckedPrompt + playerPrompt + PromptBuilder.buildRPGLiteRules()
                                }
                            }

                            // Switch models as needed
                            val roleplayResponse =
                                if (isNSFW)
                                    Facilitator.callMixtralApi(rpgPrompt, BuildConfig.MIXTRAL_API_KEY)
                                else
                                    Facilitator.callOpenAiApi(rpgPrompt, BuildConfig.OPENAI_API_KEY)


                            ensureActive()
                            Log.d("ai_cycle", "RPG Prompt is: $rpgPrompt")
                            Log.d("AI_response", "Roleplay Response:\n$roleplayResponse")

                            // Parse AI output to ChatMessage list
                            val roleplayResult = try {
                                FacilitatorResponseParser.parseRoleplayAIResponse(roleplayResponse, nextSlot!!, sessionProfile.slotRoster)
                            } catch (e: Exception) {
                                Log.e("AI_CYCLE", "Malformed roleplay response: $roleplayResponse", e)
                                if (activeSlotId != null) {
                                    setSlotTyping(sessionId, nextSlot!!, false)
                                }
                                null
                            }

                            if (roleplayResult == null || roleplayResult.messages.isEmpty()) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@MainActivity, "AI sent a broken message. Please try again.", Toast.LENGTH_SHORT).show()
                                    updateButtonState(ButtonState.SEND)
                                    if (activeSlotId != null) {
                                        setSlotTyping(sessionId, nextSlot!!, false)
                                    }
                                }
                                activationRound = 0
                                return@withTimeoutOrNull
                            }

                            val speakerSlotId = roleplayResult?.messages?.firstOrNull()?.senderId ?: nextSlot
                            val speakerSlot = sessionProfile.slotRoster.find { it.slotId == speakerSlotId }
                            if (speakerSlot != null && roleplayResult?.relationshipChanges?.isNotEmpty() == true) {
                                val fromId = speakerSlot.baseCharacterId

                                // 1. Get the acting character's relationship map (vnRelationships)
                                val relMap = speakerSlot.vnRelationships

                                // 2. Apply all relationship changes to this map
                                updateRelationshipsFromChanges(relMap, roleplayResult.relationshipChanges)

                                // 3. Enforce monogamy if enabled
                                val vnSettingsJson = sessionProfile.modeSettings["vn"] as? String
                                val vnSettings = if (!vnSettingsJson.isNullOrBlank())
                                    Gson().fromJson(vnSettingsJson, ModeSettings.VNSettings::class.java)
                                else
                                    ModeSettings.VNSettings()

                                if (vnSettings.monogamyEnabled && vnSettings.monogamyLevel != null) {
                                    enforceMonogamy(
                                        relMap,
                                        vnSettings.monogamyLevel!!
                                    )
                                }

                                // 4. Trigger Jealousy
                                if (vnSettings.jealousyEnabled) {
                                    val jealousyPenalty = 1 // Or whatever penalty you want
                                    val toId = roleplayResult.relationshipChanges.first().toId // Assumes one per turn, adjust as needed

                                    // Lower relationship points for all other characters in the same location
                                    for (otherSlot in sessionProfile.slotRoster) {
                                        if (otherSlot.baseCharacterId != fromId &&
                                            otherSlot.lastActiveLocation == speakerSlot.lastActiveLocation) {

                                            val relMap = otherSlot.vnRelationships
                                            val rel = relMap[toId]
                                            if (rel != null) {
                                                rel.points -= jealousyPenalty
                                                updateRelationshipLevel(rel)
                                            }
                                        }
                                    }
                                }

                                // 5. Save back to sessionProfile
                                sessionProfile.slotRoster = sessionProfile.slotRoster.map { slot ->
                                    if (slot.slotId == speakerSlot.slotId) speakerSlot else slot
                                }
                            }

                            val filteredMessages = roleplayResult.messages.map { msg ->
                                val timestamp = msg.timestamp ?: com.google.firebase.Timestamp.now()
                                val senderSlotProfile = sessionProfile.slotRoster.find { it.slotId == nextSlot }
                                val cleanedPose = msg.pose?.filterValues { !it.isNullOrBlank() } // optional, if you want to clean out blanks
                                msg.copy(
                                    area = senderSlotProfile?.lastActiveArea,
                                    location = senderSlotProfile?.lastActiveLocation,
                                    visibility = true,
                                    timestamp = timestamp,
                                    pose = cleanedPose
                                )
                            }
                            Log.d("AI_response", "filtered messages: $filteredMessages")

                            val newMemory = roleplayResult.newMemory
                            if (newMemory != null) {
                                // Find the area/location of the sender (assume first message in batch is the one that triggers the memory)
                                val mainMessage = roleplayResult.messages.firstOrNull()
                                val speakerSlotId = mainMessage?.senderId

                                val speakerSlot = sessionProfile.slotRoster.find { it.slotId == speakerSlotId }
                                val area = speakerSlot?.lastActiveArea
                                val location = speakerSlot?.lastActiveLocation

                                // Find all slotIds in that location
                                val presentSlots = sessionProfile.slotRoster.filter {
                                    it.lastActiveArea == area && it.lastActiveLocation == location
                                }

                                // For each, add the new memory
                                val updatedRoster = sessionProfile.slotRoster.map { slot ->
                                    if (presentSlots.any { it.slotId == slot.slotId }) {
                                        // Copy old memories and add new one
                                        val updatedMemories = slot.memories.toMutableList()
                                        updatedMemories.add(
                                            TaggedMemory(
                                                id = UUID.randomUUID().toString(),
                                                tags = newMemory.tags,
                                                text = newMemory.text,
                                                nsfw = newMemory.nsfw,
                                                messageIds = mainMessage?.let { listOf(it.id) } ?: emptyList()
                                            )
                                        )
                                        slot.copy(memories = updatedMemories)
                                    } else {
                                        slot
                                    }
                                }

                                // Update the sessionProfile with the new slotRoster
                                sessionProfile = sessionProfile.copy(slotRoster = updatedRoster)
                            }

                            handleRPGActionList(roleplayResult.actions)

                            setSlotTyping(sessionId, nextSlot!!, false)
                            saveSessionProfile(sessionProfile, sessionId)

                            withContext(Dispatchers.Main) {
                                saveMessagesSequentially(filteredMessages, sessionId, chatId)
                                val updatedHistory = chatAdapter.getMessages()
                                if (activationRound < maxActivationRounds && isActive) {
                                    processAboveTableRound("", updatedHistory)
                                }
                            }

                        }
                    } else {
                        activationRound = 0
                        withContext(Dispatchers.Main) { updateButtonState(ButtonState.SEND) }
                    }
                    // --------------------------------------------------------
                    true // Mark that completion was successful
                }

                if (didComplete == null) {
                    Log.w("AI_CYCLE", "AI response timed out after 30s, ending activation loop.")
                    activationRound = 0

                    withContext(Dispatchers.Main) {
                        updateButtonState(ButtonState.SEND)
                        Toast.makeText(this@MainActivity, "AI timed out.", Toast.LENGTH_SHORT).show()
                    }

                    // Optional: reset typing flag for the slot that was active, if known
                    val timedOutSlotId = nextSlot
                    if (timedOutSlotId != null) {
                        sessionProfile = sessionProfile.copy(
                            slotRoster = sessionProfile.slotRoster.map { slot ->
                                if (slot.slotId == timedOutSlotId) slot.copy(typing = false)
                                else slot
                            }
                        )
                        if (nextSlot != null) {
                            setSlotTyping(sessionId, nextSlot!!, false)
                        }
                        saveSessionProfile(sessionProfile, sessionId)
                    }
                }
            } catch (e: CancellationException) {
                Log.i(TAG, "AI job cancelled cleanly.")
                withContext(Dispatchers.Main) { updateButtonState(ButtonState.INTERRUPT) }
            } catch (e: Exception) {
                Log.e(TAG, "AI call failed", e)
                withContext(Dispatchers.Main) { updateButtonState(ButtonState.INTERRUPT) }
            }
        }

    }

    private fun processOnTableRPGRound(input: String, chatHistory: List<ChatMessage>, retryCount: Int = 0, maxRetries: Int = 2) {
        if (retryCount > maxRetries) {
            Log.w(TAG, "Max retries reached, aborting activation round")
            activationRound = 0
            return
        }

        if (activationRound >= maxActivationRounds) {
            Log.d(TAG, "Reached max activation rounds")
            return
        }
        activationRound++

        var needsPostNarration = false

        aiJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d("ai_cycle", "actually processing ontable round")
                val timeoutMillis = 45_000L
                var nextSlot: String? = null
                val didComplete = withTimeoutOrNull(timeoutMillis) {
                    withContext(Dispatchers.Main) {
                        updateButtonState(ButtonState.INTERRUPT)
                        setPlayerTyping(false)
                    }
                    Log.d("ai_cycle", "building activationai")
                    // Build activation prompt
                    val activeSlotId = sessionProfile.userMap[userId]?.activeSlotId
                    val locationMap: Map<String, List<String>> = sessionProfile.areas
                        .flatMap { area ->
                            area.locations.map { location ->
                                val charsHere = sessionProfile.slotRoster
                                    .filter {
                                        it.lastActiveArea?.normalizeLoc() == area.name.normalizeLoc() &&
                                                it.lastActiveLocation?.normalizeLoc() == location.name.normalizeLoc()
                                    }
                                    .map { it.name ?: it.slotId }
                                // Return a Pair here!
                                "${area.name} - ${location.name}" to charsHere
                            }
                        }.toMap()

                    val condensedMap: Map<String, String> = sessionProfile.slotRoster.associate {
                        it.slotId to it.summary // or whatever your condensed field is
                    }
                    val lastNonNarratorId = chatHistory.lastOrNull { it.senderId != "narrator" }?.senderId
                    var playerSlot = sessionProfile.slotRoster.find { it.slotId == activeSlotId }
                    val coLocatedSlotIds = sessionProfile.slotRoster
                        .filter {
                            it.lastActiveArea == playerSlot?.lastActiveArea &&
                                    it.lastActiveLocation == playerSlot?.lastActiveLocation &&
                                    it.slotId != lastNonNarratorId &&
                                    !it.typing
                        }
                        .map { it.slotId }
                    val memoriesMap: Map<String, List<TaggedMemory>> =
                        sessionProfile.slotRoster
                            .filter { it.slotId in coLocatedSlotIds }
                            .associate { it.slotId to it.memories.takeLast(5) }

                    val historyString = buildHistoryString(chatHistory.takeLast(10))
                    withContext(Dispatchers.Main) { updateButtonState(ButtonState.INTERRUPT) }
                    sessionProfile.slotRoster.forEach {
                        Log.d("DEBUG_SLOT", "slot=${it.slotId}, name=${it.name}, area=${it.lastActiveArea}, loc=${it.lastActiveLocation}")
                    }
                    sessionProfile.areas.forEach { area ->
                        area.locations.forEach { location ->
                            Log.d("DEBUG_LOC", "area='${area.name}', loc='${location.name}'")
                        }
                    }

                    val act = sessionProfile.acts.getOrNull(sessionProfile.currentAct)

                    val rpgSettingsJson = sessionProfile.modeSettings["rpg"] as? String
                    val gmStyle = if (!rpgSettingsJson.isNullOrBlank()) {
                        try {
                            val gson = Gson()
                            val rpgSettings = gson.fromJson(rpgSettingsJson, ModeSettings.RPGSettings::class.java)
                            rpgSettings.gmStyle ?: "Default"
                        } catch (e: Exception) {
                            "Default" // fallback if deserialization fails
                        }
                    } else {
                        "Default" // fallback if no settings saved
                    }
                    val gmStyleDescription = if (!rpgSettingsJson.isNullOrBlank()) {
                        try {
                            val gson = Gson()
                            val rpgSettings = gson.fromJson(rpgSettingsJson, ModeSettings.RPGSettings::class.java)
                            val styleEnum = try {
                                ModeSettings.GMStyle.valueOf(rpgSettings.gmStyle)
                            } catch (e: Exception) {
                                ModeSettings.GMStyle.HOST // fallback default
                            }
                            styleEnum.description // <-- Use the description!
                        } catch (e: Exception) {
                            "The game’s host runs things directly behind the scenes." // fallback description
                        }
                    } else {
                        "The game’s host runs things directly behind the scenes."
                    }

                    val gmLines = PromptBuilder.buildOnTableGMPrompt(
                        act = act,
                        activeSlotId = activeSlotId,
                        sessionSummary = sessionProfile.sessionDescription + sessionProfile.secretDescription,
                        locations = locationMap,
                        sessionProfile = sessionProfile,
                        condensedCharacterInfo = condensedMap, // Map: slotId → summary
                        lastNonNarratorId = lastNonNarratorId,
                        validNextSlotIds = coLocatedSlotIds,
                        memories = memoriesMap,
                        chatHistory = historyString,
                        gmStyle = gmStyleDescription
                    )
                    val gmPrompt = gmLines + PromptBuilder.buildRPGLiteRules()

                    var activationResponse = Facilitator.callActivationAI(gmPrompt,BuildConfig.OPENAI_API_KEY)
                    Log.d("ai_cycle", "$gmPrompt")
                    ensureActive()
                    Log.d("AI_response", "Raw AI Response:\n$activationResponse")

                    val isRefusal = activationResponse.trim().startsWith("I'm sorry")
                            || activationResponse.contains("I can't assist", ignoreCase = true)
                            || activationResponse.isBlank()

                    if (isRefusal) {
                        if (sessionProfile.sfwOnly == true) {
                            withContext(Dispatchers.Main) {
                                AlertDialog.Builder(this@MainActivity)
                                    .setTitle("AI Stopped")
                                    .setMessage("The AI was unable to process this round and SFW mode is enabled, so we can't use fallback. Please try another action or message.")
                                    .setPositiveButton("OK", null)
                                    .show()
                                updateButtonState(ButtonState.SEND)
                            }
                            activationRound = 0
                            return@withTimeoutOrNull
                        } else {
                            Log.d("AI_CYCLE", "OpenAI Activation AI refused. Retrying with Mixtral.")
                            var activationResponse = Facilitator.callActivationAI(gmPrompt,BuildConfig.MIXTRAL_API_KEY)

                            ensureActive()
                            Log.d("AI_response", "Mixtral fallback AI Response:\n$activationResponse")
                            val isMixtralRefusal = activationResponse.trim().startsWith("I'm sorry")
                                    || activationResponse.contains("I can't assist", ignoreCase = true)
                                    || activationResponse.isBlank()
                            if (isMixtralRefusal) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@MainActivity, "AI refused to process activation round. Try again.", Toast.LENGTH_SHORT).show()
                                    updateButtonState(ButtonState.SEND)
                                }
                                activationRound = 0
                                return@withTimeoutOrNull
                            }
                        }
                    }
                    val trimmedResponse = activationResponse.trim()
                    var roleplayResult: FacilitatorResponseParser.ParsedRoleplayResult? = null
                    var activationResult: FacilitatorResponseParser.FacilitatorActivationResult? = null


                    if (trimmedResponse.startsWith("[")) {
                        val jsonArray = JSONArray(trimmedResponse)
                        val obj1 = jsonArray.getJSONObject(0)
                        val obj2 = jsonArray.getJSONObject(1)
                        // Heuristically check: is this a roleplay or activation? (By field present)
                        if (obj1.has("messages")) {
                            roleplayResult = FacilitatorResponseParser.parseRoleplayAIResponse(obj1.toString(), "narrator", sessionProfile.slotRoster)
                            activationResult = FacilitatorResponseParser.parseActivationAIResponse(obj2.toString(), sessionProfile.slotRoster)
                        } else {
                            activationResult = FacilitatorResponseParser.parseActivationAIResponse(obj1.toString(), sessionProfile.slotRoster)
                            roleplayResult = FacilitatorResponseParser.parseRoleplayAIResponse(obj2.toString(), "narrator", sessionProfile.slotRoster)
                        }
                    } else if (trimmedResponse.startsWith("{")) {
                        // Just activation result
                        activationResult = FacilitatorResponseParser.parseActivationAIResponse(trimmedResponse, sessionProfile.slotRoster)
                    } else {
                        Log.e("AI_PARSE", "Unrecognized AI output: $trimmedResponse")
                    }
                    Log.d("AI_DEBUG", "roleplayResult: $roleplayResult, messages: ${roleplayResult?.messages}")

                    if (roleplayResult != null) {
                        val filteredMessages = roleplayResult.messages.map { msg ->
                            val timestamp = msg.timestamp ?: com.google.firebase.Timestamp.now()

                            // Narrator messages don't have a proper slotId, but we still want to tag a location if possible
                            val narratorLocation = sessionProfile.slotRoster
                                .firstOrNull { it.profileType == "player" } // fallback: first player slot
                                ?.let { it.lastActiveLocation to it.lastActiveArea }

                            val cleanedPose = msg.pose?.filterValues { !it.isNullOrBlank() }

                            msg.copy(
                                area = msg.area ?: narratorLocation?.second,
                                location = msg.location ?: narratorLocation?.first,
                                visibility = true,
                                timestamp = timestamp,
                                pose = cleanedPose
                            )
                        }

                        Log.d("AI_response", "filtered messages: $filteredMessages")

                        withContext(Dispatchers.Main) {
                            saveMessagesSequentially(filteredMessages, sessionId, chatId)

                            // No GM slot typing to update — just save session normally
                            saveSessionProfile(sessionProfile, sessionId)
                        }
                    }

                    var isNSFW = activationResult?.isNSFW ?: false

                    if (sessionProfile.sfwOnly == true && isNSFW) {
                        // SFW session but the AI detected NSFW intent
                        withContext(Dispatchers.Main) {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("NSFW Detected")
                                .setMessage("The AI detected that your message or action is heading into NSFW territory, but this session is SFW only. Try rewriting you previous message. If you think the AI made a mistake you can resend your previous message without changing it.")
                                .setPositiveButton("OK", null)
                                .show()
                            updateButtonState(ButtonState.SEND)
                        }
                        activationRound = 0
                        return@withTimeoutOrNull
                    }

                    nextSlot = activationResult?.nextSlot
                    val updatedSlotRoster = activationResult?.updatedRoster ?: sessionProfile.slotRoster
                    if (activationResult?.newNpcs?.isNotEmpty() == true) {
                        for (npc in activationResult.newNpcs) {
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, "New NPC created: ${npc.name}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    Log.d("AI_memory_ids", "Memory IDs from AI: ${activationResult?.memoryIds}")
                    Log.d("NPC_JSON_DEBUG", "npcObj = ${activationResult?.updatedRoster}")


                    sessionProfile = sessionProfile.copy(slotRoster = updatedSlotRoster)
                    saveSessionProfile(sessionProfile, sessionId)

                    playerSlot = sessionProfile.slotRoster.find { it.profileType == "player" }
                    val playerArea = playerSlot?.lastActiveArea
                    val playerLocation = playerSlot?.lastActiveLocation

                    // 3. NOW do your background lookup as before
                    fun String.normalize(): String = this.trim().lowercase().replace("\\s+".toRegex(), " ")

                    val areaObj = sessionProfile.areas.find {
                        it.name.normalize() == playerArea?.normalize() || it.id == playerArea
                    }
                    val locationObj = areaObj?.locations?.find {
                        it.name.normalize() == playerLocation?.normalize() || it.id == playerLocation
                    }


                    sessionProfile.areas.forEach { area ->
                        Log.d("debug", "AREA: ${area.name}")
                        area.locations.forEach { loc ->
                            Log.d("debug", "  -> LOCATION: '${loc.name}' (uri=${loc.uri})")
                        }
                    }
                    sessionProfile.areas.forEach { area ->
                        area.locations.forEach { location ->
                            location.characters.clear()
                        }
                    }

                    // 2. Re-populate from slotRoster
                    sessionProfile.slotRoster.forEach { slot ->
                        val area = sessionProfile.areas.find { it.name.normalize() == slot.lastActiveArea?.normalize() || it.id == slot.lastActiveArea }
                        val location = area?.locations?.find { it.name.normalize() == slot.lastActiveLocation?.normalize() || it.id == slot.lastActiveLocation }
                        if (location != null) {
                            // Add character id or profile to location's character list
                            location.characters.add(slot.name)
                        }
                    }
                    if (!nextSlot.isNullOrBlank()) {
                        val slotId = nextSlot ?: run {
                            Log.w("AI_CYCLE", "nextSlot is null, retrying.")
                            processOnTableRPGRound(input, chatHistory, retryCount + 1)
                            return@withTimeoutOrNull
                        }
                        val slotProfile = sessionProfile.slotRoster.find { it.slotId == slotId }
                        if (slotProfile == null) {
                            Log.w("AI_CYCLE", "Slot '$slotId' not found in slotRoster")
                            activationRound = 0
                            withContext(Dispatchers.Main) { updateButtonState(ButtonState.SEND) }
                            return@withTimeoutOrNull  // <-- or return@launch depending on your coroutine builder
                        }
                        if (slotProfile.profileType == "player") {
                            Log.d("AI_CYCLE", "It's the user's turn. Do NOT generate an AI message for player slot.")
                            activationRound = 0
                            // Optionally, trigger user input UI here.
                            withContext(Dispatchers.Main) {
                                updateButtonState(ButtonState.SEND)
                            }
                            return@withTimeoutOrNull
                        }
                        if (slotProfile.typing == true) {
                            Log.d(TAG, "Slot is typing, retrying activation round $retryCount")
                            processOnTableRPGRound(input, chatHistory, retryCount + 1)
                            return@withTimeoutOrNull
                        }

                        withContext(Dispatchers.Main) {
                            updateButtonState(ButtonState.WAITING)
                        }
                        val activeSlotId = sessionProfile.userMap[userId]?.activeSlotId
                        if (activeSlotId != null) {
                            sessionProfile = sessionProfile.copy(
                                slotRoster = sessionProfile.slotRoster.map { slot ->
                                    if (slot.slotId == nextSlot) slot.copy(typing = true)
                                    else slot
                                }
                            )
                            // Save to Firestore so others see the typing
                            saveSessionProfile(sessionProfile, sessionId)

                                setSlotTyping(sessionId, nextSlot!!, true)

                        }

                        val sceneSlotIds = sessionProfile.slotRoster
                            .filter { it.lastActiveArea == playerArea && it.lastActiveLocation == playerLocation }
                            .map { it.slotId }
                            .distinct()
                        val condensedCharacterInfo = sessionProfile.slotRoster
                            .filter { it.lastActiveArea == playerArea && it.lastActiveLocation == playerLocation }
                            .associate { profile ->
                                val outfit = profile.outfits.find { it.name == profile.currentOutfit }
                                val availablePoses = outfit?.poseSlots?.map { it.name } ?: "DO NOT INCLUDE THIS CHARACTER IN THE POSES SECTION"
                                profile.slotId to mapOf(
                                    "summary" to profile.summary,
                                    "pose" to profile.pose,
                                    "available_poses" to availablePoses
                                )
                            }
                        val memoryIds = activationResult?.memoryIds ?: emptyList()
                        val nextSlotProfile = sessionProfile.slotRoster.find { it.slotId == nextSlot }
                        val relevantMemories = nextSlotProfile?.memories?.filter { memoryIds.contains(it.id) } ?: emptyList()
                        val memoriesMap = mapOf(nextSlot!! to relevantMemories)
                        val modeSettings = sessionProfile.modeSettings
                        val myPersonalHistory = fetchPersonalHistory(sessionId, slotId)
                        val historyString = buildHistoryString(myPersonalHistory.takeLast(10))


                        val roleplayPrompt = PromptBuilder.buildRoleplayPrompt(
                            slotProfile = slotProfile,
                            modeSettings = modeSettings,
                            sessionSummary = sessionProfile.sessionDescription + sessionProfile.secretDescription,
                            sceneSlotIds = sceneSlotIds,
                            condensedCharacterInfo = condensedCharacterInfo,
                            chatHistory = historyString,
                            memories = memoriesMap
                        )

                        val vnPrompt = buildVNPrompt(
                            slotProfile = slotProfile,
                            sessionProfile = sessionProfile
                        )

                        val diceInstructions = buildDiceRoll()

                        val vnModeEnabled = sessionProfile.modeSettings["visual_novel"] == "true" ||
                                !(sessionProfile.modeSettings["vn"] as? String).isNullOrBlank()

                        val vnCheckedPrompt = if (vnModeEnabled) {
                            roleplayPrompt + diceInstructions + "\n\n" + vnPrompt
                        } else {
                            roleplayPrompt + diceInstructions
                        }

                        Log.d("AI_response", "History: $chatHistory")
                        val nsfwinstructions = "\n - Be descriptive and explicit. The more sexual and erotic the better"

                        val nsfwPrompt = vnCheckedPrompt + nsfwinstructions
                        // Switch models as needed
                        val roleplayResponse =
                        if (isNSFW)
                            Facilitator.callMixtralApi(
                                nsfwPrompt,
                                BuildConfig.MIXTRAL_API_KEY
                            )
                        else
                            Facilitator.callOpenAiApi(
                                vnCheckedPrompt,
                                BuildConfig.OPENAI_API_KEY
                            )

                        ensureActive()
                        Log.d("ai_cycle", "Roleplay Prompt is: $roleplayPrompt")
                        Log.d("AI_response", "Roleplay Response:\n$roleplayResponse")

                        // Parse AI output to ChatMessage list
                        val roleplayResult = try {
                            FacilitatorResponseParser.parseRoleplayAIResponse(roleplayResponse, nextSlot!!, sessionProfile.slotRoster)
                        } catch (e: Exception) {
                            Log.e("AI_CYCLE", "Malformed roleplay response: $roleplayResponse", e)
                            if (activeSlotId != null) {
                                setSlotTyping(sessionId, nextSlot!!, false)
                            }
                            null
                        }

                        if (roleplayResult == null || roleplayResult.messages.isEmpty()) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "AI sent a broken message. Please try again.", Toast.LENGTH_SHORT).show()
                                updateButtonState(ButtonState.SEND)
                                setSlotTyping(sessionId, nextSlot!!, false)

                            }
                            activationRound = 0
                            return@withTimeoutOrNull
                        }

                        val speakerSlotId = roleplayResult?.messages?.firstOrNull()?.senderId ?: nextSlot
                        val speakerSlot = sessionProfile.slotRoster.find { it.slotId == speakerSlotId }
                        if (speakerSlot != null && roleplayResult?.relationshipChanges?.isNotEmpty() == true) {
                            val fromId = speakerSlot.baseCharacterId

                            // 1. Get the acting character's relationship map (vnRelationships)
                            val relMap = speakerSlot.vnRelationships

                            // 2. Apply all relationship changes to this map
                            updateRelationshipsFromChanges(relMap, roleplayResult.relationshipChanges)

                            // 3. Enforce monogamy if enabled
                            val vnSettingsJson = sessionProfile.modeSettings["vn"] as? String
                            val vnSettings = if (!vnSettingsJson.isNullOrBlank())
                                Gson().fromJson(vnSettingsJson, ModeSettings.VNSettings::class.java)
                            else
                                ModeSettings.VNSettings()

                            if (vnSettings.monogamyEnabled && vnSettings.monogamyLevel != null) {
                                enforceMonogamy(
                                    relMap,
                                    vnSettings.monogamyLevel!!
                                )
                            }

                            // 4. Trigger Jealousy
                            if (vnSettings.jealousyEnabled) {
                                val jealousyPenalty = 1 // Or whatever penalty you want
                                val toId = roleplayResult.relationshipChanges.first().toId // Assumes one per turn, adjust as needed

                                // Lower relationship points for all other characters in the same location
                                for (otherSlot in sessionProfile.slotRoster) {
                                    if (otherSlot.baseCharacterId != fromId &&
                                        otherSlot.lastActiveLocation == speakerSlot.lastActiveLocation) {

                                        val relMap = otherSlot.vnRelationships
                                        val rel = relMap[toId]
                                        if (rel != null) {
                                            rel.points -= jealousyPenalty
                                            updateRelationshipLevel(rel)
                                        }
                                    }
                                }
                            }

                            // 5. Save back to sessionProfile
                            sessionProfile.slotRoster = sessionProfile.slotRoster.map { slot ->
                                if (slot.slotId == speakerSlot.slotId) speakerSlot else slot
                            }
                        }

                        val filteredMessages = roleplayResult.messages.map { msg ->
                            val timestamp = msg.timestamp ?: com.google.firebase.Timestamp.now()
                            val senderSlotProfile = sessionProfile.slotRoster.find { it.slotId == nextSlot }
                            val cleanedPose = msg.pose?.filterValues { !it.isNullOrBlank() } // optional, if you want to clean out blanks
                            msg.copy(
                                area = senderSlotProfile?.lastActiveArea,
                                location = senderSlotProfile?.lastActiveLocation,
                                visibility = true,
                                timestamp = timestamp,
                                pose = cleanedPose
                            )
                        }

                        Log.d("AI_response", "filtered messages: $filteredMessages")

                        val newMemory = roleplayResult.newMemory
                        if (newMemory != null) {
                            // Find the area/location of the sender (assume first message in batch is the one that triggers the memory)
                            val mainMessage = roleplayResult.messages.firstOrNull()
                            val speakerSlotId = mainMessage?.senderId

                            val speakerSlot = sessionProfile.slotRoster.find { it.slotId == speakerSlotId }
                            val area = speakerSlot?.lastActiveArea
                            val location = speakerSlot?.lastActiveLocation

                            // Find all slotIds in that location
                            val presentSlots = sessionProfile.slotRoster.filter {
                                it.lastActiveArea == area && it.lastActiveLocation == location
                            }

                            // For each, add the new memory
                            val updatedRoster = sessionProfile.slotRoster.map { slot ->
                                if (presentSlots.any { it.slotId == slot.slotId }) {
                                    // Copy old memories and add new one
                                    val updatedMemories = slot.memories.toMutableList()
                                    updatedMemories.add(
                                        TaggedMemory(
                                            id = UUID.randomUUID().toString(),
                                            tags = newMemory.tags,
                                            text = newMemory.text,
                                            nsfw = newMemory.nsfw,
                                            messageIds = mainMessage?.let { listOf(it.id) } ?: emptyList()
                                        )
                                    )
                                    slot.copy(memories = updatedMemories)
                                } else {
                                    slot
                                }
                            }

                            // Update the sessionProfile with the new slotRoster
                            sessionProfile = sessionProfile.copy(slotRoster = updatedRoster)
                        }

                        withContext(Dispatchers.Main) {
                            saveMessagesSequentially(filteredMessages, sessionId, chatId)

                            if (!nextSlot.isNullOrBlank()) {
                                    setSlotTyping(sessionId, nextSlot!!, false)

                                saveSessionProfile(sessionProfile, sessionId)
                            }

                            val updatedHistory = chatAdapter.getMessages()
                            if (activationRound < maxActivationRounds && isActive) {
                                processOnTableRPGRound("", updatedHistory)
                            }
                        }
                    } else {
                        activationRound = 0
                        withContext(Dispatchers.Main) { updateButtonState(ButtonState.SEND) }
                    }
                    // --------------------------------------------------------
                    true // Mark that completion was successful
                }

                if (didComplete == null) {
                    Log.w("AI_CYCLE", "AI response timed out after 30s, ending activation loop.")
                    activationRound = 0

                    withContext(Dispatchers.Main) {
                        updateButtonState(ButtonState.SEND)
                        Toast.makeText(this@MainActivity, "AI timed out.", Toast.LENGTH_SHORT).show()
                    }

                    // Optional: reset typing flag for the slot that was active, if known
                    val timedOutSlotId = nextSlot
                    if (timedOutSlotId != null) {
                        setSlotTyping(sessionId, nextSlot!!, false)
                        saveSessionProfile(sessionProfile, sessionId)
                    }
                }
            } catch (e: CancellationException) {
                Log.i(TAG, "AI job cancelled cleanly.")
                withContext(Dispatchers.Main) { updateButtonState(ButtonState.INTERRUPT) }
            } catch (e: Exception) {
                Log.e(TAG, "AI call failed", e)
                withContext(Dispatchers.Main) { updateButtonState(ButtonState.INTERRUPT) }
            }
        }

    }

    fun showCharacterPickerDialog(slotRoster: List<SlotProfile>, onCharacterSelected: (SlotProfile) -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_character_picker, null)
        val spinner = dialogView.findViewById<Spinner>(R.id.characterSpinner)
        val confirmButton = dialogView.findViewById<Button>(R.id.confirmButton)

        // Prepare spinner data
        val characterNames = slotRoster.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, characterNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val dialog = AlertDialog.Builder(this)
            .setTitle("Please choose a character to take control of")
            .setView(dialogView)
            .setCancelable(false)
            .create()

        confirmButton.setOnClickListener {
            val selectedPosition = spinner.selectedItemPosition
            if (selectedPosition in slotRoster.indices) {
                onCharacterSelected(slotRoster[selectedPosition])
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
            }
        }
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
                targetSlot.statusEffects.add(effect)
                Log.d("RPG", "${targetSlot.name} gained status effect: $effect")
            } else {
                targetSlot.statusEffects.remove(effect)
                Log.d("RPG", "${targetSlot.name} lost status effect: $effect")
            }
        }
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
        val msg = ChatMessage(
            id = UUID.randomUUID().toString(),
            senderId = activeSlotId ?: "unknown",
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
        val slotIdsList = sessionProfile.slotRoster.joinToString(", ") { it.slotId }
        if (messages.isEmpty() && !initialGreeting.isNullOrBlank()) {
            val greetingMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                senderId = "system",
                text = initialGreeting!! + "\n if a characters lastActiveArea and/or lastActiveLocation = null, move them to an area and location.",
                delay = 0,
                timestamp = com.google.firebase.Timestamp.now(),
                imageUpdates = null,
                visibility = false
            )
            if (isOnTable) {
                Log.d("ai_cycle", "proccessing isOnTable")
                activationRound = 1
                processOnTableRPGRound(userInput, listOf(greetingMessage))
            }else if (isAboveTable){
                Log.d("ai_cycle", "proccessing isAboveTable")
                activationRound = 1
                processAboveTableRound(userInput, listOf(greetingMessage))
            } else {
                Log.d("ai_cycle", "proccessing normal roleplay")

                activationRound = 1
                processActivationRound(userInput, listOf(greetingMessage))
            }
        } else {

            if (isOnTable) {
                Log.d("ai_cycle", "proccessing isOnTable")
                activationRound = 1
                processOnTableRPGRound(userInput, messages)
            }else if (isAboveTable){
                Log.d("ai_cycle", "proccessing isAboveTable")
                activationRound = 1
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

        db.runTransaction { transaction ->
            val snapshot = transaction.get(sessionRef)
            val sessionProfile = snapshot.toObject(SessionProfile::class.java) ?: return@runTransaction

            // Update only the typing field for the matching slotId
            val updatedSlotRoster = sessionProfile.slotRoster.map { slot ->
                if (slot.slotId == slotId) slot.copy(typing = typing) else slot
            }

            transaction.update(sessionRef, "slotRoster", updatedSlotRoster)
            null
        }.addOnSuccessListener {
            // Optionally log success
            Log.d("Typing", "Typing status for slot $slotId set to $typing")
        }.addOnFailureListener { e ->
            Log.e("Typing", "Failed to set typing status for $slotId: $e")
        }
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

    fun updateAvatarsFromSlots(slotProfiles: List<SlotProfile>, avatarSlotAssignments: MutableMap<Int, String?>) {
        if (isDestroyed || isFinishing) return
        Log.d("avatardebug", "updating: $avatarSlotAssignments")
        avatarSlotAssignments.forEach { index, slotId ->
            if (slotId == null) {
                avatarViews[index].setImageResource(R.drawable.silhouette)
                avatarViews[index].visibility = View.INVISIBLE
                return@forEach
            }

            Log.d("avatardebug", "updating 2: $avatarSlotAssignments")
            val slotProfile = slotProfiles.find { it.slotId == slotId }
            val playerSlot = slotProfiles.find { it.slotId == mySlotId }
            val playerArea = playerSlot?.lastActiveArea
            val playerLocation = playerSlot?.lastActiveLocation

            if (slotProfile == null ||
                slotProfile.lastActiveArea != playerArea ||
                slotProfile.lastActiveLocation != playerLocation
            ) {
                Log.d("avatardebug", "${slotProfile?.name} is at ${slotProfile?.lastActiveArea} ${slotProfile?.lastActiveLocation}, player ${mySlotId} is at $playerArea $playerLocation")
                Log.d("avatardebug", "${slotProfile?.name} is being removed")
                // Not present—remove and clear
                removeAvatarFromSlots(slotId, avatarSlotAssignments)
                avatarViews[index].setImageResource(R.drawable.silhouette)
                avatarViews[index].visibility = View.INVISIBLE
                return@forEach
            }

            if (slotProfile == null) {
                avatarViews[index].setImageResource(R.drawable.silhouette)
                avatarViews[index].visibility = View.INVISIBLE
                return@forEach
            }

            val poseName = slotProfile.pose?.lowercase() ?: ""

            Log.d("avatardebug", "${slotProfile?.name} is being updating it to ${slotProfile.pose} ")
            if (poseName.isBlank() || poseName == "clear" || poseName == "none") {
                avatarViews[index].setImageResource(R.drawable.silhouette)
                avatarViews[index].visibility = View.INVISIBLE
                return@forEach
            }

            val outfit = slotProfile.outfits?.find { it.name.equals(slotProfile.currentOutfit, ignoreCase = true) }
            if (outfit?.poseSlots.isNullOrEmpty()) {

                Log.d("avatardebug", "2 ${slotProfile?.name} is being updating it to ${slotProfile.pose} ")
                avatarViews[index].setImageResource(R.drawable.silhouette)
                avatarViews[index].visibility = View.INVISIBLE
                return@forEach
            }

            val poseSlot = outfit.poseSlots.find { it.name.equals(poseName, ignoreCase = true) }
            val imageUrl = poseSlot?.uri

            if (!imageUrl.isNullOrBlank()) {
                Glide.with(this)
                    .load(imageUrl)
                    .placeholder(R.drawable.silhouette)
                    .into(avatarViews[index])
                avatarViews[index].visibility = View.VISIBLE
            } else {
                avatarViews[index].setImageResource(R.drawable.silhouette)
                avatarViews[index].visibility = View.INVISIBLE
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

    private fun updateLocationCharRecycler(area: Area, location: LocationSlot?) {
        val locationChar = findViewById<RecyclerView>(R.id.locationChar)
        if (location == null) {
            locationChar.adapter = CharacterRowAdapter(emptyList(), onClick = {})
            return
        }
        val presentChars = sessionProfile.slotRoster
            .filter { it.lastActiveArea == area.name && it.lastActiveLocation == location.name }
            .map { it.toCharacterProfileStub() } // <--- this is why it works in sessionlanding

        locationChar.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        locationChar.adapter = CharacterRowAdapter(
            presentChars,
            onClick = { char -> Toast.makeText(this, "Clicked ${char.name}", Toast.LENGTH_SHORT).show() }
        )
    }

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
        sessionListener = FirebaseFirestore.getInstance()
            .collection("sessions")
            .document(sessionId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    val updatedProfile = snapshot.toObject(SessionProfile::class.java) ?: return@addSnapshotListener

                    // --- Save updated session profile! ---
                    sessionProfile = updatedProfile

                    // --- Update typing indicator for all users! ---
                    updateTypingIndicator()

                    // --- Background change logic (optional) ---
                    val mySlotId = updatedProfile.userMap[userId]?.activeSlotId
                    val playerSlot = updatedProfile.slotRoster.find { it.slotId == mySlotId }
                    val playerArea = playerSlot?.lastActiveArea
                    val playerLocation = playerSlot?.lastActiveLocation
                    updateBackgroundIfChanged(playerArea, playerLocation, updatedProfile.areas)


                    if (mySlotId != null) {
                        listenToPersonalHistory(sessionId, mySlotId)
                    }

                    // Multiplayer change detection (optional)
                    val newMultiplayer = updatedProfile.multiplayer
                    if (lastMultiplayerValue != null && lastMultiplayerValue != newMultiplayer) {
                        Log.w("MULTIPLAYER_CHANGE", "multiplayer field changed! Old: $lastMultiplayerValue, New: $newMultiplayer at ${System.currentTimeMillis()}")
                        Log.d("MULTIPLAYER_CHANGE", "Full session: $updatedProfile")
                    }
                    lastMultiplayerValue = newMultiplayer

                    SessionManager.listenMessages(sessionId) { newMessage ->
                        runOnUiThread {
                            if (!historyLoaded) return@runOnUiThread
                            // 2. Skip if messageId is null or already processed
                            val currentMessageId = newMessage.id ?: return@runOnUiThread
                            if (processedMessageIds.contains(currentMessageId)) {
                                Log.d("MessageFilter", "Skipping duplicate message: $currentMessageId")
                                return@runOnUiThread
                            }
                            processedMessageIds.add(currentMessageId)
                            if (processedMessageIds.size > 20) { // Prevent memory leak, keep the last 20-50
                                processedMessageIds.remove(processedMessageIds.first())
                            }

                            // 4. Check location and assign message to relevant slots
                            val messageArea = newMessage.area
                            val messageLocation = newMessage.location

                            // 5. Update poses from the message

                            if (!newMessage.pose.isNullOrEmpty()) {
                                val updatedRoster = sessionProfile.slotRoster.toMutableList()
                                newMessage.pose.forEach { (slotId, poseName) ->
                                    val idx = updatedRoster.indexOfFirst { it.slotId == slotId }
                                    if (idx >= 0) {
                                        val oldProfile = updatedRoster[idx]
                                        updatedRoster[idx] = oldProfile.copy(pose = poseName)
                                    }
                                }
                                sessionProfile = sessionProfile.copy(slotRoster = updatedRoster)
                            }

                            // 6. Send to local profiles
                            val recipients = sessionProfile.slotRoster.filter { slot ->
                                slot.lastActiveArea == messageArea && slot.lastActiveLocation == messageLocation
                            }
                            recipients.forEach { slot ->
                                if (newMessage.messageType == "roll" && currentMessageId != lastMessageId){

                                    lifecycleScope.launch {
                                        val rollResult = extractRollFromText(newMessage.text)
                                        Log.d("multiplayer check", "roll result: $rollResult")
                                        diceImageView.visibility = View.VISIBLE
                                        // Fast cycle 15 times
                                        repeat(15) {
                                            val roll = (1..20).random()
                                            val resId = resources.getIdentifier("ic_d$roll", "drawable", packageName)
                                            if (resId != 0) diceImageView.setImageResource(resId)
                                            delay(50)
                                        }
                                        // Show final roll for 1 second
                                        val finalResId = resources.getIdentifier("ic_d$rollResult", "drawable", packageName)
                                        if (finalResId != 0){
                                            diceImageView.setImageResource(finalResId)
                                        }
                                        delay(1000)
                                        addToPersonalHistoryFirestore(sessionId, slot.slotId, newMessage)
                                        delay(1000)
                                        diceImageView.visibility = View.GONE
                                    }
                                }
                                else
                                {
                                    addToPersonalHistoryFirestore(
                                        sessionId,
                                        slot.slotId,
                                        newMessage
                                    )
                                }
                            }

                            updateButtonState(ButtonState.SEND)

                            // 5. Update lastMessageId
                            lastMessageId = currentMessageId
                        }
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val updatedProfile = snapshot.toObject(SessionProfile::class.java)
                            ?: return@addSnapshotListener
                        sessionProfile =
                            updatedProfile  // Update local session data:contentReference[oaicite:6]{index=6}

                        // If initial greeting was delayed and all players have characters now, send it
                        if (!initialGreeting.isNullOrBlank() && !initialGreetingSent) {
                            val allPlayersChosen =
                                sessionProfile.userMap.values.all { it.activeSlotId != null }
                            if (allPlayersChosen) {
                                initialGreetingSent = true  // mark as sent to avoid repeats
                                updateButtonState(ButtonState.INTERRUPT)
                                val greetingText =
                                    initialGreeting!!  // not blank due to check above
                                // Add greeting to bots' history asynchronously, then send to AI
                                lifecycleScope.launch {
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
                                            addToPersonalHistoryFirestore(
                                                sessionId,
                                                botSlot.slotId,
                                                greetingMsg
                                            )
                                        }
                                }
                                sendToAI(greetingText)  // trigger AI with the initial greeting now
                            }
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
            putExtra(Intent.EXTRA_EMAIL, arrayOf("shirigama@gmail.com"))
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

    override fun onStop() {
        sessionListener?.remove()
        sessionListener = null
        messagesListener?.remove()
        messagesListener = null
        personalHistoryListener?.remove()
        personalHistoryListener = null
        super.onStop()
    }
}
