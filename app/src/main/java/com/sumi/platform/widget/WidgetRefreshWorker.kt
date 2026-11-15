package com.sumi.platform.widget

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Battery-conscious periodic re-render for the widgets (docs/WIDGET.md §8). This worker
 * never collects usage — the aggregation pipeline owns that; it only re-renders the
 * picker entries from the committed Room state so the staleness footer stays honest
 * and midnight rollover happens even if the app is never opened. The system's
 * `updatePeriodMillis` (6 h) remains as a backstop.
 */
@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        runCatching { WidgetUpdater.refreshNow(applicationContext) }
        return Result.success()
    }
}
