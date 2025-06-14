package com.example.RealmsAI.models

import com.google.gson.annotations.SerializedName
import com.google.firebase.Timestamp

data class CharacterProfile(
    val id                 : String              = "",
    val name               : String              = "",
    val personality        : String              = "",
    val privateDescription : String              = "",
    val backstory          : String              = "",
    val greeting           : String              = "",
    val author             : String              = "",
    val tags               : List<String>        = emptyList(),
    val emotionTags        : Map<String, String> = emptyMap(),
    val avatarResId        : Int?                = null,
    @SerializedName("avatarUri")
    val avatarUri          : String?             = null,
    var areas: List<Area> = emptyList(),
    val summary            : String?             = null,
    val outfits            : List<Outfit>        = emptyList(),
    val currentOutfit      : String              = "",
    val createdAt          : Timestamp?          = null,
    var height             : String              = "",
    var weight             : String              = "",
    var age                : Int                 =0,
    val eyeColor           : String              = "",
    val hairColor          : String              = "",
    val physicalDescription: String              = "",
    val gender             : String              = "",
    val relationships: List<Relationship> = emptyList(),
    val bubbleColor: String = "#FFFFFF",
    val textColor: String = "#000000",
    val sfwOnly: Boolean = true,
    val profileType        : String = "bot"
)

fun characterProfileToPersona(character: CharacterProfile): PersonaProfile {
    return PersonaProfile(
        id = character.id,
        name = character.name,
        gender = character.gender,
        height = character.height,
        weight = character.weight,
        age = character.age,
        hair = character.hairColor,
        eyes = character.eyeColor,
        physicaldescription = character.physicalDescription,
        relationships = character.relationships,
        outfits = character.outfits,
        currentOutfit = character.currentOutfit,
        author = character.author,
        avatarUri = character.avatarUri ?: "",
        bubbleColor = character.bubbleColor,
        textColor = character.textColor,
        profileType = "bot"
    )
}