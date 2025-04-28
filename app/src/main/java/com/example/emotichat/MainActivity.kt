package com.example.emotichat

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.emotichat.ai.buildAiPrompt
import com.example.emotichat.ai.buildFacilitatorPrompt
import com.example.emotichat.ai.AIResponseParser
import com.google.gson.Gson
import org.json.JSONObject
import com.example.emotichat.ai.FakeAiService
import com.example.emotichat.ai.FakeFacilitatorService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory


class MainActivity : BaseActivity() {

    private lateinit var chatAdapter: ChatAdapter
    private lateinit var messageEditText: EditText
    private lateinit var profile: ChatProfile
    private lateinit var parser: AIResponseParser

    // A minimal placeholder state for testing
    private var currentFacilitatorState = """{"locations":{},"volumes":{}}"""
    private var currentActiveBotProfiles = emptyList<CharacterProfile>()
    private var summaryOfInactiveBots    = emptyList<Map<String,Any>>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1) Deserialize profile
        val json = intent.getStringExtra("CHAT_PROFILE_JSON")
            ?: throw IllegalStateException("No chat profile passed!")
        profile = Gson().fromJson(json, ChatProfile::class.java)

        fun loadFullCharacterProfile(id: String): CharacterProfile? {
            val prefs = getSharedPreferences("characters", MODE_PRIVATE)
            val json  = prefs.getString(id, null) ?: return null
            return Gson().fromJson(json, CharacterProfile::class.java)        }


        // e.g. at the top of onCreate()
        val allCharProfiles: List<CharacterProfile> = profile.characterIds.mapNotNull { charId ->
            // assume you have a loader that returns a CharacterProfile
            loadFullCharacterProfile(charId)
        }



        // 2) Title & description toggle
        findViewById<TextView>(R.id.chatTitle).text = profile.title
        val chatDesc = findViewById<TextView>(R.id.chatDescription)
        chatDesc.text = profile.description
        findViewById<LinearLayout>(R.id.descriptionHeader)
            .setOnClickListener {
                val toggle = findViewById<ImageView>(R.id.descriptionToggle)
                if (chatDesc.visibility == TextView.GONE) {
                    chatDesc.visibility = TextView.VISIBLE
                    toggle.setImageResource(R.drawable.ic_expand_less)
                } else {
                    chatDesc.visibility = TextView.GONE
                    toggle.setImageResource(R.drawable.ic_expand_more)
                }
            }

        // 3) Load static avatars
        profile.characterIds.getOrNull(0)?.let { charId ->
            val uri = loadAvatarUriForCharacter(charId)
            findViewById<ImageView>(R.id.botAvatar1ImageView)
                .setImageURI(Uri.parse(uri))
        }
        profile.characterIds.getOrNull(1)?.let { charId ->
            val uri = loadAvatarUriForCharacter(charId)
            findViewById<ImageView>(R.id.botAvatar2ImageView)
                .setImageURI(Uri.parse(uri))
        }

        // 4) Recycler + Adapter
        messageEditText = findViewById(R.id.messageEditText)
        chatAdapter     = ChatAdapter(mutableListOf())
        val recycler    = findViewById<RecyclerView>(R.id.chatRecyclerView)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter       = chatAdapter

        // 5) Load saved history
        loadChatSession(profile.id).forEach { chatAdapter.addMessage(it) }

        // 6) Setup the parser
        parser = AIResponseParser(
            chatAdapter   = chatAdapter,
            chatRecycler  = recycler,
            updateAvatar  = { speakerId, emotion ->
                val botIndex = speakerId.removePrefix("B").toIntOrNull()?.minus(1)
                    ?: return@AIResponseParser
                val charId = profile.characterIds.getOrNull(botIndex) ?: return@AIResponseParser
                val iv = when (botIndex) {
                    0 -> findViewById<ImageView>(R.id.botAvatar1ImageView)
                    1 -> findViewById<ImageView>(R.id.botAvatar2ImageView)
                    else -> null
                }
                iv?.setImageURI(Uri.parse(loadAvatarUriForCharacter(charId)))
            },
            loadName = { speakerId ->
                val botIndex = speakerId.removePrefix("B").toIntOrNull()?.minus(1)
                    ?: return@AIResponseParser ""
                val charId = profile.characterIds.getOrNull(botIndex) ?: return@AIResponseParser ""
                loadCharacterName(charId)
            }
        )

        // 7) Preload first message
        intent.getStringExtra("FIRST_MESSAGE")
            ?.takeIf { it.isNotBlank() }
            ?.let { chatAdapter.addMessage(ChatMessage("Bot", it)) }

        // 8) Send button
        findViewById<Button>(R.id.sendButton).setOnClickListener {
            val userInput = messageEditText.text.toString().trim()
            if (userInput.isEmpty()) return@setOnClickListener

            // 1) Show user & persist…
            chatAdapter.addMessage(ChatMessage("You", userInput))
            messageEditText.text.clear()
            persistChat()

// 2a) Build & show the facilitator→API prompt
            val historySnippet = chatAdapter.getMessages().takeLast(3)
                .joinToString("\n") { "${it.sender}: ${it.messageText}" }
            val facPrompt =
                buildFacilitatorPrompt(userInput, historySnippet, currentFacilitatorState)
            chatAdapter.addMessage(ChatMessage("Facilitator→API", facPrompt))

// 2b) Fake-call the facilitator and show its JSON
            val facRaw = FakeFacilitatorService.getResponse(facPrompt)
            chatAdapter.addMessage(ChatMessage("Facilitator", facRaw))

// 2c) **Parse** facilitator JSON into activeIds and notes
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val mapAdapter = moshi.adapter(Map::class.java)
            val facMap = mapAdapter.fromJson(facRaw) as Map<*, *>
            currentFacilitatorState = facMap["notes"] as String
            val activeIds = (facMap["activeBots"] as List<*>).map { it as String }

// 2d) **Rebuild** active/inactive lists **before** building AI prompt
            currentActiveBotProfiles = allCharProfiles.filter { it.characterId in activeIds }
            summaryOfInactiveBots = allCharProfiles
                .filter { it.characterId !in activeIds }
                .map { bot ->
                    mapOf(
                        "id" to bot.characterId,
                        "description" to bot.description
                    )
                }

// 3) Now build & show the **AI→API** prompt with the **updated** activeBots
            val fullJson = Gson().toJson(currentActiveBotProfiles)
            val summaryJson = Gson().toJson(summaryOfInactiveBots)
            val aiPrompt = buildAiPrompt(
                userInput = userInput,
                history = historySnippet,
                fullProfilesJson = fullJson,
                summariesJson = summaryJson,
                facilitatorNotes = currentFacilitatorState,
                chatDescription = profile.description
            )
            chatAdapter.addMessage(ChatMessage("AI→API", aiPrompt))

// 4) Fake AI reply and parse
            val raw = FakeAiService.getResponse(userInput)
            parser.handle(raw)

// 5) Persist again
            persistChat()
        }


    }

    /** Persists the current chat session */
    private fun persistChat() {
        val me = getSharedPreferences("user", Context.MODE_PRIVATE)
            .getString("userId", "") ?: ""
        saveChatSession(
            chatId   = profile.id,
            title    = profile.title,
            messages = chatAdapter.getMessages(),
            author   = me         // ← match the 'author' parameter name
        )
    }

    override fun onPause() {
        super.onPause()
        persistChat()
    }

    // ── Utility loaders ──

    private fun loadAvatarUriForCharacter(charId: String): String {
        val prefs = getSharedPreferences("characters", Context.MODE_PRIVATE)
        val str   = prefs.getString(charId, null) ?: return ""
        val obj   = JSONObject(str)
        return obj.optString("avatarLocalPath",
            obj.optString("avatarUri", ""))
    }

    private fun loadCharacterName(charId: String): String {
        val prefs = getSharedPreferences("characters", Context.MODE_PRIVATE)
        val str   = prefs.getString(charId, null) ?: return ""
        return JSONObject(str).optString("name", "")
    }

    // ── Menu & stub ──

    override fun onCreateOptionsMenu(menu: Menu) =
        menuInflater.inflate(R.menu.main_menu, menu).let { true }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            R.id.clear_chat -> {
                chatAdapter.clearMessages()
                clearChatHistoryFromPrefs()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun callYourAiService(userInput: String): String {
        // Your existing stub or real implementation
        return """
            [B1,angry] "Watch your tone—I'm not the one you should be yelling at."
            [B2,shy]   "M-maybe we should all just calm down a little..."
        """.trimIndent()
    }
}
