package com.albirich.RealmsAI.models

import java.util.UUID

data class ScenarioEvent(
    var id: String = UUID.randomUUID().toString(),
    var title: String = "",           // So the user can name it (e.g., "Demon Attack")
    var description: String = "",     // Goes to the System Prompt
    var narratorMessage: String = ""  // Injected into the chat to force AI reaction
)