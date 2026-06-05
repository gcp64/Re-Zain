package com.bob.mediacompressor.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.bob.mediacompressor.domain.model.CompressionConfig
import com.bob.mediacompressor.domain.model.CompressionLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageCompressor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Compresses the input cache image file and saves the output as a JPEG to a target file.
     * Performs Downsampling (reducing dimensions) and quality reduction.
     */
    suspend fun compressImage(
        inputFile: File,
        outputFile: File,
        config: CompressionConfig
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(inputFile.absolutePath, options)

            // Determine target maximum dimensions based on compression quality level
            val maxDimension = when (config.level) {
                CompressionLevel.LOW -> 1080
                CompressionLevel.MEDIUM -> 1600
                CompressionLevel.HIGH -> 2048
            }

            // Calculate sample size for downsampling (memory efficient scaling)
            options.inSampleSize = calculateInSampleSize(options, maxDimension, maxDimension)
            options.inJustDecodeBounds = false

            val decodedBitmap = BitmapFactory.decodeFile(inputFile.absolutePath, options)
                ?: return@withContext Result.failure(Exception("Failed to decode image file"))

            // Compress to target JPEG file with chosen quality level
            val quality = when (config.level) {
                CompressionLevel.LOW -> 65
                CompressionLevel.MEDIUM -> 80
                CompressionLevel.HIGH -> 90
            }

            FileOutputStream(outputFile).use { outStream ->
                val success = decodedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outStream)
                decodedBitmap.recycle() // free native memory allocation immediately
                if (!success) {
                    return@withContext Result.failure(Exception("Bitmap compression returned false"))
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
