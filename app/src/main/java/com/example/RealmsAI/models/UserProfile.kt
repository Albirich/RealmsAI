package com.example.RealmsAI.models

data class UserProfile(
    val handle: String? = null,
    val name: String = "",
    val bio: String = "",
    val iconUrl: String? = null,
    val favorites: List<String> = emptyList(),
    val userPicks: List<String> = emptyList(),
    val friends: List<String> = emptyList(),
    val pendingFriends: List<String> = emptyList(),
    val recentChats: List<String> = emptyList(),
    val isPremium: Boolean = false,
    val dailyMessageCount: Int = 0,
    val blockedUsers: List<String> = emptyList(),
    val lastMessageDate: String? = null,
    val badges: List<String> = emptyList()
)
