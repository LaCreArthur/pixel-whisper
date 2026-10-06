package com.pixelwhisper

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.Window
import android.view.WindowInsets
import android.view.accessibility.AccessibilityManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Status and test screen, also the service's settings page. Setup itself is scripts/setup-phone.sh on the Mac. */
class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val pad = (16 * resources.displayMetrics.density).toInt()
        status = TextView(this).apply { textSize = 16f }
        val field = EditText(this).apply {
            hint = "Tap here, then tap the orb and speak"
            minLines = 4
            gravity = Gravity.TOP
        }
        val credits = TextView(this).apply {
            text = "Speech: Whisper large-v3-turbo (MIT, OpenAI) on sherpa-onnx (Apache-2.0), Silero VAD (MIT). " +
                "Everything runs on this phone; the app has no internet access."
            textSize = 12f
            alpha = 0.7f
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(status)
            addView(field)
            addView(credits)
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom)
                insets
            }
        })
    }

    override fun onResume() {
        super.onResume()
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val service = getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName }
        val missing = Models.missing(this)
        status.text = listOf(
            "Microphone: " + if (mic) "allowed" else "not allowed",
            "Dictation service: " + if (service) "on" else "off",
            "Speech models: " + if (missing.isEmpty()) "ready" else "missing " + missing.joinToString(),
            if (mic && service && missing.isEmpty()) "Ready." else "Run scripts/setup-phone.sh on the Mac with the phone on USB.",
        ).joinToString("\n")
    }
}
