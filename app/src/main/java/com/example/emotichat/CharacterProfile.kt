package com.example.emotichat

import com.google.gson.annotations.SerializedName

data class CharacterProfile(
    val id                 : String,
    val name               : String,
    val personality        : String,
    val privateDescription : String,
    val author             : String = "",
    val tags               : List<String>,
    val emotionTags        : Map<String, String>,
    val avatarResId        : Int,
    val additionalInfo     : String,
    val summary            : String? = null,
    val createdAt: Long = System.currentTimeMillis(),

    @SerializedName("avatarUri")
    val avatarUri          : String? = null   // ← add this back in
)
