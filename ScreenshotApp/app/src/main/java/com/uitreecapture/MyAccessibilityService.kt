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

                    // Improved parsing: Split only once at the first space to get command and the rest as arguments
                    val firstSpace = line.indexOf(' ')
                    val command: String
                    val arguments: String
                    if (firstSpace != -1) {
                        command = line.substring(0, firstSpace).uppercase()
                        arguments = line.substring(firstSpace + 1).trim()
                    } else {
                        command = line.uppercase()
                        arguments = ""
                    }

                    var status = "SUCCESS"
                    var message = ""

                    try {
                        when (command) {
                            "OPEN_KIWI" -> openKiwi()
                            "NEW_TAB" -> openNewTab()
                            "NEW_INCOGNITO" -> openNewIncognito()
                            "GOTO" -> if (arguments.isNotEmpty()) openUrl(arguments) else throw Exception("URL missing")
                            "CLICK" -> if (arguments.isNotEmpty()) clickNode(arguments) else throw Exception("Target missing")
                            "TYPE" -> {
                                // For TYPE, we need to split arguments into target and text
                                // We expect: TYPE "Target Name" TextToType OR TYPE TargetName TextToType
                                val typeParts = parseTypeArgs(arguments)
                                if (typeParts.size >= 2) {
                                    typeText(typeParts[0], typeParts[1])
                                } else {
                                    throw Exception("Target or text missing for TYPE")
                                }
                            }
                            "CHECK" -> if (arguments.isNotEmpty()) checkNode(arguments, true) else throw Exception("Target missing")
                            "UNCHECK" -> if (arguments.isNotEmpty()) checkNode(arguments, false) else throw Exception("Target missing")
                            "SCROLL" -> scrollDown()
                            "CLICK_AREA" -> if (arguments.isNotEmpty()) clickArea(arguments) else throw Exception("Coordinates missing")
                            "WAIT" -> {
                                if (arguments.isNotEmpty()) {
                                    val waitTime = arguments.toLong()
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

    private fun parseTypeArgs(args: String): List<String> {
        // Simple parser for TYPE arguments: supports quoted strings for multi-word targets
        val result = mutableListOf<String>()
        if (args.startsWith("\"")) {
            val endQuote = args.indexOf("\"", 1)
            if (endQuote != -1) {
                result.add(args.substring(1, endQuote))
                result.add(args.substring(endQuote + 1).trim())
            } else {
                val parts = args.split(" ", limit = 2)
                result.addAll(parts)
            }
        } else {
            val parts = args.split(" ", limit = 2)
            result.addAll(parts)
        }
        return result
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
        val cleanTarget = target.removeSurrounding("\"")
        val node = findNode(root, cleanTarget)
        if (node != null) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } else {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                dispatchClick(rect.centerX().toFloat(), rect.centerY().toFloat())
            }
            showToast("Clicked: $cleanTarget")
        } else {
            throw Exception("Node not found: $cleanTarget")
        }
        root.recycle()
    }

    private fun typeText(target: String, text: String) {
        val root = rootInActiveWindow ?: throw Exception("Window root is null")
        val cleanTarget = target.removeSurrounding("\"")
        val node = findNode(root, cleanTarget)
        if (node != null) {
            val arguments = Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            showToast("Typed into $cleanTarget")
        } else {
            throw Exception("Input field not found: $cleanTarget")
        }
        root.recycle()
    }

    private fun checkNode(target: String, check: Boolean) {
        val root = rootInActiveWindow ?: throw Exception("Window root is null")
        val cleanTarget = target.removeSurrounding("\"")
        val node = findNode(root, cleanTarget)
        if (node != null) {
            if (node.isChecked != check) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            showToast("Checked/Unchecked: $cleanTarget")
        } else {
            throw Exception("Checkbox not found: $cleanTarget")
        }
        root.recycle()
    }

    private fun findNode(root: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {
        // 1. Try exact ID
        val nodesById = root.findAccessibilityNodeInfosByViewId(target)
        if (nodesById != null && nodesById.isNotEmpty()) return nodesById[0]

        // 2. Try exact text
        val nodesByText = root.findAccessibilityNodeInfosByText(target)
        if (nodesByText != null && nodesByText.isNotEmpty()) {
            // Prefer exact matches if multiple found
            for (node in nodesByText) {
                if (node.text?.toString()?.equals(target, ignoreCase = true) == true ||
                    node.contentDescription?.toString()?.equals(target, ignoreCase = true) == true) {
                    return node
                }
            }
            return nodesByText[0]
        }

        // 3. Fallback to recursive partial match
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
