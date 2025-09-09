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
    // deepseek/deepseek-chat-v3-0324"mistralai/mistral-medium-3.1
    val model: String = "cognitivecomputations/dolphin3.0-r1-mistral-24b:free",
    val messages: List<Message>
)

data class MixtralChatResponse(
    val choices: List<Choice>
) {
    data class Choice(val message: Message)
}
