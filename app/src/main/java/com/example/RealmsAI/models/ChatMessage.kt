package com.example.RealmsAI.models

import com.google.firebase.Timestamp

data class ChatMessage(
    val id: String = System.currentTimeMillis().toString(),
    val sender: String,             // e.g. "Naruto" or "Narrator"
    val messageText: String,        // the message itself
    val timestamp: Timestamp? = null,
    val delay: Long = 0L,           // in milliseconds
    val bubbleBackgroundColor: Int = 0xFFFFFFFF.toInt(),
    val bubbleTextColor: Int = 0xFF000000.toInt(),
    val imageUpdates: Map<Int, String?> = mapOf(),
    val backgroundImage: String? = null
)

