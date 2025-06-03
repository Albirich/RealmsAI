package com.example.RealmsAI

import android.util.Log
import com.example.RealmsAI.models.ChatMessage
import com.google.firebase.Timestamp
import org.json.JSONObject
import org.json.JSONException

object FacilitatorResponseParser {
    fun parseFacilitatorBlocks(raw: String): List<ChatMessage> {

        val messages = mutableListOf<ChatMessage>()
        // Split by blank lines (assuming each output block is separated)
        val blocks = raw.split(Regex("""\n\s*\n""")).map { it.trim() }.filter { it.isNotEmpty() }
        for (block in blocks) {

            Log.d("FacilitatorResponseParser", "Raw block:\n$block")
            try {
                val map = mutableMapOf<String, String>()
                block.lines().forEach { line ->
                    val idx = line.indexOf(':')
                    if (idx > 0) {
                        val key = line.substring(0, idx).trim()
                        val value = line.substring(idx + 1).trim().removeSurrounding("\"")
                        map[key] = value
                    }
                }
                val message = map["message"] ?: ""
                if (message.isNotBlank()) {
                    val id = System.currentTimeMillis().toString()
                    val sender = map["sender"] ?: "Narrator"
                    val message = map["message"] ?: ""
                    val delay = map["delay"]?.toLongOrNull() ?: 0L
                    val imageUpdates =
                        map["image_updates"]?.let { parseImageUpdates(it) } ?: emptyMap()
                    val bubbleColors = map["bubble_colors"]?.let { parseBubbleColors(it) } ?: Pair(
                        0xFFFFFFFF.toInt(),
                        0xFF000000.toInt()
                    )
                    val backgroundImage = map["background"]
                    Log.d("FacilitatorResponseParser", "Parsed imageUpdates: $imageUpdates")
                    messages.add(
                        ChatMessage(
                            id = id,
                            sender = sender,              // use sender directly
                            messageText = message,        // message is just the text
                            timestamp = Timestamp.now(),
                            delay = delay,
                            bubbleBackgroundColor = bubbleColors.first,
                            bubbleTextColor = bubbleColors.second,
                            imageUpdates = imageUpdates,
                            backgroundImage = backgroundImage
                        )
                    )
                }

            } catch (e: Exception) {
                // Skip malformed blocks
            }
        }
        return messages
    }
    fun extractAvatarSlots(raw: String): Map<Int, Pair<String?, String?>> {
        val avatarMap = mutableMapOf<Int, Pair<String?, String?>>()
        val marker = "# Avatar Slots"
        val idx = raw.indexOf(marker)
        if (idx == -1) return avatarMap // No block found

        // Get lines after "# Avatar Slots"
        val lines = raw.substring(idx + marker.length)
            .trim()
            .lines()
            .take(4) // only want lines for 0..3

        for (line in lines) {
            val slotMatch = Regex("""^(\d+):\s*(.*?)(?:,\s*(.*))?$""").find(line)
            if (slotMatch != null) {
                val slotNum = slotMatch.groupValues[1].toInt()
                val char = slotMatch.groupValues[2].takeIf { it != "empty" }?.takeIf { it.isNotBlank() }
                val emotion = slotMatch.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }
                avatarMap[slotNum] = Pair(char, emotion)
            }
        }
        return avatarMap
    }

    // Helper to parse image_updates: { 0: "...", 1: "...", ... }
    private fun parseImageUpdates(raw: String): Map<String, String?> {
        val imageUpdates = mutableMapOf<String, String?>()
        try {
            // Step 1: quote keys
            var fixed = raw.replace(Regex("""(\d+):"""), "\"$1\":")
            // Step 2: quote values, except for null
            fixed = fixed.replace(Regex(""":\s*([^",}{\s][^,}{\s]*)""")) { m ->
                val value = m.groupValues[1]
                if (value == "null") ": null" else ": \"$value\""
            }
            val jsonObj = JSONObject(fixed)
            for (key in jsonObj.keys()) {
                val value = jsonObj.opt(key)
                imageUpdates[key] = if (value == null || value == JSONObject.NULL) null else value.toString()
            }
        } catch (e: Exception) {
            Log.e("FacilitatorParser", "ImageUpdate parse error: $e")
        }
        return imageUpdates
    }

    // Helper to parse bubble_colors: { background: "#FFFFFF", text: "#000000" }
    private fun parseBubbleColors(raw: String): Pair<Int, Int> {
        val bgMatch = Regex("""background:\s*"(#[A-Fa-f0-9]{6,8})"""").find(raw)
        val txtMatch = Regex("""text:\s*"(#[A-Fa-f0-9]{6,8})"""").find(raw)
        val bgColor = bgMatch?.groupValues?.get(1)?.let { parseHexColor(it) } ?: 0xFFFFFFFF.toInt()
        val txtColor = txtMatch?.groupValues?.get(1)?.let { parseHexColor(it) } ?: 0xFF000000.toInt()
        return Pair(bgColor, txtColor)
    }

    private fun parseHexColor(hex: String): Int = hex.removePrefix("#").toLong(16).toInt() or (0xFF shl 24)

    // You might want to extract the speaker from message (e.g., "Naruto: ...")
    private fun parseSpeaker(message: String): String {
        val idx = message.indexOf(':')
        return if (idx > 0) message.substring(0, idx).trim() else "Narrator"
    }
}
