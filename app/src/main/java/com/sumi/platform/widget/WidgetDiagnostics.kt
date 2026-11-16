package com.sumi.platform.widget

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.sumi.BuildConfig

/**
 * Debug-only logcat diagnostics for the background-refresh investigation (vivo V2322,
 * Android 15). Compiled out of release builds via [BuildConfig.DEBUG]. Tag is
 * `SumiWidgetDiag` so the whole chain — system broadcast → receiver → worker → render —
 * can be observed on a phone with `logcat -d -s SumiWidgetDiag`.
 */
internal object WidgetDiagnostics {
    private const val TAG = "SumiWidgetDiag"

    fun log(message: String) {
        if (BuildConfig.DEBUG) Log.i(TAG, message)
    }

    /** Standby bucket + battery-optimization state, the two throttles we suspect. */
    fun deviceState(context: Context): String {
        if (!BuildConfig.DEBUG) return ""
        val bucket = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val stats = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            stats?.appStandbyBucket?.toString() ?: "?"
        } else {
            "n/a"
        }
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val unrestricted = power?.isIgnoringBatteryOptimizations(context.packageName)?.toString() ?: "?"
        return "bucket=$bucket unrestricted=$unrestricted"
    }
}
