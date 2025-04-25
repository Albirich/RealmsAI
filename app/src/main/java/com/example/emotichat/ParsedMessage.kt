// ParsedMessage.kt
package com.example.emotichat.models

enum class Timing { NORMAL, INTERRUPT, DELAYED }

data class ParsedMessage(
    val speakerId: String,
    val emotion:   String,
    val timing:    Timing,
    val text:      String,
    val target:    Pair<String,String>? = null
)
