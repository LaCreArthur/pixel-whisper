package com.pixelwhisper

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.InputMethod
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import java.util.concurrent.Executors

const val TAG = "PixelWhisper"
private const val ZERO_WIDTH = "​‌‍⁠﻿"

/**
 * The whole app at runtime. Shows the orb while an editable, non-password field has focus,
 * records on demand, transcribes on the phone and types the text at the cursor. If the field
 * changed, the screen went off or a call started, the text goes to the clipboard instead.
 */
class DictationService : AccessibilityService(), OrbView.Listener {
    /** One dictation, from press to result. Flags are written on main and read by the audio and worker threads. */
    private class Session(val inputGen: Int, val connection: InputMethod.AccessibilityInputConnection?) {
        @Volatile var recording = true
        @Volatile var cancelled = false
        @Volatile var toClipboard = false
        val texts = ArrayList<String>() // worker thread only
        var stoppedAt = 0L
    }

    private val main = Handler(Looper.getMainLooper())
    private val audio = Executors.newSingleThreadExecutor()
    private val recorder by lazy { Recorder(Models.path(this, Models.VAD)) }
    private val transcriber by lazy {
        Transcriber(Models.path(this, Models.ENCODER), Models.path(this, Models.DECODER), Models.path(this, Models.TOKENS))
    }
    private val orb by lazy { OrbView(this, this) }
    private var attached = false
    private var shown = false
    private var session: Session? = null // non-null from press until the result
    private var inputGen = 0 // bumped on every input start or finish: any change during a session = field changed
    private var editing = false
    private var pending: String? = null // committed text waiting for its selection update
    private val commitTimeout = Runnable { pending?.let { copy(it) }; pending = null }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = interrupt()
    }
    private val callListener = AudioManager.OnModeChangedListener { mode ->
        if (mode != AudioManager.MODE_NORMAL) interrupt()
    }

    override fun onCreateInputMethod(): InputMethod = object : InputMethod(this) {
        override fun onStartInput(attribute: EditorInfo, restarting: Boolean) =
            fieldChanged(currentInputConnection != null && accepts(attribute.inputType))

        override fun onFinishInput() = fieldChanged(false)

        override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
            if (pending == null) return
            pending = null
            main.removeCallbacks(commitTimeout)
            flash(OrbView.Mode.DONE)
        }
    }

    override fun onServiceConnected() {
        orb.attach()
        attached = true
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        getSystemService(AudioManager::class.java).addOnModeChangedListener(mainExecutor, callListener)
        refresh()
    }

    override fun onDestroy() {
        session?.let { it.cancelled = true; it.recording = false }
        if (attached) {
            orb.detach()
            unregisterReceiver(screenOff)
            getSystemService(AudioManager::class.java).removeOnModeChangedListener(callListener)
        }
        transcriber.queue { release() }
        transcriber.shutdown()
        audio.execute { recorder.release() }
        audio.shutdown()
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        if (level >= TRIM_MEMORY_BACKGROUND && session == null) transcriber.queue { release() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (attached) orb.place()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}

    override fun onInterrupt() {}

    // Orb gestures.

    override fun onStart() {
        if (session != null) return
        val missing = Models.missing(this)
        if (missing.isNotEmpty() || checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            orb.flash(OrbView.Mode.ERROR)
            toast(if (missing.isNotEmpty()) "Speech models missing. Run the setup script." else "No mic permission. Run the setup script.")
            return
        }
        val s = Session(inputGen, inputMethod?.currentInputConnection)
        session = s
        orb.setMode(OrbView.Mode.LISTENING)
        orb.tick()
        transcriber.queue { load() }
        audio.execute { record(s) }
    }

    override fun onStop() {
        val s = session ?: return
        if (s.recording) working(s)
    }

    override fun onCancel() {
        val s = session ?: return
        s.cancelled = true
        s.recording = false
        session = null
        orb.setMode(OrbView.Mode.IDLE)
        orb.tick()
        refresh()
    }

    // Pipeline.

    /** Audio thread: records, queues each speech segment for transcription, then queues the result. */
    private fun record(s: Session) {
        val end = recorder.record(
            keepGoing = { s.recording },
            discard = { s.cancelled },
            onLevel = { level -> main.post { if (session === s && orb.mode == OrbView.Mode.LISTENING) orb.level = level } },
            onSegment = { samples -> transcriber.queue { if (!s.cancelled) decode(samples).takeIf { it.isNotEmpty() }?.let { s.texts += it } } },
        )
        main.post { recordingEnded(s, end) }
        if (s.cancelled || end == Recorder.End.MIC_BUSY) return
        // Read the character before the cursor while the last segment is transcribed.
        val before = if (s.toClipboard) null else charBeforeCursor(s.connection)
        transcriber.queue {
            val text = s.texts.joinToString(" ")
            main.post { finish(s, text, before) }
        }
    }

    private fun recordingEnded(s: Session, end: Recorder.End) {
        if (session !== s) return
        when (end) {
            Recorder.End.MIC_BUSY -> {
                session = null
                orb.flash(OrbView.Mode.ERROR)
                toast("Mic busy")
                refresh()
            }
            Recorder.End.SILENCE -> if (s.recording) working(s)
            Recorder.End.STOPPED -> {}
        }
    }

    private fun working(s: Session) {
        s.recording = false
        s.stoppedAt = SystemClock.elapsedRealtime()
        orb.setMode(OrbView.Mode.WORKING)
        orb.tick()
    }

    private fun finish(s: Session, text: String, before: Char?) {
        if (session !== s) return
        session = null
        val fieldChanged = s.inputGen != inputGen
        Log.i(TAG, "stop-to-text ${SystemClock.elapsedRealtime() - s.stoppedAt} ms, ${text.length} chars, interrupted=${s.toClipboard}, fieldChanged=$fieldChanged")
        val connection = s.connection
        when {
            text.isEmpty() -> orb.flash(OrbView.Mode.NOTHING)
            s.toClipboard || fieldChanged || connection == null -> copy(text)
            else -> {
                val space = if (before == null || before.isWhitespace() || before in ZERO_WIDTH) "" else " "
                connection.commitText(space + text, 1, null)
                pending = text
                main.postDelayed(commitTimeout, 500)
            }
        }
        refresh()
    }

    private fun charBeforeCursor(connection: InputMethod.AccessibilityInputConnection?): Char? {
        val st = connection?.getSurroundingText(1, 0, 0) ?: return null
        return st.text.getOrNull(st.selectionStart - 1)
    }

    // Field tracking and interruptions.

    private fun fieldChanged(editable: Boolean) {
        inputGen++
        editing = editable
        interrupt()
        refresh()
    }

    /** Field change, screen off or call: stop listening, and the text goes to the clipboard. */
    private fun interrupt() {
        val s = session ?: return
        s.toClipboard = true
        if (s.recording) working(s)
    }

    private fun refresh() {
        if (!attached) return
        val show = editing || session != null
        // Load on the first appearance (and again after a memory release), never at service start.
        if (show && !shown && Models.missing(this).isEmpty()) transcriber.queue { load() }
        shown = show
        orb.setShown(show)
    }

    private fun accepts(type: Int): Boolean {
        val variation = type and InputType.TYPE_MASK_VARIATION
        return when (type and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_NULL -> false
            InputType.TYPE_CLASS_TEXT -> variation != InputType.TYPE_TEXT_VARIATION_PASSWORD &&
                variation != InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD &&
                variation != InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation != InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> true
        }
    }

    private fun copy(text: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("PixelWhisper", text))
        flash(OrbView.Mode.DONE)
        toast("Copied, paste it")
    }

    /** Result states never cover a newer dictation. */
    private fun flash(mode: OrbView.Mode) {
        if (session == null) orb.flash(mode)
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
