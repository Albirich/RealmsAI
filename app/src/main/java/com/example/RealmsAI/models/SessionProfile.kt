package com.example.RealmsAI.models

import com.google.firebase.Timestamp

data class SessionProfile(
    val sessionId: String = "",
    val chatId: String = "",
    val title: String = "",
    val description: String = "",
    val backgroundUri: String? = null,
    val chatMode: ChatMode = ChatMode.SANDBOX,
    val startedAt: Timestamp? = null,
    val sfwOnly: Boolean = true,
    val participants: List<String> = emptyList(), // user/player IDs

    // --- Slot roster: mapping from slot (B1, P1, etc) to character/player data ---
    val slotRoster: Map<String, SlotInfo> = emptyMap(),

    // --- Recent history, summary, and tagged memories for AI prompts ---
    val recentSummary: String = "", // Facilitator-generated session summary
    val lastMessages: List<MessageSummary> = emptyList(), // Last few message summaries for context
    val taggedMemories: List<TaggedMemory> = emptyList(), // Important facts, relationships, etc.

    // --- Relationships, persona info (per character) ---
    val personaProfiles: Map<String, PersonaProfile> = emptyMap(), // slot -> persona

    // --- Extra session-level tags (for search/facilitator) ---
    val tags: List<String> = emptyList()
)

data class SlotInfo(
    val name: String = "",
    val id: String = "",
    val avatarUri: String? = null,
    val poses: List<String> = emptyList(), // Only the poses for the chosen outfit
    val outfit: String = "" // current outfit
)

data class MessageSummary(
    val senderSlot: String = "", // e.g., "B1", "P1"
    val text: String = "",
    val pose: String = "",       // If available
    val timestamp: Timestamp? = null
)

data class TaggedMemory(
    val label: String = "",     // e.g., "Naruto|Sasuke|Chunin Exams"
    val summary: String = "",   // Short fact/description
    val relatedSlots: List<String> = emptyList() // e.g., ["B1", "B2"]
)
