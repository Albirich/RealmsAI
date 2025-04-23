package com.example.emotichat

import android.net.Uri

data class EmotionSlot(
    val key: String,
    var uri: Uri? = null
)
