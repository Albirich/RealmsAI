package com.example.RealmsAI.models


data class SessionProfile(
    val sessionId: String = "",
    val chatId: String = "",
    val title: String = "",
    val sessionDescription: String = "",
    val backgroundUri: String? = null,
    val chatMode: String = "SANDBOX",
    val startedAt: String? = null,
    val sfwOnly: Boolean = true,
    val participants: List<String> = emptyList(), // ["player", "player2", "B1", "B2", ...]
    val playerAssignments: Map<String, String> = emptyMap(), // slot -> personaId (e.g., "player" -> personaId1)
    val userAssignments: Map<String, String> = emptyMap(),   // slot -> userId (future multiplayer)
    val relationships: List<Relationship> = emptyList(),
    val slotRoster: List<SlotInfo> = emptyList(),
    val areas: List<Area> = emptyList(),
    val userList: List<String> = emptyList(),
    val personaProfiles: List<PersonaProfile> = emptyList()
)


data class SlotInfo(
    val name: String = "",
    val slot: String = "",
    val summary: String = "",
    val id: String = "",
    val outfits: List<String> = emptyList(),
    val poses: Map<String, String> = emptyMap(),
    val currentOutfit: String = "",
    val sfwOnly: Boolean,
    val relationships: List<Relationship> = emptyList(),
    val bubbleColor: String = "#CCCCCC",
    val textColor: String = "#000000",
    val personality: String = "",
    val backstory: String = "",
    val privateDescription: String = ""

)

