package com.uitreecapture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
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
        private const val OUTPUT_PATH = "/storage/emulated/0/Controller"
        private const val OUTPUT_FILE = "ui_hierarchy.txt"
        private const val SEPARATOR   = "\n---SNAPSHOT_END---\n"
    }

    private var captureTimer: Timer? = null
    private var captureInterval: Int = 1000
    private var snapshotCount: Int   = 0

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun toast(msg: String, long: Boolean = false) {
        mainHandler.post {
            Toast.makeText(applicationContext, msg,
                if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

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
        toast("✅ Accessibility Service connected & ready")
    }

    override fun onInterrupt() {
        toast("⚠️ Accessibility Service interrupted!", long = true)
        stopCapture()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCapture()
        instance = null
        toast("🔴 Accessibility Service destroyed")
    }

    // ── Capture control ───────────────────────────────────────────────────────

    fun startCapture(intervalMs: Int) {
        captureInterval = intervalMs
        snapshotCount   = 0
        stopCapture()

        // Verify the output dir is available before starting the timer
        // (MainActivity already created it, but check again as a safety net)
        val dirCheck = ensureDir()
        if (!dirCheck) {
            // ensureDir already showed the Toast with the exact reason
            return
        }

        captureTimer = Timer("UICapture", true).also {
            it.scheduleAtFixedRate(object : TimerTask() {
                override fun run() { captureAndSave() }
            }, 0L, captureInterval.toLong())
        }
        toast("▶ Capture started — every ${intervalMs}ms\n📁 $OUTPUT_PATH/$OUTPUT_FILE")
    }

    fun stopCapture() {
        captureTimer?.cancel()
        captureTimer = null
    }

    // ── Snapshot ──────────────────────────────────────────────────────────────

    private fun captureAndSave() {
        try {
            val snapshot = JSONObject().apply {
                put("timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date()))
                put("captureInterval_ms", captureInterval)

                val windowsArray = JSONArray()
                getWindows()?.forEach { window -> windowsArray.put(serializeWindow(window)) }
                put("windows", windowsArray)

                getRootInActiveWindow()?.let { root ->
                    put("activeWindowRoot", serializeNode(root))
                    root.recycle()
                }
            }
            appendToFile(snapshot.toString(2))

        } catch (e: Exception) {
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            toast("❌ Snapshot error:\n${e.javaClass.simpleName}: ${e.message}", long = true)
            appendToFile("""{"error":"${e.message}","timestamp":"$ts"}""")
        }
    }

    // ── File I/O ──────────────────────────────────────────────────────────────

    /**
     * Ensures the output directory exists.
     * - Already exists as a directory → silent success, returns true.
     * - Doesn't exist → tries mkdirs(), toasts result, returns true/false.
     * - Exists as a file (edge case) → toasts error, returns false.
     * Never calls mkdirs() if the folder is already there.
     */
    private fun ensureDir(): Boolean {
        val dir = File(OUTPUT_PATH)
        return when {
            dir.exists() && dir.isDirectory -> true   // already fine, do nothing
            dir.exists() && !dir.isDirectory -> {
                toast("❌ '$OUTPUT_PATH' exists but is a file, not a folder!", long = true)
                false
            }
            else -> {
                val created = dir.mkdirs()
                if (!created) {
                    toast(
                        "❌ Could not create folder: $OUTPUT_PATH\n" +
                        "→ Go to Settings → Apps → UIHierarchyCapture\n" +
                        "→ Grant 'All files access'",
                        long = true
                    )
                }
                created
            }
        }
    }

    private fun appendToFile(content: String) {
        try {
            // Re-check dir on every write — handles the folder-deleted-mid-session case
            if (!ensureDir()) return

            val file = File(OUTPUT_PATH, OUTPUT_FILE)
            BufferedWriter(FileWriter(file, true)).use { writer ->
                writer.write(content)
                writer.write(SEPARATOR)
                writer.flush()
            }

            snapshotCount++
            // Toast every 5 snapshots to confirm writes without flooding the screen
            if (snapshotCount % 5 == 0) {
                val sizeKb = file.length() / 1024
                toast("✅ $snapshotCount snapshots saved (${sizeKb}KB)\n📁 $OUTPUT_FILE")
            }

        } catch (e: SecurityException) {
            toast(
                "🔒 PERMISSION DENIED writing to file!\n" +
                "→ Settings → Apps → UIHierarchyCapture\n" +
                "→ Grant 'All files access'",
                long = true
            )
        } catch (e: Exception) {
            toast(
                "❌ FILE WRITE ERROR\n" +
                "${e.javaClass.simpleName}: ${e.message}\n" +
                "Path: $OUTPUT_PATH/$OUTPUT_FILE",
                long = true
            )
        }
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    private fun serializeWindow(window: AccessibilityWindowInfo): JSONObject {
        val obj = JSONObject()
        obj.put("id",    window.id)
        obj.put("type",  windowTypeToString(window.type))
        obj.put("layer", window.layer)
        obj.put("isActive",               window.isActive)
        obj.put("isFocused",              window.isFocused)
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

        Rect().also { r -> node.getBoundsInScreen(r); obj.put("boundsInScreen", rectToJson(r)) }
        Rect().also { r -> node.getBoundsInParent(r); obj.put("boundsInParent", rectToJson(r)) }

        obj.put("isCheckable",     node.isCheckable)
        obj.put("isChecked",       node.isChecked)
        obj.put("isClickable",     node.isClickable)
        obj.put("isEnabled",       node.isEnabled)
        obj.put("isFocusable",     node.isFocusable)
        obj.put("isFocused",       node.isFocused)
        obj.put("isLongClickable", node.isLongClickable)
        obj.put("isPassword",      node.isPassword)
        obj.put("isScrollable",    node.isScrollable)
        obj.put("isSelected",      node.isSelected)
        obj.put("isVisibleToUser", node.isVisibleToUser)
        obj.put("inputType",       node.inputType)
        obj.put("childCount",      node.childCount)

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
        AccessibilityWindowInfo.TYPE_APPLICATION           -> "APPLICATION"
        AccessibilityWindowInfo.TYPE_INPUT_METHOD          -> "INPUT_METHOD"
        AccessibilityWindowInfo.TYPE_SYSTEM                -> "SYSTEM"
        AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "ACCESSIBILITY_OVERLAY"
        else                                               -> "UNKNOWN($type)"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { }
}