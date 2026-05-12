// MixtralApiService.kt
package com.albirich.RealmsAI.network

import retrofit2.http.Body
import retrofit2.http.POST

interface MixtralApiService {
    @POST("chat/completions")  // ← drop the leading "v1/"
    suspend fun getBotResponses(
        @Body body: MixtralChatRequest
    ): MixtralChatResponse
}

data class MixtralChatRequest(
    // deepseek/deepseek-chat-v3-0324"mistralai/mistral-medium-3.1/x-ai/grok-3-mini
    val model: String = "mythomax",
    val messages: List<Message>,
    val temperature: Double? = null
)

data class MixtralChatResponse(
    val choices: List<Choice>
) {
    data class Choice(val message: Message)
}
