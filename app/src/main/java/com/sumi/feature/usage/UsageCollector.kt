package com.sumi.feature.usage

import com.sumi.domain.common.Clock
import com.sumi.domain.usage.UsageRepository
import com.sumi.platform.usage.UsageAccessChecker
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Collection trigger for app open / resume (ARCHITECTURE.md §4.3).
 * WorkManager is only a safety net; live data flows from open + resume.
 * No polling loop, no foreground service.
 *
 * Concurrency lives in the repository (one Mutex for every collection path), so the
 * worker and a resume can never interleave; this class only enforces permission state.
 */
@Singleton
class UsageCollector @Inject constructor(
    private val repository: UsageRepository,
    private val checker: UsageAccessChecker,
    private val clock: Clock,
) {
    /** Reconcile the recent window; safe to call on every resume (idempotent). */
    suspend fun collectOnForeground() {
        if (!checker.hasUsageAccess()) return
        val now = clock.nowUtcMs()
        val from = now - WINDOW_MS
        repository.collectRecent(from, now)
    }

    /** One-time historical import after first grant. */
    suspend fun importHistory() {
        if (!checker.hasUsageAccess()) return
        val now = clock.nowUtcMs()
        val from = now - HISTORY_MS
        repository.initialSyncFromUsageStats(from, now)
        repository.collectRecent(from, now)
        repository.backfillInstalledApps()
    }

    private companion object {
        const val WINDOW_MS = 6L * 60L * 60L * 1000L // overlap window
        const val HISTORY_MS = 90L * 24L * 60L * 60L * 1000L
    }
}
