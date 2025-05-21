package com.example.RealmsAI.models

data class SessionLandingProfile(
    val relationships: List<Relationship> = emptyList(),
    val participants: List<String> = emptyList(),   // character/user IDs, in slot order if needed
    val sfwOnly: Boolean = true
)

