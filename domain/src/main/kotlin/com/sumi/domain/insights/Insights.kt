package com.sumi.domain.insights

import com.sumi.domain.usage.Aggregation
import com.sumi.domain.usage.DailyUsage
import com.sumi.domain.usage.HourlyUsage
import com.sumi.domain.usage.UsageSession
import java.time.ZoneId

/**
 * Rule-based personal observations (M3). Deterministic, unit-tested, no free-text AI.
 * Facts are stated plainly; interpretations are hedged and flagged (PRINCIPLES.md §4).
 */
enum class InsightKind { FACT, INTERPRETATION }

data class Insight(
    val kind: InsightKind,
    val text: String,
)

data class InsightsSummary(
    val insights: List<Insight>,
    val estimatedSessions: Int,
    val longestSessionMs: Long,
    val pickupsEstimate: Int?, // null when not reliably measurable
    val totalSessions: Int,
)

object InsightsEngine {

    /**
     * Deterministic rules over sessions + aggregates in [zone]. No psychological claims.
     * [includeAverage] and [includeTimeOfDay] default to true; callers that surface the
     * same figures in a dedicated overview/patterns block pass false so no number appears
     * twice with two different windows.
     */
    fun summarize(
        sessions: List<UsageSession>,
        dailyTotals: List<DailyUsage>,
        hourly: List<HourlyUsage>,
        zone: ZoneId,
        nowUtcMs: Long,
        /** Trailing-week total for the average figure (zeros included) and its window. */
        windowTotalMs: Long,
        windowDays: Int,
        includeAverage: Boolean = true,
        includeTimeOfDay: Boolean = true,
    ): InsightsSummary {
        val insights = mutableListOf<Insight>()
        val durations = sessions.map { it.durationMs(nowUtcMs) }
        val estimatedSessions = sessions.count { it.isEstimated }
        val longest = durations.maxOrNull() ?: 0L
        val totalSessions = sessions.size

        if (totalSessions > 0) {
            insights += Insight(
                InsightKind.FACT,
                "You had $totalSessions sessions in this period.",
            )
        }
        if (longest > 0) {
            insights += Insight(
                InsightKind.FACT,
                "Your longest uninterrupted session was ${format(longest)}.",
            )
        }
        // Window days of 1 make the "average" identical to the total — the overview owns
        // that figure; repeating it here would say the same thing twice.
        if (includeAverage && windowDays > 1) {
            // Window is stated in the copy; zeros are included — a quiet day is still a day.
            val avg = Averages.dailyAverage(windowTotalMs, windowDays)
            insights += Insight(
                InsightKind.FACT,
                "Your average day was ${format(avg)}, across the last $windowDays days.",
            )
        }
        // Time-of-day rhythm: peak hour from hourly buckets.
        val peakHour = if (includeTimeOfDay) {
            hourly
                .groupBy { it.hour }
                .mapValues { (_, rows) -> rows.sumOf { it.durationMs } }
                .maxByOrNull { it.value }
                ?.key
        } else {
            null
        }
        if (peakHour != null) {
            insights += Insight(
                InsightKind.FACT,
                "Most of your usage happened around ${peakHour}:00.",
            )
            // Interpretation, hedged and optional.
            val late = peakHour in 21..23
            if (late) {
                insights += Insight(
                    InsightKind.INTERPRETATION,
                    "This might be an evening wind-down pattern.",
                )
            }
        }
        // Pickups are only reported where reliably measurable — here estimated from session starts.
        // We omit when evidence is thin (< 7 days) per roadmap graceful-degradation rule.
        val pickups = if (dailyTotals.size >= 7) {
            totalSessions.coerceAtLeast(0)
        } else {
            null
        }
        if (pickups != null) {
            insights += Insight(
                InsightKind.FACT,
                "You picked up your phone about $pickups times (estimated).",
            )
        }

        return InsightsSummary(
            insights = insights,
            estimatedSessions = estimatedSessions,
            longestSessionMs = longest,
            pickupsEstimate = pickups,
            totalSessions = totalSessions,
        )
    }

    private fun format(ms: Long): String {
        val minutes = ms / 60_000L
        val hours = minutes / 60
        val rem = minutes % 60
        return if (hours > 0) "${hours}h ${rem}m" else "${rem}m"
    }
}
