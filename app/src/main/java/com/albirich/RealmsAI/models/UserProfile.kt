package com.albirich.RealmsAI.models

import com.google.firebase.firestore.PropertyName

data class UserProfile(
    val handle: String? = null,
    val name: String = "",
    val bio: String = "",
    val iconUrl: String? = null,
    val favorites: List<String> = emptyList(),
    val userPicks: List<String> = emptyList(),
    val friends: List<String> = emptyList(),
    var following: List<String> = emptyList(),
    val blockedUsers: List<String> = emptyList(),
    val pendingFriends: List<String> = emptyList(),
    val recentChats: List<String> = emptyList(),
    val dailyMessageCount: Int = 0,
    val lastMessageDate: Any? = null,
    val badges: List<String> = emptyList(),

    @get:PropertyName("isPremium")
    @set:PropertyName("isPremium")
    var isPremium: Boolean = false,

    @get:PropertyName("isDev")
    @set:PropertyName("isDev")
    var isDev: Boolean = false
)
