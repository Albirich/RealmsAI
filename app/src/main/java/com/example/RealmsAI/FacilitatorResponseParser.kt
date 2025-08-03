package com.example.RealmsAI


import android.util.Log
import com.example.RealmsAI.models.ChatMessage
import com.example.RealmsAI.models.ModeSettings.VNRelationship
import com.example.RealmsAI.models.Relationship
import com.example.RealmsAI.models.SlotProfile
import com.example.RealmsAI.models.TaggedMemory
import com.google.gson.Gson
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID


object FacilitatorResponseParser {

    fun parseActivationAIResponse(
        response: String,
        slotRoster: List<SlotProfile>
    ): FacilitatorActivationResult {
        // Remove markdown fences and whitespace
        val cleaned = extractLastJsonObject(
            response.replace(Regex("^```json|^```|```$", RegexOption.MULTILINE), "")
                .trim()
        ) ?: throw JSONException("No JSON object found in AI response")

        val jsonObj = JSONObject(cleaned)
        val nextSlot = jsonObj.optString("next_slot", null)
        val narration = jsonObj.optString("narration", null)
        val isNSFW = jsonObj.optBoolean("nsfw", false)

        // --- Parse area_changes: keys are now SLOT IDS! ---
        val areaChangesObj = jsonObj.optJSONObject("area_changes")
        val areaChanges = mutableMapOf<String, Map<String, String>>() // slotId -> mapOf("area" to ..., "location" to ...)
        if (areaChangesObj != null) {
            for (slotId in areaChangesObj.keys()) {
                val areaLoc = areaChangesObj.optJSONObject(slotId)
                val area = areaLoc?.optString("area", "") ?: ""
                val location = areaLoc?.optString("location", "") ?: ""
                areaChanges[slotId] = mapOf("area" to area, "location" to location)
            }
        }

        val memoryIds: List<String> = if (jsonObj.has("memories")) {
            val memoriesJsonArray = jsonObj.optJSONArray("memories")
            if (memoriesJsonArray != null) {
                (0 until memoriesJsonArray.length())
                    .mapNotNull { idx -> memoriesJsonArray.optString(idx) }
            } else emptyList()
        } else emptyList()

        // --- Update slotRoster using slotId (not slot.name) ---
        val areaUpdatedRoster = slotRoster.map { slot ->
            val change = areaChanges[slot.slotId]
            if (change != null) {
                slot.copy(
                    lastActiveArea = change["area"],
                    lastActiveLocation = change["location"]
                )
            } else {
                slot
            }
        }

        val newNpcs = mutableListOf<SlotProfile>()
        if (jsonObj.has("new_npcs")) {
            val npcArray = jsonObj.optJSONArray("new_npcs")
            if (npcArray != null) {
                for (i in 0 until npcArray.length()) {
                    val npcObj = npcArray.getJSONObject(i)
                    // Construct SlotProfile from npcObj
                    val newSlot = SlotProfile(
                        slotId = npcObj.getString("slotId"),
                        name = npcObj.getString("name"),
                        profileType = npcObj.optString("profileType", "npc"),
                        summary = npcObj.optString("summary", ""),
                        lastActiveArea = npcObj.optString("lastActiveArea"),
                        lastActiveLocation = npcObj.optString("lastActiveLocation"),
                        memories = npcObj.optJSONArray("memories")?.let { memArray ->
                            (0 until memArray.length()).map { idx ->
                                val memObj = memArray.getJSONObject(idx)
                                TaggedMemory(
                                    id = UUID.randomUUID().toString(),
                                    tags = memObj.optJSONArray("tags")?.let { tagArr ->
                                        (0 until tagArr.length()).map { tagArr.getString(it) }
                                    } ?: emptyList(),
                                    text = memObj.optString("text", ""),
                                    nsfw = memObj.optBoolean("nsfw", false),
                                    messageIds = emptyList()
                                )
                            }
                        } ?: emptyList(),
                        age = npcObj.optString("age", "0").toIntOrNull() ?: 0,
                        abilities = npcObj.optString("abilities", ""),
                        bubbleColor = npcObj.optString("bubbleColor", "#FFFFFF"),
                        textColor = npcObj.optString("textColor", "#000000"),
                        gender = npcObj.optString("gender", ""),
                        height = npcObj.optString("height", ""),
                        weight = npcObj.optString("weight", ""),
                        eyeColor = npcObj.optString("eyeColor", ""),
                        hairColor = npcObj.optString("hairColor", ""),
                        physicalDescription = npcObj.optString("physicalDescription", ""),
                        personality = npcObj.optString("personality", ""),
                        privateDescription = npcObj.optString("privateDescription", ""),
                        sfwOnly = npcObj.optBoolean("sfwOnly", false)
                    )
                    newNpcs.add(newSlot)
                }
            }
        }

        val updatedRoster = areaUpdatedRoster + newNpcs

        return FacilitatorActivationResult(
            nextSlot = nextSlot,
            updatedRoster = updatedRoster,
            newNpcs = newNpcs,
            areaChanges = areaChanges,
            narration = narration,
            isNSFW = isNSFW,
            memoryIds = memoryIds
        )
    }

    fun extractLastJsonObject(text: String): String? {
        var lastStart = -1
        var braceCount = 0
        var result: String? = null

        for (i in text.indices.reversed()) {
            val c = text[i]
            if (c == '}') {
                if (braceCount == 0) lastStart = i
                braceCount++
            } else if (c == '{') {
                braceCount--
                if (braceCount == 0 && lastStart != -1) {
                    result = text.substring(i, lastStart + 1)
                    break
                }
            }
        }
        return result
    }

    data class FacilitatorActivationResult(
        val nextSlot: String?,
        val updatedRoster: List<SlotProfile>,
        val areaChanges: Map<String, Map<String, String>>,
        val newNpcs: List<SlotProfile>,
        val narration: String?,
        val isNSFW: Boolean,
        val memoryIds: List<String> = emptyList()
    )




    fun parseRoleplayAIResponse(response: String): ParsedRoleplayResult {
        val gson = Gson()
        val cleaned = response
            .replace(Regex("^```json|^```|```$", RegexOption.MULTILINE), "")
            .trim()
        try {
            val roleplayResponse = gson.fromJson(cleaned, RoleplayResponse::class.java)

            val safeMessages = roleplayResponse.messages.map { msg ->
                val safeId = msg.id.takeIf { !it.isNullOrBlank() } ?: UUID.randomUUID().toString()
                val isIntent = msg.senderId == "intent"
                msg.copy(
                    id = safeId,
                    visibility = !isIntent
                )
            }

            val relationshipChanges = roleplayResponse.relationship?.mapNotNull { entry ->
                val match = Regex("RELATIONSHIPPOINTS([+-]\\d+):(.*)").find(entry)
                match?.let {
                    val delta = it.groupValues[1].toIntOrNull() ?: 0
                    val relationshipId = it.groupValues[2].trim()
                    RelationshipPointChange(relationshipId, delta)
                }
            } ?: emptyList()

            return ParsedRoleplayResult(
                messages = safeMessages,
                newMemory = roleplayResponse.new_memory,
                relationshipChanges = relationshipChanges,
                actions = roleplayResponse.actions ?: emptyList()
            )
        } catch (e: Exception) {
            Log.e("FacilitatorResponseParser", "Malformed JSON (roleplay): $response", e)
            return ParsedRoleplayResult(messages = emptyList(), newMemory = null)
        }
    }

    data class RoleplayResponse(
        val messages: List<ChatMessage>,
        val new_memory: NewMemory? = null,
        val relationship: List<String>? = null,
        val actions: List<Action>?
    )

    data class RelationshipPointChange(
        val relationshipId: String,
        val delta: Int
    )

    data class Action(
        val type: String,
        val slot: String,
        val stat: String,
        val mod: Int
    )

    data class ParsedRoleplayResult(
        val messages: List<ChatMessage>,
        val newMemory: NewMemory? = null,
        val relationshipChanges: List<RelationshipPointChange> = emptyList(),
        val actions: List<Action> = emptyList()
    )

    fun updateRelationshipLevel(relationship: VNRelationship) {
        // Sort levels by threshold (ascending), just in case
        val levels = relationship.levels.sortedBy { it.threshold }
        val points = relationship.points

        // Default to the lowest level if no match (shouldn't happen if level 0 is 0)
        var newLevel = 0

        // Iterate and find the highest level whose threshold is <= points
        for (level in levels) {
            if (points >= level.threshold) {
                newLevel = level.level
            } else {
                break // Stop once points are below the threshold
            }
        }
        relationship.currentLevel = newLevel
    }

    data class NewMemory(
        val tags: List<String>,
        val text: String,
        val nsfw: Boolean
    )
}
