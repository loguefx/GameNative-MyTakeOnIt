package app.gamenative.config

/**
 * Hardcoded fixes for games that crash or misbehave without specific Proton/DX/env settings.
 * Override recommender defaults; can be extended later via remote JSON for new fixes without app update.
 *
 * Notes on global defaults (DO NOT DUPLICATE THESE — they are already applied universally):
 *   • DXVK_ASYNC=1, DXVK_GPLASYNCCACHE=1 — applied by applyGameConfig for all games when dxvkAsync=true
 *   • TU_DEBUG=noconform, mesa_glthread=true, MESA_SHADER_CACHE_MAX_SIZE=512MB — in Container.DEFAULT_ENV_VARS
 *   • PULSE_LATENCY_MSEC=144 — in Container.DEFAULT_ENV_VARS (override to 60 only when 144 causes hangs)
 *   • WINEESYNC=1, MESA_VK_WSI_PRESENT_MODE=mailbox — in Container.DEFAULT_ENV_VARS
 *
 * Add to extraEnvVars only what is genuinely game-specific:
 *   • DX12 games: VKD3D_CONFIG + PROTON_ENABLE_NVAPI=0 (always needed for Adreno DX12)
 *   • Games with intro video hangs: WINEDLLOVERRIDES=mfplat.dll=n,b;xaudio2_7.dll=n,b
 *   • Games with XAudio2 audio blocks: PULSE_LATENCY_MSEC=60 (override from default 144)
 */
object KnownGameFixes {

    data class GameFix(
        val appId: Int,
        val gameName: String,
        val protonVersion: ProtonVersion? = null,
        val dxVersion: DxVersion? = null,
        val requiresD8VK: Boolean = false,
        val resolutionScale: Float? = null,
        val fpsLimit: Int? = null,
        val extraEnvVars: Map<String, String> = emptyMap(),
        val launchArgs: String = "",
        val reason: String = "",
        /**
         * When true, bypasses ColdClientLoader and launches the game exe directly,
         * using replaceSteamApi (Goldberg steam_api.dll in the game directory) instead
         * of replaceSteamclientDll.  Use for games where ColdClientLoader fails silently
         * or hangs before CreateProcess — e.g. Aspyr/UE3 titles with their own sub-launcher.
         */
        val forceUseLegacyDrm: Boolean = false,
        /**
         * Relative exe path from the game's install directory, forward-slash separated
         * (e.g. "Build/Final/Bioshock2HD.exe").  Used as a guaranteed fallback when
         * container.executablePath is empty and getInstalledExe() cannot auto-detect the
         * correct binary (e.g. Steam appinfo not yet loaded at launch time).
         * Leave empty ("") to rely solely on auto-detection.
         */
        val gameExePath: String = "",
    )

    // DX12 VKD3D flags required for ALL DX12 games on Adreno.
    // pipeline_library_app_cache: persistent DX12 PSO cache — eliminates per-session stutter.
    // no_upload_hvv: disables a slow upload heap path that wastes time on unified-memory ARM devices.
    // force_static_cbv: forces static CBV root signature layout — avoids driver bugs on Turnip/Vortek.
    private const val VKD3D_BASE = "pipeline_library_app_cache,no_upload_hvv,force_static_cbv"

    private val fixes = mapOf(

        // ────────────────────────────────────────────────────────────────────────────────
        // BIOSHOCK 2 REMASTERED (409720) — Aspyr/Unreal Engine 3, DX9 via DXVK
        // ────────────────────────────────────────────────────────────────────────────────
        // Root cause of black screen: ColdClientLoader (steamclient_loader_x64.exe) starts
        // as an ARM64 native process and calls CreateProcess for Bioshock2HD.exe.  On Adreno
        // devices this CreateProcess silently fails — Bioshock2HD.exe never receives init_peb
        // and Wine produces no further output.  The three "Button" windows that appear in
        // wine_output.log are wfm.exe's own startup UI, not the game.
        //
        // Fix: forceUseLegacyDrm = true bypasses ColdClientLoader entirely.  We replace the
        // game-directory steam_api64.dll with the Goldberg standalone build (replaceSteamApi)
        // and launch Bioshock2HD.exe directly.  This is the same code path that works for all
        // other DRM-free / Aspyr titles and avoids the ColdClientLoader CreateProcess bug.
        //
        // Belt-and-suspenders: WINEDLLOVERRIDES prevents mfplat/xaudio2 from hanging if the
        // intro video is attempted; -nosplash -nointro skips it at the game-argv level.
        409720 to GameFix(
            appId = 409720,
            gameName = "BioShock 2 Remastered",
            dxVersion = DxVersion.DX9,
            forceUseLegacyDrm = true,
            // Hardcoded path guarantees the exe is found even when Steam appinfo is not yet loaded
            // at composition time and getInstalledExe() returns empty.
            gameExePath = "Build/Final/Bioshock2HD.exe",
            extraEnvVars = mapOf(
                // mfplat.dll=n,b: prevent intro video from hanging Wine's media pipeline (WMF).
                // xaudio2_7.dll=n,b: force native XAudio2 so audio init doesn't block render thread.
                // Do NOT add d3d9=n,b here. BioShock 2 runs under "Wrapper" graphics (D8VK/OpenGL
                // backend). Forcing DXVK's d3d9 shim explicitly in WINEDLLOVERRIDES breaks D8VK's
                // own dll-override chain and causes an immediate black screen.
                "WINEDLLOVERRIDES" to "mfplat.dll=n,b;xaudio2_7.dll=n,b",
                // DXVK_ASYNC belt-and-suspenders: applyGameConfig also sets this, but explicit
                // KnownGameFix override guarantees it wins even over a stale saved GameConfig.
                "DXVK_ASYNC" to "1",
                "DXVK_GPLASYNCCACHE" to "1",
                // PulseAudio latency: override from the container default (144 ms) to 60 ms.
                // 144 ms causes the XAudio2 initialisation to block for multiple frames on ARM,
                // producing a visible freeze at the title screen before audio starts.
                "PULSE_LATENCY_MSEC" to "60",
                // Disable strict D3D shader math — the Wine registry key strict_shader_math=1
                // from the container default config forces synchronous exact-precision shader
                // compilation, stalling the render thread on every new shader combination.
                // Setting WINE_D3D_CONFIG here overrides the registry value at runtime.
                "WINE_D3D_CONFIG" to "strict_shader_math=0",
            ),
            launchArgs = "-nosplash -nointro",
            reason = "ColdClientLoader CreateProcess silently fails on Adreno; bypass via direct exe + replaceSteamApi. " +
                "DXVK_ASYNC + WINE_D3D_CONFIG=strict_shader_math=0 prevent the freeze caused by synchronous shader compilation. " +
                "mfplat/xaudio2 overrides prevent intro-video hang.",
        ),

        // ────────────────────────────────────────────────────────────────────────────────
        // ELDEN RING (1245620) — FromSoftware, DX12 via VKD3D
        // ────────────────────────────────────────────────────────────────────────────────
        1245620 to GameFix(
            appId = 1245620,
            gameName = "Elden Ring",
            protonVersion = ProtonVersion.PROTON_9_0,
            dxVersion = DxVersion.DX12,
            extraEnvVars = mapOf(
                "PROTON_ENABLE_NVAPI" to "0",
                "VKD3D_CONFIG" to VKD3D_BASE,
            ),
            reason = "Proton 9.0 more stable than 10.0 for DX12 on Adreno; pipeline cache critical for hitching.",
        ),

        // ────────────────────────────────────────────────────────────────────────────────
        // GTA V (271590) — Rockstar RAGE engine, DX11 via DXVK
        // ────────────────────────────────────────────────────────────────────────────────
        271590 to GameFix(
            appId = 271590,
            gameName = "Grand Theft Auto V",
            protonVersion = ProtonVersion.PROTON_8_0,
            dxVersion = DxVersion.DX11,
            extraEnvVars = mapOf(
                "PROTON_NO_ESYNC" to "0",
            ),
            reason = "DX11 mode more stable than DX12/Vulkan on Adreno. Proton 8.0 avoids regression in 9.0 shader compiler.",
        ),

        // ────────────────────────────────────────────────────────────────────────────────
        // CYBERPUNK 2077 (1091500) — CD Projekt RED REDengine 4, DX12 via VKD3D
        // ────────────────────────────────────────────────────────────────────────────────
        1091500 to GameFix(
            appId = 1091500,
            gameName = "Cyberpunk 2077",
            protonVersion = ProtonVersion.PROTON_9_0,
            dxVersion = DxVersion.DX12,
            resolutionScale = 0.75f,
            extraEnvVars = mapOf(
                "VKD3D_CONFIG" to VKD3D_BASE,
                "PROTON_HIDE_NVIDIA_GPU" to "0",
                "VKD3D_SHADER_MODEL" to "6_5",
                "PROTON_ENABLE_NVAPI" to "0",
            ),
            reason = "DX12 with VKD3D pipeline cache; 0.75x scale for stable FPS on mobile. Shader model 6_5 for Adreno 7xx.",
        ),

        // ────────────────────────────────────────────────────────────────────────────────
        // RED DEAD REDEMPTION 2 (1174180) — Rockstar RAGE engine, DX11 via DXVK
        // ────────────────────────────────────────────────────────────────────────────────
        1174180 to GameFix(
            appId = 1174180,
            gameName = "Red Dead Redemption 2",
            protonVersion = ProtonVersion.PROTON_8_0,
            dxVersion = DxVersion.DX11,
            resolutionScale = 0.75f,
            extraEnvVars = mapOf(
                "WINEDLLOVERRIDES" to "vulkan-1=n;winevulkan=n",
            ),
            reason = "DX11 more stable than Vulkan native on Adreno for RDR2. Proton 8.0 avoids shader regression.",
        ),

        // ────────────────────────────────────────────────────────────────────────────────
        // THE WITCHER 3 (292030) — CD Projekt RED REDengine 3, DX11 via DXVK
        // ────────────────────────────────────────────────────────────────────────────────
        292030 to GameFix(
            appId = 292030,
            gameName = "The Witcher 3: Wild Hunt",
            protonVersion = ProtonVersion.PROTON_9_0,
            dxVersion = DxVersion.DX11,
            reason = "DX11 most compatible version on Wine/Proton; DX12 mode has Vortek/Turnip stability issues.",
        ),

        // ────────────────────────────────────────────────────────────────────────────────
        // COUNTER-STRIKE 2 (730) — Source 2, DX11 via DXVK
        // ────────────────────────────────────────────────────────────────────────────────
        730 to GameFix(
            appId = 730,
            gameName = "Counter-Strike 2",
            protonVersion = ProtonVersion.PROTON_10_0,
            dxVersion = DxVersion.DX11,
            extraEnvVars = mapOf(
                "PROTON_ENABLE_NVAPI" to "0",
                "DXVK_FRAME_RATE" to "60",
            ),
            reason = "CS2 on Proton 10.0 with DX11; frame cap prevents thermal throttle during competitive play.",
        ),

        // ────────────────────────────────────────────────────────────────────────────────
        // SOURCE ENGINE GAMES — Valve, DX9 via DXVK
        // ────────────────────────────────────────────────────────────────────────────────

        220 to GameFix(
            appId = 220,
            gameName = "Half-Life 2",
            dxVersion = DxVersion.DX9,
            launchArgs = "-dxlevel 90 -fullscreen -width 1280 -height 720",
            reason = "Source engine defaults to DX8 on Wine; -dxlevel 90 forces DX9 mode for correct lighting.",
        ),

        400 to GameFix(
            appId = 400,
            gameName = "Portal",
            dxVersion = DxVersion.DX9,
            launchArgs = "-dxlevel 90 -fullscreen",
            reason = "Source engine DX9; -dxlevel 90 prevents fallback to DX8 which breaks reflections.",
        ),

        620 to GameFix(
            appId = 620,
            gameName = "Portal 2",
            protonVersion = ProtonVersion.PROTON_9_0,
            dxVersion = DxVersion.DX9,
            launchArgs = "-dxlevel 90",
            reason = "Portal 2 runs stably on DX9; DX11 co-op mode has desync on Proton ARM.",
        ),

        550 to GameFix(
            appId = 550,
            gameName = "Left 4 Dead 2",
            dxVersion = DxVersion.DX9,
            launchArgs = "-dxlevel 90 -fullscreen",
            reason = "Source engine DX9; consistent performance, no advantage in DX10+ mode on Adreno.",
        ),

        440 to GameFix(
            appId = 440,
            gameName = "Team Fortress 2",
            dxVersion = DxVersion.DX9,
            launchArgs = "-dxlevel 90 -fullscreen",
            reason = "Source engine DX9; stable on Proton with no overrides needed.",
        ),

        // ────────────────────────────────────────────────────────────────────────────────
        // FALLOUT 4 (377160) — Bethesda Creation Engine, DX11 via DXVK
        // ────────────────────────────────────────────────────────────────────────────────
        377160 to GameFix(
            appId = 377160,
            gameName = "Fallout 4",
            protonVersion = ProtonVersion.PROTON_9_0,
            dxVersion = DxVersion.DX11,
            extraEnvVars = mapOf(
                // mfplat: prevents intro Bethesda logo video from hanging Wine's media pipeline.
                // xaudio2_7: forces native XAudio2 so the pip-boy radio and speech don't desync.
                "WINEDLLOVERRIDES" to "mfplat.dll=n,b;xaudio2_7.dll=n,b",
            ),
            reason = "Creation Engine DX11; mfplat/xaudio overrides required to prevent intro hang and audio desync.",
        ),

        // ────────────────────────────────────────────────────────────────────────────────
        // SKYRIM SPECIAL EDITION (489830) — Bethesda Creation Engine 64, DX11
        // ────────────────────────────────────────────────────────────────────────────────
        489830 to GameFix(
            appId = 489830,
            gameName = "The Elder Scrolls V: Skyrim Special Edition",
            protonVersion = ProtonVersion.PROTON_9_0,
            dxVersion = DxVersion.DX11,
            extraEnvVars = mapOf(
                // mfplat: intro movies use Windows Media Foundation; without native mfplat they hang.
                "WINEDLLOVERRIDES" to "mfplat.dll=n,b",
            ),
            reason = "Creation Engine 64 DX11; Platinum ProtonDB. mfplat override prevents intro video hang on cold start.",
        ),

        // ────────────────────────────────────────────────────────────────────────────────
        // FROMSOFT DX11 TITLES — DX11 via DXVK, Proton 9.0
        // ────────────────────────────────────────────────────────────────────────────────

        374320 to GameFix(
            appId = 374320,
            gameName = "Dark Souls III",
            protonVersion = ProtonVersion.PROTON_9_0,
            dxVersion = DxVersion.DX11,
            reason = "FromSoftware DX11; Proton 9.0 fixes controller vibration regression present in 10.0.",
        ),

        570940 to GameFix(
            appId = 570940,
            gameName = "Dark Souls: Remastered",
            protonVersion = ProtonVersion.PROTON_9_0,
            dxVersion = DxVersion.DX11,
            reason = "QLOC DX11 remaster; Proton 9.0 most stable, no additional env vars needed.",
        ),

        814380 to GameFix(
            appId = 814380,
            gameName = "Sekiro: Shadows Die Twice",
            protonVersion = ProtonVersion.PROTON_9_0,
            dxVersion = DxVersion.DX11,
            reason = "FromSoftware DX11; Platinum ProtonDB. No special fixes — works out of the box on Proton 9.0.",
        ),

        // ────────────────────────────────────────────────────────────────────────────────
        // NIERAUTOMATA (524220) — PlatinumGames, DX11 via DXVK
        // ────────────────────────────────────────────────────────────────────────────────
        524220 to GameFix(
            appId = 524220,
            gameName = "NieR: Automata",
            protonVersion = ProtonVersion.PROTON_9_0,
            dxVersion = DxVersion.DX11,
            extraEnvVars = mapOf(
                // Without this, the game ships a broken d3d11.dll stub that causes a white screen on load.
                "WINEDLLOVERRIDES" to "d3d11=n,b",
            ),
            reason = "PlatinumGames DX11; bundled d3d11.dll stub causes white screen — native DXVK d3d11 required.",
        ),

        // ────────────────────────────────────────────────────────────────────────────────
        // BORDERLANDS 2 + PRE-SEQUEL (49520 / 209540) — 2K/Gearbox, Unreal Engine 3, DX11
        // ────────────────────────────────────────────────────────────────────────────────
        49520 to GameFix(
            appId = 49520,
            gameName = "Borderlands 2",
            protonVersion = ProtonVersion.PROTON_9_0,
            dxVersion = DxVersion.DX11,
            reason = "UE3 DX11; Proton 9.0 stable. No additional env vars needed beyond global DXVK_ASYNC.",
        ),

        209540 to GameFix(
            appId = 209540,
            gameName = "Borderlands: The Pre-Sequel",
            protonVersion = ProtonVersion.PROTON_9_0,
            dxVersion = DxVersion.DX11,
            reason = "UE3 DX11; same engine as Borderlands 2, identical fix.",
        ),

        // ────────────────────────────────────────────────────────────────────────────────
        // METAL GEAR SOLID V (287700) — Konami Fox Engine, DX11
        // ────────────────────────────────────────────────────────────────────────────────
        287700 to GameFix(
            appId = 287700,
            gameName = "METAL GEAR SOLID V: The Phantom Pain",
            protonVersion = ProtonVersion.PROTON_9_0,
            dxVersion = DxVersion.DX11,
            reason = "Fox Engine DX11; Proton 9.0 avoids MGS V audio regression in 10.0 on ARM.",
        ),

        // ────────────────────────────────────────────────────────────────────────────────
        // DEEP ROCK GALACTIC (548430) — Ghost Ship Games, Unreal Engine 4, DX11
        // ────────────────────────────────────────────────────────────────────────────────
        548430 to GameFix(
            appId = 548430,
            gameName = "Deep Rock Galactic",
            protonVersion = ProtonVersion.PROTON_10_0,
            dxVersion = DxVersion.DX11,
            reason = "UE4 DX11; Platinum ProtonDB. Works on Proton 10.0 with no additional fixes.",
        ),

        // ────────────────────────────────────────────────────────────────────────────────
        // INDIE / XNA / FNA / UNITY TITLES — lightweight, Platinum ProtonDB
        // ────────────────────────────────────────────────────────────────────────────────

        413150 to GameFix(
            appId = 413150,
            gameName = "Stardew Valley",
            protonVersion = ProtonVersion.PROTON_9_0,
            dxVersion = DxVersion.DX11,
            reason = "Mono/.NET DX11; Proton 9.0 most stable for XNA-based titles on ARM Wine.",
        ),

        105600 to GameFix(
            appId = 105600,
            gameName = "Terraria",
            protonVersion = ProtonVersion.PROTON_9_0,
            dxVersion = DxVersion.DX11,
            reason = "FNA/XNA DX11; extremely lightweight — runs at native resolution on any device.",
        ),

        367520 to GameFix(
            appId = 367520,
            gameName = "Hollow Knight",
            protonVersion = ProtonVersion.PROTON_10_0,
            dxVersion = DxVersion.DX11,
            reason = "Unity DX11; Platinum ProtonDB. Native-like compatibility on Proton 10.0.",
        ),

        1145360 to GameFix(
            appId = 1145360,
            gameName = "Hades",
            protonVersion = ProtonVersion.PROTON_10_0,
            dxVersion = DxVersion.DX11,
            reason = "Supergiant engine DX11; Platinum ProtonDB. No fixes needed — just set DX11 explicitly.",
        ),

        504230 to GameFix(
            appId = 504230,
            gameName = "Celeste",
            protonVersion = ProtonVersion.PROTON_9_0,
            dxVersion = DxVersion.DX11,
            reason = "FNA DX11; Platinum ProtonDB. Extremely lightweight, runs perfectly without overrides.",
        ),

        632360 to GameFix(
            appId = 632360,
            gameName = "Risk of Rain 2",
            protonVersion = ProtonVersion.PROTON_10_0,
            dxVersion = DxVersion.DX11,
            reason = "Unity DX11; Platinum ProtonDB. Proton 10.0 handles Unity's Vulkan backend correctly.",
        ),

        945360 to GameFix(
            appId = 945360,
            gameName = "Among Us",
            protonVersion = ProtonVersion.PROTON_9_0,
            dxVersion = DxVersion.DX11,
            reason = "Unity DX11; minimal resource use, works perfectly on Proton 9.0 without overrides.",
        ),

        // ────────────────────────────────────────────────────────────────────────────────
        // PATH OF EXILE (238960) — Grinding Gear Games, DX11 via DXVK
        // ────────────────────────────────────────────────────────────────────────────────
        238960 to GameFix(
            appId = 238960,
            gameName = "Path of Exile",
            protonVersion = ProtonVersion.PROTON_9_0,
            dxVersion = DxVersion.DX11,
            extraEnvVars = mapOf(
                // DX12 mode flickers on Turnip/Vortek — force DX11 via DXVK for stable rendering.
                "WINEDLLOVERRIDES" to "d3d12=b",
            ),
            reason = "DX11 mode more stable than DX12 on Adreno; d3d12=b forces DXVK's DX11 path.",
        ),

        // ────────────────────────────────────────────────────────────────────────────────
        // PERSONA 5 ROYAL (1687950) — Atlus P-Studio, DX11 via DXVK
        // ────────────────────────────────────────────────────────────────────────────────
        1687950 to GameFix(
            appId = 1687950,
            gameName = "Persona 5 Royal",
            protonVersion = ProtonVersion.PROTON_9_0,
            dxVersion = DxVersion.DX11,
            reason = "Atlus DX11 port; Platinum ProtonDB. Proton 9.0 recommended over 10.0 for this title.",
        ),

        // ────────────────────────────────────────────────────────────────────────────────
        // DX12 SONY FIRST-PARTY PC PORTS — all require VKD3D pipeline cache on Adreno
        // ────────────────────────────────────────────────────────────────────────────────

        1593500 to GameFix(
            appId = 1593500,
            gameName = "God of War",
            protonVersion = ProtonVersion.PROTON_9_0,
            dxVersion = DxVersion.DX12,
            resolutionScale = 0.75f,
            extraEnvVars = mapOf(
                "VKD3D_CONFIG" to VKD3D_BASE,
                "PROTON_ENABLE_NVAPI" to "0",
                "VKD3D_SHADER_MODEL" to "6_5",
            ),
            reason = "Sony DX12 PC port; pipeline library cache essential on Adreno. 0.75x scale for stable 60fps.",
        ),

        1151640 to GameFix(
            appId = 1151640,
            gameName = "Horizon Zero Dawn",
            protonVersion = ProtonVersion.PROTON_9_0,
            dxVersion = DxVersion.DX12,
            resolutionScale = 0.75f,
            extraEnvVars = mapOf(
                "VKD3D_CONFIG" to VKD3D_BASE,
                "PROTON_ENABLE_NVAPI" to "0",
            ),
            reason = "Sony DX12 port; pipeline cache prevents per-session hitching. 0.75x scale for stable FPS.",
        ),

        1817070 to GameFix(
            appId = 1817070,
            gameName = "Marvel's Spider-Man Remastered",
            protonVersion = ProtonVersion.PROTON_9_0,
            dxVersion = DxVersion.DX12,
            resolutionScale = 0.75f,
            extraEnvVars = mapOf(
                "VKD3D_CONFIG" to VKD3D_BASE,
                "PROTON_ENABLE_NVAPI" to "0",
                "VKD3D_SHADER_MODEL" to "6_5",
            ),
            reason = "Sony DX12 port; identical VKD3D requirements to God of War. 0.75x scale for perf.",
        ),

        // ────────────────────────────────────────────────────────────────────────────────
        // RE ENGINE DX12 TITLES — Capcom, extremely PSO-heavy, pipeline cache mandatory
        // ────────────────────────────────────────────────────────────────────────────────

        582010 to GameFix(
            appId = 582010,
            gameName = "Monster Hunter: World",
            protonVersion = ProtonVersion.PROTON_9_0,
            dxVersion = DxVersion.DX12,
            resolutionScale = 0.75f,
            extraEnvVars = mapOf(
                "VKD3D_CONFIG" to VKD3D_BASE,
                "PROTON_ENABLE_NVAPI" to "0",
            ),
            reason = "RE Engine DX12; PSO-heavy title — pipeline library cache is essential to prevent per-fight stutter.",
        ),

        1196590 to GameFix(
            appId = 1196590,
            gameName = "Resident Evil Village",
            protonVersion = ProtonVersion.PROTON_9_0,
            dxVersion = DxVersion.DX12,
            resolutionScale = 0.75f,
            extraEnvVars = mapOf(
                "VKD3D_CONFIG" to VKD3D_BASE,
                "PROTON_ENABLE_NVAPI" to "0",
            ),
            reason = "RE Engine DX12; same PSO cache requirements as Monster Hunter World.",
        ),

        // ────────────────────────────────────────────────────────────────────────────────
        // DOOM ETERNAL (782330) — id Tech 7, DX12 / Vulkan hybrid
        // ────────────────────────────────────────────────────────────────────────────────
        782330 to GameFix(
            appId = 782330,
            gameName = "DOOM Eternal",
            protonVersion = ProtonVersion.PROTON_9_0,
            dxVersion = DxVersion.DX12,
            resolutionScale = 0.75f,
            extraEnvVars = mapOf(
                "VKD3D_CONFIG" to VKD3D_BASE,
                "PROTON_ENABLE_NVAPI" to "0",
            ),
            launchArgs = "+com_skipIntroVideo 1",
            reason = "id Tech 7 DX12; VKD3D pipeline cache required. Skip intro prevents startup hang on first boot.",
        ),

        // ────────────────────────────────────────────────────────────────────────────────
        // BALDUR'S GATE 3 (1086940) — Larian, DX11 (NOT DX12 — more stable on Adreno)
        // ────────────────────────────────────────────────────────────────────────────────
        1086940 to GameFix(
            appId = 1086940,
            gameName = "Baldur's Gate 3",
            protonVersion = ProtonVersion.PROTON_9_0,
            dxVersion = DxVersion.DX11,
            resolutionScale = 0.75f,
            reason = "Larian engine; DX12 mode has shader compilation hangs on Vortek/Turnip. DX11 via DXVK is stable.",
        ),

        // ────────────────────────────────────────────────────────────────────────────────
        // BATMAN ARKHAM KNIGHT (208650) — Rocksteady, Unreal Engine 3, DX11
        // ────────────────────────────────────────────────────────────────────────────────
        208650 to GameFix(
            appId = 208650,
            gameName = "Batman: Arkham Knight",
            protonVersion = ProtonVersion.PROTON_9_0,
            dxVersion = DxVersion.DX11,
            extraEnvVars = mapOf(
                // Without this, the game uses its own broken d3dx11_43 which causes startup crash on Wine.
                "WINEDLLOVERRIDES" to "d3dx11_43=n,b",
            ),
            reason = "UE3 DX11; ships broken d3dx11_43 — native Wine d3dx11 required to prevent startup crash.",
        ),

        // ────────────────────────────────────────────────────────────────────────────────
        // DOTA 2 (570) — Valve Source 2, Vulkan-native
        // ────────────────────────────────────────────────────────────────────────────────
        570 to GameFix(
            appId = 570,
            gameName = "Dota 2",
            protonVersion = ProtonVersion.PROTON_10_0,
            dxVersion = DxVersion.DX11,
            reason = "Source 2 Vulkan; Proton 10.0 has best Source 2 Vulkan support on Adreno via Turnip.",
        ),
    )

    fun get(appId: Int): GameFix? = fixes[appId]
}
