package com.example.RealmsAI.models

data class Outfit(
    var name: String,
    var uri: String? = null,                   // if you still want a “main” image
    var poseUris: Map<String, String> = emptyMap()
) {
    // Firestore needs this
    @Suppress("unused")    // called via reflection
    constructor() : this(
        name    = "",
        poseUris = emptyMap()
    )
}
