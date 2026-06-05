package com.bob.mediacompressor.domain.usecase.converter

import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.FFprobeKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoToGifConverter @Inject constructor() {
    fun convert(inputFile: File, outputFile: File): Flow<Int> = callbackFlow {
        // 1. Fetch input video duration for progress tracking
        val infoSession = FFprobeKit.getMediaInformation(inputFile.absolutePath)
        val mediaInformation = infoSession.mediaInformation
        val durationSeconds = mediaInformation?.duration?.toDoubleOrNull() ?: 0.0

        // 2. Build double-palette professional FFmpeg GIF command
        // ffmpeg -i input.mp4 -vf "fps=15,scale=480:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse" output.gif
        val cmd = "-y -i \"${inputFile.absolutePath}\" -vf \"fps=15,scale=480:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse\" \"${outputFile.absolutePath}\""

        // 3. Execute command asynchronously
        val session = FFmpegKit.executeAsync(cmd, { completionSession ->
            val returnCode = completionSession.returnCode
            if (returnCode.isValueSuccess) {
                trySend(100)
                close()
            } else if (returnCode.isValueCancel) {
                close(Exception("GIF conversion canceled by user"))
            } else {
                close(Exception("FFmpeg GIF conversion failed with state: ${completionSession.state}"))
            }
        }, {}, { statistics ->
            if (durationSeconds > 0) {
                val progress = ((statistics.time / 1000.0) / durationSeconds) * 100
                trySend(progress.toInt().coerceIn(0, 99))
            }
        })

        awaitClose {
            FFmpegKit.cancel(session.sessionId)
        }
    }.flowOn(Dispatchers.IO)
}
