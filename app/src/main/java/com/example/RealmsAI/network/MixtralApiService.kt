package com.example.RealmsAI.network

import retrofit2.http.Body
import retrofit2.http.POST

interface MixtralApiService {
    @POST("v1/chat/completions")
    suspend fun getBotResponses(
        @Body body: MixtralChatRequest
    ): MixtralChatResponse
}

data class MixtralChatRequest(
    val model: String = "mixtral-8x7b",
    val messages: List<Message>    // <-- uses the same shared Message
)

data class MixtralChatResponse(
    val choices: List<Choice>
) {
    data class Choice(val message: Message)
}
