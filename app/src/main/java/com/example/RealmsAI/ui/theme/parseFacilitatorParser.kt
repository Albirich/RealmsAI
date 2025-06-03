package com.example.RealmsAI.ai

import org.json.JSONObject

/**
 * Pulls “notes” and “activeBots” out of the JSON
 * that OpenAI’s facilitator returns.
 */
fun parseFacilitatorJson(json: String): Pair<String, List<String>> {
    val obj   = JSONObject(json)
    val notes = obj.optString("notes", "")
    val bots  = mutableListOf<String>()
    obj.optJSONArray("activeBots")?.let { arr ->
        for (i in 0 until arr.length()) {
            arr.optString(i)?.let { bots.add(it) }
        }
    }
    return notes to bots
}
