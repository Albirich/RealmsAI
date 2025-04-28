package com.example.emotichat.ai

object FakeAiService {
    // Round‐robin index so responses cycle predictably
    private var idx = 0

    // A few canned replies with different bots/emotions
    private val responses = listOf(
        "[B1,normal][normal] \"I hear you—tell me more!\"",
        "[B2,happy][normal] \"That’s fascinating!\"",
        "[B3,thinking][delayed] \"Hmm… let me consider that…\"",
        "[B4,angry][normal] \"Whoa, easy there!\"",
        "[B5,flirty][normal] \"Oh, you’re full of surprises 😉\"",
        "[B6,sad][delayed] \"That makes me a bit sad…\""
    )

    fun getResponse(userInput: String): String {
        // Optionally, you could tailor based on keywords in userInput
        val reply = responses[idx % responses.size]
        idx++
        return reply
    }
}
