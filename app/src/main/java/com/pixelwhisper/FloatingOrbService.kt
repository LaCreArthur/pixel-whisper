package com.pixelwhisper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingOrbService : Service(),
    androidx.lifecycle.LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    companion object {
        private const val TAG = "FloatingOrbService"
        private const val CHANNEL_ID = "pixel_whisper_channel"
        private const val NOTIFICATION_ID = 1
    }

    enum class OrbState { IDLE, RECORDING, PROCESSING }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val _viewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = _viewModelStore
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private lateinit var layoutParams: WindowManager.LayoutParams

    private lateinit var transcriptionEngine: TranscriptionEngine
    private lateinit var polishEngine: PolishEngine

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var orbState by mutableStateOf(OrbState.IDLE)
    private var statusText by mutableStateOf("")

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        transcriptionEngine = TranscriptionEngine(this)
        polishEngine = PolishEngine(this)

        serviceScope.launch {
            try {
                statusText = "Loading model..."
                transcriptionEngine.initialize()
                polishEngine.initialize()
                statusText = ""
                Log.d(TAG, "Engines initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Engine init failed", e)
                statusText = "Init failed"
            }
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        setupOverlay()

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        val overlayContext = createDisplayContext(displayManager.displays[0])
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)

        composeView = ComposeView(overlayContext).apply {
            setViewTreeLifecycleOwner(this@FloatingOrbService)
            setViewTreeViewModelStoreOwner(this@FloatingOrbService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOrbService)

            setContent {
                OrbUI()
            }
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 300
        }

        windowManager.addView(composeView, layoutParams)
    }

    @Composable
    private fun OrbUI() {
        val bgColor by animateColorAsState(
            targetValue = when (orbState) {
                OrbState.IDLE -> Color(0xFF4A90D9)
                OrbState.RECORDING -> Color(0xFFE53935)
                OrbState.PROCESSING -> Color(0xFFFFA726)
            },
            label = "orbColor"
        )

        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = if (orbState == OrbState.RECORDING) 1.15f else 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(600),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(bgColor)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            layoutParams.x -= dragAmount.x.toInt()
                            layoutParams.y += dragAmount.y.toInt()
                            windowManager.updateViewLayout(composeView, layoutParams)
                        }
                    }
                    .combinedClickable(
                        onClick = { onOrbTapped() },
                        onLongClick = { stopSelf() }
                    )
            ) {
                Icon(
                    imageVector = if (orbState == OrbState.RECORDING) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = "Toggle recording",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            if (statusText.isNotEmpty()) {
                Text(
                    text = statusText,
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .background(
                            Color.Black.copy(alpha = 0.7f),
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }

    private fun onOrbTapped() {
        when (orbState) {
            OrbState.IDLE -> {
                orbState = OrbState.RECORDING
                statusText = ""
                transcriptionEngine.start()
            }
            OrbState.RECORDING -> {
                orbState = OrbState.PROCESSING
                statusText = "Transcribing..."

                serviceScope.launch {
                    val transcript = withContext(Dispatchers.IO) {
                        transcriptionEngine.stop()
                    }

                    if (transcript.isBlank()) {
                        statusText = "No speech detected"
                        orbState = OrbState.IDLE
                        return@launch
                    }

                    val polishEnabled = getSharedPreferences("settings", MODE_PRIVATE)
                        .getBoolean("polish_enabled", true)

                    if (!polishEnabled) {
                        injectOrCopy(transcript)
                        statusText = ""
                        orbState = OrbState.IDLE
                        return@launch
                    }

                    // Launch transparent activity so AICore considers us foreground
                    ForegroundProxyActivity.launch(this@FloatingOrbService) {
                        serviceScope.launch {
                            try {
                                statusText = "Polishing..."
                                val polished = polishEngine.polish(transcript)
                                // Dismiss proxy BEFORE injecting so focus returns to the text field
                                ForegroundProxyActivity.dismiss()
                                kotlinx.coroutines.delay(150)
                                injectOrCopy(polished)
                                statusText = ""
                            } catch (e: Exception) {
                                Log.e(TAG, "Pipeline error", e)
                                statusText = "Error"
                                ForegroundProxyActivity.dismiss()
                                kotlinx.coroutines.delay(150)
                                if (transcript.isNotBlank()) {
                                    injectOrCopy(transcript)
                                }
                            } finally {
                                orbState = OrbState.IDLE
                            }
                        }
                    }
                }
            }
            OrbState.PROCESSING -> {
                // Ignore taps while processing
            }
        }
    }

    private fun injectOrCopy(text: String) {
        val injectionService = TextInjectionService.instance
        if (injectionService != null) {
            injectionService.inject(text)
        } else {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("PixelWhisper", text))
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("PixelWhisper")
            .setContentText("Tap the floating orb to start dictating")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        if (::composeView.isInitialized) {
            windowManager.removeView(composeView)
        }
        transcriptionEngine.release()
        polishEngine.release()
        serviceScope.cancel()
        _viewModelStore.clear()
        Log.d(TAG, "Service destroyed")
    }
}
