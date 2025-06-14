package com.example.RealmsAI

import android.util.Log
import android.util.Log.e
import com.example.RealmsAI.models.ChatMessage
import com.google.firebase.Timestamp
import org.json.JSONObject

object FacilitatorResponseParser {

    fun parseFacilitatorBlocks(raw: String): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()

        // 1. Find all triple-backtick code blocks (prefer these if present)
        val tripleBlocks = Regex("""```(?:json)?\s*([\s\S]*?)\s*```""")
            .findAll(raw)
            .map { it.groupValues[1].trim() }
            .toList()

        // 2. If any code blocks, parse those; otherwise split by blank lines
        val blocks = if (tripleBlocks.isNotEmpty()) tripleBlocks
        else raw.split(Regex("""\n\s*\n""")).map { it.trim() }.filter { it.isNotEmpty() }

        for (block in blocks) {
            Log.d("FacilitatorResponseParser", "Raw block:\n$block")
            val cleanBlock = block.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val jsonBlocks = extractTopLevelJsonBlocks(cleanBlock)

            var foundJson = false
            for (jsonString in jsonBlocks) {
                try {
                    val jsonObj = JSONObject(jsonString)
                    foundJson = true
                    val map = mutableMapOf<String, String>()
                    for (key in jsonObj.keys()) {
                        map[key] = jsonObj.get(key).toString()
                    }
                    val message = map["message"] ?: ""
                    if (message.isNotBlank()) {
                        val id = System.currentTimeMillis().toString()
                        val sender = map["sender"] ?: "Narrator"
                        val delay = map["delay"]?.toLongOrNull() ?: 0L

                        val imageUpdates: Map<String, String?> = map["image_updates"]?.let {
                            parseImageUpdates(it)
                        } ?: emptyMap()

                        // Allow both keys for background
                        val backgroundImage = map["background"] ?: map["background_image"]
                        Log.d("FacilitatorResponseParser", "Parsed imageUpdates: $imageUpdates")
                        messages.add(
                            ChatMessage(
                                id = id,
                                sender = sender,
                                messageText = message,
                                timestamp = Timestamp.now(),
                                delay = delay,
                                imageUpdates = imageUpdates,
                                backgroundImage = backgroundImage
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e("FacilitatorResponseParser", "Failed to parse JSON: $jsonString", e)
                }
            }

            // If no JSON blocks found, try YAML-style key-value parsing ONCE for the whole block
            if (!foundJson) {
                try {
                    val map = mutableMapOf<String, String>()
                    val lines = cleanBlock.lines()
                    var i = 0
                    while (i < lines.size) {
                        val line = lines[i]
                        if (line.startsWith("#")) { i++; continue }
                        val idx = line.indexOf(':')
                        if (idx > 0) {
                            val key = line.substring(0, idx).trim()
                            var value = line.substring(idx + 1).trim().removeSurrounding("\"")
                            // Detect and collect multi-line value (for image_updates)
                            if (key == "image_updates" && (value.isEmpty() || value == "|" || value == "")) {
                                val multiLineValue = StringBuilder()
                                i++
                                // Collect all indented lines (YAML-style)
                                while (i < lines.size && (lines[i].startsWith(" ") || lines[i].startsWith("\t") || Regex("""^\d+: """).containsMatchIn(lines[i]))) {
                                    multiLineValue.append(lines[i].trim()).append("\n")
                                    i++
                                }
                                map[key] = multiLineValue.toString().trim()
                                continue // already incremented i in while loop
                            } else {
                                map[key] = value
                            }
                        }
                        i++
                    }
                    val message = map["message"] ?: ""
                    if (message.isNotBlank()) {
                        val id = System.currentTimeMillis().toString()
                        val sender = map["sender"] ?: "Narrator"
                        val delay = map["delay"]?.toLongOrNull() ?: 0L

                        val imageUpdates: Map<String, String?> = map["image_updates"]?.let {
                            parseImageUpdates(it)
                        } ?: emptyMap()

                        // Allow both keys for background
                        val backgroundImage = map["background"] ?: map["background_image"]
                        Log.d("FacilitatorResponseParser", "Parsed imageUpdates: $imageUpdates")
                        messages.add(
                            ChatMessage(
                                id = id,
                                sender = sender,
                                messageText = message,
                                timestamp = Timestamp.now(),
                                delay = delay,
                                imageUpdates = imageUpdates,
                                backgroundImage = backgroundImage
                            )
                        )
                    }
                } catch (_: Exception) {}
            }
        }
        return messages
    }

    // Helper: Extracts only *top-level* JSON blocks from a text string.
    fun extractTopLevelJsonBlocks(input: String): List<String> {
        val blocks = mutableListOf<String>()
        val sb = StringBuilder()
        var openBraces = 0
        var inBlock = false
        for (c in input) {
            if (c == '{') {
                openBraces++
                inBlock = true
            }
            if (inBlock) sb.append(c)
            if (c == '}') {
                openBraces--
                if (openBraces == 0 && inBlock) {
                    blocks.add(sb.toString().trim())
                    sb.clear()
                    inBlock = false
                }
            }
        }
        return blocks
    }


    // Example: Handles both {"0": "..."} and 0: ...
    fun parseImageUpdates(input: String): Map<String, String?> {
        return try {
            val out = mutableMapOf<String, String?>()
            val trimmed = input.trim()
            // Try JSON
            if (trimmed.startsWith("{")) {
                val obj = JSONObject(trimmed)
                obj.keys().forEach { k ->
                    val v = obj.get(k)
                    when {
                        v == JSONObject.NULL -> out[k] = null
                        v is String -> out[k] = v.takeIf { it != "null" }
                        v is org.json.JSONArray && v.length() > 0 -> out[k] = v.getString(0)
                        v is org.json.JSONArray && v.length() == 0 -> out[k] = null
                        else -> out[k] = v.toString()
                    }
                }
                out
            } else {
                // YAML-style: 0: url or 0: null (single-line only)
                trimmed
                    .removeSurrounding("{", "}")
                    .lines()
                    .mapNotNull { entry ->
                        val idx = entry.indexOf(':')
                        if (idx > 0) {
                            val key = entry.substring(0, idx).trim().removeSurrounding("\"")
                            val value = entry.substring(idx + 1).trim().removeSurrounding("\"")
                            key to value.takeIf { it != "null" }
                        } else null
                    }
                    .toMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun extractAvatarSlots(raw: String): Triple<Map<Int, Pair<String?, String?>>, String?, Pair<String?, String?>?> {
        val avatarMap = mutableMapOf<Int, Pair<String?, String?>>()
        var currentBackground: String? = null
        var areaLocIds: Pair<String?, String?>? = null
        val marker = "# Avatar Slots"
        val idx = raw.indexOf(marker)
        if (idx == -1) return Triple(avatarMap.toMap(), null, null)

        // Get lines after "# Avatar Slots"
        val lines = raw.substring(idx + marker.length)
            .trim()
            .lines()
            .filter { it.isNotBlank() }

        for (line in lines) {
            // Check for background line
            val bgMatch = Regex("""^background:\s*(.*)""").find(line)
            if (bgMatch != null) {
                currentBackground = bgMatch.groupValues[1].trim()
                // Try to extract IDs: e.g. (a1/l2) at end
                val idMatch = Regex("""\(([^/]+)?/([^)]+)?\)""").find(currentBackground)
                if (idMatch != null) {
                    val areaId = idMatch.groupValues.getOrNull(1)
                    val locationId = idMatch.groupValues.getOrNull(2)
                    areaLocIds = Pair(areaId, locationId)
                }
                continue
            }
            // Parse avatar slots as before
            val slotMatch = Regex("""^(\d+):\s*(.*?)(?:,\s*(.*))?$""").find(line)
            if (slotMatch != null) {
                val slotNum = slotMatch.groupValues[1].toInt()
                val char = slotMatch.groupValues[2].takeIf { it != "empty" }?.takeIf { it.isNotBlank() }
                val emotion = slotMatch.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }
                avatarMap[slotNum] = Pair(char, emotion)
            }
        }
        return Triple(avatarMap.toMap(), currentBackground, areaLocIds)
    }


}
