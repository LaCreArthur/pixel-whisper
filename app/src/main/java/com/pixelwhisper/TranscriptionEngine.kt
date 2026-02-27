package com.pixelwhisper

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import ai.moonshine.voice.Transcriber
import ai.moonshine.voice.JNI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TranscriptionEngine(private val context: Context) {

    companion object {
        private const val TAG = "TranscriptionEngine"
        private const val SAMPLE_RATE = 16000
        private const val MODEL_DIR = "medium-streaming-en"
        // Max 60 seconds of audio (16kHz mono float32)
        private const val MAX_SAMPLES = SAMPLE_RATE * 60
    }

    private var transcriber: Transcriber? = null
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null

    // Buffer all audio for batch transcription
    private var audioBuffer = FloatArray(MAX_SAMPLES)
    private var audioBufferPos = 0

    suspend fun initialize() = withContext(Dispatchers.IO) {
        val modelPath = copyModelToFiles()
        transcriber = Transcriber().apply {
            loadFromFiles(modelPath, JNI.MOONSHINE_MODEL_ARCH_MEDIUM_STREAMING)
        }
        Log.d(TAG, "Moonshine model loaded from $modelPath")
    }

    fun start() {
        if (isRecording) return

        audioBufferPos = 0

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2
        )

        audioRecord?.startRecording()
        isRecording = true

        recordingThread = Thread {
            val buffer = ShortArray(bufferSize / 2)
            var chunkCount = 0

            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0) {
                    // Convert to float and append to buffer
                    val remaining = MAX_SAMPLES - audioBufferPos
                    val toWrite = minOf(read, remaining)
                    for (i in 0 until toWrite) {
                        audioBuffer[audioBufferPos + i] = buffer[i] / 32768.0f
                    }
                    audioBufferPos += toWrite

                    if (chunkCount++ % 50 == 0) {
                        var maxAmp: Short = 0
                        for (i in 0 until read) {
                            if (buffer[i] > maxAmp) maxAmp = buffer[i]
                        }
                        Log.d(TAG, "Audio chunk #$chunkCount read=$read maxAmp=$maxAmp buffered=${audioBufferPos}/${MAX_SAMPLES}")
                    }

                    if (audioBufferPos >= MAX_SAMPLES) {
                        Log.w(TAG, "Audio buffer full (60s), stopping capture")
                        break
                    }
                }
            }
        }.apply {
            name = "PixelWhisper-AudioCapture"
            start()
        }

        Log.d(TAG, "Recording started (batch mode)")
    }

    fun stop(): String {
        if (!isRecording) return ""

        isRecording = false
        recordingThread?.join(2000)
        recordingThread = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        if (audioBufferPos == 0) {
            Log.d(TAG, "No audio captured")
            return ""
        }

        val samples = audioBuffer.copyOf(audioBufferPos)
        val durationSec = audioBufferPos.toFloat() / SAMPLE_RATE
        Log.d(TAG, "Batch transcribing ${audioBufferPos} samples (${String.format("%.1f", durationSec)}s)")

        val result = try {
            val transcript = transcriber?.transcribeWithoutStreaming(samples, SAMPLE_RATE)
            transcript?.text()?.trim().orEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Batch transcription failed", e)
            ""
        }

        Log.d(TAG, "Transcript: '$result'")
        return result
    }

    fun release() {
        if (isRecording) stop()
        transcriber = null
    }

    private fun copyModelToFiles(): String {
        val outDir = File(context.filesDir, MODEL_DIR)
        if (outDir.exists() && outDir.listFiles()?.isNotEmpty() == true) {
            return outDir.absolutePath
        }
        outDir.mkdirs()
        context.assets.list(MODEL_DIR)?.forEach { fileName ->
            context.assets.open("$MODEL_DIR/$fileName").use { input ->
                File(outDir, fileName).outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return outDir.absolutePath
    }
}
