package com.example.RealmsAI.network

import com.example.RealmsAI.network.OpenAiService
import com.example.RealmsAI.network.OpenAiChatRequest
import com.example.RealmsAI.ai.parseFacilitatorJson

class ChatGptFacilitator(private val openAi: OpenAiService) {
    suspend fun getFacilitatorNotes(prompt: String): Pair<String,List<String>> {
        val req = OpenAiChatRequest(
            model    = "gpt-4.1-nano-2025-04-14",
            messages = listOf(Message(role="user", content=prompt))
        )
        val resp = openAi.getFacilitatorNotes(req)
        val json  = resp.choices.first().message.content
        // parse JSON into notes + token list
        return parseFacilitatorJson(json)
    }
}