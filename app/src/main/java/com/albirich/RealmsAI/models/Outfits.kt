package com.albirich.RealmsAI.models

import java.util.UUID

data class Outfit(
    var id: String = UUID.randomUUID().toString(),
    var parentId: String? = null,
    var name: String = "",
    var poseSlots: MutableList<PoseSlot> = mutableListOf(),
    var isNSFW: Boolean = false,
    var description: String = "",
    var heightOverride: String? = null,
    var weightOverride: String? = null,
    var eyeColorOverride: String? = null,
    var hairColorOverride: String? = null,
    var physicalDescOverride: String? = null,

    @Transient var variants: MutableList<Outfit> = mutableListOf()
)

// models/PoseSlot.kt
data class PoseSlot(
    var id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var uri: String? = null,
    var nsfw: Boolean = false,
    var description: String = "",
    var vector: List<Double>? = null
)

