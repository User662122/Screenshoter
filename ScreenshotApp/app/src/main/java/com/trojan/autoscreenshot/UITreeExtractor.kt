package com.trojan.autoscreenshot

import android.view.accessibility.AccessibilityNodeInfo
import java.lang.StringBuilder

object UITreeExtractor {

    fun extractUITree(rootNode: AccessibilityNodeInfo?): String {
        if (rootNode == null) {
            return "ERROR: Root node is null\nTimestamp: ${System.currentTimeMillis()}"
        }

        val sb = StringBuilder()
        sb.append("=== UI TREE ===\n")
        sb.append("Timestamp: ${System.currentTimeMillis()}\n")
        sb.append("Package: ${rootNode.packageName}\n")
        sb.append("\n--- Elements ---\n")

        nodeToString(rootNode, sb, 0)

        return sb.toString()
    }

    private fun nodeToString(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        try {
            if (shouldIncludeNode(node)) {
                val indent = "  ".repeat(depth)
                
                // Get widget type from className
                val className = node.className?.toString() ?: "View"
                val widgetType = className.substringAfterLast('.')
                
                // Build properties string
                val properties = mutableListOf<String>()
                
                val text = node.text?.toString()
                if (!text.isNullOrEmpty()) {
                    properties.add("text='$text'")
                }
                
                val desc = node.contentDescription?.toString()
                if (!desc.isNullOrEmpty()) {
                    properties.add("contentDescription='$desc'")
                }
                
                val resId = node.viewIdResourceName
                if (!resId.isNullOrEmpty()) {
                    properties.add("resourceId='$resId'")
                }
                
                if (node.isClickable) properties.add("clickable=true")
                if (node.isFocusable) properties.add("focusable=true")
                if (node.isLongClickable) properties.add("longClickable=true")
                if (node.isScrollable) properties.add("scrollable=true")
                
                val propertiesStr = properties.joinToString(", ")
                sb.append("$indent$widgetType{$propertiesStr}\n")
            }

            // Process children
            val childCount = minOf(node.childCount, 50)
            for (i in 0 until childCount) {
                try {
                    val child = node.getChild(i)
                    if (child != null) {
                        nodeToString(child, sb, depth + 1)
                        child.recycle()
                    }
                } catch (e: Exception) {
                    // Skip this child on error
                }
            }

        } catch (e: Exception) {
            sb.append("ERROR: ${e.message}\n")
        }
    }

    // Helper that decides whether a node is worth sending
    private fun shouldIncludeNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) return true
        if (node.isFocusable) return true
        if (node.isLongClickable) return true
        if (node.isScrollable) return true
        if (!node.actionList.isNullOrEmpty()) return true
        return false
    }
}
