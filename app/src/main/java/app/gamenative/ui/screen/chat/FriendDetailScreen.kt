package app.gamenative.ui.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gamenative.R
import app.gamenative.data.SteamFriend
import app.gamenative.service.SteamService
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
import app.gamenative.utils.getAvatarURL
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon

/**
 * In-app friend profile and message screen. Shows profile (avatar, name, status) and a message
 * input. Sending uses SteamService when the SteamKit chat API is available.
 */
@Composable
fun FriendDetailScreen(
    steamId: Long,
    onBack: () -> Unit,
) {
    val friends by SteamService.friendsList.collectAsStateWithLifecycle(initialValue = emptyList())
    val friend = friends.firstOrNull { it.steamId == steamId }
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<Pair<Boolean, String>>() }
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(steamId) {
        if (friend == null) SteamService.requestFriendInfo(steamId)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gnBgDeepest),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
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
                    ProfileSection(friend = friend)
                    Spacer(modifier = Modifier.height(24.dp))
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
                        items(messages.size, key = { it }) { idx ->
                            val (fromMe, text) = messages[idx]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (fromMe) Arrangement.End else Arrangement.Start,
                            ) {
                                Text(
                                    text = text,
                                    style = PluviaTypography.bodyMedium,
                                    color = if (fromMe) gnTextPrimary else gnTextSecondary,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                messages.add(true to text)
                                scope.launch {
                                    SteamService.sendFriendMessage(steamId, text)
                                }
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
private fun ProfileSection(friend: SteamFriend) {
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(gnBgSurface)
            .border(1.dp, gnBorderCard, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
}
