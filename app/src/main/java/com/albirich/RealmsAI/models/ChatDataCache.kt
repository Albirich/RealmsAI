package com.albirich.RealmsAI

import com.albirich.RealmsAI.models.CharacterProfile
import com.albirich.RealmsAI.models.Relationship // Make sure to import this!

object ChatDataCache {
    var selectedCharacters: MutableList<CharacterProfile> = mutableListOf()

    // ADD THIS: Hold relationships safely in memory
    var chatRelationships: MutableList<Relationship> = mutableListOf()

    fun clear() {
        selectedCharacters.clear()
        chatRelationships.clear()
    }
}