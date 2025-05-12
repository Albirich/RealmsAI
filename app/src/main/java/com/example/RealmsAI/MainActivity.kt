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
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject

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

        // Firestore session + message listener
        SessionManager.getOrCreateSessionFor(
            chatId,
            onResult = { sid ->
                sessionId = sid

                parser = AIResponseParser(
                    chatAdapter  = chatAdapter,
                    chatRecycler = chatRecycler,



                    // 1) updateAvatar: (speakerId, emotion) -> Unit
                    updateAvatar = { slot, _emotion ->
                        if (slot == "N0") return@AIResponseParser
                        val idx = slot.removePrefix("B").toIntOrNull()?.minus(1) ?: return@AIResponseParser
                        if (idx in avatarViews.indices) {
                            avatarViews[idx].setImageURI(Uri.parse(charAvatarUris[idx]))
                        }
                    },

                    // 2) loadName: (speakerId) -> String
                    loadName = { slot ->
                        if (slot == "N0") {
                            "Narrator"
                        } else {
                            val idx = slot.removePrefix("B").toIntOrNull()?.minus(1) ?: return@AIResponseParser slot
                            charNames.getOrNull(idx) ?: slot
                        }
                    },

                    // 3) pass chatId & sessionId into the parser so it can Persist turns
                    chatId    = chatId,
                    sessionId = sessionId
                )

                SessionManager.listenMessages(chatId, sessionId) { fullHistory ->
                    runOnUiThread {
                        // 1) Clear out the adapter’s old list
                        chatAdapter.clearMessages()
                        // 2) Re-populate with every message
                        fullHistory.forEach { chatAdapter.addMessage(it) }
                        // 3) Scroll to the latest
                        chatRecycler.scrollToPosition(fullHistory.size - 1)
                    }
                }

            },
            onError = { e -> Log.e(TAG, "Session error", e) }
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
        // 1) Build common history
        val historyStr = chatAdapter.getMessages()
            .joinToString("\n") { "${it.sender}: ${it.messageText}" }

        when (chatMode) {
            ChatMode.ONE_ON_ONE -> {
                // — load your single CharacterProfile —
                val charId = JSONObject(fullProfilesJson)
                    .optJSONArray("characterIds")
                    ?.getString(0).orEmpty()
                val charJson = prefs.getString(charId, "")!!
                val character = Gson().fromJson(charJson, CharacterProfile::class.java)

                // — 2a) Facilitator step (optional for one-on-one) —
                val oneFacPrompt = buildOneOnOneFacilitatorPrompt(
                    userInput, historyStr, facilitatorNotes, character
                )
                val facRespJson = callModel(oneFacPrompt)
                facilitatorNotes = facRespJson.optString("notes", "")

                // — 2b) AI step —
                val oneAiPrompt = buildOneOnOneAiPrompt(
                    userInput, historyStr, facilitatorNotes, character
                )
                val aiRaw = callModel(oneAiPrompt)
                parser.handle(aiRaw)
            }

            else -> {
                // — SANDBOX / GROUP FLOW —
                // 2a) Facilitator
                val facPrompt = buildSandboxFacilitatorPrompt(
                    userInput, historyStr, summariesJson, facilitatorNotes, finalActiveBots
                )
                val facJson = callModel(facPrompt)
                facilitatorNotes = facJson.optString("notes","")
                finalActiveBots  = facJson
                    .optJSONArray("activeBots")?.let { ... } ?: finalActiveBots

                // 2b) AI
                val activeProfilesJson = buildActiveProfilesJson(finalActiveBots)
                val aiPrompt = buildSandboxAiPrompt(
                    userInput, historyStr,
                    activeProfilesJson, summariesJson,
                    facilitatorNotes, chatDescription,
                    finalActiveBots
                )
                val aiRaw = callModel(aiPrompt)
                parser.handle(aiRaw)
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
