package com.example.emotichat

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : BaseActivity() {
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var messageEditText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 0) Title & Avatars from Intent
        findViewById<TextView>(R.id.TitleTextView).text =
            intent.getStringExtra("CHAT_TITLE") ?: getString(R.string.title_default)

        // grab your two avatar ImageViews
        val botAvatar1ImageView: ImageView = findViewById(R.id.botAvatar1ImageView)
        val botAvatar2ImageView: ImageView = findViewById(R.id.botAvatar2ImageView)

        // NEW: always use your helper logic, defaulting to the "default" emotion
        val neutral1 = getAvatarResource("01", "default")
        updateEmotionImage(neutral1, botAvatar1ImageView)

        val neutral2 = getAvatarResource("02", "default")
        updateEmotionImage(neutral2, botAvatar2ImageView)


        // 1) RecyclerView + Adapter
        val chatId = intent.getStringExtra("CHAT_ID") ?: "chat_default"
        messageEditText = findViewById(R.id.messageEditText)

        chatAdapter = ChatAdapter(mutableListOf())
        findViewById<RecyclerView>(R.id.chatRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter       = chatAdapter
        }

        loadChatSession(chatId).forEach { chatAdapter.addMessage(it) }

        // 2) Send button
        findViewById<Button>(R.id.sendButton).setOnClickListener {
            onSendClicked(chatId)
        }
    }

    private fun onSendClicked(chatId: String) {
        val text = messageEditText.text.toString().trim()
        if (text.isBlank()) {
            Toast.makeText(this, "Please type a message", Toast.LENGTH_SHORT).show()
            return
        }

        // 2a) Add user message
        chatAdapter.addMessage(ChatMessage("User", text))

        // 2b) Generate & add AI responses

        fun generateAIResponse(botId: String, userMessage: String): String {
            return when (botId) {
                "01" -> "I am feeling really happy today!"
                "02" -> "I’m not doing so well; I feel a bit sad."
                else -> "I’m at a loss for words."
            }
        }

        listOf("01" to "Bot 1", "02" to "Bot 2").forEach { (botId, label) ->
            val resp    = generateAIResponse(botId, text)
            val emot    = detectEmotion(resp)
            val avatar  = getAvatarResource(botId, emot)
            chatAdapter.addMessage(ChatMessage(label, resp))
            updateEmotionImage(avatar, findViewById(
                if (botId == "01") R.id.botAvatar1ImageView else R.id.botAvatar2ImageView
            ))
        }

        messageEditText.text.clear()
        findViewById<RecyclerView>(R.id.chatRecyclerView)
            .smoothScrollToPosition(chatAdapter.itemCount - 1)
    }

    override fun onPause() {
        super.onPause()
        // persist the current chat
        saveChatSession(
            chatId    = intent.getStringExtra("CHAT_ID") ?: "chat_default",
            title     = findViewById<TextView>(R.id.TitleTextView).text.toString(),
            messages  = chatAdapter.getMessages()
        )
    }

    // UI helpers extracted out of onCreate:
    private fun getAvatarResource(botId: String, emotion: String): Int {
        val name = "${emotion}_${botId}"
        return resources.getIdentifier(name, "drawable", packageName)
    }

    private fun updateEmotionImage(avatarResId: Int, imageView: ImageView) {
        imageView.setImageResource(avatarResId)
        Log.d("EmotionUpdate", "Avatar updated: $avatarResId")
    }

    private fun detectEmotion(message: String): String = when {
        message.contains("happy", ignoreCase = true) -> "happy"
        message.contains("sad",   ignoreCase = true) -> "sad"
        else                                        -> "neutral"
    }

    override fun onCreateOptionsMenu(menu: Menu) =
        menuInflater.inflate(R.menu.main_menu, menu).let { true }

    fun clearChatHistoryFromPrefs() {
        val prefs = getSharedPreferences("chat_sessions", MODE_PRIVATE)
        prefs.edit().remove("all_chats").apply()    // or "chat_history" if you kept that key
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.clear_chat -> {
            chatAdapter.clearMessages()
            clearChatHistoryFromPrefs()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

}
