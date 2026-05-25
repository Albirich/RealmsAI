package com.albirich.RealmsAI.network

import com.google.gson.annotations.SerializedName
import com.squareup.moshi.Json
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
    val id: String,
    val choices: List<MancerChoice>,
    val usage: MancerUsage? = null
) {
    data class MancerChoice(val message: Message)
}
data class MancerUsage(
    @Json(name = "prompt_tokens") val promptTokens: Long = 0,
    @Json(name = "completion_tokens") val completionTokens: Long = 0,
    @Json(name = "total_tokens") val totalTokens: Long = 0
)