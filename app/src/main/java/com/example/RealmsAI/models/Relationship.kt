package com.example.RealmsAI.models

data class Relationship(
    var fromId: String = "",
    var toName: String = "",
    var type: String = "",
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