package app.gamenative.profile

import kotlinx.serialization.Serializable

/** Per-game Wine runtime: stock (app build) or Wine-GE (community build with game patches). */
@Serializable
enum class WineRuntime {
    STOCK,
    WINE_GE,
}

/**
 * Per-game overrides stored at keyvalues/profiles/{appId}.json.
 * Null means use global/default; these override base config when set.
 * Env overrides (frameCap, asyncShaders, vsync) are applied in LaunchOrchestrator.applyIsolationEnv() last.
 * Container overrides (resolutionScale, dxVersionOverride) are applied when building/updating container data.
 */
@Serializable
data class GameProfileOverrides(
    val frameCap: Int? = null,           // 0 = uncapped, or 30/45/60/90/120
    val resolutionScale: Float? = null,   // 0.5f .. 1.25f
    val asyncShaders: Boolean? = null,
    val vsync: Boolean? = null,
    val dxVersionOverride: String? = null, // AUTO | DX9 | DX10 | DX11 | DX12; AUTO = no override
    val wineRuntime: WineRuntime = WineRuntime.STOCK,
    val compatibilityLayerVersionId: String? = null, // selected Wine/Proton version id for this game
) {
    companion object {
        const val DX_AUTO = "AUTO"
        const val DX_9 = "DX9"
        const val DX_10 = "DX10"
        const val DX_11 = "DX11"
        const val DX_12 = "DX12"
    }
}
