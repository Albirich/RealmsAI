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
    val abilities           : String              = "",
    val gender             : String              = "",
    val relationships: List<Relationship> = emptyList(),
    val bubbleColor: String = "#FFFFFF",
    val textColor: String = "#000000",
    val sfwOnly: Boolean = true,
    val private: Boolean = false,
    val linkedToMap: Map<String, List<CharacterLink>> = emptyMap(),
    val profileType        : String = "bot"
)

data class CharacterLink(
    val targetId: String,   // the linked character
    val type: String,       // e.g. "transformation", "sidekickTo", etc.
    val trigger: String,    // e.g. "when he plays games"
    val notes: String = ""  // optional notes
)