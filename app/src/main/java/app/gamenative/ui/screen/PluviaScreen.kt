package app.gamenative.ui.screen

/**
 * Destinations for top level screens, excluding home screen destinations.
 */
sealed class PluviaScreen(val route: String) {
    data object LoginUser : PluviaScreen("login")
    data object Home : PluviaScreen("home")
    data object XServer : PluviaScreen("xserver")
    data object Settings : PluviaScreen("settings")
    data object PlayerProfile : PluviaScreen("profile/{id}") {
        fun route(id: Long) = "profile/$id"
        const val ARG_ID = "id"
    }
    data object Chat : PluviaScreen("chat/{id}") {
        fun route(id: Long) = "chat/$id"
        const val ARG_ID = "id"
    }
    data object Achievements : PluviaScreen("achievements/{appId}/{steamId}") {
        fun route(appId: String, steamId: Long = 0L) = "achievements/$appId/$steamId"
        const val ARG_APP_ID = "appId"
        const val ARG_STEAM_ID = "steamId"
    }

    data object GameConfig : PluviaScreen("game_config/{appId}") {
        fun route(appId: String) = "game_config/$appId"
        const val ARG_APP_ID = "appId"
    }
}
