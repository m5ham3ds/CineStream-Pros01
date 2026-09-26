package com.example.ui.screens.player

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.model.DownloadItem
import com.example.data.repository.DownloadRepository
import com.example.extension.managed.error.ExtensionError
import com.example.extension.managed.model.ContentType
import com.example.extension.orchestrator.ManagedDiscoveryOutcome
import com.example.extension.orchestrator.ManagedMediaOrchestrator
import com.example.ui.theme.SuccessGreen
import com.example.utils.AndroidDownloader
import com.example.utils.M3U8Parser
import com.example.utils.NetworkConnectivityObserver
import com.example.utils.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StandardQualityDef(
    val key: String,         // "1080", "720", "480", "360", "240", "144"
    val label: String,       // "1080p", "720p", "480p", "360p", "240p", "144p"
    val badge: String,       // "FHD", "HD", "SD", etc.
    val description: String
)

val STANDARD_DOWNLOAD_QUALITIES = listOf(
    StandardQualityDef("1080", "1080p", "FHD", "Full HD"),
    StandardQualityDef("720", "720p", "HD", "High Definition"),
    StandardQualityDef("480", "480p", "SD", "Standard Definition"),
    StandardQualityDef("360", "360p", "360p", "Medium Quality"),
    StandardQualityDef("240", "240p", "240p", "Low Quality"),
    StandardQualityDef("144", "144p", "144p", "Data Saver")
)

@Composable
fun SmartDownloadQualityDialog(
    title: String,
    year: String = "",
    isMovie: Boolean = true,
    season: Int = 1,
    episode: Int = 1,
    isAnime: Boolean = false,
    posterUrl: String? = null,
    mediaId: String = "",
    onDismiss: () -> Unit,
    onNavigateToExtensions: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadRepository = remember { DownloadRepository(context) }
    val managedOrchestrator = remember { ManagedMediaOrchestrator.getInstance(context) }

    val mediaKey = "$title-$isMovie-$season-$episode"
    val fileId = if (isMovie) mediaId else "${mediaId}_${season}_${episode}"
    val fullTitle = if (isMovie) title else "$title - S${season}E${episode}"

    // Clean isolation: Ensure state store is primed for this media
    LaunchedEffect(mediaKey, mediaId) {
        ServerStateStore.prepareForMedia(mediaKey, fileId, mediaId)
    }

    // Check cached data first (strict isolation - never falls back to another movie)
    val cachedData = remember(mediaKey, fileId, mediaId) {
        ServerStateStore.getCachedData(mediaKey, fileId, mediaId)
    }

    // Pre-populate immediately from cache ONLY if extracted qualities belong to this media
    val initialCachedMap = remember(cachedData, mediaKey, mediaId) {
        if (cachedData != null && (mediaId.isBlank() || cachedData.mediaId.isBlank() || cachedData.mediaId == mediaId) && ServerStateStore.hasRealQualities(cachedData.extractedQualities)) {
            val map = mutableMapOf<String, M3U8Parser.QualityInfo>()
            for (q in cachedData.extractedQualities) {
                val key = normalizeQualityKey(q.name)
                if (key != null && !map.containsKey(key)) {
                    map[key] = q
                }
            }
            map
        } else {
            emptyMap()
        }
    }

    // State of resolved qualities: Map of "1080" -> QualityInfo(name="1080p", url="...")
    var availableQualitiesMap by remember(mediaKey) { mutableStateOf<Map<String, M3U8Parser.QualityInfo>>(initialCachedMap) }
    var hasScanned by remember(mediaKey) { mutableStateOf(initialCachedMap.isNotEmpty()) }
    var isChecking by remember(mediaKey) { mutableStateOf(initialCachedMap.isEmpty()) }
    var isFailed by remember(mediaKey) { mutableStateOf(false) }
    var isNetworkError by remember(mediaKey) { mutableStateOf(false) }
    var retryTrigger by remember(mediaKey) { mutableIntStateOf(0) }

    val networkObserver = remember { NetworkConnectivityObserver(context) }
    val isOnline by networkObserver.observe().collectAsState(initial = NetworkUtils.isInternetAvailable(context))

    // Automatically recover and retry when internet connection is restored
    LaunchedEffect(isOnline) {
        if (isOnline && (isNetworkError || (availableQualitiesMap.isEmpty() && !hasScanned))) {
            isNetworkError = false
            isFailed = false
            isChecking = true
            retryTrigger++
        }
    }

    // Live update when background server extraction resolves qualities for THIS media
    val liveQualities by ServerStateStore.extractedQualitiesFlow.collectAsState()
    LaunchedEffect(liveQualities, mediaKey, mediaId) {
        val isCurrentMedia = (ServerStateStore.currentMediaKey == mediaKey || ServerStateStore.currentMediaKey == fileId) &&
            (mediaId.isBlank() || ServerStateStore.currentMediaId == null || ServerStateStore.currentMediaId == mediaId)
        if (isCurrentMedia && liveQualities.isNotEmpty()) {
            val updatedMap = availableQualitiesMap.toMutableMap()
            var changed = false
            for (q in liveQualities) {
                val key = normalizeQualityKey(q.name)
                if (key != null && !updatedMap.containsKey(key)) {
                    updatedMap[key] = q
                    changed = true
                }
            }
            if (changed) {
                availableQualitiesMap = updatedMap
                hasScanned = true
                isChecking = false
            }
        }
    }

    fun executeDownload(qualityKey: String, qualityInfo: M3U8Parser.QualityInfo) {
        val qLabel = qualityInfo.name.ifEmpty { "${qualityKey}p" }
        val currentMediaData = ServerStateStore.getCachedData(mediaKey, fileId, mediaId)
        val isMatchingStore = (ServerStateStore.currentMediaKey == mediaKey || ServerStateStore.currentMediaKey == fileId) &&
            (mediaId.isBlank() || ServerStateStore.currentMediaId == null || ServerStateStore.currentMediaId == mediaId)
        val candidates = currentMediaData?.internalCandidates?.get(qualityKey)
            ?: (if (isMatchingStore) ServerStateStore.internalCandidates[qualityKey] else null)
            ?: emptyList()
        val canonicalSource = if (candidates.isNotEmpty()) {
            com.example.extension.managed.adapter.UnifiedDownloadCoordinator.fromCandidate(
                candidate = candidates.first(),
                mediaId = mediaId,
                title = fullTitle,
                posterUrl = posterUrl,
                isMovie = isMovie,
                episodeId = if (!isMovie) "${season}_$episode" else null,
                fallbackCandidates = candidates.drop(1)
            )
        } else {
            com.example.extension.managed.adapter.UnifiedDownloadCoordinator.buildDownloadSource(
                streamUrl = qualityInfo.url,
                mediaId = mediaId,
                title = fullTitle,
                quality = qLabel,
                qualityKey = qualityKey,
                headers = qualityInfo.headers,
                posterUrl = posterUrl,
                isMovie = isMovie,
                episodeId = if (!isMovie) "${season}_$episode" else null,
                sourceUrl = currentMediaData?.playbackPageUrl
            )
        }
        com.example.extension.managed.adapter.UnifiedDownloadCoordinator.download(
            context = context,
            source = canonicalSource,
            scope = scope,
            onStarted = {
                Toast.makeText(context, context.getString(R.string.download_started_success), Toast.LENGTH_SHORT).show()
            }
        )
        onDismiss()
    }

    // Initialize from cache or run full unified inspection
    LaunchedEffect(mediaKey, retryTrigger) {
        if (hasScanned && availableQualitiesMap.isNotEmpty()) {
            isChecking = false
            return@LaunchedEffect
        }
        if (!NetworkUtils.isInternetAvailable(context)) {
            isNetworkError = true
            isChecking = false
            return@LaunchedEffect
        }

        // 1. Check if cached qualities already exist for this media
        val currentCached = ServerStateStore.getCachedData(mediaKey, fileId, mediaId)
        if (currentCached != null && ServerStateStore.hasRealQualities(currentCached.extractedQualities)) {
            val cachedMap = mutableMapOf<String, M3U8Parser.QualityInfo>()
            for (q in currentCached.extractedQualities) {
                val key = normalizeQualityKey(q.name)
                if (key != null && !cachedMap.containsKey(key)) {
                    cachedMap[key] = q
                }
            }
            if (cachedMap.isNotEmpty()) {
                availableQualitiesMap = cachedMap
                hasScanned = true
                isChecking = false
                return@LaunchedEffect
            }
        }

        // 2. Run real inspection and quality extraction (embeds + direct streams)
        isChecking = true
        isFailed = false
        isNetworkError = false

        val inspectedData = ServerStateStore.inspectAndCacheMedia(
            mediaKey = mediaKey,
            title = title,
            year = year,
            isMovie = isMovie,
            season = season,
            episode = episode,
            mediaId = mediaId,
            altKeys = listOf(fileId, mediaId),
            context = context
        )

        if (inspectedData != null && inspectedData.extractedQualities.isNotEmpty()) {
            val resultMap = mutableMapOf<String, M3U8Parser.QualityInfo>()
            for (q in inspectedData.extractedQualities) {
                val key = normalizeQualityKey(q.name)
                if (key != null && !resultMap.containsKey(key)) {
                    resultMap[key] = q
                }
            }
            availableQualitiesMap = resultMap
            hasScanned = true
            isChecking = false
        } else {
            isChecking = false
            if (!NetworkUtils.isInternetAvailable(context)) {
                isNetworkError = true
            } else {
                isFailed = true
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    // Header handle
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(width = 36.dp, height = 4.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), CircleShape)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Title row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.select_download_quality),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = fullTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Checking status indicator
                    if (isChecking && !hasScanned) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.checking_quality_in_servers),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // If network lost
                    if (isNetworkError) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.connection_lost),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.connection_lost_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onDismiss,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(stringResource(R.string.cancel), color = Color.White)
                                    }
                                    Button(
                                        onClick = {
                                            if (NetworkUtils.isInternetAvailable(context)) {
                                                isNetworkError = false
                                                isFailed = false
                                                isChecking = true
                                                retryTrigger++
                                            } else {
                                                Toast.makeText(context, context.getString(R.string.no_internet_check_connection), Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text(stringResource(R.string.retry), color = Color.White)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    } else if (isFailed && availableQualitiesMap.isEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.no_download_sources_found),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // The 6 standard qualities list: 1080, 720, 480, 360, 240, 144
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(STANDARD_DOWNLOAD_QUALITIES) { qualityDef ->
                            val isFound = availableQualitiesMap.containsKey(qualityDef.key)
                            val isUnavailable = hasScanned && !isChecking && !isFound
                            val isClickEnabled = hasScanned && !isChecking && isFound

                            val cardBgColor = when {
                                isUnavailable -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                isFound && hasScanned -> MaterialTheme.colorScheme.surfaceVariant
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            }

                            val cardBorderColor = when {
                                isUnavailable -> Color.Transparent
                                isFound && hasScanned -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                            }

                            val titleColor = when {
                                isUnavailable -> Color.Gray
                                isFound && hasScanned -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            }

                            val descColor = when {
                                isUnavailable -> Color.Gray.copy(alpha = 0.6f)
                                isFound && hasScanned -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(enabled = isClickEnabled) {
                                        val qInfo = availableQualitiesMap[qualityDef.key]
                                        if (qInfo != null) {
                                            executeDownload(qualityDef.key, qInfo)
                                        }
                                    },
                                shape = RoundedCornerShape(16.dp),
                                color = cardBgColor,
                                border = androidx.compose.foundation.BorderStroke(1.dp, cardBorderColor)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Quality badge icon
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(
                                                if (isUnavailable) Color.Gray.copy(alpha = 0.12f)
                                                else if (isFound && hasScanned) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                else MaterialTheme.colorScheme.surfaceVariant,
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Download,
                                            contentDescription = null,
                                            tint = if (isUnavailable) Color.Gray else if (isFound && hasScanned) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    // Quality label & description
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = qualityDef.label,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = titleColor
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isUnavailable) Color.Gray.copy(alpha = 0.15f)
                                                else if (isFound && hasScanned) MaterialTheme.colorScheme.secondaryContainer
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            ) {
                                                Text(
                                                    text = qualityDef.badge,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isUnavailable) Color.Gray else if (isFound && hasScanned) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = qualityDef.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = descColor
                                        )
                                    }

                                    // Availability status badge
                                    if (hasScanned && !isChecking) {
                                        if (isFound) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = SuccessGreen.copy(alpha = 0.15f)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = SuccessGreen,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = stringResource(R.string.quality_available_badge),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = SuccessGreen,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        } else {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color.Gray.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.quality_unavailable_badge),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.Gray,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
