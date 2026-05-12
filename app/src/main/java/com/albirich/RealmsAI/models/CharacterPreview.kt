package com.albirich.RealmsAI.models

data class CharacterPreview(
    val id: String,
    val originalId: String,
    val name: String,
    val summary: String,
    val avatarUri: String?,
    val avatarResId: Int,
    val author: String,
    val rawJson: String,
    val rating: Double = 0.0
)