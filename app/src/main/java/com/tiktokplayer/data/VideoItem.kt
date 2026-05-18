package com.tiktokplayer.data

import android.net.Uri

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val duration: Long, // milliseconds
    val size: Long,     // bytes
    val thumbnailUri: Uri? = null // video thumbnail/content URI
)
