package com.sumi.platform.usage

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Usage Access detection. Granted in system Settings (special access), not via runtime dialog.
 * Explain first, then open [Settings.ACTION_USAGE_ACCESS_SETTINGS].
 * Re-check on every resume via ViewModel observing [hasUsageAccess] or Lifecycle resume.
 *
 * No QUERY_ALL_PACKAGES: app discovery uses <queries> for launcher intent (M2).
 */
@Singleton
class UsageAccessChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Suppress("DEPRECATION")
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val uid = android.os.Process.myUid()
        return try {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, context.packageName) ==
                AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    fun usageAccessSettingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
}

/** Whether this device needs a runtime permission check for notifications (Android 13+). */
fun needsNotificationPermission(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
