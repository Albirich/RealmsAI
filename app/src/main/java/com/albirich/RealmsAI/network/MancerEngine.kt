package com.albirich.RealmsAI.network

class MancerEngine(
    private val mancer: MancerApiService
) {
    /**
     * Send a single‐system‐message prompt to Maner and return the assistant’s reply.
     * NOW ACCEPTS 'modelKey'
     */
    suspend fun getBotOutput(prompt: String, modelKey: String = "mytholite"): String {

        // Map friendly ID to actual API String
        val apiModelString = when (modelKey) {
            "mytholite"     -> "mytholite"          // free"
            "mythomax"      -> "mythomax"   // $0.80 / $1.20
            "weaver-alpha"  -> "weaver-alpha"      // $1.20 / $1.60
            "magnum"        -> "magnum-72b-v4"     // Expensive but brilliant
            "goliath"       -> "goliath-120b"      // The titan
            else            -> "mythomax"           // Default Fallback
        }

        val req = MancerChatRequest(
            model    = apiModelString,
            messages = listOf(
                Message(role = "system", content = prompt),
                Message(
                    role = "user",
                    content = "Write the next reply in character. You MUST output your entire response as a single, valid JSON object. Do NOT output any normal text, conversational filler, or script format. ONLY output JSON."
                )
        ),
            temperature = 0.7
        )
        val resp = mancer.getBotResponses(req)
        return resp.choices
            .firstOrNull()
            ?.message
            ?.content
            ?: ""
    }
}