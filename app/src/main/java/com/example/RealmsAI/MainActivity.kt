package com.example.RealmsAI

import ChatAdapter
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
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.RealmsAI.adapters.CollectionAdapter.CharacterRowAdapter
import android.net.Uri
import com.example.RealmsAI.models.ModeSettings.RPGSettings
import org.json.JSONArray

class MainActivity : AppCompatActivity() {
    companion object { private const val TAG = "MainActivity" }

    // UI
    private lateinit var chatTitleView: TextView
    private lateinit var chatDescriptionView: TextView
    private lateinit var chatRecycler: RecyclerView
    private lateinit var messageEditText: EditText
    private lateinit var sendButton: Button
    private lateinit var topOverlay: View
    private lateinit var toggleChatInputButton: ImageButton
    private lateinit var avatarViews: List<ImageView>
    private lateinit var chatInputGroup: View
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


    // State
    private lateinit var sessionProfile: SessionProfile
    private lateinit var chatAdapter: ChatAdapter
    private val visibleAvatarSlotIds = MutableList(4) { "" }
    private val avatarSlotLocked = BooleanArray(4) { false }

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
    private var initialGreeting: String? = null

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
        resendButton = findViewById(R.id.resendButton)
        toggleControlButton = findViewById(R.id.toggleControlButton)
        toggleChatButton = findViewById(R.id.toggleChatButton)
        typingIndicatorBar = findViewById(R.id.typingIndicatorBar)
        moveButton = findViewById(R.id.controlMoveButton)
        personaButton = findViewById(R.id.controlPersonaButton)
        optionButton = findViewById(R.id.controlOptionsButton)

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
        val controlBox = findViewById<View>(R.id.controlBox)
        val visibleAvatarSlotIds = MutableList(4) { "" }
        val avatarSlotLocked = BooleanArray(4) { false }

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
                toggleChatInputButton.translationY = -keypadHeight.toFloat() + 125f
                resendButton.translationY = -keypadHeight.toFloat() + 125f
                backgroundImageView.translationY = -keypadHeight.toFloat() + 125f
                toggleControlButton.translationY = -keypadHeight.toFloat() + 125f
            } else {
                avatarContainer.translationY = 0f
                chatInputGroup.translationY = 0f
                toggleChatInputButton.translationY = 0f
                resendButton.translationY = 0f
                backgroundImageView.translationY = 0f
                toggleControlButton.translationY = 0f
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

        if (sessionProfile.chatMode == "ONE_ON_ONE")
        {
            maxActivationRounds = 2
        } else {
            maxActivationRounds = 3
        }

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

        // Hide all avatars, then show only those in use
        avatarViews.forEach { it.visibility = View.INVISIBLE }

        // New session or load old chat?
        when (intent.getStringExtra("ENTRY_MODE") ?: "CREATE") {

            "CREATE" -> {
                Log.d("entering", "entrymode: CREATE")
                chatAdapter.clearMessages()
                updateButtonState(ButtonState.INTERRUPT)

                val greetingText = initialGreeting ?: ""

                if (greetingText.isNotBlank()) {
                    lifecycleScope.launch {
                        // Add greeting to every bot's personal history
                        sessionProfile.slotRoster.filter { it.profileType == "bot" }
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
                    sendToAI(initialGreeting!!)
                } else {
                    sendToAI("")

                }
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
                    .set(updatedSessionProfile, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d("Firestore", "Session saved with multiplayer = true")
                    }
                    .addOnFailureListener { e ->
                        Log.e("Firestore", "Failed to save session", e)
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

        // Toggle input visibility
        toggleChatInputButton.setOnClickListener {
            val isVisible = chatInputGroup.visibility == View.VISIBLE
            chatInputGroup.visibility = if (isVisible) View.GONE else View.VISIBLE
        }



        toggleChatButton.setOnClickListener {
            controlBox.visibility = View.GONE
            chatInputGroup.visibility = View.VISIBLE
            toggleChatButton.visibility = View.GONE
            toggleControlButton.visibility = View.VISIBLE
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

        toggleControlButton.setOnClickListener {
            chatInputGroup.visibility = View.GONE
            controlBox.visibility = View.VISIBLE
            toggleControlButton.visibility = View.GONE
            toggleChatButton.visibility = View.VISIBLE
            optionsOptions.visibility = View.VISIBLE
            showOptions()
        }
        moveBtn.setOnClickListener { showMove() }
        avatarBtn.setOnClickListener { showAvatar() }
        optionsBtn.setOnClickListener { showOptions() }

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


        resendButton.setOnClickListener {
            val lastMessage = chatAdapter.getMessages()
                .lastOrNull { it.senderId == sessionProfile.userMap[userId]?.activeSlotId }

            if (lastMessage != null) {
                // Resend same text but with visibility=false and blank text
                val resendMsg = lastMessage.copy(
                    id = UUID.randomUUID().toString(),
                    text = "", // blank text
                    visibility = false,
                    timestamp = com.google.firebase.Timestamp.now()
                )
                SessionManager.sendMessage(chatId, sessionId, resendMsg)
                // (Optional: Trigger AI if you want this to start a new round)
                sendToAI("")
            }
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

    // ========== AI Activation & Roleplay Loop (Modernized) ==========
    private fun processActivationRound(
        input: String,
        chatHistory: List<ChatMessage>,
        retryCount: Int = 0,
        maxRetries: Int = 3
    ) {
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
                                FacilitatorResponseParser.parseRoleplayAIResponse(narratorResponse)
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
                                processActivationRound(input, chatHistory, retryCount + 1)
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

                                if (activeSlotId != null) {
                                    setSlotTyping(sessionId, nextSlot!!, true)
                                }
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

                            Log.d("AI_response", "History: $chatHistory")
                            val nsfwinstructions =  "/n - Be descriptive and explicit. The more sexual and erotic the better"

                            val nsfwPrompt = roleplayPrompt + nsfwinstructions

                            // Switch models as needed
                            val roleplayResponse =
                                if (isNSFW)
                                    Facilitator.callMixtralApi(nsfwPrompt, BuildConfig.MIXTRAL_API_KEY)
                                else
                                    Facilitator.callOpenAiApi(roleplayPrompt, BuildConfig.OPENAI_API_KEY)


                            ensureActive()
                            Log.d("ai_cycle", "Roleplay Prompt is: $roleplayPrompt")
                            Log.d("AI_response", "Roleplay Response:\n$roleplayResponse")

                            // Parse AI output to ChatMessage list
                            val roleplayResult = try {
                                FacilitatorResponseParser.parseRoleplayAIResponse(roleplayResponse)
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

                            val speakerSlotId = roleplayResult.messages.firstOrNull()?.senderId ?: nextSlot
                            val speakerSlot = sessionProfile.slotRoster.find { it.slotId == speakerSlotId }
                            if (speakerSlot != null && roleplayResult.relationshipChanges.isNotEmpty()) {
                                val updatedRelationships = speakerSlot.relationships.toMutableList()
                                for (change in roleplayResult.relationshipChanges) {
                                    val relIdx = updatedRelationships.indexOfFirst { it.id == change.relationshipId }
                                    if (relIdx != -1) {
                                        val rel = updatedRelationships[relIdx]
                                        rel.points += change.delta
                                        FacilitatorResponseParser.updateRelationshipLevel(rel)
                                        updatedRelationships[relIdx] = rel
                                    }
                                }
                                val updatedSlot = speakerSlot.copy(relationships = updatedRelationships)
                                val updatedSlotRoster = sessionProfile.slotRoster.map { slot ->
                                    if (slot.slotId == updatedSlot.slotId) updatedSlot else slot
                                }
                                sessionProfile = sessionProfile.copy(slotRoster = updatedSlotRoster)
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
                                    if (activeSlotId != null) {
                                        setSlotTyping(sessionId, nextSlot!!, false)
                                    }
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


    private fun processAboveTableRound(
        input: String,
        chatHistory: List<ChatMessage>,
        retryCount: Int = 0,
        maxRetries: Int = 4
    ) {
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
                    if (playerSlot == null) {
                        Log.w("AI_CYCLE", "No player slot found. Aborting.")
                        return@withTimeoutOrNull
                    }
                    val playerArea = playerSlot.lastActiveArea
                    val playerLocation = playerSlot.lastActiveLocation

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
                                FacilitatorResponseParser.parseRoleplayAIResponse(narratorResponse)
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
                                processAboveTableRound("", updatedHistory)
                            }
                        }else {

                            val slotId = nextSlot ?: run {
                                Log.w("AI_CYCLE", "nextSlot is null, retrying.")
                                processAboveTableRound(input, chatHistory, retryCount + 1)
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
                                processAboveTableRound(input, chatHistory, retryCount + 1)
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

                                if (activeSlotId != null) {
                                    setSlotTyping(sessionId, nextSlot!!, true)
                                }
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

                            val nsfwinstructions =  "/n - Be descriptive and explicit. The more sexual and erotic the better"

                            val nsfwPrompt = roleplayPrompt + nsfwinstructions

                            Log.d("AI_response", "History: $chatHistory")
                            val rpgPrompt = if (slotProfile.hiddenRoles == "GM"){
                                if (isNSFW){
                                    nsfwPrompt + gmPrompt + PromptBuilder.buildRPGLiteRules()
                                }else {
                                    roleplayPrompt + gmPrompt + PromptBuilder.buildRPGLiteRules()
                                }
                            }else{
                                if (isNSFW){
                                    nsfwPrompt + playerPrompt + PromptBuilder.buildRPGLiteRules()
                                }else {
                                    roleplayPrompt + playerPrompt + PromptBuilder.buildRPGLiteRules()
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
                                FacilitatorResponseParser.parseRoleplayAIResponse(roleplayResponse)
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
                                    if (activeSlotId != null) {
                                        setSlotTyping(sessionId, nextSlot!!, false)
                                    }
                                    saveSessionProfile(sessionProfile, sessionId)
                                }

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

    private fun processOnTableRPGRound(
        input: String,
        chatHistory: List<ChatMessage>,
        retryCount: Int = 0,
        maxRetries: Int = 2
    ) {
        if (retryCount > maxRetries) {
            Log.w(TAG, "Max retries reached, aborting activation round")
            activationRound = 0
            return
        }

        if (activationRound >= maxActivationRounds) return
        activationRound++

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
                    sessionProfile.slotRoster.forEach {
                        Log.d("DEBUG_SLOT", "slot=${it.slotId}, name=${it.name}, area=${it.lastActiveArea}, loc=${it.lastActiveLocation}")
                    }
                    sessionProfile.areas.forEach { area ->
                        area.locations.forEach { location ->
                            Log.d("DEBUG_LOC", "area='${area.name}', loc='${location.name}'")
                        }
                    }

                    val isRPG = sessionProfile.modeSettings["rpg"] == "true"
                    val gmSlot = sessionProfile.slotRoster.find { it.hiddenRoles == "GM" }
                    val gmSlotId = gmSlot?.slotId
                    val isGmPlayer = sessionProfile.userMap[userId]?.activeSlotId == gmSlotId
                    val act = sessionProfile.acts.getOrNull(sessionProfile.currentAct)

                    val gmLines = PromptBuilder.buildOnTableGMPrompt(
                        slotProfile = gmSlot!!,
                        act = act,
                        activeSlotId = activeSlotId,
                        sessionSummary = sessionProfile.sessionDescription + sessionProfile.secretDescription,
                        areas = if (sessionProfile.areas.isNullOrEmpty()) listOf("DM's") else sessionProfile.areas.map { it.name },
                        locations = locationMap,
                        sessionProfile = sessionProfile,
                        condensedCharacterInfo = condensedMap, // Map: slotId → summary
                        lastNonNarratorId = lastNonNarratorId,
                        validNextSlotIds = coLocatedSlotIds,
                        memories = memoriesMap,
                        chatHistory = historyString
                    )
                    val gmPrompt = gmLines + PromptBuilder.buildRPGLiteRules()
                    Log.d("rpgcheck", "its rpg game? $isRPG, the prompt $gmPrompt")

                    var activationResponse =
                        if (isRPG && !isGmPlayer){
                            withContext(Dispatchers.Main) {
                                setSlotTyping(sessionId, gmSlotId!!, true)
                            }
                            activationRound = 3
                            Facilitator.callActivationAI(gmPrompt,BuildConfig.OPENAI_API_KEY)
                        }else {
                            Facilitator.callActivationAI(activationPrompt,BuildConfig.OPENAI_API_KEY)
                        }

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
                            var activationResponse =
                                if (isRPG && !isGmPlayer){
                                    Facilitator.callActivationAI(gmPrompt,BuildConfig.MIXTRAL_API_KEY)
                                }else {
                                    Facilitator.callActivationAI(activationPrompt,BuildConfig.MIXTRAL_API_KEY)
                                }
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
                            roleplayResult = FacilitatorResponseParser.parseRoleplayAIResponse(obj1.toString())
                            activationResult = FacilitatorResponseParser.parseActivationAIResponse(obj2.toString(), sessionProfile.slotRoster)
                        } else {
                            activationResult = FacilitatorResponseParser.parseActivationAIResponse(obj1.toString(), sessionProfile.slotRoster)
                            roleplayResult = FacilitatorResponseParser.parseRoleplayAIResponse(obj2.toString())
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
                            val senderSlotProfile =
                                sessionProfile.slotRoster.find { it.slotId == gmSlotId }
                            val cleanedPose =
                                msg.pose?.filterValues { !it.isNullOrBlank() } // optional, if you want to clean out blanks
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

                            val speakerSlot =
                                sessionProfile.slotRoster.find { it.slotId == speakerSlotId }
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
                                            messageIds = mainMessage?.let { listOf(it.id) }
                                                ?: emptyList()
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
                            if (!gmSlotId.isNullOrBlank()) {
                                setSlotTyping(sessionId, gmSlotId!!, false)

                                saveSessionProfile(sessionProfile, sessionId)
                            }
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
                    if (playerSlot == null) {
                        Log.w("AI_CYCLE", "No player slot found. Aborting.")
                        return@withTimeoutOrNull
                    }
                    val playerArea = playerSlot.lastActiveArea
                    val playerLocation = playerSlot.lastActiveLocation

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
                                FacilitatorResponseParser.parseRoleplayAIResponse(narratorResponse)
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
                                processOnTableRPGRound("", updatedHistory)
                            }
                        }else {

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

                            Log.d("AI_response", "History: $chatHistory")
                            val nsfwinstructions =  "/n - Be descriptive and explicit. The more sexual and erotic the better"
                            val rpgLines = buildString {
                                if (!slotProfile.rpgClass.isNullOrBlank()) {
                                    appendLine("You are playing a tabletop-style RPG character. Roleplay accordingly. The following is your charactersheet:")
                                    appendLine("Class: ${slotProfile.rpgClass}")
                                    appendLine("Hidden Role: ${slotProfile.hiddenRoles ?: "Unknown"}")
                                    appendLine("Stats:")
                                    slotProfile.stats.forEach { (stat, value) ->
                                        appendLine("- $stat: $value")
                                    }
                                    appendLine(PromptBuilder.buildRPGLiteRules())
                                    appendLine("HP: ${slotProfile.hp} / ${slotProfile.maxHp}")
                                    appendLine("Defense: ${slotProfile.defense}")
                                    if (slotProfile.equipment.isNotEmpty()) {
                                        appendLine("Equipment: ${slotProfile.equipment.joinToString(", ")}")
                                    }
                                    appendLine("You must roleplay your class and personality, and respect your character’s strengths and weaknesses.")
                                }
                            }
                            val nsfwPrompt = roleplayPrompt + nsfwinstructions
                            val nsfwRPGPrompt = nsfwPrompt + rpgLines
                            val sfwRPGPrompt = roleplayPrompt + rpgLines
                            // Switch models as needed
                            val roleplayResponse =
                            if (isRPG){
                                    if (isNSFW)
                                        Facilitator.callMixtralApi(nsfwRPGPrompt, BuildConfig.MIXTRAL_API_KEY)
                                    else
                                        Facilitator.callOpenAiApi(sfwRPGPrompt, BuildConfig.OPENAI_API_KEY)
                            }else {
                                    if (isNSFW)
                                        Facilitator.callMixtralApi(
                                            nsfwPrompt,
                                            BuildConfig.MIXTRAL_API_KEY
                                        )
                                    else
                                        Facilitator.callOpenAiApi(
                                            roleplayPrompt,
                                            BuildConfig.OPENAI_API_KEY
                                        )
                            }

                            ensureActive()
                            Log.d("ai_cycle", "Roleplay Prompt is: $roleplayPrompt")
                            Log.d("AI_response", "Roleplay Response:\n$roleplayResponse")

                            // Parse AI output to ChatMessage list
                            val roleplayResult = try {
                                FacilitatorResponseParser.parseRoleplayAIResponse(roleplayResponse)
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
                    updateAvatarsFromMessage(messages.last(), sessionProfile.slotRoster, visibleAvatarSlotIds)
                }
            }
        }, { error ->
            Log.e(TAG, "history load failed", error)
        })
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
        val rpgSettingsJson = sessionProfile.modeSettings["rpg"]
        var isOnTable = false
        var isAboveTable = false
        if (!rpgSettingsJson.isNullOrBlank() && rpgSettingsJson.trim().startsWith("{")) {
            val rpgSettings = Gson().fromJson(rpgSettingsJson, RPGSettings::class.java)
            when (rpgSettings.perspective) {
                "onTable" -> isOnTable = true
                "aboveTable" -> isAboveTable = true
            }
        }
        val slotIdsList = sessionProfile.slotRoster.joinToString(", ") { it.slotId }
        if (messages.isEmpty() && !initialGreeting.isNullOrBlank()) {
            val greetingMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                senderId = "system",
                text = initialGreeting!!,
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
                processOnTableRPGRound(userInput, messages)
            }else if (isAboveTable){
                Log.d("ai_cycle", "proccessing isAboveTable")
                processAboveTableRound(userInput, messages)
            } else {
                Log.d("ai_cycle", "proccessing normal roleplay")
                processActivationRound(userInput, messages)
            }
        }

    }

    // save each message in sequence, update avatars/backgrounds if present
    suspend fun saveMessagesSequentially(
        messages: List<ChatMessage>,
        sessionId: String,
        chatId: String
    ) {
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
    fun updateVisibleAvatarSlots(
        senderId: String,
        visibleAvatarSlotIds: MutableList<String>,
        slotProfiles: List<SlotProfile>,
        avatarSlotLocked: BooleanArray,
        maxSlots: Int = 4
    ) {
        // Find the slot profile for the sender
        val senderProfile = slotProfiles.find { it.slotId == senderId }
        // Check if they have any poses available
        val hasPose = senderProfile?.outfits?.any { it.poseSlots.isNotEmpty() } == true
        if (!hasPose) {
            Log.d("avatardebug", "Sender $senderId has no poses; not displaying in avatar slots.")
            return
        }

        // 1. If already present, do nothing (pose updated elsewhere)
        if (visibleAvatarSlotIds.contains(senderId)) return

        // 2. Find first unlocked slot for insertion
        for (i in 0 until maxSlots) {
            if (!avatarSlotLocked[i]) {
                // Shift all unlocked slots above i up one, to make room
                for (j in (maxSlots - 1) downTo (i + 1)) {
                    if (!avatarSlotLocked[j]) {
                        visibleAvatarSlotIds[j] = visibleAvatarSlotIds[j - 1]
                    }
                }
                visibleAvatarSlotIds[i] = senderId
                Log.d("avatardebug", "Added $senderId to avatar slot $i")
                return
            }
        }
        Log.d("avatardebug", "No unlocked slot for $senderId; not displayed.")
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

    fun updatePosesOnSlotProfiles(
        poseMap: Map<String, String>,
        slotProfiles: List<SlotProfile>
    ): List<SlotProfile> {
        return slotProfiles.map { profile ->
            val newPose = poseMap[profile.slotId]
            if (newPose != null) profile.copy(pose = newPose) else profile
        }
    }

    fun updateAvatarsFromMessage(
        msg: ChatMessage,
        slotProfiles: List<SlotProfile>,
        visibleAvatarSlotIds: List<String>
    ) {
        if (isDestroyed || isFinishing) return
        if (msg.pose.isNullOrEmpty()) return
        Log.d("avatardebug", "updateAvatarsFromMessage Activated $visibleAvatarSlotIds")

        // For each avatar slot, try to show the corresponding character
        visibleAvatarSlotIds.take(avatarViews.size).forEachIndexed { index, slotId ->
            val poseName = msg.pose?.get(slotId)?.lowercase() ?: ""
            if (poseName.isBlank() || poseName == "clear" || poseName == "none") {
                avatarViews[index].setImageResource(R.drawable.silhouette) // Or .setImageDrawable(null)
                avatarViews[index].visibility = View.INVISIBLE
                return@forEachIndexed
            }
            val slotProfile = slotProfiles.find { it.slotId == slotId }
            val outfit = slotProfile?.outfits?.find { it.name.equals(slotProfile.currentOutfit, ignoreCase = true) }

            // ---- Fix: skip if pose list is empty or null ----
            if (outfit?.poseSlots.isNullOrEmpty()) {
                avatarViews[index].setImageResource(R.drawable.silhouette)
                avatarViews[index].visibility = View.INVISIBLE
                return@forEachIndexed
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

                    // 1. Track the last seen messageId globally or inside your class/scope
                    var lastMessageId: String? = null

                    SessionManager.listenMessages(sessionId) { newMessage ->
                        runOnUiThread {
                            // 2. Skip if messageId is null or already processed
                            val currentMessageId = newMessage.id ?: return@runOnUiThread
                            if (currentMessageId == lastMessageId) {
                                Log.d("MessageFilter", "Skipping duplicate message: $currentMessageId")
                                return@runOnUiThread
                            }

                            // 4. Check location and assign message to relevant slots
                            val messageArea = newMessage.area
                            val messageLocation = newMessage.location

                            val recipients = sessionProfile.slotRoster.filter { slot ->
                                slot.lastActiveArea == messageArea && slot.lastActiveLocation == messageLocation
                            }

                            recipients.forEach { slot ->
                                addToPersonalHistoryFirestore(sessionId, slot.slotId, newMessage)
                            }

                            updateButtonState(ButtonState.SEND)

                            // 5. Update lastMessageId
                            lastMessageId = currentMessageId
                        }
                    }

                }
            }
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
                    updateVisibleAvatarSlots(lastMsg.senderId, visibleAvatarSlotIds, sessionProfile.slotRoster, avatarSlotLocked, avatarViews.size)
                    Log.d("avatardebug", "Preparing visibleAvatarSlotIds: ${visibleAvatarSlotIds}")
                    updateAvatarsFromMessage(lastMsg, sessionProfile.slotRoster, visibleAvatarSlotIds)
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
