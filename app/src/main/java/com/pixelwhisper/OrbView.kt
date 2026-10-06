package com.pixelwhisper

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import kotlin.math.hypot

private const val HOLD_MS = 300L
private const val FLASH_MS = 1200L

/**
 * The mic button: one circle in its own accessibility overlay window. The window never takes
 * focus, so a tap does not end the text field's input.
 * Gestures: press from idle starts recording at once; release before 300 ms = toggle (the next tap
 * stops), hold = push-to-talk; moving before 300 ms drags the orb instead (the recording is
 * cancelled); releasing more than a quarter screen away while recording cancels.
 */
@SuppressLint("ViewConstructor")
class OrbView(context: Context, private val listener: Listener) : View(context) {
    interface Listener {
        fun onStart()
        fun onStop()
        fun onCancel()
    }

    enum class Mode(val color: Int, val glyph: String?) {
        IDLE(0xCC4A90D9.toInt(), null),
        LISTENING(0xFFE53935.toInt(), null),
        WORKING(0xFF4A90D9.toInt(), null),
        DONE(0xFF43A047.toInt(), "✓"),
        NOTHING(0xFF757575.toInt(), "?"),
        ERROR(0xFFFB8C00.toInt(), "!"),
        CANCEL(0xFF757575.toInt(), "✕"),
    }

    var mode = Mode.IDLE
        private set
    var level = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val sizePx = dp(64)
    private val radius = dp(24).toFloat()
    private val wm = context.getSystemService(WindowManager::class.java)
    private val prefs = context.getSharedPreferences("orb", Context.MODE_PRIVATE)
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        textSize = dp(22).toFloat()
    }
    private val mic = context.getDrawable(R.drawable.ic_mic)!!
    private val unflash = Runnable { setMode(Mode.IDLE) }
    private val lp = WindowManager.LayoutParams(
        sizePx, sizePx,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }

    // Current gesture.
    private var downX = 0f
    private var downY = 0f
    private var downT = 0L
    private var grabX = 0
    private var grabY = 0
    private var tracking = false
    private var startedHere = false
    private var dragging = false

    fun attach() {
        visibility = GONE
        place()
        wm.addView(this, lp)
    }

    fun detach() = wm.removeView(this)

    fun setShown(shown: Boolean) {
        visibility = if (shown) VISIBLE else GONE
    }

    fun setMode(m: Mode) {
        removeCallbacks(unflash)
        mode = m
        if (m != Mode.LISTENING) level = 0f
        invalidate()
    }

    /** Shows a result state briefly, then idle. */
    fun flash(m: Mode) {
        setMode(m)
        postDelayed(unflash, FLASH_MS)
    }

    fun tick() = performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

    /** Puts the orb on its saved edge and height fraction for the current screen (also after rotation). */
    fun place() {
        val screen = wm.currentWindowMetrics.bounds
        lp.x = if (prefs.getBoolean("right", true)) screen.width() - sizePx else 0
        lp.y = ((screen.height() - sizePx) * prefs.getFloat("y", 0.35f)).toInt()
        if (isAttachedToWindow) wm.updateViewLayout(this, lp)
    }

    private fun snap() {
        val screen = wm.currentWindowMetrics.bounds
        prefs.edit()
            .putBoolean("right", lp.x + sizePx / 2 > screen.width() / 2)
            .putFloat("y", (lp.y.toFloat() / (screen.height() - sizePx)).coerceIn(0f, 1f))
            .apply()
        place()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val dist = hypot(e.rawX - downX, e.rawY - downY)
        val early = e.eventTime - downT < HOLD_MS
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX
                downY = e.rawY
                downT = e.eventTime
                grabX = lp.x
                grabY = lp.y
                dragging = false
                tracking = mode != Mode.WORKING
                startedHere = tracking && mode != Mode.LISTENING
                if (startedHere) listener.onStart()
            }
            MotionEvent.ACTION_MOVE -> if (tracking) {
                if (startedHere && !dragging && early && dist > slop) {
                    dragging = true
                    if (mode == Mode.LISTENING) listener.onCancel()
                }
                if (dragging) {
                    lp.x = grabX + (e.rawX - downX).toInt()
                    lp.y = grabY + (e.rawY - downY).toInt()
                    wm.updateViewLayout(this, lp)
                } else if (mode == Mode.LISTENING || mode == Mode.CANCEL) {
                    setMode(if (dist > wm.currentWindowMetrics.bounds.width() / 4) Mode.CANCEL else Mode.LISTENING)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (tracking) {
                tracking = false
                when {
                    dragging -> snap()
                    mode == Mode.CANCEL -> listener.onCancel()
                    mode != Mode.LISTENING -> {}
                    startedHere && early -> {} // a tap: keep recording until the next tap
                    else -> listener.onStop()
                }
            }
        }
        return true
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // Dragging from the screen edge must move the orb, not trigger the back gesture.
        systemGestureExclusionRects = listOf(Rect(0, 0, width, height))
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        fill.color = mode.color
        if (mode == Mode.LISTENING) {
            fill.alpha = 80
            canvas.drawCircle(cx, cy, radius + dp(8) * level, fill)
            fill.alpha = 255
        }
        canvas.drawCircle(cx, cy, radius, fill)
        val glyph = mode.glyph
        when {
            glyph != null -> {
                ink.style = Paint.Style.FILL
                canvas.drawText(glyph, cx, cy - (ink.descent() + ink.ascent()) / 2, ink)
            }
            mode == Mode.WORKING -> {
                // 1 to 3 dots, redrawn 3 times per second: a per-frame spinner slowed transcription by ~30% (Leg 1).
                ink.style = Paint.Style.FILL
                val dots = 1 + (SystemClock.uptimeMillis() / 330 % 3).toInt()
                for (i in 0 until dots) canvas.drawCircle(cx + (i - 1) * dp(9), cy, dp(3).toFloat(), ink)
                postInvalidateDelayed(330)
            }
            else -> {
                val h = dp(14)
                mic.setBounds((cx - h).toInt(), (cy - h).toInt(), (cx + h).toInt(), (cy + h).toInt())
                mic.draw(canvas)
            }
        }
    }

    private fun dp(v: Int) = (v * density).toInt()
}
