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
    val outfits: List<Outfit> = emptyList(),
    val currentOutfit: String = "",
    val author: String = "",
    val avatarUri: String = "",
    val bubbleColor: String = "#FFFFFF",
    val textColor: String = "#000000",
    val profileType: String = "player"
)
