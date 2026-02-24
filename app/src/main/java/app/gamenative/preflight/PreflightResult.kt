package app.gamenative.preflight

/**
 * Result of preflight checks before launch.
 * Either Ok to proceed, or Blocked with a human-readable reason and machine code.
 */
sealed class PreflightResult {
    data object Ok : PreflightResult()

    data class Blocked(
        val reason: String,
        val code: PreflightCode,
    ) : PreflightResult()
}
