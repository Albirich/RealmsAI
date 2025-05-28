package com.example.RealmsAI

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
            try {
                // Optional: If your facilitator outputs real JSON, parse it as JSONObject:
                // val obj = JSONObject(block)
                // If it's key:value lines, parse manually:

                val map = mutableMapOf<String, String>()
                block.lines().forEach { line ->
                    val idx = line.indexOf(':')
                    if (idx > 0) {
                        val key = line.substring(0, idx).trim()
                        val value = line.substring(idx + 1).trim().removeSurrounding("\"")
                        map[key] = value
                    }
                }

                val id = System.currentTimeMillis().toString()
                val message = map["message"] ?: ""
                val delay = map["delay"]?.toLongOrNull() ?: 0L
                val imageUpdates = map["image_updates"]?.let { parseImageUpdates(it) } ?: emptyMap()
                val bubbleColors = map["bubble_colors"]?.let { parseBubbleColors(it) } ?: Pair(0xFFFFFFFF.toInt(), 0xFF000000.toInt())
                val backgroundImage = map["background"]

                messages.add(
                    ChatMessage(
                        id = id,
                        sender = parseSpeaker(message), // or use a field if you add one
                        messageText = message,
                        timestamp = Timestamp.now(),
                        delay = delay,
                        bubbleBackgroundColor = bubbleColors.first,
                        bubbleTextColor = bubbleColors.second,
                        imageUpdates = imageUpdates,
                        backgroundImage = backgroundImage
                    )
                )

            } catch (e: Exception) {
                // Skip malformed blocks
            }
        }
        return messages
    }

    // Helper to parse image_updates: { 0: "...", 1: "...", ... }
    private fun parseImageUpdates(raw: String): Map<Int, String?> {
        return Regex("""(\d+):\s*"([^"]*)"""")
            .findAll(raw)
            .associate { matchResult -> matchResult.groupValues[1].toInt() to matchResult.groupValues[2].ifBlank { null } }
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
