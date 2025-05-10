package com.example.RealmsAI.network


class MixtralEngine(
    private val mixtral: MixtralApiService
) {
    /**
     * Send a single‐system‐message prompt to Mixtral and return the assistant’s reply.
     */
    suspend fun getBotOutput(prompt: String): String {
        val req = MixtralChatRequest(
            model    = "mistralai/mixtral-8x7b-instruct",
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
