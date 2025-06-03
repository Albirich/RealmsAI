package com.example.RealmsAI.models

data class Outfit(
    var name: String = "",
    var poseSlots: MutableList<PoseSlot> = mutableListOf()
)

// models/PoseSlot.kt
data class PoseSlot(
    var name: String = "",
    var uri: String? = null
)

