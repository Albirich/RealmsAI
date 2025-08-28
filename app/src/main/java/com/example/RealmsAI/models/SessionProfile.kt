package com.example.RealmsAI.models

import com.example.RealmsAI.models.ModeSettings.VNRelationship
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.gson.annotations.SerializedName

// ----------------------
// MAIN SESSION PROFILE
// ----------------------
data class SessionProfile(
    val sessionId: String = "",
    val chatId: String = "",
    val title: String = "",
    val sessionDescription: String = "",
    val chatMode: String = "SANDBOX",
    val startedAt: Timestamp? = null,
    val sfwOnly: Boolean = true,
    val sessionSummary: String = "",
    var relationships: List<Relationship> = emptyList(),
    val secretDescription: String? = null,

    var userMap: Map<String, SessionUser> = emptyMap(),
    val userAssignments: Map<String, String> = emptyMap(),
    val userList: List<String> = emptyList(),

    var slotRoster: List<SlotProfile> = emptyList(),

    val areas: List<Area> = emptyList(),

    val history: List<ChatMessage> = emptyList(),

    val started: Boolean = false,
    val isBuilding: Boolean = false,
    val currentAreaId: String? = null,
    @SerializedName("multiplayer")
    val multiplayer: Boolean? = false,

    val acts: List<RPGAct> = emptyList(),
    val currentAct: Int = 0,
    var enabledModes: MutableList<String> = mutableListOf(),
    var modeSettings: MutableMap<String, Any> = mutableMapOf()

)

// ----------------------
// SESSION USER
// ----------------------
data class SessionUser(
    val userId: String = "",
    val username: String = "",
    val personaIds: List<String> = emptyList(),
    val activeSlotId: String? = null,
    val bubbleColor: String = "#CCCCCC",
    val textColor: String = "#000000",
    // Extend with avatarUri, isHost, etc. as needed
)

// ----------------------
// SLOT PROFILE
// (Session-specific character/persona profile)
// ----------------------
data class SlotProfile(
    val slotId: String = "",
    val isPlaceholder: Boolean = false,
    val baseCharacterId: String? = null,
    val name: String = "",
    val summary: String = "",
    val personality: String = "",
    val memories: List<TaggedMemory> = emptyList(),
    val privateDescription: String = "",
    val abilities: String = "",
    val greeting: String = "",
    // -- PHYSICAL/IDENTITY --
    val avatarUri: String? = null,
    val outfits: List<Outfit> = emptyList(),
    val currentOutfit: String = "",
    val height: String = "",
    val weight: String = "",
    val age: Int = 0,
    val eyeColor: String = "",
    val hairColor: String = "",
    val gender: String = "",
    val physicalDescription: String = "",
    val relationships: List<Relationship> = emptyList(),
    // -- CHAT/AI/SESSION --
    val bubbleColor: String = "#FFFFFF",
    val textColor: String = "#000000",
    val sfwOnly: Boolean = true,
    val profileType: String = "bot",
    var statusEffects: MutableList<String> = mutableListOf(),
    val lastActiveArea: String? = null,
    val lastActiveLocation: String? = null,
    var lastSynced: com.google.firebase.Timestamp? = null,
    val pose: String? = null,
    val typing: Boolean = false,
    var moreInfo: String? = null,
    // -- RPG STUFF --
    var hiddenRoles: String? = "",
    val rpgClass: String = "",
    val stats: Map<String, Int> = emptyMap(),
    val equipment: List<String> = emptyList(),
    var hp: Int = 0,
    val maxHp: Int = 0,
    val defense: Int = 0,
    @get:Exclude var linkedTo: List<CharacterLink> = emptyList(),
    // -- VN STUFF --
    var vnRelationships: MutableMap<String, VNRelationship> = mutableMapOf()
)
data class TaggedMemory(
    var id: String = "",
    var tags: List<String> = emptyList(),
    var text: String = "",
    var nsfw: Boolean = false,
    var messageIds: List<String> = emptyList()
)
data class AvatarMapEntry(
    var slotId: String? = null,
    var poseName: String? = null
)

data class RPGAct(
    val actNumber: Int = 0,
    val summary: String = "",
    val goal: String = "",
    val areaId: String = ""
)

