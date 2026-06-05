package com.bob.mediacompressor.domain.usecase.converter

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageFormatConverter @Inject constructor() {
    fun convert(inputFile: File, outputFile: File, formatName: String): Flow<Int> = flow {
        emit(10)
        // 1. Decode bitmap from cached file
        val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath)
            ?: throw Exception("Failed to decode image file")
        emit(40)
        
        // 2. Select corresponding compression format
        val compressFormat = when (formatName.uppercase()) {
            "PNG" -> Bitmap.CompressFormat.PNG
            "WEBP" -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            }
            else -> Bitmap.CompressFormat.JPEG
        }
        
        emit(70)
        // 3. Compress and write to destination cache file
        FileOutputStream(outputFile).use { outStream ->
            val success = bitmap.compress(compressFormat, 90, outStream)
            if (!success) {
                throw Exception("Failed to compress image bitmap to format $formatName")
            }
        }
        
        emit(100)
    }.flowOn(Dispatchers.IO)
}
