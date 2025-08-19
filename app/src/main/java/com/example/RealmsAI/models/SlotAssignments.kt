package com.example.RealmsAI.models

data class SlotAssignmentMaps(
    val nameAssignments: Map<String, String>,        // "character1" -> "Lira"
    val idAssignments: Map<String, String>,          // "character1" -> "<profileId>"
    val avatarByKey: Map<String, String?>            // optional convenience
)

fun buildSlotAssignmentMaps(
    selectedCharacters: List<CharacterProfile>
): SlotAssignmentMaps {
    val names = mutableMapOf<String, String>()
    val ids   = mutableMapOf<String, String>()
    val avatars = mutableMapOf<String, String?>()

    selectedCharacters.forEachIndexed { index, p ->
        val key = ModeSettings.SlotKeys.fromPosition(index)
        names[key] = p.name
        ids[key]   = p.id
        avatars[key] = p.avatarUri
    }
    return SlotAssignmentMaps(names, ids, avatars)
}
