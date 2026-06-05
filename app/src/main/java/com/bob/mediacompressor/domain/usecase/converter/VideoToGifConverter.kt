package com.bob.mediacompressor.domain.usecase.converter

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoToGifConverter @Inject constructor() {
    fun convert(inputFile: File, outputFile: File): Flow<Int> = flow {
        emit(5)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(inputFile.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 5000L

            // Extract frames at ~10 fps, max 100 frames
            val intervalMs = 100L // 10 fps
            val frameCount = minOf((durationMs / intervalMs).toInt(), 100)
            if (frameCount <= 0) throw Exception("Video is too short to convert to GIF")

            val frames = mutableListOf<Bitmap>()
            for (i in 0 until frameCount) {
                val timeUs = i * intervalMs * 1000L
                val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    // Scale down to max 480px width
                    val scaledWidth = minOf(frame.width, 480)
                    val scaledHeight = (frame.height.toFloat() / frame.width * scaledWidth).toInt()
                    val scaled = Bitmap.createScaledBitmap(frame, scaledWidth, scaledHeight, true)
                    frames.add(scaled)
                    if (frame !== scaled) frame.recycle()
                }
                emit(((i + 1).toFloat() / frameCount * 80).toInt().coerceIn(5, 80))
            }

            if (frames.isEmpty()) throw Exception("Failed to extract any frames from video")

            emit(85)
            // Encode as animated GIF using AnimatedGifEncoder
            FileOutputStream(outputFile).use { fos ->
                val encoder = GifEncoder()
                encoder.start(fos)
                encoder.setDelay(intervalMs.toInt())
                encoder.setRepeat(0) // loop forever
                for (frame in frames) {
                    encoder.addFrame(frame)
                }
                encoder.finish()
            }

            // Recycle bitmaps
            frames.forEach { it.recycle() }
            emit(100)
        } finally {
            retriever.release()
        }
    }.flowOn(Dispatchers.IO)
}

/**
 * Minimal GIF encoder adapted from standard AnimatedGifEncoder.
 * Writes NeuQuant-quantized animated GIF files.
 */
class GifEncoder {
    private var out: java.io.OutputStream? = null
    private var width = 0
    private var height = 0
    private var delay = 100
    private var repeat = -1
    private var started = false
    private var firstFrame = true

    fun setDelay(ms: Int) { delay = ms }
    fun setRepeat(iter: Int) { repeat = iter }

    fun start(os: java.io.OutputStream) {
        out = os
        started = true
        firstFrame = true
    }

    fun addFrame(bitmap: Bitmap): Boolean {
        if (!started) return false
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        if (firstFrame) {
            width = w
            height = h
            writeHeader()
            if (repeat >= 0) writeNetscapeExt()
            firstFrame = false
        }

        // Quantize to 256 colors
        val rgbPixels = ByteArray(pixels.size * 3)
        for (i in pixels.indices) {
            rgbPixels[i * 3] = (pixels[i] shr 16 and 0xFF).toByte()
            rgbPixels[i * 3 + 1] = (pixels[i] shr 8 and 0xFF).toByte()
            rgbPixels[i * 3 + 2] = (pixels[i] and 0xFF).toByte()
        }

        // Simple median-cut palette (use first 256 unique colors or sample)
        val palette = buildPalette(pixels)
        val indexedPixels = ByteArray(pixels.size)
        for (i in pixels.indices) {
            indexedPixels[i] = findClosestColor(pixels[i], palette).toByte()
        }

        writeGraphicControlExt()
        writeImageDesc(w, h)
        writePalette(palette)
        writeLzwData(indexedPixels, palette.size)

        return true
    }

    fun finish() {
        out?.write(0x3B) // GIF trailer
        out?.flush()
        started = false
    }

    private fun writeHeader() {
        out?.write("GIF89a".toByteArray())
        // Logical screen descriptor
        writeShort(width)
        writeShort(height)
        out?.write(0x70) // No global color table, 8-bit color depth
        out?.write(0)    // Background color
        out?.write(0)    // Pixel aspect ratio
    }

    private fun writeNetscapeExt() {
        out?.write(0x21) // Extension
        out?.write(0xFF) // App extension
        out?.write(11)   // Block size
        out?.write("NETSCAPE2.0".toByteArray())
        out?.write(3)    // Sub-block size
        out?.write(1)    // Loop sub-block ID
        writeShort(repeat)
        out?.write(0)    // Block terminator
    }

    private fun writeGraphicControlExt() {
        out?.write(0x21)  // Extension
        out?.write(0xF9)  // GCE label
        out?.write(4)     // Block size
        out?.write(0)     // Packed bits
        writeShort(delay / 10) // Delay in centiseconds
        out?.write(0)     // Transparent color index
        out?.write(0)     // Block terminator
    }

    private fun writeImageDesc(w: Int, h: Int) {
        out?.write(0x2C) // Image separator
        writeShort(0)    // Left
        writeShort(0)    // Top
        writeShort(w)
        writeShort(h)
        out?.write(0x87) // Local color table, 256 entries (2^(7+1) = 256)
    }

    private fun writePalette(palette: IntArray) {
        for (color in palette) {
            out?.write(color shr 16 and 0xFF)
            out?.write(color shr 8 and 0xFF)
            out?.write(color and 0xFF)
        }
        // Pad to 256 entries
        for (i in palette.size until 256) {
            out?.write(0); out?.write(0); out?.write(0)
        }
    }

    private fun writeLzwData(pixels: ByteArray, paletteSize: Int) {
        val minCodeSize = 8
        out?.write(minCodeSize)

        // Simple uncompressed LZW: write each pixel as a single code
        val clearCode = 1 shl minCodeSize
        val eoiCode = clearCode + 1

        val buffer = ByteArrayOutputStream()
        val bitWriter = LzwBitWriter(buffer)
        val codeSize = minCodeSize + 1

        bitWriter.write(clearCode, codeSize)
        for (pixel in pixels) {
            bitWriter.write(pixel.toInt() and 0xFF, codeSize)
        }
        bitWriter.write(eoiCode, codeSize)
        bitWriter.flush()

        val data = buffer.toByteArray()
        var offset = 0
        while (offset < data.size) {
            val blockSize = minOf(255, data.size - offset)
            out?.write(blockSize)
            out?.write(data, offset, blockSize)
            offset += blockSize
        }
        out?.write(0) // Block terminator
    }

    private fun buildPalette(pixels: IntArray): IntArray {
        val colors = mutableSetOf<Int>()
        for (p in pixels) {
            colors.add(p and 0xF8F8F8.toInt()) // Reduce precision
            if (colors.size >= 256) break
        }
        // If less than 256, sample more
        if (colors.size < 256) {
            val step = maxOf(1, pixels.size / 256)
            for (i in pixels.indices step step) {
                colors.add(pixels[i] and 0xF8F8F8.toInt())
                if (colors.size >= 256) break
            }
        }
        return colors.toIntArray().copyOf(minOf(colors.size, 256))
    }

    private fun findClosestColor(color: Int, palette: IntArray): Int {
        val r = color shr 16 and 0xFF
        val g = color shr 8 and 0xFF
        val b = color and 0xFF
        var bestIndex = 0
        var bestDist = Int.MAX_VALUE
        for (i in palette.indices) {
            val pr = palette[i] shr 16 and 0xFF
            val pg = palette[i] shr 8 and 0xFF
            val pb = palette[i] and 0xFF
            val dist = (r - pr) * (r - pr) + (g - pg) * (g - pg) + (b - pb) * (b - pb)
            if (dist < bestDist) {
                bestDist = dist
                bestIndex = i
            }
        }
        return bestIndex
    }

    private fun writeShort(value: Int) {
        out?.write(value and 0xFF)
        out?.write(value shr 8 and 0xFF)
    }
}

private class LzwBitWriter(private val out: ByteArrayOutputStream) {
    private var buffer = 0
    private var bitsInBuffer = 0

    fun write(code: Int, codeSize: Int) {
        buffer = buffer or (code shl bitsInBuffer)
        bitsInBuffer += codeSize
        while (bitsInBuffer >= 8) {
            out.write(buffer and 0xFF)
            buffer = buffer shr 8
            bitsInBuffer -= 8
        }
    }

    fun flush() {
        if (bitsInBuffer > 0) {
            out.write(buffer and 0xFF)
        }
    }
}
