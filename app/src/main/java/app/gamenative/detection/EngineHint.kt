package app.gamenative.detection

import app.gamenative.config.ProtonVersion
import kotlinx.serialization.Serializable

/**
 * Game engine detected from the install directory's file fingerprints.
 * Used by [GameConfigRecommender] to pick the optimal Proton version and Wine overrides
 * when no [app.gamenative.config.KnownGameFixes] entry exists.
 */
@Serializable
enum class EngineHint(
    /**
     * Preferred Proton version for this engine on Adreno.
     * Null = defer to ProtonDB tier selection in recommender.
     */
    val preferredProton: ProtonVersion? = null,
    /**
     * Default DirectX version for this engine when [GameExeScanner] scan inconclusive.
     * Null = defer to DxVersion.AUTO.
     */
    val defaultDxVersion: app.gamenative.config.DxVersion? = null,
) {
    /** Unity — IL2CPP or Mono, typically DX11 on Wine, excellent Proton 10 compat. */
    UNITY(
        preferredProton = ProtonVersion.PROTON_10_0,
        defaultDxVersion = app.gamenative.config.DxVersion.DX11,
    ),
    /** Unreal Engine 4 — DX11 default, shader compiler regression in Proton 10.0 on Adreno. */
    UNREAL_4(
        preferredProton = ProtonVersion.PROTON_9_0,
        defaultDxVersion = app.gamenative.config.DxVersion.DX11,
    ),
    /** Unreal Engine 5 — DX12 default, needs Proton 10.0 for SM6 shader model support. */
    UNREAL_5(
        preferredProton = ProtonVersion.PROTON_10_0,
        defaultDxVersion = app.gamenative.config.DxVersion.DX12,
    ),
    /** Capcom RE Engine — DX12, PSO-heavy, more stable on Proton 9.0 pipeline path. */
    RE_ENGINE(
        preferredProton = ProtonVersion.PROTON_9_0,
        defaultDxVersion = app.gamenative.config.DxVersion.DX12,
    ),
    /** FNA (XNA-compatible) — DX11 via DXVK, .NET runtime, extremely good compat. */
    FNA(
        preferredProton = ProtonVersion.PROTON_9_0,
        defaultDxVersion = app.gamenative.config.DxVersion.DX11,
    ),
    /** Mono / generic .NET — Unity Mono variant or MonoGame. */
    MONO_NET(
        preferredProton = ProtonVersion.PROTON_9_0,
        defaultDxVersion = app.gamenative.config.DxVersion.DX11,
    ),
    /** id Tech 6/7 — Vulkan/DX12, needs Proton 9.0 for stable VKD3D pipeline. */
    ID_TECH(
        preferredProton = ProtonVersion.PROTON_9_0,
        defaultDxVersion = app.gamenative.config.DxVersion.DX12,
    ),
    /** Valve Source engine — DX9, excellent Wine compat. */
    SOURCE(
        preferredProton = ProtonVersion.PROTON_10_0,
        defaultDxVersion = app.gamenative.config.DxVersion.DX9,
    ),
    /** Valve Source 2 engine — Vulkan-native, works best on Proton 10.0. */
    SOURCE_2(
        preferredProton = ProtonVersion.PROTON_10_0,
        defaultDxVersion = app.gamenative.config.DxVersion.DX11,
    ),
    /** Godot engine — OpenGL/Vulkan, typically needs Proton 9.0+. */
    GODOT(
        preferredProton = ProtonVersion.PROTON_9_0,
        defaultDxVersion = app.gamenative.config.DxVersion.DX11,
    ),
    /** Rockstar RAGE engine — DX11 more stable than Vulkan native on Adreno. */
    RAGE(
        preferredProton = ProtonVersion.PROTON_8_0,
        defaultDxVersion = app.gamenative.config.DxVersion.DX11,
    ),
    /** Unknown engine — fall back to full recommender logic. */
    UNKNOWN(preferredProton = null, defaultDxVersion = null),
}
