package com.example.data.repository

import android.content.Context
import com.example.data.db.AppDatabase
import com.example.data.model.HistoryItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class HistoryRepository(context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val historyDao = db.historyDao()
    private val downloadDao = db.downloadDao()
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val dismissedPrefs = context.getSharedPreferences("history_dismissed_prefs", Context.MODE_PRIVATE)

    private fun getDismissedIds(): Set<String> {
        return dismissedPrefs.getStringSet("dismissed_ids", emptySet()) ?: emptySet()
    }

    private fun dismissItem(id: String) {
        val current = getDismissedIds().toMutableSet()
        current.add(id)
        if (id.contains("_")) {
            current.add(id.substringBefore("_"))
        }
        dismissedPrefs.edit().putStringSet("dismissed_ids", current).apply()
    }

    private fun unDismissItem(id: String) {
        val current = getDismissedIds().toMutableSet()
        if (current.remove(id) || (id.contains("_") && current.remove(id.substringBefore("_")))) {
            dismissedPrefs.edit().putStringSet("dismissed_ids", current).apply()
        }
    }

    fun getHistoryItems(): Flow<List<HistoryItem>> {
        return combine(historyDao.getAllHistory(), downloadDao.getAllItems()) { history, downloads ->
            val dismissed = getDismissedIds()
            val result = mutableListOf<HistoryItem>()
            result.addAll(history.filter { !dismissed.contains(it.id) && !dismissed.contains(it.id.substringBefore("_")) })
            
            // Include completed downloads even if they haven't been watched online yet
            val completedDownloads = downloads.filter { it.isCompleted }
            for (dl in completedDownloads) {
                val mediaId = dl.mediaId.ifEmpty { dl.id }
                if (dismissed.contains(mediaId) || dismissed.contains(dl.id)) continue
                val alreadyExists = result.any { it.id == mediaId || it.id == dl.id || it.title.equals(dl.title, ignoreCase = true) }
                if (!alreadyExists) {
                    result.add(
                        HistoryItem(
                            id = mediaId,
                            title = dl.title,
                            posterUrl = dl.posterUrl,
                            isMovie = dl.isMovie,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            }
            result
        }
    }

    suspend fun addToHistory(item: HistoryItem) {
        unDismissItem(item.id)
        historyDao.insertHistory(item)
        auth.currentUser?.uid?.let { uid ->
            try {
                firestore.collection("users").document(uid).collection("history").document(item.id)
                    .set(item, com.google.firebase.firestore.SetOptions.merge())
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    suspend fun getHistoryItem(id: String): HistoryItem? {
        val item = historyDao.getHistoryItemById(id)
        if (item != null) return item
        if (id.contains("_")) {
            return historyDao.getHistoryItemById(id.substringBefore("_"))
        }
        return null
    }

    suspend fun deleteHistoryItem(id: String) {
        dismissItem(id)
        historyDao.deleteHistoryItemById(id)
        if (id.contains("_")) {
            historyDao.deleteHistoryItemById(id.substringBefore("_"))
        }
        auth.currentUser?.uid?.let { uid ->
            try {
                firestore.collection("users").document(uid).collection("history").document(id).delete()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    suspend fun updateWatchProgress(id: String, position: Long, duration: Long) {
        unDismissItem(id)
        val now = System.currentTimeMillis()
        var existing = historyDao.getHistoryItemById(id)
        var targetId = id
        if (existing == null && id.contains("_")) {
            val mediaId = id.substringBefore("_")
            existing = historyDao.getHistoryItemById(mediaId)
            if (existing != null) targetId = mediaId
        }
        if (existing != null) {
            val effectiveDuration = if (duration > 0L) duration else existing.durationMillis
            val updated = existing.copy(id = targetId, positionMillis = position, durationMillis = effectiveDuration, timestamp = now)
            historyDao.insertHistory(updated)
            auth.currentUser?.uid?.let { uid ->
                try {
                    firestore.collection("users").document(uid).collection("history").document(targetId)
                        .set(updated, com.google.firebase.firestore.SetOptions.merge())
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }
}