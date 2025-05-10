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
    val model: String = "mistralai/mixtral-8x7b-instruct",
    val messages: List<Message>
)

data class MixtralChatResponse(
    val choices: List<Choice>
) {
    data class Choice(val message: Message)
}
