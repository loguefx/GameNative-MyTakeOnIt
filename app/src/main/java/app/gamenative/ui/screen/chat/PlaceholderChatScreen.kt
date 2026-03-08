package app.gamenative.ui.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gamenative.R
import app.gamenative.ui.theme.gnBgDeepest
import app.gamenative.ui.theme.gnTextSecondary

/**
 * Placeholder until Phase 2 Chat (ChatRepository, Room, ChatScreen) is implemented.
 */
@Composable
fun PlaceholderChatScreen(
    steamId: Long,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gnBgDeepest),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
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
                text = "Chat",
                style = MaterialTheme.typography.titleMedium,
                color = gnTextSecondary,
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.chat_phase2_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = gnTextSecondary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            val uriHandler = LocalUriHandler.current
            FilledTonalButton(
                onClick = {
                    uriHandler.openUri("https://steamcommunity.com/profiles/$steamId")
                },
            ) {
                Text(stringResource(R.string.chat_open_profile_steam))
            }
        }
    }
}
