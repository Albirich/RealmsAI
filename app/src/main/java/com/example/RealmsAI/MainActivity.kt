package com.example.RealmsAI

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.ai.AIResponseParser
import com.example.RealmsAI.ai.FakeAiService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONObject
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var chatAdapter: ChatAdapter
    private lateinit var messageEditText: EditText
    private lateinit var profile: ChatProfile
    private lateinit var parser: AIResponseParser
    private lateinit var chatId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1) Pull chatId + deserialize profile
        chatId = intent.getStringExtra("chatId") ?: return
        val json = intent.getStringExtra("CHAT_PROFILE_JSON")
            ?: throw IllegalStateException("No chat profile passed!")
        profile = Gson().fromJson(json, ChatProfile::class.java)

        // 2) Set up RecyclerView & adapter
        messageEditText = findViewById(R.id.messageEditText)
        chatAdapter = ChatAdapter(mutableListOf()).apply {
            onNewMessage = { saveLocalHistory() }
        }
        val recycler = findViewById<RecyclerView>(R.id.chatRecyclerView)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = chatAdapter

        // 3) Load any saved history
        loadLocalHistory().forEach { chatAdapter.addMessage(it) }

        // 4) Finish UI wiring
        setupUI()
    }

    private fun setupUI() {
        // Header
        findViewById<TextView>(R.id.chatTitle).text = profile.title
        val chatDesc = findViewById<TextView>(R.id.chatDescription).apply {
            text = profile.description
        }
        findViewById<LinearLayout>(R.id.descriptionHeader)
            .setOnClickListener {
                val toggle = findViewById<ImageView>(R.id.descriptionToggle)
                if (chatDesc.visibility == View.GONE) {
                    chatDesc.visibility = View.VISIBLE
                    toggle.setImageResource(R.drawable.ic_expand_less)
                } else {
                    chatDesc.visibility = View.GONE
                    toggle.setImageResource(R.drawable.ic_expand_more)
                }
            }

        // Background
        val chatRoot = findViewById<View>(R.id.chatRoot)
        profile.backgroundUri
            ?.takeIf { it.isNotBlank() }
            ?.let { uriStr ->
                val uri = Uri.parse(uriStr)
                when (uri.scheme) {
                    "file" -> uri.path?.let {
                        BitmapFactory.decodeFile(it)
                    }?.let { bmp ->
                        chatRoot.background = BitmapDrawable(resources, bmp)
                    }
                    "content" -> contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }?.let { bmp ->
                        chatRoot.background = BitmapDrawable(resources, bmp)
                    }
                    "android.resource" -> uri.lastPathSegment
                        ?.toIntOrNull()
                        ?.let { chatRoot.setBackgroundResource(it) }
                }
            }
            ?: profile.backgroundResId?.let { chatRoot.setBackgroundResource(it) }

        // Clear placeholders
        findViewById<ImageView>(R.id.botAvatar1ImageView).setImageDrawable(null)
        findViewById<ImageView>(R.id.botAvatar2ImageView).setImageDrawable(null)

        // AI parser
        val speakerTokens = listOf("B1","B2","B3","B4","B5","B6")
        parser = AIResponseParser(
            chatAdapter  = chatAdapter,
            chatRecycler = findViewById(R.id.chatRecyclerView),
            updateAvatar = { token, emotion ->
                val idx = speakerTokens.indexOf(token)
                val charId = profile.characterIds.getOrNull(idx) ?: return@AIResponseParser
                val file = File(filesDir, "${emotion}_$charId.png")
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)
                        ?.let { bmp ->
                            val iv = when (idx) {
                                0 -> findViewById<ImageView>(R.id.botAvatar1ImageView)
                                1 -> findViewById<ImageView>(R.id.botAvatar2ImageView)
                                else -> null
                            }
                            iv?.setImageDrawable(BitmapDrawable(resources, bmp))
                        }
                }
            },
            loadName = { token ->
                val idx = speakerTokens.indexOf(token)
                val charId = profile.characterIds.getOrNull(idx) ?: return@AIResponseParser token
                getSharedPreferences("characters", Context.MODE_PRIVATE)
                    .getString(charId, null)
                    ?.let { JSONObject(it).optString("name", token) }
                    ?: token
            }
        )

        // Send button
        findViewById<Button>(R.id.sendButton).setOnClickListener {
            onSendClicked()
        }
    }

    private fun onSendClicked() {
        val text = messageEditText.text.toString().trim()
        if (text.isEmpty()) return

        // 1) Add user message
        chatAdapter.addMessage(ChatMessage(sender = "You", messageText = text))
        messageEditText.text.clear()

        // 2) AI reply
        val rawAi = FakeAiService.getResponse(text)
        parser.handle(rawAi)

        // 3) Scroll
        findViewById<RecyclerView>(R.id.chatRecyclerView)
            .smoothScrollToPosition(chatAdapter.itemCount - 1)
    }

    override fun onPause() {
        super.onPause()
        saveLocalHistory()
    }

    override fun onStop() {
        super.onStop()
        saveLocalHistory()
    }

    // ────────────────────────────────────
    // Local SharedPreferences persistence
    // ────────────────────────────────────

    private fun prefs() =
        getSharedPreferences("chat_sessions", Context.MODE_PRIVATE)

    private fun saveLocalHistory() {
        val messages = chatAdapter.getMessages()
        val json      = Gson().toJson(messages)
        prefs().edit()
            .putString("chat_$chatId", json)
            .apply()
    }

    private fun loadLocalHistory(): List<ChatMessage> {
        val json = prefs().getString("chat_$chatId", null) ?: return emptyList()
        val type = object : TypeToken<List<ChatMessage>>() {}.type
        return Gson().fromJson(json, type)
    }

    override fun onCreateOptionsMenu(menu: Menu) =
        menuInflater.inflate(R.menu.main_menu, menu).let { true }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            R.id.clear_chat -> {
                // Clear UI + remove prefs
                chatAdapter.clearMessages()
                prefs().edit().remove("chat_$chatId").apply()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
}
