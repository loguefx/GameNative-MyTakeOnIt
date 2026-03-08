package app.gamenative.data

import `in`.dragonbra.javasteam.enums.EPersonaState

/**
 * Presence state for player profile hero and in-game chat.
 * Colors: ONLINE=teal, IN_GAME=blue, AWAY=orange, OFFLINE=gray.
 */
enum class PresenceState {
    ONLINE,
    OFFLINE,
    IN_GAME,
    AWAY,
}

fun SteamFriend.toPresenceState(): PresenceState {
    if (gameAppID > 0 || gameName.isNotBlank()) return PresenceState.IN_GAME
    return when (state) {
        EPersonaState.Away, EPersonaState.Busy, EPersonaState.Snooze -> PresenceState.AWAY
        EPersonaState.Online, EPersonaState.LookingToTrade, EPersonaState.LookingToPlay -> PresenceState.ONLINE
        else -> PresenceState.OFFLINE
    }
}

/**
 * Friend's game row for profile games list. Sorted by playtime descending by default.
 * Uses Steam header art (460×215) for row thumbnail.
 */
data class FriendGame(
    val appId: Int,
    val name: String,
    val headerUrl: String,
    val playtimeForever: Int,   // total minutes
    val playtime2Weeks: Int,    // recent minutes
    val lastPlayed: Long?,      // unix timestamp, null if never
)
