package app.gamenative.ui.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gamenative.Constants
import app.gamenative.R
import app.gamenative.data.FriendMessage
import app.gamenative.data.OwnedGames
import app.gamenative.data.SteamFriend
import app.gamenative.service.SteamService
import app.gamenative.ui.screen.PluviaScreen
import app.gamenative.ui.theme.gnBgDeepest
import app.gamenative.ui.theme.gnBgSurface
import app.gamenative.ui.theme.gnBorderCard
import app.gamenative.ui.theme.gnNeonTeal
import app.gamenative.ui.theme.gnStatusDownloading
import app.gamenative.ui.theme.gnTextPrimary
import app.gamenative.ui.theme.gnTextSecondary
import app.gamenative.ui.theme.gnTextTertiary
import app.gamenative.ui.theme.PluviaTypography
import app.gamenative.ui.model.FriendDetailViewModel
import app.gamenative.ui.util.SteamIconImage
import app.gamenative.utils.getAvatarURL
import app.gamenative.utils.PaddingUtils
import app.gamenative.utils.SteamUtils
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import com.skydoves.landscapist.coil.CoilImage
import com.skydoves.landscapist.ImageOptions

/**
 * In-app friend profile: avatar, name, status, currently playing, games (recently played + by hours),
 * link to view/compare achievements, and chat with message history and send.
 */
@Composable
fun FriendDetailScreen(
    steamId: Long,
    onBack: () -> Unit,
    onNavigateRoute: (String) -> Unit = {},
    viewModel: FriendDetailViewModel = hiltViewModel(),
) {
    val friends by SteamService.friendsList.collectAsStateWithLifecycle(initialValue = emptyList())
    val friend = friends.firstOrNull { it.steamId == steamId }
    val ownedGames by viewModel.ownedGames.collectAsStateWithLifecycle()
    val messages by viewModel.messages(steamId).collectAsStateWithLifecycle(initialValue = emptyList())
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(steamId) {
        if (friend == null) SteamService.requestFriendInfo(steamId)
    }

    LaunchedEffect(friend) {
        if (friend != null) viewModel.loadOwnedGames(steamId)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    val topPadding = PaddingUtils.statusBarAwarePadding().calculateTopPadding()
    val density = LocalDensity.current
    val scrollToGamesPx = with(density) { 200.dp.roundToPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gnBgDeepest),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topPadding, start = 8.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cancel),
                    )
                }
                Text(
                    text = friend?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.friend_unknown),
                    style = MaterialTheme.typography.titleMedium,
                    color = gnTextPrimary,
                )
            }

            if (friend == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.friend_loading),
                        style = PluviaTypography.bodyMedium,
                        color = gnTextSecondary,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 420.dp)
                            .verticalScroll(scrollState),
                    ) {
                        ProfileSection(
                            friend = friend,
                            onClick = {
                                scope.launch {
                                    val target = (scrollState.value + scrollToGamesPx).coerceAtMost(scrollState.maxValue)
                                    scrollState.animateScrollTo(target)
                                }
                            },
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        GamesSection(
                            games = ownedGames,
                            friendSteamId = steamId,
                            onNavigateRoute = onNavigateRoute,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.friend_message_section),
                        style = PluviaTypography.labelMedium,
                        color = gnTextTertiary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(gnBgSurface)
                            .border(1.dp, gnBorderCard, RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(messages.size, key = { messages[it].id }) { idx ->
                            val msg = messages[idx]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (msg.fromLocal) Arrangement.End else Arrangement.Start,
                            ) {
                                Text(
                                    text = msg.message,
                                    style = PluviaTypography.bodyMedium,
                                    color = if (msg.fromLocal) gnTextPrimary else gnTextSecondary,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.friend_message_hint)) },
                            singleLine = true,
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Button(
                            onClick = {
                                val text = messageText.trim()
                                if (text.isEmpty()) return@Button
                                messageText = ""
                                viewModel.sendMessage(steamId, text)
                            },
                        ) {
                            Text(stringResource(R.string.friend_send))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSection(
    friend: SteamFriend,
    onClick: () -> Unit = {},
) {
    val statusText = when {
        friend.isPlayingGame -> friend.gameName.ifBlank { stringResource(R.string.in_game) }
        friend.isOnline -> stringResource(R.string.online)
        else -> stringResource(R.string.offline)
    }
    val presenceColor = when {
        friend.isPlayingGame -> gnStatusDownloading
        friend.isOnline -> gnNeonTeal
        else -> gnTextTertiary
    }

    Text(
        text = stringResource(R.string.friend_profile),
        style = PluviaTypography.labelMedium,
        color = gnTextTertiary,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(gnBgSurface)
            .border(1.dp, gnBorderCard, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SteamIconImage(
                modifier = Modifier.size(56.dp),
                contentDescription = null,
                size = 56.dp,
                image = { friend.avatarHash.getAvatarURL() },
            )
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = friend.name.ifBlank { stringResource(R.string.friend_unknown) },
                    style = PluviaTypography.titleMedium,
                    color = gnTextPrimary,
                )
                Text(
                    text = statusText,
                    style = PluviaTypography.bodySmall,
                    color = gnTextSecondary,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(presenceColor),
            )
        }
        if (friend.isPlayingGame && friend.gameName.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.friend_currently_playing) + ": " + friend.gameName,
                style = PluviaTypography.bodySmall,
                color = gnTextTertiary,
            )
        }
    }
}

@Composable
private fun GamesSection(
    games: List<OwnedGames>,
    friendSteamId: Long,
    onNavigateRoute: (String) -> Unit,
) {
    Text(
        text = stringResource(R.string.friend_games),
        style = PluviaTypography.labelMedium,
        color = gnTextTertiary,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.friend_recently_played),
        style = PluviaTypography.labelSmall,
        color = gnTextTertiary,
    )
    Spacer(modifier = Modifier.height(8.dp))
    val maxGames = 20
    val list = games.take(maxGames)
    if (list.isEmpty()) {
        Text(
            text = stringResource(R.string.friend_loading),
            style = PluviaTypography.bodySmall,
            color = gnTextSecondary,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            list.forEach { game ->
                GameRow(
                    game = game,
                    onViewAchievements = { onNavigateRoute(PluviaScreen.Achievements.route(game.appId.toString(), friendSteamId)) },
                )
            }
        }
    }
}

@Composable
private fun GameRow(
    game: OwnedGames,
    onViewAchievements: () -> Unit,
) {
    val iconUrl = if (game.imgIconUrl.isNotBlank()) {
        "${Constants.Library.ICON_URL}${game.appId}/${game.imgIconUrl}.jpg"
    } else null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(gnBgSurface)
            .border(1.dp, gnBorderCard, RoundedCornerShape(8.dp))
            .clickable(onClick = onViewAchievements)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconUrl != null) {
            CoilImage(
                imageModel = { iconUrl },
                imageOptions = ImageOptions(contentScale = ContentScale.Crop),
                modifier = Modifier.size(40.dp),
            )
            Spacer(modifier = Modifier.size(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = game.name.ifBlank { "App ${game.appId}" },
                style = PluviaTypography.titleSmall,
                color = gnTextPrimary,
            )
            Text(
                text = stringResource(R.string.friend_hours, SteamUtils.formatPlayTime(game.playtimeForever)),
                style = PluviaTypography.bodySmall,
                color = gnTextSecondary,
            )
        }
        OutlinedButton(
            onClick = onViewAchievements,
            modifier = Modifier.padding(start = 8.dp),
        ) {
            Text(stringResource(R.string.friend_view_achievements), style = PluviaTypography.labelMedium)
        }
    }
}
