package com.example.data.repository

import android.net.Uri
import com.example.data.model.UserRestrictions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@IgnoreExtraProperties
data class User(
    val uid: String = "",
    val id: String = "",
    val email: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val displayName: String = "",
    val username: String = "",
    val photoUrl: String = "",
    val bio: String = "",
    val isProfilePublic: Boolean = true,
    
    // Subscriptions & Roles (Admin managed)
    val role: String = "user",
    val subscriptionTier: String = "free",
    val isPremium: Boolean = false,
    val subscriptionStatus: String = "none",
    val subscriptionExpiresAt: Long? = null,
    val deviceLimit: Int? = null,
    val allowedQuality: String? = null,
    val downloadLimit: Int? = null,
    
    // Status & Bans (Admin managed)
    val isActive: Boolean = true,
    val isBanned: Boolean = false,
    val banReason: String = "",
    val banExpiresAt: Long? = null,
    
    // Feature Permissions (Admin managed)
    val canWatch: Boolean = true,
    val watchBan: Boolean = false,
    val canDownload: Boolean = true,
    val downloadBan: Boolean = false,
    val canChat: Boolean = true,
    val chatBan: Boolean = false,
    val canStory: Boolean = true,
    val storyBan: Boolean = false,
    val canP2P: Boolean = true,
    val p2pBan: Boolean = false,
    val canComment: Boolean = true,
    val canUpload: Boolean = true,
    val canRequest: Boolean = true,
    
    val offlineDaysOverride: Int? = null,
    val forcedAdsOverride: Int? = null,
    
    // Timestamps
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lastLoginAt: Long = 0L,
    val lastActiveAt: Long = 0L,
    val appVersion: String = ""
) {
    // Backwards-compatible aliases
    val plan: String get() = subscriptionTier
    val maxDevices: Int? get() = deviceLimit
    val lastLoginTimestamp: Long get() = lastLoginAt

    val isBanActive: Boolean
        get() = isBanned && (banExpiresAt == null || banExpiresAt > System.currentTimeMillis())

    val isWatchAllowed: Boolean get() = !isBanActive && canWatch && !watchBan
    val isDownloadAllowed: Boolean get() = !isBanActive && canDownload && !downloadBan
    val isChatAllowed: Boolean get() = !isBanActive && canChat && !chatBan
    val isStoryAllowed: Boolean get() = !isBanActive && canStory && !storyBan
    val isP2PAllowed: Boolean get() = !isBanActive && canP2P && !p2pBan
    val isCommentAllowed: Boolean get() = !isBanActive && canComment
    val isUploadAllowed: Boolean get() = !isBanActive && canUpload
    val isRequestAllowed: Boolean get() = !isBanActive && canRequest

    val isAdmin: Boolean get() = role.equals("admin", true) || role.equals("superadmin", true)

    companion object {
        fun fromDocument(doc: DocumentSnapshot): User {
            val uid = doc.getString("uid") ?: doc.id
            val role = doc.getString("role") ?: "user"
            val subTier = doc.getString("subscriptionTier")
                ?: doc.getString("plan")
                ?: "free"
            val isPrem = doc.getBoolean("isPremium")
                ?: (subTier.equals("premium", true) || subTier.equals("vip", true))
                ?: (role.equals("vip", true))
                ?: false

            val created = when (val c = doc.get("createdAt")) {
                is Timestamp -> c.toDate().time
                is Number -> c.toLong()
                else -> 0L
            }

            val updated = when (val u = doc.get("updatedAt")) {
                is Timestamp -> u.toDate().time
                is Number -> u.toLong()
                else -> 0L
            }

            val lastLogin = when (val l = doc.get("lastLoginAt") ?: doc.get("lastLoginTimestamp")) {
                is Timestamp -> l.toDate().time
                is Number -> l.toLong()
                else -> 0L
            }

            val subExp = when (val s = doc.get("subscriptionExpiresAt")) {
                is Timestamp -> s.toDate().time
                is Number -> s.toLong()
                else -> null
            }

            val banExp = when (val b = doc.get("banExpiresAt")) {
                is Timestamp -> b.toDate().time
                is Number -> b.toLong()
                else -> null
            }

            val deviceLimit = doc.getLong("deviceLimit")?.toInt()
                ?: doc.getLong("maxDevices")?.toInt()

            return User(
                uid = uid,
                id = uid,
                email = doc.getString("email") ?: "",
                firstName = doc.getString("firstName") ?: "",
                lastName = doc.getString("lastName") ?: "",
                displayName = doc.getString("displayName") ?: "",
                username = doc.getString("username") ?: "",
                photoUrl = doc.getString("photoUrl") ?: "",
                isProfilePublic = doc.getBoolean("isProfilePublic") ?: true,
                role = role,
                subscriptionTier = subTier,
                isPremium = isPrem,
                subscriptionStatus = doc.getString("subscriptionStatus") ?: "none",
                subscriptionExpiresAt = subExp,
                deviceLimit = deviceLimit,
                allowedQuality = doc.getString("allowedQuality"),
                downloadLimit = doc.getLong("downloadLimit")?.toInt(),
                isActive = doc.getBoolean("isActive") ?: true,
                isBanned = doc.getBoolean("isBanned") ?: false,
                banReason = doc.getString("banReason") ?: "",
                banExpiresAt = banExp,
                canWatch = doc.getBoolean("canWatch") ?: true,
                watchBan = doc.getBoolean("watchBan") ?: false,
                canDownload = doc.getBoolean("canDownload") ?: true,
                downloadBan = doc.getBoolean("downloadBan") ?: false,
                canChat = doc.getBoolean("canChat") ?: true,
                chatBan = doc.getBoolean("chatBan") ?: false,
                canStory = doc.getBoolean("canStory") ?: true,
                storyBan = doc.getBoolean("storyBan") ?: false,
                canP2P = doc.getBoolean("canP2P") ?: true,
                p2pBan = doc.getBoolean("p2pBan") ?: false,
                canComment = doc.getBoolean("canComment") ?: true,
                canUpload = doc.getBoolean("canUpload") ?: true,
                canRequest = doc.getBoolean("canRequest") ?: true,
                offlineDaysOverride = doc.getLong("offlineDaysOverride")?.toInt(),
                forcedAdsOverride = doc.getLong("forcedAdsOverride")?.toInt(),
                createdAt = created,
                updatedAt = updated,
                lastLoginAt = lastLogin,
                lastActiveAt = doc.getLong("lastActiveAt") ?: 0L,
                appVersion = doc.getString("appVersion") ?: ""
            )
        }
    }
}

object AuthRepository {

    val currentUserFlow = MutableStateFlow<User?>(null)
    val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    suspend fun uploadProfilePicture(uid: String, uri: Uri): String {
        val cloudName = com.example.BuildConfig.CLOUDINARY_CLOUD_NAME
        val uploadPreset = com.example.BuildConfig.CLOUDINARY_UPLOAD_PRESET
        
        if (cloudName.isEmpty() || uploadPreset.isEmpty()) {
            throw Exception("Cloudinary configuration is missing. Please set CLOUDINARY_CLOUD_NAME and CLOUDINARY_UPLOAD_PRESET.")
        }

        try {
            com.cloudinary.android.MediaManager.get()
        } catch (e: Exception) {
            throw Exception("Cloudinary MediaManager not initialized.")
        }
        
        return suspendCancellableCoroutine<String> { continuation ->
            com.cloudinary.android.MediaManager.get().upload(uri)
                .unsigned(uploadPreset)
                .callback(object : com.cloudinary.android.callback.UploadCallback {
                    override fun onSuccess(requestId: String?, resultData: Map<*, *>?) {
                        val secureUrl = resultData?.get("secure_url") as? String
                        if (secureUrl != null) {
                            continuation.resume(secureUrl)
                        } else {
                            continuation.resumeWithException(Exception("Secure URL not found in Cloudinary response"))
                        }
                    }
                    
                    override fun onStart(requestId: String?) {}
                    override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                    override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                        continuation.resumeWithException(Exception(error?.description ?: "Cloudinary upload error"))
                    }
                    override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                        continuation.resumeWithException(Exception("Upload rescheduled"))
                    }
                }).dispatch()
        }
    }

    suspend fun getCurrentUser(): User? {
        val firebaseUser = auth.currentUser
        if (firebaseUser == null) {
            currentUserFlow.value = null
            UserSecurityManager.reset()
            return null
        }
        return try {
            val snapshot = kotlinx.coroutines.withTimeout(15000) { db.collection("users").document(firebaseUser.uid).get().await() }
            if (snapshot.exists()) {
                val user = User.fromDocument(snapshot)
                currentUserFlow.value = user
                UserSecurityManager.listenToUserSecurity(firebaseUser.uid)
                // Keep activity timestamp updated
                try {
                    val appVersion = AppStartupManager.getCurrentVersionName(com.example.MyApplication.appContext)
                    db.collection("users").document(firebaseUser.uid).update(
                        mapOf(
                            "lastActiveAt" to System.currentTimeMillis(),
                            "appVersion" to appVersion
                        )
                    )
                } catch (_: Exception) {}
                user
            } else {
                currentUserFlow.value = null
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Saves or updates user document in /users/{uid} using SetOptions.merge().
     * Strictly protects Admin-managed fields from being overwritten.
     */
    suspend fun saveUser(user: User) {
        val uid = if (user.uid.isNotBlank()) user.uid else (auth.currentUser?.uid ?: return)
        val appVersion = AppStartupManager.getCurrentVersionName(com.example.MyApplication.appContext)
        val resolvedName = "${user.firstName} ${user.lastName}".trim().ifBlank { user.username }

        val docRef = db.collection("users").document(uid)
        val existingDoc = try { docRef.get().await() } catch (_: Exception) { null }

        val userMap = mutableMapOf<String, Any>(
            "uid" to uid,
            "id" to uid,
            "email" to user.email,
            "firstName" to user.firstName,
            "lastName" to user.lastName,
            "displayName" to (if (user.displayName.isNotBlank()) user.displayName else resolvedName),
            "username" to user.username,
            "photoUrl" to user.photoUrl,
            "isProfilePublic" to user.isProfilePublic,
            "lastLoginAt" to FieldValue.serverTimestamp(),
            "lastLoginTimestamp" to System.currentTimeMillis(),
            "lastActiveAt" to System.currentTimeMillis(),
            "updatedAt" to FieldValue.serverTimestamp(),
            "appVersion" to appVersion
        )

        // Only for brand new user documents, initialize default non-privileged state
        if (existingDoc == null || !existingDoc.exists()) {
            userMap["createdAt"] = FieldValue.serverTimestamp()
            userMap["isActive"] = true
            userMap["role"] = "user"
            userMap["subscriptionTier"] = "free"
            userMap["plan"] = "free"
            userMap["isPremium"] = false
            userMap["subscriptionStatus"] = "none"
            userMap["isBanned"] = false
            userMap["banReason"] = ""
            userMap["canWatch"] = true
            userMap["canDownload"] = true
            userMap["canChat"] = true
            userMap["canStory"] = true
            userMap["canP2P"] = true
            userMap["canComment"] = true
            userMap["canUpload"] = true
            userMap["canRequest"] = true
        }

        docRef.set(userMap, SetOptions.merge()).await()

        val updatedUser = if (existingDoc != null && existingDoc.exists()) {
            User.fromDocument(existingDoc).copy(
                uid = uid,
                email = user.email,
                firstName = user.firstName,
                lastName = user.lastName,
                displayName = userMap["displayName"] as String,
                username = user.username,
                photoUrl = user.photoUrl,
                isProfilePublic = user.isProfilePublic,
                updatedAt = System.currentTimeMillis(),
                lastActiveAt = System.currentTimeMillis()
            )
        } else {
            user.copy(uid = uid, role = "user", subscriptionTier = "free", isPremium = false, isBanned = false, isActive = true)
        }

        currentUserFlow.value = updatedUser
        UserSecurityManager.listenToUserSecurity(uid)
    }

    suspend fun isUsernameTaken(username: String, currentUid: String): Boolean {
        val snapshot = db.collection("users")
            .whereEqualTo("username", username)
            .get()
            .await()
            
        for (doc in snapshot.documents) {
            if (doc.id != currentUid) return true
        }
        return false
    }

    suspend fun generateUniqueUsername(baseName: String): String {
        var base = baseName.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
        if (base.isEmpty()) base = "user"
        
        var attempt = base
        var isTaken = isUsernameTaken(attempt, "")
        var count = 1
        
        while (isTaken) {
            attempt = "${base}${count}"
            isTaken = isUsernameTaken(attempt, "")
            count++
        }
        return attempt
    }
}
