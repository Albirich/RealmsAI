package com.example.RealmsAI.models

data class ChatMessage(
    val sender: String = "",
    var messageText: String = "",
    val timeStamp: Long = System.currentTimeMillis()
)
