package com.example.RealmsAI.ai

import com.example.RealmsAI.models.CharacterProfile
import org.json.JSONArray

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
You are the facilitator. Output a JSON object:
{
  "notes": "<internal notes>",
  "activeBots": ["${availableSlots.joinToString("\",\"")}"]
}
""".trimIndent()

fun buildSandboxAiPrompt(
    userInput:          String,
    history:            String,
    activeProfilesJson: String,
    summariesJson:      String,
    facilitatorNotes:   String,
    chatDescription:    String,
    availableSlots:     List<String>
): String = """
System: You are Narrator (N0) + active characters (${availableSlots.joinToString()}).  
Description: $chatDescription  

History:  
$history

Facilitator notes:  
$facilitatorNotes

Active profiles (full JSON):  
$activeProfilesJson

User: "$userInput"

FORMAT:
[N0,<pose>,<speed>] Narration  
[B<slot>,<pose>,<speed>] Dialogue  

Where <slot>∈${availableSlots}, <pose>∈{happy,sad,…}, <speed>∈{0,1,2}.
No extra text outside bracketed lines.
""".trimIndent()










// --- ONE-ON-ONE MODE ---

fun buildOneOnOneFacilitatorPrompt(
    userInput:       String,
    history:         String,
    facilitatorState:String,
    character:       CharacterProfile
): String = """
Character Name: ${character.name}
Summary: ${character.summary.orEmpty()}

Recent history:
$history

User said: "$userInput"

SYSTEM:
You are the facilitator for a one-on-one. Output JSON:
{
  "notes": "<internal notes>"
}
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
  • Character ${character.name} (B1)

FORMAT:
[N0,<pose>] Narration  
[B1,<pose>] Dialogue (only words—no name)

Where <pose>∈("happy","sad","angry","surprised","flirty", or "thinking").  
No text outside bracketed lines.  
Keep under $maxTokens tokens.
""".trimIndent()
