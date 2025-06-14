package com.example.RealmsAI.ai

import android.graphics.Color
import android.util.Log
import com.example.RealmsAI.models.*
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.RealmsAI.network.ApiClients


object Facilitator {


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

    fun parseActivationAIResponse(
        response: String,
        previousAreas: List<Area>
    ): Pair<List<Pair<String, Boolean>>, List<Area>> {
        val activatedSlots = mutableListOf<Pair<String, Boolean>>()
        val updatedAreas = mutableListOf<Area>()

        val lines = response.lines().map { it.trim() }
        var inActivate = false
        var inAreaMap = false
        var currentAreaName: String? = null
        var characterNames: List<String> = emptyList()

        for (i in lines.indices) {
            val line = lines[i]
            // Block starts
            if (line.startsWith("characters_to_activate:")) {
                inActivate = true
                inAreaMap = false
                continue
            }
            if (line.startsWith("current_area_map:")) {
                inActivate = false
                inAreaMap = true
                continue
            }
            // Parse activations
            if (inActivate && line.startsWith("- name:")) {
                val name = line.removePrefix("- name:").trim()
                val nsfwLine = lines.getOrNull(i + 1)?.trim()
                val nsfw = nsfwLine?.startsWith("nsfw:") == true && nsfwLine.contains("true")
                activatedSlots.add(name to nsfw)
            }
            // Parse area map
            if (inAreaMap) {
                if (line.startsWith("- area:")) {
                    // Save previous area (if any)
                    if (currentAreaName != null) {
                        updatedAreas.add(
                            Area(
                                id = UUID.randomUUID().toString(),
                                creatorId = "",
                                name = currentAreaName!!,
                                locations = mutableListOf(
                                    LocationSlot(
                                        id = UUID.randomUUID().toString(),
                                        name = currentAreaName!!,
                                        uri = null,
                                        characters = characterNames // Fix: assign the parsed names!
                                    )
                                )
                            )
                        )
                    }
                    // Start new area
                    currentAreaName = line.removePrefix("- area:").trim()
                    characterNames = emptyList()
                } else if (line.startsWith("characters: [")) {
                    characterNames = line.substringAfter("[").substringBefore("]").split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    // Insert characterNames in the last location slot for the current area
                    // Optionally, you could add them as a field to LocationSlot if your model supports it
                    if (currentAreaName != null) {
                        updatedAreas.removeAll { it.name == currentAreaName }
                        updatedAreas.add(
                            Area(
                                id = UUID.randomUUID().toString(),
                                creatorId = "",
                                name = currentAreaName!!,
                                locations = mutableListOf(
                                    LocationSlot(
                                        id = UUID.randomUUID().toString(),
                                        name = currentAreaName!!,
                                        uri = null,
                                        characters = characterNames // Fix: assign the parsed names!
                                    )

                                )
                            )
                        )
                    }
                    currentAreaName = null // Mark area complete after processing
                }
            }
        }
        // Add any final area left open (edge case)
        if (currentAreaName != null) {
            updatedAreas.add(
                Area(
                    id = UUID.randomUUID().toString(),
                    creatorId = "",
                    name = currentAreaName!!,
                    locations = mutableListOf(
                        LocationSlot(
                            id = UUID.randomUUID().toString(),
                            name = currentAreaName!!,
                            uri = null
                        )
                    )
                )
            )
        }
        // Fallback: use previousAreas if nothing parsed
        return activatedSlots to (if (updatedAreas.isNotEmpty()) updatedAreas else previousAreas)
    }

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
}
