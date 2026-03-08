package app.gamenative.ui.screen.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gamenative.R
import app.gamenative.data.SteamFriend
import app.gamenative.ui.theme.gnBgDeepest
import app.gamenative.ui.theme.gnBgSurface
import app.gamenative.ui.theme.gnBorderCard
import app.gamenative.ui.theme.gnNeonTeal
import app.gamenative.ui.theme.gnStatusDownloading
import app.gamenative.ui.theme.gnTextPrimary
import app.gamenative.ui.theme.gnTextSecondary
import app.gamenative.ui.theme.gnTextTertiary
import app.gamenative.ui.theme.PluviaTypography
import app.gamenative.ui.util.SteamIconImage
import app.gamenative.ui.model.FriendsViewModel
import app.gamenative.utils.getAvatarURL
import app.gamenative.utils.PaddingUtils

@Composable
fun FriendsScreen(
    viewModel: FriendsViewModel = hiltViewModel(),
    onFriendClick: (Long) -> Unit,
) {
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val topPadding = PaddingUtils.statusBarAwarePadding().calculateTopPadding()

    Scaffold(
        containerColor = gnBgDeepest,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(gnBgDeepest)
                .padding(paddingValues)
                .padding(top = topPadding)
        ) {
            Text(
                text = stringResource(R.string.destination_friends),
                style = PluviaTypography.headlineSmall,
                color = gnTextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Text(
                text = stringResource(R.string.friends_count, friends.size).uppercase(),
                style = PluviaTypography.labelMedium,
                color = gnTextTertiary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            val onlineFriends = friends.filter { it.isOnline }
            val offlineFriends = friends.filter { !it.isOnline }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onlineFriends.isNotEmpty()) {
                    item(key = "header_online") {
                        FriendsSectionHeader(
                            title = stringResource(R.string.online),
                            count = onlineFriends.size,
                        )
                    }
                    items(
                        items = onlineFriends,
                        key = { it.steamId },
                    ) { friend ->
                        FriendRow(
                            friend = friend,
                            onClick = { onFriendClick(friend.steamId) },
                        )
                    }
                }
                if (offlineFriends.isNotEmpty()) {
                    item(key = "header_offline") {
                        FriendsSectionHeader(
                            title = stringResource(R.string.offline),
                            count = offlineFriends.size,
                        )
                    }
                    items(
                        items = offlineFriends,
                        key = { it.steamId },
                    ) { friend ->
                        FriendRow(
                            friend = friend,
                            onClick = { onFriendClick(friend.steamId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendsSectionHeader(
    title: String,
    count: Int,
) {
    Text(
        text = "$title ($count)",
        style = PluviaTypography.labelMedium,
        color = gnTextTertiary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun FriendRow(
    friend: SteamFriend,
    onClick: () -> Unit,
) {
    val presenceColor = when {
        friend.isPlayingGame -> gnStatusDownloading
        friend.isOnline -> gnNeonTeal
        else -> gnTextTertiary
    }
    val statusText = when {
        friend.isPlayingGame -> friend.gameName.ifBlank { stringResource(R.string.in_game) }
        friend.isOnline -> stringResource(R.string.online)
        else -> stringResource(R.string.offline)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(gnBgSurface)
            .border(1.dp, gnBorderCard, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SteamIconImage(
            modifier = Modifier.size(48.dp),
            contentDescription = null,
            size = 48.dp,
            image = { friend.avatarHash.getAvatarURL() },
        )
        Row(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = friend.name.ifBlank { " " },
                    style = PluviaTypography.titleMedium,
                    color = gnTextPrimary,
                )
                Text(
                    text = statusText,
                    style = PluviaTypography.bodySmall,
                    color = gnTextSecondary,
                )
            }
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(presenceColor),
            )
        }
    }
}
