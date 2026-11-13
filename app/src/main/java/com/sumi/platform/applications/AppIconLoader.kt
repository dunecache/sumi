package com.sumi.platform.applications

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache
import com.sumi.core.common.AppDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * Resolves launcher icons for installed packages on demand, with an in-memory cache.
 * No schema change and no new permission: packages appear here only when already visible
 * to Sumi via the manifest `<queries>` launcher intent (see AndroidManifest.xml).
 *
 * Returns null when no icon can be resolved (uninstalled or hidden package); callers
 * render a monogram fallback instead of failing.
 */
interface AppIconLoader {
    suspend fun iconFor(packageName: String): Drawable?
}

@Singleton
class PackageManagerIconLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: AppDispatchers,
) : AppIconLoader {

    // Cache nulls too: negative lookups (uninstalled/hidden) are the expensive repeat case.
    private val cache = object : LruCache<String, Drawable?>(CACHE_ENTRIES) {}

    override suspend fun iconFor(packageName: String): Drawable? = withContext(dispatchers.io) {
        if (packageName.isBlank()) return@withContext null
        cache.get(packageName) ?: loadAndCache(packageName)
    }

    private fun loadAndCache(packageName: String): Drawable? {
        val pm = context.packageManager
        val icon = resolve(pm, packageName)
        cache.put(packageName, icon)
        return icon
    }

    private fun resolve(pm: PackageManager, packageName: String): Drawable? {
        // Launch-activity icon first (matches the launcher), then the app icon.
        val launchIntent = pm.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            try {
                return pm.getActivityIcon(launchIntent)
            } catch (_: Exception) {
                // fall through to the application icon
            }
        }
        return try {
            pm.getApplicationIcon(packageName)
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val CACHE_ENTRIES = 128
    }
}
