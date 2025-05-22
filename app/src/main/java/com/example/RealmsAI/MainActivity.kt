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
import com.example.RealmsAI.ai.buildSandboxFacilitatorPrompt
import com.example.RealmsAI.models.CharacterProfile
import com.example.RealmsAI.models.ChatMessage
import com.example.RealmsAI.models.ChatMode
import com.example.RealmsAI.models.SessionProfile
import com.example.RealmsAI.network.ModelClient
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Auth check
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            Log.d("SessionLanding", "Launching MainActivity with chatId=$chatId, sessionId=$sessionId")
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
        Log.d(TAG, "SESSION_PROFILE_JSON raw: $sessionProfileJson")

        // Parse sessionProfile directly
        sessionProfile = Gson().fromJson(sessionProfileJson, SessionProfile::class.java)

        // Use sessionProfile fields for UI
        chatTitleView.text = sessionProfile.title ?: "Untitled"
        val summary = sessionProfile.recentSummary ?: ""
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

        // Recycler setup
        chatRecycler.layoutManager = LinearLayoutManager(this)
        chatAdapter = ChatAdapter(mutableListOf()) { pos ->
            val msg = chatAdapter.getMessages()[pos]
            val emotions = mapOf(msg.sender to "neutral")
            onNewMessage(msg.sender, emotions)
            chatRecycler.smoothScrollToPosition(pos)
        }
        chatRecycler.adapter = chatAdapter

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

            Log.d(TAG, "About to call sendMessage: chatId=$chatId sessionId=$sessionId, userMsg=$userMsg")
            SessionManager.sendMessage(chatId!!, sessionId, userMsg)
            Log.d(TAG, "sendMessage called")
            sendToAI(text)
            messageEditText.text.clear()
        }
    }

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


    // On message received:
    fun onNewMessage(speakerSlot: String, emotions: Map<String, String>) {
        val now = System.currentTimeMillis()

        if (botSlotToAvatarIndex.containsKey(speakerSlot)) {
            // Speaker already has an assigned avatar slot
            val avatarIndex = botSlotToAvatarIndex[speakerSlot]!!
            if (avatarIndex == 0 || avatarIndex == 1) {
                // Update timestamp for front slots only
                frontSlotTimestamps[avatarIndex] = now
            }
            // Update all avatars with the new emotions provided
            updateAvatars(emotions)
            return
        }

        // Speaker not assigned yet — assign to the older front slot (0 or 1)
        val olderFrontSlot =
            if ((frontSlotTimestamps[0] ?: 0) < (frontSlotTimestamps[1] ?: 0)) 0 else 1

        // Find which bot is currently occupying the older front slot
        val displacedBot = botSlotToAvatarIndex.entries.find { it.value == olderFrontSlot }?.key

        // If a bot is displaced from front slot, move it to a back slot
        if (displacedBot != null) {
            // Find a free back slot that is not currently assigned
            val freeBackSlot =
                backSlots.find { backSlot -> botSlotToAvatarIndex.values.none { it == backSlot } }
                    ?: backSlots.minByOrNull { slot ->
                        // For now, just pick the first back slot as fallback
                        0L
                    } ?: backSlots[0]

            botSlotToAvatarIndex[displacedBot] = freeBackSlot
        }

        // Assign the new speaker to the freed older front slot
        botSlotToAvatarIndex[speakerSlot] = olderFrontSlot
        frontSlotTimestamps[olderFrontSlot] = now

        // Finally, update avatars with new emotions mapping
        updateAvatars(emotions)
    }

    fun updateAvatars(emotions: Map<String, String>) {
        // Example: update all avatarViews to visible (customize for your design)
        avatarViews.forEachIndexed { idx, imageView ->
            if (idx < sessionProfile.slotRoster.size) {
                imageView.visibility = View.VISIBLE
                // You might update the avatar here based on emotion
            } else {
                imageView.visibility = View.INVISIBLE
                imageView.setImageDrawable(null)
            }
        }
    }

    private fun cleanFacilitatorContent(rawContent: String): String {
        // Clean escaped newlines and quotes from JSON string if needed
        return rawContent
            .replace("\\n", "\n")
            .replace("\\\"", "\"")
            .trim()
    }
    /**
     * Fetches a CharacterProfile document from Firestore by its ID.
     * Call this from within a coroutine (e.g. in sendToAI).
     */
    private suspend fun fetchCharacterProfile(userId: String, charId: String): CharacterProfile =
        suspendCancellableCoroutine { cont ->
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

    private var facilitatorNotes: String = ""

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

        // Figure out which character (bot) to send to, and which persona (player/user)
        val botSlotInfo = sessionProfile.slotRoster.find { it.slot.startsWith("B") }
        val userSlotInfo = sessionProfile.slotRoster.find { it.slot.startsWith("P") }

        when (chatMode) {
            ChatMode.ONE_ON_ONE -> {
                if (botSlotInfo == null) {
                    Log.e(TAG, "No bot slot found in ONE_ON_ONE mode!")
                    return@launch
                }
                // Load full character info from Firestore, or use cached if you want to optimize
                val character = fetchCharacterProfile(botSlotInfo.id)

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
                // GROUP/SANDBOX flow
                // Build bot list JSON, etc. from sessionProfile.slotRoster
                val botProfilesJson = Gson().toJson(sessionProfile.slotRoster.filter { it.slot.startsWith("B") })

                val sandboxAi = buildSandboxAiPrompt(
                    userMessage = userInput,
                    recentHistory = historyStr,
                    botProfilesJson = botProfilesJson,
                    summaryJson = summariesJson,
                    facilitatorState = facilitatorNotes,
                    chatInfo = sessionProfile.recentSummary ?: "",
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

    override fun onCreateOptionsMenu(menu: Menu) =
        menuInflater.inflate(R.menu.main_menu, menu).let { true }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.clear_chat -> {
            chatAdapter.clearMessages()
            true
        }
        else -> super.onOptionsItemSelected(item)
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
}
