package com.sumi.platform.applications

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.sumi.core.common.AppDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * Discovers installed apps without any permission — Sumi's manifest already declares
 * `<queries>` for `MAIN`/`LAUNCHER` (app discovery per AGENTS.md §2, never
 * QUERY_ALL_PACKAGES), which is exactly the visibility window granted for that purpose.
 *
 * Only launchable apps are returned, i.e. the apps the user sees and opens. This is a
 * superset of apps that ever generated usage events (including installed-but-never-used
 * apps) and is used to build the install library (applications table), not to observe
 * usage. Resolution never throws — an empty result on failure.
 */
interface InstalledAppsSource {
    /** All launchable apps installed on this device, once, resolved at call time. */
    suspend fun launchableApps(): List<InstalledApp>
}

/** One discovered launcher application. */
data class InstalledApp(
    val packageName: String,
    /** True for preinstalled/privileged system apps (eligible to hide in the library). */
    val isSystemApp: Boolean,
)

@Singleton
class PackageManagerInstalledAppsSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: AppDispatchers,
) : InstalledAppsSource {

    override suspend fun launchableApps(): List<InstalledApp> = withContext(dispatchers.io) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        try {
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(0L),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(intent, 0)
            }
            result.mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.packageName?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                InstalledApp(packageName, isSystemApp = isSystemPackage(resolveInfo, packageName))
            }.distinctBy { it.packageName }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * System-app detection: the activity-declared `applicationInfo` flags when present,
     * falling back to a direct `getApplicationInfo` lookup. `FLAG_UPDATED_SYSTEM_APP`
     * counts as system (the app shipped with the device, the user updated it later).
     * Any failure resolves to non-system — a wrong "user app" classification is safer
     * than wrongly hiding something the user installed.
     */
    private fun isSystemPackage(info: android.content.pm.ResolveInfo, packageName: String): Boolean {
        val fromResolve = info.activityInfo?.applicationInfo?.flags ?: 0
        if (fromResolve != 0) {
            return flaggedSystem(fromResolve)
        }
        return try {
            flaggedSystem(context.packageManager.getApplicationInfo(packageName, 0).flags)
        } catch (_: Exception) {
            false
        }
    }

    private fun flaggedSystem(flags: Int): Boolean =
        (flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or
            android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
}
