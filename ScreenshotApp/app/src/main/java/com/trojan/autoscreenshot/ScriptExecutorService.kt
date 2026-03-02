package com.uitreecapture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.annotation.RequiresApi
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.util.*

/**
 * ScriptExecutorService
 * ─────────────────────
 * Watches /storage/emulated/0/Controller/commands.json every 300ms.
 * When a command file appears, it executes each action in order,
 * writes results to /storage/emulated/0/Controller/results.json,
 * then deletes the command file (ready for the next script run).
 *
 * Supported actions (set via Python script):
 *   open_app, close_app, tap, long_press, swipe, scroll,
 *   type_text, clear_text, press_back, press_home, press_recents,
 *   open_url, web_search, open_tab, close_tab, new_tab,
 *   find_and_click, find_and_type, wait
 */
class ScriptExecutorService : AccessibilityService() {

    companion object {
        var instance: ScriptExecutorService? = null
        private const val CMD_FILE    = "commands.json"
        private const val RESULT_FILE = "results.json"
        private const val DIR_NAME    = "Controller"
        private const val POLL_MS     = 300L
    }

    private val mainHandler  = Handler(Looper.getMainLooper())
    private var pollTimer: Timer? = null
    private val results = mutableListOf<JSONObject>()

    private val controlDir: File
        get() = File(Environment.getExternalStorageDirectory(), DIR_NAME)

    private val cmdFile: File
        get() = File(controlDir, CMD_FILE)

    private val resultFile: File
        get() = File(controlDir, RESULT_FILE)

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        toast("🤖 Script Executor ready — watching for commands.json")
        startPolling()
    }

    override fun onInterrupt() {
        stopPolling()
        toast("⚠️ Script Executor interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPolling()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { }

    // ── Polling ───────────────────────────────────────────────────────────────

    private fun startPolling() {
        pollTimer = Timer("ScriptPoll", true).also {
            it.scheduleAtFixedRate(object : TimerTask() {
                override fun run() { checkAndExecute() }
            }, 0L, POLL_MS)
        }
    }

    private fun stopPolling() {
        pollTimer?.cancel()
        pollTimer = null
    }

    // ── Command file check ────────────────────────────────────────────────────

    private fun checkAndExecute() {
        if (!cmdFile.exists()) return
        val content = try { cmdFile.readText() } catch (e: Exception) { return }
        if (content.isBlank()) return

        // Immediately rename to prevent double-execution
        val processing = File(controlDir, "commands_processing.json")
        cmdFile.renameTo(processing)

        results.clear()

        try {
            val root = JSONObject(content)
            val actions = root.optJSONArray("actions") ?: return
            toast("▶ Running script: ${actions.length()} action(s)")

            for (i in 0 until actions.length()) {
                val action = actions.getJSONObject(i)
                val result = executeAction(action)
                results.add(result)
                // Delay between actions if specified
                val delay = action.optLong("delay_after", 0L)
                if (delay > 0) Thread.sleep(delay)
            }

            toast("✅ Script done — ${results.size} action(s) completed")
        } catch (e: Exception) {
            results.add(makeResult("error", false, "Parse error: ${e.message}"))
            toast("❌ Script error: ${e.message}", long = true)
        } finally {
            writeResults()
            processing.delete()
        }
    }

    // ── Action router ─────────────────────────────────────────────────────────

    private fun executeAction(action: JSONObject): JSONObject {
        val type = action.optString("action", "")
        return try {
            when (type) {
                "tap"           -> doTap(action)
                "long_press"    -> doLongPress(action)
                "swipe"         -> doSwipe(action)
                "scroll"        -> doScroll(action)
                "type_text"     -> doTypeText(action)
                "clear_text"    -> doClearText(action)
                "press_back"    -> doPressBack()
                "press_home"    -> doPressHome()
                "press_recents" -> doPressRecents()
                "open_app"      -> doOpenApp(action)
                "close_app"     -> doCloseApp(action)
                "open_url"      -> doOpenUrl(action)
                "web_search"    -> doWebSearch(action)
                "new_tab"       -> doNewTab(action)
                "find_and_click"-> doFindAndClick(action)
                "find_and_type" -> doFindAndType(action)
                "wait"          -> doWait(action)
                else            -> makeResult(type, false, "Unknown action: $type")
            }
        } catch (e: Exception) {
            makeResult(type, false, "Exception: ${e.message}")
        }
    }

    // ── Gesture actions ───────────────────────────────────────────────────────

    private fun doTap(action: JSONObject): JSONObject {
        val x = action.optInt("x", -1)
        val y = action.optInt("y", -1)
        if (x < 0 || y < 0) return makeResult("tap", false, "Missing x/y")

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val tapped = performTap(x.toFloat(), y.toFloat())
            makeResult("tap", tapped, "Tapped ($x, $y)")
        } else {
            makeResult("tap", false, "Gesture API requires Android 7+")
        }
    }

    private fun doLongPress(action: JSONObject): JSONObject {
        val x = action.optInt("x", -1)
        val y = action.optInt("y", -1)
        val duration = action.optLong("duration", 800L)
        if (x < 0 || y < 0) return makeResult("long_press", false, "Missing x/y")

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val done = performSwipeGesture(
                x.toFloat(), y.toFloat(), x.toFloat(), y.toFloat(), duration
            )
            makeResult("long_press", done, "Long pressed ($x, $y) for ${duration}ms")
        } else {
            makeResult("long_press", false, "Gesture API requires Android 7+")
        }
    }

    private fun doSwipe(action: JSONObject): JSONObject {
        val direction = action.optString("direction", "")
        val x1 = action.optInt("x1", -1)
        val y1 = action.optInt("y1", -1)
        val x2 = action.optInt("x2", -1)
        val y2 = action.optInt("y2", -1)
        val duration = action.optLong("duration", 300L)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
            return makeResult("swipe", false, "Gesture API requires Android 7+")

        // Support both explicit coords and direction shortcuts
        val (sx, sy, ex, ey) = if (x1 >= 0 && y1 >= 0 && x2 >= 0 && y2 >= 0) {
            arrayOf(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat())
        } else {
            // Get screen size from root window
            val root = rootInActiveWindow
            val sw = root?.let { r ->
                val b = android.graphics.Rect(); r.getBoundsInScreen(b); b.width()
            } ?: 1080
            val sh = root?.let { r ->
                val b = android.graphics.Rect(); r.getBoundsInScreen(b); b.height()
            } ?: 1920
            val cx = sw / 2f; val cy = sh / 2f
            when (direction.lowercase()) {
                "up"    -> arrayOf(cx, cy + 400f, cx, cy - 400f)
                "down"  -> arrayOf(cx, cy - 400f, cx, cy + 400f)
                "left"  -> arrayOf(cx + 400f, cy, cx - 400f, cy)
                "right" -> arrayOf(cx - 400f, cy, cx + 400f, cy)
                else    -> return makeResult("swipe", false, "Need direction or x1/y1/x2/y2")
            }
        }

        val done = performSwipeGesture(sx, sy, ex, ey, duration)
        return makeResult("swipe", done, "Swiped ($sx,$sy)→($ex,$ey)")
    }

    private fun doScroll(action: JSONObject): JSONObject {
        val direction = action.optString("direction", "down")
        val distance  = action.optInt("distance", 500)

        // Try node-based scroll first (more reliable)
        val root = rootInActiveWindow
        if (root != null) {
            val scrollable = findScrollableNode(root)
            if (scrollable != null) {
                val scrollAction = when (direction.lowercase()) {
                    "up"    -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                    "down"  -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    "left"  -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                    "right" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    else    -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                }
                val done = scrollable.performAction(scrollAction)
                scrollable.recycle()
                if (done) return makeResult("scroll", true, "Scrolled $direction via node")
            }
        }

        // Fallback: gesture swipe
        return doSwipe(JSONObject().apply {
            put("direction", if (direction == "down") "up" else
                             if (direction == "up") "down" else direction)
            put("duration", 400L)
        })
    }

    // ── Text actions ──────────────────────────────────────────────────────────

    private fun doTypeText(action: JSONObject): JSONObject {
        val text = action.optString("text", "")
        if (text.isEmpty()) return makeResult("type_text", false, "Empty text")

        // Try to find focused or focusable input
        val root = rootInActiveWindow ?: return makeResult("type_text", false, "No active window")
        val node = findFocusedInput(root) ?: findFirstInput(root)
                   ?: return makeResult("type_text", false, "No input field found")

        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        Thread.sleep(100)

        val args = Bundle().apply {
            putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val done = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        node.recycle()
        return makeResult("type_text", done, "Typed: $text")
    }

    private fun doClearText(action: JSONObject): JSONObject {
        val root = rootInActiveWindow ?: return makeResult("clear_text", false, "No active window")
        val node = findFocusedInput(root) ?: findFirstInput(root)
                   ?: return makeResult("clear_text", false, "No input field found")

        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        Thread.sleep(50)
        // Select all then set empty text
        node.performAction(AccessibilityNodeInfo.ACTION_SELECT)
        val args = Bundle().apply {
            putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        }
        val done = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        node.recycle()
        return makeResult("clear_text", done, "Cleared input field")
    }

    // ── System button actions ─────────────────────────────────────────────────

    private fun doPressBack(): JSONObject {
        val done = performGlobalAction(GLOBAL_ACTION_BACK)
        return makeResult("press_back", done, "Back pressed")
    }

    private fun doPressHome(): JSONObject {
        val done = performGlobalAction(GLOBAL_ACTION_HOME)
        return makeResult("press_home", done, "Home pressed")
    }

    private fun doPressRecents(): JSONObject {
        val done = performGlobalAction(GLOBAL_ACTION_RECENTS)
        return makeResult("press_recents", done, "Recents pressed")
    }

    // ── App control ───────────────────────────────────────────────────────────

    private fun doOpenApp(action: JSONObject): JSONObject {
        val pkg = action.optString("package", "")
        if (pkg.isEmpty()) return makeResult("open_app", false, "Missing package name")

        return try {
            val intent = applicationContext.packageManager
                .getLaunchIntentForPackage(pkg)
                ?: return makeResult("open_app", false, "App not found: $pkg")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            applicationContext.startActivity(intent)
            makeResult("open_app", true, "Opened: $pkg")
        } catch (e: Exception) {
            makeResult("open_app", false, "Failed: ${e.message}")
        }
    }

    private fun doCloseApp(action: JSONObject): JSONObject {
        // Press recents then swipe away — simplest approach without root
        val done = performGlobalAction(GLOBAL_ACTION_RECENTS)
        Thread.sleep(600)
        // Swipe the top card away (right swipe to dismiss)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val sw = performSwipeGesture(540f, 800f, 1100f, 800f, 250L)
            makeResult("close_app", sw, "Closed via recents swipe")
        } else {
            makeResult("close_app", done, "Opened recents (swipe manually on older Android)")
        }
    }

    // ── Browser / URL actions ─────────────────────────────────────────────────

    private fun doOpenUrl(action: JSONObject): JSONObject {
        val url = action.optString("url", "")
        if (url.isEmpty()) return makeResult("open_url", false, "Missing url")
        val finalUrl = if (url.startsWith("http")) url else "https://$url"

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Prefer Chrome
                setPackage("com.android.chrome")
            }
            try {
                applicationContext.startActivity(intent)
            } catch (e: Exception) {
                // Chrome not available, use default browser
                intent.setPackage(null)
                applicationContext.startActivity(intent)
            }
            makeResult("open_url", true, "Opened: $finalUrl")
        } catch (e: Exception) {
            makeResult("open_url", false, "Failed: ${e.message}")
        }
    }

    private fun doWebSearch(action: JSONObject): JSONObject {
        val query = action.optString("query", "")
        if (query.isEmpty()) return makeResult("web_search", false, "Missing query")

        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return doOpenUrl(JSONObject().apply {
            put("url", "https://www.google.com/search?q=$encoded")
        })
    }

    private fun doNewTab(action: JSONObject): JSONObject {
        // Open Chrome with a new tab intent
        val url = action.optString("url", "")
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = if (url.isNotEmpty()) Uri.parse(if (url.startsWith("http")) url else "https://$url")
                       else Uri.parse("about:blank")
                setPackage("com.android.chrome")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Chrome extra to open in new tab
                putExtra("com.android.browser.application_id", applicationContext.packageName)
            }
            applicationContext.startActivity(intent)
            makeResult("new_tab", true, if (url.isNotEmpty()) "New tab: $url" else "New blank tab")
        } catch (e: Exception) {
            makeResult("new_tab", false, "Failed: ${e.message}")
        }
    }

    // ── Smart find & interact ─────────────────────────────────────────────────

    private fun doFindAndClick(action: JSONObject): JSONObject {
        val text   = action.optString("text", "")
        val resId  = action.optString("resource_id", "")
        val desc   = action.optString("content_desc", "")

        val root = rootInActiveWindow ?: return makeResult("find_and_click", false, "No active window")
        val node = when {
            resId.isNotEmpty() -> findNodeByResourceId(root, resId)
            text.isNotEmpty()  -> findNodeByText(root, text)
            desc.isNotEmpty()  -> findNodeByDesc(root, desc)
            else               -> null
        } ?: return makeResult("find_and_click", false, "Element not found: text='$text' resId='$resId' desc='$desc'")

        node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        Thread.sleep(80)
        val done = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        node.recycle()
        return makeResult("find_and_click", done,
            "Clicked: ${text.ifEmpty { resId.ifEmpty { desc } }}")
    }

    private fun doFindAndType(action: JSONObject): JSONObject {
        val text   = action.optString("text", "")
        val resId  = action.optString("resource_id", "")
        val value  = action.optString("value", "")

        val root = rootInActiveWindow ?: return makeResult("find_and_type", false, "No active window")
        val node = when {
            resId.isNotEmpty() -> findNodeByResourceId(root, resId)
            text.isNotEmpty()  -> findNodeByText(root, text)
            else               -> findFirstInput(root)
        } ?: return makeResult("find_and_type", false, "Input element not found")

        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Thread.sleep(150)

        val args = Bundle().apply {
            putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        val done = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        node.recycle()
        return makeResult("find_and_type", done, "Typed '$value' into '$text'")
    }

    private fun doWait(action: JSONObject): JSONObject {
        val ms = action.optLong("ms", 1000L)
        Thread.sleep(ms)
        return makeResult("wait", true, "Waited ${ms}ms")
    }

    // ── Gesture helpers ───────────────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.N)
    private fun performTap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 80)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        var result = false
        val latch = java.util.concurrent.CountDownLatch(1)
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription) { result = true; latch.countDown() }
            override fun onCancelled(g: GestureDescription) { latch.countDown() }
        }, null)
        latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
        return result
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun performSwipeGesture(
        x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long
    ): Boolean {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        var result = false
        val latch = java.util.concurrent.CountDownLatch(1)
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription) { result = true; latch.countDown() }
            override fun onCancelled(g: GestureDescription) { latch.countDown() }
        }, null)
        latch.await(durationMs + 1000, java.util.concurrent.TimeUnit.MILLISECONDS)
        return result
    }

    // ── Node search helpers ───────────────────────────────────────────────────

    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByText(text)
        return nodes?.firstOrNull()
    }

    private fun findNodeByResourceId(root: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByViewId(id)
        return nodes?.firstOrNull()
    }

    private fun findNodeByDesc(root: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? =
        searchNode(root) { it.contentDescription?.toString()?.contains(desc, ignoreCase = true) == true }

    private fun findFocusedInput(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        searchNode(root) {
            it.isFocused && (it.className?.contains("EditText") == true ||
                             it.className?.contains("Input") == true)
        }

    private fun findFirstInput(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        searchNode(root) {
            it.isEnabled && it.isVisibleToUser &&
            (it.className?.contains("EditText") == true ||
             it.inputType != 0)
        }

    private fun findScrollableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        searchNode(root) { it.isScrollable }

    private fun searchNode(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = searchNode(child, predicate)
            if (found != null) { child.recycle(); return found }
            child.recycle()
        }
        return null
    }

    // ── Result helpers ────────────────────────────────────────────────────────

    private fun makeResult(action: String, success: Boolean, message: String) =
        JSONObject().apply {
            put("action", action)
            put("success", success)
            put("message", message)
            put("timestamp", System.currentTimeMillis())
        }

    private fun writeResults() {
        try {
            controlDir.mkdirs()
            val out = JSONObject().apply {
                put("executed_at", System.currentTimeMillis())
                put("results", JSONArray().also { arr -> results.forEach { arr.put(it) } })
            }
            FileWriter(resultFile).use { it.write(out.toString(2)) }
        } catch (e: Exception) {
            toast("⚠️ Could not write results: ${e.message}")
        }
    }

    private fun toast(msg: String, long: Boolean = false) {
        mainHandler.post {
            Toast.makeText(applicationContext, msg,
                if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
        }
    }
}
