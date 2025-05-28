package com.example.RealmsAI.models


import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class ChatProfile(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val firstmessage: String = "",
    val characterIds: List<String> = emptyList(),
    val backgroundUri: String? = null,
    val backgroundResId: Int? = null,
    val mode: ChatMode = ChatMode.SANDBOX,
    val relationships: List<Relationship> = emptyList(),
    val rating: Float = 0f,
    @ServerTimestamp
    val timestamp: Timestamp? = null,
    val author: String = "",
    val tags: List<String> = emptyList(),
    val sfwOnly: Boolean = true
) {
    // This secondary constructor gives Firestore the no-arg it needs:
    constructor() : this(
        id         = "",
        title      = "",
        description= "",
        characterIds = emptyList(),
        backgroundUri = null,
        backgroundResId = null,
        mode       = ChatMode.SANDBOX,
        rating     = 0f,
        timestamp  = null,
        author     = "",
        tags       = emptyList(),
        sfwOnly    = true
    )
}
