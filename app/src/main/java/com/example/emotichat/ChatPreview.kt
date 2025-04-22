package com.example.emotichat

import java.sql.Timestamp

data class ChatPreview(
    val id: String,
    val title: String,
    val description: String,
    val avatar1ResId: Int,
    val avatar2ResId: Int,
    val rating: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)
