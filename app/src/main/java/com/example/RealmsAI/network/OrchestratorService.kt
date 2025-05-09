package com.example.RealmsAI.network

import com.example.RealmsAI.ai.MixtralEngine
import com.example.RealmsAI.ai.buildAiPrompt
import com.example.RealmsAI.ai.buildFacilitatorPrompt

interface AiService {
    suspend fun sendPrompt(userInput: String, history: String, chatDesc: String): Pair<String,List<String>>
}

class OrchestratorService(
    private val facilitator: ChatGptFacilitator,
    private val engine: MixtralEngine
): AiService {
    override suspend fun sendPrompt(
        userInput: String,
        history: String,
        chatDesc: String
    ): Pair<String,List<String>> {
        // 1) build facilitator prompt
        val facPrompt = buildFacilitatorPrompt(userInput, history, /*state*/"")
        val (notes, bots) = facilitator.getFacilitatorNotes(facPrompt)

        // 2) build AI prompt
        val aiPrompt = buildAiPrompt(
            userInput          = userInput,
            history            = history,
            fullProfilesJson   = "{}",
            summariesJson      = "[]",
            facilitatorNotes   = notes,
            chatDescription    = chatDesc
        )

        // 3) get mixtral output
        val output = engine.getBotOutput(aiPrompt)
        return output to bots
    }
}