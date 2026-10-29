// SPDX-FileCopyrightText: 2026 Comma
// SPDX-License-Identifier: GPL-3.0-or-later

package com.droid.fetch.data

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.media.MediaRecorder
import android.os.Build
import java.util.Locale
import kotlin.math.hypot

/** One section per camera the system exposes, with what each can do. */
fun cameraSections(context: Context): List<InfoSection> {
    val cm = context.getSystemService(CameraManager::class.java) ?: return emptyList()
    val ids = runCatching { cm.cameraIdList.toList() }.getOrDefault(emptyList())
    return ids.mapNotNull { id ->
        val ch = runCatching { cm.getCameraCharacteristics(id) }.getOrNull() ?: return@mapNotNull null
        cameraSection(cm, id, ch)
    }
}

private fun cameraSection(cm: CameraManager, id: String, ch: CameraCharacteristics): InfoSection? {
    val facing = when (ch.get(CameraCharacteristics.LENS_FACING)) {
        CameraCharacteristics.LENS_FACING_FRONT -> "Front"
        CameraCharacteristics.LENS_FACING_BACK -> "Rear"
        else -> "External"
    }
    val caps = ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toSet() ?: emptySet()
    val pixels = ch.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
    val physical = ch.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
    val diagonalMm = physical?.let { hypot(it.width.toDouble(), it.height.toDouble()) }
    val focal = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
    val map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

    return section("$facing camera ($id)") {
        row("Resolution", pixels?.let {
            String.format(Locale.getDefault(), "%.1f MP · %d × %d", it.width * it.height / 1_000_000.0, it.width, it.height)
        })
        // Optical format is quoted as 1/x" where x ≈ 16 mm ÷ the sensor diagonal (a camera-tube legacy).
        row("Sensor size", diagonalMm?.let {
            String.format(Locale.getDefault(), "1/%.2f\" · %.2f × %.2f mm", 16 / it, physical.width, physical.height)
        })
        row("Aperture", ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.joinToString(", ") {
            String.format(Locale.getDefault(), "f/%.2g", it)
        })
        row("Focal length", focal?.let { f ->
            val equiv = diagonalMm?.let { (f * 43.27 / it).toInt() }
            String.format(Locale.getDefault(), "%.2f mm", f) + (equiv?.let { " (≈ $it mm equiv.)" } ?: "")
        })
        row("Optical stabilisation", ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
            ?.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON).yesNo())
        row("Autofocus", ch.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)?.let { if (it > 0f) "Yes" else "Fixed focus" })
        row("Flash", ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE).yesNo())
        if (Build.VERSION.SDK_INT >= 30) {
            row("Zoom", ch.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.let {
                String.format(Locale.getDefault(), "%.1f× – %.1f×", it.lower, it.upper)
            })
        }
        row("Max frame rate", ch.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.maxOfOrNull { it.upper }?.let { "$it fps" })
        row("Slow motion", map?.highSpeedVideoFpsRanges?.maxOfOrNull { it.upper }?.takeIf { it > 0 }?.let { "Up to $it fps" })
        row("Max video", maxVideo(map?.getOutputSizes(MediaRecorder::class.java)))
        row("RAW capture", (CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW in caps ||
            map?.outputFormats?.contains(ImageFormat.RAW_SENSOR) == true).yesNo())
        if (Build.VERSION.SDK_INT >= 33) {
            row("10-bit HDR video", (CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT in caps).yesNo())
        }
        row("Manual controls", (CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in caps).yesNo())
        row("Hardware level", hardwareLevel(ch.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)))
        if (CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA in caps) {
            row("Physical lenses", physicalLenses(cm, ch))
        }
    }
}

/** A logical camera fuses several lenses; list each one's focal length so wide/tele/ultrawide show. */
private fun physicalLenses(cm: CameraManager, ch: CameraCharacteristics): String? {
    val lenses = ch.physicalCameraIds.mapNotNull { pid ->
        val p = runCatching { cm.getCameraCharacteristics(pid) }.getOrNull() ?: return@mapNotNull null
        val f = p.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: return@mapNotNull null
        val diag = p.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.let { hypot(it.width.toDouble(), it.height.toDouble()) }
        val px = p.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        val mp = px?.let { String.format(Locale.getDefault(), "%.0f MP", it.width * it.height / 1_000_000.0) }
        val equiv = diag?.let { (f * 43.27 / it).toInt() }
        // Sorted widest first: ultrawide, wide, telephoto.
        (equiv ?: Int.MAX_VALUE) to listOfNotNull(equiv?.let { "$it mm" }, mp).joinToString(" · ")
    }.sortedBy { it.first }.map { it.second }
    return if (lenses.isEmpty()) "${ch.physicalCameraIds.size}" else "${lenses.size}: " + lenses.joinToString(", ")
}

/**
 * Largest 16:9 recording size. Cameras also list full-sensor 4:3 sizes for the recorder, which
 * aren't what anyone means by "max video".
 */
private fun maxVideo(sizes: Array<android.util.Size>?): String? {
    val wide = sizes?.filter { kotlin.math.abs(it.width.toFloat() / it.height - 16f / 9f) < 0.02f }
    val best = (wide?.takeIf { it.isNotEmpty() } ?: sizes?.toList())?.maxByOrNull { it.width.toLong() * it.height } ?: return null
    val short = minOf(best.width, best.height)
    val label = when {
        short >= 4320 -> "8K"
        short >= 2160 -> "4K"
        short >= 1440 -> "1440p"
        short >= 1080 -> "1080p"
        short >= 720 -> "720p"
        else -> null
    }
    return listOfNotNull(label, "${best.width} × ${best.height}").joinToString(" · ")
}

private fun hardwareLevel(level: Int?): String? = when (level) {
    CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "Legacy"
    CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "Limited"
    CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "Full"
    CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "Level 3"
    CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "External"
    null -> null
    else -> level.toString()
}
