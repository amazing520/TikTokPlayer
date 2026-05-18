package com.tiktokplayer.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import android.util.Size
import java.io.ByteArrayOutputStream

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
     * Uses ContentResolver.loadThumbnail on API 29+ (handles scoped storage correctly),
     * falls back to deprecated Video.Thumbnails on older versions.
     */
    private fun getThumbnailUri(videoId: Long, videoUri: Uri): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+: use loadThumbnail which works with scoped storage
                // Returns a Bitmap — we store it as a content URI reference
                // For simplicity, return the video URI itself (PlayerView handles it)
                // The thumbnail is just a visual hint while video loads
                val bitmap = context.contentResolver.loadThumbnail(
                    videoUri, Size(320, 240), null as CancellationSignal?
                )
                if (bitmap != null) {
                    // Convert bitmap to a data URI that ImageView can use
                    bitmapToUri(bitmap)
                } else {
                    null
                }
            } else {
                // Android 9 and below: use deprecated Video.Thumbnails API
                ContentUris.withAppendedId(
                    MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI, videoId
                )
            }
        } catch (_: Exception) {
            // Thumbnail generation failed — not critical, just show black background
            null
        }
    }

    /**
     * Convert a Bitmap to a content Uri via a temporary file.
     * This avoids the deprecated Video.Thumbnails API on newer devices.
     */
    private fun bitmapToUri(bitmap: Bitmap): Uri? {
        return try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            val bytes = stream.toByteArray()
            // Use a temporary file in the app's cache directory
            val tempFile = java.io.File(context.cacheDir, "thumb_${System.nanoTime()}.jpg")
            tempFile.writeBytes(bytes)
            Uri.fromFile(tempFile)
        } catch (_: Exception) {
            null
        }
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
