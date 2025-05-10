package com.example.RealmsAI.network

import android.util.Log
import com.example.RealmsAI.network.MixtralEngine
import com.example.RealmsAI.ai.buildAiPrompt
import com.example.RealmsAI.ai.buildFacilitatorPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

interface AiService {
    /**
     * Sends the two-step prompt, returning the Mixtral output
     * and the list of slots the facilitator activated.
     */
    suspend fun sendPrompt(
        userInput: String,
        history:   String,
        chatDesc:  String
    ): Pair<String, List<String>>
}

class OrchestratorService(
    private val facilitator: ChatGptFacilitator,
    private val engine:      MixtralEngine,
    private val fullProfilesJson: String,
    private val summariesJson:    String = "[]"
) : AiService {

    private var facilitatorNotes: String = ""
    companion object {
        private const val TAG = "OrchestratorSvc"
    }

    override suspend fun sendPrompt(
        userInput: String,
        history:   String,
        chatDesc:  String
    ): Pair<String, List<String>> {
        // 1) figure out slots
        val availableSlots = extractAvailableSlots(fullProfilesJson)
        Log.d(TAG, "Available slots: $availableSlots")

        // 2) build facilitator prompt
        val facPrompt = buildFacilitatorPrompt(
            userInput        = userInput,
            history          = history,
            facilitatorState = facilitatorNotes,
            availableSlots   = availableSlots  // ← here!
        )

        val (notes, activeBots) = facilitator.getFacilitatorNotes(facPrompt)

    facilitatorNotes = notes

        // 4) Build the AI (Mixtral) prompt with the fresh notes
        val aiPrompt = buildAiPrompt(
            userInput        = userInput,
            history          = history,
            fullProfilesJson = fullProfilesJson,
            summariesJson    = summariesJson,
            facilitatorNotes = notes,
            chatDescription  = chatDesc
        )

        // 5) Finally call Mixtral
        val mixtralOutput = engine.getBotOutput(aiPrompt)

        // Return the generated text *and* which slots to show
        return mixtralOutput to activeBots
    }

    /**
     * Reads either an object or array form of fullProfilesJson
     * and returns a List of all "slot" IDs, e.g. ["B1","B2",...].
     */
    private fun extractAvailableSlots(json: String): List<String> {
        // Try object form: { "B1": {...}, "B2": {...} }
        runCatching {
            val obj = JSONObject(json)
            return obj.keys().asSequence().toList()
        }
        // Fallback to array form: [ {"slot":"B1",...}, {"slot":"B2",...} ]
        runCatching {
            val arr = JSONArray(json)
            return (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.optString("slot")
            }
        }
        return emptyList()
    }
}
