package com.example.RealmsAI.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class DirectMessage(
    val id: String = "",
    val from: String = "",
    val to: String = "",
    val text: String = "",
    @ServerTimestamp
    val timestamp: Timestamp? = null,
    var status: MessageStatus = MessageStatus.UNOPENED,
    val type: MessageType = MessageType.DIRECT,
    val sessionId: String? = null,
    val inviteProfileJson: String? = null
)

enum class MessageStatus {
    OPENED,
    UNOPENED
}

enum class MessageType {
    DIRECT,
    FRIEND_REQUEST,
    SESSION_INVITE
}