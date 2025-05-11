package com.example.RealmsAI.network

import com.example.RealmsAI.network.MixtralEngine
import com.example.RealmsAI.ai.buildAiPrompt
import com.example.RealmsAI.ai.buildFacilitatorPrompt
import android.util.Log

/**
 * Orchestrator ties together facilitator (OpenAI) and MixtralEngine, passing availableSlots,
 * history, and chat metadata to construct prompts.
 */
class OrchestratorService(
    private val facilitator: ChatGptFacilitator,
    private val engine: MixtralEngine
) {
    companion object {
        private const val TAG = "OrchestratorService"
    }

    /**
     * Sends a turn to the facilitator and Mixtral: returns Mixtral response + active slots.
     * @param userInput   the raw user message
     * @param history     full history string ("You: ...\nB1: ...")
     * @param chatDesc    chat description/personality blob
     * @param facilitatorState  JSON or string state for facilitator
     * @param availableSlots    list of slot IDs (e.g. ["B1","B2"])
     */
    suspend fun sendPrompt(
        userInput: String,
        history: String,
        chatDesc: String,
        facilitatorState: String,
        availableSlots: List<String>
    ): Pair<String, List<String>> {
        // 1) Build facilitator prompt
        val facPrompt = buildFacilitatorPrompt(
            userInput        = userInput,
            history          = history,
            facilitatorState = facilitatorState,
            availableSlots   = availableSlots
        )
        Log.d(TAG, "Facilitator prompt: $facPrompt")

        // 2) Call facilitator
        val (notes, activeBots) = try {
            facilitator.getFacilitatorNotes(facPrompt)
        } catch (e: Exception) {
            Log.e(TAG, "Facilitator call failed", e)
            Pair("", emptyList())
        }
        Log.d(TAG, "Facilitator notes: $notes")
        Log.d(TAG, "Facilitator activeBots: $activeBots")

        // 3) Build AI prompt for Mixtral using only activeSlots profiles in summariesJson
        val aiPrompt = buildAiPrompt(
            userInput       = userInput,
            history         = history,
            activeProfilesJson= summariesJsonFromSlots(availableSlots),
            summariesJson   = "[]",
            facilitatorNotes= notes,
            chatDescription = chatDesc,
            availableSlots     = availableSlots
        )
        Log.d(TAG, "Mixtral prompt: $aiPrompt")

        // 4) Call Mixtral
        val aiResponse = try {
            engine.getBotOutput(aiPrompt)
        } catch (e: Exception) {
            Log.e(TAG, "Mixtral call failed", e)
            ""
        }
        Log.d(TAG, "Mixtral response: $aiResponse")

        return Pair(aiResponse, activeBots)
    }

    /**
     * Helper: given full profiles JSON and availableSlots, extract only the profiles for those slots.
     * (stubbed here, implement per your storage schema)
     */
    private fun summariesJsonFromSlots(slots: List<String>): String {
        // TODO: lookup and serialize only those profiles
        return "[]"
    }
}
