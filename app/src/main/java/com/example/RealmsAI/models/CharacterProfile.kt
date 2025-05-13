package com.example.RealmsAI.models

import com.google.gson.annotations.SerializedName

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
    val additionalInfo     : String              = "",
    val summary            : String?             = null,
    val createdAt          : Long                = 0L
)


