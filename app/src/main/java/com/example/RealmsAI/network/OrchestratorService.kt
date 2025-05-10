package com.example.RealmsAI.network

import com.example.RealmsAI.ai.buildAiPrompt
import com.example.RealmsAI.ai.buildFacilitatorPrompt

/**
 * A simple interface for sending a user prompt through the facilitator → Mixtral pipeline.
 * Returns a Pair of (Mixtral-generated text, list of active slot IDs).
 */
interface AiService {
    suspend fun sendPrompt(
        userInput: String,
        history:   String,
        chatDesc:  String
    ): Pair<String, List<String>>
}

class OrchestratorService(
    private val facilitator: ChatGptFacilitator,
    private val engine:      MixtralEngine
) : AiService {

    override suspend fun sendPrompt(
        userInput: String,
        history:   String,
        chatDesc:  String
    ): Pair<String, List<String>> {
        // 1) Build the facilitator prompt. Pass in the list of slots that could speak.
        //    (Replace this stub list with one derived from your chat profile.)
        val availableSlots = listOf("B1", "B2")  // TODO: compute from your fullProfilesJson

        val facPrompt = buildFacilitatorPrompt(
            userInput         = userInput,
            history           = history,
            facilitatorState  = "",            // e.g. volumes/locations JSON if you have it
            availableSlots    = availableSlots
        )

        // 2) Call the facilitator and unpack notes + chosen activeBots
        val (notes, activeSlots) = facilitator.getFacilitatorNotes(facPrompt)

        // 3) Build the Mixtral prompt, now passing in the activeSlots returned above
        val aiPrompt = buildAiPrompt(
            userInput           = userInput,
            history             = history,
            activeProfilesJson  = "{}",       // TODO: your actual fullProfilesJson
            summariesJson       = "[]",       // TODO: your inactive summaries JSON
            facilitatorNotes    = notes,
            chatDescription     = chatDesc,
            activeSlots         = activeSlots
        )

        // 4) Call Mixtral and return its output along with the active slot list
        val mixtralOutput = engine.getBotOutput(aiPrompt)
        return mixtralOutput to activeSlots
    }
}
