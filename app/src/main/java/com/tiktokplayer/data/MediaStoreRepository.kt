package com.tiktokplayer.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

class MediaStoreRepository(private val context: Context) {

    /**
     * Load all videos (for backward compatibility).
     * Filters out videos shorter than 1 second.
     */
    fun getAllVideos(): List<VideoItem> {
        return queryVideos(limit = null, offset = null)
    }

    /**
     * Load videos with pagination support.
     * @param limit Maximum number of videos to return
     * @param offset Number of videos to skip
     * @return List of VideoItem for this page
     */
    fun getVideosPaged(limit: Int, offset: Int): List<VideoItem> {
        return queryVideos(limit = limit, offset = offset)
    }

    /**
     * Get total video count (for pagination metadata).
     */
    fun getVideoCount(): Int {
        var count = 0
        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Video.Media._ID),
                "${MediaStore.Video.Media.DURATION} > ?",
                arrayOf("1000"),
                null
            )?.use { cursor ->
                count = cursor.count
            }
        } catch (_: Exception) {}
        return count
    }

    private fun queryVideos(limit: Int?, offset: Int?): List<VideoItem> {
        val videos = mutableListOf<VideoItem>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Video.Media.DURATION} > ?",
                arrayOf("1000"),
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

                // Skip to offset
                val skipCount = offset ?: 0
                var skipped = 0
                while (skipped < skipCount && cursor.moveToNext()) {
                    skipped++
                }

                // Read up to limit
                val maxCount = limit ?: Int.MAX_VALUE
                var read = 0
                while (read < maxCount && cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "未知视频"
                    val duration = cursor.getLong(durationCol)
                    val size = cursor.getLong(sizeCol)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                    )
                    // Generate thumbnail URI — works on all API levels
                    val thumbnailUri = getThumbnailUri(id, uri)
                    videos.add(VideoItem(id, uri, name, duration, size, thumbnailUri))
                    read++
                }
            }
        } catch (_: Exception) {}

        return videos
    }

    /**
     * Get a thumbnail URI for a video.
     * Returns the video URI itself — Coil handles async thumbnail extraction.
     * No synchronous bitmap loading, so no main-thread blocking.
     */
    private fun getThumbnailUri(videoId: Long, videoUri: Uri): Uri? {
        // Just return the video URI; Coil's videoFrameFetcher will extract a frame
        return videoUri
    }

    /**
     * Clean up old thumbnail cache files (call periodically).
     */
    fun cleanupThumbnailCache() {
        try {
            val cacheDir = context.cacheDir
            cacheDir.listFiles()?.filter {
                it.name.startsWith("thumb_") && it.name.endsWith(".jpg")
            }?.forEach { file ->
                // Delete files older than 1 hour
                if (System.currentTimeMillis() - file.lastModified() > 3600_000) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }
}
