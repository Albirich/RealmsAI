package com.example.emotichat

import android.content.Context
import android.content.Intent
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
import com.example.emotichat.ai.AIResponseParser
import com.google.gson.Gson
import org.json.JSONObject

class MainActivity : BaseActivity() {

    private lateinit var chatAdapter: ChatAdapter
    private lateinit var messageEditText: EditText
    private lateinit var profile: ChatProfile
    private lateinit var parser: AIResponseParser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1) Deserialize profile
        val json = intent.getStringExtra("CHAT_PROFILE_JSON")
            ?: throw IllegalStateException("No chat profile passed!")
        profile = Gson().fromJson(json, ChatProfile::class.java)

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
            val text = messageEditText.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener

            // a) show user
            chatAdapter.addMessage(ChatMessage("You", text))
            messageEditText.text.clear()

            // Optional: persist after each message
            persistChat()

            // b) call stub or real AI
            val raw = callYourAiService(text)
            parser.handle(raw)
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
