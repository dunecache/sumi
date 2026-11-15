package com.sumi.platform.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Forced widget refresh on state changes the app does not observe at runtime
 * (docs/WIDGET.md §8): local date change, timezone change, locale change. The system
 * delivers these as protected broadcasts; we enqueue an expedited one-time render and
 * return immediately — no aggregation, no collection, just the re-render.
 *
 * Boot is handled here too (a second registration of the same enqueue from
 * [BootReceiver]) because a rebooted device must never show pre-reboot widget numbers.
 */
class WidgetRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(appContext)
            .enqueueUniqueWork(WIDGET_REFRESH_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        const val WIDGET_REFRESH_WORK = "sumi-widget-refresh"
    }
}
