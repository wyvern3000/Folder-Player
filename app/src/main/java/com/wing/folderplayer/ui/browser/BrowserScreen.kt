package com.wing.folderplayer.ui.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wing.folderplayer.data.source.MusicFile
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BrowserScreen(
    onFolderPlay: (SourceConfig, String, String?) -> Unit,
    onCustomPlay: (SourceConfig, List<MusicFile>, Int) -> Unit,
    onCuePlay: (SourceConfig, String) -> Unit,
    allPlaylists: List<com.wing.folderplayer.data.playlist.Playlist> = emptyList(),
    onAddToPlaylist: (String, List<MusicFile>) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    viewModel: BrowserViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollTrigger by remember { derivedStateOf { uiState.scrollTrigger } }
    
    var showAddDialog by remember { mutableStateOf(false) }
    var sourceToEdit by remember { mutableStateOf<SourceConfig?>(null) }
    
    // 1. Back Navigation: Folder Up instead of Minimize
    androidx.activity.compose.BackHandler(enabled = true) {
        if (uiState.currentPath != "ROOT") {
            viewModel.navigateUp()
        } else {
            onBack()
        }
    }
    
    if (showAddDialog) {
        AddWebDavDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, url, path, user, pass ->
                viewModel.addWebDavSource(name, url, path, user, pass)
                showAddDialog = false
            }
        )
    }

    if (sourceToEdit != null) {
        AddWebDavDialog(
            initialSource = sourceToEdit,
            onDismiss = { sourceToEdit = null },
            onConfirm = { name, url, path, user, pass ->
                viewModel.editWebDavSource(sourceToEdit!!.id, name, url, path, user, pass)
                sourceToEdit = null
            }
        )
    }

    if (uiState.selectedFileForPlaylist != null) {
        PlaylistSelectBottomSheet(
            playlists = allPlaylists,
            onPlaylistSelected = { playlistId ->
                onAddToPlaylist(playlistId, listOf(uiState.selectedFileForPlaylist!!))
                viewModel.onAddToPlaylistDone()
            },
            onDismiss = { viewModel.closePlaylistDialog() }
        )
    }

    val horizontalSafePadding = WindowInsets.displayCutout.asPaddingValues().let { 
        val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
        maxOf(it.calculateLeftPadding(layoutDirection), it.calculateRightPadding(layoutDirection))
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = horizontalSafePadding)
        ) {
            Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (uiState.currentPath == "ROOT") "Music Sources" 
                        else {
                            val lastPart = uiState.currentPath.trimEnd('/').substringAfterLast('/')
                            val decoded = try { java.net.URLDecoder.decode(lastPart, "UTF-8") } catch(e: Exception) { lastPart }
                            "${uiState.currentSource?.name ?: "Source"} > $decoded"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        if (uiState.currentPath != "ROOT") {
                            viewModel.exitSource()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to Sources")
                    }
                },
                actions = {
                    if (uiState.currentPath == "ROOT") {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Source")
                        }
                    } else {
                        IconButton(onClick = { viewModel.shufflePlay(onFolderPlay, onCustomPlay) }) {
                            Icon(Icons.Default.Shuffle, contentDescription = "Shuffle Play")
                        }
                        IconButton(onClick = { viewModel.navigateUp() }) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Up one level")
                        }
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        }
    ) { padding ->
        val listState = rememberLazyListState()
        val scrollbarColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)

        // Save scroll position
        LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
            viewModel.saveScrollPosition(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
        
        // Restore scroll position
        LaunchedEffect(scrollTrigger) {
            if (scrollTrigger > 0) {
                listState.scrollToItem(uiState.scrollToIndex, uiState.scrollToOffset)
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (uiState.currentPath != "ROOT") {
                SortHeader(
                    currentField = uiState.sortField,
                    ascending = uiState.sortAscending,
                    onSortClick = { viewModel.sortFiles(it) }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                if (uiState.currentPath == "ROOT") {
                    SourceList(
                        sources = uiState.availableSources,
                        onSourceClick = { viewModel.selectSource(it) },
                        onEditSource = { sourceToEdit = it },
                        onDeleteSource = { viewModel.removeSource(it.id) },
                        onDuplicateSource = { viewModel.duplicateWebDavSource(it.id) },
                        onMoveUp = { viewModel.moveSourceUp(it.id) },
                        onMoveDown = { viewModel.moveSourceDown(it.id) }
                    )
                } else {
                    FileList(
                        files = uiState.files,
                        currentlyPlayingPath = uiState.currentlyPlayingPath,
                        onFileClick = { viewModel.onFileClicked(it, onFolderPlay, onCustomPlay, onCuePlay) },
                        onFileLongClick = { viewModel.onFileLongClick(it) },
                        modifier = Modifier.drawWithContent {
                            drawContent()
                            val first = listState.firstVisibleItemIndex
                            val total = uiState.files.size
                            if (total > 0) {
                                val height = size.height * (listState.layoutInfo.visibleItemsInfo.size.toFloat() / total)
                                val offset = size.height * (first.toFloat() / total)
                                drawRect(scrollbarColor, Offset(size.width - 4.dp.toPx(), offset), androidx.compose.ui.geometry.Size(4.dp.toPx(), height))
                            }
                        },
                        listState = listState
                    )
                }
            }
        }
    }
}
}
}

@Composable
fun SortHeader(currentField: String, ascending: Boolean, onSortClick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf("NAME" to "Name", "DATE" to "Date", "SIZE" to "Size").forEach { (field, label) ->
            val isSelected = currentField == field
            Surface(
                onClick = { onSortClick(field) },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge)
                    if (isSelected) {
                        Icon(
                            if (ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp).padding(start = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddWebDavDialog(
    initialSource: SourceConfig? = null,
    onDismiss: () -> Unit, 
    onConfirm: (String, String, String?, String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialSource?.name ?: "My NAS") }
    var url by remember { mutableStateOf(initialSource?.url ?: "http://192.168.2.10:5244/dav") }
    var path by remember { mutableStateOf(initialSource?.path ?: "") }
    var user by remember { mutableStateOf(initialSource?.username ?: "") }
    var pass by remember { mutableStateOf(initialSource?.password ?: "") }
    var passVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialSource == null) "Add WebDAV Source" else "Edit Source") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, 
                    onValueChange = { name = it }, 
                    label = { Text("Source Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = url, 
                    onValueChange = { url = it }, 
                    label = { Text("Server URL") },
                    placeholder = { Text("http://192.168.x.x:5244/dav") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = path, 
                    onValueChange = { path = it }, 
                    label = { Text("Specific Path (Optional)") },
                    placeholder = { Text("e.g. /Music/Pop") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = user, 
                    onValueChange = { user = it }, 
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = pass, 
                    onValueChange = { pass = it }, 
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    trailingIcon = {
                        IconButton(onClick = { passVisible = !passVisible }) {
                            Icon(
                                if (passVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, url, path.ifBlank { null }, user, pass) }) {
                Text(if (initialSource == null) "Add" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SourceList(
    sources: List<SourceConfig>, 
    onSourceClick: (SourceConfig) -> Unit, 
    onEditSource: (SourceConfig) -> Unit,
    onDeleteSource: (SourceConfig) -> Unit,
    onDuplicateSource: (SourceConfig) -> Unit,
    onMoveUp: (SourceConfig) -> Unit,
    onMoveDown: (SourceConfig) -> Unit
) {
    LazyColumn {
        items(sources) { source ->
            var showMenu by remember { mutableStateOf(false) }
            
            Box {
                ListItem(
                    headlineContent = { Text(source.name) },
                    supportingContent = { Text(source.url) },
                    leadingContent = { Icon(if (source.type == SourceType.LOCAL) Icons.Default.Folder else Icons.Default.Cloud, contentDescription = null) },
                    modifier = Modifier.combinedClickable(
                        onClick = { onSourceClick(source) },
                        onLongClick = { if (source.type == SourceType.WEBDAV) showMenu = true }
                    )
                )
                
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") }, 
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { onEditSource(source); showMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate") }, 
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = { onDuplicateSource(source); showMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Move Up") }, 
                        leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) },
                        onClick = { onMoveUp(source); showMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Move Down") }, 
                        leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) },
                        onClick = { onMoveDown(source); showMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, 
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { onDeleteSource(source); showMenu = false }
                    )
                }
            }
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FileList(
    files: List<MusicFile>,
    currentlyPlayingPath: String?,
    onFileClick: (MusicFile) -> Unit,
    onFileLongClick: (MusicFile) -> Unit = {},
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    
    LazyColumn(state = listState, modifier = modifier) {
        items(files) { file ->
            val isPlaying = currentlyPlayingPath == file.path
            
            // Highlight folder if it contains the playing song
            val isParentOfPlaying = remember(file.path, currentlyPlayingPath) {
                file.isDirectory && currentlyPlayingPath != null && 
                currentlyPlayingPath.startsWith(file.path.trimEnd('/'))
            }

            val isUnsupported = !file.isDirectory && !isMusic(file.name)
            
            Column {
                Box(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { 
                            Text(
                                file.name, 
                                color = when {
                                    isPlaying -> MaterialTheme.colorScheme.primary
                                    isParentOfPlaying -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) // Highlight parent folder
                                    isUnsupported -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    else -> Color.Unspecified
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (isPlaying || isParentOfPlaying) androidx.compose.ui.text.font.FontWeight.Bold else null
                            ) 
                        },
                        supportingContent = { 
                            if (!file.isDirectory) {
                                Text(
                                    "${formatSize(file.size)} • ${file.name.substringAfterLast('.', "").uppercase()}",
                                    color = if (isUnsupported) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else Color.Unspecified
                                )
                            }
                        },
                        leadingContent = { 
                            val icon = when {
                                isPlaying -> Icons.Default.PlayCircleFilled
                                file.isDirectory -> Icons.Default.Folder
                                file.name.endsWith(".cue", ignoreCase = true) -> Icons.Default.Description
                                isMusic(file.name) -> Icons.Default.MusicNote
                                else -> Icons.Default.InsertDriveFile // Generic file icon
                            }
                            
                            val tint = when {
                                isPlaying || isParentOfPlaying -> MaterialTheme.colorScheme.primary
                                isUnsupported -> LocalContentColor.current.copy(alpha = 0.3f)
                                else -> LocalContentColor.current
                            }
                            
                            Icon(icon, contentDescription = null, tint = tint)
                        },
                        modifier = Modifier.combinedClickable(
                            enabled = !isUnsupported,
                            onClick = { onFileClick(file) },
                            onLongClick = { onFileLongClick(file) }
                        )
                    )
                    
                    // Modification Date at bottom-right
                    if (file.lastModified > 0) {
                        Text(
                            text = dateFormatter.format(Date(file.lastModified)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isUnsupported) 0.2f else 0.4f),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 4.dp)
                        )
                    }
                }
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
        }
    }
}

private fun isMusic(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext in listOf("mp3", "flac", "m4a", "wav", "ogg", "aac", "opus", "ape", "cue", "dsf", "dff")
}

fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSelectBottomSheet(
    playlists: List<com.wing.folderplayer.data.playlist.Playlist>,
    onPlaylistSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Add to Playlist",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )
            LazyColumn {
                items(playlists) { playlist ->
                    ListItem(
                        headlineContent = { Text(playlist.name) },
                        leadingContent = { 
                            Icon(
                                if (playlist.id == "default") Icons.Default.Audiotrack else Icons.Default.PlaylistPlay, 
                                contentDescription = null
                            ) 
                        },
                        modifier = Modifier.clickable { onPlaylistSelected(playlist.id) }
                    )
                }
            }
        }
    }
}
