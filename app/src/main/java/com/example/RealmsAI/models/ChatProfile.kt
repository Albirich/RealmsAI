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
    val characterToArea: Map<String, String> = emptyMap(), // NEW!
    val mode: ChatMode = ChatMode.SANDBOX,
    val relationships: List<Relationship> = emptyList(),
    val rating: Float = 0f,
    @ServerTimestamp
    val timestamp: Timestamp? = null,
    val author: String = "",
    val tags: List<String> = emptyList(),
    val areas: List<Area> = emptyList(),
    val sfwOnly: Boolean = true
) {
    constructor() : this(
        id         = "",
        title      = "",
        description= "",
        secretDescription = "",
        characterIds = emptyList(),
        characterToArea = emptyMap(),
        mode       = ChatMode.SANDBOX,
        rating     = 0f,
        timestamp  = null,
        author     = "",
        tags       = emptyList(),
        sfwOnly    = true
    )
}
