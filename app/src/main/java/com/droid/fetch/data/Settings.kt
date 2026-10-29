// SPDX-FileCopyrightText: 2026 Comma
// SPDX-License-Identifier: GPL-3.0-or-later

package com.droid.fetch.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

enum class ThemeMode(val label: String) { System("Follow system"), Light("Light"), Dark("Dark") }

enum class ColorSource(val label: String) { Monet("Wallpaper (Monet)"), Custom("Custom color"), Off("Off (classic miuix)") }

/** Seed colors offered when the color source is [ColorSource.Custom]. */
enum class Accent(val label: String, val color: Color) {
    Blue("Blue", Color(0xFF3482FF)),
    Indigo("Indigo", Color(0xFF5B5FEF)),
    Purple("Purple", Color(0xFF9152E8)),
    Pink("Pink", Color(0xFFE5508E)),
    Red("Red", Color(0xFFE5483B)),
    Orange("Orange", Color(0xFFF2852A)),
    Yellow("Yellow", Color(0xFFE8B430)),
    Green("Green", Color(0xFF34A853)),
    Teal("Teal", Color(0xFF14A3A0)),
}

enum class TempUnit(val label: String) { Celsius("Celsius (°C)"), Fahrenheit("Fahrenheit (°F)") }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val colorSource: ColorSource = ColorSource.Monet,
    val accent: Accent = Accent.Blue,
    val paletteStyle: ThemePaletteStyle = ThemePaletteStyle.TonalSpot,
    val floatingBar: Boolean = false,
    /** Only takes effect on the floating bar. */
    val liquidGlass: Boolean = false,
    val showLabels: Boolean = true,
    val tempUnit: TempUnit = TempUnit.Celsius,
    val liveUpdates: Boolean = true,
)

val AppSettings.glassBar: Boolean get() = floatingBar && liquidGlass

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = state.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(state.value)
        state.value = next
        prefs.edit()
            .putString("themeMode", next.themeMode.name)
            .putString("colorSource", next.colorSource.name)
            .putString("accent", next.accent.name)
            .putString("paletteStyle", next.paletteStyle.name)
            .putBoolean("floatingBar", next.floatingBar)
            .putBoolean("liquidGlass", next.liquidGlass)
            .putBoolean("showLabels", next.showLabels)
            .putString("tempUnit", next.tempUnit.name)
            .putBoolean("liveUpdates", next.liveUpdates)
            .apply()
    }

    private fun read(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            themeMode = enumOr(prefs.getString("themeMode", null), d.themeMode),
            colorSource = enumOr(prefs.getString("colorSource", null), d.colorSource),
            accent = enumOr(prefs.getString("accent", null), d.accent),
            paletteStyle = enumOr(prefs.getString("paletteStyle", null), d.paletteStyle),
            // Before the floating bar existed, Liquid Glass implied it.
            floatingBar = prefs.getBoolean("floatingBar", prefs.getBoolean("liquidGlass", d.floatingBar)),
            liquidGlass = prefs.getBoolean("liquidGlass", d.liquidGlass),
            showLabels = prefs.getBoolean("showLabels", d.showLabels),
            tempUnit = enumOr(prefs.getString("tempUnit", null), d.tempUnit),
            liveUpdates = prefs.getBoolean("liveUpdates", d.liveUpdates),
        )
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default
}
