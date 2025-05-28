package com.example.RealmsAI.models

import com.google.firebase.Timestamp

data class SessionProfile(
    val sessionId: String = "",
    val chatId: String = "",
    val title: String = "",
    val sessionDescription: String = "",
    val backgroundUri: String? = null,
    val chatMode: String = "SANDBOX", // or enum, but use String to match JSON
    val startedAt: String? = null,
    val sfwOnly: Boolean = true,
    val participants: List<String> = emptyList(),
    val relationships: List<Relationship> = emptyList(),

    val slotRoster: List<SlotInfo> = emptyList(),
    val personaProfiles: List<PersonaProfile> = emptyList()
)

data class SlotInfo(
    val name: String = "",
    val slot: String = "",          // match JSON "slot" not "id"
    val summary: String = "",       // Add if JSON has it
    val id: String = "",
    val outfits: List<String> = emptyList(), // Add if JSON has it
    val poses: Map<String, List<String>> = emptyMap(),
    val relationships: List<Relationship> = emptyList()
)

