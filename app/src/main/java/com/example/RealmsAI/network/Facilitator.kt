package com.example.RealmsAI.ai

import android.graphics.Color
import android.util.Log
import com.example.RealmsAI.models.*
import com.google.firebase.Timestamp
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.RealmsAI.network.ApiClients

object Facilitator {

    // Main entry point: handle activation prompt and return ChatMessages from activated bots
    suspend fun handleActivationPrompt(
        activationPrompt: String,
        openAiKey: String,
        mixtralKey: String,
        slotIdToCharacterProfile: Map<String, CharacterProfile>
    ): List<ChatMessage> {
        Log.d("Facilitator", "ActivationPrompt:\n$activationPrompt")

        // Step 1: Send prompt to Activation AI using your existing OpenAiService
        val activationAiResponse = callActivationAI(activationPrompt, openAiKey)

        Log.d("Facilitator", "Raw Activation AI Response:\n$activationAiResponse")

        // Step 2: Parse the raw response for bots to activate and NSFW flags
        val activatedSlots = parseActivatedSlotsFromResponse(activationAiResponse, slotIdToCharacterProfile)

        Log.d("Facilitator", "Activated bots this round: ${activatedSlots.joinToString()}")

        // Step 3: For each activated bot, call their AI and build ChatMessages
        val responses = mutableListOf<ChatMessage>()
        for ((slotId, isNSFW) in activatedSlots) {
            val charPrompt = buildCharPromptForSlot(activationPrompt, slotId)
            Log.d("Facilitator", "Slot $slotId (${if (isNSFW) "Mixtral" else "OpenAI"}): Prompt = $charPrompt")

            val aiResponseText = if (isNSFW) {
                callMixtralApi(charPrompt, mixtralKey)
            } else {
                callOpenAiApi(charPrompt, openAiKey)
            }

            Log.d("Facilitator", "Slot $slotId Response: $aiResponseText")

            val charProfile = slotIdToCharacterProfile[slotId]
            val chatMsg = ChatMessage(
                id = generateMsgId(),
                sender = getCharacterNameForSlot(slotId, slotIdToCharacterProfile),
                messageText = aiResponseText,
                timestamp = Timestamp.now(),
                bubbleBackgroundColor = Color.parseColor(charProfile?.bubbleColor ?: "#FFFFFF"),
                bubbleTextColor = Color.parseColor(charProfile?.textColor ?: "#000000")
            )
            Log.d("Facilitator", "ChatMessage: ${chatMsg.sender}: ${chatMsg.messageText}")

            responses.add(chatMsg)
        }

        return responses
    }

    // Use your existing OpenAiService to send the activation prompt to OpenAI API
    private suspend fun callActivationAI(prompt: String, apiKey: String): String = withContext(Dispatchers.IO) {
        try {
            val request = com.example.RealmsAI.network.OpenAiChatRequest(
                model = "gpt-4o-mini",
                messages = listOf(com.example.RealmsAI.network.Message(role = "system", content = prompt))
            )
            val response = ApiClients.openai.getFacilitatorNotes(request)
            val content = response.choices.firstOrNull()?.message?.content ?: ""
            content
        } catch (ex: Exception) {
            Log.e("Facilitator", "Error calling Activation AI", ex)
            ""
        }
    }

    // Parse the raw activation AI response to find bots and NSFW flags
    fun parseActivatedSlotsFromResponse(
        rawResponse: String,
        slotIdToCharacterProfile: Map<String, CharacterProfile>
    ): List<Pair<String, Boolean>> {
        val activated = mutableListOf<Pair<String, Boolean>>()
        val regex = Regex("""- name:\s*(\S+)\s+nsfw:\s*(true|false)""", RegexOption.IGNORE_CASE)
        for (match in regex.findAll(rawResponse)) {
            val botName = match.groupValues[1]
            val nsfw = match.groupValues[2].toBoolean()
            val slotId = slotIdToCharacterProfile.entries.find { it.value.name == botName }?.key
            if (slotId != null) {
                activated.add(slotId to nsfw)
            } else {
                Log.w("Facilitator", "Bot name '$botName' not found in slotIdToCharacterProfile keys")
            }
        }
        return activated
    }

    // Build the prompt for each character (you can customize this)
    private fun buildCharPromptForSlot(activationPrompt: String, slotId: String): String {
        // For now, just return the full activationPrompt. You can slice or customize per slot.
        return activationPrompt
    }

    // Call OpenAI for the actual character reply
    private suspend fun callOpenAiApi(prompt: String, key: String): String {
        // Reuse the same helper you already have in ApiClients / ModelClient or write a similar call here
        // For simplicity, call the same endpoint as ActivationAI, but ideally this can be different
        return callActivationAI(prompt, key)
    }

    // Call Mixtral API for NSFW character reply
    private suspend fun callMixtralApi(prompt: String, key: String): String {
        // Implement your Mixtral call (similar to OpenAI call)
        // Placeholder implementation:
        return "[Mixtral Stub] $prompt"
    }

    private fun getCharacterNameForSlot(slotId: String, slotIdToCharacterProfile: Map<String, CharacterProfile>): String {
        return slotIdToCharacterProfile[slotId]?.name ?: slotId
    }

    private fun generateMsgId(): String = UUID.randomUUID().toString()
}
