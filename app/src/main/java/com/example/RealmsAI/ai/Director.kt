package com.example.RealmsAI.ai

import android.util.Log
import com.example.RealmsAI.network.ApiClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Director {

    /**
     * Standard OpenAI Chat Completion (Used for SFW Roleplay & Logic)
     */
    suspend fun callOpenAiApi(prompt: String, apiKey: String): String = withContext(Dispatchers.IO) {
        try {
            Log.d("Director", "[OpenAI] Prompt:\n$prompt")
            val request = com.example.RealmsAI.network.OpenAiChatRequest(
                model = "gpt-4o-mini", // Updated to the latest cost-effective model
                messages = listOf(
                    com.example.RealmsAI.network.Message(
                        role = "system",
                        content = prompt
                    )
                )
            )
            val response = ApiClients.openai.getFacilitatorNotes(request)
            response.choices.firstOrNull()?.message?.content ?: ""
        } catch (ex: Exception) {
            Log.e("Director", "Error calling OpenAI API", ex)
            ""
        }
    }

    /**
     * Mixtral API (Used for NSFW / Unfiltered Roleplay)
     */
    suspend fun callMixtralApi(prompt: String, apiKey: String): String = withContext(Dispatchers.IO) {
        try {
            Log.d("Director", "[Mixtral] Prompt:\n$prompt")
            val engine = com.example.RealmsAI.network.MixtralEngine(ApiClients.mixtral)
            engine.getBotOutput(prompt)
        } catch (ex: Exception) {
            Log.e("Director", "Error calling Mixtral API", ex)
            ""
        }
    }

    // ============================================================================================
    //  RAG MEMORY SYSTEM (The "Brain")
    // ============================================================================================

    /**
     * Converts text into a list of numbers (Vector) using OpenAI's embedding model.
     * Cost: Extremely low (~$0.00002 per 1k tokens).
     */
    suspend fun getEmbedding(text: String, apiKey: String): List<Double> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.openai.com/v1/embeddings")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            // Input: The text to memorize
            // Model: text-embedding-3-small (Fastest & Cheapest)
            val jsonBody = JSONObject().apply {
                put("input", text)
                put("model", "text-embedding-3-small")
            }

            connection.outputStream.use { it.write(jsonBody.toString().toByteArray()) }

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val responseString = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseString)
                val data = json.getJSONArray("data")
                val embeddingArray = data.getJSONObject(0).getJSONArray("embedding")

                // Convert JSON array to Kotlin List
                val vector = mutableListOf<Double>()
                for (i in 0 until embeddingArray.length()) {
                    vector.add(embeddingArray.getDouble(i))
                }
                vector
            } else {
                val errorMsg = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e("Director", "Embedding Error $responseCode: $errorMsg")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("Director", "Embedding Exception", e)
            emptyList()
        }
    }

    /**
     * Calculates similarity between two memory vectors (0.0 = unrelated, 1.0 = exact match).
     */
    fun cosineSimilarity(vecA: List<Double>, vecB: List<Double>): Double {
        if (vecA.size != vecB.size || vecA.isEmpty()) return 0.0
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in vecA.indices) {
            dot += vecA[i] * vecB[i]
            normA += vecA[i] * vecA[i]
            normB += vecB[i] * vecB[i]
        }
        return if (normA == 0.0 || normB == 0.0) 0.0 else dot / (Math.sqrt(normA) * Math.sqrt(normB))
    }
}