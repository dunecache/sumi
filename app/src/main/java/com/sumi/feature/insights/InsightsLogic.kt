package com.sumi.feature.insights

import com.sumi.core.designsystem.component.CategorySegment
import com.sumi.core.designsystem.component.DayBar
import com.sumi.core.usage.TopUsedApp
import com.sumi.core.usage.topApps
import com.sumi.core.usage.categorySegments
import com.sumi.core.usage.coverageCount
import com.sumi.core.usage.hourlyTotals
import com.sumi.core.usage.trackedMs
import com.sumi.domain.insights.Averages
import com.sumi.domain.insights.InsightsRange
import com.sumi.domain.insights.NeutralComparison
import com.sumi.domain.usage.DailyAppUsage
import com.sumi.domain.usage.DailyUsage
import com.sumi.domain.usage.HourlyUsage
import com.sumi.core.database.entity.AppCategory
import com.sumi.core.database.entity.Application
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pure derivation for the Insights screen (feature logic, kept internal and unit-tested).
 * The ViewModel only wires flows; every rule that shapes a number lives here.
 */

/** Hour buckets (5 through 11 inclusive) treated as morning. */
private val MORNING_HOURS = 5..11

/** Hour buckets (17 through 22 inclusive) treated as evening. */
private val EVENING_HOURS = 17..22

/** Minimum days of recorded data before pattern observations are shown. */
const val MIN_PATTERN_COVERAGE_DAYS = 3

/** Minimum uncategorized share of tracked time (15%) before the quiet review card shows. */
const val UNCATEGORIZED_PROMPT_SHARE = 0.15f

/** One inspectable day for the tap-a-bar panel. */
data class DayDetail(
    val dateKey: String,
    val title: String,
    val totalMs: Long,
    val isPartial: Boolean,
    val trackedMs: Long,
    val segments: List<CategorySegment>,
)

/** Derived pattern numbers; every field is directly testable. */
data class PatternFacts(
    /** Busiest hour across the range (0–23), or null when nothing was recorded. */
    val busiestHour: Int?,
    val busiestHourMs: Long,
    val morningMs: Long,
    val eveningMs: Long,
    val weekdayMs: Long,
    val weekendMs: Long,
    val weekdayDays: Int,
    val weekendDays: Int,
)

/** Socially-categorized numbers for the "Time in social apps" card. */
data class SocialFacts(
    /** Display name of the Social category (user-renamed names are honored). */
    val name: String,
    val totalMs: Long,
    val share: Float,
    /** Per-day series across the range for the trend strip. */
    val dailyMs: List<Long>,
    val topApps: List<TopUsedApp>,
    /** Distinct Social apps used this range beyond the [topApps] shown — the remainder the card must account for. */
    val moreApps: Int = 0,
    /** Neutral comparison vs the equivalent previous window; null for TODAY or thin history. */
    val comparison: String?,
)

/** Everything the screen needs, derived from raw range inputs. */
data class InsightsDerived(
    val totalMs: Long,
    /** Average per day with zeros included; null for 1-day windows (the total IS the average). */
    val averageMs: Long?,
    /** Bar average rule; equals [averageMs] when present, else null. */
    val averageMsForBars: Long?,
    val comparison: String?,
    val trackedMs: Long,
    val dailyTotals: List<DayBar>,
    val dailyDetails: List<DayDetail>,
    val categories: List<CategorySegment>,
    val uncategorizedMs: Long,
    val uncategorizedShare: Float,
    val topApps: List<TopUsedApp>,
    val hourly: List<Long>,
    val patterns: PatternFacts,
    val social: SocialFacts?,
    val coverageDays: Int,
    val rangeCaption: String,
)

private val CAPTION_DAY_SHORT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d").withLocale(Locale.getDefault())

/** Pattern facts over the aggregated hourly buckets and per-day totals in the window. */
fun patternFacts(
    keys: List<String>,
    hourly: List<HourlyUsage>,
    dailyTotals: Map<String, Long>,
): PatternFacts {
    val buckets = hourlyTotals(hourly)
    val busiest = buckets.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0L }
    val morning = buckets.withIndex().filter { it.index in MORNING_HOURS }.sumOf { it.value }
    val evening = buckets.withIndex().filter { it.index in EVENING_HOURS }.sumOf { it.value }
    var weekdayMs = 0L
    var weekendMs = 0L
    var weekdayDays = 0
    var weekendDays = 0
    for (key in keys) {
        val date = LocalDate.parse(key)
        val total = dailyTotals[key] ?: 0L
        if (date.dayOfWeek == java.time.DayOfWeek.SATURDAY || date.dayOfWeek == java.time.DayOfWeek.SUNDAY) {
            weekendMs += total
            weekendDays += 1
        } else {
            weekdayMs += total
            weekdayDays += 1
        }
    }
    return PatternFacts(
        busiestHour = busiest?.index,
        busiestHourMs = busiest?.value ?: 0L,
        morningMs = morning,
        eveningMs = evening,
        weekdayMs = weekdayMs,
        weekendMs = weekendMs,
        weekdayDays = weekdayDays,
        weekendDays = weekendDays,
    )
}

/**
 * Social-lens facts. Hidden entirely when the Social category no longer exists (the user
 * deleted it) — it is a lens on the category system, never a parallel one. When the
 * category exists but holds no rows this range, the card renders a quiet zero-state line
 * rather than fabricating a trend. Membership is the CURRENT assignment applied to the
 * untouched raw history — the same read-time derivation as everywhere else
 * (ARCHITECTURE.md §13).
 */
fun socialFacts(
    range: InsightsRange,
    rangeKeys: List<String>,
    trackedMs: Long,
    perApp: List<DailyAppUsage>,
    comparisonPerApp: List<DailyAppUsage>,
    comparisonRecorded: Boolean,
    applications: List<Application>,
    categories: List<AppCategory>,
): SocialFacts? {
    val socialCategory = categories.firstOrNull { it.id == com.sumi.domain.categories.DefaultPackageCategories.SOCIAL }
        ?: return null
    val socialPackages = applications.filter { it.categoryId == socialCategory.id }
        .mapTo(HashSet()) { it.packageName }
    val rangeRows = perApp.filter { it.packageName in socialPackages }
    val total = rangeRows.sumOf { it.durationMs }
    val perDay = rangeKeys.map { key ->
        rangeRows.filter { it.dateKey == key }.sumOf { it.durationMs }
    }
    val top = topApps(rangeRows, applications, limit = 3)
    val moreApps = (rangeRows.mapTo(HashSet()) { it.packageName }.size - top.size).coerceAtLeast(0)
    val comparison = if (range == InsightsRange.TODAY) {
        null
    } else if (comparisonRecorded) {
        val cmpTotal = comparisonPerApp.filter { it.packageName in socialPackages }.sumOf { it.durationMs }
        NeutralComparison.neutralComparison(total, cmpTotal, range.comparisonLabel)
    } else {
        null
    }
    return SocialFacts(
        name = socialCategory.name,
        totalMs = total,
        share = if (trackedMs > 0L) total.toFloat() / trackedMs.toFloat() else 0f,
        dailyMs = perDay,
        topApps = top,
        moreApps = moreApps,
        comparison = comparison,
    )
}

/** Coverage = days with any recorded row (aggregate, per-app, hourly or session). */
fun coverageDays(
    keys: List<String>,
    dailyTotals: List<DailyUsage>,
    perApp: List<DailyAppUsage>,
    hourly: List<HourlyUsage>,
    sessionDays: Set<String>,
): Int {
    val present = HashSet<String>()
    dailyTotals.forEach { present += it.dateKey }
    perApp.forEach { present += it.dateKey }
    hourly.forEach { present += it.dateKey }
    present += sessionDays
    return coverageCount(keys, present)
}

/**
 * Builds the neutral comparison line for the headline.
 * - [InsightsRange.TODAY]: [previousMs] must be the yesterday window clipped to the same
 *   local wall-clock time (session granularity, supplied by the caller); null → omitted.
 * - Complete windows: [previousWindowRecorded] (any daily row in the previous window)
 *   enables a zeros-included comparison; otherwise the line is omitted.
 */
fun headlineComparison(
    range: InsightsRange,
    currentMs: Long,
    previousMs: Long?,
    previousWindowRecorded: Boolean,
): String? = when {
    range == InsightsRange.TODAY ->
        previousMs?.let { NeutralComparison.neutralComparison(currentMs, it, range.comparisonLabel) }
    previousWindowRecorded ->
        NeutralComparison.neutralComparison(currentMs, previousMs ?: 0L, range.comparisonLabel)
    else -> null
}

/** Sum of daily totals; days missing a row count as zero. */
fun windowTotalMs(keys: List<String>, totalsByKey: Map<String, Long>): Long =
    keys.sumOf { totalsByKey[it] ?: 0L }

/** Coverage note under the overview; only when history is thinner than the window. */
fun coverageNote(coverageDays: Int, windowDays: Int): String? =
    if (coverageDays in 1 until windowDays) {
        "Based on the $coverageDays days Sumi has recorded in this range."
    } else {
        null
    }

/** Whole-percent share of the window total (single shared rounding rule). */
fun windowPercent(valueMs: Long, totalMs: Long): Int = Averages.percentOf(valueMs, totalMs)

/** Derived category usage per day; used by the tap-a-bar inspector. */
fun dayDetails(
    keys: List<String>,
    totalsByKey: Map<String, Long>,
    perApp: List<DailyAppUsage>,
    applications: List<Application>,
    categories: List<AppCategory>,
    todayKey: String,
): List<DayDetail> {
    return keys.map { key ->
        val rows = perApp.filter { it.dateKey == key }
        val date = LocalDate.parse(key)
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        DayDetail(
            dateKey = key,
            title = "$weekday, ${date.format(CAPTION_DAY_SHORT)}",
            totalMs = totalsByKey[key] ?: 0L,
            isPartial = key == todayKey,
            trackedMs = trackedMs(rows),
            segments = categorySegments(rows, applications, categories).filter { it.durationMs > 0L },
        )
    }
}
