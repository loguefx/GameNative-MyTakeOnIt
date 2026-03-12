package app.gamenative.detection

import app.gamenative.config.DxVersion
import kotlinx.serialization.Serializable

/**
 * Result of scanning a Windows game EXE for graphics API and audio dependencies.
 * Produced by [GameExeScanner] and cached per-game to avoid re-scanning on every launch.
 */
@Serializable
data class ExeScanResult(
    /** The highest DirectX API version detected from import table DLL strings. */
    val dxVersion: DxVersion,
    /** True if the EXE imports any XAudio2 DLL — requires WINEDLLOVERRIDES=xaudio2_*=n,b */
    val needsXAudio2Override: Boolean = false,
    /**
     * True if the EXE imports mfplat.dll (Windows Media Foundation).
     * Without native mfplat, intro videos and FMV sequences hang Wine.
     * Requires WINEDLLOVERRIDES=mfplat.dll=n,b
     */
    val needsMfplatOverride: Boolean = false,
    /** True if the EXE imports xinput*.dll — controller support via XInput. */
    val needsXInput: Boolean = false,
    /** True if a native Vulkan import was detected (vulkan-1.dll / vulkan-1-999.dll). */
    val hasVulkanImport: Boolean = false,
    /**
     * True when the EXE PE header declares IMAGE_FILE_MACHINE_I386 (0x014C).
     *
     * 32-bit games run under Wine's "experimental wow64 mode" via the FEX WoW64 bridge
     * (libwow64fex.dll) on ARM64EC.  FEX has a known bug where vkCreateXlibSurfaceKHR
     * receives a misaligned Display* pointer, creating a dead Vulkan surface — the game
     * renders audio normally but produces a permanent black screen.
     *
     * The recommender uses this flag to automatically route DX9 rendering through
     * wined3d (OpenGL ES via GLRenderer) instead of DXVK (Vulkan) for every 32-bit
     * DX9 game, without requiring a KnownGameFix entry.
     */
    val is32bit: Boolean = false,
    /** The exe file's last-modified timestamp at scan time — used to invalidate the cache. */
    val exeLastModifiedMs: Long = 0L,
)
