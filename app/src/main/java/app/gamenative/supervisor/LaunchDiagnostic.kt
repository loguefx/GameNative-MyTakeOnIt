package app.gamenative.supervisor

import app.gamenative.preflight.PreflightCode
import kotlinx.serialization.Serializable

/**
 * Diagnostic report produced when a launch fails (crash, exit, hang).
 * Human-readable summary + machine code for UI and logging.
 */
@Serializable
data class LaunchDiagnostic(
    val exitCode: Int? = null,
    val signal: String? = null,
    val diagnosticCode: DiagnosticCode,
    val summary: String,
    val wineLogLines: List<String> = emptyList(),
    val box64LogLines: List<String> = emptyList(),
    val stdoutTail: List<String> = emptyList(),
    val stderrTail: List<String> = emptyList(),
    val timestampMillis: Long = System.currentTimeMillis(),
) {
    @Serializable
    enum class DiagnosticCode {
        VULKAN_INIT_FAILED,
        OUT_OF_MEMORY,
        DX12_UNSUPPORTED_ON_DRIVER,
        MISSING_VCRUNTIME,
        HANG_ON_SHADER_COMPILE,
        GAME_EXIT_UNKNOWN,
        PREFLIGHT_BLOCKED,
        UNKNOWN,
    }

    fun toPreflightCodeOrNull(): PreflightCode? = when (diagnosticCode) {
        DiagnosticCode.VULKAN_INIT_FAILED -> PreflightCode.VULKAN_INIT_FAILED
        DiagnosticCode.OUT_OF_MEMORY -> PreflightCode.INSUFFICIENT_RAM
        DiagnosticCode.DX12_UNSUPPORTED_ON_DRIVER -> PreflightCode.DX12_DRIVER_BLACKLISTED
        DiagnosticCode.MISSING_VCRUNTIME -> PreflightCode.WINE_PREFIX_INVALID
        else -> null
    }
}
