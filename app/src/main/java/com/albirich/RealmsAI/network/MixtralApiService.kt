// MixtralApiService.kt
package com.albirich.RealmsAI.network

import com.google.gson.annotations.SerializedName
import com.squareup.moshi.Json
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
    val model: String = "nvidia",
    val messages: List<Message>,
    val temperature: Double? = null
)

data class MixtralChatResponse(
    val id: String,
    val choices: List<MixtralChoice>,
    val usage: MixtralUsage? = null
) {
    data class MixtralChoice(val message: Message)
}
data class MixtralUsage(
    @Json(name = "prompt_tokens") val promptTokens: Long = 0,
    @Json(name = "completion_tokens") val completionTokens: Long = 0,
    @Json(name = "total_tokens") val totalTokens: Long = 0
)