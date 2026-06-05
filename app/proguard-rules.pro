# ProGuard rules for MediaCompressor hardening

# Repackage all obfuscated classes into a single package to conceal package structure
-repackageclasses 'com.bob.mediacompressor.internal'
-allowaccessmodification

# Keep Android entry points and custom application class
-keep class com.bob.mediacompressor.MainActivity { *; }
-keep class com.bob.mediacompressor.MediaCompressorApp { *; }

# Hilt & Dependency Injection rules
-keep class * extends android.app.Application
-keep class * extends android.app.Activity
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.content.ContentProvider

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# WorkManager rules for background processing
-keep class androidx.work.Worker { *; }
-keep class androidx.work.ListenableWorker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keep @androidx.hilt.work.HiltWorker class * extends androidx.work.ListenableWorker { *; }

# Media3 Transformer keep rules
-keep class androidx.media3.transformer.** { *; }
-keep class androidx.media3.effect.** { *; }
-keep class androidx.media3.common.** { *; }

# FFmpegKit GPL community fork keep rules
-keep class com.antonkarpenko.ffmpegkit.** { *; }

# Keep data models
-keep class com.bob.mediacompressor.domain.model.** { *; }
