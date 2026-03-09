package com.example.RealmsAI.ai

import android.graphics.Color
import android.util.Log
import com.example.RealmsAI.models.*
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.RealmsAI.network.ApiClients
import com.example.RealmsAI.network.MixtralEngine


object Facilitator {

    suspend fun callActivationAI(prompt: String, apiKey: String): String =
        withContext(Dispatchers.IO) {
            try {
                val request = com.example.RealmsAI.network.OpenAiChatRequest(
                    model = "gpt-4.1-nano-2025-04-14",
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
                    model = "gpt-5-nano-2025-08-07",
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

    suspend fun callMixtralApi(prompt: String, apiKey: String, model: String = "z-ai"): String {
        return try {
            Log.d("Facilitator", "[Mixtral] Character Prompt:\n$prompt")

            // 1. Create the instance
            val engine = MixtralEngine(ApiClients.mixtral)

            // 2. Call the method ON THE INSTANCE (engine.), not the Class Name
            engine.getBotOutput(prompt, model)

        } catch (ex: Exception) {
            Log.e("Facilitator", "Error calling Mixtral API", ex)
            ""
        }
    }

}


