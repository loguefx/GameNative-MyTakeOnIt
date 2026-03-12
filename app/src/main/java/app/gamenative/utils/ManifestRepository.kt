package app.gamenative.utils

import android.content.Context
import app.gamenative.PrefManager
import app.gamenative.compat.CompatibilityDb
import app.gamenative.config.GameConfigRecommender
import app.gamenative.config.ProtonVersion
import app.gamenative.hardware.HardwareProfileCache
import app.gamenative.profile.WineRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import timber.log.Timber

@Serializable
private data class WineProtonVersionsAsset(
    val proton: List<ManifestEntry> = emptyList(),
    val wine: List<ManifestEntry> = emptyList(),
)

object ManifestRepository {
    private const val ONE_DAY_MS = 24 * 60 * 60 * 1000L
    private const val MANIFEST_URL = "https://raw.githubusercontent.com/utkarshdalal/GameNative/refs/heads/master/manifest.json"
    private const val LOCAL_WINE_PROTON_ASSET = "wine_proton_versions.json"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadManifest(context: Context): ManifestData {
        val cachedJson = PrefManager.componentManifestJson
        val cachedManifest = parseManifest(cachedJson) ?: ManifestData.empty()
        val lastFetchedAt = PrefManager.componentManifestFetchedAt
        val isStale = System.currentTimeMillis() - lastFetchedAt >= ONE_DAY_MS

        val base = if (cachedJson.isNotEmpty() && !isStale) {
            cachedManifest
        } else {
            val fetched = fetchManifestJson()
            if (fetched != null) {
                val parsed = parseManifest(fetched)
                if (parsed != null) {
                    val now = System.currentTimeMillis()
                    PrefManager.componentManifestJson = fetched
                    PrefManager.componentManifestFetchedAt = now
                    parsed
                } else cachedManifest
            } else cachedManifest
        }
        return mergeLocalWineProton(context, base)
    }

    /** Merges local wine/proton previous versions from assets so they appear in the list and install on click. */
    private fun mergeLocalWineProton(context: Context, manifest: ManifestData): ManifestData {
        val local = loadLocalWineProtonAsset(context) ?: return manifest
        val remoteWine = manifest.items[ManifestContentTypes.WINE].orEmpty()
        val remoteProton = manifest.items[ManifestContentTypes.PROTON].orEmpty()
        val remoteIdsWine = remoteWine.map { it.id }.toSet()
        val remoteIdsProton = remoteProton.map { it.id }.toSet()
        val mergedWine = remoteWine + local.wine.filter { it.id !in remoteIdsWine }
        val mergedProton = remoteProton + local.proton.filter { it.id !in remoteIdsProton }
        val newItems = manifest.items.toMutableMap().apply {
            put(ManifestContentTypes.WINE, mergedWine)
            put(ManifestContentTypes.PROTON, mergedProton)
        }
        return manifest.copy(items = newItems)
    }

    private fun loadLocalWineProtonAsset(context: Context): WineProtonVersionsAsset? {
        return try {
            context.assets.open(LOCAL_WINE_PROTON_ASSET).use { input ->
                val str = input.bufferedReader().readText()
                json.decodeFromString<WineProtonVersionsAsset>(str)
            }
        } catch (e: Exception) {
            Timber.w(e, "ManifestRepository: could not load $LOCAL_WINE_PROTON_ASSET")
            null
        }
    }

    private suspend fun fetchManifestJson(): String? = withContext(Dispatchers.IO) {
    try {
        val request = Request.Builder().url(MANIFEST_URL).build()
        Net.http.newCall(request).execute().use { response ->
            response.takeIf { it.isSuccessful }?.body?.string()
        }
    } catch (e: Exception) {
        Timber.e(e, "ManifestRepository: fetch failed")
        null
    }
}

    fun parseManifest(jsonString: String?): ManifestData? {
        if (jsonString.isNullOrBlank()) return null
        return try {
            json.decodeFromString<ManifestData>(jsonString)
        } catch (e: Exception) {
            Timber.e(e, "ManifestRepository: parse failed")
            null
        }
    }

    /**
     * Returns merged Wine/Proton versions for the Compatibility Layer picker.
     * Groups by source (Wine, Proton), marks installed/latest.
     * When [gameName] is provided, [MergedVersionEntry.isRecommended] is set using
     * [GameConfigRecommender] with the cached hardware profile and ProtonDB tier for this app.
     */
    suspend fun getMergedVersionList(
        context: Context,
        appId: String,
        gameName: String? = null,
    ): List<MergedVersionEntry> = withContext(Dispatchers.IO) {
        val manifest = loadManifest(context)
        val installed = ManifestComponentHelper.loadInstalledContentLists(context).installed
        val installedWineIds = installed.wine.toSet()
        val installedProtonIds = installed.proton.toSet()

        fun isInstalled(entry: ManifestEntry, contentType: String): Boolean {
            val set = if (contentType == ManifestContentTypes.PROTON) installedProtonIds else installedWineIds
            return set.any { inst -> entry.id == inst || entry.id.endsWith("-$inst") }
        }

        val wineEntries = manifest.items[ManifestContentTypes.WINE].orEmpty()
        val protonEntries = manifest.items[ManifestContentTypes.PROTON].orEmpty()

        val recommendedProtonVersion: ProtonVersion?
        val recommendedWineRuntime: WineRuntime
        if (gameName != null) {
            val hardware = HardwareProfileCache.getProfile(context)
            val steamAppId = appId.toLongOrNull() ?: 0L
            val protonTier = CompatibilityDb.getProtonTierFor(steamAppId)
            val appIdInt = appId.toIntOrNull() ?: 0
            val config = GameConfigRecommender.recommend(appIdInt, gameName, hardware, protonTier)
            recommendedProtonVersion = config.protonVersion
            recommendedWineRuntime = config.wineRuntime
        } else {
            recommendedProtonVersion = null
            recommendedWineRuntime = WineRuntime.STOCK
        }

        fun protonVersionPattern(version: ProtonVersion): String? = when (version) {
            ProtonVersion.PROTON_10_0       -> "10.0"
            ProtonVersion.PROTON_9_0        -> "9.0"
            ProtonVersion.PROTON_9_0_X86_64 -> "9.0-x86_64"
            ProtonVersion.PROTON_8_0        -> "8.0"
            ProtonVersion.PROTON_7_0        -> "7.0"
            ProtonVersion.WINE_GE_LATEST, ProtonVersion.STOCK_WINE -> null
        }

        val list = mutableListOf<MergedVersionEntry>()

        if (protonEntries.isNotEmpty()) {
            val protonPattern = recommendedProtonVersion?.let { protonVersionPattern(it) }
            list.addAll(protonEntries.mapIndexed { index, entry ->
                val recommended = protonPattern != null &&
                    (entry.id.contains(protonPattern) || entry.name.contains(protonPattern))
                MergedVersionEntry(
                    id = entry.id,
                    displayName = if (entry.name.isNotBlank()) entry.name else entry.id,
                    sourceGroup = "Proton (Valve)",
                    sourceType = ManifestContentTypes.PROTON,
                    sizeMb = 0f,
                    isInstalled = isInstalled(entry, ManifestContentTypes.PROTON),
                    isLatest = index == 0,
                    isRecommended = recommended,
                    manifestEntry = entry,
                )
            })
        }
        if (wineEntries.isNotEmpty()) {
            list.addAll(wineEntries.mapIndexed { index, entry ->
                val recommended = recommendedWineRuntime == WineRuntime.WINE_GE &&
                    (index == 0 || entry.name.contains("ge", ignoreCase = true) || entry.id.contains("ge", ignoreCase = true))
                MergedVersionEntry(
                    id = entry.id,
                    displayName = if (entry.name.isNotBlank()) entry.name else entry.id,
                    sourceGroup = "Wine",
                    sourceType = ManifestContentTypes.WINE,
                    sizeMb = 0f,
                    isInstalled = isInstalled(entry, ManifestContentTypes.WINE),
                    isLatest = index == 0,
                    isRecommended = recommended,
                    manifestEntry = entry,
                )
            })
        }

        list
    }
}
