package com.example.emotichat

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.emotichat.ai.AIResponseParser
import com.example.emotichat.ai.FakeAiService
import com.google.gson.Gson
import org.json.JSONObject
import java.io.File

class MainActivity : BaseActivity() {

    // pull the current user ID from prefs once
    private val currentUserId: String by lazy {
        getSharedPreferences("user", Context.MODE_PRIVATE)
            .getString("userId", "")!!
    }

    private lateinit var chatAdapter: ChatAdapter
    private lateinit var messageEditText: EditText
    private lateinit var profile: ChatProfile
    private lateinit var parser: AIResponseParser
    private var sessionId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1) figure out if we were given an existing session:
        val incomingChatId = intent.getStringExtra("CHAT_ID")
        profile = if (incomingChatId != null) {
            // load the serialized ChatProfile you saved under prefs["chats"]
            val prefs = getSharedPreferences("chats", Context.MODE_PRIVATE)
            val raw = prefs.getString(incomingChatId, null)
                ?: throw IllegalStateException("No saved chat for $incomingChatId")
            Gson().fromJson(raw, ChatProfile::class.java)
        } else {
            // old behavior: brand-new chat from JSON blob
            val json = intent.getStringExtra("CHAT_PROFILE_JSON")
                ?: error("No profile JSON or session ID!")
            Gson().fromJson(json, ChatProfile::class.java)
        }

        // 2) Now you already have profile.id set appropriately (either the existing sessionId
        // or the newly created one), so you can immediately load history:
        loadChatSession(profile.id).forEach { chatAdapter.addMessage(it) }

        // 1) Deserialize the ChatProfile
        val json = intent.getStringExtra("CHAT_PROFILE_JSON")
            ?: throw IllegalStateException("No chat profile passed!")
        profile = Gson().fromJson(json, ChatProfile::class.java)

        // 2) Header: title + collapsible description
        findViewById<TextView>(R.id.chatTitle).text = profile.title
        val chatDesc = findViewById<TextView>(R.id.chatDescription)
        chatDesc.text = profile.description
        findViewById<LinearLayout>(R.id.descriptionHeader).setOnClickListener {
            val toggle = findViewById<ImageView>(R.id.descriptionToggle)
            if (chatDesc.visibility == TextView.GONE) {
                chatDesc.visibility = TextView.VISIBLE
                toggle.setImageResource(R.drawable.ic_expand_less)
            } else {
                chatDesc.visibility = TextView.GONE
                toggle.setImageResource(R.drawable.ic_expand_more)
            }
        }

        // 1) find your root container
        val chatRoot = findViewById<View>(R.id.chatRoot)
// first try the gallery URI
        profile.backgroundUri
            ?.takeIf { it.isNotBlank() }
            ?.let { uriStr ->
                val uri = Uri.parse(uriStr)
                when (uri.scheme) {
                    "file" -> uri.path?.let { path ->
                        BitmapFactory.decodeFile(path)?.let { bmp ->
                            chatRoot.background = BitmapDrawable(resources, bmp)
                        }
                    }
                    "content" -> {
                        contentResolver.openInputStream(uri)
                            ?.use { stream ->
                                BitmapFactory.decodeStream(stream)
                                    ?.let { bmp -> chatRoot.background = BitmapDrawable(resources, bmp) }
                            }
                    }
                    "android.resource" -> {
                        // e.g. "android.resource://com.example.emotichat/2131230894"
                        uri.lastPathSegment?.toIntOrNull()?.let { resId ->
                            chatRoot.setBackgroundResource(resId)
                        }
                    }
                }
            }

// if no URI, fall back to any built-in drawable you saved
        profile.backgroundResId?.let { resId ->
            chatRoot.setBackgroundResource(resId)
        }



        // 3) Load initial avatars for the first two characters
        profile.characterIds.getOrNull(0)?.let { charId ->
            val uri = loadAvatarUriForCharacter(charId)
            findViewById<ImageView>(R.id.botAvatar1ImageView).setImageDrawable(null)
        }
        profile.characterIds.getOrNull(1)?.let { charId ->
            val uri = loadAvatarUriForCharacter(charId)
            findViewById<ImageView>(R.id.botAvatar2ImageView).setImageDrawable(null)
        }

        // 4) Set up chat RecyclerView + Adapter
        messageEditText = findViewById(R.id.messageEditText)
        chatAdapter     = ChatAdapter(mutableListOf())
        val recycler    = findViewById<RecyclerView>(R.id.chatRecyclerView)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter       = chatAdapter

        // 5) Pre‐load any existing messages for this profile.id
        loadChatSession(profile.id).forEach { chatAdapter.addMessage(it) }

        // 6) Set up the AIResponseParser
        val speakerTokens = listOf("B1","B2","B3","B4","B5","B6")
        parser = AIResponseParser(
            chatAdapter              = chatAdapter,
            chatRecycler             = recycler,
            updateAvatar             = { token, emotion ->
                // map token→character index, then load from internal storage
                val idx = speakerTokens.indexOf(token)
                val charId = profile.characterIds.getOrNull(idx) ?: return@AIResponseParser
                val file = File(filesDir, "${emotion}_$charId.png")
                val iv = when(idx) {
                    0 -> findViewById<ImageView>(R.id.botAvatar1ImageView)
                    1 -> findViewById<ImageView>(R.id.botAvatar2ImageView)
                    else -> null
                }
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)?.let { bmp ->
                        iv?.setImageDrawable(BitmapDrawable(resources, bmp))
                    }
                }
            },
            loadName                 = { token ->
                // map token→characterId→name from prefs
                val idx = speakerTokens.indexOf(token)
                val charId = profile.characterIds.getOrNull(idx) ?: return@AIResponseParser token
                val str = getSharedPreferences("characters", Context.MODE_PRIVATE)
                    .getString(charId, null) ?: return@AIResponseParser token
                JSONObject(str).optString("name", token)
            }
        )

        // 7) Wire up the Send button
        findViewById<Button>(R.id.sendButton).setOnClickListener {
            onSendClicked()
        }
    }

    private fun onSendClicked() {
        val text = messageEditText.text.toString().trim()
        if (text.isEmpty()) return

        // A) On first send, create a new session entry
        if (sessionId == null) {
            sessionId = System.currentTimeMillis().toString()
            saveChatSession(
                chatId   = sessionId!!,
                title    = profile.title,
                messages = emptyList(),
                author   = currentUserId
            )
        }

        // B) Show user message & persist
        chatAdapter.addMessage(ChatMessage("You", text))
        messageEditText.text.clear()
        saveChatSession(
            chatId   = sessionId!!,
            title    = profile.title,
            messages = chatAdapter.getMessages(),
            author   = currentUserId
        )

        // C) Fake‐AI reply → parser
        val rawAi = FakeAiService.getResponse(text)
        parser.handle(rawAi)

        // D) Persist again with the newly added AI messages
        saveChatSession(
            chatId   = sessionId!!,
            title    = profile.title,
            messages = chatAdapter.getMessages(),
            author   = currentUserId
        )

        // E) Scroll to bottom
        findViewById<RecyclerView>(R.id.chatRecyclerView)
            .smoothScrollToPosition(chatAdapter.itemCount - 1)
    }

    /** Load the local URI for a character’s avatar (saved under “avatar_<id>.png”) */
    private fun loadAvatarUriForCharacter(charId: String): String {
        val prefs = getSharedPreferences("characters", Context.MODE_PRIVATE)
        val str   = prefs.getString(charId, null) ?: return ""
        return JSONObject(str).optString("avatarUri", "")
    }

    // ─────────────────────────────────────────────────────────────────
    // Preserve “clear chat” menu behavior from BaseActivity
    // ─────────────────────────────────────────────────────────────────
    override fun onCreateOptionsMenu(menu: Menu) =
        menuInflater.inflate(R.menu.main_menu, menu).let { true }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            R.id.clear_chat -> {
                chatAdapter.clearMessages()
                clearChatHistoryFromPrefs(chatId = profile.id)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
}
