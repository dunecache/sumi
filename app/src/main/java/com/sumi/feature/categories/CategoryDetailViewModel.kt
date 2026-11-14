package com.sumi.feature.categories

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumi.core.database.dao.ApplicationDao
import com.sumi.core.database.entity.AppCategory
import com.sumi.core.database.entity.Application
import com.sumi.core.designsystem.component.DayBar
import com.sumi.core.usage.dayBars
import com.sumi.core.usage.rangeCaption
import com.sumi.core.usage.topApps
import com.sumi.data.categories.CategoryRepository
import com.sumi.domain.common.Clock
import com.sumi.domain.common.DateKey
import com.sumi.domain.common.TimeZoneProvider
import com.sumi.domain.insights.InsightsRange
import com.sumi.domain.insights.NeutralComparison
import com.sumi.domain.usage.DailyAppUsage
import com.sumi.domain.usage.UsageRepository
import com.sumi.platform.applications.AppIconLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn


/** One app inside the category, with its range usage (and share of tracked time). */
data class CategoryAppRow(
    val packageName: String,
    val displayName: String,
    val rangeMs: Long,
    val shareOfTracked: Float?,
)

data class CategoryDetailUiState(
    val isLoading: Boolean = true,
    /** False when the category id no longer exists (e.g. deleted while this screen was open). */
    val exists: Boolean = true,
    val categoryId: String = "",
    val name: String = "",
    val colorToken: String = "matcha-4",
    /** The range this detail is showing; carried from the caller (Insights) or defaults to the week. */
    val range: InsightsRange = InsightsRange.DEFAULT,
    /** "Nov 8 – Nov 14, 2026" etc. */
    val rangeCaption: String = "",
    /** Σ usage of member apps in the range. */
    val rangeTotalMs: Long = 0L,
    /** Share of the range's *tracked* (per-app) usage; the documented denominator. */
    val rangeShare: Float = 0f,
    /** Neutral comparison vs the equivalent previous window; omitted for TODAY or thin history. */
    val comparison: String? = null,
    /** Per-day series across the range, oldest first. */
    val series: List<Long> = List(7) { 0L },
    val dayBars: List<DayBar> = emptyList(),
    /** Spoken day titles aligned with [series] ("Friday, Nov 13"), for the most/quietest line. */
    val dayLabels: List<String> = emptyList(),
    /** Index into [series] of the most active day; null when the range recorded nothing. */
    val mostActiveDayIndex: Int? = null,
    /** Index into [series] of the quietest day; null when the range recorded nothing. */
    val quietestDayIndex: Int? = null,
    /** All apps assigned to the category (installed or not), ranked by range usage. */
    val apps: List<CategoryAppRow> = emptyList(),
)

@HiltViewModel
class CategoryDetailViewModel @Inject constructor(
    private val repository: UsageRepository,
    categoryRepository: CategoryRepository,
    applicationDao: ApplicationDao,
    private val clock: Clock,
    private val timeZone: TimeZoneProvider,
    iconLoader: AppIconLoader,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Platform icon resolution for rows; the UiState itself stays pure data. */
    val iconLoader: AppIconLoader = iconLoader

    private val categoryId: String = savedStateHandle.get<String>("categoryId").orEmpty()
    private val range: InsightsRange = InsightsRange.fromStorage(savedStateHandle.get<String>("range"))

    val uiState: StateFlow<CategoryDetailUiState> = run {
        val zone = timeZone.currentZone()
        val now = clock.nowUtcMs()
        val rangeKeys = range.dateKeys(zone, now)
        val comparisonKeys = range.comparisonKeys(zone, now)
        val todayKey = DateKey.of(now, zone)
        combine(
            categoryRepository.observeCategories(),
            applicationDao.observeByCategory(categoryId),
            repository.observeDailyAppUsageRange(rangeKeys + comparisonKeys),
        ) { categories, apps, combinedUsage ->
            deriveState(
                categoryId = categoryId,
                range = range,
                rangeKeys = rangeKeys,
                comparisonKeys = comparisonKeys,
                todayKey = todayKey,
                categories = categories,
                apps = apps,
                combinedUsage = combinedUsage,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryDetailUiState())
}

/**
 * Pure derivation, kept internal for tests. [combinedUsage] must cover rangeKeys + comparisonKeys;
 * the two windows are split here by date key. Reassignment follows the read-time derivation
 * policy (ARCHITECTURE.md §13): the *current* assignment decides membership, and the untouched
 * raw records decide the numbers — so reassigning apps corrects history everywhere.
 */
internal fun deriveState(
    categoryId: String,
    range: InsightsRange,
    rangeKeys: List<String>,
    comparisonKeys: List<String>,
    todayKey: String,
    categories: List<AppCategory>,
    apps: List<Application>,
    combinedUsage: List<DailyAppUsage>,
): CategoryDetailUiState {
    val category = categories.firstOrNull { it.id == categoryId }
    if (category == null) {
        // Category was deleted (or the id is stale) while this screen was open.
        return CategoryDetailUiState(isLoading = false, exists = false, categoryId = categoryId)
    }
    val rangeKeySet = rangeKeys.toSet()
    val comparisonKeySet = comparisonKeys.toSet()
    val rangeUsage = combinedUsage.filter { it.dateKey in rangeKeySet }
    val comparisonUsage = combinedUsage.filter { it.dateKey in comparisonKeySet }

    // Members are decided by the *current* assignment: only apps whose categoryId matches
    // belong to this category's numbers. A reassigned app therefore leaves the old
    // category immediately, and uncategorized apps never leak into any category bucket.
    val members = apps.filter { it.categoryId == categoryId }
    val memberPackages = members.mapTo(HashSet()) { it.packageName }

    // Single pass per window: per-package totals, then member-filtered sums.
    val msByPkg = HashMap<String, Long>()
    val msByDayAndPkg = HashMap<String, HashMap<String, Long>>()
    for (row in rangeUsage) {
        msByPkg[row.packageName] = (msByPkg[row.packageName] ?: 0L) + row.durationMs
        val dayMap = msByDayAndPkg.getOrPut(row.dateKey) { HashMap() }
        dayMap[row.packageName] = (dayMap[row.packageName] ?: 0L) + row.durationMs
    }
    val trackedRange = rangeUsage.sumOf { it.durationMs }
    val total = memberPackages.sumOf { msByPkg[it] ?: 0L }

    val perDay = rangeKeys.map { key ->
        val dayMap = msByDayAndPkg[key].orEmpty()
        memberPackages.sumOf { dayMap[it] ?: 0L }
    }
    val totalsByKey = rangeKeys.zip(perDay).toMap()

    val cmpMsByPkg = HashMap<String, Long>()
    for (row in comparisonUsage) {
        cmpMsByPkg[row.packageName] = (cmpMsByPkg[row.packageName] ?: 0L) + row.durationMs
    }
    val previousTotal = memberPackages.sumOf { cmpMsByPkg[it] ?: 0L }
    val previousWindowRecorded = comparisonUsage.isNotEmpty()
    val comparison = if (range == InsightsRange.TODAY) {
        // A partial today is never compared against a complete day.
        null
    } else if (previousWindowRecorded) {
        NeutralComparison.neutralComparison(total, previousTotal, range.comparisonLabel)
    } else {
        null
    }

    val rangeTotal = total
    val bars = dayBars(rangeKeys, totalsByKey, todayKey)
    val hasData = perDay.any { it > 0L }
    val mostActive = if (hasData) perDay.indices.maxByOrNull { perDay[it] } else null
    val quietest = if (hasData) perDay.indices.minByOrNull { perDay[it] } else null
    val dayTitles = rangeKeys.map { key ->
        val date = java.time.LocalDate.parse(key)
        date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) + ", " +
            date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.getDefault()))
    }

    val rows = topApps(rangeUsage.filter { it.packageName in memberPackages }, members, limit = Int.MAX_VALUE)
        .map { app ->
            CategoryAppRow(
                packageName = app.packageName,
                displayName = app.displayName,
                rangeMs = app.durationMs,
                shareOfTracked = if (trackedRange > 0L && app.durationMs > 0L) {
                    app.durationMs.toFloat() / trackedRange.toFloat()
                } else {
                    null
                },
            )
        }

    return CategoryDetailUiState(
        isLoading = false,
        exists = true,
        categoryId = category.id,
        name = category.name,
        colorToken = category.colorToken,
        range = range,
        rangeCaption = rangeCaption(rangeKeys),
        rangeTotalMs = rangeTotal,
        rangeShare = if (trackedRange > 0L) rangeTotal.toFloat() / trackedRange.toFloat() else 0f,
        comparison = comparison,
        series = perDay,
        dayBars = bars,
        dayLabels = dayTitles,
        mostActiveDayIndex = mostActive,
        quietestDayIndex = quietest,
        apps = rows,
    )
}
