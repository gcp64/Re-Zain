package com.bob.mediacompressor.domain.repository

import com.bob.mediacompressor.domain.model.CompressionConfig
import kotlinx.coroutines.flow.Flow
import java.io.File

interface VideoCompressor {
    /**
     * Compresses the input video file into the output video file.
     * Emits progress values from 0.0f to 1.0f.
     */
    fun compressVideo(
        inputFile: File,
        outputFile: File,
        config: CompressionConfig
    ): Flow<Float>

    /**
     * Cancels the active compression process.
     */
    fun cancel()
}
