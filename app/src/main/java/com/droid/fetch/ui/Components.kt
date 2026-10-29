// SPDX-FileCopyrightText: 2026 Comma
// SPDX-License-Identifier: GPL-3.0-or-later

package com.droid.fetch.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.droid.fetch.data.InfoItem
import com.droid.fetch.data.InfoSection
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import androidx.compose.foundation.layout.RowScope
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** Height the floating/fixed bottom bar takes, so page content can scroll clear of it. */
val LocalBottomBarHeight = compositionLocalOf { 0.dp }

/** One tab: a miuix large-title app bar over a scrolling list of cards. */
@Composable
fun PageScaffold(
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val scroll = MiuixScrollBehavior()
    Scaffold(
        topBar = { TopAppBar(title = title, scrollBehavior = scroll, actions = actions) },
        // The bottom inset is handled by the bar; the page only needs the status bar and cutout.
        contentWindowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scroll.nestedScrollConnection),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = LocalBottomBarHeight.current + 16.dp),
            overscrollEffect = null,
        ) {
            content()
        }
    }
}

fun LazyListScope.infoSections(sections: List<InfoSection>) {
    for (s in sections) {
        item(key = s.title) { InfoSectionCard(s) }
    }
}

@Composable
fun InfoSectionCard(section: InfoSection) {
    SmallTitle(section.title)
    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
        section.items.forEach { InfoRow(it) }
    }
}

/**
 * A label/value row; tapping copies the value, like long-press-to-copy in Settings' About.
 *
 * The value always stays in its own right-aligned column and wraps there, so long strings
 * (fingerprints, kernel builds, cluster layouts) never spill under the label. The label gets up
 * to half the row and takes only what it needs, leaving the rest to the value.
 */
@Composable
fun InfoRow(item: InfoItem) {
    val context = LocalContext.current
    BasicComponent(onClick = { copyToClipboard(context, item.label, item.value) }) {
        BoxWithConstraints {
            val labelMax = maxWidth * 0.5f
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.label,
                    modifier = Modifier.widthIn(max = labelMax),
                    fontSize = MiuixTheme.textStyles.headline1.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = item.value,
                    modifier = Modifier.weight(1f),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

fun copyToClipboard(context: Context, label: String, value: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, value))
    // Android 13+ shows its own clipboard confirmation.
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Copied $label", Toast.LENGTH_SHORT).show()
}

/** Thin rounded progress track, used for memory, storage and per-core clocks. */
@Composable
fun UsageBar(fraction: Float, color: Color, track: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(color),
        )
    }
}

val CardSpacing = Arrangement.spacedBy(12.dp)
