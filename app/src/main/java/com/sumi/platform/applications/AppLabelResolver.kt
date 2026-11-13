package com.sumi.platform.applications

import android.content.Context
import android.util.LruCache
import com.sumi.core.common.AppDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * Resolves user-facing display labels for packages via the package manager
 * (launcher apps are visible through the manifest `<queries>` launcher intent).
 * Used when Application rows are created so the Apps/Today screens show real
 * names instead of package identifiers; returns null when no label resolves
 * (non-launchable or hidden package) and callers keep the package id.
 */
interface AppLabelResolver {
    suspend fun labelFor(packageName: String): String?
}

@Singleton
class PackageManagerLabelResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: AppDispatchers,
) : AppLabelResolver {

    // Caches misses as sentinels too — the platform LruCache rejects null values.
    private val cache = LruCache<String, Any>(CACHE_ENTRIES)

    override suspend fun labelFor(packageName: String): String? = withContext(dispatchers.io) {
        if (packageName.isBlank()) return@withContext null
        when (val cached = cache.get(packageName)) {
            null -> loadAndCache(packageName) // cache miss
            NEGATIVE -> null // cached negative: no label resolvable
            else -> cached as String
        }
    }

    private fun loadAndCache(packageName: String): String? {
        val label = try {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(packageName, 0),
            ).toString().trim().takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
        cache.put(packageName, label ?: NEGATIVE)
        return label
    }

    private companion object {
        const val CACHE_ENTRIES = 256
        val NEGATIVE = Any()
    }
}
