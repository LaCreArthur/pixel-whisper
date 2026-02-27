package com.pixelwhisper

import android.content.Context
import android.util.Log
import com.google.mlkit.genai.proofreading.Proofreading
import com.google.mlkit.genai.proofreading.Proofreader
import com.google.mlkit.genai.proofreading.ProofreaderOptions
import com.google.mlkit.genai.proofreading.ProofreadingRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class PolishEngine(context: Context) {

    companion object {
        private const val TAG = "PolishEngine"
        private const val FILLER_PROMPT =
            "Remove filler words (um, uh, like, you know, so, basically, actually, I mean) " +
            "from the following text. Fix any remaining grammar issues. " +
            "Return ONLY the cleaned text, nothing else:\n\n"
    }

    private var proofreader: Proofreader? = null
    private var promptModel: GenerativeModel? = null
    private var proofreadingAvailable = false
    private var promptAvailable = false

    init {
        try {
            proofreader = Proofreading.getClient(
                ProofreaderOptions.builder(context)
                    .setInputType(ProofreaderOptions.InputType.VOICE)
                    .setLanguage(ProofreaderOptions.Language.ENGLISH)
                    .build()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create proofreader", e)
        }
    }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        checkProofreadingAvailability()
        checkPromptAvailability()
    }

    private fun checkProofreadingAvailability() {
        proofreader?.let { client ->
            try {
                val status = client.checkFeatureStatus().get(10, TimeUnit.SECONDS)
                when (status) {
                    0 -> { // AVAILABLE
                        proofreadingAvailable = true
                        client.prepareInferenceEngine()
                        Log.d(TAG, "Proofreading available and warmed up")
                    }
                    1 -> { // DOWNLOADABLE
                        Log.d(TAG, "Proofreading model downloading...")
                        // Download will happen in background; not available yet
                    }
                    else -> Log.w(TAG, "Proofreading unavailable (status=$status)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Proofreading init error", e)
            }
        }
    }

    private fun checkPromptAvailability() {
        try {
            promptModel = Generation.getClient()
            promptAvailable = true
            Log.d(TAG, "Prompt API client created")
        } catch (e: Exception) {
            Log.w(TAG, "Prompt API unavailable", e)
            promptAvailable = false
        }
    }

    suspend fun polish(rawText: String): String = withContext(Dispatchers.IO) {
        if (rawText.isBlank()) return@withContext rawText

        var result = rawText

        // Stage 1: Proofreading (grammar + punctuation)
        if (proofreadingAvailable && proofreader != null) {
            result = runProofreading(result)
        }

        // Stage 2: Filler removal via Prompt API
        if (promptAvailable && promptModel != null) {
            result = runFillerRemoval(result)
        }

        Log.d(TAG, "Polish complete: '$rawText' -> '$result'")
        result
    }

    private fun runProofreading(text: String): String {
        return try {
            val request = ProofreadingRequest.builder(text).build()
            val result = proofreader!!.runInference(request).get(10, TimeUnit.SECONDS)
            val proofread = result.toString()
            proofread.ifBlank { text }
        } catch (e: Exception) {
            Log.e(TAG, "Proofreading failed", e)
            text
        }
    }

    private suspend fun runFillerRemoval(text: String): String {
        return try {
            val response = promptModel!!.generateContent("$FILLER_PROMPT$text")
            response.text?.ifBlank { text } ?: text
        } catch (e: Exception) {
            Log.e(TAG, "Filler removal failed", e)
            text
        }
    }

    fun release() {
        proofreader?.close()
        proofreader = null
        promptModel = null
    }
}
