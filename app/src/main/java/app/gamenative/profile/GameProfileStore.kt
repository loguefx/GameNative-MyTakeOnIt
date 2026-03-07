package app.gamenative.profile

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import timber.log.Timber

/**
 * Per-game profile overrides. Stored as JSON at keyvalues/profiles/{appId}.json
 * under app-private filesDir (not SharedPreferences; no write-ahead locking issues when container runs).
 */
object GameProfileStore {

    private const val KEYVALUES_PROFILES = "keyvalues/profiles"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun profilesDir(context: Context): File {
        return File(context.filesDir, KEYVALUES_PROFILES).apply { mkdirs() }
    }

    fun getProfileFile(context: Context, appId: String): File {
        return File(profilesDir(context), "$appId.json")
    }

    fun load(context: Context, appId: String): GameProfileOverrides? {
        return try {
            val file = getProfileFile(context, appId)
            if (!file.exists()) return null
            json.decodeFromString<GameProfileOverrides>(file.readText())
        } catch (e: Exception) {
            Timber.w(e, "Failed to load game profile for $appId")
            null
        }
    }

    fun save(context: Context, appId: String, overrides: GameProfileOverrides): Boolean {
        return try {
            val file = getProfileFile(context, appId)
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(overrides))
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to save game profile for $appId")
            false
        }
    }

    fun clear(context: Context, appId: String): Boolean {
        return try {
            getProfileFile(context, appId).delete()
        } catch (e: Exception) {
            Timber.w(e, "Failed to clear game profile for $appId")
            false
        }
    }
}
