package com.bob.mediacompressor.data.repository

import android.media.MediaExtractor
import android.media.MediaMuxer
import com.bob.mediacompressor.domain.model.CompressionConfig
import com.bob.mediacompressor.domain.model.CompressionLevel
import com.bob.mediacompressor.domain.repository.VideoCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FFmpegVideoCompressor @Inject constructor() : VideoCompressor {

    @Volatile
    private var isCancelled = false

    override fun compressVideo(
        inputFile: File,
        outputFile: File,
        config: CompressionConfig
    ): Flow<Float> = flow {
        isCancelled = false
        emit(0.0f)

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(inputFile.absolutePath)

            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val trackCount = extractor.trackCount
            val trackIndexMap = mutableMapOf<Int, Int>()

            // Select tracks (skip audio if removeAudio is true)
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                if (config.removeAudio && mime.startsWith("audio/")) {
                    continue
                }
                extractor.selectTrack(i)
                val muxerTrackIndex = muxer.addTrack(format)
                trackIndexMap[i] = muxerTrackIndex
            }

            muxer.start()

            val buffer = ByteBuffer.allocate(1024 * 1024) // 1MB buffer
            val bufferInfo = android.media.MediaCodec.BufferInfo()

            // Get total duration for progress
            var totalDuration = 0L
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val duration = if (format.containsKey(android.media.MediaFormat.KEY_DURATION)) {
                    format.getLong(android.media.MediaFormat.KEY_DURATION)
                } else 0L
                if (duration > totalDuration) totalDuration = duration
            }

            while (!isCancelled) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                val trackIndex = extractor.sampleTrackIndex
                val muxerIndex = trackIndexMap[trackIndex]
                if (muxerIndex != null) {
                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = extractor.sampleTime
                    bufferInfo.flags = extractor.sampleFlags

                    muxer.writeSampleData(muxerIndex, buffer, bufferInfo)
                }

                // Emit progress
                if (totalDuration > 0) {
                    val progress = (extractor.sampleTime.toFloat() / totalDuration).coerceIn(0.0f, 0.99f)
                    emit(progress)
                }

                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            emit(1.0f)
        } catch (e: Exception) {
            // If output file was partially written, delete it
            if (outputFile.exists()) outputFile.delete()
            throw e
        } finally {
            extractor.release()
        }
    }.flowOn(Dispatchers.IO)

    override fun cancel() {
        isCancelled = true
    }
}
