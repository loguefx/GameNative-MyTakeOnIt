package app.gamenative.ui.component.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gamenative.R
import app.gamenative.supervisor.DiagnosticStore
import app.gamenative.supervisor.LaunchDiagnostic

/**
 * Shows the last launch diagnostic for a game (why it failed or that it succeeded).
 * Display in game detail or settings so the user sees a clear, deterministic reason.
 */
@Composable
fun LastRunDiagnosticsSection(
    appId: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val diagnostic = appId?.let { DiagnosticStore.load(context, it) }

    if (diagnostic == null) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.diagnostics_last_run_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = diagnostic.summary,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (diagnostic.diagnosticCode != LaunchDiagnostic.DiagnosticCode.UNKNOWN) {
            Text(
                text = "Code: ${diagnostic.diagnosticCode.name}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (diagnostic.stderrTail.isNotEmpty()) {
            Text(
                text = stringResource(R.string.diagnostics_stderr_label),
                style = MaterialTheme.typography.labelMedium,
            )
            diagnostic.stderrTail.takeLast(30).forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
