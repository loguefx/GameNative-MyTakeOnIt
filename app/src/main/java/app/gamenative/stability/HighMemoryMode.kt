package app.gamenative.stability

import android.content.Context
import app.gamenative.preflight.PreflightRunner

/**
 * High-memory mode: warns user, locks resolution/cache limits; block launch if device doesn't meet budget.
 * "Requirements," not prediction.
 */
object HighMemoryMode {

    const val MIN_FREE_RAM_MB = 1536L
    const val LOCKED_RESOLUTION = "1280x720"

    fun isEligible(context: Context): Boolean {
        val memInfo = android.app.ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)?.getMemoryInfo(memInfo)
        return (memInfo.availMem / (1024 * 1024)) >= MIN_FREE_RAM_MB
    }

    fun getWarningMessage(): String =
        "High-memory mode will lock resolution to $LOCKED_RESOLUTION and may close background apps. Ensure you have at least ${MIN_FREE_RAM_MB / 1024}GB free RAM."
}
