package com.example.emotichat

/** Full chat profile passed between activities as JSON. */
data class ChatProfile(
    val id: String,
    val title: String,
    val description: String,
    val tags: List<String>,
    val mode: ChatMode,
    val backgroundUri: String?,    // URI string or resource identifier
    val sfwOnly: Boolean,
    val characterIds: List<String>,
    val rating: Float,
    val timestamp: Long,
    val author: String = ""
)

/** Supported game modes for chats. */
enum class ChatMode {
    SANDBOX,
    RPG,
    SLOW_BURN,
    GOD,
    VISUAL_NOVEL,
    CHARACTER
}
