package com.albirich.RealmsAI.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.albirich.RealmsAI.network.ApiClients
import com.albirich.RealmsAI.network.MancerEngine
import com.albirich.RealmsAI.network.MixtralEngine

// 1. THE DATA PACKAGE
data class AiResponseData(
    val content: String?,
    val totalTokens: Long
)

object Facilitator {

    suspend fun callActivationAI(
        prompt: String,
        apiKey: String,
        model: String,
        temp: Float?,
        topK: Int?,
        topP: Float?
    ): AiResponseData = withContext(Dispatchers.IO) { // RETURN TYPE CHANGED
        try {
            val request = com.albirich.RealmsAI.network.OpenAiChatRequest(
                model = "gpt-4o-mini-2024-07-18", // Note: I updated this from your gpt-4.1-nano typo!
                messages = listOf(
                    com.albirich.RealmsAI.network.Message(
                        role = "system",
                        content = prompt
                    )
                )
            )
            val response = ApiClients.openai.getFacilitatorNotes(request)
            val content = response.choices.firstOrNull()?.message?.content ?: ""

            // Grab the tokens from the updated Retrofit model
            val tokens = response.usage?.totalTokens ?: 0L

            AiResponseData(content, tokens)
        } catch (ex: Exception) {
            Log.e("Facilitator", "Error calling Activation AI", ex)
            AiResponseData(null, 0L) // Return null on crash
        }
    }

    suspend fun callOpenAiApi(
        prompt: String,
        apiKey: String,
        model: String,
        temp: Float?,
        topK: Int?,
        topP: Float?
    ): AiResponseData = withContext(Dispatchers.IO) { // RETURN TYPE CHANGED
        try {
            Log.d("Facilitator", "[OpenAI] Character Prompt:\n$prompt")
            val request = com.albirich.RealmsAI.network.OpenAiChatRequest(
                model = "gpt-4o-mini-2024-07-18",
                messages = listOf(
                    com.albirich.RealmsAI.network.Message(
                        role = "system",
                        content = prompt
                    )
                )
            )
            val response = ApiClients.openai.getFacilitatorNotes(request)
            val content = response.choices.firstOrNull()?.message?.content ?: ""
            val tokens = response.usage?.totalTokens ?: 0L

            AiResponseData(content, tokens)
        } catch (ex: Exception) {
            Log.e("Facilitator", "Error calling OpenAI API", ex)
            AiResponseData(null, 0L)
        }
    }

    suspend fun callMixtralApi(
        prompt: String,
        apiKey: String,
        model: String = "nvidia",
        temp: Float?,
        topK: Int?,
        topP: Float?
    ): AiResponseData { // RETURN TYPE CHANGED
        return try {
            Log.d("Facilitator", "[Mixtral] Character Prompt:\n$prompt")
            val engine = MixtralEngine(ApiClients.mixtral)

            // NOTE: You will need to go into MixtralEngine.kt and change
            // getBotOutput() to also return AiResponseData instead of a String!
            return engine.getBotOutput(
                prompt = prompt,
                modelKey = model,
                role = "user",
                temperature = temp?.toDouble() ?: 0.7
            )
        } catch (ex: Exception) {
            Log.e("Facilitator", "Error calling Mixtral API", ex)
            AiResponseData(null, 0L)
        }
    }

    suspend fun callMancerApi(
        prompt: String,
        apiKey: String,
        model: String,
        temp: Float? = null,
        topK: Int? = null,
        topP: Float? = null
    ): AiResponseData { // RETURN TYPE CHANGED
        return try {
            Log.d("Facilitator", "[Mancer] Character Prompt:\n$prompt")
            val engine = MancerEngine(ApiClients.mancer)

            // NOTE: You will need to go into MancerEngine.kt and change
            // getBotOutput() to also return AiResponseData instead of a String!
            engine.getBotOutput(prompt, model)
        } catch (ex: Exception) {
            Log.e("Facilitator", "Error calling Mancer API", ex)
            AiResponseData(null, 0L)
        }
    }
}