package app.gamenative.compat

import android.content.Context
import com.winlator.core.GPUInformation
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Device GPU driver compatibility table (blacklist/whitelist).
 * Deterministic "we don't support this GPU/driver for DX12" instead of random crashes.
 */
object GpuDriverCompat {

    private const val FILE_NAME = "gpu_driver_compat.json"
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class DbFile(val entries: List<GpuDriverEntry> = emptyList())

    private var entries: List<GpuDriverEntry> = emptyList()

    fun load(context: Context) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) {
                entries = emptyList()
                return
            }
            val db = json.decodeFromString<DbFile>(file.readText())
            entries = db.entries
        } catch (_: Exception) {
            entries = emptyList()
        }
    }

    fun isDx12Allowed(context: Context): Boolean {
        val renderer = GPUInformation.getRenderer(context) ?: return false
        load(context)
        val match = entries.firstOrNull { renderer.contains(it.gpuNamePattern, ignoreCase = true) }
            ?: return false
        return !match.blacklisted && match.allowDx12
    }

    fun getBlacklistReason(context: Context): String? {
        val renderer = GPUInformation.getRenderer(context) ?: return null
        load(context)
        val match = entries.firstOrNull { renderer.contains(it.gpuNamePattern, ignoreCase = true) }
            ?: return null
        return if (match.blacklisted) match.blacklistReason else null
    }
}
