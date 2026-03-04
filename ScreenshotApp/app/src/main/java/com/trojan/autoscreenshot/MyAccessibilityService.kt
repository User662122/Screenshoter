package com.uitreecapture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.io.File

class MyAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        intent?.let {
            when (it.action) {

                "RUN_SCRIPT" -> {
                    runScript()
                }
            }
        }

        return START_STICKY
    }

    private fun runScript() {

        try {

            val file = File(Environment.getExternalStorageDirectory(), "Controller/script.txt")

            if (!file.exists()) {
                showToast("script.txt not found")
                return
            }

            val lines = file.readLines()

            Thread {

                for (lineRaw in lines) {

                    val line = lineRaw.trim()

                    when {

                        line.startsWith("CLICK ") -> {
                            val text = line.removePrefix("CLICK ").trim()
                            handler.post { clickByText(text) }
                        }

                        line == "SCROLL" -> {
                            handler.post { scrollDown() }
                        }

                        line.startsWith("WAIT ") -> {
                            val time = line.removePrefix("WAIT ").trim().toLong()
                            Thread.sleep(time)
                        }
                    }

                    Thread.sleep(500)
                }

            }.start()

        } catch (e: Exception) {
            showToast("Script Error: ${e.message}")
        }
    }

    private fun clickByText(text: String) {

        val root = rootInActiveWindow ?: return
        val node = findNodeByText(root, text)

        if (node != null) {

            val rect = Rect()
            node.getBoundsInScreen(rect)

            val path = Path()
            path.moveTo(rect.centerX().toFloat(), rect.centerY().toFloat())

            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
                .build()

            dispatchGesture(gesture, null, null)
            showToast("Clicked: $text")
        } else {
            showToast("Not found: $text")
        }

        root.recycle()
    }

    private fun scrollDown() {

        val display = resources.displayMetrics
        val width = display.widthPixels
        val height = display.heightPixels

        val path = Path()
        path.moveTo(width / 2f, height * 0.8f)
        path.lineTo(width / 2f, height * 0.3f)

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 500))
            .build()

        dispatchGesture(gesture, null, null)
    }

    private fun findNodeByText(root: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {

        if (root == null) return null

        if (root.text?.toString()?.contains(text, true) == true)
            return root

        if (root.contentDescription?.toString()?.contains(text, true) == true)
            return root

        for (i in 0 until root.childCount) {
            val child = root.getChild(i)
            val result = findNodeByText(child, text)
            if (result != null) return result
            child?.recycle()
        }

        return null
    }

    private fun showToast(msg: String) {
        handler.post {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }
}