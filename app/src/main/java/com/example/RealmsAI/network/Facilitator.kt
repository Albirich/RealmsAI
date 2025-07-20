package com.example.RealmsAI.ai

import android.graphics.Color
import android.util.Log
import com.example.RealmsAI.models.*
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.RealmsAI.network.ApiClients


object Facilitator {

    suspend fun callActivationAI(prompt: String, apiKey: String): String =
        withContext(Dispatchers.IO) {
            try {
                val request = com.example.RealmsAI.network.OpenAiChatRequest(
                    model = "gpt-4o-mini-2024-07-18",
                    messages = listOf(
                        com.example.RealmsAI.network.Message(
                            role = "system",
                            content = prompt
                        )
                    )
                )
                val response = ApiClients.openai.getFacilitatorNotes(request)
                val content = response.choices.firstOrNull()?.message?.content ?: ""
                content
            } catch (ex: Exception) {
                Log.e("Facilitator", "Error calling Activation AI", ex)
                ""
            }
        }

        suspend fun callOpenAiApi(prompt: String, key: String): String = withContext(Dispatchers.IO) {
            try {
                Log.d("Facilitator", "[OpenAI] Character Prompt:\n$prompt")
                val request = com.example.RealmsAI.network.OpenAiChatRequest(
                    model = "gpt-4o-mini-2024-07-18",
                    messages = listOf(
                        com.example.RealmsAI.network.Message(
                            role = "system",
                            content = prompt
                        )
                    )
                )
                val response = ApiClients.openai.getFacilitatorNotes(request)
                response.choices.firstOrNull()?.message?.content ?: ""
            } catch (ex: Exception) {
                Log.e("Facilitator", "Error calling OpenAI API", ex)
                ""
            }
        }

    suspend fun callMixtralApi(prompt: String, key: String): String = withContext(Dispatchers.IO) {
        try {
            Log.d("Facilitator", "[Mixtral] Character Prompt:\n$prompt")
            val engine = com.example.RealmsAI.network.MixtralEngine(ApiClients.mixtral)
            engine.getBotOutput(prompt)
        } catch (ex: Exception) {
            Log.e("Facilitator", "Error calling Mixtral API", ex)
            ""
        }
    }

}


