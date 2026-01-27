package com.example.RealmsAI.models

import android.net.Uri
import java.util.UUID

data class LocationSlot(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var uri: String? = null,
    var characters: MutableList<String> = mutableListOf(),
    var description: String = ""
)

data class Area(
    var id: String = UUID.randomUUID().toString(),
    var creatorId: String = "",  // <- Add this!
    var name: String = "",
    var locations: MutableList<LocationSlot> = mutableListOf(),
)
