// SPDX-FileCopyrightText: 2026 Comma
// SPDX-License-Identifier: GPL-3.0-or-later

package com.droid.fetch.data

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import java.io.File

private data class Mount(val device: String, val point: String, val type: String, val options: List<String>)

private fun mounts(): List<Mount> = runCatching {
    File("/proc/mounts").readLines().mapNotNull { line ->
        val p = line.split(' ')
        if (p.size < 4) null else Mount(p[0], p[1], p[2], p[3].split(','))
    }
}.getOrDefault(emptyList())

/** Filesystem layout of internal storage: what's mounted where, and how. */
fun storageSection(context: Context): InfoSection? {
    val all = mounts()
    val data = all.lastOrNull { it.point == "/data" }
    return section("Storage") {
        row("Type", storageType(all))
        row("Data filesystem", data?.let { fsName(it.type) })
        row("Compression", data?.options?.firstOrNull { it.startsWith("compress_algorithm=") }?.substringAfter('=')?.uppercase())
        row("Encryption", listOfNotNull(
            when (SysProps["ro.crypto.type"]) { "file" -> "File-based"; "block" -> "Full-disk"; else -> null },
            if (data?.options?.contains("inlinecrypt") == true) "inline (hardware)" else null,
        ).joinToString(", ").ifEmpty { null })
        row("TRIM", data?.options?.let { if ("discard" in it) "Continuous" else "Scheduled" })
        for ((label, point) in listOf("System" to "/", "Vendor" to "/vendor", "Product" to "/product", "System ext" to "/system_ext")) {
            val m = all.lastOrNull { it.point == point } ?: continue
            val size = runCatching { StatFs(point).totalBytes }.getOrNull()
            val ro = if ("ro" in m.options) "read-only" else "read-write"
            row(label, listOfNotNull(size?.let(::formatBytes), fsName(m.type), ro).joinToString(" · "))
        }
        removableVolumes(context).forEach { (name, desc) -> row(name, desc) }
    }
}

private fun fsName(type: String): String = when (type) {
    "f2fs" -> "F2FS"
    "ext4" -> "ext4"
    "erofs" -> "EROFS"
    "vfat" -> "FAT32"
    "exfat" -> "exFAT"
    else -> type
}

/** Block device naming gives the bus away: sdX is UFS (SCSI), mmcblk is eMMC, nvme is NVMe. */
private fun storageType(all: List<Mount>): String? {
    val devices = all.map { it.device }
    return when {
        devices.any { it.startsWith("/dev/block/nvme") } -> "NVMe"
        devices.any { Regex("^/dev/block/sd[a-z]").containsMatchIn(it) } -> "UFS"
        devices.any { it.startsWith("/dev/block/mmcblk") } -> "eMMC"
        else -> null
    }
}

private fun removableVolumes(context: Context): List<Pair<String, String>> {
    val sm = context.getSystemService(StorageManager::class.java) ?: return emptyList()
    return sm.storageVolumes.filter { it.isRemovable }.map { v ->
        val dir = if (Build.VERSION.SDK_INT >= 30) v.directory else null
        val stat = dir?.let { runCatching { StatFs(it.path) }.getOrNull() }
        val desc = if (stat != null) "${formatBytes(stat.totalBytes - stat.availableBytes)} used of ${formatBytes(stat.totalBytes)}" else v.state
        v.getDescription(context) to desc
    }
}

// ---- Breakdown by category ---------------------------------------------------------------

data class StorageBreakdown(
    val total: Long,
    val free: Long,
    val apps: Long,
    val images: Long,
    val videos: Long,
    val audio: Long,
    val other: Long,
    val system: Long,
)

/** Category stats need Usage access, which the user grants in Settings. */
fun hasUsageAccess(context: Context): Boolean {
    val ops = context.getSystemService(AppOpsManager::class.java)
    @Suppress("DEPRECATION")
    val mode = if (Build.VERSION.SDK_INT >= 29) {
        ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    } else {
        ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

/**
 * How internal storage is used, split the way Settings › Storage splits it. "System" is whatever
 * the user-visible categories don't account for (the OS partitions, reserved blocks and so on).
 */
fun storageBreakdown(context: Context): StorageBreakdown? {
    if (!hasUsageAccess(context)) return null
    val ssm = context.getSystemService(StorageStatsManager::class.java) ?: return null
    return runCatching {
        val uuid = StorageManager.UUID_DEFAULT
        val user = Process.myUserHandle()
        val total = ssm.getTotalBytes(uuid)
        val free = ssm.getFreeBytes(uuid)
        val apps = ssm.queryStatsForUser(uuid, user).let { it.appBytes + it.dataBytes }
        val ext = ssm.queryExternalStatsForUser(uuid, user)
        val media = ext.imageBytes + ext.videoBytes + ext.audioBytes
        val other = (ext.totalBytes - media - ext.appBytes).coerceAtLeast(0)
        val used = total - free
        StorageBreakdown(
            total = total,
            free = free,
            apps = apps,
            images = ext.imageBytes,
            videos = ext.videoBytes,
            audio = ext.audioBytes,
            other = other,
            system = (used - apps - media - other).coerceAtLeast(0),
        )
    }.getOrNull()
}
