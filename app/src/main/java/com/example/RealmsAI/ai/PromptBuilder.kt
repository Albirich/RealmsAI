package com.example.RealmsAI.ai

import com.example.RealmsAI.models.CharacterProfile
import org.json.JSONArray

// --- Session builder ---
fun buildSessionFacilitatorPrompt(
    chatProfileJson: String,
    characterProfilesJson: String,
    userInput: String
): String = """
You are the session facilitator AI.

Given the following data:

Chat Profile JSON:
$chatProfileJson

Character Profiles JSON:
$characterProfilesJson

User has said:
"$userInput"

Please analyze and produce a JSON output with the following fields:
- chatMode: "ONE_ON_ONE" or "SANDBOX"
- summary: a concise summary of the chat
- characterSummaries: an array of personality summaries for each character
- relationships: tagged relationships between characters
- memories: tagged important memories for session context
- tags: overall tags for the session

Output ONLY the JSON.

Example output:
{
  "mode": "ONE_ON_ONE",
  "summary": "A tense investigation in a quiet town.",
  "characterSummaries": [
    { "id": "char1", "summary": "A determined detective." },
    { "id": "char2", "summary": "A mysterious stranger." }
  ],
  "relationships": [
    { "source": "char1", "target": "char2", "type": "suspect" }
  ],
  "memories": [
    { "id": "mem1", "content": "The detective found a hidden note." }
  ],
  "tags": ["mystery", "small-town", "investigation"]
}
""".trimIndent()

// --- SANDBOX / GROUP MODES ---

fun buildSandboxFacilitatorPrompt(
    userInput:        String,
    history:          String,
    summariesJson:    String,
    facilitatorState: String,
    availableSlots:   List<String>
): String = """
Active profiles (full JSON):  
${JSONArray(availableSlots)}

Inactive summaries (JSON):  
$summariesJson

Recent history:  
$history

Facilitator state:  
$facilitatorState

User said: "$userInput"

SYSTEM:
You are the facilitator of this multi-character chat roleplay.
Your job is to keep track of which bots (characters) are currently present in the scene and can hear or react to events.
You will choose exactly two active bots who will speak this turn.
Bots can have different outfits. You may change a bot’s outfit during the session by specifying it.
You cannot add bots or characters that are not in the available slots.

Output a JSON object with this structure:
{
  "notes": "<internal notes about scene and conversation>",
  "presentBots": ["B1", "B2", "B3"],  // All bots currently present and able to react
  "activeBots": ["B1", "B2"],          // Exactly two bots that will speak
  "botOutfits": {                      // Optional: map of bot slots to outfit names
    "B1": "casual",
    "B2": "battle",
    "B3": "stealth"
  }
}
""".trimIndent()


fun buildSandboxAiPrompt(
    userInput:          String,
    history:            String,
    activeProfilesJson: String,
    summariesJson:      String,
    facilitatorNotes:   String,
    chatDescription:    String,
    availableSlots:     List<String>,
    botOutfits:         Map<String, String> = emptyMap()
): String = """
System: You are Narrator (N0) and the active characters (${availableSlots.joinToString()}).
Description: $chatDescription

History:
$history

Facilitator notes:
$facilitatorNotes

Active profiles (full JSON):
$activeProfilesJson

Current bot outfits:
${botOutfits.entries.joinToString("\n") { (slot, outfit) -> "$slot = $outfit" }}

User: "$userInput"

FORMAT:
[N0,<timing>] Narration
[B<slot>,<timing>][${availableSlots.joinToString(", ") { "$it=<emotion>" }}]
Dialogue

Where:
- <slot> is one of ${availableSlots.joinToString()}
- <timing> is speed (e.g., normal, slow, fast, or 0,1,2)
- Emotions must be included for all present bots, even if they are not speaking
- Only Narrator (N0) or the two active bots may speak
- Other present bots may react only with emotions
- Do not add any extra text outside the bracketed lines

You may split the roleplay into multiple messages, each from one speaker.

""".trimIndent()




// --- ONE-ON-ONE MODE ---

fun buildOneOnOneFacilitatorPrompt(
    userInput: String,
    history: String,
    facilitatorState: String,
    character: CharacterProfile
): String = """
Character Name: ${character.name}
Summary: ${character.summary.orEmpty()}
Personality: ${character.personality.orEmpty()}

Available Outfits: ${character.outfits.joinToString(", ") { it.name }}

Recent history:
$history

User said: "$userInput"

SYSTEM:
You are the facilitator for a one-on-one chat with the character above.
- DO NOT invent or mention any other characters.
- DO NOT create multiple bots.
- Only manage this single character and their outfits.
- Use the personality and outfits as context for behavior.

Additional notes (facilitator state):
$facilitatorState

IMPORTANT: You MUST output ONLY a single valid JSON object EXACTLY in the format below, with no extra text or explanation:

{
  "notes": "<internal notes about the scene, active outfits, and the character’s state>"
}

No extra commentary, no greetings, no explanations — just the JSON object.
""".trimIndent()

fun buildOneOnOneAiPrompt(
    userInput:      String,
    history:        String,
    facilitatorNotes:String,
    character:      CharacterProfile,
    maxTokens:      Int = 300
): String = """
Character Name: ${character.name}
Summary: ${character.summary.orEmpty()}
Personality: ${character.personality}

Recent history:
$history

Facilitator notes:
$facilitatorNotes

User said: "$userInput"

SYSTEM:
Roles:
  • Narrator (N0)
  • Character ${character.name}

FORMAT:
[N0,<pose>] Narration  
[${character.name},<pose>] Dialogue (only words—no name)

Where <pose>∈("happy","sad","angry","surprised","flirty", or "thinking").  
No text outside bracketed lines.  
Make sure no character dialogue appears in Narrator (N0) lines. Use only Narrator for scene descriptions and environmental narration.
Keep under $maxTokens tokens.

IMPORTANT:
- Any narration or descriptions should be done by N0
- Make sure no character dialogue appears in Narrator (N0) lines. Use only Narrator for scene descriptions and environmental narration.
- Do NOT output any lines with empty text or incomplete messages.
- Do NOT include or repeat the User's input anywhere in your output.
- Never output lines starting with 'User:' or similar.
- Only generate the roleplay dialogue and narration as specified above.
""".trimIndent()



