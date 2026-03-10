package app.gamenative.ui.screen

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gamenative.R
import app.gamenative.data.GameInvite
import app.gamenative.ui.enums.HomeDestination
import app.gamenative.ui.model.HomeViewModel
import app.gamenative.ui.screen.downloads.DownloadsScreen
import app.gamenative.ui.screen.friends.FriendsScreen
import app.gamenative.ui.screen.library.HomeLibraryScreen
import app.gamenative.ui.theme.PluviaTheme
import app.gamenative.ui.theme.PluviaTypography
import app.gamenative.ui.theme.gnAccentGlow
import app.gamenative.ui.theme.gnAccentPrimary
import app.gamenative.ui.theme.gnBgDeepest
import app.gamenative.ui.theme.gnBgSurface
import app.gamenative.ui.theme.gnBorderCard
import app.gamenative.ui.theme.gnTextPrimary
import app.gamenative.ui.theme.gnTextSecondary
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil.CoilImage

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    initialTab: HomeDestination? = null,
    pendingInvites: List<GameInvite> = emptyList(),
    onAcceptInvite: (GameInvite) -> Unit = {},
    onDeclineInvite: (GameInvite) -> Unit = {},
    onChat: (Long) -> Unit,
    onClickExit: () -> Unit,
    onClickPlay: (String, Boolean) -> Unit,
    onTestGraphics: (String) -> Unit,
    onLogout: () -> Unit,
    onNavigateRoute: (String) -> Unit,
    onGoOnline: () -> Unit,
    isOffline: Boolean = false,
) {
    val homeState by viewModel.homeState.collectAsStateWithLifecycle()
    val currentDestination = homeState.currentDestination
    val downloadingCount by viewModel.downloadingCount.collectAsStateWithLifecycle()
    LaunchedEffect(initialTab) {
        initialTab?.let { viewModel.onDestination(it) }
    }
    val downloadingAppIds by viewModel.downloadingAppIds.collectAsStateWithLifecycle()

    val latestInvite = pendingInvites.firstOrNull()
    val inviteCount = pendingInvites.size

    BackHandler {
        onClickExit()
    }

    Scaffold(
        containerColor = gnBgDeepest,
        bottomBar = {
            Column {
                // Notification bar: shows the latest pending game invite above the nav bar.
                AnimatedVisibility(
                    visible = latestInvite != null,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                ) {
                    latestInvite?.let { invite ->
                        InviteNotificationBar(
                            invite = invite,
                            extraCount = (inviteCount - 1).coerceAtLeast(0),
                            onAccept = { onAcceptInvite(invite) },
                            onDecline = { onDeclineInvite(invite) },
                        )
                    }
                }

                NavigationBar(
                    containerColor = gnBgSurface,
                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                ) {
                    HomeDestination.entries.forEach { destination ->
                        val selected = currentDestination == destination
                        val showDownloadBadge = destination == HomeDestination.Downloads && downloadingCount > 0
                        NavigationBarItem(
                            selected = selected,
                            onClick = { viewModel.onDestination(destination) },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (showDownloadBadge) {
                                            Badge {
                                                Text(
                                                    text = if (downloadingCount > 99) "99+" else "$downloadingCount",
                                                )
                                            }
                                        }
                                    },
                                ) {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = stringResource(destination.title),
                                    )
                                }
                            },
                            label = { Text(stringResource(destination.title)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { paddingValues ->
        Box(Modifier.padding(paddingValues)) {
            when (currentDestination) {
                HomeDestination.Library -> HomeLibraryScreen(
                    onClickPlay = onClickPlay,
                    onTestGraphics = onTestGraphics,
                    onNavigateRoute = onNavigateRoute,
                    onLogout = onLogout,
                    onGoOnline = onGoOnline,
                    isOffline = isOffline,
                )
                HomeDestination.Downloads -> DownloadsScreen(downloadingAppIds = downloadingAppIds)
                HomeDestination.Friends -> FriendsScreen(
                    onFriendClick = onChat,
                )
            }
        }
    }
}

/**
 * Compact notification bar shown above the bottom navigation when a game invite is pending.
 * Friend avatar, name, and game name are shown. Accept/Decline actions are inline.
 */
@Composable
private fun InviteNotificationBar(
    invite: GameInvite,
    extraCount: Int,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(gnBgSurface)
            .border(width = 1.dp, color = gnBorderCard, shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Friend avatar
        CoilImage(
            imageModel = { invite.fromAvatarUrl },
            imageOptions = ImageOptions(contentScale = ContentScale.Crop),
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .border(1.dp, gnAccentPrimary, CircleShape),
        )

        Spacer(Modifier.width(8.dp))

        // Invite text
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.SportsEsports,
                    contentDescription = null,
                    tint = gnAccentGlow,
                    modifier = Modifier.size(11.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = "${invite.fromName} is playing",
                    style = PluviaTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = gnTextPrimary,
                    maxLines = 1,
                )
                if (extraCount > 0) {
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "+$extraCount more",
                        style = PluviaTypography.labelSmall,
                        color = gnTextSecondary,
                    )
                }
            }
            Text(
                text = invite.gameName,
                style = PluviaTypography.labelSmall,
                color = gnAccentGlow,
                maxLines = 1,
            )
        }

        // Decline
        TextButton(
            onClick = onDecline,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            modifier = Modifier.height(28.dp),
        ) {
            Text(
                text = "Decline",
                style = PluviaTypography.labelSmall,
                color = gnTextSecondary,
            )
        }

        Spacer(Modifier.width(2.dp))

        // Join
        Button(
            onClick = onAccept,
            colors = ButtonDefaults.buttonColors(
                containerColor = gnAccentPrimary,
                contentColor = Color.White,
            ),
            shape = RoundedCornerShape(5.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            modifier = Modifier.height(28.dp),
        ) {
            Text(
                text = "Join",
                style = PluviaTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    device = "spec:width=1080px,height=1920px,dpi=440,orientation=landscape",
)
@Composable
private fun Preview_HomeScreenContent() {
    PluviaTheme {
        var destination: HomeDestination by remember {
            mutableStateOf(HomeDestination.Library)
        }
        HomeScreen(
            onChat = {},
            onClickPlay = { _, _ -> },
            onTestGraphics = { },
            onLogout = {},
            onNavigateRoute = {},
            onClickExit = {},
            onGoOnline = {},
        )
    }
}
