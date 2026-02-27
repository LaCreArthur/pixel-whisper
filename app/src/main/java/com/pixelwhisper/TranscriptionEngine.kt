package com.pixelwhisper

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import ai.moonshine.voice.Transcriber
import ai.moonshine.voice.JNI
import ai.moonshine.voice.TranscriptEventListener
import ai.moonshine.voice.TranscriptEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class TranscriptionEngine(private val context: Context) {

    companion object {
        private const val TAG = "TranscriptionEngine"
        private const val SAMPLE_RATE = 16000
        private const val MODEL_DIR = "base-en"
    }

    private var transcriber: Transcriber? = null
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val transcriptBuilder = StringBuilder()
    private var recordingThread: Thread? = null

    suspend fun initialize() = withContext(Dispatchers.IO) {
        val modelPath = copyModelToFiles()
        transcriber = Transcriber().apply {
            loadFromFiles(modelPath, JNI.MOONSHINE_MODEL_ARCH_BASE)
            addListener { event ->
                event.accept(object : TranscriptEventListener() {
                    override fun onLineTextChanged(event: TranscriptEvent.LineTextChanged) {
                        // Partial updates — we only care about completed lines
                    }

                    override fun onLineCompleted(event: TranscriptEvent.LineCompleted) {
                        val text = event.line.text.trim()
                        if (text.isNotEmpty()) {
                            synchronized(transcriptBuilder) {
                                if (transcriptBuilder.isNotEmpty()) transcriptBuilder.append(" ")
                                transcriptBuilder.append(text)
                            }
                        }
                    }

                    override fun onError(event: TranscriptEvent.Error) {
                        Log.e(TAG, "Transcription error", event.cause)
                    }
                })
            }
        }
        Log.d(TAG, "Moonshine model loaded from $modelPath")
    }

    fun start() {
        if (isRecording) return

        synchronized(transcriptBuilder) {
            transcriptBuilder.clear()
        }

        transcriber?.start()

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
            val floatBuffer = FloatArray(buffer.size)

            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0) {
                    for (i in 0 until read) {
                        floatBuffer[i] = buffer[i] / 32768.0f
                    }
                    transcriber?.addAudio(floatBuffer.copyOf(read), SAMPLE_RATE)
                }
            }
        }.apply {
            name = "PixelWhisper-AudioCapture"
            start()
        }

        Log.d(TAG, "Recording started")
    }

    fun stop(): String {
        if (!isRecording) return ""

        isRecording = false
        recordingThread?.join(2000)
        recordingThread = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        transcriber?.stop()

        val result = synchronized(transcriptBuilder) {
            transcriptBuilder.toString()
        }
        Log.d(TAG, "Recording stopped. Transcript: $result")
        return result
    }

    fun release() {
        if (isRecording) stop()
        transcriber?.removeAllListeners()
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
