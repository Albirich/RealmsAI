package com.albirich.RealmsAI.network

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
    val choices: List<Choice>
) {
    data class Choice(val message: Message)
}