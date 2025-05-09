package com.example.RealmsAI.ai

import com.example.RealmsAI.network.Message
import com.example.RealmsAI.network.MixtralApiService
import com.example.RealmsAI.network.MixtralChatRequest

class MixtralEngine(
    private val mixtral: MixtralApiService
) {
    suspend fun getBotOutput(prompt: String): String {
        val req = MixtralChatRequest(
            model    = "mixtral-8x7b",
            messages = listOf(
                Message(role = "system", content = prompt)
            )
        )
        val resp = mixtral.getBotResponses(req)
        return resp.choices
            .firstOrNull()
            ?.message
            ?.content
            ?: ""
    }
}
