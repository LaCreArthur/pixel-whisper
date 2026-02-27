package com.pixelwhisper

import android.content.Context
import android.util.Log
import com.google.mlkit.genai.proofreading.Proofreading
import com.google.mlkit.genai.proofreading.Proofreader
import com.google.mlkit.genai.proofreading.ProofreaderOptions
import com.google.mlkit.genai.proofreading.ProofreadingRequest
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class PolishEngine(context: Context) {

    companion object {
        private const val TAG = "PolishEngine"
        private const val FILLER_PROMPT =
            "Remove filler words (um, uh, like, you know, so, basically, actually, I mean) " +
            "from the following text. Fix any remaining grammar issues. " +
            "Return ONLY the cleaned text, nothing else:\n\n"
    }

    private var proofreader: Proofreader? = null
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

    suspend fun initialize() {
        checkProofreadingAvailability()
        checkPromptAvailability()
    }

    private suspend fun checkProofreadingAvailability() {
        proofreader?.let { client ->
            try {
                val status = suspendCancellableCoroutine { cont ->
                    client.checkFeatureStatus()
                        .addOnSuccessListener { cont.resume(it) }
                        .addOnFailureListener {
                            Log.e(TAG, "Proofreading status check failed", it)
                            cont.resume(-1)
                        }
                }
                when (status) {
                    0 -> { // AVAILABLE
                        proofreadingAvailable = true
                        client.prepareInferenceEngine()
                        Log.d(TAG, "Proofreading available and warmed up")
                    }
                    1 -> { // DOWNLOADABLE
                        Log.d(TAG, "Proofreading model downloading...")
                        client.downloadFeature { }
                    }
                    else -> Log.w(TAG, "Proofreading unavailable (status=$status)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Proofreading init error", e)
            }
        }
    }

    private suspend fun checkPromptAvailability() {
        try {
            val model = Generation.getClient()
            // Prompt API availability check is implicit — if getClient() succeeds
            // and the device supports it, it works. We'll catch errors at inference time.
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
        if (promptAvailable) {
            result = runFillerRemoval(result)
        }

        Log.d(TAG, "Polish complete: '$rawText' -> '$result'")
        result
    }

    private suspend fun runProofreading(text: String): String {
        return try {
            suspendCancellableCoroutine { cont ->
                val request = ProofreadingRequest.builder(text).build()
                proofreader!!.runInference(request)
                    .addOnSuccessListener { result ->
                        val proofread = result.toString()
                        cont.resume(proofread.ifBlank { text })
                    }
                    .addOnFailureListener {
                        Log.e(TAG, "Proofreading failed", it)
                        cont.resume(text)
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Proofreading exception", e)
            text
        }
    }

    private suspend fun runFillerRemoval(text: String): String {
        return try {
            val model = Generation.getClient()
            val response = suspendCancellableCoroutine { cont ->
                model.generateContent("$FILLER_PROMPT$text")
                    .addOnSuccessListener { result ->
                        cont.resume(result.text ?: text)
                    }
                    .addOnFailureListener {
                        Log.e(TAG, "Prompt API failed", it)
                        cont.resume(text)
                    }
            }
            response.ifBlank { text }
        } catch (e: Exception) {
            Log.e(TAG, "Filler removal exception", e)
            text
        }
    }

    fun release() {
        proofreader?.close()
        proofreader = null
    }
}
