package com.example.RealmsAI.models

data class RelationshipLevel(
    var level: Int = 0,
    var threshold: Int = 0,
    var personality: String = ""
)

data class Relationship(
    val id: String = System.currentTimeMillis().toString(), // or UUID.randomUUID().toString()
    val fromId: String = "",
    val toName: String = "",
    val type: String = "",
    val description: String? = null
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