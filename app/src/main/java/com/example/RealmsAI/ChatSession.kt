package com.example.RealmsAI

import android.app.Notification.MessagingStyle.Message

data class ChatSession(
    val sessionId: String,
    val userId: String,
    val chatId: String,
    val messages: List<Message>,
    val createdAt: Long,
    val lastOpened: Long
)
