package com.example.RealmsAI.models

data class PersonaProfile(
    val id: String = "",
    val name: String = "",
    val age: String = "",
    val gender: String = "",
    val height: String = "",
    val hair: String = "",
    val eyes: String = "",
    val description: String = "",
    val images: List<String> = emptyList(),
    val author: String = "",
    val avatarUri: String = ""
)
