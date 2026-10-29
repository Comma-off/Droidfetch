package com.droid.fetch.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.droid.fetch.DroidfetchApp
import com.droid.fetch.data.LiveStats
import com.droid.fetch.data.StaticInfo
import com.droid.fetch.data.batterySection
import com.droid.fetch.data.glassBar
import com.droid.fetch.data.memorySection
import com.droid.fetch.data.toReport
import com.droid.fetch.data.uptimeSection
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val Tabs = listOf(
    Tab("System", Icons.Outlined.Android),
    Tab("Hardware", Icons.Outlined.DeveloperBoard),
    Tab("Other", Icons.Outlined.Widgets),
    Tab("Settings", Icons.Outlined.Settings),
)

@Composable
fun AppRoot(app: DroidfetchApp) {
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    DroidfetchTheme(settings) {
        val context = LocalContext.current
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        val pager = rememberPagerState { Tabs.size }

        val info by produceState<StaticInfo?>(null) { value = app.info.static() }
        val live by liveStats(app, settings.liveUpdates)
        val share = { info?.let { shareReport(context, it, live) } ?: Unit }

        // Back from another tab returns to System before leaving the app.
        BackHandler(pager.currentPage != 0) { scope.launch { pager.animateScrollToPage(0) } }

        // Status/nav bar icons follow the in-app theme, not just the system's.
        val dark = settings.isDark()
        val view = LocalView.current
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }

        var barHeight by remember { mutableStateOf(0.dp) }
        // What the glass bar samples: the pages, recorded into a layer.
        val pageBackdrop = rememberLayerBackdrop()

        CompositionLocalProvider(LocalBottomBarHeight provides barHeight) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.surface),
            ) {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (settings.glassBar) Modifier.layerBackdrop(pageBackdrop) else Modifier),
                    beyondViewportPageCount = 1,
                    key = { Tabs[it].label },
                ) { page ->
                    when (page) {
                        0 -> SystemPage(info, live, share)
                        1 -> HardwarePage(info, live, settings, share)
                        2 -> OtherPage(info, share)
                        else -> SettingsPage(settings, app.settings::update, share)
                    }
                }

                val barModifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { barHeight = with(density) { it.height.toDp() } + if (settings.floatingBar) 14.dp else 0.dp }
                if (settings.floatingBar) {
                    FloatingBottomBar(settings.glassBar, pageBackdrop, Tabs, pager, settings.showLabels, barModifier)
                } else {
                    ClassicBottomBar(Tabs, pager, settings.showLabels, barModifier)
                }
            }
        }
    }
}

/** Polls live stats while the app is resumed; once per resume when live updates are off. */
@Composable
private fun liveStats(app: DroidfetchApp, continuous: Boolean): androidx.compose.runtime.State<LiveStats?> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state = remember { mutableStateOf<LiveStats?>(null) }
    LaunchedEffect(lifecycle, continuous) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            do {
                state.value = withContext(Dispatchers.IO) { app.info.live() }
                if (continuous) delay(1000)
            } while (continuous)
        }
    }
    return state
}

private fun shareReport(context: Context, info: StaticInfo, live: LiveStats?) {
    val header = buildString {
        appendLine("Droidfetch report")
        appendLine("-----------------")
        appendLine("Device:  ${info.manufacturer} ${info.model}")
        appendLine("OS:      Android ${info.android.release}${info.android.codename?.let { " ($it)" } ?: ""}")
        appendLine("Build:   ${info.android.build}")
        appendLine("Chipset: ${info.socName}")
        info.gpuRenderer?.let { appendLine("GPU:     $it") }
        appendLine("RAM:     ${info.ramMarketedGb} GB")
    }
    val liveSections = listOfNotNull(live?.memorySection(), live?.batterySection(com.droid.fetch.data.TempUnit.Celsius), live?.uptimeSection())
    val text = header + "\n" + (info.system + info.hardware + liveSections + info.other).toReport()
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Share device report"))
}
