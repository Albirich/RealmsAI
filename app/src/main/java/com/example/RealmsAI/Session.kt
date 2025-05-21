package com.example.RealmsAI.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class Message(
    val id: String = "",
    val sender: String = "",
    val text: String = "",
    val pose: String = "",         // e.g., "happy", "sad", "fighting", "flashback"
    val timing: Int = 0,           // e.g., 0 = normal, 1 = interrupt, 2 = slow, etc.
    @ServerTimestamp
    val createdAt: Timestamp? = null
)


data class Session(
    val sessionId: String = "",
    val chatId: String = "",
    val participants: List<String> = listOf(), // NEW: all users in this session
    val characterId: String = "",              // (Optional: for singleplayer RP)
    @ServerTimestamp
    val startedAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
)

