package app.gamenative.launch

import android.content.Context
import app.gamenative.adaptive.AdaptivePrelaunchTuner
import app.gamenative.adaptive.CrashRecoveryManager
import app.gamenative.compat.CompatibilityDb
import app.gamenative.config.GameConfig
import app.gamenative.config.GameConfigRecommender
import app.gamenative.config.GameConfigStore
import app.gamenative.config.KnownGameFixes
import app.gamenative.detection.GameEngineDetector
import app.gamenative.detection.GameExeScanner
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
     * Full game config resolution pipeline:
     *
     *  1. Return saved config if the user has already customized settings for this game.
     *  2. Run EXE scanner to detect DirectX version and audio dependencies.
     *  3. Run engine detector to fingerprint the game engine.
     *  4. Feed results into GameConfigRecommender for a hardware-optimized config.
     *  5. Apply crash recovery downgrade if the game has a crash history.
     *  6. Apply pre-launch adaptive tuning (thermal headroom + available RAM right now).
     *  7. Save the produced config so future launches skip steps 2–6 unless the game is updated.
     *
     * Call from a coroutine scope — never from the main thread.
     *
     * @param gameExeFile  Optional path to the game EXE for DirectX/audio auto-detection.
     *                     Pass null to skip scanning (e.g. for non-executable game types).
     * @param gameInstallDir  Optional game install directory for engine fingerprinting.
     */
    suspend fun resolveGameConfig(
        context: Context,
        appId: String,
        gameName: String,
        steamAppId: Long,
        gameExeFile: File? = null,
        gameInstallDir: File? = null,
        saveIfFromRecommender: Boolean = true,
    ): GameConfig {
        // Step 1 — return saved user config (preserves manual customizations)
        val saved = GameConfigStore.load(context, appId)
        if (saved != null) {
            // Re-apply KnownGameFixes.protonVersion in-memory so the version picker "Recommended"
            // badge and diagnostic UI always reflect the current fix, even when the stored config
            // was generated before the fix was added or was using a different version.
            // We intentionally do NOT re-save here: only the recommender path (first launch or
            // "Recommended" button) should update the stored config, preserving user intent.
            val fixedSaved = run {
                val fixId = try { appId.substringAfterLast("_").toLong() } catch (_: Exception) { 0L }
                val fix = if (fixId != 0L) KnownGameFixes.get(fixId.toInt()) else null
                val requiredVersion = fix?.protonVersion
                if (requiredVersion != null && requiredVersion != saved.protonVersion) {
                    Timber.tag("GameLaunch").i(
                        "KnownGameFix: in-memory protonVersion override ${saved.protonVersion} → $requiredVersion for $appId"
                    )
                    saved.copy(protonVersion = requiredVersion)
                } else saved
            }
            val recovered = CrashRecoveryManager.applyRecovery(context, appId, fixedSaved)
            return AdaptivePrelaunchTuner.tune(recovered, context)
        }

        // Step 2 — EXE scan: detect DX version, XAudio2, mfplat from import table
        val scanResult = if (gameExeFile != null) {
            GameExeScanner.scan(appId, gameExeFile)
        } else {
            app.gamenative.detection.ExeScanResult(dxVersion = app.gamenative.config.DxVersion.AUTO)
        }

        // Step 3 — engine detection: fingerprint game engine from install dir
        val engineHint = if (gameInstallDir != null) {
            GameEngineDetector.detect(gameInstallDir)
        } else {
            app.gamenative.detection.EngineHint.UNKNOWN
        }

        if (scanResult.dxVersion != app.gamenative.config.DxVersion.AUTO || engineHint != app.gamenative.detection.EngineHint.UNKNOWN) {
            Timber.tag("GameLaunch").i(
                "Auto-detected: dx=${scanResult.dxVersion} engine=${engineHint.name} " +
                    "xaudio=${scanResult.needsXAudio2Override} mfplat=${scanResult.needsMfplatOverride}",
            )
        }

        // Step 4 — recommender: build hardware-optimal config using all detected inputs
        CompatibilityDb.load(context)
        val hardware = HardwareProfileCache.getProfile(context)
        val protonTier = CompatibilityDb.getProtonTierFor(steamAppId)
        val config = GameConfigRecommender.recommend(
            appId      = steamAppId.toInt(),
            gameName   = gameName,
            hardware   = hardware,
            protonTier = protonTier,
            scanResult = scanResult,
            engineHint = engineHint,
        )

        // Step 5 — crash recovery: downgrade config if game has a crash history
        val recovered = CrashRecoveryManager.applyRecovery(context, appId, config)

        // Step 6 — adaptive pre-launch tuning: scale down for current thermal + memory pressure
        val tuned = AdaptivePrelaunchTuner.tune(recovered, context)

        // Step 7 — persist so the next launch is fast (skips steps 2–6)
        if (saveIfFromRecommender) {
            // Save the recommender result (not the tuned one) so the saved config reflects
            // the game's true recommended settings, not a momentary thermal reduction.
            GameConfigStore.save(context, appId, config)
        }
        return tuned
    }

    /**
     * Record a game exit for crash recovery tracking.
     * Call from the guest exit callback in XServerScreen / SteamService.
     */
    suspend fun recordGameExit(
        context: Context,
        appId: String,
        exitCode: Int,
        playDurationMs: Long,
    ) {
        CrashRecoveryManager.recordExit(context, appId, exitCode, playDurationMs)
    }

    /**
     * Applies full GameConfig to env. Call after base container env and DXVKHelper.
     * KnownGameFixes.extraEnvVars are always applied last (even over a saved config) so they win
     * unconditionally regardless of whether the user has a persisted config for this game.
     */
    fun applyGameConfig(context: Context, appId: String, config: GameConfig, envVars: EnvVars) {
        // ── DXVK ─────────────────────────────────────────────────────────────────────
        envVars.put("DXVK_ASYNC", if (config.dxvkAsync) "1" else "0")
        envVars.put("DXVK_GPLASYNCCACHE", if (config.dxvkAsync) "1" else "0")

        // Write the per-game dxvk.conf and point DXVK_CONFIG_FILE at it.
        // DXVK_CONFIG_FILE is sufficient — DXVK_ASYNC=1 and DXVK_GPLASYNCCACHE=1 are already
        // set unconditionally by DXVKHelper (see DXVKHelper.java). We do NOT override the
        // DXVK_CONFIG env var here: the "Wrapper" graphics driver uses D8VK (DXVK's OpenGL
        // backend) which does not recognise all standard-DXVK config keys. Injecting an inline
        // config with unrecognised keys (dxvk.hud, dxvk.presentMode, etc.) causes D8VK to abort
        // its initialisation, producing a black screen immediately on launch.
        val dxvkConfigPath = GamePaths.getDxvkConfigPath(context, appId)
        envVars.put("DXVK_CONFIG_FILE", dxvkConfigPath)
        writeDxvkConfig(context, appId, config)

        // VKD3D
        if (config.vkd3dEnabled) {
            envVars.put("VKD3D_CONFIG", "pipeline_library_app_cache,no_upload_hvv,force_static_cbv")
            envVars.put("VKD3D_WORKER_THREAD_COUNT", config.vkd3dWorkerThreads.toString())
            envVars.put("VKD3D_SHADER_MODEL", config.vkd3dShaderModel)
            envVars.put("VKD3D_DISABLE_EXTENSIONS", "VK_KHR_ray_tracing_pipeline,VK_KHR_ray_query")
            envVars.put("VKD3D_DEBUG", "none")
            // DXVKHelper.setEnvVars (called by XServerScreen for DXVK containers) appends
            // winepulse.drv=n,b to WINEDLLOVERRIDES. DXVKHelper.setVKD3DEnvVars does NOT —
            // so VKD3D (DX12) games would get Wine's built-in winepulse.drv instead of the
            // native PulseAudio driver, causing stuttering or completely silent audio.
            // Apply it explicitly here so DX12 games get the same audio treatment as DX9/11.
            mergeWineDllOverrides(envVars, "winepulse.drv=n,b")
        }

        // Wine sync
        envVars.put("WINEESYNC", if (config.esyncEnabled) "1" else "0")
        envVars.put("WINEFSYNC", if (config.fsyncEnabled) "1" else "0")
        // WINEDEBUG is set in XServerScreen from Settings > Debug (PrefManager); do not overwrite here so global Wine debug applies to all launches.

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

        // config.extraEnvVars (from recommender or saved config) applied first.
        // WINEDLLOVERRIDES is MERGED (appended) rather than replaced so that infrastructure
        // overrides set earlier by DXVKHelper (winepulse.drv=n,b for PulseAudio, d3d8=n,b for
        // DX8 games) are never silently dropped when a saved config or KnownGameFix supplies
        // its own DLL overrides.  All other env vars still use put (KnownGameFix wins).
        config.extraEnvVars.forEach { (key, value) ->
            if (key == "WINEDLLOVERRIDES") mergeWineDllOverrides(envVars, value)
            else envVars.put(key, value)
        }

        // KnownGameFixes ALWAYS wins — re-apply even if a saved config already set different values.
        // WINEDLLOVERRIDES is merged (same reason as above) so DXVKHelper's audio overrides survive.
        val steamAppId = try { appId.toLong() } catch (_: Exception) {
            try { appId.substringAfterLast("_").toLong() } catch (_: Exception) { 0L }
        }
        if (steamAppId != 0L) {
            KnownGameFixes.get(steamAppId.toInt())?.let { fix ->
                fix.extraEnvVars.forEach { (key, value) ->
                    if (key == "WINEDLLOVERRIDES") mergeWineDllOverrides(envVars, value)
                    else envVars.put(key, value)
                }
                if (fix.launchArgs.isNotEmpty()) {
                    envVars.put("GN_WINE_LAUNCH_ARGS", fix.launchArgs)
                }
            }
        }

        // If no KnownGameFix overrode launch args, use the config's wineLaunchArgs (from recommender).
        // Game-specific launch args flow into ColdClientLoader.ini ExeCommandLine via GN_WINE_LAUNCH_ARGS.
        if (envVars.get("GN_WINE_LAUNCH_ARGS").isEmpty() && config.wineLaunchArgs.isNotEmpty()) {
            envVars.put("GN_WINE_LAUNCH_ARGS", config.wineLaunchArgs)
        }

        // Diagnostic: log all final env vars that affect rendering, audio, and Wine behaviour.
        // These lines appear in logcat with tag "GameLaunch" — grep for "LaunchEnv" to filter.
        val dllOverrides = envVars.get("WINEDLLOVERRIDES")
        if (dllOverrides.isEmpty()) {
            Timber.tag("GameLaunch").w(
                "LaunchEnv appId=$appId WINEDLLOVERRIDES=<empty> — audio/DRM overrides not applied!"
            )
        } else {
            Timber.tag("GameLaunch").d("LaunchEnv appId=$appId WINEDLLOVERRIDES=[$dllOverrides]")
        }
        val wineD3dConfig = envVars.get("WINE_D3D_CONFIG")
        if (wineD3dConfig.isNotEmpty()) {
            Timber.tag("GameLaunch").d("LaunchEnv appId=$appId WINE_D3D_CONFIG=[$wineD3dConfig]")
        } else {
            Timber.tag("GameLaunch").d("LaunchEnv appId=$appId WINE_D3D_CONFIG=<not set> (wined3d will use its compiled-in default renderer)")
        }
        val pulseLatency = envVars.get("PULSE_LATENCY_MSEC")
        if (pulseLatency.isNotEmpty()) {
            Timber.tag("GameLaunch").d("LaunchEnv appId=$appId PULSE_LATENCY_MSEC=$pulseLatency")
        }
        val winePath = envVars.get("WINEPATH")
        if (winePath.isNotEmpty()) {
            Timber.tag("GameLaunch").d("LaunchEnv appId=$appId WINEPATH=$winePath")
        }
        val wineArgs = envVars.get("GN_WINE_LAUNCH_ARGS")
        if (wineArgs.isNotEmpty()) {
            Timber.tag("GameLaunch").d("LaunchEnv appId=$appId GN_WINE_LAUNCH_ARGS=[$wineArgs]")
        }
        val dxvkLogLevel = envVars.get("DXVK_LOG_LEVEL")
        Timber.tag("GameLaunch").d("LaunchEnv appId=$appId DXVK_LOG_LEVEL=$dxvkLogLevel DXVK_LOG_TO_STDERR=${envVars.get("DXVK_LOG_TO_STDERR")}")
        val display = envVars.get("DISPLAY")
        Timber.tag("GameLaunch").d("LaunchEnv appId=$appId DISPLAY=$display (winex11.drv abstract socket: \\0/tmp/.X11-unix/X0)")
    }

    /**
     * Merges [newOverrides] (semicolon-separated WINEDLLOVERRIDES tokens) into the existing
     * WINEDLLOVERRIDES in [envVars].
     *
     * New overrides WIN over existing entries for the same DLL name.  This ensures that
     * KnownGameFix entries (applied last, highest-priority layer) override any stale value
     * that was written to the container config by an earlier session (e.g. rsaenh=n,b set
     * via ADB debug that must not block a later rsaenh=b fix).  Wine parses
     * WINEDLLOVERRIDES left-to-right with first-match-wins, so new entries are placed first.
     *
     * Existing entries for DLLs NOT mentioned in newOverrides are preserved, so infrastructure
     * overrides written earlier by DXVKHelper (e.g. winepulse.drv=n,b, d3d8=n,b) survive when
     * a KnownGameFix adds its own overrides.
     */
    private fun mergeWineDllOverrides(envVars: com.winlator.core.envvars.EnvVars, newOverrides: String) {
        val existing = envVars.get("WINEDLLOVERRIDES")
        if (existing.isEmpty()) {
            envVars.put("WINEDLLOVERRIDES", newOverrides)
            return
        }
        // Parse new overrides; extract the DLL name (part before '=') for each entry.
        val newEntries = newOverrides.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        val newDllNames = newEntries.map { it.substringBefore('=').trim().lowercase() }.toSet()
        // Keep existing entries only for DLLs not covered by the new overrides.
        val kept = existing.split(";").map { it.trim() }.filter { entry ->
            entry.isNotEmpty() && entry.substringBefore('=').trim().lowercase() !in newDllNames
        }
        // New entries come first so Wine's left-to-right first-match parsing picks them up.
        val merged = (newEntries + kept).joinToString(";")
        envVars.put("WINEDLLOVERRIDES", merged)
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
            appendLine("dxvk.hud = none")
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
