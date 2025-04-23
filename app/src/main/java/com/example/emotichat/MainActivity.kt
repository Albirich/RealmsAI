package com.example.emotichat

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import android.view.View
import android.widget.LinearLayout


class MainActivity : BaseActivity() {
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var messageEditText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Grab the JSON string and deserialize
        val json = intent.getStringExtra("CHAT_PROFILE_JSON")
            ?: throw IllegalStateException("No chat profile passed!")
        val profile = Gson().fromJson(json, ChatProfile::class.java)

        // 2. Populate header UI from profile
        findViewById<TextView>(R.id.chatTitle).text = profile.title
        findViewById<TextView>(R.id.chatDescription).text = profile.description
        // TODO: load profile.backgroundUri into an ImageView if you have one

        // find views
        val descriptionHeader = findViewById<LinearLayout>(R.id.descriptionHeader)
        val descriptionToggle = findViewById<ImageView>(R.id.descriptionToggle)
        val chatDescription   = findViewById<TextView>(R.id.chatDescription)

// toggle on click
        descriptionHeader.setOnClickListener {
            if (chatDescription.visibility == View.GONE) {
                chatDescription.visibility = View.VISIBLE
                descriptionToggle.setImageResource(R.drawable.ic_expand_less)
            } else {
                chatDescription.visibility = View.GONE
                descriptionToggle.setImageResource(R.drawable.ic_expand_more)
            }
        }

        // 3. Set up RecyclerView + Adapter
        val chatId = profile.id
        messageEditText = findViewById(R.id.messageEditText)
        chatAdapter = ChatAdapter(mutableListOf())
        findViewById<RecyclerView>(R.id.chatRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = chatAdapter
        }

        // 4. Load previous messages
        loadChatSession(chatId).forEach { chatAdapter.addMessage(it) }

        // 5. Send button
        findViewById<Button>(R.id.sendButton).setOnClickListener {
            onSendClicked(chatId)
        }
    }
    private fun getAvatarResource(botId: String, emotion: String): Int {
        val name = "${emotion}_${botId}"
        return resources.getIdentifier(name, "drawable", packageName)
    }

    private fun updateEmotionImage(@DrawableRes resId: Int, imageView: ImageView) {
        imageView.setImageResource(resId)
        Log.d("EmotionUpdate", "Avatar updated: $resId")
    }

    private fun onSendClicked(chatId: String) {
        val text = messageEditText.text.toString().trim()
        if (text.isBlank()) {
            Toast.makeText(this, "Please type a message", Toast.LENGTH_SHORT).show()
            return
        }

        // a) Add user message
        chatAdapter.addMessage(ChatMessage("User", text))

        // b) Generate AI response + emotions
        listOf("01" to "Bot 1", "02" to "Bot 2").forEach { (botId, label) ->
            val resp   = generateAIResponse(botId, text)
            val emot   = detectEmotion(resp)
            val avatar = getAvatarResource(botId, emot)
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
            chatId   = intent.getStringExtra("CHAT_ID") ?: return,
            title    = findViewById<TextView>(R.id.chatTitle).text.toString(),
            messages = chatAdapter.getMessages()
        )
    }

    // Helper functions
    private fun generateAIResponse(botId: String, message: String) = when (botId) {
        "01" -> "I am feeling really happy today!"
        "02" -> "I’m not doing so well; I feel a bit sad."
        else -> "I’m at a loss for words."
    }

    private fun detectEmotion(message: String) = when {
        message.contains("happy", ignoreCase = true) -> "happy"
        message.contains("sad", ignoreCase = true)   -> "sad"
        else -> "neutral"
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

    // Persisted storage helpers live in BaseActivity
}
