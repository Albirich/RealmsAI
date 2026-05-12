package com.albirich.RealmsAI.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp
import java.util.UUID

data class FeedEvent(
    val id: String = UUID.randomUUID().toString(),
    val authorId: String = "",       // Who posted this? (Or "ADMIN" if it's you)
    val type: FeedEventType = FeedEventType.TEXT_POST,
    val title: String = "",          // e.g., "New Character Published!" or "Server Maintenance"
    val content: String = "",        // e.g., "Check out my new Cyberpunk setting."
    val referenceId: String? = null, // The ID of the Character/Area/Lorebook so they can click it!
    @ServerTimestamp
    val timestamp: Timestamp? = null
)

enum class FeedEventType {
    ADMIN_ANNOUNCEMENT,
    NEW_CHARACTER,
    NEW_CHAT,
    NEW_AREA,
    NEW_LOREBOOK,
    TEXT_POST // Just in case you want users to be able to just post status updates later!
}