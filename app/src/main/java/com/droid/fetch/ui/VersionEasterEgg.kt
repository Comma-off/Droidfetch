// SPDX-FileCopyrightText: 2026 Comma
// SPDX-License-Identifier: GPL-3.0-or-later

package com.droid.fetch.ui

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.droid.fetch.data.AndroidVersion
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

private data class Release(val label: String, val name: String?, val year: Int, val sdk: Int)

/** Every major release with a public name, oldest first. */
private val Releases = listOf(
    Release("1.5", "Cupcake", 2009, 3),
    Release("1.6", "Donut", 2009, 4),
    Release("2.0", "Eclair", 2009, 5),
    Release("2.2", "Froyo", 2010, 8),
    Release("2.3", "Gingerbread", 2010, 9),
    Release("3", "Honeycomb", 2011, 11),
    Release("4.0", "Ice Cream Sandwich", 2011, 14),
    Release("4.1", "Jelly Bean", 2012, 16),
    Release("4.4", "KitKat", 2013, 19),
    Release("5", "Lollipop", 2014, 21),
    Release("6", "Marshmallow", 2015, 23),
    Release("7", "Nougat", 2016, 24),
    Release("8", "Oreo", 2017, 26),
    Release("9", "Pie", 2018, 28),
    Release("10", "Quince Tart", 2019, 29),
    Release("11", "Red Velvet Cake", 2020, 30),
    Release("12", "Snow Cone", 2021, 31),
    Release("13", "Tiramisu", 2022, 33),
    Release("14", "Upside Down Cake", 2023, 34),
    Release("15", "Vanilla Ice Cream", 2024, 35),
    Release("16", "Baklava", 2025, 36),
    Release("17", "Cinnamon Bun", 2026, 37),
)

private class Bubble(
    val release: Release,
    val radius: Float,
    val color: Color,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
)

private class Pop(val x: Float, val y: Float, val radius: Float, val color: Color, val startNanos: Long)

private const val Restitution = 0.72f
private const val PopNanos = 380_000_000L

/**
 * The version card's Easter egg: one glass bubble per Android release up to this device's,
 * tumbling under real gravity. Tilt the phone to pour them around; tap one to pop it and see
 * which release it was. Popping the last one closes the Easter egg.
 */
@Composable
fun VersionEasterEgg(version: AndroidVersion, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BubbleField(version, onDismiss)
    }
}

@Composable
private fun BubbleField(version: AndroidVersion, onDismiss: () -> Unit) {
    val cs = MiuixTheme.colorScheme
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()

    val releases = remember(version.sdk) {
        Releases.filter { it.sdk <= version.sdk }.ifEmpty { Releases.takeLast(1) }
    }
    // A wheel of hues around the theme's accent, so the field follows Monet.
    val colors = remember(cs.primary, releases.size) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(cs.primary.toArgb(), hsv)
        releases.indices.map { i ->
            val h = (hsv[0] + i * 360f / releases.size) % 360f
            Color(android.graphics.Color.HSVToColor(floatArrayOf(h, max(hsv[1], 0.45f), max(hsv[2], 0.8f))))
        }
    }

    val gravity = rememberGravity()
    val bubbles = remember { mutableListOf<Bubble>() }
    val pops = remember { mutableListOf<Pop>() }
    var field by remember { mutableStateOf(Size.Zero) }
    var frame by remember { mutableLongStateOf(0L) }
    var popped by remember { mutableStateOf<Release?>(null) }
    var cleared by remember { mutableStateOf(false) }

    // Last bubble popped: let its ripple finish, then go back to the card.
    LaunchedEffect(cleared) {
        if (cleared) {
            delay(PopNanos / 1_000_000 + 150)
            onDismiss()
        }
    }

    fun burst() {
        if (field == Size.Zero) return
        val cx = field.width / 2
        val cy = field.height / 2
        bubbles.clear()
        releases.forEachIndexed { i, r ->
            // Newer releases are bigger; the device's own is the biggest.
            val t = i / max(1f, (releases.size - 1).toFloat())
            val dp = if (i == releases.lastIndex) 62f else 28f + 14f * t
            val speed = with(density) { Random.nextFloat() * 900.dp.toPx() + 300.dp.toPx() }
            val angle = Random.nextFloat() * (2 * Math.PI).toFloat()
            bubbles += Bubble(
                release = r,
                radius = with(density) { dp.dp.toPx() },
                color = colors[i],
                x = cx + Random.nextFloat() * 4 - 2,
                y = cy + Random.nextFloat() * 4 - 2,
                vx = kotlin.math.cos(angle) * speed,
                vy = kotlin.math.sin(angle) * speed,
            )
        }
    }

    LaunchedEffect(field) {
        if (field == Size.Zero) return@LaunchedEffect
        if (bubbles.isEmpty()) burst()
        val g = with(density) { 260.dp.toPx() } // px/s² per 1 m/s² of real gravity
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(1 / 30f)
                last = now
                step(bubbles, field, -gravity[0] * g, gravity[1] * g, dt)
                pops.removeAll { now - it.startNanos > PopNanos }
                frame = now
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(cs.surface.copy(alpha = 0.97f)),
    ) {
        // The device's version, huge and faint behind the bubbles.
        Text(
            text = version.major,
            modifier = Modifier.align(Alignment.Center),
            fontSize = 260.sp,
            fontWeight = FontWeight.Bold,
            color = cs.primary.copy(alpha = 0.07f),
        )

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        // Topmost (last drawn) bubble under the finger.
                        val hit = bubbles.lastOrNull { b ->
                            val dx = pos.x - b.x
                            val dy = pos.y - b.y
                            dx * dx + dy * dy <= b.radius * b.radius
                        } ?: return@detectTapGestures
                        bubbles.remove(hit)
                        pops += Pop(hit.x, hit.y, hit.radius, hit.color, frame)
                        popped = hit.release
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        if (bubbles.isEmpty()) cleared = true
                    }
                },
        ) {
            if (size != field) field = size
            @Suppress("UNUSED_EXPRESSION") frame // redraw every physics frame

            for (p in pops) {
                val t = ((frame - p.startNanos).toFloat() / PopNanos).coerceIn(0f, 1f)
                drawCircle(
                    color = p.color.copy(alpha = (1f - t) * 0.8f),
                    radius = p.radius * (1f + t * 0.9f),
                    center = Offset(p.x, p.y),
                    style = Stroke(width = p.radius * 0.18f * (1f - t)),
                )
            }

            for (b in bubbles) {
                val c = Offset(b.x, b.y)
                // Glass body: light from the top-left, deeper colour at the far edge.
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(lerp(b.color, Color.White, 0.45f), b.color, lerp(b.color, Color.Black, 0.18f)),
                        center = c + Offset(-b.radius * 0.35f, -b.radius * 0.4f),
                        radius = b.radius * 1.5f,
                    ),
                    radius = b.radius,
                    center = c,
                    alpha = 0.9f,
                )
                drawCircle(Color.White.copy(alpha = 0.35f), b.radius, c, style = Stroke(width = b.radius * 0.05f))
                // Specular highlight.
                drawOval(
                    color = Color.White.copy(alpha = 0.55f),
                    topLeft = c + Offset(-b.radius * 0.6f, -b.radius * 0.72f),
                    size = Size(b.radius * 0.62f, b.radius * 0.36f),
                )
                // The robot in the upper part of the bubble, the version number beneath it.
                drawAndroidRobot(
                    origin = c + Offset(0f, -b.radius * 0.47f),
                    scale = b.radius * 0.95f,
                    color = Color.White.copy(alpha = 0.95f),
                    eyes = b.color,
                )
                val label = measurer.measure(
                    b.release.label,
                    TextStyle(
                        color = Color.White,
                        fontSize = with(density) { (b.radius * if (b.release.label.length > 2) 0.3f else 0.36f).toSp() },
                        fontWeight = FontWeight.Bold,
                    ),
                )
                drawText(label, topLeft = c + Offset(-label.size.width / 2f, b.radius * 0.45f - label.size.height / 2f))
            }
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp),
        ) {
            Icon(Icons.Outlined.Close, contentDescription = "Close", tint = cs.onSurface)
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(
                targetState = popped,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith fadeOut() },
                label = "popped",
            ) { r ->
                if (r != null) {
                    Text(
                        text = "Android ${r.label}" + (r.name?.let { " · $it" } ?: "") + " · ${r.year}",
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(cs.surfaceContainerHigh)
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface,
                    )
                } else {
                    Box(Modifier.size(1.dp))
                }
            }
        }
    }
}

/**
 * The Android robot, built from its basic shapes. Unit coordinates: x centred, y = 0 at the top
 * of the body; the whole figure spans about one unit, multiplied by [scale].
 *
 * The Android robot is reproduced or modified from work created and shared by Google and used
 * according to terms described in the Creative Commons 3.0 Attribution License.
 */
private fun DrawScope.drawAndroidRobot(origin: Offset, scale: Float, color: Color, eyes: Color) {
    fun p(x: Float, y: Float) = origin + Offset(x * scale, y * scale)
    fun capsule(left: Float, top: Float, right: Float, bottom: Float) = drawRoundRect(
        color = color,
        topLeft = p(left, top),
        size = Size((right - left) * scale, (bottom - top) * scale),
        cornerRadius = CornerRadius((right - left) * scale / 2),
    )

    // Head: the top half of a circle, with a small gap above the body.
    val headCenter = p(0f, -0.04f)
    val headR = 0.31f * scale
    drawArc(color, 180f, 180f, useCenter = true, topLeft = headCenter - Offset(headR, headR), size = Size(headR * 2, headR * 2))
    // Antennae, splayed 30° from vertical.
    for (side in listOf(-1f, 1f)) {
        val dx = 0.5f * side
        val dy = -0.866f
        drawLine(
            color = color,
            start = headCenter + Offset(dx * 0.22f * scale, dy * 0.22f * scale),
            end = headCenter + Offset(dx * 0.40f * scale, dy * 0.40f * scale),
            strokeWidth = 0.035f * scale,
            cap = StrokeCap.Round,
        )
    }
    // Eyes, punched out in the bubble's colour.
    drawCircle(eyes, 0.036f * scale, p(-0.13f, -0.18f))
    drawCircle(eyes, 0.036f * scale, p(0.13f, -0.18f))

    // Body: square top, rounded bottom corners.
    val body = Path().apply {
        addRoundRect(
            RoundRect(
                left = p(-0.31f, 0f).x, top = p(0f, 0f).y, right = p(0.31f, 0f).x, bottom = p(0f, 0.48f).y,
                topLeftCornerRadius = CornerRadius.Zero, topRightCornerRadius = CornerRadius.Zero,
                bottomRightCornerRadius = CornerRadius(0.08f * scale), bottomLeftCornerRadius = CornerRadius(0.08f * scale),
            ),
        )
    }
    drawPath(body, color)

    // Arms and legs.
    capsule(-0.48f, 0.02f, -0.35f, 0.36f)
    capsule(0.35f, 0.02f, 0.48f, 0.36f)
    capsule(-0.185f, 0.36f, -0.055f, 0.62f)
    capsule(0.055f, 0.36f, 0.185f, 0.62f)
}

/** One physics tick: gravity, walls, then pairwise collisions between bubbles. */
private fun step(bubbles: List<Bubble>, field: Size, ax: Float, ay: Float, dt: Float) {
    if (dt <= 0f) return
    for (b in bubbles) {
        b.vx = (b.vx + ax * dt) * 0.996f
        b.vy = (b.vy + ay * dt) * 0.996f
        b.x += b.vx * dt
        b.y += b.vy * dt
        if (b.x < b.radius) { b.x = b.radius; b.vx = -b.vx * Restitution }
        if (b.x > field.width - b.radius) { b.x = field.width - b.radius; b.vx = -b.vx * Restitution }
        if (b.y < b.radius) { b.y = b.radius; b.vy = -b.vy * Restitution }
        if (b.y > field.height - b.radius) { b.y = field.height - b.radius; b.vy = -b.vy * Restitution }
    }
    // A few passes so stacks settle instead of sinking into each other.
    repeat(3) {
        for (i in bubbles.indices) for (j in i + 1 until bubbles.size) {
            val a = bubbles[i]
            val b = bubbles[j]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val minDist = a.radius + b.radius
            val d2 = dx * dx + dy * dy
            if (d2 >= minDist * minDist || d2 == 0f) continue
            val d = sqrt(d2)
            val nx = dx / d
            val ny = dy / d
            // Heavier (bigger) bubbles move less.
            val ma = a.radius * a.radius
            val mb = b.radius * b.radius
            val overlap = minDist - d
            a.x -= nx * overlap * mb / (ma + mb)
            a.y -= ny * overlap * mb / (ma + mb)
            b.x += nx * overlap * ma / (ma + mb)
            b.y += ny * overlap * ma / (ma + mb)
            val rv = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny
            if (rv < 0) {
                val impulse = -(1 + Restitution) * rv / (1 / ma + 1 / mb)
                a.vx -= impulse / ma * nx
                a.vy -= impulse / ma * ny
                b.vx += impulse / mb * nx
                b.vy += impulse / mb * ny
            }
        }
    }
}

/** Live gravity vector in m/s² (x right, y up the screen), falling back to "straight down". */
@Composable
private fun rememberGravity(): FloatArray {
    val context = LocalContext.current
    val gravity = remember { floatArrayOf(0f, 9.81f) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                gravity[0] = event.values[0]
                gravity[1] = event.values[1]
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm?.unregisterListener(listener) }
    }
    return gravity
}
