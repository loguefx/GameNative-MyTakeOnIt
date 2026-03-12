package app.gamenative.config

import app.gamenative.detection.EngineHint
import app.gamenative.detection.ExeScanResult
import app.gamenative.hardware.GpuVendor
import app.gamenative.hardware.HardwareProfile
import app.gamenative.profile.WineRuntime
import app.gamenative.proton.ProtonTier

/**
 * Recommends a full GameConfig from hardware + ProtonDB tier + auto-detection results.
 *
 * ## Inputs (in descending override priority)
 *  1. [KnownGameFixes] entry — wins unconditionally when appId is in the database
 *  2. [ExeScanResult]  — DirectX version from EXE import table, audio dependency flags
 *  3. [EngineHint]     — game engine fingerprint drives Proton version and DX defaults
 *  4. [ProtonTier]     — ProtonDB community compatibility rating
 *  5. [HardwareProfile] — device GPU tier, RAM, display, thermal headroom
 *
 * ## Max-FPS strategy for Adreno/Turnip
 *  1. Pin game to P-cores (cpuAffinityMask = pCoreMask)
 *  2. Full DXVK async stack — dxvkAsync=true + DXVK_GPLASYNCCACHE applied by LaunchOrchestrator
 *  3. MAILBOX present mode — lowest latency without screen-tear risk on mobile
 *  4. Memory budget = 40 % of total RAM — prevents texture eviction stutters
 *  5. Compiler threads = P-cores − 1 — leave 1 core for the render thread
 *  6. DX12 games get VKD3D pipeline cache by default — critical for Adreno hitching
 *  7. Resolution scaled to hardware tier — Adreno 730+ can hold 1.0x at 60 fps
 */
object GameConfigRecommender {

    fun recommend(
        appId: Int,
        gameName: String,
        hardware: HardwareProfile,
        protonTier: ProtonTier,
        scanResult: ExeScanResult = ExeScanResult(dxVersion = DxVersion.AUTO),
        engineHint: EngineHint   = EngineHint.UNKNOWN,
    ): GameConfig {
        val knownFix = KnownGameFixes.get(appId)

        // ── Proton version ────────────────────────────────────────────────────────────
        // Priority: KnownGameFix > EngineHint preferred Proton > ProtonTier mapping
        val protonVersion = knownFix?.protonVersion
            ?: engineHint.preferredProton
            ?: when (protonTier) {
                ProtonTier.PLATINUM -> ProtonVersion.PROTON_10_0
                ProtonTier.GOLD     -> ProtonVersion.PROTON_9_0
                ProtonTier.SILVER   -> ProtonVersion.PROTON_8_0
                ProtonTier.BRONZE   -> ProtonVersion.PROTON_8_0
                ProtonTier.BORKED   -> ProtonVersion.WINE_GE_LATEST
                else                -> ProtonVersion.PROTON_9_0
            }

        // ── DirectX version ────────────────────────────────────────────────────────────
        // Priority: KnownGameFix > ExeScanResult > EngineHint default > AUTO
        val dxVersion = knownFix?.dxVersion
            ?: scanResult.dxVersion.takeIf { it != DxVersion.AUTO }
            ?: engineHint.defaultDxVersion
            ?: DxVersion.AUTO

        // ── Resolution scale — tiered by GPU generation + RAM ────────────────────────
        val resolutionScale = knownFix?.resolutionScale
            ?: when {
                hardware.totalRamMb >= 12_000 && isHighEndAdreno(hardware) -> 1.0f
                hardware.totalRamMb >= 8_000  && isHighEndAdreno(hardware) -> 1.0f
                hardware.totalRamMb >= 8_000  && isMidRangeAdreno(hardware) -> 0.85f
                hardware.totalRamMb >= 8_000                                -> 0.85f
                hardware.totalRamMb >= 6_000                                -> 0.75f
                else                                                         -> 0.65f
            }

        // ── FPS limit ─────────────────────────────────────────────────────────────────
        val fpsLimit = knownFix?.fpsLimit
            ?: when {
                hardware.thermalHeadroom < 0.3f                                  -> 30
                hardware.displayRefreshHz >= 120 && isHighEndAdreno(hardware)
                    && hardware.totalRamMb >= 12_000                              -> 90
                else                                                              -> 60
            }

        // ── DXVK memory budget (40 % of total RAM, 1 GB–6 GB) ──────────────────────
        val dxvkMemMiB = (hardware.totalRamMb * 0.40).toInt().coerceIn(1_024, 6_144)

        // ── DXVK compiler threads (P-cores − 1, minimum 2) ─────────────────────────
        val compilerThreads = (hardware.cpuPCoreCount - 1).coerceAtLeast(2)

        // ── VKD3D shader model — 6_5 on Vulkan 1.3 Adreno (730+), else 6_3 ─────────
        val shaderModel = when {
            hardware.gpuVendor == GpuVendor.ADRENO && hardware.supportsVulkan13 -> "6_5"
            else -> "6_3"
        }

        // ── Extra env vars — start with sane defaults for detected conditions ────────
        val baseExtraEnvVars = buildMap<String, String> {
            // DX12 games on Adreno must always have the VKD3D pipeline cache.
            // Without it every PSO recompiles per session → severe hitching on first encounters.
            if (dxVersion == DxVersion.DX12) {
                put("VKD3D_CONFIG", "pipeline_library_app_cache,no_upload_hvv,force_static_cbv")
                put("PROTON_ENABLE_NVAPI", "0")
            }
            // NOTE: DX9 games use DXVK (no d3d9 override) — do NOT add d3d9=b here.
            //
            // Previous versions of this recommender added d3d9=b + WINE_D3D_CONFIG=renderer=gl
            // for all 32-bit DX9 games, based on a hypothesis that "FEX WoW64 misaligns the
            // Display* pointer for vkCreateXlibSurfaceKHR".  Extensive per-game testing
            // (BioShock 2 Remastered, 9 log iterations) disproved this and revealed that
            // BOTH wined3d rendering paths fail structurally for DX9 — not just for one game:
            //
            //  wined3d renderer=gl (Zink path):
            //    Zink's OpenGL wrapper always reports GL_VENDOR "0000" instead of the real
            //    Qualcomm vendor ID → wined3d_guess_card finds no card selector → falls back
            //    to generic GPU settings → shader compilation stall loop → permanent black screen.
            //    This affects every DX9 game on this platform, not specific titles.
            //
            //  wined3d renderer=vulkan (direct Vulkan path):
            //    Wine's wined3d Vulkan backend was built for DX11 (VKD3D path). For DX9 it is
            //    structurally incomplete: validate_state_table shows a dozen missing DX9
            //    fixed-function render states (LIGHTING, SPECULARENABLE, COLORVERTEX, etc.),
            //    and wined3d_swapchain_vk_create_vulkan_swapchain has no alpha_mode mapping for
            //    DX9 present params (alpha_mode arrives as garbage 0xa) → VkSurface creation
            //    fails → vkDestroySurfaceKHR assertion crash in winevulkan/loader_thunks.c.
            //    Again, structural — affects every DX9 game.
            //
            //  DXVK (native d3d9.dll, no override):
            //    DXVK has a mature, complete DX9 implementation that bypasses wined3d entirely.
            //    It uses the GameNative shared framebuffer presentation path already used by
            //    DX11/DX12 games. Confirmed working for 32-bit DX9 under arm64ec FEX WoW64.
            //
            // Consequence: DXVK is the correct and only viable default for all DX9 games on
            // this platform. No d3d9=b override is needed or should be added here. If a
            // specific game has a conflict with DXVK, that game gets a KnownGameFix entry.

            // XAudio2 override — detected from EXE scan (or from KnownGameFix below)
            if (scanResult.needsXAudio2Override) {
                val existing = get("WINEDLLOVERRIDES") ?: ""
                val addition = "xaudio2_7.dll=n,b"
                put("WINEDLLOVERRIDES", if (existing.isEmpty()) addition else "$existing;$addition")
            }
            // mfplat override — prevents intro video hang when game imports WMF
            if (scanResult.needsMfplatOverride) {
                val existing = get("WINEDLLOVERRIDES") ?: ""
                val addition = "mfplat.dll=n,b"
                put("WINEDLLOVERRIDES", if (existing.isEmpty()) addition else "$existing;$addition")
            }
            // KnownGameFix overrides always win — applied last into the base map
            putAll(knownFix?.extraEnvVars ?: emptyMap())
        }

        val wineRuntime = if (protonTier == ProtonTier.BORKED) WineRuntime.WINE_GE else WineRuntime.STOCK

        return GameConfig(
            appId                    = appId,
            protonVersion            = protonVersion,
            wineRuntime              = wineRuntime,
            dxVersion                = dxVersion,
            dxvkEnabled              = dxVersion != DxVersion.DX12,
            vkd3dEnabled             = dxVersion == DxVersion.DX12,
            d8vkEnabled              = knownFix?.requiresD8VK ?: false,
            resolutionScale          = resolutionScale,
            fpsLimit                 = fpsLimit,
            vsync                    = false,
            dxvkAsync                = true,
            dxvkNumCompilerThreads   = compilerThreads,
            dxvkDeviceLocalMemoryMiB = dxvkMemMiB,
            dxvkPresentMode          = "MAILBOX",
            vkd3dWorkerThreads       = (hardware.cpuPCoreCount / 2).coerceAtLeast(2),
            vkd3dShaderModel         = shaderModel,
            esyncEnabled             = true,
            // WINEFSYNC requires futex2 kernel support unavailable on Android; ESYNC covers the same
            // threading wins via eventfd. Defaulting to false avoids log floods and fallback slowdowns.
            fsyncEnabled             = false,
            wineDebug                = false,
            box64BigBlock            = true,
            box64StrongMem           = true,
            box64DynarecFastRound    = true,
            cpuAffinityMask          = hardware.cpuPCoreMask,
            extraEnvVars             = baseExtraEnvVars,
            wineLaunchArgs           = knownFix?.launchArgs ?: "",
            preset                   = ConfigPreset.RECOMMENDED,
            notes                    = buildRecommendationNotes(hardware, protonVersion, protonTier, engineHint, dxVersion),
        )
    }

    /**
     * Adreno 730, 740, 750 — flagship (Snapdragon 8 Gen 2/3).
     * Can hold native resolution at 60 fps for most DX11 titles.
     */
    internal fun isHighEndAdreno(hw: HardwareProfile): Boolean {
        return hw.gpuVendor == GpuVendor.ADRENO &&
            hw.gpuModel.contains(Regex("Adreno.*(7[3-9][0-9])"))
    }

    /**
     * Adreno 710, 720 — upper-mid range (Snapdragon 7s Gen 2 / 7 Gen 3).
     * Can hold 0.85x at 60 fps for most DX11 titles.
     */
    private fun isMidRangeAdreno(hw: HardwareProfile): Boolean {
        return hw.gpuVendor == GpuVendor.ADRENO &&
            hw.gpuModel.contains(Regex("Adreno.*(7[1-2][0-9])"))
    }

    private fun buildRecommendationNotes(
        hw: HardwareProfile,
        proton: ProtonVersion,
        tier: ProtonTier,
        engine: EngineHint,
        dx: DxVersion,
    ): String = buildString {
        append("Recommended for ${hw.cpuSoC} with ${hw.gpuModel}. ")
        append("${proton.displayName} selected")
        if (engine != EngineHint.UNKNOWN) {
            append(" for ${engine.name} engine")
        } else {
            append(" based on ProtonDB ${tier.name} rating")
        }
        val renderer = when (dx) {
            DxVersion.DX12 -> "VKD3D"
            else           -> "DXVK"
        }
        append(". $dx via $renderer. ")
        append("Resolution scale and memory budget optimized for ${hw.totalRamMb} MB RAM.")
    }
}
