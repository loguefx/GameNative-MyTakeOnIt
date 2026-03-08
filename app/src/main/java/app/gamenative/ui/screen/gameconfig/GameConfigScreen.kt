package app.gamenative.ui.screen.gameconfig

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.round
import androidx.hilt.navigation.compose.hiltViewModel
import app.gamenative.config.ConfigPreset
import app.gamenative.ui.model.GameConfigViewModel
import app.gamenative.config.DxVersion
import app.gamenative.config.GameConfig
import app.gamenative.config.ProtonVersion
import app.gamenative.hardware.HardwareProfile
import app.gamenative.profile.WineRuntime
import app.gamenative.ui.theme.PluviaTypography
import app.gamenative.ui.theme.gnAccentGlow
import app.gamenative.ui.theme.gnAccentPrimary
import app.gamenative.ui.theme.gnBgDeepest
import app.gamenative.ui.theme.gnBgElevated
import app.gamenative.ui.theme.gnBgSurface
import app.gamenative.ui.theme.gnBorderCard
import app.gamenative.ui.theme.gnTextPrimary
import app.gamenative.ui.theme.gnTextSecondary
import app.gamenative.ui.theme.gnTextTertiary
import app.gamenative.ui.theme.gnAccentSubtle
import app.gamenative.proton.ProtonTier
import app.gamenative.ui.component.ProtonBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameConfigScreen(
    appId: String,
    gameName: String,
    onLaunch: (GameConfig) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: GameConfigViewModel = hiltViewModel(),
) {
    val hardware by viewModel.hardware.collectAsState()
    val config by viewModel.config.collectAsState()
    val protonTier by viewModel.protonTier.collectAsState()
    val recommendedProton by viewModel.recommendedProtonVersion.collectAsState()
    var showProtonPicker by remember { mutableStateOf(false) }
    var showDxPicker by remember { mutableStateOf(false) }
    var showWinePicker by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    LaunchedEffect(appId) {
        viewModel.load(appId, gameName)
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = gnBgDeepest,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Game Configuration",
                        style = PluviaTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = gnTextPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = gnTextPrimary)
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.resetToRecommended(appId, gameName) }) {
                        Text("Reset", color = gnAccentPrimary, style = PluviaTypography.labelLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = gnBgDeepest),
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(gnBgDeepest)
                    .padding(16.dp)
                    .navigationBarsPadding(),
            ) {
                Button(
                    onClick = { config?.let { onLaunch(it) } },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = config != null,
                    colors = ButtonDefaults.buttonColors(containerColor = gnAccentPrimary),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.SportsEsports, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Launch with These Settings",
                        style = PluviaTypography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        },
    ) { padding ->
        val c = config ?: return@Scaffold
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                HardwareSummaryCard(hardware = hardware, protonTier = protonTier)
            }
            item {
                PresetChipRow(
                    selected = c.preset,
                    onSelect = { viewModel.applyPreset(it) },
                )
            }
            if (c.notes.isNotBlank()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = gnBgSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, gnAccentPrimary.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                Icons.Default.AutoFixHigh,
                                contentDescription = null,
                                tint = gnAccentGlow,
                                modifier = Modifier.size(16.dp).padding(top = 2.dp),
                            )
                            Text(
                                text = c.notes,
                                style = PluviaTypography.bodySmall,
                                color = gnTextSecondary,
                            )
                        }
                    }
                }
            }
            item { ConfigSectionHeader("RUNTIME") }
            item {
                ConfigCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showProtonPicker = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Proton", style = PluviaTypography.bodyMedium, color = gnTextPrimary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.protonVersion.displayName, style = PluviaTypography.bodySmall, color = gnTextSecondary)
                            Icon(Icons.Default.ExpandMore, contentDescription = null, tint = gnTextTertiary, modifier = Modifier.size(20.dp))
                        }
                    }
                    ConfigDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showWinePicker = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Wine", style = PluviaTypography.bodyMedium, color = gnTextPrimary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (c.wineRuntime == WineRuntime.WINE_GE) "Wine-GE" else "Stock Wine", style = PluviaTypography.bodySmall, color = gnTextSecondary)
                            Icon(Icons.Default.ExpandMore, contentDescription = null, tint = gnTextTertiary, modifier = Modifier.size(20.dp))
                        }
                    }
                    ConfigDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDxPicker = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("DirectX", style = PluviaTypography.bodyMedium, color = gnTextPrimary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.dxVersion.name, style = PluviaTypography.bodySmall, color = gnTextSecondary)
                            Icon(Icons.Default.ExpandMore, contentDescription = null, tint = gnTextTertiary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
            item { ConfigSectionHeader("GRAPHICS") }
            item {
                ConfigCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Resolution scale", style = PluviaTypography.bodyMedium, color = gnTextPrimary)
                        Text("${(c.resolutionScale * 100).toInt()}%", style = PluviaTypography.bodySmall, color = gnTextSecondary)
                    }
                    Slider(
                        value = c.resolutionScale,
                        onValueChange = {
                            val stepped = (round(it / 0.05f) * 0.05f).coerceIn(0.5f, 1.25f)
                            viewModel.update(c.copy(resolutionScale = stepped, preset = ConfigPreset.CUSTOM))
                        },
                        valueRange = 0.5f..1.25f,
                        steps = 14,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(thumbColor = gnAccentPrimary, activeTrackColor = gnAccentPrimary, inactiveTrackColor = gnBorderCard),
                    )
                    ConfigDivider()
                    Text("FPS limit", style = PluviaTypography.bodyMedium, color = gnTextPrimary)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(30 to "30", 45 to "45", 60 to "60", 90 to "90", 120 to "120", 0 to "Uncapped").forEach { (value, label) ->
                            FilterChip(
                                selected = c.fpsLimit == value,
                                onClick = { viewModel.update(c.copy(fpsLimit = value, preset = ConfigPreset.CUSTOM)) },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = gnAccentSubtle, selectedLabelColor = gnTextPrimary),
                            )
                        }
                    }
                    ConfigDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("VSync", style = PluviaTypography.bodyMedium, color = gnTextPrimary)
                        Switch(
                            checked = c.vsync,
                            onCheckedChange = { viewModel.update(c.copy(vsync = it, preset = ConfigPreset.CUSTOM)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = gnAccentPrimary, checkedTrackColor = gnAccentSubtle),
                        )
                    }
                }
            }
            item { ConfigSectionHeader("DXVK") }
            item {
                ConfigCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Async shaders", style = PluviaTypography.bodyMedium, color = gnTextPrimary)
                        Switch(
                            checked = c.dxvkAsync,
                            onCheckedChange = { viewModel.update(c.copy(dxvkAsync = it, preset = ConfigPreset.CUSTOM)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = gnAccentPrimary, checkedTrackColor = gnAccentSubtle),
                        )
                    }
                    ConfigDivider()
                    ConfigRow("Compiler threads", c.dxvkNumCompilerThreads.toString())
                    ConfigDivider()
                    ConfigRow("VRAM budget", "${c.dxvkDeviceLocalMemoryMiB} MB")
                }
            }
            if (c.dxVersion == DxVersion.DX12 || c.dxVersion == DxVersion.AUTO) {
                item { ConfigSectionHeader("VKD3D") }
                item {
                    ConfigCard {
                        ConfigRow("Worker threads", c.vkd3dWorkerThreads.toString())
                        ConfigDivider()
                        ConfigRow("Shader model", c.vkd3dShaderModel)
                    }
                }
            }
            item { ConfigSectionHeader("WINE") }
            item {
                ConfigCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("ESYNC", style = PluviaTypography.bodyMedium, color = gnTextPrimary)
                        Switch(
                            checked = c.esyncEnabled,
                            onCheckedChange = { viewModel.update(c.copy(esyncEnabled = it, preset = ConfigPreset.CUSTOM)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = gnAccentPrimary, checkedTrackColor = gnAccentSubtle),
                        )
                    }
                    ConfigDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("FSYNC", style = PluviaTypography.bodyMedium, color = gnTextPrimary)
                        Switch(
                            checked = c.fsyncEnabled,
                            onCheckedChange = { viewModel.update(c.copy(fsyncEnabled = it, preset = ConfigPreset.CUSTOM)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = gnAccentPrimary, checkedTrackColor = gnAccentSubtle),
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    if (showProtonPicker && config != null) {
        val c = config!!
        ModalBottomSheet(
            onDismissRequest = { showProtonPicker = false },
            sheetState = sheetState,
            containerColor = gnBgSurface,
            contentColor = gnTextPrimary,
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
                Text("Proton version", style = PluviaTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = gnTextPrimary, modifier = Modifier.padding(bottom = 8.dp))
                ProtonVersion.entries.forEachIndexed { index, version ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.update(c.copy(protonVersion = version, preset = ConfigPreset.CUSTOM))
                                scope.launch { sheetState.hide() }
                                showProtonPicker = false
                            }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(version.displayName, style = PluviaTypography.bodyLarge, color = gnTextPrimary)
                        if (version == recommendedProton) {
                            Text("Recommended", style = PluviaTypography.labelSmall, color = gnAccentPrimary)
                        }
                    }
                    if (index < ProtonVersion.entries.size - 1) {
                        HorizontalDivider(color = gnBorderCard, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }

    if (showDxPicker && config != null) {
        val c = config!!
        ModalBottomSheet(
            onDismissRequest = { showDxPicker = false },
            sheetState = sheetState,
            containerColor = gnBgSurface,
            contentColor = gnTextPrimary,
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
                Text("DirectX version", style = PluviaTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = gnTextPrimary, modifier = Modifier.padding(bottom = 8.dp))
                DxVersion.entries.forEachIndexed { index, version ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.update(
                                    c.copy(
                                        dxVersion = version,
                                        preset = ConfigPreset.CUSTOM,
                                        dxvkEnabled = version != DxVersion.DX12,
                                        vkd3dEnabled = version == DxVersion.DX12,
                                    ),
                                )
                                scope.launch { sheetState.hide() }
                                showDxPicker = false
                            }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(version.name, style = PluviaTypography.bodyLarge, color = gnTextPrimary)
                    }
                    if (index < DxVersion.entries.size - 1) {
                        HorizontalDivider(color = gnBorderCard, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }

    if (showWinePicker && config != null) {
        val c = config!!
        ModalBottomSheet(
            onDismissRequest = { showWinePicker = false },
            sheetState = sheetState,
            containerColor = gnBgSurface,
            contentColor = gnTextPrimary,
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
                Text("Wine runtime", style = PluviaTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = gnTextPrimary, modifier = Modifier.padding(bottom = 8.dp))
                WineRuntime.entries.forEachIndexed { index, runtime ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.update(c.copy(wineRuntime = runtime, preset = ConfigPreset.CUSTOM))
                                    scope.launch { sheetState.hide() }
                                    showWinePicker = false
                                }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (runtime == WineRuntime.WINE_GE) "Wine-GE" else "Stock Wine",
                                style = PluviaTypography.bodyLarge,
                                color = gnTextPrimary,
                            )
                        }
                        if (runtime == WineRuntime.WINE_GE) {
                            Text(
                                "Requires a separate download if not installed.",
                                style = PluviaTypography.bodySmall,
                                color = gnTextSecondary,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        if (index < WineRuntime.entries.size - 1) {
                            HorizontalDivider(color = gnBorderCard, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun HardwareSummaryCard(hardware: HardwareProfile?, protonTier: ProtonTier) {
    if (hardware == null) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = gnBgSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, gnBorderCard),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(gnBgElevated, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Memory, contentDescription = null, tint = gnAccentGlow, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = hardware.cpuSoC,
                    style = PluviaTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = gnTextPrimary,
                )
                Text(
                    text = "${hardware.gpuModel} · ${hardware.ramGb}GB RAM",
                    style = PluviaTypography.bodySmall,
                    color = gnTextSecondary,
                )
                Text(
                    text = "Vulkan ${hardware.gpuVulkanVersion} · ${hardware.displayRefreshHz}Hz",
                    style = PluviaTypography.labelSmall,
                    color = gnTextTertiary,
                )
            }
            ProtonBadge(tier = protonTier)
        }
    }
}

@Composable
private fun PresetChipRow(selected: ConfigPreset, onSelect: (ConfigPreset) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            ConfigPreset.RECOMMENDED,
            ConfigPreset.PERFORMANCE,
            ConfigPreset.QUALITY,
            ConfigPreset.COMPATIBILITY,
        ).forEach { preset ->
            FilterChip(
                selected = selected == preset,
                onClick = { onSelect(preset) },
                label = { Text(preset.name.lowercase().replaceFirstChar { it.uppercase() }) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = gnAccentSubtle,
                    selectedLabelColor = gnTextPrimary,
                ),
            )
        }
    }
}

@Composable
private fun ConfigSectionHeader(title: String) {
    Text(
        text = title,
        style = PluviaTypography.labelSmall.copy(
            fontSize = 11.sp,
            letterSpacing = 0.10.sp,
        ),
        color = gnTextTertiary,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun ConfigCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = gnBgSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, gnBorderCard),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            content()
        }
    }
}

@Composable
private fun ConfigRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = PluviaTypography.bodyMedium, color = gnTextPrimary)
        Text(value, style = PluviaTypography.bodySmall, color = gnTextSecondary)
    }
}

@Composable
private fun ConfigDivider() {
    Spacer(Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(gnBorderCard),
    )
    Spacer(Modifier.height(8.dp))
}
