package com.droid.fetch.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val longDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
private val mediumDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val mediumDateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)

/** "2026-10-05" → "October 5, 2026" in the user's locale; unparsable input is returned as is. */
fun formatIsoDate(iso: String?): String? = iso?.let {
    runCatching { LocalDate.parse(it).format(longDate) }.getOrDefault(it)
}

/** "2026-10-05" → "Oct 5, 2026". */
fun formatIsoDateShort(iso: String?): String? = iso?.let {
    runCatching { LocalDate.parse(it).format(mediumDate) }.getOrDefault(it)
}

fun formatEpoch(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(mediumDateTime)

/** Binary units, shown the way Android Settings shows them ("7.4 GB"). */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(Locale.getDefault(), if (value >= 100) "%.0f %s" else "%.1f %s", value, units[unit])
}

/** Total RAM rounded up to the size it was sold as (the kernel reserves some). */
fun marketedRamGb(totalBytes: Long): Int {
    val gb = totalBytes / (1024.0 * 1024 * 1024)
    return listOf(1, 2, 3, 4, 6, 8, 10, 12, 16, 18, 20, 24, 32, 48, 64).firstOrNull { it >= gb } ?: kotlin.math.ceil(gb).toInt()
}

fun formatKhz(khz: Long): String =
    if (khz >= 1_000_000) String.format(Locale.getDefault(), "%.2f GHz", khz / 1_000_000.0)
    else "${khz / 1000} MHz"

fun formatDuration(millis: Long): String {
    val totalMinutes = millis / 60_000
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes / 60) % 24
    val minutes = totalMinutes % 60
    return buildList {
        if (days > 0) add("${days}d")
        if (days > 0 || hours > 0) add("${hours}h")
        add("${minutes}m")
    }.joinToString(" ")
}

fun formatTemp(celsius: Float, unit: TempUnit): String = when (unit) {
    TempUnit.Celsius -> String.format(Locale.getDefault(), "%.1f °C", celsius)
    TempUnit.Fahrenheit -> String.format(Locale.getDefault(), "%.1f °F", celsius * 9 / 5 + 32)
}

fun Boolean?.yesNo(): String? = when (this) {
    true -> "Yes"
    false -> "No"
    null -> null
}
