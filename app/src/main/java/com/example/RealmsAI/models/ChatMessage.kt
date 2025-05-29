package com.example.RealmsAI.models

import com.google.firebase.Timestamp

data class ChatMessage(
    val id: String = "",
    val sender: String = "",
    val messageText: String = "",
    val timestamp: Timestamp? = null,
    val delay: Long = 0L,
    val bubbleBackgroundColor: Int = 0xFFFFFFFF.toInt(),
    val bubbleTextColor: Int = 0xFF000000.toInt(),
    val imageUpdates: Map<String, String?> = emptyMap(),
    val backgroundImage: String? = null
)


