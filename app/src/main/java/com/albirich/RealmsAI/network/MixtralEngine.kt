package com.albirich.RealmsAI.network

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
            "deepseek"      -> "deepseek/deepseek-v4-flash"                 // .14/.28  - 1
            "grok4.1"       -> "x-ai/grok-4.1-fast"                         // .2/.5    - 2
            "openai"        -> "openai/gpt-oss-120b"                        // .039/.19 - 3
            "gemini"        -> "google/gemini-2.5-flash-lite"               // .1/.4    - 5
            "step3.5"       -> "stepfun/step-3.5-flash:free"                // free     - 6
            "z-ai"          -> "z-ai/glm-5.1"                               // .95/2.55 - 7
            "acree"         -> "arcee-ai/trinity-mini:free"                 // free     - 13
            "nemo"          -> "mistralai/mistral-nemo"                     // .02/.04  - 17
            "xiaomi"        -> "xiaomi/mimo-v2-flash"                       // .09/.29  - 20
            "gemini3.1"     -> "google/gemini-3.1-flash-lite-preview"       // .25/1.5  - 23
            "mistral_small" -> "mistralai/mistral-small-2603"               // .06/.18  - 26
            "hunter_alpha"  -> "openrouter/healer-alpha"                    // free     - 38
            "mistral_med"   -> "mistralai/mistral-medium-3.1"               // .4/2.0   - 39
            "moonshotai"    -> "moonshotai/kimi-k2.5"                       // .45/2.2  - 34
            "minimax"       -> "minimax/minimax-m2.7"                       // .3/1.2   - 50
            "claude"        -> "anthropic/claude-3-haiku"                   // .8/4
            "qwen"          -> "qwen/qwen3.5-9b"                            // .05/.15
            "nvidia"        -> "nvidia/nemotron-3-super-120b-a12b:free"     // free
            else            -> "deepseek/deepseek-v4-flash"                     // Default Fallback
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