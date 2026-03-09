package app.gamenative.ui.component.dialog

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.R
import app.gamenative.profile.GameProfileOverrides
import app.gamenative.profile.GameProfileStore
import app.gamenative.profile.WineRuntime
import app.gamenative.ui.theme.PluviaTypography
import app.gamenative.ui.theme.gnAccentGlow
import app.gamenative.ui.theme.gnAccentPrimary
import app.gamenative.ui.theme.gnBgDeepest
import app.gamenative.ui.theme.gnBgElevated
import app.gamenative.ui.theme.gnBgOverlay
import app.gamenative.ui.theme.gnBgSurface
import app.gamenative.ui.theme.gnBorderCard
import app.gamenative.ui.theme.gnDivider
import app.gamenative.ui.theme.gnNeonTeal
import app.gamenative.ui.theme.gnTextPrimary
import app.gamenative.ui.theme.gnTextSecondary
import app.gamenative.ui.theme.gnTextTertiary
import app.gamenative.utils.ContainerUtils
import app.gamenative.utils.ManifestContentTypes
import app.gamenative.utils.ManifestInstaller
import app.gamenative.utils.MergedVersionEntry
import app.gamenative.utils.ManifestRepository
import com.winlator.container.ContainerData
import com.winlator.contents.ContentProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.ui.graphics.Color

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
    gameName: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var config by remember(appId) { mutableStateOf<GameProfileOverrides?>(null) }
    var mergedVersions by remember(appId) { mutableStateOf<List<MergedVersionEntry>>(emptyList()) }
    var showVersionPicker by remember { mutableStateOf(false) }
    val downloadProgress = remember { MutableStateFlow<Map<String, Float>>(emptyMap()) }
    val downloadProgressState by downloadProgress.collectAsState(emptyMap())

    LaunchedEffect(appId, gameName) {
        val overrides = withContext(Dispatchers.IO) { GameProfileStore.load(context, appId) }
        config = overrides ?: GameProfileOverrides(
            dxVersionOverride = GameProfileOverrides.DX_AUTO,
        )
        mergedVersions = ManifestRepository.getMergedVersionList(context, appId, gameName)
    }

    val activeVersion = remember(config?.compatibilityLayerVersionId, mergedVersions) {
        config?.compatibilityLayerVersionId?.let { id ->
            mergedVersions.find { it.id == id }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = gnBgDeepest,
        sheetState = sheetState,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(gnTextTertiary.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            // Header
            Column(
                modifier = Modifier.padding(
                    start = 20.dp, end = 20.dp,
                    top = 8.dp, bottom = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.game_settings_title),
                    style = PluviaTypography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = gnTextPrimary,
                )
                Text(
                    text = gameName,
                    style = PluviaTypography.bodyMedium,
                    color = gnTextSecondary,
                )
            }

            if (config == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Loading…",
                        style = PluviaTypography.bodyMedium,
                        color = gnTextSecondary,
                    )
                }
            } else {
                val c = config!!
                var frameCap by remember(c) { mutableStateOf(c.frameCap ?: 60) }
                var resolutionScale by remember(c) { mutableStateOf(c.resolutionScale ?: 1f) }
                var asyncShaders by remember(c) { mutableStateOf(c.asyncShaders ?: true) }
                var vsync by remember(c) { mutableStateOf(c.vsync ?: true) }
                var dxVersionOverride by remember(c) {
                    mutableStateOf(c.dxVersionOverride ?: GameProfileOverrides.DX_AUTO)
                }
                var wineRuntime by remember(c) { mutableStateOf(c.wineRuntime) }
                var activeVersionId by remember(c) {
                    mutableStateOf(c.compatibilityLayerVersionId)
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    // PERFORMANCE
                    SettingsSectionLabel(stringResource(R.string.game_settings_performance_section))
                    SettingsFieldLabel(stringResource(R.string.game_settings_frame_cap))
                    EqualWidthChipRow {
                        SettingsChip("Off", frameCap == 0, onClick = { frameCap = 0 })
                        SettingsChip("30", frameCap == 30, onClick = { frameCap = 30 })
                        SettingsChip("45", frameCap == 45, onClick = { frameCap = 45 })
                        SettingsChip("60", frameCap == 60, onClick = { frameCap = 60 })
                        SettingsChip("90", frameCap == 90, onClick = { frameCap = 90 })
                        SettingsChip("120", frameCap == 120, onClick = { frameCap = 120 })
                    }

                    SettingsFieldLabel(stringResource(R.string.game_settings_resolution_scale))
                    EqualWidthChipRow {
                        SettingsChip("50%", resolutionScale == 0.5f, onClick = { resolutionScale = 0.5f })
                        SettingsChip("75%", resolutionScale == 0.75f, onClick = { resolutionScale = 0.75f })
                        SettingsChip("100%", resolutionScale == 1f, onClick = { resolutionScale = 1f })
                        SettingsChip("125%", resolutionScale == 1.25f, onClick = { resolutionScale = 1.25f })
                    }

                    // GRAPHICS
                    SettingsSectionLabel(stringResource(R.string.game_settings_graphics_section))
                    SettingsToggleRow(
                        label = stringResource(R.string.game_settings_async_shaders),
                        checked = asyncShaders,
                        onToggle = { asyncShaders = it },
                    )
                    SettingsFieldLabel(stringResource(R.string.game_settings_dx_override))
                    EqualWidthChipRow {
                        SettingsChip("Auto", dxVersionOverride == GameProfileOverrides.DX_AUTO, onClick = { dxVersionOverride = GameProfileOverrides.DX_AUTO })
                        SettingsChip("DX9", dxVersionOverride == GameProfileOverrides.DX_9, onClick = { dxVersionOverride = GameProfileOverrides.DX_9 })
                        SettingsChip("DX10", dxVersionOverride == GameProfileOverrides.DX_10, onClick = { dxVersionOverride = GameProfileOverrides.DX_10 })
                        SettingsChip("DX11", dxVersionOverride == GameProfileOverrides.DX_11, onClick = { dxVersionOverride = GameProfileOverrides.DX_11 })
                        SettingsChip("DX12", dxVersionOverride == GameProfileOverrides.DX_12, onClick = { dxVersionOverride = GameProfileOverrides.DX_12 })
                    }
                    SettingsToggleRow(
                        label = stringResource(R.string.game_settings_vsync),
                        checked = vsync,
                        onToggle = { vsync = it },
                    )

                    // RUNTIME
                    SettingsSectionLabel(stringResource(R.string.game_settings_runtime_section))
                    SettingsFieldLabel("Wine runtime")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SettingsChip(
                            label = "Stock Wine",
                            selected = wineRuntime == WineRuntime.STOCK,
                            onClick = { wineRuntime = WineRuntime.STOCK },
                        )
                        SettingsChip(
                            label = "Wine-GE",
                            selected = wineRuntime == WineRuntime.WINE_GE,
                            onClick = { wineRuntime = WineRuntime.WINE_GE },
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    VersionPickerRow(
                        label = "Compatibility Layer",
                        currentValue = activeVersion?.displayName ?: "Default",
                        onClick = { showVersionPicker = true },
                    )
                    Spacer(Modifier.height(4.dp))
                }

                // Footer: Cancel + Save
                HorizontalDivider(color = gnDivider)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, gnBorderCard),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = gnTextSecondary),
                    ) {
                        Text(
                            stringResource(R.string.cancel),
                            style = PluviaTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                    Button(
                        onClick = {
                            val overrides = GameProfileOverrides(
                                frameCap = frameCap,
                                resolutionScale = resolutionScale,
                                asyncShaders = asyncShaders,
                                vsync = vsync,
                                dxVersionOverride = if (dxVersionOverride == GameProfileOverrides.DX_AUTO) null else dxVersionOverride,
                                wineRuntime = wineRuntime,
                                compatibilityLayerVersionId = activeVersionId,
                            )
                            scope.launch {
                                saveAndApply(context, appId, overrides)
                                onDismiss()
                            }
                        },
                        modifier = Modifier
                            .weight(2f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = gnAccentPrimary),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.game_settings_save),
                            style = PluviaTypography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }
        }
    }

    if (showVersionPicker && config != null) {
        val currentId = config!!.compatibilityLayerVersionId
        VersionPickerSheet(
            appId = appId,
            currentVersion = currentId,
            versions = mergedVersions,
            downloadProgress = downloadProgressState,
            onSelect = { entry ->
                scope.launch {
                    val overrides = (config ?: return@launch).copy(
                        compatibilityLayerVersionId = entry.id,
                    )
                    withContext(Dispatchers.IO) {
                        GameProfileStore.save(context, appId, overrides)
                    }
                    config = overrides
                    showVersionPicker = false
                }
            },
            onDownload = { entry ->
                scope.launch {
                    val contentType = if (entry.sourceType == ManifestContentTypes.PROTON) {
                        ContentProfile.ContentType.CONTENT_TYPE_PROTON
                    } else {
                        ContentProfile.ContentType.CONTENT_TYPE_WINE
                    }
                    downloadProgress.update { it + (entry.id to 0f) }
                    try {
                        val result = ManifestInstaller.installManifestEntry(
                            context = context,
                            entry = entry.manifestEntry,
                            isDriver = false,
                            contentType = contentType,
                            onProgress = { p ->
                                downloadProgress.update { m -> m + (entry.id to p) }
                            },
                            preferredVerName = entry.id,
                        )
                        downloadProgress.update { it - entry.id }
                        mergedVersions = ManifestRepository.getMergedVersionList(context, appId, gameName)
                        if (result.success) {
                            config = config?.copy(compatibilityLayerVersionId = entry.id)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Version install failed")
                        downloadProgress.update { it - entry.id }
                    }
                }
            },
            onDismiss = { showVersionPicker = false },
        )
    }
}

// --- Helper composables (redesign spec) ---

@Composable
fun EqualWidthChipRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun RowScope.SettingsChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = {
            Text(
                text = label,
                style = PluviaTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        modifier = Modifier
            .weight(1f)
            .height(44.dp),
        shape = RoundedCornerShape(22.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = gnAccentPrimary,
            selectedLabelColor = Color.White,
            containerColor = gnBgElevated,
            labelColor = gnTextSecondary,
            disabledContainerColor = gnBgSurface,
            disabledLabelColor = gnTextTertiary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = gnBorderCard,
            selectedBorderColor = Color.Transparent,
            borderWidth = 1.dp,
        ),
    )
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = PluviaTypography.labelSmall.copy(
            letterSpacing = 0.08.sp,
            fontWeight = FontWeight.Bold,
        ),
        color = gnTextTertiary,
    )
}

@Composable
private fun SettingsFieldLabel(text: String) {
    Text(
        text = text,
        style = PluviaTypography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        color = gnTextPrimary,
    )
}

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = PluviaTypography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = gnTextPrimary,
        )
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = gnAccentPrimary,
                uncheckedThumbColor = gnTextSecondary,
                uncheckedTrackColor = gnBgElevated,
                uncheckedBorderColor = gnBorderCard,
            ),
        )
    }
}

@Composable
private fun VersionPickerRow(
    label: String,
    currentValue: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(gnBgElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = PluviaTypography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = gnTextPrimary,
            )
            Text(
                text = "Tap to pick or download a version",
                style = PluviaTypography.bodySmall,
                color = gnTextTertiary,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = currentValue,
                style = PluviaTypography.bodySmall,
                color = gnTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = gnTextTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VersionPickerSheet(
    appId: String,
    currentVersion: String?,
    versions: List<MergedVersionEntry>,
    downloadProgress: Map<String, Float>,
    onSelect: (MergedVersionEntry) -> Unit,
    onDownload: (MergedVersionEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = gnBgSurface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(gnTextTertiary.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Compatibility Layer",
                    style = PluviaTypography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = gnTextPrimary,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null, tint = gnTextTertiary)
                }
            }
            HorizontalDivider(color = gnDivider)

            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                val grouped = versions.groupBy { it.sourceGroup }
                grouped.forEach { (group, entries) ->
                    item {
                        Text(
                            text = group.uppercase(),
                            style = PluviaTypography.labelSmall.copy(
                                letterSpacing = 0.10.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = gnTextTertiary,
                            modifier = Modifier.padding(
                                start = 20.dp, end = 20.dp,
                                top = 16.dp, bottom = 4.dp,
                            ),
                        )
                    }
                    items(entries) { entry ->
                        VersionPickerItem(
                            entry = entry,
                            isCurrent = entry.id == currentVersion,
                            progress = downloadProgress[entry.id],
                            onSelect = {
                                if (entry.isInstalled) {
                                    onSelect(entry)
                                    onDismiss()
                                }
                            },
                            onDownload = { onDownload(entry) },
                        )
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun VersionPickerItem(
    entry: MergedVersionEntry,
    isCurrent: Boolean,
    progress: Float?,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = entry.isInstalled, onClick = onSelect)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.displayName,
                    style = PluviaTypography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = if (entry.isInstalled) gnTextPrimary else gnTextTertiary,
                    ),
                )
                if (entry.isLatest) MiniVersionBadge("LATEST", gnNeonTeal)
                if (entry.isRecommended) MiniVersionBadge("RECOMMENDED", gnAccentGlow)
            }
            Text(
                text = if (entry.isInstalled) "Installed" else "%.0f MB · Tap Download to install".format(entry.sizeMb),
                style = PluviaTypography.labelSmall,
                color = if (entry.isInstalled) gnNeonTeal else gnTextTertiary,
            )
        }

        Spacer(Modifier.width(12.dp))

        when {
            isCurrent -> {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = gnNeonTeal,
                    modifier = Modifier.size(22.dp),
                )
            }
            progress != null -> {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(34.dp),
                        color = gnAccentPrimary,
                        strokeWidth = 3.dp,
                        trackColor = gnBgOverlay,
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = PluviaTypography.labelSmall.copy(fontSize = 8.sp),
                        color = gnTextPrimary,
                    )
                }
            }
            entry.isInstalled -> {
                OutlinedButton(
                    onClick = onSelect,
                    border = androidx.compose.foundation.BorderStroke(1.dp, gnBorderCard),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = gnTextSecondary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(34.dp),
                ) {
                    Text("Use", style = PluviaTypography.labelMedium)
                }
            }
            else -> {
                OutlinedButton(
                    onClick = onDownload,
                    border = androidx.compose.foundation.BorderStroke(1.dp, gnAccentPrimary.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = gnAccentPrimary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(34.dp),
                ) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Download", style = PluviaTypography.labelMedium)
                }
            }
        }
    }

    if (progress != null) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = gnAccentPrimary,
            trackColor = Color.Transparent,
        )
    }
    }
}

@Composable
private fun MiniVersionBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .border(0.5.dp, color.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = PluviaTypography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = color,
        )
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
