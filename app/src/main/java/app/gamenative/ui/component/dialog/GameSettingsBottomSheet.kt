package app.gamenative.ui.component.dialog

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gamenative.R
import app.gamenative.profile.GameProfileOverrides
import app.gamenative.profile.GameProfileStore
import app.gamenative.utils.ContainerUtils
import com.winlator.container.ContainerData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

private val FRAME_CAP_OPTIONS = listOf(0, 30, 45, 60, 90, 120)
private val RESOLUTION_SCALE_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f)
private val DX_OVERRIDE_OPTIONS = listOf(
    GameProfileOverrides.DX_AUTO,
    GameProfileOverrides.DX_9,
    GameProfileOverrides.DX_10,
    GameProfileOverrides.DX_11,
    GameProfileOverrides.DX_12,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameSettingsBottomSheet(
    appId: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var frameCap by remember(appId) { mutableStateOf<Int?>(null) }
    var resolutionScale by remember(appId) { mutableStateOf<Float?>(null) }
    var asyncShaders by remember(appId) { mutableStateOf<Boolean?>(null) }
    var vsync by remember(appId) { mutableStateOf<Boolean?>(null) }
    var dxVersionOverride by remember(appId) { mutableStateOf<String?>(null) }
    var loaded by remember(appId) { mutableStateOf(false) }

    LaunchedEffect(appId) {
        val overrides = withContext(Dispatchers.IO) { GameProfileStore.load(context, appId) }
        if (overrides != null) {
            frameCap = overrides.frameCap
            resolutionScale = overrides.resolutionScale
            asyncShaders = overrides.asyncShaders
            vsync = overrides.vsync
            dxVersionOverride = overrides.dxVersionOverride ?: GameProfileOverrides.DX_AUTO
        } else {
            dxVersionOverride = GameProfileOverrides.DX_AUTO
        }
        loaded = true
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.game_settings_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (!loaded) {
            Text("Loading…", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        // Frame cap
        val frameCapUncappedStr = stringResource(R.string.game_settings_frame_cap_uncapped)
        Text(text = stringResource(R.string.game_settings_frame_cap), style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(4.dp))
        RowOfOptions(
            current = frameCap ?: 60,
            options = FRAME_CAP_OPTIONS,
            label = { if (it == 0) frameCapUncappedStr else "$it" },
            onSelect = { frameCap = it },
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Resolution scale
        Text(text = stringResource(R.string.game_settings_resolution_scale), style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(4.dp))
        RowOfOptions(
            current = resolutionScale ?: 1f,
            options = RESOLUTION_SCALE_OPTIONS,
            label = { "${(it * 100).toInt()}%" },
            onSelect = { resolutionScale = it },
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Async shaders
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.game_settings_async_shaders),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = asyncShaders ?: true,
                onCheckedChange = { asyncShaders = it },
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // VSync
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.game_settings_vsync),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = vsync ?: true,
                onCheckedChange = { vsync = it },
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        // DX override
        val dxAutoStr = stringResource(R.string.game_settings_dx_auto)
        Text(text = stringResource(R.string.game_settings_dx_override), style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(4.dp))
        RowOfOptions(
            current = dxVersionOverride ?: GameProfileOverrides.DX_AUTO,
            options = DX_OVERRIDE_OPTIONS,
            label = { if (it == GameProfileOverrides.DX_AUTO) dxAutoStr else it },
            onSelect = { dxVersionOverride = it },
        )
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
            Button(
                onClick = {
                    val overrides = GameProfileOverrides(
                        frameCap = frameCap,
                        resolutionScale = resolutionScale,
                        asyncShaders = asyncShaders,
                        vsync = vsync,
                        dxVersionOverride = if (dxVersionOverride == GameProfileOverrides.DX_AUTO) null else dxVersionOverride,
                    )
                    scope.launch {
                        saveAndApply(context, appId, overrides)
                        onDismiss()
                    }
                },
            ) {
                Text(stringResource(R.string.game_settings_save))
            }
        }
    }
}

@Composable
private fun <T> RowOfOptions(
    current: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val selected = option == current
            if (selected) {
                Button(
                    onClick = { onSelect(option) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(label(option))
                }
            } else {
                OutlinedButton(
                    onClick = { onSelect(option) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(label(option))
                }
            }
        }
    }
}

private suspend fun saveAndApply(context: Context, appId: String, overrides: GameProfileOverrides) {
    withContext(Dispatchers.IO) {
        try {
            GameProfileStore.save(context, appId, overrides)
            val container = ContainerUtils.getOrCreateContainer(context, appId)
            var containerData: ContainerData = ContainerUtils.toContainerData(container)
            containerData = ContainerUtils.applyPerGameContainerOverrides(context, appId, containerData)
            ContainerUtils.applyToContainer(context, container, containerData, saveToDisk = true)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save game settings for $appId")
        }
    }
}
