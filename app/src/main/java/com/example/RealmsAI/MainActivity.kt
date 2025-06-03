package com.example.RealmsAI

import android.content.Intent
import android.graphics.Color
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
import com.example.RealmsAI.ai.*
import com.example.RealmsAI.models.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.resumeWithException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    // UI Components
    private lateinit var chatTitleView: TextView
    private lateinit var chatDescriptionView: TextView
    private lateinit var chatRecycler: RecyclerView
    private lateinit var messageEditText: EditText
    private lateinit var sendButton: Button
    private lateinit var topOverlay: FrameLayout
    private lateinit var chatOverlayContainer: FrameLayout
    private lateinit var toggleChatInputButton: ImageButton
    private lateinit var avatarViews: List<ImageView>

    // Chat Variables
    private lateinit var sessionProfile: SessionProfile
    private lateinit var chatMode: ChatMode
    private lateinit var chatAdapter: ChatAdapter

    private var chatId: String? = null
    private var characterId: String? = null
    private lateinit var sessionId: String
    private var summariesJson: String = "[]"
    private var facilitatorNotes: String = ""

    // Avatar Management
    private val botSlotToAvatarIndex = mutableMapOf<String, Int>()
    private val frontSlotTimestamps = mutableListOf(0L, 0L)
    private val backSlots = listOf(2, 3)
    private val avatarEmotions = mutableMapOf<String, String>()
    private val recentSpeakers = mutableListOf<String>()
    private val slotIdToCharacterProfile = mutableMapOf<String, CharacterProfile>()
    private var currentAvatarMap: Map<Int, Pair<String?, String?>> = emptyMap()

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

        val greeting = intent.getStringExtra("GREETING")

        // Bind UI Elements
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

        // Load Intent Data
        chatId = intent.getStringExtra("CHAT_ID")
        characterId = intent.getStringExtra("CHARACTER_ID")
        sessionId = intent.getStringExtra("SESSION_ID") ?: error("SESSION_ID missing")
        val sessionProfileJson = intent.getStringExtra("SESSION_PROFILE_JSON") ?: "{}"
        sessionProfile = Gson().fromJson(sessionProfileJson, SessionProfile::class.java)
        chatMode = ChatMode.values().find { it.name == sessionProfile.chatMode } ?: ChatMode.SANDBOX
        val chatRoot = findViewById<LinearLayout>(R.id.chatRoot)
        if (chatId.isNullOrBlank()) {
            chatId = when (chatMode) {
                ChatMode.ONE_ON_ONE -> {
                    val botSlot = sessionProfile.slotRoster.find { it.slot == "B1" }
                    if (botSlot == null || botSlot.id.isNullOrBlank()) {
                        Toast.makeText(this, "Missing character ID for one-on-one chat.", Toast.LENGTH_SHORT).show()
                        return
                    }
                    "${botSlot.id}_${FirebaseAuth.getInstance().currentUser?.uid}"
                }
                else -> {
                    Toast.makeText(this, "CHAT_ID missing for non-one-on-one session.", Toast.LENGTH_SHORT).show()
                    return
                }
            }
        }

        // Load Characters
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        lifecycleScope.launch {
            for (slot in sessionProfile.slotRoster) {
                try {
                    val charProfile = fetchCharacterProfile(currentUserId, slot.id)
                    slotIdToCharacterProfile[slot.slot] = charProfile
                } catch (e: Exception) {
                    Log.e("AvatarLoader", "Failed to load charProfile for slot: ${slot.slot}, id: ${slot.id}")
                }
            }
            updateAvatarViews()
        }

        // UI Initialization
        chatTitleView.text = sessionProfile.title ?: "Untitled"
        val summary = sessionProfile.sessionDescription ?: ""
        chatDescriptionView.text = if (summary.isNotBlank()) summary else ""
        chatDescriptionView.visibility = if (summary.isNotBlank()) View.VISIBLE else View.GONE

        // Adapter and RecyclerView Setup
        chatAdapter = ChatAdapter(mutableListOf()) {
            val lastPos = chatAdapter.itemCount - 1
            if (lastPos >= 0) chatRecycler.smoothScrollToPosition(lastPos)
        }
        chatRecycler.layoutManager = LinearLayoutManager(this)
        chatRecycler.adapter = chatAdapter

        // Avatar Initialization
        sessionProfile.slotRoster.forEachIndexed { idx, slotInfo ->
            loadAvatarUriForCharacter(slotInfo.id) { uri ->
                if (!uri.isNullOrBlank() && idx < avatarViews.size) {
                    avatarViews[idx].setImageURI(Uri.parse(uri))
                    avatarViews[idx].visibility = View.VISIBLE
                } else if (idx < avatarViews.size) {
                    avatarViews[idx].setImageDrawable(null)
                    avatarViews[idx].visibility = View.INVISIBLE
                }
            }
        }

        // Entry Mode Handling
        when (intent.getStringExtra("ENTRY_MODE") ?: "CREATE") {
            "CREATE" -> chatAdapter.clearMessages()
            "LOAD" -> loadSessionHistory()
        }

        if ((intent.getStringExtra("ENTRY_MODE") ?: "CREATE") == "CREATE") {
            chatAdapter.clearMessages()
            if (!greeting.isNullOrBlank()) {
                sendMessageAndCallAI(greeting)
            }
        }

        toggleChatInputButton.setOnClickListener {
            val isVisible = messageEditText.visibility == View.VISIBLE
            listOf(messageEditText, sendButton, chatRecycler, topOverlay, chatOverlayContainer).forEach {
                it.visibility = if (isVisible) View.GONE else View.VISIBLE
            }
        }

        sendButton.setOnClickListener {
            if (currentState == ButtonState.SEND) {
                val text = messageEditText.text.toString().trim()
                if (text.isNotEmpty()) {
                    updateButtonState(ButtonState.INTERRUPT)
                    sendMessageAndCallAI(text)
                    messageEditText.text.clear()
                }
            } else if (currentState == ButtonState.INTERRUPT) {
                interruptAILoop()
                updateButtonState(ButtonState.SEND)
            }
        }
    }
    private fun interruptAILoop() {
        aiJob?.cancel()
        // Optionally, display a "Stopped" message or reset UI
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

    private fun sendMessageAndCallAI(text: String) {
        val userMsg = ChatMessage(UUID.randomUUID().toString(), "You", text, Timestamp.now())
        chatAdapter.addMessage(userMsg)
        chatRecycler.smoothScrollToPosition(chatAdapter.itemCount - 1)
        SessionManager.sendMessage(chatId!!, sessionId, userMsg)
        sendToAI(text)
    }

    private fun sendToAI(userInput: String) {
        activationRound = 0
            processActivationRound(userInput, chatAdapter.getMessages())
    }

    private fun processActivationRound(
        input: String,
        chatHistory: List<ChatMessage>,
        prevFacilitatorNotes: String = ""
    ) {
        if (activationRound >= maxActivationRounds) return
        activationRound++

        Log.d("FacilitatorResponseParser", "History for activation: $chatHistory")
        val activationPrompt = PromptBuilder.buildActivationPrompt(
            slotRoster = sessionProfile.slotRoster,
            sessionDescription = sessionProfile.sessionDescription,
            history = buildTrimmedHistory(chatHistory, maxChars = 800),
            slotIdToCharacterProfile = slotIdToCharacterProfile
        )

        aiJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                // STEP 1: Call OpenAI for character activation (cancellable)
                val openAIResponse = Facilitator.callActivationAI(activationPrompt, BuildConfig.OPENAI_API_KEY)
                ensureActive()

                withContext(Dispatchers.Main) { updateButtonState(ButtonState.INTERRUPT) }
                ensureActive()

                // STEP 2: Parse which slots should activate (fallback to Mixtral for NSFW/blocked)
                val activatedSlots: List<Pair<String, Boolean>> = when {
                    Facilitator.activationRefusedOrMalformed(openAIResponse) -> {
                        val fallbackPrompt = PromptBuilder.buildNSFWActivationPrompt(
                            slotRoster = sessionProfile.slotRoster,
                            history = buildTrimmedHistory(chatHistory, maxChars = 800)
                        )
                        val mixtralResponse = Facilitator.callMixtralApi(fallbackPrompt, BuildConfig.MIXTRAL_API_KEY)
                        ensureActive()
                        Facilitator.parseActivatedSlotsFromResponse(mixtralResponse, slotIdToCharacterProfile)
                    }
                    Facilitator.activationIsEmptyList(openAIResponse) -> {
                        activationRound = 0
                        return@launch
                    }
                    else -> Facilitator.parseActivatedSlotsFromResponse(openAIResponse, slotIdToCharacterProfile)
                }
                ensureActive()

                // STEP 3: For each activated character, build prompt and get reply
                val characterResponses = mutableListOf<ChatMessage>()
                val imageUrlList = sessionProfile.slotRoster.joinToString("\n") { slot ->
                    val profile = slotIdToCharacterProfile[slot.slot]
                    // Find the right outfit by name
                    val outfit = profile?.outfits?.find { it.name == profile.currentOutfit }
                    val poseImages = outfit?.poseSlots?.filter { !it.name.isNullOrBlank() && !it.uri.isNullOrBlank() }
                    val imageLines = poseImages?.joinToString(", ") { "${it.name} → ${it.uri}" } ?: ""
                    "- ${slot.name}: $imageLines"
                }

                for ((slotId, isNSFW) in activatedSlots) {
                    ensureActive()
                    val charProfile = slotIdToCharacterProfile[slotId] ?: continue
                    val charPrompt = if (isNSFW) {
                        PromptBuilder.buildNSFWRoleplayPrompt(
                            slotRoster = sessionProfile.slotRoster,
                            chatHistory = chatHistory,
                            sessionDescription = sessionProfile.sessionDescription,
                            slotIdToCharacterProfile = slotIdToCharacterProfile,
                            imageUrlList = imageUrlList
                        )
                    } else {
                        PromptBuilder.buildSFWRoleplayPrompt(
                            characterProfile = charProfile,
                            chatHistory = chatHistory,
                            sessionSummary = sessionProfile.sessionDescription
                        )
                    }

                    val aiResponseText = if (isNSFW) {
                        Facilitator.callMixtralApi(charPrompt, BuildConfig.MIXTRAL_API_KEY)
                    } else {
                        Facilitator.callOpenAiApi(charPrompt, BuildConfig.OPENAI_API_KEY)
                    }
                    ensureActive()

                    // Parse response(s) from AI and add to result list
                    if (isNSFW) {
                        val messages = FacilitatorResponseParser.parseFacilitatorBlocks(aiResponseText)
                        characterResponses.addAll(messages)
                    } else {
                        characterResponses.add(
                            ChatMessage(
                                id = UUID.randomUUID().toString(),
                                sender = charProfile.name,
                                messageText = aiResponseText,
                                timestamp = Timestamp.now(),
                                bubbleBackgroundColor = Color.parseColor(charProfile.bubbleColor ?: "#FFFFFF"),
                                bubbleTextColor = Color.parseColor(charProfile.textColor ?: "#000000")
                            )
                        )
                    }
                    withContext(Dispatchers.Main) { updateButtonState(ButtonState.WAITING) }
                    ensureActive()
                }

                // STEP 4: Build and send facilitator prompt (narration, slot images, avatars, etc.)
                val outfits = sessionProfile.slotRoster.associate { slotInfo ->
                    slotInfo.slot to (slotIdToCharacterProfile[slotInfo.slot]?.outfits?.firstOrNull()?.name ?: "default")
                }
                val poseImageUrls = sessionProfile.slotRoster.associate { slotInfo ->
                    val charProfile = slotIdToCharacterProfile[slotInfo.slot]
                    val currentOutfitName = charProfile?.currentOutfit ?: charProfile?.outfits?.firstOrNull()?.name ?: "default"
                    val outfit = charProfile?.outfits?.find { it.name == currentOutfitName }
                    // Build a Map<String, String> of poseName -> url (omit blank names or uris)
                    slotInfo.slot to (outfit?.poseSlots
                        ?.filter { !it.name.isNullOrBlank() && !it.uri.isNullOrBlank() }
                        ?.associate { it.name to (it.uri ?: "") }
                        ?: emptyMap()
                            )
                }


                val facilitatorPrompt = PromptBuilder.buildFacilitatorPrompt(
                    slotRoster = sessionProfile.slotRoster,
                    outfits = outfits,
                    poseImageUrls = poseImageUrls,
                    sessionDescription = sessionProfile.sessionDescription,
                    history = buildTrimmedHistory(chatHistory, maxChars = 800),
                    userInput = input,
                    backgroundImage = null,
                    availableColors = slotIdToCharacterProfile.mapValues { (_, p) -> p.bubbleColor ?: "#FFFFFF" },
                    botReplies = characterResponses
                )
                ensureActive()

                val facilitatorOutput = Facilitator.callOpenAiFacilitator(facilitatorPrompt, BuildConfig.OPENAI_API_KEY)
                ensureActive()

                val newAvatarMap = FacilitatorResponseParser.extractAvatarSlots(facilitatorOutput)
                Log.d("AvatarDebug", "Facilitator output avatar map: $newAvatarMap")
                currentAvatarMap = newAvatarMap

                val finalMessages = FacilitatorResponseParser.parseFacilitatorBlocks(facilitatorOutput)

                // STEP 5: Show messages in UI and save to Firestore (main thread)
                withContext(Dispatchers.Main) {
                    displayMessagesSequentially(finalMessages)
                    FirestoreHelper.saveMessages(sessionId, finalMessages)

                    // Recursively call next round if not maxed, and still active
                    val updatedHistory = chatAdapter.getMessages()
                    if (activationRound < maxActivationRounds && isActive) {
                        processActivationRound(input = "", chatHistory = updatedHistory)
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

    fun updateAvatarsFromMessage(msg: ChatMessage) {
        msg.imageUpdates.forEach { (slotStr, url) ->
            val slot = slotStr.toIntOrNull() ?: return@forEach
            if (slot in avatarViews.indices) {
                if (!url.isNullOrBlank()) {
                    Glide.with(this).load(url).placeholder(R.drawable.default_01).into(avatarViews[slot])
                    avatarViews[slot].visibility = View.VISIBLE
                }
            }
        }
    }
    suspend fun executeCancellableRequest(request: Request, client: OkHttpClient): String =
        suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() } // If coroutine is cancelled, cancel HTTP
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!cont.isActive) return
                        if (response.isSuccessful) {
                            cont.resume(response.body?.string() ?: "") {}
                        } else {
                            cont.resumeWithException(IOException("HTTP error"))
                        }
                    }
                }
            })
        }
    suspend fun displayMessagesSequentially(messages: List<ChatMessage>) {
        for (msg in messages) {
            delay(msg.delay)  // Wait for specified delay
            Log.d("AvatarDebug", "Updating avatars with imageUpdates: ${msg.imageUpdates}")
            withContext(Dispatchers.Main) {
                chatAdapter.addMessage(msg)
                updateAvatarsFromMessage(msg)
                chatRecycler.smoothScrollToPosition(chatAdapter.itemCount - 1)
            }
        }
        withContext(Dispatchers.Main) {
            updateButtonState(ButtonState.SEND)
        }
    }

    private fun loadAvatarUriForCharacter(charId: String, onUriLoaded: (String?) -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return onUriLoaded(null)
        FirebaseFirestore.getInstance()
            .collection("characters")
            .document(charId)
            .get()
            .addOnSuccessListener { onUriLoaded(it.getString("avatarUri")) }
            .addOnFailureListener { onUriLoaded(null) }
    }

    private fun updateAvatarViews() {
        for (i in avatarViews.indices) {
            if (i < recentSpeakers.size) {
                val speakerId = recentSpeakers[i]
                val emotion = avatarEmotions[speakerId] ?: "neutral"
                setAvatarImage(avatarViews[i], speakerId, emotion)
                avatarViews[i].visibility = View.VISIBLE
            } else {
                avatarViews[i].setImageDrawable(null)
                avatarViews[i].visibility = View.INVISIBLE
            }
        }
    }

    private fun setAvatarImage(imageView: ImageView, speakerId: String, emotion: String) {
        val character = slotIdToCharacterProfile[speakerId] ?: return
        val outfit = character.outfits.firstOrNull()
        // Try to find a poseSlot with a name matching `emotion`
        val poseSlot = outfit?.poseSlots?.firstOrNull {
            it.name.equals(emotion, ignoreCase = true)
        }
        val poseUri = poseSlot?.uri
        if (!poseUri.isNullOrBlank()) {
            Glide.with(imageView).load(poseUri).placeholder(R.drawable.default_01).into(imageView)
        } else {
            imageView.setImageResource(R.drawable.default_01)
        }
    }


    fun onNewMessage(speakerSlot: String, emotions: Map<String, String>) {
        val now = System.currentTimeMillis()
        if (chatMode == ChatMode.ONE_ON_ONE) {
            botSlotToAvatarIndex["B1"] = 0
            botSlotToAvatarIndex["P1"] = 1
            if (speakerSlot == "B1") frontSlotTimestamps[0] = now
            if (speakerSlot == "P1") frontSlotTimestamps[1] = now
        } else {
            if (!botSlotToAvatarIndex.containsKey(speakerSlot)) {
                val older = if (frontSlotTimestamps[0] <= frontSlotTimestamps[1]) 0 else 1
                botSlotToAvatarIndex.entries.find { it.value == older }?.key?.let {
                    val back = backSlots.find { b -> b !in botSlotToAvatarIndex.values } ?: backSlots[0]
                    botSlotToAvatarIndex[it] = back
                }
                botSlotToAvatarIndex[speakerSlot] = older
                frontSlotTimestamps[older] = now
            } else {
                val idx = botSlotToAvatarIndex[speakerSlot]!!
                if (idx == 0 || idx == 1) frontSlotTimestamps[idx] = now
            }
        }
        avatarEmotions.putAll(emotions)
        recentSpeakers.clear()
        recentSpeakers.addAll(botSlotToAvatarIndex.entries.sortedBy { it.value }.map { it.key })
        updateAvatarViews()
    }

    fun buildTrimmedHistory(messages: List<ChatMessage>, maxChars: Int = 500): String {
        var totalChars = 0
        val trimmed = mutableListOf<String>()
        for (msg in messages.asReversed()) {
            val line = "${msg.sender}: ${msg.messageText}"
            if (totalChars + line.length > maxChars) break
            trimmed.add(line)
            totalChars += line.length
        }
        return trimmed.asReversed().joinToString("\n")
    }

    suspend fun fetchCharacterProfile(userId: String, charId: String): CharacterProfile = suspendCancellableCoroutine { cont ->
        if (charId.isBlank() || userId.isBlank()) {
            cont.resumeWithException(IllegalArgumentException("Invalid userId/charId"))
            return@suspendCancellableCoroutine
        }
        FirebaseFirestore.getInstance().collection("characters").document(charId).get()
            .addOnSuccessListener {
                it.toObject(CharacterProfile::class.java)?.let { profile ->
                    cont.resume(profile, onCancellation = null)
                } ?: cont.resumeWithException(RuntimeException("Character $charId not found for user $userId"))
            }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    private fun updateButtonState(state: ButtonState) {
        currentState = state
        val sendButton = findViewById<Button>(R.id.sendButton)
        when (state) {
            ButtonState.SEND -> {
                sendButton.text = "Send"
                sendButton.isEnabled = true
                // Optionally change color/style
            }
            ButtonState.INTERRUPT -> {
                sendButton.text = "Interrupt"
                sendButton.isEnabled = true
                // Optionally: set a red background
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
