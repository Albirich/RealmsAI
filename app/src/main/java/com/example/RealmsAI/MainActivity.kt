package com.example.RealmsAI

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import com.example.RealmsAI.network.ModelClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resumeWithException
import com.example.RealmsAI.models.CharacterProfile
import com.example.RealmsAI.models.ChatMessage
import com.example.RealmsAI.models.ChatMode
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import java.util.UUID
import kotlin.text.isNullOrEmpty


class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val OPENROUTER_API_URL = "https://openrouter.ai/api/v1/chat/completions"
    }

    private lateinit var chatId: String
    private lateinit var fullProfilesJson: String
    private var facilitatorNotes: String = ""
    private var summariesJson: String = "[]"
    private lateinit var chatTitle: String
    private var chatDescription: String = ""
    private lateinit var avatarViews: List<ImageView>
    private lateinit var chatTitleView: TextView
    private lateinit var chatDescriptionView: TextView
    private lateinit var chatRecycler: RecyclerView
    private lateinit var messageEditText: EditText
    private lateinit var sendButton: Button
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var parser: AIResponseParser
    private lateinit var sessionId: String
    private lateinit var chatMode: ChatMode
    private lateinit var prefs: SharedPreferences
    private lateinit var charAvatarUris: List<String>
    private val botSlotToAvatarIndex = mutableMapOf<String, Int>()
    val frontSlotTimestamps = mutableListOf(0L, 0L)         // For slots 0 and 1
    val backSlots = listOf(2, 3)                            // Back slots

    // Shared OkHttpClient with logging
    private val client = OkHttpClient.Builder()
        .addInterceptor(
            HttpLoggingInterceptor { msg -> Log.d("HTTP-OpenAI", msg) }
                .apply { level = HttpLoggingInterceptor.Level.BODY }
        )
        .build()

    private fun loadAvatarUriForCharacter(charId: String): String {
        val prefs = getSharedPreferences("characters", Context.MODE_PRIVATE)
        val json  = prefs.getString(charId, null) ?: return ""
        return JSONObject(json).optString("avatarUri", "")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Auth guard
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        // Bind views
        chatTitleView = findViewById(R.id.chatTitle)
        chatDescriptionView = findViewById(R.id.chatDescription)
        chatRecycler = findViewById(R.id.chatRecyclerView)
        messageEditText = findViewById(R.id.messageEditText)
        sendButton = findViewById(R.id.sendButton)

        // Load intent data
        // Initialize chatId ASAP
        chatId = intent.getStringExtra("CHAT_ID") ?: error("CHAT_ID missing")

        // Initialize sessionId similarly
        sessionId = intent.getStringExtra("SESSION_ID") ?: error("SESSION_ID missing")

        // Initialize fullProfilesJson as discussed
        fullProfilesJson = intent.getStringExtra("SESSION_PROFILE_JSON") ?: "{}"

        val sessionProfileJson = intent.getStringExtra("SESSION_PROFILE_JSON") ?: "{}"
        val sessionProfileApiResponse = JSONObject(sessionProfileJson)
        val contentStr = sessionProfileApiResponse
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()

        val unescapedContent = contentStr
            .replace("\\n", "\n")
            .replace("\\\"", "\"")
            .trim()

        val sessionProfileObj = JSONObject(unescapedContent)
        val chatModeStr = sessionProfileObj.optString("chatMode", "SANDBOX")
        chatMode = try {
            ChatMode.valueOf(chatModeStr)
        } catch (e: Exception) {
            ChatMode.SANDBOX
        }

        Log.d(TAG, "Chat mode set early: $chatMode")

        Log.d(TAG, "Using chatId: $chatId")
        Log.d(TAG, "Using sessionId: $sessionId")


        // Header
        val profileObj = JSONObject(fullProfilesJson)
        chatTitle = profileObj.optString("title", "Untitled")
        chatDescription = profileObj.optString("description", "")
        chatTitleView.text = chatTitle
        if (chatDescription.isNotBlank()) {
            chatDescriptionView.text = chatDescription
            chatDescriptionView.visibility = View.VISIBLE
        } else chatDescriptionView.visibility = View.GONE

        // Avatars

        avatarViews = listOf(
            findViewById(R.id.botAvatar1ImageView),
            findViewById(R.id.botAvatar2ImageView),
            findViewById(R.id.botAvatar3ImageView),
            findViewById(R.id.botAvatar4ImageView)
        )


// 2) Extract the characterIds array
        val charArr = profileObj.optJSONArray("characterIds") ?: JSONArray()
        Log.d("Debug", "Character IDs in fullProfilesJson: $charArr")

// 3) Build your two helper lists **right here**, before the parser
        val charNames: List<String> = (0 until charArr.length()).map { idx ->
            val charId   = charArr.optString(idx)
            val charJson = getSharedPreferences("characters", Context.MODE_PRIVATE)
                .getString(charId, null)
                ?: return@map "B${idx+1}"
            JSONObject(charJson).optString("name", "B${idx+1}")
        }

        this.charAvatarUris = (0 until charArr.length()).map { idx ->
            val charId = charArr.optString(idx)
            this.loadAvatarUriForCharacter(charId)
        }



        // Recycler setup
        chatRecycler.layoutManager = LinearLayoutManager(this)
        chatAdapter = ChatAdapter(mutableListOf()) { pos ->
            val msg = chatAdapter.getMessages()[pos]
            Log.d(TAG, "onNewMessage callback: scrolling to position $pos, sender: ${msg.sender}")

            // Example: Update avatar based on the sender's latest message
            val emotions = mapOf(msg.sender to "neutral") // or track real emotion elsewhere
            onNewMessage(msg.sender, emotions)
            chatRecycler.smoothScrollToPosition(pos)
        }
        chatRecycler.adapter = chatAdapter

        // 1) First get or create the session
        SessionManager.getOrCreateSessionFor(

            chatId,
            onResult = { sid ->
                sessionId = sid

                // 2) In MainActivity, inside your SessionManager.getOrCreateSessionFor(onResult=…) block:
                lifecycleScope.launch(Dispatchers.Main) {
                    // a) Once, load whole history:
                    SessionManager.loadHistory(chatId, sessionId,
                        onResult = { history ->
                            chatAdapter.clearMessages()
                            history.forEach { chatAdapter.addMessage(it) }
                            chatRecycler.scrollToPosition(history.lastIndex)
                            // --- ADD THIS: Greeting logic
                            val profileObj = JSONObject(fullProfilesJson) // Full chat/session profile JSON
                            val greeting = profileObj.optString("greeting", "") // or whatever your greeting field is called
                            if (history.isEmpty() && greeting.isNotBlank()) {
                                Log.d("SessionStartup", "Greeting? history=${history.size}, greeting='$greeting'")
                                sendToAI(greeting)
                            }
                        },
                        onError = { e -> Log.e(TAG,"history load failed",e) }
                    )

                    // b) Then start listening for _new_ messages one‐by‐one:
                    SessionManager.listenMessages(chatId, sessionId) { msg ->
                        chatAdapter.addMessage(msg)
                        chatRecycler.smoothScrollToPosition(chatAdapter.itemCount - 1)
                    }

                    // 3) Pull your character IDs from the profile JSON
                    val charIds = JSONObject(fullProfilesJson)
                        .optJSONArray("characterIds") ?: JSONArray()


                    // prepare the lookup-tables
                    val slotToName = mutableMapOf("N0" to "Narrator")

                    for (i in 0 until charIds.length()) {
                        val slot = "B${i + 1}"
                        val charId = charIds.getString(i)
                        if (charId.isBlank()) continue

                        val profile = fetchCharacterProfile(charId)  // suspend function that fetches profile
                        slotToName[slot] = profile.name
                    }
                    val slotToAvatar = mutableMapOf<String,String>()

                    // iterate by index, not forEachIndexed
                    for (i in 0 until charIds.length()) {
                        val slot = "B${i + 1}"
                        val charId = charIds.getString(i)

                        // suspend call is now legal inside this coroutine
                        if (charId.isBlank()) {
                            Log.e("Debug", "Attempted to fetch character profile with empty ID — skipping!")
                            continue  // skip this iteration instead of return so other characters load
                        }

                        val profile = fetchCharacterProfile(charId)
                        slotToName[slot] = profile.name
                        slotToAvatar[slot] = profile.avatarUri ?: ""
                    }

                    val singleCharacterName = slotToName["B1"] ?: "B1"



                    // 5) Now that you have your look-ups, you can build your parser
                    parser = AIResponseParser(
                        chatAdapter = chatAdapter,
                        chatRecycler = chatRecycler,
                        updateAvatar = { slot, emotion -> /* your existing update avatar logic */ },
                        loadName = { slot ->
                            if (chatMode == ChatMode.ONE_ON_ONE) singleCharacterName else slotToName[slot] ?: slot
                        },
                        chatId = chatId,
                        sessionId = sessionId,
                        chatMode = chatMode,
                        onNewMessage = { speakerId, emotions ->
                        }
                    )
                }

            },
            onError = { e -> Log.e(TAG,"session setup failed",e) }
        )

        // Send button
        sendButton.setOnClickListener {
            val text = messageEditText.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener

            // 1) Immediately show it (now with unique ID!)
            val userMsg = ChatMessage(
                id = UUID.randomUUID().toString(),   // <-- THIS LINE
                sender = "You",
                messageText = text,
                timestamp = Timestamp.now()
            )
            chatAdapter.addMessage(userMsg)

            // 2) Persist it
            SessionManager.sendMessage(chatId, sessionId, userMsg)
            prefs = getSharedPreferences("characters", Context.MODE_PRIVATE)
            // 3) Kick off the AI
            sendToAI(text)

            messageEditText.text.clear()
        }

    }
    // Helper to get the last message timestamp for a given sender (bot slot)
    fun getLastMessageTimestampForSender(sender: String): Long {
        return chatAdapter.getMessages()
            .filter { it.sender == sender }
            .maxOfOrNull { it.timestamp as Long } ?: 0L
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
        val olderFrontSlot = if ((frontSlotTimestamps[0] ?: 0) < (frontSlotTimestamps[1] ?: 0)) 0 else 1

        // Find which bot is currently occupying the older front slot
        val displacedBot = botSlotToAvatarIndex.entries.find { it.value == olderFrontSlot }?.key

        // If a bot is displaced from front slot, move it to a back slot
        if (displacedBot != null) {
            // Find a free back slot that is not currently assigned
            val freeBackSlot = backSlots.find { backSlot -> botSlotToAvatarIndex.values.none { it == backSlot } }
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


    // Updates the avatarViews according to the botSlotToAvatarIndex and emotions map
    fun updateAvatars(emotions: Map<String, String>) {
        avatarViews.forEachIndexed { idx, imageView ->
            if (idx < charAvatarUris.size) {
                val uri = charAvatarUris[idx]
                if (!uri.isNullOrEmpty()) {
                    imageView.visibility = View.VISIBLE
                    imageView.setImageURI(Uri.parse(uri))
                } else {
                    imageView.visibility = View.INVISIBLE
                    imageView.setImageDrawable(null)
                }
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

    private fun sendToAI(userInput: String) = lifecycleScope.launch {
        Log.d(TAG, "sendToAI() called with input: $userInput")

        // 1) Build common history string
        val historyStr = chatAdapter.getMessages()
            .joinToString("\n") { "${it.sender}: ${it.messageText}" }

        // Parse the fullProfilesJson raw response to extract the actual session data from 'content'
        if (!this@MainActivity::fullProfilesJson.isInitialized) {
            Log.e(TAG, "fullProfilesJson is not initialized before sendToAI call!")
            return@launch
        }

        val outerJson = JSONObject(fullProfilesJson)
        val contentStr = outerJson.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?: ""

        if (contentStr.isBlank()) {
            Log.e(TAG, "Content string from facilitator response is empty!")
            return@launch
        }

        val contentJson = JSONObject(contentStr)

        // Extract chatMode and update
        val chatModeStr = contentJson.optString("chatMode", "SANDBOX")
        chatMode = try {
            ChatMode.valueOf(chatModeStr)
        } catch (e: Exception) {
            ChatMode.SANDBOX
        }
        Log.d(TAG, "Extracted and parsed chatMode: $chatMode")

        // Extract character IDs from characterSummaries array
        val charArr = contentJson.optJSONArray("characterSummaries")
            ?.let { summaries ->
                JSONArray().apply {
                    for (i in 0 until summaries.length()) {
                        val charId = summaries.optJSONObject(i)?.optString("id")
                        if (!charId.isNullOrBlank()) {
                            put(charId)
                        }
                    }
                }
            } ?: JSONArray()

        // Map slots for characters B1, B2, ...
        val defaultSlots = (1..charArr.length()).map { idx -> "B$idx" }

        when (chatMode) {
            ChatMode.ONE_ON_ONE -> {
                if (charArr.length() == 0) {
                    Log.e(TAG, "No character IDs found for ONE_ON_ONE mode!")
                    return@launch
                }

                val charId = charArr.optString(0).orEmpty()
                if (charId.isBlank()) {
                    Log.e(TAG, "Character ID is blank for ONE_ON_ONE mode!")
                    return@launch
                }

                val character = fetchCharacterProfile(charId)  // suspend helper

                // ---- Facilitator prompt ----
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

                // ---- AI prompt ----
                val oneAiPrompt = buildOneOnOneAiPrompt(
                    userInput = userInput,
                    history = historyStr,
                    facilitatorNotes = facilitatorNotes,
                    character = character
                )
                Log.d(TAG, "One-on-One AI prompt:\n$oneAiPrompt")

                val chatPayload = JSONObject().apply {
                    put("model", "mistralai/mixtral-8x7b-instruct")
                    put(
                        "messages", JSONArray().put(
                            JSONObject()
                                .put("role", "system")
                                .put("content", oneAiPrompt)
                        )
                    )
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
                // ---- Sandbox flow ----
                val sandboxFac = buildSandboxFacilitatorPrompt(
                    userInput = userInput,
                    history = historyStr,
                    summariesJson = summariesJson,
                    facilitatorState = facilitatorNotes,
                    availableSlots = defaultSlots
                )
                val facJson = ModelClient.callModel(
                    promptJson = sandboxFac,
                    forFacilitator = true,
                    openAiKey = BuildConfig.OPENAI_API_KEY,
                    mixtralKey = BuildConfig.MIXTRAL_API_KEY
                )
                Log.d(TAG, "Chat mode: $chatMode")
                Log.d(TAG, "Facilitator prompt:\n$sandboxFac")

                facilitatorNotes = facJson.optString("notes", "")
                val contentStrSandbox = facJson.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()

                val sessionProfileObjSandbox = if (contentStrSandbox.isNotBlank()) JSONObject(contentStrSandbox) else null
                val chatModeStrSandbox = sessionProfileObjSandbox?.optString("chatMode", "SANDBOX") ?: "SANDBOX"
                chatMode = try {
                    ChatMode.valueOf(chatModeStrSandbox)
                } catch (e: Exception) {
                    ChatMode.SANDBOX
                }

                Log.d(TAG, "Extracted and parsed chatMode: $chatMode")

                val activeBots = facJson.optJSONArray("activeBots")
                    ?.let { arr ->
                        (0 until arr.length())
                            .mapNotNull { i -> arr.optString(i).takeIf(String::isNotBlank) }
                    }
                    ?: defaultSlots

                val activeProfilesJson = JSONArray(activeBots).toString()

                val sandboxAi = buildSandboxAiPrompt(
                    userInput = userInput,
                    history = historyStr,
                    activeProfilesJson = activeProfilesJson,
                    summariesJson = summariesJson,
                    facilitatorNotes = facilitatorNotes,
                    chatDescription = chatDescription,
                    availableSlots = activeBots
                )

                val sandboxPayload = JSONObject().apply {
                    put("model", "mistralai/mixtral-8x7b-instruct")
                    put(
                        "messages", JSONArray().put(
                            JSONObject()
                                .put("role", "system")
                                .put("content", sandboxAi)
                        )
                    )
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
}

/**
 * Fetches a CharacterProfile document from Firestore by its ID.
 * Call this from within a coroutine (e.g. in sendToAI).
 */

private suspend fun fetchCharacterProfile(charId: String): CharacterProfile =
    suspendCancellableCoroutine { cont ->
        if (charId.isBlank()) {
            cont.resumeWithException(IllegalArgumentException("Invalid character ID: '$charId'"))
            return@suspendCancellableCoroutine
        }

        FirebaseFirestore
            .getInstance()
            .collection("characters")
            .document(charId)
            .get()
            .addOnSuccessListener { doc ->
                val profile = doc.toObject(CharacterProfile::class.java)
                if (profile != null) cont.resume(profile, onCancellation = null)
                else cont.resumeWithException(
                    RuntimeException("Character $charId not found")
                )
            }
            .addOnFailureListener { e ->
                cont.resumeWithException(e)
            }
    }

