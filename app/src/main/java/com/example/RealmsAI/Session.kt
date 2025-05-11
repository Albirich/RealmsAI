package com.example.RealmsAI.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class Message(
    val id: String = "",           // Firestore doc ID
    val sender: String = "",       // e.g. "You", "B1", "N0"
    val text: String = "",
    @ServerTimestamp
    val createdAt: Timestamp? = null
)

data class Session(
    val sessionId: String = "",    // Firestore doc ID
    val chatId: String = "",       // which chat blueprint this belongs to
    @ServerTimestamp
    val startedAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
)
