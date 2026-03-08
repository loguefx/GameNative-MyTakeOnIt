package app.gamenative.ui.screen.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import app.gamenative.Constants
import app.gamenative.R
import app.gamenative.service.SteamService
import app.gamenative.ui.theme.gnAccentPrimary
import app.gamenative.ui.theme.gnBgDeepest
import app.gamenative.ui.theme.gnBgSurface
import app.gamenative.ui.theme.gnBorderCard
import app.gamenative.ui.theme.gnTextPrimary
import app.gamenative.ui.theme.gnTextSecondary
import app.gamenative.ui.theme.gnTextTertiary
import app.gamenative.ui.theme.PluviaTypography
import com.skydoves.landscapist.coil.CoilImage
import com.skydoves.landscapist.ImageOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DownloadsScreen(
    downloadingAppIds: List<Int>,
) {
    val context = LocalContext.current
    var names by remember(downloadingAppIds) { mutableStateOf<Map<Int, String>>(emptyMap()) }

    LaunchedEffect(downloadingAppIds) {
        if (downloadingAppIds.isEmpty()) {
            names = emptyMap()
            return@LaunchedEffect
        }
        names = withContext(Dispatchers.IO) {
            downloadingAppIds.associateWith { id ->
                SteamService.getAppInfoOf(id)?.name ?: "App $id"
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gnBgDeepest),
    ) {
        if (downloadingAppIds.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.destination_downloads),
                    style = PluviaTypography.bodyLarge,
                    color = gnTextSecondary,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.destination_downloads),
                    style = PluviaTypography.headlineSmall,
                    color = gnTextPrimary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                Text(
                    text = "${downloadingAppIds.size} downloading",
                    style = PluviaTypography.labelMedium,
                    color = gnTextTertiary,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(downloadingAppIds, key = { it }) { appId ->
                        DownloadingGameRow(
                            appId = appId,
                            name = names[appId] ?: "App $appId",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadingGameRow(
    appId: Int,
    name: String,
) {
    val progress = SteamService.getAppDownloadInfo(appId)?.getProgress()?.coerceIn(0f, 1f) ?: 0f
    val capsuleUrl = Constants.Library.steamCapsuleUrl(appId)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = gnBgSurface),
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(gnBorderCard, gnBorderCard))),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoilImage(
                imageModel = { capsuleUrl },
                imageOptions = ImageOptions(contentScale = ContentScale.Crop),
                modifier = Modifier
                    .size(width = 80.dp, height = 107.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = name,
                    style = PluviaTypography.titleMedium,
                    color = gnTextPrimary,
                    maxLines = 2,
                )
                Text(
                    text = "Installing…",
                    style = PluviaTypography.bodySmall,
                    color = gnTextSecondary,
                )
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = gnAccentPrimary,
                    trackColor = gnTextTertiary.copy(alpha = 0.3f),
                )
            }
        }
    }
}
