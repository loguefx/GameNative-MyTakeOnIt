package app.gamenative.data

/**
 * UI model for a single Steam achievement (schema + player unlock state).
 */
data class SteamAchievement(
    val apiName: String,
    val name: String,
    val description: String,
    val iconUrl: String,
    val iconGrayUrl: String,
    val unlocked: Boolean,
    val unlockTime: Long = 0L,
)
