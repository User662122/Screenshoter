package com.uitreecapture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class UIHierarchyAccessibilityService : AccessibilityService() {

    companion object {
        var instance: UIHierarchyAccessibilityService? = null
        private const val TAG         = "UIHierarchySvc"
        private const val OUTPUT_PATH = "/storage/emulated/0/Controller"
        private const val OUTPUT_FILE = "ui_hierarchy.txt"
        private const val SEPARATOR   = "\n---SNAPSHOT_END---\n"
    }

    private var captureTimer: Timer? = null
    private var captureInterval: Int = 1000

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes   = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags        = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                           AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                           AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY
            notificationTimeout = 100
        }
        Log.i(TAG, "Accessibility service connected")
    }

    // ── Capture control ───────────────────────────────────────────────────────

    fun startCapture(intervalMs: Int) {
        captureInterval = intervalMs
        stopCapture()
        captureTimer = Timer("UICapture", true).also {
            it.scheduleAtFixedRate(object : TimerTask() {
                override fun run() { captureAndSave() }
            }, 0L, captureInterval.toLong())
        }
        Log.i(TAG, "Capture started, interval=${intervalMs}ms")
    }

    fun stopCapture() {
        captureTimer?.cancel()
        captureTimer = null
        Log.i(TAG, "Capture stopped")
    }

    // ── Snapshot ──────────────────────────────────────────────────────────────

    private fun captureAndSave() {
        try {
            val snapshot = JSONObject().apply {
                put("timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date()))
                put("captureInterval_ms", captureInterval)

                val windowsArray = JSONArray()
                getWindows()?.forEach { window ->
                    windowsArray.put(serializeWindow(window))
                }
                put("windows", windowsArray)

                getRootInActiveWindow()?.let { root ->
                    put("activeWindowRoot", serializeNode(root))
                    root.recycle()
                }
            }
            // FIX #1 — appendToFile now logs errors instead of silently swallowing them
            appendToFile(snapshot.toString(2))

        } catch (e: Exception) {
            Log.e(TAG, "captureAndSave failed: ${e.message}", e)
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            appendToFile("""{"error":"${e.message}","timestamp":"$ts"}""")
        }
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    private fun serializeWindow(window: AccessibilityWindowInfo): JSONObject {
        val obj = JSONObject()
        obj.put("id",   window.id)
        obj.put("type", windowTypeToString(window.type))
        obj.put("layer", window.layer)
        obj.put("isActive",              window.isActive)
        obj.put("isFocused",             window.isFocused)
        obj.put("isAccessibilityFocused", window.isAccessibilityFocused)

        val bounds = Rect()
        window.getBoundsInScreen(bounds)
        obj.put("bounds", rectToJson(bounds))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            window.title?.let { obj.put("title", it.toString()) }
        }
        window.root?.let { root ->
            obj.put("root", serializeNode(root))
            root.recycle()
        }
        return obj
    }

    private fun serializeNode(node: AccessibilityNodeInfo): JSONObject {
        val obj = JSONObject()

        node.className?.let          { obj.put("className",          it.toString()) }
        node.text?.let               { obj.put("text",               it.toString()) }
        node.contentDescription?.let { obj.put("contentDescription", it.toString()) }
        node.viewIdResourceName?.let { obj.put("resourceId",         it) }
        node.packageName?.let        { obj.put("packageName",        it.toString()) }

        Rect().also { r -> node.getBoundsInScreen(r);  obj.put("boundsInScreen",  rectToJson(r)) }
        Rect().also { r -> node.getBoundsInParent(r);  obj.put("boundsInParent",  rectToJson(r)) }

        obj.put("isCheckable",    node.isCheckable)
        obj.put("isChecked",      node.isChecked)
        obj.put("isClickable",    node.isClickable)
        obj.put("isEnabled",      node.isEnabled)
        obj.put("isFocusable",    node.isFocusable)
        obj.put("isFocused",      node.isFocused)
        obj.put("isLongClickable",node.isLongClickable)
        obj.put("isPassword",     node.isPassword)
        obj.put("isScrollable",   node.isScrollable)
        obj.put("isSelected",     node.isSelected)
        obj.put("isVisibleToUser",node.isVisibleToUser)
        obj.put("inputType",      node.inputType)
        obj.put("childCount",     node.childCount)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            node.hintText?.let { obj.put("hintText", it.toString()) }
            obj.put("drawingOrder", node.drawingOrder)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            node.tooltipText?.let { obj.put("tooltipText", it.toString()) }
            node.paneTitle?.let   { obj.put("paneTitle",   it.toString()) }
        }

        if (node.childCount > 0) {
            val children = JSONArray()
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    children.put(serializeNode(child))
                    child.recycle()
                } else {
                    children.put(JSONObject())
                }
            }
            obj.put("children", children)
        }
        return obj
    }

    private fun rectToJson(rect: Rect) = JSONObject().apply {
        put("left",   rect.left)
        put("top",    rect.top)
        put("right",  rect.right)
        put("bottom", rect.bottom)
        put("width",  rect.width())
        put("height", rect.height())
    }

    private fun windowTypeToString(type: Int) = when (type) {
        AccessibilityWindowInfo.TYPE_APPLICATION          -> "APPLICATION"
        AccessibilityWindowInfo.TYPE_INPUT_METHOD         -> "INPUT_METHOD"
        AccessibilityWindowInfo.TYPE_SYSTEM               -> "SYSTEM"
        AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "ACCESSIBILITY_OVERLAY"
        else -> "UNKNOWN($type)"
    }

    // ── File I/O (FIX #1) ────────────────────────────────────────────────────

    private fun appendToFile(content: String) {
        try {
            // FIX #1 — create dir fresh every write attempt in case it was deleted
            val dir = File(OUTPUT_PATH)
            if (!dir.exists()) {
                val created = dir.mkdirs()
                if (!created) {
                    Log.e(TAG, "Could not create directory: $OUTPUT_PATH")
                    return
                }
            }
            val file = File(dir, OUTPUT_FILE)
            BufferedWriter(FileWriter(file, true)).use { writer ->
                writer.write(content)
                writer.write(SEPARATOR)
                writer.flush()
            }
            Log.v(TAG, "Snapshot written to ${file.absolutePath} (${file.length()} bytes)")
        } catch (e: Exception) {
            // FIX #1 — log the real error instead of swallowing it silently
            Log.e(TAG, "appendToFile FAILED: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    // ── AccessibilityService callbacks ────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Periodic capture is handled by the Timer; no event-driven logic needed
    }

    override fun onInterrupt() {
        stopCapture()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCapture()
        instance = null
    }
}