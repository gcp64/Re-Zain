package com.bob.mediacompressor
 
import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
 
@HiltAndroidApp
class MediaCompressorApp : Application(), Configuration.Provider {
 
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
 
    override val workManagerConfiguration: Configuration
        get() {
            val factory = if (::workerFactory.isInitialized) {
                workerFactory
            } else {
                androidx.work.DelegatingWorkerFactory()
            }
            return Configuration.Builder()
                .setWorkerFactory(factory)
                .build()
        }
 
    override fun onCreate() {
        super.onCreate()
    }
}
