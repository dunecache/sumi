package com.sumi.platform.widget

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sumi.domain.common.Clock
import com.sumi.domain.usage.UsageRepository
import com.sumi.platform.usage.UsageAccessChecker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 30-minute periodic backstop for the widgets (docs/WIDGET.md §8).
 *
 * Unlike a pure re-render this runs an **incremental collection pass first** — the only
 * writer of committed state — so the widget's numbers (and the staleness footer's
 * reference point) advance between app opens whenever the system lets this job run.
 * The pass is the same 7-day overlap reconcile `CollectionWorker` performs and is
 * serialized with every other collection path by the repository's mutex, so duplicate
 * work degenerates to a cheap no-op scan.
 *
 * This is a backstop, not a tracker: WorkManager periodic work is deferrable and may
 * not run for hours on devices with aggressive battery management or a rare/restricted
 * app-standby bucket, and the platform clamps widget broadcasts to a 30-minute floor.
 * Neither path is guaranteed in deep Doze; the system's `updatePeriodMillis` (30 min,
 * the platform floor) is the independent second path. When nothing wakes, the widget
 * shows the last committed state with its honest staleness footer — never stale numbers
 * presented as fresh.
 */
@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: UsageRepository,
    private val clock: Clock,
    private val usageAccessChecker: UsageAccessChecker,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (usageAccessChecker.hasUsageAccess()) {
            val now = clock.nowUtcMs()
            runCatching { repository.collectRecent(now - SEVEN_DAYS_MS, now) }
                .onFailure { Log.w(TAG, "usage collection failed", it) }
        }
        runCatching { WidgetUpdater.refreshNow(applicationContext) }
            .onFailure { Log.w(TAG, "widget refresh failed", it) }
        return Result.success()
    }

    private companion object {
        const val TAG = "WidgetRefreshWorker"
        const val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
