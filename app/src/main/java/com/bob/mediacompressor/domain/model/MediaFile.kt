package com.bob.mediacompressor.domain.model

import android.net.Uri

enum class MediaType {
    IMAGE, VIDEO
}

data class MediaFile(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val mediaType: MediaType,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0L
)
