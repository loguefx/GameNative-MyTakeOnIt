package app.gamenative.ui.screen.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gamenative.R
import app.gamenative.data.SteamAchievement
import app.gamenative.ui.theme.PluviaTypography
import app.gamenative.ui.theme.gnBgDeepest
import app.gamenative.ui.theme.gnBgSurface
import app.gamenative.ui.theme.gnBorderCard
import app.gamenative.ui.theme.gnTextPrimary
import app.gamenative.ui.theme.gnTextSecondary
import app.gamenative.ui.theme.gnTextTertiary
import app.gamenative.ui.model.AchievementsViewModel
import app.gamenative.utils.PaddingUtils
import com.skydoves.landscapist.coil.CoilImage
import com.skydoves.landscapist.ImageOptions

@Composable
fun AchievementsScreen(
    viewModel: AchievementsViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val achievements by viewModel.achievements.collectAsStateWithLifecycle()
    val topPadding = PaddingUtils.statusBarAwarePadding().calculateTopPadding()

    Scaffold(
        containerColor = gnBgDeepest,
        topBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = topPadding)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cancel),
                    )
                }
                Text(
                    text = stringResource(R.string.achievements_title),
                    style = PluviaTypography.titleMedium,
                    color = gnTextPrimary,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        },
    ) { paddingValues ->
        val unlocked = achievements.count { it.unlocked }
        val total = achievements.size
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "$unlocked / $total " + stringResource(R.string.achievements_progress_suffix).uppercase(),
                style = PluviaTypography.labelMedium,
                color = gnTextTertiary,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            if (achievements.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    items(achievements, key = { it.apiName }) { achievement ->
                        AchievementCard(achievement = achievement)
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementCard(
    achievement: SteamAchievement,
) {
    val iconUrl = if (achievement.unlocked) achievement.iconUrl else achievement.iconGrayUrl
    val url = if (iconUrl.startsWith("http")) iconUrl else {
        // Some responses return only the hash
        "https://cdn.cloudflare.steamstatic.com/steamcommunity/public/images/apps/0/$iconUrl.jpg"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(gnBgSurface)
            .border(1.dp, gnBorderCard, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            CoilImage(
                imageModel = { url },
                imageOptions = ImageOptions(
                    contentScale = ContentScale.Crop,
                    contentDescription = achievement.name,
                    colorFilter = if (achievement.unlocked) null else ColorFilter.grayscale(),
                ),
                modifier = Modifier.fillMaxSize(),
                failure = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(gnBgDeepest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "?",
                            style = PluviaTypography.titleMedium,
                            color = gnTextTertiary,
                        )
                    }
                },
            )
        }
        Text(
            text = achievement.name.ifBlank { " " },
            style = PluviaTypography.labelMedium,
            color = gnTextPrimary,
            modifier = Modifier.padding(top = 6.dp),
            maxLines = 2,
        )
        Text(
            text = achievement.description.ifBlank { " " },
            style = PluviaTypography.bodySmall,
            color = gnTextSecondary,
            maxLines = 2,
        )
    }
}
