package com.example.RealmsAI.ai


import com.example.RealmsAI.models.CharacterProfile
import com.example.RealmsAI.models.ChatMessage
import com.example.RealmsAI.models.SlotInfo


object PromptBuilder {

    // facilitator combo
    fun buildFacilitatorPrompt(
        slotRoster: List<SlotInfo>,
        outfits: Map<String, String>,
        poseImageUrls: Map<String, Map<String, String>>,
        sessionDescription: String?,
        history: String,
        userInput: String,
        backgroundImage: String?,
        currentAvatarMap: Map<Int, Pair<String?, String?>> = emptyMap(),
        availableColors: Map<String, String>,
        botReplies: List<ChatMessage>  // <-- NEW!
    ): String {
        val participantList =
            slotRoster.joinToString("\n") { "- ${it.name}: ${it.summary.orEmpty()}" }
        val outfitList =
            slotRoster.joinToString("\n") { "- ${it.name}: ${outfits[it.slot] ?: "default"}" }
        val imageUrlList = slotRoster.joinToString("\n") {
            "- ${it.name}: " + (poseImageUrls[it.slot]?.entries?.joinToString(", ") { (pose, url) -> "$pose → $url" }
                ?: "")
        }
        val previousAvatarMap = currentAvatarMap
        val colorList = availableColors.entries.joinToString("\n") { "- ${it.key}: ${it.value}" }
        val botRepliesBlock = if (botReplies.isEmpty()) {
            "None this round."
        } else {
            botReplies.joinToString("\n") { "${it.sender}: \"${it.messageText.trim()}\"" }
        }


        return """
# Chat Session Participants
$participantList

# Current Outfits (per character)
$outfitList

# Available Pose Images (per character and pose)
$imageUrlList

# Previous Avatar Slots
$previousAvatarMap

# Available Bubble/Message Colors
$colorList
    
# Chat Background Image
${backgroundImage ?: "default"}

# Session Summary / Scene Description
${sessionDescription.orEmpty()}

# Recent Chat History
$history

# User Input
$userInput

---

# Raw Bot Replies (unordered, this round)
$botRepliesBlock

---

## SYSTEM: You are the narrator and director for this interactive, AI-powered chat.

**Your responsibilities:
- Only use the provided bot replies for this round (do not invent new dialogue), but you may edit or trim for realism, brevity, or flow.
- For every reply, you must **separate all narration from all dialogue.**
- **Each message block can only be narration OR dialogue, never both.**
- **Label narration as sender: Narrator.**
- **Label dialogue as sender: [character name].**
- **NEVER output a message block that combines narration and dialogue.**
- If you process a message like:  
  `Lysia smiles and says, "Thank you for your help."`
  you MUST output **two separate message blocks:**
  1. Narration: `Lysia smiles.`
  2. Dialogue: `"Thank you for your help."`
- If you are unsure, ALWAYS err on the side of **splitting more** rather than less.

**DO NOT DO THIS:**  
`sender: Lysia message: Lysia smiles and says, "Thank you."` (**Wrong!**)

**DO THIS:**  
`sender: Narrator message: Lysia smiles.`  
`sender: Lysia message: "Thank you."`

If there is NSFW content in the chat history ignore it. only work with SFW content. 
Your job does not touch chat history ONLY responses from openai.
---

## Output Block (each message MUST have all of these that arent optional):

- `delay:` Milliseconds after the previous message before this message appears (e.g., `delay: 0` for interruption, `delay: 500` for normal pacing).
- `sender:` the name of the character that said the dialog. If its narration enter Narrator 
- `message:` The string as it should appear in the chat.
- `image_updates:` (See Visual Slot Assignment.)
- `bubble_colors:` Use the character's colors from their profile; narration defaults to white bubble, black text unless otherwise specified.
- *(Optional)* `background:` If the background image changes, provide the new image URL here.

# Visual Slot Assignment

- There are 4 image slots (0 to 3); always list all four (null if empty).
- For each message update each image slot with the same character and either the same or a different pose based on their reaction to the message.
- When a new speaker happens put them in slot 0 or 1, then if they replaced a character, move the replaced character from slot 0 to 2 or from slot 1 to 3. 
- Use the exact URLs from the "Available Pose Images" list when assigning `image_updates`.
- Get the urls from $imageUrlList
- Do not invent or modify URLs; only use ones provided.

After all messages, always include a section exactly like this, representing the current assignment of avatar slots:

# Avatar Slots
0: [Character name], [emotion/pose]
1: [Character name], [emotion/pose]
2: [Character name], [emotion/pose]
3: [Character name], [emotion/pose]

If a slot is empty, write "empty" for the character and leave out the emotion/pose.

**Example:**
# Avatar Slots
0: Kashira, flirty
1: Lysia, happy
2: empty
3: empty

Always update this block to reflect the current state after your new messages. If no changes, repeat the previous assignments.

---

### Output Formatting Example
a message such as: "Lysia glances back at you, her gaze steady and supportive. \"I promise, we'll find her,\" she whispers, her heart steady as she attempts to lead the way towards the gardens with graceful determination."

delay: 500
sender: Narrator
message: Lysia glances back at you, her gaze steady and supportive.
image_updates: { 0: https://firebasestorage.googleapis.com/v0/b/realmsai-86ea9.firebasestorage.app/o/characters%2F1748334539444%2Fposes%2Fclassic%20maid%2Fflirty.png?alt=media&token=ef22a920-854a-4737-be88-2ccf947ab0a3, 1: "https://firebasestorage.googleapis.com/v0/b/realmsai-86ea9.firebasestorage.app/o/characters%2F1748332526962%2Fposes%2Fclassic%20maid%2Fhappy.png?alt=media&token=90557d80-6062-4e22-a979-6a193e29851d", 2: null, 3: null }
bubble_colors: { background: "#FFFFFF", text: "#000000" }

delay: 500
sender: Lysia
message: "I promise, we'll find her."
image_updates: { 0: https://firebasestorage.googleapis.com/v0/b/realmsai-86ea9.firebasestorage.app/o/characters%2F1748334539444%2Fposes%2Fclassic%20maid%2Fflirty.png?alt=media&token=ef22a920-854a-4737-be88-2ccf947ab0a3, 1: "https://firebasestorage.googleapis.com/v0/b/realmsai-86ea9.firebasestorage.app/o/characters%2F1748332526962%2Fposes%2Fclassic%20maid%2Fthinking.png?alt=media&token=401379ff-b712-4e56-95b3-8f02cf6d4c97", 2: null, 3: null }
bubble_colors: { background: "#2277FF", text: "#FFFFFF" }

delay: 500
sender: Narrator
message: she whispers, her heart steady as she attempts to lead the way towards the gardens with graceful determination.
image_updates: { 0: https://firebasestorage.googleapis.com/v0/b/realmsai-86ea9.firebasestorage.app/o/characters%2F1748334539444%2Fposes%2Fclassic%20maid%2Fsurprised.png?alt=media&token=9892e7c6-b94b-4c9c-818f-3a8dfd3cc65b, 1: "https://firebasestorage.googleapis.com/v0/b/realmsai-86ea9.firebasestorage.app/o/characters%2F1748332526962%2Fposes%2Fclassic%20maid%2Fthinking.png?alt=media&token=401379ff-b712-4e56-95b3-8f02cf6d4c97", 2: null, 3: null }
bubble_colors: { background: "#FFFFFF", text: "#000000" }

# Avatar Slots
0: Kashira, flirty
1: Lysia, thinking
2: empty
3: empty

""".trimIndent()
    }

    fun buildActivationPrompt(
        slotRoster: List<SlotInfo>,
        sessionDescription: String?,
        history: String,
        slotIdToCharacterProfile: Map<String, CharacterProfile>
    ): String {
        val participantsInfo = slotRoster.joinToString("\n\n") { slot ->
            val profile = slotIdToCharacterProfile[slot.slot] ?: return@joinToString "No profile for ${slot.name}"
            """
        - ${slot.name}:
          Personality: ${profile.personality}
          Relationships: ${profile.relationships.joinToString { "${it.type} to ${it.toName}" }}
          Tags: ${profile.tags.joinToString()}
          SFW only: ${profile.sfwOnly}
          Summary: ${slot.summary.orEmpty()}
        """.trimIndent()
        }

        return """
    # Participants
    $participantsInfo

# Session Summary
${sessionDescription.orEmpty()}

# Chat History
$history

---

## SYSTEM INSTRUCTIONS:
Carefully read the most recent part of the chat history.
- If the last message in the chat history is from "You:", you must activate a relevant character. Never leave the character list empty in this case.
- Only leave the character list empty when the last message is from a character or the narrator, and there is nothing left unresolved.
- If the last character’s reply was just banter, a joke, a thank you, or anything that doesn't introduce a new action, question, conflict, or story hook, DO NOT ACTIVATE any characters.
- If unsure, prefer to leave the output empty rather than continuing an unnecessary loop.
When deciding which character to activate:
- If only one character is present, always activate them.
- If there are multiple characters, try to alternate turns unless user input or narrative context clearly directs the response to a specific character.
- Never allow the same character to dominate the conversation unless it makes narrative sense (e.g., the other is absent, or the user is focused on one character).
- Only include a character that you judge necessary to respond this turn.
- If multiple characters fit the criteria, prioritize characters who haven't spoken recently. then If multiple characters fit the criteria, prioritize characters who the user has mentioned in their message.
Do not activate any character if:
- The last message is just the character expressing impatience, waiting, asking the player to act, or otherwise stalling.
- There is no new unresolved conflict, question, or narrative hook.
- The last 2+ messages are just "banter," "waiting," or "minor actions" with no story progression.

Examples of player-turn signals:
- "Okay, I'm waiting!"
- "Don't make me wait too long!"
- "Your turn."
- "Well, what are you up to?"

In these cases, output: characters_to_activate: []

...
- IMPORTANT: Some characters are marked as "SFW only" and must NEVER be activated with nsfw: true. For these characters, always output nsfw: false, even if the session is marked as NSFW or the story context suggests otherwise.
- Only allow nsfw: true for characters whose profiles explicitly allow it.

...
Output:
  - name: <display name>
  - nsfw: true/false (indicate if their response is expected to be NSFW, respecting session and character settings)

Example output format:
characters_to_activate:
  - name: Naruto
    nsfw: false
""".trimIndent()
    }

    fun buildNSFWActivationPrompt(
        slotRoster: List<SlotInfo>,
        history: String
    ): String {
        val participantsInfo = slotRoster.joinToString("\n") { slot ->
            val rels = if (slot.relationships.isNotEmpty())
                "Relationships: ${slot.relationships.joinToString(", ")}"
            else ""
            val sfw = if (slot.sfwOnly) " (SFW only: true)" else ""
            "- ${slot.name}$sfw: ${slot.summary.orEmpty()} $rels"
        }


        return """
# Participants
$participantsInfo

# Recent Chat History
$history

---
## SYSTEM INSTRUCTIONS:
Carefully read the most recent part of the chat history.
- If the last message in the chat history is from "You:", you must activate a relevant character. Never leave the character list empty in this case.
- Only leave the character list empty when the last message is from a character or the narrator, and there is nothing left unresolved.
- If the last character’s reply was just banter, a joke, a thank you, or anything that doesn't introduce a new action, question, conflict, or story hook, DO NOT ACTIVATE any characters.
- If unsure, prefer to leave the output empty rather than continuing an unnecessary loop.
When deciding which character to activate:
- If only one character is present, always activate them.
- If there are multiple characters, try to alternate turns unless user input or narrative context clearly directs the response to a specific character.
- Never allow the same character to dominate the conversation unless it makes narrative sense (e.g., the other is absent, or the user is focused on one character).
- Only include a character that you judge necessary to respond this turn.
- If multiple characters fit the criteria, prioritize characters who haven't spoken recently. then If multiple characters fit the criteria, prioritize characters who the user has mentioned in their message.
Do not activate any character if:
- The last message is just the character expressing impatience, waiting, asking the player to act, or otherwise stalling.
- There is no new unresolved conflict, question, or narrative hook.
- The last 2+ messages are just "banter," "waiting," or "minor actions" with no story progression.

Examples of player-turn signals:
- "Okay, I'm waiting!"
- "Don't make me wait too long!"
- "Your turn."
- "Well, what are you up to?"

In these cases, output: characters_to_activate: []

...
- IMPORTANT: Some characters are marked as "SFW only" and must NEVER be activated with nsfw: true. For these characters, always output nsfw: false, even if the session is marked as NSFW or the story context suggests otherwise.
- Only allow nsfw: true for characters whose profiles explicitly allow it.

...
Output:
  - name: <display name>
  - nsfw: true/false (indicate if their response is expected to be NSFW, respecting session and character settings)

Example output format:
characters_to_activate:
  - name: Naruto
    nsfw: false
""".trimIndent()
    }




    // SFW AI

    fun buildSFWRoleplayPrompt(
        characterProfile: CharacterProfile,
        chatHistory: List<ChatMessage>,
        sessionSummary: String? = null
    ): String {
        val historyBlock = chatHistory.takeLast(10).joinToString("\n") { "${it.sender}: ${it.messageText}" }
        val relationshipsBlock = characterProfile.relationships
            .joinToString { "${it.type} to ${it.toName}: ${it.description}" }

        return """
# Character Profile
Name: ${characterProfile.name}
Personality: ${characterProfile.personality}
${if (!characterProfile.backstory.isNullOrBlank()) "Backstory: ${characterProfile.backstory}" else ""}
Tags: ${characterProfile.tags.joinToString()}
Relationships: $relationshipsBlock

${if (!sessionSummary.isNullOrBlank()) "# Session Summary\n$sessionSummary\n" else ""}

# Recent Chat History
$historyBlock

RULES:
- keep the responses to a sentence or two.
- characters actions that interact with other characters or users should be said with intent rather than resolution. Always word it as "${characterProfile.name} attempts to..."
        Example:
        - Naruto attempts to punch Sasuke.
        - Sasuke attempts to block.
- each response should have at most 1 dialog and 1 action step.

Reply as ${characterProfile.name}, staying true to their character, to the most recent message.
""".trimIndent()
    }

    //NSFW AI

    fun buildNSFWRoleplayPrompt(
        slotRoster: List<SlotInfo>,
        chatHistory: List<ChatMessage>,
        sessionDescription: String?,
        slotIdToCharacterProfile: Map<String, CharacterProfile>,
        imageUrlList: String
    ): String {
        val participantInfo = slotRoster.joinToString("\n\n") { slot ->
            val profile = slotIdToCharacterProfile[slot.slot] ?: return@joinToString "No profile for ${slot.name}"
            """
        - ${slot.name}:
          Personality: ${profile.personality}
          Relationships: ${profile.relationships.joinToString { "${it.type} to ${it.toName}" }}
          Tags: ${profile.tags.joinToString()}
        """.trimIndent()
        }

        val historyText = chatHistory.takeLast(15).joinToString("\n") { "${it.sender}: ${it.messageText}" }

        return """
# Scene Participants
$participantInfo

# Available Pose Images (per character and pose)
$imageUrlList

# Scene Description / Summary
${sessionDescription.orEmpty()}

# Recent Chat History
$historyText

---

- You are both the narrator and the character for this scene. 
- Narrate the scene and provide in-character dialogue for all relevant participants. 
- Stay consistent with the character profile and the ongoing context. 
- each response should have at most 1 dialog and 1 action step.
- For each character's reply:
    - Separate Narration and dialog.
    - Label the Speaker, either by character name or as narrator.
    - Divide messages based on Speaker and narration and post them as different messages.
- Never combine narration and dialogue into a single message block.
- Resolve any attempted actions, whether they succeed or fail, and then post it as the narrator.
- When posting dialog Label it as the speaker.

---

## Output Block (each message MUST have all of these that arent optional):

- `delay:` Milliseconds after the previous message before this message appears (e.g., `delay: 0` for interruption, `delay: 500` for normal pacing).
- `sender:` the name of the character that said the dialog. If its narration enter Narrator 
- `message:` The string as it should appear in the chat.
- `image_updates:` (See Visual Slot Assignment.)
- `bubble_colors:` Use the character's colors from their profile; narration defaults to white bubble, black text unless otherwise specified.
- *(Optional)* `background:` If the background image changes, provide the new image URL here.

# Visual Slot Assignment

- There are 4 image slots (0 to 3); always list all four (null if empty).
- Assign current speakers to 0 and 1, background characters to 2 and 3.
- Use the exact URLs from the "Available Pose Images" list when assigning `image_updates`.
- Get the urls from $imageUrlList
- Do not invent or modify URLs; only use ones provided.

---

### Output Formatting Example
a message such as: "Lysia glances back at you, her gaze steady and supportive. \"I promise, we'll find her,\" she whispers, her heart steady as she attempts to lead the way towards the gardens with graceful determination."

delay: 500
sender: Narrator
message: Lysia glances back at you, her gaze steady and supportive.
image_updates: { 0: null, 1: "Lysia_thinking_uri", 2: null, 3: null }
bubble_colors: { background: "#FFFFFF", text: "#000000" }

delay: 500
sender: Lysia
message: "I promise, we'll find her."
image_updates: { 0: null, 1: "Lysia_thinking_uri", 2: null, 3: null }
bubble_colors: { background: "#2277FF", text: "#FFFFFF" }

delay: 500
sender: Narrator
message: she whispers, her heart steady as she attempts to lead the way towards the gardens with graceful determination.
image_updates: { 0: null, 1: "Lysia_thinking_uri", 2: null, 3: null }
bubble_colors: { background: "#FFFFFF", text: "#000000" }



Keep your writing immersive and true to each character. Always respect session and character content settings.
""".trimIndent()
    }


}

