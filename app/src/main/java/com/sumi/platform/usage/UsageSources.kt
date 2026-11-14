package com.sumi.platform.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import android.annotation.SuppressLint
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
                    UsageEventType.DEVICE_SHUTDOWN,
                    UsageEventType.DEVICE_STARTUP,
                    -> "" // not app-scoped events
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
        // Per-day queries: INTERVAL_BEST/DAILY over a multi-day range returns one row
        // per package spanning the whole range, which would misattribute totals to a
        // single day. Query one calendar day at a time and stamp each with its dateKey.
        // Some builds return buckets overlapping the queried range (neighbouring days,
        // duplicated rows): keep only buckets whose `firstTimeStamp` lies inside the
        // day — queried-day attribution is then exact, and the repository merges
        // duplicate rows per (day, package) with a sum.
        val out = mutableListOf<DailyAppAggregate>()
        var oldestWithData: String? = null
        var dayStart = com.sumi.domain.common.DateKey.startOfDayUtcMs(
            com.sumi.domain.common.DateKey.of(fromUtcMs, zone), zone)
        while (dayStart < toUtcMs) {
            val dateKey = com.sumi.domain.common.DateKey.of(dayStart, zone)
            val dayEnd = minOf(
                com.sumi.domain.common.DateKey.endOfDayUtcMsExclusive(dateKey, zone),
                toUtcMs,
            )
            val before = out.size
            val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, dayStart, dayEnd)
            stats
                .filter {
                    it.packageName.isNotBlank() && it.totalTimeInForeground > 0 &&
                        it.firstTimeStamp >= dayStart && it.firstTimeStamp < dayEnd
                }
                .mapTo(out) { stat ->
                    DailyAppAggregate(dateKey, stat.packageName, stat.totalTimeInForeground)
                }
            if (out.size > before && oldestWithData == null) {
                oldestWithData = dateKey
                Log.d(TAG, "UsageStats import: oldest retained day $dateKey")
            }
            if (dayEnd <= dayStart) break // defensive: never loop forever on zero-length day
            dayStart = dayEnd
        }
        out
    }

    // NewApi: the API 29 constants are compile-time inlined, so they are safe to
    // reference below API 29 (the events are simply absent before API 29).
    @SuppressLint("NewApi")
    private fun mapType(androidType: Int): UsageEventType? = when (androidType) {
        UsageEvents.Event.MOVE_TO_FOREGROUND -> UsageEventType.FOREGROUND
        UsageEvents.Event.MOVE_TO_BACKGROUND -> UsageEventType.BACKGROUND
        UsageEvents.Event.SCREEN_INTERACTIVE -> UsageEventType.SCREEN_INTERACTIVE
        UsageEvents.Event.SCREEN_NON_INTERACTIVE -> UsageEventType.SCREEN_NON_INTERACTIVE
        // API 29+: constants are compile-time inlined, so referencing them is safe on
        // older devices (the events are simply absent before API 29).
        UsageEvents.Event.DEVICE_SHUTDOWN -> UsageEventType.DEVICE_SHUTDOWN
        UsageEvents.Event.DEVICE_STARTUP -> UsageEventType.DEVICE_STARTUP
        else -> null // ignore standby, config change, etc.
    }

    private companion object {
        const val TAG = "SumiUsage"
    }
}
