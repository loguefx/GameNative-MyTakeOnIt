package app.gamenative.data

import `in`.dragonbra.javasteam.enums.EPersonaState

/**
 * Represents a Steam user (self or friend) for profile icon, friends list, and chat.
 * steamId is the 64-bit Steam ID; 0 when representing only local cached persona.
 */
data class SteamFriend(
    val steamId: Long = 0L,
    val avatarHash: String = "",
    val gameAppID: Int = 0,
    val gameName: String = "",
    val name: String = "",
    val state: EPersonaState = EPersonaState.Offline,
) {
    val isOnline: Boolean
        get() = (state.code() in 1..6)

    val isPlayingGame: Boolean
        get() = if (isOnline) gameAppID > 0 || gameName.isNotBlank() else false
}
