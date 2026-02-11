package com.wing.folderplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.wing.folderplayer.ui.theme.FolderPlayerTheme

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import com.wing.folderplayer.ui.player.MainPlayerScreen
import com.wing.folderplayer.ui.player.PlayerViewModel
import com.wing.folderplayer.ui.browser.BrowserScreen
import com.wing.folderplayer.ui.browser.SourceConfig
import com.wing.folderplayer.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

import android.content.pm.ActivityInfo
import com.wing.folderplayer.data.prefs.OrientationPreferences
import com.wing.folderplayer.data.prefs.NotchPreferences
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.LaunchedEffect

class MainActivity : ComponentActivity() {
    
    private fun applyOrientation(orientation: String) {
        requestedOrientation = when (orientation) {
            OrientationPreferences.ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            OrientationPreferences.ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    
    private fun applyNotchMode(mode: String) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes = window.attributes.also {
                // FIXED: Always allow display cutout so switching doesn't trigger a layout jump/resize
                it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            // Toggle visual "Black Bar" effect by changing status bar transparency
            window.statusBarColor = when (mode) {
                NotchPreferences.NOTCH_BLACK_BAR -> android.graphics.Color.BLACK
                else -> android.graphics.Color.TRANSPARENT
            }
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }
    
    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // RE-ENABLE edge-to-edge so background can flow into notch area
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Apply saved orientation preference
        val orientationPrefs = OrientationPreferences(this)
        applyOrientation(orientationPrefs.getOrientation())
        
        // Apply saved notch mode preference
        val notchPrefs = NotchPreferences(this)
        applyNotchMode(notchPrefs.getNotchMode())
        
        // Request Permissions
        val permissionLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
        ) { _ -> 
        }
        
        val permissions = if (android.os.Build.VERSION.SDK_INT >= 33) {
            arrayOf(
                android.Manifest.permission.READ_MEDIA_AUDIO,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
        permissionLauncher.launch(permissions)

        setContent {
            val configuration = LocalConfiguration.current
            
            // Auto hide/show status bar based on orientation
            LaunchedEffect(configuration.orientation) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                if (configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                     controller.hide(WindowInsetsCompat.Type.systemBars())
                     controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                     controller.show(WindowInsetsCompat.Type.systemBars())
                }
            }

            FolderPlayerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Black) {
                    val playerViewModel: PlayerViewModel = viewModel() 
                    val browserViewModel: com.wing.folderplayer.ui.browser.BrowserViewModel = viewModel()
                    val pagerState = rememberPagerState(pageCount = { 3 })
                    val scope = rememberCoroutineScope()

                    // Bridge State: Update browser's "playing" indicator when player state changes
                    androidx.compose.runtime.LaunchedEffect(playerViewModel.uiState) {
                        playerViewModel.uiState.collect { state ->
                            val path = state.currentMediaId?.let { 
                                if (it.startsWith("file://")) android.net.Uri.parse(it).path else it 
                            }
                            browserViewModel.updateCurrentlyPlaying(path)
                        }
                    }

                    HorizontalPager(state = pagerState) { page ->
                        when (page) {
                            0 -> MainPlayerScreen(
                                viewModel = playerViewModel
                            )
                            1 -> BrowserScreen(
                                viewModel = browserViewModel,
                                onFolderPlay = { sourceConfig, path, startingFileUri ->
                                    playerViewModel.playFolder(sourceConfig, path, startingFileUri)
                                    scope.launch { pagerState.animateScrollToPage(0) }
                                },
                                onCustomPlay = { sourceConfig, files, index ->
                                    playerViewModel.playCustomList(sourceConfig, files, index)
                                    scope.launch { pagerState.animateScrollToPage(0) }
                                },
                                onCuePlay = { sourceConfig, cuePath ->
                                    playerViewModel.playCueSheet(sourceConfig, cuePath)
                                    scope.launch { pagerState.animateScrollToPage(0) }
                                },
                                allPlaylists = playerViewModel.uiState.collectAsState().value.allPlaylists,
                                onAddToPlaylist = { targetListId, musicFiles ->
                                    val currentSource = browserViewModel.uiState.value.currentSource
                                    if (currentSource != null) {
                                        playerViewModel.addFilesToPlaylist(targetListId, currentSource, musicFiles)
                                    }
                                },
                                onBack = { 
                                    scope.launch { pagerState.animateScrollToPage(0) }
                                }
                            )
                            2 -> {
                                val state by playerViewModel.uiState.collectAsState()
                                SettingsScreen(
                                    onBack = { 
                                        scope.launch { pagerState.animateScrollToPage(0) }
                                    },
                                    currentCoverSize = state.coverDisplaySize,
                                    onCoverSizeChange = { playerViewModel.setCoverDisplaySize(it) },
                                    onOrientationChange = { applyOrientation(it) },
                                    onNotchModeChange = { applyNotchMode(it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
