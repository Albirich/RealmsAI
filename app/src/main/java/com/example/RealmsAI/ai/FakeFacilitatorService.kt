package com.example.RealmsAI.ai

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Returns a fake facilitator JSON payload with "notes" and "activeBots".
 */
object FakeFacilitatorService {
    private var idx = 0
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val templateResponses = listOf(
        mapOf(
            "notes"      to "Facilitator round 1 notes",
            "activeBots" to listOf("B1","B2")
        ),
        mapOf(
            "notes"      to "Facilitator round 2 notes",
            "activeBots" to listOf("B2","B3")
        ),
        mapOf(
            "notes"      to "Facilitator round 3 notes",
            "activeBots" to listOf("B1","B3")
        )
    )

    /**
     * Ignores prompt for now; returns a rotating JSON string.
     */
    fun getResponse(facilitatorPrompt: String): String {
        val payload = templateResponses[idx % templateResponses.size]
        idx++
        val adapter = moshi.adapter(Map::class.java)
        return adapter.toJson(payload)    // e.g. {"notes":"…","activeBots":["B1","B2"]}
    }
}
