package app.gamenative.hardware

import android.content.Context
import app.gamenative.BuildConfig
import app.gamenative.PrefManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Cached HardwareProfile in DataStore. Re-detect when:
 * - App version changes (first run after update), or
 * - Graphics driver changes (Turnip/Mesa installed or switched in Component settings),
 * so that gpuVulkanVersion and gpuDriverVersion stay accurate for the recommender.
 */
object HardwareProfileCache {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Returns the current hardware profile, from cache if valid else by running detection on IO.
     * Call from a coroutine; detection runs on Dispatchers.IO.
     * On the launch path the cache is expected to be warm (version + driver key match): only
     * first run or after app/driver change triggers slow detection; normal game launch gets
     * cached profile quickly (decode + pref read, no EGL/sysfs).
     */
    suspend fun getProfile(context: Context): HardwareProfile = withContext(Dispatchers.IO) {
        PrefManager.init(context)
        val currentVersion = buildCacheVersion(context)
        val storedVersion = PrefManager.hardwareProfileCacheVersion
        val storedJson = PrefManager.hardwareProfileJson
        if (storedVersion == currentVersion && storedJson.isNotEmpty()) {
            try {
                json.decodeFromString<HardwareProfile>(storedJson)
            } catch (e: Exception) {
                Timber.w(e, "Hardware profile cache invalid, re-detecting")
                detectAndSave(context, currentVersion)
            }
        } else {
            detectAndSave(context, currentVersion)
        }
    }

    /**
     * Invalidates the cache so the next getProfile() will re-detect. Optional: cache is
     * already invalidated implicitly when app version or PrefManager.graphicsDriver/
     * graphicsDriverVersion changes. Call this after installing or switching Turnip/Mesa
     * in Component settings if the UI does not update those prefs (e.g. component install
     * before selection is saved).
     */
    fun invalidate(context: Context) {
        PrefManager.init(context)
        PrefManager.hardwareProfileCacheVersion = ""
        PrefManager.hardwareProfileJson = ""
    }

    private fun buildCacheVersion(context: Context): String {
        PrefManager.init(context)
        val driverKey = "${PrefManager.graphicsDriver}:${PrefManager.graphicsDriverVersion}"
        return "${BuildConfig.VERSION_CODE}:$driverKey"
    }

    private fun detectAndSave(context: Context, cacheVersion: String): HardwareProfile {
        val profile = HardwareDetector.detect(context)
        val encoded = json.encodeToString(profile)
        PrefManager.hardwareProfileCacheVersion = cacheVersion
        PrefManager.hardwareProfileJson = encoded
        return profile
    }
}
