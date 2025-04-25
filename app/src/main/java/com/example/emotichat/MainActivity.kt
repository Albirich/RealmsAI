package com.example.emotichat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
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

       // Deserialize profile
        val json = intent.getStringExtra("CHAT_PROFILE_JSON")
            ?: throw IllegalStateException("No chat profile passed!")
        profile = Gson().fromJson(json, ChatProfile::class.java)

        // Title & collapsible description
        findViewById<TextView>(R.id.chatTitle).text = profile.title
        val chatDesc   = findViewById<TextView>(R.id.chatDescription)
        chatDesc.text  = profile.description
        findViewById<LinearLayout>(R.id.descriptionHeader)
            .setOnClickListener {
                if (chatDesc.visibility == View.GONE) {
                    chatDesc.visibility = View.VISIBLE
                    findViewById<ImageView>(R.id.descriptionToggle)
                        .setImageResource(R.drawable.ic_expand_less)
                } else {
                    chatDesc.visibility = View.GONE
                    findViewById<ImageView>(R.id.descriptionToggle)
                        .setImageResource(R.drawable.ic_expand_more)
                }
            }

        // Load avatars for the two slots
        profile.characterIds.getOrNull(0)?.let { id ->
            val uri = loadAvatarUriForCharacter(id)
            findViewById<ImageView>(R.id.botAvatar1ImageView)
                .setImageURI(Uri.parse(uri))
        }
        profile.characterIds.getOrNull(1)?.let { id ->
            val uri = loadAvatarUriForCharacter(id)
            findViewById<ImageView>(R.id.botAvatar2ImageView)
                .setImageURI(Uri.parse(uri))
        }

        // Recycler + Adapter
        messageEditText = findViewById(R.id.messageEditText)
        chatAdapter = ChatAdapter(mutableListOf())
        val recycler = findViewById<RecyclerView>(R.id.chatRecyclerView)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = chatAdapter

        // Load history
        loadChatSession(profile.id).forEach { chatAdapter.addMessage(it) }

        // Build the AI parser
        // in onCreate(), where you build the parser:
        parser = AIResponseParser(
            chatAdapter   = chatAdapter,
            chatRecycler  = recycler,
            updateAvatar  = { speakerId, emotion ->
                // 1) turn "B1".."B6" into 0..5
                val botIndex = speakerId
                    .removePrefix("B")
                    .toIntOrNull()
                    ?.minus(1)
                    ?: return@AIResponseParser

                // 2) look up the real characterId
                val charId = profile.characterIds.getOrNull(botIndex)
                    ?: return@AIResponseParser

                // 3) pick the right ImageView for that slot
                val iv = when (botIndex) {
                    0 -> findViewById<ImageView>(R.id.botAvatar1ImageView)
                    1 -> findViewById<ImageView>(R.id.botAvatar2ImageView)
                    //  2 -> findViewById<ImageView>(R.id.botAvatar3ImageView)
                    //  3 -> findViewById<ImageView>(R.id.botAvatar4ImageView)
                    //  4 -> findViewById<ImageView>(R.id.botAvatar5ImageView)
                    //  5 -> findViewById<ImageView>(R.id.botAvatar6ImageView)
                    else -> null
                }

                // 4) set the avatar URI
                iv?.setImageURI(
                    Uri.parse(loadAvatarUriForCharacter(charId))
                )
            },
            loadName      = { speakerId ->
                // same mapping from B# → index → characterId → name
                val botIndex = speakerId
                    .removePrefix("B")
                    .toIntOrNull()
                    ?.minus(1)
                    ?: return@AIResponseParser ""

                val charId = profile.characterIds.getOrNull(botIndex)
                    ?: return@AIResponseParser ""

                loadCharacterName(charId)
            }
        )


        val firstMessage = intent.getStringExtra("FIRST_MESSAGE")
        if (!firstMessage.isNullOrBlank()) {
            chatAdapter.addMessage(ChatMessage("Bot", firstMessage))
        }



        // Send button → hand off to parser
        findViewById<Button>(R.id.sendButton).setOnClickListener {
            val text = messageEditText.text.toString().trim()
            if (text.isBlank()) return@setOnClickListener

            // show user
            chatAdapter.addMessage(ChatMessage("You", text))
            messageEditText.text.clear()

            // TODO: replace with real AI call
            val raw = callYourAiService(text)
            parser.handle(raw)
        }
    }

    private fun loadAvatarUriForCharacter(charId: String): String {
        val prefs   = getSharedPreferences("characters", MODE_PRIVATE)
        val str     = prefs.getString(charId, null) ?: return ""
        return JSONObject(str).optString("avatarUri", "")
    }

    private fun loadCharacterName(charId: String): String {
        val prefs   = getSharedPreferences("characters", MODE_PRIVATE)
        val str     = prefs.getString(charId, null) ?: return ""
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

    /** Stub: replace with your actual AI integration */
    private fun callYourAiService(userInput: String): String {
        // Example stub: two back-to-back messages
        return """
      [B1,angry][normal] "Watch your tone—I'm not the one you should be yelling at."
      [B2,shy][delayed] "M-maybe we should all just calm down a little..."
    """.trimIndent()
    }
}
