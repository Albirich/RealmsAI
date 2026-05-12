package com.albirich.RealmsAI.network

import retrofit2.http.Body
import retrofit2.http.POST

interface MancerApiService {
    @POST("chat/completions")  // ← drop the leading "v1/"
    suspend fun getBotResponses(
        @Body body: MancerChatRequest
    ): MancerChatResponse
}

data class MancerChatRequest(
    //
    val model: String = "mytholite",
    val messages: List<Message>,
    val temperature: Double? = null
)

data class MancerChatResponse(
    val choices: List<Choice>
) {
    data class Choice(val message: Message)
}