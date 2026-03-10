package com.example.metareel

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ReelAccessibilityService : AccessibilityService() {

    private var lastReelIdentifier: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName?.toString() != INSTAGRAM_PACKAGE) return

        val root = rootInActiveWindow ?: return
        val detectedSection = detectSection(root)
        CounterStore.updateSection(detectedSection)

        // Heuristic: If we are in the "reels" section, track the "identifier" of the current reel.
        // If the identifier changes, it means the user scrolled to a new reel.
        if (detectedSection == "reels") {
            val currentIdentifier = findReelIdentifier(root)
            if (currentIdentifier != null && currentIdentifier != lastReelIdentifier) {
                Log.d(TAG, "Reel changed from $lastReelIdentifier to $currentIdentifier")
                lastReelIdentifier = currentIdentifier
                CounterStore.incrementScroll(detectedSection)
            }
        }

        // Keep the old scroll event detection as a fallback
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            Log.d(TAG, "Standard Scroll Event detected in $detectedSection")
            // To avoid double counting with the heuristic, we could add logic here, 
            // but for now let's see if the heuristic works better.
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
            
            // Instagram bottom tabs often have contentDescription like "Reels, Tab 3 of 5"
            // and the 'selected' state is usually reliable.
            if ((desc.contains(label, true) || txt.contains(label, true)) && 
                (current.isSelected || current.isFocused)) {
                return true
            }
            
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let(stack::add)
            }
        }
        return false
    }

    /**
     * Attempts to find a unique string for the current reel (e.g., username + caption snippet).
     */
    private fun findReelIdentifier(root: AccessibilityNodeInfo): String? {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        
        var username: String? = null
        // We look for patterns typical in Reels: usernames often don't have spaces and are near the bottom
        // This is a simplified heuristic.
        
        while (stack.isNotEmpty()) {
            val node = stack.removeFirst()
            val text = node.text?.toString().orEmpty()
            
            // Heuristic for Instagram username in Reels: usually starts with @ or is a single word
            // or we look for nodes that are clickable and have short text.
            if (node.isClickable && text.isNotEmpty() && !text.contains(" ") && text.length > 2) {
                 // Likely a username node
                 username = text
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(stack::add)
            }
        }
        return username
    }

    private fun detectPlayingState(root: AccessibilityNodeInfo): Boolean {
        val cues = listOf("Pause", "Double tap to pause", "Video Player")
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeFirst()
            val desc = node.contentDescription?.toString().orEmpty()
            if (cues.any { desc.contains(it, true) }) {
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
