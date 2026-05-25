package com.albirich.RealmsAI.network

import com.google.gson.annotations.SerializedName
import com.squareup.moshi.Json
import retrofit2.http.Body
import retrofit2.http.POST

interface OpenAiService {
    @POST("chat/completions")
    suspend fun getFacilitatorNotes(@Body body: OpenAiChatRequest): OpenAiChatResponse
}

data class OpenAiChatRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Float? = null,
    val top_p: Float? = null,
    val top_k: Int? = null
)

data class OpenAiChatResponse(
    val id: String,
    val choices: List<Choice>,
    val usage: Usage? = null
) {
    data class Choice(val message: Message)
}

data class Usage(
    @Json(name = "prompt_tokens") val promptTokens: Long = 0,
    @Json(name = "completion_tokens") val completionTokens: Long = 0,
    @Json(name = "total_tokens") val totalTokens: Long = 0
)