package app.gamenative.preflight

import android.app.ActivityManager
import android.content.Context
import app.gamenative.compat.CompatibilityDb
import app.gamenative.isolation.GamePaths
import app.gamenative.profile.GameProfileStore
import app.gamenative.profile.LaunchProfile
import app.gamenative.profile.WineRuntime
import com.winlator.xenvironment.ImageFs
import java.io.File
import timber.log.Timber

/**
 * Fail-fast checklist before launch. If any check fails, block with a clear reason.
 * Addresses the 6 crash causes from gamenative-game-config-engine: wrong Proton version
 * (handled by recommender), missing/corrupt prefix, wrong DX version (wrapper check),
 * Turnip/Mesa mismatch (logged), vm.max_map_count (in container script), Proton path.
 */
object PreflightRunner {

    private const val MIN_FREE_RAM_MB = 512L
    private const val MIN_FREE_STORAGE_MB = 500L

    /**
     * Runs all preflight checks. Returns Ok only if every check passes.
     */
    fun run(
        context: Context,
        profile: LaunchProfile,
        gameBinaryPath: String?,
        prefixPath: String,
        appId: String,
    ): PreflightResult {
        checkCpu()?.let { return it }
        checkRam(context)?.let { return it }
        checkStorage(context, appId)?.let { return it }
        checkWinePrefix(context, prefixPath)?.let { return it }
        checkWineRuntimePath(context, appId)?.let { return it }
        if (gameBinaryPath != null) {
            checkGameBinary(gameBinaryPath)?.let { return it }
        }
        if (profile.graphicsBackend == LaunchProfile.GraphicsBackend.DX12) {
            checkDx12Support(context, appId)?.let { return it }
        }
        return PreflightResult.Ok
    }

    /** Crash cause 2 — Wine prefix must exist and contain at least user.reg (valid prefix). */
    private fun checkWinePrefix(context: Context, prefixPath: String): PreflightResult? {
        val imageFs = ImageFs.find(context)
        val winePrefixDir = File(imageFs.wineprefix)
        if (!winePrefixDir.isDirectory) {
            return PreflightResult.Blocked(
                reason = "Wine prefix is missing or invalid. The prefix directory does not exist. Re-create the container or run container setup.",
                code = PreflightCode.WINE_PREFIX_INVALID,
            )
        }
        val userReg = File(winePrefixDir, "user.reg")
        val systemReg = File(winePrefixDir, "system.reg")
        if (!userReg.canRead() && !systemReg.canRead()) {
            return PreflightResult.Blocked(
                reason = "Wine prefix appears corrupt (no user.reg or system.reg). Re-create the container or run container setup.",
                code = PreflightCode.WINE_PREFIX_INVALID,
            )
        }
        return null
    }

    /** Crash cause 6 — Proton/Wine runtime path must exist so WINE env points to a valid binary. */
    private fun checkWineRuntimePath(context: Context, appId: String): PreflightResult? {
        val overrides = GameProfileStore.load(context, appId)
        val useWineGe = overrides?.wineRuntime == WineRuntime.WINE_GE
        if (useWineGe) {
            val wineGePath = GamePaths.getWineGEPath(context)
            val wineBin = File(wineGePath, "bin/wine")
            if (!wineBin.exists()) {
                return PreflightResult.Blocked(
                    reason = "Wine-GE is selected but not downloaded. Download Wine-GE in Game Settings → Compatibility, or switch to Stock Wine.",
                    code = PreflightCode.PROTON_PATH_NOT_FOUND,
                )
            }
            return null
        }
        val imageFs = ImageFs.find(context)
        val winePath = imageFs.winePath
        if (winePath.isNullOrEmpty()) {
            return PreflightResult.Blocked(
                reason = "Wine runtime path is not set. Install a compatibility layer (Proton/Wine) in Component settings.",
                code = PreflightCode.PROTON_PATH_NOT_FOUND,
            )
        }
        val wineDir = File(winePath)
        val wineBin = File(wineDir, "bin/wine")
        if (!wineDir.exists() || !wineBin.exists()) {
            return PreflightResult.Blocked(
                reason = "Proton/Wine runtime not found at $winePath. Reinstall the compatibility layer in Component settings.",
                code = PreflightCode.PROTON_PATH_NOT_FOUND,
            )
        }
        return null
    }

    private fun checkCpu(): PreflightResult? {
        val arch = System.getProperty("os.arch", "")
        if (!arch.equals("aarch64", ignoreCase = true) && !arch.equals("arm64", ignoreCase = true)) {
            return PreflightResult.Blocked(
                reason = "Unsupported CPU: $arch. GameNative requires ARM64.",
                code = PreflightCode.CPU_NOT_ARM64,
            )
        }
        return null
    }

    private fun checkRam(context: Context): PreflightResult? {
        val memInfo = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(memInfo)
        val freeMb = memInfo.availMem / (1024 * 1024)
        if (freeMb < MIN_FREE_RAM_MB) {
            return PreflightResult.Blocked(
                reason = "Insufficient memory: ${freeMb}MB free. Need at least ${MIN_FREE_RAM_MB}MB.",
                code = PreflightCode.INSUFFICIENT_RAM,
            )
        }
        return null
    }

    private fun checkStorage(context: Context, appId: String): PreflightResult? {
        val root = context.filesDir ?: return null
        val stat = android.os.StatFs(root.path)
        val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
        val freeMb = freeBytes / (1024 * 1024)
        if (freeMb < MIN_FREE_STORAGE_MB) {
            return PreflightResult.Blocked(
                reason = "Insufficient storage: ${freeMb}MB free. Need at least ${MIN_FREE_STORAGE_MB}MB for prefix and shader cache.",
                code = PreflightCode.INSUFFICIENT_STORAGE,
            )
        }
        return null
    }

    private fun checkGameBinary(path: String): PreflightResult? {
        val file = File(path)
        if (!file.exists() || !file.canRead()) {
            return PreflightResult.Blocked(
                reason = "Game executable not found or not readable: $path",
                code = PreflightCode.GAME_BINARY_MISSING,
            )
        }
        return null
    }

    private fun checkDx12Support(context: Context, appId: String): PreflightResult? {
        CompatibilityDb.load(context)
        val steamAppId = appId.removePrefix("STEAM_").substringBefore("(").toLongOrNull() ?: 0L
        if (steamAppId != 0L && !CompatibilityDb.allowDx12For(steamAppId)) {
            return PreflightResult.Blocked(
                reason = "DX12 is not verified for this game. Use DX11 (Stable mode) or add this title to the DX12 Verified list.",
                code = PreflightCode.DX12_DRIVER_BLACKLISTED,
            )
        }
        Timber.d("DX12 selected; VKD3D assumed present (driver blacklist in Phase 3)")
        return null
    }
}
