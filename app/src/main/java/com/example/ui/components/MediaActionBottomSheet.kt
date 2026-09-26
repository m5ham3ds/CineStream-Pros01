package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.ui.res.stringResource
import com.example.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.domain.models.VideoStream
import com.example.domain.models.VideoQuality

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaActionBottomSheet(
    isMovie: Boolean,
    onDismissRequest: () -> Unit,
    onDownloadStart: (String) -> Unit,
    onAddToLibrary: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    
    var step by remember { mutableStateOf(if (isMovie) 2 else 0) } // 0 = episode select, 1 = episode quality, 2 = movie quality

    // TODO: In a real implementation, you would pass `streams: List<VideoStream>` from your ViewModel 
    // which gets it from the `ServerAggregator`. This is mock data demonstrating the architecture.
    val availableStreams = listOf(
        VideoStream("Server 1 (HighSpeed)", VideoQuality.Q_1080, "url1"),
        VideoStream("Server 2 (Backup)", VideoQuality.Q_720, "url2"),
        VideoStream("Server 1 (HighSpeed)", VideoQuality.Q_480, "url3"),
        VideoStream("Server 3 (External)", VideoQuality.Q_1080, "url4")
    ).sortedByDescending { it.quality.resolution }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            when (step) {
                0 -> {
                    Text(stringResource(R.string.select_episode), color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    (1..5).forEach { ep ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { step = 1 }
                                .padding(vertical = 14.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = stringResource(R.string.episode_number, ep), color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
                        }
                    }
                }
                1, 2 -> {
                    Text(stringResource(R.string.select_source), color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    availableStreams.forEach { stream ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    scope.launch { sheetState.hide() }.invokeOnCompletion { 
                                        if (!sheetState.isVisible) {
                                            // Pass the selected quality/server string for now
                                            onDownloadStart("${stream.quality.displayName} - ${stream.serverName}")
                                        }
                                    }
                                }
                                .padding(vertical = 14.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(text = stream.quality.displayName, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text(text = stream.serverName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.Default.Download, contentDescription = stringResource(R.string.cd_download), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            
            if (step == 0 || step == 2) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            scope.launch { sheetState.hide() }.invokeOnCompletion { 
                                if (!sheetState.isVisible) {
                                    onAddToLibrary()
                                }
                            }
                        }
                        .padding(vertical = 14.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = stringResource(R.string.add_to_library_favorites), color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}