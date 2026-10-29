// SPDX-FileCopyrightText: 2026 Comma
// SPDX-License-Identifier: GPL-3.0-or-later

package com.droid.fetch.data

import android.content.Context
import android.os.Build
import android.os.PowerManager
import java.io.File
import java.util.Locale

data class ThermalZone(val name: String, val tempC: Float)

data class ThermalState(
    val status: Int?,
    /** 1.0 = the point where the system starts throttling. */
    val headroomNow: Float?,
    val headroom10s: Float?,
    /** Null when the kernel's thermal sysfs is closed to apps, as on most modern devices. */
    val zones: List<ThermalZone>?,
)

/**
 * Thermal readings. The headroom API may only be called about once a second and answers NaN
 * otherwise, so the last real answer is kept.
 */
class ThermalReader(context: Context) {
    private val pm = context.getSystemService(PowerManager::class.java)
    private var lastNow: Float? = null
    private var last10s: Float? = null

    fun read(): ThermalState {
        if (Build.VERSION.SDK_INT >= 30 && pm != null) {
            pm.getThermalHeadroom(0).takeIf { !it.isNaN() }?.let { lastNow = it }
            pm.getThermalHeadroom(10).takeIf { !it.isNaN() }?.let { last10s = it }
        }
        return ThermalState(
            status = pm?.currentThermalStatus,
            headroomNow = lastNow,
            headroom10s = last10s,
            zones = zones(),
        )
    }

    private fun zones(): List<ThermalZone>? {
        val dirs = File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") } ?: return null
        return dirs.mapNotNull { dir ->
            val type = readLine("${dir.path}/type") ?: return@mapNotNull null
            val raw = readLine("${dir.path}/temp")?.toFloatOrNull() ?: return@mapNotNull null
            // Millidegrees on almost every kernel; a few report whole degrees.
            val c = if (raw > 1000 || raw < -1000) raw / 1000f else raw
            ThermalZone(type, c).takeIf { c > -40 && c < 150 }
        }.sortedBy { it.name }.ifEmpty { null }
    }
}

fun thermalSection(state: ThermalState, batteryC: Float?, unit: TempUnit): InfoSection? = section("Thermal") {
    row("Status", state.status?.let(::thermalStatusName))
    row("Headroom now", state.headroomNow?.let(::headroom))
    row("Headroom in 10 s", state.headroom10s?.let(::headroom))
    row("Battery", batteryC?.let { formatTemp(it, unit) })
    if (state.zones == null) {
        row("Sensor zones", "Restricted by Android")
    } else {
        for (z in state.zones) row(z.name, formatTemp(z.tempC, unit))
    }
}

private fun thermalStatusName(status: Int): String = when (status) {
    PowerManager.THERMAL_STATUS_NONE -> "Normal"
    PowerManager.THERMAL_STATUS_LIGHT -> "Light throttling"
    PowerManager.THERMAL_STATUS_MODERATE -> "Moderate throttling"
    PowerManager.THERMAL_STATUS_SEVERE -> "Severe throttling"
    PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
    PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
    PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutting down"
    else -> "Unknown"
}

/** Headroom as how much of the throttling threshold is used up. */
private fun headroom(h: Float): String =
    String.format(Locale.getDefault(), "%.0f%% of throttling point", h * 100)
