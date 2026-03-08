package app.gamenative.data.repository

import app.gamenative.BuildConfig
import app.gamenative.data.SteamAchievement
import app.gamenative.db.dao.CachedAchievementDao
import app.gamenative.service.SteamService
import javax.inject.Inject
import javax.inject.Singleton
import app.gamenative.db.entity.CachedAchievement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

private const val SCHEMA_URL = "https://api.steampowered.com/ISteamUserStats/GetSchemaForGame/v2/"
private const val PLAYER_URL = "https://api.steampowered.com/ISteamUserStats/GetPlayerAchievements/v1/"
private const val CACHE_VALID_MS = 60 * 60 * 1000L // 1 hour

@Singleton
class AchievementRepository @Inject constructor(
    private val cachedAchievementDao: CachedAchievementDao,
) {

    fun achievements(appId: Int, steamId: Long): Flow<List<SteamAchievement>> =
        cachedAchievementDao.get(appId, steamId).map { cache ->
            if (cache == null) emptyList() else parseMerged(cache)
        }

    suspend fun refreshIfNeeded(appId: Int, steamId: Long) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = cachedAchievementDao.getOnce(appId, steamId)
        val needRefresh = existing == null || (now - existing.schemaFetchedAt) > CACHE_VALID_MS ||
            (now - existing.playerFetchedAt) > CACHE_VALID_MS
        if (!needRefresh) return@withContext

        // Prefer SteamKit (no API key): when logged in and requesting own stats
        val loggedInSteamId = SteamService.userSteamId?.convertToUInt64()
        if (loggedInSteamId != null && steamId == loggedInSteamId) {
            val callback = SteamService.requestUserStats(appId)
            val pair = callback?.let { SteamService.userStatsCallbackToCacheJson(it) }
            if (pair != null) {
                cachedAchievementDao.insert(
                    CachedAchievement(
                        appId = appId,
                        steamId = steamId,
                        schemaJson = pair.first,
                        playerJson = pair.second,
                        schemaFetchedAt = now,
                        playerFetchedAt = now,
                    ),
                )
                Timber.tag("Achievements").d("Refreshed achievements for appId=$appId via SteamKit")
                return@withContext
            }
        }

        // Fallback: Steam Web API (requires STEAM_WEB_API_KEY)
        if (BuildConfig.STEAM_WEB_API_KEY.isBlank() || BuildConfig.STEAM_WEB_API_KEY == "unset") {
            Timber.tag("Achievements").d("SteamKit path failed or not own profile; STEAM_WEB_API_KEY not set")
            return@withContext
        }
        val key = BuildConfig.STEAM_WEB_API_KEY
        var schemaJson = existing?.schemaJson
        var playerJson = existing?.playerJson
        var schemaFetchedAt = existing?.schemaFetchedAt ?: 0L
        var playerFetchedAt = existing?.playerFetchedAt ?: 0L

        if (existing == null || (now - existing.schemaFetchedAt) > CACHE_VALID_MS) {
            fetchSchema(key, appId)?.let { (json, _) ->
                schemaJson = json
                schemaFetchedAt = now
            }
        }
        if (existing == null || (now - existing.playerFetchedAt) > CACHE_VALID_MS) {
            fetchPlayerAchievements(key, appId, steamId)?.let { (json, _) ->
                playerJson = json
                playerFetchedAt = now
            }
        }
        cachedAchievementDao.insert(
            CachedAchievement(
                appId = appId,
                steamId = steamId,
                schemaJson = schemaJson,
                playerJson = playerJson,
                schemaFetchedAt = schemaFetchedAt,
                playerFetchedAt = playerFetchedAt,
            ),
        )
    }

    private fun fetchSchema(key: String, appId: Int): Pair<String, Long>? {
        return try {
            val url = "$SCHEMA_URL?key=$key&appid=$appId&l=english"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            Pair(body, System.currentTimeMillis())
        } catch (e: Exception) {
            Timber.tag("Achievements").w(e, "Failed to fetch schema for appId=$appId")
            null
        }
    }

    private fun fetchPlayerAchievements(key: String, appId: Int, steamId: Long): Pair<String, Long>? {
        return try {
            val url = "$PLAYER_URL?key=$key&appid=$appId&steamid=$steamId&l=english"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            Pair(body, System.currentTimeMillis())
        } catch (e: Exception) {
            Timber.tag("Achievements").w(e, "Failed to fetch player achievements appId=$appId")
            null
        }
    }

    private fun parseMerged(cache: CachedAchievement): List<SteamAchievement> {
        val schema = cache.schemaJson?.let { parseSchema(it) } ?: return emptyList()
        val playerMap = cache.playerJson?.let { parsePlayerAchievements(it) } ?: emptyMap()
        return schema.map { row ->
            val (unlocked, unlockTime) = playerMap[row.apiName] ?: (false to 0L)
            SteamAchievement(
                apiName = row.apiName,
                name = row.name,
                description = row.desc,
                iconUrl = row.icon,
                iconGrayUrl = row.iconGray,
                unlocked = unlocked,
                unlockTime = unlockTime,
            )
        }
    }

    private data class SchemaRow(val apiName: String, val name: String, val desc: String, val icon: String, val iconGray: String)

    private fun parseSchema(json: String): List<SchemaRow> {
        val list = mutableListOf<SchemaRow>()
        try {
            val root = JSONObject(json)
            val game = root.optJSONObject("game") ?: return list
            val stats = game.optJSONObject("availableGameStats") ?: return list
            val arr = stats.optJSONArray("achievements") ?: return list
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                list.add(
                    SchemaRow(
                        apiName = o.optString("name", ""),
                        name = o.optString("displayName", ""),
                        desc = o.optString("description", ""),
                        icon = o.optString("icon", ""),
                        iconGray = o.optString("icongray", ""),
                    ),
                )
            }
        } catch (_: Exception) { }
        return list
    }

    private fun parsePlayerAchievements(json: String): Map<String, Pair<Boolean, Long>> {
        val map = mutableMapOf<String, Pair<Boolean, Long>>()
        try {
            val root = JSONObject(json)
            val playerstats = root.optJSONObject("playerstats") ?: return map
            val arr = playerstats.optJSONArray("achievements") ?: return map
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val apiname = o.optString("apiname", "")
                if (apiname.isEmpty()) continue
                val achieved = o.optInt("achieved", 0) == 1
                val unlocktime = o.optLong("unlocktime", 0L)
                map[apiname] = Pair(achieved, unlocktime)
            }
        } catch (_: Exception) { }
        return map
    }
}
