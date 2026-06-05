package com.bob.mediacompressor.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.bob.mediacompressor.domain.model.MediaFile
import com.bob.mediacompressor.domain.model.MediaType
import com.bob.mediacompressor.domain.repository.MediaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : MediaRepository {

    override suspend fun getMediaFileMetadata(uri: Uri): MediaFile? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            var name = "unknown_${System.currentTimeMillis()}"
            var size = 0L
            
            // Query MediaStore for name and size
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx != -1) name = cursor.getString(nameIdx)
                    if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                }
            }

            val mimeType = contentResolver.getType(uri) ?: getMimeTypeFromUri(uri)
            val isVideo = mimeType.startsWith("video/")
            val mediaType = if (isVideo) MediaType.VIDEO else MediaType.IMAGE

            var width = 0
            var height = 0
            var duration = 0L

            if (isVideo) {
                // Video Metadata extraction via Retriever
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L

                    // Handle video rotation
                    val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    if (rotation == 90 || rotation == 270) {
                        val temp = width
                        width = height
                        height = temp
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try {
                        retriever.release()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else {
                // Image Metadata extraction via BitmapFactory
                try {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeStream(stream, null, options)
                        width = options.outWidth
                        height = options.outHeight
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Fallback for file size if query returned 0
            if (size == 0L) {
                try {
                    contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                        size = fd.statSize
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            MediaFile(
                uri = uri,
                name = name,
                sizeBytes = size,
                mimeType = mimeType,
                mediaType = mediaType,
                width = width,
                height = height,
                durationMs = duration
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun copyUriToTempFile(uri: Uri, tempPrefix: String): File? = withContext(Dispatchers.IO) {
        try {
            val extension = getExtensionFromUri(uri)
            val cacheDir = context.cacheDir
            val tempFile = File.createTempFile(tempPrefix, ".$extension", cacheDir)
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun saveVideoToGallery(cacheFile: File, originalName: String): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val baseName = originalName.substringBeforeLast(".")
            val targetName = "${baseName}_compressed_${System.currentTimeMillis()}.mp4"

            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, targetName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/MediaCompressor")
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val itemUri = resolver.insert(collection, contentValues)
                ?: return@withContext Result.failure(Exception("Failed to insert video entry into MediaStore"))

            resolver.openOutputStream(itemUri).use { output ->
                if (output == null) return@withContext Result.failure(Exception("Failed to open output stream for inserted media"))
                cacheFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)
            }

            Result.success(itemUri)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    override suspend fun saveImageToGallery(cacheFile: File, originalName: String): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val baseName = originalName.substringBeforeLast(".")
            val cacheExt = cacheFile.extension.lowercase()
            val targetExt = if (cacheExt == "png" || cacheExt == "webp" || cacheExt == "jpg" || cacheExt == "jpeg") cacheExt else "jpg"
            val targetName = "${baseName}_compressed_${System.currentTimeMillis()}.$targetExt"
            val mimeType = when (targetExt) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                else -> "image/jpeg"
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, targetName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MediaCompressor")
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val itemUri = resolver.insert(collection, contentValues)
                ?: return@withContext Result.failure(Exception("Failed to insert image entry into MediaStore"))

            resolver.openOutputStream(itemUri).use { output ->
                if (output == null) return@withContext Result.failure(Exception("Failed to open output stream for inserted media"))
                cacheFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)
            }

            Result.success(itemUri)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    override suspend fun saveGifToGallery(cacheFile: File, originalName: String): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val baseName = originalName.substringBeforeLast(".")
            val targetName = "${baseName}_converted_${System.currentTimeMillis()}.gif"

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, targetName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/gif")
                put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MediaCompressor")
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val itemUri = resolver.insert(collection, contentValues)
                ?: return@withContext Result.failure(Exception("Failed to insert GIF entry into MediaStore"))

            resolver.openOutputStream(itemUri).use { output ->
                if (output == null) return@withContext Result.failure(Exception("Failed to open output stream for inserted media"))
                cacheFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)
            }

            Result.success(itemUri)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    override suspend fun saveAudioToGallery(cacheFile: File, originalName: String): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val baseName = originalName.substringBeforeLast(".")
            val targetName = "${baseName}_extracted_${System.currentTimeMillis()}.mp3"

            val contentValues = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, targetName)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                put(MediaStore.Audio.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.Audio.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/MediaCompressor")
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val itemUri = resolver.insert(collection, contentValues)
                ?: return@withContext Result.failure(Exception("Failed to insert audio entry into MediaStore"))

            resolver.openOutputStream(itemUri).use { output ->
                if (output == null) return@withContext Result.failure(Exception("Failed to open output stream for inserted media"))
                cacheFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)
            }

            Result.success(itemUri)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun getMimeTypeFromUri(uri: Uri): String {
        return if (uri.scheme == "file") {
            val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: "application/octet-stream"
        } else {
            "application/octet-stream"
        }
    }

    private fun getExtensionFromUri(uri: Uri): String {
        val mime = context.contentResolver.getType(uri) ?: getMimeTypeFromUri(uri)
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "tmp"
    }
}
