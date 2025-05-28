package com.example.RealmsAI

import com.example.RealmsAI.models.ChatMessage
import com.google.firebase.Timestamp
import org.json.JSONObject
import java.util.UUID

object StaticAIResponseParser {
    fun parseMultiBlockMessages(raw: String): List<ChatMessage> {
        // Simple line-by-line parser for your new facilitator block format
        val result = mutableListOf<ChatMessage>()
        val lines = raw.split("\n").map { it.trim() }
        var currentDelay = 0L
        var message = ""
        var imageUpdates = mapOf<Int, String?>()
        var bubbleBackgroundColor = 0xFFFFFFFF.toInt()
        var bubbleTextColor = 0xFF000000.toInt()
        var backgroundImage: String? = null

        for (line in lines) {
            when {
                line.startsWith("delay:") -> currentDelay = line.removePrefix("delay:").trim().toLongOrNull() ?: 0L
                line.startsWith("message:") -> message = line.removePrefix("message:").trim()
                line.startsWith("image_updates:") -> {
                    val json = JSONObject(line.removePrefix("image_updates:").trim())
                    imageUpdates = json.keys().asSequence().associate {
                        it.toInt() to json.optString(it).takeIf { s -> s != "null" }
                    }
                }
                line.startsWith("bubble_colors:") -> {
                    val json = JSONObject(line.removePrefix("bubble_colors:").trim())
                    bubbleBackgroundColor = android.graphics.Color.parseColor(json.optString("background", "#FFFFFF"))
                    bubbleTextColor = android.graphics.Color.parseColor(json.optString("text", "#000000"))
                }
                line.startsWith("background:") -> backgroundImage = line.removePrefix("background:").trim()
                line.isBlank() && message.isNotBlank() -> {
                    // End of block, push message
                    result.add(
                        ChatMessage(
                            id = UUID.randomUUID().toString(),
                            sender = "AI", // You can parse name if included in message
                            messageText = message,
                            timestamp = Timestamp.now(),
                            delay = currentDelay,
                            bubbleBackgroundColor = bubbleBackgroundColor,
                            bubbleTextColor = bubbleTextColor,
                            imageUpdates = imageUpdates,
                            backgroundImage = backgroundImage
                        )
                    )
                    // Reset
                    message = ""
                    imageUpdates = mapOf()
                    bubbleBackgroundColor = 0xFFFFFFFF.toInt()
                    bubbleTextColor = 0xFF000000.toInt()
                    backgroundImage = null
                    currentDelay = 0L
                }
            }
        }
        // Push last if needed
        if (message.isNotBlank()) {
            result.add(
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = "AI",
                    messageText = message,
                    timestamp = Timestamp.now(),
                    delay = currentDelay,
                    bubbleBackgroundColor = bubbleBackgroundColor,
                    bubbleTextColor = bubbleTextColor,
                    imageUpdates = imageUpdates,
                    backgroundImage = backgroundImage
                )
            )
        }
        return result
    }
}
