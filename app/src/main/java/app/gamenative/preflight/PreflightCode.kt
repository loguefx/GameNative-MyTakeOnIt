package app.gamenative.preflight

/**
 * Deterministic failure codes for preflight and supervisor.
 * Enables UI to show exact reason and logs to be categorized.
 */
enum class PreflightCode {
    CPU_NOT_ARM64,
    INSUFFICIENT_RAM,
    INSUFFICIENT_STORAGE,
    VULKAN_INIT_FAILED,
    VULKAN_EXTENSIONS_MISSING,
    DX12_VKD3D_NOT_PRESENT,
    DX12_DRIVER_BLACKLISTED,
    GAME_BINARY_MISSING,
    WINE_PREFIX_INVALID,
    /** Crash cause 6 — Proton/Wine runtime path missing or invalid */
    PROTON_PATH_NOT_FOUND,
    UNKNOWN,
}
