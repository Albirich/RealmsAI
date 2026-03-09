package com.example.RealmsAI.models

import com.google.gson.annotations.SerializedName
import com.google.firebase.Timestamp

data class CharacterProfile(
    val id                 : String              = "",
    val originalId         : String              = "",
    val name               : String              = "",
    val personality        : String              = "",
    val privateDescription : String              = "",
    val backstory          : String              = "",
    val soloScenario       : String              = "",
    val greeting           : String              = "",
    val author             : String              = "",
    val tags               : List<String>        = emptyList(),
    val universe           : String              = "",
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
    val lastTimestamp      : Timestamp?          = null,
    val eyeColor           : String              = "",
    val hairColor          : String              = "",
    val physicalDescription: String              = "",
    val abilities          : String              = "",
    val gender             : String              = "",
    val relationships      : List<Relationship>  = emptyList(),
    val bubbleColor        : String              = "#FFFFFF",
    val textColor          : String              = "#000000",
    val sfwOnly            : Boolean             = true,
    val private            : Boolean             = false,
    val linkedToMap        : Map<String, List<CharacterLink>> = emptyMap(),
    val profileType        : String              = "bot",
    val ratingCount        : Int                 = 0,
    val ratingSum          : Double              = 0.0,
    val exampleDialogue    : List<DialogueExample> = emptyList()
)

data class CharacterLink(
    var targetId: String,
    var type: String,
    var trigger: String,
    var notes: String = ""
)