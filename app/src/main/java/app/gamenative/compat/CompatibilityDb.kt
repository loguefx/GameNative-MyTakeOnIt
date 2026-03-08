package app.gamenative.compat

import android.content.Context
import app.gamenative.compat.CompatibilityEntry.VerificationStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Compatibility DB: Verified / Boots only / Unsupported per game.
 * Default: no entries (UNKNOWN); ship with embedded JSON or fetch via OTA.
 */
object CompatibilityDb {

    private const val FILE_NAME = "compatibility_db.json"
    private val json = Json { ignoreUnknownKeys = true }

    private var inMemoryEntries: Map<Long, CompatibilityEntry> = emptyMap()

    @Serializable
    private data class DbFile(val entries: List<CompatibilityEntry> = emptyList())

    fun load(context: Context) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) {
                inMemoryEntries = emptyMap()
                return
            }
            val db = json.decodeFromString<DbFile>(file.readText())
            inMemoryEntries = db.entries.associateBy { it.steamAppId }
        } catch (_: Exception) {
            inMemoryEntries = emptyMap()
        }
    }

    fun get(steamAppId: Long): CompatibilityEntry? = inMemoryEntries[steamAppId]

    fun getStatus(steamAppId: Long): VerificationStatus = get(steamAppId)?.verificationStatus ?: VerificationStatus.UNKNOWN

    fun isDx12Verified(steamAppId: Long): Boolean = get(steamAppId)?.dx12Verified == true

    fun isOverlayVerified(steamAppId: Long): Boolean = get(steamAppId)?.overlayVerified == true

    /**
     * DX11-first policy: only allow DX12 if game is on DX12 Verified list.
     */
    fun allowDx12For(steamAppId: Long): Boolean = isDx12Verified(steamAppId)

    /**
     * Maps compatibility verification status to ProtonDB-style tier for GameConfigRecommender.
     * BOOTS_ONLY → SILVER: game launches but has significant issues; Silver means "works with
     * workarounds", which matches that state.
     */
    fun getProtonTierFor(steamAppId: Long): app.gamenative.proton.ProtonTier {
        val entry = get(steamAppId) ?: return app.gamenative.proton.ProtonTier.UNKNOWN
        return when (entry.verificationStatus) {
            CompatibilityEntry.VerificationStatus.VERIFIED -> app.gamenative.proton.ProtonTier.PLATINUM
            CompatibilityEntry.VerificationStatus.BOOTS_ONLY -> app.gamenative.proton.ProtonTier.SILVER
            CompatibilityEntry.VerificationStatus.UNSUPPORTED -> app.gamenative.proton.ProtonTier.BORKED
            CompatibilityEntry.VerificationStatus.UNKNOWN -> app.gamenative.proton.ProtonTier.UNKNOWN
        }
    }
}
