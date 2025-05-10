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
    fullProfilesJson: String,
    summariesJson: String,
    facilitatorNotes: String,
    chatDescription: String
): String = """
  Now you are one of the active characters. Do not break format.
  Active profiles (full JSON): $fullProfilesJson
  Chat description (personality & relationships):
  $chatDescription
  Inactive summaries (JSON): $summariesJson
  Recent history:
  $history
  Facilitator notes:
  $facilitatorNotes
  User said: "$userInput"

Valid poses: happy, sad, angry, shy, surprised, flirty, thinking, fighting

Respond *strictly* as:
  [B<slot>,<pose>,<speed>] "Your reply here"

  • `<slot>`  = the bot’s slot number, e.g. B1  
  • `<pose>`  = one of the valid poses  
  • `<speed>` = an integer speed code (0 = normal, 1 = interrupt, 2 = delayed)  
  • **Do not** include any extra brackets or text outside of that single bracket.
""".trimIndent()
