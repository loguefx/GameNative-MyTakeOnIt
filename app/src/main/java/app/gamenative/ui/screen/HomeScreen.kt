package app.gamenative.ui.screen

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gamenative.R
import app.gamenative.ui.enums.HomeDestination
import app.gamenative.ui.model.HomeViewModel
import app.gamenative.ui.screen.friends.FriendsScreen
import app.gamenative.ui.screen.library.HomeLibraryScreen
import app.gamenative.ui.theme.PluviaTheme
import app.gamenative.ui.theme.gnBgDeepest
import app.gamenative.ui.theme.gnBgSurface

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onChat: (Long) -> Unit,
    onClickExit: () -> Unit,
    onClickPlay: (String, Boolean) -> Unit,
    onTestGraphics: (String) -> Unit,
    onLogout: () -> Unit,
    onNavigateRoute: (String) -> Unit,
    onGoOnline: () -> Unit,
    isOffline: Boolean = false
) {
    val homeState by viewModel.homeState.collectAsStateWithLifecycle()
    val currentDestination = homeState.currentDestination

    BackHandler {
        onClickExit()
    }

    Scaffold(
        containerColor = gnBgDeepest,
        bottomBar = {
            NavigationBar(
                containerColor = gnBgSurface,
                contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
            ) {
                HomeDestination.entries.forEach { destination ->
                    val selected = currentDestination == destination
                    NavigationBarItem(
                        selected = selected,
                        onClick = { viewModel.onDestination(destination) },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = stringResource(destination.title),
                            )
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
                HomeDestination.Downloads -> PlaceholderDownloadsContent()
                HomeDestination.Friends -> FriendsScreen(
                    onFriendClick = onChat,
                )
            }
        }
    }
}

@Composable
private fun PlaceholderDownloadsContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.destination_downloads),
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = app.gamenative.ui.theme.gnTextSecondary,
        )
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
