package com.example.RealmsAI.models

data class Relationship(
    var fromId: String,      // Who has the relationship
    var toName: String,        // Who the relationship is with
    var type: String,        // E.g. "rival", "mentor", "friend"
    var description: String = ""
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