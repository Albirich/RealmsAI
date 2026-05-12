package com.albirich.RealmsAI.models

import com.google.firebase.Timestamp
import java.util.UUID

data class LoreEntry(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var keys: List<String> = emptyList(),
    var content: String = "",
    var alwaysOn: Boolean = false,
    var embedding: List<Double>? = null
)

data class Lorebook(
    val id: String = UUID.randomUUID().toString(),
    var creatorId: String = "",
    var originalId: String? = null,
    var title: String = "",
    var description: String = "",
    var coverUri: String? = null,
    var private: Boolean = false,
    var timestamp: Timestamp? = null,
    var announced: Boolean = false,
    var entries: MutableList<LoreEntry> = mutableListOf()
)