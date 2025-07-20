package com.example.RealmsAI.models


data class InviteProfile (
    val sessionId: String = "",
    val title: String = "",
    val userList: List<String> = emptyList(),
    val characterIds: List<String> = emptyList(),
    val relationships: List<Relationship> = emptyList(),
    val chatId: String = "",
    val sessionSummary: String = "",
    val chatMode: String = "SANDBOX",
    val areas: List<Area> = emptyList(),
    val sfwOnly: Boolean = true,
    val isBuilding: Boolean = false,
    val started: Boolean = false,
    val sessionDescription: String = "",
    val secretDescription: String? = null,
    val multiplayer: Boolean? = true
)

