package com.example.RealmsAI.network

class MixtralEngine(
    private val mixtral: MixtralApiService
) {
    /**
     * Send a single‐system‐message prompt to Mixtral and return the assistant’s reply.
     * NOW ACCEPTS 'modelKey'
     */
    suspend fun getBotOutput(prompt: String, modelKey: String = "grok4.1"): String {

        // Map friendly ID to actual API String
        val apiModelString = when (modelKey) {
            "deepseek"      -> "deepseek/deepseek-v3.2"                     // .25/.4   - 1
            "grok4.1"       -> "x-ai/grok-4.1-fast"                         // .2/.5    - 2
            "openai"        -> "openai/gpt-oss-120b"                        // .039/.19 - 3
            "gemini"        -> "google/gemini-2.5-flash-lite"               // .1/.4    - 5
            "z-ai"          -> "z-ai/glm-5"                                 // .95/2.55 - 6
            "acree"         -> "arcee-ai/trinity-large-preview:free"        // free     - 13
            "nemo"          -> "mistralai/mistral-nemo"                     // .02/.04  - 17
            "xiaomi"        -> "xiaomi/mimo-v2-flash"                       // .09/.29  - 20
            "mistral_small" -> "mistralai/mistral-small-3.2-24b-instruct"   // .06/.18  - 26
        //  "moonshotai"    -> "moonshotai/kimi-k2.5"                       // .45/2.2  - 29
            "mistral_med"   -> "mistralai/mistral-medium-3.1"               // .4/2.0   - 39
            "minimax"       -> "minimax/minimax-m2.5"                       // .295/1.2 - 42
        //  "claude"        -> "anthropic/claude-3.5-haiku"                 // .8/4
            else            -> "deepseek/deepseek-v3.2"                     // Default Fallback
        }

        val req = MixtralChatRequest(
            model    = apiModelString,
            messages = listOf(
                Message(role = "system", content = prompt)
            ),
            temperature = 0.7
        )
        val resp = mixtral.getBotResponses(req)
        return resp.choices
            .firstOrNull()
            ?.message
            ?.content
            ?: ""
    }
}