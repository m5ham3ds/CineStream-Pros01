package com.example.ui.components

import androidx.compose.ui.res.stringResource
import com.example.R

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DownloadItem
import com.example.data.repository.DownloadRepository
import com.example.domain.models.Episode
import com.example.domain.models.Season
import com.example.domain.models.Series
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchDownloadSheet(
    series: Series,
    currentSeason: Season?,
    episodes: List<Episode>,
    onDismiss: () -> Unit
) {
    var selectedEpisodes by remember { mutableStateOf(setOf<String>()) }
    var showQualitySelector by remember { mutableStateOf(false) }
    var targetQuality by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val downloadRepository = remember { DownloadRepository(context) }

    if (showQualitySelector) {
        AlertDialog(
            onDismissRequest = { showQualitySelector = false },
            title = { Text(stringResource(R.string.select_quality)) },
            text = {
                Column {
                    listOf("1080p", "720p", "480p", "360p", "Auto").forEach { quality ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    targetQuality = quality
                                    showQualitySelector = false
                                    isProcessing = true
                                    showQualitySelector = false
                                }
                                .padding(16.dp)
                        ) {
                            Text(quality)
                        }
                    }
                }
            },
            confirmButton = {}
        )
        return
    }

    if (isProcessing) {
        val episodesToDownload = episodes.filter { selectedEpisodes.contains(it.id) }
        BatchDownloadProcessor(
            series = series,
            seasonNumber = currentSeason?.seasonNumber ?: 1,
            episodes = episodesToDownload,
            targetQuality = targetQuality,
            onComplete = {
                isProcessing = false
                Toast.makeText(context, context.getString(R.string.episodes_added_to_downloads), Toast.LENGTH_SHORT).show()
                onDismiss()
            },
            onCancel = {
                isProcessing = false
            }
        )
        return
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            val seasonTitle = currentSeason?.seasonNumber?.let { stringResource(R.string.season_number, it) } ?: ""
            val titleText = if (seasonTitle.isNotBlank()) {
                "${stringResource(R.string.batch_download)} - $seasonTitle"
            } else {
                stringResource(R.string.batch_download)
            }
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { 
                    selectedEpisodes = if (selectedEpisodes.size == episodes.size) {
                        emptySet()
                    } else {
                        episodes.map { it.id }.toSet()
                    }
                }) {
                    Text(if (selectedEpisodes.size == episodes.size) stringResource(R.string.deselect_all_episodes) else stringResource(R.string.select_all_episodes))
                }
                Button(
                    onClick = { showQualitySelector = true },
                    enabled = selectedEpisodes.isNotEmpty()
                ) {
                    Text(stringResource(R.string.download_count, selectedEpisodes.size))
                }
            }

            LazyColumn {
                items(episodes) { episode ->
                    val isSelected = selectedEpisodes.contains(episode.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedEpisodes = if (isSelected) {
                                    selectedEpisodes - episode.id
                                } else {
                                    selectedEpisodes + episode.id
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                selectedEpisodes = if (checked) {
                                    selectedEpisodes + episode.id
                                } else {
                                    selectedEpisodes - episode.id
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.episode_format, episode.episodeNumber, episode.title))
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}