package com.example.RealmsAI.models

import java.io.Serializable

data class DialogueExample(
    val prompt: String = "",
    val response: String = ""
) : Serializable