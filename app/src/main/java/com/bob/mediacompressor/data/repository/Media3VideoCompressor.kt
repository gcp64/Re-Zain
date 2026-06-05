package com.bob.mediacompressor.data.repository

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.bob.mediacompressor.domain.model.CompressionConfig
import com.bob.mediacompressor.domain.model.CompressionLevel
import com.bob.mediacompressor.domain.repository.VideoCompressor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Media3VideoCompressor @Inject constructor(
    @ApplicationContext private val context: Context
) : VideoCompressor {

    private var activeTransformer: Transformer? = null

    @OptIn(UnstableApi::class)
    override fun compressVideo(
        inputFile: File,
        outputFile: File,
        config: CompressionConfig
    ): Flow<Float> = callbackFlow {
        val inputUri = Uri.fromFile(inputFile)
        val outputPath = outputFile.absolutePath

        val decoderFactory = DefaultDecoderFactory(context)

        // Set target bitrate based on compression quality level
        val targetBitrate = when (config.level) {
            CompressionLevel.LOW -> 1_200_000 // 1.2 Mbps
            CompressionLevel.MEDIUM -> 2_500_000 // 2.5 Mbps
            CompressionLevel.HIGH -> 5_000_000 // 5.0 Mbps
        }

        // Configure Encoder Factory with VBR (Variable Bitrate) mode for better quality/size balance in hardware encoding
        val videoEncoderSettings = VideoEncoderSettings.Builder()
            .setBitrate(targetBitrate)
            .setBitrateMode(android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            .build()

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setEnableFallback(true)
            .setRequestedVideoEncoderSettings(videoEncoderSettings)
            .build()

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264) // Hardware H.264
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setAssetLoaderFactory(androidx.media3.transformer.DefaultAssetLoaderFactory(context, decoderFactory, false, androidx.media3.common.util.Clock.DEFAULT))
            .setEncoderFactory(encoderFactory)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    trySend(1.0f)
                    close()
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    close(exportException)
                }
            })
            .build()

        activeTransformer = transformer

        // Video effects (scaling)
        val effectsList = mutableListOf<androidx.media3.common.Effect>()
        config.customResolutionHeight?.let { height ->
            var h = height
            if (h % 2 != 0) h -= 1
            // standard 16:9 width logic
            val w = ((h * 16) / 9).let { if (it % 2 != 0) it - 1 else it }
            effectsList.add(Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT))
        }

        val mediaItem = MediaItem.fromUri(inputUri)
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(emptyList(), effectsList))
            .setRemoveAudio(config.removeAudio)
            .build()

        val composition = Composition.Builder(
            listOf(EditedMediaItemSequence(editedMediaItem))
        ).build()

        transformer.start(composition, outputPath)

        // Polling coroutine
        val progressJob = launch(Dispatchers.Default) {
            val progressHolder = ProgressHolder()
            while (true) {
                try {
                    val progressState = transformer.getProgress(progressHolder)
                    if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                        trySend(progressHolder.progress / 100f)
                    }
                    delay(250)
                } catch (e: Exception) {
                    // Ignore errors during progress retrieval if transformer gets canceled/finished
                }
            }
        }

        awaitClose {
            progressJob.cancel()
            transformer.cancel()
            activeTransformer = null
        }
    }.flowOn(Dispatchers.IO)

    override fun cancel() {
        activeTransformer?.cancel()
        activeTransformer = null
    }
}
