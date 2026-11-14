package com.sumi.platform.usage

import android.app.AppOpsManager
import android.Manifest
import android.annotation.SuppressLint
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
    // ProtectedPermissions: PACKAGE_USAGE_STATS is the canonical special-access permission
    // granted via Settings; lint misclassifies it (same as the manifest ignore).
    @SuppressLint("ProtectedPermissions")
    @Suppress("DEPRECATION", "NewApi")
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val uid = android.os.Process.myUid()
        return try {
            // checkOpNoThrow returns MODE_DEFAULT on some OEMs even when the special
            // access is granted; in that case fall back to the permission check.
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("NewApi")
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, context.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, context.packageName)
            }
            if (mode == AppOpsManager.MODE_DEFAULT) {
                context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                mode == AppOpsManager.MODE_ALLOWED
            }
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
