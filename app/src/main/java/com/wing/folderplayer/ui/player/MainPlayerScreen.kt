package com.wing.folderplayer.ui.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AccessAlarm
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wing.folderplayer.R
import androidx.media3.common.Player
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import android.content.res.Configuration

import androidx.compose.ui.platform.LocalConfiguration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainPlayerScreen(
    viewModel: PlayerViewModel = viewModel()
) {
    val configuration = LocalConfiguration.current
    if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        val uiState by viewModel.uiState.collectAsState()
        val context = LocalContext.current
        LaunchedEffect(Unit) {
            viewModel.initializeController(context)
        }
        LandscapePlayerLayout(viewModel = viewModel, uiState = uiState)
    } else {
        PortraitPlayerLayout(viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortraitPlayerLayout(
    viewModel: PlayerViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showPlaylist by remember { mutableStateOf(false) }
    var lyricsExpanded by remember { mutableStateOf(false) }
    var showAlbumInfo by remember { mutableStateOf(false) }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    
    // Animation targets for Large Lyrics mode
    val coverWidthFraction by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (lyricsExpanded) 0.3f else 1.0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 500)
    )
    
    val rootPaddingTop by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (lyricsExpanded) 12.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 500)
    )
    
    val infoLyricsSpacer by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (lyricsExpanded) 2.dp else 24.dp,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 500)
    )
    
    val controlsScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (lyricsExpanded) 0.8f else 1.0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 500)
    )
    
    val controlsPaddingBottom by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (lyricsExpanded) 8.dp else 24.dp,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 500)
    )

    LaunchedEffect(Unit) {
        viewModel.initializeController(context)
    }

    // Dynamic background based on cover (Enhanced Gradient)
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
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        // Detect upward swipe with a threshold
                        if (dragAmount < -30) {
                            showPlaylist = true
                        }
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(top = rootPaddingTop),
                horizontalAlignment = Alignment.CenterHorizontally,
                // No SpaceBetween to allow tight packing at the top
            ) {
                // Top Bar Removed as requested

                // Album Cover with shadow and borders
                val isLargeCover = uiState.coverDisplaySize == "LARGE"
                
                // Unified Animation targets for cover properties
                val horizontalPaddingTarget = if (lyricsExpanded || isLargeCover) 0.dp else 36.dp
                val topPaddingTarget = if (lyricsExpanded) 4.dp else (if (isLargeCover) 0.dp else 32.dp)
                val bottomPaddingTarget = if (lyricsExpanded) 0.dp else (if (isLargeCover) 0.dp else 24.dp)
                val cornerSizeTarget = if (lyricsExpanded) 8.dp else (if (isLargeCover) 0.dp else 28.dp)
                
                val horizontalPadding by androidx.compose.animation.core.animateDpAsState(
                    targetValue = horizontalPaddingTarget, 
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 500)
                )
                val animatedTopPadding by androidx.compose.animation.core.animateDpAsState(
                    targetValue = topPaddingTarget,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 500)
                )
                val animatedBottomPadding by androidx.compose.animation.core.animateDpAsState(
                    targetValue = bottomPaddingTarget,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 500)
                )
                val animatedCornerSize by androidx.compose.animation.core.animateDpAsState(
                    targetValue = cornerSizeTarget,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 500)
                )
                
                val elevationTarget = if (isLargeCover || lyricsExpanded) 0.dp else 24.dp
                val borderAlphaTarget = if (isLargeCover || lyricsExpanded) 0f else 0.15f
                
                val animatedElevation by androidx.compose.animation.core.animateDpAsState(
                    targetValue = elevationTarget,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 500)
                )
                val animatedBorderAlpha by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = borderAlphaTarget,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 500)
                )
                
                val maxCoverSize = screenHeight * 0.6f

                Box(
                    modifier = Modifier
                        .width(maxCoverSize * coverWidthFraction) // Dynamically resize the container!
                        .padding(top = animatedTopPadding, bottom = animatedBottomPadding)
                        .padding(horizontal = horizontalPadding),
                    contentAlignment = Alignment.Center
                ) {
                     // Shadow logic - simplified: only show in Standard
                    if (!isLargeCover && !lyricsExpanded) {
                         Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .padding(12.dp) // Slight offset for shadow effect
                                .clip(RoundedCornerShape(animatedCornerSize))
                                .background(Color.White.copy(alpha = 0.05f))
                        )
                    }
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        shape = RoundedCornerShape(animatedCornerSize),
                        elevation = CardDefaults.cardElevation(defaultElevation = animatedElevation),
                        border = if (animatedBorderAlpha > 0.01f) androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = animatedBorderAlpha)) else null
                    ) {
                        if (uiState.coverUri != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(uiState.coverUri)
                                    .apply {
                                        val auth = com.wing.folderplayer.data.source.WebDavAuthManager.authHeader
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
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF333333), Color(0xFF111111))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(80.dp),
                                    tint = Color.White.copy(alpha = 0.2f)
                                )
                            }
                        }
                    }
                }

                // Song Info & Lyrics - This Column now takes all remaining space
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Song Details Box (Shrinks its padding in expanded mode)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = 0.dp)
                    ) {
                        TextCompressed(
                            text = uiState.currentTitle,
                            style = if (lyricsExpanded) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        
                        if (uiState.currentFolderName.isNotEmpty()) {
                            TextCompressed(
                                text = uiState.currentFolderName,
                                style = if (lyricsExpanded) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { 
                                    showAlbumInfo = true
                                    viewModel.fetchAlbumInfo()
                                }
                            )
                            
                            if (uiState.audioInfo.isNotEmpty() && !lyricsExpanded) {
                                Text(
                                    text = uiState.audioInfo,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(infoLyricsSpacer))
                    
                    // Glassmorphism Lyrics Display - Fills the rest of the weighted Column
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f) // TAKE ALL REMAINING SPACE
                            .padding(8.dp)
                            .pointerInput(uiState.lyrics) {
                                detectTapGestures(
                                    onDoubleTap = { 
                                        if (uiState.lyrics.isNotEmpty() || lyricsExpanded) {
                                            lyricsExpanded = !lyricsExpanded 
                                        }
                                    }
                                )
                            }
                    ) {
                        val viewHeight = maxHeight
                        
                        if (uiState.lyrics.isNotEmpty()) {
                            val listState = rememberLazyListState()
                            
                            LaunchedEffect(uiState.currentLyricIndex) {
                                if (uiState.currentLyricIndex >= 0 && uiState.currentLyricIndex < uiState.lyrics.size) {
                                    // Calculate offset based on current height
                                    listState.animateScrollToItem(
                                        index = uiState.currentLyricIndex,
                                        scrollOffset = 0
                                    )
                                }
                            }
                            
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                contentPadding = PaddingValues(vertical = (viewHeight / 2 - 16.dp)) 
                            ) {
                                itemsIndexed(uiState.lyrics) { index, lyric ->
                                    val isCurrentLine = index == uiState.currentLyricIndex
                                    val colorAlpha by androidx.compose.animation.core.animateFloatAsState(
                                        targetValue = if (isCurrentLine) 0.8f else 0.4f,
                                        label = "lyricAlpha"
                                    )
                                    val scale by androidx.compose.animation.core.animateFloatAsState(
                                        targetValue = if (isCurrentLine) 1.2f else 1f,
                                        label = "lyricScale"
                                    )

                                    // Dynamic height based on lyric length and current line status
                                    val isLongLyric = lyric.text.length > 40
                                    val minLineHeight by androidx.compose.animation.core.animateDpAsState(
                                        targetValue = when {
                                            isCurrentLine && isLongLyric -> 56.dp  // Current line + long lyric
                                            isCurrentLine -> 40.dp                  // Current line + normal lyric
                                            else -> 32.dp                           // Non-current line
                                        },
                                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
                                        label = "lineHeight"
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = minLineHeight)
                                            .wrapContentHeight(),  // Allow height to grow naturally
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = lyric.text,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White.copy(alpha = colorAlpha),
                                            fontWeight = if (isCurrentLine) FontWeight.ExtraBold else FontWeight.Normal,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            maxLines = if (isCurrentLine) 3 else 2,  // Allow more lines for current
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .padding(horizontal = 16.dp)  // Horizontal padding to prevent overflow when scaled
                                                .graphicsLayer {
                                                    scaleX = scale
                                                    scaleY = scale
                                                }
                                        )
                                    }
                                }
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "No lyrics available", 
                                    color = Color.White.copy(alpha = 0.3f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Progress Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    var sliderPosition by remember { mutableStateOf(0f) }
                    val actualPosition = if (uiState.duration > 0) uiState.currentPosition.toFloat() / uiState.duration else 0f
                    val bufferedFraction = if (uiState.duration > 0) uiState.bufferedPosition.toFloat() / uiState.duration else 0f
                    
                    LaunchedEffect(uiState.currentPosition) {
                        sliderPosition = actualPosition
                    }
                    
                    // Buffering spinner rotation
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
                        // Background track
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .align(Alignment.Center)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.2f))
                        )
                        
                        // Buffered track (thicker)
                        if (bufferedFraction > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(bufferedFraction.coerceIn(0f, 1f))
                                    .height(6.dp)
                                    .align(Alignment.CenterStart)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color.White.copy(alpha = 0.35f))
                            )
                        }
                        
                        // Played track
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(sliderPosition.coerceIn(0f, 1f))
                                .height(4.dp)
                                .align(Alignment.CenterStart)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White)
                        )
                        
                        // Thumb with buffering spinner - using BoxWithConstraints
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
                            val thumbSize = if (uiState.isBuffering) 24.dp else 12.dp
                            val availableWidth = maxWidth - thumbSize
                            val thumbOffset = availableWidth * sliderPosition
                            
                            Box(
                                modifier = Modifier
                                    .padding(start = thumbOffset.coerceAtLeast(0.dp))
                                    .align(Alignment.CenterStart)
                            ) {
                                // Spinning ring when buffering
                                if (uiState.isBuffering) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .graphicsLayer { rotationZ = spinnerRotation }
                                            .drawBehind {
                                                drawArc(
                                                    color = Color.White.copy(alpha = 0.6f),
                                                    startAngle = 0f,
                                                    sweepAngle = 270f,
                                                    useCenter = false,
                                                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(Color.White)
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(uiState.currentPosition),
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = formatTime(uiState.duration),
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = controlsPaddingBottom)
                        .graphicsLayer {
                            scaleX = controlsScale
                            scaleY = controlsScale
                        },
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.previous() }, modifier = Modifier.size(56.dp)) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Surface(
                        onClick = { viewModel.playPause() },
                        shape = CircleShape,
                        color = Color.White,
                        modifier = Modifier.size(80.dp),
                        shadowElevation = 8.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.Black,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    IconButton(onClick = { viewModel.next() }, modifier = Modifier.size(56.dp)) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }

        // Playlist BottomSheet
        if (showPlaylist) {
            ModalBottomSheet(
                onDismissRequest = { showPlaylist = false },
                containerColor = Color(0xFF1A1A1A),
                scrimColor = Color.Black.copy(alpha = 0.7f)
            ) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp).fillMaxHeight(0.6f)) {
                    Text(
                        "PLAYLIST",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 0.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Playlist Selector Dropdown
                        var expanded by remember { mutableStateOf(false) }
                        var showCreateDialog by remember { mutableStateOf(false) }
                        var playlistToRename by remember { mutableStateOf<String?>(null) }
                        var renameName by remember { mutableStateOf("") }
                        var createName by remember { mutableStateOf("") }
                        var showTimerDialog by remember { mutableStateOf(false) }

                        if (showTimerDialog) {
                            TimerDialog(
                                uiState = uiState,
                                onStart = { type, value -> viewModel.startSleepTimer(type, value) },
                                onReset = { viewModel.resetSleepTimer() },
                                onDismiss = { showTimerDialog = false }
                            )
                        }

                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { expanded = true }.padding(top = 0.dp, bottom = 4.dp)
                            ) {
                                Text(
                                    uiState.activePlaylistName.uppercase(),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.background(Color(0xFF2A2A2A))
                            ) {
                                uiState.allPlaylists.forEach { playlist ->
                                    DropdownMenuItem(
                                        text = { 
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                                Text(playlist.name, modifier = Modifier.weight(1f), color = Color.White)
                                                if (playlist.id != "default") {
                                                    IconButton(onClick = { 
                                                        playlistToRename = playlist.id
                                                        renameName = playlist.name
                                                        expanded = false
                                                    }, modifier = Modifier.size(24.dp)) {
                                                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                                                    }
                                                    IconButton(onClick = { 
                                                        viewModel.deletePlaylist(playlist.id)
                                                    }, modifier = Modifier.size(24.dp)) {
                                                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.switchPlaylist(playlist.id)
                                            expanded = false
                                        }
                                    )
                                }
                                if (uiState.allPlaylists.size < 11) {
                                    Divider(color = Color.White.copy(alpha = 0.1f))
                                    DropdownMenuItem(
                                        text = { Text("+ NEW LIST", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) },
                                        onClick = {
                                            showCreateDialog = true
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Dialogs
                        if (showCreateDialog) {
                            AlertDialog(
                                onDismissRequest = { showCreateDialog = false },
                                title = { Text("Create Playlist") },
                                text = {
                                    OutlinedTextField(
                                        value = createName,
                                        onValueChange = { createName = it },
                                        label = { Text("Name") },
                                        singleLine = true
                                    )
                                },
                                confirmButton = {
                                    Button(onClick = {
                                        if (createName.isNotBlank()) {
                                            viewModel.createPlaylist(createName)
                                            createName = ""
                                            showCreateDialog = false
                                        }
                                    }) { Text("Create") }
                                },
                                dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") } }
                            )
                        }

                        if (playlistToRename != null) {
                            AlertDialog(
                                onDismissRequest = { playlistToRename = null },
                                title = { Text("Rename Playlist") },
                                text = {
                                    OutlinedTextField(
                                        value = renameName,
                                        onValueChange = { renameName = it },
                                        label = { Text("New Name") },
                                        singleLine = true
                                    )
                                },
                                confirmButton = {
                                    Button(onClick = {
                                        if (renameName.isNotBlank()) {
                                            viewModel.renamePlaylist(playlistToRename!!, renameName)
                                            playlistToRename = null
                                        }
                                    }) { Text("Rename") }
                                },
                                dismissButton = { TextButton(onClick = { playlistToRename = null }) { Text("Cancel") } }
                            )
                        }
                        
                        Row {
                            IconButton(onClick = { showTimerDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.AccessAlarm,
                                    contentDescription = "Sleep Timer",
                                    tint = if (uiState.sleepTimerActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(onClick = { viewModel.toggleAutoNextFolder() }) {
                                Icon(
                                    imageVector = Icons.Default.AllInclusive,
                                    contentDescription = "Sequential Folder Playback",
                                    tint = if (uiState.autoNextFolder) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(onClick = { viewModel.toggleShuffle() }) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = "Shuffle",
                                    tint = if (uiState.shuffleModeEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(onClick = { viewModel.toggleRepeatMode() }) {
                                Icon(
                                    imageVector = when(uiState.repeatMode) {
                                        Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                        else -> Icons.Default.Repeat
                                    },
                                    contentDescription = "Repeat",
                                    tint = if (uiState.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp) // Increased spacing
                    ) {
                        itemsIndexed(uiState.activePlaylistItems, key = { index, item -> "${uiState.activePlaylistId}_${item.path}_$index" }) { index, item ->
                            val isCurrent = uiState.currentMediaId == item.path
                            val title = item.title
                            val artist = item.artist ?: ""

                            val dismissState = rememberDismissState(
                                confirmValueChange = {
                                    if (it == DismissValue.DismissedToStart) {
                                        viewModel.removeFromActivePlaylist(index)
                                        true
                                    } else false
                                }
                            )

                            SwipeToDismiss(
                                state = dismissState,
                                directions = setOf(DismissDirection.EndToStart), // Right to Left
                                background = {
                                    val color = when (dismissState.dismissDirection) {
                                        DismissDirection.EndToStart -> Color.Red.copy(alpha = 0.3f)
                                        else -> Color.Transparent
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(color)
                                    )
                                },
                                dismissContent = {
                                    Surface(
                                        color = if (dismissState.dismissDirection != null) Color.Transparent else Color.Transparent,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.playPlaylistSong(uiState.activePlaylistId, index)
                                                    showPlaylist = false
                                                }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Track Number / Playing Icon (Centered with first line)
                                            Box(
                                                modifier = Modifier.width(40.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isCurrent) {
                                                    Icon(
                                                        Icons.Default.PlayArrow, 
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                } else {
                                                    Text(
                                                        text = (index + 1).toString(),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color.White.copy(alpha = 0.3f)
                                                    )
                                                }
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (artist.isNotEmpty()) {
                                                    Text(
                                                        text = artist,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Gemini Album Info Dialog
    if (showAlbumInfo) {
        var showArtistDetail by remember { mutableStateOf(false) }
        
        AlertDialog(
            onDismissRequest = { 
                showAlbumInfo = false
                showArtistDetail = false
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (showArtistDetail) Icons.Default.MusicNote else Icons.Default.MusicNote, 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.primary, 
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        if (showArtistDetail) "Artist Discovery" else "Album Discovery", 
                        style = MaterialTheme.typography.headlineSmall, 
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (showArtistDetail) uiState.currentArtist else uiState.currentFolderName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    if (uiState.isFetchingAlbumInfo) {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    } else {
                        val displayInfo = if (showArtistDetail) uiState.artistInfo else uiState.albumInfo
                        
                        Text(
                            text = displayInfo ?: "No information available.",
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp
                        )
                    }
                }
            },
            dismissButton = null,
            confirmButton = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (!showArtistDetail && !uiState.artistInfo.isNullOrBlank() && !uiState.isFetchingAlbumInfo) {
                        TextButton(onClick = { showArtistDetail = true }) {
                            Text("Learn about Artist")
                        }
                    } else {
                        // Spacer to keep Close button at the end if the left button is missing
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    
                    TextButton(onClick = { 
                        showAlbumInfo = false
                        showArtistDetail = false
                    }) {
                        Text("Close")
                    }
                }
            },
            containerColor = Color(0xFF1E1E1E),
            textContentColor = Color.White,
            titleContentColor = Color.White
        )
    }
}



fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
fun TextCompressed(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(1f) }
    var readyToDraw by remember { mutableStateOf(false) }

    val mergedStyle = style.merge(
        androidx.compose.ui.text.TextStyle(
            color = color,
            fontWeight = fontWeight,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    )

    androidx.compose.ui.layout.Layout(
        content = {
            Text(
                text = text,
                style = mergedStyle,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                }
            )
        },
        modifier = modifier.fillMaxWidth()
    ) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints.copy(minWidth = 0, maxWidth = Int.MAX_VALUE))
        
        val contentWidth = placeable.width.toFloat()
        val containerWidth = constraints.maxWidth.toFloat()
        
        if (contentWidth > containerWidth) {
            scale = containerWidth / contentWidth
        } else {
            scale = 1f
        }
        readyToDraw = true

        layout(constraints.maxWidth, placeable.height) {
            val xOffset = ((constraints.maxWidth - placeable.width) / 2)
            placeable.place(xOffset, 0)
        }
    }
}
