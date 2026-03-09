package app.gamenative.ui.screen.achievements

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gamenative.Constants
import app.gamenative.R
import app.gamenative.data.SteamAchievement
import app.gamenative.ui.theme.PluviaTypography
import app.gamenative.ui.theme.gnAccentPrimary
import app.gamenative.ui.theme.gnBgDeepest
import app.gamenative.ui.theme.gnBgSurface
import app.gamenative.ui.theme.gnBorderCard
import app.gamenative.ui.theme.gnNeonTeal
import app.gamenative.ui.theme.gnTextPrimary
import app.gamenative.ui.theme.gnTextSecondary
import app.gamenative.ui.theme.gnTextTertiary
import app.gamenative.ui.model.AchievementsViewModel
import app.gamenative.utils.PaddingUtils
import app.gamenative.utils.getAvatarURL
import com.skydoves.landscapist.coil.CoilImage
import com.skydoves.landscapist.ImageOptions

private const val LOCKED_ICON_SATURATION_ALPHA = 0.35f

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
        val myAchievements by viewModel.myAchievements.collectAsStateWithLifecycle()
        var showCompare by remember { mutableStateOf(false) }
        var filter by remember { mutableStateOf(AchievementFilter.ALL) }
        val unlocked = achievements.count { it.unlocked }
        val total = achievements.size
        val friendMap = achievements.associateBy { it.apiName }
        val myMap = myAchievements.associateBy { it.apiName }
        val allNames = (friendMap.keys + myMap.keys).distinct()
        val showingCompare = showCompare && viewModel.isViewingFriend && allNames.isNotEmpty()
        val filteredAchievements = remember(achievements, filter) {
            when (filter) {
                AchievementFilter.ALL -> achievements
                AchievementFilter.UNLOCKED -> achievements.filter { it.unlocked }
                AchievementFilter.LOCKED -> achievements.filter { !it.unlocked }
            }
        }
        var selectedAchievementForDescription by remember { mutableStateOf<SteamAchievement?>(null) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            if (viewModel.isViewingFriend) {
                TextButton(onClick = { showCompare = !showCompare }) {
                    Text(
                        text = stringResource(R.string.friend_compare_achievements),
                        style = PluviaTypography.labelMedium,
                    )
                }
            }

            if (showingCompare) {
                CompareWithMeContent(
                    friendMap = friendMap,
                    myMap = myMap,
                    allNames = allNames,
                    onAchievementClick = { selectedAchievementForDescription = it },
                    modifier = Modifier.fillMaxHeight(),
                )
            } else if (achievements.isEmpty()) {
                AchievementsEmptyState(
                    onRetry = { viewModel.refresh() },
                    modifier = Modifier.fillMaxHeight(),
                )
            } else {
                ProgressCard(
                    unlocked = unlocked,
                    total = total,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        AchievementFilter.ALL to R.string.achievements_filter_all,
                        AchievementFilter.UNLOCKED to R.string.achievements_filter_unlocked,
                        AchievementFilter.LOCKED to R.string.achievements_filter_locked,
                    ).forEach { (f, resId) ->
                        FilterChip(
                            selected = filter == f,
                            onClick = { filter = f },
                            label = { Text(stringResource(resId)) },
                        )
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = 4.dp),
                ) {
                    items(filteredAchievements, key = { it.apiName }) { achievement ->
                        AchievementCard(
                            achievement = achievement,
                            onClick = { selectedAchievementForDescription = achievement },
                        )
                    }
                }
            }
        }
        selectedAchievementForDescription?.let { achievement ->
            AlertDialog(
                onDismissRequest = { selectedAchievementForDescription = null },
                title = { Text(achievement.name.ifBlank { achievement.apiName }, style = PluviaTypography.titleMedium, color = gnTextPrimary) },
                text = {
                    Text(
                        text = achievement.description.ifBlank { stringResource(R.string.achievements_no_description) },
                        style = PluviaTypography.bodyMedium,
                        color = gnTextSecondary,
                    )
                },
                confirmButton = {
                    TextButton(onClick = { selectedAchievementForDescription = null }) {
                        Text(stringResource(android.R.string.ok), color = gnAccentPrimary)
                    }
                },
            )
        }
    }
}

private enum class AchievementFilter { ALL, UNLOCKED, LOCKED }

@Composable
private fun ProgressCard(
    unlocked: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val progress = if (total > 0) unlocked.toFloat() / total else 0f
    val gradient = Brush.linearGradient(
        colors = listOf(gnAccentPrimary, gnNeonTeal),
    )
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(gnBgSurface)
            .border(1.dp, gnBorderCard, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(64.dp),
            ) {
                Canvas(modifier = Modifier.size(64.dp)) {
                    val strokeWidth = 6.dp.toPx()
                    drawArc(
                        color = gnTextTertiary.copy(alpha = 0.3f),
                        startAngle = 90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                        size = Size(size.minDimension - strokeWidth, size.minDimension - strokeWidth),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                    drawArc(
                        brush = gradient,
                        startAngle = 90f,
                        sweepAngle = -360f * progress,
                        useCenter = false,
                        topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                        size = Size(size.minDimension - strokeWidth, size.minDimension - strokeWidth),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = PluviaTypography.labelMedium,
                    color = gnTextPrimary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$unlocked / $total",
                    style = PluviaTypography.titleMedium,
                    color = gnTextPrimary,
                )
                Text(
                    text = stringResource(R.string.achievements_progress_suffix).uppercase(),
                    style = PluviaTypography.labelSmall,
                    color = gnTextTertiary,
                )
            }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .padding(top = 12.dp),
            color = gnAccentPrimary,
            trackColor = gnTextTertiary.copy(alpha = 0.3f),
        )
    }
}

@Composable
private fun AchievementsEmptyState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = gnTextTertiary,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.achievements_empty_title),
                    style = PluviaTypography.titleMedium,
                    color = gnTextPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.achievements_empty_subtitle),
                    style = PluviaTypography.bodySmall,
                    color = gnTextSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                    textAlign = TextAlign.Center,
                )
            }
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.achievements_retry))
            }
        }
    }
}

@Composable
private fun CompareWithMeContent(
    friendMap: Map<String, SteamAchievement>,
    myMap: Map<String, SteamAchievement>,
    allNames: List<String>,
    onAchievementClick: (SteamAchievement) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val friendCount = friendMap.values.count { it.unlocked }
    val myCount = myMap.values.count { it.unlocked }
    val total = allNames.size
    val friendProgress = if (total > 0) friendCount.toFloat() / total else 0f
    val myProgress = if (total > 0) myCount.toFloat() / total else 0f
    val myAvatarUrl = remember { app.gamenative.PrefManager.steamUserAvatarHash.getAvatarURL() }

    Column(modifier = modifier) {
        CompareHeaderCard(
            myAvatarUrl = myAvatarUrl,
            friendAvatarUrl = Constants.Persona.MISSING_AVATAR_URL,
            myCount = myCount,
            friendCount = friendCount,
            total = total,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth(0.5f)) {
                Text(
                    text = stringResource(R.string.achievements_compare_you),
                    style = PluviaTypography.labelSmall,
                    color = gnTextTertiary,
                )
                LinearProgressIndicator(
                    progress = { myProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .padding(top = 4.dp),
                    color = gnAccentPrimary,
                    trackColor = gnTextTertiary.copy(alpha = 0.3f),
                )
            }
            Column(modifier = Modifier.fillMaxWidth(0.5f)) {
                Text(
                    text = stringResource(R.string.achievements_compare_friend),
                    style = PluviaTypography.labelSmall,
                    color = gnTextTertiary,
                )
                LinearProgressIndicator(
                    progress = { friendProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .padding(top = 4.dp),
                    color = gnTextTertiary,
                    trackColor = gnTextTertiary.copy(alpha = 0.2f),
                )
            }
        }
        Text(
            text = stringResource(R.string.achievements_compare_legend),
            style = PluviaTypography.labelSmall,
            color = gnTextTertiary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(allNames) { apiName ->
                val achievement = friendMap[apiName] ?: myMap[apiName]
                val friendUnlocked = friendMap[apiName]?.unlocked == true
                val myUnlocked = myMap[apiName]?.unlocked == true
                val displayName = friendMap[apiName]?.name ?: myMap[apiName]?.name ?: apiName
                val iLead = myUnlocked && !friendUnlocked
                CompareRow(
                    displayName = displayName,
                    friendUnlocked = friendUnlocked,
                    myUnlocked = myUnlocked,
                    iLead = iLead,
                    achievement = achievement,
                    onAchievementClick = { achievement?.let(onAchievementClick) },
                )
            }
        }
    }
}

@Composable
private fun CompareHeaderCard(
    myAvatarUrl: String,
    friendAvatarUrl: String,
    myCount: Int,
    friendCount: Int,
    total: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(gnBgSurface)
            .border(1.dp, gnBorderCard, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CoilImage(
                imageModel = { friendAvatarUrl },
                imageOptions = ImageOptions(
                    contentScale = ContentScale.Crop,
                    contentDescription = stringResource(R.string.achievements_compare_friend),
                ),
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
            )
            Text(
                text = stringResource(R.string.achievements_compare_friend),
                style = PluviaTypography.labelSmall,
                color = gnTextTertiary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = "$friendCount / $total",
                style = PluviaTypography.labelMedium,
                color = gnTextPrimary,
            )
        }
        Text(
            text = "VS",
            style = PluviaTypography.titleMedium,
            color = gnTextTertiary,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CoilImage(
                imageModel = { myAvatarUrl },
                imageOptions = ImageOptions(
                    contentScale = ContentScale.Crop,
                    contentDescription = stringResource(R.string.achievements_compare_you),
                ),
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
            )
            Text(
                text = stringResource(R.string.achievements_compare_you),
                style = PluviaTypography.labelSmall,
                color = gnTextTertiary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = "$myCount / $total",
                style = PluviaTypography.labelMedium,
                color = gnTextPrimary,
            )
        }
    }
}

@Composable
private fun CompareRow(
    displayName: String,
    friendUnlocked: Boolean,
    myUnlocked: Boolean,
    iLead: Boolean,
    achievement: SteamAchievement? = null,
    onAchievementClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (iLead) gnAccentPrimary.copy(alpha = 0.12f)
                else gnBgSurface
            )
            .border(1.dp, gnBorderCard, RoundedCornerShape(8.dp))
            .clickable(onClick = onAchievementClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = displayName,
            style = PluviaTypography.bodySmall,
            color = gnTextPrimary,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompareStatusIcon(unlocked = friendUnlocked, isMine = false)
            CompareStatusIcon(unlocked = myUnlocked, isMine = true)
        }
    }
}

@Composable
private fun CompareStatusIcon(unlocked: Boolean, isMine: Boolean) {
    val tint = when {
        unlocked && isMine -> gnAccentPrimary
        unlocked && !isMine -> gnNeonTeal
        else -> gnTextTertiary
    }
    Icon(
        imageVector = if (unlocked) Icons.Default.CheckCircle else Icons.Default.Lock,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = tint,
    )
}

@Composable
private fun AchievementCard(
    achievement: SteamAchievement,
    onClick: () -> Unit = {},
) {
    val iconUrl = if (achievement.unlocked) achievement.iconUrl else achievement.iconGrayUrl
    val url = if (iconUrl.startsWith("http")) iconUrl else {
        "https://cdn.cloudflare.steamstatic.com/steamcommunity/public/images/apps/0/$iconUrl.jpg"
    }
    val lockedColorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(gnBgSurface)
            .border(1.dp, gnBorderCard, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        if (achievement.unlocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(gnNeonTeal, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)),
            )
        }
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .then(if (!achievement.unlocked) Modifier.alpha(LOCKED_ICON_SATURATION_ALPHA) else Modifier)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            CoilImage(
                imageModel = { url },
                imageOptions = ImageOptions(
                    contentScale = ContentScale.Crop,
                    contentDescription = achievement.name,
                    colorFilter = if (achievement.unlocked) null else lockedColorFilter,
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
            if (achievement.unlocked) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .padding(4.dp),
                    tint = gnNeonTeal,
                )
            }
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
}
