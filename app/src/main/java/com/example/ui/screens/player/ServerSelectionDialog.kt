package com.example.ui.screens.player

import android.annotation.SuppressLint
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudDownload
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.extension.managed.error.ExtensionError
import com.example.extension.managed.model.ContentType
import com.example.extension.orchestrator.ManagedDiscoveryOutcome
import com.example.extension.orchestrator.ManagedMediaOrchestrator
import com.example.ui.screens.extensions.NoExtensionsDialog
import com.example.ui.theme.SuccessGreen
import com.example.utils.M3U8Parser
import com.example.utils.NetworkConnectivityObserver
import com.example.utils.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSelectionDialog(
    title: String,
    year: String = "",
    isMovie: Boolean,
    season: Int = 1,
    episode: Int = 1,
    isAnime: Boolean = false,
    posterUrl: String? = null,
    mediaId: String = "",
    isDownloadMode: Boolean = false,
    onDismiss: () -> Unit,
    onPlay: (url: String, serverName: String, website: String) -> Unit,
    onNavigateToExtensions: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val mediaKey = "$title-$isMovie-$season-$episode"
    val fileId = if (isMovie) mediaId else "${mediaId}_${season}_${episode}"

    val cachedData = remember(mediaKey, fileId, mediaId) {
        ServerStateStore.getCachedData(mediaKey, fileId, mediaId)
    }
    val hasCachedWithQualities = cachedData != null && ServerStateStore.hasRealQualities(cachedData.extractedQualities)
    val hasCached = cachedData != null && cachedData.servers.isNotEmpty()

    var showSmartQualityDialog by remember(mediaKey, isDownloadMode) {
        mutableStateOf(isDownloadMode && hasCachedWithQualities)
    }

    if (showSmartQualityDialog) {
        SmartDownloadQualityDialog(
            title = title,
            year = year,
            isMovie = isMovie,
            season = season,
            episode = episode,
            isAnime = isAnime,
            posterUrl = posterUrl,
            mediaId = mediaId,
            onDismiss = onDismiss,
            onNavigateToExtensions = onNavigateToExtensions
        )
        return
    }

    val isStoreCurrent = (ServerStateStore.currentMediaKey == mediaKey || ServerStateStore.currentMediaKey == fileId) &&
        (mediaId.isBlank() || ServerStateStore.currentMediaId == null || ServerStateStore.currentMediaId == mediaId)

    val isLoadingInitial = if (isDownloadMode) !hasCachedWithQualities else !hasCached
    var isLoading by remember { mutableStateOf(isLoadingInitial) }
    var loadingMessage by remember {
        mutableStateOf(
            if (isDownloadMode) context.getString(R.string.checking_quality_in_servers)
            else context.getString(R.string.checking_bypassing_protection)
        )
    }

    var extractedServers by remember {
        mutableStateOf<List<String>>(
            cachedData?.servers ?: (if (isStoreCurrent) ServerStateStore.extractedServers else emptyList())
        )
    }
    var extractedServerLinks by remember {
        mutableStateOf<Map<String, String>>(
            cachedData?.serverLinks ?: (if (isStoreCurrent) ServerStateStore.extractedServerLinks else emptyMap())
        )
    }
    var extractedServerIds by remember {
        mutableStateOf<Map<String, String>>(
            cachedData?.serverIds ?: (if (isStoreCurrent) ServerStateStore.extractedServerIds else emptyMap())
        )
    }
    var extractedDownloadLinks by remember {
        mutableStateOf<Map<String, String>>(
            cachedData?.downloadLinks ?: (if (isStoreCurrent) ServerStateStore.extractedDownloadLinks else emptyMap())
        )
    }
    var finalWatchUrl by remember { mutableStateOf<String?>(null) }
    var isFailed by remember { mutableStateOf(false) }
    var isNetworkError by remember { mutableStateOf(false) }
    var retryTrigger by remember { mutableIntStateOf(0) }
    var showCancelConfirmDialog by remember { mutableStateOf(false) }
    var showNoMoreExtensionsDialog by remember { mutableStateOf(false) }

    val managedOrchestrator = remember { ManagedMediaOrchestrator.getInstance(context) }
    var activeSiteName by remember { mutableStateOf<String?>(null) }

    val networkObserver = remember { NetworkConnectivityObserver(context) }
    val isOnline by networkObserver.observe().collectAsState(initial = NetworkUtils.isInternetAvailable(context))

    // Automatically recover and retry when internet connection is restored
    LaunchedEffect(isOnline) {
        if (isOnline && (isNetworkError || (isLoading && !NetworkUtils.isInternetAvailable(context)))) {
            isNetworkError = false
            isFailed = false
            isLoading = true
            retryTrigger++
        }
    }

    var hasAutoPlayed by remember { mutableStateOf(false) }

    LaunchedEffect(extractedServers) {
        if (extractedServers.isNotEmpty() && !hasAutoPlayed) {
            hasAutoPlayed = true
            val firstServer = extractedServers.first()
            val finalUrl = extractedServerLinks[firstServer] ?: ""
            val dLink = extractedDownloadLinks[firstServer] ?: ""
            val rawWatchUrl = if (finalUrl.contains("akamaized.net") || finalUrl.endsWith(".m3u8") || finalUrl.endsWith(".mp4")) {
                finalUrl
            } else if (dLink.isNotEmpty()) {
                dLink
            } else {
                finalUrl
            }

            if (isDownloadMode) {
                loadingMessage = context.getString(R.string.checking_quality_in_servers)
                val directStreamUrl = withContext(Dispatchers.IO) {
                    var directUrl: String? = null
                    val firstDirect = extractedServers.mapNotNull { extractedServerLinks[it] }.firstOrNull {
                        it.contains(".m3u8") || it.contains(".mp4") || it.contains("akamaized.net")
                    } ?: extractedDownloadLinks.values.firstOrNull {
                        it.contains(".m3u8") || it.contains(".mp4") || it.contains("akamaized.net")
                    }
                    if (!firstDirect.isNullOrBlank()) {
                        directUrl = firstDirect
                    } else {
                        val isPureDownloadOnly = { s: com.example.extension.managed.model.ServerItem ->
                            s.name.contains("(تحميل)") && !s.link.endsWith(".mp4") && !s.link.endsWith(".m3u8")
                        }
                        val candidates = extractedServers.mapNotNull { name ->
                            val link = extractedServerLinks[name] ?: return@mapNotNull null
                            val id = extractedServerIds[name] ?: name
                            com.example.extension.managed.model.ServerItem(id = id, name = name, link = link)
                        }.filter { !isPureDownloadOnly(it) }.take(3)

                        for (srv in candidates) {
                            try {
                                val extractResult = managedOrchestrator.extractPlaybackSource(srv, title)
                                if (extractResult.isSuccess) {
                                    val stream = extractResult.getOrThrow().streamUrl
                                    if (!stream.isNullOrBlank()) {
                                        directUrl = stream
                                        break
                                    }
                                }
                            } catch (_: Throwable) {}
                        }
                    }
                    directUrl
                }

                val resolvedQualities = withContext(Dispatchers.IO) {
                    ServerStateStore.resolveAndCacheAllQualities(
                        mediaKey = mediaKey,
                        serversNames = extractedServers,
                        serversMap = extractedServerLinks,
                        downloadsMap = extractedDownloadLinks,
                        currentStreamUrl = directStreamUrl,
                        altKeys = listOf(fileId, mediaId),
                        sourcePageUrl = finalWatchUrl
                    )
                }

                ServerStateStore.saveForMedia(
                    mediaKey = mediaKey,
                    servers = extractedServers,
                    links = extractedServerLinks,
                    ids = extractedServerIds,
                    downloads = extractedDownloadLinks,
                    extractedQ = resolvedQualities,
                    website = activeSiteName ?: "EgyDead",
                    playbackPageUrl = finalWatchUrl,
                    scraperKey = activeSiteName,
                    altKeys = listOf(fileId, mediaId),
                    directStreamUrl = directStreamUrl,
                    mediaId = mediaId
                )

                showSmartQualityDialog = true
            } else {
                val estimatedSpeed = NetworkUtils.getEstimatedBandwidthKbps(context)
                val firstQualities = withContext(Dispatchers.IO) {
                    if (rawWatchUrl.contains(".m3u8") || rawWatchUrl.contains("akamaized.net")) {
                        try {
                            M3U8Parser.getQualities(rawWatchUrl)
                        } catch (_: Exception) {
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }
                }

                val selectedQuality = if (firstQualities.isNotEmpty()) {
                    NetworkUtils.selectBestQuality(firstQualities, estimatedSpeed)
                } else {
                    null
                }
                val optimalUrl = selectedQuality?.url?.ifBlank { rawWatchUrl } ?: rawWatchUrl

                ServerStateStore.startBackgroundQualityExtraction(
                    mediaKey = mediaKey,
                    serversNames = extractedServers,
                    serversMap = extractedServerLinks,
                    downloadsMap = extractedDownloadLinks,
                    currentStreamUrl = optimalUrl,
                    altKeys = listOf(fileId, mediaId)
                )

                delay(400)
                try { android.util.Log.i("QFILM_DIAG", "[QFILM_DIAG] 20_PLAYBACK_STARTED url='$optimalUrl', server='$firstServer', website='${activeSiteName ?: "EgyDead"}'") } catch (_: Throwable) {}
                onPlay(optimalUrl, firstServer, activeSiteName ?: "EgyDead")
            }
        }
    }

    // --- PRIMARY FLOW: Managed Extension Discovery ---
    LaunchedEffect(mediaKey, retryTrigger) {
        val hasCachedTarget = if (isDownloadMode) hasCachedWithQualities else hasCached
        if (hasCachedTarget) return@LaunchedEffect
        val targetType = if (isMovie) ContentType.MOVIE else ContentType.SERIES
        if (managedOrchestrator.hasActiveExtensions(targetType)) {
            isLoading = true
            isFailed = false
            isNetworkError = false
            loadingMessage = if (isDownloadMode) {
                context.getString(R.string.checking_quality_in_servers)
            } else {
                context.getString(R.string.checking_in_site, "الامتداد المدار (EgyDead)")
            }
            val outcome = managedOrchestrator.discoverServers(
                title = title,
                year = year,
                isMovie = isMovie,
                season = season,
                episode = episode,
                mediaId = mediaId,
                altKeys = listOf(fileId, mediaId)
            )
            when (outcome) {
                is ManagedDiscoveryOutcome.Success -> {
                    activeSiteName = outcome.website
                    // Distinguish between pure download pages and playable stream servers.
                    // Direct media (.mp4, .m3u8) MUST remain valid playable stream candidates!
                    val isPureDownloadOnly = { s: com.example.extension.managed.model.ServerItem ->
                        s.name.contains("(تحميل)") && !s.link.endsWith(".mp4") && !s.link.endsWith(".m3u8")
                    }
                    val streamServers = outcome.servers.filter { !isPureDownloadOnly(it) }
                    val streamList = if (streamServers.isNotEmpty()) streamServers.map { it.name } else outcome.servers.map { it.name }
                    extractedServers = streamList
                    extractedServerLinks = outcome.servers.associate { it.name to it.link }
                    extractedServerIds = outcome.servers.associate { it.name to it.id }
                    extractedDownloadLinks = outcome.servers.filter {
                        it.name.contains("(تحميل)") || it.link.endsWith(".mp4") || it.link.endsWith(".mkv")
                    }.associate { it.name to it.link }
                    finalWatchUrl = outcome.sourceUrl
                    isLoading = false

                    ServerStateStore.saveForMedia(
                        mediaKey = mediaKey,
                        servers = streamList,
                        links = extractedServerLinks,
                        ids = extractedServerIds,
                        downloads = extractedDownloadLinks,
                        website = outcome.website,
                        altKeys = listOf(fileId, mediaId),
                        mediaId = mediaId
                    )
                }
                is ManagedDiscoveryOutcome.RecoverableFailure -> {
                    isLoading = false
                    if (outcome.error is ExtensionError.NoInternet) {
                        isNetworkError = true
                    } else {
                        isFailed = true
                    }
                }
                is ManagedDiscoveryOutcome.SecurityFailure -> {
                    isLoading = false
                    isFailed = true
                }
            }
        } else {
            isLoading = false
            isFailed = true
            showNoMoreExtensionsDialog = true
        }
    }

    Dialog(
        onDismissRequest = {
            if (isLoading) {
                showCancelConfirmDialog = true
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = true
        )
    ) {
        val isConnected = extractedServers.isNotEmpty() && !isFailed && !isNetworkError

        val displayTitle = when {
            isNetworkError -> stringResource(R.string.connection_lost)
            isFailed -> stringResource(R.string.content_not_available_currently)
            isConnected && isDownloadMode -> stringResource(R.string.select_download_quality)
            isConnected -> stringResource(R.string.connected_ellipsis)
            isDownloadMode -> stringResource(R.string.select_download_quality)
            else -> stringResource(R.string.connecting_to_server)
        }

        val displaySubtitle = when {
            isNetworkError -> stringResource(R.string.connection_lost_desc)
            isFailed -> stringResource(R.string.content_not_available_desc)
            isConnected && isDownloadMode -> stringResource(R.string.checking_quality_in_servers)
            isConnected -> stringResource(R.string.starting_playback)
            isDownloadMode -> stringResource(R.string.checking_quality_in_servers)
            else -> stringResource(R.string.connecting_ellipsis)
        }

        val displayIcon = when {
            isNetworkError -> Icons.Default.CloudOff
            isFailed -> Icons.Default.CloudOff
            isConnected -> Icons.Outlined.CloudDone
            else -> Icons.Outlined.CloudDownload
        }

        val iconTint = when {
            isNetworkError || isFailed -> MaterialTheme.colorScheme.error
            isConnected -> SuccessGreen
            else -> MaterialTheme.colorScheme.primary
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, iconTint.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.radialGradient(
                                colors = listOf(iconTint.copy(alpha = 0.15f), Color.Transparent),
                                radius = 600f,
                                center = androidx.compose.ui.geometry.Offset(0f, 0f)
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .animateContentSize()
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(48.dp)
                                .background(iconTint.copy(alpha = 0.15f), CircleShape)
                                .border(1.dp, iconTint.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(displayIcon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
                        }

                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 52.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = displayTitle,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = displaySubtitle,
                                color = if (isFailed || isNetworkError) MaterialTheme.colorScheme.onSurfaceVariant else iconTint.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (!isFailed && !isNetworkError) {
                                Spacer(modifier = Modifier.height(10.dp))
                                if (isConnected) {
                                    LinearProgressIndicator(
                                        progress = { 1f },
                                        modifier = Modifier
                                            .fillMaxWidth(0.65f)
                                            .height(4.dp)
                                            .clip(CircleShape),
                                        color = SuccessGreen,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth(0.65f)
                                            .height(4.dp)
                                            .clip(CircleShape),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.surface, CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .clip(CircleShape)
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }

                    if (isNetworkError) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.connection_lost),
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.connection_lost_desc),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onDismiss,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(stringResource(R.string.cancel), color = Color.White)
                                    }
                                    Button(
                                        onClick = {
                                            if (NetworkUtils.isInternetAvailable(context)) {
                                                isNetworkError = false
                                                isFailed = false
                                                isLoading = true
                                                retryTrigger++
                                            } else {
                                                android.widget.Toast.makeText(context, context.getString(R.string.no_internet_check_connection), android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.retry), color = Color.White)
                                    }
                                }
                            }
                        }
                    } else if (isFailed) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.content_not_available_currently),
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.content_not_available_desc),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onDismiss,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(stringResource(R.string.close))
                                    }
                                    Button(
                                        onClick = {
                                            isFailed = false
                                            isLoading = true
                                            retryTrigger++
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.retry_again), color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCancelConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmDialog = false },
            title = { Text(stringResource(R.string.cancel_search_title)) },
            text = { Text(stringResource(R.string.cancel_search_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelConfirmDialog = false
                        onDismiss()
                    }
                ) {
                    Text(stringResource(R.string.yes_cancel), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirmDialog = false }) {
                    Text(stringResource(R.string.keep_searching))
                }
            }
        )
    }

    if (showNoMoreExtensionsDialog) {
        NoExtensionsDialog(
            onDismiss = {
                showNoMoreExtensionsDialog = false
                onDismiss()
            },
            onGoToExtensions = {
                showNoMoreExtensionsDialog = false
                onDismiss()
                onNavigateToExtensions()
            },
            onRetry = {
                showNoMoreExtensionsDialog = false
                isLoading = true
                isFailed = false
                retryTrigger++
            }
        )
    }
}
