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
import kotlinx.coroutines.Dispatchers


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
        chatId        = intent.getStringExtra("CHAT_ID")
            ?: error("CHAT_ID missing")
        sessionId     = intent.getStringExtra("SESSION_ID")
            ?: error("SESSION_ID missing")
        fullProfilesJson = intent.getStringExtra("CHAT_PROFILE_JSON")
            ?: "{}"

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
            findViewById(R.id.botAvatar2ImageView)
        )
        // CHATMODE:
        chatMode = try {
            ChatMode.valueOf(profileObj.optString("mode", "SANDBOX"))
        } catch(e: Exception) {
            ChatMode.SANDBOX
        }


// 2) Extract the characterIds array
        val charArr = profileObj.optJSONArray("characterIds") ?: JSONArray()

// 3) Build your two helper lists **right here**, before the parser
        val charNames: List<String> = (0 until charArr.length()).map { idx ->
            val charId   = charArr.optString(idx)
            val charJson = getSharedPreferences("characters", Context.MODE_PRIVATE)
                .getString(charId, null)
                ?: return@map "B${idx+1}"
            JSONObject(charJson).optString("name", "B${idx+1}")
        }

        val charAvatarUris = (0 until charArr.length()).map { idx ->
            val charId = charArr.optString(idx)
            this.loadAvatarUriForCharacter(charId)
        }


        // Recycler setup
        chatRecycler.layoutManager = LinearLayoutManager(this)
        chatAdapter = ChatAdapter(mutableListOf()) { pos ->
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
                    val slotToName   = mutableMapOf("N0" to "Narrator")
                    val slotToAvatar = mutableMapOf<String,String>()

                    // iterate by index, not forEachIndexed
                    for (i in 0 until charIds.length()) {
                        val slot   = "B${i + 1}"
                        val charId = charIds.getString(i)

                        // suspend call is now legal inside this coroutine
                        val profile = fetchCharacterProfile(charId)
                        slotToName[slot]   = profile.name
                        slotToAvatar[slot] = profile.avatarUri ?: ""
                    }

                    // 5) Now that you have your look-ups, you can build your parser
                    parser = AIResponseParser(
                        chatAdapter  = chatAdapter,
                        chatRecycler = chatRecycler,
                        updateAvatar = { slot, emotion ->
                            // example: look up uri & set on the matching ImageView
                            val uri = slotToAvatar[slot]
                            val iv  = if (slot=="B1") avatarViews[0] else avatarViews[1]
                            if (!uri.isNullOrEmpty()) iv.setImageURI(Uri.parse(uri))
                        },
                        loadName = { slot -> slotToName[slot] ?: slot },
                        chatId    = chatId,
                        sessionId = sessionId,
                        chatMode  = chatMode
                    )
                }
            },
            onError = { e -> Log.e(TAG,"session setup failed",e) }
        )


        // Send button
        sendButton.setOnClickListener {
            val text = messageEditText.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener

            // 1) Immediately show it
            val userMsg = ChatMessage(sender = "You", messageText = text)
            chatAdapter.addMessage(userMsg)

            // 2) Persist it
            SessionManager.sendMessage(chatId, sessionId, userMsg)
            prefs = getSharedPreferences("characters", Context.MODE_PRIVATE)
            // 3) Kick off the AI
            sendToAI(text)

            messageEditText.text.clear()
        }
    }

    private fun sendToAI(userInput: String) = lifecycleScope.launch {
        Log.d(TAG, "sendToAI() called with input: $userInput")
        // 1) Build common history string
        val historyStr = chatAdapter.getMessages()
            .joinToString("\n") { "${it.sender}: ${it.messageText}" }
        // if you have N characters in this chat profile, slots are B1..BN:
        val charArr = JSONObject(fullProfilesJson)
            .optJSONArray("characterIds") ?: JSONArray()
        val defaultSlots = (1..charArr.length())
            .map { idx -> "B$idx" }

        when (chatMode) {
            ChatMode.ONE_ON_ONE -> {
                // ---- Fetch the single character profile from Firestore ----
                val charId = JSONObject(fullProfilesJson)
                    .optJSONArray("characterIds")
                    ?.getString(0)
                    .orEmpty()

                val character = fetchCharacterProfile(charId)  // suspend helper

                // ---- 2a) Lightweight facilitator step ----
                val oneFacPrompt = buildOneOnOneFacilitatorPrompt(
                    userInput        = userInput,
                    history          = historyStr,
                    facilitatorState = facilitatorNotes,
                    character        = character
                )

                Log.d(TAG, "One-on-One FAC prompt:\n$oneFacPrompt")
                val facJson = ModelClient.callModel(
                    promptJson     = oneFacPrompt,
                    forFacilitator = true,
                    openAiKey      = BuildConfig.OPENAI_API_KEY,
                    mixtralKey     = BuildConfig.MIXTRAL_API_KEY
                )
                Log.d(TAG, "Facilitator response JSON: $facJson")
                facilitatorNotes = facJson.optString("notes", "")

                // ---- 2b) Character response step ----
                val oneAiPrompt = buildOneOnOneAiPrompt(
                    userInput        = userInput,
                    history          = historyStr,
                    facilitatorNotes = facilitatorNotes,
                    character        = character
                )
                Log.d(TAG, "One-on-One AI prompt:\n$oneAiPrompt")
                // 1) Build the Mixtral chat-completion JSON
                val chatPayload = JSONObject().apply {
                    put("model", "mistralai/mixtral-8x7b-instruct")
                    put("messages", JSONArray().put(
                        JSONObject()
                            .put("role",    "system")
                            .put("content", oneAiPrompt)
                    ))
                    put("max_tokens", 300)
                }.toString()
                Log.d(TAG, "One-on-One payload JSON:\n$chatPayload")

                // 2) Send that JSON to the Mixtral path
                val aiJson = ModelClient.callModel(
                    promptJson     = chatPayload,
                    forFacilitator = false,
                    openAiKey      = BuildConfig.OPENAI_API_KEY,
                    mixtralKey     = BuildConfig.MIXTRAL_API_KEY
                )
                Log.d(TAG, "AI raw JSON: $aiJson")
                // Extract the assistant “content” and hand off to your parser
                val rawContent = aiJson
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
                Log.d(TAG, "Parsed rawContent: $rawContent")

                if (chatMode == ChatMode.ONE_ON_ONE) {
                    parser.handleOneOnOne(rawContent)
                } else {
                    parser.handle(rawContent)  // your existing group/sandbox handler
                }

            }

            else -> {
                // ---- Sandbox flow ----

                // 2a) Facilitator round
                val sandboxFac = buildSandboxFacilitatorPrompt(
                    userInput        = userInput,
                    history          = historyStr,
                    summariesJson    = summariesJson,
                    facilitatorState = facilitatorNotes,
                    availableSlots   = defaultSlots
                )
                val facJson = ModelClient.callModel(
                    promptJson     = sandboxFac,
                    forFacilitator = true,
                    openAiKey      = BuildConfig.OPENAI_API_KEY,
                    mixtralKey     = BuildConfig.MIXTRAL_API_KEY
                )
                Log.d(TAG, "Facilitator response JSON: $facJson")
                facilitatorNotes = facJson.optString("notes","")

                // parse out “activeBots”, falling back to defaultSlots if missing
                val activeBots = facJson
                    .optJSONArray("activeBots")
                    ?.let { arr ->
                        (0 until arr.length())
                            .mapNotNull { i -> arr.optString(i).takeIf(String::isNotBlank) }
                    }
                    ?: defaultSlots


                // 2b) AI chat round
                val activeProfilesJson = JSONArray(activeBots).toString()

                val sandboxAi = buildSandboxAiPrompt(
                    userInput          = userInput,
                    history            = historyStr,
                    activeProfilesJson = activeProfilesJson,
                    summariesJson      = summariesJson,
                    facilitatorNotes   = facilitatorNotes,
                    chatDescription    = chatDescription,
                    availableSlots     = activeBots
                )

                val sandboxPayload = JSONObject().apply {
                    put("model", "mistralai/mixtral-8x7b-instruct")
                    put("messages", JSONArray().put(
                        JSONObject()
                            .put("role",    "system")
                            .put("content", sandboxAi)
                    ))
                    put("max_tokens", 300)
                }.toString()

                val aiJson = ModelClient.callModel(
                    promptJson     = sandboxPayload,
                    forFacilitator = false,
                    openAiKey      = BuildConfig.OPENAI_API_KEY,
                    mixtralKey     = BuildConfig.MIXTRAL_API_KEY
                )


                Log.d(TAG, "AI raw JSON: $aiJson")
                val rawContent = aiJson
                    .optJSONArray("choices")
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
        FirebaseFirestore
            .getInstance()
            .collection("characters")
            .document(charId)
            .get()
            .addOnSuccessListener { doc ->
                val profile = doc.toObject(CharacterProfile::class.java)
                if (profile != null) cont.resume(profile,onCancellation = null)
                else              cont.resumeWithException(
                    RuntimeException("Character $charId not found")
                )
            }
            .addOnFailureListener { e ->
                cont.resumeWithException(e)
            }
    }