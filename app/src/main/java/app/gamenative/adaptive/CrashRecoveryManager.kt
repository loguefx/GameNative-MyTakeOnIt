package app.gamenative.adaptive

import android.content.Context
import app.gamenative.config.ConfigPreset
import app.gamenative.config.DxVersion
import app.gamenative.config.GameConfig
import app.gamenative.config.KnownGameFixes
import app.gamenative.profile.WineRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File

/**
 * Tracks per-game crash history and automatically applies a progressively safer
 * config on repeated crashes — without requiring the user to change any setting.
 *
 * ## Recovery Ladder
 *
 * Each time a game crashes (non-zero exit, or exit within [MIN_PLAY_SECONDS]):
 *   → Crash count increments and recovery step advances.
 *
 * | Step | What is applied                                                |
 * |------|----------------------------------------------------------------|
 * |  0   | Full recommended config — no changes                          |
 * |  1   | Disable VSync, drop resolution −0.10x                         |
 * |  2   | ConfigPreset.COMPATIBILITY: DX11, Proton 8.0, 0.75x, no VKD3D |
 * |  3   | Force DX9 (DXVK only), Proton 8.0, 0.65x scale                |
 * |  4   | Wine-GE, DX9, 0.5x scale — last resort before Cannot Run      |
 *
 * After [CLEAN_EXITS_TO_RESET] consecutive clean exits at any step, reset to step 0.
 */
object CrashRecoveryManager {

    private const val SUBDIR               = "crash_recovery"
    private const val MIN_PLAY_SECONDS     = 60   // exits shorter than this count as a crash
    private const val CLEAN_EXITS_TO_RESET = 3    // clean exits needed to clear crash history

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    @Serializable
    data class CrashHistory(
        val appId: String,
        val crashCount: Int = 0,
        val cleanExitCount: Int = 0,
        val recoveryStep: Int = 0,
        val lastCrashMs: Long = 0L,
    )

    /**
     * Apply crash recovery adjustments to [config] for [appId].
     * Returns the original config when no crash history exists (step 0).
     */
    suspend fun applyRecovery(context: Context, appId: String, config: GameConfig): GameConfig =
        withContext(Dispatchers.IO) {
            val history = loadHistory(context, appId) ?: return@withContext config
            if (history.recoveryStep == 0) return@withContext config

            Timber.tag("CrashRecovery").w(
                "appId=$appId step=${history.recoveryStep} crashes=${history.crashCount}",
            )
            applyStep(config, history.recoveryStep)
        }

    /**
     * Call after the game process exits.
     * [exitCode] = Wine process exit code (0 = clean); [playDurationMs] = how long the game ran.
     */
    suspend fun recordExit(context: Context, appId: String, exitCode: Int, playDurationMs: Long) =
        withContext(Dispatchers.IO) {
            val playSeconds = playDurationMs / 1000L

            // Exit code 137 = SIGKILL (128 + 9).  This is always an external kill from Android's
            // OOM manager or process lifecycle — never a game bug.  Counting it as a crash would
            // incorrectly downgrade config for games running fine but starved of RAM by the OS.
            val isAndroidOomKill = exitCode == 137

            // Games in KnownGameFixes have been hand-tuned with specific Proton/DX/env settings.
            // Auto-downgrading their config (e.g. from Proton 10 to Proton 8) would undo those
            // fixes and is almost certain to make things worse, not better.  The known-fix env
            // vars (layer 6) are always re-applied last, but the config fields (protonVersion,
            // dxVersion, dxvkEnabled) would still be corrupted by the recovery step.
            val steamId = appId.removePrefix("STEAM_").toIntOrNull() ?: -1
            val hasKnownFix = KnownGameFixes.get(steamId) != null
            if (hasKnownFix) {
                Timber.tag("CrashRecovery").d(
                    "Skipping crash recovery for appId=$appId — KnownGameFix present " +
                        "(exitCode=$exitCode playSeconds=$playSeconds). " +
                        "Fix settings are already optimal; auto-downgrade would revert intentional overrides.",
                )
                return@withContext
            }

            val isCrash = !isAndroidOomKill && (exitCode != 0 || playSeconds < MIN_PLAY_SECONDS)
            if (isAndroidOomKill) {
                Timber.tag("CrashRecovery").w(
                    "OOM-kill (status=137) for appId=$appId after ${playSeconds}s — " +
                        "skipping crash count increment (this is Android killing the process, not a game bug).",
                )
            }

            val history = loadHistory(context, appId) ?: CrashHistory(appId)
            val updated = if (isCrash) {
                val nextStep = (history.recoveryStep + 1).coerceAtMost(4)
                Timber.tag("CrashRecovery").w(
                    "CRASH recorded appId=$appId exitCode=$exitCode " +
                        "playSeconds=$playSeconds → step $nextStep",
                )
                history.copy(
                    crashCount    = history.crashCount + 1,
                    cleanExitCount = 0,
                    recoveryStep  = nextStep,
                    lastCrashMs   = System.currentTimeMillis(),
                )
            } else {
                val cleanExits = history.cleanExitCount + 1
                Timber.tag("CrashRecovery").i(
                    "Clean exit appId=$appId step=${history.recoveryStep} cleanExits=$cleanExits",
                )
                if (cleanExits >= CLEAN_EXITS_TO_RESET && history.recoveryStep > 0) {
                    Timber.tag("CrashRecovery").i("Resetting recovery history for appId=$appId")
                    CrashHistory(appId)   // full reset
                } else {
                    history.copy(cleanExitCount = cleanExits)
                }
            }
            saveHistory(context, updated)
        }

    /** Returns the current recovery step for diagnostics (0 = no crash history). */
    suspend fun getRecoveryStep(context: Context, appId: String): Int =
        withContext(Dispatchers.IO) {
            loadHistory(context, appId)?.recoveryStep ?: 0
        }

    /** Manually clears crash history — called from diagnostics / game settings "Reset Config". */
    suspend fun clearHistory(context: Context, appId: String) = withContext(Dispatchers.IO) {
        getFile(context, appId).delete()
        Timber.tag("CrashRecovery").i("Crash history cleared for appId=$appId")
    }

    // ── Config downgrade steps ──────────────────────────────────────────────────────────
    //
    // ARCHITECTURAL NOTE — why protonVersion is intentionally NOT changed here:
    //
    // container.wineVersion (which selects the actual Wine binary) is set by
    // ContainerUtils.applyPerGameContainerOverrides, which is called from getOrCreateContainer.
    // That call happens in PluviaMain.preLaunchApp BEFORE XServerScreen launches and calls
    // resolveGameConfig → applyRecovery. So any protonVersion written here arrives too late
    // to influence the current launch's container — it would be a no-op for this session and
    // potentially confuse the UI by showing a stale "recommended" version badge.
    //
    // The meaningful recovery work is: DX version downgrade (which DOES reach dxwrapper via
    // applyPerGameContainerOverridesInternal on the next launch because GameConfig is saved),
    // resolution/FPS reduction, and conservative Box64 settings. These all take effect
    // correctly through applyGameConfig or the container override path.

    private fun applyStep(config: GameConfig, step: Int): GameConfig = when (step) {
        1 -> config.copy(
            vsync           = false,
            resolutionScale = (config.resolutionScale - 0.10f).coerceAtLeast(0.50f),
            preset          = ConfigPreset.CUSTOM,
            notes           = config.notes + " [Recovery step 1: VSync off, resolution reduced]",
        )
        2 -> config.copy(
            dxVersion                = DxVersion.DX11,
            dxvkEnabled              = true,
            vkd3dEnabled             = false,
            resolutionScale          = 0.75f.coerceAtMost(config.resolutionScale),
            fpsLimit                 = if (config.fpsLimit == 0 || config.fpsLimit > 60) 60 else config.fpsLimit,
            vsync                    = false,
            box64BigBlock            = true,
            box64StrongMem           = true,
            preset                   = ConfigPreset.COMPATIBILITY,
            notes                    = config.notes + " [Recovery step 2: COMPATIBILITY mode — DX11 forced]",
        )
        3 -> config.copy(
            dxVersion                = DxVersion.DX9,
            dxvkEnabled              = true,
            vkd3dEnabled             = false,
            resolutionScale          = 0.65f.coerceAtMost(config.resolutionScale),
            fpsLimit                 = 30,
            vsync                    = false,
            esyncEnabled             = true,
            fsyncEnabled             = false,
            box64BigBlock            = false,   // more conservative JIT for stability
            box64StrongMem           = true,
            preset                   = ConfigPreset.COMPATIBILITY,
            notes                    = config.notes + " [Recovery step 3: DX9 forced, 30fps cap, conservative Box64]",
        )
        else -> config.copy(
            wineRuntime              = WineRuntime.WINE_GE,
            dxVersion                = DxVersion.DX9,
            dxvkEnabled              = true,
            vkd3dEnabled             = false,
            resolutionScale          = 0.50f,
            fpsLimit                 = 30,
            vsync                    = false,
            esyncEnabled             = true,
            fsyncEnabled             = false,
            box64BigBlock            = false,
            box64StrongMem           = true,
            preset                   = ConfigPreset.COMPATIBILITY,
            notes                    = config.notes + " [Recovery step 4: Wine-GE last resort]",
        )
    }

    // ── Persistence ───────────────────────────────────────────────────────────────────

    private fun loadHistory(context: Context, appId: String): CrashHistory? {
        return try {
            val file = getFile(context, appId)
            if (!file.exists()) null else json.decodeFromString<CrashHistory>(file.readText())
        } catch (e: Exception) {
            Timber.w(e, "Failed to load crash history for $appId")
            null
        }
    }

    private fun saveHistory(context: Context, history: CrashHistory) {
        try {
            val file = getFile(context, history.appId)
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(history))
        } catch (e: Exception) {
            Timber.e(e, "Failed to save crash history for ${history.appId}")
        }
    }

    private fun getFile(context: Context, appId: String): File {
        val dir = File(context.filesDir, SUBDIR)
        val safeId = appId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(dir, "$safeId.json")
    }
}
