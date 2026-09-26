package com.example.ui.screens.player

import android.content.Context
import com.example.data.repository.HistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale

object PlaybackSyncStore {
    private val positionMap = ConcurrentHashMap<String, Long>()
    private val scope = CoroutineScope(Dispatchers.IO)

    fun setPosition(key: String, position: Long) {
        if (key.isNotBlank() && position >= 0L) {
            positionMap[key] = position
            if (key.contains("_")) {
                val parentKey = key.substringBefore("_")
                if (parentKey.isNotBlank()) {
                    positionMap[parentKey] = position
                }
            }
        }
    }

    fun setPositionAndPersist(context: Context, key: String, position: Long, duration: Long) {
        if (key.isBlank()) return
        setPosition(key, position)
        if (position > 0L) {
            scope.launch {
                try {
                    val repo = HistoryRepository(context)
                    repo.updateWatchProgress(key, position, duration)
                    if (key.contains("_")) {
                        val parentKey = key.substringBefore("_")
                        if (parentKey.isNotBlank()) {
                            repo.updateWatchProgress(parentKey, position, duration)
                        }
                    }
                    val mediaId = if (key.contains("_")) key.substringBefore("_") else key
                    val epId = if (key.contains("_")) key.substringAfter("_") else null
                    com.example.utils.LastPlaybackStore.updatePosition(
                        context = context,
                        mediaId = mediaId,
                        episodeId = epId,
                        positionMillis = position,
                        durationMillis = duration
                    )
                } catch (e: Exception) {
                    // Ignore transient errors
                }
            }
        }
    }

    fun getPosition(key: String): Long {
        if (key.isBlank()) return 0L
        return positionMap[key] ?: (if (key.contains("_")) positionMap[key.substringBefore("_")] ?: 0L else 0L)
    }

    fun clearPosition(key: String) {
        positionMap.remove(key)
    }

    fun formatTime(millis: Long): String {
        if (millis <= 0L) return "00:00"
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun getProgressFraction(position: Long, duration: Long): Float {
        if (duration <= 0L || position <= 0L) return 0f
        return (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }
}
