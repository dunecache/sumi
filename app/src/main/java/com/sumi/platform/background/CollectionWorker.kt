package com.sumi.platform.background

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sumi.domain.common.Clock
import com.sumi.domain.usage.UsageRepository
import com.sumi.platform.usage.UsageAccessChecker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic safety-net collection (ARCHITECTURE.md §4.3). Never a live tracker:
 * reconciles on open / resume; this worker only backfills gaps.
 */
@HiltWorker
class CollectionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: UsageRepository,
    private val clock: Clock,
    private val usageAccessChecker: UsageAccessChecker,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!usageAccessChecker.hasUsageAccess()) return Result.success()
        val now = clock.nowUtcMs()
        // 7d retention guard: reconcile last 7 days plus overlap.
        val from = now - SEVEN_DAYS_MS
        repository.collectRecent(from, now)
        return Result.success()
    }

    private companion object {
        const val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
