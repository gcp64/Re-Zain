# ProGuard rules for Re:Zain / MediaCompressor

# Repackage all obfuscated classes into a single package to conceal package structure
-repackageclasses 'com.bob.mediacompressor.internal'
-allowaccessmodification

# ---- Keep Android entry points and custom application class ----
-keep class com.bob.mediacompressor.MainActivity { *; }
-keep class com.bob.mediacompressor.MediaCompressorApp { *; }

# Keep all Android component subclasses
-keep class * extends android.app.Application
-keep class * extends android.app.Activity
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.content.ContentProvider

# ---- Annotations and Reflection ----
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations,RuntimeInvisibleParameterAnnotations

# ---- Hilt / Dagger DI Rules (Critical for startup) ----
-keep,allowobfuscation,allowshrinking class * extends dagger.hilt.internal.GeneratedComponent
-keep,allowobfuscation,allowshrinking class * extends dagger.hilt.internal.GeneratedComponentManager
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponent { *; }

# Keep Hilt generated component trees
-keep class **_HiltComponents* { *; }
-keep class **_HiltModules* { *; }
-keep class **_GeneratedInjector { *; }
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }

# Keep all Hilt entry points and modules
-keep @dagger.hilt.InstallIn class * { *; }
-keep @dagger.Module class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }

# Keep AssistedInject for WorkManager
-keep class * extends androidx.work.ListenableWorker
-keep @androidx.hilt.work.HiltWorker class * { *; }
-keepclassmembers class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Keep Assisted factories  
-keep class **_AssistedFactory { *; }
-keep class dagger.assisted.** { *; }

# ---- WorkManager rules for background processing ----
-keep class androidx.work.Worker { *; }
-keep class androidx.work.ListenableWorker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class androidx.work.impl.** { *; }

# ---- Jetpack Compose ----
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ---- Media3 Transformer keep rules ----
-keep class androidx.media3.transformer.** { *; }
-keep class androidx.media3.effect.** { *; }
-keep class androidx.media3.common.** { *; }

# ---- Security / Crypto ----
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }

# ---- Keep data models ----
-keep class com.bob.mediacompressor.domain.model.** { *; }

# ---- Keep security manager ----
-keep class com.bob.mediacompressor.security.** { *; }

# ---- Kotlin Coroutines ----
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# ---- Suppress harmless warnings ----
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
