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
import com.example.RealmsAI.ChatMessageParser
import com.example.RealmsAI.ai.*
import com.example.RealmsAI.models.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.resumeWithException

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
    private lateinit var parser: ChatMessageParser
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Auth check
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_main)

        val initialMessage = intent.getStringExtra("INITIAL_MESSAGE")
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

        parser = ChatMessageParser(
            chatAdapter, chatRecycler, ::onNewMessage,
            chatId ?: "UNKNOWN_CHAT", sessionId, chatMode
        )

        if (!initialMessage.isNullOrBlank()) {
            val aiMsg = ChatMessage(
                id = System.currentTimeMillis().toString(),
                sender = "AI", // Or actual bot/character name if you have it
                messageText = initialMessage,
                timestamp = Timestamp.now(),
                delay = 0L, // Initial message probably no delay
                bubbleBackgroundColor = Color.parseColor("#FFFFFF"), // default white
                bubbleTextColor = Color.parseColor("#000000"), // default black
                imageUpdates = mapOf(0 to null, 1 to null, 2 to null, 3 to null), // or set image URLs if you have them
                backgroundImage = null // or set background if you want
            )
            chatAdapter.addMessage(aiMsg)
        }

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

        // Parser Initialization
        parser = ChatMessageParser(
            chatAdapter, chatRecycler, ::onNewMessage,
            chatId ?: "UNKNOWN_CHAT", sessionId, chatMode
        )

        toggleChatInputButton.setOnClickListener {
            val isVisible = messageEditText.visibility == View.VISIBLE
            listOf(messageEditText, sendButton, chatRecycler, topOverlay, chatOverlayContainer).forEach {
                it.visibility = if (isVisible) View.GONE else View.VISIBLE
            }
        }

        sendButton.setOnClickListener {
            val text = messageEditText.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessageAndCallAI(text)
                messageEditText.text.clear()
            }
        }
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

    private var activationRound = 0
    private val maxActivationRounds = 3

    private fun sendToAI(userInput: String) {
        activationRound = 0
        processActivationRound(userInput, chatAdapter.getMessages())
    }

    // Recursive/looping function to process activation and facilitator steps
    private fun processActivationRound(
        input: String,
        chatHistory: List<ChatMessage>,
        prevFacilitatorNotes: String = ""
    ) {
        if (activationRound >= maxActivationRounds) return
        activationRound++

        // 1. Build activation prompt (selects which characters act this round)
        val activationPrompt = PromptBuilder.buildActivationPrompt(
            slotRoster = sessionProfile.slotRoster,
            sessionDescription = sessionProfile.sessionDescription,
            history = buildTrimmedHistory(chatHistory, maxChars = 800),
            slotIdToCharacterProfile = slotIdToCharacterProfile
        )

        // 2. Send activation prompt to correct models for each character
        // This should return List<ChatMessage> (one per active character)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val characterResponses: List<ChatMessage> = Facilitator.handleActivationPrompt(
                    activationPrompt = activationPrompt,
                    openAiKey = BuildConfig.OPENAI_API_KEY,
                    mixtralKey = BuildConfig.MIXTRAL_API_KEY,
                    slotIdToCharacterProfile = slotIdToCharacterProfile
                )
                // 3. Facilitator: write to Firestore, update UI, manage history, prepare next round
                withContext(Dispatchers.Main) {
                    // Write messages to Firestore (implement this function as needed)
                    FirestoreHelper.saveMessages(sessionId, characterResponses)

                    // Add new messages to chat UI
                    characterResponses.forEach { aiMsg -> chatAdapter.addMessage(aiMsg) }
                    chatRecycler.smoothScrollToPosition(chatAdapter.itemCount - 1)

                    // Merge new responses into chat history
                    val updatedHistory = chatAdapter.getMessages()

                    // Recursively call for next round (if not done)
                    if (activationRound < maxActivationRounds) {
                        processActivationRound(
                            input = "", // You might want to clear userInput after the first round
                            chatHistory = updatedHistory
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "AI call failed", e)
                // UI error handling, if desired
            }
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
        val poseUri = character.outfits.firstOrNull()?.poseUris?.get(emotion)
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

    override fun onCreateOptionsMenu(menu: Menu) = menuInflater.inflate(R.menu.main_menu, menu).let { true }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.clear_chat -> {
            chatAdapter.clearMessages()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
