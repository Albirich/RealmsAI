package com.example.RealmsAI.models


import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
@IgnoreExtraProperties
data class ChatProfile(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val secretDescription: String = "",
    val firstmessage: String = "",
    val characterIds: List<String> = emptyList(),
    val characterToArea: Map<String, String> = emptyMap(),
    val characterToLocation: Map<String, String> = emptyMap(),
    val mode: String = "SANDBOX",
    val enabledModes: List<String> = emptyList(),
    var modeSettings: MutableMap<String, String> = mutableMapOf(),
    val relationships: List<Relationship> = emptyList(),
    val rating: Float = 0f,
    @ServerTimestamp
    val timestamp: Timestamp? = null,
    val author: String = "",
    val tags: List<String> = emptyList(),
    val universe: String = "",
    val areas: List<Area> = emptyList(),
    val sfwOnly: Boolean = true,
    val private: Boolean = false,
    val ratingCount: Int = 0,
    val ratingSum: Double = 0.0

) {
    constructor() : this(
        id         = "",
        title      = "",
        description= "",
        secretDescription = "",
        characterIds = emptyList(),
        characterToArea = emptyMap(),
        mode       = "SANDBOX",
        rating     = 0f,
        timestamp  = null,
        author     = "",
        tags       = emptyList(),
        sfwOnly    = true
    )
}
