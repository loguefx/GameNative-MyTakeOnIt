package app.gamenative.utils

import android.app.Activity
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.getSystemService
import java.lang.ref.WeakReference

/**
 * Holds wake lock and WiFi lock while a game is running so the device doesn't throttle
 * or drop WiFi mid-game. Call release() when leaving the game (e.g. in exit()).
 * See gamenative-steam-performance.mdc 5G.
 */
object GameRunWakeLocks {
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var sustainedPerformance = false
    private var activityRef: WeakReference<Activity>? = null

    fun acquire(activity: Activity) {
        release()
        activityRef = WeakReference(activity)
        val powerManager = activity.getSystemService(Activity.POWER_SERVICE) as? PowerManager
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "GameNative:GameRunning"
            ).apply {
                acquire(10 * 60 * 60 * 1000L) // 10 hours max; release on exit
            }
        }
        val wifiManager = activity.applicationContext.getSystemService(Activity.WIFI_SERVICE) as? WifiManager
        if (wifiManager != null) {
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "GameNative:GameWifi").apply {
                acquire(10 * 60 * 60 * 1000L)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                activity.window.setSustainedPerformanceMode(true)
                sustainedPerformance = true
            } catch (_: Exception) { }
        }
    }

    fun release() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
                wakeLock = null
            }
            wifiLock?.let {
                if (it.isHeld) it.release()
                wifiLock = null
            }
            activityRef?.get()?.window?.let { window ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && sustainedPerformance) {
                    try {
                        window.setSustainedPerformanceMode(false)
                    } catch (_: Exception) { }
                    sustainedPerformance = false
                }
            }
            activityRef = null
        } catch (_: Exception) { }
    }
}
