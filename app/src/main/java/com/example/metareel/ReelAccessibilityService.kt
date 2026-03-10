package com.example.metareel

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * ReelAccessibilityService monitors Instagram UI to detect and extract reel information.
 * It tracks which section the user is in (Home, Reels, etc.) and extracts real-time data
 * such as username, likes, and whether content is sponsored.
 */
class ReelAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingProcess: Runnable? = null
    private val state = DetectorState()

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "✅ Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        val pkg = event.packageName?.toString() ?: return
        
        // Only process Instagram events
        if (pkg != INSTAGRAM_PACKAGE) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SELECTED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                // Track raw scroll events
                if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                    CounterStore.incrementRawScroll(state.currentSection.name.lowercase())
                }
                scheduleProcess()
            }
        }
    }

    /**
     * Debounce processing to avoid excessive calls
     */
    private fun scheduleProcess() {
        pendingProcess?.let(handler::removeCallbacks)
        pendingProcess = Runnable { processActiveWindow() }
        handler.postDelayed(pendingProcess!!, 150L)
    }

    /**
     * Main processing logic - detects section and extracts reel data
     */
    private fun processActiveWindow() {
        val root = rootInActiveWindow ?: return

        // Detect which section of Instagram we're in
        val section = detectSection(root)
        if (section != state.currentSection) {
            state.currentSection = section
            CounterStore.updateSection(section.name.lowercase())
            Log.d(TAG, "📍 Section changed to: ${section.name}")
        }

        // Extract reel features from the current screen
        val features = extractReelFeatures(root)
        
        // Always update overlay with latest data (even if not counting as a scroll)
        // Only update if we have valid data
        if (features.username != null || features.likes != null) {
            CounterStore.updateReelInfo(
                username = features.username ?: "Unknown",
                likes = features.likes ?: "0",
                isAd = features.isAd
            )
        }

        // Only process as a potential reel scroll if we have enough confidence
        if (features.reelLikelihoodScore >= 2) {
            val snapshot = ReelSnapshot(
                section = section,
                username = features.username,
                likes = features.likes,
                isAd = features.isAd,
                caption = features.captionSnippet,
                timestamp = System.currentTimeMillis()
            )
            handleSnapshot(snapshot)
        }
    }

    /**
     * Handles reel snapshot detection with deduplication logic
     */
    private fun handleSnapshot(snapshot: ReelSnapshot) {
        val last = state.lastCommittedSnapshot

        // 1. If this snapshot is identical to the last counted one, ignore it
        if (last != null && isSameReel(snapshot, last)) {
            state.candidateSnapshot = null
            return
        }

        // 2. Candidate management: content must be different from last committed
        val candidate = state.candidateSnapshot
        if (candidate == null || !isSameReel(snapshot, candidate)) {
            // New candidate reel detected
            state.candidateSnapshot = snapshot
            state.candidateSince = snapshot.timestamp
            return
        }

        // 3. Stability check: Reel must be stable for 250ms to be counted
        val now = snapshot.timestamp
        val stableEnough = now - state.candidateSince >= 250L
        val gapOkay = now - state.lastCommittedAt >= 600L

        if (stableEnough && gapOkay) {
            state.lastCommittedSnapshot = snapshot
            state.lastCommittedAt = now
            state.candidateSnapshot = null

            // Increment the scroll counter
            CounterStore.incrementScroll(section = "reels")
            
            Log.i(TAG, "✅ REEL COUNTED | User: ${snapshot.username} | Likes: ${snapshot.likes} | Ad: ${snapshot.isAd}")
        }
    }

    /**
     * Determines if two snapshots represent the same reel
     */
    private fun isSameReel(s1: ReelSnapshot, s2: ReelSnapshot): Boolean {
        if (s1.isAd != s2.isAd) return false
        
        return if (s1.isAd) {
            // Ads often don't have stable usernames, use likes and caption
            s1.likes == s2.likes && s1.caption == s2.caption
        } else {
            // Regular reels: match by username and either likes or caption
            s1.username == s2.username && (s1.likes == s2.likes || s1.caption == s2.caption)
        }
    }

    /**
     * Detects which section of Instagram the user is currently viewing
     */
    private fun detectSection(root: AccessibilityNodeInfo): AppSection {
        // Look for selected navigation items
        if (findNodeByTextOrDesc(root, "Reels", selectedOnly = true)) {
            return AppSection.REELS
        }
        if (findNodeByTextOrDesc(root, "Home", selectedOnly = true)) {
            return AppSection.HOME
        }
        if (findNodeByTextOrDesc(root, "Search", selectedOnly = true)) {
            return AppSection.SEARCH
        }
        if (findNodeByTextOrDesc(root, "Profile", selectedOnly = true)) {
            return AppSection.PROFILE
        }
        return AppSection.OTHER
    }

    /**
     * Searches for a node with specific text or content description
     */
    private fun findNodeByTextOrDesc(
        root: AccessibilityNodeInfo,
        label: String,
        selectedOnly: Boolean
    ): Boolean {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        
        while (stack.isNotEmpty()) {
            val node = stack.removeFirst()
            val desc = node.contentDescription?.toString().orEmpty()
            val text = node.text?.toString().orEmpty()

            if (desc.contains(label, ignoreCase = true) || text.contains(label, ignoreCase = true)) {
                if (!selectedOnly || node.isSelected) {
                    return true
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(stack::add)
            }
        }
        return false
    }

    /**
     * Extracts reel features from the accessibility tree
     */
    private fun extractReelFeatures(root: AccessibilityNodeInfo): ReelFeatures {
        var username: String? = null
        var likes: String? = null
        var caption: String? = null
        var isAd = false
        var score = 0
        
        // Collect ALL text for debugging
        val allTexts = mutableListOf<String>()
        val allDescriptions = mutableListOf<String>()

        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)

        while (stack.isNotEmpty()) {
            val node = stack.removeFirst()
            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val combined = "$text $desc".trim()
            
            // Log ALL non-empty text and descriptions
            if (text.isNotEmpty()) {
                allTexts.add(text)
            }
            if (desc.isNotEmpty()) {
                allDescriptions.add(desc)
            }

            if (combined.isNotBlank()) {
                val lc = combined.lowercase()

                // Ad detection
                if (lc == "ad" || lc == "sponsored" || lc.contains("sponsored by")) {
                    isAd = true
                    score += 3
                }

                // USERNAME DETECTION - Pattern: "Reel by {username}. Double-tap to play or pause."
                if (username == null && desc.contains("Reel by ", ignoreCase = true) &&
                    desc.contains("Double-tap to play or pause", ignoreCase = true)) {
                    val extracted = extractUsernameFromReelBy(desc)
                    if (extracted != null) {
                        username = extracted
                        score += 5
                    }
                }

                // LIKES DETECTION - Pattern: "The like number is {number}. View likes."
                if (likes == null && desc.contains("The like number is", ignoreCase = true)) {
                    val extracted = extractLikesFromDescription(desc)
                    if (extracted != null) {
                        likes = extracted
                        score += 5
                    }
                }

                // Caption detection
                if (caption == null && text.length > 20 && text.contains(" ")) {
                    caption = text.take(80)
                    score += 1
                }
                
                // Additional reel indicators
                if (lc.contains("follow") || lc.contains("audio") ||
                    lc.contains("comment") || lc.contains("share")) {
                    score += 1
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(stack::add)
            }
        }
        
        // ========== RAW DATA LOGGING FOR DEBUGGING ==========
        Log.d(TAG, "========== RAW SCROLL DATA START ==========")
        Log.d(TAG, "Total text nodes found: ${allTexts.size}")
        Log.d(TAG, "Total description nodes found: ${allDescriptions.size}")
        Log.d(TAG, "")
        
        Log.d(TAG, "--- ALL TEXT NODES ---")
        allTexts.forEachIndexed { index, text ->
            Log.d(TAG, "Text[$index]: $text")
        }
        Log.d(TAG, "")
        
        Log.d(TAG, "--- ALL CONTENT DESCRIPTIONS ---")
        allDescriptions.forEachIndexed { index, desc ->
            Log.d(TAG, "Desc[$index]: $desc")
        }
        Log.d(TAG, "")
        
        Log.d(TAG, "--- DETECTED DATA ---")
        Log.d(TAG, "Selected Username: $username")
        Log.d(TAG, "Selected Likes: $likes")
        Log.d(TAG, "Is Ad: $isAd")
        Log.d(TAG, "Caption: $caption")
        Log.d(TAG, "Score: $score")
        Log.d(TAG, "========== RAW SCROLL DATA END ==========")
        Log.d(TAG, "")

        return ReelFeatures(
            username = username,
            likes = likes,
            isAd = isAd,
            captionSnippet = caption,
            reelLikelihoodScore = score
        )
    }
    
    
    /**
     * Extracts username from "Reel by {username}. Double-tap to play or pause."
     */
    private fun extractUsernameFromReelBy(description: String): String? {
        val pattern = Regex("Reel by ([^.]+)\\. Double-tap", RegexOption.IGNORE_CASE)
        val match = pattern.find(description)
        return match?.groupValues?.get(1)?.trim()
    }
    
    /**
     * Extracts likes from "The like number is {number}. View likes."
     */
    private fun extractLikesFromDescription(description: String): String? {
        val pattern = Regex("The like number is\\s*(\\d+)", RegexOption.IGNORE_CASE)
        val match = pattern.find(description)
        return match?.groupValues?.get(1)?.trim()
    }
    /**
     * Extracts like count from text like "48 likes" or "1.2K likes"
     */
    private fun extractLikeCount(text: String): String {
        // Remove "likes" and other text, keep only numbers and K/M
        val cleaned = text.replace(Regex("[^0-9.,KMkm]"), "")
        return cleaned.ifEmpty { "" }
    }

    /**
     * Checks if text looks like a like count (e.g., "123", "1.2K", "1M")
     */
    private fun isLikeNumber(text: String): Boolean {
        if (text.isEmpty() || text.length > 10) return false
        
        val cleaned = text.replace(",", "").replace(".", "")
        val hasDigits = cleaned.any { it.isDigit() }
        val hasValidSuffix = text.endsWith("K", ignoreCase = true) || 
                            text.endsWith("M", ignoreCase = true) ||
                            text.all { it.isDigit() || it == ',' || it == '.' }
        
        return hasDigits && hasValidSuffix && text.length in 1..8
    }

    /**
     * Checks if text looks like an Instagram username
     */
    private fun looksLikeUsername(text: String): Boolean {
        if (text.length !in 3..30 || text.contains(" ")) return false
        if (text.all { it.isDigit() }) return false
        
        // Instagram usernames can contain letters, numbers, underscores, and periods
        return text.all { it.isLetterOrDigit() || it == '_' || it == '.' }
    }

    override fun onInterrupt() {
        Log.d(TAG, "⚠️ Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "🛑 Accessibility service destroyed")
    }

    companion object {
        private const val TAG = "ReelA11yService"
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"
    }
}

/**
 * Represents different sections of the Instagram app
 */
enum class AppSection {
    HOME,
    REELS,
    SEARCH,
    PROFILE,
    OTHER
}

/**
 * Snapshot of a detected reel with all its metadata
 */
data class ReelSnapshot(
    val section: AppSection,
    val username: String?,
    val likes: String?,
    val isAd: Boolean,
    val caption: String?,
    val timestamp: Long
)

/**
 * Internal state for tracking reel detection
 */
data class DetectorState(
    var currentSection: AppSection = AppSection.OTHER,
    var lastCommittedSnapshot: ReelSnapshot? = null,
    var candidateSnapshot: ReelSnapshot? = null,
    var candidateSince: Long = 0L,
    var lastCommittedAt: Long = 0L
)

/**
 * Extracted features from a potential reel
 */
data class ReelFeatures(
    val username: String? = null,
    val likes: String? = null,
    val isAd: Boolean = false,
    val captionSnippet: String? = null,
    val reelLikelihoodScore: Int = 0
)

// Made with Bob
