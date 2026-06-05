package com.bob.mediacompressor.data.repository

import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.FFprobeKit
import com.bob.mediacompressor.domain.model.CompressionConfig
import com.bob.mediacompressor.domain.model.CompressionLevel
import com.bob.mediacompressor.domain.repository.VideoCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.util.StringJoiner
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FFmpegVideoCompressor @Inject constructor() : VideoCompressor {

    private var activeSessionId: Long? = null

    override fun compressVideo(
        inputFile: File,
        outputFile: File,
        config: CompressionConfig
    ): Flow<Float> = callbackFlow {
        // 1. Get video duration using FFprobe for progress tracking
        val infoSession = FFprobeKit.getMediaInformation(inputFile.absolutePath)
        val mediaInformation = infoSession.mediaInformation
        val durationSeconds = mediaInformation?.duration?.toDoubleOrNull() ?: 0.0

        // 2. Build FFmpeg command parameters
        val cmd = StringJoiner(" ")
        cmd.add("-y")
        cmd.add("-i").add("\"${inputFile.absolutePath}\"")

        // Codec selection (H.265 / HEVC for high compression efficiency)
        cmd.add("-c:v").add("libx265")

        // CRF setting (Constant Rate Factor) based on quality level
        val crf = when (config.level) {
            CompressionLevel.LOW -> 30      // Higher CRF = lower quality, smaller file
            CompressionLevel.MEDIUM -> 27   // Balanced
            CompressionLevel.HIGH -> 24     // Lower CRF = higher quality, larger file
        }
        cmd.add("-crf").add(crf.toString())

        // Preset (compression speed vs efficiency)
        cmd.add("-preset").add("fast")

        // Video filters (resolution & formatting)
        val videoFilters = StringJoiner(",")
        config.customResolutionHeight?.let { height ->
            // scale aspect ratio to target height, ensuring width is divisible by 2 for H.265
            videoFilters.add("scale=-2:$height")
        }
        config.customFps?.let { fps ->
            videoFilters.add("fps=fps=$fps")
        }
        // Always enforce standard YUV420P pixel format for wide device compatibility
        videoFilters.add("format=yuv420p")

        cmd.add("-vf").add("\"$videoFilters\"")

        // Audio configuration
        if (config.removeAudio) {
            cmd.add("-an")
        } else {
            cmd.add("-c:a").add("aac")
            cmd.add("-b:a").add("128k")
        }

        cmd.add("\"${outputFile.absolutePath}\"")

        val commandString = cmd.toString()

        // 3. Execute Async FFmpeg command
        val session = FFmpegKit.executeAsync(commandString, { completionSession ->
            val returnCode = completionSession.returnCode
            if (returnCode.isValueSuccess) {
                trySend(1.0f)
                close()
            } else if (returnCode.isValueCancel) {
                close(Exception("Compression canceled by user"))
            } else {
                close(Exception("FFmpeg failed with state: ${completionSession.state}, logs: ${completionSession.failStackTrace}"))
            }
        }, { /* Logs ignored here to avoid spamming callbacks */ }, { statistics ->
            if (durationSeconds > 0) {
                // statistics.time is in milliseconds
                val progress = (statistics.time / 1000.0) / durationSeconds
                trySend(progress.toFloat().coerceIn(0.0f, 0.99f))
            }
        })

        activeSessionId = session.sessionId

        awaitClose {
            // Cancel specific session if coroutine context is closed/cancelled
            activeSessionId?.let {
                FFmpegKit.cancel(it)
            }
            activeSessionId = null
        }
    }.flowOn(Dispatchers.IO)

    override fun cancel() {
        activeSessionId?.let {
            FFmpegKit.cancel(it)
        }
        activeSessionId = null
    }
}
