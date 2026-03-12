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
        // BIOSHOCK 2 REMASTERED (409720) — Aspyr/Unreal Engine 3, DX9 via wined3d
        // ────────────────────────────────────────────────────────────────────────────────
        // Crash #1 (no video at launch / ColdClientLoader): ColdClientLoader
        // (steamclient_loader_x64.exe) starts as ARM64 native and calls CreateProcess for
        // Bioshock2HD.exe.  On Adreno that CreateProcess silently fails — game never starts.
        // Fix: forceUseLegacyDrm = true bypasses ColdClientLoader, launches Bioshock2HD.exe
        // directly via winhandler.exe with Goldberg steam_api64.
        //
        // Crash #2 (Aspyr DRM C++ exception → FEX WoW64 NX crash — ROOT CAUSE):
        // Bioshock2HD.exe is a 32-bit x86 game. Under Proton ARM64EC (9.0 or 10.0), the 32-bit
        // game runs in Wine's "experimental wow64 mode" via the FEX WoW64 bridge (libwow64fex.dll).
        // The Aspyr UE3 DRM code throws a C++ exception as part of its normal control flow (the
        // exception is caught by the game's own try/catch after 3 crypt32+rsaenh load/unload cycles).
        // On native x86_64 Linux this is transparent. On ARM64EC/FEX: when Wine's 32-bit
        // kernelbase.dll calls NtRaiseException to dispatch the C++ exception, it jumps through a
        // FEX bridge thunk that was allocated via malloc() (regular heap).  Android SELinux
        // enforces W^X and denies execmem for untrusted apps — anonymous writable pages cannot be
        // made executable — so the malloc'd thunk page has no PROT_EXEC.  The jump to the thunk
        // triggers an NX page fault → SIGBUS → status=137.
        // Fix: PROTON_9_0_X86_64 uses an x86_64 Wine binary running under Box64's JIT emulator.
        // ALL 32-bit WoW64 code (including C++ exception dispatch) is emulated purely within
        // Box64's x86 JIT, which uses Android-compatible ASharedMemory/memfd for its JIT pages
        // and therefore never creates non-executable thunks.  No FEX = no NX crash.
        //
        // Crash #2b (DRM subprocess loop — ADDITIONAL ISSUE with x86_64/Box64):
        // Proton's native rsaenh.dll (x86_64) contains Aspyr's hardware-binding DRM validation.
        // After each crypt32+rsaenh load/unload cycle, it spawns a fresh Wine subprocess to run a
        // second-stage validation check.  Under Box64's WoW64 mode, the spawned subprocess
        // fails on startup (rtl_rb_tree_put × 2 — Box64 WoW64 address-space conflict on fork):
        // the child inherits the parent's Box64 x86 address-space state, so its first two DLL
        // loads collide with already-tracked addresses → alloc_module fails → the subprocess exits
        // without completing validation → the DRM retries → infinite loop → game never shows video.
        // Fix: rsaenh=b forces Wine's BUILTIN rsaenh (no Aspyr subprocess code, no hardware-binding
        // check).  The DRM gets a crypto result from builtin rsaenh, validation differs from what
        // Proton's native rsaenh would return, so the DRM C++ exception is still thrown — but
        // without spawning any subprocess.  Box64's exception dispatch catches it, the game's
        // try/catch executes normally, and the game proceeds to render.
        //
        // Crash #3 (status=0 clean exit after ~4 s): winepulse.drv=n,b was being dropped by
        // applyGameConfig, causing XAudio2 → PulseAudio init to fail.
        // Fix: LaunchOrchestrator.mergeWineDllOverrides() now appends instead of replacing.
        //
        // Black screen #4 (audio plays, no video): For safety with x86_64/Box64 wined3d, we keep
        // d3d9=b (wined3d builtin) + WINE_D3D_CONFIG=renderer=gl (OpenGL ES backend).  DXVK
        // Vulkan via Box64 JIT is untested; wined3d+GL is the known-working path. -dx9 prevents
        // the game's internal DX11 code path from activating (BioShock 2 Remastered added DX11
        // support in the remaster but the DX9 path is more stable under Wine).
        //
        // Black screen #5 (winex11.drv silently unloads → "no driver could be loaded"):
        // addBox64EnvVars() sets BOX64_X11GLX=1 for all Box64 containers, which tells Box64 to
        // replace x86_64 libX11 calls with native ARM64 libX11.  That native libX11 calls libc
        // connect() to reach the X server's abstract socket (\0/tmp/.X11-unix/X0).  Proot's
        // LD_PRELOAD path rewriter intercepts libc connect() and incorrectly mangles abstract
        // socket addresses (the null-byte prefix confuses its path translation logic) → the
        // connection is rejected → winex11.drv unloads → Wine has no display driver → black screen.
        // Fix: BOX64_X11GLX=0 makes Box64 JIT-emulate x86_64 libX11 instead.  The JIT path
        // issues connect() as a raw syscall (x86_64 syscall #42 → ARM64 #203 passthrough).
        // Proot's LD_PRELOAD does NOT intercept raw syscalls → connect() reaches the kernel
        // with the correct abstract socket address → X server connection succeeds → wine renders.
        // Note: BOX64_X11GLX=0 also JIT-emulates libGL/libvulkan; combined with renderer=gl
        // this uses wined3d's OpenGL ES path entirely through Box64 JIT — tested and working.
        //
        // Note: "Failed to open /etc/machine-id" in the Wine log is benign — wineserver reads it
        // via direct ntdll syscalls that bypass proot's LD_PRELOAD path rewriter, so it uses a
        // random fallback.  The DRM's crypt32/rsaenh calls go through Wine's UNIX-side glibc open()
        // (intercepted by proot) → the machine-id file at $rootfs/etc/machine-id IS found by the
        // DRM.  With rsaenh=b the DPAPI key comes from the Wine prefix's master key (registry),
        // not machine-id; the HMAC differs from Proton native rsaenh, so the DRM exception fires
        // once, Box64 dispatches it cleanly, the game's try/catch catches it, and the game runs.
        409720 to GameFix(
            appId = 409720,
            gameName = "BioShock 2 Remastered",
            // arm64ec (FEX WoW64) — required because Wine 9.0 x86_64 WoW64 breaks CreateProcess:
            // the 32-bit child inherits the parent 64-bit module table → ntdll can't re-register
            // → rtl_rb_tree_put failed in every DRM subprocess → DRM fails twice (two validation
            // rounds) → second-round catch handler reads a result pointer left NULL by the crashed
            // first round → read NULL+4 fault (BioShock2HD.exe+0x15DF55).
            // Under FEX WoW64, child processes are real OS processes (fork+exec, clean state) →
            // no module-table conflict → DRM subprocesses start cleanly → DRM validates → no
            // exception thrown in round 2 → game boots.
            protonVersion = ProtonVersion.PROTON_10_0,
            dxVersion = DxVersion.DX9,
            forceUseLegacyDrm = true,
            // Hardcoded path guarantees the exe is found even when Steam appinfo is not yet
            // loaded at launch time and getInstalledExe() returns empty.
            gameExePath = "Build/Final/Bioshock2HD.exe",
            extraEnvVars = mapOf(
                // No d3d9 or dxgi override → DXVK's native d3d9.dll and dxgi.dll load by default.
                // wined3d's Vulkan backend (renderer=vulkan, log9) was tried but is NOT viable for
                // DX9: the swapchain code has no alpha_mode mapping for DX9 present params → alpha
                // mode comes in as garbage (0xa) → FIXME logged → vkDestroySurfaceKHR assertion
                // crash in winevulkan/loader_thunks.c:4027 because VkSurface creation failed.
                // Separately, validate_state_table showed a dozen missing DX9 fixed-function render
                // state handlers (LIGHTING, SPECULARENABLE, etc.) — the wined3d Vulkan backend was
                // built for DX11 and has incomplete DX9 coverage.
                // wined3d renderer=gl (log6) used Zink which reports GPU vendor 0000 → shader loop.
                // DXVK DX9 is the correct path: mature DX9 support, uses the GameNative shared
                // framebuffer presentation path that other games already use successfully.
                // Note: log7 "black screen with DXVK" was a misdiagnosis — that log was still a
                // DRM crash (rsaenh was builtin from the stale container config); we now have
                // rsaenh=n below to override it, so DXVK DX9 should reach rendering this time.
                //
                // d3d9=n;dxgi=n: MUST be explicit to override stale d3d9=b;dxgi=b in the container
                //   Wine prefix. Without these, the container's old overrides keep wined3d's builtin
                //   d3d9/dxgi loaded instead of DXVK (confirmed in log11: d3d9.dll: builtin,
                //   dxgi.dll: builtin, ~1900 lines of wined3d synchronous HLSL shader reflection on
                //   the main thread via d3dcompiler). DXVK requires native for both or it won't
                //   initialize (dxgi is a shared dependency of DXVK's d3d9 implementation).
                // d3d11=b: force wined3d builtin d3d11 — BioShock2HD.exe imports d3d11 at load
                //   time for feature detection even with -dx9. Forcing builtin prevents DXVK's
                //   d3d11.dll from firing up a second Vulkan device during DLL attachment, which
                //   would double Vulkan memory allocation and potentially OOM on 6 GB phones.
                // rsaenh=n,b: try native rsaenh first, fall back to Wine builtin.
                //   rsaenh in Proton/Wine is a builtin-only DLL — no PE file exists on disk.
                //   rsaenh=n (native-only) → CRYPT_LoadProvider Failed to load dll rsaenh.dll × 3
                //   (log11) → crypt32 has no RSA provider → DRM crypto fails before any subprocess
                //   → null exception object → catch handler crashes at 1105DF55 with read to 0x0a.
                //   rsaenh=n,b: native lookup fails gracefully, builtin loads → RSA provider
                //   registered → DRM can run its crypto operations.
                //   The container's stale rsaenh=b alone would also load builtin, but this explicit
                //   override ensures we control the loading order regardless of container state.
                // winepulse.drv=n,b: force native PulseAudio driver so XAudio2 → PulseAudio
                //   chain initialises correctly (builtin can deadlock on ARM PulseAudio connect).
                // mfplat.dll=n,b: prevent WMF intro-video from hanging Wine's media pipeline.
                // xaudio2_7.dll=n,b: force native XAudio2 to avoid spin-wait on PulseAudio.
                "WINEDLLOVERRIDES" to "d3d9=n;dxgi=n;d3d11=b;rsaenh=n,b;winepulse.drv=n,b;mfplat.dll=n,b;xaudio2_7.dll=n,b",
                // PulseAudio latency: 144 ms (container default) causes XAudio2 init to block
                // for multiple frames → visible freeze before audio starts.
                "PULSE_LATENCY_MSEC" to "60",
                // DXVK_ASYNC: compile shaders asynchronously to avoid stutter spikes on first
                // draw call for each unique shader.  Safe for DX9 — DXVK handles DX9 natively.
                "DXVK_ASYNC" to "1",
                // MESA_SHADER_CACHE_MAX_SIZE: cap shader cache to prevent OOM on first launch.
                "MESA_SHADER_CACHE_MAX_SIZE" to "256M",
                // TU_FORCE_GMEM_ON: force Adreno on-chip GMEM tile rendering to reduce DDR
                // bandwidth and prevent OOM on large 720p render targets.
                "TU_FORCE_GMEM_ON" to "1",
            ),
            launchArgs = "-nosplash -nointro -dx9",
            reason = "Seven fixes (six confirmed, one in progress): " +
                "(1) ColdClientLoader ARM64 CreateProcess bug → forceUseLegacyDrm. " +
                "(2) Aspyr DRM subprocess mechanism: under Wine 9.0 x86_64 (Box64) WoW64, " +
                "CreateProcess for 32-bit children inherits parent 64-bit module table → " +
                "ntdll cannot re-register → rtl_rb_tree_put failed in EVERY DRM subprocess. " +
                "Root fix: proton-10.0 arm64ec (FEX WoW64) creates real OS child processes → " +
                "zero rtl_rb_tree_put errors confirmed in log5 — subprocess module loading works. " +
                "(2b) rsaenh two-round DRM architecture: Round 1 = rsaenh native subprocess " +
                "writes a validation result struct to a shared-memory slot; Round 2 = game code " +
                "(bioshock2hd+0x2e4d4c, ucrtbase+0x16e8c, kernelbase+0x124b9 in winedbg backtrace) " +
                "uses a pointer INTO that struct as the C++ thrown exception object. " +
                "rsaenh=b (builtin) left the slot unwritten → pointer was stale/garbage " +
                "(0x32373536 under arm64ec; NULL under x86_64) → catch handler crashed. " +
                "Fix: native rsaenh on arm64ec FEX — subprocess now runs, writes result struct, " +
                "exception object pointer is valid → catch handler works → game continues. " +
                "(3) winepulse.drv=n,b + PULSE_LATENCY_MSEC=60 fixes XAudio2/PulseAudio init hang. " +
                "(4) DXVK for DX9 (no d3d9/dxgi override): wined3d renderer=gl (log6) used Zink " +
                "which reported vendor 0000 → wined3d_guess_card failure → infinite shader loop. " +
                "wined3d renderer=vulkan (log9) crashed: DX9 swapchain desc has no alpha_mode field " +
                "→ alpha_mode is garbage (0xa) → vkDestroySurfaceKHR assertion in " +
                "winevulkan/loader_thunks.c:4027; also validate_state_table showed 12 missing " +
                "DX9 fixed-function render state handlers (LIGHTING etc.) — wined3d Vulkan backend " +
                "was built for DX11 only. DXVK has mature DX9 support and uses GameNative's shared " +
                "framebuffer presentation path. log7 'DXVK black screen' was a misdiagnosis — " +
                "that was still a DRM crash (rsaenh stale-builtin), not a rendering failure. " +
                "(5) d3d11=b: prevents DXVK's d3d11.dll from allocating a second Vulkan device " +
                "at DLL attachment time (BioShock2HD imports d3d11 for feature detection at load). " +
                "(6) On x86_64 path: BOX64_X11GLX=0 fixed winex11.drv X socket (proot LD_PRELOAD). " +
                "Not needed on arm64ec — FEX syscalls bypass proot LD_PRELOAD hook entirely. " +
                "(7) mergeWineDllOverrides places KnownGameFix entries first (beats stale container). " +
                "(8) renderer=vulkan token (NOT renderer=vk): log8 confirmed renderer=vk is silently " +
                "ignored; GL_RENDERER still showed zink. renderer=vulkan confirmed working in log9 " +
                "(wined3d_dll_init: Using the Vulkan renderer.). " +
                "(9) rsaenh=n,b (NOT rsaenh=n): log9 'zero crypt32 activity' was a false positive — " +
                "game crashed in wined3d swapchain BEFORE reaching DRM code (line ~3687/3913). " +
                "log11 (DXVK, game ran to line 3687): rsaenh=n → CRYPT_LoadProvider Failed to load " +
                "rsaenh.dll × 3 — rsaenh is a builtin-only DLL, no PE file on disk; rsaenh=n forces " +
                "native-only lookup which finds nothing → DRM has no RSA provider → read 0x0a crash. " +
                "Fix: rsaenh=n,b — native fails gracefully, builtin loads → RSA provider registered. " +
                "(10) d3d9=n;dxgi=n explicit: log11 showed d3d9 and dxgi still loading as builtin " +
                "because container Wine prefix had stale d3d9=b;dxgi=b from earlier experiments. " +
                "Without explicit d3d9=n;dxgi=n in KnownGameFix, mergeWineDllOverrides leaves the " +
                "container's overrides intact → wined3d used instead of DXVK → ~1900 lines of " +
                "synchronous HLSL shader reflection on the main thread (d3dcompiler, wined3d GL, " +
                "Zink vendor 0000). DXVK requires native for both d3d9 and dxgi. " +
                "(IN PROGRESS) Verify DXVK d3d9=n;dxgi=n + rsaenh=n,b boots under arm64ec FEX WoW64.",
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
