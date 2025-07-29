// MixtralApiService.kt
package com.example.RealmsAI.network

import retrofit2.http.Body
import retrofit2.http.POST

interface MixtralApiService {
    @POST("chat/completions")  // ← drop the leading "v1/"
    suspend fun getBotResponses(
        @Body body: MixtralChatRequest
    ): MixtralChatResponse
}

data class MixtralChatRequest(
    // deepseek/deepseek-chat-v3-0324"
    val model: String = "x-ai/grok-3-mini-beta",
    val messages: List<Message>
)

data class MixtralChatResponse(
    val choices: List<Choice>
) {
    data class Choice(val message: Message)
}
