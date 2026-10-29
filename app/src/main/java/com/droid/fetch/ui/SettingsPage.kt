// SPDX-FileCopyrightText: 2026 Comma
// SPDX-License-Identifier: GPL-3.0-or-later

package com.droid.fetch.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.droid.fetch.BuildConfig
import com.droid.fetch.data.Accent
import com.droid.fetch.data.AppSettings
import com.droid.fetch.data.ColorSource
import com.droid.fetch.data.TempUnit
import com.droid.fetch.data.ThemeMode
import com.kyant.backdrop.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

private fun LazyListScope.group(title: String, content: @Composable () -> Unit) {
    item(key = title) {
        SmallTitle(title)
        Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) { content() }
    }
}

@Composable
fun SettingsPage(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit, onShare: () -> Unit) {
    val context = LocalContext.current
    // Refraction needs runtime shaders (Android 13+); below that the bar still blurs.
    val lensSupported = isRuntimeShaderSupported()

    PageScaffold(title = "Settings") {
        group("Appearance") {
            WindowDropdownPreference(
                title = "Theme",
                items = ThemeMode.entries.map { it.label },
                selectedIndex = settings.themeMode.ordinal,
                onSelectedIndexChange = { i -> update { it.copy(themeMode = ThemeMode.entries[i]) } },
            )
            WindowDropdownPreference(
                title = "Colors",
                summary = "Monet follows your wallpaper",
                items = ColorSource.entries.map { it.label },
                selectedIndex = settings.colorSource.ordinal,
                onSelectedIndexChange = { i -> update { it.copy(colorSource = ColorSource.entries[i]) } },
            )
            if (settings.colorSource == ColorSource.Custom) {
                WindowDropdownPreference(
                    title = "Accent color",
                    items = Accent.entries.map { it.label },
                    selectedIndex = settings.accent.ordinal,
                    onSelectedIndexChange = { i -> update { it.copy(accent = Accent.entries[i]) } },
                )
                WindowDropdownPreference(
                    title = "Palette style",
                    items = ThemePaletteStyle.entries.map { it.name.splitCamel() },
                    selectedIndex = settings.paletteStyle.ordinal,
                    onSelectedIndexChange = { i -> update { it.copy(paletteStyle = ThemePaletteStyle.entries[i]) } },
                )
            }
        }

        group("Bottom bar") {
            SwitchPreference(
                checked = settings.floatingBar,
                onCheckedChange = { v -> update { it.copy(floatingBar = v) } },
                title = "Floating bar",
                summary = "A capsule that hovers above the page",
            )
            AnimatedVisibility(
                visible = settings.floatingBar,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                SwitchPreference(
                    checked = settings.liquidGlass,
                    onCheckedChange = { v -> update { it.copy(liquidGlass = v) } },
                    title = "Liquid Glass",
                    summary = if (lensSupported) "Refract and blur the page behind the bar" else "Refraction needs Android 13; the bar will only blur",
                )
            }
            SwitchPreference(
                checked = settings.showLabels,
                onCheckedChange = { v -> update { it.copy(showLabels = v) } },
                title = "Show labels",
            )
        }

        group("Data") {
            SwitchPreference(
                checked = settings.liveUpdates,
                onCheckedChange = { v -> update { it.copy(liveUpdates = v) } },
                title = "Live updates",
                summary = "Refresh CPU clocks, memory and battery every second",
            )
            WindowDropdownPreference(
                title = "Temperature unit",
                items = TempUnit.entries.map { it.label },
                selectedIndex = settings.tempUnit.ordinal,
                onSelectedIndexChange = { i -> update { it.copy(tempUnit = TempUnit.entries[i]) } },
            )
            ArrowPreference(
                title = "Share report",
                summary = "Everything Droidfetch knows, as plain text",
                onClick = onShare,
            )
        }

        group("About") {
            BasicComponent(title = "Droidfetch", summary = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            BasicComponent(title = "Built with", summary = "miuix · Kyant Backdrop · Jetpack Compose")
            BasicComponent(
                title = "Android robot",
                summary = "Reproduced or modified from work created and shared by Google and used according to terms described in the Creative Commons 3.0 Attribution License.",
            )
            ArrowPreference(
                title = "License",
                summary = "GNU GPL v3 or later",
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.gnu.org/licenses/gpl-3.0.html"))) },
            )
            ArrowPreference(
                title = "App info",
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                },
            )
        }
    }
}

private fun String.splitCamel() = replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
