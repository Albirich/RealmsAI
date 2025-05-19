package com.example.RealmsAI

data class PersonaPreview(
    val id: String,
    val name: String,
    val description: String,
    val avatarUri: String? = null,
    val avatarResId: Int = 0,
    val author: String = ""
)
