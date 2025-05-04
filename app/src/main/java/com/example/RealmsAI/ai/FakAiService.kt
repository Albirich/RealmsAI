package com.example.RealmsAI.ai

object FakeAiService {
    // Round‐robin index so responses cycle predictably
    private var idx = 0

    // A few canned replies with different bots/emotions
    private val responses = listOf(
        "[B1,happy][normal] \"Hi, nice to meet you!\" [B2,happy][normal] \"Hey...\"",
        "[B1,thinking][delayed] \"Oh I'm sorry, I'm just a demo. I can't actually respond\" [B2,angry][interrupt] \"Whoa, you're not suppose to break the fourth wall idiot!\"",
        "[B2,flirty][normal] \"Now they're gonna have to close it.\" [B1,embarrassed][normal] \"My bad, heh.\""
    )

    fun getResponse(userInput: String): String {
        // Optionally, you could tailor based on keywords in userInput
        val reply = responses[idx % responses.size]
        idx++
        return reply
    }
}