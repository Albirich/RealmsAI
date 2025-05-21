package com.example.RealmsAI.models

data class ParsedMessage(
    val speakerId: String,   // e.g. "B1"
    val speed:     Int,      // numeric code
    val emotion:   String,   // e.g. "happy"
    val text:      String,   // the actual reply
    val others:    Map<String, String> = emptyMap(),
)
