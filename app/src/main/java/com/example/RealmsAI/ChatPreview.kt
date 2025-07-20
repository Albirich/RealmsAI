package com.example.RealmsAI

import com.example.RealmsAI.models.ChatMode
import com.example.RealmsAI.models.ChatProfile
import com.google.firebase.Timestamp

data class ChatPreview(
    val id: String,
    val title: String,
    val description: String,
    val avatar1Uri: String?,
    val avatar1ResId: Int,
    val avatar2Uri: String?,
    val avatar2ResId: Int,
    val rating: Float = 0f,
    val timestamp: Timestamp? = null,
    val author: String = "",
    val tags: List<String> = emptyList(),
    val sfwOnly: Boolean = true,
    val chatProfile: ChatProfile? = null,
    val rawJson: String
)
