package com.wing.folderplayer.ui.player

import com.wing.folderplayer.data.playlist.PlaylistManager
import com.wing.folderplayer.data.playlist.Playlist
import com.wing.folderplayer.data.playlist.PlaylistItem

import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.wing.folderplayer.service.MusicService
import com.wing.folderplayer.data.prefs.PlaybackPreferences
import com.wing.folderplayer.ui.browser.SourceConfig
import com.wing.folderplayer.ui.browser.SourceType
import com.wing.folderplayer.data.repo.PlayerRepository
import com.wing.folderplayer.data.source.LocalSource
import com.wing.folderplayer.utils.LrcParser
import com.wing.folderplayer.utils.LyricLine
import com.wing.folderplayer.utils.CueParser
import com.wing.folderplayer.utils.CueTrack
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import android.os.Environment
import java.io.File

enum class TimerType { TIME, SONGS }

data class PlayerUiState(
    val currentTitle: String = "No Song Playing",
    val currentArtist: String = "",
    val coverUri: Any? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val duration: Long = 0L,
    val currentPosition: Long = 0L,
    val lyrics: List<LyricLine> = emptyList(),
    val currentLyricIndex: Int = -1,
    val currentMediaId: String? = null,
    val currentFolderName: String = "",
    val audioInfo: String = "",
    val shuffleModeEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,

    val playlist: List<androidx.media3.common.MediaItem> = emptyList(),
    val coverDisplaySize: String = "STANDARD", // STANDARD or LARGE
    val autoNextFolder: Boolean = false,
    
    // Buffering state
    val isBuffering: Boolean = false,
    val bufferedPosition: Long = 0L,
    
    // Album Info (Gemini)
    val albumInfo: String? = null,
    val artistInfo: String? = null,
    val albumInfoCacheKey: String? = null, // To ensure cache consistency
    val isFetchingAlbumInfo: Boolean = false,

    // Playlist Management
    val activePlaylistId: String = "default",
    val activePlaylistName: String = "Default",
    val activePlaylistItems: List<PlaylistItem> = emptyList(),
    val allPlaylists: List<Playlist> = emptyList(),
    
    // Sleep Timer
    val sleepTimerActive: Boolean = false,
    val sleepTimerValue: Int = 0,
    val sleepTimerType: TimerType = TimerType.TIME,
    val sleepTimerLabel: String = "0 min"
)

class PlayerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val exceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("PlayerViewModel", "Coroutine failure", throwable)
    }

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var player: Player? = null

    // For demo purposes, we will initialize the repository here
    private val repository = PlayerRepository()
    private val localSource = LocalSource()
    
    private var metadataJob: kotlinx.coroutines.Job? = null
    private val lyricsCache = mutableMapOf<String, List<LyricLine>>()
    private val albumInfoCache = mutableMapOf<String, String>()
    
    // Keep track of current source for lyrics and cover loading
    private var currentSource: com.wing.folderplayer.data.source.MusicSource? = null
    private var currentFolderPath: String? = null
    private var currentSourceConfig: SourceConfig? = null
    private var playbackPreferences: PlaybackPreferences? = null
    private var sourcePreferences: com.wing.folderplayer.data.prefs.SourcePreferences? = null
    private var lyricPreferences: com.wing.folderplayer.data.prefs.LyricPreferences? = null
    private var isRestoring = false
    private var pendingPlayIntent: String? = null
    private var lastMediaIdBeforeIntent: String? = null
    private var playlistManager: PlaylistManager? = null
    private val audioInfoCache = mutableMapOf<String, String>()
    
    // Sleep Timer internals
    private var sleepTimerDeadlineMs: Long = 0L
    private var remainingSongsCount: Int = 0

    fun initializeController(context: Context) {
        if (playbackPreferences == null) {
            playbackPreferences = PlaybackPreferences(context)
        }
        if (sourcePreferences == null) {
            sourcePreferences = com.wing.folderplayer.data.prefs.SourcePreferences(context)
        }
        if (lyricPreferences == null) {
            lyricPreferences = com.wing.folderplayer.data.prefs.LyricPreferences(context)
        }
        if (playlistManager == null) {
            playlistManager = PlaylistManager(context)
            
            // Sync initial playlist state (Async load)
            val activeId = playbackPreferences!!.getActivePlaylistId()
            val all = playlistManager!!.getAllPlaylists()
            val currentActual = all.find { it.id == activeId } ?: all.first()
            
            _uiState.value = _uiState.value.copy(
                activePlaylistId = currentActual.id,
                activePlaylistName = currentActual.name,
                activePlaylistItems = currentActual.items,
                allPlaylists = all
            )
        }
        
        // Safety Check: Detect "Init" folder trigger
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val resetTrigger = File(musicDir, "Init")
        val resetTriggerLower = File(musicDir, "init")
        
        if (resetTrigger.exists() || resetTriggerLower.exists()) {
             android.util.Log.w("PlayerViewModel", "Safe Mode Trigger Detected! Clearing persistence.")
             playbackPreferences?.clearAll()
             sourcePreferences?.clearAll()
             lyricPreferences?.clear()
             // We do NOT delete the folder, user must do it manually to re-enable persistence.
             // We just skip restoration logic below.
             return
        }
        
        // Restore cached UI state immediately for instant feedback
        val lastMediaId = playbackPreferences?.getLastMediaId()
        playbackPreferences?.getCachedMetadata()?.let { cached ->
             _uiState.value = _uiState.value.copy(
                 currentTitle = cached.title,
                 currentArtist = cached.artist,
                 currentFolderName = cached.folderName,
                 audioInfo = cached.audioInfo,
                 coverUri = cached.coverUri,
                 lyrics = cached.lyrics.toList(),
                 currentMediaId = lastMediaId
             )
             
             // Also seed the lyrics cache to prevent re-fetching
             if (lastMediaId != null && cached.lyrics.isNotEmpty()) {
                 lyricsCache[lastMediaId] = cached.lyrics.toList()
             }
        }
        
        // Load initial cover size and auto next folder setting
        val size = playbackPreferences?.getCoverDisplaySize() ?: "STANDARD"
        val autoNext = playbackPreferences?.getAutoNextFolder() ?: false
        _uiState.value = _uiState.value.copy(
            coverDisplaySize = size,
            autoNextFolder = autoNext
        )

        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        mediaControllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
            try {
                val controller = mediaControllerFuture?.get() ?: return@addListener
                player = controller
                setupPlayerListener()
                updatePlaybackState()
                
                // 1. Sync internal variables FROM PREFS first
                syncInternalStateFromPrefs()

                // 2. High-precision Restoration Detection
                val savedMediaId = playbackPreferences?.getLastMediaId()
                val playerMediaId = controller.currentMediaItem?.mediaId
                val playerState = controller.playbackState
                val isPlaying = controller.isPlaying
                
                // We restore if:
                // a) The player is totally empty or idle.
                // b) The player has items, but the ID doesn't match our 'last saved' ID, 
                //    AND it's not currently playing (don't interrupt active music).
                if (savedMediaId != null) {
                    val isPlayerEmpty = controller.mediaItemCount == 0
                    val isMismatched = playerMediaId != savedMediaId
                    val isInterrupted = !isPlaying && playerState != Player.STATE_READY
                    
                    if (isPlayerEmpty || (isMismatched && isInterrupted)) {
                        isRestoring = true
                        android.util.Log.d("PlayerViewModel", "Detected state drift or cold start. Restoring last known song: $savedMediaId")
                    }
                }

                // 3. Initial sync
                updateMetadata() 
                
                if (isRestoring) {
                    restoreLastState()
                }
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "MediaController Init Error", e)
                _error.value = "Playback System Failed to Initialize"
            }
        }, MoreExecutors.directExecutor())
    }

    private fun restoreLastState() {
        val prefs = playbackPreferences ?: return
        val config = prefs.getLastSourceConfig() ?: return
        val folderPath = prefs.getLastFolderPath() ?: return
        val mediaId = prefs.getLastMediaId()

        if (mediaId == null) return

        viewModelScope.launch(exceptionHandler) {
            // Check if we were playing a CUE track
            if (mediaId.contains("#track_")) {
                // Determine cuePath: usually base name of the audio file referenced in ID
                val audioPath = mediaId.substringBefore("#")
                // Try .cue with same name as audio
                val cuePath = audioPath.substringBeforeLast(".") + ".cue"
                
                playCueSheetInternal(config, cuePath, mediaId, prefs.getLastPosition(), playWhenReady = false)
            } else if (mediaId.lowercase().endsWith(".cue")) {
                playCueSheetInternal(config, mediaId, null, prefs.getLastPosition(), playWhenReady = false)
            } else {
                playFolderInternal(config, folderPath, mediaId, prefs.getLastPosition(), playWhenReady = false)
            }
        }
    }

    private fun syncInternalStateFromPrefs() {
        val prefs = playbackPreferences ?: return
        val config = prefs.getLastSourceConfig() ?: return
        val folderPath = prefs.getLastFolderPath() ?: return
        
        // Reconstruct source
        currentSourceConfig = config
        currentFolderPath = folderPath
        currentSource = when (config.type) {
            com.wing.folderplayer.ui.browser.SourceType.LOCAL -> localSource
            com.wing.folderplayer.ui.browser.SourceType.WEBDAV -> {
                com.wing.folderplayer.data.source.WebDavAuthManager.setCredentials(config.username, config.password)
                com.wing.folderplayer.data.source.WebDavSource(config.url, config.username, config.password)
            }
        }
    }

    private fun setupPlayerListener() {
        player?.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // When we transition to a new item, cancel any existing metadata work
                // to prevent older song data from 'flickering' in
                _uiState.value = _uiState.value.copy(
                    currentTitle = "Switching Track..",
                    lyrics = emptyList(), 
                    currentLyricIndex = -1
                )
                metadataJob?.cancel()
                updateMetadata()
                
                if (mediaItem == null && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    checkAndPlayNextFolder()
                }

                // Sleep Timer: Songs
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && _uiState.value.sleepTimerActive && _uiState.value.sleepTimerType == TimerType.SONGS) {
                    remainingSongsCount--
                    if (remainingSongsCount <= 0) {
                        player?.pause()
                        resetSleepTimer()
                    } else {
                        _uiState.value = _uiState.value.copy(
                            sleepTimerValue = remainingSongsCount,
                            sleepTimerLabel = "$remainingSongsCount songs"
                        )
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    checkAndPlayNextFolder()
                    resetSleepTimer()
                }
                // Update buffering state
                val buffering = playbackState == Player.STATE_BUFFERING
                _uiState.value = _uiState.value.copy(isBuffering = buffering)
                updatePlaybackState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlaybackState()
                if (isPlaying) {
                    startProgressLoop()
                    // Re-check metadata after 1.5 seconds to catch bitrates that populate after buffering
                    viewModelScope.launch(exceptionHandler) {
                        kotlinx.coroutines.delay(1500)
                        updateMetadata()
                    }
                }
            }
            
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED) || 
                    events.contains(Player.EVENT_TRACKS_CHANGED) ||
                    events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
                    events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED) ||
                    events.contains(Player.EVENT_REPEAT_MODE_CHANGED)) {
                    updateMetadata()
                }
            }
        })
    }

    private fun startProgressLoop() {
        viewModelScope.launch(exceptionHandler) {
            while (player?.isPlaying == true) {
                updatePlaybackState()
                checkSleepTimer()
                kotlinx.coroutines.delay(990) // Update approx every 1 second to reduce log spam
            }
        }
    }

    private fun checkSleepTimer() {
        val state = _uiState.value
        if (!state.sleepTimerActive || state.sleepTimerType != TimerType.TIME) return

        val now = System.currentTimeMillis()
        if (now >= sleepTimerDeadlineMs) {
            player?.pause()
            resetSleepTimer()
        } else {
            val remainingMins = ((sleepTimerDeadlineMs - now + 59999) / 60000).toInt()
            if (remainingMins != state.sleepTimerValue) {
                _uiState.value = _uiState.value.copy(
                    sleepTimerValue = remainingMins,
                    sleepTimerLabel = "$remainingMins min"
                )
            }
        }
    }

    fun startSleepTimer(type: TimerType, value: Int) {
        if (value <= 0) {
            resetSleepTimer()
            return
        }
        
        val label = if (type == TimerType.TIME) "$value min" else "$value songs"
        _uiState.value = _uiState.value.copy(
            sleepTimerActive = true,
            sleepTimerType = type,
            sleepTimerValue = value,
            sleepTimerLabel = label
        )

        if (type == TimerType.TIME) {
            sleepTimerDeadlineMs = System.currentTimeMillis() + (value * 60 * 1000L)
        } else {
            remainingSongsCount = value
        }
    }

    fun resetSleepTimer() {
        _uiState.value = _uiState.value.copy(
            sleepTimerActive = false,
            sleepTimerValue = 0,
            sleepTimerLabel = "0 min"
        )
        sleepTimerDeadlineMs = 0L
        remainingSongsCount = 0
    }

    private fun updateMetadata() {
        val p = player ?: return
        val currentMediaItem = p.currentMediaItem ?: return
        val metadata = p.mediaMetadata
        val currentTitleFromMetadata = metadata.title?.toString()
        val mediaId = currentMediaItem.mediaId

        // Protection against metadata updates for the OLD song during transitions
        val pending = pendingPlayIntent
        if (pending != null) {
            val isChangeDetected = if (pending == "ANY_NEW") {
                mediaId != lastMediaIdBeforeIntent
            } else {
                mediaId == pending
            }
            
            if (!isChangeDetected) {
                // Still waiting for player to reach the new state or the target song.
                return
            }
            pendingPlayIntent = null // Target reached!
        }

        // Guard: If we have an item but NO title yet, the service is still parsing metadata.
        // We MUST NOT overwrite the UI with "No Song Playing" if we are restoring or buffering.
        if (currentTitleFromMetadata.isNullOrBlank()) {
            if (isRestoring || p.playbackState == Player.STATE_BUFFERING || p.playbackState == Player.STATE_IDLE) {
                return
            }
        }

        // Only once we have a real media item AND a valid title, or if we've truly given up,
        if (!currentTitleFromMetadata.isNullOrBlank()) {
            isRestoring = false
        }

        val items = mutableListOf<MediaItem>()
        for (i in 0 until p.mediaItemCount) {
            items.add(p.getMediaItemAt(i))
        }

        val folderName = cleanFolderName(mediaId)
        
        // Reset album info and lyrics cache if we changed folders
        if (folderName != _uiState.value.currentFolderName) {
            _uiState.value = _uiState.value.copy(albumInfo = null)
            // Optional: Clear lyrics cache if switching albums to save memory
            // lyricsCache.clear() 
        }
        val rawTitle = metadata.title?.toString() ?: "No Song Playing"
        val musicExtensions = listOf("mp3", "flac", "m4a", "wav", "ogg", "aac", "opus", "ape", "dsf", "dff")
        val cleanTitle = if (rawTitle.contains('.')) {
            val ext = rawTitle.substringAfterLast('.').lowercase()
            if (musicExtensions.contains(ext)) {
                rawTitle.substringBeforeLast('.')
            } else {
                rawTitle
            }
        } else {
            rawTitle
        }
        
        // Path validation: Check if this song actually belongs to our current directory context
        val belongsToCurrentFolder = currentFolderPath != null && 
                                     mediaId.contains(currentFolderPath!!)
        
        // If we are NOT restoring, and the player is playing something we didn't expect,
        // we should update our context to match the player (reactive behavior).
        if (!isRestoring && mediaId != null && !belongsToCurrentFolder) {
            // Update internal context to match reality in the player
            val extractedParent = if (mediaId.contains("#track_")) {
                mediaId.substringBefore("#").substringBeforeLast('/')
            } else {
                mediaId.substringBeforeLast('/')
            }
            if (extractedParent.isNotEmpty() && extractedParent.startsWith("http")) {
                currentFolderPath = extractedParent
            }
        }
        
        // Extract audio format info from tracks
        var audioInfo = ""
        for (groupIndex in 0 until p.currentTracks.groups.size) {
            val group = p.currentTracks.groups[groupIndex]
            if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                // Grab info from the first track in the group even if not selected
                val format = group.getTrackFormat(0)
                val mime = format.sampleMimeType?.lowercase() ?: ""
                val displayFormat = when {
                    mime.contains("flac") -> "FLAC"
                    mime.contains("alac") || mime.contains("apple") && mime.contains("lossless") -> "ALAC"
                    mime.contains("mpeg") || mime.contains("mp3") -> "MP3"
                    mime.contains("ogg") || mime.contains("vorbis") -> "OGG"
                    mime.contains("opus") -> "OPUS"
                    mime.contains("aac") -> "AAC"
                    mime.contains("mp4") || mime.contains("m4a") -> "M4A"
                    else -> {
                        val ext = p.currentMediaItem?.mediaMetadata?.extras?.getString("file_ext")
                        val fallback = ext?.uppercase()?.take(4) ?: p.currentMediaItem?.mediaId?.substringAfterLast('.')?.uppercase()?.take(4) ?: "AUDIO"
                        if (mime.isNotEmpty()) "$fallback ($mime)" else fallback
                    }
                }

                var bitrateStr = ""
                val formatBitrate = format.bitrate
                val extension = p.currentMediaItem?.mediaMetadata?.extras?.getString("file_ext") ?: ""

                // Check cache first to prevent dynamic jumping or disappearance
                val cachedInfo = audioInfoCache[mediaId]
                if (cachedInfo != null && cachedInfo.contains("kbps")) {
                    audioInfo = cachedInfo
                    break
                }

                if (formatBitrate > 0) {
                    bitrateStr = snapBitrate(formatBitrate / 1000.0, extension)
                }
                
                var sampleRate = format.sampleRate
                var channelCount = format.channelCount
                
                // Fallback 1: Local MediaMetadataRetriever
                if (bitrateStr.isEmpty() || sampleRate <= 0 || channelCount <= 0) {
                    val path = p.currentMediaItem?.mediaId?.let { 
                        if (it.startsWith("file://")) it.substring(7) else if (it.startsWith("/") && !it.startsWith("http")) it else null
                    }
                    if (path != null && java.io.File(path).exists()) {
                        try {
                            val retriever = android.media.MediaMetadataRetriever()
                            retriever.setDataSource(path)
                            val b = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE)
                            if (b != null) {
                                bitrateStr = snapBitrate(b.toDouble() / 1000.0, extension)
                            }
                            
                            if (sampleRate <= 0) {
                                val sr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                                if (sr != null) {
                                    sampleRate = sr.toInt()
                                }
                            }
                            retriever.release()
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                }

                // Fallback 2: Calculate from file size and duration
                if (bitrateStr.isEmpty() && p.duration > 0) {
                    val fileSize = p.currentMediaItem?.mediaMetadata?.extras?.getLong("file_size", 0L) ?: 0L
                    if (fileSize > 0) {
                        val durationSec = p.duration / 1000.0
                        val bitrateCalc = ((fileSize * 8) / durationSec) / 1000.0
                        bitrateStr = snapBitrate(bitrateCalc, extension)
                    }
                }

                val samplerate = if (sampleRate > 0) {
                    val rate = sampleRate / 1000.0
                    if (rate % 1 == 0.0) "${rate.toInt()}kHz" else "${String.format("%.1f", rate)}kHz"
                } else ""
                
                audioInfo = listOfNotNull(
                    displayFormat,
                    bitrateStr.ifEmpty { null },
                    samplerate.ifEmpty { null }
                ).joinToString(" | ")

                // Cache it if we found a bitrate
                if (bitrateStr.isNotEmpty()) {
                    audioInfoCache[mediaId] = audioInfo
                }
                break
            }
        }
        
        // Immediate (FAST) Update: Update title, cover, and folder name synchronously
        // so the UI reacts instantly to a song change without any coroutine delay.
        val currentArtistImmediate = metadata.artist?.toString() ?: ""
        val targetMediaId = p.currentMediaItem?.mediaId
        
        // Synchronous cache check for immediate display
        val cachedLyrics = if (targetMediaId != null) lyricsCache[targetMediaId] else null
        
        _uiState.value = _uiState.value.copy(
            currentTitle = cleanTitle,
            currentArtist = currentArtistImmediate,
            currentFolderName = folderName,
            coverUri = metadata.artworkUri ?: metadata.artworkData,
            audioInfo = audioInfo,
            isPlaying = p.isPlaying,
            currentMediaId = targetMediaId,
            playlist = items,
            lyrics = cachedLyrics ?: listOf(LyricLine(0L, ".. Loading Lyrics ..")),
            currentLyricIndex = -1
        )
        
        metadataJob?.cancel()
        metadataJob = viewModelScope.launch(exceptionHandler) {
             // DEEP Update
             val lyrics = if (lyricsCache.containsKey(targetMediaId) && lyricsCache[targetMediaId]?.isNotEmpty() == true) {
                 lyricsCache[targetMediaId] ?: emptyList()
             } else {
                 val loaded = loadLyrics(targetMediaId, cleanTitle, currentArtistImmediate, metadata)
                 if (targetMediaId != null) lyricsCache[targetMediaId] = loaded
                 loaded
             }

             if (p.currentMediaItem?.mediaId != targetMediaId) return@launch

             _uiState.value = _uiState.value.copy(
               lyrics = lyrics,
               duration = p.duration.takeIf { it > 0 } ?: 1L,
               shuffleModeEnabled = p.shuffleModeEnabled,
               repeatMode = p.repeatMode
             )
             
             // Save for next restart (instant cache)
             playbackPreferences?.saveCachedMetadata(
                 com.wing.folderplayer.data.prefs.CachedMetadata(
                     title = cleanTitle,
                     artist = currentArtistImmediate,
                     folderName = folderName,
                     audioInfo = audioInfo,
                     coverUri = metadata.artworkUri?.toString(),
                     lyrics = lyrics.toTypedArray()
                 )
             )
             
             // Save playback state
             if (!isRestoring && p.playbackState != Player.STATE_IDLE) {
                 playbackPreferences?.savePlaybackState(
                     currentSourceConfig,
                     currentFolderPath,
                     targetMediaId,
                     p.currentPosition
                 )
             }
        }
    }

    fun toggleShuffle() {
        player?.let { p ->
            p.shuffleModeEnabled = !p.shuffleModeEnabled
            _uiState.value = _uiState.value.copy(shuffleModeEnabled = p.shuffleModeEnabled)
        }
    }

    fun toggleRepeatMode() {
        player?.let { p ->
            val nextMode = when (p.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
                else -> Player.REPEAT_MODE_OFF
            }
            p.repeatMode = nextMode
            _uiState.value = _uiState.value.copy(repeatMode = p.repeatMode)
        }
    }

    fun toggleAutoNextFolder() {
        val nextValue = !_uiState.value.autoNextFolder
        _uiState.value = _uiState.value.copy(autoNextFolder = nextValue)
        playbackPreferences?.saveAutoNextFolder(nextValue)
    }

    private fun checkAndPlayNextFolder() {
        if (!_uiState.value.autoNextFolder) return
        
        // We only move to next folder if repeat mode is OFF or ALL (not ONE)
        // Usually if it's ALL, it will loop the current folder, so it will never reach STATE_ENDED.
        // If it's OFF, it reaches STATE_ENDED.
        
        viewModelScope.launch(exceptionHandler) {
            moveNextFolder()
        }
    }

    private suspend fun moveNextFolder() {
        val config = currentSourceConfig ?: return
        val currentPath = currentFolderPath ?: return
        val source = currentSource ?: return
        
        // 1. Determine parent path
        val parentPath = if (currentPath.contains("/")) {
            currentPath.substringBeforeLast('/')
        } else {
            "" // Root
        }
        
        // 2. List siblings (folders in the same parent)
        val siblings = withContext(Dispatchers.IO) {
            try {
                source.list(parentPath)
            } catch (e: Exception) {
                emptyList()
            }
        }.filter { it.isDirectory }
        
        if (siblings.isEmpty()) return
        
        // 3. Apply sorting using SourcePreferences
        val sortOption = sourcePreferences?.getDirectorySort(parentPath) ?: sourcePreferences?.getDefaultSort() ?: com.wing.folderplayer.data.prefs.SourcePreferences.SortOption("NAME", true)
        
        val sortedSiblings = when (sortOption.field) {
            "NAME" -> {
                if (sortOption.ascending) siblings.sortedBy { it.name.lowercase() }
                else siblings.sortedByDescending { it.name.lowercase() }
            }
            "DATE" -> {
                if (sortOption.ascending) siblings.sortedBy { it.lastModified }
                else siblings.sortedByDescending { it.lastModified }
            }
            "SIZE" -> {
                if (sortOption.ascending) siblings.sortedBy { it.size }
                else siblings.sortedByDescending { it.size }
            }
            else -> siblings.sortedBy { it.name.lowercase() }
        }
        
        // Find current folder in sorted siblings
        val currentIndex = sortedSiblings.indexOfFirst { it.path == currentPath || it.path == "$currentPath/" || it.path.trimEnd('/') == currentPath.trimEnd('/') }
        
        if (currentIndex != -1 && currentIndex < sortedSiblings.size - 1) {
            val nextFolder = sortedSiblings[currentIndex + 1]
            playFolderInternal(config, nextFolder.path, null, 0L, playWhenReady = true)
        }
    }

    private fun snapBitrate(bitrateKbps: Double, extension: String): String {
        if (bitrateKbps <= 0) return ""
        
        var capped = bitrateKbps
        if (extension.equals("mp3", ignoreCase = true) && capped > 320.5) {
            capped = 320.0
        }

        val standards = listOf(32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
        
        // Find closest standard
        val closest = standards.minByOrNull { Math.abs(it - capped) } ?: 0
        // Snapping range: within 10kbps or 5%
        val diff = Math.abs(closest.toDouble() - capped)
        if (diff < 10.0 || diff < (capped * 0.05)) {
            return "${closest}kbps"
        }

        // For VBR (average) or high-res formats (FLAC/WAV), use rounded value
        return "${capped.toInt()}kbps"
    }

    private fun cleanFolderName(mediaId: String?): String {
        if (mediaId == null) return ""
        try {
            val decodedPath = java.net.URLDecoder.decode(mediaId, "UTF-8")
            val parts = decodedPath.split('/', '\\').filter { it.isNotEmpty() }
            if (parts.size < 2) return "Root"
            
            // parts.last() is typically the filename (e.g., Song.mp3)
            // The segment before it is the folder
            val folderIndex = parts.size - 2
            val folderPart = parts[folderIndex]
            val cleanFolder = cleanString(folderPart)
            
            // If the folder name is very short (e.g., "CD1"), prepend the parent folder
            if (cleanFolder.length <= 6 && folderIndex >= 1) {
                val parentPart = parts[folderIndex - 1]
                return "${cleanString(parentPart)} - $cleanFolder"
            }
            return cleanFolder
        } catch (e: Exception) {
            // Fallback: extract the part before the last slash
            val trimmed = mediaId.substringBeforeLast('/')
            return trimmed.substringAfterLast('/').ifEmpty { "Music" }
        }
    }

    private fun cleanString(input: String): String {
        return input
            .replace(Regex("\\{.*?\\}"), "") // Remove {...}
            .replace(Regex("\\[.*?\\]"), "") // Remove [...]
            .replace(Regex("(?i)\\s+flac"), "") // Remove " FLAC" (case insensitive)
            .replace(Regex("\\s+"), " ") // Collapse multiple spaces
            .trim()
    }

    fun fetchAlbumInfo() {
        val state = _uiState.value
        val folder = state.currentFolderName
        val artist = state.currentArtist
        if (folder.isEmpty() || folder == "No Song Playing" || folder == "Root") return

        val cacheKey = "$folder|$artist"
        if (albumInfoCache.containsKey(cacheKey)) {
            val cached = albumInfoCache[cacheKey] ?: ""
            val parts = cached.split("[ARTIST_START]")
            _uiState.value = _uiState.value.copy(
                albumInfo = parts[0].trim(),
                artistInfo = if (parts.size > 1) parts[1].trim() else null
            )
            return
        }

        val baseUrl = (lyricPreferences?.getAiBaseUrl() ?: "https://api.openai.com/v1").trim().removeSuffix("/")
        val apiKey = (lyricPreferences?.getAiApiKey() ?: "").trim()
        val modelName = (lyricPreferences?.getAiModel() ?: "gpt-3.5-turbo").trim()

        if (apiKey.isEmpty()) {
            _uiState.value = _uiState.value.copy(albumInfo = "Please set AI API Key in Settings to enable this feature.")
            return
        }

        _uiState.value = _uiState.value.copy(isFetchingAlbumInfo = true, albumInfo = null, artistInfo = null)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prompt = """
                    你是一位资深的音乐唱片评论家。请简要介绍一下专辑《$folder》，它的演唱者/艺术家是 $artist。
                    请按照以下格式返回内容，确保中间包含分隔符 [ARTIST_START]：
                    
                    [专辑介绍部分：从专辑特点、音乐风格、制作背景等角度进行分段介绍，项目符号请用简单的减号，不要使用Markdown格式。此部分300字以内。]
                    [ARTIST_START]
                    [艺术家介绍部分：介绍这个专辑的艺术家或乐团的生平或成就，不要使用Markdown格式。此部分200字以内。]
                """.trimIndent()
                
                val json = org.json.JSONObject().apply {
                    put("model", modelName)
                    put("messages", org.json.JSONArray().put(
                        org.json.JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        }
                    ))
                }

                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val url = "$baseUrl/chat/completions"
                android.util.Log.d("AIDebug", "Requesting URL: $url with model: $modelName")
                
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $apiKey")
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (response.isSuccessful && body != null) {
                        val respJson = org.json.JSONObject(body)
                        val text = respJson.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                        
                        val cleanText = text.trim()
                        albumInfoCache[cacheKey] = cleanText
                        
                        val parts = cleanText.split("[ARTIST_START]")
                        _uiState.value = _uiState.value.copy(
                            isFetchingAlbumInfo = false, 
                            albumInfo = parts[0].trim(),
                            artistInfo = if (parts.size > 1) parts[1].trim() else null
                        )
                    } else {
                        android.util.Log.e("AIDebug", "Error Response (${response.code}): $body")
                        val errorMsg = when(response.code) {
                            429 -> "请求过于频繁（429）。"
                            401 -> "API Key 授权失败。"
                            404 -> "API 地址或模型未找到（404）。"
                            else -> "获取失败：错误代码 ${response.code}"
                        }
                        _uiState.value = _uiState.value.copy(isFetchingAlbumInfo = false, albumInfo = errorMsg)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "AI Error", e)
                _uiState.value = _uiState.value.copy(isFetchingAlbumInfo = false, albumInfo = "Error: ${e.message}")
            }
        }
    }

    private fun extractEmbeddedLyrics(metadata: androidx.media3.common.MediaMetadata): String? {
        // Media3 often puts unrecognized tags into extras
        val extras = metadata.extras
        if (extras != null) {
            // Check common keys used by various extractors
            return extras.getString("lyrics") 
                ?: extras.getString("lyric")
                ?: extras.getString("text")
        }
        return null
    }

    private suspend fun loadLyrics(
        mediaId: String?, 
        title: String, 
        artist: String?,
        metadata: androidx.media3.common.MediaMetadata? = null
    ): List<LyricLine> {
        if (mediaId == null || currentSource == null) return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                // 1. Try .lrc file in the same directory (Highest Priority)
                val lrcPath = mediaId.substringBeforeLast('.') + ".lrc"
                val lrcContent = currentSource?.readText(lrcPath)
                if (lrcContent != null) {
                    val parsed = LrcParser.parse(lrcContent)
                    if (parsed.isNotEmpty()) return@withContext parsed
                }
                
                // 2. Try Embedded lyrics (Priority 2)
                metadata?.let { m ->
                    val embedded = extractEmbeddedLyrics(m)
                    if (!embedded.isNullOrBlank()) {
                        val parsed = LrcParser.parse(embedded)
                        if (parsed.isNotEmpty()) return@withContext parsed
                    }
                }

                // 3. Try Lyric API (Priority 3)
                val apiUrl = lyricPreferences?.getLyricApiUrl()
                
                val url = apiUrl
                if (url != null && title != "No Song Playing") {
                    val apiLrc = com.wing.folderplayer.data.network.LyricApi.fetchLyrics(url, title, artist)
                    if (apiLrc != null) {
                        val parsed = LrcParser.parse(apiLrc)
                        if (parsed.isNotEmpty()) return@withContext parsed
                    }
                }

                emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun updatePlaybackState() {
        player?.let { p ->
            val position = p.currentPosition
            val duration = p.duration.takeIf { it > 0 } ?: 1L
            val lyrics = _uiState.value.lyrics
            // Find current lyric index
            val index = lyrics.indexOfLast { it.timeMs <= position }

            _uiState.value = _uiState.value.copy(
                currentPosition = position,
                duration = duration,
                progress = position.toFloat() / duration,
                isPlaying = p.isPlaying,
                currentLyricIndex = index,
                bufferedPosition = p.bufferedPosition
            )

            // Periodically save position (every 5 seconds)
            if (!isRestoring && Math.abs(position - (playbackPreferences?.getLastPosition() ?: 0L)) > 5000) {
                playbackPreferences?.savePosition(position)
            }
        }
    }

    fun playPause() {
        player?.let { p ->
            if (p.isPlaying) p.pause() else p.play()
            updatePlaybackState()
        }
    }

    fun previous() {
        _uiState.value = _uiState.value.copy(
            currentTitle = "Loading..",
            lyrics = emptyList(), 
            currentLyricIndex = -1
        )
        lastMediaIdBeforeIntent = player?.currentMediaItem?.mediaId
        pendingPlayIntent = "ANY_NEW"
        player?.seekToPreviousMediaItem()
    }

    fun next() {
        _uiState.value = _uiState.value.copy(
            currentTitle = "Loading..",
            lyrics = emptyList(), 
            currentLyricIndex = -1
        )
        lastMediaIdBeforeIntent = player?.currentMediaItem?.mediaId
        pendingPlayIntent = "ANY_NEW"
        player?.seekToNextMediaItem()
    }
    
    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
        updatePlaybackState()
    }

    fun seekTo(position: Float) {
        player?.let { p ->
            val duration = p.duration
            if (duration > 0) {
                p.seekTo((position * duration).toLong())
                updatePlaybackState()
            }
        }
    }

    fun playAt(index: Int) {
        isRestoring = false
        _uiState.value = _uiState.value.copy(
            currentTitle = "Loading..",
            lyrics = emptyList(), 
            currentLyricIndex = -1
        )
        lastMediaIdBeforeIntent = player?.currentMediaItem?.mediaId
        pendingPlayIntent = "ANY_NEW"
        player?.seekTo(index, 0L)
        player?.play()
    }

    // Demo function to simulate playing a folder
    // Updated to support SourceConfig
    fun playFolder(sourceConfig: com.wing.folderplayer.ui.browser.SourceConfig, path: String, startingFileUri: String? = null) {
        isRestoring = false
        // Immediately set pending state for visual feedback
        val pendingTitle = startingFileUri?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Loading..."
        val pendingFolder = path.substringAfterLast('/').let { 
            try { java.net.URLDecoder.decode(it, "UTF-8") } catch (e: Exception) { it } 
        }
        _uiState.value = _uiState.value.copy(
            currentTitle = pendingTitle,
            currentFolderName = pendingFolder,
            coverUri = null,
            lyrics = emptyList(),
            isBuffering = true,
            progress = 0f,
            currentPosition = 0L,
            duration = 0L
        )
        lastMediaIdBeforeIntent = player?.currentMediaItem?.mediaId
        pendingPlayIntent = startingFileUri ?: "ANY_NEW"
        
        viewModelScope.launch(exceptionHandler) {
            playFolderInternal(sourceConfig, path, startingFileUri, 0L, playWhenReady = true)
        }
    }

    fun playCustomList(sourceConfig: SourceConfig, files: List<com.wing.folderplayer.data.source.MusicFile>, startIndex: Int) {
        isRestoring = false
        viewModelScope.launch(exceptionHandler) {
            val source = when (sourceConfig.type) {
                com.wing.folderplayer.ui.browser.SourceType.LOCAL -> {
                    com.wing.folderplayer.data.source.WebDavAuthManager.clear()
                    localSource
                }
                com.wing.folderplayer.ui.browser.SourceType.WEBDAV -> {
                     com.wing.folderplayer.data.source.WebDavAuthManager.setCredentials(sourceConfig.username, sourceConfig.password)
                     com.wing.folderplayer.data.source.WebDavSource(sourceConfig.url, sourceConfig.username, sourceConfig.password)
                }
            }
            
            // Reconstruct internal state
            currentSource = source
            val firstFile = files.getOrNull(startIndex)
            val folderPath = firstFile?.path?.substringBeforeLast('/') ?: ""
            currentFolderPath = folderPath
            currentSourceConfig = sourceConfig
            
            // Fast UI Reset
            _uiState.value = _uiState.value.copy(
                currentTitle = firstFile?.name ?: "Loading...",
                currentArtist = "",
                currentFolderName = folderPath.substringAfterLast('/'),
                currentMediaId = firstFile?.path,
                lyrics = emptyList(),
                coverUri = null,
                isBuffering = true
            )
            lastMediaIdBeforeIntent = player?.currentMediaItem?.mediaId
            pendingPlayIntent = firstFile?.path ?: "ANY_NEW"
            
            // Try to find cover for this batch (assuming same folder)
            val coverUri = repository.findCover(source, folderPath)

            val mediaItems = files.map { file ->
                repository.createMediaItem(file.name, source.getUri(file.path), coverUri, null, file.size)
            }
            player?.setMediaItems(mediaItems)
            if (startIndex in mediaItems.indices) {
                player?.seekTo(startIndex, 0L)
            }
            player?.prepare()
            player?.play()
            
            // Save state
            playbackPreferences?.savePlaybackState(sourceConfig, currentFolderPath, files.getOrNull(startIndex)?.path, 0L)

            // Sync to "Default" playlist
            val playlistItems = files.map { file ->
                PlaylistItem(
                    path = file.path,
                    title = file.name,
                    artist = "",
                    sourceId = getSourceId(sourceConfig),
                    artworkUri = coverUri?.toString()
                )
            }
            playlistManager?.savePlaylist(Playlist("default", "Default", playlistItems))
            
            // Focus on Default in UI
            _uiState.value = _uiState.value.copy(
                activePlaylistId = "default",
                activePlaylistName = "Default",
                activePlaylistItems = playlistItems
            )
            refreshPlaylists()
            
            // Force metadata update after a delay
            kotlinx.coroutines.delay(1000)
            updateMetadata()
        }
    }

    fun playCueSheet(sourceConfig: SourceConfig, cuePath: String) {
        isRestoring = false
        // Immediately set pending state for visual feedback
        val pendingTitle = cuePath.substringAfterLast('/').substringBeforeLast('.')
        val pendingFolder = cuePath.substringBeforeLast('/').substringAfterLast('/').let { 
            try { java.net.URLDecoder.decode(it, "UTF-8") } catch (e: Exception) { it } 
        }
        _uiState.value = _uiState.value.copy(
            currentTitle = pendingTitle,
            currentFolderName = pendingFolder,
            currentMediaId = cuePath,
            coverUri = null,
            lyrics = emptyList(),
            isBuffering = true,
            progress = 0f,
            currentPosition = 0L,
            duration = 0L
        )
        lastMediaIdBeforeIntent = player?.currentMediaItem?.mediaId
        pendingPlayIntent = "ANY_NEW"
        
        viewModelScope.launch(exceptionHandler) {
            playCueSheetInternal(sourceConfig, cuePath, null, 0L, playWhenReady = true)
        }
    }

    fun playPlaylistSong(playlistId: String, startIndex: Int) {
        val manager = playlistManager ?: return
        val playlist = manager.getPlaylist(playlistId) ?: return
        val items = playlist.items
        val clickedItem = items.getOrNull(startIndex) ?: return
        
        viewModelScope.launch(exceptionHandler) {
            // Find SourceConfig to set up auth manager
            val sources = sourcePreferences?.getSources() ?: emptyList()
            val config = if (clickedItem.sourceId == "local") {
                SourceConfig(name = "Local", type = com.wing.folderplayer.ui.browser.SourceType.LOCAL)
            } else {
                sources.find { it.url == clickedItem.sourceId }
            }
            
            if (config != null && config.type == com.wing.folderplayer.ui.browser.SourceType.WEBDAV) {
                com.wing.folderplayer.data.source.WebDavAuthManager.setCredentials(config.username, config.password)
            } else {
                com.wing.folderplayer.data.source.WebDavAuthManager.clear()
            }

            val mediaItems = items.map { pItem ->
            val uri = pItem.path
            val finalUri = if (uri.startsWith("/") && !uri.startsWith("http")) {
                "file://$uri"
            } else {
                uri
            }

            androidx.media3.common.MediaItem.Builder()
                .setMediaId(pItem.path)
                .setUri(finalUri)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(pItem.title)
                        .setArtist(pItem.artist?.takeIf { it.isNotBlank() })
                        .setArtworkUri(pItem.artworkUri?.let { android.net.Uri.parse(it) })
                        .build()
                )
                .build()
        }
            
            player?.setMediaItems(mediaItems)
            if (startIndex in mediaItems.indices) {
                player?.seekTo(startIndex, 0L)
            }
            player?.prepare()
            player?.play()
            
            // Sync UI state
            _uiState.value = _uiState.value.copy(
                activePlaylistId = playlistId,
                activePlaylistName = playlist.name,
                activePlaylistItems = items
            )
            refreshPlaylists()
        }
    }

    private suspend fun playCueSheetInternal(
        sourceConfig: SourceConfig, 
        cuePath: String, 
        startingMediaId: String?, 
        positionMs: Long, 
        playWhenReady: Boolean
    ) {
        val source = when (sourceConfig.type) {
            com.wing.folderplayer.ui.browser.SourceType.LOCAL -> {
                com.wing.folderplayer.data.source.WebDavAuthManager.clear()
                localSource
            }
            com.wing.folderplayer.ui.browser.SourceType.WEBDAV -> {
                 com.wing.folderplayer.data.source.WebDavAuthManager.setCredentials(sourceConfig.username, sourceConfig.password)
                 com.wing.folderplayer.data.source.WebDavSource(sourceConfig.url, sourceConfig.username, sourceConfig.password)
            }
        }
        
        val cueContent = source.readText(cuePath) ?: return
        val (referencedFile, tracks) = CueParser.parse(cueContent)
        if (tracks.isEmpty()) return

        val parentPath = cuePath.substringBeforeLast('/', "")
        var audioPath: String? = null
        
        // 1. Try the file referenced in the BUG sheet
        if (referencedFile != null) {
            val p = if (parentPath.isEmpty()) referencedFile else "$parentPath/$referencedFile"
            // Use name only for comparison if it's a relative path in the CUE
            val name = referencedFile.substringAfterLast('/')
            val list = source.list(parentPath)
            if (list.any { it.name == name }) {
                audioPath = p
            }
        }
        
        // 2. Fallback: Try common extensions with same name as .cue
        if (audioPath == null) {
            val base = cuePath.substringBeforeLast('.')
            val possibleExtensions = listOf("flac", "ape", "wav", "mp3", "m4a", "dsf", "dff")
            for (ext in possibleExtensions) {
                val p = "$base.$ext"
                val name = p.substringAfterLast('/')
                val list = source.list(parentPath)
                if (list.any { it.name == name }) {
                    audioPath = p
                    break
                }
            }
        }

        if (audioPath == null) return

        currentSource = source
        currentSourceConfig = sourceConfig
        currentFolderPath = cuePath.substringBeforeLast('/')
        
        val audioUri = source.getUri(audioPath)
        val coverUri = repository.findCover(source, currentFolderPath!!)

        val mediaItems = tracks.map { track ->
            repository.createCueMediaItem(
                fullAudioUri = audioUri,
                trackTitle = track.title,
                performer = track.performer,
                startTimeMs = track.startTimeMs,
                endTimeMs = track.endTimeMs,
                coverUri = coverUri,
                fileSize = 0
            )
        }

        player?.setMediaItems(mediaItems)
        
        // Sync to "Default" playlist file
        if (!isRestoring) {
            val playlistItems = tracks.map { track ->
                PlaylistItem(
                    path = cuePath, // CUE tracks point to the .cue file
                    title = track.title,
                    artist = track.performer ?: "",
                    sourceId = getSourceId(sourceConfig),
                    artworkUri = coverUri?.toString(),
                    durationMs = (track.endTimeMs ?: 0L) - track.startTimeMs
                )
            }
            playlistManager?.savePlaylist(Playlist("default", "Default", playlistItems))
            
            // Focus on Default in UI
            _uiState.value = _uiState.value.copy(
                activePlaylistId = "default",
                activePlaylistName = "Default",
                activePlaylistItems = playlistItems
            )
            refreshPlaylists()
        }
        // Find correct index to restore
        if (startingMediaId != null) {
            val index = mediaItems.indexOfFirst { it.mediaId == startingMediaId }
            if (index != -1) {
                player?.seekTo(index, positionMs)
            }
        } else {
            player?.seekTo(0, positionMs)
        }

        player?.prepare()
        if (playWhenReady) {
            player?.play()
        }

        // Save state immediately
        playbackPreferences?.savePlaybackState(sourceConfig, currentFolderPath!!, startingMediaId ?: cuePath, positionMs)

        kotlinx.coroutines.delay(1000)
        updateMetadata()
    }

    private suspend fun playFolderInternal(
        sourceConfig: SourceConfig, 
        path: String, 
        startingFileUri: String?, 
        positionMs: Long,
        playWhenReady: Boolean
    ) {
        val source = when (sourceConfig.type) {
            com.wing.folderplayer.ui.browser.SourceType.LOCAL -> {
                com.wing.folderplayer.data.source.WebDavAuthManager.clear()
                localSource
            }
            com.wing.folderplayer.ui.browser.SourceType.WEBDAV -> {
                 com.wing.folderplayer.data.source.WebDavAuthManager.setCredentials(sourceConfig.username, sourceConfig.password)
                 com.wing.folderplayer.data.source.WebDavSource(sourceConfig.url, sourceConfig.username, sourceConfig.password)
            }
        }
        
        // Store current source for lyrics loading and persistence
        currentSource = source
        currentFolderPath = path
        currentSourceConfig = sourceConfig
        
        // Get sorting preferences for this path to ensure restoration order is correct
        val sortPref = sourcePreferences?.getDirectorySort(path) ?: sourcePreferences?.getDefaultSort()
        
        val items = repository.getMediaItemsInFolder(
            source = source, 
            path = path,
            sortField = sortPref?.field ?: "NAME",
            sortAscending = sortPref?.ascending ?: true
        )
        player?.setMediaItems(items)
        
        // Sync to "Default" playlist file
        if (!isRestoring) {
            val coverUri = repository.findCover(source, path)
            val playlistItems = items.map { item ->
                PlaylistItem(
                    path = item.localConfiguration?.uri?.toString() ?: "",
                    title = item.mediaMetadata.title?.toString() ?: "Unknown",
                    artist = item.mediaMetadata.artist?.toString() ?: "",
                    sourceId = getSourceId(sourceConfig),
                    artworkUri = coverUri?.toString()
                )
            }
            playlistManager?.savePlaylist(Playlist("default", "Default", playlistItems))
            
            // Focus on Default in UI
            _uiState.value = _uiState.value.copy(
                activePlaylistId = "default",
                activePlaylistName = "Default",
                activePlaylistItems = playlistItems
            )
            refreshPlaylists()
        }
        
        // Find the index of the starting file if provided
        if (startingFileUri != null) {
            val targetUri = source.getUri(startingFileUri).toString()
            val index = items.indexOfFirst { it.localConfiguration?.uri.toString() == targetUri || it.mediaId == startingFileUri }
            if (index != -1) {
                player?.seekTo(index, positionMs)
            }
        } else if (positionMs > 0) {
            player?.seekTo(0, positionMs)
        }
        
        player?.prepare()
        if (playWhenReady) {
            player?.play()
        }
        
        // High reliability seek for restoration: 
        if (positionMs > 0) {
            viewModelScope.launch(exceptionHandler) {
                // Wait for the player to transition from IDLE and start buffering/ready
                var attempts = 0
                while (player?.playbackState == Player.STATE_IDLE && attempts < 15) {
                    kotlinx.coroutines.delay(100)
                    attempts++
                }
                
                // Extra delay to ensure internal state is settled
                kotlinx.coroutines.delay(200)
                
                val index = if (startingFileUri != null) {
                    val targetUri = source.getUri(startingFileUri).toString()
                    items.indexOfFirst { it.localConfiguration?.uri.toString() == targetUri || it.mediaId == startingFileUri }
                } else 0
                
                if (index != -1) {
                    player?.seekTo(index, positionMs)
                }
            }
        }
        
        // Force metadata update after a short delay to ensure player is ready
        viewModelScope.launch(exceptionHandler) {
            kotlinx.coroutines.delay(1000)
            updateMetadata()
        }
        
        // Save state immediately
        playbackPreferences?.savePlaybackState(sourceConfig, path, startingFileUri, positionMs)
    }

    fun setCoverDisplaySize(size: String) {
        playbackPreferences?.saveCoverDisplaySize(size)
        _uiState.value = _uiState.value.copy(coverDisplaySize = size)
    }

    // --- Playlist Management Methods ---

    fun switchPlaylist(id: String) {
        val manager = playlistManager ?: return
        val playlist = manager.getPlaylist(id) ?: return
        
        // 1. Persist active ID
        playbackPreferences?.saveActivePlaylistId(id)
        
        // 2. Clear Modes as requested
        _uiState.value = _uiState.value.copy(
            activePlaylistId = id,
            activePlaylistName = playlist.name,
            activePlaylistItems = playlist.items,
            shuffleModeEnabled = false,
            repeatMode = Player.REPEAT_MODE_OFF,
            autoNextFolder = false
        )
        player?.shuffleModeEnabled = false
        player?.repeatMode = Player.REPEAT_MODE_OFF
        
        refreshPlaylists()
    }

    private fun refreshPlaylists() {
        val manager = playlistManager ?: return
        val all = manager.getAllPlaylists()
        val currentId = _uiState.value.activePlaylistId
        val currentItems = manager.getPlaylist(currentId)?.items ?: emptyList<PlaylistItem>()
        
        _uiState.value = _uiState.value.copy(
            allPlaylists = all,
            activePlaylistItems = currentItems
        )
    }

    fun createPlaylist(name: String) {
        val id = playlistManager?.createPlaylist(name)
        if (id != null) {
            refreshPlaylists()
        }
    }

    fun renamePlaylist(id: String, newName: String) {
        playlistManager?.renamePlaylist(id, newName)
        if (id == _uiState.value.activePlaylistId) {
            _uiState.value = _uiState.value.copy(activePlaylistName = newName)
        }
        refreshPlaylists()
    }

    fun deletePlaylist(id: String) {
        if (id == "default") return
        playlistManager?.deletePlaylist(id)
        if (id == _uiState.value.activePlaylistId) {
            switchPlaylist("default")
        } else {
            refreshPlaylists()
        }
    }

    fun removeFromActivePlaylist(index: Int) {
        val currentId = _uiState.value.activePlaylistId
        
        // If it's the default list, we also need to remove it from the PLAYER queue
        if (currentId == "default") {
            player?.removeMediaItem(index)
        }
        
        playlistManager?.removeFromPlaylist(currentId, index)
        refreshPlaylists()
    }

    fun addFilesToPlaylist(targetPlaylistId: String, sourceConfig: SourceConfig, files: List<com.wing.folderplayer.data.source.MusicFile>) {
        viewModelScope.launch(exceptionHandler) {
            val sourceId = getSourceId(sourceConfig)
            val source = when (sourceConfig.type) {
                com.wing.folderplayer.ui.browser.SourceType.LOCAL -> localSource
                com.wing.folderplayer.ui.browser.SourceType.WEBDAV -> {
                    com.wing.folderplayer.data.source.WebDavAuthManager.setCredentials(sourceConfig.username, sourceConfig.password)
                    com.wing.folderplayer.data.source.WebDavSource(sourceConfig.url, sourceConfig.username, sourceConfig.password)
                }
            }
            
            val allItems = mutableListOf<PlaylistItem>()
            for (file in files) {
                if (file.isDirectory) {
                    val coverUri = repository.findCover(source, file.path)
                    val mediaItems = repository.getMediaItemsInFolder(source, file.path)
                    mediaItems.forEach { item ->
                        allItems.add(PlaylistItem(
                            path = item.localConfiguration?.uri?.toString() ?: "",
                            title = item.mediaMetadata.title?.toString() ?: "Unknown",
                            artist = item.mediaMetadata.artist?.toString() ?: "",
                            sourceId = sourceId,
                            artworkUri = coverUri?.toString(),
                            durationMs = 0L
                        ))
                    }
                } else {
                    val parent = if (file.path.contains("/")) file.path.substringBeforeLast("/") else file.path
                    val coverUri = repository.findCover(source, parent)
                    allItems.add(PlaylistItem(
                        path = file.path,
                        title = file.name,
                        artist = "",
                        sourceId = sourceId,
                        artworkUri = coverUri?.toString(),
                        durationMs = 0L
                    ))
                }
            }
            
            playlistManager?.appendToPlaylist(targetPlaylistId, allItems)
            refreshPlaylists()
        }
    }

    private fun getSourceId(config: SourceConfig): String {
        return if (config.type == com.wing.folderplayer.ui.browser.SourceType.LOCAL) "local" else config.url
    }

    override fun onCleared() {
        mediaControllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}
