package com.sumi.platform.background

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sumi.domain.common.Clock
import com.sumi.domain.usage.UsageRepository
import com.sumi.platform.usage.UsageAccessChecker
import com.sumi.platform.widget.WidgetUpdater
import android.util.Log
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic safety-net collection (ARCHITECTURE.md §4.3). Never a live tracker:
 * reconciles on open / resume; this worker only backfills gaps.
 */
@HiltWorker
class CollectionWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: UsageRepository,
    private val clock: Clock,
    private val usageAccessChecker: UsageAccessChecker,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Widget refresh on every path: grant state can change while we run, and the
        // widget honestly renders the permission explanation when access is absent.
        if (usageAccessChecker.hasUsageAccess()) {
            val now = clock.nowUtcMs()
            // 7d retention guard: reconcile last 7 days plus overlap.
            val from = now - SEVEN_DAYS_MS
            repository.collectRecent(from, now)
        }
        runCatching { WidgetUpdater.refreshNow(appContext) }
            .onFailure { Log.w("CollectionWorker", "widget refresh failed", it) }
        return Result.success()
    }

    private companion object {
        const val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
