package com.example.RealmsAI.models

data class PlayerSlot(
    val slotId: String,
    val name: String,
    val personaId: String? = null,
    val avatarUri: String? = null,
    val isHost: Boolean = false
)
