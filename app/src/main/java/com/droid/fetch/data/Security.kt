// SPDX-FileCopyrightText: 2026 Comma
// SPDX-License-Identifier: GPL-3.0-or-later

package com.droid.fetch.data

import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.os.Build
import android.provider.Settings
import java.io.File

/** Device security state, as far as an unprivileged app can see it. */
fun securitySection(context: Context): InfoSection? {
    val pm = context.packageManager
    return section("Security") {
        row("SELinux", selinux())
        row("Verified boot", verifiedBoot())
        row("Bootloader", bootloaderLock())
        row("Android Verified Boot", SysProps["ro.boot.vbmeta.avb_version"]?.let { "AVB $it" })
        row("Encryption", encryption())
        row("Security patch", formatIsoDate(Build.VERSION.SECURITY_PATCH))
        row("Vendor security patch", formatIsoDate(SysProps["ro.vendor.build.security_patch"]))
        row("Screen lock", context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure?.let { if (it) "Set" else "None" })
        row("Strong biometrics", strongBiometrics(context))
        row("Hardware keystore", keystore(pm))
        row("StrongBox", pm.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE).yesNo())
        row("Seccomp", seccomp())
        row("Root (su binary)", if (suPaths.any { File(it).exists() }) "Found" else "Not found")
        row("Developer options", globalFlag(context, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED))
        row("USB debugging", globalFlag(context, Settings.Global.ADB_ENABLED))
    }
}

private val suPaths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/sbin/su", "/vendor/bin/su", "/debug_ramdisk/su")

/**
 * Apps can't read SELinux state on modern Android. But being refused is itself the answer: a
 * permissive kernel logs denials without enforcing them, so a refused read means enforcing.
 */
private fun selinux(): String {
    readLine("/sys/fs/selinux/enforce")?.let { return if (it == "1") "Enforcing" else "Permissive" }
    runCommand("getenforce")?.takeIf { it == "Enforcing" || it == "Permissive" }?.let { return it }
    return "Enforcing (inferred)"
}

private fun verifiedBoot(): String? = when (val s = SysProps["ro.boot.verifiedbootstate"]) {
    "green" -> "Green · verified by the device's own key"
    "yellow" -> "Yellow · verified with a custom key"
    "orange" -> "Orange · not verified (unlocked)"
    "red" -> "Red · verification failed"
    null -> null
    else -> s
}

private fun bootloaderLock(): String? {
    SysProps["ro.boot.vbmeta.device_state"]?.let { return it.replaceFirstChar { c -> c.uppercase() } }
    return when (SysProps["ro.boot.flash.locked"]) {
        "1" -> "Locked"
        "0" -> "Unlocked"
        else -> null
    }
}

private fun encryption(): String? {
    if (SysProps["ro.crypto.state"] == "unencrypted") return "Not encrypted"
    return when (SysProps["ro.crypto.type"]) {
        "file" -> "File-based"
        "block" -> "Full-disk"
        else -> SysProps["ro.crypto.state"]?.replaceFirstChar { it.uppercase() }
    }
}

private fun strongBiometrics(context: Context): String? {
    if (Build.VERSION.SDK_INT < 30) return null
    val bm = context.getSystemService(BiometricManager::class.java) ?: return null
    return when (bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
        BiometricManager.BIOMETRIC_SUCCESS -> "Enrolled"
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "Available, not enrolled"
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "Not available"
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Temporarily unavailable"
        else -> "Unknown"
    }
}

/** FEATURE_HARDWARE_KEYSTORE's version is the KeyMint version × 100 (or Keymaster's × 10). */
private fun keystore(pm: PackageManager): String? {
    if (Build.VERSION.SDK_INT < 31) return null
    val v = pm.systemAvailableFeatures.firstOrNull { it.name == PackageManager.FEATURE_HARDWARE_KEYSTORE }?.version ?: return "Not reported"
    return if (v >= 100) "KeyMint ${v / 100}.${v % 100 / 10}" else "Keymaster ${v / 10}.${v % 10}"
}

private fun seccomp(): String? {
    val mode = runCatching {
        File("/proc/self/status").readLines().firstOrNull { it.startsWith("Seccomp:") }?.substringAfter(':')?.trim()
    }.getOrNull() ?: return null
    return when (mode) {
        "0" -> "Disabled"
        "1" -> "Strict"
        "2" -> "Filtering"
        else -> mode
    }
}

private fun globalFlag(context: Context, key: String): String =
    if (Settings.Global.getInt(context.contentResolver, key, 0) == 1) "On" else "Off"
