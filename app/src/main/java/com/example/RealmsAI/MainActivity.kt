package com.example.RealmsAI

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.network.Message
import com.example.RealmsAI.network.MixtralChatRequest
import com.example.RealmsAI.ai.AIResponseParser
import com.example.RealmsAI.ai.buildFacilitatorPrompt
import com.example.RealmsAI.ai.buildAiPrompt
import com.example.RealmsAI.network.ApiClients
import com.example.RealmsAI.network.OpenAiChatRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import org.json.JSONObject
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var chatAdapter: ChatAdapter
    private lateinit var messageEditText: EditText
    private lateinit var profile: ChatProfile
    private lateinit var parser: AIResponseParser
    private lateinit var chatId: String

    // Real AI clients
    private val mixtralService = ApiClients.mixtral
    private val openAiService  = ApiClients.openai

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1) Pull chatId + deserialize profile
        chatId = intent.getStringExtra("chatId") ?: return
        val json = intent.getStringExtra("CHAT_PROFILE_JSON")
            ?: throw IllegalStateException("No chat profile passed!")
        profile = Gson().fromJson(json, ChatProfile::class.java)

        // 2) RecyclerView setup
        val recyclerView = findViewById<RecyclerView>(R.id.chatRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        messageEditText = findViewById(R.id.messageEditText)
        chatAdapter = ChatAdapter(mutableListOf()) {
            saveLocalHistory()
        }
        recyclerView.adapter = chatAdapter

        // 3) Load saved history
        loadLocalHistory().forEach { chatAdapter.addMessage(it) }

        // 4) Finish UI wiring
        setupUI()
    }

    private fun setupUI() {
        // … your existing header, background, parser setup …

        findViewById<Button>(R.id.sendButton).setOnClickListener {
            onSendClicked()
        }
    }

    private fun onSendClicked() {
        val text = messageEditText.text.toString().trim()
        if (text.isEmpty()) return

        // 1) Show user message
        chatAdapter.addMessage(ChatMessage(sender = "You", messageText = text))
        messageEditText.text.clear()

        // 2) Real AI sequence, with rate-limit-safe calls
        lifecycleScope.launch {
            try {
                // build history
                val historyStr = chatAdapter.getMessages()
                    .joinToString("\n") { "${it.sender}: ${it.messageText}" }

                // a) Facilitator (OpenAI) with retry-on-429
                val facPrompt = buildFacilitatorPrompt(
                    userInput        = text,
                    history          = historyStr,
                    facilitatorState = ""
                )
                val facReq = OpenAiChatRequest(
                    model    = "gpt-4o-mini",
                    messages = listOf(Message(role = "user", content = facPrompt))
                )
                val facResp = retryOn429 { openAiService.getFacilitatorNotes(facReq) }
                val facJson = facResp.choices
                    .firstOrNull()
                    ?.message
                    ?.content
                    .orEmpty()
                val (facNotes, activeBots) = parseFacilitatorJson(facJson)

                // b) Character bots (Mixtral) with retry-on-429
                val aiPrompt = buildAiPrompt(
                    userInput        = text,
                    history          = historyStr,
                    fullProfilesJson = "{}",
                    summariesJson    = "[]",
                    facilitatorNotes = facNotes,
                    chatDescription  = profile.description
                )
                val mixReq = MixtralChatRequest(
                    model    = "mixtral-8x7b",
                    messages = listOf(
                        Message(role = "system", content = aiPrompt)
                    )
                )

                val mixResp = retryOn429 { mixtralService.getBotResponses(mixReq) }
                val rawBotOutput = mixResp.choices
                    .firstOrNull()
                    ?.message
                    ?.content
                    .orEmpty()

                // c) Parse & render
                parser.activeTokens = activeBots
                parser.handle(rawBotOutput)

                // d) Scroll to bottom
                findViewById<RecyclerView>(R.id.chatRecyclerView)
                    .smoothScrollToPosition(chatAdapter.itemCount - 1)

            } catch (e: HttpException) {
                // final failure: show a toast instead of crashing
                when (e.code()) {
                    429 -> Toast.makeText(
                        this@MainActivity,
                        "Rate limit hit—please wait a moment and try again.",
                        Toast.LENGTH_LONG
                    ).show()
                    else -> Toast.makeText(
                        this@MainActivity,
                        "Server error ${e.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * Retry the given [block] up to [times] when we get a 429,
     * with exponential backoff starting at [initialDelayMs].
     */
    private suspend fun <T> retryOn429(
        times: Int = 3,
        initialDelayMs: Long = 1_000,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        repeat(times - 1) {
            try {
                return block()
            } catch (e: HttpException) {
                if (e.code() != 429) throw e
                delay(currentDelay)
                currentDelay *= 2
            }
        }
        return block() // last attempt
    }

    private fun parseFacilitatorJson(rawJson: String): Pair<String, List<String>> {
        // 1) Trim and remove any Markdown code-fence markers
        val cleaned = rawJson
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        // 2) Parse into a JSONObject
        val obj = JSONObject(cleaned)

        // 3) Pull out the "notes" field
        val notes = obj.optString("notes", "")

        // 4) Pull out the array of activeBots, if present
        val bots = mutableListOf<String>()
        obj.optJSONArray("activeBots")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optString(i)?.let { bots.add(it) }
            }
        }

        return notes to bots
    }


    override fun onPause() {
        super.onPause()
        saveLocalHistory()
    }

    override fun onStop() {
        super.onStop()
        saveLocalHistory()
    }

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
                chatAdapter.clearMessages()
                prefs().edit().remove("chat_$chatId").apply()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
}
