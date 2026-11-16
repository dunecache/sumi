package com.sumi.platform.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Forced widget refresh on state changes the app does not observe at runtime
 * (docs/WIDGET.md §8): local date change, timezone change, locale change. The system
 * delivers these as protected broadcasts; we enqueue the same expedited worker used by
 * the widget receivers — it performs an incremental reconcile and re-renders both
 * providers, so numbers, day key and staleness footer all advance together.
 *
 * Boot is handled here too (a second registration of the same enqueue from
 * [BootReceiver]) because a rebooted device must never show pre-reboot widget numbers.
 */
class WidgetRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        WidgetDiagnostics.log("system receiver: ${intent.action} -> worker")
        WidgetRefreshWorker.enqueueNow(context.applicationContext)
    }

    companion object {
        const val WIDGET_REFRESH_WORK = WidgetRefreshWorker.WORK_NOW
    }
}
