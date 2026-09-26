@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.example.ui.screens.details

import androidx.activity.compose.BackHandler

import androidx.compose.animation.core.animateFloat
import com.example.ui.theme.SuccessGreen
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp

import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import com.example.utils.SiteVerificationManager
import androidx.compose.ui.res.stringResource
import com.example.R

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState

import androidx.compose.runtime.*
import com.example.ui.screens.player.ServerSelectionDialog
import com.example.ui.screens.extensions.NoExtensionsDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.model.ContentType
import com.example.data.util.ContentTypeResolver
import com.example.data.model.DownloadItem
import com.example.data.model.LibraryItem
import com.example.data.repository.DownloadRepository
import com.example.data.repository.LibraryRepository
import com.example.domain.models.CastMember
import com.example.domain.models.Episode
import com.example.domain.models.Season
import com.example.domain.models.VideoTrailer
import com.example.ui.ViewModelFactory
import kotlinx.coroutines.launch
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailsScreen(
    onPersonClick: (String) -> Unit = {},
    movieId: String, 
    autoPlay: Boolean = false,
    onBack: () -> Unit,
    onPlay: (String, String, String?, String?, String) -> Unit,
    onNavigateToExtensions: () -> Unit = {},
    viewModel: MovieDetailsViewModel = viewModel(factory = ViewModelFactory())
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val downloadRepository = remember { DownloadRepository(context) }
    val libraryRepository = remember { LibraryRepository(context) }
    val scope = rememberCoroutineScope()
    val libraryItems by libraryRepository.getLibraryItems().collectAsState(initial = emptyList())
    val downloadItems by downloadRepository.getDownloadItems().collectAsState(initial = emptyList())
    
    val isFavorite by libraryRepository.isItemInLibrary(movieId, ContentType.MOVIE).collectAsState(initial = false)
    val downloadItem = downloadItems.find { it.id == movieId || it.mediaId == movieId }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showNotReleasedDialog by remember { mutableStateOf(false) }
    var isCoverEnlarged by remember { mutableStateOf(false) }

    LaunchedEffect(movieId) {
        viewModel.loadMovie(movieId)
        com.example.ui.screens.player.ServerStateStore.prepareForMedia(movieId, movieId)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (uiState.isLoading && uiState.movie == null) {
            DetailsSkeleton()
        } else if (uiState.movie != null) {
            val movie = uiState.movie!!
            val ctx = LocalContext.current
            val historyRepository = remember { com.example.data.repository.HistoryRepository(ctx) }
            var showSourceSheet by remember { mutableStateOf(false) }
    var showNoExtensionsDialog by remember { mutableStateOf(false) }
            var isDownloadMode by remember { mutableStateOf(false) }
            var selectedTrailerId by remember { mutableStateOf<String?>(null) }

            val ptrState = rememberPullToRefreshState()
            val scrollState = rememberScrollState()
            var activePlayback by remember { mutableStateOf<com.example.ui.components.ActiveInlinePlayback?>(null) }
            val isInPip = com.example.ui.screens.player.PlayerStateHolder.isInPipMode

            LaunchedEffect(movie.id) {
                val rawTitle = movie.originalTitle ?: movie.title
                val mediaKey = "$rawTitle-true-1-1"
                com.example.ui.screens.player.ServerStateStore.prepareForMedia(mediaKey, movie.id.toString())
            }

            BackHandler(enabled = (activePlayback != null || selectedTrailerId != null) && !isInPip) {
                activePlayback = null
                selectedTrailerId = null
            }

            val isMovieDownloaded = (downloadItem?.isCompleted == true) ||
                    com.example.utils.MediaStorageUtils.hasDownloadedMedia(ctx, movie.id) ||
                    downloadItems.any { (it.id == movie.id || it.mediaId == movie.id) && it.isCompleted }

            val resumeLastPlayback = {
                if (isMovieDownloaded) {
                    selectedTrailerId = null
                    val syncPos = com.example.ui.screens.player.PlaybackSyncStore.getPosition(movie.id)
                    val lastPlayback = com.example.utils.LastPlaybackStore.getLastPlayback(ctx, movie.id)
                    val startPos = if (syncPos > 0L) syncPos else (lastPlayback?.positionMillis ?: 0L)
                    val fileId = downloadItem?.id ?: movie.id
                    activePlayback = com.example.ui.components.ActiveInlinePlayback(
                        mediaId = movie.id,
                        title = movie.title,
                        url = "local_offline_file://$fileId",
                        posterUrl = movie.posterUrl,
                        isMovie = true,
                        initialPosition = startPos
                    )
                    scope.launch { scrollState.animateScrollTo(0) }
                } else {
                    if (!com.example.utils.NetworkUtils.isInternetAvailable(ctx)) {
                        Toast.makeText(ctx, ctx.getString(R.string.no_internet_check_connection), Toast.LENGTH_LONG).show()
                    } else {
                        val lastPlayback = com.example.utils.LastPlaybackStore.getLastPlayback(ctx, movie.id)
                        val rawTitle = movie.originalTitle ?: movie.title
                        val mediaKey = "$rawTitle-true-1-1"
                        val cachedData = com.example.ui.screens.player.ServerStateStore.getCachedData(movie.id.toString(), mediaKey)
                        val cachedUrl = if (lastPlayback != null && lastPlayback.url.isNotBlank()) {
                            lastPlayback.url
                        } else if (cachedData != null && cachedData.extractedQualities.isNotEmpty() && cachedData.extractedQualities.first().url.isNotBlank()) {
                            cachedData.extractedQualities.first().url
                        } else if (cachedData != null && cachedData.serverLinks.isNotEmpty()) {
                            cachedData.serverLinks.values.firstOrNull()
                        } else null

                        if (cachedUrl != null) {
                            selectedTrailerId = null
                            val syncPos = com.example.ui.screens.player.PlaybackSyncStore.getPosition(movie.id)
                            val startPos = if (syncPos > 0L) syncPos else (lastPlayback?.positionMillis ?: 0L)
                            activePlayback = com.example.ui.components.ActiveInlinePlayback(
                                mediaId = movie.id,
                                title = movie.title,
                                url = cachedUrl,
                                serverName = lastPlayback?.serverName ?: cachedData?.servers?.firstOrNull(),
                                website = lastPlayback?.website ?: cachedData?.website,
                                posterUrl = movie.posterUrl,
                                isMovie = true,
                                initialPosition = startPos,
                                initialQuality = lastPlayback?.quality
                            )
                            scope.launch { scrollState.animateScrollTo(0) }
                        } else {
                            val isReleased = (movie.releaseDate ?: "") <= java.time.LocalDate.now().toString()
                            if (!isReleased) {
                                showNotReleasedDialog = true
                            } else {
                                isDownloadMode = false
                                if (!com.example.extension.orchestrator.ManagedMediaOrchestrator.hasActiveExtensions()) {
                                    showNoExtensionsDialog = true
                                } else {
                                    showSourceSheet = true
                                }
                            }
                        }
                    }
                }
            }

            val startPlayFlow = {
                resumeLastPlayback()
            }

            var hasAutoPlayed by remember { mutableStateOf(false) }
            LaunchedEffect(movie, autoPlay) {
                if (autoPlay && !hasAutoPlayed) {
                    hasAutoPlayed = true
                    resumeLastPlayback()
                }
            }
            PullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = { viewModel.loadMovie(movieId) },
                state = ptrState,
                modifier = Modifier.fillMaxSize().padding(bottom = if (isInPip) 0.dp else padding.calculateBottomPadding())
            ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (!isInPip) Modifier.verticalScroll(scrollState) else Modifier)
            ) {
                
                if (showDeleteConfirm && !isInPip) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
                        title = { Text(stringResource(R.string.delete_download), color = MaterialTheme.colorScheme.onBackground) },
                        text = { Text(stringResource(R.string.delete_download_confirm), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        confirmButton = {
                            TextButton(onClick = {
                                scope.launch {
                                    if (downloadItem != null) {
                                        downloadRepository.removeFromDownloads(downloadItem)
                                    } else {
                                        downloadRepository.removeFromDownloads(movie.id)
                                    }
                                    Toast.makeText(ctx, ctx.getString(R.string.download_deleted), Toast.LENGTH_SHORT).show()
                                }
                                showDeleteConfirm = false
                            }) { Text(stringResource(R.string.yes_delete), color = Color.Red) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onBackground) }
                        },
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                }
                
                
    if (showNotReleasedDialog && !isInPip) {
        AlertDialog(
            onDismissRequest = { showNotReleasedDialog = false },
            title = { Text(stringResource(R.string.coming_soon), color = MaterialTheme.colorScheme.onBackground) },
            text = { Text(stringResource(R.string.not_released_yet), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = { showNotReleasedDialog = false }) { Text(stringResource(R.string.ok_button)) }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Hero Image or Video Player
                if (activePlayback != null) {
                    Box(modifier = if (isInPip) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f/9f)) {
                        com.example.ui.components.InlineDetailVideoPlayer(
                            playback = activePlayback!!,
                            onFullscreen = { currentPos ->
                                val p = activePlayback!!
                                onPlay(p.title, p.url, p.serverName, p.website, p.posterUrl)
                            },
                            onClose = {
                                activePlayback = null
                            },
                            onChangeServer = {
                                if (!com.example.utils.NetworkUtils.isInternetAvailable(context)) {
                                    Toast.makeText(context, context.getString(R.string.no_internet_check_connection), Toast.LENGTH_SHORT).show()
                                } else {
                                    isDownloadMode = false
                                    showSourceSheet = true
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (!isInPip) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(text = movie.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${movie.year} • ${movie.genres.take(3).joinToString(" • ")}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = stringResource(R.string.rating_r), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(String.format("%.1f", movie.rating), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                            }
                            Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) { Text("18+", color = MaterialTheme.colorScheme.onBackground) }
                        }
                    }
                    }
                } else if (selectedTrailerId != null) {
                    Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f)) {
                        com.example.ui.components.InlineYouTubePlayer(
                            videoId = selectedTrailerId!!,
                            modifier = Modifier.fillMaxSize(),
                            onClose = { selectedTrailerId = null }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(text = movie.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${movie.year} • ${movie.genres.take(3).joinToString(" • ")}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = stringResource(R.string.rating_r), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(String.format("%.1f", movie.rating), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                            }
                            Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) { Text("18+", color = MaterialTheme.colorScheme.onBackground) }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.8f)) {
                        AsyncImage(
                            model = movie.posterUrl.takeIf { it.isNotBlank() } ?: movie.backdropUrl,
                            contentDescription = movie.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { isCoverEnlarged = true }
                        )
                        Box(modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha=0.6f), MaterialTheme.colorScheme.background),
                                startY = 0f
                            )
                        ))
                        Column(
                            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
                        ) {
                            Text(text = movie.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${movie.year} • ${movie.genres.take(3).joinToString(" • ")}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, contentDescription = stringResource(R.string.rating_r), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(String.format("%.1f", movie.rating), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                                }
                                Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) { Text("18+", color = MaterialTheme.colorScheme.onBackground) } // Placeholder for age rating
                            }
                        }
                    }
                }
                
                if (!isInPip) {
                // Action Buttons
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .combinedClickable(
                                onClick = { startPlayFlow() },
                                onLongClick = {
                                    if (isMovieDownloaded) {
                                        showDeleteConfirm = true
                                    }
                                }
                            )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.play_now), tint = MaterialTheme.colorScheme.onBackground)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isMovieDownloaded) stringResource(R.string.resume_offline) else stringResource(R.string.play_now), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                        }
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(50.dp)
                            .background(
                                if (isMovieDownloaded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant, 
                                CircleShape
                            )
                            .clip(CircleShape)
                            .combinedClickable(
                                onClick = { 
                                    if (isMovieDownloaded) {
                                        showDeleteConfirm = true
                                    } else if (downloadItem != null) {
                                        val intent = android.content.Intent(context, com.example.utils.StreamDownloaderService::class.java).apply {
                                            action = if (downloadItem.isPaused) "RESUME" else "PAUSE"
                                            putExtra("id", movie.id.toString())
                                        }
                                        context.startService(intent)
                                        scope.launch { downloadRepository.updateDownload(downloadItem.copy(isPaused = !downloadItem.isPaused)) }
                                        Toast.makeText(context, if (downloadItem.isPaused) context.getString(R.string.download_resumed) else context.getString(R.string.download_paused), Toast.LENGTH_SHORT).show()
                                     } else {
                                        if (!com.example.utils.NetworkUtils.isInternetAvailable(context)) {
                                            Toast.makeText(context, context.getString(R.string.no_internet_check_connection), Toast.LENGTH_SHORT).show()
                                        } else {
                                            isDownloadMode = true
                                            if (!com.example.extension.orchestrator.ManagedMediaOrchestrator.hasActiveExtensions()) {
                                                showNoExtensionsDialog = true
                                            } else {
                                                showSourceSheet = true
                                            }
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (isMovieDownloaded || (downloadItem != null && !downloadItem.isCompleted)) {
                                        showDeleteConfirm = true
                                    }
                                }
                            )
                    ) {
                        if (isMovieDownloaded) {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.completed), tint = Color(0xFF4CAF50))
                        } else if (downloadItem != null) {
                            AnimatedDownloadIcon(isPaused = downloadItem.isPaused)
                        } else {
                            Icon(Icons.Default.Download, contentDescription = stringResource(R.string.downloads), tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                    IconButton(
                        onClick = { 
                            scope.launch {
                                val item = LibraryItem.create(
                                    contentType = ContentType.MOVIE,
                                    tmdbId = movie.id,
                                    title = movie.originalTitle ?: movie.title,
                                    posterUrl = movie.posterUrl
                                )
                                if (isFavorite) libraryRepository.removeFromLibrary(item)
                                else libraryRepository.addToLibrary(item)
                            }
                        },
                        modifier = Modifier.size(50.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = stringResource(R.string.add_to_library_favorites), tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Trailers
                if (movie.trailers.isNotEmpty()) {
                    Text(stringResource(R.string.trailers), color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(movie.trailers) { trailer ->
                            TrailerCard(trailer) {
                                if (!com.example.utils.NetworkUtils.isInternetAvailable(context)) {
                                    Toast.makeText(context, context.getString(R.string.no_internet_check_connection), Toast.LENGTH_SHORT).show()
                                } else {
                                    activePlayback = null
                                    selectedTrailerId = trailer.key
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
                
                // Overview
                Text(stringResource(R.string.overview), color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(movie.overview, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Cast
                if (movie.cast.isNotEmpty()) {
                    Text(stringResource(R.string.cast), color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(movie.cast) { CastMemberCard(it) { onPersonClick(it.id) } }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
                } // end if (!isInPip)
            }

            }
            if (showNoExtensionsDialog && !isInPip) {
                NoExtensionsDialog(
                    onDismiss = { showNoExtensionsDialog = false },
                    onGoToExtensions = { 
                        onNavigateToExtensions()
                    },
                    onRetry = {
                        if (com.example.extension.orchestrator.ManagedMediaOrchestrator.hasActiveExtensions()) {
                            showNoExtensionsDialog = false
                            showSourceSheet = true
                        }
                    }
                )
            }
            if (showSourceSheet && !isInPip) {
                ServerSelectionDialog(
                    title = movie.originalTitle ?: movie.title,
                    year = movie.year.toString(),
                    isMovie = true,
                    isAnime = movie.genres.any { it.contains("Animation", ignoreCase = true) || it.contains("Anime", ignoreCase = true) },
                    isDownloadMode = isDownloadMode,
                    posterUrl = movie.posterUrl,
                    mediaId = movie.id,
                    onDismiss = { showSourceSheet = false },
                    onNavigateToExtensions = onNavigateToExtensions,
                    onPlay = { url, serverName, website ->
                        showSourceSheet = false
                        if (isDownloadMode) {
                            val canonicalSource = com.example.extension.managed.adapter.UnifiedDownloadCoordinator.buildDownloadSource(
                                streamUrl = url,
                                mediaId = movie.id.toString(),
                                title = movie.originalTitle ?: movie.title,
                                quality = serverName,
                                headers = if (website.isNotBlank()) mapOf("Referer" to website) else emptyMap(),
                                posterUrl = movie.posterUrl,
                                isMovie = true,
                                sourceUrl = website.ifBlank { null }
                            )
                            com.example.extension.managed.adapter.UnifiedDownloadCoordinator.download(
                                context = context,
                                source = canonicalSource,
                                scope = scope
                            )
                        } else {
                            scope.launch {
                                historyRepository.addToHistory(
                                    com.example.data.model.HistoryItem(
                                        id = movie.id,
                                        title = movie.originalTitle ?: movie.title,
                                        posterUrl = movie.posterUrl,
                                        isMovie = true
                                    )
                                )
                            }
                            com.example.utils.LastPlaybackStore.saveLastPlayback(
                                context = context,
                                mediaId = movie.id,
                                url = url,
                                serverName = serverName,
                                website = website
                            )
                            selectedTrailerId = null
                            activePlayback = com.example.ui.components.ActiveInlinePlayback(
                                mediaId = movie.id,
                                title = movie.title,
                                url = url,
                                serverName = serverName,
                                website = website,
                                posterUrl = movie.posterUrl,
                                isMovie = true
                            )
                            scope.launch { scrollState.animateScrollTo(0) }
                        }
                    }
                )
            }

            if (isCoverEnlarged) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { isCoverEnlarged = false },
                    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.95f))
                            .clickable { isCoverEnlarged = false },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = movie.posterUrl.takeIf { it.isNotBlank() } ?: movie.backdropUrl,
                            contentDescription = movie.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .clickable { isCoverEnlarged = false }
                        )
                        IconButton(
                            onClick = { isCoverEnlarged = false },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(16.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.cancel),
                                tint = Color.White
                            )
                        }
                    }
                }
            }

    }
}
}

@Composable
fun SeriesDetailsScreen(
    onPersonClick: (String) -> Unit = {},
    seriesId: String,
    autoPlay: Boolean = false,
    onBack: () -> Unit,
    onPlay: (String, String, String?, String?, String) -> Unit,
    onNavigateToExtensions: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: SeriesDetailsViewModel = viewModel(factory = ViewModelFactory())
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val libraryRepository = remember { LibraryRepository(context) }
    val resolvedContentType = uiState.series?.let { ContentTypeResolver.resolveSeries(it) }
    val isFavorite by if (resolvedContentType != null) {
        libraryRepository.isItemInLibrary(seriesId, resolvedContentType).collectAsState(initial = false)
    } else {
        libraryRepository.isSeriesInLibrary(seriesId).collectAsState(initial = false)
    }
    val downloadRepository = remember { DownloadRepository(context) }
    val downloads by downloadRepository.getDownloadItems().collectAsState(initial = emptyList())
    val downloadedEpisodeIds = downloads.map { it.id }.toSet()
    val historyRepository = remember { com.example.data.repository.HistoryRepository(context) }
    val watchedRepo = remember { com.example.data.repository.WatchedEpisodeRepository(context) }
    val watchedEpisodes by watchedRepo.getAllWatched().collectAsState(initial = emptyList())
    val watchedEpisodeIds = watchedEpisodes.map { it.id }.toSet()

    var selectedEpisodeForSource by remember { mutableStateOf<Episode?>(null) }
    var isDownloadMode by remember { mutableStateOf(false) }
    var showBatchDownloadSheet by remember { mutableStateOf(false) }
    var showNotReleasedDialog by remember { mutableStateOf(false) }
    var selectedTrailerId by remember { mutableStateOf<String?>(null) }
    var isCoverEnlarged by remember { mutableStateOf(false) }
    var episodeToDelete by remember { mutableStateOf<com.example.data.model.DownloadItem?>(null) }
    var showEpisodeDeleteConfirm by remember { mutableStateOf(false) }
    var activePlayback by remember { mutableStateOf<com.example.ui.components.ActiveInlinePlayback?>(null) }
    val isInPip = com.example.ui.screens.player.PlayerStateHolder.isInPipMode
    BackHandler(enabled = (activePlayback != null || selectedTrailerId != null) && !isInPip) {
        activePlayback = null
        selectedTrailerId = null
    }

    LaunchedEffect(seriesId) {
        viewModel.loadSeries(seriesId)
        com.example.ui.screens.player.ServerStateStore.prepareForMedia(seriesId, seriesId)
    }

    val pullRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = { viewModel.loadSeries(seriesId) },
        state = pullRefreshState
    ) {
        val series = uiState.series
        if (uiState.isLoading && series == null) {
            DetailsSkeleton()
            return@PullToRefreshBox
        }
        if (series == null && !uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(uiState.error ?: stringResource(R.string.failed_to_load_details), color = MaterialTheme.colorScheme.primary)
            }
            return@PullToRefreshBox
        }
        if (series != null) {
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            val checkIsEpisodeDownloaded: (String) -> Boolean = { epId ->
                val epIdStr1 = "${series.id}_$epId"
                val epIdStr2 = epId
                com.example.utils.MediaStorageUtils.hasDownloadedMedia(context, epIdStr1) ||
                com.example.utils.MediaStorageUtils.hasDownloadedMedia(context, epIdStr2) ||
                downloads.any { (it.id == epIdStr1 || it.id == epIdStr2) && it.isCompleted }
            }

            LaunchedEffect(series.id) {
                val rawTitle = series.originalTitle ?: series.title
                val mediaKey = "$rawTitle-false-1-1"
                com.example.ui.screens.player.ServerStateStore.prepareForMedia(mediaKey, series.id.toString())
            }

            val isSeriesLevelDownloaded = com.example.utils.MediaStorageUtils.hasDownloadedMedia(context, series.id.toString()) ||
                    downloads.any { it.mediaId == series.id.toString() && it.isCompleted }

            val firstDownloadedEpisode = uiState.episodes.firstOrNull { checkIsEpisodeDownloaded(it.id) }

        val isScrollNearBottom by remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                totalItems > 0 && lastVisibleItemIndex >= totalItems - 2
            }
        }
    
    LaunchedEffect(isScrollNearBottom) {
        if (isScrollNearBottom) {
            if (uiState.episodes.isEmpty() && !uiState.isEpisodesLoading) {
                viewModel.triggerInitialEpisodesLoad()
            } else if (uiState.episodes.size > uiState.visibleEpisodesCount && !uiState.isLoadingMore) {
                viewModel.loadMoreEpisodes(context)
            }
        }
    }
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                userScrollEnabled = !isInPip
            ) {
                item {
                val firstUnplayedEpisode = uiState.episodes.firstOrNull { !watchedEpisodeIds.contains(it.id) } ?: uiState.episodes.firstOrNull()

                val resumeLastPlayback = {
                    val lastPlayback = com.example.utils.LastPlaybackStore.getLastPlayback(context, series.id.toString())
                    val targetEpisode = if (lastPlayback?.episodeId != null) {
                        uiState.episodes.find { it.id == lastPlayback.episodeId } 
                            ?: firstDownloadedEpisode 
                            ?: firstUnplayedEpisode
                    } else {
                        firstDownloadedEpisode ?: firstUnplayedEpisode
                    } ?: uiState.episodes.firstOrNull()

                    if (targetEpisode != null) {
                        val ep = targetEpisode
                        val isEpDownloaded = checkIsEpisodeDownloaded(ep.id) || isSeriesLevelDownloaded
                        if (isEpDownloaded) {
                            val fullTitle = "${series.title} - S${uiState.selectedSeason?.seasonNumber ?: 1}E${ep.episodeNumber}"
                            val syncPos = com.example.ui.screens.player.PlaybackSyncStore.getPosition("${series.id}_${ep.id}")
                            val startPos = if (syncPos > 0L) syncPos else (lastPlayback?.positionMillis ?: 0L)
                            val fileId = if (checkIsEpisodeDownloaded(ep.id)) "${series.id}_${ep.id}" else series.id.toString()
                            scope.launch {
                                historyRepository.addToHistory(
                                    com.example.data.model.HistoryItem(
                                        id = series.id.toString(), title = fullTitle, posterUrl = ep.thumbnailUrl, isMovie = false
                                    )
                                )
                            }
                            selectedTrailerId = null
                            activePlayback = com.example.ui.components.ActiveInlinePlayback(
                                mediaId = series.id.toString(),
                                episodeId = ep.id,
                                title = fullTitle,
                                url = "local_offline_file://$fileId",
                                posterUrl = ep.thumbnailUrl,
                                isMovie = false,
                                initialPosition = startPos
                            )
                            scope.launch { listState.animateScrollToItem(0) }
                        } else {
                            if (!com.example.utils.NetworkUtils.isInternetAvailable(context)) {
                                Toast.makeText(context, context.getString(R.string.no_internet_check_connection), Toast.LENGTH_LONG).show()
                            } else {
                                val rawTitle = series.originalTitle ?: series.title
                                val epMediaKey = "$rawTitle-false-${uiState.selectedSeason?.seasonNumber ?: 1}-${ep.episodeNumber}"
                                val cachedData = com.example.ui.screens.player.ServerStateStore.getCachedData("${series.id}_${ep.id}", epMediaKey, series.id.toString())
                                val cachedUrl = if (lastPlayback != null && lastPlayback.url.isNotBlank() && (lastPlayback.episodeId == null || lastPlayback.episodeId == ep.id)) {
                                    lastPlayback.url
                                } else if (cachedData != null && cachedData.extractedQualities.isNotEmpty() && cachedData.extractedQualities.first().url.isNotBlank()) {
                                    cachedData.extractedQualities.first().url
                                } else if (cachedData != null && cachedData.serverLinks.isNotEmpty()) {
                                    cachedData.serverLinks.values.firstOrNull()
                                } else null

                                if (cachedUrl != null) {
                                    val fullTitle = "${series.title} - S${uiState.selectedSeason?.seasonNumber ?: 1}E${ep.episodeNumber}"
                                    selectedTrailerId = null
                                    val syncPos = com.example.ui.screens.player.PlaybackSyncStore.getPosition("${series.id}_${ep.id}")
                                    val startPos = if (syncPos > 0L) syncPos else (if (lastPlayback?.episodeId == ep.id) lastPlayback.positionMillis else 0L)
                                    activePlayback = com.example.ui.components.ActiveInlinePlayback(
                                        mediaId = series.id.toString(),
                                        episodeId = ep.id,
                                        title = fullTitle,
                                        url = cachedUrl,
                                        serverName = lastPlayback?.serverName ?: cachedData?.servers?.firstOrNull(),
                                        website = lastPlayback?.website ?: cachedData?.website,
                                        posterUrl = ep.thumbnailUrl,
                                        isMovie = false,
                                        initialPosition = startPos,
                                        initialQuality = lastPlayback?.quality
                                    )
                                    scope.launch { listState.animateScrollToItem(0) }
                                } else {
                                    val isReleased = (series.firstAirDate ?: "") <= java.time.LocalDate.now().toString()
                                    if (!isReleased) {
                                        showNotReleasedDialog = true
                                    } else {
                                        selectedEpisodeForSource = ep
                                        isDownloadMode = false
                                    }
                                }
                            }
                        }
                    } else {
                        val dlForSeries = downloads.find { (it.mediaId == series.id.toString() || it.id.startsWith("${series.id}_")) && it.isCompleted }
                        if (dlForSeries != null) {
                            selectedTrailerId = null
                            val fallbackEpId = if (dlForSeries.id.contains("_")) dlForSeries.id.substringAfter("_") else null
                            activePlayback = com.example.ui.components.ActiveInlinePlayback(
                                mediaId = series.id.toString(),
                                episodeId = fallbackEpId,
                                title = dlForSeries.title,
                                url = "local_offline_file://${dlForSeries.id}",
                                posterUrl = dlForSeries.posterUrl,
                                isMovie = false
                            )
                            scope.launch { listState.animateScrollToItem(0) }
                        } else {
                            if (!com.example.utils.NetworkUtils.isInternetAvailable(context)) {
                                Toast.makeText(context, context.getString(R.string.no_internet_check_connection), Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, context.getString(R.string.no_episodes_available), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                val startPlayFlow = {
                    resumeLastPlayback()
                }

                var hasAutoPlayed by remember { mutableStateOf(false) }
                LaunchedEffect(series, uiState.episodes, autoPlay, downloads) {
                    if (autoPlay && !hasAutoPlayed && (uiState.episodes.isNotEmpty() || downloads.isNotEmpty())) {
                        hasAutoPlayed = true
                        resumeLastPlayback()
                    }
                }

                
    if (showNotReleasedDialog && !isInPip) {
        AlertDialog(
            onDismissRequest = { showNotReleasedDialog = false },
            title = { Text(stringResource(R.string.coming_soon), color = MaterialTheme.colorScheme.onBackground) },
            text = { Text(stringResource(R.string.not_released_yet), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = { showNotReleasedDialog = false }) { Text(stringResource(R.string.ok_button)) }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Hero Image or Video Player
                if (activePlayback != null) {
                    Box(modifier = if (isInPip) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f/9f)) {
                        com.example.ui.components.InlineDetailVideoPlayer(
                            playback = activePlayback!!,
                            onFullscreen = { currentPos ->
                                val p = activePlayback!!
                                onPlay(p.title, p.url, p.serverName, p.website, p.posterUrl)
                            },
                            onClose = {
                                activePlayback = null
                            },
                            onChangeServer = {
                                if (!com.example.utils.NetworkUtils.isInternetAvailable(context)) {
                                    Toast.makeText(context, context.getString(R.string.no_internet_check_connection), Toast.LENGTH_SHORT).show()
                                } else {
                                    isDownloadMode = false
                                    val targetEp = uiState.episodes.find { it.id.toString() == activePlayback?.episodeId }
                                        ?: firstUnplayedEpisode ?: uiState.episodes.firstOrNull()
                                    if (targetEp != null) {
                                        selectedEpisodeForSource = targetEp
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (!isInPip) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(text = series.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${series.year} • ${series.genres.take(3).joinToString(" • ")}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = stringResource(R.string.rating_r), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(String.format("%.1f", series.rating), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                            }
                            Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) { Text("18+", color = MaterialTheme.colorScheme.onBackground) }
                        }
                    }
                    }
                } else if (selectedTrailerId != null) {
                    Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f)) {
                        com.example.ui.components.InlineYouTubePlayer(
                            videoId = selectedTrailerId!!,
                            modifier = Modifier.fillMaxSize(),
                            onClose = { selectedTrailerId = null }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(text = series.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${series.year} • ${series.genres.take(3).joinToString(" • ")}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = stringResource(R.string.rating_r), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(String.format("%.1f", series.rating), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                            }
                            Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) { Text("18+", color = MaterialTheme.colorScheme.onBackground) }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.8f)) {
                        AsyncImage(
                            model = series.posterUrl.takeIf { it.isNotBlank() } ?: series.backdropUrl,
                            contentDescription = series.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { isCoverEnlarged = true }
                        )
                        Box(modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha=0.6f), MaterialTheme.colorScheme.background),
                                startY = 0f
                            )
                        ))
                        Column(
                            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
                        ) {
                            Text(text = series.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${series.year} • ${series.genres.take(3).joinToString(" • ")}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, contentDescription = stringResource(R.string.rating_r), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(String.format("%.1f", series.rating), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                                }
                                Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) { Text("18+", color = MaterialTheme.colorScheme.onBackground) } // Placeholder for age rating
                            }
                        }
                    }
                }
                
                if (!isInPip) {
                // Action Buttons
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    val isAnyDownloaded = firstDownloadedEpisode != null || isSeriesLevelDownloaded
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .combinedClickable(
                                onClick = { startPlayFlow() },
                                onLongClick = {
                                    if (isAnyDownloaded) {
                                        val epDl = firstDownloadedEpisode?.let { ep ->
                                            downloads.find { it.id == "${series.id}_${ep.id}" || it.id == ep.id }
                                        } ?: downloads.find { it.mediaId == series.id.toString() }
                                        if (epDl != null) {
                                            episodeToDelete = epDl
                                            showEpisodeDeleteConfirm = true
                                        }
                                    }
                                }
                            )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.play_now), tint = MaterialTheme.colorScheme.onBackground)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isAnyDownloaded) stringResource(R.string.resume_offline) else stringResource(R.string.play), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                        }
                    }
                    IconButton(
                        onClick = {
                            if (!com.example.utils.NetworkUtils.isInternetAvailable(context)) {
                                Toast.makeText(context, context.getString(R.string.no_internet_check_connection), Toast.LENGTH_SHORT).show()
                            } else {
                                showBatchDownloadSheet = true
                            }
                        },
                        modifier = Modifier.size(50.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = stringResource(R.string.downloads), tint = MaterialTheme.colorScheme.onBackground)
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                val contentType = ContentTypeResolver.resolveSeries(series)
                                val item = LibraryItem.create(
                                    contentType = contentType,
                                    tmdbId = series.id,
                                    title = series.originalTitle ?: series.title,
                                    posterUrl = series.posterUrl
                                )
                                if (isFavorite) libraryRepository.removeFromLibrary(item)
                                else libraryRepository.addToLibrary(item)
                            }
                        },
                        modifier = Modifier.size(50.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = stringResource(R.string.add_to_library_favorites), tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Trailers
                if (series.trailers.isNotEmpty()) {
                    Text(stringResource(R.string.trailers), color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(series.trailers) { trailer ->
                            TrailerCard(trailer) {
                                if (!com.example.utils.NetworkUtils.isInternetAvailable(context)) {
                                    Toast.makeText(context, context.getString(R.string.no_internet_check_connection), Toast.LENGTH_SHORT).show()
                                } else {
                                    activePlayback = null
                                    selectedTrailerId = trailer.key
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
                
                // Overview
                Text(stringResource(R.string.overview), color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(series.overview, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Cast
                if (series.cast.isNotEmpty()) {
                    Text(stringResource(R.string.cast), color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(series.cast) { CastMemberCard(it) { onPersonClick(it.id) } }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
                // Seasons & Episodes
                if (series.seasons.isNotEmpty()) {
                    Text(stringResource(R.string.seasons_and_episodes), color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Season Tabs
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(series.seasons.filter { it.seasonNumber > 0 }) { season ->
                            val isSelected = uiState.selectedSeason?.id == season.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectSeason(season) },
                                label = { Text(stringResource(R.string.season_number, season.seasonNumber)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onBackground
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // Episodes
                } // Close if block
                } // Close if (!isInPip) block
                } // Close item block
                
                if (!isInPip) {
                if (uiState.isEpisodesLoading) {
                    item { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                } else {
                    items(uiState.episodes.take(uiState.visibleEpisodesCount)) { episode ->
                        val isWatched = watchedEpisodeIds.contains(episode.id)
                        val downloadItem = downloads.find { it.id == "${series.id}_${episode.id}" || it.id == episode.id.toString() }
                        val isDownloaded = downloadItem?.isCompleted == true || checkIsEpisodeDownloaded(episode.id)
                        EpisodeCard(
                            episode = episode,
                            isWatched = isWatched,
                            downloadItem = downloadItem,
                            onClick = {
                                if (isDownloaded) {
                                    val fullTitle = "${series.title} - S${uiState.selectedSeason?.seasonNumber}E${episode.episodeNumber}"
                                    scope.launch {
                                        historyRepository.addToHistory(
                                            com.example.data.model.HistoryItem(
                                                id = series.id.toString(), title = fullTitle, posterUrl = episode.thumbnailUrl, isMovie = false
                                            )
                                        )
                                    }
                                    val syncPos = com.example.ui.screens.player.PlaybackSyncStore.getPosition("${series.id}_${episode.id}")
                                    selectedTrailerId = null
                                    activePlayback = com.example.ui.components.ActiveInlinePlayback(
                                        mediaId = series.id.toString(),
                                        episodeId = episode.id.toString(),
                                        title = fullTitle,
                                        url = "local_offline_file://${series.id}_${episode.id}",
                                        posterUrl = episode.thumbnailUrl,
                                        isMovie = false,
                                        initialPosition = syncPos
                                    )
                                    scope.launch { listState.animateScrollToItem(0) }
                                } else {
                                    if (!com.example.utils.NetworkUtils.isInternetAvailable(context)) {
                                        Toast.makeText(context, context.getString(R.string.no_internet_check_connection), Toast.LENGTH_SHORT).show()
                                    } else {
                                        val rawTitle = series.originalTitle ?: series.title
                                        val epMediaKey = "$rawTitle-false-${uiState.selectedSeason?.seasonNumber ?: 1}-${episode.episodeNumber}"
                                        val cachedData = com.example.ui.screens.player.ServerStateStore.getCachedData("${series.id}_${episode.id}", epMediaKey, series.id.toString())
                                        val lastPlayback = com.example.utils.LastPlaybackStore.getLastPlayback(context, series.id.toString())
                                        val cachedUrl = if (lastPlayback != null && lastPlayback.url.isNotBlank() && lastPlayback.episodeId == episode.id) {
                                            lastPlayback.url
                                        } else if (cachedData != null && cachedData.extractedQualities.isNotEmpty() && cachedData.extractedQualities.first().url.isNotBlank()) {
                                            cachedData.extractedQualities.first().url
                                        } else if (cachedData != null && cachedData.serverLinks.isNotEmpty()) {
                                            cachedData.serverLinks.values.firstOrNull()
                                        } else null

                                        if (cachedUrl != null) {
                                            val fullTitle = "${series.title} - S${uiState.selectedSeason?.seasonNumber ?: 1}E${episode.episodeNumber}"
                                            selectedTrailerId = null
                                            val syncPos = com.example.ui.screens.player.PlaybackSyncStore.getPosition("${series.id}_${episode.id}")
                                            val startPos = if (syncPos > 0L) syncPos else (if (lastPlayback?.episodeId == episode.id) lastPlayback.positionMillis else 0L)
                                            activePlayback = com.example.ui.components.ActiveInlinePlayback(
                                                mediaId = series.id.toString(),
                                                episodeId = episode.id.toString(),
                                                title = fullTitle,
                                                url = cachedUrl,
                                                serverName = lastPlayback?.serverName ?: cachedData?.servers?.firstOrNull(),
                                                website = lastPlayback?.website ?: cachedData?.website,
                                                posterUrl = episode.thumbnailUrl,
                                                isMovie = false,
                                                initialPosition = startPos
                                            )
                                            scope.launch { listState.animateScrollToItem(0) }
                                        } else {
                                            selectedEpisodeForSource = episode
                                            isDownloadMode = false
                                        }
                                    }
                                }
                            },
                            onLongClick = {
                                if (isDownloaded) {
                                    episodeToDelete = downloadItem ?: com.example.data.model.DownloadItem(
                                        id = "${series.id}_${episode.id}",
                                        mediaId = series.id.toString(),
                                        title = "${series.title} - S${uiState.selectedSeason?.seasonNumber ?: 1}E${episode.episodeNumber}",
                                        posterUrl = episode.thumbnailUrl,
                                        isMovie = false,
                                        quality = "",
                                        progress = 1f,
                                        isPaused = false,
                                        isCompleted = true,
                                        fileSizeBytes = 0L
                                    )
                                    showEpisodeDeleteConfirm = true
                                } else {
                                    scope.launch {
                                        if (isWatched) watchedRepo.markAsUnwatched(episode.id)
                                        else watchedRepo.markAsWatched(episode.id)
                                    }
                                }
                            },
                            onLongDownloadClick = {
                                if (isDownloaded || (downloadItem != null && !downloadItem.isCompleted)) {
                                    episodeToDelete = downloadItem ?: com.example.data.model.DownloadItem(
                                        id = "${series.id}_${episode.id}",
                                        mediaId = series.id.toString(),
                                        title = "${series.title} - S${uiState.selectedSeason?.seasonNumber ?: 1}E${episode.episodeNumber}",
                                        posterUrl = episode.thumbnailUrl,
                                        isMovie = false,
                                        quality = "",
                                        progress = 1f,
                                        isPaused = false,
                                        isCompleted = true,
                                        fileSizeBytes = 0L
                                    )
                                    showEpisodeDeleteConfirm = true
                                }
                            },
                            onDownloadClick = {
                                if (isDownloaded) {
                                    episodeToDelete = downloadItem ?: com.example.data.model.DownloadItem(
                                        id = "${series.id}_${episode.id}",
                                        mediaId = series.id.toString(),
                                        title = "${series.title} - S${uiState.selectedSeason?.seasonNumber ?: 1}E${episode.episodeNumber}",
                                        posterUrl = episode.thumbnailUrl,
                                        isMovie = false,
                                        quality = "",
                                        progress = 1f,
                                        isPaused = false,
                                        isCompleted = true,
                                        fileSizeBytes = 0L
                                    )
                                    showEpisodeDeleteConfirm = true
                                } else if (downloadItem != null) {
                                    val intent = android.content.Intent(context, com.example.utils.StreamDownloaderService::class.java).apply {
                                        action = if (downloadItem.isPaused) "RESUME" else "PAUSE"
                                        putExtra("id", "${series.id}_${episode.id}")
                                    }
                                    context.startService(intent)
                                    scope.launch { downloadRepository.updateDownload(downloadItem.copy(isPaused = !downloadItem.isPaused)) }
                                    Toast.makeText(context, if (downloadItem.isPaused) context.getString(R.string.download_resumed) else context.getString(R.string.download_paused), Toast.LENGTH_SHORT).show()
                                } else {
                                    if (!com.example.utils.NetworkUtils.isInternetAvailable(context)) {
                                        Toast.makeText(context, context.getString(R.string.no_internet_check_connection), Toast.LENGTH_SHORT).show()
                                    } else {
                                        selectedEpisodeForSource = episode
                                        isDownloadMode = true
                                    }
                                }
                            }
                        )
                    }
                    
                    if (uiState.episodes.size > uiState.visibleEpisodesCount || uiState.isLoadingMore) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                                if (uiState.isLoadingMore) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(stringResource(R.string.loading_eps), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                                    }
                                } else {
                                    Text(stringResource(R.string.swipe_to_load), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }
            
            if (selectedEpisodeForSource != null && !isInPip) {
                ServerSelectionDialog(
                    title = series.originalTitle ?: series.title,
                    year = series.year.toString(),
                    isMovie = false,
                    season = uiState.selectedSeason?.seasonNumber ?: 1,
                    episode = selectedEpisodeForSource?.episodeNumber ?: 1,
                    isAnime = series.genres.any { it.contains("Animation", ignoreCase = true) || it.contains("Anime", ignoreCase = true) },
                    isDownloadMode = isDownloadMode,
                    posterUrl = selectedEpisodeForSource?.thumbnailUrl ?: series.posterUrl,
                    mediaId = series.id.toString(),
                    onDismiss = { selectedEpisodeForSource = null },
                    onNavigateToExtensions = onNavigateToExtensions,
                    onPlay = { url, serverName, website ->
                        val ep = selectedEpisodeForSource!!
                        selectedEpisodeForSource = null
                        if (isDownloadMode) {
                            val fullTitle = "${series.title} - S${uiState.selectedSeason?.seasonNumber}E${ep.episodeNumber}"
                            val epIdStr = "${series.id}_${ep.id}"
                            val canonicalSource = com.example.extension.managed.adapter.UnifiedDownloadCoordinator.buildDownloadSource(
                                streamUrl = url,
                                mediaId = series.id.toString(),
                                title = fullTitle,
                                quality = serverName,
                                headers = if (website.isNotBlank()) mapOf("Referer" to website) else emptyMap(),
                                posterUrl = ep.thumbnailUrl,
                                isMovie = false,
                                episodeId = ep.id,
                                sourceUrl = website.ifBlank { null }
                            )
                            com.example.extension.managed.adapter.UnifiedDownloadCoordinator.download(
                                context = context,
                                source = canonicalSource,
                                scope = scope
                            )
                        } else {
                            val fullTitle = "${series.title} - S${uiState.selectedSeason?.seasonNumber}E${ep.episodeNumber}"
                            scope.launch {
                                historyRepository.addToHistory(
                                    com.example.data.model.HistoryItem(
                                        id = series.id.toString(), title = fullTitle, posterUrl = ep.thumbnailUrl, isMovie = false
                                    )
                                )
                            }
                            com.example.utils.LastPlaybackStore.saveLastPlayback(
                                context = context,
                                mediaId = series.id.toString(),
                                url = url,
                                serverName = serverName,
                                website = website,
                                episodeId = ep.id.toString()
                            )
                            selectedTrailerId = null
                            activePlayback = com.example.ui.components.ActiveInlinePlayback(
                                mediaId = series.id.toString(),
                                episodeId = ep.id.toString(),
                                title = fullTitle,
                                url = url,
                                serverName = serverName,
                                website = website,
                                posterUrl = ep.thumbnailUrl,
                                isMovie = false
                            )
                            scope.launch { listState.animateScrollToItem(0) }
                        }
                    }
                )
            }

            if (showBatchDownloadSheet && !isInPip) {
                com.example.ui.components.BatchDownloadSheet(
                    series = series,
                    currentSeason = uiState.selectedSeason,
                    episodes = uiState.episodes,
                    onDismiss = { showBatchDownloadSheet = false }
                )
            }

            if (isCoverEnlarged) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { isCoverEnlarged = false },
                    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.95f))
                            .clickable { isCoverEnlarged = false },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = series.posterUrl.takeIf { it.isNotBlank() } ?: series.backdropUrl,
                            contentDescription = series.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .clickable { isCoverEnlarged = false }
                        )
                        IconButton(
                            onClick = { isCoverEnlarged = false },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(16.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.cancel),
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            if (showEpisodeDeleteConfirm && !isInPip && episodeToDelete != null) {
                AlertDialog(
                    onDismissRequest = {
                        showEpisodeDeleteConfirm = false
                        episodeToDelete = null
                    },
                    title = { Text(stringResource(R.string.delete_download), color = MaterialTheme.colorScheme.onBackground) },
                    text = { Text(stringResource(R.string.delete_download_confirm), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    confirmButton = {
                        TextButton(onClick = {
                            episodeToDelete?.let { epDl ->
                                scope.launch {
                                    downloadRepository.removeFromDownloads(epDl)
                                    Toast.makeText(context, context.getString(R.string.download_deleted), Toast.LENGTH_SHORT).show()
                                }
                            }
                            showEpisodeDeleteConfirm = false
                            episodeToDelete = null
                        }) { Text(stringResource(R.string.yes_delete), color = Color.Red) }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showEpisodeDeleteConfirm = false
                            episodeToDelete = null
                        }) { Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onBackground) }
                    },
                    containerColor = MaterialTheme.colorScheme.surface
                )
            }
        }
    }
}

@Composable
fun TrailerCard(trailer: VideoTrailer, onClick: () -> Unit) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .width(200.dp)
            .aspectRatio(16f/9f)
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                if (!com.example.utils.NetworkUtils.isInternetAvailable(context)) {
                    Toast.makeText(context, context.getString(R.string.no_internet_check_connection), Toast.LENGTH_SHORT).show()
                } else {
                    onClick()
                }
            }
    ) {
        AsyncImage(
            model = "https://img.youtube.com/vi/${trailer.key}/hqdefault.jpg",
            contentDescription = trailer.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha=0.3f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.play_now), tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(48.dp))
        }
        Text(
            text = trailer.name,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
        )
    }
}

@Composable
fun CastMemberCard(cast: CastMember, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp).clickable { onClick() }) {
        val placeholderColor = MaterialTheme.colorScheme.surfaceVariant
        AsyncImage(
            model = cast.profileUrl,
            placeholder = androidx.compose.ui.graphics.painter.ColorPainter(placeholderColor),
            error = androidx.compose.ui.graphics.painter.ColorPainter(placeholderColor),
            contentDescription = cast.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(cast.name, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(cast.character, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun EpisodeCard(
    episode: Episode,
    isWatched: Boolean,
    downloadItem: com.example.data.model.DownloadItem? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onLongDownloadClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(120.dp).aspectRatio(16f/9f).clip(RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = episode.thumbnailUrl,
                contentDescription = episode.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.play_now), tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.align(Alignment.Center))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${episode.episodeNumber}. ${episode.title}",
                color = if (isWatched) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = stringResource(R.string.rating_r), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(String.format("%.1f", episode.rating), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.width(8.dp))
                Text("${episode.duration}m", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(episode.overview, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        val isEpisodeDownloaded = downloadItem?.isCompleted == true
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .combinedClickable(
                    onClick = onDownloadClick,
                    onLongClick = onLongDownloadClick
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isEpisodeDownloaded) {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.completed), tint = Color(0xFF4CAF50))
            } else if (downloadItem != null) {
                AnimatedDownloadIcon(isPaused = downloadItem.isPaused)
            } else {
                Icon(Icons.Default.Download, contentDescription = stringResource(R.string.downloads), tint = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
    }
@Composable
fun AnimatedDownloadIcon(isPaused: Boolean) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
    val offset by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        )
    )
    
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier.size(24.dp).clipToBounds(), 
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        val tint = if (isPaused) androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.5f) else androidx.compose.material3.MaterialTheme.colorScheme.primary
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Default.ArrowDownward, 
            contentDescription = stringResource(R.string.downloading), 
            tint = tint, 
            modifier = if (isPaused) androidx.compose.ui.Modifier else androidx.compose.ui.Modifier.offset(y = offset.dp)
        )
    


}




}

