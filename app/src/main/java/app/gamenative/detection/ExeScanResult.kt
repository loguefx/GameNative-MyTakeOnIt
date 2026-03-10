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
    /** The exe file's last-modified timestamp at scan time — used to invalidate the cache. */
    val exeLastModifiedMs: Long = 0L,
)
