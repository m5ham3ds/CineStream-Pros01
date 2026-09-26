package com.example.extension.managed.repository

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Production implementation of ManagedExtensionRemoteDataSource querying Firestore at canonical path:
 * /managed_extensions/{extensionId}
 */
class FirebaseFirestoreManagedExtensionDataSource(
    private val firestoreProvider: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() }
) : ManagedExtensionRemoteDataSource {

    companion object {
        const val COLLECTION_PATH = "managed_extensions"
    }

    override suspend fun fetchManagedExtensionDtos(): Result<List<ManagedExtensionDto>> =
        suspendCancellableCoroutine { continuation ->
            try {
                val firestore = firestoreProvider()
                val query = firestore.collection(COLLECTION_PATH)

                query.get()
                    .addOnSuccessListener { snapshot ->
                        val dtos = snapshot.documents.mapNotNull { doc ->
                            try {
                                ManagedExtensionDto.fromDocument(doc)
                            } catch (e: Exception) {
                                // Skip individual malformed documents safely
                                null
                            }
                        }
                        if (continuation.isActive) {
                            continuation.resume(Result.success(dtos))
                        }
                    }
                    .addOnFailureListener { exception ->
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(exception))
                        }
                    }
            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resume(Result.failure(e))
                }
            }
        }
}
