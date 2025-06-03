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
    val responses = mutableListOf<ChatMessage>()
    suspend fun getActivatedSlots(
        activationPrompt: String,
        openAiKey: String,
        slotIdToCharacterProfile: Map<String, CharacterProfile>
    ): List<Pair<String, Boolean>> {
        // 1. Call OpenAI to get the raw response
        val rawResponse = callActivationAI(activationPrompt, openAiKey)

        // 2. Parse the response into slot IDs and NSFW flags
        return parseActivatedSlotsFromResponse(rawResponse, slotIdToCharacterProfile)
    }
    fun activationRefusedOrMalformed(response: String): Boolean {
        if (response.isBlank()) return true
        val lower = response.lowercase()
        return lower.contains("sorry") ||
                lower.contains("cannot assist") ||
                lower.contains("not allowed") ||
                !lower.contains("characters_to_activate")
    }
    fun activationIsEmptyList(response: String): Boolean {
        val stripped = response.replace("\\s".toRegex(), "")
        return stripped.contains("characters_to_activate:[]") ||
                stripped.contains("\"characters_to_activate\":[]")
    }
    suspend fun callOpenAiFacilitator(prompt: String, apiKey: String): String = withContext(Dispatchers.IO) {
        try {
            val request = com.example.RealmsAI.network.OpenAiChatRequest(
                model = "gpt-4o-mini", // or whichever model you use
                messages = listOf(com.example.RealmsAI.network.Message(role = "system", content = prompt))
            )
            val response = ApiClients.openai.getFacilitatorNotes(request)
            response.choices.firstOrNull()?.message?.content ?: ""
        } catch (ex: Exception) {
            Log.e("Facilitator", "Error calling facilitator AI", ex)
            ""
        }
    }




    // Use your existing OpenAiService to send the activation prompt to OpenAI API
    suspend fun callActivationAI(prompt: String, apiKey: String): String = withContext(Dispatchers.IO) {
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
    private fun buildCharPromptForSlot(
        slotId: String,
        chatHistory: List<ChatMessage>,
        characterProfile: CharacterProfile,
        sessionSummary: String? = null,
        isNSFW: Boolean = false // true = Mixtral, false = OpenAI
    ): String {
        val sb = StringBuilder()

        sb.appendLine("# Character Profile")
        sb.appendLine("Name: ${characterProfile.name}")
        sb.appendLine("Personality: ${characterProfile.personality}")
        // Only include backstory for OpenAI, not Mixtral
        if (!isNSFW) {
            sb.appendLine("Backstory: ${characterProfile.backstory}")
        }
        if (!characterProfile.tags.isNullOrEmpty()) sb.appendLine("Tags: ${characterProfile.tags.joinToString()}")
        if (!characterProfile.relationships.isNullOrEmpty()) sb.appendLine("Relationships: ${characterProfile.relationships.joinToString { "${it.toName} (${it.type}): ${it.description}" }}")

        sb.appendLine()

        if (!sessionSummary.isNullOrBlank() && !isNSFW) {
            sb.appendLine("# Session Summary")
            sb.appendLine(sessionSummary)
            sb.appendLine()
        }

        sb.appendLine("# Recent Chat History")
        chatHistory.takeLast(10).forEach { msg ->
            sb.appendLine("${msg.sender}: ${msg.messageText}")
        }
        sb.appendLine()
        sb.appendLine("Reply as ${characterProfile.name}, in character, to the most recent user message.")

        return sb.toString()
    }


    // Call OpenAI for the actual character reply
    suspend fun callOpenAiApi(prompt: String, key: String): String = withContext(Dispatchers.IO) {
        try {
            Log.d("Facilitator", "[OpenAI] Character Prompt:\n$prompt")
            val request = com.example.RealmsAI.network.OpenAiChatRequest(
                model = "gpt-4o-mini",
                messages = listOf(com.example.RealmsAI.network.Message(role = "system", content = prompt))
            )
            val response = ApiClients.openai.getFacilitatorNotes(request)
            response.choices.firstOrNull()?.message?.content ?: ""
        } catch (ex: Exception) {
            Log.e("Facilitator", "Error calling OpenAI API", ex)
            ""
        }

    }


    suspend fun callMixtralApi(prompt: String, key: String): String = withContext(Dispatchers.IO) {
        try {
            Log.d("Facilitator", "[Mixtral] Character Prompt:\n$prompt")
            val engine = com.example.RealmsAI.network.MixtralEngine(ApiClients.mixtral)
            engine.getBotOutput(prompt)
        } catch (ex: Exception) {
            Log.e("Facilitator", "Error calling Mixtral API", ex)
            ""
        }
    }

    private fun getCharacterNameForSlot(slotId: String, slotIdToCharacterProfile: Map<String, CharacterProfile>): String {
        return slotIdToCharacterProfile[slotId]?.name ?: slotId
    }

    private fun generateMsgId(): String = UUID.randomUUID().toString()
}
