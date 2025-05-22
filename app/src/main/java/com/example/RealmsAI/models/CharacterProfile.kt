package com.example.RealmsAI.models

import com.google.gson.annotations.SerializedName
import com.google.firebase.Timestamp

data class CharacterProfile(
    val id                 : String              = "",
    val name               : String              = "",
    val personality        : String              = "",
    val privateDescription : String              = "",
    val author             : String              = "",
    val tags               : List<String>        = emptyList(),
    val emotionTags        : Map<String, String> = emptyMap(),
    val avatarResId        : Int?                = null,
    @SerializedName("avatarUri")
    val avatarUri          : String?             = null,
    val background         : String              = "",
    val summary            : String?             = null,
    val outfits            : List<Outfit>        = emptyList(),
    val currentOutfit      : String              = "",
    val createdAt          : Timestamp?          = null,
    val age                : Double? = null,
    val weight             : Double? = null,
    val eyeColor           : String              = "",
    val hairColor          : String              = "",
    val profileType        : String = "bot"
){
    val weightStr: String
        get() = weight?.toString() ?: "Unknown"
    val ageStr: String
        get() = age?.toString() ?: "Unknown"

}
