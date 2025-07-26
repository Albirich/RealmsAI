package com.example.RealmsAI.network

import retrofit2.http.Body
import retrofit2.http.POST

interface OpenAiService {
    @POST("chat/completions")
    suspend fun getFacilitatorNotes(@Body body: OpenAiChatRequest): OpenAiChatResponse
}

data class OpenAiChatRequest(
    val model: String = "gpt-4o-mini-2024-07-18",
    val messages: List<Message>    // <-- uses the shared Message
)

data class OpenAiChatResponse(
    val choices: List<Choice>
) {
    data class Choice(val message: Message)
}
