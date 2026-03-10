package app.gamenative.adaptive

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Emits device health events while a game is running.
 *
 * This is **read-only** — it never changes the game's config (which is write-once at launch).
 * Instead, it drives warning overlays in XServerScreen so the user knows why performance
 * may have dropped, and it feeds data into [CrashRecoveryManager] and the pre-launch
 * tuner for the *next* session.
 *
 * Collect the [monitor] flow from XServerScreen's LaunchedEffect.
 * The flow terminates when the collector's coroutine scope is cancelled (game exits).
 */
object RuntimeGameMonitor {

    /** Interval between health checks in milliseconds. */
    private const val POLL_INTERVAL_MS = 5_000L

    sealed class AdaptiveEvent {
        /** Device is overheating — performance may drop. headroom in [0, 1]; lower = hotter. */
        data class ThermalWarning(val headroom: Float) : AdaptiveEvent()
        /** Thermal condition resolved. */
        object ThermalRecovered : AdaptiveEvent()
        /** Available RAM dropped below a safe threshold. availableMb = current free RAM in MB. */
        data class MemoryWarning(val availableMb: Long) : AdaptiveEvent()
        /** Memory condition resolved. */
        object MemoryRecovered : AdaptiveEvent()
    }

    // Thresholds for warning vs. clear hysteresis
    private const val THERMAL_WARN_THRESHOLD    = 0.25f
    private const val THERMAL_RECOVER_THRESHOLD = 0.50f
    private const val MEMORY_WARN_MB            = 1_500L
    private const val MEMORY_RECOVER_MB         = 2_500L

    /**
     * Returns a cold [Flow] that polls device health every [POLL_INTERVAL_MS] ms.
     * Designed to be launched with [kotlinx.coroutines.launch] from a UI coroutine scope
     * so it cancels automatically when the game screen leaves the composition.
     */
    fun monitor(context: Context): Flow<AdaptiveEvent> = flow {
        val powerManager    = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

        var thermalWarningActive = false
        var memoryWarningActive  = false

        while (true) {
            // ── Thermal check ────────────────────────────────────────────────────────
            val thermal = readThermalHeadroom(powerManager)
            if (!thermalWarningActive && thermal < THERMAL_WARN_THRESHOLD) {
                thermalWarningActive = true
                emit(AdaptiveEvent.ThermalWarning(thermal))
            } else if (thermalWarningActive && thermal > THERMAL_RECOVER_THRESHOLD) {
                thermalWarningActive = false
                emit(AdaptiveEvent.ThermalRecovered)
            }

            // ── Memory check ─────────────────────────────────────────────────────────
            val availMb = readAvailableRamMb(activityManager)
            if (!memoryWarningActive && availMb < MEMORY_WARN_MB) {
                memoryWarningActive = true
                emit(AdaptiveEvent.MemoryWarning(availMb))
            } else if (memoryWarningActive && availMb > MEMORY_RECOVER_MB) {
                memoryWarningActive = false
                emit(AdaptiveEvent.MemoryRecovered)
            }

            delay(POLL_INTERVAL_MS)
        }
    }

    private fun readThermalHeadroom(powerManager: PowerManager?): Float {
        if (powerManager == null) return 1.0f
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try { powerManager.getThermalHeadroom(5).coerceIn(0f, 1f) } catch (_: Throwable) { 1.0f }
        } else 1.0f
    }

    private fun readAvailableRamMb(activityManager: ActivityManager?): Long {
        if (activityManager == null) return Long.MAX_VALUE
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        return info.availMem / (1024L * 1024L)
    }
}
