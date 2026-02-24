package app.gamenative.supervisor

import app.gamenative.supervisor.LaunchDiagnostic.DiagnosticCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Process supervisor: starts the game process tree, monitors for crash/exit/hang,
 * captures stdout/stderr and log tails, and produces a LaunchDiagnostic on failure.
 *
 * This layer does not start the actual box64/wine process (that remains in
 * XServerScreen / BionicProgramLauncherComponent); it provides the contract and
 * helpers so the existing launcher can record diagnostics. Full process wrapping
 * can be added later (e.g. ProcessBuilder with redirects).
 */
object LaunchSupervisor {

    private const val HANG_TIMEOUT_MS = 90_000L

    /**
     * Captures the last N lines from a file (e.g. Wine log, Box64 log).
     */
    suspend fun captureLogTail(file: File?, maxLines: Int = 200): List<String> = withContext(Dispatchers.IO) {
        if (file == null || !file.exists()) return@withContext emptyList()
        try {
            file.useLines { lines ->
                lines.toList().let { all ->
                    if (all.size <= maxLines) all else all.takeLast(maxLines)
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to read log tail from ${file?.path}")
            emptyList()
        }
    }

    /**
     * Builds a diagnostic from a preflight block (no process was started).
     */
    fun diagnosticFromPreflight(reason: String, code: app.gamenative.preflight.PreflightCode): LaunchDiagnostic {
        return DiagnosticStore.fromPreflightBlocked(reason, code)
    }

    /**
     * Builds a diagnostic from process exit (exit code or signal).
     * Caller can pass captured stdout/stderr and log file paths.
     */
    fun diagnosticFromExit(
        exitCode: Int?,
        signal: String?,
        stdoutLines: List<String> = emptyList(),
        stderrLines: List<String> = emptyList(),
        wineLogPath: String? = null,
        box64LogPath: String? = null,
    ): LaunchDiagnostic {
        val wineLog = if (wineLogPath != null) {
            try {
                File(wineLogPath).useLines { it.toList() }.takeLast(200)
            } catch (_: Exception) { emptyList() }
        } else emptyList()
        val box64Log = if (box64LogPath != null) {
            try {
                File(box64LogPath).useLines { it.toList() }.takeLast(200)
            } catch (_: Exception) { emptyList() }
        } else emptyList()

        val (code, summary) = inferDiagnostic(exitCode, signal, stderrLines)
        return LaunchDiagnostic(
            exitCode = exitCode,
            signal = signal,
            diagnosticCode = code,
            summary = summary,
            wineLogLines = wineLog,
            box64LogLines = box64Log,
            stdoutTail = DiagnosticStore.truncateLines(stdoutLines),
            stderrTail = DiagnosticStore.truncateLines(stderrLines),
        )
    }

    private fun inferDiagnostic(
        exitCode: Int?,
        signal: String?,
        stderr: List<String>,
    ): Pair<DiagnosticCode, String> {
        val errText = stderr.joinToString(" ").lowercase()
        when {
            signal != null && (signal == "SIGKILL" || signal.contains("kill")) ->
                return DiagnosticCode.OUT_OF_MEMORY to "Process was killed (likely out of memory / Android LMK)."
            exitCode != null && exitCode != 0 && errText.contains("vulkan") ->
                return DiagnosticCode.VULKAN_INIT_FAILED to "Vulkan initialization failed. Check driver and extensions."
            exitCode != null && errText.contains("vkd3d") ->
                return DiagnosticCode.DX12_UNSUPPORTED_ON_DRIVER to "DX12/VKD3D not supported on this driver."
            errText.contains("vcruntime") || errText.contains("msvcp") ->
                return DiagnosticCode.MISSING_VCRUNTIME to "Missing Visual C++ runtime in prefix."
            errText.contains("shader") && errText.contains("compile") ->
                return DiagnosticCode.HANG_ON_SHADER_COMPILE to "Game may have hung during shader compile."
            exitCode != null && exitCode != 0 ->
                return DiagnosticCode.GAME_EXIT_UNKNOWN to "Game exited with code $exitCode."
            else ->
                return DiagnosticCode.UNKNOWN to (signal?.let { "Process received $it." } ?: "Unknown failure.")
        }
    }
}
