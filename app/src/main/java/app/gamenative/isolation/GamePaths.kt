package app.gamenative.isolation

import android.content.Context
import java.io.File

/**
 * Deterministic per-game paths for Wine prefix and DXVK/VKD3D caches.
 * Rule: one game = one prefix + one DXVK cache + one VKD3D cache.
 * Key by Steam AppID (or stable id for non-Steam games).
 */
object GamePaths {

    private const val DIR_PREFIXES = "prefixes"
    private const val DIR_DXVK_CACHE = "dxvk_cache"
    private const val DIR_DXVK_CONFIG = "dxvk_config"
    private const val DIR_VKD3D_CACHE = "vkd3d_cache"
    private const val DIR_MESA_CACHE = "mesa_cache"
    private const val DIR_WINE_GE = "wine_ge"

    /**
     * Root directory for all isolation data (prefixes and caches).
     * Uses app-private storage so paths are stable and not shared across games.
     */
    fun getIsolationRoot(context: Context): File {
        return context.getDir("gamenative_isolation", Context.MODE_PRIVATE)
    }

    /**
     * Wine prefix directory for the given game.
     * Path: isolationRoot/prefixes/<appId>/
     * For compatibility with existing containers, the actual prefix in use may be
     * the container's rootDir; this path is the canonical isolation path and can be
     * used for preflight storage checks and when creating new containers.
     */
    fun getPrefixDir(context: Context, appId: String): File {
        return File(getIsolationRoot(context), "$DIR_PREFIXES/$appId")
    }

    /**
     * DXVK state/cache directory for the given game.
     * Path: isolationRoot/dxvk_cache/<appId>/
     * Set DXVK_STATE_CACHE_PATH to this path when launching.
     */
    fun getDxvkCacheDir(context: Context, appId: String): File {
        return File(getIsolationRoot(context), "$DIR_DXVK_CACHE/$appId")
    }

    /**
     * VKD3D cache directory for the given game (DX12).
     * Path: isolationRoot/vkd3d_cache/<appId>/
     */
    fun getVkd3dCacheDir(context: Context, appId: String): File {
        return File(getIsolationRoot(context), "$DIR_VKD3D_CACHE/$appId")
    }

    /**
     * Shader cache path for the game (can be derived from cache dir or prefix).
     * Default: under dxvk_cache/<appId>/shader_cache or similar.
     */
    fun getShaderCacheDir(context: Context, appId: String): File {
        return File(getDxvkCacheDir(context, appId), "shader_cache")
    }

    /**
     * Mesa shader cache directory for the given game.
     * Path: isolationRoot/mesa_cache/<appId>/
     * Set MESA_SHADER_CACHE_DIR to this path when launching.
     */
    fun getMesaCacheDir(context: Context, appId: String): File {
        return File(getIsolationRoot(context), "$DIR_MESA_CACHE/$appId")
    }

    /**
     * Ensures all per-game directories exist (prefix, dxvk_cache, vkd3d_cache, mesa_cache).
     * Call before launch so env vars point to existing dirs.
     */
    fun ensureGameDirs(context: Context, appId: String) {
        getPrefixDir(context, appId).mkdirs()
        getDxvkCacheDir(context, appId).mkdirs()
        getVkd3dCacheDir(context, appId).mkdirs()
        getShaderCacheDir(context, appId).mkdirs()
        getMesaCacheDir(context, appId).mkdirs()
    }

    /**
     * Directory for the Wine-GE runtime (extracted from GitHub release).
     * When present, use bin/wine, bin/wineserver, lib/wine for WINE/WINESERVER/WINELOADER/WINEDLLPATH.
     */
    fun getWineGEPath(context: Context): String {
        return File(getIsolationRoot(context), DIR_WINE_GE).absolutePath
    }

    /**
     * Per-game DXVK config file path. Set DXVK_CONFIG_FILE to this when launching.
     * DXVK accepts an arbitrary absolute path for DXVK_CONFIG_FILE (doitsujin/dxvk wiki).
     */
    fun getDxvkConfigPath(context: Context, appId: String): String {
        return File(getIsolationRoot(context), "$DIR_DXVK_CONFIG/$appId/dxvk.conf").absolutePath
    }

    /**
     * Compat data / prefix path for Steam compatibility layer (STEAM_COMPAT_DATA_PATH).
     */
    fun getCompatDataPath(context: Context, appId: String): String {
        return getPrefixDir(context, appId).absolutePath
    }

    /**
     * Returns a map of environment variable names to values for per-game isolation.
     * Merge these into the launch environment so DXVK/VKD3D use per-game caches.
     */
    fun getIsolationEnv(context: Context, appId: String): Map<String, String> {
        ensureGameDirs(context, appId)
        return mapOf(
            "DXVK_STATE_CACHE_PATH" to getDxvkCacheDir(context, appId).absolutePath,
            "DXVK_LOG_PATH" to File(getDxvkCacheDir(context, appId), "dxvk.log").absolutePath,
            "VKD3D_SHADER_CACHE_PATH" to getVkd3dCacheDir(context, appId).absolutePath,
            "MESA_SHADER_CACHE_DIR" to getMesaCacheDir(context, appId).absolutePath,
        )
    }
}
