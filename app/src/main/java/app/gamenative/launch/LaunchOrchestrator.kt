package app.gamenative.launch

import android.content.Context
import app.gamenative.isolation.GamePaths
import app.gamenative.preflight.PreflightResult
import app.gamenative.preflight.PreflightRunner
import app.gamenative.profile.GameProfileStore
import app.gamenative.profile.LaunchProfile
import app.gamenative.profile.ProfileStore
import app.gamenative.supervisor.DiagnosticStore
import app.gamenative.supervisor.LaunchSupervisor
import com.winlator.core.envvars.EnvVars

/**
 * Orchestrator: resolve profile → run preflight → either block with reason or allow launch.
 * Call before starting the game process; never start the runner when preflight blocks.
 */
object LaunchOrchestrator {

    /**
     * Runs preflight checks for the given game. Returns Blocked with reason and code, or Ok.
     * Saves a launch diagnostic when blocked so the Diagnostics UI can show it.
     */
    fun runPreflight(
        context: Context,
        appId: String,
        gameBinaryPath: String?,
        prefixPath: String,
        defaultProfileId: String? = null,
    ): PreflightResult {
        val profile = ProfileStore.resolveForGame(context, appId, defaultProfileId)
        val result = PreflightRunner.run(
            context = context,
            profile = profile,
            gameBinaryPath = gameBinaryPath,
            prefixPath = prefixPath,
            appId = appId,
        )
        if (result is PreflightResult.Blocked) {
            val diagnostic = LaunchSupervisor.diagnosticFromPreflight(result.reason, result.code)
            DiagnosticStore.save(context, diagnostic, appId)
        }
        return result
    }

    /**
     * Merges per-game isolation env vars (DXVK_STATE_CACHE_PATH, etc.) into a mutable map.
     */
    fun applyIsolationEnv(context: Context, appId: String, env: MutableMap<String, String>) {
        env.putAll(GamePaths.getIsolationEnv(context, appId))
    }

    /**
     * Applies isolation env and per-game overrides to launch env. Call this **last** after
     * DXVKHelper and all other env setup so per-game overrides (frameCap, asyncShaders, vsync) win.
     */
    fun applyIsolationEnv(context: Context, appId: String, envVars: EnvVars) {
        GamePaths.getIsolationEnv(context, appId).forEach { (k, v) -> envVars.put(k, v) }
        val overrides = GameProfileStore.load(context, appId) ?: return
        if (overrides.frameCap != null) {
            envVars.put("DXVK_FRAME_RATE", if (overrides.frameCap == 0) "0" else overrides.frameCap.toString())
        }
        if (overrides.asyncShaders != null) {
            envVars.put("DXVK_ASYNC", if (overrides.asyncShaders) "1" else "0")
            envVars.put("DXVK_GPLASYNCCACHE", if (overrides.asyncShaders) "1" else "0")
        }
        if (overrides.vsync != null) {
            envVars.put("MESA_VK_WSI_PRESENT_MODE", if (overrides.vsync) "mailbox" else "immediate")
        }
    }
}
