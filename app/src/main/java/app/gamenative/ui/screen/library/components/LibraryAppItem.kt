package app.gamenative.ui.screen.library.components

import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face4
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.data.GameCompatibilityStatus
import app.gamenative.data.GameSource
import app.gamenative.data.LibraryItem
import app.gamenative.service.DownloadService
import app.gamenative.service.SteamService
import app.gamenative.service.epic.EpicService
import app.gamenative.service.gog.GOGService
import app.gamenative.ui.enums.PaneType
import app.gamenative.ui.icons.Steam
import app.gamenative.ui.internal.fakeAppInfo
import app.gamenative.ui.theme.PluviaTheme
import app.gamenative.ui.theme.gnBgSurface
import app.gamenative.ui.theme.gnBorderCard
import app.gamenative.ui.theme.gnStatusDownloading
import app.gamenative.ui.theme.gnStatusInstalled
import app.gamenative.ui.theme.gnTextPrimary
import app.gamenative.ui.util.ListItemImage
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil.CoilImage
import app.gamenative.utils.CustomGameScanner
import java.io.File
import timber.log.Timber

@Composable
internal fun AppItem(
    modifier: Modifier = Modifier,
    appInfo: LibraryItem,
    onClick: () -> Unit,
    paneType: PaneType = PaneType.LIST,
    onFocus: () -> Unit = {},
    isRefreshing: Boolean = false,
    imageRefreshCounter: Long = 0L,
    compatibilityStatus: GameCompatibilityStatus? = null,
) {
    val context = LocalContext.current
    var hideText by remember { mutableStateOf(true) }
    var alpha by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(paneType) {
        hideText = true
        alpha = 1f
    }

    // Reset alpha and hideText when image URL changes (e.g., when new images are fetched)
    LaunchedEffect(imageRefreshCounter) {
        if (paneType != PaneType.LIST) {
            hideText = true
            alpha = 1f
        }
    }

    // True when selected, e.g. with controller
    var isFocused by remember { mutableStateOf(false) }

    val isGrid = paneType == PaneType.GRID_CAPSULE || paneType == PaneType.GRID_HERO
    val border = when {
        isGrid -> androidx.compose.foundation.BorderStroke(1.dp, gnBorderCard)
        isFocused -> androidx.compose.foundation.BorderStroke(
            width = 3.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                ),
            ),
        )
        else -> androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .then(if (isGrid) Modifier.aspectRatio(2f / 3f) else Modifier)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
                if (isFocused) onFocus()
            }
            .clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ),
        shape = if (isGrid) RoundedCornerShape(12.dp) else RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGrid) gnBgSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
        border = border,
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            draggedElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
    ) {
        if (paneType == PaneType.LIST) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            Box(modifier = Modifier.clip(RoundedCornerShape(12.dp))) {
                if (paneType == PaneType.LIST) {
                    val iconUrl = remember(appInfo.appId) {
                        if (appInfo.gameSource == GameSource.CUSTOM_GAME) {
                            val path = CustomGameScanner.findIconFileForCustomGame(context, appInfo.appId)
                            if (!path.isNullOrEmpty()) {
                                if (path.startsWith("file://")) path else "file://$path"
                            } else {
                                appInfo.clientIconUrl
                            }
                        } else {
                            appInfo.clientIconUrl
                        }
                    }
                    ListItemImage(
                        modifier = Modifier.size(56.dp),
                        imageModifier = Modifier.clip(RoundedCornerShape(10.dp)),
                        image = { iconUrl },
                    )
                } else {
                    GridCardContent(
                        appInfo = appInfo,
                        imageRefreshCounter = imageRefreshCounter,
                        compatibilityStatus = compatibilityStatus,
                        isRefreshing = isRefreshing,
                    )
                }
            }
            }
            }

            if (paneType == PaneType.LIST) {
                GameInfoBlock(
                    modifier = Modifier.weight(1f),
                    appInfo = appInfo,
                    isRefreshing = isRefreshing,
                    compatibilityStatus = compatibilityStatus,
                )

                // Play/Open button
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(40.dp),
                ) {
                    Text(
                        text = stringResource(R.string.library_open),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        }
    }
@Composable
private fun GridCardContent(
    appInfo: LibraryItem,
    imageRefreshCounter: Long,
    compatibilityStatus: GameCompatibilityStatus?,
    isRefreshing: Boolean,
) {
    fun findCapsuleUrl(): String {
        if (appInfo.gameSource == GameSource.CUSTOM_GAME) {
            val path = CustomGameScanner.getFolderPathFromAppId(appInfo.appId) ?: return appInfo.capsuleImageUrl
            val folder = File(path)
            val imageFile = folder.listFiles()?.firstOrNull { file ->
                file.name.startsWith("steamgriddb_grid_capsule") &&
                    (file.name.endsWith(".png", true) || file.name.endsWith(".jpg", true) || file.name.endsWith(".webp", true))
            }
            return imageFile?.let { Uri.fromFile(it).toString() } ?: appInfo.capsuleImageUrl
        }
        return when (appInfo.gameSource) {
            GameSource.STEAM -> appInfo.capsuleImageUrl
            else -> appInfo.capsuleImageUrl
        }
    }
    val capsuleUrl = remember(appInfo.appId, imageRefreshCounter) { findCapsuleUrl() }
    var imageUrl by remember(capsuleUrl) { mutableStateOf(capsuleUrl) }
    var isInstalled by remember(appInfo.appId, appInfo.gameSource) { mutableStateOf(false) }
    val downloadInfo = remember(appInfo.appId) { if (appInfo.gameSource == GameSource.STEAM) SteamService.getAppDownloadInfo(appInfo.gameId) else null }
    var downloadProgress by remember(downloadInfo) { mutableFloatStateOf(downloadInfo?.getProgress() ?: 1f) }
    val isDownloading = downloadInfo != null && downloadProgress < 1f
    LaunchedEffect(appInfo.appId, appInfo.gameSource) {
        isInstalled = when (appInfo.gameSource) {
            GameSource.STEAM -> SteamService.isAppInstalled(appInfo.gameId)
            GameSource.GOG -> GOGService.isGameInstalled(appInfo.gameId.toString())
            GameSource.EPIC -> EpicService.isGameInstalled(appInfo.gameId)
            GameSource.CUSTOM_GAME -> true
            else -> false
        }
    }
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            isInstalled = when (appInfo.gameSource) {
                GameSource.STEAM -> SteamService.isAppInstalled(appInfo.gameId)
                GameSource.GOG -> GOGService.isGameInstalled(appInfo.gameId.toString())
                GameSource.EPIC -> EpicService.isGameInstalled(appInfo.gameId)
                GameSource.CUSTOM_GAME -> true
                else -> false
            }
        }
    }
    LaunchedEffect(downloadInfo) {
        downloadProgress = downloadInfo?.getProgress() ?: 1f
    }
    DisposableEffect(downloadInfo) {
        val onProgress: (Float) -> Unit = { downloadProgress = it }
        downloadInfo?.addProgressListener(onProgress)
        onDispose { downloadInfo?.removeProgressListener(onProgress) }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp)),
    ) {
        CoilImage(
            modifier = Modifier.fillMaxSize(),
            imageModel = { imageUrl },
            imageOptions = ImageOptions(contentScale = ContentScale.Crop, contentDescription = null),
            loading = { CircularProgressIndicator(modifier = Modifier.padding(24.dp)) },
            failure = { Icon(Icons.Filled.QuestionMark, null, modifier = Modifier.padding(24.dp)) },
            previewPlaceholder = painterResource(R.drawable.ic_logo_color),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .fillMaxHeight(0.33f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE0000000)))),
        )
        Text(
            text = appInfo.name,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = gnTextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (isInstalled || isDownloading) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                shape = RoundedCornerShape(6.dp),
                color = if (isDownloading) gnStatusDownloading else gnStatusInstalled,
            ) {
                Text(
                    text = if (isDownloading) stringResource(R.string.library_installing) else stringResource(R.string.library_installed),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Black,
                )
            }
        }
    }
}

@Composable
internal fun GameInfoBlock(
    modifier: Modifier,
    appInfo: LibraryItem,
    isRefreshing: Boolean = false,
    compatibilityStatus: GameCompatibilityStatus? = null,
) {
    // For text displayed in list view, or as override if image loading fails

    // Determine download and install state for Steam games only
    val isSteam = appInfo.gameSource == GameSource.STEAM
    val downloadInfo = remember(appInfo.appId) { if (isSteam) SteamService.getAppDownloadInfo(appInfo.gameId) else null }
    var downloadProgress by remember(downloadInfo) { mutableFloatStateOf(downloadInfo?.getProgress() ?: 0f) }
    val isDownloading = downloadInfo != null && downloadProgress < 1f
    var isInstalledSteam by remember(appInfo.appId) { mutableStateOf(if (isSteam) SteamService.isAppInstalled(appInfo.gameId) else false) }

    // Update installation status when refresh completes
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            if (isSteam) {
                // Refresh just completed, check installation status
                isInstalledSteam = SteamService.isAppInstalled(appInfo.gameId)
            }
        }
    }

    // Function to refresh progress from downloadInfo - can be called from remember and LaunchedEffect
    val refreshProgress: () -> Unit = {
        downloadProgress = downloadInfo?.getProgress() ?: 0f
    }

    // Refresh progress when list reloads (for downloading games) or when downloadInfo changes
    LaunchedEffect(appInfo.appId, downloadInfo, isRefreshing) {
        if (downloadInfo != null) {
            refreshProgress()
        }
    }

    // Listen to real-time progress updates via listener
    DisposableEffect(downloadInfo) {
        val onDownloadProgress: (Float) -> Unit = { progress ->
            downloadProgress = progress
        }
        downloadInfo?.addProgressListener(onDownloadProgress)

        onDispose {
            downloadInfo?.removeProgressListener(onDownloadProgress)
        }
    }

    var appSizeOnDisk by remember { mutableStateOf("") }

    var hideText by remember { mutableStateOf(true) }
    var alpha = remember(Int) { 1f }

    LaunchedEffect(isSteam, isInstalledSteam) {
        if (isSteam && isInstalledSteam) {
            appSizeOnDisk = "..."
            DownloadService.getSizeOnDiskDisplay(appInfo.gameId) { appSizeOnDisk = it }
        }
    }

    // Game info
    Column(
        modifier = modifier,
    ) {
        Text(
            text = appInfo.name,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Column(
            modifier = Modifier.padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Status indicator
            val (statusText, statusColor) = when (appInfo.gameSource) {
                GameSource.STEAM -> {
                    val text = when {
                        isDownloading -> stringResource(R.string.library_installing)
                        isInstalledSteam -> stringResource(R.string.library_installed)
                        else -> stringResource(R.string.library_not_installed)
                    }
                    val color = when {
                        isDownloading || isInstalledSteam -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    }
                    text to color
                }

                GameSource.GOG, GameSource.EPIC -> {
                    // GOG and Epic games - check installation status from their respective services
                    val isInstalled = when (appInfo.gameSource) {
                        GameSource.GOG -> GOGService.isGameInstalled(appInfo.gameId.toString())
                        GameSource.EPIC -> EpicService.isGameInstalled(appInfo.gameId)
                        else -> false
                    }
                    val text = if (isInstalled) {
                        stringResource(R.string.library_installed)
                    } else {
                        stringResource(R.string.library_not_installed)
                    }
                    val color = if (isInstalled) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    }
                    text to color
                }

                GameSource.CUSTOM_GAME -> {
                    // Custom Games are considered ready (no install tracking)
                    stringResource(R.string.library_status_ready) to MaterialTheme.colorScheme.tertiary
                }

                else -> {
                    stringResource(R.string.library_not_installed) to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Status dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color = statusColor, shape = CircleShape),
                )
                // Status text
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = statusColor,
                )
                // Download percentage when installing
                if (isDownloading) {
                    Text(
                        text = "${(downloadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusColor,
                    )
                }
            }

            // Game size on its own line for installed Steam games only
            if (isSteam && isInstalledSteam) {
                Text(
                    text = "$appSizeOnDisk",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Family share indicator on its own line if needed
            if (appInfo.isShared) {
                Text(
                    text = stringResource(R.string.library_family_shared),
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            // Compatibility status indicator on its own line if needed
            compatibilityStatus?.let { status ->
                val (text, color) = when (status) {
                    GameCompatibilityStatus.COMPATIBLE -> stringResource(R.string.library_compatible) to Color.Green

                    GameCompatibilityStatus.GPU_COMPATIBLE -> stringResource(R.string.library_compatible) to Color.Green

                    GameCompatibilityStatus.UNKNOWN -> stringResource(R.string.library_compatibility_unknown) to
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

                    GameCompatibilityStatus.NOT_COMPATIBLE -> stringResource(R.string.library_not_compatible) to Color.Red
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(text = text, style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), color = color)
                    GameSourceIcon(appInfo.gameSource)
                }
            }
        }
    }
}

@Composable
fun GameSourceIcon(gameSource: GameSource, modifier: Modifier = Modifier, iconSize: Int = 12) {
    when (gameSource) {
        GameSource.STEAM -> Icon(imageVector = Icons.Filled.Steam, contentDescription = "Steam", modifier = modifier.size(iconSize.dp).alpha(0.7f))
        GameSource.CUSTOM_GAME -> Icon(imageVector = Icons.Filled.Folder, contentDescription = "Custom Game", modifier = modifier.size(iconSize.dp).alpha(0.7f))
        GameSource.GOG -> Icon(painter = painterResource(R.drawable.ic_gog), contentDescription = "Gog", modifier = modifier.size(iconSize.dp).alpha(0.7f))
        GameSource.EPIC -> Icon(painter = painterResource(R.drawable.ic_epic), contentDescription = "Epic", modifier = modifier.size(iconSize.dp).alpha(0.7f))
    }
}

/***********
 * PREVIEW *
 ***********/

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun Preview_AppItem() {
    PrefManager.init(LocalContext.current)
    PluviaTheme {
        Surface {
            LazyColumn(
                modifier = Modifier.padding(16.dp),
            ) {
                items(
                    items = List(5) { idx ->
                        val item = fakeAppInfo(idx)
                        LibraryItem(
                            index = idx,
                            appId = "${GameSource.STEAM.name}_${item.id}",
                            name = item.name,
                            iconHash = item.iconHash,
                            isShared = idx % 2 == 0,
                            gameSource = GameSource.STEAM,
                        )
                    },
                    itemContent = {
                        // Show different compatibility states in preview
                        val status = when (it.index % 4) {
                            0 -> GameCompatibilityStatus.COMPATIBLE
                            1 -> GameCompatibilityStatus.GPU_COMPATIBLE
                            2 -> GameCompatibilityStatus.NOT_COMPATIBLE
                            else -> GameCompatibilityStatus.UNKNOWN
                        }
                        AppItem(
                            appInfo = it,
                            onClick = {},
                            compatibilityStatus = status,
                        )
                    },
                )
            }
        }
    }
}

@Preview(device = "spec:width=1920px,height=1080px,dpi=440") // Odin2 Mini
@Composable
private fun Preview_AppItemGrid() {
    PrefManager.init(LocalContext.current)
    PluviaTheme {
        Surface {
            Column {
                val appInfoList = List(4) { idx ->
                    val item = fakeAppInfo(idx)
                    LibraryItem(
                        index = idx,
                        appId = "${GameSource.STEAM.name}_${item.id}",
                        name = item.name,
                        iconHash = item.iconHash,
                        isShared = idx % 2 == 0,
                        gameSource = GameSource.CUSTOM_GAME,
                    )
                }

                // Hero
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = 72.dp,
                    ),
                ) {
                    items(items = appInfoList, key = { "${it.gameSource}_${it.appId}" }) { item ->
                        // Show different compatibility states in preview
                        val status = when (item.index % 4) {
                            0 -> GameCompatibilityStatus.COMPATIBLE
                            1 -> GameCompatibilityStatus.GPU_COMPATIBLE
                            2 -> GameCompatibilityStatus.NOT_COMPATIBLE
                            else -> GameCompatibilityStatus.UNKNOWN
                        }
                        AppItem(
                            appInfo = item,
                            onClick = { },
                            paneType = PaneType.GRID_HERO,
                            compatibilityStatus = status,
                        )
                    }
                }

                // Capsule
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = 72.dp,
                    ),
                ) {
                    items(items = appInfoList, key = { "${it.gameSource}_${it.appId}" }) { item ->
                        // Show different compatibility states in preview
                        val status = when (item.index % 4) {
                            0 -> GameCompatibilityStatus.COMPATIBLE
                            1 -> GameCompatibilityStatus.GPU_COMPATIBLE
                            2 -> GameCompatibilityStatus.NOT_COMPATIBLE
                            else -> GameCompatibilityStatus.UNKNOWN
                        }
                        AppItem(
                            appInfo = item,
                            onClick = { },
                            paneType = PaneType.GRID_CAPSULE,
                            compatibilityStatus = status,
                        )
                    }
                }
            }
        }
    }
}
