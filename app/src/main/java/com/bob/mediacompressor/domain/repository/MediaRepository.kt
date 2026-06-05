package com.bob.mediacompressor.domain.repository

import android.net.Uri
import com.bob.mediacompressor.domain.model.MediaFile
import java.io.File

interface MediaRepository {
    /**
     * Resolves the metadata (name, size, type, width, height, duration) of a given Content URI.
     */
    suspend fun getMediaFileMetadata(uri: Uri): MediaFile?

    /**
     * Copies a Uri stream into a temporary file in the application cache directory.
     * This is useful for FFmpeg commands requiring a raw filepath.
     */
    suspend fun copyUriToTempFile(uri: Uri, tempPrefix: String = "input_"): File?

    /**
     * Saves a compressed video file from app cache to the public gallery using MediaStore.
     * Uses IS_PENDING on API 29+ to prevent incomplete file reads by other apps.
     */
    suspend fun saveVideoToGallery(cacheFile: File, originalName: String): Result<Uri>

    /**
     * Saves a compressed image file from app cache to the public gallery using MediaStore.
     */
    suspend fun saveImageToGallery(cacheFile: File, originalName: String): Result<Uri>

    /**
     * Saves a GIF image file from app cache to the public gallery using MediaStore.
     */
    suspend fun saveGifToGallery(cacheFile: File, originalName: String): Result<Uri>

    /**
     * Saves an MP3 audio file from app cache to the public gallery using MediaStore.
     */
    suspend fun saveAudioToGallery(cacheFile: File, originalName: String): Result<Uri>
}
