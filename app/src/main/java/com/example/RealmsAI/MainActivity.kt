package com.example.RealmsAI

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.ai.AIResponseParser
import com.example.RealmsAI.ai.buildAiPrompt
import com.example.RealmsAI.ai.buildFacilitatorPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val OPENROUTER_API_URL = "https://openrouter.ai/api/v1/chat/completions"
        private const val OPENROUTER_API_KEY = "YOUR_API_KEY" // TODO: replace with actual key
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var chatId: String
    private lateinit var fullProfilesJson: String
    private var facilitatorNotes = ""
    private var summariesJson = "[]"
    private var chatDescription = ""

    private lateinit var chatRecycler: RecyclerView
    private lateinit var messageEditText: EditText
    private lateinit var sendButton: Button
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var parser: AIResponseParser

    private val client = OkHttpClient.Builder()
        .addInterceptor(
            HttpLoggingInterceptor { msg -> Log.d("HTTP-Facilitator", msg) }
                .apply { level = HttpLoggingInterceptor.Level.BODY }
        )
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("RealmsAI", Context.MODE_PRIVATE)
        chatId = intent.getStringExtra("CHAT_ID") ?: "default_chat"
        fullProfilesJson = intent.getStringExtra("CHAT_PROFILE_JSON") ?: "{}"
        chatDescription = intent.getStringExtra("CHAT_DESCRIPTION") ?: ""

        // RecyclerView & adapter setup
        chatRecycler = findViewById(R.id.chatRecyclerView)
        chatRecycler.layoutManager = LinearLayoutManager(this)
        messageEditText = findViewById(R.id.messageEditText)
        sendButton = findViewById(R.id.sendButton)

        chatAdapter = ChatAdapter(mutableListOf()) { newPos ->
            chatRecycler.smoothScrollToPosition(newPos)
            saveLocalHistory()
        }
        chatRecycler.adapter = chatAdapter

        // AIResponseParser wiring
        parser = AIResponseParser(
            chatAdapter = chatAdapter,
            chatRecycler = chatRecycler,
            updateAvatar = { slot, pose ->
                // TODO: implement avatar update based on slot & pose
                Log.d(TAG, "Avatar update: $slot → $pose")
            },
            loadName = { slot -> slot /* TODO: map slot to character name */ }
        )

        loadLocalHistory()

        sendButton.setOnClickListener {
            val text = messageEditText.text.toString().trim()
            if (text.isNotEmpty()) {
                addUserMessage(text)
                messageEditText.text.clear()
                sendToAI(text)
            }
        }
    }

    private fun addUserMessage(text: String) {
        chatAdapter.addMessage(ChatMessage("You", text))
    }

    private fun sendToAI(userInput: String) {
        lifecycleScope.launch {
            // 1) Build the shared history string
            val historyStr = chatAdapter.getMessages()
                .joinToString("\n") { "${it.sender}: ${it.messageText}" }

            // 2) Build both prompts
            val facPrompt = buildFacilitatorPrompt(userInput, historyStr, facilitatorNotes)
            val aiPrompt  = buildAiPrompt(
                userInput, historyStr,
                fullProfilesJson, summariesJson,
                facilitatorNotes, chatDescription
            )

            Log.d(TAG, "Facilitator prompt: $facPrompt")

            // 3) Call facilitator (OpenAI completions)
            val activeBots = try {
                callFacilitator(facPrompt)
            } catch (e: Exception) {
                Log.e(TAG, "Facilitator call failed", e)
                emptyList<String>()
            }
            Log.d(TAG, "Active bots from facilitator: $activeBots")
            if (activeBots.isEmpty()) {
                Log.w(TAG, "No active bots returned—skipping Mixtral call")
                return@launch
            }
            // tell the parser which slots to expect
            parser.activeTokens = activeBots

            // 4) Now do your Mixtral call exactly as before
            try {
                // build Mixtral JSON
                val jsonBody = JSONObject().apply {
                    put("model", "mistralai/mixtral-8x7b-instruct")
                    put("messages", listOf(
                        JSONObject().apply {
                            put("role", "system")
                            put("content", aiPrompt)
                        }
                    ))
                }
                val body = jsonBody
                    .toString()
                    .toRequestBody("application/json".toMediaTypeOrNull())

                val request = Request.Builder()
                    .url(OPENROUTER_API_URL)
                    .addHeader("Authorization", "Bearer $OPENROUTER_API_KEY")
                    .post(body)
                    .build()

                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }
                val respBody = response.body?.string().orEmpty()
                Log.d(TAG, "Mixtral raw: $respBody")

                val content = JSONObject(respBody)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")

                parser.handle(content)
            } catch (e: Exception) {
                Log.e(TAG, "Mixtral request failed", e)
            }
        }
    }

    /** Synchronously POSTs `prompt` to OpenAI’s completions endpoint and returns the list of activeBots. */
    private suspend fun callFacilitator(prompt: String): List<String> {
        // build the JSON request
        val facJson = JSONObject().apply {
            put("model", "text-davinci-003")
            put("prompt", prompt)
            put("max_tokens", 50)
        }.toString()

        val body = facJson
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val req = Request.Builder()
            .url("https://api.openai.com/v1/completions")
            .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
            .post(body)
            .build()

        val resp = withContext(Dispatchers.IO) {
            client.newCall(req).execute()
        }
        val raw = resp.body?.string().orEmpty()
        Log.d(TAG, "Facilitator raw: $raw")

        // parse activeBots array out of the JSON { "notes": "...", "activeBots": ["B1","B3"] }
        val arr = JSONObject(raw).optJSONArray("activeBots")
            ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optString(i).takeIf { it.isNotBlank() }
        }

    }


    private fun saveLocalHistory() {
        val key = "chat_$chatId"
        val arr = JSONArray()
        chatAdapter.getMessages().forEach {
            arr.put(JSONObject().apply {
                put("sender", it.sender)
                put("text", it.messageText)
            })
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private fun loadLocalHistory() {
        val key = "chat_$chatId"
        prefs.getString(key, null)?.let { hist ->
            try {
                val arr = JSONArray(hist)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    chatAdapter.addMessage(
                        ChatMessage(
                            obj.getString("sender"),
                            obj.getString("text")
                        )
                    )
                }
            } catch (_: Exception) {}
        }
    }

    override fun onCreateOptionsMenu(menu: Menu) =
        menuInflater.inflate(R.menu.main_menu, menu).let { true }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            R.id.clear_chat -> {
                chatAdapter.clearMessages()
                prefs.edit().remove("chat_$chatId").apply()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
}
