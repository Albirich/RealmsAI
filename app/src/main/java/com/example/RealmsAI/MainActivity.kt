package com.example.RealmsAI

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.RealmsAI.FacilitatorResponseParser.extractAvatarSlots
import com.example.RealmsAI.ai.*
import com.example.RealmsAI.ai.Facilitator.parseActivationAIResponse
import com.example.RealmsAI.ai.PromptBuilder.buildRoleplayPrompt
import com.example.RealmsAI.models.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.util.UUID

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    // UI
    private lateinit var chatTitleView: TextView
    private lateinit var chatDescriptionView: TextView
    private lateinit var chatRecycler: RecyclerView
    private lateinit var messageEditText: EditText
    private lateinit var sendButton: Button
    private lateinit var topOverlay: FrameLayout
    private lateinit var chatOverlayContainer: FrameLayout
    private lateinit var toggleChatInputButton: ImageButton
    private lateinit var avatarViews: List<ImageView>
    private var currentAvatarMap: Map<Int, Pair<String?, String?>> = emptyMap()
    private var currentBackground: String? = null



    // State
    private lateinit var sessionProfile: SessionProfile
    private lateinit var chatMode: ChatMode
    private lateinit var chatAdapter: ChatAdapter

    private var chatId: String? = null
    private lateinit var sessionId: String

    // Slot/profile management
    private val slotIdToProfile = mutableMapOf<String, Any>()
    private lateinit var slotNameToSlotInfo: Map<String, SlotInfo>
    private var userPersonaProfile: PersonaProfile? = null
    private var initialGreeting: String? = null

    private var activationRound = 0
    private val maxActivationRounds = 3

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

        setContentView(R.layout.activity_main)

        // Load session profile
        sessionProfile = Gson().fromJson(intent.getStringExtra("SESSION_PROFILE_JSON") ?: "{}", SessionProfile::class.java)

        val playerPersonaId = sessionProfile.playerAssignments["player1"]
        userPersonaProfile = sessionProfile.personaProfiles.find { it.id == playerPersonaId }

        Log.d("initialization", "playerAssignments: ${sessionProfile.playerAssignments}")
        Log.d("initialization", "personaProfiles: ${sessionProfile.personaProfiles.map { it.id to it.name }}")
        Log.d("initialization", "userPersonaProfile: ${userPersonaProfile?.name}")

        initialGreeting = intent.getStringExtra("GREETING")
        Log.d("initialization", "greeting being loaded is:\n$initialGreeting")

        slotNameToSlotInfo = sessionProfile.slotRoster.associateBy { it.name }
        slotIdToProfile.clear()
        sessionProfile.slotRoster.forEach { slotIdToProfile[it.slot] = it }
        sessionProfile.personaProfiles.forEach { slotIdToProfile[it.id] = it }
        Log.d("initialization", "slotNameToSlotInfo: $slotNameToSlotInfo")
        Log.d("initialization", "slotIdToProfile: $slotIdToProfile")


        // Bind UI
        chatTitleView = findViewById(R.id.chatTitle)
        chatDescriptionView = findViewById(R.id.chatDescription)
        chatRecycler = findViewById(R.id.chatRecyclerView)
        messageEditText = findViewById(R.id.messageEditText)
        sendButton = findViewById(R.id.sendButton)
        topOverlay = findViewById(R.id.topOverlay)
        chatOverlayContainer = findViewById(R.id.chatOverlayContainer)
        toggleChatInputButton = findViewById(R.id.toggleChatInputButton)
        avatarViews = listOf(
            findViewById(R.id.botAvatar0ImageView),
            findViewById(R.id.botAvatar1ImageView),
            findViewById(R.id.botAvatar2ImageView),
            findViewById(R.id.botAvatar3ImageView)
        )

        sessionId = intent.getStringExtra("SESSION_ID") ?: error("SESSION_ID missing")
        chatMode = ChatMode.values().find { it.name == sessionProfile.chatMode } ?: ChatMode.SANDBOX

        // UI display
        chatTitleView.text = sessionProfile.title
        val summary = sessionProfile.sessionDescription ?: ""
        Log.d("initialization", "heres the whole summary: $summary")
        chatDescriptionView.text = if (summary.isNotBlank()) summary else ""
        chatDescriptionView.visibility = if (summary.isNotBlank()) View.VISIBLE else View.GONE

        // Adapter setup
        chatAdapter = ChatAdapter(
            mutableListOf(),
            { lastPos -> if (lastPos >= 0) chatRecycler.smoothScrollToPosition(lastPos) },
            sessionProfile.slotRoster,
            sessionProfile.personaProfiles,
            userPersonaProfile?.name ?: "You"
        )
        chatRecycler.layoutManager = LinearLayoutManager(this)
        chatRecycler.adapter = chatAdapter

        // Load avatars
        // First, hide all avatar views to start clean
        avatarViews.forEach { it.visibility = View.INVISIBLE }

        // Then, only show avatars for each slot in use
        sessionProfile.slotRoster.forEachIndexed { idx, slotInfo ->
            val profile = getProfileById(slotInfo.id)
            val avatarUri = getProfileAvatarUri(profile)
            if (!avatarUri.isNullOrBlank() && idx < avatarViews.size) {
                avatarViews[idx].setImageURI(Uri.parse(avatarUri))
                avatarViews[idx].visibility = View.VISIBLE
            }
        }


        // Entry mode: new or load
        when (intent.getStringExtra("ENTRY_MODE") ?: "CREATE") {
            "CREATE" -> {
                chatAdapter.clearMessages()
                activationRound = 0
                updateButtonState(ButtonState.INTERRUPT)
                sendToAI("") // Triggers the greeting to AI, but doesn't add a message to visible chat
            }
            "LOAD" -> loadSessionHistory()
        }

        // Toggle input visibility
        toggleChatInputButton.setOnClickListener {
            val isVisible = messageEditText.visibility == View.VISIBLE
            listOf(messageEditText, sendButton, chatRecycler, topOverlay, chatOverlayContainer).forEach {
                it.visibility = if (isVisible) View.GONE else View.VISIBLE
            }
        }

        // Send button logic
        sendButton.setOnClickListener {
            if (currentState == ButtonState.SEND) {
                val text = messageEditText.text.toString().trim()
                if (text.isNotEmpty()) {
                    updateButtonState(ButtonState.INTERRUPT)
                    sendMessageAndCallAI(text, displayInChat = true)
                    messageEditText.text.clear()
                }
            } else if (currentState == ButtonState.INTERRUPT) {
                interruptAILoop()
                updateButtonState(ButtonState.SEND)
            }
        }
    }

    // ========== AI Activation & Roleplay Loop ==========

    private fun processActivationRound(
        input: String,
        chatHistory: List<ChatMessage>
    ) {
        if (activationRound >= maxActivationRounds) return
        activationRound++

        val userPersonaName = userPersonaProfile?.name ?: "You"

        val historyWithGreeting: List<ChatMessage> =
            if (chatHistory.isEmpty() && !initialGreeting.isNullOrBlank()) {
                listOf(ChatMessage(
                    id = "greeting",
                    sender = userPersonaName,
                    messageText = initialGreeting!!,
                    timestamp = null
                ))
            } else chatHistory

        aiJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Build activation prompt with correct parameter names!
                val activationPrompt = PromptBuilder.buildActivationPrompt(
                    sessionProfile = sessionProfile,
                    chatHistory = buildTrimmedHistory(
                        historyWithGreeting, // chat history with greeting only if needed
                        maxChars = 800,
                        greeting = null,
                        userPersonaName = userPersonaName
                    )
                )
                Log.d("AI_CYCLE", "Activation Prompt:\n$activationPrompt")

                // 2. Call OpenAI (or fallback)
                val activationResponse = Facilitator.callActivationAI(
                    prompt = activationPrompt,
                    apiKey = BuildConfig.OPENAI_API_KEY
                )
                ensureActive()
                Log.d("AI_CYCLE", "Raw AI Response:\n$activationResponse")

                // 3. Parse activation AND updated area map
                val (activatedSlots, updatedAreas) = parseActivationAIResponse(activationResponse, sessionProfile.areas)

                // 4. Update and persist
                sessionProfile = sessionProfile.copy(areas = updatedAreas)

                Log.d("AI_CYCLE", "Updated areas: ${
                    updatedAreas.joinToString("\n") { area ->
                        val chars = area.locations.flatMap { it.characters }
                        "- ${area.name}: ${chars.joinToString(", ")}"
                    }
                }")

                saveSessionProfile(sessionProfile, sessionId)


                // 5. Proceed to roleplay AI
                if (activatedSlots.isNotEmpty()) {
                    val (charName, isNSFW) = activatedSlots.first()
                    val slotProfile = sessionProfile.slotRoster.find { it.name == charName }
                    if (slotProfile == null) {
                        Log.w("AI_CYCLE", "Bot name '$charName' not found in slotRoster")
                        activationRound = 0
                        withContext(Dispatchers.Main) { updateButtonState(ButtonState.SEND) }
                        return@launch
                    }
                    Log.d("AI_CYCLE","Activated slots: $slotProfile")


                    // Build roleplay prompt
                    val otherProfiles = sessionProfile.slotRoster.filter { it.name != charName }
                    val roleplayPrompt = buildRoleplayPrompt(
                        characterProfile = slotProfile,
                        otherProfiles = sessionProfile.slotRoster.filter { it.name != slotProfile.name },
                        sessionProfile = sessionProfile,
                        recentHistory = buildTrimmedHistory(historyWithGreeting, greeting = null, userPersonaName = userPersonaName),
                        currentAvatarMap = currentAvatarMap,
                        currentBackground = currentBackground
                    )
                    Log.d("AI_CYCLE", "Roleplay Prompt for ${slotProfile.name}:\n$roleplayPrompt")

                    // Call the appropriate AI (Mixtral for NSFW, OpenAI otherwise)
                    val roleplayResponse = if (isNSFW) {

                        Facilitator.callMixtralApi(roleplayPrompt, BuildConfig.MIXTRAL_API_KEY)
                    } else {

                        Facilitator.callOpenAiApi(roleplayPrompt, BuildConfig.OPENAI_API_KEY)
                    }
                    ensureActive()
                    Log.d("AI_CYCLE", roleplayResponse)

                    // Parse output blocks (ChatMessages)
                    val finalMessages = FacilitatorResponseParser.parseFacilitatorBlocks(roleplayResponse)
                    val (avatarMap, background, areaLocIds) = extractAvatarSlots(roleplayResponse)
                    Log.d("AI_CYCLE", "Final Messages: $finalMessages")
                    Log.d("AI_CYCLE", "this stuff too: $avatarMap, $background, $areaLocIds")
                    currentAvatarMap = avatarMap
                    currentBackground = background
                    withContext(Dispatchers.Main) {
                        displayMessagesSequentially(finalMessages)
                        FirestoreHelper.saveMessages(sessionId, finalMessages)
                        val updatedHistory = chatAdapter.getMessages()
                        if (activationRound < maxActivationRounds && isActive) {
                            processActivationRound(input = "", chatHistory = updatedHistory)
                        }
                    }
                } else {
                    activationRound = 0
                    withContext(Dispatchers.Main) { updateButtonState(ButtonState.SEND) }
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

    // ========== Helpers and Boilerplate (Unchanged) ==========

    private fun saveSessionProfile(sessionProfile: SessionProfile, sessionId: String) {
        // Firestore or wherever you want to persist (implement as in your project)
        // Example:
        // FirebaseFirestore.getInstance().collection("sessions").document(sessionId).set(sessionProfile)
    }

    private fun getProfileById(id: String): Any? {
        sessionProfile.personaProfiles.find { it.id == id }?.let { return it }
        sessionProfile.slotRoster.find { it.id == id }?.let { return it }
        return null
    }

    private fun getProfileAvatarUri(profile: Any?): String? = when (profile) {
        is PersonaProfile -> profile.avatarUri
        else -> null
    }

    private fun interruptAILoop() {
        aiJob?.cancel()
    }

    private fun loadSessionHistory() {
        SessionManager.loadHistory(sessionId, {
            chatAdapter.clearMessages()
            it.forEach(chatAdapter::addMessage)
            if (chatAdapter.itemCount > 0) chatRecycler.scrollToPosition(chatAdapter.itemCount - 1)
        }, {
            Log.e(TAG, "history load failed", it)
        })
        SessionManager.listenMessages(sessionId) {
            val lastPos = chatAdapter.itemCount - 1
            if (lastPos >= 0) chatRecycler.smoothScrollToPosition(lastPos)
        }
    }

    private fun sendMessageAndCallAI(
        text: String,
        displayInChat: Boolean = true
    ) {
        val senderName = userPersonaProfile?.name ?: "You"
        val userMsg = ChatMessage(UUID.randomUUID().toString(), senderName, text, Timestamp.now())
        if (displayInChat) {
            chatAdapter.addMessage(userMsg)
            chatRecycler.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }
        SessionManager.sendMessage(chatId ?: "", sessionId, userMsg)
        sendToAI(text)
    }

    private fun sendToAI(userInput: String) {
        activationRound = 0
        processActivationRound(userInput, chatAdapter.getMessages())
    }



    suspend fun displayMessagesSequentially(messages: List<ChatMessage>) {
        for (msg in messages) {
            Log.d("AI_CYCLE", "messages in waiting: $messages")
            delay(msg.delay)
            withContext(Dispatchers.Main) {
                Log.d("AI_CYCLE","Background url ${msg.backgroundImage}")
                if (!msg.backgroundImage.isNullOrBlank()) {
                    Glide.with(this@MainActivity)
                        .load(msg.backgroundImage)
                        .into(findViewById<ImageView>(R.id.backgroundImageView))
                }
                chatAdapter.addMessage(msg)
                Log.d("AI_CYCLE","Message posted: $msg")
                updateAvatarsFromMessage(msg) // <-- this is all you need for avatars!
                chatRecycler.smoothScrollToPosition(chatAdapter.itemCount - 1)
            }
        }
        withContext(Dispatchers.Main) {
            updateButtonState(ButtonState.SEND)
        }
    }

    fun updateAvatarsFromMessage(msg: ChatMessage) {
        Log.d("AI_CYCLE", "Image list: ${msg.imageUpdates}")
        msg.imageUpdates.forEach { (slotStr, url) ->
            val slot = slotStr.toIntOrNull() ?: return@forEach
            if (slot in avatarViews.indices) {
                if (!url.isNullOrBlank()) {
                    Glide.with(this)
                        .load(url)
                        .placeholder(R.drawable.default_01)
                        .into(avatarViews[slot])
                    avatarViews[slot].visibility = View.VISIBLE
                } else {
                    avatarViews[slot].setImageDrawable(null)
                    avatarViews[slot].visibility = View.INVISIBLE
                }
            }
        }
    }

    fun buildTrimmedHistory(messages: List<ChatMessage>, maxChars: Int = 500, greeting: String? = null, userPersonaName: String): String {
        val trimmed = mutableListOf<String>()
        var totalChars = 0

        if (!greeting.isNullOrBlank()) {
            val line = "$userPersonaName: $greeting"
            if (totalChars + line.length <= maxChars) {
                trimmed.add(line)
                totalChars += line.length
            }
        }

        for (msg in messages.asReversed()) {
            val line = "${msg.sender}: ${msg.messageText}"
            if (totalChars + line.length > maxChars) break
            trimmed.add(line)
            totalChars += line.length
        }
        return trimmed.asReversed().joinToString("\n")
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

    override fun onCreateOptionsMenu(menu: Menu) = menuInflater.inflate(R.menu.main_menu, menu).let { true }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.clear_chat -> {
            chatAdapter.clearMessages()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

}
