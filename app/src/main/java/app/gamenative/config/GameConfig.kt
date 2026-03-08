package app.gamenative.config

import app.gamenative.profile.WineRuntime
import kotlinx.serialization.Serializable

/**
 * Full configuration for one game on one device. Built by GameConfigRecommender
 * and applied at launch via LaunchOrchestrator.applyGameConfig.
 */
@Serializable
data class GameConfig(
    val appId: Int,
    val configVersion: Int = 1,

    val protonVersion: ProtonVersion,
    val wineRuntime: WineRuntime,

    val dxVersion: DxVersion,
    val dxvkEnabled: Boolean,
    val vkd3dEnabled: Boolean,
    val d8vkEnabled: Boolean,

    val resolutionScale: Float,
    val fpsLimit: Int,
    val vsync: Boolean,

    val dxvkAsync: Boolean,
    val dxvkNumCompilerThreads: Int,
    val dxvkDeviceLocalMemoryMiB: Int,
    val dxvkPresentMode: String,

    val vkd3dWorkerThreads: Int,
    val vkd3dShaderModel: String,

    val esyncEnabled: Boolean,
    val fsyncEnabled: Boolean,
    val wineDebug: Boolean,

    val box64BigBlock: Boolean,
    val box64StrongMem: Boolean,
    val box64DynarecFastRound: Boolean,

    val cpuAffinityMask: String,

    val extraEnvVars: Map<String, String>,
    val wineLaunchArgs: String,

    val preset: ConfigPreset,
    val notes: String,
)

@Serializable
enum class ProtonVersion(
    val displayName: String,
) {
    PROTON_10_0("Proton 10.0"),
    PROTON_9_0("Proton 9.0"),
    PROTON_8_0("Proton 8.0"),
    PROTON_7_0("Proton 7.0"),
    WINE_GE_LATEST("Wine-GE"),
    STOCK_WINE("Stock Wine"),
}

@Serializable
enum class DxVersion {
    AUTO,
    DX9,
    DX10,
    DX11,
    DX12,
}

@Serializable
enum class ConfigPreset {
    RECOMMENDED,
    PERFORMANCE,
    QUALITY,
    COMPATIBILITY,
    CUSTOM,
}
