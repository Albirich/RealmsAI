package com.albirich.RealmsAI.network

import android.util.Log
import com.albirich.RealmsAI.ai.AiResponseData // Make sure to import your new data class!

class MancerEngine(
    private val Mancer: MancerApiService
) {
    /**
     * Send a single-system-message prompt to Mancer and return the assistant’s reply.
     * NOW ACCEPTS 'modelKey' AND RETURNS AiResponseData
     */
    suspend fun getBotOutput(prompt: String, modelKey: String = "grok4.1"): AiResponseData {

        // Map friendly ID to actual API String
        val apiModelString = when (modelKey) {
            "deepseek"      -> "deepseek/deepseek-v4-flash"
            "grok4.1"       -> "x-ai/grok-4.1-fast"
            "openai"        -> "openai/gpt-oss-120b"
            "gemini"        -> "google/gemini-2.5-flash-lite"
            "step3.5"       -> "stepfun/step-3.5-flash:free"
            "z-ai"          -> "z-ai/glm-5.1"
            "acree"         -> "arcee-ai/trinity-mini:free"
            "nemo"          -> "mistralai/mistral-nemo"
            "xiaomi"        -> "xiaomi/mimo-v2-flash"
            "gemini3.1"     -> "google/gemini-3.1-flash-lite-preview"
            "mistral_small" -> "mistralai/mistral-small-2603"
            "hunter_alpha"  -> "openrouter/healer-alpha"
            "mistral_med"   -> "mistralai/mistral-medium-3.1"
            "moonshotai"    -> "moonshotai/kimi-k2.5"
            "minimax"       -> "minimax/minimax-m2.7"
            "claude"        -> "anthropic/claude-3-haiku"
            "qwen"          -> "qwen/qwen3.5-9b"
            "nvidia"        -> "nvidia/nemotron-3-super-120b-a12b:free"
            else            -> "nvidia/nemotron-3-super-120b-a12b:free" // Default Fallback
        }

        val req = MancerChatRequest(
            model    = apiModelString,
            messages = listOf(
                Message(role = "system", content = prompt)
            ),
            temperature = 0.7
        )

        return try {
            val resp = Mancer.getBotResponses(req)

            // 1. Grab the text
            val content = resp.choices.firstOrNull()?.message?.content ?: ""

            // 2. Grab the exact tokens from the API!
            val tokens = resp.usage?.totalTokens ?: 0L

            // 3. Return the bucket
            AiResponseData(content, tokens)

        } catch (e: Exception) {
            Log.e("MancerEngine", "API Call Failed", e)
            // If it crashes or times out, return null content and 0 tokens
            AiResponseData(null, 0L)
        }
    }
}