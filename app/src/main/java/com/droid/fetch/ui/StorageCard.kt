// SPDX-FileCopyrightText: 2026 Comma
// SPDX-License-Identifier: GPL-3.0-or-later

package com.droid.fetch.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SdStorage
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
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Result of trying to read storage categories: null breakdown means Usage access is missing. */
data class StorageUsage(val breakdown: StorageBreakdown?)

/**
 * The Hardware tab's storage card: headline size, a bar and a breakdown by category, like
 * Settings › Storage. Without Usage access it still shows used/free from the filesystem, with a
 * row offering to open the setting.
 *
 * [total] and [free] are the filesystem's numbers, used until (or unless) categories are known.
 */
@Composable
fun StorageCard(usage: StorageUsage?, headline: String, total: Long, free: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cs = MiuixTheme.colorScheme
    val b = usage?.breakdown
    val palette = remember(cs.primary) { categoryColors(cs.primary) }
    val parts = if (b == null) {
        listOf(Triple("Used", total - free, cs.primary))
    } else {
        listOf(
            Triple("Apps", b.apps, palette[0]),
            Triple("Images", b.images, palette[1]),
            Triple("Videos", b.videos, palette[2]),
            Triple("Audio", b.audio, palette[3]),
            Triple("Other files", b.other, palette[4]),
            Triple("System", b.system, palette[5]),
        )
    }
    val shownTotal = b?.total ?: total
    val shownFree = b?.free ?: free

    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(cs.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.SdStorage, contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text("Storage", style = MiuixTheme.textStyles.body2, color = cs.onSurfaceVariantSummary)
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(headline, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${formatBytes(shownTotal - shownFree)} used · ${formatBytes(shownFree)} free",
                    style = MiuixTheme.textStyles.body2,
                    color = cs.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
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
                    val w = bytes.toFloat() / shownTotal
                    if (w > 0.002f) Box(Modifier.weight(w).fillMaxHeight().background(color))
                }
                val freeW = shownFree.toFloat() / shownTotal
                if (freeW > 0.002f) Spacer(Modifier.weight(freeW))
            }
            if (b != null) {
                Spacer(Modifier.height(14.dp))
                for ((label, bytes, color) in parts) {
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
