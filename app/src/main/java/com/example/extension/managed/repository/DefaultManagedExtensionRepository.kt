package com.example.extension.managed.repository

import com.example.extension.managed.error.ExtensionError
import com.example.extension.managed.model.ManagedExtension
import com.example.extension.managed.model.ManagedExtensionValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Standard implementation of ManagedExtensionRepository.
 * Enforces:
 * 1. Untrusted remote snapshot validation via ManagedExtensionValidator
 * 2. Fault tolerance: malformed documents are skipped, not crashing the list
 * 3. Deterministic deduplication
 * 4. Priority tiering: Fresh remote -> Validated cache -> Domain error
 * 5. Mapping of Firestore exceptions to domain ExtensionErrors
 */
class DefaultManagedExtensionRepository(
    private val remoteDataSource: ManagedExtensionRemoteDataSource,
    private val cache: ManagedExtensionCache = SafeLocalMetadataCache(),
    private val userPreferences: ExtensionUserPreferences = InMemoryExtensionUserPreferences()
) : ManagedExtensionRepository {

    override suspend fun getExtensions(forceRefresh: Boolean): Result<List<ManagedExtension>> =
        withContext(Dispatchers.IO) {
            // 1. Check cache if not forcing refresh
            if (!forceRefresh && !cache.isExpired()) {
                val cached = cache.getCached()
                if (cached != null) {
                    // Update user preference states in cached items
                    val updatedWithPreferences = cached.map { ext ->
                        ext.copy(userEnabled = userPreferences.isExtensionEnabled(ext.id))
                    }
                    return@withContext Result.success(updatedWithPreferences)
                }
            }

            // 2. Fetch fresh remote data from Firestore
            val remoteResult = remoteDataSource.fetchManagedExtensionDtos()

            if (remoteResult.isSuccess) {
                val dtos = remoteResult.getOrNull() ?: emptyList()
                val validExtensions = mutableListOf<ManagedExtension>()

                for (dto in dtos) {
                    val localEnabled = userPreferences.isExtensionEnabled(dto.id.orEmpty())
                    val domainExt = ManagedExtensionMapper.toDomain(dto, localUserEnabled = localEnabled)

                    // Gate: strict validation
                    when (val validation = ManagedExtensionValidator.validate(domainExt)) {
                        is ManagedExtensionValidator.ValidationResult.Valid -> {
                            validExtensions.add(domainExt)
                        }
                        is ManagedExtensionValidator.ValidationResult.Invalid -> {
                            // Safely skip malformed/untrusted remote documents without crashing the pipeline
                        }
                    }
                }

                // Deterministic deduplication: if duplicate IDs exist, retain the one with newest updatedAt or higher priority
                val deduplicated = validExtensions
                    .groupBy { it.id }
                    .map { (_, group) ->
                        group.maxWithOrNull(compareBy({ it.updatedAt }, { it.priority })) ?: group.first()
                    }
                    .sortedByDescending { it.priority }

                // Save validated items to cache
                cache.saveCache(deduplicated)

                return@withContext Result.success(deduplicated)
            }

            // 3. Fallback to cache on remote failure (e.g. offline/network failure)
            val cachedFallback = cache.getCached()
            if (cachedFallback != null) {
                val updatedWithPreferences = cachedFallback.map { ext ->
                    ext.copy(userEnabled = userPreferences.isExtensionEnabled(ext.id))
                }
                return@withContext Result.success(updatedWithPreferences)
            }

            // 4. No cache available -> map remote failure to domain ExtensionError
            val error = remoteResult.exceptionOrNull()
            val domainError = mapToDomainError(error)
            Result.failure(domainError)
        }

    override suspend fun getExtensionById(id: String, forceRefresh: Boolean): Result<ManagedExtension> =
        withContext(Dispatchers.IO) {
            val listResult = getExtensions(forceRefresh)
            if (listResult.isFailure) {
                return@withContext Result.failure(listResult.exceptionOrNull()!!)
            }

            val extensions = listResult.getOrNull() ?: emptyList()
            val match = extensions.firstOrNull { it.id == id }

            if (match != null) {
                Result.success(match)
            } else {
                Result.failure(
                    ExtensionError.ExtractionFailed("Managed extension with ID '$id' was not found or is invalid")
                )
            }
        }

    private fun mapToDomainError(throwable: Throwable?): Throwable {
        if (throwable == null) {
            return ExtensionError.ExtractionFailed("Unknown repository failure occurred")
        }

        val message = throwable.message.orEmpty().lowercase()
        return if (throwable is IOException ||
            message.contains("unavailable") ||
            message.contains("offline") ||
            message.contains("network") ||
            message.contains("no internet")
        ) {
            ExtensionError.NoInternet(
                detail = "No internet connection available and no local cache present",
                cause = throwable
            )
        } else {
            ExtensionError.ExtractionFailed(
                detail = "Failed to retrieve managed extensions: ${throwable.message}",
                cause = throwable
            )
        }
    }
}
