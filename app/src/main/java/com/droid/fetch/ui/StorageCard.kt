// SPDX-FileCopyrightText: 2026 Comma
// SPDX-License-Identifier: GPL-3.0-or-later

package com.droid.fetch.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droid.fetch.data.StorageBreakdown
import com.droid.fetch.data.formatBytes
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Result of trying to read storage categories: null breakdown means Usage access is missing. */
data class StorageUsage(val breakdown: StorageBreakdown?)

/**
 * Storage split by category, like Settings › Storage: one segmented bar and a legend. Without
 * Usage access it offers to open the setting instead.
 */
@Composable
fun StorageUsageCard(usage: StorageUsage) {
    val context = LocalContext.current
    val cs = MiuixTheme.colorScheme
    SmallTitle("Storage usage")
    Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
        val b = usage.breakdown
        if (b == null) {
            ArrowPreference(
                title = "Show storage by category",
                summary = "Allow Usage access to see how much apps, photos, videos and audio take up",
                onClick = {
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        .setData(Uri.fromParts("package", context.packageName, null))
                    runCatching { context.startActivity(intent) }
                        .onFailure { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                },
            )
            return@Card
        }

        val palette = remember(cs.primary) { categoryColors(cs.primary) }
        val parts = listOf(
            Triple("Apps", b.apps, palette[0]),
            Triple("Images", b.images, palette[1]),
            Triple("Videos", b.videos, palette[2]),
            Triple("Audio", b.audio, palette[3]),
            Triple("Other files", b.other, palette[4]),
            Triple("System", b.system, palette[5]),
        )
        Box(Modifier.padding(16.dp)) {
            androidx.compose.foundation.layout.Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(formatBytes(b.total - b.free), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
                    Spacer(Modifier.width(6.dp))
                    Text("used of ${formatBytes(b.total)}", style = MiuixTheme.textStyles.body2, color = cs.onSurfaceVariantSummary, modifier = Modifier.padding(bottom = 3.dp))
                }
                Spacer(Modifier.height(12.dp))
                // Segmented bar; free space is the track showing through.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(cs.primary.copy(alpha = 0.12f)),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for ((_, bytes, color) in parts) {
                        val w = bytes.toFloat() / b.total
                        if (w > 0.002f) Box(Modifier.weight(w).fillMaxHeight().background(color))
                    }
                    val free = b.free.toFloat() / b.total
                    if (free > 0.002f) Spacer(Modifier.weight(free))
                }
                Spacer(Modifier.height(14.dp))
                for ((label, bytes, color) in parts + Triple("Free", b.free, cs.primary.copy(alpha = 0.18f))) {
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(10.dp))
                        Text(label, style = MiuixTheme.textStyles.body1, color = cs.onSurface, modifier = Modifier.weight(1f))
                        Text(formatBytes(bytes), style = MiuixTheme.textStyles.body2, color = cs.onSurfaceVariantActions)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    // Settings adds preinstalled system apps to Apps and leaves trashed media out,
                    // using per-app queries an unprivileged app can't make.
                    "Estimated. Android Settings counts preinstalled apps and the Bin differently, so its numbers can vary.",
                    style = MiuixTheme.textStyles.footnote1,
                    color = cs.onSurfaceVariantSummary,
                )
            }
        }
    }
}

/** Six hues spread from the theme accent, so the bar follows Monet. */
private fun categoryColors(primary: Color): List<Color> {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(primary.toArgb(), hsv)
    return (0 until 6).map { i ->
        val h = (hsv[0] + i * 52f) % 360f
        Color(android.graphics.Color.HSVToColor(floatArrayOf(h, maxOf(hsv[1], 0.5f), maxOf(hsv[2], 0.78f))))
    }
}
