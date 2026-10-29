// SPDX-FileCopyrightText: 2026 Comma
// SPDX-License-Identifier: GPL-3.0-or-later

package com.droid.fetch.ui

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.droid.fetch.data.AndroidVersion
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The Android version hero card, in the manner of the OxygenOS 16 / ColorOS 17 "About device"
 * version card: a soft aurora wash in the theme's colors, an oversized glassy version numeral,
 * the release name beneath it and a strip of key facts along the bottom.
 *
 * Tapping it three times opens the version-bubbles Easter egg, as tapping the version in
 * Settings opens Android's own.
 */
@Composable
fun AndroidVersionCard(version: AndroidVersion, modifier: Modifier = Modifier) {
    val cs = MiuixTheme.colorScheme
    val dark = cs.surface.luminance() < 0.5f
    var showEgg by remember { mutableStateOf(false) }
    var taps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0L) }

    val accent = cs.primary
    val accentLight = lerp(accent, Color.White, if (dark) 0.55f else 0.35f)
    val accentDeep = lerp(accent, if (dark) Color.Black else Color(0xFF1A1A40), 0.25f)
    val glow2 = lerp(cs.tertiaryContainer, accent, 0.35f)

    Box(
        modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(cs.surfaceContainer)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                val now = System.currentTimeMillis()
                taps = if (now - lastTap < 500) taps + 1 else 1
                lastTap = now
                if (taps >= 3) {
                    taps = 0
                    showEgg = true
                }
            },
    ) {
        if (showEgg) VersionEasterEgg(version) { showEgg = false }

        Aurora(accent = accent, second = glow2, third = accentLight, dark = dark, modifier = Modifier.matchParentSize())

        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GlassNumeral(
                text = version.major,
                top = accentLight,
                mid = accent,
                bottom = accentDeep,
                dark = dark,
            )
            Text(
                text = "Android ${version.release}",
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
            )
            val sub = listOfNotNull(
                version.codename,
                if (version.isPreview) "Developer preview" else null,
            ).joinToString(" · ")
            if (sub.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(text = sub, style = MiuixTheme.textStyles.body2, color = cs.onSurfaceVariantSummary)
            }
            Spacer(Modifier.height(20.dp))
            FactStrip(
                facts = listOf(
                    "API level" to (version.sdkFull ?: version.sdk.toString()),
                    "Security patch" to (version.securityPatchShort ?: "—"),
                    "Build" to version.build,
                ),
                dark = dark,
            )
        }
    }
}

/** Large blurred colour blobs behind the numeral; the light is what makes the card read as glass. */
@Composable
private fun Aurora(accent: Color, second: Color, third: Color, dark: Boolean, modifier: Modifier) {
    val alpha = if (dark) 0.55f else 0.42f
    val blurred = if (Build.VERSION.SDK_INT >= 31) modifier.blur(56.dp, BlurredEdgeTreatment.Rectangle) else modifier
    Canvas(blurred) {
        val w = size.width
        val h = size.height
        fun blob(color: Color, center: Offset, radius: Float) = drawCircle(
            brush = Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)), center, radius),
            radius = radius,
            center = center,
        )
        blob(accent, Offset(w * 0.22f, h * 0.18f), w * 0.55f)
        blob(second, Offset(w * 0.86f, h * 0.30f), w * 0.48f)
        blob(third, Offset(w * 0.55f, h * 0.62f), w * 0.42f)
    }
}

/**
 * The version number as a block of tinted glass: a soft coloured shadow under it, a vertical
 * gradient body, a specular sheen across the top half and a thin lit rim.
 */
@Composable
private fun GlassNumeral(text: String, top: Color, mid: Color, bottom: Color, dark: Boolean) {
    val density = LocalDensity.current
    val size = if (text.length <= 2) 148.sp else 104.sp
    val base = TextStyle(
        fontSize = size,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.045).em,
        lineHeight = size,
        textAlign = TextAlign.Center,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    )
    val rimWidth = with(density) { 1.4.dp.toPx() }
    Box(contentAlignment = Alignment.Center) {
        // Coloured shadow, like light passing through the glass onto the card.
        Text(
            text = text,
            style = base.copy(color = mid.copy(alpha = if (dark) 0.55f else 0.40f)),
            modifier = Modifier
                .offset(y = 10.dp)
                .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(22.dp, BlurredEdgeTreatment.Unbounded) else Modifier),
        )
        // Body.
        Text(
            text = text,
            style = base.copy(
                brush = Brush.verticalGradient(listOf(top, mid, bottom)),
                alpha = 0.92f,
            ),
        )
        // Sheen over the upper half.
        Text(
            text = text,
            style = base.copy(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (dark) 0.42f else 0.62f),
                    0.48f to Color.White.copy(alpha = 0.06f),
                    0.5f to Color.Transparent,
                ),
            ),
        )
        // Rim light, brighter at the top-left where the "light" sits.
        Text(
            text = text,
            style = base.copy(
                brush = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.45f)),
                ),
                drawStyle = Stroke(width = rimWidth),
            ),
        )
    }
}

/** Three facts separated by hairlines, on a frosted strip. */
@Composable
private fun FactStrip(facts: List<Pair<String, String>>, dark: Boolean) {
    val cs = MiuixTheme.colorScheme
    Row(
        Modifier
            .padding(horizontal = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (dark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.55f))
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        facts.forEachIndexed { i, (label, value) ->
            if (i > 0) {
                Box(
                    Modifier
                        .width(0.6.dp)
                        .height(28.dp)
                        .background(cs.dividerLine),
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = value,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                )
                Text(
                    text = label,
                    style = MiuixTheme.textStyles.footnote1,
                    color = cs.onSurfaceVariantSummary,
                    maxLines = 1,
                    fontSize = 11.sp,
                )
            }
        }
    }
}
