package com.example.RealmsAI

data class ChatMessage(
    val sender: String = "",
    var messageText: String = "",
    val timeStamp: Long = System.currentTimeMillis()
)
