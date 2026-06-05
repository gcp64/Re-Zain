package com.bob.mediacompressor.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.bob.mediacompressor.domain.model.CompressionLevel
import com.bob.mediacompressor.domain.model.MediaFile
import com.bob.mediacompressor.domain.model.MediaType
import com.bob.mediacompressor.domain.repository.MediaRepository
import com.bob.mediacompressor.worker.MediaCompressionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface UiState {
    object Idle : UiState
    data class LoadingMetadata(val uri: Uri) : UiState
    data class MetadataLoaded(val mediaFile: MediaFile) : UiState
    data class Compressing(val progress: Int) : UiState
    data class Success(val outputUri: String) : UiState
    data class Error(val message: String) : UiState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    companion object {
        const val UNIQUE_WORK_NAME = "media_compression_work"
        const val WORK_TAG = "compression_tag"
    }

    private val workManager = WorkManager.getInstance(context)

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Tab state (0: Compressor, 1: Converter)
    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    // Action state ("COMPRESS", "TO_GIF", "TO_AUDIO", "CONVERT_IMAGE")
    private val _actionType = MutableStateFlow("COMPRESS")
    val actionType: StateFlow<String> = _actionType.asStateFlow()

    // Image Format Conversion target ("JPEG", "PNG", "WEBP")
    private val _targetImageFormat = MutableStateFlow("WEBP")
    val targetImageFormat: StateFlow<String> = _targetImageFormat.asStateFlow()

    // Compression Config states
    private val _compressionLevel = MutableStateFlow(CompressionLevel.MEDIUM)
    val compressionLevel: StateFlow<CompressionLevel> = _compressionLevel.asStateFlow()

    private val _compressorType = MutableStateFlow("FAST") // FAST (Media3) or HIGH (FFmpeg)
    val compressorType: StateFlow<String> = _compressorType.asStateFlow()

    private val _removeAudio = MutableStateFlow(false)
    val removeAudio: StateFlow<Boolean> = _removeAudio.asStateFlow()

    private val _customHeight = MutableStateFlow("")
    val customHeight: StateFlow<String> = _customHeight.asStateFlow()

    private val _customFps = MutableStateFlow("")
    val customFps: StateFlow<String> = _customFps.asStateFlow()

    private val _selectedMediaList = MutableStateFlow<List<MediaFile>>(emptyList())
    val selectedMediaList: StateFlow<List<MediaFile>> = _selectedMediaList.asStateFlow()

    // Backward compatibility for single media views
    private val _selectedMedia = MutableStateFlow<MediaFile?>(null)
    val selectedMedia: StateFlow<MediaFile?> = _selectedMedia.asStateFlow()

    init {
        observeWork()
    }

    fun setCurrentTab(tab: Int) {
        _currentTab.value = tab
        // Default action type based on tab
        val media = _selectedMedia.value
        if (tab == 0) {
            _actionType.value = "COMPRESS"
        } else {
            if (media != null) {
                _actionType.value = if (media.mediaType == MediaType.VIDEO) "TO_GIF" else "CONVERT_IMAGE"
            } else {
                _actionType.value = "TO_GIF"
            }
        }
    }

    fun setActionType(action: String) {
        _actionType.value = action
    }

    fun setTargetImageFormat(format: String) {
        _targetImageFormat.value = format
    }

    fun selectMedia(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = UiState.LoadingMetadata(uri)
            val metadata = mediaRepository.getMediaFileMetadata(uri)
            if (metadata != null) {
                _selectedMediaList.value = listOf(metadata)
                _selectedMedia.value = metadata
                _uiState.value = UiState.MetadataLoaded(metadata)
                
                // Adjust default action type
                if (_currentTab.value == 1) {
                    _actionType.value = if (metadata.mediaType == MediaType.VIDEO) "TO_GIF" else "CONVERT_IMAGE"
                } else {
                    _actionType.value = "COMPRESS"
                }
            } else {
                _uiState.value = UiState.Error("Failed to load media metadata")
            }
        }
    }

    fun selectMultipleImages(uris: List<Uri>) {
        viewModelScope.launch {
            if (uris.isEmpty()) return@launch
            _uiState.value = UiState.LoadingMetadata(uris.first())
            val list = mutableListOf<MediaFile>()
            for (uri in uris) {
                val metadata = mediaRepository.getMediaFileMetadata(uri)
                if (metadata != null) {
                    list.add(metadata)
                }
            }
            if (list.isNotEmpty()) {
                _selectedMediaList.value = list
                _selectedMedia.value = list.first()
                _uiState.value = UiState.MetadataLoaded(list.first())
                _actionType.value = if (_currentTab.value == 1) "CONVERT_IMAGE" else "COMPRESS"
            } else {
                _uiState.value = UiState.Error("فشل في تحميل تفاصيل الصور المختارة")
            }
        }
    }

    fun setCompressionLevel(level: CompressionLevel) {
        _compressionLevel.value = level
    }

    fun setCompressorType(type: String) {
        _compressorType.value = type
    }

    fun setRemoveAudio(remove: Boolean) {
        _removeAudio.value = remove
    }

    fun setCustomHeight(height: String) {
        _customHeight.value = height
    }

    fun setCustomFps(fps: String) {
        _customFps.value = fps
    }

    fun startProcessing() {
        val mediaList = _selectedMediaList.value
        if (mediaList.isEmpty()) return

        val inputUrisString = mediaList.joinToString(",") { it.uri.toString() }
        val height = _customHeight.value.toIntOrNull() ?: -1
        val fps = _customFps.value.toIntOrNull() ?: -1

        val inputData = workDataOf(
            MediaCompressionWorker.KEY_INPUT_URI to inputUrisString,
            MediaCompressionWorker.KEY_ACTION_TYPE to _actionType.value,
            MediaCompressionWorker.KEY_IMAGE_FORMAT to _targetImageFormat.value,
            MediaCompressionWorker.KEY_COMPRESSION_LEVEL to _compressionLevel.value.name,
            MediaCompressionWorker.KEY_COMPRESSOR_TYPE to _compressorType.value,
            MediaCompressionWorker.KEY_REMOVE_AUDIO to _removeAudio.value,
            MediaCompressionWorker.KEY_CUSTOM_HEIGHT to height,
            MediaCompressionWorker.KEY_CUSTOM_FPS to fps
        )

        val workRequest = OneTimeWorkRequestBuilder<MediaCompressionWorker>()
            .setInputData(inputData)
            .addTag(WORK_TAG)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun startCompression() {
        startProcessing()
    }

    fun cancelCompression() {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
        _uiState.value = _selectedMedia.value?.let { UiState.MetadataLoaded(it) } ?: UiState.Idle
    }

    fun reset() {
        _selectedMediaList.value = emptyList()
        _selectedMedia.value = null
        _uiState.value = UiState.Idle
    }

    private fun observeWork() {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_WORK_NAME).collect { workInfos ->
                val workInfo = workInfos.firstOrNull() ?: return@collect
                
                when (workInfo.state) {
                    WorkInfo.State.RUNNING -> {
                        val progress = workInfo.progress.getInt("progress", 0)
                        _uiState.value = UiState.Compressing(progress)
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        val outputUri = workInfo.outputData.getString(MediaCompressionWorker.KEY_OUTPUT_URI) ?: ""
                        _uiState.value = UiState.Success(outputUri)
                    }
                    WorkInfo.State.FAILED -> {
                        val error = workInfo.outputData.getString(MediaCompressionWorker.KEY_ERROR) ?: "Processing failed"
                        _uiState.value = UiState.Error(error)
                    }
                    WorkInfo.State.CANCELLED -> {
                        _uiState.value = _selectedMedia.value?.let { UiState.MetadataLoaded(it) } ?: UiState.Idle
                    }
                    else -> {
                        _uiState.value = UiState.Compressing(0)
                    }
                }
            }
        }
    }
}
