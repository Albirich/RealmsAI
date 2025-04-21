package com.example.emotichat

data class ChatMessage(
    val sender: String,
    var messageText: String,
    val timeStamp: Long = System.currentTimeMillis()
)
