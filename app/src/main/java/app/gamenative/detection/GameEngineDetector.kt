package app.gamenative.detection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Detects the game engine from the install directory's file and folder fingerprints.
 * No EXE parsing — just a fast file-existence check against known engine signatures.
 *
 * Detection result drives Proton version selection and default DX version in
 * [app.gamenative.config.GameConfigRecommender] for games not in [app.gamenative.config.KnownGameFixes].
 *
 * Always call on [Dispatchers.IO] — never the main thread.
 */
object GameEngineDetector {

    /**
     * Ordered list of (relative-path-pattern, engine) checks.
     * The first match wins.  Paths support two wildcards:
     *   * at the START of a filename → file whose name ENDS with the suffix
     *   * in the middle (simple glob) → not supported; use exact relative paths
     */
    private data class Fingerprint(
        val relativePath: String,
        val engine: EngineHint,
        val description: String = "",
    )

    private val FINGERPRINTS = listOf(
        // ── Unreal Engine 5 (check before UE4 — UE5 also ships Shipping.exe) ─────────
        Fingerprint("Engine/Binaries/Win64/UnrealEditor.exe", EngineHint.UNREAL_5, "UE5 Editor binary"),
        Fingerprint("Engine/Binaries/Win64/EpicWebHelper.exe", EngineHint.UNREAL_5, "UE5 EpicWebHelper"),

        // ── Unreal Engine 4 ────────────────────────────────────────────────────────────
        Fingerprint("Engine/Binaries/Win64/UE4Editor.exe", EngineHint.UNREAL_4, "UE4 Editor binary"),
        // Most shipped UE4 games have a *-Win64-Shipping.exe at root — checked via suffix scan

        // ── Capcom RE Engine ───────────────────────────────────────────────────────────
        Fingerprint("re_chunk_000.pak", EngineHint.RE_ENGINE, "RE Engine pak archive"),
        Fingerprint("RE_RSZ.ini", EngineHint.RE_ENGINE, "RE Engine RSZ config"),

        // ── id Tech 6 / 7 ──────────────────────────────────────────────────────────────
        Fingerprint("base/renderprogs2", EngineHint.ID_TECH, "id Tech 7 render programs"),
        Fingerprint("base/renderprogs",  EngineHint.ID_TECH, "id Tech 6 render programs"),

        // ── Valve Source 2 ─────────────────────────────────────────────────────────────
        Fingerprint("game/core/gameinfo.gi", EngineHint.SOURCE_2, "Source 2 gameinfo"),
        Fingerprint("game/dota/gameinfo.gi", EngineHint.SOURCE_2, "Dota 2 Source 2"),

        // ── Valve Source ───────────────────────────────────────────────────────────────
        Fingerprint("hl2.exe",                 EngineHint.SOURCE, "Half-Life 2 / Source exe"),
        Fingerprint("gameinfo.txt",            EngineHint.SOURCE, "Source engine gameinfo"),

        // ── Rockstar RAGE ──────────────────────────────────────────────────────────────
        Fingerprint("GTA5.exe",        EngineHint.RAGE, "RAGE / GTA V main binary"),
        Fingerprint("RDR2.exe",        EngineHint.RAGE, "RAGE / RDR2 main binary"),
        Fingerprint("PlayGTAV.exe",    EngineHint.RAGE, "RAGE / GTA V launcher"),

        // ── Unity (IL2CPP — GameAssembly.dll) ─────────────────────────────────────────
        Fingerprint("GameAssembly.dll",      EngineHint.UNITY, "Unity IL2CPP GameAssembly"),
        // ── Unity (Mono) ───────────────────────────────────────────────────────────────
        Fingerprint("UnityPlayer.dll",        EngineHint.UNITY, "Unity Player DLL"),

        // ── FNA / XNA ──────────────────────────────────────────────────────────────────
        Fingerprint("FNA.dll",                EngineHint.FNA, "FNA framework"),
        Fingerprint("fna.dll",                EngineHint.FNA, "FNA framework (lowercase)"),

        // ── Mono / .NET (after Unity check — Unity also ships mono.dll) ───────────────
        Fingerprint("MonoBleedingEdge/EmbedRuntime", EngineHint.MONO_NET, "Mono bleeding-edge runtime"),
        Fingerprint("mono.dll",               EngineHint.MONO_NET, "Mono runtime DLL"),

        // ── Godot ──────────────────────────────────────────────────────────────────────
        Fingerprint("GodotSharp.dll",         EngineHint.GODOT, "Godot .NET assembly"),
    )

    // In-memory cache keyed by (gameDir.canonicalPath)
    private val cache = HashMap<String, EngineHint>()

    /**
     * Detects the engine for the game installed at [gameDir].
     * Returns [EngineHint.UNKNOWN] when the directory doesn't exist or no fingerprint matches.
     */
    suspend fun detect(gameDir: File): EngineHint = withContext(Dispatchers.IO) {
        if (!gameDir.exists() || !gameDir.isDirectory) return@withContext EngineHint.UNKNOWN

        val key = try { gameDir.canonicalPath } catch (_: Exception) { gameDir.absolutePath }
        cache[key]?.let { return@withContext it }

        val hint = detectInternal(gameDir)
        cache[key] = hint
        Timber.tag("EngineDetect").i("${gameDir.name}: $hint")
        hint
    }

    private fun detectInternal(gameDir: File): EngineHint {
        // Check exact relative paths from FINGERPRINTS list
        for ((relativePath, engine) in FINGERPRINTS) {
            if (File(gameDir, relativePath).exists()) return engine
        }

        // Wildcard pass: look for *-Win64-Shipping.exe at root → UE4 or UE5
        // (no Editor binary = shipped build)
        val rootFiles = gameDir.listFiles() ?: return EngineHint.UNKNOWN
        val hasShippingExe = rootFiles.any { it.name.endsWith("-Win64-Shipping.exe") }
        if (hasShippingExe) {
            // Distinguish UE4 vs UE5 by checking for SM6-related shader bytecode folder
            val hasSm6Shaders = File(gameDir, "Engine/GlobalShaderCache-PCD3D_SM6.bin").exists()
            return if (hasSm6Shaders) EngineHint.UNREAL_5 else EngineHint.UNREAL_4
        }

        // Check for Unity data folder: <GameName>_Data/ at root
        val hasUnityData = rootFiles.any { it.isDirectory && it.name.endsWith("_Data") &&
            File(it, "Managed/UnityEngine.dll").exists() }
        if (hasUnityData) return EngineHint.UNITY

        return EngineHint.UNKNOWN
    }

    /** Clears the in-memory cache. */
    fun clearCache() = cache.clear()
}
