package app.gamenative.launch

import android.content.Context
import app.gamenative.compat.CompatibilityDb
import app.gamenative.config.GameConfig
import app.gamenative.config.GameConfigRecommender
import app.gamenative.config.GameConfigStore
import app.gamenative.hardware.HardwareProfileCache
import app.gamenative.isolation.GamePaths
import app.gamenative.preflight.PreflightResult
import app.gamenative.preflight.PreflightRunner
import app.gamenative.profile.GameProfileOverrides
import app.gamenative.profile.GameProfileStore
import app.gamenative.profile.LaunchProfile
import app.gamenative.profile.ProfileStore
import app.gamenative.profile.WineRuntime
import app.gamenative.supervisor.DiagnosticStore
import app.gamenative.supervisor.LaunchSupervisor
import com.winlator.core.envvars.EnvVars
import kotlinx.coroutines.runBlocking
import java.io.File
import timber.log.Timber

/**
 * Orchestrator: resolve profile → run preflight → either block with reason or allow launch.
 * Call before starting the game process; never start the runner when preflight blocks.
 *
 * GameConfig merge order (see gamenative-game-config-engine): base env → DXVKHelper → GameConfig
 * → KnownGameFixes.extraEnvVars last (must use put, not putIfAbsent).
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
        Timber.tag("GameLaunch").i("runPreflight appId=$appId prefixPath=$prefixPath gameBinaryPath=${gameBinaryPath?.takeLast(80) ?: "null"}")
        val profile = ProfileStore.resolveForGame(context, appId, defaultProfileId)
        val result = PreflightRunner.run(
            context = context,
            profile = profile,
            gameBinaryPath = gameBinaryPath,
            prefixPath = prefixPath,
            appId = appId,
        )
        if (result is PreflightResult.Blocked) {
            val blocked = result as PreflightResult.Blocked
            Timber.tag("GameLaunch").w("Preflight blocked: ${blocked.reason} (code=${blocked.code})")
            val diagnostic = LaunchSupervisor.diagnosticFromPreflight(result.reason, result.code)
            DiagnosticStore.save(context, diagnostic, appId)
        } else {
            Timber.tag("GameLaunch").i("Preflight passed for appId=$appId")
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
        // Task 4 — Wine-GE: point WINE at Wine-GE when selected and downloaded
        if (overrides.wineRuntime == WineRuntime.WINE_GE) {
            val wineGePath = GamePaths.getWineGEPath(context)
            val wineGeDir = File(wineGePath)
            if (wineGeDir.exists() && File(wineGeDir, "bin/wine").exists()) {
                envVars.put("WINE", "$wineGePath/bin/wine")
                envVars.put("WINESERVER", "$wineGePath/bin/wineserver")
                envVars.put("WINELOADER", "$wineGePath/bin/wine")
                envVars.put("WINEDLLPATH", "$wineGePath/lib/wine")
            } else {
                envVars.put("WINE_GE_MISSING", "1")
            }
        }
    }

    /**
     * Resolves GameConfig for launch: load saved from GameConfigStore first; if none, run
     * recommender and optionally save. Call from a coroutine or runBlocking when already on a worker thread.
     */
    suspend fun resolveGameConfig(
        context: Context,
        appId: String,
        gameName: String,
        steamAppId: Long,
        saveIfFromRecommender: Boolean = true,
    ): GameConfig {
        val saved = GameConfigStore.load(context, appId)
        if (saved != null) return saved
        CompatibilityDb.load(context)
        val hardware = HardwareProfileCache.getProfile(context)
        val protonTier = CompatibilityDb.getProtonTierFor(steamAppId)
        val config = GameConfigRecommender.recommend(
            appId = steamAppId.toInt(),
            gameName = gameName,
            hardware = hardware,
            protonTier = protonTier,
        )
        if (saveIfFromRecommender) {
            GameConfigStore.save(context, appId, config)
        }
        return config
    }

    /**
     * Applies full GameConfig to env. Call after base container env and DXVKHelper.
     * KnownGameFixes.extraEnvVars are applied last with put (not putIfAbsent) so they win unconditionally.
     */
    fun applyGameConfig(context: Context, appId: String, config: GameConfig, envVars: EnvVars) {
        // DXVK
        envVars.put("DXVK_ASYNC", if (config.dxvkAsync) "1" else "0")
        envVars.put("DXVK_CONFIG_FILE", GamePaths.getDxvkConfigPath(context, appId))
        writeDxvkConfig(context, appId, config)
        envVars.put("DXVK_GPLASYNCCACHE", if (config.dxvkAsync) "1" else "0")

        // VKD3D
        if (config.vkd3dEnabled) {
            envVars.put("VKD3D_CONFIG", "pipeline_library_app_cache,no_upload_hvv,force_static_cbv")
            envVars.put("VKD3D_WORKER_THREAD_COUNT", config.vkd3dWorkerThreads.toString())
            envVars.put("VKD3D_SHADER_MODEL", config.vkd3dShaderModel)
            envVars.put("VKD3D_DISABLE_EXTENSIONS", "VK_KHR_ray_tracing_pipeline,VK_KHR_ray_query")
            envVars.put("VKD3D_DEBUG", "none")
        }

        // Wine sync
        envVars.put("WINEESYNC", if (config.esyncEnabled) "1" else "0")
        envVars.put("WINEFSYNC", if (config.fsyncEnabled) "1" else "0")
        envVars.put("WINEDEBUG", if (config.wineDebug) "" else "-all")

        // Box64
        envVars.put("BOX64_DYNAREC_BIGBLOCK", if (config.box64BigBlock) "1" else "0")
        envVars.put("BOX64_DYNAREC_STRONGMEM", if (config.box64StrongMem) "1" else "0")
        envVars.put("BOX64_DYNAREC_FASTROUND", if (config.box64DynarecFastRound) "1" else "0")
        envVars.put("BOX64_DYNAREC_ALIGNED_ATOMICS", "1")
        envVars.put("BOX64_LOG", "0")

        // FPS cap
        if (config.fpsLimit > 0) {
            envVars.put("DXVK_FRAME_RATE", config.fpsLimit.toString())
        }

        // VSync
        envVars.put("MESA_VK_WSI_PRESENT_MODE", if (config.vsync) "mailbox" else "immediate")

        // Wine runtime (Wine-GE when selected)
        if (config.wineRuntime == WineRuntime.WINE_GE) {
            val wineGePath = GamePaths.getWineGEPath(context)
            val wineGeDir = File(wineGePath)
            if (wineGeDir.exists() && File(wineGeDir, "bin/wine").exists()) {
                envVars.put("WINE", "$wineGePath/bin/wine")
                envVars.put("WINESERVER", "$wineGePath/bin/wineserver")
                envVars.put("WINELOADER", "$wineGePath/bin/wine")
                envVars.put("WINEDLLPATH", "$wineGePath/lib/wine")
            }
        }

        // KnownGameFixes.extraEnvVars last — must use put so overrides win unconditionally (never putIfAbsent)
        config.extraEnvVars.forEach { (key, value) ->
            envVars.put(key, value)
        }
    }

    private fun writeDxvkConfig(context: Context, appId: String, config: GameConfig) {
        val configText = buildString {
            appendLine("dxvk.enableAsync = ${if (config.dxvkAsync) "True" else "False"}")
            appendLine("dxvk.numCompilerThreads = ${config.dxvkNumCompilerThreads}")
            appendLine("dxvk.enableGraphicsPipelineLibrary = True")
            appendLine("dxvk.enableStateCache = True")
            appendLine("dxvk.stateCacheMaxSize = 256")
            appendLine("dxvk.deviceLocalMemoryMiB = ${config.dxvkDeviceLocalMemoryMiB}")
            appendLine("dxvk.maxFrameLatency = 1")
            appendLine("dxvk.presentMode = ${config.dxvkPresentMode}")
            appendLine("dxvk.trackPipelineLifetime = 0")
            appendLine("DXVK_HUD = none")
            appendLine("dxvk.logLevel = none")
        }
        val configFile = File(GamePaths.getDxvkConfigPath(context, appId))
        configFile.parentFile?.mkdirs()
        try {
            configFile.writeText(configText)
        } catch (e: Exception) {
            Timber.w(e, "Failed to write DXVK config for $appId")
        }
    }
}
