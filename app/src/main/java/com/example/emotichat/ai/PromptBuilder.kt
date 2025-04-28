package com.example.emotichat.ai

/** Builds the small facilitator prompt (1st API call). */
fun buildFacilitatorPrompt(
    userInput: String,
    history: String,
    facilitatorState: String
): String = """
  You are the game facilitator. Do not role-play.
  • New user message: "$userInput"
  • Recent history:
  $history
  • Facilitator state (locations & volumes):
  $facilitatorState

  Return a JSON object:
  { "notes": "...", "activeBots": ["B1","B3"] }
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

  Respond with one line per active character:
  [B<slot>,<emotion>][<speed>] "Their reply here"
""".trimIndent()
