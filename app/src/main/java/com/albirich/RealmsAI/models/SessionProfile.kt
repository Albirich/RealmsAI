package com.albirich.RealmsAI.models

import com.albirich.RealmsAI.models.ModeSettings.VNRelationship
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
    val sessionTitle: String = "",
    val sessionDescription: String = "",
    val chatMode: String = "SANDBOX",
    val startedAt: Timestamp? = null,
    val sfwOnly: Boolean = true,
    val sessionSummary: String = "",
    var relationships: List<Relationship> = emptyList(),
    var events: List<ScenarioEvent> = emptyList(),
    var currentEvent: String = "",
    val secretDescription: String? = null,
    val initialGreeting: String? = null,
    var aiModel: String = "Grok 4.1",
    var userMap: Map<String, SessionUser> = emptyMap(),
    val userAssignments: Map<String, String> = emptyMap(),
    val userList: List<String> = emptyList(),
    val characterIds: List<String> = emptyList(),
    var slotRoster: List<SlotProfile> = emptyList(),
    val areas: List<Area> = emptyList(),
    val history: List<ChatMessage> = emptyList(),
    val globalInstructions: List<Instruction> = emptyList(),
    val started: Boolean = false,
    val isBuilding: Boolean = false,
    val currentAreaId: String? = null,
    @SerializedName("multiplayer")
    val multiplayer: Boolean? = false,
    val acts: List<RPGAct> = emptyList(),
    val currentAct: Int = 0,
    var enabledModes: MutableList<String> = mutableListOf(),
    var modeSettings: MutableMap<String, Any> = mutableMapOf(),
    val globalLorebookIds: List<String> = emptyList(),
    val pinnedMessages: MutableList<String> = mutableListOf(),
    var silent: Boolean = false
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
    val backstory: String = "",
    val lorebookIds: List<String> = emptyList(),
    var memoryCounter: Int = 0,
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
    val exampleDialogue: List<DialogueExample> = emptyList(),
    // -- CHAT/AI/SESSION --
    var bubbleColor: String = "#FFFFFF",
    var textColor: String = "#000000",
    val sfwOnly: Boolean = true,
    val profileType: String = "bot",
    var statusEffects: MutableList<String> = mutableListOf(),
    val lastActiveArea: String? = "null",
    val lastActiveLocation: String? = "null",
    var lastSynced: com.google.firebase.Timestamp? = null,
    val pose: String? = null,
    val typing: Boolean = false,
    var moreInfo: String? = null,
    val instructions: List<Instruction> = emptyList(),
    val userReplaced: Boolean = false,
    // -- RPG STUFF --
    var hiddenRoles: String? = "",
    val rpgClass: String = "",
    val stats: Map<String, Int> = emptyMap(),
    val equipment: List<String> = emptyList(),
    var hp: Int = 0,
    val maxHp: Int = 0,
    val defense: Int = 0,
    var linkedTo: List<CharacterLink> = emptyList(),
    var vnRelationships: MutableMap<String, VNRelationship> = mutableMapOf(),
    val tags: List<String>        = emptyList(),
    val universe: String              = "",
    var activityStatus: Boolean = true,
    var modelId: String? = null,
    var temperature: Float? = null,
    var topK: Int? = null,
    var topP: Float? = null,
    val pinnedMessages: MutableList<String> = mutableListOf(),
    val creatorNotes: String? = null
)
data class TaggedMemory(
    var id: String = "",
    var slotId: String = "",
    var tags: List<String> = emptyList(),
    var text: String = "",
    var nsfw: Boolean = false,
    var messageIds: List<String> = emptyList(),
    val embedding: List<Double> = emptyList()
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

