package com.sumi.data.usage

import com.sumi.core.database.entity.Application
import com.sumi.platform.applications.InstalledApp

/**
 * Pure merge logic for the installed-app refresh ([UsageRepositoryImpl.backfillInstalledApps]).
 * Kept free of Android types so the policy is unit-testable on the JVM.
 *
 * Rules:
 *  - Discovery **creates** rows for launcher-visible packages that have no row yet
 *    (device furniture per the exclusion set is never created).
 *  - Rows that vanished from the launcher catalog are marked `isInstalled = 0` — never
 *    deleted, so the stored assignment and the usage history survive a reinstall.
 *  - Rows that reappear are marked `isInstalled = 1` again and refresh `isSystemApp`.
 *    Every other field — the user's category assignment first and foremost — is kept.
 *  - A system-flag *drift* on an installed row is also refreshed (device restored from a
 *    backup, or the app was updated in place).
 */
internal object LibraryMerge {

    /** What a refresh should do to the `applications` table. */
    data class Plan(
        /** Packages with no row at all — create fresh rows (labels/defaults resolved by the caller). */
        val insertPackages: List<String>,
        /** Existing rows to upsert: reinstall restore and/or system-flag correction. User fields kept. */
        val restoreRows: List<Application>,
        /** Package names to flip to `isInstalled = 0`. Never deleted. */
        val markUninstalled: List<String>,
    )

    /**
     * @param existing all rows in `applications` (installed or not).
     * @param discovered the live launcher catalog, resolved at call time.
     * @param excluded device furniture that must never get a row (Sumi itself, launcher, IME).
     */
    fun plan(
        existing: List<Application>,
        discovered: List<InstalledApp>,
        excluded: Set<String>,
    ): Plan {
        val raw = discovered.mapTo(HashSet(discovered.size)) { it.packageName }
        val candidates = discovered.filter { it.packageName !in excluded }
        val existingByPackage = existing.associateBy { it.packageName }

        val inserts = candidates
            .filter { it.packageName !in existingByPackage }
            .map { it.packageName }

        val restores = candidates.mapNotNull { app ->
            val row = existingByPackage[app.packageName] ?: return@mapNotNull null
            val reinstall = !row.isInstalled
            val flagDrift = row.isSystemApp != app.isSystemApp
            if (reinstall || flagDrift) {
                row.copy(isInstalled = true, isSystemApp = app.isSystemApp)
            } else {
                null
            }
        }

        val markUninstalled = existing
            .filter { it.isInstalled && it.packageName !in raw }
            .map { it.packageName }

        return Plan(inserts, restores, markUninstalled)
    }
}
