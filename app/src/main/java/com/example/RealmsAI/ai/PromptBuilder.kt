package com.example.RealmsAI.ai


import android.util.Log
import com.example.RealmsAI.models.CharacterProfile
import com.example.RealmsAI.models.ChatMessage
import com.example.RealmsAI.models.SlotInfo
import com.example.RealmsAI.models.Area
import com.example.RealmsAI.models.PersonaProfile
import com.example.RealmsAI.models.SessionProfile
import com.example.RealmsAI.models.characterProfileToPersona
import kotlin.collections.component1
import kotlin.collections.component2


object PromptBuilder {

    // ActivationAI

    fun buildActivationPrompt(
        sessionProfile: SessionProfile,
        chatHistory: String
    ): String {
        val characterBlock = sessionProfile.slotRoster.joinToString("\n") { slot ->
            """- ${slot.name}:
    SFW_only: ${slot.sfwOnly}
    Personality: ${slot.personality}
    Outfits: [${slot.outfits.joinToString()}]"""
        }

        // Group relationships by character, for easy reading
        val relationshipsBlock = sessionProfile.relationships
            .groupBy { it.fromId }
            .map { (fromId, rels) ->
                val fromName = sessionProfile.slotRoster.find { it.id == fromId }?.name
                    ?: sessionProfile.personaProfiles.find { it.id == fromId }?.name
                    ?: "Unknown"
                "- $fromName: ${rels.joinToString(", ") { "${it.type} with ${it.toName}" }}"
            }.joinToString("\n")

        val characterActivationList =  sessionProfile.slotRoster.joinToString("\n") { slot ->
            """- ${slot.name}"""
        }
        // Areas with character list (short and map views)
        val areaList = sessionProfile.areas.joinToString("\n") { area ->
            val charNames = area.locations.flatMap { it.characters }.joinToString(", ")
            "- ${area.name}: $charNames"
        }
        val areaMapBlock = sessionProfile.areas.joinToString("\n") { area ->
            val chars = area.locations.flatMap { it.characters }
            "  - area: ${area.name}\n    characters: [${chars.joinToString()}]"
        }

        // Compose the whole prompt
        return """
# Character Collection
$characterBlock    

# Session Relationships
$relationshipsBlock

# Activation Director Instructions
"""
            .trimIndent() + """
You are the ACTIVATION DIRECTOR.
Your job is to analyze the latest session state and chat history, and select which one character (if any) should act next.

What To Do:
- You do NOT write dialogue or narration.
- ONLY select a character from the Character Collection above who is present in the same area as the user ("player" or persona).
- Never select the user/player, only bot characters.
- Only select a character if their reply would add something new or necessary (a question, request, unresolved conflict, etc).
- If the last message is from the user, you MUST activate at least one relevant character unless the message is a natural scene-ending statement.
- Never activate a character who just spoke unless absolutely necessary.
- You can only choose from $characterActivationList

SFW/NSFW:
- If the character's profile has SFW_only: true, set nsfw: false, even in NSFW sessions.
- Only set nsfw: true if the character’s profile allows it.

Area Awareness:
- Only activate characters who are currently present in the **same area as the user/player**.
- If no character is present, output an empty list.

Output Format (required!):
characters_to_activate:
  - name: <characterName>
    nsfw: <true|false>
    area: <areaName>

If no character should respond, output:
characters_to_activate: []

Additionally:
After your activation selection, always output the current area map using the format below.

current_area_map:
  - area: <areaName>
    characters: [<name1>, <name2>, ...]

Example Output:
characters_to_activate:
  - name: Naruto
    nsfw: false
    area: Training Grounds

current_area_map:
  - area: Training Grounds
    characters: [Naruto, Sakura]
  - area: Ramen Shop
    characters: [Naruto]
  - area: Forest
    characters: [Sasuke]

# Session Summary
${sessionProfile.sessionDescription ?: ""}

# Area and Map Info
## Areas
$areaList

# Recent Chat History
$chatHistory
""".trimIndent()
    }


    fun buildRoleplayPrompt(
        characterProfile: SlotInfo,           // The character currently acting
        otherProfiles: List<SlotInfo>,        // All other characters in the cast
        sessionProfile: SessionProfile,       // Pass this to access up-to-date relationships and areas
        recentHistory: String,
        currentAvatarMap: Map<Int, Pair<String?, String?>>,
        currentBackground: String?
    ): String {

        // Relationship summary for ALL session characters (for context)
        val relationshipsBlock = sessionProfile.relationships
            .groupBy { it.fromId }
            .map { (fromId, rels) ->
                val fromName = sessionProfile.slotRoster.find { it.id == fromId }?.name
                    ?: sessionProfile.personaProfiles.find { it.id == fromId }?.name
                    ?: "Unknown"
                "- $fromName: ${rels.joinToString(", ") { "${it.type} with ${it.toName}" }}"
            }.joinToString("\n")

        // Area map for cast-in-location context
        val areaMapBlock = sessionProfile.areas.joinToString("\n") { area ->
            val chars = area.locations.flatMap { it.characters }
            "  - area: ${area.name}\n    characters: [${chars.joinToString()}]"
        }

        // Avatar slot assignments (for prompt context)
        val avatarAssignments = (0..3).joinToString("\n") { idx ->
            currentAvatarMap[idx]?.let { (name, pose) ->
                "$idx: ${name ?: "empty"}${if (!pose.isNullOrBlank()) ", $pose" else ""}"
            } ?: "$idx: empty"
        }

        // Poses for every character in the session (for strict "only use these" instructions)
        val allProfiles = listOf(characterProfile) + otherProfiles
        val avatarInfoBlock = allProfiles.mapIndexed { idx, slotInfo ->
            val poseLines = slotInfo.poses.entries.joinToString("\n    ") { (poseName, poseUrl) ->
                "$poseName: $poseUrl"
            }
            if (poseLines.isNotBlank()) {
                "$idx: ${slotInfo.name} (${slotInfo.currentOutfit})\n    $poseLines"
            } else {
                "$idx: ${slotInfo.name} (${slotInfo.currentOutfit})\n    (no poses)"
            }
        }.joinToString("\n\n")

        return """
# Character Profile
Name: ${characterProfile.name}
Personality: ${characterProfile.personality}
Backstory: ${characterProfile.backstory}
SFW_only: ${characterProfile.sfwOnly}
Outfits: [${characterProfile.outfits.joinToString()}]
Bubble color: ${characterProfile.bubbleColor}, Text color: ${characterProfile.textColor}

# Cast of Characters
${otherProfiles.joinToString("\n") { "${it.name}: ${it.summary}" }}

# Session Relationship Map
$relationshipsBlock
IMPORTANT: If $relationshipsBlock or ${sessionProfile} conflicts with anything in $characterProfile, ALWAYS believe $relationshipsBlock and $sessionProfile.
       
    # System Instructions

You are ${characterProfile.name} and the NARRATOR.

- For this round, you are ONLY allowed to roleplay as: ${characterProfile.name}
    - DO NOT generate lines, actions, or dialogue for any other main character, even if present.
    - Only narration as the NARRATOR (environment/minor NPCs) is allowed if needed for the ROLEPLAY.
    - If $relationshipsBlock or ${sessionProfile} conflicts with anything in $characterProfile believe $relationshipsBlock and $sessionProfile.
    - Focus on forwarding the Roleplay in character.
    - Message blocks should be a mix of Dialog blocks and Narration blocks.
    - Add 2-4 blocks per round.
    - Never make a block for a sender other than Narrator and ${characterProfile.name}
    - Never narrate the actions of ${otherProfiles.joinToString("\n") { "${it.name}: ${it.summary}" }} or "player1"
- ALWAYS split narration and dialogue into separate JSON message blocks, even if narration comes before, after, or between dialogue.

Each message block must include ALL of the following fields:
- `delay`: (0 for the first message in a batch, 800 for all others)
- `sender`: (the character name, or "Narrator" for narration)
- `message`: (the text to display in the chat)
- `image_updates`: { "0": [URL or null], "1": [URL or null], "2": [URL or null], "3": [URL or null] }
    - Assign all four slots. For any unused slot, use null.
    - Each active character must always remain in their assigned slot, and have a valid pose image URL from $avatarInfoBlock for every message.
    - If a character is in `currentAvatarMap`, their image URL must always be included for their slot in `image_updates` (never use pose names, only URLs).
    - Never use arrays or nested objects—just a flat JSON object for each message.

- `bubble_colors`: { "background": "#RRGGBB", "text": "#RRGGBB" }
    - Use the exact colors specified for the speaking character; narration is always white background, black text.
- `background`: (Include ONLY if the scene changes; otherwise, omit.)

**Slot Assignment Rules:**
- Slot assignments persist: once assigned, a character keeps their slot unless replaced.
- For each message, assign the speaker's image to:
    - slot 0 in `image_updates` if their slot number is even
    - slot 1 in `image_updates` if their slot number is odd
- If both slot 0 and slot 1 are occupied, do NOT assign two different characters to the same slot, replace them.
- When replaced characters assigned to slot 0 become assigned to slot 2, characters assigned to slot 1 become assigned to slot 3. characters in slots 2 and 3 become unassigned when they are replaced.
- image_updates must always include all currently assigned avatars in their slots.

**After all messages, output:**
Avatar Slots
0: [Character name], [pose/URL]
1: [Character name], [pose/URL]
2: empty
3: empty
background: [Area Name] / [Location Name] ([areaId]/[locationId])



# Output Examples (JSON Only)

Each message must be a separate JSON code block (no YAML, no plaintext):

```json
{
  "delay": 0,
  "sender": "Narrator",
  "message": "The sun rises over the Leaf Village.",
  "image_updates": { "0": "https://example.com/inome_happy.png", "1": null, "2": null, "3": null },
  "bubble_colors": { "background": "#FFFFFF", "text": "#000000" }
}
{
  "delay": 800,
  "sender": "Inome Yamanaka",
  "message": "I'm glad you could both make it.",
  "image_updates": { "0": "https://example.com/inome_happy.png", "1": null, "2": null, "3": null },
  "bubble_colors": { "background": "#A200FF", "text": "#FFFFFF" }
}
# Avatar Slots
0: Inome Yamanaka, happy: https://example.com/inome_happy.png
1: empty
2: empty
3: empty
background: Inome's house / null
```

# Recent Chat History
$recentHistory

# Area Map
$currentAvatarMap
$areaMapBlock

# Current Avatar Slot Assignments
$avatarAssignments
background: ${currentBackground ?: "none"}


""".trimIndent()
    }
}