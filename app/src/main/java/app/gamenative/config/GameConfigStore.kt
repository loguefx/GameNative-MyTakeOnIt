package app.gamenative.config

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import timber.log.Timber

/**
 * Persists GameConfig per appId. Load first on launch; if none, recommender output is used
 * and can be saved so the next launch has a saved config. Users who customize keep their config;
 * new users get recommended settings automatically.
 */
object GameConfigStore {

    private const val SUBDIR = "game_config"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    suspend fun load(context: Context, appId: String): GameConfig? = withContext(Dispatchers.IO) {
        try {
            val file = getFile(context, appId)
            if (!file.exists()) return@withContext null
            json.decodeFromString<GameConfig>(file.readText())
        } catch (e: Exception) {
            Timber.w(e, "Failed to load GameConfig for $appId")
            null
        }
    }

    suspend fun save(context: Context, appId: String, config: GameConfig) = withContext(Dispatchers.IO) {
        try {
            val file = getFile(context, appId)
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(config))
        } catch (e: Exception) {
            Timber.e(e, "Failed to save GameConfig for $appId")
        }
    }

    private fun getFile(context: Context, appId: String): File {
        val dir = File(context.filesDir, SUBDIR)
        val safeId = appId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(dir, "$safeId.json")
    }
}
