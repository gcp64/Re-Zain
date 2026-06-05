package com.bob.mediacompressor.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.bob.mediacompressor.domain.model.CompressionConfig
import com.bob.mediacompressor.domain.model.CompressionLevel
import com.bob.mediacompressor.domain.model.MediaType
import com.bob.mediacompressor.domain.repository.MediaRepository
import com.bob.mediacompressor.domain.repository.VideoCompressor
import com.bob.mediacompressor.domain.usecase.ImageCompressor
import com.bob.mediacompressor.domain.usecase.converter.VideoToGifConverter
import com.bob.mediacompressor.domain.usecase.converter.VideoToAudioExtractor
import com.bob.mediacompressor.domain.usecase.converter.ImageFormatConverter
import com.bob.mediacompressor.di.FastCompressor
import com.bob.mediacompressor.di.HighCompressor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@HiltWorker
class MediaCompressionWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val mediaRepository: MediaRepository,
    private val imageCompressor: ImageCompressor,
    @FastCompressor private val media3VideoCompressor: VideoCompressor,
    @HighCompressor private val ffmpegVideoCompressor: VideoCompressor,
    private val videoToGifConverter: VideoToGifConverter,
    private val videoToAudioExtractor: VideoToAudioExtractor,
    private val imageFormatConverter: ImageFormatConverter
) : CoroutineWorker(context, workerParams) {

    private var activeCompressor: VideoCompressor? = null
    private var tempInputFile: File? = null
    private var tempOutputFile: File? = null

    companion object {
        const val KEY_ACTION_TYPE = "action_type" // "COMPRESS", "TO_GIF", "TO_AUDIO", "CONVERT_IMAGE"
        const val KEY_IMAGE_FORMAT = "image_format" // "JPEG", "PNG", "WEBP"

        const val KEY_INPUT_URI = "input_uri"
        const val KEY_COMPRESSION_LEVEL = "compression_level"
        const val KEY_COMPRESSOR_TYPE = "compressor_type" // "FAST" (Media3) or "HIGH" (FFmpeg)
        const val KEY_REMOVE_AUDIO = "remove_audio"
        const val KEY_CUSTOM_HEIGHT = "custom_height"
        const val KEY_CUSTOM_FPS = "custom_fps"

        const val KEY_OUTPUT_URI = "output_uri"
        const val KEY_ERROR = "error"
        
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "media_compression_channel"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val inputUriString = inputData.getString(KEY_INPUT_URI)
            ?: return@withContext Result.failure(workDataOf(KEY_ERROR to "Missing input URI"))

        val actionType = inputData.getString(KEY_ACTION_TYPE) ?: "COMPRESS"
        val uris = inputUriString.split(",")
        val totalFiles = uris.size
        
        // Start foreground notification immediately
        setForeground(createForegroundInfo(0, actionType, if (totalFiles > 1) "جاري بدء معالجة الصور المتعددة..." else null))

        try {
            if (totalFiles > 1) {
                // Batch processing mode for multiple images
                val outputUris = mutableListOf<String>()
                
                for ((index, uriStr) in uris.withIndex()) {
                    val currentUri = Uri.parse(uriStr)
                    val metadata = mediaRepository.getMediaFileMetadata(currentUri) ?: continue
                    
                    val tempIn = mediaRepository.copyUriToTempFile(currentUri, "batch_in_") ?: continue
                    val targetFormat = inputData.getString(KEY_IMAGE_FORMAT) ?: "JPEG"
                    val ext = if (actionType == "CONVERT_IMAGE") targetFormat.lowercase() else "jpg"
                    val tempOut = File(context.cacheDir, "batch_out_${UUID.randomUUID()}.$ext")
                    
                    // Update batch progress (e.g. 1st file starting = 0%, 2nd starting = 50% for 2 files)
                    val progressPercent = (index * 100 / totalFiles)
                    setProgress(workDataOf("progress" to progressPercent))
                    setForeground(
                        createForegroundInfo(
                            progressPercent, 
                            actionType, 
                            "جاري معالجة الصورة ${index + 1} من $totalFiles (${progressPercent}%)"
                        )
                    )
                    
                    if (actionType == "CONVERT_IMAGE") {
                        // Batch image format conversion
                        imageFormatConverter.convert(tempIn, tempOut, targetFormat).collect { /* sub-progress ignored */ }
                    } else {
                        // Batch image compression
                        val configLevel = CompressionLevel.valueOf(
                            inputData.getString(KEY_COMPRESSION_LEVEL) ?: CompressionLevel.MEDIUM.name
                        )
                        val config = CompressionConfig(level = configLevel)
                        imageCompressor.compressImage(tempIn, tempOut, config).getOrThrow()
                    }
                    
                    if (tempOut.exists() && tempOut.length() > 0L) {
                        val savedUriResult = mediaRepository.saveImageToGallery(tempOut, metadata.name)
                        savedUriResult.getOrNull()?.let {
                            outputUris.add(it.toString())
                        }
                    }
                    
                    tempIn.delete()
                    tempOut.delete()
                }
                
                setProgress(workDataOf("progress" to 100))
                setForeground(createForegroundInfo(100, actionType, "اكتملت معالجة $totalFiles صور بنجاح"))
                
                return@withContext Result.success(workDataOf(KEY_OUTPUT_URI to outputUris.joinToString(",")))
            } else {
                // Single file mode (Old code)
                val inputUri = Uri.parse(inputUriString)
                val metadata = mediaRepository.getMediaFileMetadata(inputUri)
                    ?: return@withContext Result.failure(workDataOf(KEY_ERROR to "Failed to query media metadata"))

                tempInputFile = mediaRepository.copyUriToTempFile(inputUri, "process_in_")
                    ?: return@withContext Result.failure(workDataOf(KEY_ERROR to "Failed to create local temp input file"))

                val extension = when (actionType) {
                    "TO_GIF" -> "gif"
                    "TO_AUDIO" -> "mp3"
                    "CONVERT_IMAGE" -> inputData.getString(KEY_IMAGE_FORMAT)?.lowercase() ?: "jpg"
                    else -> if (metadata.mediaType == MediaType.VIDEO) "mp4" else "jpg"
                }
                tempOutputFile = File(context.cacheDir, "process_out_${UUID.randomUUID()}.$extension")

                when (actionType) {
                    "TO_GIF" -> {
                        videoToGifConverter.convert(tempInputFile!!, tempOutputFile!!).collect { progress ->
                            setProgress(workDataOf("progress" to progress))
                            setForeground(createForegroundInfo(progress, actionType))
                        }
                    }
                    "TO_AUDIO" -> {
                        videoToAudioExtractor.extract(tempInputFile!!, tempOutputFile!!).collect { progress ->
                            setProgress(workDataOf("progress" to progress))
                            setForeground(createForegroundInfo(progress, actionType))
                        }
                    }
                    "CONVERT_IMAGE" -> {
                        val targetFormatName = inputData.getString(KEY_IMAGE_FORMAT) ?: "JPEG"
                        imageFormatConverter.convert(tempInputFile!!, tempOutputFile!!, targetFormatName).collect { progress ->
                            setProgress(workDataOf("progress" to progress))
                            setForeground(createForegroundInfo(progress, actionType))
                        }
                    }
                    else -> {
                        val configLevel = CompressionLevel.valueOf(
                            inputData.getString(KEY_COMPRESSION_LEVEL) ?: CompressionLevel.MEDIUM.name
                        )
                        val removeAudio = inputData.getBoolean(KEY_REMOVE_AUDIO, false)
                        val customHeight = inputData.getInt(KEY_CUSTOM_HEIGHT, -1).let { if (it == -1) null else it }
                        val customFps = inputData.getInt(KEY_CUSTOM_FPS, -1).let { if (it == -1) null else it }

                        val config = CompressionConfig(
                            level = configLevel,
                            removeAudio = removeAudio,
                            customResolutionHeight = customHeight,
                            customFps = customFps
                        )

                        if (metadata.mediaType == MediaType.VIDEO) {
                            val compressorType = inputData.getString(KEY_COMPRESSOR_TYPE) ?: "FAST"
                            val compressor = if (compressorType == "FAST") media3VideoCompressor else ffmpegVideoCompressor
                            activeCompressor = compressor

                            compressor.compressVideo(tempInputFile!!, tempOutputFile!!, config).collect { progress ->
                                val progressPercent = (progress * 100).toInt()
                                setProgress(workDataOf("progress" to progressPercent))
                                setForeground(createForegroundInfo(progressPercent, actionType))
                            }
                        } else {
                            setForeground(createForegroundInfo(30, actionType))
                            imageCompressor.compressImage(tempInputFile!!, tempOutputFile!!, config).getOrThrow()
                            setForeground(createForegroundInfo(100, actionType))
                        }
                    }
                }

                if (!tempOutputFile!!.exists() || tempOutputFile!!.length() == 0L) {
                    return@withContext Result.failure(workDataOf(KEY_ERROR to "Output file is empty or missing"))
                }

                val savedUriResult = when (actionType) {
                    "TO_GIF" -> mediaRepository.saveGifToGallery(tempOutputFile!!, metadata.name)
                    "TO_AUDIO" -> mediaRepository.saveAudioToGallery(tempOutputFile!!, metadata.name)
                    else -> {
                        if (metadata.mediaType == MediaType.VIDEO) {
                            mediaRepository.saveVideoToGallery(tempOutputFile!!, metadata.name)
                        } else {
                            mediaRepository.saveImageToGallery(tempOutputFile!!, metadata.name)
                        }
                    }
                }

                val savedUri = savedUriResult.getOrThrow()
                return@withContext Result.success(workDataOf(KEY_OUTPUT_URI to savedUri.toString()))
            }

        } catch (e: CancellationException) {
            activeCompressor?.cancel()
            return@withContext Result.failure(workDataOf(KEY_ERROR to "Task cancelled by user"))
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Result.failure(workDataOf(KEY_ERROR to (e.localizedMessage ?: "Unknown processing failure")))
        } finally {
            // Delete temp files
            tempInputFile?.delete()
            tempOutputFile?.delete()
        }
    }

    private fun createForegroundInfo(progress: Int, actionType: String, contentText: String? = null): ForegroundInfo {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Compressor Activity",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val title = when (actionType) {
            "TO_GIF" -> "تحويل الفيديو إلى GIF متحرك"
            "TO_AUDIO" -> "استخراج الصوت MP3 من الفيديو"
            "CONVERT_IMAGE" -> "تحويل صيغة الصورة"
            else -> "ضغط ملف الوسائط"
        }

        val defaultText = when (actionType) {
            "TO_GIF" -> "جاري تحويل الفيديو إلى GIF... $progress%"
            "TO_AUDIO" -> "جاري استخراج الصوت... $progress%"
            "CONVERT_IMAGE" -> "جاري تغيير صيغة الصورة... $progress%"
            else -> "جاري ضغط الملف... $progress%"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText ?: defaultText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .setOnlyAlertOnce(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}
