package com.example.emotichat

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

        // — Deserialize the ChatProfile passed via Intent
        val json = intent.getStringExtra("CHAT_PROFILE_JSON")
            ?: throw IllegalStateException("No chat profile passed!")
        profile = Gson().fromJson(json, ChatProfile::class.java)

        // — Title & collapsible description
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

        // — Load each character’s static avatar
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

        // — RecyclerView + Adapter
        messageEditText = findViewById(R.id.messageEditText)
        chatAdapter = ChatAdapter(mutableListOf())
        val recycler = findViewById<RecyclerView>(R.id.chatRecyclerView)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = chatAdapter

        // — Load saved chat history
        loadChatSession(profile.id).forEach { chatAdapter.addMessage(it) }

        // — Build the AI parser with emotion → avatar logic
        parser = AIResponseParser(
            chatAdapter  = chatAdapter,
            chatRecycler = recycler,
            updateAvatar = { speakerId, emotion ->
                // Map “B1”→0, etc.
                val botIndex = speakerId.removePrefix("B").toIntOrNull()?.minus(1) ?: return@AIResponseParser
                val charId   = profile.characterIds.getOrNull(botIndex) ?: return@AIResponseParser
                // Pick the right ImageView slot
                val iv = when (botIndex) {
                    0 -> findViewById<ImageView>(R.id.botAvatar1ImageView)
                    1 -> findViewById<ImageView>(R.id.botAvatar2ImageView)
                    else -> null
                }
                // Reload static or emotion-specific avatar (you can extend loader if you store per-emotion files)
                iv?.setImageURI(Uri.parse(loadAvatarUriForCharacter(charId)))
            },
            loadName = { speakerId ->
                val botIndex = speakerId.removePrefix("B").toIntOrNull()?.minus(1) ?: return@AIResponseParser ""
                val charId   = profile.characterIds.getOrNull(botIndex) ?: return@AIResponseParser ""
                loadCharacterName(charId)
            }
        )

        // — Preload a “first message” if provided
        intent.getStringExtra("FIRST_MESSAGE")?.takeIf { it.isNotBlank() }
            ?.let { chatAdapter.addMessage(ChatMessage("Bot", it)) }

        // — Send button: show user, clear input, call AI & hand off to parser
        findViewById<Button>(R.id.sendButton).setOnClickListener {
            val text = messageEditText.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            chatAdapter.addMessage(ChatMessage("You", text))
            messageEditText.text.clear()
            val raw = callYourAiService(text)  // replace with your real call
            parser.handle(raw)
        }
    }

    /** Load either the local-file path or the original URI you saved at creation time */
    private fun loadAvatarUriForCharacter(charId: String): String {
        val prefs = getSharedPreferences("characters", MODE_PRIVATE)
        val str   = prefs.getString(charId, null) ?: return ""
        val json  = JSONObject(str)
        return json.optString("avatarLocalPath",
            json.optString("avatarUri", ""))
    }

    /** Simply pulls the stored “name” field from your character JSON */
    private fun loadCharacterName(charId: String): String {
        val prefs = getSharedPreferences("characters", MODE_PRIVATE)
        val str   = prefs.getString(charId, null) ?: return ""
        return JSONObject(str).optString("name", "")
    }

    override fun onCreateOptionsMenu(menu: Menu) =
        menuInflater.inflate(R.menu.main_menu, menu).let { true }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.clear_chat -> {
            chatAdapter.clearMessages()
            clearChatHistoryFromPrefs()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onPause() {
        super.onPause()
        saveChatSession(
            chatId   = profile.id,
            title    = profile.title,
            messages = chatAdapter.getMessages()
        )
    }

    /** Stub—replace with your actual AI backend call */
    private fun callYourAiService(userInput: String): String {
        return """
            [B1,angry] "Watch your tone—I'm not the one you should be yelling at."
            [B2,shy]   "M-maybe we should all just calm down a little..."
        """.trimIndent()
    }
}
