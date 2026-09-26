package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.webkit.CookieManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.example.ui.screens.player.normalizeQualityKey
import com.example.ui.screens.player.normalizeQualityLabel
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.ui.screens.player.PlaybackSyncStore
import com.example.utils.MediaStorageUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

data class ActiveInlinePlayback(
    val mediaId: String,
    val title: String,
    val url: String,
    val serverName: String? = null,
    val website: String? = null,
    val posterUrl: String = "",
    val isMovie: Boolean = true,
    val episodeId: String? = null,
    val initialPosition: Long = 0L,
    val initialQuality: String? = null
)

@OptIn(UnstableApi::class)
@Composable
fun InlineDetailVideoPlayer(
    playback: ActiveInlinePlayback,
    onFullscreen: (currentPosition: Long) -> Unit,
    onClose: () -> Unit,
    onChangeServer: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val syncKey = if (playback.isMovie) playback.mediaId else "${playback.mediaId}_${playback.episodeId ?: ""}"

    val userPrefs = remember { com.example.data.repository.UserPreferencesRepository(context) }
    val seekSeconds by userPrefs.playbackSeekDuration.collectAsState(initial = 10)
    val controlsTimeoutSeconds by userPrefs.playbackControlsTimeout.collectAsState(initial = 10)

    BackHandler(enabled = true) {
        onClose()
    }

    var showInlineDownloadDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    val initialQ = playback.initialQuality?.takeIf { it.isNotBlank() }
        ?: com.example.utils.LastPlaybackStore.getLastPlayback(context, playback.mediaId, playback.episodeId)?.quality?.takeIf { it.isNotBlank() }
        ?: "Auto"
    var currentQuality by remember { mutableStateOf(initialQ) }
    var targetSwitchSeekPos by remember { mutableLongStateOf(0L) }
    var resizeMode by remember { mutableIntStateOf(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var showControls by remember { mutableStateOf(true) }

    val fileId = if (playback.isMovie) playback.mediaId else "${playback.mediaId}_${playback.episodeId ?: "1"}"
    val downloadRepo = remember { com.example.data.repository.DownloadRepository(context) }
    val downloadItem by downloadRepo.getDownloadItemById(fileId).collectAsState(initial = null)

    val isDownloaded = playback.url.startsWith("local_offline_file://") || downloadItem?.isCompleted == true

    var allDeduplicatedQualities by remember { mutableStateOf<List<com.example.utils.M3U8Parser.QualityInfo>>(emptyList()) }

    val liveExtractedQualities by com.example.ui.screens.player.ServerStateStore.extractedQualitiesFlow.collectAsState()

    val currentMediaKey = remember(playback.title, playback.isMovie, playback.episodeId) {
        val epNum = playback.episodeId?.toIntOrNull() ?: 1
        "${playback.title}-${playback.isMovie}-1-$epNum"
    }

    LaunchedEffect(liveExtractedQualities, currentMediaKey, playback.mediaId) {
        val isStoreMatching = com.example.ui.screens.player.ServerStateStore.currentMediaKey == currentMediaKey ||
            (playback.mediaId.isNotBlank() && com.example.ui.screens.player.ServerStateStore.currentMediaId == playback.mediaId)
        if (isStoreMatching && liveExtractedQualities.isNotEmpty()) {
            allDeduplicatedQualities = liveExtractedQualities
        }
    }

    LaunchedEffect(playback.url, playback.mediaId) {
        val cached = com.example.ui.screens.player.ServerStateStore.getCachedData(currentMediaKey, fileId, playback.mediaId)
        if (cached != null && cached.extractedQualities.isNotEmpty()) {
            allDeduplicatedQualities = cached.extractedQualities
        }
        val isStoreMatching = com.example.ui.screens.player.ServerStateStore.currentMediaKey == currentMediaKey ||
            (playback.mediaId.isNotBlank() && com.example.ui.screens.player.ServerStateStore.currentMediaId == playback.mediaId)
        val servers = cached?.servers ?: (if (isStoreMatching) com.example.ui.screens.player.ServerStateStore.extractedServers else emptyList())
        val links = cached?.serverLinks ?: (if (isStoreMatching) com.example.ui.screens.player.ServerStateStore.extractedServerLinks else emptyMap())
        val downloads = cached?.downloadLinks ?: (if (isStoreMatching) com.example.ui.screens.player.ServerStateStore.extractedDownloadLinks else emptyMap())

        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val resolved = com.example.ui.screens.player.ServerStateStore.resolveAndCacheAllQualities(
                mediaKey = currentMediaKey,
                serversNames = servers,
                serversMap = links,
                downloadsMap = downloads,
                currentStreamUrl = playback.url,
                altKeys = listOf(fileId, playback.mediaId)
            )
            com.example.ui.screens.player.ServerStateStore.saveForMedia(
                mediaKey = currentMediaKey,
                servers = servers,
                links = links,
                ids = cached?.serverIds ?: emptyMap(),
                downloads = downloads,
                extractedQ = resolved,
                website = playback.website ?: cached?.website ?: "",
                playbackPageUrl = playback.website ?: cached?.playbackPageUrl,
                scraperKey = playback.serverName ?: cached?.scraperKey,
                altKeys = listOf(fileId, playback.mediaId),
                directStreamUrl = playback.url,
                mediaId = playback.mediaId
            )
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (resolved.isNotEmpty()) {
                    allDeduplicatedQualities = resolved
                }
            }
        }
    }

    LaunchedEffect(downloadItem) {
        val clean = com.example.data.model.DownloadItem.cleanQualityName(downloadItem?.quality)
        if (clean.isNotBlank()) {
            currentQuality = clean
        }
    }

    // Hardware Brightness
    val initialBrightness = remember {
        try {
            val systemBrightnessInt = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS
            )
            (systemBrightnessInt / 255f).coerceIn(0.01f, 1f)
        } catch (e: Exception) {
            0.5f
        }
    }
    var brightness by remember { mutableFloatStateOf(initialBrightness) }

    // Hardware Volume
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val initialVolume = remember {
        audioManager?.let { am ->
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (maxVol > 0) curVol.toFloat() / maxVol.toFloat() else 0.5f
        } ?: 0.5f
    }
    var volume by remember { mutableFloatStateOf(initialVolume) }

    LaunchedEffect(brightness) {
        val activity = context as? Activity
        val window = activity?.window
        val lp = window?.attributes
        if (lp != null) {
            lp.screenBrightness = brightness.coerceIn(0.01f, 1f)
            window.attributes = lp
        }
    }

    LaunchedEffect(volume) {
        audioManager?.let { am ->
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (maxVol > 0) {
                val target = (volume * maxVol).roundToInt().coerceIn(0, maxVol)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            }
        }
    }

    var hudBrightness by remember { mutableStateOf<Float?>(null) }
    var hudVolume by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(hudBrightness, hudVolume) {
        if (hudBrightness != null || hudVolume != null) {
            delay(1200)
            hudBrightness = null
            hudVolume = null
        }
    }

    // Drag Gesture state (Seek horizontal, Volume & Brightness vertical)
    var boxWidthPx by remember { mutableFloatStateOf(0f) }
    var boxHeightPx by remember { mutableFloatStateOf(0f) }
    var activeDragSide by remember { mutableStateOf<String?>(null) }
    var dragGestureDirection by remember { mutableStateOf<String?>(null) }
    var dragStartSeekPos by remember { mutableLongStateOf(0L) }
    var targetDragSeekPos by remember { mutableLongStateOf(0L) }
    var accumulatedDragX by remember { mutableFloatStateOf(0f) }
    var accumulatedDragY by remember { mutableFloatStateOf(0f) }
    var isDragSeeking by remember { mutableStateOf(false) }

    val enterPip = {
        val activity = context as? Activity
        if (activity != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            showControls = false
            com.example.ui.screens.player.PlayerStateHolder.isInPipMode = true
            val aspectRatio = android.util.Rational(16, 9)
            val params = android.app.PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()
            activity.enterPictureInPictureMode(params)
        }
    }

    DisposableEffect(Unit) {
        com.example.ui.screens.player.PlayerStateHolder.isPlayerActive = true
        com.example.ui.screens.player.PlayerStateHolder.onEnterPipRequested = {
            enterPip()
        }
        onDispose {
            com.example.ui.screens.player.PlayerStateHolder.isPlayerActive = false
            com.example.ui.screens.player.PlayerStateHolder.onEnterPipRequested = null
        }
    }

    // State for direct playable URL
    var playableUrl by remember(playback.url) {
        val raw = playback.url
        val initialUrl = if (raw.startsWith("local_offline_file://")) {
            val fileId = raw.removePrefix("local_offline_file://")
            val f = MediaStorageUtils.findMediaFile(context, fileId)
                ?: MediaStorageUtils.findMediaFile(context, playback.mediaId)
                ?: if (playback.episodeId != null) MediaStorageUtils.findMediaFile(context, playback.episodeId) else null
            if (f != null && f.exists()) Uri.fromFile(f).toString() else raw
        } else if (MediaStorageUtils.hasDownloadedMedia(context, playback.mediaId)) {
            val f = MediaStorageUtils.findMediaFile(context, playback.mediaId)
            if (f != null && f.exists()) Uri.fromFile(f).toString() else if (raw.contains(".mp4") || raw.contains(".m3u8") || raw.startsWith("file://") || raw.startsWith("content://")) raw else null
        } else if (raw.contains(".mp4") || raw.contains(".m3u8") || raw.startsWith("file://") || raw.startsWith("content://")) {
            raw
        } else {
            null
        }
        mutableStateOf(initialUrl)
    }

    var isExtracting by remember(playback.url) {
        mutableStateOf(playableUrl == null && !playback.url.startsWith("local_offline_file://") && !playback.url.startsWith("file://") && !playback.url.startsWith("content://"))
    }
    var extractionFailed by remember { mutableStateOf(false) }

    // Player state
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentTime by remember { mutableStateOf(playback.initialPosition) }
    var totalDuration by remember { mutableStateOf(0L) }

    // Scrubbing state
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableStateOf(0L) }

    // Double-tap visual indicators
    var showDoubleTapForward by remember { mutableStateOf(false) }
    var showDoubleTapBackward by remember { mutableStateOf(false) }

    // Setup ExoPlayer
    var detectedQualities by remember { mutableStateOf<List<com.example.utils.M3U8Parser.QualityInfo>>(emptyList()) }
    var hasAutoSelectedSpeedQuality by remember { mutableStateOf(false) }

    var lastWorkingPlayableUrl by remember { mutableStateOf<String?>(null) }
    var lastWorkingQuality by remember { mutableStateOf<String?>(null) }
    var lastWorkingPosition by remember { mutableLongStateOf(0L) }

    val exoPlayer = remember(context) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000,
                50_000,
                2_000,
                3_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val cookie = CookieManager.getInstance().getCookie(playableUrl ?: "") ?: ""
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36")
        if (cookie.isNotEmpty()) {
            httpDataSourceFactory.setDefaultRequestProperties(mapOf("Cookie" to cookie))
        }
        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .build().apply {
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_READY -> {
                                totalDuration = duration.coerceAtLeast(0L)
                                isBuffering = false
                                lastWorkingPlayableUrl = playableUrl
                                lastWorkingQuality = currentQuality
                                lastWorkingPosition = currentPosition
                            }
                            Player.STATE_BUFFERING -> {
                                isBuffering = true
                            }
                            Player.STATE_ENDED -> {
                                isBuffering = false
                                isPlaying = false
                            }
                            else -> {
                                isBuffering = false
                            }
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        isBuffering = false
                        val prevUrl = lastWorkingPlayableUrl
                        val prevQuality = lastWorkingQuality
                        if (prevUrl != null && prevUrl != playableUrl && prevQuality != null) {
                            android.util.Log.w("InlinePlayer", "QUALITY_SWITCH_FAILED error=${error.message}, falling back to $prevQuality")
                            val fallbackPos = if (lastWorkingPosition > 0L) lastWorkingPosition else currentTime
                            currentQuality = prevQuality
                            targetSwitchSeekPos = fallbackPos
                            playableUrl = prevUrl
                        }
                    }

                    override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                        if (videoSize.height > 0) {
                            val label = "${videoSize.height}p"
                            if (detectedQualities.none { it.name == label }) {
                                detectedQualities = (detectedQualities + com.example.utils.M3U8Parser.QualityInfo(name = label, url = playableUrl ?: ""))
                                    .sortedByDescending { it.name.removeSuffix("p").toIntOrNull() ?: 0 }
                            }
                        }
                    }

                    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                        val qList = mutableListOf<com.example.utils.M3U8Parser.QualityInfo>()
                        for (group in tracks.groups) {
                            if (group.type == androidx.media3.common.C.TRACK_TYPE_VIDEO) {
                                for (i in 0 until group.length) {
                                    val format = group.getTrackFormat(i)
                                    val h = format.height
                                    if (h > 0) {
                                        val label = "${h}p"
                                        if (qList.none { it.name == label }) {
                                            qList.add(com.example.utils.M3U8Parser.QualityInfo(name = label, url = playableUrl ?: ""))
                                        }
                                    }
                                }
                            }
                        }
                        if (qList.isNotEmpty()) {
                            detectedQualities = qList.sortedByDescending { it.name.removeSuffix("p").toIntOrNull() ?: 0 }
                        }
                    }
                })
            }
    }

    // Prepare media when URL is resolved
    LaunchedEffect(playableUrl) {
        playableUrl?.let { url ->
            if (url.contains(".m3u8") || url.contains("akamaized.net")) {
                coroutineScope.launch {
                    val parsed = com.example.utils.M3U8Parser.getQualities(url)
                    if (parsed.isNotEmpty()) {
                        detectedQualities = parsed
                    }
                }
            }
            val cookie = CookieManager.getInstance().getCookie(url) ?: ""
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36")
            if (cookie.isNotEmpty()) {
                httpDataSourceFactory.setDefaultRequestProperties(mapOf("Cookie" to cookie))
            }
            val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
            val mediaSource = DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(MediaItem.fromUri(url))

            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()

            val savedPos = when {
                targetSwitchSeekPos > 0L -> {
                    val p = targetSwitchSeekPos
                    targetSwitchSeekPos = 0L
                    p
                }
                currentTime > 0L -> currentTime
                playback.initialPosition > 0L -> playback.initialPosition
                else -> {
                    val p = PlaybackSyncStore.getPosition(syncKey)
                    if (p > 0L) p else if (syncKey.contains("_")) PlaybackSyncStore.getPosition(playback.mediaId) else 0L
                }
            }
            if (savedPos > 0L) {
                exoPlayer.seekTo(savedPos)
                currentTime = savedPos
            }
            exoPlayer.playWhenReady = true
        }
    }

    LaunchedEffect(playback.mediaId, playback.episodeId, playback.url) {
        val cached = com.example.ui.screens.player.ServerStateStore.getCachedData(currentMediaKey, fileId, playback.mediaId)
        val knownUrl = cached?.playbackPageUrl ?: playback.url
        val scraperKey = cached?.scraperKey ?: ""
        val managedOrchestrator = com.example.extension.orchestrator.ManagedMediaOrchestrator.getInstance(context)
        managedOrchestrator.revalidateMediaInBackground(
            mediaId = playback.mediaId,
            episodeId = playback.episodeId,
            mediaTitle = playback.title,
            isMovie = playback.isMovie,
            knownPlaybackUrl = knownUrl,
            scraperKey = scraperKey,
            currentServerName = playback.serverName,
            currentQuality = currentQuality,
            altKeys = listOf(fileId, playback.mediaId)
        )
    }

    val networkObserver = remember { com.example.utils.NetworkConnectivityObserver(context) }
    val isOnline by networkObserver.observe().collectAsState(initial = com.example.utils.NetworkUtils.isInternetAvailable(context))

    LaunchedEffect(isOnline) {
        if (isOnline && (extractionFailed || (!isPlaying && playableUrl != null))) {
            extractionFailed = false
            if (playableUrl != null) {
                exoPlayer.prepare()
                exoPlayer.play()
            }
        }
    }

    val serverSpecificQualities = remember(playback.serverName, detectedQualities, allDeduplicatedQualities) {
        val allMap = mutableMapOf<String, com.example.utils.M3U8Parser.QualityInfo>()
        for (q in allDeduplicatedQualities) {
            val key = normalizeQualityKey(q.name) ?: q.name
            val label = normalizeQualityLabel(key) ?: "${key}p"
            if (!allMap.containsKey(key)) {
                allMap[key] = q.copy(name = label)
            }
        }
        for (q in detectedQualities) {
            val key = normalizeQualityKey(q.name) ?: q.name
            val label = normalizeQualityLabel(key) ?: "${key}p"
            if (!allMap.containsKey(key)) {
                allMap[key] = q.copy(name = label)
            }
        }
        val isStoreMatching = com.example.ui.screens.player.ServerStateStore.currentMediaKey == currentMediaKey ||
            (playback.mediaId.isNotBlank() && com.example.ui.screens.player.ServerStateStore.currentMediaId == playback.mediaId)
        if (isStoreMatching) {
            for (q in com.example.ui.screens.player.ServerStateStore.extractedQualities) {
                val key = normalizeQualityKey(q.name) ?: q.name
                val label = normalizeQualityLabel(key) ?: "${key}p"
                if (!allMap.containsKey(key)) {
                    allMap[key] = q.copy(name = label)
                }
            }
        }
        val order = listOf("1080p", "720p", "480p", "360p", "240p", "144p")
        allMap.values.sortedWith(Comparator { a, b ->
            val idxA = order.indexOf(a.name).let { if (it == -1) 99 else it }
            val idxB = order.indexOf(b.name).let { if (it == -1) 99 else it }
            idxA.compareTo(idxB)
        })
    }

    // Auto quality selection based on user's internet speed ONLY if quality is Auto
    LaunchedEffect(serverSpecificQualities, detectedQualities) {
        val valid = (serverSpecificQualities + detectedQualities)
            .filter { it.name != "Auto" && it.url.isNotBlank() }
            .distinctBy { it.name }
        if (valid.isNotEmpty() && currentQuality == "Auto" && !hasAutoSelectedSpeedQuality && !isDownloaded) {
            hasAutoSelectedSpeedQuality = true
            val speed = com.example.utils.NetworkUtils.getEstimatedBandwidthKbps(context)
            val best = com.example.utils.NetworkUtils.selectBestQuality(valid, speed)
            if (best != null && best.name != currentQuality) {
                if (best.url.isNotBlank() && best.url != playableUrl && best.url.startsWith("http")) {
                    val savedPos = if (currentTime > 0L) currentTime else exoPlayer.currentPosition
                    targetSwitchSeekPos = savedPos
                    playableUrl = best.url
                }
            }
        } else if (valid.isNotEmpty() && currentQuality != "Auto" && !isDownloaded) {
            // Restore saved quality source if not already playing it
            val matched = valid.find { it.name == currentQuality }
            if (matched != null && matched.url.isNotBlank() && matched.url != playableUrl && matched.url.startsWith("http")) {
                val savedPos = if (currentTime > 0L) currentTime else exoPlayer.currentPosition
                targetSwitchSeekPos = savedPos
                playableUrl = matched.url
            }
        }
    }

    // Auto-update position while playing
    LaunchedEffect(isPlaying) {
        var tick = 0
        while (isPlaying) {
            if (!isScrubbing) {
                currentTime = exoPlayer.currentPosition
                totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                if (currentTime > 0) {
                    PlaybackSyncStore.setPosition(syncKey, currentTime)
                    tick++
                    if (tick % 10 == 0) {
                        PlaybackSyncStore.setPositionAndPersist(context, syncKey, currentTime, totalDuration)
                    }
                }
            }
            delay(500)
        }
    }

    // Auto-hide controls
    LaunchedEffect(showControls, isPlaying, controlsTimeoutSeconds) {
        if (showControls && isPlaying && !isScrubbing) {
            delay(controlsTimeoutSeconds * 1000L)
            showControls = false
        }
    }

    // Double tap fadeout helpers
    LaunchedEffect(showDoubleTapForward) {
        if (showDoubleTapForward) {
            delay(650)
            showDoubleTapForward = false
        }
    }
    LaunchedEffect(showDoubleTapBackward) {
        if (showDoubleTapBackward) {
            delay(650)
            showDoubleTapBackward = false
        }
    }

    // Apply Quality changes to ExoPlayer
    LaunchedEffect(currentQuality) {
        val maxVideoWidth = when (currentQuality) {
            "1080p" -> 1920
            "720p" -> 1280
            "480p" -> 854
            "360p" -> 640
            else -> Int.MAX_VALUE
        }
        val maxVideoHeight = when (currentQuality) {
            "1080p" -> 1080
            "720p" -> 720
            "480p" -> 480
            "360p" -> 360
            else -> Int.MAX_VALUE
        }
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setMaxVideoSize(maxVideoWidth, maxVideoHeight)
            .build()
    }

    // Keep playing in PiP mode
    LaunchedEffect(com.example.ui.screens.player.PlayerStateHolder.isInPipMode) {
        if (com.example.ui.screens.player.PlayerStateHolder.isInPipMode) {
            showControls = false
            exoPlayer.playWhenReady = true
            exoPlayer.play()
        }
    }

    // Pause on lifecycle background (unless in PiP mode)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val activity = context as? Activity
            val inPip = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                activity?.isInPictureInPictureMode == true || com.example.ui.screens.player.PlayerStateHolder.isInPipMode
            } else com.example.ui.screens.player.PlayerStateHolder.isInPipMode

            if (event == Lifecycle.Event.ON_PAUSE) {
                if (!inPip) {
                    exoPlayer.pause()
                    val pos = exoPlayer.currentPosition
                    val dur = exoPlayer.duration.coerceAtLeast(0L)
                    if (pos > 0) {
                        PlaybackSyncStore.setPositionAndPersist(context, syncKey, pos, dur)
                    }

                    // Reset screen brightness when player is paused/backgrounded
                    activity?.window?.let { window ->
                        val lp = window.attributes
                        lp.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                        window.attributes = lp
                    }
                } else {
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()
                }
            } else if (event == Lifecycle.Event.ON_STOP) {
                if (!inPip) {
                    exoPlayer.pause()
                    val pos = exoPlayer.currentPosition
                    val dur = exoPlayer.duration.coerceAtLeast(0L)
                    if (pos > 0) {
                        PlaybackSyncStore.setPositionAndPersist(context, syncKey, pos, dur)
                    }

                    activity?.window?.let { window ->
                        val lp = window.attributes
                        lp.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                        window.attributes = lp
                    }
                }
            } else if (event == Lifecycle.Event.ON_RESUME) {
                activity?.window?.let { window ->
                    val lp = window.attributes
                    lp.screenBrightness = brightness.coerceIn(0.01f, 1f)
                    window.attributes = lp
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            val pos = exoPlayer.currentPosition
            val dur = exoPlayer.duration.coerceAtLeast(0L)
            if (pos > 0) {
                PlaybackSyncStore.setPositionAndPersist(context, syncKey, pos, dur)
            }
            exoPlayer.release()

            // Restore system screen brightness when player is closed
            val activity = context as? Activity
            activity?.window?.let { window ->
                val lp = window.attributes
                lp.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = lp
            }
        }
    }

    // Managed runtime stream extraction if URL is an embed/webpage
    val managedOrchestrator = remember { com.example.extension.orchestrator.ManagedMediaOrchestrator.getInstance(context) }
    LaunchedEffect(playback.url, isExtracting) {
        if (playableUrl == null && isExtracting && !playback.url.startsWith("local_offline_file://") && !playback.url.startsWith("file://") && !playback.url.startsWith("content://")) {
            val serverItem = com.example.extension.managed.model.ServerItem(
                id = playback.serverName ?: "default",
                name = playback.serverName ?: "Server",
                link = playback.url
            )
            val extractResult = managedOrchestrator.extractPlaybackSource(serverItem, playback.title)
            if (extractResult.isSuccess) {
                playableUrl = extractResult.getOrThrow().streamUrl
                isExtracting = false
            } else {
                isExtracting = false
                extractionFailed = true
            }
        }
    }

    val displayTime = if (isScrubbing) scrubPosition else currentTime
    val hasHours = totalDuration >= 3600_000L
    val progressFraction = if (totalDuration > 0L) (displayTime.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f) else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black)
            .onSizeChanged {
                boxWidthPx = it.width.toFloat()
                boxHeightPx = it.height.toFloat()
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        showControls = !showControls
                    },
                    onDoubleTap = { offset ->
                        val isLeft = offset.x < size.width / 2f
                        val seekMs = seekSeconds * 1000L
                        if (isLeft) {
                            // Double tap backward (-seekSeconds)
                            val newPos = (exoPlayer.currentPosition - seekMs).coerceAtLeast(0L)
                            isBuffering = true
                            exoPlayer.seekTo(newPos)
                            currentTime = newPos
                            showDoubleTapBackward = true
                            showDoubleTapForward = false
                        } else {
                            // Double tap forward (+seekSeconds)
                            val dur = if (exoPlayer.duration > 0) exoPlayer.duration else totalDuration
                            val newPos = (exoPlayer.currentPosition + seekMs).coerceAtMost(dur)
                            isBuffering = true
                            exoPlayer.seekTo(newPos)
                            currentTime = newPos
                            showDoubleTapForward = true
                            showDoubleTapBackward = false
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        accumulatedDragX = 0f
                        accumulatedDragY = 0f
                        dragGestureDirection = null
                        dragStartSeekPos = exoPlayer.currentPosition
                        targetDragSeekPos = dragStartSeekPos
                        isDragSeeking = false
                        if (boxWidthPx > 0) {
                            activeDragSide = if (offset.x < boxWidthPx / 2f) "left" else "right"
                        }
                    },
                    onDragEnd = {
                        if (dragGestureDirection == "horizontal" && isDragSeeking) {
                            isBuffering = true
                            exoPlayer.seekTo(targetDragSeekPos)
                            currentTime = targetDragSeekPos
                        }
                        dragGestureDirection = null
                        isDragSeeking = false
                        activeDragSide = null
                    },
                    onDragCancel = {
                        dragGestureDirection = null
                        isDragSeeking = false
                        activeDragSide = null
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDragX += dragAmount.x
                        accumulatedDragY += dragAmount.y

                        if (dragGestureDirection == null) {
                            val absX = kotlin.math.abs(accumulatedDragX)
                            val absY = kotlin.math.abs(accumulatedDragY)
                            if (absX > 15f || absY > 15f) {
                                dragGestureDirection = if (absX > absY) "horizontal" else "vertical"
                            }
                        }

                        if (dragGestureDirection == "horizontal") {
                            isDragSeeking = true
                            hudBrightness = null
                            hudVolume = null
                            val seekDeltaMs = (accumulatedDragX * 120f).toLong()
                            val dur = if (totalDuration > 0) totalDuration else exoPlayer.duration.coerceAtLeast(0L)
                            targetDragSeekPos = (dragStartSeekPos + seekDeltaMs).coerceIn(0L, dur)
                        } else if (dragGestureDirection == "vertical") {
                            isDragSeeking = false
                            val h = if (boxHeightPx > 0) boxHeightPx else 600f
                            val delta = -dragAmount.y / (h * 0.75f)
                            if (activeDragSide == "left") {
                                brightness = (brightness + delta).coerceIn(0.01f, 1f)
                                hudBrightness = brightness
                                hudVolume = null
                            } else if (activeDragSide == "right") {
                                volume = (volume + delta).coerceIn(0f, 1f)
                                hudVolume = volume
                                hudBrightness = null
                            }
                        }
                    }
                )
            }
    ) {
        // Player Surface View
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = exoPlayer
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.resizeMode = resizeMode
            }
        )

        // Double-Tap Animated Feedback: Backward (-10s)
        AnimatedVisibility(
            visible = showDoubleTapBackward && !com.example.ui.screens.player.PlayerStateHolder.isInPipMode,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(300)),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 28.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
            ) {
                Icon(
                    imageVector = Icons.Filled.FastRewind,
                    contentDescription = "-${seekSeconds}s",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                Text("-${seekSeconds}s", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Double-Tap Animated Feedback: Forward (+10s)
        AnimatedVisibility(
            visible = showDoubleTapForward && !com.example.ui.screens.player.PlayerStateHolder.isInPipMode,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(300)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 28.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
            ) {
                Icon(
                    imageVector = Icons.Filled.FastForward,
                    contentDescription = "+${seekSeconds}s",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                Text("+${seekSeconds}s", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Floating HUD for Seek Gesture
        if (isDragSeeking && !com.example.ui.screens.player.PlayerStateHolder.isInPipMode) {
            val diffSeconds = (targetDragSeekPos - dragStartSeekPos) / 1000
            val isForward = diffSeconds >= 0
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isForward) Icons.Filled.FastForward else Icons.Filled.FastRewind,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${if (isForward) "+" else ""}${diffSeconds}s",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    val dur = if (totalDuration > 0) totalDuration else exoPlayer.duration.coerceAtLeast(0L)
                    Text(
                        text = "${formatTime(targetDragSeekPos, hasHours)} / ${formatTime(dur, hasHours)}",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Floating HUD for Brightness
        if (hudBrightness != null && !com.example.ui.screens.player.PlayerStateHolder.isInPipMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.BrightnessMedium, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "${(hudBrightness!! * 100).roundToInt()}%",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { hudBrightness!! },
                            modifier = Modifier.width(90.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.White.copy(alpha = 0.25f)
                        )
                    }
                }
            }
        }

        // Floating HUD for Volume
        if (hudVolume != null && !com.example.ui.screens.player.PlayerStateHolder.isInPipMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (hudVolume!! <= 0.01f) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "${(hudVolume!! * 100).roundToInt()}%",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { hudVolume!! },
                            modifier = Modifier.width(90.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.White.copy(alpha = 0.25f)
                        )
                    }
                }
            }
        }

        // Buffering / Loading Indicator (when waiting for stream or internet)
        if (isBuffering || isExtracting) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp
                )
            }
        }

        // Extraction Failed Error
        if (extractionFailed && playableUrl == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.cannot_play_server_automatically),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Controls Overlay (Tap to show/hide)
        AnimatedVisibility(
            visible = showControls && !com.example.ui.screens.player.PlayerStateHolder.isInPipMode,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.65f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            ) {
                // Top Header (Title + Quick Download + Quality + MMM Options)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = playback.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val confirmedQualities = (serverSpecificQualities + allDeduplicatedQualities + detectedQualities)
                            .filter { it.name != "Auto" && it.url.isNotBlank() }
                            .distinctBy { it.name }
                        val isQuickDownloadEnabled = isDownloaded || (playableUrl != null && !isExtracting && confirmedQualities.isNotEmpty())

                        // Quick Download / Delete Button (borderless clean icon button)
                        IconButton(
                            onClick = {
                                if (isDownloaded) {
                                    showDeleteConfirm = true
                                } else if (isQuickDownloadEnabled) {
                                    showInlineDownloadDialog = true
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(R.string.wait_stream_load_first),
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .alpha(if (isQuickDownloadEnabled || isDownloaded) 1f else 0.38f)
                        ) {
                            Icon(
                                imageVector = if (isDownloaded) Icons.Default.Check else Icons.Default.Download,
                                contentDescription = if (isDownloaded) stringResource(R.string.downloaded) else stringResource(R.string.quick_download),
                                tint = if (isDownloaded) Color(0xFF4CAF50) else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Change Quality Button
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .clip(RoundedCornerShape(17.dp))
                                .background(Color.Black.copy(alpha = 0.7f))
                                .border(
                                    1.dp, 
                                    if (isDownloaded) Color.Gray.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), 
                                    RoundedCornerShape(17.dp)
                                )
                                .then(if (!isDownloaded) Modifier.clickable { showQualityDialog = true } else Modifier)
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = com.example.data.model.DownloadItem.cleanQualityName(currentQuality),
                                color = if (isDownloaded) Color.Gray else MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Picture-in-Picture (PiP) Button
                        IconButton(
                            onClick = { enterPip() },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureInPictureAlt,
                                contentDescription = stringResource(R.string.pip_mode),
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Center Play / Pause Button
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .pointerInput(Unit) {
                            detectTapGestures {
                                if (isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    exoPlayer.play()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) stringResource(R.string.cd_pause) else stringResource(R.string.cd_play),
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Bottom Controls Toolbar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    // Time and Fullscreen button row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatTime(displayTime, hasHours)} / ${formatTime(totalDuration, hasHours)}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        IconButton(
                            onClick = {
                                val currentPos = exoPlayer.currentPosition
                                if (currentPos > 0) PlaybackSyncStore.setPosition(syncKey, currentPos)
                                exoPlayer.pause()
                                onFullscreen(currentPos)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = stringResource(R.string.cd_fullscreen),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Interactive scrubber right along the bottom edge
                    InlineBottomScrubber(
                        progressFraction = progressFraction,
                        onScrubStart = { frac ->
                            isScrubbing = true
                            scrubPosition = (frac * totalDuration).toLong().coerceIn(0L, totalDuration)
                        },
                        onScrubMove = { frac ->
                            scrubPosition = (frac * totalDuration).toLong().coerceIn(0L, totalDuration)
                        },
                        onScrubEnd = {
                            isScrubbing = false
                            isBuffering = true
                            exoPlayer.seekTo(scrubPosition)
                            currentTime = scrubPosition
                            if (scrubPosition > 0) PlaybackSyncStore.setPosition(syncKey, scrubPosition)
                        },
                        isInteractive = true,
                        modifier = Modifier.fillMaxWidth().height(16.dp)
                    )
                }
            }
        }

        // When controls are hidden: Sleek YouTube-like flush bottom progress line
        if (!showControls) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.BottomStart)
                    .background(Color.White.copy(alpha = 0.25f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressFraction)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }

        // Download Quality Selection Dialog (Instant direct download upon selecting quality)
        if (showInlineDownloadDialog && !com.example.ui.screens.player.PlayerStateHolder.isInPipMode) {
            val availableDownloadQualities = remember(serverSpecificQualities, allDeduplicatedQualities, detectedQualities) {
                (serverSpecificQualities + allDeduplicatedQualities + detectedQualities)
                    .filter { it.name != "Auto" && it.url.isNotBlank() }
                    .distinctBy { it.name }
            }

            AlertDialog(
                onDismissRequest = { showInlineDownloadDialog = false },
                icon = {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.select_download_quality),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (availableDownloadQualities.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = stringResource(R.string.checking_quality_in_servers),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            availableDownloadQualities.forEach { qInfo ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            showInlineDownloadDialog = false
                                            val downloadUrl = qInfo.url.ifBlank { playableUrl ?: playback.url }
                                            if (downloadUrl.isNotBlank()) {
                                                val qKey = normalizeQualityKey(qInfo.name)
                                                val candidates = if (qKey != null) {
                                                    com.example.ui.screens.player.ServerStateStore.internalCandidates[qKey] ?: emptyList()
                                                } else {
                                                    emptyList()
                                                }
                                                val canonicalSource = if (candidates.isNotEmpty()) {
                                                    com.example.extension.managed.adapter.UnifiedDownloadCoordinator.fromCandidate(
                                                        candidate = candidates.first(),
                                                        mediaId = playback.mediaId,
                                                        title = playback.title,
                                                        posterUrl = playback.posterUrl,
                                                        isMovie = playback.isMovie,
                                                        episodeId = playback.episodeId,
                                                        scraperKey = playback.serverName,
                                                        fallbackCandidates = candidates.drop(1)
                                                    )
                                                } else {
                                                    com.example.extension.managed.adapter.UnifiedDownloadCoordinator.buildDownloadSource(
                                                        streamUrl = downloadUrl,
                                                        mediaId = playback.mediaId,
                                                        title = playback.title,
                                                        quality = qInfo.name,
                                                        qualityKey = qKey,
                                                        headers = qInfo.headers,
                                                        posterUrl = playback.posterUrl,
                                                        isMovie = playback.isMovie,
                                                        episodeId = playback.episodeId,
                                                        scraperKey = playback.serverName,
                                                        sourceUrl = playback.website ?: com.example.ui.screens.player.ServerStateStore.getCachedData(currentMediaKey, fileId, playback.mediaId)?.playbackPageUrl
                                                    )
                                                }
                                                com.example.extension.managed.adapter.UnifiedDownloadCoordinator.download(
                                                    context = context,
                                                    source = canonicalSource,
                                                    scope = coroutineScope,
                                                    onStarted = {
                                                        android.widget.Toast.makeText(
                                                            context,
                                                            context.getString(R.string.download_started_success),
                                                            android.widget.Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                )
                                            }
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Download,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = qInfo.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Text(
                                                text = stringResource(R.string.quality_available_badge),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showInlineDownloadDialog = false }) {
                        Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp)
            )
        }

        // Delete Download Confirmation Dialog
        if (showDeleteConfirm && !com.example.ui.screens.player.PlayerStateHolder.isInPipMode) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                icon = {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.delete_download),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.delete_download_confirm),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirm = false
                            coroutineScope.launch {
                                val targetId = if (playback.isMovie) playback.mediaId else "${playback.mediaId}_${playback.episodeId ?: "1"}"
                                downloadRepo.removeFromDownloads(targetId)
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.download_deleted),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                onClose()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.delete), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp)
            )
        }

        // Quality Selection Dialog
        if (showQualityDialog && !com.example.ui.screens.player.PlayerStateHolder.isInPipMode) {
            val qualityOptions = remember(serverSpecificQualities) {
                if (serverSpecificQualities.isNotEmpty()) {
                    listOf("Auto") + serverSpecificQualities.map { it.name }.distinct()
                } else {
                    listOf("Auto")
                }
            }

            AlertDialog(
                onDismissRequest = { showQualityDialog = false },
                title = {
                    Text(
                        text = stringResource(R.string.change_quality),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        qualityOptions.forEach { q ->
                            val isSelected = q == currentQuality
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable {
                                        val currentPos = if (currentTime > 0L) currentTime else exoPlayer.currentPosition
                                        currentQuality = q
                                        showQualityDialog = false
                                        com.example.utils.LastPlaybackStore.updateQuality(context, playback.mediaId, playback.episodeId, q)
                                        val matchedQ = serverSpecificQualities.find { it.name == q }
                                        if (matchedQ != null && matchedQ.url.isNotBlank() && matchedQ.url != playableUrl && matchedQ.url.startsWith("http")) {
                                            targetSwitchSeekPos = currentPos
                                            isBuffering = true
                                            playableUrl = matchedQ.url
                                        } else {
                                            val maxVideoHeight = when (q) {
                                                "1080p" -> 1080
                                                "720p" -> 720
                                                "480p" -> 480
                                                "360p" -> 360
                                                "Auto" -> Int.MAX_VALUE
                                                else -> q.removeSuffix("p").toIntOrNull() ?: Int.MAX_VALUE
                                            }
                                            val maxVideoWidth = if (maxVideoHeight == Int.MAX_VALUE) Int.MAX_VALUE else (maxVideoHeight * 16) / 9
                                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                .buildUpon()
                                                .setMaxVideoSize(maxVideoWidth, maxVideoHeight)
                                                .build()
                                        }
                                        android.widget.Toast.makeText(context, q, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (q == "Auto" && serverSpecificQualities.isEmpty()) stringResource(R.string.auto_quality) else q,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showQualityDialog = false }) {
                        Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}

@Composable
private fun InlineBottomScrubber(
    progressFraction: Float,
    onScrubStart: (Float) -> Unit,
    onScrubMove: (Float) -> Unit,
    onScrubEnd: () -> Unit,
    isInteractive: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackBgColor = Color.White.copy(alpha = 0.3f)

    androidx.compose.foundation.Canvas(
        modifier = modifier
            .pointerInput(isInteractive) {
                if (!isInteractive) return@pointerInput
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                    onScrubStart(fraction)
                    onScrubEnd()
                }
            }
            .pointerInput(isInteractive) {
                if (!isInteractive) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onScrubStart(fraction)
                    },
                    onDragEnd = {
                        onScrubEnd()
                    },
                    onDragCancel = {
                        onScrubEnd()
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        onScrubMove(fraction)
                    }
                )
            }
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val trackHeight = 4.dp.toPx()
        val thumbRadius = 6.dp.toPx()

        // Background Track
        drawRoundRect(
            color = trackBgColor,
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = Size(width, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f)
        )

        // Active Track
        drawRoundRect(
            color = primaryColor,
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = Size(width * progressFraction, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f)
        )

        // Scrubber Thumb
        drawCircle(
            color = primaryColor,
            radius = thumbRadius,
            center = Offset(width * progressFraction, centerY)
        )
    }
}

private fun formatTime(millis: Long, forceHours: Boolean = false): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0 || forceHours) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
