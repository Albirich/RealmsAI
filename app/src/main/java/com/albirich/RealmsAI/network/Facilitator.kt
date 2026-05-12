package com.albirich.RealmsAI.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.albirich.RealmsAI.network.ApiClients
import com.albirich.RealmsAI.network.MancerEngine
import com.albirich.RealmsAI.network.MixtralEngine


object Facilitator {

    suspend fun callActivationAI(prompt: String
                                 , apiKey: String,
                                 model: String,
                                 temp: Float?,
                                 topK: Int?,
                                 topP: Float?  ): String =
        withContext(Dispatchers.IO) {
            try {
                val request = com.albirich.RealmsAI.network.OpenAiChatRequest(
                    model = "gpt-4.1-nano-2025-04-14",
                    messages = listOf(
                        com.albirich.RealmsAI.network.Message(
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

        suspend fun callOpenAiApi(prompt: String,
                                  apiKey: String,
                                  model: String,
                                  temp: Float?,
                                  topK: Int?,
                                  topP: Float?  ): String = withContext(Dispatchers.IO) {
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
                response.choices.firstOrNull()?.message?.content ?: ""
            } catch (ex: Exception) {
                Log.e("Facilitator", "Error calling OpenAI API", ex)
                ""
            }
        }

    suspend fun callMixtralApi(prompt: String,
                               apiKey: String,
                               model: String = "deepseek",
                               temp: Float?,
                               topK: Int?,
                               topP: Float?): String {
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

    suspend fun callMancerApi(prompt: String,
                              apiKey: String,
                              model: String,
                              temp: Float?= null,
                              topK: Int?= null,
                              topP: Float?= null  ): String {
        return try {
            Log.d("Facilitator", "[Mancer] Character Prompt:\n$prompt")

            // 1. Create the instance
            val engine = MancerEngine(ApiClients.mancer)

            // 2. Call the method ON THE INSTANCE (engine.), not the Class Name
            engine.getBotOutput(prompt, model)

        } catch (ex: Exception) {
            Log.e("Facilitator", "Error calling Mancer API", ex)
            ""
        }
    }

}


