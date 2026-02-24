package app.gamenative.compat

import android.content.Context

/**
 * Overlay discipline: default no overlay unless game is on Overlay Verified list.
 * If user enables manually, show warning that it can break titles.
 */
object OverlayPolicy {

    fun allowOverlayByDefault(context: Context, steamAppId: Long): Boolean {
        CompatibilityDb.load(context)
        return CompatibilityDb.isOverlayVerified(steamAppId)
    }

    fun getManualOverlayWarning(): String =
        "Overlays can cause crashes or instability in some games. Enable only if you understand the risk."
}
