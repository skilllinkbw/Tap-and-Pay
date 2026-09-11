package com.getauthepay.app.security

import android.annotation.SuppressLint

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import com.getauthepay.app.core.log.SecureLogger
import java.io.File

/**
 * Coarse device-integrity checks used to compute the device trust score
 * surfaced to the risk engine.
 *
 * No single client-side signal is reliable on its own; these signals are
 * *hints* for the risk engine, never the basis for a security decision.
 * Server-side attestation (Play Integrity / SafetyNet) is the
 * authoritative input.
 */
object DeviceSecurityChecker {

    fun trustScore(context: Context): Int {
        var score = 100

        if (hasObviousRootIndicators(context)) score -= 50
        if (isDebugBuild(context)) score -= 30
        if (isEmulator(context)) score -= 25
        if (isRunningOnAdb()) score -= 15
        if (isBootloaderUnlockedHint()) score -= 20

        return score.coerceIn(0, 100)
    }

    fun hasObviousRootIndicators(context: Context): Boolean {
        val paths = listOf(
            "/system/xbin/su", "/system/bin/su", "/sbin/su",
            "/system/app/Superuser.apk", "/data/local/xbin/su",
            "/data/local/bin/su", "/data/local/su",
        )
        for (p in paths) if (File(p).exists()) return true

        val suPackages = listOf(
            "com.topjohnwu.magisk", "eu.chainfire.supersu",
            "com.koushikdutta.superuser", "com.noshufou.android.su",
            "com.thirdparty.superuser",
        )
        val pm = context.packageManager
        for (pkg in suPackages) {
            runCatching {
                pm.getPackageInfo(pkg, 0)
                return true
            }
        }
        return false
    }

    fun isDebugBuild(context: Context): Boolean {
        return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    fun isEmulator(context: Context): Boolean {
        val fingerprints = listOf(
            Build.FINGERPRINT.startsWith("generic"),
            Build.FINGERPRINT.startsWith("unknown"),
            Build.MODEL.contains("google_sdk"),
            Build.MODEL.contains("Emulator"),
            Build.MODEL.contains("Android SDK built for"),
            Build.MANUFACTURER.contains("Genymotion"),
            Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"),
            "google_sdk" == Build.PRODUCT,
        )
        return fingerprints.any { it }
    }

    /** True if adb is currently connected. */
    @SuppressLint("PrivateApi") // deliberate, guarded by runCatching; only consulted when a debugger is attached
    fun isRunningOnAdb(): Boolean {
        return runCatching {
            Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, "activity") != null
        }.getOrDefault(false) && android.os.Debug.isDebuggerConnected()
    }

    /** Heuristic only. Bootloader unlock state is not directly readable. */
    @SuppressLint("PrivateApi") // deliberate, guarded by runCatching; heuristic only, failures return false
    fun isBootloaderUnlockedHint(): Boolean {
        val props = listOf("ro.boot.flash.locked", "ro.boot.verifiedbootstate")
        for (name in props) {
            val v = runCatching {
                Class.forName("android.os.SystemProperties")
                    .getMethod("get", String::class.java, String::class.java)
                    .invoke(null, name, "") as String
            }.getOrDefault("")
            if (v.isNotEmpty() && v != "1" && v != "green") return true
        }
        return false
    }

    fun logDeviceFingerprint(): Map<String, String> = mapOf(
        "manufacturer" to Build.MANUFACTURER,
        "model" to Build.MODEL,
        "sdk" to Build.VERSION.SDK_INT.toString(),
        "fingerprint" to Build.FINGERPRINT,
    )

    init {
        SecureLogger.event(
            event = "device.fingerprint.observed",
            extra = logDeviceFingerprint(),
        )
    }
}