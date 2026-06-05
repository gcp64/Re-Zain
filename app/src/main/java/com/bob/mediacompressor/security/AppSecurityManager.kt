package com.bob.mediacompressor.security

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.MessageDigest

object AppSecurityManager {

    private const val TAG = "AppSecurity"

    // PLACEHOLDER: Replace this with the actual SHA-256 base64/hex signature printed in Logcat
    private const val HARDCODED_SIGNATURE_HASH = "PLACEHOLDER_SIGNATURE_HASH"

    /**
     * Runs comprehensive runtime self-protection checks (RASP).
     * Bypasses termination to ensure the application executes on rooted systems and emulators.
     */
    fun performRuntimeSecurityChecks(activity: Activity) {
        if (isDebuggerConnected() || isRooted() || isEmulator() || isHooked()) {
            Log.w(TAG, "Security check detected a non-standard device environment, but execution continues.")
        }
    }

    /**
     * Computes and prints the current APK signing signature hash in Logcat,
     * but bypasses crash/termination checks to ensure maximum compatibility.
     */
    fun verifySignature(context: Context) {
        try {
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = context.packageManager.getPackageInfo(
                    context.packageName, 
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                val packageInfo = context.packageManager.getPackageInfo(
                    context.packageName, 
                    PackageManager.GET_SIGNATURES
                )
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            if (signatures.isNullOrEmpty()) {
                Log.w(TAG, "No signatures found.")
                return
            }

            // Compute SHA-256 hash of the signing certificate
            val certBytes = signatures[0].toByteArray()
            val md = MessageDigest.getInstance("SHA-256")
            val hashBytes = md.digest(certBytes)
            val currentHash = Base64.encodeToString(hashBytes, Base64.NO_WRAP)

            // Print signature so the developer can copy-paste it
            Log.i(TAG, "Current APK Signature Hash: $currentHash")

        } catch (e: Throwable) {
            Log.w(TAG, "Signature verification bypassed: ${e.message}")
        }
    }

    /**
     * Initializes and returns hardware-backed EncryptedSharedPreferences,
     * falling back to standard private SharedPreferences if initialization fails.
     */
    fun getEncryptedPreferences(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "secure_app_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Throwable) {
            Log.e(TAG, "EncryptedSharedPreferences initialization failed, falling back to standard SharedPreferences: ${e.message}")
            context.getSharedPreferences("secure_app_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    // --- Helper Checks ---

    private fun isDebuggerConnected(): Boolean {
        return Debug.isDebuggerConnected()
    }

    private fun isRooted(): Boolean {
        val rootPaths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )

        for (path in rootPaths) {
            if (File(path).exists()) return true
        }

        // Check build tags
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) return true

        // Check running su command
        var process: java.lang.Process? = null
        try {
            process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            val reader = process.inputStream.bufferedReader()
            if (reader.readLine() != null) return true
        } catch (t: Throwable) {
            // su not found
        } finally {
            process?.destroy()
        }

        return false
    }

    private fun isEmulator(): Boolean {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.BOARD.equals("QC_Reference_Phone")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator")
    }

    private fun isHooked(): Boolean {
        try {
            // Check for XposedBridge presence
            Class.forName("de.robv.android.xposed.XposedBridge")
            return true
        } catch (e: ClassNotFoundException) {
            // Xposed not found
        }

        // Check Frida hook artifacts in threads/memory
        try {
            val mapsFile = File("/proc/self/maps")
            if (mapsFile.exists()) {
                mapsFile.forEachLine { line ->
                    if (line.contains("frida") || line.contains("xposed")) {
                        return@forEachLine
                    }
                }
            }
        } catch (e: Exception) {
            // Ignored
        }

        return false
    }
}
