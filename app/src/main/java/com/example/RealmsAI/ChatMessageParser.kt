package com.example.RealmsAI

import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.ChatMessage
import com.example.RealmsAI.models.ChatMode
import com.google.firebase.Timestamp
import org.json.JSONObject
import java.util.UUID

class ChatMessageParser(
    private val chatAdapter: ChatAdapter,
    private val chatRecycler: RecyclerView,
    private val onNewMessage: (String, Map<String, String>) -> Unit,
    private val chatId: String,
    private val sessionId: String,
    private val chatMode: ChatMode
) {

    // === 1. Bracketed (legacy) Format ===
    private val oneOnOneRe =
        Regex("""\[\s*([\w\s]+)\s*,\s*(\w+)\s*,\s*(\d+)\s*]\s*["“]?(.+?)["”]?$""")

    fun parseJsonMessages(raw: String): List<ChatMessage> {
        // Return an empty list for now
        return emptyList()
    }

    fun parseBracketed(raw: String): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        raw.split(Regex("(?=\\[)"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                oneOnOneRe.matchEntire(line)?.destructured?.let { (slot, pose, timing, text) ->
                    messages.add(
                        ChatMessage(
                            id = UUID.randomUUID().toString(),
                            sender = slot,
                            messageText = text.trim(),
                            timestamp = Timestamp.now(),
                            // If you have emotion as a field, include here:
                            // emotion = pose
                        )
                    )
                }
            }
        return messages
    }
}

    // === 2. Facilitator Block Format ===
    // Example:
    // delay: 500
    // message: Naruto: "Hey!"
    // image_updates: { 0: "...", 1: "...", ... }
    // bubble_colors: { background: "#FFFFFF", text: "#000000" }
    // background: ... (optional)
/*    fun parseFacilitatorBlocks(raw: String): List<ChatMessage> {

        val messages = mutableListOf<ChatMessage>()
        val blocks = raw.split(Regex("(?=delay:)")).map { it.trim() }.filter { it.isNotEmpty() }

        for (block in blocks) {
            var delay = 0L
            var message = ""
            var bubbleBackgroundColor = 0xFFFFFFFF.toInt()
            var bubbleTextColor = 0xFF000000.toInt()
            val poseUris = mutableMapOf<String, String?>()
            val keys = jsonObj.keys()
            for (key in keys) {
                poseUris[key] = jsonObj.optString(key, null)
            }


            var backgroundImage: String? = null

            block.lines().forEach { line ->
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("delay:") -> {
                        delay = trimmed.removePrefix("delay:").trim().toLongOrNull() ?: 0L
                    }
                    trimmed.startsWith("message:") -> {
                        message = trimmed.removePrefix("message:").trim()
                    }
                    trimmed.startsWith("image_updates:") -> {
                        // Expecting: image_updates: { 0: "...", 1: "...", ... }
                        val updates = mutableMapOf<Int, String?>()
                        val content = trimmed.removePrefix("image_updates:").trim()
                        val regex = Regex("""(\d+):\s*"([^"]*|null)"""")
                        regex.findAll(content).forEach { m ->
                            val slot = m.groupValues[1].toInt()
                            val url = m.groupValues[2].takeIf { it != "null" }
                            updates[slot] = url
                        }
                        imageUpdates = updates
                    }
                    trimmed.startsWith("bubble_colors:") -> {
                        // bubble_colors: { background: "#FFFFFF", text: "#000000" }
                        val regex = Regex("""background:\s*"(#\w+)"[,}]""")
                        bubbleBackgroundColor = regex.find(trimmed)?.groupValues?.getOrNull(1)?.let { hexToColorInt(it) }
                            ?: bubbleBackgroundColor
                        val regex2 = Regex("""text:\s*"(#\w+)"[,}]""")
                        bubbleTextColor = regex2.find(trimmed)?.groupValues?.getOrNull(1)?.let { hexToColorInt(it) }
                            ?: bubbleTextColor
                    }
                    trimmed.startsWith("background:") -> {
                        backgroundImage = trimmed.removePrefix("background:").trim().removeSurrounding("\"")
                    }
                }
            }
            // Use the first word before ":" as sender, if available (or "Narrator")
            val sender = message.substringBefore(":").ifBlank { "Narrator" }.trim()
            val msgText = message.substringAfter(":", missingDelimiterValue = message).trim()

            messages.add(
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = sender,
                    messageText = msgText,
                    timestamp = Timestamp.now(),
                    delay = delay,
                    bubbleBackgroundColor = bubbleBackgroundColor,
                    bubbleTextColor = bubbleTextColor,
                    imageUpdates = imageUpdates,
                    backgroundImage = backgroundImage
                )
            )
        }
        return messages
    }

    // === 3. JSON (future) Format ===
    fun parseJsonMessages(raw: String): List<ChatMessage> {
        // Example for future: list of ChatMessage JSON objects
        val messages = mutableListOf<ChatMessage>()
        val arr = org.json.JSONArray(raw)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            messages.add(
                ChatMessage(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    sender = obj.optString("sender", "Narrator"),
                    messageText = obj.optString("messageText", ""),
                    timestamp = Timestamp.now(),
                    delay = obj.optLong("delay", 0L),
                    bubbleBackgroundColor = obj.optInt("bubbleBackgroundColor", 0xFFFFFFFF.toInt()),
                    bubbleTextColor = obj.optInt("bubbleTextColor", 0xFF000000.toInt()),
                    imageUpdates = parseImageUpdatesJson(obj.optJSONObject("imageUpdates")),
                    backgroundImage = obj.optString("backgroundImage", null)
                )
            )
        }
        return messages
    }

    // === Helper for converting "#RRGGBB" to Int ===
    private fun hexToColorInt(hex: String): Int {
        // Accepts "#FFFFFF" and similar
        return try {
            android.graphics.Color.parseColor(hex)
        } catch (e: Exception) {
            0xFFFFFFFF.toInt()
        }
    }

    // Helper for JSON image_updates
    private fun parseImageUpdatesJson(obj: JSONObject?): Map<Int, String?> {
        if (obj == null) return emptyMap()
        val map = mutableMapOf<Int, String?>()
        for (key in obj.keys()) {
            map[key.toIntOrNull() ?: continue] = obj.optString(key)
        }
        return map
    }*/
