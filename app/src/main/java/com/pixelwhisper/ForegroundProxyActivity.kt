package com.pixelwhisper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Transparent activity that briefly comes to foreground so AICore
 * (ML Kit Prompt API) considers the app "foreground" during polish.
 * Auto-finishes after a timeout or when signaled.
 */
class ForegroundProxyActivity : Activity() {

    companion object {
        private const val TAG = "ForegroundProxy"
        private var onReady: (() -> Unit)? = null

        fun launch(context: Context, ready: () -> Unit) {
            onReady = ready
            val intent = Intent(context, ForegroundProxyActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            context.startActivity(intent)
        }

        fun dismiss() {
            instance?.finish()
        }

        private var instance: ForegroundProxyActivity? = null
    }

    private val timeout = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        // Auto-dismiss after 15s in case something goes wrong
        timeout.postDelayed({ finish() }, 15_000)
        Log.d(TAG, "Foreground proxy active")
        onReady?.invoke()
        onReady = null
    }

    override fun onDestroy() {
        super.onDestroy()
        timeout.removeCallbacksAndMessages(null)
        instance = null
        Log.d(TAG, "Foreground proxy dismissed")
    }
}
