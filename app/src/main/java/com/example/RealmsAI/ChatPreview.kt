package com.example.RealmsAI

import com.example.RealmsAI.models.ChatMode
import com.example.RealmsAI.models.ChatProfile
import com.google.firebase.Timestamp

data class ChatPreview(
    val id: String,
    val title: String,
    val description: String,
    val avatar1ResId: Int,
    val avatar2ResId: Int,
    val avatar1Uri: String? = null,
    val avatar2Uri: String? = null,
    val rating: Float = 0f,
    val timestamp: Timestamp? = null,
    val mode: ChatMode,
    val author: String = "",
    val tags: List<String> = emptyList(),      // ← Add this
    val sfwOnly: Boolean = true,
    val chatProfile: ChatProfile? = null,
    val rawJson: String
)
