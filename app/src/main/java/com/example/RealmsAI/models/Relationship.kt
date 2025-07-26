package com.example.RealmsAI.models

data class RelationshipLevel(
    val level: Int,
    val threshold: Int,
    val personality: String = ""
)

data class Relationship(
    val fromId: String = "",
    val toName: String = "",
    val type: String = "",
    val description: String? = null,
    // ... other fields ...
    var upTriggers: String? = "",
    var downTriggers: String? = "",
    var levels: List<RelationshipLevel> = emptyList(),
    var currentLevel: Int = 0   // Or you can set this elsewhere!
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
    "Lover",
    "Teammate",
    "Ally",
    "Enemy"
)