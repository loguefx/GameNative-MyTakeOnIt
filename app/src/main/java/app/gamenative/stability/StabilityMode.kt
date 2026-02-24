package app.gamenative.stability

import app.gamenative.profile.LaunchProfile

/**
 * Global stability modes (non-adaptive). Apply to all games; no per-user guessing.
 */
enum class StabilityMode(
    val label: String,
    val resolution: String,
    val fpsCap: Int,
    val graphicsBackend: LaunchProfile.GraphicsBackend,
    val overlayAllowed: Boolean,
    val description: String,
) {
    STABLE(
        label = "Stable",
        resolution = "1280x720",
        fpsCap = 60,
        graphicsBackend = LaunchProfile.GraphicsBackend.DX11,
        overlayAllowed = false,
        description = "720p, DX11 only, overlays off, frame cap 60. Default for reliability.",
    ),
    PERFORMANCE(
        label = "Performance",
        resolution = "1920x1080",
        fpsCap = 60,
        graphicsBackend = LaunchProfile.GraphicsBackend.DX11,
        overlayAllowed = false,
        description = "Higher res, DX11, still deterministic.",
    ),
    EXPERIMENTAL(
        label = "Experimental",
        resolution = "1280x720",
        fpsCap = 60,
        graphicsBackend = LaunchProfile.GraphicsBackend.DX12,
        overlayAllowed = false,
        description = "DX12 allowed. Unsupported; may crash on many devices.",
    ),
    ;

    fun applyTo(profile: LaunchProfile): LaunchProfile {
        return profile.copy(
            resolution = resolution,
            fpsCap = fpsCap,
            graphicsBackend = graphicsBackend,
            overlayAllowed = overlayAllowed,
        )
    }
}
