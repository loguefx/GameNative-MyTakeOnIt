package app.gamenative.adaptive

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import app.gamenative.config.DxVersion
import app.gamenative.config.GameConfig
import timber.log.Timber

/**
 * Adjusts a resolved [GameConfig] based on the device's *current* thermal and memory state
 * immediately before launch.
 *
 * The recommender produces a config based on hardware maximums (total RAM, GPU tier).
 * This tuner reads live conditions — how much RAM is free *right now*, how hot is the
 * device *before we even start* — and scales the config down where necessary.
 *
 * This runs once per launch on the launch worker thread (Dispatchers.IO).
 * Config is immutable after this point; no adjustments happen mid-game.
 */
object AdaptivePrelaunchTuner {

    /**
     * Tune [config] for the current device state and return a (possibly reduced) config.
     * Original config is never mutated — a copy is returned when changes are needed.
     */
    fun tune(config: GameConfig, context: Context): GameConfig {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return config

        val thermal = readThermalHeadroom(powerManager)
        val availMb = readAvailableRamMb(activityManager)

        val originalFps    = config.fpsLimit
        val originalBudget = config.dxvkDeviceLocalMemoryMiB
        val originalScale  = config.resolutionScale

        // ── Thermal pressure — lower FPS cap to reduce GPU work ──────────────────────
        val tunedFps = when {
            thermal < 0.20f -> {
                // Device is already hot before the game even starts. Hard-cap at 30.
                val cap = if (config.fpsLimit == 0) 30 else minOf(config.fpsLimit, 30)
                Timber.tag("PrelaunchTune").w("Thermal critical (%.2f) → capping FPS at %d".format(thermal, cap))
                cap
            }
            thermal < 0.40f -> {
                // Warm but not critical. Cap at 45 if config allows more.
                val cap = if (config.fpsLimit == 0 || config.fpsLimit > 45) 45 else config.fpsLimit
                Timber.tag("PrelaunchTune").w("Thermal warm (%.2f) → capping FPS at %d".format(thermal, cap))
                cap
            }
            else -> config.fpsLimit
        }

        // ── Memory pressure — reduce DXVK budget and resolution ─────────────────────
        // These thresholds leave enough headroom for the Wine process, Mesa, and the OS.
        val tunedBudget = when {
            availMb < 1_800 -> {
                Timber.tag("PrelaunchTune").w("RAM critical (${availMb}MB) → DXVK budget = 1024 MB")
                1_024
            }
            availMb < 2_500 -> {
                Timber.tag("PrelaunchTune").w("RAM low (${availMb}MB) → DXVK budget = 1536 MB")
                1_536
            }
            availMb < 3_500 -> minOf(config.dxvkDeviceLocalMemoryMiB, 2_048)
            else -> config.dxvkDeviceLocalMemoryMiB
        }

        val tunedScale = when {
            availMb < 1_800 -> {
                val s = 0.65f.coerceAtMost(config.resolutionScale)
                if (s < originalScale)
                    Timber.tag("PrelaunchTune").w("RAM critical → resolution scale clamped to %.2f".format(s))
                s
            }
            availMb < 2_500 -> {
                val s = 0.75f.coerceAtMost(config.resolutionScale)
                if (s < originalScale)
                    Timber.tag("PrelaunchTune").w("RAM low → resolution scale clamped to %.2f".format(s))
                s
            }
            else -> config.resolutionScale
        }

        // NOTE: DX12 → DX11 downgrade was intentionally removed here.
        //
        // The tuner runs AFTER container.dxwrapper has already been written to disk by
        // applyPerGameContainerOverrides (called from getOrCreateContainer).
        // XServerScreen uses container.dxwrapper — not GameConfig.dxVersion — to decide which
        // DLLs to extract. Downgrading dxVersion in GameConfig while dxwrapper stays "vkd3d-*"
        // causes VKD3D DLLs to be extracted but without their required env vars
        // (VKD3D_CONFIG, VKD3D_DISABLE_EXTENSIONS, etc.) because applyGameConfig only sets those
        // when config.vkd3dEnabled = true. The result is VKD3D starting unconfigured → black screen.
        //
        // The DXVK budget (tunedBudget) and resolution scale (tunedScale) reductions above already
        // handle the OOM risk for DX12 games under memory pressure — no DX downgrade is needed.

        val changed = tunedFps != originalFps || tunedBudget != originalBudget ||
            tunedScale != originalScale
        if (!changed) return config

        return config.copy(
            fpsLimit                  = tunedFps,
            dxvkDeviceLocalMemoryMiB  = tunedBudget,
            resolutionScale           = tunedScale,
            notes                     = config.notes + buildTuneNote(thermal, availMb),
        )
    }

    private fun readThermalHeadroom(powerManager: PowerManager?): Float {
        if (powerManager == null) return 1.0f
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try { powerManager.getThermalHeadroom(5).coerceIn(0f, 1f) } catch (_: Throwable) { 1.0f }
        } else 1.0f
    }

    private fun readAvailableRamMb(activityManager: ActivityManager): Long {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        return info.availMem / (1024L * 1024L)
    }

    private fun buildTuneNote(thermal: Float, availMb: Long): String = buildString {
        if (thermal < 0.40f) append(" [FPS reduced: device was warm at launch (thermal=%.2f)]".format(thermal))
        if (availMb < 3_500) append(" [DXVK budget reduced: ${availMb}MB RAM available]")
    }
}
