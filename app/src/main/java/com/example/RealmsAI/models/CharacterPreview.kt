package com.example.RealmsAI.models

data class CharacterPreview(
    val id: String,
    val name: String,
    val summary: String,
    val avatarUri: String?,
    val avatarResId: Int,
    val author: String,
    val rawJson: String
)