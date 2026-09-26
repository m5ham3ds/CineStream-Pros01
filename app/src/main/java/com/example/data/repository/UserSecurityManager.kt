package com.example.data.repository

import android.util.Log
import com.example.data.model.UserRestrictions
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object UserSecurityManager {
    private const val TAG = "UserSecurityManager"
    private var listenerRegistration: ListenerRegistration? = null
    private var adminListenerRegistration: ListenerRegistration? = null

    private val _restrictionsFlow = MutableStateFlow(UserRestrictions())
    val restrictionsFlow: StateFlow<UserRestrictions> = _restrictionsFlow.asStateFlow()

    private val _isAdminDocFlow = MutableStateFlow(false)
    val isAdminDocFlow: StateFlow<Boolean> = _isAdminDocFlow.asStateFlow()

    var restrictions: UserRestrictions = UserRestrictions()
        private set

    fun listenToUserSecurity(uid: String, onSecurityChanged: ((UserRestrictions) -> Unit)? = null): ListenerRegistration? {
        if (uid.isBlank()) {
            reset()
            return null
        }

        listenerRegistration?.remove()
        adminListenerRegistration?.remove()

        // 1. Listen to /admins/{uid} to verify enabled == true per Canonical Admin Contract
        adminListenerRegistration = FirebaseFirestore.getInstance()
            .collection("admins")
            .document(uid)
            .addSnapshotListener { adminSnap, adminErr ->
                if (adminErr != null) {
                    Log.w(TAG, "Admin doc listen error: ", adminErr)
                    return@addSnapshotListener
                }
                val isDocAdmin = adminSnap != null && adminSnap.exists() && (adminSnap.getBoolean("enabled") == true)
                _isAdminDocFlow.value = isDocAdmin
                Log.d(TAG, "Admin status for $uid: enabled=$isDocAdmin")
            }

        // 2. Listen to /users/{uid}
        listenerRegistration = FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Listen failed for user security: ", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val rawRole = snapshot.getString("role") ?: "user"
                    val subTier = snapshot.getString("subscriptionTier")
                        ?: snapshot.getString("plan")
                        ?: "free"
                    val subStatus = snapshot.getString("subscriptionStatus") ?: "none"
                    val subExpiresAt = when (val s = snapshot.get("subscriptionExpiresAt")) {
                        is Timestamp -> s.toDate().time
                        is Number -> s.toLong()
                        else -> null
                    }

                    val isPrem = snapshot.getBoolean("isPremium")
                        ?: (subTier.equals("premium", ignoreCase = true) || subTier.equals("vip", ignoreCase = true))
                        ?: (rawRole.equals("vip", ignoreCase = true))
                        ?: false

                    val isActive = snapshot.getBoolean("isActive") ?: true
                    val deviceLimit = snapshot.getLong("deviceLimit")?.toInt()
                        ?: snapshot.getLong("maxDevices")?.toInt()

                    val isBanned = snapshot.getBoolean("isBanned") ?: false
                    val banReason = snapshot.getString("banReason") ?: ""
                    val banExpiresAt = when (val b = snapshot.get("banExpiresAt")) {
                        is Timestamp -> b.toDate().time
                        is Number -> b.toLong()
                        else -> null
                    }

                    val updated = UserRestrictions(
                        isActive = isActive,
                        isPremium = isPrem,
                        role = rawRole,
                        subscriptionTier = subTier,
                        subscriptionStatus = subStatus,
                        subscriptionExpiresAt = subExpiresAt,
                        deviceLimit = deviceLimit,
                        allowedQuality = snapshot.getString("allowedQuality"),
                        downloadLimit = snapshot.getLong("downloadLimit")?.toInt(),
                        isBanned = isBanned,
                        banReason = banReason,
                        banExpiresAt = banExpiresAt,
                        canWatch = snapshot.getBoolean("canWatch") ?: true,
                        watchBan = snapshot.getBoolean("watchBan") ?: false,
                        canDownload = snapshot.getBoolean("canDownload") ?: true,
                        downloadBan = snapshot.getBoolean("downloadBan") ?: false,
                        canChat = snapshot.getBoolean("canChat") ?: true,
                        chatBan = snapshot.getBoolean("chatBan") ?: false,
                        canStory = snapshot.getBoolean("canStory") ?: true,
                        storyBan = snapshot.getBoolean("storyBan") ?: false,
                        canP2P = snapshot.getBoolean("canP2P") ?: true,
                        p2pBan = snapshot.getBoolean("p2pBan") ?: false,
                        canComment = snapshot.getBoolean("canComment") ?: true,
                        canUpload = snapshot.getBoolean("canUpload") ?: true,
                        canRequest = snapshot.getBoolean("canRequest") ?: true,
                        offlineDaysOverride = snapshot.getLong("offlineDaysOverride")?.toInt(),
                        forcedAdsOverride = snapshot.getLong("forcedAdsOverride")?.toInt()
                    )
                    restrictions = updated
                    _restrictionsFlow.value = updated
                    
                    // Sync with AuthRepository currentUserFlow
                    val current = AuthRepository.currentUserFlow.value
                    if (current != null && current.uid == uid) {
                        AuthRepository.currentUserFlow.value = current.copy(
                            isActive = updated.isActive,
                            isPremium = updated.isPremium,
                            role = updated.role,
                            subscriptionTier = updated.subscriptionTier,
                            subscriptionStatus = updated.subscriptionStatus,
                            subscriptionExpiresAt = updated.subscriptionExpiresAt,
                            deviceLimit = updated.deviceLimit,
                            isBanned = updated.isBanned,
                            banReason = updated.banReason,
                            banExpiresAt = updated.banExpiresAt,
                            canWatch = updated.canWatch,
                            watchBan = updated.watchBan,
                            canDownload = updated.canDownload,
                            downloadBan = updated.downloadBan,
                            canChat = updated.canChat,
                            chatBan = updated.chatBan,
                            canStory = updated.canStory,
                            storyBan = updated.storyBan,
                            canP2P = updated.canP2P,
                            p2pBan = updated.p2pBan,
                            canComment = updated.canComment,
                            canUpload = updated.canUpload,
                            canRequest = updated.canRequest,
                            offlineDaysOverride = updated.offlineDaysOverride,
                            forcedAdsOverride = updated.forcedAdsOverride
                        )
                    }

                    onSecurityChanged?.invoke(updated)
                    Log.d(TAG, "Updated user security restrictions for $uid: isBanned=${updated.isBanned}, role=${updated.role}")
                }
            }

        return listenerRegistration
    }

    fun stopListening() {
        listenerRegistration?.remove()
        listenerRegistration = null
        adminListenerRegistration?.remove()
        adminListenerRegistration = null
    }

    fun reset() {
        stopListening()
        restrictions = UserRestrictions()
        _restrictionsFlow.value = restrictions
        _isAdminDocFlow.value = false
    }

    // Fast check helpers matching CineStream Admin rules
    fun canWatch(): Boolean = restrictions.isWatchAllowed
    fun canDownload(): Boolean = restrictions.isDownloadAllowed
    fun canChat(): Boolean = restrictions.isChatAllowed
    fun canPostStory(): Boolean = restrictions.isStoryAllowed
    fun canPostStories(): Boolean = restrictions.isStoryAllowed
    fun canUseP2P(): Boolean = restrictions.isP2PAllowed
    fun canShareP2P(): Boolean = restrictions.isP2PAllowed
    fun canComment(): Boolean = restrictions.isCommentAllowed
    fun canUpload(): Boolean = restrictions.isUploadAllowed
    fun canRequest(): Boolean = restrictions.isRequestAllowed
    fun isBanned(): Boolean = restrictions.isBanActive
    fun getBanReason(): String = restrictions.banReason
    fun isAdmin(): Boolean = _isAdminDocFlow.value || restrictions.isAdmin
    fun isVip(): Boolean = restrictions.isPremium || restrictions.isSubscriptionActive

    fun getForcedAdsRequired(defaultGlobal: Int = 5): Int {
        if (isVip()) return 0
        return restrictions.forcedAdsOverride ?: defaultGlobal
    }

    fun getOfflineDaysLimit(defaultGlobal: Int = 2): Int {
        return restrictions.offlineDaysOverride ?: defaultGlobal
    }
}
