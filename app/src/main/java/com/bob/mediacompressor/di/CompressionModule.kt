package com.bob.mediacompressor.di

import com.bob.mediacompressor.data.repository.FFmpegVideoCompressor
import com.bob.mediacompressor.data.repository.Media3VideoCompressor
import com.bob.mediacompressor.domain.repository.VideoCompressor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FastCompressor

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class HighCompressor

@Module
@InstallIn(SingletonComponent::class)
abstract class CompressionModule {

    @Binds
    @Singleton
    @FastCompressor
    abstract fun bindFastVideoCompressor(
        media3VideoCompressor: Media3VideoCompressor
    ): VideoCompressor

    @Binds
    @Singleton
    @HighCompressor
    abstract fun bindHighVideoCompressor(
        ffmpegVideoCompressor: FFmpegVideoCompressor
    ): VideoCompressor
}
