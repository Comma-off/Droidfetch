// SPDX-FileCopyrightText: 2026 Comma
// SPDX-License-Identifier: GPL-3.0-or-later

package com.droid.fetch.ui

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt

private val Capsule = RoundedCornerShape(50)

/**
 * The Liquid Glass bar. Unlike the solid floating bar it behaves like a slab of liquid glass:
 *
 * - Clear, thick glass: light tint, strong rim refraction with dispersion, an inner shadow for
 *   depth, and a rim highlight whose light direction follows the phone's tilt.
 * - At rest the current tab sits on a soft tinted pill. Press or drag and a clear lens lifts out
 *   of the bar, bigger than it, refracting and magnifying the bar and icons beneath.
 * - The lens is jelly: it stretches along its direction of travel (from taps, drags and page
 *   swipes alike), wobbles as it settles, and rubber-bands past the first and last tab.
 */
@Composable
fun LiquidGlassBar(
    backdrop: Backdrop,
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

    val itemWidth = 76.dp
    val barHeight = if (showLabels) 66.dp else 58.dp
    val inset = 5.dp
    val itemWidthPx = with(density) { itemWidth.toPx() }
    val insetPx = with(density) { inset.toPx() }
    val maxOverscroll = itemWidthPx * 0.4f

    val lift = remember { Animatable(0f) }
    val stretch = remember { Animatable(0f) }
    val overscroll = remember { Animatable(0f) }
    // Separate flags: when a press turns into a drag, the tap detector lets go after the drag
    // detector has taken over, and a single flag would drop the lens mid-drag.
    var touching by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val pressed = touching || dragging
    val light by rememberLightAngle()

    LaunchedEffect(pressed) {
        if (pressed) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        lift.animateTo(
            if (pressed) 1f else 0f,
            spring(dampingRatio = if (pressed) 0.55f else 0.62f, stiffness = 420f),
        )
    }

    // Stretch with speed, whatever is moving the pager; an underdamped spring back gives the wobble.
    LaunchedEffect(pager) {
        var lastP = pager.currentPage + pager.currentPageOffsetFraction
        var lastT = 0L
        var settle: Job? = null
        snapshotFlow { pager.currentPage + pager.currentPageOffsetFraction }.collect { p ->
            val now = System.nanoTime()
            if (lastT != 0L) {
                val dt = ((now - lastT) / 1e9f).coerceAtLeast(0.004f)
                val speed = abs(p - lastP) / dt // tabs per second
                val target = (speed * 0.05f).coerceAtMost(0.3f)
                stretch.snapTo(stretch.value * 0.5f + target * 0.5f)
            }
            lastP = p
            lastT = now
            settle?.cancel()
            settle = launch {
                delay(60)
                stretch.animateTo(0f, spring(dampingRatio = 0.28f, stiffness = 260f))
            }
        }
    }

    val position = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, (tabs.size - 1).toFloat())
    val lensX = insetPx + position * itemWidthPx + overscroll.value

    val barGlass = rememberLayerBackdrop()
    val iconsLayer = rememberLayerBackdrop()
    // The lens sees the page, the bar's glass and the icons, in that order.
    val lensSource = rememberCombinedBackdrop(backdrop, barGlass, iconsLayer)

    fun lensScaleX() = (1f + 0.3f * lift.value) * (1f + stretch.value)
    fun lensScaleY() = (1f + 0.3f * lift.value) * (1f - stretch.value * 0.5f)

    Box(
        modifier
            .navigationBarsPadding()
            .padding(bottom = 14.dp)
            .size(width = itemWidth * tabs.size + inset * 2, height = barHeight)
            .pointerInput(tabs.size) {
                detectTapGestures(
                    onPress = {
                        touching = true
                        tryAwaitRelease()
                        touching = false
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
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        val target = (pager.currentPage + pager.currentPageOffsetFraction).roundToInt().coerceIn(0, tabs.lastIndex)
                        scope.launch { pager.animateScrollToPage(target) }
                        scope.launch { overscroll.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 380f)) }
                    },
                    onDragCancel = {
                        dragging = false
                        scope.launch { pager.animateScrollToPage(pager.currentPage) }
                        scope.launch { overscroll.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 380f)) }
                    },
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        val page = (pager.layoutInfo.pageSize + pager.layoutInfo.pageSpacing).toFloat()
                        val before = pager.currentPage
                        // Pulling back from an overscroll comes out of the rubber band first.
                        val intoBand = overscroll.value != 0f && (dx > 0f) == (overscroll.value < 0f)
                        val leftover = if (intoBand) {
                            dx
                        } else {
                            val consumed = pager.dispatchRawDelta(dx * page / itemWidthPx)
                            dx - consumed * itemWidthPx / page
                        }
                        if (abs(leftover) > 0.5f) {
                            // Resistance grows towards the limit, like stretching rubber.
                            val resistance = 1f - abs(overscroll.value) / maxOverscroll
                            val next = if (intoBand) overscroll.value + leftover else overscroll.value + leftover * 0.45f * resistance
                            scope.launch { overscroll.snapTo(next.coerceIn(-maxOverscroll, maxOverscroll)) }
                        }
                        if (pager.currentPage != before) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    },
                )
            },
    ) {
        // Glass slab.
        Box(
            Modifier
                .matchParentSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule },
                    effects = {
                        vibrancy()
                        blur(2.dp.toPx())
                        lens(24.dp.toPx(), 48.dp.toPx(), chromaticAberration = true)
                    },
                    highlight = {
                        Highlight(width = 1.dp, alpha = if (dark) 0.75f else 1f, style = HighlightStyle.Default(angle = light))
                    },
                    shadow = { Shadow(radius = 24.dp, color = Color.Black.copy(alpha = if (dark) 0.4f else 0.14f)) },
                    innerShadow = { InnerShadow(radius = 10.dp, color = Color.Black.copy(alpha = if (dark) 0.35f else 0.12f)) },
                    layerBlock = {
                        val s = 1f + 0.035f * lift.value
                        scaleX = s
                        scaleY = s
                    },
                    exportedBackdrop = barGlass,
                    onDrawSurface = { drawRect(cs.surfaceContainer.copy(alpha = if (dark) 0.26f else 0.36f)) },
                ),
        )

        // Resting pill under the current tab; fades as the lens lifts out.
        Box(
            Modifier
                .offset { IntOffset(lensX.roundToInt(), insetPx.roundToInt()) }
                .size(width = itemWidth, height = barHeight - inset * 2)
                .graphicsLayer {
                    alpha = 1f - lift.value
                    scaleX = 1f + stretch.value
                    scaleY = 1f - stretch.value * 0.5f
                }
                .background(cs.primary.copy(alpha = if (dark) 0.22f else 0.14f), Capsule),
        )

        // Icons, recorded so the lens can magnify them.
        Row(
            Modifier
                .layerBackdrop(iconsLayer)
                .padding(horizontal = inset)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { i, tab ->
                val closeness = (1f - abs(position - i)).coerceIn(0f, 1f)
                val tint = lerp(cs.onSurfaceContainerVariant, cs.primary, closeness)
                Column(
                    Modifier
                        .width(itemWidth)
                        .fillMaxHeight()
                        .graphicsLayer {
                            val s = 1f + 0.2f * lift.value * closeness
                            scaleX = s
                            scaleY = s
                        },
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

        // The lens: clear, lifted, refracting what's under it.
        Box(
            Modifier
                .offset { IntOffset(lensX.roundToInt(), insetPx.roundToInt()) }
                .size(width = itemWidth, height = barHeight - inset * 2)
                .drawBackdrop(
                    backdrop = lensSource,
                    shape = { Capsule },
                    effects = {
                        lens(14.dp.toPx(), 28.dp.toPx(), depthEffect = true, chromaticAberration = true)
                    },
                    highlight = {
                        Highlight(width = 1.2.dp, alpha = lift.value, style = HighlightStyle.Default(angle = light))
                    },
                    shadow = { Shadow(radius = 16.dp, color = Color.Black.copy(alpha = 0.2f * lift.value)) },
                    innerShadow = { InnerShadow(radius = 6.dp, color = Color.Black.copy(alpha = 0.25f * lift.value)) },
                    layerBlock = {
                        alpha = lift.value
                        scaleX = lensScaleX()
                        scaleY = lensScaleY()
                    },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = 0.05f * lift.value)) },
                ),
        )
    }
}

/**
 * Direction of the light on the glass rim, in degrees. It swings with the phone's tilt, low-pass
 * filtered so the highlight glides rather than jitters.
 */
@Composable
private fun rememberLightAngle(): State<Float> {
    val context = LocalContext.current
    val angle = remember { mutableFloatStateOf(45f) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val target = 45f - event.values[0] * 7f
                angle.floatValue += (target - angle.floatValue) * 0.2f
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm?.unregisterListener(listener) }
    }
    return angle
}
