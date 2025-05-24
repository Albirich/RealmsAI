package com.example.RealmsAI

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.ai.AIResponseParser
import com.example.RealmsAI.ai.buildOneOnOneAiPrompt
import com.example.RealmsAI.ai.buildOneOnOneFacilitatorPrompt
import com.example.RealmsAI.ai.buildSandboxAiPrompt
import com.example.RealmsAI.models.*
import com.example.RealmsAI.network.ModelClient
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.coroutines.resumeWithException

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var parser: AIResponseParser
    private var chatId: String? = null
    private var characterId: String? = null
    private lateinit var sessionId: String
    private lateinit var chatMode: ChatMode
    private lateinit var chatTitleView: TextView
    private lateinit var chatDescriptionView: TextView
    private lateinit var chatRecycler: RecyclerView
    private lateinit var messageEditText: EditText
    private lateinit var sendButton: Button
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var avatarViews: List<ImageView>
    private val botSlotToAvatarIndex = mutableMapOf<String, Int>()
    private val frontSlotTimestamps = mutableListOf(0L, 0L)
    private val backSlots = listOf(2, 3)
    private lateinit var sessionProfile: SessionProfile
    private var summariesJson: String = "[]"
    private var facilitatorNotes: String = ""

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

        // Bind UI elements
        chatTitleView = findViewById(R.id.chatTitle)
        chatDescriptionView = findViewById(R.id.chatDescription)
        chatRecycler = findViewById(R.id.chatRecyclerView)
        messageEditText = findViewById(R.id.messageEditText)
        sendButton = findViewById(R.id.sendButton)
        avatarViews = listOf(
            findViewById(R.id.botAvatar1ImageView),
            findViewById(R.id.botAvatar2ImageView),
            findViewById(R.id.botAvatar3ImageView),
            findViewById(R.id.botAvatar4ImageView)
        )

        // Intent data
        chatId = intent.getStringExtra("CHAT_ID")
        characterId = intent.getStringExtra("CHARACTER_ID")
        sessionId = intent.getStringExtra("SESSION_ID") ?: error("SESSION_ID missing")
        val sessionProfileJson = intent.getStringExtra("SESSION_PROFILE_JSON") ?: "{}"
        sessionProfile = Gson().fromJson(sessionProfileJson, SessionProfile::class.java)

        // UI
        chatTitleView.text = sessionProfile.title ?: "Untitled"
        val summary = sessionProfile.sessionDescription ?: ""
        if (summary.isNotBlank()) {
            chatDescriptionView.text = summary
            chatDescriptionView.visibility = View.VISIBLE
        } else {
            chatDescriptionView.visibility = View.GONE
        }

        chatMode = try {
            ChatMode.valueOf(sessionProfile.chatMode)
        } catch (e: Exception) {
            ChatMode.SANDBOX
        }

        // RecyclerView and adapter
        chatAdapter = ChatAdapter(
            messages = mutableListOf(),
            onNewMessage = { msg ->
                // Scroll to latest
                val lastPos = chatAdapter.itemCount - 1
                if (lastPos >= 0) chatRecycler.smoothScrollToPosition(lastPos)
            }
        )
        chatRecycler.layoutManager = LinearLayoutManager(this)
        chatRecycler.adapter = chatAdapter

        // Avatars: update or hide based on slotRoster (if you store avatarUri in SlotInfo or CharacterProfile)
        val slotList = sessionProfile.slotRoster
        slotList.forEachIndexed { idx, slotInfo ->
            val charId = slotInfo.id
            loadAvatarUriForCharacter(charId) { uri ->
                if (!uri.isNullOrBlank() && idx < avatarViews.size) {
                    avatarViews[idx].setImageURI(Uri.parse(uri))
                    avatarViews[idx].visibility = View.VISIBLE
                } else if (idx < avatarViews.size) {
                    avatarViews[idx].setImageDrawable(null)
                    avatarViews[idx].visibility = View.INVISIBLE
                }
            }
        }

        val entryMode = intent.getStringExtra("ENTRY_MODE") ?: "CREATE"
        // Or use a boolean: val isNewSession = intent.getBooleanExtra("IS_NEW_SESSION", true)

        if (entryMode == "CREATE") {
            // Creating new chat/session (from ChatHub)
            // Do NOT load history, just create session objects, show intro message, etc.
            // (Optionally call a SessionManager.createNewSessionFor(chatId) helper if you have it)
            chatAdapter.clearMessages()
            // Optionally, add a "welcome" or "intro" message if you want.
            // No need to call loadHistory or listenMessages here.
        } else if (entryMode == "LOAD") {
            // Loading from SessionHistory
            SessionManager.loadHistory(chatId!!, sessionId, onResult = { history ->
                chatAdapter.clearMessages()
                history.forEach { chatAdapter.addMessage(it) }
                if (chatAdapter.itemCount > 0) {
                    chatRecycler.scrollToPosition(chatAdapter.itemCount - 1)
                }
            }, onError = { e -> Log.e(TAG, "history load failed", e) })

            // (Optionally) Start listenMessages to get new live messages if you want live updates
            SessionManager.listenMessages(chatId!!, sessionId) { msg ->
                chatAdapter.addMessage(msg)
                val lastPos = chatAdapter.itemCount - 1
                if (lastPos >= 0) chatRecycler.smoothScrollToPosition(lastPos)
            }
        }


        // Parser initialization
        parser = AIResponseParser(
            chatAdapter = chatAdapter,
            chatRecycler = chatRecycler,
            updateAvatar = ::updateAvatar,
            onNewMessage = ::onNewMessage,
            chatId = chatId ?: "UNKNOWN_CHAT",
            sessionId = sessionId ?: "UNKNOWN_SESSION",
            chatMode = chatMode
        )

        // Send button logic
        sendButton.setOnClickListener {
            Log.d(TAG, "Send button clicked!")
            val text = messageEditText.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener

            val userMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                sender = "You",
                messageText = text,
                timestamp = Timestamp.now()
            )
            val parentChatId = chatId
            if (parentChatId.isNullOrBlank() || sessionId.isNullOrBlank()) {
                Log.e(
                    TAG,
                    "No valid chatId or sessionId to send message, chatId=$chatId, sessionId=$sessionId"
                )
                return@setOnClickListener
            }

            // 2) Immediately show it for instant feedback
            chatAdapter.addMessage(userMsg)
            val lastPos = chatAdapter.itemCount - 1
            if (lastPos >= 0) chatRecycler.smoothScrollToPosition(lastPos)

            SessionManager.sendMessage(chatId!!, sessionId, userMsg)

            sendToAI(text)
            messageEditText.text.clear()
        }
    }

    // Avatar and message helpers
    private fun loadAvatarUriForCharacter(charId: String, onUriLoaded: (String?) -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return onUriLoaded(null)
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("characters")
            .document(charId)
            .get()
            .addOnSuccessListener { doc ->
                val uri = doc.getString("avatarUri")
                onUriLoaded(uri)
            }
            .addOnFailureListener { onUriLoaded(null) }
    }

    // Add your onNewMessage, updateAvatar, updateAvatars, sendToAI, etc. below (your current versions are fine!)
    // For example:
    fun onNewMessage(speakerSlot: String, emotions: Map<String, String>) {
        val now = System.currentTimeMillis()
        // ... (keep your current logic)
    }
    private fun updateAvatar(speakerId: String, emotion: String) {
        updateAvatars(mapOf(speakerId to emotion))
    }
    fun updateAvatars(emotions: Map<String, String>) {
        // ... (your existing logic)
    }

    // Chat history loader
    private fun loadHistory(
        chatId: String,
        sessionId: String,
        onResult: (List<ChatMessage>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        // You can re-use your SessionManager.loadHistory, or implement here
        SessionManager.loadHistory(chatId, sessionId, onResult, onError)
    }

    private fun sendToAI(userInput: String) = lifecycleScope.launch {
        Log.d(TAG, "sendToAI() called with input: $userInput")

        // 1) Build chat history
        val historyStr = buildTrimmedHistory(chatAdapter.getMessages(), 500)

        // Make sure sessionProfile is available
        if (!::sessionProfile.isInitialized) {
            Log.e(TAG, "sessionProfile is not initialized before sendToAI call!")
            return@launch
        }

        // Use chat mode from sessionProfile
        chatMode = try {
            ChatMode.valueOf(sessionProfile.chatMode)
        } catch (e: Exception) {
            ChatMode.SANDBOX
        }
        Log.d(TAG, "ChatMode in sendToAI: $chatMode")

        val botSlotInfo = sessionProfile.slotRoster.find { it.slot.startsWith("B") }
        Log.d(TAG, "Current slotRoster: ${sessionProfile.slotRoster}")

        when (chatMode) {
            ChatMode.ONE_ON_ONE -> {
                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                if (currentUserId.isNullOrBlank()) {
                    Log.e(TAG, "No current user ID found!")
                    return@launch
                }
                if (botSlotInfo == null || botSlotInfo.id.isNullOrBlank()) {
                    Log.e(TAG, "No bot slot or invalid charId!")
                    return@launch
                }
                val character = fetchCharacterProfile(userId = currentUserId, charId = botSlotInfo.id)

                // Facilitator prompt (for advanced stuff, e.g. updating notes)
                val oneFacPrompt = buildOneOnOneFacilitatorPrompt(
                    userInput = userInput,
                    history = historyStr,
                    facilitatorState = facilitatorNotes,
                    character = character
                )
                Log.d(TAG, "Facilitator prompt:\n$oneFacPrompt")

                val facJson = ModelClient.callModel(
                    promptJson = oneFacPrompt,
                    forFacilitator = true,
                    openAiKey = BuildConfig.OPENAI_API_KEY,
                    mixtralKey = BuildConfig.MIXTRAL_API_KEY
                )
                Log.d(TAG, "Facilitator response JSON: $facJson")

                val facContentStr = facJson.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
                val facContentJson = if (facContentStr.isNotBlank()) JSONObject(facContentStr) else null
                facilitatorNotes = facContentJson?.optString("notes", "") ?: ""

                // AI prompt
                val oneAiPrompt = buildOneOnOneAiPrompt(
                    userInput = userInput,
                    history = historyStr,
                    facilitatorNotes = facilitatorNotes,
                    characterName = character.name,
                    emotionTags = character.emotionTags,
                    maxTokens = 300
                )
                Log.d(TAG, "One-on-One AI prompt:\n$oneAiPrompt")

                val chatPayload = JSONObject().apply {
                    put("model", "mistralai/mixtral-8x7b-instruct")
                    put("messages", JSONArray().put(
                        JSONObject()
                            .put("role", "system")
                            .put("content", oneAiPrompt)
                    ))
                    put("max_tokens", 300)
                }.toString()
                Log.d(TAG, "One-on-One payload JSON:\n$chatPayload")

                val aiJson = ModelClient.callModel(
                    promptJson = chatPayload,
                    forFacilitator = false,
                    openAiKey = BuildConfig.OPENAI_API_KEY,
                    mixtralKey = BuildConfig.MIXTRAL_API_KEY
                )
                Log.d(TAG, "AI raw JSON: $aiJson")

                val rawContent = aiJson.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
                Log.d(TAG, "Parsed rawContent: $rawContent")

                parser.handleOneOnOne(rawContent)
            }
            else -> {
                val botSlotInfos = sessionProfile.slotRoster.filter { it.slot.startsWith("B") }
                val botProfilesJson = Gson().toJson(botSlotInfos)
                val sandboxAi = buildSandboxAiPrompt(
                    userMessage = userInput,
                    recentHistory = historyStr,
                    botProfilesJson = botProfilesJson,
                    summaryJson = summariesJson,
                    facilitatorState = facilitatorNotes,
                    chatInfo = sessionProfile.sessionDescription ?: "",
                    openSlots = sessionProfile.slotRoster.map { it.slot }
                )
                Log.d(TAG, "Sandbox AI prompt:\n$sandboxAi")

                val sandboxPayload = JSONObject().apply {
                    put("model", "mistralai/mixtral-8x7b-instruct")
                    put("messages", JSONArray().put(
                        JSONObject()
                            .put("role", "system")
                            .put("content", sandboxAi)
                    ))
                    put("max_tokens", 300)
                }.toString()

                val aiJson = ModelClient.callModel(
                    promptJson = sandboxPayload,
                    forFacilitator = false,
                    openAiKey = BuildConfig.OPENAI_API_KEY,
                    mixtralKey = BuildConfig.MIXTRAL_API_KEY
                )
                Log.d(TAG, "AI raw JSON: $aiJson")

                val rawContent = aiJson.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
                Log.d(TAG, "Parsed rawContent: $rawContent")

                parser.handle(rawContent)
            }
        }
    }
    fun buildTrimmedHistory(
        messages: List<ChatMessage>,
        maxChars: Int = 500 // ~2000 tokens
    ): String {
        var totalChars = 0
        val trimmed = mutableListOf<String>()
        // Traverse from latest to oldest
        for (msg in messages.asReversed()) {
            val line = "${msg.sender}: ${msg.messageText}"
            if (totalChars + line.length > maxChars) break
            trimmed.add(line)
            totalChars += line.length
        }
        // Now reverse to restore original order
        return trimmed.asReversed().joinToString("\n")
    }
    suspend fun fetchCharacterProfile(
        userId: String,
        charId: String
    ): CharacterProfile = suspendCancellableCoroutine { cont ->
        if (charId.isBlank() || userId.isBlank()) {
            cont.resumeWithException(IllegalArgumentException("Invalid userId/charId"))
            return@suspendCancellableCoroutine
        }
        FirebaseFirestore
            .getInstance()
            .collection("users")
            .document(userId)
            .collection("characters")
            .document(charId)
            .get()
            .addOnSuccessListener { doc ->
                val profile = doc.toObject(CharacterProfile::class.java)
                if (profile != null) cont.resume(profile, onCancellation = null)
                else cont.resumeWithException(
                    RuntimeException("Character $charId not found for user $userId")
                )
            }
            .addOnFailureListener { e ->
                cont.resumeWithException(e)
            }
    }

    override fun onCreateOptionsMenu(menu: Menu) =
        menuInflater.inflate(R.menu.main_menu, menu).let { true }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.clear_chat -> {
            chatAdapter.clearMessages()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
