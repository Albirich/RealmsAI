package com.albirich.RealmsAI.models

import com.google.firebase.Timestamp
import java.util.UUID

data class LocationSlot(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var uri: String? = null,
    var description: String = "",
    var nsfw: Boolean = false
)

data class Area(
    var id: String = UUID.randomUUID().toString(),
    var creatorId: String = "",
    var originalId: String? = null,
    var name: String = "",
    var locations: MutableList<LocationSlot> = mutableListOf(),
    var description: String = "",
    var publicInfo: String = "",
    var private: Boolean = false,
    var timestamp: Timestamp? = null,
    var announced: Boolean = false,
    var nsfw: Boolean = false
)

// Add this so the Creation Hub has something lightweight to display!
data class AreaPreview(
    val id: String = "",
    val name: String = "",
    val publicInfo: String = "",
    val coverImageUri: String? = null,
    val locationCount: Int = 0,
    val nsfw: Boolean = false,
    val rawJson: String = ""
)