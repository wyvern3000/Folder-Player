package com.wing.folderplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wing.folderplayer.data.source.WebDavAuthManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun LandscapePlayerLayout(
    viewModel: PlayerViewModel,
    uiState: PlayerUiState
) {
    val configuration = LocalConfiguration.current
    val screenRatio = configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.toFloat()

    // Determine split ratio
    val leftPanelWeight = when {
        screenRatio < 1.6f -> 0.50f   // 3:2
        screenRatio < 1.9f -> 0.46f   // 16:9
        screenRatio < 2.1f -> 0.43f   // 18:9
        else -> 0.40f                  // Ultra-wide
    }
    
    var showPlaylist by remember { mutableStateOf(false) }
    var showAlbumInfo by remember { mutableStateOf(false) }

    // Dynamic background
    val brush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
            Color(0xFF0F0F0F)
        )
    )

    Surface(
        color = Color.Black,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // Left Panel: Controls + Swipe Gesture for Playlist
                Box(
                    modifier = Modifier
                        .weight(leftPanelWeight)
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Left))
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { change, dragAmount ->
                                // Detect upward swipe to show playlist, similar to portrait
                                if (dragAmount < -30) {
                                    showPlaylist = true
                                }
                            }
                        }
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LandscapeLeftControlPanel(
                        uiState = uiState,
                        viewModel = viewModel,
                        onAlbumClick = {
                            showAlbumInfo = true
                            viewModel.fetchAlbumInfo()
                        }
                    )
                }

                // Right Panel: Lyrics (No swipe gesture here requested)
                Box(
                    modifier = Modifier
                        .weight(1f - leftPanelWeight)
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Right))
                        .padding(top = 16.dp, bottom = 16.dp, end = 24.dp)
                ) {
                    LandscapeLyricsPanel(
                        uiState = uiState,
                        onDoubleTap = {} // No expand in landscape
                    )
                }
            }
            
            // Playlist Overlay (Right Side)
            AnimatedVisibility(
                visible = showPlaylist,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .fillMaxWidth(1f - leftPanelWeight)
                    .fillMaxHeight(0.95f) // Almost full height
                    .graphicsLayer { translationX = 0f }
            ) {
                 Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { change, dragAmount ->
                                // Detect downward swipe to close
                                if (dragAmount > 30) {
                                    showPlaylist = false
                                }
                            }
                        },
                    color = Color(0xFF1A1A1A).copy(alpha = 0.98f),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 0.dp)
                ) {
                    PlaylistSheetContent(
                        uiState = uiState,
                        viewModel = viewModel,
                        onClose = { showPlaylist = false }
                    )
                }
            }
            
             // Album Info Overlay (Right Side)
            AnimatedVisibility(
                visible = showAlbumInfo,
                enter = slideInVertically(initialOffsetY = { it / 2 }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxWidth(1f - leftPanelWeight)
                    .fillMaxHeight()
                    .padding(16.dp)
            ) {
                 var showArtistDetail by remember { mutableStateOf(false) }
                 
                 Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF1A1A1A).copy(alpha = 0.95f),
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 8.dp
                ) {
                     Column(modifier = Modifier.padding(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.MusicNote, 
                                contentDescription = null, 
                                tint = MaterialTheme.colorScheme.primary, 
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                if (showArtistDetail) "Artist Discovery" else "Album Discovery", 
                                style = MaterialTheme.typography.titleLarge, 
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Text(
                            text = if (showArtistDetail) uiState.currentArtist else uiState.currentFolderName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha=0.8f),
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )
                        
                        if (uiState.isFetchingAlbumInfo) {
                            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                item {
                                    val displayInfo = if (showArtistDetail) uiState.artistInfo else uiState.albumInfo
                                    Text(
                                        displayInfo ?: "No information available.",
                                        color = Color.White.copy(alpha = 0.9f),
                                        style = MaterialTheme.typography.bodyLarge,
                                        lineHeight = 24.sp
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            if (!showArtistDetail && !uiState.artistInfo.isNullOrBlank() && !uiState.isFetchingAlbumInfo) {
                                TextButton(onClick = { showArtistDetail = true }) {
                                    Text("Learn about Artist")
                                }
                            }
                            if (showArtistDetail) {
                                TextButton(onClick = { showArtistDetail = false }) {
                                    Text("Back to Album")
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { 
                                    showAlbumInfo = false 
                                    showArtistDetail = false
                                }
                            ) {
                                Text("Close")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LandscapeLeftControlPanel(
    uiState: PlayerUiState,
    viewModel: PlayerViewModel,
    onAlbumClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 1. Cover - Maximize space
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f) // Take all available space
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val size = minOf(maxHeight, maxWidth)
            
            Card(
                modifier = Modifier
                    .size(size)
                    .aspectRatio(1f),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                if (uiState.coverUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(uiState.coverUri)
                            .apply {
                                val auth = WebDavAuthManager.authHeader
                                if (auth != null && uiState.coverUri.toString().startsWith("http")) {
                                    addHeader("Authorization", auth)
                                }
                            }
                            .crossfade(true)
                            .build(),
                        contentDescription = "Album Cover",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color(0xFF333333)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(80.dp), tint = Color.White.copy(alpha = 0.2f))
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        // 2. Info - Compact
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
             TextCompressed(
                text = uiState.currentTitle,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            if (uiState.currentFolderName.isNotEmpty()) {
                TextCompressed(
                    text = uiState.currentFolderName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    modifier = Modifier.clickable { onAlbumClick() }
                )
                 // Audio Info
                if (uiState.audioInfo.isNotEmpty()) {
                    Text(
                        text = uiState.audioInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        // 3. Progress
        Column(modifier = Modifier.fillMaxWidth()) {
             var sliderPosition by remember { mutableStateOf(0f) }
             val actualPosition = if (uiState.duration > 0) uiState.currentPosition.toFloat() / uiState.duration else 0f
             val bufferedFraction = if (uiState.duration > 0) uiState.bufferedPosition.toFloat() / uiState.duration else 0f
             
             LaunchedEffect(uiState.currentPosition) {
                 sliderPosition = actualPosition
             }
             
             val infiniteTransition = rememberInfiniteTransition(label = "buffering")
             val spinnerRotation by infiniteTransition.animateFloat(
                 initialValue = 0f,
                 targetValue = 360f,
                 animationSpec = infiniteRepeatable(
                     animation = tween(1000, easing = LinearEasing),
                     repeatMode = RepeatMode.Restart
                 ),
                 label = "spinnerRotation"
             )

             Box(modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(4.dp).align(Alignment.Center).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.2f)))
                
                if (bufferedFraction > 0) {
                     Box(Modifier.fillMaxWidth(bufferedFraction.coerceIn(0f, 1f)).height(4.dp).align(Alignment.CenterStart).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.35f)))
                }
                
                Box(Modifier.fillMaxWidth(sliderPosition.coerceIn(0f, 1f)).height(4.dp).align(Alignment.CenterStart).clip(RoundedCornerShape(2.dp)).background(Color.White))
                
                 BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val newPos = (offset.x / size.width).coerceIn(0f, 1f)
                                sliderPosition = newPos
                                val newPosition = (newPos * uiState.duration).toLong()
                                viewModel.seekTo(newPosition)
                            }
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    val newPosition = (sliderPosition * uiState.duration).toLong()
                                    viewModel.seekTo(newPosition)
                                }
                            ) { change, _ ->
                                val newPos = (change.position.x / size.width).coerceIn(0f, 1f)
                                sliderPosition = newPos
                            }
                        }
                ) {
                    val thumbSize = if (uiState.isBuffering) 16.dp else 12.dp
                    val availableWidth = maxWidth - thumbSize
                    val thumbOffset = availableWidth * sliderPosition
                    
                    Box(modifier = Modifier.padding(start = thumbOffset.coerceAtLeast(0.dp)).align(Alignment.CenterStart)) {
                         if (uiState.isBuffering) {
                             Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .graphicsLayer { rotationZ = spinnerRotation }
                                    .drawBehind {
                                        drawArc(Color.White.copy(alpha = 0.6f), 0f, 270f, false, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                                    }
                            )
                         } else {
                             Box(Modifier.size(12.dp).clip(CircleShape).background(Color.White))
                         }
                    }
                }
             }

             Row(
                 modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                 horizontalArrangement = Arrangement.SpaceBetween
             ) {
                 Text(formatTimeLs(uiState.currentPosition), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                 Text(formatTimeLs(uiState.duration), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
             }
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        // 4. Controls
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.previous() }) {
                Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }

            Surface(
                onClick = { viewModel.playPause() },
                shape = CircleShape,
                color = Color.White,
                modifier = Modifier.size(72.dp),
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            IconButton(onClick = { viewModel.next() }) {
                Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }
    }
}

@Composable
fun LandscapeLyricsPanel(
    uiState: PlayerUiState,
    onDoubleTap: () -> Unit
) {
    if (uiState.lyrics.isNotEmpty()) {
        val listState = rememberLazyListState()
        
        LaunchedEffect(uiState.currentLyricIndex) {
            if (uiState.currentLyricIndex >= 0 && uiState.currentLyricIndex < uiState.lyrics.size) {
                listState.animateScrollToItem(
                    index = uiState.currentLyricIndex,
                    scrollOffset = 0 
                )
            }
        }
        
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val viewHeight = maxHeight
            
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(vertical = viewHeight / 2 - 20.dp)
            ) {
                itemsIndexed(uiState.lyrics) { index, lyric ->
                    val isCurrentLine = index == uiState.currentLyricIndex
                    val colorAlpha by animateFloatAsState(targetValue = if (isCurrentLine) 0.9f else 0.4f, label = "alpha")
                    val scale by animateFloatAsState(targetValue = if (isCurrentLine) 1.2f else 1f, label = "scale")

                    val isLongLyric = lyric.text.length > 40
                    val minLineHeight by animateDpAsState(
                        targetValue = when {
                            isCurrentLine && isLongLyric -> 56.dp
                            isCurrentLine -> 40.dp
                            else -> 32.dp
                        },
                        animationSpec = tween(durationMillis = 200),
                        label = "height"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = minLineHeight)
                            .wrapContentHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = lyric.text,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = colorAlpha),
                            fontWeight = if (isCurrentLine) FontWeight.ExtraBold else FontWeight.Normal,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = if (isCurrentLine) 3 else 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                        )
                    }
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No lyrics available", color = Color.White.copy(alpha = 0.3f))
        }
    }
}

fun formatTimeLs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
