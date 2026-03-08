package app.gamenative

import androidx.compose.ui.unit.dp

/**
 * Constants values that may be used around the app more than once.
 * Constants that are used in composables and or view models should be here too.
 */
object Constants {

    object Composables {
        val WINDOW_WIDTH_LARGE = 1200.dp
    }

    object XServer {
        const val DEFAULT_WINE_DEBUG_CHANNELS = "warn,err,fixme,loaddll"
        const val CONTAINER_PATTERN_COMPRESSION_LEVEL = 9
    }

    object Persona {
        const val AVATAR_BASE_URL = "https://steamcdn-a.akamaihd.net/steamcommunity/public/images/avatars/"
        const val MISSING_AVATAR_URL = "${AVATAR_BASE_URL}fe/fef49e7fa7e1997310d705b2a6158ff8dc1cdfeb_full.jpg"
        const val PROFILE_URL = "https://steamcommunity.com/profiles/"
    }

    object Chat {
        const val EMOTICON_URL = "https://steamcommunity-a.akamaihd.net/economy/emoticonlarge/"
        const val STICKER_URL = "https://steamcommunity-a.akamaihd.net/economy/sticker/"
    }

    object Library {
        const val ICON_URL = "https://cdn.cloudflare.steamstatic.com/steamcommunity/public/images/apps/"
        const val STORE_URL = "https://store.steampowered.com/app/"
        /** Steam header image 460×215 for game rows and hero background. */
        fun steamHeaderUrl(appId: Int): String =
            "https://shared.steamstatic.com/store_item_assets/steam/apps/$appId/header.jpg"
        /** Fallback capsule for grid when PICS library assets are missing. */
        fun steamCapsuleUrl(appId: Int): String =
            "https://cdn.akamai.steamstatic.com/steam/apps/$appId/library_600x900.jpg"
    }

    object Misc {
        const val KO_FI_LINK = "https://ko-fi.com/gamenative"
        const val GITHUB_LINK = "https://github.com/utkarshdalal/GameNative"
        const val PRIVACY_LINK = "https://github.com/utkarshdalal/GameNative/tree/master/PrivacyPolicy"
        /** Empty = disabled (fork does not use upstream update API). */
        const val UPDATE_CHECK_URL = ""
    }
}
