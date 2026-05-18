package com.tiktokplayer.data

import android.content.ContentUris
import android.content.Context
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
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Video.Media._ID),
            "${MediaStore.Video.Media.DURATION} > ?",
            arrayOf("1000"),
            null
        )?.use { cursor ->
            count = cursor.count
        }
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
                videos.add(VideoItem(id, uri, name, duration, size))
                read++
            }
        }

        return videos
    }
}
