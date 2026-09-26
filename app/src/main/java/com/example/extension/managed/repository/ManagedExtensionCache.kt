package com.example.extension.managed.repository

import com.example.extension.managed.model.ManagedExtension
import com.example.extension.managed.model.ManagedExtensionValidator
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Contract for caching verified ManagedExtension definitions.
 */
interface ManagedExtensionCache {
    suspend fun getCached(): List<ManagedExtension>?
    suspend fun saveCache(extensions: List<ManagedExtension>)
    suspend fun clear()
    fun isExpired(): Boolean
    fun getCachedTimestamp(): Long
}

/**
 * Thread-safe local cache with configurable TTL (default: 30 minutes).
 * Enforces re-validation upon cache retrieval to prevent corrupted/tampered metadata exposure.
 */
class SafeLocalMetadataCache(
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ManagedExtensionCache {

    companion object {
        const val DEFAULT_TTL_MILLIS: Long = 30 * 60 * 1000L // 30 minutes
    }

    private val cachedData = AtomicReference<List<ManagedExtension>?>(null)
    private val cachedTimestamp = AtomicLong(0L)

    override suspend fun getCached(): List<ManagedExtension>? {
        val data = cachedData.get() ?: return null
        if (isExpired()) {
            return null
        }

        // Re-validate cached items to ensure integrity
        val validItems = data.filter { ext ->
            ManagedExtensionValidator.validate(ext) is ManagedExtensionValidator.ValidationResult.Valid
        }

        return if (validItems.isNotEmpty()) validItems else null
    }

    override suspend fun saveCache(extensions: List<ManagedExtension>) {
        // Only save validated extensions
        val validated = extensions.filter { ext ->
            ManagedExtensionValidator.validate(ext) is ManagedExtensionValidator.ValidationResult.Valid
        }
        cachedData.set(validated)
        cachedTimestamp.set(clock())
    }

    override suspend fun clear() {
        cachedData.set(null)
        cachedTimestamp.set(0L)
    }

    override fun isExpired(): Boolean {
        val timestamp = cachedTimestamp.get()
        if (timestamp == 0L) return true
        return (clock() - timestamp) > ttlMillis
    }

    override fun getCachedTimestamp(): Long {
        return cachedTimestamp.get()
    }
}
