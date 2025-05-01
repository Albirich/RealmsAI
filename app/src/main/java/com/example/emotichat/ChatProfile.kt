package com.example.emotichat

data class ChatProfile(
    val id: String,
    val title: String,
    val description: String,
    val tags: List<String>,
    val mode: ChatMode,
    val backgroundUri: String?,     // e.g. "android.resource://…"/custom URI
    val sfwOnly: Boolean,
    val characterIds: List<String>, // IDs of the characters in this chat
    val rating: Float,
    val timestamp: Long,
    val author: String
)

enum class ChatMode {
    SANDBOX,
    RPG,
    SLOW_BURN,
    GOD,
    VISUAL_NOVEL
}
