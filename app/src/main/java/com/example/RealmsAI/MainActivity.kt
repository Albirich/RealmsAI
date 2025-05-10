package com.example.RealmsAI

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val OPENROUTER_API_URL = "https://openrouter.ai/api/v1/chat/completions"
        private const val OPENROUTER_API_KEY = "YOUR_API_KEY" // TODO: replace
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var chatId: String
    private lateinit var fullProfilesJson: String
    private var facilitatorNotes = ""
    private var summariesJson = "[]"
    private var chatDescription = ""
    private lateinit var avatarViews: List<ImageView>

    private lateinit var chatRecycler: RecyclerView
    private lateinit var messageEditText: EditText
    private lateinit var sendButton: Button
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var parser: AIResponseParser

    // Shared OkHttpClient with logging for facilitator calls
    private val client = OkHttpClient.Builder()
        .addInterceptor(
            HttpLoggingInterceptor { msg -> Log.d("HTTP-OpenAI", msg) }
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

        avatarViews = listOf(
             findViewById(R.id.botAvatar1ImageView),
             findViewById(R.id.botAvatar2ImageView),
            // findViewById(R.id.botAvatar3ImageView),
            // findViewById(R.id.botAvatar4ImageView),
            // findViewById(R.id.botAvatar5ImageView),
            // findViewById(R.id.botAvatar6ImageView),
        )

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

        // Parser wiring
        parser = AIResponseParser(
            chatAdapter   = chatAdapter,
            chatRecycler  = chatRecycler,
            updateAvatar  = { slot, pose ->
                // slot: "B1", "B2", …   pose: "thinking", "happy", …
                val idx = slot.removePrefix("B").toIntOrNull()?.minus(1) ?: return@AIResponseParser
                if (idx !in avatarViews.indices) return@AIResponseParser

                // Pull out the characterIds array from your profile JSON
                val charIds = JSONObject(fullProfilesJson).optJSONArray("characterIds")
                val charId  = charIds?.optString(idx) ?: return@AIResponseParser

                // Load that character’s saved avatar URI
                val uriString = loadAvatarUriForCharacter(charId)
                avatarViews[idx].setImageURI(Uri.parse(uriString))

                // (Optional) you could switch an overlay or tint based on `pose`
                Log.d(TAG, "Swapped $slot → character $charId (pose=$pose)")
            },
            loadName     = { slot ->
                // 1) compute index from "B1","B2",… → 0,1,…
                val idx = slot.removePrefix("B").toIntOrNull()?.minus(1)
                    ?: return@AIResponseParser slot

                // 2) pull your ChatProfile.characterIds from the JSON you passed in
                val ids = JSONObject(fullProfilesJson)
                    .optJSONArray("characterIds")
                    ?: return@AIResponseParser slot

                // 3) get the characterId for that slot
                val charId = ids.optString(idx, null) ?: return@AIResponseParser slot

                // 4) load the character’s saved JSON from prefs and extract the "name"
                val charJson = getSharedPreferences("characters", Context.MODE_PRIVATE)
                    .getString(charId, null)
                    ?: return@AIResponseParser slot

                JSONObject(charJson).optString("name", slot)
            }
        )

        loadLocalHistory()

        sendButton.setOnClickListener {
            val text = messageEditText.text.toString().trim()
            if (text.isNotEmpty()) {
                chatAdapter.addMessage(ChatMessage("You", text))
                messageEditText.text.clear()
                sendToAI(text)
            }
        }
    }

    private fun sendToAI(userInput: String) {
        lifecycleScope.launch {
            // 1) History
            val historyStr = chatAdapter.getMessages()
                .joinToString("\n") { "${it.sender}: ${it.messageText}" }

            // 2) Compute real slot tokens from the chat’s characterIds
            val availableSlots = try {
                // fullProfilesJson is your ChatProfile JSON with a `characterIds` array
                val charArr = JSONObject(fullProfilesJson)
                    .optJSONArray("characterIds")
                    ?: JSONArray()
                // If there are N characterIds, slots are B1..BN
                (1..charArr.length()).map { idx -> "B$idx" }
            } catch (e: Exception) {
                // fallback in case parsing fails
                emptyList<String>()
            }

            // 3) Build facilitator prompt
            val facPrompt = buildFacilitatorPrompt(
                userInput        = userInput,
                history          = historyStr,
                facilitatorState = facilitatorNotes,
                availableSlots   = availableSlots    // now passes B1, B2, …
            )
            Log.d(TAG, "Facilitator prompt: $facPrompt")

            // 3) Call facilitator
            val facRespJson = try {
                callFacilitator(facPrompt)
            } catch (e: Exception) {
                Log.e(TAG, "Facilitator call failed", e)
                JSONObject()
            }

            // 4) Extract assistant content (JSON string)
            val assistantContent = facRespJson
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content", "")
                .orEmpty()
            Log.d(TAG, "Facilitator raw content: $assistantContent")

            // 5) Parse notes & activeBots
            val facContentJson = try {
                JSONObject(assistantContent)
            } catch (_: Exception) {
                JSONObject()
            }
            facilitatorNotes = facContentJson.optString("notes", "")
            Log.d(TAG, "Facilitator notes: $facilitatorNotes")

            val activeArr = facContentJson.optJSONArray("activeBots")
            val activeBots: List<String> = (0 until (activeArr?.length() ?: 0))
                .mapNotNull { i -> activeArr.optString(i).takeIf(String::isNotBlank) }

            Log.d(TAG, "Facilitator returned: $activeBots")

// now FALLBACK if empty:
            val finalActiveBots = activeBots.ifEmpty {
                // pull the number of characterIds from your fullProfilesJson
                val charIds = JSONObject(fullProfilesJson)
                    .optJSONArray("characterIds")
                if (charIds != null && charIds.length() > 0) {
                    (1..charIds.length()).map { idx -> "B$idx" }
                } else {
                    listOf("B1")
                }.also { fallback ->
                    Log.w(TAG, "No facilitator bots → falling back to $fallback")
                }
            }

            Log.d(TAG, "Using activeBots = $finalActiveBots")
            parser.activeTokens = finalActiveBots

            // —— Mixtral Call
            // 1) Build JSON of only the active character profiles
            val charArr = JSONObject(fullProfilesJson)
                .optJSONArray("characterIds") ?: JSONArray()

            val activeProfilesJson = JSONArray().apply {
                finalActiveBots.forEach { slot ->
                    // slot “B1” → index 0, “B2” → 1, etc.
                    val idx = slot.removePrefix("B").toIntOrNull()?.minus(1)
                    if (idx != null && idx in 0 until charArr.length()) {
                        val charId = charArr.optString(idx)
                        val charJsonString = getSharedPreferences("characters", Context.MODE_PRIVATE)
                            .getString(charId, null)
                        if (!charJsonString.isNullOrEmpty()) {
                            put(JSONObject(charJsonString))
                        }
                    }
                }
            }.toString()

            // 2) Build the AI prompt using only those profiles
            val aiPrompt = buildAiPrompt(
                userInput,
                historyStr,
                activeProfilesJson,   // ← swapped in here
                summariesJson,
                facilitatorNotes,
                chatDescription
            )
            Log.d(TAG, "Mixtral prompt: $aiPrompt")

            // 3) Construct the HTTP body properly as JSON
            val msgArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("role",    "system")
                    put("content", aiPrompt)
                })
            }
            val mixJson = JSONObject().apply {
                put("model",    "mistralai/mixtral-8x7b-instruct")
                put("messages", msgArray)
            }
            val body = mixJson.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

            // 4) Send to Mixtral / OpenRouter
            try {
                val mixReq = Request.Builder()
                    .url(OPENROUTER_API_URL)
                    .addHeader("Authorization", "Bearer ${BuildConfig.MIXTRAL_API_KEY}")
                    .post(body)
                    .build()

                val mixResp = withContext(Dispatchers.IO) {
                    client.newCall(mixReq).execute()
                }
                val mixRaw = mixResp.body?.string().orEmpty()
                Log.d(TAG, "Mixtral raw: $mixRaw")

                // 5) Parse out the assistant’s content and hand off to the parser
                val content = JSONObject(mixRaw)
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

    private suspend fun callFacilitator(prompt: String): JSONObject {
        // Build chat-completions body
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", prompt)
            })
        }
        val bodyJson = JSONObject().apply {
            put("model", "gpt-3.5-turbo")
            put("messages", messages)
            put("temperature", 0)
        }.toString()

        Log.d("HTTP-OpenAI", "--> FAC POST /v1/chat/completions")
        Log.d("HTTP-OpenAI", bodyJson)

        val body = bodyJson.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
            .post(body)
            .build()

        val resp = withContext(Dispatchers.IO) { client.newCall(req).execute() }
        val raw = resp.body?.string().orEmpty()

        Log.d("HTTP-OpenAI", "<-- FAC response")
        Log.d("HTTP-OpenAI", raw)
        return JSONObject(raw)
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
                        ChatMessage(obj.getString("sender"), obj.getString("text"))
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
    /** Helper to pull the saved avatarUri from Character prefs */
    fun loadAvatarUriForCharacter(charId: String): String {
        val prefs = getSharedPreferences("characters", Context.MODE_PRIVATE)
        val json  = prefs.getString(charId, null) ?: return ""
        return JSONObject(json).optString("avatarUri", "")
    }

}
