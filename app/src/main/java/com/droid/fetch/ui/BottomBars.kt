// SPDX-FileCopyrightText: 2026 Comma
// SPDX-License-Identifier: GPL-3.0-or-later

package com.droid.fetch.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt

data class Tab(val label: String, val icon: ImageVector)

private val Capsule = RoundedCornerShape(50)

/** The stock miuix bottom bar. */
@Composable
fun ClassicBottomBar(tabs: List<Tab>, pager: PagerState, showLabels: Boolean, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    NavigationBar(
        modifier = modifier,
        mode = if (showLabels) NavigationBarDisplayMode.IconAndText else NavigationBarDisplayMode.IconOnly,
    ) {
        tabs.forEachIndexed { i, tab ->
            NavigationBarItem(
                selected = pager.targetPage == i,
                onClick = { scope.launch { pager.animateScrollToPage(i) } },
                icon = tab.icon,
                label = tab.label,
            )
        }
    }
}

/**
 * Floating capsule bar: a solid capsule with a tinted pill marking the current tab.
 * The glass version is [LiquidGlassBar].
 *
 * The indicator follows the pager's scroll position, so swiping pages slides it continuously.
 * Dragging it scrolls the pager directly and it swells while held, like pressing on liquid.
 */
@Composable
fun FloatingBottomBar(
    tabs: List<Tab>,
    pager: PagerState,
    showLabels: Boolean,
    modifier: Modifier = Modifier,
) {
    val cs = MiuixTheme.colorScheme
    val dark = cs.surface.luminance() < 0.5f
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val itemWidth = 78.dp
    val barHeight = if (showLabels) 64.dp else 56.dp
    val inset = 4.dp
    val itemWidthPx = with(density) { itemWidth.toPx() }
    val insetPx = with(density) { inset.toPx() }

    var held by remember { mutableStateOf(false) }
    val swell by animateFloatAsState(
        targetValue = if (held) 1.16f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 500f),
        label = "swell",
    )
    val position = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, (tabs.size - 1).toFloat())

    Box(
        modifier
            .navigationBarsPadding()
            .padding(bottom = 14.dp)
            .size(width = itemWidth * tabs.size + inset * 2, height = barHeight)
            .pointerInput(tabs.size) {
                detectTapGestures(
                    onPress = {
                        held = true
                        tryAwaitRelease()
                        held = false
                    },
                    onTap = { pos ->
                        val i = ((pos.x - insetPx) / itemWidthPx).toInt().coerceIn(0, tabs.lastIndex)
                        if (i != pager.currentPage) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        scope.launch { pager.animateScrollToPage(i) }
                    },
                )
            }
            .pointerInput(tabs.size) {
                detectHorizontalDragGestures(
                    onDragStart = { held = true },
                    onDragEnd = {
                        held = false
                        val target = (pager.currentPage + pager.currentPageOffsetFraction).roundToInt().coerceIn(0, tabs.lastIndex)
                        scope.launch { pager.animateScrollToPage(target) }
                    },
                    onDragCancel = {
                        held = false
                        scope.launch { pager.animateScrollToPage(pager.currentPage) }
                    },
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        val page = pager.layoutInfo.pageSize + pager.layoutInfo.pageSpacing
                        val before = pager.currentPage
                        pager.dispatchRawDelta(dx * page / itemWidthPx)
                        if (pager.currentPage != before) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    },
                )
            },
    ) {
        val indicatorModifier = Modifier
            .offset { IntOffset((insetPx + position * itemWidthPx).roundToInt(), insetPx.roundToInt()) }
            .size(width = itemWidth, height = barHeight - inset * 2)

        // Solid capsule, as miuix's own FloatingNavigationBar draws it.
        Box(
            Modifier
                .matchParentSize()
                .shadow(if (dark) 0.dp else 10.dp, Capsule, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
                .background(cs.surfaceContainer, Capsule)
                .then(if (dark) Modifier.border(0.6.dp, Color.White.copy(alpha = 0.08f), Capsule) else Modifier),
        )
        Box(
            indicatorModifier
                .graphicsLayer {
                    scaleX = swell
                    scaleY = swell
                }
                .background(cs.primary.copy(alpha = if (dark) 0.22f else 0.12f), Capsule),
        )

        Row(
            Modifier
                .padding(horizontal = inset)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { i, tab ->
                // Colour by how close the drop is, so labels blend as it slides past.
                val closeness = (1f - abs(position - i)).coerceIn(0f, 1f)
                val tint = lerp(cs.onSurfaceContainerVariant, cs.primary, closeness)
                Column(
                    Modifier
                        .width(itemWidth)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(if (showLabels) 24.dp else 26.dp))
                    if (showLabels) {
                        Text(
                            text = tab.label,
                            color = tint,
                            fontSize = 11.sp,
                            fontWeight = if (closeness > 0.5f) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            modifier = Modifier.height(15.dp),
                        )
                    }
                }
            }
        }
    }
}
