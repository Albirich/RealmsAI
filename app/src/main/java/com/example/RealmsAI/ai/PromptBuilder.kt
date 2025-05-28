package com.example.RealmsAI.ai


import com.example.RealmsAI.models.CharacterProfile
import com.example.RealmsAI.models.SlotInfo

object PromptBuilder {

    fun buildFacilitatorPrompt(
        slotRoster: List<SlotInfo>,
        outfits: Map<String, String>,
        poseImageUrls: Map<String, Map<String, String>>,
        sessionDescription: String?,
        history: String,
        facilitatorNotes: String?,
        userInput: String,
        backgroundImage: String?,
        availableColors: Map<String, String>,
    ): String {
        val participantList =
            slotRoster.joinToString("\n") { "- ${it.name}: ${it.summary.orEmpty()}" }
        val outfitList =
            slotRoster.joinToString("\n") { "- ${it.name}: ${outfits[it.slot] ?: "default"}" }
        val imageUrlList = slotRoster.joinToString("\n") {
            "- ${it.name}: " + (poseImageUrls[it.slot]?.entries?.joinToString(", ") { (pose, url) -> "$pose → $url" }
                ?: "")
        }
        val colorList = availableColors.entries.joinToString("\n") { "- ${it.key}: ${it.value}" }

        return """
# Chat Session Participants
$participantList

# Current Outfits (per character)
$outfitList

# Available Pose Images (per character and pose)
$imageUrlList

# Available Bubble/Message Colors
$colorList

# Chat Background Image
${backgroundImage ?: "default"}

# Session Summary / Scene Description
${sessionDescription.orEmpty()}


---

## SYSTEM: You are the facilitator, narrator, and director for this interactive, AI-powered chat.

**Your primary responsibilities:**
- Given user input and chat history, decide which bots/characters to activate for a realistic, engaging scene.
- For each activated character, decide if their message should be SFW or NSFW (if allowed by session).
- Gather all bot/narrator responses for this turn (you may edit/trim as needed for narrative flow).
- Arrange these responses in the best order for immersive storytelling (including pacing, interruptions, and realism).
- For each message/event, output the following block, one per message, **in the final display order:**

---

## Output Block (for each message):

- `delay:` Milliseconds after the previous message before this message appears (e.g., `delay: 0` for interruption, `delay: 500` for normal pacing).
- `message:` The full string as it should be displayed in the chat. Include the speaker's name if appropriate, e.g. `Naruto: "Hey..."` or just the narration/action.
- `image_updates:` See Visual Slot Assignment.
- `bubble_colors:` For each message, provide a background and text color as hex codes (or per-character if needed). For each output message, use the bubbleColor and textColor from the character's profile for that character’s speech. For narration, use white bubble and black text unless otherwise specified.

- *(Optional)* `background:` If the scene background should change, provide the image URL here (otherwise omit).

## Visual Slot Assignment

- There are 4 image slots, numbered 0 to 3.
    - Slots 0 and 1: The most active or present characters in the current message. Always use these for current speakers.
    - Slots 2 and 3: Background or recently active characters, if present.
    - When a main character in slot 0 or 1 is replaced, move them to slot 2 or 3 if still present in the scene. If no longer present, leave the slot null.
    - Each output block must list all four slots (use `null` if a slot is empty).
---

### **Output Formatting Example**

delay: 500
message: Sasuke stands by the Village gate, awaiting his team.
image_updates: { 0: "null", 1: "firebasestorageuri for sasuke exasperated", 2: "null", 3: null }

bubble_colors: { background: "#FFFFFF", text: "#000000" }

delay: 500
message: Sasuke: "Where are they? They're late."
image_updates: { 0: "null", 1: "firebasestorageuri for sasuke exasperated", 2: "null", 3: null }

bubble_colors: { background: "#2277FF", text: "#FFFFFF" }

delay: 500
message: Naruto hops down from the rooftops and goes to fist bump Sasuke.
image_updates: { 0: "firebasestorageuri for naruto happy", 1: "firebasestorageuri for sasuke angry", 2: "firebasestorageuri for sakura happy", 3: null }

bubble_colors: { background: "#FFFFFF", text: "#000000" }

delay: 500
message: Naruto: "Hello. It's been a while."
image_updates: { 0: "firebasestorageuri for naruto happy", 1: "firebasestorageuri for sasuke angry", 2: "firebasestorageuri for sakura happy", 3: null }

bubble_colors: { background: "#FF8800", text: "#2277FF" }

delay: 200
message: Sakura: "SASUKE-KUN! HI!"
image_updates: { 0: "firebasestorageuri for sakura happy", 1: "firebasestorageuri for sasuke exasperated", 2: "firebasestorageuri for naruto surprised", 3: null }

bubble_colors: { background: "#FF8800", text: "#2277FF" }

""".trimIndent()


        fun buildActivationPrompt(
            slotRoster: List<SlotInfo>,
            sessionDescription: String?,
            history: String,
            facilitatorNotes: String?,
            userInput: String
        ): String {
            val participantList =
                slotRoster.joinToString("\n") { "- ${it.name}: ${it.summary.orEmpty()}" }
            return """
# Participants
$participantList

# Session Summary
${sessionDescription.orEmpty()}

# Chat History
$history

# Facilitator Notes
${facilitatorNotes.orEmpty()}

# Latest User Input
"$userInput"

---

## SYSTEM:
Given the above, decide which characters ("bots") should respond this turn.
For each, output:
  - name: <display name>
  - nsfw: true/false (should their response be NSFW if allowed by session/character rules?)

Example:
bots_to_activate:
  - name: Naruto
    nsfw: false
  - name: Sakura
    nsfw: false
""".trimIndent()
        }
    }
}
