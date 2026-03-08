package app.gamenative.ui.screen.chat

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gamenative.Constants
import app.gamenative.R
import app.gamenative.data.FriendGame
import app.gamenative.data.FriendMessage
import app.gamenative.data.OwnedGames
import app.gamenative.data.PresenceState
import app.gamenative.data.SteamFriend
import app.gamenative.data.toPresenceState
import app.gamenative.service.SteamService
import app.gamenative.ui.screen.PluviaScreen
import app.gamenative.ui.theme.gnAccentGlow
import app.gamenative.ui.theme.gnAccentPrimary
import app.gamenative.ui.theme.gnAccentSubtle
import app.gamenative.ui.theme.gnBgDeepest
import app.gamenative.ui.theme.gnBgElevated
import app.gamenative.ui.theme.gnBgSurface
import app.gamenative.ui.theme.gnBorderCard
import app.gamenative.ui.theme.gnDivider
import app.gamenative.ui.theme.gnNeonOrange
import app.gamenative.ui.theme.gnNeonTeal
import app.gamenative.ui.theme.gnStatusError
import app.gamenative.ui.theme.gnStatusDownloading
import app.gamenative.ui.theme.gnTextPrimary
import app.gamenative.ui.theme.gnTextSecondary
import app.gamenative.ui.theme.gnTextTertiary
import app.gamenative.ui.theme.PluviaTypography
import app.gamenative.ui.model.FriendDetailViewModel
import app.gamenative.ui.model.SORT_A_Z
import app.gamenative.ui.model.SORT_MOST_PLAYED
import app.gamenative.ui.model.SORT_RECENT
import app.gamenative.ui.util.SteamIconImage
import app.gamenative.utils.getAvatarURL
import app.gamenative.utils.PaddingUtils
import app.gamenative.utils.SteamUtils
import app.gamenative.utils.getProfileUrl
import kotlinx.coroutines.launch
import com.skydoves.landscapist.coil.CoilImage
import com.skydoves.landscapist.ImageOptions

/**
 * In-app friend profile and/or chat.
 * When [isProfileOnly] is true (friend tap from list): hero + actions + games → Message opens chat.
 * When false (from Message button): profile + games + message list + input.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendDetailScreen(
    steamId: Long,
    isProfileOnly: Boolean = false,
    isLocalUserInGame: Boolean = false,
    onBack: () -> Unit,
    onNavigateRoute: (String) -> Unit = {},
    onNavigateToChat: () -> Unit = {},
    viewModel: FriendDetailViewModel = hiltViewModel(),
) {
    val friends by SteamService.friendsList.collectAsStateWithLifecycle(initialValue = emptyList())
    val friend = friends.firstOrNull { it.steamId == steamId }
    val ownedGames by viewModel.ownedGames.collectAsStateWithLifecycle()
    val sortedGames by viewModel.sortedGames.collectAsStateWithLifecycle()
    val isLoadingGames by viewModel.isLoadingGames.collectAsStateWithLifecycle()
    val selectedSort by viewModel.selectedSort.collectAsStateWithLifecycle()
    val messages by viewModel.messages(steamId).collectAsStateWithLifecycle(initialValue = emptyList())
    var messageText by remember { mutableStateOf("") }
    var showMoreSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

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

            if (isProfileOnly) {
                // ——— Player Profile (full replacement) ———
                val showShimmer = friend == null || isLoadingGames
                if (showShimmer) {
                    ProfileShimmer()
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState)
                    ) {
                        val presenceState = friend.toPresenceState()
                        val currentGameHeaderUrl = if (friend.gameAppID > 0)
                            Constants.Library.steamHeaderUrl(friend.gameAppID) else null
                        ProfileHeroSection(
                            displayName = friend.name.ifBlank { stringResource(R.string.friend_unknown) },
                            avatarUrl = friend.avatarHash.getAvatarURL(),
                            presenceState = presenceState,
                            currentGameName = friend.gameName.takeIf { it.isNotBlank() },
                            currentGameHeaderUrl = currentGameHeaderUrl,
                            statusMessage = null,
                        )
                        ProfileActionRow(
                            onNavigateToChat = onNavigateToChat,
                            onSendInvite = { /* TODO: invite when in game */ },
                            isLocalUserInGame = isLocalUserInGame,
                            onMoreClick = { showMoreSheet = true },
                        )
                        if (presenceState == PresenceState.IN_GAME && currentGameHeaderUrl != null) {
                            ProfileCurrentlyPlayingCard(
                                gameName = friend.gameName.ifBlank { stringResource(R.string.in_game) },
                                gameHeaderUrl = currentGameHeaderUrl,
                                sessionDuration = stringResource(R.string.in_game),
                            )
                        }
                        ProfileGamesSection(
                            games = sortedGames,
                            selectedSort = selectedSort,
                            onSortChange = viewModel::setSort,
                            friendSteamId = steamId,
                            onNavigateRoute = onNavigateRoute,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
                if (showMoreSheet && friend != null) {
                    ModalBottomSheet(
                        onDismissRequest = { showMoreSheet = false },
                        containerColor = gnBgSurface,
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        uriHandler.openUri(steamId.getProfileUrl())
                                        showMoreSheet = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.Filled.OpenInBrowser, null, tint = gnTextSecondary)
                                Text("View on Steam", style = PluviaTypography.bodyLarge, color = gnTextPrimary)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val firstAppId = sortedGames.firstOrNull()?.appId
                                        if (firstAppId != null) onNavigateRoute(PluviaScreen.Achievements.route(firstAppId.toString(), steamId))
                                        showMoreSheet = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.Filled.EmojiEvents, null, tint = gnTextSecondary)
                                Text(stringResource(R.string.friend_compare_achievements), style = PluviaTypography.bodyLarge, color = gnTextPrimary)
                            }
                            Divider(color = gnDivider, modifier = Modifier.padding(vertical = 8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showMoreSheet = false }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.Filled.Block, null, tint = gnStatusError)
                                Text("Block", style = PluviaTypography.bodyLarge, color = gnStatusError)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showMoreSheet = false }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.Filled.PersonRemove, null, tint = gnStatusError)
                                Text("Remove friend", style = PluviaTypography.bodyLarge, color = gnStatusError)
                            }
                        }
                        Spacer(modifier = Modifier.navigationBarsPadding())
                    }
                }
            } else {
                // ——— Chat mode (profile + messages + input) ———
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
}

// ——— Player profile (isProfileOnly) composables ———

@Composable
private fun ProfileShimmer() {
    val infiniteTransition = rememberInfiniteTransition(label = "profileShimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    val skeletonColor = gnBgElevated.copy(alpha = alpha)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(skeletonColor)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(skeletonColor)
        )
        repeat(6) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(61.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(skeletonColor)
            )
        }
    }
}

@Composable
private fun ProfileHeroSection(
    displayName: String,
    avatarUrl: String,
    presenceState: PresenceState,
    currentGameName: String?,
    currentGameHeaderUrl: String?,
    statusMessage: String?,
) {
    val ringColor = when (presenceState) {
        PresenceState.ONLINE -> gnNeonTeal
        PresenceState.IN_GAME -> gnAccentPrimary
        PresenceState.AWAY -> gnNeonOrange
        PresenceState.OFFLINE -> gnTextTertiary
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
    ) {
        if (currentGameHeaderUrl != null) {
            CoilImage(
                imageModel = { currentGameHeaderUrl },
                imageOptions = ImageOptions(contentScale = ContentScale.Crop),
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                gnBgDeepest.copy(alpha = 0.55f),
                                gnBgDeepest,
                            )
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(gnBgDeepest)
            ) {
                Canvas(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .size(200.dp)
                ) {
                    drawCircle(
                        color = gnAccentPrimary.copy(alpha = 0.07f),
                        radius = size.minDimension / 1.2f
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                CoilImage(
                    imageModel = { avatarUrl },
                    imageOptions = ImageOptions(contentScale = ContentScale.Crop),
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .border(3.dp, ringColor, CircleShape),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .background(gnBgDeepest, CircleShape)
                        .padding(2.dp)
                        .background(ringColor, CircleShape)
                )
            }
            Text(
                text = displayName,
                style = PluviaTypography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                ),
                color = gnTextPrimary,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when (presenceState) {
                    PresenceState.IN_GAME -> {
                        Icon(
                            imageVector = Icons.Filled.SportsEsports,
                            contentDescription = null,
                            tint = gnAccentGlow,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "Playing ${currentGameName ?: "a game"}",
                            style = PluviaTypography.bodySmall,
                            color = gnAccentGlow,
                        )
                    }
                    PresenceState.ONLINE -> Text("Online", style = PluviaTypography.bodySmall, color = gnNeonTeal)
                    PresenceState.AWAY -> Text("Away", style = PluviaTypography.bodySmall, color = gnNeonOrange)
                    PresenceState.OFFLINE -> Text("Offline", style = PluviaTypography.bodySmall, color = gnTextTertiary)
                }
            }
            if (!statusMessage.isNullOrBlank()) {
                Text(
                    text = "\"$statusMessage\"",
                    style = PluviaTypography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = gnTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun ProfileActionRow(
    onNavigateToChat: () -> Unit,
    onSendInvite: () -> Unit,
    isLocalUserInGame: Boolean,
    onMoreClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Button(
            onClick = onNavigateToChat,
            modifier = Modifier.weight(1f).height(44.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = gnAccentPrimary,
                contentColor = Color.White,
            ),
            shape = RoundedCornerShape(10.dp),
        ) {
            Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.friend_message_section), style = PluviaTypography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(
            onClick = onSendInvite,
            enabled = isLocalUserInGame,
            modifier = Modifier.weight(1f).height(44.dp),
            border = BorderStroke(1.dp, Brush.linearGradient(listOf(if (isLocalUserInGame) gnAccentPrimary else gnTextTertiary, if (isLocalUserInGame) gnAccentPrimary else gnTextTertiary))),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (isLocalUserInGame) gnAccentPrimary else gnTextTertiary,
            ),
        ) {
            Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Invite", style = PluviaTypography.labelLarge)
        }
        OutlinedIconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(44.dp),
            border = BorderStroke(1.dp, Brush.linearGradient(listOf(gnBorderCard, gnBorderCard))),
            shape = RoundedCornerShape(10.dp),
            colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = gnTextSecondary),
        ) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
        }
    }
}

@Composable
private fun ProfileCurrentlyPlayingCard(
    gameName: String,
    gameHeaderUrl: String,
    sessionDuration: String,
) {
    val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = gnBgSurface),
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(gnBorderCard, gnBorderCard))),
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .alpha(pulseAlpha)
                        .background(gnNeonTeal, CircleShape),
                )
                Text(
                    text = "NOW PLAYING",
                    style = PluviaTypography.labelSmall.copy(letterSpacing = 0.10.sp),
                    color = gnNeonTeal,
                )
            }
            CoilImage(
                imageModel = { gameHeaderUrl },
                imageOptions = ImageOptions(contentScale = ContentScale.Crop),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = gameName,
                    style = PluviaTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = gnTextPrimary,
                )
                Text(
                    text = sessionDuration,
                    style = PluviaTypography.bodySmall,
                    color = gnTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun ProfileGamesSection(
    games: List<FriendGame>,
    selectedSort: String,
    onSortChange: (String) -> Unit,
    friendSteamId: Long,
    onNavigateRoute: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "GAMES",
            style = PluviaTypography.labelMedium.copy(letterSpacing = 0.10.sp),
            color = gnTextTertiary,
        )
        Text(
            text = "${games.size} games",
            style = PluviaTypography.bodySmall,
            color = gnTextSecondary,
        )
    }
    val sortOptions = listOf(SORT_MOST_PLAYED, SORT_RECENT, SORT_A_Z)
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sortOptions.forEach { option ->
            FilterChip(
                selected = selectedSort == option,
                onClick = { onSortChange(option) },
                label = { Text(option, style = PluviaTypography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = gnAccentSubtle,
                    selectedLabelColor = gnAccentGlow,
                    containerColor = gnBgElevated,
                    labelColor = gnTextSecondary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedSort == option,
                    borderColor = gnBorderCard,
                    selectedBorderColor = gnAccentPrimary.copy(alpha = 0.4f),
                ),
                shape = RoundedCornerShape(20.dp),
            )
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
    ) {
        items(games, key = { it.appId }) { game ->
            ProfileGameRowItem(
                game = game,
                onViewAchievements = {
                    onNavigateRoute(PluviaScreen.Achievements.route(game.appId.toString(), friendSteamId))
                },
            )
        }
    }
}

@Composable
private fun ProfileGameRowItem(
    game: FriendGame,
    onViewAchievements: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onViewAchievements),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = gnBgSurface),
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(listOf(
                if (game.playtime2Weeks > 0) gnAccentPrimary.copy(alpha = 0.3f) else gnBorderCard,
                if (game.playtime2Weeks > 0) gnAccentPrimary.copy(alpha = 0.3f) else gnBorderCard,
            )),
        ),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CoilImage(
                imageModel = { game.headerUrl },
                imageOptions = ImageOptions(contentScale = ContentScale.Crop),
                modifier = Modifier
                    .width(88.dp)
                    .height(41.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = game.name,
                    style = PluviaTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = gnTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (game.lastPlayed != null)
                        "Last played ${SteamUtils.formatRelativeTime(game.lastPlayed)}"
                    else "Never played",
                    style = PluviaTypography.bodySmall,
                    color = gnTextSecondary,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val totalHours = game.playtimeForever / 60f
                Text(
                    text = if (totalHours < 1f) "${game.playtimeForever}m"
                    else "%.1f hrs".format(totalHours),
                    style = PluviaTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (game.playtime2Weeks > 0) gnAccentGlow else gnTextPrimary,
                )
                if (game.playtime2Weeks > 0) {
                    Text(
                        text = "%.1fh recently".format(game.playtime2Weeks / 60f),
                        style = PluviaTypography.labelSmall,
                        color = gnNeonTeal,
                    )
                } else {
                    Text("total", style = PluviaTypography.labelSmall, color = gnTextTertiary)
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
