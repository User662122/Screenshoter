package com.uitreecapture

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MyAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val interval: Long = 5000  // 5 seconds
    private var isCapturing = false

    private val captureRunnable = object : Runnable {
        override fun run() {
            if (isCapturing) {
                captureUI()
            }
            handler.postDelayed(this, interval)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        handler.post(captureRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            if (it.action == "CAPTURE_TOGGLE") {
                isCapturing = it.getBooleanExtra("capture_enabled", false)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed for interval-based capture
    }

    override fun onInterrupt() {}

    private fun captureUI() {
        val root = rootInActiveWindow ?: return

        val builder = StringBuilder()

        builder.append("\n==============================\n")
        builder.append("Time: ${
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        }\n")

        builder.append("Package: ${root.packageName ?: "null"}\n")
        builder.append("WebView Present: ${findWebView(root)}\n")
        builder.append("Scrollable: ${root.isScrollable}\n")

        builder.append("\nClickable Elements:\n")

        traverseNode(root, builder)

        saveToFile(builder.toString())
        
        // Root node should be recycled after the entire traversal
        root.recycle()
    }

    private fun findWebView(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        if (node.className?.contains("WebView") == true) {
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                if (findWebView(child)) {
                    child.recycle()
                    return true
                }
                child.recycle()
            }
        }

        return false
    }

    private fun traverseNode(node: AccessibilityNodeInfo?, builder: StringBuilder) {
        if (node == null) return

        if (node.isClickable) {
            val rect = Rect()
            node.getBoundsInScreen(rect)

            builder.append("\n------------------\n")
            builder.append("Text: ${node.text ?: "null"}\n")
            builder.append("ContentDesc: ${node.contentDescription ?: "null"}\n")
            builder.append("ResourceID: ${node.viewIdResourceName ?: "null"}\n")
            builder.append("Class: ${node.className ?: "null"}\n")
            builder.append("Clickable: ${node.isClickable}\n")
            builder.append("Enabled: ${node.isEnabled}\n")
            builder.append("Bounds: $rect\n")
            
            // Avoid calling getChildIndex which performs another traversal/recycling
            builder.append("Checkable: ${node.isCheckable}\n")
            builder.append("Checked: ${node.isChecked}\n")
            builder.append("Selected: ${node.isSelected}\n")
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                traverseNode(child, builder)
                child.recycle()
            }
        }
    }

    private fun saveToFile(text: String) {
        try {
            // Check storage permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+: Check MANAGE_EXTERNAL_STORAGE
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.MANAGE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                    logError("MANAGE_EXTERNAL_STORAGE permission not granted")
                    return
                }
            } else {
                // Android 10 and below: Check WRITE_EXTERNAL_STORAGE
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                    logError("WRITE_EXTERNAL_STORAGE permission not granted")
                    return
                }
            }

            val dir = File(Environment.getExternalStorageDirectory(), "Controller")
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    logError("Failed to create directory: ${dir.absolutePath}")
                    return
                }
            }

            val file = File(dir, "ui_capture.txt")
            val writer = FileWriter(file, true) // Append mode
            writer.write(text)
            writer.close()
            
            logSuccess("UI data saved to: ${file.absolutePath}")

        } catch (e: SecurityException) {
            logError("SecurityException: ${e.message}")
        } catch (e: Exception) {
            logError("Error saving file: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun logSuccess(message: String) {
        try {
            val logDir = File(Environment.getExternalStorageDirectory(), "Controller")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            val logFile = File(logDir, "capture_log.txt")
            val writer = FileWriter(logFile, true)
            writer.write("[SUCCESS] ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())} - $message\n")
            writer.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun logError(message: String) {
        try {
            val logDir = File(Environment.getExternalStorageDirectory(), "Controller")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            val logFile = File(logDir, "capture_log.txt")
            val writer = FileWriter(logFile, true)
            writer.write("[ERROR] ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())} - $message\n")
            writer.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
