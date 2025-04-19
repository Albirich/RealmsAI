// MainActivity.kt (Updated)
package com.example.emotichat

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

data class ChatMessage(
    val sender: String,
    var messageText: String,
    val timeStamp: Long = System.currentTimeMillis()
)

class MainActivity : AppCompatActivity() {
    private lateinit var chatAdapter: ChatAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1) Get the chatId passed from ChatListActivity (or default)
        val chatId = intent.getStringExtra("CHAT_ID") ?: "chat_default"

        // 2) Find your RecyclerView & adapter as before
        val chatRecyclerView: RecyclerView = findViewById(R.id.chatRecyclerView)
        chatAdapter = ChatAdapter(mutableListOf())
        chatRecyclerView.layoutManager = LinearLayoutManager(this)
        chatRecyclerView.adapter = chatAdapter

        // 3) Load and display that session’s messages
        val restored = loadChatSession(chatId)
        restored.forEach { chatAdapter.addMessage(it) }

        // …then wire up your sendButton as before…

            val messageEditText: EditText = findViewById(R.id.messageEditText)
        val sendButton: Button = findViewById(R.id.sendButton)

        chatAdapter = ChatAdapter(mutableListOf())
        chatRecyclerView.layoutManager = LinearLayoutManager(this)
        chatRecyclerView.adapter = chatAdapter


        sendButton.setOnClickListener {
            fun getAvatarResource(botId: String, emotion: String): Int {
                val resourceName = "${emotion}_${botId}"
                return resources.getIdentifier(resourceName, "drawable", packageName)
            }
            fun updateEmotionImage(avatarResId: Int, imageView: ImageView) {
                imageView.setImageResource(avatarResId)
                Log.d("EmotionUpdate", "Updated avatar image with resource id: $avatarResId")
            }
            fun detectEmotion(message: String): String {
                return when {
                    message.contains("happy", ignoreCase = true) -> "happy"
                    message.contains("sad", ignoreCase = true) -> "sad"
                    else -> "neutral"
                }
            }
            val botAvatar1ImageView: ImageView = findViewById(R.id.botAvatar1ImageView)
            val botAvatar2ImageView: ImageView = findViewById(R.id.botAvatar2ImageView)
            val userMessage = messageEditText.text.toString().trim()
            if (userMessage.isNotEmpty()) {
                chatAdapter.addMessage(ChatMessage("User", userMessage))
                val ai1Response = generateAIResponse("01", userMessage)
                chatAdapter.addMessage(ChatMessage("Bot 1", ai1Response))
                val ai2Response = generateAIResponse("02", userMessage)
                chatAdapter.addMessage(ChatMessage("Bot 2", ai2Response))
                messageEditText.text.clear()
                chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
                val avatarResId1 = getAvatarResource("01", detectEmotion(ai1Response))
                updateEmotionImage(avatarResId1, botAvatar1ImageView)
                val avatarResId2 = getAvatarResource("02", detectEmotion(ai2Response))
                updateEmotionImage(avatarResId2, botAvatar2ImageView)
            } else {
                Toast.makeText(this, "Please type a message", Toast.LENGTH_SHORT).show()
            }
        }
    }



    fun saveChatSession(chatId: String, title: String, messages: List<ChatMessage>) {
        val prefs = getSharedPreferences("chat_sessions", MODE_PRIVATE)
        val editor = prefs.edit()

        // Build the JSON object for this chat
        val chatObject = org.json.JSONObject()
        chatObject.put("title", title)
        chatObject.put("lastUpdated", System.currentTimeMillis())

        // Convert messages to JSONArray
        val messageArray = org.json.JSONArray()
        for (msg in messages) {
            val msgObj = org.json.JSONObject()
            msgObj.put("sender", msg.sender)
            msgObj.put("messageText", msg.messageText)
            msgObj.put("timeStamp", msg.timeStamp)
            messageArray.put(msgObj)
        }

        chatObject.put("messages", messageArray)

        // Load the full chatSessions map (or create one)
        val allChatsStr = prefs.getString("all_chats", "{}")
        val allChats = org.json.JSONObject(allChatsStr)

        // Insert/update the chat
        allChats.put(chatId, chatObject)

        // Save it back
        editor.putString("all_chats", allChats.toString())
        editor.apply()
    }

    fun loadChatSession(chatId: String): List<ChatMessage> {
        val prefs = getSharedPreferences("chat_sessions", MODE_PRIVATE)
        val allChatsStr = prefs.getString("all_chats", null) ?: return emptyList()

        val allChats = org.json.JSONObject(allChatsStr)

        // If the chat doesn't exist, return empty
        if (!allChats.has(chatId)) return emptyList()

        val chatObject = allChats.getJSONObject(chatId)
        val messageArray = chatObject.getJSONArray("messages")

        val messages = mutableListOf<ChatMessage>()
        for (i in 0 until messageArray.length()) {
            val msgObj = messageArray.getJSONObject(i)
            val sender = msgObj.getString("sender")
            val messageText = msgObj.getString("messageText")
            val timeStamp = msgObj.getLong("timeStamp")
            messages.add(ChatMessage(sender, messageText, timeStamp))
        }

        return messages
    }



    fun generateAIResponse(botId: String, userMessage: String): String {
        return when (botId) {
            "01" -> "I am feeling really happy today!"
            "02" -> "I'm not doing so well; I feel a bit sad."
            else -> "I'm at a loss for words."
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        Log.d("MainActivity", "onCreateOptionsMenu called")
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.clear_chat -> {
                chatAdapter.clearMessages()
                clearChatHistoryFromPrefs()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }



    private fun clearChatHistoryFromPrefs() {
        val prefs = getSharedPreferences("chat_prefs", MODE_PRIVATE)
        prefs.edit().remove("chat_history").apply()
    }
}
