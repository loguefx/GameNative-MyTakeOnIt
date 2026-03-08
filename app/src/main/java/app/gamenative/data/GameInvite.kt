package app.gamenative.data

/**
 * Steam game invite received from a friend.
 * connectString is passed to Wine / game (e.g. +connect_lobby/...).
 */
data class GameInvite(
    val id: String,
    val fromSteamId: String,
    val fromName: String,
    val fromAvatarUrl: String,
    val gameName: String,
    val gameAppId: Int,
    val gameHeaderUrl: String,
    val connectString: String,
    val receivedAt: Long,
)
