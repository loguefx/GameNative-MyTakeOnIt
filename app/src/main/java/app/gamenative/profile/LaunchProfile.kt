package app.gamenative.profile

import kotlinx.serialization.Serializable

/**
 * Deterministic launch profile: same profile + same device → same behavior.
 * Profiles are versioned and immutable for "Verified"; "Custom" copies are editable but unsupported.
 */
@Serializable
data class LaunchProfile(
    val id: String,
    val version: String,
    val gameAppId: String? = null,
    val isVerified: Boolean = false,

    val box64Version: String = "0.3.6",
    val wineVersion: String = "proton-9.0-arm64ec",
    val dxvkVersion: String = "2.6.1-gplasync",
    val vkd3dVersion: String = "2.14.1",
    val vkd3dEnabled: Boolean = true,

    val graphicsBackend: GraphicsBackend = GraphicsBackend.DX11,
    val vulkanDriverPath: VulkanDriverPath = VulkanDriverPath.SYSTEM,
    val esync: Boolean = true,
    val fsync: Boolean = false,
    val audioBackend: String = "pulseaudio",
    val resolution: String = "1280x720",
    val fpsCap: Int = 60,
    val overlayAllowed: Boolean = false,
    val env: Map<String, String> = emptyMap(),
    val launchArgs: List<String> = emptyList(),
) {
    val isCustom: Boolean get() = !isVerified

    @Serializable
    enum class GraphicsBackend { DX11, DX12 }

    @Serializable
    enum class VulkanDriverPath { SYSTEM, BUNDLED }
}
