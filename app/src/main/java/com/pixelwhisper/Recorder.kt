package com.pixelwhisper

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlin.math.log10
import kotlin.math.sqrt

const val SAMPLE_RATE = 16_000
private const val SILENCE_STOP = 10L * SAMPLE_RATE

/** Mic capture cut into speech segments by Silero VAD. One recording at a time, on the caller's thread. */
class Recorder(private val vadModel: String) {
    enum class End { STOPPED, SILENCE, MIC_BUSY }

    private var vad: Vad? = null

    /**
     * Records until [keepGoing] is false or 10 s pass without speech, and passes each closed speech
     * segment to [onSegment]. At the end the unfinished segment is flushed too, unless [discard].
     */
    @SuppressLint("MissingPermission") // granted by the setup script, checked by the caller
    fun record(keepGoing: () -> Boolean, discard: () -> Boolean, onLevel: (Float) -> Unit, onSegment: (FloatArray) -> Unit): End {
        val vad = vad ?: Vad(
            null,
            VadModelConfig(
                // 0.8 s of silence closes a segment (best Whisper accuracy in Leg 0); 20 s stays under Whisper's 30 s window.
                sileroVadModelConfig = SileroVadModelConfig(model = vadModel, minSilenceDuration = 0.8f, maxSpeechDuration = 20f),
            ),
        ).also { vad = it }
        vad.reset()
        val record = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(
                    AudioFormat.Builder().setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build(),
                )
                .setBufferSizeInBytes(SAMPLE_RATE * 4 * 2)
                .build()
        } catch (e: UnsupportedOperationException) {
            return End.MIC_BUSY
        }
        var end = End.STOPPED
        try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) return End.MIC_BUSY
            val buf = FloatArray(512)
            var total = 0L
            var lastSpeech = 0L
            var checked = false
            while (keepGoing()) {
                val n = record.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING)
                if (n < 0) break
                val chunk = if (n == buf.size) buf else buf.copyOf(n)
                onLevel(level(chunk))
                vad.acceptWaveform(chunk)
                drain(vad, onSegment)
                total += n
                if (vad.isSpeechDetected()) lastSpeech = total
                if (!checked && total >= SAMPLE_RATE / 5) {
                    checked = true
                    // Another app holds the mic: we would only get silence.
                    if (record.activeRecordingConfiguration?.isClientSilenced == true) return End.MIC_BUSY
                }
                if (total - lastSpeech > SILENCE_STOP) {
                    end = End.SILENCE
                    break
                }
            }
        } finally {
            record.stop()
            record.release()
        }
        if (!discard()) {
            vad.flush()
            drain(vad, onSegment)
        }
        return end
    }

    fun release() {
        vad?.release()
        vad = null
    }

    private fun drain(vad: Vad, onSegment: (FloatArray) -> Unit) {
        while (!vad.empty()) {
            onSegment(vad.front().samples)
            vad.pop()
        }
    }

    /** RMS level mapped from -55..-15 dBFS to 0..1, for the orb's pulse. */
    private fun level(chunk: FloatArray): Float {
        var sum = 0f
        for (x in chunk) sum += x * x
        val db = 20 * log10(sqrt(sum / chunk.size) + 1e-6f)
        return ((db + 55f) / 40f).coerceIn(0f, 1f)
    }
}
