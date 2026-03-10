package com.example.metareel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class ReelStats(
    val isPlaying: Boolean = false,
    val currentSection: String = "unknown",
    val totalScrolls: Int = 0,
    val sectionScrolls: Map<String, Int> = emptyMap()
)

object CounterStore {
    private val _stats = MutableStateFlow(ReelStats())
    val stats: StateFlow<ReelStats> = _stats

    fun updateSection(section: String) {
        _stats.update { it.copy(currentSection = section) }
    }

    fun updatePlaying(isPlaying: Boolean) {
        _stats.update { it.copy(isPlaying = isPlaying) }
    }

    fun incrementScroll(section: String) {
        _stats.update {
            val updatedMap = it.sectionScrolls.toMutableMap()
            updatedMap[section] = (updatedMap[section] ?: 0) + 1
            it.copy(totalScrolls = it.totalScrolls + 1, sectionScrolls = updatedMap)
        }
    }

    fun reset() {
        _stats.value = ReelStats()
    }
}
