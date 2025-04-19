package com.example.emotichat

data class CharacterProfile(
    val characterId: String,
    val name: String,
    val description: String, // Public description
    val privateDescription: String, // Private description (for the creator)
    val author: String, // Who created the character
    val tags: List<String>, // Public tags
    val emotionTags: Map<String, String>, // Key-Value pairs (e.g., "happy" -> "excited")
    val avatarResId: Int, // Avatar image resource
    val additionalInfo: String, // Age, height, etc. displayed above the description
)
*