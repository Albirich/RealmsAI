package com.example.RealmsAI.ai

import com.example.RealmsAI.models.*
import com.example.RealmsAI.network.ModelClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.example.RealmsAI.StaticAIResponseParser


object Facilitator {

    // Place your API keys somewhere secure, not hardcoded! (Pass them as params or use BuildConfig)
    private const val OPENAI_MODEL = "gpt-4.1-nano-2025-04-14" // or "gpt-4o" if you have access
    private const val MIXTRAL_MODEL = "mistralai/mixtral-8x7b-instruct"

    suspend fun handleUserMessage(
        userInput: String,
        sessionProfile: SessionProfile,
        chatHistory: List<ChatMessage>,
        facilitatorNotes: String,
        slotIdToCharacterProfile: Map<String, CharacterProfile>,
        openAiKey: String,
        mixtralKey: String
    ): List<ChatMessage> = withContext(Dispatchers.IO) {

        // 1. Prepare all info for the facilitator
        val slotRoster = sessionProfile.slotRoster

        // Outfits per slot (currentOutfit in CharacterProfile if present, else first outfit or "default")
        val outfits: Map<String, String> = slotRoster.associate { slot ->
            val profile = slotIdToCharacterProfile[slot.slot]
            val currOutfit = profile?.currentOutfit?.takeIf { it.isNotEmpty() }
                ?: profile?.outfits?.firstOrNull()?.name
                ?: "default"
            slot.slot to currOutfit
        }

        // Build poseImageUrls: Map<slotId, Map<poseName, url>>
        val poseImageUrls: Map<String, Map<String, String>> = slotRoster.associate { slot ->
            val profile = slotIdToCharacterProfile[slot.slot]
            val outfit = profile?.outfits?.find { it.name == (outfits[slot.slot] ?: "default") }
            // If outfit is present, use its poseUris, otherwise fall back to an empty map
            slot.slot to (outfit?.poseUris ?: emptyMap())
        }

        // Bubble/message colors: for now, fallback to defaults or assign as needed
        val availableColors: Map<String, String> = slotRoster.associate { slot ->
            // Use slot, id, or name for key—choose whatever is consistent in your system
            slot.slot to "#FFFFFF" // fallback white; can change this to be dynamic later
        }

        // Compose background image (can be null)
        val backgroundImage: String? = sessionProfile.backgroundUri

        // Prepare scene/summary (session description)
        val sessionDescription = sessionProfile.sessionDescription

        // Compile recent history (as plain string)
        val history = chatHistory.takeLast(15).joinToString("\n") { "${it.sender}: ${it.messageText}" }

        // Faciltiator notes: leave empty if not used
        // val facilitatorNotes = facilitatorNotes

        // 2. Build the facilitator prompt (calls your PromptBuilder)
        val facilitatorPrompt = PromptBuilder.buildFacilitatorPrompt(
            slotRoster = slotRoster,
            outfits = outfits,
            poseImageUrls = poseImageUrls,
            sessionDescription = sessionDescription,
            history = history,
            facilitatorNotes = facilitatorNotes,
            userInput = userInput,
            backgroundImage = backgroundImage,
            availableColors = availableColors
        )

        // 3. Package prompt for OpenAI/Mixtral (always use OpenAI for facilitator!)
        val promptJson = JSONObject().apply {
            put("model", "gpt-4-turbo") // Facilitator should always be GPT-4 (OpenAI)
            put("messages", JSONArray().put(
                JSONObject().put("role", "user").put("content", facilitatorPrompt)
            ))
            put("temperature", 0.6) // Lower temp for structure/consistency
        }.toString()

        // 4. Send to OpenAI/Mixtral
        val aiResponseJson = ModelClient.callModel(
            promptJson = promptJson,
            forFacilitator = true, // always true for this step
            openAiKey = openAiKey,
            mixtralKey = mixtralKey // not needed but passed for interface compatibility
        )

        // 5. Parse the multi-message response blocks
        val aiRawResponse = aiResponseJson.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?: error("No response content from model")

        // Your StaticAIResponseParser needs to parse the *new* multi-block format into a List<ChatMessage>.
        val messages: List<ChatMessage> = StaticAIResponseParser.parseMultiBlockMessages(aiRawResponse)

        // 6. Return ready-to-display messages for the ChatAdapter
        messages
    }

}
