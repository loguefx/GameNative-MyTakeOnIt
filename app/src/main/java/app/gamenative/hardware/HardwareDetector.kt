package app.gamenative.hardware

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.view.WindowManager
import com.winlator.core.GPUInformation
import java.io.File
import timber.log.Timber

/**
 * Detects device hardware for GameConfigRecommender. Run on Dispatchers.IO only.
 * GPU strings come from OpenGL ES (GPUInformation); Vulkan version from native when available.
 */
object HardwareDetector {

    private const val P_CORE_FREQ_MHZ_THRESHOLD = 2000

    fun detect(context: Context): HardwareProfile {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: throw IllegalStateException("ActivityManager not available")
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

        // GPU from OpenGL (GPUInformation uses EGL on background thread and caches in PrefManager)
        val glRenderer = safeGetGpuRenderer(context)
        val glVersion = safeGetGpuVersion(context)
        val gpuVendor = when {
            glRenderer.contains("Adreno", ignoreCase = true) -> GpuVendor.ADRENO
            glRenderer.contains("Mali", ignoreCase = true) -> GpuVendor.MALI
            glRenderer.contains("PowerVR", ignoreCase = true) -> GpuVendor.POWERVR
            else -> GpuVendor.UNKNOWN
        }
        val vulkanVersion = safeGetVulkanVersion(context)
        val supportsVulkan13 = vulkanVersion.startsWith("1.3")

        // CPU from sysfs and /proc
        val coreFreqs = readCoreMaxFreqs()
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val pCoreCount = coreFreqs.count { it > P_CORE_FREQ_MHZ_THRESHOLD }
        val eCoreCount = (cpuCores - pCoreCount).coerceAtLeast(0)
        val pCoreMask = detectPCoreMask(coreFreqs)
        val cpuMaxFreqMhz = coreFreqs.maxOrNull() ?: 0
        val socName = readSoCName()

        // RAM from ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalRamMb = (memInfo.totalMem / (1024 * 1024)).toInt()
        val availableRamMb = (memInfo.availMem / (1024 * 1024)).toInt()

        // Display
        val (refreshHz, widthPx, heightPx) = readDisplayMode(windowManager)

        // Thermal (API 30+). Some OEMs don't implement getThermalHeadroom; default 1.0f = no pressure,
        // so recommender won't artificially lower FPS on devices that don't report thermals.
        val thermalHeadroom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && powerManager != null) {
            try {
                powerManager.getThermalHeadroom(5)?.toFloat() ?: 1.0f
            } catch (_: Throwable) {
                1.0f
            }
        } else 1.0f
        val sustainedPerformanceSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && powerManager != null) {
            powerManager.isSustainedPerformanceModeSupported
        } else false

        return HardwareProfile(
            cpuSoC = socName,
            cpuCores = cpuCores,
            cpuPCoreCount = pCoreCount.coerceAtLeast(1),
            cpuECoreCount = eCoreCount,
            cpuPCoreMask = pCoreMask,
            cpuMaxFreqMhz = cpuMaxFreqMhz,
            gpuVendor = gpuVendor,
            gpuModel = glRenderer.ifEmpty { "Unknown GPU" },
            gpuOpenGLES = glVersion,
            gpuVulkanVersion = vulkanVersion,
            gpuDriverVersion = glVersion,
            supportsVulkan13 = supportsVulkan13,
            supportsMeshShaders = false,
            supportsRayTracing = false,
            totalRamMb = totalRamMb,
            availableRamMb = availableRamMb,
            storageType = detectStorageType(),
            displayRefreshHz = refreshHz,
            displayWidthPx = widthPx,
            displayHeightPx = heightPx,
            thermalHeadroom = thermalHeadroom,
            sustainedPerformanceSupported = sustainedPerformanceSupported,
        )
    }

    private fun safeGetGpuRenderer(context: Context): String {
        return try {
            com.winlator.PrefManager.init(context)
            GPUInformation.getRenderer(context) ?: ""
        } catch (e: Exception) {
            Timber.w(e, "Failed to get GPU renderer")
            ""
        }
    }

    private fun safeGetGpuVersion(context: Context): String {
        return try {
            GPUInformation.getVersion(context) ?: ""
        } catch (e: Exception) {
            Timber.w(e, "Failed to get GPU version")
            ""
        }
    }

    private fun safeGetVulkanVersion(context: Context): String {
        val driverName = try {
            app.gamenative.PrefManager.init(context)
            app.gamenative.PrefManager.graphicsDriver
        } catch (_: Exception) {
            "system"
        }
        // Fallback 1.2.0: recommender will skip Vulkan 1.3–specific optimizations (e.g. shader model 6_5).
        // Prefer under-recommend over enabling features the driver may not support.
        return try {
            val v = GPUInformation.getVulkanVersion(driverName, context)
            if (!v.isNullOrEmpty()) v else "1.2.0"
        } catch (e: Exception) {
            Timber.w(e, "Vulkan version detection failed, using 1.2.0")
            "1.2.0"
        }
    }

    private fun readSoCName(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL?.takeIf { it.isNotEmpty() } ?: "${Build.MANUFACTURER} ${Build.MODEL}"
        } else {
            app.gamenative.utils.HardwareUtils.getSOCName()
                ?: "${Build.MANUFACTURER} ${Build.MODEL}"
        }
    }

    private fun readCoreMaxFreqs(): List<Int> {
        val list = mutableListOf<Int>()
        for (i in 0..15) {
            try {
                val path = "/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq"
                val value = File(path).readText().trim().toIntOrNull()?.div(1000)
                if (value != null) list.add(value) else break
            } catch (_: Exception) {
                break
            }
        }
        return if (list.isEmpty()) listOf(0) else list
    }

    private fun detectPCoreMask(coreFreqs: List<Int>): String {
        var mask = 0
        coreFreqs.forEachIndexed { index, freq ->
            if (freq > P_CORE_FREQ_MHZ_THRESHOLD) mask = mask or (1 shl index)
        }
        return "0x${mask.toString(16).uppercase()}"
    }

    private fun readDisplayMode(windowManager: WindowManager?): Triple<Int, Int, Int> {
        if (windowManager == null) return Triple(60, 1080, 1920)
        return try {
            val display = windowManager.defaultDisplay
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val mode = display.mode
                val refresh = mode.refreshRate.toInt().coerceIn(30, 165)
                val w = mode.physicalWidth
                val h = mode.physicalHeight
                Triple(refresh, w, h)
            } else {
                val size = android.graphics.Point()
                @Suppress("DEPRECATION")
                display.getRealSize(size)
                Triple(60, size.x, size.y)
            }
        } catch (e: Exception) {
            Timber.w(e, "Display mode read failed")
            Triple(60, 1080, 1920)
        }
    }

    private fun detectStorageType(): StorageType {
        return try {
            val rotational = File("/sys/block/mmcblk0/queue/rotational").readText().trim()
            val ufs = File("/sys/block/sda/device/type").exists() ||
                File("/sys/block/sda/queue/rotational").readText().trim() == "0"
            when {
                ufs -> StorageType.UFS_3_0
                rotational == "0" -> StorageType.UFS_2_2
                else -> StorageType.EMMC
            }
        } catch (_: Exception) {
            StorageType.UNKNOWN
        }
    }
}
