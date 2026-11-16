package com.sumi.platform.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The Sumi home-screen widgets (docs/WIDGET.md v3 — "Screen time"): two picker entries,
 * one render core. Both mirror Today's numbers and refresh wherever the app's data
 * changes; neither computes its own statistics.
 */
class CompactScreenTimeWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(SIZE_4X1.dp, SIZE_1_HEIGHT.dp),
            DpSize(SIZE_3X1.dp, SIZE_1_HEIGHT.dp), // placement floor; tightens padding
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshotLoader.load(context)
        WidgetDiagnostics.log("render compact total=${snapshot.totalMs} view=${snapshot.view::class.simpleName}")
        provideContent { CompactWidgetContent(snapshot) }
    }

    private companion object {
        const val SIZE_4X1 = 270
        const val SIZE_3X1 = 180
        const val SIZE_1_HEIGHT = 72
    }
}

/**
 * Receiver base for both v3 widgets. Glance 1.2.0's own source routes
 * `ACTION_APPWIDGET_UPDATE` through a WorkManager worker on vivo devices **only up to
 * SDK 34** (its comment: "goAsync is broken on certain OEMs"). On vivo Android 15 that
 * workaround is skipped and the plain `goAsync()` path is used — the very path the
 * workaround exists to avoid. We intercept the update broadcast and enqueue our own
 * expedited worker, which collects an incremental delta and calls `updateAll()`
 * directly: no sender-side `goAsync()` involved. Everything else (options changed,
 * locale, click actions) keeps Glance's default handling.
 */
abstract class SumiWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            WidgetDiagnostics.log(
                "receiver ${javaClass.simpleName}: APPWIDGET_UPDATE -> worker " +
                    WidgetDiagnostics.deviceState(context),
            )
            WidgetRefreshWorker.enqueueNow(context)
            return
        }
        WidgetDiagnostics.log("receiver ${javaClass.simpleName}: ${intent.action} -> Glance default")
        super.onReceive(context, intent)
    }
}

/** Receiver registered in the manifest for the compact entry. */
class SumiCompactWidgetReceiver : SumiWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CompactScreenTimeWidget()
}

class ExpandedScreenTimeWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(SIZE_4X2_W.dp, SIZE_4X2_H.dp),
            DpSize(SIZE_4X2_W.dp, SIZE_TALL.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshotLoader.load(context)
        WidgetDiagnostics.log("render expanded total=${snapshot.totalMs} view=${snapshot.view::class.simpleName}")
        provideContent { ExpandedWidgetContent(snapshot) }
    }

    private companion object {
        const val SIZE_4X2_W = 250
        const val SIZE_4X2_H = 140
        const val SIZE_TALL = 210
    }
}

/** Receiver registered in the manifest for the expanded entry. */
class SumiExpandedWidgetReceiver : SumiWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ExpandedScreenTimeWidget()
}

/** Refresh hook for every path that may change what a widget shows. */
internal object WidgetUpdater {
    /** Fire-and-forget (app open, resume, 60 s tick, theme change, goal change). */
    fun refreshAsync(context: Context) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                CompactScreenTimeWidget().updateAll(context.applicationContext)
                ExpandedScreenTimeWidget().updateAll(context.applicationContext)
            }
        }
    }

    /** Suspending (collection worker / widget refresh worker, already off the main thread). */
    suspend fun refreshNow(context: Context) {
        CompactScreenTimeWidget().updateAll(context.applicationContext)
        ExpandedScreenTimeWidget().updateAll(context.applicationContext)
    }
}
