package com.example.extension.orchestrator

import android.content.Context
import android.util.Log
import com.example.extension.managed.adapter.LegacyFallbackMigrationAdapter
import com.example.extension.managed.contract.ControlledManagedExtensionRuntime
import com.example.extension.managed.error.ExtensionError
import com.example.extension.managed.model.ContentType
import com.example.extension.managed.model.DownloadTaskRequest
import com.example.extension.managed.model.EpisodeItem
import com.example.extension.managed.model.ExtensionLifecycleStatus
import com.example.extension.managed.model.ExtractionRequest
import com.example.extension.managed.model.ManagedExtension
import com.example.extension.managed.model.MediaDetailsResult
import com.example.extension.managed.model.PlaybackSource
import com.example.extension.managed.model.ScraperCapability
import com.example.extension.managed.model.SearchRequest
import com.example.extension.managed.model.SearchResult
import com.example.extension.managed.model.ServerDiscoveryRequest
import com.example.extension.managed.model.ServerItem
import com.example.extension.managed.registry.ManagedExtensionRegistry
import com.example.extension.managed.registry.ScraperRegistry
import com.example.extension.managed.repository.DefaultManagedExtensionRepository
import com.example.extension.managed.repository.ExtensionUserPreferences
import com.example.extension.managed.repository.FirebaseFirestoreManagedExtensionDataSource
import com.example.extension.managed.repository.ManagedExtensionRepository
import com.example.extension.managed.repository.SafeLocalMetadataCache
import com.example.extension.managed.runtime.DefaultControlledManagedExtensionRuntime
import com.example.extension.managed.runtime.FallbackManager
import com.example.extension.managed.usecase.DiscoverManagedServersUseCase
import com.example.extension.managed.usecase.ExtractDownloadTaskUseCase
import com.example.extension.managed.usecase.ExtractPlaybackSourceUseCase
import com.example.extension.managed.usecase.GetManagedEpisodesUseCase
import com.example.extension.managed.usecase.GetManagedMediaDetailsUseCase
import com.example.extension.managed.usecase.ManagedExtensionResolver
import com.example.extension.managed.usecase.SearchManagedExtensionsUseCase
import com.example.ui.screens.player.ServerStateStore
import com.example.utils.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Central Orchestrator integrating the Managed Extension System into real Users App flows.
 *
 * Responsibilities:
 * 1. Primary path: Evaluates and routes requests through Managed Extensions first.
 * 2. Deterministic priority and lifecycle enforcement (ACTIVE, priority 100 > 50 > 10).
 * 3. Centralized fallback gating: Only recoverable errors fall back to candidate/legacy;
 *    security/compatibility errors halt immediately without untrusted fallback.
 * 4. Bridges normalized results into Player and Downloader via ServerStateStore and PlaybackSource.
 */
class ManagedMediaOrchestrator(
    private val repository: ManagedExtensionRepository,
    private val registry: ManagedExtensionRegistry = ManagedExtensionRegistry.INSTANCE,
    private val runtime: ControlledManagedExtensionRuntime = DefaultControlledManagedExtensionRuntime(
        managedExtensionRegistry = registry
    ),
    private val userPreferences: ExtensionUserPreferences,
    private val fallbackManager: FallbackManager = FallbackManager(ScraperRegistry.INSTANCE),
    private val isOnlineChecker: () -> Boolean = { true }
) {

    private val orchestratorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val managedExtensionsFlow: StateFlow<List<ManagedExtension>> = registry.extensionsFlow

    val resolver: ManagedExtensionResolver = ManagedExtensionResolver(
        registry = registry,
        scraperRegistry = ScraperRegistry.INSTANCE,
        currentAppVersionCode = runtime.currentAppVersionCode,
        supportedRuntimeApiVersion = runtime.supportedRuntimeApiVersion
    )

    val searchUseCase: SearchManagedExtensionsUseCase = SearchManagedExtensionsUseCase(
        resolver = resolver,
        runtime = runtime,
        fallbackManager = fallbackManager,
        isOnlineChecker = isOnlineChecker
    )

    val detailsUseCase: GetManagedMediaDetailsUseCase = GetManagedMediaDetailsUseCase(
        resolver = resolver,
        runtime = runtime,
        isOnlineChecker = isOnlineChecker
    )

    val episodesUseCase: GetManagedEpisodesUseCase = GetManagedEpisodesUseCase(
        resolver = resolver,
        runtime = runtime,
        isOnlineChecker = isOnlineChecker
    )

    val serverDiscoveryUseCase: DiscoverManagedServersUseCase = DiscoverManagedServersUseCase(
        resolver = resolver,
        runtime = runtime,
        fallbackManager = fallbackManager,
        isOnlineChecker = isOnlineChecker
    )

    val extractPlaybackSourceUseCase: ExtractPlaybackSourceUseCase = ExtractPlaybackSourceUseCase(
        runtime = runtime
    )

    val extractDownloadTaskUseCase: ExtractDownloadTaskUseCase = ExtractDownloadTaskUseCase(
        runtime = runtime
    )

    val revalidator: BackgroundMediaRevalidator = BackgroundMediaRevalidator(
        registry = registry,
        runtime = runtime,
        isOnlineChecker = isOnlineChecker,
        coroutineScope = orchestratorScope
    )

    fun revalidateMediaInBackground(
        mediaId: String,
        episodeId: String? = null,
        mediaTitle: String,
        isMovie: Boolean,
        season: Int = 1,
        episode: Int = 1,
        knownPlaybackUrl: String,
        scraperKey: String = "",
        currentServerName: String? = null,
        currentQuality: String = "Auto",
        altKeys: List<String> = emptyList()
    ): Job {
        return revalidator.revalidateMedia(
            mediaId = mediaId,
            episodeId = episodeId,
            mediaTitle = mediaTitle,
            isMovie = isMovie,
            season = season,
            episode = episode,
            knownPlaybackUrl = knownPlaybackUrl,
            scraperKey = scraperKey,
            currentServerName = currentServerName,
            currentQuality = currentQuality,
            altKeys = altKeys
        )
    }

    fun cancelAllBackgroundRevalidations(exceptKey: String? = null) {
        revalidator.cancelAllExcept(exceptKey)
    }

    val legacyFallbackAdapter: LegacyFallbackMigrationAdapter = LegacyFallbackMigrationAdapter(
        fallbackManager = fallbackManager
    )

    companion object {
        val DEFAULT_EGYDEAD_MANAGED_EXTENSION = ManagedExtension(
            id = "egydead",
            name = "EgyDead (Managed)",
            description = "المشاهدة والتحميل عبر إيجي ديد المدار رسمياً",
            baseUrl = "https://tv10.egydead.live",
            iconUrl = "",
            scraperKey = "egydead",
            definitionVersion = 1,
            minAppVersionCode = 1,
            runtimeApiVersion = 1,
            priority = 100,
            language = "ar",
            contentTypes = setOf(ContentType.MOVIE, ContentType.SERIES, ContentType.ANIME),
            status = ExtensionLifecycleStatus.ACTIVE,
            updatedAt = 1710000000000L,
            userEnabled = true
        )

        val DEFAULT_QFILM_MANAGED_EXTENSION = ManagedExtension(
            id = "qfilm",
            name = "كيو فيلم",
            description = "المشاهدة والتحميل عبر كيو فيلم المدار رسمياً",
            baseUrl = "https://a.qfilm.tv",
            iconUrl = "",
            scraperKey = "qfilm",
            definitionVersion = 1,
            minAppVersionCode = 1,
            runtimeApiVersion = 1,
            priority = 110,
            language = "ar",
            contentTypes = setOf(ContentType.MOVIE, ContentType.ANIME),
            status = ExtensionLifecycleStatus.ACTIVE,
            updatedAt = 1710000000000L,
            userEnabled = true
        )

        @Volatile
        private var instance: ManagedMediaOrchestrator? = null
        private val isInitialized = AtomicBoolean(false)

        fun getInstance(context: Context): ManagedMediaOrchestrator {
            return instance ?: synchronized(this) {
                instance ?: buildDefault(context.applicationContext).also {
                    instance = it
                    if (isInitialized.compareAndSet(false, true)) {
                        it.refreshRemote(force = false)
                    }
                }
            }
        }

        private fun buildDefault(appContext: Context): ManagedMediaOrchestrator {
            val userPrefs = SharedPreferencesExtensionUserPreferences(appContext)
            val cache = SafeLocalMetadataCache()
            val remoteSource = FirebaseFirestoreManagedExtensionDataSource()
            val repo = DefaultManagedExtensionRepository(
                remoteDataSource = remoteSource,
                cache = cache,
                userPreferences = userPrefs
            )
            val registry = ManagedExtensionRegistry.INSTANCE

            // Pre-seed default bundled managed extensions so app is immediately operational
            val existing = registry.getAllExtensions()
            val egydeadEnabled = userPrefs.isExtensionEnabled(DEFAULT_EGYDEAD_MANAGED_EXTENSION.id)
            val qfilmEnabled = userPrefs.isExtensionEnabled(DEFAULT_QFILM_MANAGED_EXTENSION.id)
            if (existing.isEmpty()) {
                registry.setExtensions(
                    listOf(
                        DEFAULT_EGYDEAD_MANAGED_EXTENSION.copy(userEnabled = egydeadEnabled),
                        DEFAULT_QFILM_MANAGED_EXTENSION.copy(userEnabled = qfilmEnabled)
                    )
                )
            } else {
                val hasQfilm = existing.any { it.id == DEFAULT_QFILM_MANAGED_EXTENSION.id }
                val updated = existing.map { ext ->
                    if (ext.id == DEFAULT_EGYDEAD_MANAGED_EXTENSION.id) {
                        ext.copy(status = ExtensionLifecycleStatus.ACTIVE)
                    } else ext
                }.toMutableList()
                if (!hasQfilm) {
                    updated.add(DEFAULT_QFILM_MANAGED_EXTENSION.copy(userEnabled = qfilmEnabled))
                }
                registry.setExtensions(updated)
            }

            val runtime = DefaultControlledManagedExtensionRuntime(
                managedExtensionRegistry = registry,
                webEngineProvider = {
                    com.example.extension.managed.web.ControlledWebViewEngine(appContext)
                },
                connectivityMonitor = com.example.extension.managed.runtime.network.DefaultConnectivityMonitor {
                    NetworkUtils.isInternetAvailable(appContext)
                }
            )

            return ManagedMediaOrchestrator(
                repository = repo,
                registry = registry,
                runtime = runtime,
                userPreferences = userPrefs,
                isOnlineChecker = { NetworkUtils.isInternetAvailable(appContext) }
            )
        }

        /**
         * For unit and integration tests.
         */
        fun setTestInstance(orchestrator: ManagedMediaOrchestrator) {
            instance = orchestrator
            isInitialized.set(true)
        }

        fun hasActiveExtensions(): Boolean {
            val fromInstance = instance?.registry?.getActiveExtensions()
            if (fromInstance != null) {
                return fromInstance.isNotEmpty()
            }
            val fromRegistry = ManagedExtensionRegistry.INSTANCE.getActiveExtensions()
            if (fromRegistry.isNotEmpty()) {
                return true
            }
            return true
        }

        fun hasActiveExtensions(contentType: ContentType?): Boolean {
            val fromInstance = instance?.hasActiveExtensions(contentType)
            if (fromInstance != null) {
                return fromInstance
            }
            val exts = ManagedExtensionRegistry.INSTANCE.getActiveExtensions()
            if (exts.isNotEmpty()) {
                return if (contentType != null) exts.any { it.contentTypes.contains(contentType) } else true
            }
            return true
        }

        fun getActiveExtensionNames(): List<String> {
            return (instance?.registry?.getActiveExtensions()
                ?: ManagedExtensionRegistry.INSTANCE.getActiveExtensions()).map { it.name }
        }
    }

    /**
     * Refreshes managed extension definitions from Firestore asynchronously.
     * Complies with Firebase Spark Plan constraint: One-time fetch + Cache.
     */
    fun refreshRemote(force: Boolean = false) {
        orchestratorScope.launch {
            forceRefresh()
        }
    }

    /**
     * Suspending force refresh for UI use with progress indicator.
     */
    suspend fun forceRefresh() {
        try {
            val result = repository.getExtensions(forceRefresh = true)
            if (result.isSuccess) {
                val freshList = result.getOrNull() ?: emptyList()
                if (freshList.isNotEmpty()) {
                    registry.setExtensions(freshList)
                }
            }
        } catch (_: Exception) {
            // Ignore transient network errors, keep cached/bundled extensions
        }
    }

    fun hasActiveExtensions(contentType: ContentType? = null): Boolean {
        val active = registry.getActiveExtensions()
        if (contentType == null) return active.isNotEmpty()
        return active.any { it.contentTypes.contains(contentType) }
    }

    fun getActiveExtensions(): List<ManagedExtension> {
        return registry.getActiveExtensions()
    }

    fun updateUserPreference(extensionId: String, enabled: Boolean) {
        userPreferences.setExtensionEnabled(extensionId, enabled)
        registry.updateExtensionUserPreference(extensionId, enabled)
    }

    suspend fun refreshExtensions() {
        forceRefresh()
    }

    fun setExtensionEnabled(extensionId: String, enabled: Boolean) {
        updateUserPreference(extensionId, enabled)
    }

    /**
     * Executes search query across eligible managed extensions.
     */
    suspend fun searchMedia(
        query: String,
        contentType: ContentType? = null,
        page: Int = 1
    ): Result<SearchResult> {
        return searchUseCase.execute(query, contentType, page)
    }

    /**
     * Retrieves normalized media details via managed extension.
     */
    suspend fun getMediaDetails(
        contentUrl: String,
        extensionId: String? = null
    ): Result<MediaDetailsResult> {
        return detailsUseCase.execute(contentUrl, extensionId)
    }

    /**
     * Retrieves normalized series episodes via managed extension.
     */
    suspend fun getEpisodes(
        seriesUrl: String,
        season: Int = 1,
        extensionId: String? = null
    ): Result<List<EpisodeItem>> {
        return episodesUseCase.execute(seriesUrl, season, extensionId)
    }

    /**
     * Main user flow: Server Discovery with prioritized fallback across managed candidates.
     */
    suspend fun discoverServers(
        title: String,
        year: String = "",
        isMovie: Boolean = true,
        season: Int = 1,
        episode: Int = 1,
        mediaId: String = "",
        altKeys: List<String> = emptyList()
    ): ManagedDiscoveryOutcome = withContext(Dispatchers.IO) {
        if (!isOnlineChecker()) {
            return@withContext ManagedDiscoveryOutcome.RecoverableFailure(
                error = ExtensionError.NoInternet()
            )
        }

        val targetContentType = if (isMovie) ContentType.MOVIE else ContentType.SERIES

        // 1. Filter and sort candidate managed extensions
        val allExtensions = registry.getAllExtensions()
        val candidates = fallbackManager.filterAndSortCandidates(
            candidates = allExtensions,
            contentType = targetContentType,
            capability = ScraperCapability.SERVER_DISCOVERY,
            appVersionCode = runtime.currentAppVersionCode,
            supportedRuntimeApi = runtime.supportedRuntimeApiVersion
        )

        if (candidates.isEmpty()) {
            return@withContext ManagedDiscoveryOutcome.RecoverableFailure(
                error = ExtensionError.MediaNotFound("No candidate managed extension available for $targetContentType")
            )
        }

        val mediaKey = "$title-$isMovie-$season-$episode"
        val cleanTitle = title.replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").replace(Regex("\\s+"), " ").trim()
        val cleanTitleWithYear = if (year.isNotBlank() && year != "0") "$cleanTitle $year" else cleanTitle

        var lastRecoverableError: ExtensionError? = null

        // 2. Iterate through prioritized managed candidates (e.g. 100 > 50 > 10)
        for (candidate in candidates) {
            try {
                // A. Search for title on candidate extension
                val searchResult = runtime.search(
                    listOf(candidate),
                    SearchRequest(query = cleanTitle, contentType = targetContentType)
                )

                val matchedItem = searchResult.getOrNull()?.items?.firstOrNull()
                    ?: runtime.search(
                        listOf(candidate),
                        SearchRequest(query = cleanTitleWithYear, contentType = targetContentType)
                    ).getOrNull()?.items?.firstOrNull()

                if (matchedItem == null) {
                    if (candidate.scraperKey == "qfilm" || candidate.id == "qfilm") {
                        try { Log.w("QFILM_DIAG", "[QFILM_DIAG] 08_SEARCH_NO_MATCH candidate=${candidate.name}, cleanTitle='$cleanTitle'") } catch (_: Throwable) {}
                    }
                    lastRecoverableError = ExtensionError.MediaNotFound("Media $cleanTitle not found on ${candidate.name}")
                    continue
                }

                if (candidate.scraperKey == "qfilm" || candidate.id == "qfilm") {
                    try { Log.i("QFILM_DIAG", "[QFILM_DIAG] 08_SEARCH_RESULT_SELECTED candidate=${candidate.name}, title='${matchedItem.title}', url='${matchedItem.url}'") } catch (_: Throwable) {}
                }

                // B. For series, resolve target episode page URL
                val targetUrl = if (isMovie) {
                    matchedItem.url
                } else {
                    val epResult = runtime.getEpisodes(candidate, matchedItem.url, season)
                    val epList = epResult.getOrNull() ?: emptyList()
                    epList.firstOrNull { it.episodeNumber == episode }?.url ?: matchedItem.url
                }

                // C. Discover servers
                val discoveryRequest = ServerDiscoveryRequest(
                    targetUrl = targetUrl,
                    mediaTitle = title,
                    isMovie = isMovie,
                    season = season,
                    episode = episode
                )
                val discoveryResult = runtime.discoverServers(listOf(candidate), discoveryRequest)

                if (discoveryResult.isSuccess) {
                    val result = discoveryResult.getOrThrow()
                    val serverItems = result.servers

                    if (serverItems.isNotEmpty()) {
                        // Bridge normalized results into ServerStateStore for Player/Downloader
                        val serverNames = mutableListOf<String>()
                        val serverLinks = mutableMapOf<String, String>()
                        val serverIds = mutableMapOf<String, String>()
                        val downloadLinks = mutableMapOf<String, String>()

                        for (item in serverItems) {
                            if (item.name.contains("(تحميل)") || item.link.endsWith(".mp4") || item.link.endsWith(".mkv")) {
                                downloadLinks[item.name] = item.link
                            } else {
                                serverNames.add(item.name)
                                serverLinks[item.name] = item.link
                                serverIds[item.name] = item.id
                            }
                        }

                        // Also extract direct stream if available on first server
                        val firstServer = serverItems.first()
                        val directPlayback = if (firstServer.isDirectStream) {
                            PlaybackSource(
                                streamUrl = firstServer.link,
                                mimeType = if (firstServer.link.contains(".m3u8")) "application/x-mpegURL" else "video/mp4"
                            )
                        } else null

                        ServerStateStore.saveForMedia(
                            mediaKey = mediaKey,
                            servers = serverNames,
                            links = serverLinks,
                            ids = serverIds,
                            downloads = downloadLinks,
                            website = candidate.name,
                            playbackPageUrl = targetUrl,
                            scraperKey = candidate.scraperKey,
                            altKeys = listOf(mediaId) + altKeys
                        )

                        return@withContext ManagedDiscoveryOutcome.Success(
                            servers = serverItems,
                            website = candidate.name,
                            sourceUrl = targetUrl,
                            directStream = directPlayback
                        )
                    }
                } else {
                    val exception = discoveryResult.exceptionOrNull()
                    val error = (exception as? Exception)?.let { ExtensionError.ExtractionFailed(it.message ?: "Unknown", it) }
                        ?: ExtensionError.MediaNotFound("No servers found")

                    if (!fallbackManager.isRecoverable(error)) {
                        return@withContext ManagedDiscoveryOutcome.SecurityFailure(error)
                    }
                    lastRecoverableError = error
                }
            } catch (e: Exception) {
                val error = ExtensionError.ExtractionFailed("Discovery exception: ${e.message}", e)
                if (!fallbackManager.isRecoverable(error)) {
                    return@withContext ManagedDiscoveryOutcome.SecurityFailure(error)
                }
                lastRecoverableError = error
            }
        }

        // 3. All managed candidates failed with recoverable errors
        val finalError = lastRecoverableError ?: ExtensionError.MediaNotFound("No servers found across managed candidates")
        return@withContext ManagedDiscoveryOutcome.RecoverableFailure(
            error = finalError
        )
    }

    /**
     * Extracts normalized playback source for consumption by media players.
     */
    suspend fun extractPlaybackSource(
        serverItem: ServerItem,
        mediaTitle: String = ""
    ): Result<PlaybackSource> = withContext(Dispatchers.IO) {
        val request = ExtractionRequest(serverItem = serverItem, mediaTitle = mediaTitle)
        val result = runtime.extractStream(request)
        if (result.isSuccess) {
            val extraction = result.getOrThrow()
            if (extraction.playbackSource != null) {
                Result.success(extraction.playbackSource)
            } else {
                Result.failure(Exception("No playback source extracted"))
            }
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Extraction failed"))
        }
    }

    /**
     * Extracts normalized download task for consumption by download managers.
     * Guarantees minimal header projection and zero session cookie leakage.
     */
    suspend fun extractDownloadTask(
        serverItem: ServerItem,
        mediaTitle: String = ""
    ): Result<DownloadTaskRequest> = withContext(Dispatchers.IO) {
        val request = ExtractionRequest(serverItem = serverItem, mediaTitle = mediaTitle)
        val result = runtime.extractStream(request)
        if (result.isSuccess) {
            val extraction = result.getOrThrow()
            val task = extraction.downloadTask ?: extraction.playbackSource?.let { src ->
                DownloadTaskRequest(
                    id = serverItem.id,
                    title = mediaTitle,
                    downloadUrl = src.streamUrl,
                    headers = src.headers,
                    mimeType = src.mimeType
                )
            }
            if (task != null) {
                Result.success(task)
            } else {
                Result.failure(Exception("No download task available"))
            }
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Download extraction failed"))
        }
    }
}
