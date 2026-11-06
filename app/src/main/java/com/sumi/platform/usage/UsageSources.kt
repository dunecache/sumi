package com.sumi.platform.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import com.sumi.domain.usage.DailyAppAggregate
import com.sumi.domain.usage.RawUsageEvent
import com.sumi.domain.usage.UsageEventSource
import com.sumi.domain.usage.UsageEventType
import com.sumi.domain.usage.UsageStatsSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Platform adapter for `UsageStatsManager` / `UsageEvents` (ARCHITECTURE.md §4.1).
 * Maps platform constants to domain [RawUsageEvent]; no domain code knows Android types.
 *
 * Two APIs, two purposes: `UsageStats` for historical daily totals,
 * `UsageEvents` for recent session reconstruction (retained only days).
 */
@Singleton
class PlatformUsageSources @Inject constructor(
    @ApplicationContext private val context: Context,
) : UsageEventSource, UsageStatsSource {

    private val manager: UsageStatsManager
        get() = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    override suspend fun loadRawEvents(fromUtcMs: Long, toUtcMs: Long): List<RawUsageEvent> =
        withContext(Dispatchers.IO) {
            val events = manager.queryEvents(fromUtcMs, toUtcMs)
            val out = mutableListOf<RawUsageEvent>()
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val type = mapType(event.eventType) ?: continue
                val pkg = when (type) {
                    UsageEventType.FOREGROUND,
                    UsageEventType.BACKGROUND,
                    -> event.packageName ?: ""
                    UsageEventType.SCREEN_INTERACTIVE,
                    UsageEventType.SCREEN_NON_INTERACTIVE,
                    -> ""
                }
                out += RawUsageEvent(pkg, type, event.timeStamp)
            }
            out
        }

    override suspend fun loadDailyAggregates(
        fromUtcMs: Long,
        toUtcMs: Long,
        zone: ZoneId,
    ): List<DailyAppAggregate> = withContext(Dispatchers.IO) {
        // `queryUsageStats` with daily interval requires minSdk 22+; guard for older devices.
        val interval = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UsageStatsManager.INTERVAL_BEST
        } else {
            UsageStatsManager.INTERVAL_DAILY
        }
        val stats = manager.queryUsageStats(interval, fromUtcMs, toUtcMs)
        stats
            .filter { it.packageName.isNotBlank() && it.totalTimeInForeground > 0 }
            .map { stat ->
                val dateKey = com.sumi.domain.common.DateKey.of(stat.lastTimeStamp, zone)
                DailyAppAggregate(dateKey, stat.packageName, stat.totalTimeInForeground)
            }
    }

    private fun mapType(androidType: Int): UsageEventType? = when (androidType) {
        UsageEvents.Event.MOVE_TO_FOREGROUND -> UsageEventType.FOREGROUND
        UsageEvents.Event.MOVE_TO_BACKGROUND -> UsageEventType.BACKGROUND
        UsageEvents.Event.SCREEN_INTERACTIVE -> UsageEventType.SCREEN_INTERACTIVE
        UsageEvents.Event.SCREEN_NON_INTERACTIVE -> UsageEventType.SCREEN_NON_INTERACTIVE
        else -> null // ignore standby, config change, etc.
    }
}
