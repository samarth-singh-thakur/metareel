package com.example.metareel

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ReelAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName?.toString() != INSTAGRAM_PACKAGE) return

        val root = rootInActiveWindow ?: return
        val detectedSection = detectSection(root)
        CounterStore.updateSection(detectedSection)

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            CounterStore.incrementScroll(detectedSection)
        }

        val playing = detectPlayingState(root)
        CounterStore.updatePlaying(playing)
    }

    override fun onInterrupt() = Unit

    private fun detectSection(root: AccessibilityNodeInfo): String {
        val sectionCandidates = listOf("Home", "Search", "Reels", "Shop", "Profile")
        sectionCandidates.forEach { label ->
            if (isBottomTabSelected(root, label)) return label.lowercase()
        }
        return "unknown"
    }

    private fun isBottomTabSelected(node: AccessibilityNodeInfo, label: String): Boolean {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(node)
        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            val desc = current.contentDescription?.toString().orEmpty()
            val txt = current.text?.toString().orEmpty()
            if ((desc.contains(label, true) || txt.contains(label, true)) && current.isSelected) {
                return true
            }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let(stack::add)
            }
        }
        return false
    }

    private fun detectPlayingState(root: AccessibilityNodeInfo): Boolean {
        val cues = listOf("Pause", "Double tap to pause", "Playing")
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeFirst()
            val text = node.text?.toString().orEmpty()
            val desc = node.contentDescription?.toString().orEmpty()
            if (cues.any { text.contains(it, true) || desc.contains(it, true) }) {
                return true
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(stack::add)
            }
        }
        return false
    }

    companion object {
        private const val TAG = "ReelA11yService"
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"
    }
}
