package app.gamenative.supervisor

import android.content.Context
import app.gamenative.preflight.PreflightCode
import app.gamenative.supervisor.LaunchDiagnostic.DiagnosticCode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import timber.log.Timber

/**
 * Stores and retrieves the last launch diagnostic (for "Last run" / Diagnostics UI).
 */
object DiagnosticStore {

    private const val FILE_NAME = "last_launch_diagnostic.json"
    private const val MAX_LOG_LINES = 200
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun getDiagnosticFile(context: Context, appId: String? = null): File {
        val dir = context.filesDir
        val name = if (appId != null) "last_launch_${appId}.json" else FILE_NAME
        return File(dir, name)
    }

    fun save(context: Context, diagnostic: LaunchDiagnostic, appId: String? = null) {
        try {
            getDiagnosticFile(context, appId).writeText(json.encodeToString(diagnostic))
        } catch (e: Exception) {
            Timber.e(e, "Failed to save launch diagnostic")
        }
    }

    fun load(context: Context, appId: String? = null): LaunchDiagnostic? {
        return try {
            val file = getDiagnosticFile(context, appId)
            if (!file.exists()) return null
            json.decodeFromString<LaunchDiagnostic>(file.readText())
        } catch (e: Exception) {
            Timber.w(e, "Failed to load launch diagnostic")
            null
        }
    }

    fun fromPreflightBlocked(reason: String, code: PreflightCode): LaunchDiagnostic {
        return LaunchDiagnostic(
            diagnosticCode = DiagnosticCode.PREFLIGHT_BLOCKED,
            summary = reason,
        )
    }

    fun truncateLines(lines: List<String>, max: Int = MAX_LOG_LINES): List<String> {
        if (lines.size <= max) return lines
        return lines.takeLast(max)
    }
}
