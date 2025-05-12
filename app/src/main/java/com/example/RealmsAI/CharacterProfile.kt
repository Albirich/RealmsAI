package com.example.RealmsAI

import com.google.gson.annotations.SerializedName

data class CharacterProfile(
    val id                 : String,
    val name               : String,

    // these two are now required
    val personality        : String = "",
    val privateDescription : String = "",

    // metadata
    val author             : String              = "",
    val tags               : List<String>        = emptyList(),
    val emotionTags        : Map<String, String> = emptyMap(),

    // avatar fields; you’ll set one or the other
    val avatarResId        : Int?                = null,
    @SerializedName("avatarUri")
    val avatarUri          : String?             = null,

    val background         : String              = "",

    // any extra info you want to add later
    val additionalInfo     : String              = "",

    // optional summary
    val summary            : String?             = null,

    // timestamp
    val createdAt          : Long                = System.currentTimeMillis()
)

