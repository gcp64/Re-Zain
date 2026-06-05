package com.bob.mediacompressor.domain.model

enum class CompressionLevel {
    LOW,    // Low file size, lower quality
    MEDIUM, // Balanced
    HIGH    // High quality, larger file size
}

data class CompressionConfig(
    val level: CompressionLevel = CompressionLevel.HIGH,
    val targetSizeMb: Float = 0f, // 0 means automatic based on level
    val removeAudio: Boolean = false,
    val customResolutionHeight: Int? = null,
    val customFps: Int? = null
)
