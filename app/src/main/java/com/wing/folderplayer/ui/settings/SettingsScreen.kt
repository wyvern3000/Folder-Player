package com.wing.folderplayer.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.wing.folderplayer.R
import com.wing.folderplayer.data.prefs.LyricPreferences
import com.wing.folderplayer.data.prefs.OrientationPreferences
import com.wing.folderplayer.data.prefs.NotchPreferences
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    currentCoverSize: String,
    onCoverSizeChange: (String) -> Unit,
    onOrientationChange: (String) -> Unit = {},  // Callback to notify MainActivity
    onNotchModeChange: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lyricPrefs = remember { LyricPreferences(context) }
    var lyricApiUrl by remember { mutableStateOf(lyricPrefs.getLyricApiUrl()) }
    var isDebug by remember { mutableStateOf(com.wing.folderplayer.utils.CrashHandler.isDebugEnabled(context)) }

    // Use horizontal safe insets to prevent UI shift and camera overlap
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
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // 1. Cover Display Size
            Text(
                text = "Cover Display Size",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = currentCoverSize == "STANDARD",
                    onClick = { onCoverSizeChange("STANDARD") },
                    label = { Text("Standard") }
                )
                FilterChip(
                    selected = currentCoverSize == "LARGE",
                    onClick = { onCoverSizeChange("LARGE") },
                    label = { Text("Large") }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            // Screen Orientation
            Text(
                text = "Screen Orientation",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            val orientationPrefs = remember { OrientationPreferences(context) }
            var currentOrientation by remember { mutableStateOf(orientationPrefs.getOrientation()) }
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = currentOrientation == OrientationPreferences.ORIENTATION_SYSTEM,
                    onClick = { 
                        currentOrientation = OrientationPreferences.ORIENTATION_SYSTEM
                        orientationPrefs.setOrientation(currentOrientation)
                        onOrientationChange(currentOrientation)
                    },
                    label = { Text("System") }
                )
                FilterChip(
                    selected = currentOrientation == OrientationPreferences.ORIENTATION_PORTRAIT,
                    onClick = { 
                        currentOrientation = OrientationPreferences.ORIENTATION_PORTRAIT
                        orientationPrefs.setOrientation(currentOrientation)
                        onOrientationChange(currentOrientation)
                    },
                    label = { Text("Portrait") }
                )
                FilterChip(
                    selected = currentOrientation == OrientationPreferences.ORIENTATION_LANDSCAPE,
                    onClick = { 
                        currentOrientation = OrientationPreferences.ORIENTATION_LANDSCAPE
                        orientationPrefs.setOrientation(currentOrientation)
                        onOrientationChange(currentOrientation)
                    },
                    label = { Text("Landscape") }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            // Notch Display Mode
            Text(
                text = "Notch Display",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            val notchPrefs = remember { NotchPreferences(context) }
            var currentNotchMode by remember { mutableStateOf(notchPrefs.getNotchMode()) }
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = currentNotchMode == NotchPreferences.NOTCH_FULLSCREEN,
                    onClick = { 
                        currentNotchMode = NotchPreferences.NOTCH_FULLSCREEN
                        notchPrefs.setNotchMode(currentNotchMode)
                        onNotchModeChange(currentNotchMode)
                    },
                    label = { Text("Fullscreen") }
                )
                FilterChip(
                    selected = currentNotchMode == NotchPreferences.NOTCH_BLACK_BAR,
                    onClick = { 
                        currentNotchMode = NotchPreferences.NOTCH_BLACK_BAR
                        notchPrefs.setNotchMode(currentNotchMode)
                        onNotchModeChange(currentNotchMode)
                    },
                    label = { Text("Black Bar") }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            // 2. Default File Sorting
            Text(
                text = "Default File Sorting",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            val sourcePrefs = remember { com.wing.folderplayer.data.prefs.SourcePreferences(context) }
            var defaultSort by remember { mutableStateOf(sourcePrefs.getDefaultSort()) }
            
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Sort By:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("NAME", "DATE", "SIZE").forEach { field ->
                        FilterChip(
                            selected = defaultSort.field == field,
                            onClick = { 
                                val newSort = defaultSort.copy(field = field)
                                defaultSort = newSort
                                sourcePrefs.saveDefaultSort(newSort.field, newSort.ascending)
                            },
                            label = { Text(field) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("Direction:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = defaultSort.ascending,
                        onClick = { 
                            val newSort = defaultSort.copy(ascending = true)
                            defaultSort = newSort
                            sourcePrefs.saveDefaultSort(newSort.field, newSort.ascending)
                        },
                        label = { Text("Ascending") }
                    )
                    FilterChip(
                        selected = !defaultSort.ascending,
                        onClick = { 
                            val newSort = defaultSort.copy(ascending = false)
                            defaultSort = newSort
                            sourcePrefs.saveDefaultSort(newSort.field, newSort.ascending)
                        },
                        label = { Text("Descending") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Lyrics Configuration
            Text(
                text = "Lyrics Configuration",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            OutlinedTextField(
                value = lyricApiUrl,
                onValueChange = { 
                    lyricApiUrl = it
                    lyricPrefs.setLyricApiUrl(it)
                },
                label = { Text("Lyric API URL") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))
            
            Text("AI Assistant (OpenAI Compatible)", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            
            var aiBaseUrl by remember { mutableStateOf(lyricPrefs.getAiBaseUrl()) }
            OutlinedTextField(
                value = aiBaseUrl,
                onValueChange = { 
                    aiBaseUrl = it
                    lyricPrefs.setAiBaseUrl(it)
                },
                label = { Text("API Base URL") },
                placeholder = { Text("https://api.openai.com/v1") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            var aiApiKey by remember { mutableStateOf(lyricPrefs.getAiApiKey()) }
            OutlinedTextField(
                value = aiApiKey,
                onValueChange = { 
                    aiApiKey = it
                    lyricPrefs.setAiApiKey(it)
                },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            var aiModel by remember { mutableStateOf(lyricPrefs.getAiModel()) }
            OutlinedTextField(
                value = aiModel,
                onValueChange = { 
                    aiModel = it
                    lyricPrefs.setAiModel(it)
                },
                label = { Text("Model Name") },
                placeholder = { Text("gpt-3.5-turbo") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))
            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Folder Player V0.5",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Copyright © 2026 Wyvern2000.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "All rights reserved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Build Date: 2026-2-11",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (isDebug) {
                         Text(
                             text = "(DEBUG MODE ACTIVATED - Logs in Download/FolderPlayer_Logs)",
                             style = MaterialTheme.typography.labelSmall,
                             color = MaterialTheme.colorScheme.error,
                             fontWeight = FontWeight.Bold,
                             modifier = Modifier.padding(top = 4.dp)
                         )
                    }
                    
                    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "https://github.com/wyvern3000/Folder-Player",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        softWrap = false,
                        modifier = Modifier.clickable {
                            uriHandler.openUri("https://github.com/wyvern3000/Folder-Player")
                        }
                    )
                }
                
                // App Icon with Secret Trigger
                var tapCount by remember { mutableStateOf(0) }
                
                androidx.compose.foundation.Image(
                    painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                    contentDescription = "App Icon",
                    modifier = Modifier
                        .size(100.dp)
                        .align(androidx.compose.ui.Alignment.TopEnd)
                        .graphicsLayer(scaleX = 1.5f, scaleY = 1.5f)
                        .offset(x = (-1).dp, y = 1.dp)
                        .clickable {
                            tapCount++
                            if (tapCount >= 2) {
                                tapCount = 0
                                // Toggle Debug
                                val newState = !isDebug
                                com.wing.folderplayer.utils.CrashHandler.setDebugEnabled(context, newState)
                                isDebug = newState
                            }
                        }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
}
}
