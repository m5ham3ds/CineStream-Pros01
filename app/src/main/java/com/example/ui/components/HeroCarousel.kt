package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.ui.res.stringResource
import com.example.R
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ContentType
import com.example.data.model.LibraryItem
import com.example.data.repository.LibraryRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HeroCarousel(
    items: List<HeroItem>,
    onClick: (String) -> Unit
) {
    if (items.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { items.size })
    val context = LocalContext.current
    val libraryRepository = remember { LibraryRepository(context) }
    val scope = rememberCoroutineScope()
    var showRemoveDialog by remember { mutableStateOf<HeroItem?>(null) }

    if (showRemoveDialog != null) {
        val itemToRemove = showRemoveDialog!!
        AlertDialog(
            onDismissRequest = { showRemoveDialog = null },
            title = { Text(stringResource(R.string.remove_from_favorites), color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(stringResource(R.string.remove_from_favorites_confirm), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val libItem = LibraryItem.create(
                            contentType = itemToRemove.contentType,
                            tmdbId = itemToRemove.id,
                            title = itemToRemove.title,
                            posterUrl = itemToRemove.backdropUrl
                        )
                        libraryRepository.removeFromLibrary(libItem)
                    }
                    showRemoveDialog = null
                }) {
                    Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = null }) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurface)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    LaunchedEffect(pagerState) {
        while (true) {
            delay(3000)
            val nextPage = (pagerState.currentPage + 1) % items.size
            pagerState.animateScrollToPage(nextPage)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f) // Set aspect ratio to 16:9
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val item = items[page]
            val isBookmarked by libraryRepository.isItemInLibrary(item.id, item.contentType).collectAsState(initial = false)

            Box(modifier = Modifier
                .fillMaxSize()
                .clickable { onClick(item.id) } // Clickable whole box
            ) {
                AsyncImage(
                    model = item.backdropUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Background Gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.9f)),
                                startY = 50f
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(stringResource(R.string.new_release), color = MaterialTheme.colorScheme.onBackground, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleLarge, // Slightly smaller text since box is smaller
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(percent = 50))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { onClick(item.id) }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.cd_play), tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.play), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                                .clickable {
                                    if (isBookmarked) {
                                        showRemoveDialog = item
                                    } else {
                                        scope.launch {
                                            val libItem = LibraryItem.create(
                                                contentType = item.contentType,
                                                tmdbId = item.id,
                                                title = item.title,
                                                posterUrl = item.backdropUrl
                                            )
                                            libraryRepository.addToLibrary(libItem)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isBookmarked) Icons.Default.Check else Icons.Default.Add, 
                                contentDescription = stringResource(R.string.library), 
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Carousel Dots
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(items.size) { index ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 16.dp else 4.dp, 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
        }
    }
}

data class HeroItem(
    val id: String,
    val title: String,
    val backdropUrl: String,
    val isMovie: Boolean = true,
    val contentType: String = if (isMovie) ContentType.MOVIE else ContentType.TV
)