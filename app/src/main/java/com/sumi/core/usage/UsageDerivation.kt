package com.sumi.core.usage

import com.sumi.core.database.entity.AppCategory
import com.sumi.core.database.entity.Application
import com.sumi.core.designsystem.component.CategorySegment
import com.sumi.core.designsystem.component.DayBar
import com.sumi.core.designsystem.component.formatDuration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import com.sumi.data.categories.DefaultCategories
import com.sumi.domain.categories.AppUsage
import com.sumi.domain.categories.CategoryAnalytics
import com.sumi.domain.usage.DailyAppUsage
import com.sumi.domain.usage.HourlyUsage

/**
 * Shared usage-derivation logic for the feature screens (Today, Insights, details).
 * One implementation of the ranking, category-segment and hourly-bucket math — screens
 * must not re-implement variants (they previously diverged, e.g. 26% vs 27% shares).
 * Everything here is deterministic and free of Android UI types.
 */

/** A ranked application with a friendly display label. */
data class TopUsedApp(
    val packageName: String,
    val displayName: String,
    val durationMs: Long,
)

/**
 * Top most-used apps (navigation/Today reference §3.5): duration descending with an
 * alphabetical tiebreak, friendly display labels, and package-tail disambiguation when
 * two packages resolve to the same display name. Denominator for shares is the caller's
 * concern — pass the range's tracked total, never just the top five.
 */
fun topApps(
    perApp: List<DailyAppUsage>,
    applications: List<Application>,
    limit: Int = 5,
): List<TopUsedApp> {
    if (perApp.isEmpty()) return emptyList()
    // Aggregate same package across days — callers may pass several days.
    val byPackage = HashMap<String, Long>()
    for (usage in perApp) {
        byPackage[usage.packageName] = (byPackage[usage.packageName] ?: 0L) + usage.durationMs
    }
    val labels = applications.associate { it.packageName to it.displayName }
    val names = byPackage.mapValues { (pkg, _) -> labels[pkg] ?: pkg.substringAfterLast('.') }
    val nameCounts = names.values.groupingBy { it }.eachCount()
    return byPackage.entries
        .sortedWith(
            compareByDescending<Map.Entry<String, Long>> { it.value }
                .thenBy { names.getValue(it.key) },
        )
        .take(limit)
        .map { entry ->
            val name = names.getValue(entry.key)
            val display = if (nameCounts.getValue(name) > 1) {
                "$name (${disambiguationSuffix(entry.key, name)})"
            } else {
                name
            }
            TopUsedApp(entry.key, display, entry.value)
        }
}

/** Package-tail suffix per §3.5; falls back to the parent segment (or "system") when the tail equals the name. */
private fun disambiguationSuffix(packageName: String, name: String): String {
    val tail = packageName.substringAfterLast('.')
    if (!tail.equals(name, ignoreCase = true)) return tail
    val head = packageName.substringBeforeLast('.')
    return head.substringAfterLast('.', "system")
}

/**
 * Derives the display segments for the category bar from per-app usage and the current
 * assignment (ARCHITECTURE.md §13 read-time derivation). Seeded defaults act as a
 * fallback name/color source so the bar still renders before [CategoryRepository] has
 * finished seeding. Uncategorized (categoryId == null) is always the residual bucket.
 */
fun categorySegments(
    perApp: List<DailyAppUsage>,
    applications: List<Application>,
    categories: List<AppCategory>,
): List<CategorySegment> {
    if (perApp.isEmpty()) return emptyList()
    val fallbackNames = DefaultCategories.ALL.associate { it.id to it.name }
    val fallbackColors = DefaultCategories.ALL.associate { it.id to it.colorToken }
    val nameById = fallbackNames + categories.associate { it.id to it.name }
    val colorById = fallbackColors + categories.associate { it.id to it.colorToken }
    val assignment = applications.associate { it.packageName to it.categoryId }
    val breakdown = CategoryAnalytics.breakdown(
        perApp = perApp.map { AppUsage(it.packageName, it.durationMs) },
        categoryNames = nameById,
        assignment = assignment,
    )
    val total = breakdown.totalDurationMs
    return breakdown.categories.map { usage ->
        CategorySegment(
            categoryId = usage.categoryId,
            name = usage.name,
            durationMs = usage.durationMs,
            share = if (total > 0L) usage.durationMs.toFloat() / total.toFloat() else 0f,
            colorToken = usage.categoryId?.let { colorById[it] } ?: "stone-1",
        )
    }
}

/**
 * Sums per-app hourly buckets into 24 wall-clock totals for the timeline strip.
 * Hour values from aggregation are always 0–23 (zone `hour`); values outside that range
 * are clamped defensively so malformed rows can never crash rendering.
 */
fun hourlyTotals(hourly: List<HourlyUsage>): List<Long> {
    val totals = MutableList(HOURS_PER_DAY) { 0L }
    for (row in hourly) {
        val index = row.hour.coerceIn(0, HOURS_PER_DAY - 1)
        totals[index] += row.durationMs.coerceAtLeast(0L)
    }
    return totals
}

/** Tracked time of a window: the Σ per-app usage, i.e. the documented denominator. */
fun trackedMs(perApp: List<DailyAppUsage>): Long = perApp.sumOf { it.durationMs }

/**
 * Counts the window days that hold any recorded data (a daily-total row, a per-app row,
 * an hourly bucket or a session). Days before Sumi started tracking are absent, not
 * zero — the caller surfaces this honestly instead of printing confident zeros.
 */
fun coverageCount(dateKeys: List<String>, daysWithData: Set<String>): Int =
    dateKeys.count { it in daysWithData }

private const val HOURS_PER_DAY = 24

/**
 * The shared day-by-day bars for any range. The current day is always partial
 * (`isPartial`), zero-usage days are legitimate bars of zero. Label cadence adapts:
 * weekday labels up to 7 days, day-of-month beyond.
 */
fun dayBars(
    keys: List<String>,
    totalsByKey: Map<String, Long>,
    todayKey: String,
): List<DayBar> {
    val total = keys.size
    return keys.map { key ->
        val date = LocalDate.parse(key)
        val value = totalsByKey[key] ?: 0L
        val isPartial = key == todayKey
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        val spoken = if (isPartial) {
            "$weekday, ${formatDuration(value)}, so far"
        } else {
            "$weekday, ${formatDuration(value)}"
        }
        val label = if (total > 7) {
            date.dayOfMonth.toString()
        } else {
            date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2)
        }
        DayBar(label = label, valueMs = value, isPartial = isPartial, contentDescription = spoken)
    }
}

/** Headline date-range caption, e.g. "Nov 8 – Nov 14, 2026" or "Saturday, Nov 14, 2026". */
fun rangeCaption(keys: List<String>): String {
    val first = LocalDate.parse(keys.first())
    val last = LocalDate.parse(keys.last())
    return if (keys.size == 1) {
        first.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) + ", " + first.format(DAY_FULL)
    } else if (first.year == last.year) {
        "${first.format(DAY_SHORT)} – ${last.format(DAY_FULL)}"
    } else {
        "${first.format(DAY_FULL)} – ${last.format(DAY_FULL)}"
    }
}


private val DAY_FULL: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy").withLocale(Locale.getDefault())

private val DAY_SHORT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d").withLocale(Locale.getDefault())
