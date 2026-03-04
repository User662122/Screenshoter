package com.uitreecapture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.io.File
import java.util.Date

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
        val controllerDir = File(Environment.getExternalStorageDirectory(), "Controller")
        val scriptFile = File(controllerDir, "script.txt")
        val resultFile = File(controllerDir, "result.txt")

        if (!scriptFile.exists()) {
            showToast("script.txt not found")
            return
        }

        try {
            resultFile.writeText("Script Execution Started: ${Date()}\n\n")
            val lines = scriptFile.readLines()

            Thread {
                for (lineRaw in lines) {
                    val line = lineRaw.trim()
                    if (line.isEmpty() || line.startsWith("#")) continue

                    val parts = line.split(" ", limit = 3)
                    val command = parts[0].uppercase()
                    var status = "SUCCESS"
                    var message = ""

                    try {
                        when (command) {
                            "OPEN_KIWI" -> openKiwi()
                            "NEW_TAB" -> openNewTab()
                            "NEW_INCOGNITO" -> openNewIncognito()
                            "GOTO" -> if (parts.size > 1) openUrl(parts[1]) else throw Exception("URL missing")
                            "CLICK" -> if (parts.size > 1) clickNode(parts[1]) else throw Exception("Target missing")
                            "TYPE" -> if (parts.size > 2) typeText(parts[1], parts[2]) else throw Exception("Target or text missing")
                            "CHECK" -> if (parts.size > 1) checkNode(parts[1], true) else throw Exception("Target missing")
                            "UNCHECK" -> if (parts.size > 1) checkNode(parts[1], false) else throw Exception("Target missing")
                            "SCROLL" -> scrollDown()
                            "CLICK_AREA" -> if (parts.size > 1) clickArea(parts[1]) else throw Exception("Coordinates missing")
                            "WAIT" -> {
                                if (parts.size > 1) {
                                    val waitTime = parts[1].toLong()
                                    Thread.sleep(waitTime)
                                    message = "Waited ${waitTime}ms"
                                }
                            }
                            else -> throw Exception("Unknown command: $command")
                        }
                    } catch (e: Exception) {
                        status = "FAILED"
                        message = e.message ?: "Unknown error"
                    }

                    resultFile.appendText("[$status] $line ${if (message.isNotEmpty()) " - $message" else ""}\n")

                    if (command != "WAIT") {
                        Thread.sleep(1000) // Default delay between commands
                    }
                }
                resultFile.appendText("\nScript Execution Finished: ${Date()}\n")
            }.start()
        } catch (e: Exception) {
            showToast("Script Error: ${e.message}")
            resultFile.appendText("\nFatal Error: ${e.message}\n")
        }
    }

    private fun openKiwi() {
        val intent = packageManager.getLaunchIntentForPackage("com.kiwibrowser.browser")
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            showToast("Opening Kiwi Browser")
        } else {
            throw Exception("Kiwi Browser not installed")
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
        intent.setPackage("com.kiwibrowser.browser")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        showToast("Navigating to $url")
    }

    private fun openNewTab() {
        clickNode("New tab")
    }

    private fun openNewIncognito() {
        clickNode("New incognito tab")
    }

    private fun clickNode(target: String) {
        val root = rootInActiveWindow ?: throw Exception("Window root is null")
        val node = findNode(root, target)
        if (node != null) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } else {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                dispatchClick(rect.centerX().toFloat(), rect.centerY().toFloat())
            }
            showToast("Clicked: $target")
        } else {
            throw Exception("Node not found: $target")
        }
        root.recycle()
    }

    private fun typeText(target: String, text: String) {
        val root = rootInActiveWindow ?: throw Exception("Window root is null")
        val node = findNode(root, target)
        if (node != null) {
            val arguments = Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            showToast("Typed into $target")
        } else {
            throw Exception("Input field not found: $target")
        }
        root.recycle()
    }

    private fun checkNode(target: String, check: Boolean) {
        val root = rootInActiveWindow ?: throw Exception("Window root is null")
        val node = findNode(root, target)
        if (node != null) {
            if (node.isChecked != check) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            showToast("Checked/Unchecked: $target")
        } else {
            throw Exception("Checkbox not found: $target")
        }
        root.recycle()
    }

    private fun findNode(root: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {
        val nodesById = root.findAccessibilityNodeInfosByViewId(target)
        if (nodesById != null && nodesById.isNotEmpty()) return nodesById[0]

        val nodesByText = root.findAccessibilityNodeInfosByText(target)
        if (nodesByText != null && nodesByText.isNotEmpty()) return nodesByText[0]

        return recursiveFindNode(root, target)
    }

    private fun recursiveFindNode(node: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {
        if (node.text?.toString()?.contains(target, true) == true) return node
        if (node.contentDescription?.toString()?.contains(target, true) == true) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = recursiveFindNode(child, target)
            if (result != null) return result
            child.recycle()
        }
        return null
    }

    private fun dispatchClick(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun clickArea(area: String) {
        try {
            val coords = area.split(",").map { it.trim().toFloat() }
            if (coords.size >= 4) {
                val centerX = (coords[0] + coords[2]) / 2
                val centerY = (coords[1] + coords[3]) / 2
                dispatchClick(centerX, centerY)
            } else if (coords.size >= 2) {
                dispatchClick(coords[0], coords[1])
            } else {
                throw Exception("Invalid coordinates")
            }
        } catch (e: Exception) {
            throw Exception("Invalid area format: $area")
        }
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

    private fun showToast(msg: String) {
        handler.post {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
