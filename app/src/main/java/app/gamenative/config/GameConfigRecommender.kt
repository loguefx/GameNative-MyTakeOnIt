package app.gamenative.config

import app.gamenative.hardware.GpuVendor
import app.gamenative.hardware.HardwareProfile
import app.gamenative.profile.WineRuntime
import app.gamenative.proton.ProtonTier

/**
 * Recommends a full GameConfig from hardware + ProtonDB tier + KnownGameFixes.
 * Known fixes override everything; otherwise hardware-based defaults apply.
 */
object GameConfigRecommender {

    fun recommend(
        appId: Int,
        gameName: String,
        hardware: HardwareProfile,
        protonTier: ProtonTier,
    ): GameConfig {
        val knownFix = KnownGameFixes.get(appId)

        val protonVersion = knownFix?.protonVersion
            ?: when (protonTier) {
                ProtonTier.PLATINUM -> ProtonVersion.PROTON_10_0
                ProtonTier.GOLD -> ProtonVersion.PROTON_9_0
                ProtonTier.SILVER -> ProtonVersion.PROTON_8_0
                ProtonTier.BRONZE -> ProtonVersion.PROTON_8_0
                ProtonTier.BORKED -> ProtonVersion.WINE_GE_LATEST
                else -> ProtonVersion.PROTON_9_0
            }

        val dxVersion = knownFix?.dxVersion ?: DxVersion.AUTO

        val resolutionScale = knownFix?.resolutionScale
            ?: when {
                hardware.totalRamMb >= 12000 && isHighEndAdreno(hardware) -> 1.0f
                hardware.totalRamMb >= 8000 && isHighEndAdreno(hardware) -> 1.0f
                hardware.totalRamMb >= 8000 -> 0.85f
                hardware.totalRamMb >= 6000 -> 0.75f
                else -> 0.65f
            }

        val fpsLimit = knownFix?.fpsLimit
            ?: when {
                hardware.thermalHeadroom < 0.3f -> 30
                hardware.displayRefreshHz >= 120 -> 60
                else -> 60
            }

        val dxvkMemMiB = (hardware.totalRamMb * 0.40).toInt().coerceIn(1024, 6144)
        val compilerThreads = (hardware.cpuPCoreCount - 1).coerceAtLeast(2)
        val shaderModel = when {
            hardware.gpuVendor == GpuVendor.ADRENO && hardware.supportsVulkan13 -> "6_5"
            hardware.gpuVendor == GpuVendor.ADRENO -> "6_3"
            else -> "6_3"
        }

        val wineRuntime = if (protonTier == ProtonTier.BORKED) WineRuntime.WINE_GE else WineRuntime.STOCK

        return GameConfig(
            appId = appId,
            protonVersion = protonVersion,
            wineRuntime = wineRuntime,
            dxVersion = dxVersion,
            dxvkEnabled = dxVersion != DxVersion.DX12,
            vkd3dEnabled = dxVersion == DxVersion.DX12,
            d8vkEnabled = knownFix?.requiresD8VK ?: false,
            resolutionScale = resolutionScale,
            fpsLimit = fpsLimit,
            vsync = false,
            dxvkAsync = true,
            dxvkNumCompilerThreads = compilerThreads,
            dxvkDeviceLocalMemoryMiB = dxvkMemMiB,
            dxvkPresentMode = "MAILBOX",
            vkd3dWorkerThreads = (hardware.cpuPCoreCount / 2).coerceAtLeast(2),
            vkd3dShaderModel = shaderModel,
            esyncEnabled = true,
            fsyncEnabled = true,
            wineDebug = false,
            box64BigBlock = true,
            box64StrongMem = true,
            box64DynarecFastRound = true,
            cpuAffinityMask = hardware.cpuPCoreMask,
            extraEnvVars = knownFix?.extraEnvVars ?: emptyMap(),
            wineLaunchArgs = knownFix?.launchArgs ?: "",
            preset = ConfigPreset.RECOMMENDED,
            notes = buildRecommendationNotes(hardware, protonVersion, protonTier),
        )
    }

    /** Used for resolution scaling; exposed for tests. Regex: 730–799 (not 640–669). */
    internal fun isHighEndAdreno(hw: HardwareProfile): Boolean {
        return hw.gpuModel.contains(Regex("Adreno.*(7[3-9][0-9])"))
    }

    private fun buildRecommendationNotes(
        hw: HardwareProfile,
        proton: ProtonVersion,
        tier: ProtonTier,
    ): String = buildString {
        append("Recommended for ${hw.cpuSoC} with ${hw.gpuModel}. ")
        append("${proton.displayName} selected based on ProtonDB ${tier.name} rating. ")
        append("Resolution scale and memory budget optimized for ${hw.totalRamMb}MB RAM.")
    }
}
