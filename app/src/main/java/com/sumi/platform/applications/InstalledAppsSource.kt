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
    /** All launchable package names installed on this device, once, resolved at call time. */
    suspend fun launchablePackages(): List<String>
}

@Singleton
class PackageManagerInstalledAppsSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: AppDispatchers,
) : InstalledAppsSource {

    override suspend fun launchablePackages(): List<String> = withContext(dispatchers.io) {
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
            result.mapNotNull { it.activityInfo?.packageName?.takeIf { name -> name.isNotBlank() } }
                .distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
