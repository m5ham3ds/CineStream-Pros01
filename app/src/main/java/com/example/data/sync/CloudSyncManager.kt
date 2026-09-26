package com.example.data.sync

import android.content.Context
import com.example.data.db.AppDatabase
import com.example.data.model.HistoryItem
import com.example.data.model.LibraryItem
import com.example.data.model.WatchedEpisode
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.firstOrNull

class CloudSyncManager(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun syncFromCloud(userId: String) {
        val userDoc = firestore.collection("users").document(userId)
        
        try {
            // 1. Push local data to cloud (in case they used the app as guest)
            val localLibrary = db.libraryDao().getAllItems().firstOrNull() ?: emptyList()
            localLibrary.forEach { userDoc.collection("library").document(it.libraryId).set(it.toFirestoreMap(), com.google.firebase.firestore.SetOptions.merge()) }

            val localHistory = db.historyDao().getAllHistory().firstOrNull() ?: emptyList()
            localHistory.forEach { userDoc.collection("history").document(it.id).set(it, com.google.firebase.firestore.SetOptions.merge()) }

            val localEpisodes = db.watchedEpisodeDao().getAllWatched().firstOrNull() ?: emptyList()
            localEpisodes.forEach { userDoc.collection("watched_episodes").document(it.id).set(it, com.google.firebase.firestore.SetOptions.merge()) }

            // 2. Pull cloud data to local with robust backward compatibility
            val librarySnapshot = userDoc.collection("library").get().await()
            val libraryItems = librarySnapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                LibraryItem.fromFirestoreMap(doc.id, data)
            }
            libraryItems.forEach { db.libraryDao().insertItem(it) }

            // History
            val historySnapshot = userDoc.collection("history").get().await()
            val historyItems = historySnapshot.toObjects(HistoryItem::class.java)
            historyItems.forEach { db.historyDao().insertHistory(it) }

            // Watched Episodes
            val episodesSnapshot = userDoc.collection("watched_episodes").get().await()
            val episodes = episodesSnapshot.toObjects(WatchedEpisode::class.java)
            episodes.forEach { db.watchedEpisodeDao().insert(it) }

            // 3. Notification Preferences Sync
            try {
                com.example.data.repository.NotificationPreferencesRepository(context).syncWithFirestore(userId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun clearLocalData() {
        db.libraryDao().clearAll()
        db.historyDao().clearAll()
        db.watchedEpisodeDao().clearAll()
        try {
            com.example.data.repository.NotificationPreferencesRepository(context).resetToDefaults()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
