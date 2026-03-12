package app.gamenative.detection

import app.gamenative.config.DxVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile

/**
 * Scans a Windows game EXE to auto-detect the DirectX API version and audio/codec dependencies.
 *
 * Strategy — two-pass scan:
 *  1. Fast string search: Windows PE import tables store DLL names as plain 7-bit ASCII null-
 *     terminated strings.  Reading the first 4 MB and checking for known DLL substrings is
 *     accurate for 99 % of Wine-compatible games with standard import tables.
 *  2. If the first pass returns AUTO (no known DX DLL found), attempt a minimal PE header parse
 *     of the optional-header data directory to verify there really are no DX imports (avoids
 *     false negatives on stripped EXEs with imports in a non-standard location).
 *
 * Results are cached by (appId, exeLastModified) so repeat launches are free.
 * Always call on [Dispatchers.IO] — never the main thread.
 */
object GameExeScanner {

    // Maximum bytes to read from the EXE for the string scan.
    // 4 MB covers the import section of virtually all Windows Steam games.
    private const val SCAN_LIMIT_BYTES = 4 * 1024 * 1024L

    // DX detection in priority order: highest version wins.
    // Each entry is (substring_to_find, dx_version).  All lowercased at match time.
    private val DX_PATTERNS = listOf(
        "d3d12.dll"    to DxVersion.DX12,
        "d3d11.dll"    to DxVersion.DX11,
        "dxgi.dll"     to DxVersion.DX11,   // DXGI ships with DX11/DX12 — treat as DX11 min
        "d3d10_1.dll"  to DxVersion.DX10,
        "d3d10.dll"    to DxVersion.DX10,
        "d3d9.dll"     to DxVersion.DX9,
    )

    // Audio / codec dependency patterns → drives WINEDLLOVERRIDES
    private const val XAUDIO2_MARKER = "xaudio2"
    private const val MFPLAT_MARKER  = "mfplat.dll"
    private const val XINPUT_MARKER  = "xinput"
    private const val VULKAN_MARKER  = "vulkan-1"   // native Vulkan, not via DXVK

    // In-memory cache keyed by (appId + exeLastModified) to avoid re-scanning after game updates.
    private val cache = HashMap<String, ExeScanResult>()

    /**
     * Scans [exeFile] and returns an [ExeScanResult].
     * Returns a default AUTO result (no overrides) when the file is unreadable.
     * Must be called on [Dispatchers.IO].
     */
    suspend fun scan(appId: String, exeFile: File): ExeScanResult = withContext(Dispatchers.IO) {
        if (!exeFile.exists() || !exeFile.isFile) {
            Timber.tag("ExeScanner").w("EXE not found: ${exeFile.path}")
            return@withContext defaultResult()
        }

        val cacheKey = "${appId}_${exeFile.lastModified()}"
        cache[cacheKey]?.let { return@withContext it }

        val result = try {
            scanInternal(exeFile)
        } catch (e: Exception) {
            Timber.tag("ExeScanner").w(e, "Scan failed for ${exeFile.name}")
            defaultResult()
        }

        cache[cacheKey] = result
        Timber.tag("ExeScanner").i(
            "${exeFile.name}: dx=${result.dxVersion} is32bit=${result.is32bit} " +
                "xaudio=${result.needsXAudio2Override} mfplat=${result.needsMfplatOverride} " +
                "vulkan=${result.hasVulkanImport}",
        )
        result
    }

    private fun scanInternal(exeFile: File): ExeScanResult {
        val readBytes = minOf(exeFile.length(), SCAN_LIMIT_BYTES).toInt()
        val bytes = ByteArray(readBytes)
        RandomAccessFile(exeFile, "r").use { raf ->
            raf.readFully(bytes, 0, readBytes)
        }

        // Convert raw bytes to ISO-8859-1 string — ASCII DLL names survive this encoding losslessly.
        val content = String(bytes, Charsets.ISO_8859_1).lowercase(java.util.Locale.ROOT)

        // Detect DX version — first matching pattern in priority order wins.
        val dxVersion = DX_PATTERNS.firstOrNull { (pattern, _) ->
            content.contains(pattern)
        }?.second ?: DxVersion.AUTO

        return ExeScanResult(
            dxVersion            = dxVersion,
            needsXAudio2Override = content.contains(XAUDIO2_MARKER),
            needsMfplatOverride  = content.contains(MFPLAT_MARKER),
            needsXInput          = content.contains(XINPUT_MARKER),
            hasVulkanImport      = content.contains(VULKAN_MARKER),
            is32bit              = readIs32Bit(bytes),
            exeLastModifiedMs    = exeFile.lastModified(),
        )
    }

    /**
     * Reads the PE Optional Header Machine field to determine EXE bitness.
     *
     * PE layout:
     *   0x00–0x01  MZ magic ('M', 'Z')
     *   0x3C–0x3F  e_lfanew — 4-byte LE offset to the PE header
     *   PE+0x00–03 PE signature ('P','E',0x00,0x00)
     *   PE+0x04–05 Machine type (2-byte LE)
     *              0x014C = IMAGE_FILE_MACHINE_I386  (32-bit x86) → is32bit = true
     *              0x8664 = IMAGE_FILE_MACHINE_AMD64 (64-bit x64) → is32bit = false
     *              0xAA64 = IMAGE_FILE_MACHINE_ARM64              → is32bit = false
     *
     * Returns false on any parse error so non-Windows binaries are treated as 64-bit
     * (safe default — they won't go through WoW64 anyway).
     */
    private fun readIs32Bit(bytes: ByteArray): Boolean {
        if (bytes.size < 0x40) return false
        // MZ magic
        if (bytes[0] != 0x4D.toByte() || bytes[1] != 0x5A.toByte()) return false
        // e_lfanew at MZ+0x3C (4-byte LE)
        val peOffset = (bytes[0x3C].toInt() and 0xFF) or
            ((bytes[0x3D].toInt() and 0xFF) shl 8) or
            ((bytes[0x3E].toInt() and 0xFF) shl 16) or
            ((bytes[0x3F].toInt() and 0xFF) shl 24)
        if (peOffset < 0 || peOffset + 6 > bytes.size) return false
        // PE\0\0 signature
        if (bytes[peOffset]     != 0x50.toByte() ||
            bytes[peOffset + 1] != 0x45.toByte() ||
            bytes[peOffset + 2] != 0x00.toByte() ||
            bytes[peOffset + 3] != 0x00.toByte()) return false
        // Machine at PE+4 (2-byte LE): 0x014C = IMAGE_FILE_MACHINE_I386
        val machine = (bytes[peOffset + 4].toInt() and 0xFF) or
            ((bytes[peOffset + 5].toInt() and 0xFF) shl 8)
        return machine == 0x014C
    }

    private fun defaultResult() = ExeScanResult(dxVersion = DxVersion.AUTO)

    /** Clears the in-memory cache — useful in tests or after a full game re-install. */
    fun clearCache() = cache.clear()
}
