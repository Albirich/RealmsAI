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
    activeSlots: List<String>            // <— new param
): String = """
  Now you are one of the active characters. Do not break format.
  Active profiles (full JSON): $activeProfilesJson
  Chat description (personality & relationships):
  $chatDescription
  Inactive summaries (JSON): $summariesJson
  Recent history:
  $history
  Facilitator notes:
  $facilitatorNotes
  User said: "$userInput"

  Valid poses: happy, sad, angry, shy, surprised, flirty, thinking, fighting

  You have ${activeSlots.size} active character(s): ${activeSlots.joinToString(", ")}.
  **Please produce exactly ${activeSlots.size} lines, one per slot in the order above.**

  Each line must be:
    [B<slot>,<pose>,<speed>] "Your reply here"
    
 Make sure each line *starts* with a bracket, the slot, pose, and a numeric speed code, *enclosed* in square brackets. For example:

   [B1,happy,0] "Hey there!"
   [B2,thinking,0] "How can I help?"

 Do NOT omit any brackets, and DO use numbers (0/1/2) instead of words.

  Do not output anything else.
""".trimIndent()
