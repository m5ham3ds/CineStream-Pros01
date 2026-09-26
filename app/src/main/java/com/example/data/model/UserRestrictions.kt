package com.example.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class UserRestrictions(
    val isActive: Boolean = true,
    val isPremium: Boolean = false,
    val role: String = "user",
    val subscriptionTier: String = "free",
    val subscriptionStatus: String = "none",
    val subscriptionExpiresAt: Long? = null,
    val deviceLimit: Int? = null,
    val allowedQuality: String? = null,
    val downloadLimit: Int? = null,
    
    // Account ban status
    val isBanned: Boolean = false,
    val banReason: String = "",
    val banExpiresAt: Long? = null,
    
    // Feature Permissions
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
    val forcedAdsOverride: Int? = null
) {
    // Backwards compatibility aliases
    val plan: String get() = subscriptionTier
    val maxDevices: Int? get() = deviceLimit

    val isBanActive: Boolean
        get() = isBanned && (banExpiresAt == null || banExpiresAt > System.currentTimeMillis())

    val isSubscriptionActive: Boolean
        get() {
            if (isPremium || subscriptionTier.equals("premium", ignoreCase = true) || subscriptionTier.equals("vip", ignoreCase = true)) {
                return subscriptionExpiresAt == null || subscriptionExpiresAt > System.currentTimeMillis()
            }
            return false
        }

    val isWatchAllowed: Boolean get() = !isBanActive && canWatch && !watchBan
    val isDownloadAllowed: Boolean get() = !isBanActive && canDownload && !downloadBan
    val isChatAllowed: Boolean get() = !isBanActive && canChat && !chatBan
    val isStoryAllowed: Boolean get() = !isBanActive && canStory && !storyBan
    val isP2PAllowed: Boolean get() = !isBanActive && canP2P && !p2pBan
    val isCommentAllowed: Boolean get() = !isBanActive && canComment
    val isUploadAllowed: Boolean get() = !isBanActive && canUpload
    val isRequestAllowed: Boolean get() = !isBanActive && canRequest

    val isAdmin: Boolean get() = role.equals("admin", true) || role.equals("superadmin", true)
}
