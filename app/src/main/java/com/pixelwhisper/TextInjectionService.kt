package com.pixelwhisper

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class TextInjectionService : AccessibilityService() {

    companion object {
        private const val TAG = "TextInjectionService"
        var instance: TextInjectionService? = null
            private set

        fun isAvailable(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility service connected")
    }

    fun inject(text: String) {
        val root = rootInActiveWindow
        if (root == null) {
            copyToClipboard(text)
            return
        }

        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) {
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }
            val success = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (success) {
                Log.d(TAG, "Text injected into focused field")
            } else {
                Log.w(TAG, "ACTION_SET_TEXT failed, falling back to clipboard")
                pasteViaClipboard(focused, text)
            }
            focused.recycle()
        } else {
            focused?.recycle()
            copyToClipboard(text)
        }
        root.recycle()
    }

    private fun pasteViaClipboard(node: AccessibilityNodeInfo, text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("PixelWhisper", text))
        val pasted = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        if (pasted) {
            Log.d(TAG, "Text pasted via clipboard")
        } else {
            Log.w(TAG, "Paste failed, text is on clipboard")
            showToast()
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("PixelWhisper", text))
        showToast()
        Log.d(TAG, "Text copied to clipboard")
    }

    private fun showToast() {
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to react to events — injection is triggered by FloatingOrbService
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "Accessibility service destroyed")
    }
}
