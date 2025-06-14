package com.example.RealmsAI.models

data class PlayerSlot(
    val slotId: String,     // e.g. "player1"
    val name: String,       // e.g. "Player 1" or player username
    val personaId: String? = null, // id of the selected persona, if any
    val avatarUri: String? = null  // selected persona avatar, if any
)
