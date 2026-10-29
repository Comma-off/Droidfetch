package com.droid.fetch.data

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.os.storage.StorageManager
import android.provider.Settings
import android.view.Display
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.TimeZone
import kotlin.math.hypot
import kotlin.math.roundToInt

/** What the Android version hero card shows. */
data class AndroidVersion(
    val release: String,
    val major: String,
    val sdk: Int,
    val sdkFull: String?,
    val codename: String?,
    val securityPatch: String?,
    val securityPatchShort: String?,
    val build: String,
    val isPreview: Boolean,
)

data class StaticInfo(
    val android: AndroidVersion,
    val deviceName: String,
    val manufacturer: String,
    val model: String,
    val socName: String,
    val ramMarketedGb: Int,
    val storageMarketedBytes: Long,
    val gpuRenderer: String?,
    val system: List<InfoSection>,
    val hardware: List<InfoSection>,
    val other: List<InfoSection>,
)

data class BatteryState(
    val level: Int,
    val status: String,
    val health: String,
    val plugged: String?,
    val tempC: Float,
    val voltageMv: Int,
    val currentMa: Int?,
    val technology: String?,
    val cycles: Int?,
    val capacityMah: Int?,
)

data class CoreFreq(val index: Int, val curKhz: Long?, val maxKhz: Long?)

data class LiveStats(
    val cores: List<CoreFreq>,
    val memTotal: Long,
    val memAvail: Long,
    val swapTotal: Long?,
    val swapFree: Long?,
    val storageTotal: Long,
    val storageFree: Long,
    val battery: BatteryState?,
    val uptimeMs: Long,
)

class DeviceInfo(private val context: Context) {
    private val pm = context.packageManager
    private val mutex = Mutex()
    private var cached: StaticInfo? = null

    suspend fun static(): StaticInfo = mutex.withLock {
        cached ?: withContext(Dispatchers.IO) { collect() }.also { cached = it }
    }

    // ---- Android version -------------------------------------------------------------------

    private fun androidVersion(): AndroidVersion {
        val release = Build.VERSION.RELEASE_OR_CODENAME
        val preview = Build.VERSION.CODENAME != "REL"
        val major = release.substringBefore('.').takeIf { r -> r.all { it.isDigit() } } ?: release
        return AndroidVersion(
            release = release,
            major = major,
            sdk = Build.VERSION.SDK_INT,
            sdkFull = SysProps["ro.build.version.sdk_full"],
            codename = if (preview) Build.VERSION.CODENAME else dessert(Build.VERSION.SDK_INT),
            securityPatch = formatIsoDate(Build.VERSION.SECURITY_PATCH),
            securityPatchShort = formatIsoDateShort(Build.VERSION.SECURITY_PATCH),
            build = Build.DISPLAY,
            isPreview = preview,
        )
    }

    private fun dessert(sdk: Int): String? = when (sdk) {
        29 -> "Quince Tart"
        30 -> "Red Velvet Cake"
        31 -> "Snow Cone"
        32 -> "Snow Cone v2"
        33 -> "Tiramisu"
        34 -> "Upside Down Cake"
        35 -> "Vanilla Ice Cream"
        36 -> "Baklava"
        37 -> "Cinnamon Bun"
        else -> null
    }

    // ---- Collect everything static ----------------------------------------------------------

    private fun collect(): StaticInfo {
        val android = androidVersion()
        val gpu = Gpu.query(pm)
        val am = context.getSystemService(ActivityManager::class.java)
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val storageMarketed = runCatching {
            context.getSystemService(StorageStatsManager::class.java).getTotalBytes(StorageManager.UUID_DEFAULT)
        }.getOrDefault(StatFs(Environment.getDataDirectory().path).totalBytes)
        val socName = socName()

        return StaticInfo(
            android = android,
            deviceName = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) ?: Build.MODEL,
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.titlecase(Locale.getDefault()) },
            model = Build.MODEL,
            socName = socName,
            ramMarketedGb = marketedRamGb(mem.totalMem),
            storageMarketedBytes = storageMarketed,
            gpuRenderer = gpu.renderer,
            system = systemSections(android),
            hardware = hardwareSections(socName, gpu),
            other = otherSections(),
        )
    }

    private fun socName(): String {
        val model = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL.takeIf { it != Build.UNKNOWN } else null
        val maker = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MANUFACTURER.takeIf { it != Build.UNKNOWN } else null
        return when {
            model != null && maker != null && !model.startsWith(maker, ignoreCase = true) -> "$maker $model"
            model != null -> model
            else -> SysProps["ro.board.platform"] ?: Build.HARDWARE
        }
    }

    // ---- System ----------------------------------------------------------------------------

    private fun systemSections(android: AndroidVersion): List<InfoSection> = listOfNotNull(
        section("Android") {
            row("Version", "Android ${android.release}")
            row("Codename", android.codename)
            row("API level", android.sdkFull ?: android.sdk.toString())
            row("Security patch", android.securityPatch)
            row("Vendor security patch", formatIsoDate(SysProps["ro.vendor.build.security_patch"]))
            row("Google Play system update", packageVersion("com.google.android.modulemetadata")?.let { formatIsoDate(it.take(10)) })
            row("First API level", SysProps["ro.product.first_api_level"])
        },
        section("Build") {
            row("Build number", Build.DISPLAY)
            row("Build ID", Build.ID.takeIf { it != Build.DISPLAY })
            row("Incremental", Build.VERSION.INCREMENTAL)
            row("Build type", "${Build.TYPE} (${Build.TAGS})")
            row("Build date", formatEpoch(Build.TIME))
            row("Fingerprint", Build.FINGERPRINT)
        },
        section("Kernel") {
            row("Version", System.getProperty("os.version"))
            row("Architecture", System.getProperty("os.arch"))
            row("Build", readLine("/proc/version")?.substringAfter(") ")?.substringAfter(") ")?.takeIf { it.isNotBlank() })
            row("Runtime", System.getProperty("java.vm.version")?.let { "ART $it" })
            row("SELinux", selinux())
        },
        section("Boot & partitions") {
            row("Project Treble", SysProps.bool("ro.treble.enabled").yesNo())
            row("Seamless updates (A/B)", SysProps.bool("ro.build.ab_update").yesNo())
            row("Current slot", SysProps["ro.boot.slot_suffix"]?.removePrefix("_")?.uppercase())
            row("Dynamic partitions", SysProps.bool("ro.boot.dynamic_partitions").yesNo())
            row("Verified boot", SysProps["ro.boot.verifiedbootstate"]?.replaceFirstChar { it.uppercase() })
            row("Bootloader", Build.BOOTLOADER.takeIf { it != Build.UNKNOWN })
            row("Baseband", Build.getRadioVersion()?.split(',')?.first())
        },
        section("Region") {
            row("Language", Locale.getDefault().displayName)
            row("Time zone", TimeZone.getDefault().let { "${it.getDisplayName(false, TimeZone.LONG)} (${it.id})" })
        },
    )

    private fun selinux(): String? {
        readLine("/sys/fs/selinux/enforce")?.let { return if (it == "1") "Enforcing" else "Permissive" }
        return runCommand("getenforce")
    }

    private fun packageVersion(pkg: String): String? =
        runCatching { pm.getPackageInfo(pkg, 0).versionName }.getOrNull()

    // ---- Hardware --------------------------------------------------------------------------

    private fun hardwareSections(soc: String, gpu: GpuInfo): List<InfoSection> = listOfNotNull(
        section("Processor") {
            row("Chipset", soc)
            row("Platform", SysProps["ro.board.platform"])
            row("Cores", Runtime.getRuntime().availableProcessors().toString())
            row("Clusters", clusters())
            row("ABIs", Build.SUPPORTED_ABIS.joinToString(", "))
            row("Instruction set", cpuIsa())
        },
        section("Graphics") {
            row("Renderer", gpu.renderer)
            row("GLES backend", gpu.backend)
            row("Vendor", gpu.vendor)
            row("OpenGL ES", gpu.glVersion?.removePrefix("OpenGL ES ")?.substringBefore(' ')?.let { "OpenGL ES $it" })
            row("Driver", gpu.glVersion?.removePrefix("OpenGL ES ")?.substringAfter(' ', "")?.let(::stripAngleHash))
            row("GLSL", gpu.glslVersion?.removePrefix("OpenGL ES GLSL ES ")?.substringBefore(' '))
            row("GL extensions", gpu.extensionCount.takeIf { it > 0 }?.toString())
            row("Vulkan", gpu.vulkanVersion)
            row("Vulkan hardware level", gpu.vulkanLevel?.toString())
        },
        displaySection(),
    )

    /** "(ANGLE 2.1 git hash: 8d87…)" → "ANGLE 2.1"; the hash means nothing to a reader. */
    private fun stripAngleHash(driver: String): String =
        driver.replace(Regex("""\(?ANGLE ([\d.]+) git hash: [0-9a-f]+\)?"""), "ANGLE $1").trim()

    private fun maxFreq(core: Int) = readLine("/sys/devices/system/cpu/cpu$core/cpufreq/cpuinfo_max_freq")?.toLongOrNull()

    /** Cores grouped by peak clock, fastest first: "1× 4.11 GHz + 5× 3.38 GHz + 2× 2.65 GHz". */
    private fun clusters(): String? {
        val freqs = (0 until Runtime.getRuntime().availableProcessors()).mapNotNull { maxFreq(it) }
        if (freqs.isEmpty()) return null
        return freqs.groupingBy { it }.eachCount().toSortedMap(compareByDescending { it })
            .entries.joinToString(" + ") { (f, n) -> "$n× ${formatKhz(f)}" }
    }

    private fun cpuIsa(): String? {
        val features = runCatching {
            File("/proc/cpuinfo").readLines().firstOrNull { it.startsWith("Features") }?.substringAfter(':')?.trim()
        }.getOrNull() ?: return null
        val set = features.split(' ').toSet()
        return when {
            "sve2" in set -> "ARMv9-A"
            "atomics" in set -> "ARMv8.1-A or later"
            "asimd" in set -> "ARMv8-A"
            else -> null
        }
    }

    private fun displaySection(): InfoSection? {
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY) ?: return null
        val modes = display.supportedModes
        val best = modes.maxByOrNull { it.physicalWidth * it.physicalHeight } ?: display.mode
        val w = minOf(best.physicalWidth, best.physicalHeight)
        val h = maxOf(best.physicalWidth, best.physicalHeight)
        val metrics = context.resources.displayMetrics
        val inches = hypot(w / metrics.xdpi.toDouble(), h / metrics.ydpi.toDouble())
        val rates = modes.map { it.refreshRate.roundToInt() }.distinct().sorted()
        @Suppress("DEPRECATION")
        val hdrTypes = if (Build.VERSION.SDK_INT >= 34) display.mode.supportedHdrTypes else display.hdrCapabilities?.supportedHdrTypes ?: intArrayOf()
        val hdr = hdrTypes.distinct().mapNotNull {
            when (it) {
                Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
                Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
                Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
                Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
                else -> null
            }
        }
        return section("Display") {
            row("Resolution", "$w × $h")
            row("Size", String.format(Locale.getDefault(), "%.2f\"", inches))
            row("Density", "${metrics.densityDpi} dpi (${String.format(Locale.getDefault(), "%.0f", (metrics.xdpi + metrics.ydpi) / 2)} ppi)")
            row("Refresh rates", rates.joinToString(", ") { "$it Hz" })
            row("HDR", hdr.joinToString(", ").ifEmpty { "Not supported" })
            row("Wide color gamut", display.isWideColorGamut.yesNo())
        }
    }

    // ---- Other -----------------------------------------------------------------------------

    private fun otherSections(): List<InfoSection> = listOfNotNull(
        section("Device") {
            row("Model", Build.MODEL)
            row("Manufacturer", Build.MANUFACTURER.replaceFirstChar { it.uppercase() })
            row("Brand", Build.BRAND.replaceFirstChar { it.uppercase() })
            row("Codename", Build.DEVICE)
            row("Product", Build.PRODUCT)
            row("Hardware", Build.HARDWARE)
            row("Board", Build.BOARD)
        },
        cameraSection(),
        section("Connectivity") {
            row("Wi-Fi standard", wifiStandard())
            row("Wi-Fi 6 GHz", wifi6Ghz().yesNo())
            row("Bluetooth LE", pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE).yesNo())
            row("NFC", pm.hasSystemFeature(PackageManager.FEATURE_NFC).yesNo())
            row("Ultra-wideband", pm.hasSystemFeature(PackageManager.FEATURE_UWB).yesNo())
            row("eSIM", pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_EUICC).yesNo())
            row("Active network", activeNetwork())
        },
        section("Features") {
            row("Fingerprint", pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT).yesNo())
            row("Face unlock", pm.hasSystemFeature(PackageManager.FEATURE_FACE).yesNo())
            row("IR blaster", pm.hasSystemFeature(PackageManager.FEATURE_CONSUMER_IR).yesNo())
            row("Stylus", pm.hasSystemFeature("android.hardware.stylus").yesNo())
            row("System features", pm.systemAvailableFeatures.count { it.name != null }.toString())
        },
        sensorSection(),
        section("Software") {
            row("WebView", WebView.getCurrentWebViewPackage()?.let { "${it.versionName}" })
            row("Google Play services", packageVersion("com.google.android.gms")?.substringBefore(' '))
            row("Google Play Store", packageVersion("com.android.vending")?.substringBefore(' '))
        },
    )

    private fun cameraSection(): InfoSection? {
        val cm = context.getSystemService(CameraManager::class.java) ?: return null
        val ids = runCatching { cm.cameraIdList.toList() }.getOrDefault(emptyList())
        return section("Cameras") {
            row("Cameras exposed", ids.size.toString())
            for (id in ids) {
                val ch = runCatching { cm.getCameraCharacteristics(id) }.getOrNull() ?: continue
                val facing = when (ch.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                    CameraCharacteristics.LENS_FACING_BACK -> "Rear"
                    else -> "External"
                }
                val px = ch.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                val mp = px?.let { String.format(Locale.getDefault(), "%.1f MP", it.width * it.height / 1_000_000.0) }
                val focal = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
                    ?.let { String.format(Locale.getDefault(), "%.1f mm", it) }
                row("Camera $id ($facing)", listOfNotNull(mp, focal).joinToString(" · "))
            }
        }
    }

    private fun sensorSection(): InfoSection? {
        val sm = context.getSystemService(SensorManager::class.java) ?: return null
        val sensors = sm.getSensorList(Sensor.TYPE_ALL)
        return section("Sensors") {
            row("Total", sensors.size.toString())
            sensors.distinctBy { it.type }.sortedBy { it.type }.forEach { s ->
                row(s.stringType.substringAfterLast('.').replace('_', ' ').replaceFirstChar { it.uppercase() }, s.vendor)
            }
        }
    }

    private fun wifiStandard(): String? {
        if (Build.VERSION.SDK_INT < 30) return null
        val wm = context.getSystemService(WifiManager::class.java) ?: return null
        return when {
            Build.VERSION.SDK_INT >= 33 && wm.isWifiStandardSupported(8) -> "Wi-Fi 7 (802.11be)"
            wm.isWifiStandardSupported(6) -> "Wi-Fi 6 (802.11ax)"
            wm.isWifiStandardSupported(5) -> "Wi-Fi 5 (802.11ac)"
            wm.isWifiStandardSupported(4) -> "Wi-Fi 4 (802.11n)"
            else -> "Legacy"
        }
    }

    private fun wifi6Ghz(): Boolean? =
        if (Build.VERSION.SDK_INT >= 30) context.getSystemService(WifiManager::class.java)?.is6GHzBandSupported else null

    private fun activeNetwork(): String {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return "Offline"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Other"
        }
    }

    // ---- Live ------------------------------------------------------------------------------

    fun live(): LiveStats {
        val n = Runtime.getRuntime().availableProcessors()
        val cores = (0 until n).map { i ->
            CoreFreq(i, readLine("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")?.toLongOrNull(), maxFreq(i))
        }
        val am = context.getSystemService(ActivityManager::class.java)
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val meminfo = runCatching {
            File("/proc/meminfo").readLines().associate { line ->
                line.substringBefore(':') to (line.substringAfter(':').trim().substringBefore(' ').toLongOrNull() ?: 0L) * 1024
            }
        }.getOrDefault(emptyMap())
        val stat = StatFs(Environment.getDataDirectory().path)
        return LiveStats(
            cores = cores,
            memTotal = mem.totalMem,
            memAvail = mem.availMem,
            swapTotal = meminfo["SwapTotal"]?.takeIf { it > 0 },
            swapFree = meminfo["SwapFree"],
            storageTotal = stat.totalBytes,
            storageFree = stat.availableBytes,
            battery = battery(),
            uptimeMs = SystemClock.elapsedRealtime(),
        )
    }

    private fun battery(): BatteryState? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val bm = context.getSystemService(BatteryManager::class.java)
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pct = if (level >= 0) level * 100 / scale else -1
        val status = when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
            else -> "Unknown"
        }
        val health = when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }
        val plugged = when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            BatteryManager.BATTERY_PLUGGED_DOCK -> "Dock"
            else -> null
        }
        // CURRENT_NOW is µA on most devices but mA on a few; anything implausibly small is mA already.
        val currentRaw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW).takeIf { it != Int.MIN_VALUE && it != 0 }
        val currentMa = currentRaw?.let { if (kotlin.math.abs(it) > 20_000) it / 1000 else it }
        // Full capacity ≈ remaining charge ÷ level. Good to a few percent, and needs no hidden APIs.
        val charge = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER).takeIf { it > 0 && it != Int.MIN_VALUE }
        val capacity = if (charge != null && pct > 5) (charge / 1000.0 / (pct / 100.0)).roundToInt() else null
        val cycles = if (Build.VERSION.SDK_INT >= 34) intent.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1).takeIf { it >= 0 } else null
        return BatteryState(
            level = pct,
            status = status,
            health = health,
            plugged = plugged,
            tempC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f,
            voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0),
            currentMa = currentMa,
            technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY),
            cycles = cycles,
            capacityMah = capacity,
        )
    }
}

/** Live values rendered as rows, so they share the list styling and the text report. */
fun LiveStats.memorySection(): InfoSection? = section("Memory") {
    row("RAM", "${formatBytes(memTotal - memAvail)} used of ${formatBytes(memTotal)}")
    row("Available", formatBytes(memAvail))
    if (swapTotal != null) row("Swap (zRAM)", "${formatBytes(swapTotal - (swapFree ?: 0))} used of ${formatBytes(swapTotal)}")
    row("Internal storage", "${formatBytes(storageTotal - storageFree)} used of ${formatBytes(storageTotal)}")
}

fun LiveStats.batterySection(unit: TempUnit): InfoSection? {
    val b = battery ?: return null
    return section("Battery") {
        row("Level", "${b.level}%")
        row("Status", listOfNotNull(b.status, b.plugged).joinToString(" · "))
        row("Health", b.health)
        row("Temperature", formatTemp(b.tempC, unit))
        row("Voltage", String.format(Locale.getDefault(), "%.2f V", b.voltageMv / 1000f))
        row("Current", b.currentMa?.let { "$it mA" })
        row("Estimated capacity", b.capacityMah?.let { "≈ $it mAh" })
        row("Charge cycles", b.cycles?.toString())
        row("Technology", b.technology)
    }
}

fun LiveStats.uptimeSection(): InfoSection? = section("Uptime") {
    row("Since boot", formatDuration(uptimeMs))
}
