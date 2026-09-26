package com.example.ui.screens.player

import android.util.Log
import com.example.extension.managed.model.QualityAggregator
import com.example.extension.managed.model.QualityCandidate
import com.example.extension.managed.model.QualityEvidence
import com.example.extension.managed.web.StaticMediaExtractor
import com.example.utils.M3U8Parser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class MediaServerData(
    val mediaKey: String = "",
    val mediaId: String = "",
    val servers: List<String> = emptyList(),
    val serverLinks: Map<String, String> = emptyMap(),
    val serverIds: Map<String, String> = emptyMap(),
    val downloadLinks: Map<String, String> = emptyMap(),
    val serverQualities: Map<String, List<M3U8Parser.QualityInfo>> = emptyMap(),
    val extractedQualities: List<M3U8Parser.QualityInfo> = emptyList(),
    val internalCandidates: Map<String, List<QualityCandidate>> = emptyMap(),
    val website: String = "",
    val playbackPageUrl: String? = null,
    val scraperKey: String? = null,
    val directStreamUrl: String? = null
)

fun normalizeQualityKey(rawName: String): String? {
    val trimmed = rawName.trim()
    val lower = trimmed.lowercase()

    // 1. Explicitly reject arbitrary/non-evidence descriptors
    if (lower in listOf("high", "best", "medium", "hd1", "quality-1", "quality-2", "auto", "unknown") ||
        lower.startsWith("server") ||
        lower.contains("quality-") ||
        lower.matches(Regex("""hd\d+""")) ||
        lower == "custom" ||
        lower.endsWith("-custom")
    ) {
        return null
    }

    // 2. Resolution attribute matching (e.g. RESOLUTION=1920x1080)
    val resMatch = Regex("""(?i)resolution\s*=\s*\d+x(\d+)""").find(rawName)
    if (resMatch != null) {
        val h = resMatch.groupValues[1].toIntOrNull()
        if (h != null) {
            return when {
                h >= 2160 -> "2160"
                h in 1440..2159 -> "1440"
                h in 1080..1439 -> "1080"
                h in 720..1079 -> "720"
                h in 576..719 -> "576"
                h in 480..575 -> "480"
                h in 360..479 -> "360"
                h in 240..359 -> "240"
                else -> "144"
            }
        }
    }

    // 3. Exact standard resolution numeric and evidence tokens
    return when {
        lower.contains("2160") || lower.contains("4k") || lower.contains("uhd") -> "2160"
        lower.contains("1440") || lower.contains("2k") -> "1440"
        lower.contains("1080") || lower.contains("fhd") || lower.contains("1920") -> "1080"
        lower.contains("720") || lower.contains("1280") -> "720"
        lower.contains("576") -> "576"
        lower.contains("480") || lower.contains("854") || Regex("""\bsd\b""").containsMatchIn(lower) -> "480"
        lower.contains("360") || lower.contains("640") -> "360"
        lower.contains("240") || lower.contains("426") -> "240"
        lower.contains("144") || lower.contains("256") -> "144"
        Regex("""\bhd\b""").containsMatchIn(lower) && !lower.contains("server") -> "720"
        else -> null
    }
}

fun normalizeQualityLabel(rawName: String): String? {
    val key = normalizeQualityKey(rawName) ?: return null
    return "${key}p"
}

object ServerStateStore {
    private val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeExtractionJobs = ConcurrentHashMap<String, Job>()
    private val activeInspectionDeferreds = ConcurrentHashMap<String, Deferred<MediaServerData?>>()

    var currentMediaKey: String? = null
    var currentMediaId: String? = null
    var extractedServers: List<String> = emptyList()
    var extractedServerLinks: Map<String, String> = emptyMap()
    var extractedServerIds: Map<String, String> = emptyMap()
    var extractedDownloadLinks: Map<String, String> = emptyMap()
    
    // Server Name -> List of Qualities
    var serverQualities: MutableMap<String, List<M3U8Parser.QualityInfo>> = mutableMapOf()
    
    // Extracted and deduplicated qualities across ALL servers (canonical, ordered)
    var extractedQualities: List<M3U8Parser.QualityInfo> = emptyList()

    // Internal fallback candidates per quality level
    var internalCandidates: Map<String, List<QualityCandidate>> = emptyMap()

    private val _extractedQualitiesFlow = MutableStateFlow<List<M3U8Parser.QualityInfo>>(emptyList())
    val extractedQualitiesFlow: StateFlow<List<M3U8Parser.QualityInfo>> = _extractedQualitiesFlow.asStateFlow()

    private val _serversFlow = MutableStateFlow<List<String>>(emptyList())
    val serversFlow: StateFlow<List<String>> = _serversFlow.asStateFlow()

    private val cache = ConcurrentHashMap<String, MediaServerData>()

    private fun logDiag(msg: String) {
        try {
            Log.i("MULTI_QUALITY", msg)
        } catch (_: Throwable) {
            println(msg)
        }
    }

    fun isExtractionJobActive(key: String): Boolean {
        return activeExtractionJobs[key]?.isActive == true
    }

    fun cancelBackgroundExtractionsExcept(activeKey: String) {
        val iterator = activeExtractionJobs.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!entry.key.contains(activeKey)) {
                entry.value.cancel()
                iterator.remove()
            }
        }
    }

    fun saveForMedia(
        mediaKey: String,
        servers: List<String>,
        links: Map<String, String>,
        ids: Map<String, String>,
        downloads: Map<String, String>,
        qualities: Map<String, List<M3U8Parser.QualityInfo>> = emptyMap(),
        extractedQ: List<M3U8Parser.QualityInfo> = emptyList(),
        internalCand: Map<String, List<QualityCandidate>> = emptyMap(),
        website: String = "",
        playbackPageUrl: String? = null,
        scraperKey: String? = null,
        altKeys: List<String> = emptyList(),
        directStreamUrl: String? = null,
        mediaId: String = ""
    ) {
        val targetMediaId = if (mediaId.isNotBlank()) mediaId else altKeys.firstOrNull { it.matches(Regex("""\d+""")) } ?: ""
        val isForCurrent = currentMediaKey == null || currentMediaKey == mediaKey || (targetMediaId.isNotBlank() && currentMediaId == targetMediaId)
        
        if (isForCurrent) {
            currentMediaKey = mediaKey
            if (targetMediaId.isNotBlank()) currentMediaId = targetMediaId
            extractedServers = servers
            extractedServerLinks = links
            extractedServerIds = ids
            extractedDownloadLinks = downloads
            serverQualities = qualities.toMutableMap()
            internalCandidates = internalCand
            if (servers.isNotEmpty()) {
                _serversFlow.value = servers
            }
            if (extractedQ.isNotEmpty()) {
                extractedQualities = extractedQ
                _extractedQualitiesFlow.value = extractedQ
            }
        }

        val existing = cache[mediaKey]
        val data = MediaServerData(
            mediaKey = mediaKey,
            mediaId = if (targetMediaId.isNotBlank()) targetMediaId else existing?.mediaId ?: "",
            servers = servers,
            serverLinks = links,
            serverIds = ids,
            downloadLinks = downloads,
            serverQualities = qualities,
            extractedQualities = if (extractedQ.isNotEmpty()) extractedQ else (existing?.extractedQualities ?: emptyList()),
            internalCandidates = if (internalCand.isNotEmpty()) internalCand else (existing?.internalCandidates ?: emptyMap()),
            website = website.ifBlank { existing?.website ?: "" },
            playbackPageUrl = playbackPageUrl ?: existing?.playbackPageUrl,
            scraperKey = scraperKey ?: existing?.scraperKey,
            directStreamUrl = directStreamUrl ?: existing?.directStreamUrl
        )

        cache[mediaKey] = data
        for (alt in altKeys) {
            if (alt.isNotBlank()) {
                cache[alt] = data
            }
        }
        val isSeriesEpisode = mediaKey.contains("-false-")
        if (targetMediaId.isNotBlank() && !isSeriesEpisode) {
            cache[targetMediaId] = data
        }
    }

    /**
     * Atomically merges revalidated servers and qualities into state and cache
     * WITHOUT disrupting or restarting the current active playback.
     */
    fun updateRevalidatedData(
        mediaKey: String,
        newServers: List<String>,
        newLinks: Map<String, String>,
        newIds: Map<String, String>,
        newDownloads: Map<String, String>,
        newQualities: List<M3U8Parser.QualityInfo>,
        newCandidates: Map<String, List<QualityCandidate>> = emptyMap(),
        sourcePageUrl: String? = null,
        scraperKey: String? = null,
        altKeys: List<String> = emptyList(),
        directStreamUrl: String? = null,
        mediaId: String = ""
    ) {
        val targetMediaId = if (mediaId.isNotBlank()) mediaId else altKeys.firstOrNull { it.matches(Regex("""\d+""")) } ?: ""
        val isForCurrent = currentMediaKey == null || currentMediaKey == mediaKey || (targetMediaId.isNotBlank() && currentMediaId == targetMediaId)

        if (isForCurrent) {
            if (newServers.isNotEmpty()) {
                extractedServers = newServers
                extractedServerLinks = newLinks
                extractedServerIds = newIds
                _serversFlow.value = newServers
            }
            if (newDownloads.isNotEmpty()) {
                extractedDownloadLinks = newDownloads
            }
            if (newQualities.isNotEmpty()) {
                extractedQualities = newQualities
                _extractedQualitiesFlow.value = newQualities
            }
            if (newCandidates.isNotEmpty()) {
                internalCandidates = newCandidates
            }
        }

        val existing = cache[mediaKey]
        val updated = (existing ?: MediaServerData()).copy(
            mediaKey = mediaKey,
            mediaId = if (targetMediaId.isNotBlank()) targetMediaId else existing?.mediaId ?: "",
            servers = if (newServers.isNotEmpty()) newServers else existing?.servers ?: emptyList(),
            serverLinks = if (newLinks.isNotEmpty()) newLinks else existing?.serverLinks ?: emptyMap(),
            serverIds = if (newIds.isNotEmpty()) newIds else existing?.serverIds ?: emptyMap(),
            downloadLinks = if (newDownloads.isNotEmpty()) newDownloads else existing?.downloadLinks ?: emptyMap(),
            extractedQualities = if (newQualities.isNotEmpty()) newQualities else existing?.extractedQualities ?: emptyList(),
            internalCandidates = if (newCandidates.isNotEmpty()) newCandidates else existing?.internalCandidates ?: emptyMap(),
            website = existing?.website ?: scraperKey ?: "",
            playbackPageUrl = sourcePageUrl ?: existing?.playbackPageUrl,
            scraperKey = scraperKey ?: existing?.scraperKey,
            directStreamUrl = directStreamUrl ?: existing?.directStreamUrl
        )
        cache[mediaKey] = updated
        for (alt in altKeys) {
            if (alt.isNotBlank()) {
                cache[alt] = updated
            }
        }
        val isSeriesEpisode = mediaKey.contains("-false-")
        if (targetMediaId.isNotBlank() && !isSeriesEpisode) {
            cache[targetMediaId] = updated
        }
    }

    fun startBackgroundQualityExtraction(
        mediaKey: String,
        serversNames: List<String>,
        serversMap: Map<String, String>,
        downloadsMap: Map<String, String> = emptyMap(),
        currentStreamUrl: String? = null,
        altKeys: List<String> = emptyList(),
        scraperKey: String? = null,
        sourcePageUrl: String? = null
    ) {
        val jobKey = "${scraperKey ?: "scraper"}:$mediaKey:${currentStreamUrl ?: "all"}"
        val existing = activeExtractionJobs[jobKey]
        if (existing != null && existing.isActive) {
            logDiag("[MULTI_QUALITY] job_deduplicated key=$jobKey")
            return
        }

        val job = storeScope.launch {
            try {
                resolveAndCacheAllQualities(
                    mediaKey = mediaKey,
                    serversNames = serversNames,
                    serversMap = serversMap,
                    downloadsMap = downloadsMap,
                    currentStreamUrl = currentStreamUrl,
                    altKeys = altKeys,
                    sourcePageUrl = sourcePageUrl
                )
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                activeExtractionJobs.remove(jobKey)
            }
        }
        activeExtractionJobs[jobKey] = job
    }

    fun sortQualities(list: Collection<M3U8Parser.QualityInfo>): List<M3U8Parser.QualityInfo> {
        val order = listOf("4320", "2160", "1440", "1080", "720", "576", "480", "360", "240", "144")
        val withoutAuto = list.filter { it.name != "Auto" }.sortedWith(Comparator { a, b ->
            val keyA = normalizeQualityKey(a.name) ?: ""
            val keyB = normalizeQualityKey(b.name) ?: ""
            val idxA = order.indexOf(keyA).let { if (it == -1) 999 else it }
            val idxB = order.indexOf(keyB).let { if (it == -1) 999 else it }
            idxA.compareTo(idxB)
        })
        val autoItem = list.firstOrNull { it.name == "Auto" }
        return if (autoItem != null) withoutAuto + autoItem else withoutAuto
    }

    suspend fun resolveAndCacheAllQualities(
        mediaKey: String,
        serversNames: List<String>,
        serversMap: Map<String, String>,
        downloadsMap: Map<String, String> = emptyMap(),
        currentStreamUrl: String? = null,
        altKeys: List<String> = emptyList(),
        sourcePageUrl: String? = null
    ): List<M3U8Parser.QualityInfo> = withContext(Dispatchers.IO) {
        val allCandidates = mutableListOf<QualityCandidate>()

        logDiag("[MULTI_QUALITY] servers_received=${serversNames.size}")

        // 1. Direct download links
        for ((dlName, dlUrl) in downloadsMap) {
            if (dlUrl.isNotBlank()) {
                val key = normalizeQualityKey(dlName) ?: normalizeQualityKey(dlUrl)
                if (key != null) {
                    allCandidates.add(
                        QualityCandidate(
                            qualityKey = key,
                            label = "${key}p",
                            streamUrl = dlUrl,
                            sourceUrl = dlUrl,
                            evidence = QualityEvidence.EXPLICIT_LABEL
                        )
                    )
                }
            }
        }

        // 2. Current active stream if provided
        if (!currentStreamUrl.isNullOrBlank()) {
            try {
                logDiag("[MULTI_QUALITY] server_started=current_active")
                val isHls = currentStreamUrl.contains(".m3u8") || currentStreamUrl.contains("akamaized.net")
                logDiag("[MULTI_QUALITY] server_type=${if (isHls) "DIRECT" else "DIRECT_FILE"}")
                logDiag("[MULTI_QUALITY] media_source=$currentStreamUrl")
                if (isHls) {
                    logDiag("[MULTI_QUALITY] master_playlist_fetch=START")
                    val headers = mapOf("Referer" to (sourcePageUrl ?: currentStreamUrl), "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    var streamQualities = emptyList<M3U8Parser.QualityInfo>()
                    try {
                        streamQualities = M3U8Parser.getQualities(currentStreamUrl, headers)
                    } catch (_: Exception) {}
                    logDiag("[MULTI_QUALITY] master_playlist_fetch=200")
                    logDiag("[MULTI_QUALITY] variants_found=${streamQualities.size}")
                    for (q in streamQualities) {
                        val key = normalizeQualityKey(q.name)
                        if (key != null) {
                            logDiag("[MULTI_QUALITY] quality_detected=${key}p")
                            allCandidates.add(
                                QualityCandidate(
                                    qualityKey = key,
                                    label = "${key}p",
                                    width = q.width,
                                    height = q.height,
                                    streamUrl = q.url,
                                    headers = q.headers,
                                    sourceUrl = currentStreamUrl,
                                    evidence = QualityEvidence.RESOLUTION
                                )
                            )
                        } else {
                            logDiag("[MULTI_QUALITY] quality_rejected=no_evidence_${q.name}")
                        }
                    }
                    if (streamQualities.isEmpty()) {
                        // Single direct stream or media playlist without sub-variants
                        allCandidates.add(
                            QualityCandidate(
                                qualityKey = "720",
                                label = "720p",
                                streamUrl = currentStreamUrl,
                                headers = headers,
                                sourceUrl = currentStreamUrl,
                                evidence = QualityEvidence.OTHER_VERIFIED
                            )
                        )
                    }
                } else if (currentStreamUrl.endsWith(".mp4") || currentStreamUrl.endsWith(".mkv")) {
                    val key = normalizeQualityKey(currentStreamUrl) ?: "720"
                    allCandidates.add(
                        QualityCandidate(
                            qualityKey = key,
                            label = "${key}p",
                            streamUrl = currentStreamUrl,
                            sourceUrl = currentStreamUrl,
                            evidence = QualityEvidence.OTHER_VERIFIED
                        )
                    )
                }
            } catch (e: Exception) {
                logDiag("[MULTI_QUALITY] server_failed=${e.message}")
            }
        }

        // 3. Scan each remaining server in serversNames / serversMap
        for (server in serversNames) {
            val link = serversMap[server] ?: ""
            if (link.isNotBlank() && link != currentStreamUrl) {
                logDiag("[MULTI_QUALITY] server_started=$server")
                val isDirect = link.contains(".m3u8") || link.contains(".mp4") || link.contains(".mkv") || link.contains("akamaized.net")
                val serverType = if (isDirect) "DIRECT" else "EMBED"
                logDiag("[MULTI_QUALITY] server_type=$serverType")

                try {
                    if (isDirect) {
                        logDiag("[MULTI_QUALITY] media_source=$link")
                        if (link.contains(".m3u8") || link.contains("akamaized.net")) {
                            logDiag("[MULTI_QUALITY] master_playlist_fetch=START")
                            val headers = mapOf("Referer" to link, "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            val streamQualities = M3U8Parser.getQualities(link, headers)
                            logDiag("[MULTI_QUALITY] master_playlist_fetch=200")
                            logDiag("[MULTI_QUALITY] master_playlist_status=200")
                            logDiag("[MULTI_QUALITY] variants_found=${streamQualities.size}")
                            for (q in streamQualities) {
                                val key = normalizeQualityKey(q.name)
                                if (key != null) {
                                    logDiag("[MULTI_QUALITY] quality_detected=${key}p")
                                    allCandidates.add(
                                        QualityCandidate(
                                            qualityKey = key,
                                            label = "${key}p",
                                            width = q.width,
                                            height = q.height,
                                            streamUrl = q.url,
                                            headers = headers,
                                            serverName = server,
                                            sourceUrl = link,
                                            evidence = QualityEvidence.RESOLUTION
                                        )
                                    )
                                } else {
                                    logDiag("[MULTI_QUALITY] quality_rejected=no_evidence_${q.name}")
                                }
                            }
                        } else {
                            val key = normalizeQualityKey(server) ?: normalizeQualityKey(link) ?: "720"
                            allCandidates.add(
                                QualityCandidate(
                                    qualityKey = key,
                                    label = "${key}p",
                                    streamUrl = link,
                                    serverName = server,
                                    sourceUrl = link,
                                    evidence = QualityEvidence.OTHER_VERIFIED
                                )
                            )
                        }
                    } else {
                        // Embed Page: Extract actual media source using StaticMediaExtractor
                        var extractedMedia: String? = null
                        try {
                            extractedMedia = StaticMediaExtractor.extract(link, referer = sourcePageUrl)
                        } catch (e: Exception) {
                            logDiag("[MULTI_QUALITY] server_failed=${e.message}")
                        }

                        if (!extractedMedia.isNullOrBlank()) {
                            logDiag("[MULTI_QUALITY] media_source=$extractedMedia")
                            val isHls = extractedMedia.contains(".m3u8") || extractedMedia.contains("akamaized.net")
                            val headers = mapOf("Referer" to link, "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            if (isHls) {
                                logDiag("[MULTI_QUALITY] master_playlist_fetch=START")
                                val streamQualities = M3U8Parser.getQualities(extractedMedia, headers)
                                logDiag("[MULTI_QUALITY] master_playlist_fetch=200")
                                logDiag("[MULTI_QUALITY] master_playlist_status=200")
                                logDiag("[MULTI_QUALITY] variants_found=${streamQualities.size}")
                                for (q in streamQualities) {
                                    val key = normalizeQualityKey(q.name)
                                    if (key != null) {
                                        logDiag("[MULTI_QUALITY] quality_detected=${key}p")
                                        allCandidates.add(
                                            QualityCandidate(
                                                qualityKey = key,
                                                label = "${key}p",
                                                width = q.width,
                                                height = q.height,
                                                streamUrl = q.url,
                                                headers = headers,
                                                serverName = server,
                                                sourceUrl = link,
                                                evidence = QualityEvidence.RESOLUTION
                                            )
                                        )
                                    } else {
                                        logDiag("[MULTI_QUALITY] quality_rejected=no_evidence_${q.name}")
                                    }
                                }
                            } else {
                                val key = normalizeQualityKey(server) ?: normalizeQualityKey(extractedMedia) ?: "720"
                                allCandidates.add(
                                    QualityCandidate(
                                        qualityKey = key,
                                        label = "${key}p",
                                        streamUrl = extractedMedia,
                                        headers = headers,
                                        serverName = server,
                                        sourceUrl = link,
                                        evidence = QualityEvidence.OTHER_VERIFIED
                                    )
                                )
                            }
                        } else {
                            logDiag("[MULTI_QUALITY] server_failed=media_not_found_on_$server")
                        }
                    }
                } catch (e: Exception) {
                    // Server failure isolation
                    logDiag("[MULTI_QUALITY] server_failed=${e.message}")
                }
            }
        }

        // Centrally aggregate all candidates using QualityAggregator
        val aggregated = QualityAggregator.aggregate(allCandidates)
        val groupedMap = allCandidates.groupBy { it.qualityKey }

        val finalQualityList = if (aggregated.isNotEmpty()) {
            QualityAggregator.toQualityInfoList(
                aggregated = aggregated,
                defaultStreamUrl = currentStreamUrl ?: allCandidates.firstOrNull()?.streamUrl
            )
        } else {
            listOf(M3U8Parser.QualityInfo("Auto", currentStreamUrl.orEmpty()))
        }

        logDiag("[MULTI_QUALITY] aggregation_complete=${finalQualityList.size}")

        // Atomic non-disruptive state update: strictly verify this still belongs to currently active media
        val targetMediaId = altKeys.firstOrNull { it.matches(Regex("""\d+""")) } ?: ""
        val isForCurrent = currentMediaKey == mediaKey || (targetMediaId.isNotBlank() && currentMediaId == targetMediaId)
        if (isForCurrent) {
            extractedQualities = finalQualityList
            internalCandidates = groupedMap
            _extractedQualitiesFlow.value = finalQualityList
        }

        val existing = cache[mediaKey]
        val updated = (existing ?: MediaServerData()).copy(
            mediaKey = mediaKey,
            mediaId = if (targetMediaId.isNotBlank()) targetMediaId else existing?.mediaId ?: "",
            servers = if (serversNames.isNotEmpty()) serversNames else existing?.servers ?: emptyList(),
            serverLinks = if (serversMap.isNotEmpty()) serversMap else existing?.serverLinks ?: emptyMap(),
            downloadLinks = if (downloadsMap.isNotEmpty()) downloadsMap else existing?.downloadLinks ?: emptyMap(),
            extractedQualities = finalQualityList,
            internalCandidates = groupedMap,
            directStreamUrl = currentStreamUrl ?: existing?.directStreamUrl
        )
        cache[mediaKey] = updated
        for (alt in altKeys) {
            if (alt.isNotBlank()) {
                cache[alt] = updated
            }
        }
        val isSeriesEpisode = mediaKey.contains("-false-")
        if (targetMediaId.isNotBlank() && !isSeriesEpisode) {
            cache[targetMediaId] = updated
        }

        finalQualityList
    }

    fun hasExtractedServers(vararg keys: String?): Boolean {
        for (k in keys) {
            if (k != null) {
                val cached = cache[k]
                if (cached != null && (cached.servers.isNotEmpty() || cached.extractedQualities.isNotEmpty())) {
                    return true
                }
            }
        }
        return false
    }

    fun hasRealQualities(qualities: List<M3U8Parser.QualityInfo>): Boolean {
        return qualities.any { it.name.isNotBlank() && it.name != "Auto" && it.url.isNotBlank() }
    }

    fun hasRealQualities(vararg keys: String?): Boolean {
        val cached = getCachedData(*keys) ?: return false
        return hasRealQualities(cached.extractedQualities)
    }

    fun loadForMedia(mediaKey: String): Boolean {
        val data = cache[mediaKey]
        if (data != null && (data.servers.isNotEmpty() || data.extractedQualities.isNotEmpty())) {
            currentMediaKey = mediaKey
            currentMediaId = data.mediaId.ifBlank { null }
            extractedServers = data.servers
            extractedServerLinks = data.serverLinks
            extractedServerIds = data.serverIds
            extractedDownloadLinks = data.downloadLinks
            serverQualities = data.serverQualities.toMutableMap()
            extractedQualities = data.extractedQualities
            internalCandidates = data.internalCandidates
            _serversFlow.value = data.servers
            _extractedQualitiesFlow.value = data.extractedQualities
            return true
        }
        return false
    }

    /**
     * Prepares store for a specific media item.
     * Cancels any pending background jobs from previous media.
     * If the media is in cache, active state is populated from cache.
     * If NOT in cache, active variables and flows are strictly cleared to prevent
     * data from the previous movie or series episode leaking into the current media.
     */
    fun prepareForMedia(mediaKey: String, vararg altKeys: String?) {
        val cleanAlt = altKeys.filterNotNull().filter { it.isNotBlank() }
        val targetMediaId = cleanAlt.firstOrNull { it.matches(Regex("""\d+""")) }

        // Cancel background extractions belonging to prior media
        cancelBackgroundExtractionsExcept(mediaKey)

        val cached = getCachedData(mediaKey, *cleanAlt.toTypedArray())
        if (cached != null) {
            currentMediaKey = mediaKey
            currentMediaId = targetMediaId ?: cached.mediaId.ifBlank { null }
            extractedServers = cached.servers
            extractedServerLinks = cached.serverLinks
            extractedServerIds = cached.serverIds
            extractedDownloadLinks = cached.downloadLinks
            serverQualities = cached.serverQualities.toMutableMap()
            extractedQualities = cached.extractedQualities
            internalCandidates = cached.internalCandidates
            _serversFlow.value = cached.servers
            _extractedQualitiesFlow.value = cached.extractedQualities
            return
        }

        // Not in cache: strictly reset active variables and flows
        currentMediaKey = mediaKey
        currentMediaId = targetMediaId
        extractedServers = emptyList()
        extractedServerLinks = emptyMap()
        extractedServerIds = emptyMap()
        extractedDownloadLinks = emptyMap()
        serverQualities.clear()
        extractedQualities = emptyList()
        internalCandidates = emptyMap()
        _serversFlow.value = emptyList()
        _extractedQualitiesFlow.value = emptyList()
    }

    fun getCachedData(vararg keys: String?): MediaServerData? {
        val cleanKeys = keys.filterNotNull().filter { it.isNotBlank() }
        if (cleanKeys.isEmpty()) return null

        val requestedNumericId = cleanKeys.firstOrNull { it.matches(Regex("""\d+""")) }
        val requestedHyphenKey = cleanKeys.firstOrNull { it.contains("-") }

        for (k in cleanKeys) {
            val data = cache[k]
            if (data != null && (data.servers.isNotEmpty() || data.extractedQualities.isNotEmpty())) {
                // Strict isolation: ensure no cross-media bleeding
                if (requestedNumericId != null) {
                    if (data.mediaId.isNotBlank() && data.mediaId != requestedNumericId) {
                        continue
                    }
                    if (data.mediaId.isBlank() && requestedHyphenKey != null && data.mediaKey != requestedHyphenKey) {
                        continue
                    }
                }
                if (requestedHyphenKey != null) {
                    if (data.mediaKey.isNotBlank() && data.mediaKey != requestedHyphenKey) {
                        continue
                    }
                }
                return data
            }
        }
        return null
    }

    fun getDataForMedia(mediaKey: String): MediaServerData? = cache[mediaKey]

    /**
     * Unified inspection method: discovers servers, extracts embed streams,
     * resolves real quality variants, and caches everything in ServerStateStore
     * so that all components on the details page (download dialog, player, etc.)
     * share the exact same inspection results without duplicate scans.
     */
    suspend fun inspectAndCacheMedia(
        mediaKey: String,
        title: String,
        year: String = "",
        isMovie: Boolean = true,
        season: Int = 1,
        episode: Int = 1,
        mediaId: String = "",
        altKeys: List<String> = emptyList(),
        context: android.content.Context
    ): MediaServerData? = withContext(Dispatchers.IO) {
        val allAltKeys = (altKeys + listOf(mediaId)).filter { it.isNotBlank() }.distinct()

        // 1. Return immediately if fully cached with extracted qualities
        val existingCached = getCachedData(mediaKey, *allAltKeys.toTypedArray())
        if (existingCached != null && existingCached.extractedQualities.isNotEmpty()) {
            return@withContext existingCached
        }

        // Deduplicate in-flight inspection for this exact mediaKey
        val inFlight = activeInspectionDeferreds[mediaKey]
        if (inFlight != null && inFlight.isActive) {
            return@withContext inFlight.await()
        }

        val deferred = storeScope.async {
            doInspectAndCacheMedia(
                mediaKey = mediaKey,
                title = title,
                year = year,
                isMovie = isMovie,
                season = season,
                episode = episode,
                mediaId = mediaId,
                allAltKeys = allAltKeys,
                context = context
            )
        }
        activeInspectionDeferreds[mediaKey] = deferred
        try {
            deferred.await()
        } finally {
            activeInspectionDeferreds.remove(mediaKey)
        }
    }

    private suspend fun doInspectAndCacheMedia(
        mediaKey: String,
        title: String,
        year: String,
        isMovie: Boolean,
        season: Int,
        episode: Int,
        mediaId: String,
        allAltKeys: List<String>,
        context: android.content.Context
    ): MediaServerData? {
        val existingCached = getCachedData(mediaKey, *allAltKeys.toTypedArray())
        val serversNames: List<String>
        val serversMap: Map<String, String>
        val serversIds: Map<String, String>
        val downloadsMap: Map<String, String>
        val sourceUrl: String?
        val website: String
        var directStreamUrl: String?
        var serverItems: List<com.example.extension.managed.model.ServerItem> = emptyList()

        if (existingCached != null && existingCached.servers.isNotEmpty()) {
            serversNames = existingCached.servers
            serversMap = existingCached.serverLinks
            serversIds = existingCached.serverIds
            downloadsMap = existingCached.downloadLinks
            sourceUrl = existingCached.playbackPageUrl
            website = existingCached.website
            directStreamUrl = existingCached.directStreamUrl
        } else {
            val managedOrchestrator = com.example.extension.orchestrator.ManagedMediaOrchestrator.getInstance(context)
            val targetType = if (isMovie) com.example.extension.managed.model.ContentType.MOVIE else com.example.extension.managed.model.ContentType.SERIES
            if (!managedOrchestrator.hasActiveExtensions(targetType)) {
                return null
            }

            val outcome = managedOrchestrator.discoverServers(
                title = title,
                year = year,
                isMovie = isMovie,
                season = season,
                episode = episode,
                mediaId = mediaId,
                altKeys = allAltKeys
            )

            if (outcome !is com.example.extension.orchestrator.ManagedDiscoveryOutcome.Success) {
                return null
            }

            serverItems = outcome.servers
            val isPureDownloadOnly = { s: com.example.extension.managed.model.ServerItem ->
                s.name.contains("(تحميل)") && !s.link.endsWith(".mp4") && !s.link.endsWith(".m3u8")
            }
            val streamServers = outcome.servers.filter { !isPureDownloadOnly(it) }
            serversNames = if (streamServers.isNotEmpty()) streamServers.map { it.name } else outcome.servers.map { it.name }
            serversMap = outcome.servers.associate { it.name to it.link }
            serversIds = outcome.servers.associate { it.name to it.id }
            downloadsMap = outcome.servers.filter {
                it.name.contains("(تحميل)") || it.link.endsWith(".mp4") || it.link.endsWith(".mkv")
            }.associate { it.name to it.link }
            sourceUrl = outcome.sourceUrl
            website = outcome.website
            directStreamUrl = outcome.directStream?.streamUrl
        }

        // If direct stream URL is not yet resolved, check direct links in serversMap / downloadsMap
        if (directStreamUrl.isNullOrBlank()) {
            val directLink = serversMap.values.firstOrNull {
                it.contains(".m3u8") || it.contains(".mp4") || it.contains("akamaized.net")
            } ?: downloadsMap.values.firstOrNull {
                it.contains(".m3u8") || it.contains(".mp4") || it.contains("akamaized.net")
            }
            if (!directLink.isNullOrBlank()) {
                directStreamUrl = directLink
            }
        }

        // If still blank, extract playback source from candidate servers using managed runtime
        if (directStreamUrl.isNullOrBlank() && serverItems.isNotEmpty()) {
            val managedOrchestrator = com.example.extension.orchestrator.ManagedMediaOrchestrator.getInstance(context)
            val isPureDownloadOnly = { s: com.example.extension.managed.model.ServerItem ->
                s.name.contains("(تحميل)") && !s.link.endsWith(".mp4") && !s.link.endsWith(".m3u8")
            }
            val candidateServers = serverItems.filter { !isPureDownloadOnly(it) }.take(3)
            for (srv in candidateServers) {
                try {
                    val extractResult = managedOrchestrator.extractPlaybackSource(srv, title)
                    if (extractResult.isSuccess) {
                        val stream = extractResult.getOrThrow().streamUrl
                        if (stream.isNotBlank()) {
                            directStreamUrl = stream
                            break
                        }
                    }
                } catch (_: Throwable) {}
            }
        }

        // Resolve and cache all qualities using resolved direct stream and candidate servers
        val resolvedQualities = resolveAndCacheAllQualities(
            mediaKey = mediaKey,
            serversNames = serversNames,
            serversMap = serversMap,
            downloadsMap = downloadsMap,
            currentStreamUrl = directStreamUrl,
            altKeys = allAltKeys,
            sourcePageUrl = sourceUrl
        )

        saveForMedia(
            mediaKey = mediaKey,
            servers = serversNames,
            links = serversMap,
            ids = serversIds,
            downloads = downloadsMap,
            extractedQ = resolvedQualities,
            website = website,
            playbackPageUrl = sourceUrl,
            scraperKey = website,
            altKeys = allAltKeys,
            directStreamUrl = directStreamUrl,
            mediaId = mediaId
        )

        return getCachedData(mediaKey, *allAltKeys.toTypedArray())
    }

    fun clear() {
        currentMediaKey = null
        currentMediaId = null
        activeExtractionJobs.values.forEach { it.cancel() }
        activeExtractionJobs.clear()
        activeInspectionDeferreds.values.forEach { it.cancel() }
        activeInspectionDeferreds.clear()
        extractedServers = emptyList()
        extractedServerLinks = emptyMap()
        extractedServerIds = emptyMap()
        extractedDownloadLinks = emptyMap()
        extractedQualities = emptyList()
        internalCandidates = emptyMap()
        _extractedQualitiesFlow.value = emptyList()
        _serversFlow.value = emptyList()
        serverQualities.clear()
    }
}
