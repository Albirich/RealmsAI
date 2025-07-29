package com.example.RealmsAI.network


class MixtralEngine(
    private val mixtral: MixtralApiService
) {
    /**
     * Send a single‐system‐message prompt to Mixtral and return the assistant’s reply.
     */
    suspend fun getBotOutput(prompt: String): String {
        val req = MixtralChatRequest(
            // nsfw bots here mistralai/devstral-small:free mistralai/mistral-small-3.1-24b-instruct
            model    = "x-ai/grok-3-mini-beta",
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
