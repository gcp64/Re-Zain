package com.bob.mediacompressor.domain.usecase.converter

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoToAudioExtractor @Inject constructor() {
    fun extract(inputFile: File, outputFile: File): Flow<Int> = flow {
        emit(5)
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(inputFile.absolutePath)

            // Find the audio track
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                throw Exception("No audio track found in video file")
            }

            extractor.selectTrack(audioTrackIndex)

            // Use MPEG_4 container for extracted audio (M4A - widely compatible)
            // Change output extension expectation: caller sends .mp3, but we produce .m4a-compatible
            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()

            val totalDuration = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION)
            } else 1L

            val buffer = ByteBuffer.allocate(1024 * 1024)
            val bufferInfo = android.media.MediaCodec.BufferInfo()

            emit(15)

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags

                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)

                if (totalDuration > 0) {
                    val progress = 15 + ((extractor.sampleTime.toFloat() / totalDuration) * 80).toInt()
                    emit(progress.coerceIn(15, 95))
                }

                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            emit(100)
        } catch (e: Exception) {
            if (outputFile.exists()) outputFile.delete()
            throw e
        } finally {
            extractor.release()
        }
    }.flowOn(Dispatchers.IO)
}
