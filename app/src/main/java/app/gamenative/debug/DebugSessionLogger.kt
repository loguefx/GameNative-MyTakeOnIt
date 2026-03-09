package app.gamenative.debug

import android.util.Log
import org.json.JSONObject

/**
 * Session debug logger for agent-driven debugging.
 * Logs one NDJSON line per call to Log.d with tag Dbg6f24d2; capture via adb logcat and save to debug-6f24d2.log.
 */
internal object DebugSessionLogger {
    private const val TAG = "Dbg6f24d2"

    fun log(
        hypothesisId: String,
        location: String,
        message: String,
        data: Map<String, Any?> = emptyMap(),
        level: Int = Log.DEBUG,
    ) {
        try {
            val payload = JSONObject().apply {
                put("sessionId", "6f24d2")
                put("hypothesisId", hypothesisId)
                put("location", location)
                put("message", message)
                put("timestamp", System.currentTimeMillis())
                val dataObj = JSONObject()
                data.forEach { (k, v) -> dataObj.put(k, v?.toString() ?: "null") }
                put("data", dataObj)
            }
            val msg = payload.toString()
            when (level) {
                Log.ERROR -> Log.e(TAG, msg)
                Log.WARN -> Log.w(TAG, msg)
                else -> Log.d(TAG, msg)
            }
        } catch (_: Throwable) { /* avoid breaking app */ }
    }
}
