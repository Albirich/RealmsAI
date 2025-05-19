package com.example.RealmsAI.models

import com.google.firebase.Timestamp

data class ChatMessage(
    val id: String = "",
    val sender: String = "",
    val messageText: String = "",
    val timestamp: Timestamp? = null
)