package app.gamenative.config

import app.gamenative.hardware.HardwareProfile

/**
 * Applies a preset to a base config. Returns a new GameConfig with all fields updated
 * so the UI can show the full result (preset chip + every visible field).
 */
object ConfigPresets {

    fun apply(base: GameConfig, preset: ConfigPreset, hardware: HardwareProfile): GameConfig {
        return when (preset) {
            ConfigPreset.PERFORMANCE -> base.copy(
                resolutionScale = (base.resolutionScale - 0.15f).coerceAtLeast(0.5f),
                fpsLimit = hardware.displayRefreshHz.coerceAtMost(60),
                dxvkAsync = true,
                vsync = false,
                preset = ConfigPreset.PERFORMANCE,
                notes = "Lower resolution scale for higher FPS. Best for fast-paced games.",
            )
            ConfigPreset.QUALITY -> base.copy(
                resolutionScale = 1.0f,
                fpsLimit = 30,
                vsync = true,
                preset = ConfigPreset.QUALITY,
                notes = "Native resolution with frame cap for visual quality. Best for story games.",
            )
            ConfigPreset.COMPATIBILITY -> base.copy(
                protonVersion = ProtonVersion.PROTON_8_0,
                dxVersion = DxVersion.DX11,
                vkd3dEnabled = false,
                dxvkEnabled = true,
                resolutionScale = 0.75f,
                fpsLimit = 30,
                esyncEnabled = true,
                fsyncEnabled = false,
                box64BigBlock = false,
                box64StrongMem = true,
                preset = ConfigPreset.COMPATIBILITY,
                notes = "Maximum compatibility mode. Use if game crashes with other presets. Forces DX11 and Proton 8.0.",
            )
            else -> base // RECOMMENDED and CUSTOM return unchanged
        }
    }
}
