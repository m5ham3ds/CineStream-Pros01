package com.example.ui.screens.player

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import com.example.data.repository.TmdbMediaRepositoryImpl
import com.example.domain.models.Episode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

data class PlayerUiState(
    val isLoading: Boolean = true,
    val mediaId: String = "",
    val isMovie: Boolean = true,
    val isAnime: Boolean = false,
    val isOffline: Boolean = false,
    val title: String = "",

    // Website (Provider)
    val availableWebsites: List<String> = emptyList(),
    val currentWebsite: String = "",
    val fallbackWebsites: List<String> = emptyList(),

    // Server
    val availableServers: List<String> = emptyList(),
    val currentServer: String = "",
    val availableServerLinks: Map<String, String> = emptyMap(),
    val availableServerIds: Map<String, String> = emptyMap(),
    val serverIdToChange: String? = null,

    // Quality
    val availableQualities: List<String> = listOf("Auto"),
    val currentQuality: String = "Auto",
    val extractedQualitiesInfo: List<com.example.utils.M3U8Parser.QualityInfo> = emptyList(),

    // Episodes
    val episodes: List<Episode> = emptyList(),
    val currentEpisodeId: String = "",
    val currentSeasonNumber: Int = 1,
    val currentEpisodeNumber: Int = 1,
    val visibleEpisodesCount: Int = 10,

    // Extracted URL
    val currentVideoUrl: String? = null,
    val extractionUrl: String? = null, // The URL to feed to the hidden WebView
    val pendingSeekPosition: Long? = null,
    val currentPositionMillis: Long = 0L,
    val durationMillis: Long = 0L
)

class PlayerViewModel : ViewModel() {
    private val tmdbRepo = TmdbMediaRepositoryImpl()

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var extractionTimeoutJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            com.example.ui.screens.player.ServerStateStore.extractedQualitiesFlow.collect { newQualities ->
                if (newQualities.isNotEmpty()) {
                    val currentQ = _uiState.value.currentQuality
                    val allQualitiesList = (listOf("Auto") + newQualities.map { it.name }).distinct()
                    _uiState.value = _uiState.value.copy(
                        extractedQualitiesInfo = newQualities,
                        availableQualities = allQualitiesList,
                        currentQuality = if (currentQ in allQualitiesList) currentQ else currentQ.ifBlank { "Auto" }
                    )
                }
            }
        }
        viewModelScope.launch {
            com.example.ui.screens.player.ServerStateStore.serversFlow.collect { newServers ->
                if (newServers.isNotEmpty()) {
                    val currentS = _uiState.value.currentServer
                    val cached = com.example.ui.screens.player.ServerStateStore.getCachedData(_uiState.value.mediaId)
                    _uiState.value = _uiState.value.copy(
                        availableServers = newServers,
                        availableServerLinks = cached?.serverLinks ?: _uiState.value.availableServerLinks,
                        availableServerIds = cached?.serverIds ?: _uiState.value.availableServerIds,
                        currentServer = if (currentS in newServers) currentS else if (currentS.isBlank()) newServers.first() else currentS
                    )
                }
            }
        }
    }

    fun updatePlaybackPosition(currentPos: Long, totalDuration: Long) {
        _uiState.value = _uiState.value.copy(
            currentPositionMillis = currentPos,
            durationMillis = totalDuration
        )
    }

    fun initialize(
        mediaId: String,
        isMovie: Boolean,
        initialTitle: String,
        directUrl: String? = null,
        targetServer: String? = null,
        website: String? = null,
        episodeId: String? = null
    ) {
        val hasArabic = initialTitle.any { it in '؀'..'ۿ' }
        val isAnime = initialTitle.contains("anime", ignoreCase = true) || initialTitle.contains("أنمي", ignoreCase = true)
        
        val activeManagedNames = com.example.extension.orchestrator.ManagedMediaOrchestrator.getActiveExtensionNames()
        val defaultActive = if (activeManagedNames.isNotEmpty()) activeManagedNames else listOf("EgyDead (Managed)")
        val bestWebsite = website ?: defaultActive.firstOrNull() ?: ""
        val remainingFallbacks = defaultActive.filter { it != bestWebsite }

        val isDownloaded = directUrl != null && (
            directUrl.startsWith("local_offline_file") ||
            directUrl.startsWith("file://") ||
            directUrl.startsWith("content://")
        )
        val ctx = com.example.MyApplication.appContext
        val localFile = if (isMovie) {
            com.example.utils.MediaStorageUtils.findMediaFile(ctx, mediaId)
        } else {
            com.example.utils.MediaStorageUtils.findMediaFile(ctx, mediaId)
                ?: com.example.utils.MediaStorageUtils.findMediaFile(ctx, "${mediaId}_1")
        }
        val isOffline = !com.example.utils.NetworkUtils.isInternetAvailable(ctx)
        val isOfflineOrDownloaded = isDownloaded || (localFile != null && localFile.exists()) || isOffline

        if (isOfflineOrDownloaded) {
            var localVideoUrl: String? = null
            if (directUrl != null) {
                if (directUrl.startsWith("local_offline_file://")) {
                    val fileId = directUrl.removePrefix("local_offline_file://")
                    val file = com.example.utils.MediaStorageUtils.findMediaFile(ctx, fileId)
                    if (file != null && file.exists()) {
                        localVideoUrl = android.net.Uri.fromFile(file).toString()
                    }
                } else if (directUrl.startsWith("file://") || directUrl.startsWith("content://") || directUrl.contains(".mp4") || directUrl.contains(".mkv")) {
                    localVideoUrl = directUrl
                }
            } else if (localFile != null && localFile.exists()) {
                localVideoUrl = android.net.Uri.fromFile(localFile).toString()
            }

            _uiState.value = _uiState.value.copy(
                mediaId = mediaId,
                isMovie = isMovie,
                isAnime = isAnime,
                isOffline = true,
                title = initialTitle,
                availableWebsites = emptyList(),
                currentWebsite = "",
                fallbackWebsites = emptyList(),
                availableServers = emptyList(),
                currentServer = "",
                availableServerLinks = emptyMap(),
                availableServerIds = emptyMap(),
                serverIdToChange = null,
                availableQualities = emptyList(),
                currentQuality = "",
                extractedQualitiesInfo = emptyList(),
                currentVideoUrl = localVideoUrl,
                extractionUrl = null,
                isLoading = false
            )

            if (!isMovie) {
                viewModelScope.launch {
                    try {
                        val downloadRepo = com.example.data.repository.DownloadRepository(ctx)
                        val allDownloads = downloadRepo.getAllItemsSync()
                        val seriesDownloads = allDownloads.filter {
                            (it.mediaId == mediaId || it.id.startsWith("${mediaId}_")) && it.isCompleted
                        }
                        if (seriesDownloads.isNotEmpty()) {
                            val offlineEpisodes = seriesDownloads.mapIndexed { index, dl ->
                                val epNum = dl.id.substringAfterLast("_").toIntOrNull() ?: (index + 1)
                                Episode(
                                    id = dl.id,
                                    episodeNumber = epNum,
                                    title = dl.title,
                                    overview = "",
                                    thumbnailUrl = dl.posterUrl,
                                    duration = 0,
                                    rating = 0.0
                                )
                            }
                            _uiState.value = _uiState.value.copy(
                                episodes = offlineEpisodes,
                                currentEpisodeId = offlineEpisodes.firstOrNull()?.id ?: mediaId
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            return
        }

        val serverIds = com.example.ui.screens.player.ServerStateStore.extractedServerIds
        val effectiveEpId = episodeId?.takeIf { it.isNotBlank() } ?: com.example.utils.LastPlaybackStore.getLastEpisodeId(ctx, mediaId)
        val fileId = if (effectiveEpId.isNullOrBlank()) mediaId else "${mediaId}_$effectiveEpId"
        val lastPlayback = com.example.utils.LastPlaybackStore.getLastPlayback(ctx, mediaId, effectiveEpId)
        val savedQuality = lastPlayback?.quality?.ifBlank { "Auto" } ?: "Auto"
        var savedPosition = lastPlayback?.positionMillis ?: 0L
        if (savedPosition <= 0L) {
            savedPosition = com.example.ui.screens.player.PlaybackSyncStore.getPosition(fileId)
        }
        if (savedPosition <= 0L && fileId.contains("_")) {
            savedPosition = com.example.ui.screens.player.PlaybackSyncStore.getPosition(mediaId)
        }
        val currentS = targetServer ?: lastPlayback?.serverName ?: ""

        _uiState.value = _uiState.value.copy(
            mediaId = mediaId,
            isMovie = isMovie,
            isAnime = isAnime,
            title = initialTitle,
            availableWebsites = (defaultActive + listOf(bestWebsite)).distinct(),
            currentWebsite = lastPlayback?.website ?: bestWebsite,
            fallbackWebsites = remainingFallbacks,
            currentServer = currentS,
            availableServers = com.example.ui.screens.player.ServerStateStore.extractedServers,
            availableServerLinks = com.example.ui.screens.player.ServerStateStore.extractedServerLinks,
            availableServerIds = serverIds,
            serverIdToChange = if (currentS.isNotBlank()) serverIds[currentS] else null,
            extractedQualitiesInfo = com.example.ui.screens.player.ServerStateStore.extractedQualities,
            availableQualities = let {
                val qNames = com.example.ui.screens.player.ServerStateStore.extractedQualities.map { it.name }.filter { it != "Auto" }.distinct()
                if (qNames.isNotEmpty()) qNames + "Auto" else listOf("Auto")
            },
            currentQuality = savedQuality,
            pendingSeekPosition = savedPosition,
            currentEpisodeId = effectiveEpId ?: ""
        )

        if (savedPosition <= 0L) {
            viewModelScope.launch {
                try {
                    val hist = com.example.data.repository.HistoryRepository(ctx).getHistoryItem(fileId)
                    if (hist != null && hist.positionMillis > 0L) {
                        _uiState.value = _uiState.value.copy(pendingSeekPosition = hist.positionMillis)
                    }
                } catch (_: Exception) {}
            }
        }

        var effectiveDirectUrl = directUrl ?: lastPlayback?.url
        if (savedQuality != "Auto") {
            val matchedQ = com.example.ui.screens.player.ServerStateStore.extractedQualities.find { it.name == savedQuality }
            if (matchedQ != null && matchedQ.url.isNotBlank()) {
                effectiveDirectUrl = matchedQ.url
            }
        }
        if (!effectiveDirectUrl.isNullOrEmpty() && (effectiveDirectUrl.contains(".mp4") || effectiveDirectUrl.contains(".m3u8") || effectiveDirectUrl.startsWith("local_offline_file") || effectiveDirectUrl.startsWith("file://"))) {
            var finalDirectUrl = effectiveDirectUrl
            if (effectiveDirectUrl.startsWith("local_offline_file://")) {
                val localFileId = effectiveDirectUrl.removePrefix("local_offline_file://")
                val file = com.example.utils.MediaStorageUtils.findMediaFile(ctx, localFileId)
                    ?: com.example.utils.MediaStorageUtils.findMediaFile(ctx, mediaId)
                if (file != null && file.exists()) {
                    finalDirectUrl = android.net.Uri.fromFile(file).toString()
                }
            }
            _uiState.value = _uiState.value.copy(
                currentVideoUrl = finalDirectUrl, 
                isLoading = false
            )
            val knownUrl = lastPlayback?.playbackPageUrl 
                ?: com.example.ui.screens.player.ServerStateStore.getDataForMedia(mediaId)?.playbackPageUrl 
                ?: finalDirectUrl
            val sKey = lastPlayback?.scraperKey 
                ?: com.example.ui.screens.player.ServerStateStore.getDataForMedia(mediaId)?.scraperKey 
                ?: ""
            val managedOrchestrator = com.example.extension.orchestrator.ManagedMediaOrchestrator.getInstance(ctx)
            managedOrchestrator.revalidateMediaInBackground(
                mediaId = mediaId,
                episodeId = effectiveEpId,
                mediaTitle = initialTitle,
                isMovie = isMovie,
                knownPlaybackUrl = knownUrl,
                scraperKey = sKey,
                currentServerName = currentS,
                currentQuality = savedQuality,
                altKeys = listOf(mediaId, fileId)
            )
        } else if (!effectiveDirectUrl.isNullOrEmpty()) {
            // It's a watch url (webpage), we need to extract from it
            _uiState.value = _uiState.value.copy(extractionUrl = effectiveDirectUrl, isLoading = true)
            startExtractionTimeout()
        } else if (!isMovie) {
            loadEpisodes(mediaId, 1) // Default to season 1
        } else {
            generateExtractionUrl()
        }
    }

    fun loadMoreEpisodes() {
        _uiState.value = _uiState.value.copy(visibleEpisodesCount = _uiState.value.visibleEpisodesCount + 10)
    }

    private fun loadEpisodes(seriesId: String, seasonNumber: Int) {
        viewModelScope.launch {
            try {
                // Fetch full series details to get episodes for the season
                val series = tmdbRepo.getSeriesById(seriesId)
                val season = series?.seasons?.find { it.seasonNumber == seasonNumber }
                if (season != null) {
                    val fullSeason = tmdbRepo.getSeasonEpisodes(seriesId, seasonNumber)
                    _uiState.value = _uiState.value.copy(
                        episodes = fullSeason,
                        currentEpisodeId = fullSeason.firstOrNull()?.id ?: "",
                        currentSeasonNumber = seasonNumber,
                        currentEpisodeNumber = fullSeason.firstOrNull()?.episodeNumber ?: 1
                    )
                }
                generateExtractionUrl()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun selectWebsite(website: String) {
        com.example.ui.screens.player.ServerStateStore.clear()
        _uiState.value = _uiState.value.copy(
            currentWebsite = website, 
            isLoading = true, 
            currentVideoUrl = null, 
            fallbackWebsites = emptyList(),
            availableServers = emptyList(),
            availableServerLinks = emptyMap(),
            availableServerIds = emptyMap(),
            currentServer = ""
        )
        generateExtractionUrl()
    }

    fun selectQuality(qualityName: String, currentPos: Long = 0L) {
        val qInfo = _uiState.value.extractedQualitiesInfo.find { it.name == qualityName }
        val targetPos = if (currentPos > 0L) currentPos else _uiState.value.currentPositionMillis
        if (qInfo != null && qInfo.url.isNotEmpty() && qInfo.url != _uiState.value.currentVideoUrl && qualityName != "Auto") {
            _uiState.value = _uiState.value.copy(
                currentQuality = qualityName,
                currentVideoUrl = qInfo.url,
                pendingSeekPosition = targetPos
            )
        } else {
            _uiState.value = _uiState.value.copy(
                currentQuality = qualityName
            )
        }
        val ctx = com.example.MyApplication.appContext
        val cached = com.example.ui.screens.player.ServerStateStore.getDataForMedia(_uiState.value.mediaId)
        com.example.utils.LastPlaybackStore.savePlayback(
            context = ctx,
            mediaId = _uiState.value.mediaId,
            url = _uiState.value.currentVideoUrl ?: "",
            quality = qualityName,
            serverName = _uiState.value.currentServer,
            website = _uiState.value.currentWebsite,
            episodeId = _uiState.value.currentEpisodeId.takeIf { it.isNotBlank() },
            positionMillis = targetPos,
            durationMillis = _uiState.value.durationMillis,
            playbackPageUrl = cached?.playbackPageUrl,
            scraperKey = cached?.scraperKey
        )
    }

    fun selectServer(server: String) {
        val link = _uiState.value.availableServerLinks[server]
        val id = _uiState.value.availableServerIds[server]
        
        var nextExtractionUrl = _uiState.value.extractionUrl
        if (link != null && link.isNotEmpty()) {
            nextExtractionUrl = link
        }
        
        _uiState.value = _uiState.value.copy(
            currentServer = server,
            isLoading = true,
            currentVideoUrl = null,
            extractionUrl = nextExtractionUrl,
            serverIdToChange = id
        )
        
        if (nextExtractionUrl != null) {
            if (nextExtractionUrl.contains(".m3u8") || nextExtractionUrl.contains(".mp4") || nextExtractionUrl.contains("akamaized.net")) {
                setFinalVideoUrl(nextExtractionUrl)
            } else {
                startExtractionTimeout()
            }
        } else {
            generateExtractionUrl()
        }
    }

    fun selectEpisode(episode: com.example.domain.models.Episode) {
        if (_uiState.value.isOffline) {
            val ctx = com.example.MyApplication.appContext
            val file = com.example.utils.MediaStorageUtils.findMediaFile(ctx, episode.id)
            val fileUrl = if (file != null && file.exists()) android.net.Uri.fromFile(file).toString() else null
            _uiState.value = _uiState.value.copy(
                currentEpisodeId = episode.id,
                currentEpisodeNumber = episode.episodeNumber,
                title = episode.title,
                currentVideoUrl = fileUrl,
                isLoading = false
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            currentEpisodeId = episode.id,
            currentEpisodeNumber = episode.episodeNumber,
            title = episode.title,
            isLoading = true,
            currentVideoUrl = null
        )
        generateExtractionUrl()
    }

    fun setFinalVideoUrl(url: String) {
        extractionTimeoutJob?.cancel()
        
        // Immediately stop extraction to prevent multiple calls
        _uiState.value = _uiState.value.copy(extractionUrl = null)
        
        viewModelScope.launch {
            try {
                val mediaKey = com.example.ui.screens.player.ServerStateStore.currentMediaKey ?: url
                val sortedQualities = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.example.ui.screens.player.ServerStateStore.resolveAndCacheAllQualities(
                        mediaKey = mediaKey,
                        serversNames = com.example.ui.screens.player.ServerStateStore.extractedServers,
                        serversMap = com.example.ui.screens.player.ServerStateStore.extractedServerLinks,
                        downloadsMap = com.example.ui.screens.player.ServerStateStore.extractedDownloadLinks,
                        currentStreamUrl = url
                    )
                }

                val explicitQualities = sortedQualities.map { it.name }.filter { it != "Auto" }.distinct()
                val allQualitiesList = if (explicitQualities.isNotEmpty()) explicitQualities + "Auto" else listOf("Auto")

                _uiState.value = _uiState.value.copy(
                    extractedQualitiesInfo = sortedQualities,
                    availableQualities = allQualitiesList,
                    currentQuality = if (_uiState.value.currentQuality in allQualitiesList) _uiState.value.currentQuality else "Auto",
                    currentVideoUrl = url,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    currentVideoUrl = url,
                    isLoading = false
                )
            }
        }
    }

    fun setIframeUrl(url: String) {
        extractionTimeoutJob?.cancel()
        _uiState.value = _uiState.value.copy(
            extractionUrl = url,
            isLoading = true,
            currentVideoUrl = null
        )
        // Only wait 3 seconds to see if a direct video can be extracted from this iframe
        extractionTimeoutJob = viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            if (_uiState.value.currentVideoUrl == null) {
                // If no direct video found, just use the iframe as the final video URL
                setFinalVideoUrl(url)
            }
        }
    }

    fun updateServers(servers: List<String>) {
        val isOffline = !com.example.utils.NetworkUtils.isInternetAvailable(com.example.MyApplication.appContext)
        val isLocal = _uiState.value.currentVideoUrl?.let { it.startsWith("file://") || it.startsWith("content://") } ?: false
        if (isOffline || isLocal) {
            _uiState.value = _uiState.value.copy(availableServers = emptyList())
            return
        }
        if (_uiState.value.availableServers != servers && servers.isNotEmpty()) {
            val firstServer = servers.first()
            val link = com.example.ui.screens.player.ServerStateStore.extractedServerLinks[firstServer]
            val id = com.example.ui.screens.player.ServerStateStore.extractedServerIds[firstServer]
            
            var nextExtractionUrl = _uiState.value.extractionUrl
            if (link != null && link.isNotEmpty()) {
                nextExtractionUrl = link
            }
            
            _uiState.value = _uiState.value.copy(
                availableServers = servers,
                currentServer = firstServer,
                extractionUrl = nextExtractionUrl,
                serverIdToChange = id
            )
            
            if (nextExtractionUrl != null) {
                startExtractionTimeout()
            }
        }
    }

    private fun startExtractionTimeout() {
        extractionTimeoutJob?.cancel()
        extractionTimeoutJob = viewModelScope.launch {
            delay(300000) // 5 minutes timeout to allow for manual Cloudflare bypass
            if (_uiState.value.currentVideoUrl == null) {
                tryNextFallback()
            }
        }
    }
    
    fun tryNextFallback() {
        val fallbacks = _uiState.value.fallbackWebsites
        if (fallbacks.isNotEmpty()) {
            val nextSite = fallbacks.first()
            _uiState.value = _uiState.value.copy(
                currentWebsite = nextSite,
                fallbackWebsites = fallbacks.drop(1),
                isLoading = true,
                currentVideoUrl = null
            )
            generateExtractionUrl()
        } else {
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    private fun generateExtractionUrl() {
        val state = _uiState.value
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, extractionUrl = null)
            val ctx = com.example.MyApplication.appContext
            val managedOrchestrator = com.example.extension.orchestrator.ManagedMediaOrchestrator.getInstance(ctx)
            val outcome = managedOrchestrator.discoverServers(
                title = state.title,
                isMovie = state.isMovie,
                season = state.currentSeasonNumber,
                episode = state.currentEpisodeNumber,
                mediaId = state.mediaId
            )
            
            when (outcome) {
                is com.example.extension.orchestrator.ManagedDiscoveryOutcome.Success -> {
                    val streamServers = outcome.servers.filter {
                        !it.name.contains("(تحميل)") && !it.link.endsWith(".mp4") && !it.link.endsWith(".mkv")
                    }
                    val streamList = if (streamServers.isNotEmpty()) streamServers.map { it.name } else outcome.servers.map { it.name }
                    val linksMap = outcome.servers.associate { it.name to it.link }
                    val idsMap = outcome.servers.associate { it.name to it.id }
                    val chosenServer = if (state.currentServer in streamList) state.currentServer else streamList.firstOrNull() ?: ""
                    val directUrl = outcome.directStream?.streamUrl ?: linksMap[chosenServer] ?: outcome.servers.firstOrNull()?.link

                    _uiState.value = _uiState.value.copy(
                        availableServers = streamList,
                        availableServerLinks = linksMap,
                        availableServerIds = idsMap,
                        currentServer = chosenServer,
                        currentWebsite = outcome.website
                    )

                    if (directUrl != null && (directUrl.contains(".mp4") || directUrl.contains(".m3u8") || directUrl.contains("akamaized.net"))) {
                        setFinalVideoUrl(directUrl)
                    } else if (directUrl != null) {
                        _uiState.value = _uiState.value.copy(extractionUrl = directUrl, isLoading = true)
                        val serverItem = com.example.extension.managed.model.ServerItem(
                            id = idsMap[chosenServer] ?: chosenServer,
                            name = chosenServer,
                            link = directUrl
                        )
                        val extractResult = managedOrchestrator.extractPlaybackSource(serverItem, state.title)
                        if (extractResult.isSuccess) {
                            setFinalVideoUrl(extractResult.getOrThrow().streamUrl)
                        } else {
                            setFinalVideoUrl(directUrl)
                        }
                    } else {
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
                }
                is com.example.extension.orchestrator.ManagedDiscoveryOutcome.RecoverableFailure -> {
                    tryNextFallback()
                }
                is com.example.extension.orchestrator.ManagedDiscoveryOutcome.SecurityFailure -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }
}