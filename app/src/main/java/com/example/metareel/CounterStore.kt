package com.example.metareel

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Data class representing the current state of Instagram Reel statistics
 */
data class ReelStats(
    val isPlaying: Boolean = false,
    val currentSection: String = "unknown",
    val totalScrolls: Int = 0,
    val rawScrolls: Int = 0,
    val sectionScrolls: Map<String, Int> = emptyMap(),
    val sectionRawScrolls: Map<String, Int> = emptyMap(),
    // Real-time overlay data
    val currentUsername: String = "",
    val currentLikes: String = "",
    val isAd: Boolean = false
)

/**
 * CounterStore is the single source of truth for all reel statistics.
 * It uses Kotlin StateFlow to provide reactive updates to all subscribers.
 */
object CounterStore {
    private const val TAG = "CounterStore"
    
    private val _stats = MutableStateFlow(ReelStats())
    val stats: StateFlow<ReelStats> = _stats.asStateFlow()

    /**
     * Updates the current Instagram section (Home, Reels, etc.)
     */
    fun updateSection(section: String) {
        _stats.update { currentStats ->
            currentStats.copy(currentSection = section)
        }
        Log.d(TAG, "Section updated to: $section")
    }

    /**
     * Updates whether a reel is currently playing
     */
    fun updatePlaying(isPlaying: Boolean) {
        _stats.update { currentStats ->
            currentStats.copy(isPlaying = isPlaying)
        }
    }

    /**
     * Updates the current reel information displayed in the overlay
     * @param username The Instagram username of the reel creator
     * @param likes The number of likes (can include K/M suffixes)
     * @param isAd Whether the current content is an advertisement
     */
    fun updateReelInfo(username: String, likes: String, isAd: Boolean) {
        _stats.update { currentStats ->
            currentStats.copy(
                currentUsername = username,
                currentLikes = likes,
                isAd = isAd
            )
        }
    }

    /**
     * Increments the unique scroll counter for a specific section.
     * This represents actual unique reels viewed, not just scroll events.
     * @param section The section where the scroll occurred (e.g., "reels", "home")
     */
    fun incrementScroll(section: String) {
        _stats.update { currentStats ->
            val updatedSectionScrolls = currentStats.sectionScrolls.toMutableMap()
            updatedSectionScrolls[section] = (updatedSectionScrolls[section] ?: 0) + 1
            
            val newTotal = currentStats.totalScrolls + 1
            
            Log.d(TAG, "Unique scroll incremented: section=$section, total=$newTotal")
            
            currentStats.copy(
                totalScrolls = newTotal,
                sectionScrolls = updatedSectionScrolls
            )
        }
    }

    /**
     * Increments the raw scroll counter for a specific section.
     * This tracks all scroll events, including repeated scrolls on the same reel.
     * @param section The section where the scroll occurred
     */
    fun incrementRawScroll(section: String) {
        _stats.update { currentStats ->
            val updatedRawScrolls = currentStats.sectionRawScrolls.toMutableMap()
            updatedRawScrolls[section] = (updatedRawScrolls[section] ?: 0) + 1
            
            currentStats.copy(
                rawScrolls = currentStats.rawScrolls + 1,
                sectionRawScrolls = updatedRawScrolls
            )
        }
    }

    /**
     * Resets all statistics to their initial state
     */
    fun reset() {
        _stats.value = ReelStats()
        Log.d(TAG, "All statistics reset")
    }

    /**
     * Gets the current statistics snapshot (non-reactive)
     */
    fun getCurrentStats(): ReelStats = _stats.value

    /**
     * Gets scroll count for a specific section
     */
    fun getScrollCountForSection(section: String): Int {
        return _stats.value.sectionScrolls[section] ?: 0
    }

    /**
     * Gets raw scroll count for a specific section
     */
    fun getRawScrollCountForSection(section: String): Int {
        return _stats.value.sectionRawScrolls[section] ?: 0
    }
}

// Made with Bob
