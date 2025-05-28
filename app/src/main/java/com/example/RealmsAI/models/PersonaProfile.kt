package com.example.RealmsAI.models

data class PersonaProfile(
    val id: String = "",
    val name: String = "",
    val gender: String = "",
    var height: String = "",
    var weight: String = "",
    var age: Int = 0,
    var hair: String = "",
    val eyes: String = "",
    val physicaldescription: String = "",
    val relationships: List<Relationship> = emptyList(),
    val images: List<String> = emptyList(),
    val author: String = "",
    val avatarUri: String = "",
    val profileType: String = "player"
)
