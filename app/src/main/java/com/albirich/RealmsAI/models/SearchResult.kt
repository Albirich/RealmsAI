package com.albirich.RealmsAI.models

data class SearchResult(
    val title: String,
    val content: String,
    val type: String, // "Lore" or "Memory"
    val score: Double
)