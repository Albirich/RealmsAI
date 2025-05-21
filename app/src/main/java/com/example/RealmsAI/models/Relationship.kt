package com.example.RealmsAI.models

data class Relationship(
    val fromId: String,      // Who has the relationship
    val toId: String,        // Who the relationship is with
    val type: String,        // E.g. "rival", "mentor", "friend"
    val description: String = ""
)

data class ParticipantPreview(
    val id: String,
    val name: String,
    val avatarUri: String = ""
)

val RELATIONSHIP_TYPES = listOf(
    "Friend",
    "Rival",
    "Sibling",
    "Mentor",
    "Protege",
    "Parent",
    "Child",
    "Romantic",
    "Teammate",
    "Ally",
    "Enemy"
)