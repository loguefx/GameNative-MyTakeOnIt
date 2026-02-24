package app.gamenative.launch

import android.content.Context
import app.gamenative.isolation.GamePaths
import app.gamenative.preflight.PreflightResult
import app.gamenative.preflight.PreflightRunner
import app.gamenative.profile.LaunchProfile
import app.gamenative.profile.ProfileStore
import app.gamenative.supervisor.DiagnosticStore
import app.gamenative.supervisor.LaunchSupervisor

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
}
