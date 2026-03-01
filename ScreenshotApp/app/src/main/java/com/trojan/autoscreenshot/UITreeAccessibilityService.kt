package com.trojan.autoscreenshot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class UITreeAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "UITreeAccessibilityService"
        private const val PREFS_NAME = "ui_tree_config"
        private const val NGROK_URL_KEY = "ngrok_url"

        private var isCapturing = false
        private var currentNgrokUrl = ""

        fun setCapturing(capturing: Boolean) {
            isCapturing = capturing
        }

        fun updateNgrokUrl(context: Context, url: String) {
            currentNgrokUrl = url
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(NGROK_URL_KEY, url).apply()
        }

        fun getNgrokUrl(context: Context): String {
            if (currentNgrokUrl.isEmpty()) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                currentNgrokUrl = prefs.getString(NGROK_URL_KEY, "") ?: ""
            }
            return currentNgrokUrl
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private var lastCaptureTime = 0L
    private val captureIntervalMs = 500 // Capture every 500ms

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "UITree Accessibility Service Connected")

        val info = AccessibilityServiceInfo().apply {
            flags = AccessibilityServiceInfo.DEFAULT
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }

        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isCapturing) {
            return
        }

        // Throttle captures
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastCaptureTime < captureIntervalMs) {
            return
        }
        lastCaptureTime = currentTime

        try {
            val rootNode = rootInActiveWindow ?: return

            // Extract UI tree as native format
            val uiTreeString = UITreeExtractor.extractUITree(rootNode)

            // Send to ngrok URL
            if (currentNgrokUrl.isNotEmpty()) {
                sendUITreeToNgrok(currentNgrokUrl, uiTreeString)
            } else {
                Log.w(TAG, "Ngrok URL is empty, not sending UI tree")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in onAccessibilityEvent: ${e.message}", e)
        }
    }

    private fun sendUITreeToNgrok(ngrokUrl: String, uiTreeString: String) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val requestBody = uiTreeString.toRequestBody("text/plain".toMediaType())

                val request = Request.Builder()
                    .url(ngrokUrl)
                    .post(requestBody)
                    .addHeader("Content-Type", "text/plain")
                    .build()

                val response = httpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    Log.d(TAG, "UI Tree sent successfully - Code: ${response.code}")
                    // after sending we poll for commands
                    fetchAndExecuteCommands(ngrokUrl)
                } else {
                    Log.e(TAG, "Failed to send UI Tree - Code: ${response.code}")
                }
                response.close()

            } catch (e: Exception) {
                Log.e(TAG, "Error sending UI Tree: ${e.message}")
            }
        }
    }

    /**
     * Poll the endpoint for commands in simple format:
     * click: resourceId
     * longClick: resourceId
     * setText: resourceId:value
     * scroll: up
     * scroll: down
     */
    private fun fetchAndExecuteCommands(ngrokUrl: String) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val url = ngrokUrl
                val request = Request.Builder().url(url).get().build()
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrEmpty()) {
                        executeSimpleCommands(body)
                    }
                }
                response.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching commands: ${e.message}")
            }
        }
    }

    private fun executeSimpleCommands(commandString: String) {
        try {
            val lines = commandString.split("\n")
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                
                executeSimpleCommand(trimmed)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing commands: ${e.message}")
        }
    }

    private fun executeSimpleCommand(commandLine: String) {
        try {
            // Format: command: parameter
            val parts = commandLine.split(":", limit = 2)
            if (parts.size < 2) {
                Log.w(TAG, "Invalid command format: $commandLine")
                return
            }

            val command = parts[0].trim().lowercase()
            val parameter = parts[1].trim()

            when (command) {
                "click" -> {
                    val resourceId = parameter
                    performActionByResourceId(resourceId) { node ->
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                }
                "longclick" -> {
                    val resourceId = parameter
                    performActionByResourceId(resourceId) { node ->
                        node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                    }
                }
                "write" -> {
                    // write: text_to_write
                    val text = parameter
                    performActionOnFocusedNode { node ->
                        val args = Bundle()
                        args.putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            text
                        )
                        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    }
                }
                "scroll" -> {
                    when (parameter.lowercase()) {
                        "up" -> {
                            performActionOnScrollable { node ->
                                node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                            }
                        }
                        "down" -> {
                            performActionOnScrollable { node ->
                                node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                            }
                        }
                        else -> Log.w(TAG, "Unknown scroll direction: $parameter")
                    }
                }
                else -> Log.w(TAG, "Unknown command: $command")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command: ${e.message}")
        }
    }

    private fun performActionByResourceId(
        resourceId: String,
        action: (AccessibilityNodeInfo) -> Unit
    ) {
        try {
            val root = rootInActiveWindow ?: return
            val target = findNodeByResourceId(root, resourceId)
            if (target != null) {
                action(target)
                target.recycle()
            } else {
                Log.w(TAG, "Node not found with resourceId: $resourceId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing action by resourceId: ${e.message}")
        }
    }

    private fun performActionOnFocusedNode(action: (AccessibilityNodeInfo) -> Unit) {
        try {
            val root = rootInActiveWindow
            if (root != null) {
                // Try to find a focusable EditText or similar
                val editable = findFirstEditable(root)
                if (editable != null) {
                    action(editable)
                    editable.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing action on focused node: ${e.message}")
        }
    }

    private fun performActionOnScrollable(action: (AccessibilityNodeInfo) -> Unit) {
        try {
            val root = rootInActiveWindow
            if (root != null) {
                val scrollable = findFirstScrollable(root)
                if (scrollable != null) {
                    action(scrollable)
                    scrollable.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing scroll action: ${e.message}")
        }
    }

    private fun findNodeByResourceId(root: AccessibilityNodeInfo, resourceId: String): AccessibilityNodeInfo? {
        try {
            val found = root.findAccessibilityNodeInfosByViewId(resourceId)
            if (found != null && found.isNotEmpty()) {
                return found[0]
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error finding node by resourceId: ${e.message}")
        }
        return null
    }

    private fun findFirstEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isEditable) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val result = findFirstEditable(child)
            if (result != null) return result
        }
        return null
    }

    private fun findFirstScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isScrollable) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val result = findFirstScrollable(child)
            if (result != null) return result
        }
        return null
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "UITree Accessibility Service Destroyed")
    }
}
