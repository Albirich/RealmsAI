package com.example.RealmsAI.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object ModelClient {
    private const val OPENAI_URL     = "https://api.openai.com/v1/chat/completions"
    private const val OPENAI_MODEL   = "gpt-4o-mini-2024-07-18"
    private const val MIXTRAL_URL    = "https://openrouter.ai/api/v1/chat/completions"

    private val http = OkHttpClient()

    /** Calls your OpenAI facilitator (GPT-3.5) with a single “system” message. */
    suspend fun callFacilitator(systemPrompt: String, apiKey: String): JSONObject {
        val bodyJson = JSONObject().apply {
            put("model", OPENAI_MODEL)
            put("messages", JSONArray().put(
                JSONObject().put("role", "system").put("content", systemPrompt)
            ))
            put("temperature", 0)
        }.toString()

        val reqBody = bodyJson
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val req = Request.Builder()
            .url(OPENAI_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(reqBody)
            .build()

        return withContext(Dispatchers.IO) {
            http.newCall(req).execute().use { resp ->
                JSONObject(resp.body?.string().orEmpty())
            }
        }
    }

    /** Calls your Mixtral/OpenRouter endpoint with whatever JSON you pass. */
    suspend fun callChatModel(chatJson: String, apiKey: String): JSONObject {
        val reqBody = chatJson
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val req = Request.Builder()
            .url(MIXTRAL_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(reqBody)
            .build()

        return withContext(Dispatchers.IO) {
            http.newCall(req).execute().use { resp ->
                JSONObject(resp.body?.string().orEmpty())
            }
        }
    }

    /**
     * Convenience wrapper: if `forFacilitator` is true, routes to GPT-3.5; otherwise
     * uses Mixtral/OpenRouter. Keeps your calling code simple.
     */
    suspend fun callModel(
        promptJson:     String,
        forFacilitator: Boolean,
        openAiKey:      String,
        mixtralKey:     String
    ): JSONObject = when {
        forFacilitator -> callFacilitator(promptJson, openAiKey)
        else           -> callChatModel(promptJson, mixtralKey)
    }
}
