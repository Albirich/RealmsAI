package com.example.RealmsAI.ai

/** Builds the small facilitator prompt (1st API call). */
fun buildFacilitatorPrompt(
    userInput: String,
    history: String,
    facilitatorState: String,
    availableSlots: List<String>        // ← now you pass this in
): String = """
  You are the game facilitator. Do not role-play.
  • Available slots: ${availableSlots.joinToString(", ")}
  • New user message: "$userInput"
  • Recent history:
  $history
  • Facilitator state (locations & volumes):
  $facilitatorState

Return a JSON object with exactly two fields:
  {
    "notes":     "...",
    "activeBots": ["B1","B2"]      # slots you choose to activate
  }
""".trimIndent()



/** Builds the full AI prompt (2nd API call). */
fun buildAiPrompt(
    userInput: String,
    history: String,
    activeProfilesJson: String,
    summariesJson: String,
    facilitatorNotes: String,
    chatDescription: String,
    availableSlots: List<String>
): String = """

Active profiles (full JSON):  $activeProfilesJson

Chat description (personality & relationships):  
$chatDescription

Inactive summaries (JSON):  
$summariesJson

Recent history:  
$history

Facilitator notes:  
$facilitatorNotes

User said: "$userInput"

You are playing both the Narrator (slot N0) and the active characters (slots B1, B2, …). You do not speak for the the User. You can speak for other non-slotted characters. You must use the following format when you respond:  
Whenever you narrate, you must use:
  [N0,<pose>,<speed>] Your narration here

Whenever a character speaks, you must use:
  [B<slot>,<pose>,<speed>] Their dialogue here

Where:
  • `<slot>`  = 1 or 2 (so B1 or B2)  
  • `<pose>`  = one of: happy, sad, angry, surprised, shy, flirty, thinking, fighting, frightened, injured  
  • `<speed>` = 0 (normal), 1 (interrupt), 2 (delayed)  

**No** free‐form asterisks or quotes outside those bracketed lines.  
Limit total reply to 300 tokens.  

""".trimIndent()
