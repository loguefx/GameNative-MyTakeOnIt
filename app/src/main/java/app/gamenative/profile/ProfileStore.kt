package app.gamenative.profile

import android.content.Context
import app.gamenative.isolation.GamePaths
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import timber.log.Timber

/**
 * Stores and loads deterministic profiles.
 * Verified profiles are read-only; Custom profiles are user-editable copies.
 */
object ProfileStore {

    private const val PROFILES_DIR = "profiles"
    private const val FILE_SUFFIX = ".json"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun profilesDir(context: Context): File {
        return File(context.filesDir, PROFILES_DIR).apply { mkdirs() }
    }

    fun getProfileFile(context: Context, profileId: String): File {
        return File(profilesDir(context), "$profileId$FILE_SUFFIX")
    }

    fun load(context: Context, profileId: String): LaunchProfile? {
        return try {
            val file = getProfileFile(context, profileId)
            if (!file.exists()) return null
            json.decodeFromString<LaunchProfile>(file.readText())
        } catch (e: Exception) {
            Timber.w(e, "Failed to load profile $profileId")
            null
        }
    }

    fun save(context: Context, profile: LaunchProfile): Boolean {
        if (profile.isVerified) {
            Timber.w("Cannot overwrite Verified profile ${profile.id}; create a Custom copy to edit.")
            return false
        }
        return try {
            val file = getProfileFile(context, profile.id)
            file.writeText(json.encodeToString(profile))
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to save profile ${profile.id}")
            false
        }
    }

    fun listProfileIds(context: Context): List<String> {
        return profilesDir(context).listFiles()?.mapNotNull { f ->
            if (f.isFile && f.name.endsWith(FILE_SUFFIX)) {
                f.name.removeSuffix(FILE_SUFFIX)
            } else null
        } ?: emptyList()
    }

    /**
     * Creates a Custom (editable) copy of a profile with a new id.
     */
    fun copyAsCustom(context: Context, source: LaunchProfile, newId: String): LaunchProfile {
        val custom = source.copy(
            id = newId,
            version = source.version + "-custom",
            isVerified = false,
        )
        save(context, custom)
        return custom
    }

    /**
     * Resolves the effective profile for a game: use game-specific profile if present,
     * else default profile, else built-in default.
     */
    fun resolveForGame(context: Context, appId: String, defaultProfileId: String?): LaunchProfile {
        val preferred = defaultProfileId ?: "default"
        load(context, preferred)?.let { return it }
        load(context, "default")?.let { return it }
        return defaultProfile(appId)
    }

    fun defaultProfile(gameAppId: String? = null): LaunchProfile {
        return LaunchProfile(
            id = "default",
            version = "1",
            gameAppId = gameAppId,
            isVerified = false,
            graphicsBackend = LaunchProfile.GraphicsBackend.DX11,
            overlayAllowed = false,
            fpsCap = 60,
            resolution = "1280x720",
        )
    }
}
