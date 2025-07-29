package com.example.RealmsAI.models

import com.google.firebase.Timestamp

data class ChatMessage(
    var id: String = "",
    var senderId: String = "",
    var text: String = "",
    val pose: Map<String, String>? = null,
    var delay: Int = 0,
    val timestamp: Timestamp? = null,
    var imageUpdates: Map<Int, String?>? = null,
    val area: String? = null,
    val location: String? = null,
    var visibility: Boolean = true,
    val messageType: String = "message"
)





