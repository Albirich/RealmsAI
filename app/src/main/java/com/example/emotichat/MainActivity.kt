package com.example.emotichat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import org.json.JSONObject

class MainActivity : BaseActivity() {

    private lateinit var chatAdapter: ChatAdapter
    private lateinit var messageEditText: EditText
    private lateinit var profile: ChatProfile

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Deserialize ChatProfile
        val json = intent.getStringExtra("CHAT_PROFILE_JSON")
            ?: throw IllegalStateException("No chat profile passed!")
        profile = Gson().fromJson(json, ChatProfile::class.java)

        // 2. Header: title + collapsible description
        findViewById<TextView>(R.id.chatTitle).text = profile.title
        val chatDesc      = findViewById<TextView>(R.id.chatDescription)
        chatDesc.text     = profile.description
        val descHeader    = findViewById<LinearLayout>(R.id.descriptionHeader)
        val descToggle    = findViewById<ImageView>(R.id.descriptionToggle)
        descHeader.setOnClickListener {
            if (chatDesc.visibility == View.GONE) {
                chatDesc.visibility = View.VISIBLE
                descToggle.setImageResource(R.drawable.ic_expand_less)
            } else {
                chatDesc.visibility = View.GONE
                descToggle.setImageResource(R.drawable.ic_expand_more)
            }
        }

        // 3. Load the two character slots
        val charIds = profile.characterIds
        // slot 1
        charIds.getOrNull(0)?.let { id ->
            val uriStr = loadAvatarUriForCharacter(id)
            findViewById<ImageView>(R.id.botAvatar1ImageView)
                .setImageURI(Uri.parse(uriStr))
            findViewById<TextView>(R.id.botName1TextView)
                .text = loadCharacterName(id)
        }
        // slot 2
        charIds.getOrNull(1)?.let { id ->
            val uriStr = loadAvatarUriForCharacter(id)
            findViewById<ImageView>(R.id.botAvatar2ImageView)
                .setImageURI(Uri.parse(uriStr))
            findViewById<TextView>(R.id.botName2TextView)
                .text = loadCharacterName(id)
        }

        // 4. RecyclerView + Adapter
        val chatId = profile.id
        messageEditText = findViewById(R.id.messageEditText)
        chatAdapter = ChatAdapter(mutableListOf())
        findViewById<RecyclerView>(R.id.chatRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter       = chatAdapter
        }

        // 5. Load any saved history
        loadChatSession(chatId).forEach { chatAdapter.addMessage(it) }

        // 6. Send button → generate AI replies for each slot
        findViewById<Button>(R.id.sendButton).setOnClickListener {
            onSendClicked(chatId)
        }
    }

    /** Reads the saved avatarUri string for a character ID */
    private fun loadAvatarUriForCharacter(charId: String): String {
        val prefs   = getSharedPreferences("characters", MODE_PRIVATE)
        val jsonStr = prefs.getString(charId, null) ?: return ""
        return JSONObject(jsonStr).optString("avatarUri", "")
    }

    /** Reads the saved name for a character ID */
    private fun loadCharacterName(charId: String): String {
        val prefs   = getSharedPreferences("characters", MODE_PRIVATE)
        val jsonStr = prefs.getString(charId, null) ?: return ""
        return JSONObject(jsonStr).optString("name", "")
    }

    private fun onSendClicked(chatId: String) {
        val text = messageEditText.text.toString().trim()
        if (text.isBlank()) {
            Toast.makeText(this, "Please type a message", Toast.LENGTH_SHORT).show()
            return
        }

        // a) show user message
        chatAdapter.addMessage(ChatMessage("You", text))

        // b) For each selected character, generate & show a response
        profile.characterIds.forEachIndexed { index, charId ->
            val label = loadCharacterName(charId)
            val resp  = generateAIResponse(charId, text)
            val emot  = detectEmotion(resp)
            val avatarUri = loadAvatarUriForCharacter(charId)

            chatAdapter.addMessage(ChatMessage(label, resp))

            // update the avatar image for this slot to reflect the new emotion
            val avatarView = when (index) {
                0 -> findViewById<ImageView>(R.id.botAvatar1ImageView)
                1 -> findViewById<ImageView>(R.id.botAvatar2ImageView)
                else -> null
            }
            avatarView?.setImageURI(Uri.parse(avatarUri))
        }

        messageEditText.text.clear()
        findViewById<RecyclerView>(R.id.chatRecyclerView)
            .smoothScrollToPosition(chatAdapter.itemCount - 1)
    }

    /** Dummy AI by charId – you can replace with your real logic */
    private fun generateAIResponse(botId: String, message: String): String = when (botId) {
        // if you have special logic for certain IDs, do it here
        else -> "<$botId> received: \"$message\""
    }

    /** Very naive emotion‐detector */
    private fun detectEmotion(message: String): String = when {
        message.contains("happy", ignoreCase = true) -> "happy"
        message.contains("sad",   ignoreCase = true) -> "sad"
        else                                         -> "neutral"
    }

    // ── Clearing & menu ──

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
        // persist the current chat under the same ID
        saveChatSession(
            chatId   = profile.id,
            title    = profile.title,
            messages = chatAdapter.getMessages()
        )
    }
}
