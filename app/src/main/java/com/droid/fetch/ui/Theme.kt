package com.droid.fetch.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.droid.fetch.data.AppSettings
import com.droid.fetch.data.ColorSource
import com.droid.fetch.data.ThemeMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** True when the app is drawing dark, whichever way the theme mode resolved. */
@Composable
fun AppSettings.isDark(): Boolean = when (themeMode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

/**
 * miuix theme driven by settings. Monet takes the system's wallpaper palette; Custom seeds the
 * same Material You pipeline from a chosen accent, so palette styles apply there only.
 */
@Composable
fun DroidfetchTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val mode = when (settings.colorSource) {
        ColorSource.Off -> when (settings.themeMode) {
            ThemeMode.System -> ColorSchemeMode.System
            ThemeMode.Light -> ColorSchemeMode.Light
            ThemeMode.Dark -> ColorSchemeMode.Dark
        }
        else -> when (settings.themeMode) {
            ThemeMode.System -> ColorSchemeMode.MonetSystem
            ThemeMode.Light -> ColorSchemeMode.MonetLight
            ThemeMode.Dark -> ColorSchemeMode.MonetDark
        }
    }
    val keyColor = if (settings.colorSource == ColorSource.Custom) settings.accent.color else null
    val controller = remember(mode, keyColor, settings.paletteStyle) {
        ThemeController(colorSchemeMode = mode, keyColor = keyColor, paletteStyle = settings.paletteStyle)
    }
    MiuixTheme(controller = controller, content = content)
}
