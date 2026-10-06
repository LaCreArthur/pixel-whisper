package com.pixelwhisper

import android.os.SystemClock
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.util.concurrent.Executors

/**
 * Whisper large-v3-turbo int8 on one worker thread. Loading, segment decodes and session ends
 * all go through [queue], so they run in submission order.
 */
class Transcriber(private val encoder: String, private val decoder: String, private val tokens: String) {
    private val worker = Executors.newSingleThreadExecutor()
    private var recognizer: OfflineRecognizer? = null

    fun queue(task: Transcriber.() -> Unit) = worker.execute { task() }

    fun shutdown() = worker.shutdown()

    // Worker thread only below.

    fun load(): OfflineRecognizer = recognizer ?: run {
        val t0 = SystemClock.elapsedRealtime()
        val config = OfflineRecognizerConfig(
            modelConfig = OfflineModelConfig(
                // language "" = detect per segment (French, English or both).
                whisper = OfflineWhisperModelConfig(encoder = encoder, decoder = decoder, language = "", task = "transcribe"),
                tokens = tokens,
                modelType = "whisper",
                numThreads = 6,
            ),
        )
        OfflineRecognizer(null, config).also {
            recognizer = it
            Log.i(TAG, "Whisper loaded in ${SystemClock.elapsedRealtime() - t0} ms")
        }
    }

    fun decode(samples: FloatArray): String {
        val rec = load()
        val t0 = SystemClock.elapsedRealtime()
        val stream = rec.createStream()
        stream.acceptWaveform(samples, SAMPLE_RATE)
        rec.decode(stream)
        val text = rec.getResult(stream).text.trim()
        stream.release()
        Log.i(TAG, "segment %.1f s decoded in %d ms".format(samples.size / SAMPLE_RATE.toFloat(), SystemClock.elapsedRealtime() - t0))
        return text
    }

    fun release() {
        recognizer?.release()
        recognizer = null
    }
}
