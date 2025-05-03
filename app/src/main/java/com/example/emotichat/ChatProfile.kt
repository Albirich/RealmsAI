package com.example.emotichat


data class ChatProfile(
    val id: String,
    val title: String,
    val description: String,
    val tags: List<String>,
    val mode: ChatMode,
    val backgroundUri: String? = null,
    val backgroundResId: Int?  = null,
    val sfwOnly: Boolean,
    val characterIds: List<String>,
    val rating: Float,
    val timestamp: Long,
    val author: String,
    val createdAt: Long = System.currentTimeMillis()
)

