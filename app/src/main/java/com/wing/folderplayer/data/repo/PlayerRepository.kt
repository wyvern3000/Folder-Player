package com.wing.folderplayer.data.repo

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.wing.folderplayer.data.source.MusicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlayerRepository {

    private val supportedExtensions = setOf("mp3", "flac", "wav", "ogg", "aac", "m4a", "opus", "wma", "ape", "dsf", "dff")
    private val imageExtensions = setOf("jpg", "jpeg", "png")
    private val lyricExtensions = setOf("lrc")
    private val cueExtensions = setOf("cue")

    suspend fun getMediaItemsInFolder(
        source: MusicSource, 
        path: String,
        sortField: String? = "NAME",
        sortAscending: Boolean = true
    ): List<MediaItem> = withContext(Dispatchers.Default) {
        val filesRaw = source.list(path)
        
        // Apply sorting to match Browser UI logic
        val musicOnly = filesRaw.filter { !it.isDirectory && supportedExtensions.contains(it.name.substringAfterLast('.', "").lowercase()) }
        val sortedFiles = when(sortField) {
            "NAME" -> {
                if (sortAscending) musicOnly.sortedBy { it.name.lowercase() }
                else musicOnly.sortedByDescending { it.name.lowercase() }
            }
            "DATE" -> {
                if (sortAscending) musicOnly.sortedBy { it.lastModified }
                else musicOnly.sortedByDescending { it.lastModified }
            }
            "SIZE" -> {
                if (sortAscending) musicOnly.sortedBy { it.size }
                else musicOnly.sortedByDescending { it.size }
            }
            else -> musicOnly
        }

        val coverUri = findCover(source, path, filesRaw)

        sortedFiles.map { file ->
            val uri = source.getUri(file.path)
            
            // Try to find lyric file with same name
            val baseName = file.name.substringBeforeLast('.')
            val lrcFile = filesRaw.find { 
                !it.isDirectory && it.name.startsWith(baseName) && lyricExtensions.contains(it.name.substringAfterLast('.', "").lowercase()) 
            }
            val lrcUri = lrcFile?.path?.let { source.getUri(it) }

            buildMediaItem(file.name, uri, coverUri, lrcUri, file.size)
        }
    }

    suspend fun findCover(source: MusicSource, folderPath: String, knownFiles: List<com.wing.folderplayer.data.source.MusicFile>? = null): Uri? {
        val files = knownFiles ?: try { source.list(folderPath) } catch (e: Exception) { emptyList() }
        
        // Helper to find cover in a list of files
        fun findCoverInFiles(fileList: List<com.wing.folderplayer.data.source.MusicFile>): com.wing.folderplayer.data.source.MusicFile? {
            val priorityNames = setOf("cover", "folder", "album", "front", "disk")
            val imageFiles = fileList.filter { !it.isDirectory && imageExtensions.contains(it.name.substringAfterLast('.', "").lowercase()) }
            return imageFiles.find { img -> priorityNames.contains(img.name.substringBeforeLast('.').lowercase()) }
                ?: imageFiles.firstOrNull()
        }

        var coverFile = findCoverInFiles(files)

        // If no cover in current folder AND folder name is short (like DISK 1), check parent folder
        if (coverFile == null) {
            val normalizedPath = folderPath.replace('\\', '/').trimEnd('/')
            val segments = normalizedPath.split('/')
            if (segments.size >= 2) {
                val currentFolderName = try { java.net.URLDecoder.decode(segments.last(), "UTF-8") } catch (e: Exception) { segments.last() }
                if (currentFolderName.length <= 6) {
                    val parentPath = segments.dropLast(1).joinToString("/")
                    if (parentPath.isNotEmpty()) {
                        val parentFiles = try { source.list(parentPath) } catch(e: Exception) { emptyList() }
                        coverFile = findCoverInFiles(parentFiles)
                    }
                }
            }
        }

        return coverFile?.path?.let { source.getUri(it) }
    }



    fun createMediaItem(title: String, uri: Uri, coverUri: Uri?, lrcUri: Uri?, fileSize: Long): MediaItem {
        return buildMediaItem(title, uri, coverUri, lrcUri, fileSize) 
    }

    private fun buildMediaItem(title: String, uri: Uri, coverUri: Uri?, lrcUri: Uri?, fileSize: Long): MediaItem {
        // Store extension and size in extras for robustness
        val cleanUriPath = uri.toString().substringBefore('?')
        val fileExtension = cleanUriPath.substringAfterLast('.', "").lowercase()
        
        val extras = android.os.Bundle().apply {
            putLong("file_size", fileSize)
            putString("file_ext", fileExtension)
            lrcUri?.let { putString("lrc_uri", it.toString()) }
        }
        
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setExtras(extras)
            .setIsBrowsable(false)
            .setIsPlayable(true)
        
        if (coverUri != null) {
            metadataBuilder.setArtworkUri(coverUri)
        }
        
        // Store lrcUri in extras or simply rely on file naming convention logic in UI
        // For MediaItem, we mainly care about Title and Artwork for notification
        
        // Determine MimeType based on extension for robustness
        val mimeType = when(fileExtension) {
            "mp3" -> androidx.media3.common.MimeTypes.AUDIO_MPEG
            "flac" -> androidx.media3.common.MimeTypes.AUDIO_FLAC
            "m4a" -> "audio/mp4"
            "ape" -> "audio/x-ape"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "dsf" -> "audio/x-dsf"
            "dff" -> "audio/x-dff"
            // For others, let ExoPlayer sniff
            else -> null 
        }

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(uri.toString())
            .setMediaMetadata(metadataBuilder.build())
            
        if (mimeType != null) {
            mediaItemBuilder.setMimeType(mimeType)
        }
            
        return mediaItemBuilder.build()
    }

    fun createCueMediaItem(
        fullAudioUri: Uri,
        trackTitle: String,
        performer: String?,
        startTimeMs: Long,
        endTimeMs: Long?,
        coverUri: Uri?,
        fileSize: Long
    ): MediaItem {
        val extras = android.os.Bundle().apply {
            putLong("file_size", fileSize)
        }
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(trackTitle)
            .setArtist(performer)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setExtras(extras)
        
        if (coverUri != null) {
            metadataBuilder.setArtworkUri(coverUri)
        }

        val clippingConfig = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startTimeMs)
            .apply {
                if (endTimeMs != null) {
                    setEndPositionMs(endTimeMs)
                }
            }
            .build()
            
        // Use a unique MediaID for virtual tracks to distinguish them in cache/playlist
        val virtualId = "${fullAudioUri}#track_${startTimeMs}"
        
        // Detect mime type from fullAudioUri extension for correct decoding
        val ext = fullAudioUri.toString().substringBefore('?').substringAfterLast('.', "").lowercase()
        val mimeType = when(ext) {
            "flac" -> androidx.media3.common.MimeTypes.AUDIO_FLAC
            "ape" -> "audio/ape"
            "wav" -> "audio/wav"
            "mp3" -> androidx.media3.common.MimeTypes.AUDIO_MPEG
            "dsf" -> "audio/x-dsf"
            "dff" -> "audio/x-dff"
            else -> null
        }

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(fullAudioUri)
            .setMediaId(virtualId)
            .setMediaMetadata(metadataBuilder.build())
            .setClippingConfiguration(clippingConfig)
            
        if (mimeType != null) {
            mediaItemBuilder.setMimeType(mimeType)
        }

        return mediaItemBuilder.build()
    }
}
