package com.droid.fetch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droid.fetch.data.AppSettings
import com.droid.fetch.data.LiveStats
import com.droid.fetch.data.StaticInfo
import com.droid.fetch.data.batterySection
import com.droid.fetch.data.formatBytes
import com.droid.fetch.data.formatKhz
import com.droid.fetch.data.memorySection
import com.droid.fetch.data.uptimeSection
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
private fun ShareAction(onShare: () -> Unit) {
    IconButton(onClick = onShare, modifier = Modifier.padding(end = 16.dp)) {
        Icon(Icons.Outlined.Share, contentDescription = "Share report", tint = MiuixTheme.colorScheme.onSurface)
    }
}

@Composable
fun SystemPage(info: StaticInfo?, live: LiveStats?, onShare: () -> Unit) {
    PageScaffold(title = "System", actions = { ShareAction(onShare) }) {
        if (info == null) return@PageScaffold
        item(key = "hero") {
            AndroidVersionCard(info.android, Modifier.padding(top = 4.dp, bottom = 6.dp))
        }
        infoSections(info.system)
        live?.uptimeSection()?.let { s -> item(key = "uptime") { InfoSectionCard(s) } }
    }
}

@Composable
fun HardwarePage(info: StaticInfo?, live: LiveStats?, settings: AppSettings, onShare: () -> Unit) {
    PageScaffold(title = "Hardware", actions = { ShareAction(onShare) }) {
        if (info == null) return@PageScaffold
        if (live != null) {
            item(key = "tiles") {
                Row(Modifier.padding(horizontal = 12.dp).padding(top = 4.dp, bottom = 6.dp), horizontalArrangement = CardSpacing) {
                    UsageTile(
                        icon = Icons.Outlined.Memory,
                        title = "RAM",
                        headline = "${info.ramMarketedGb} GB",
                        used = live.memTotal - live.memAvail,
                        total = live.memTotal,
                        modifier = Modifier.weight(1f),
                    )
                    UsageTile(
                        icon = Icons.Outlined.SdStorage,
                        title = "Storage",
                        headline = formatMarketedStorage(info.storageMarketedBytes),
                        used = live.storageTotal - live.storageFree,
                        total = live.storageTotal,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item(key = "cpu") { CpuCard(info, live) }
        }
        infoSections(info.hardware)
        live?.memorySection()?.let { s -> item(key = "memory") { InfoSectionCard(s) } }
        live?.batterySection(settings.tempUnit)?.let { s -> item(key = "battery") { InfoSectionCard(s) } }
    }
}

@Composable
fun OtherPage(info: StaticInfo?, onShare: () -> Unit) {
    PageScaffold(title = "Other", actions = { ShareAction(onShare) }) {
        if (info == null) return@PageScaffold
        item(key = "device") { DeviceCard(info) }
        infoSections(info.other)
    }
}

/** Storage is sold in decimal units; Settings shows "256 GB", not "238 GB". */
private fun formatMarketedStorage(bytes: Long): String {
    val gb = bytes / 1_000_000_000.0
    return if (gb >= 1000) "${(gb / 1000).let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it) }} TB" else "${gb.toInt()} GB"
}

@Composable
private fun UsageTile(icon: ImageVector, title: String, headline: String, used: Long, total: Long, modifier: Modifier) {
    val cs = MiuixTheme.colorScheme
    Card(modifier = modifier, insideMargin = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(cs.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.size(10.dp))
            Text(title, style = MiuixTheme.textStyles.body2, color = cs.onSurfaceVariantSummary)
        }
        Spacer(Modifier.height(14.dp))
        Text(headline, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
        Spacer(Modifier.height(10.dp))
        UsageBar(used.toFloat() / total, cs.primary, cs.primary.copy(alpha = 0.14f))
        Spacer(Modifier.height(8.dp))
        Text(
            "${formatBytes(used)} / ${formatBytes(total)}",
            style = MiuixTheme.textStyles.footnote1,
            color = cs.onSurfaceVariantSummary,
            maxLines = 1,
        )
    }
}

/** Chip name, GPU, and a live grid of per-core clocks against each core's peak. */
@Composable
private fun CpuCard(info: StaticInfo, live: LiveStats) {
    val cs = MiuixTheme.colorScheme
    SmallTitle("CPU clocks")
    Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp), insideMargin = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Text(info.socName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
        info.gpuRenderer?.let {
            Text(it, style = MiuixTheme.textStyles.body2, color = cs.onSurfaceVariantSummary)
        }
        Spacer(Modifier.height(14.dp))
        live.cores.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { core ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(cs.surfaceContainerHigh)
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                    ) {
                        Text("CPU ${core.index}", fontSize = 11.sp, color = cs.onSurfaceVariantSummary)
                        Text(
                            core.curKhz?.let { formatKhz(it) } ?: "Offline",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onSurface,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(6.dp))
                        val frac = if (core.curKhz != null && core.maxKhz != null && core.maxKhz > 0) core.curKhz.toFloat() / core.maxKhz else 0f
                        UsageBar(frac, cs.primary, cs.primary.copy(alpha = 0.14f), height = 4.dp)
                    }
                }
                // Keep the last row's tiles the same width as the others.
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DeviceCard(info: StaticInfo) {
    val cs = MiuixTheme.colorScheme
    Card(Modifier.padding(horizontal = 12.dp).padding(top = 4.dp, bottom = 6.dp), insideMargin = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(cs.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = cs.primary, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(info.manufacturer, style = MiuixTheme.textStyles.body2, color = cs.onSurfaceVariantSummary)
                Text(info.model, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
                if (info.deviceName != info.model) {
                    Text("“${info.deviceName}”", style = MiuixTheme.textStyles.body2, color = cs.onSurfaceVariantSummary)
                }
            }
        }
    }
}
