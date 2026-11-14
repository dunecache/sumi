package com.sumi.feature.insights

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumi.core.database.dao.ApplicationDao
import com.sumi.core.designsystem.component.CategorySegment
import com.sumi.data.categories.CategoryRepository
import com.sumi.domain.common.Clock
import com.sumi.domain.common.DateKey
import com.sumi.domain.common.TimeZoneProvider
import com.sumi.domain.insights.Insight
import com.sumi.domain.insights.InsightsEngine
import com.sumi.domain.insights.InsightsRange
import com.sumi.domain.usage.DailyAppUsage
import com.sumi.domain.usage.DailyUsage
import com.sumi.domain.usage.HourlyUsage
import com.sumi.domain.usage.IntervalMath
import com.sumi.domain.usage.UsageRepository
import com.sumi.domain.usage.UsageSession
import com.sumi.core.usage.dayBars
import com.sumi.core.usage.rangeCaption
import com.sumi.core.usage.trackedMs
import com.sumi.platform.usage.UsageAccessChecker
import com.sumi.platform.applications.AppIconLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** Range-aware Insights UI state; see InsightsRange for window semantics. */
data class InsightsUiState(
    val isLoading: Boolean = true,
    /** False → Insights renders the full permission-missing state, never zeros. */
    val hasUsageAccess: Boolean = true,
    val range: InsightsRange = InsightsRange.DEFAULT,
    /** "Nov 8 – Nov 14, 2026" or "Saturday, Nov 14, 2026". */
    val rangeCaption: String = "",
    val totalMs: Long = 0L,
    /** Average per day with zeros included; null for 1-day windows. */
    val averageMs: Long? = null,
    /** Neutral previous-period line, or null when omitted (see comparison rules). */
    val comparison: String? = null,
    /** Σ per-app usage of the range — the documented denominator for every share. */
    val trackedMs: Long = 0L,
    val dailyTotals: List<com.sumi.core.designsystem.component.DayBar> = emptyList(),
    /** Same value as [averageMs], used for the bar rule; null when no average line. */
    val averageMsForBars: Long? = null,
    /** Range category distribution; uncategorized last. */
    val categories: List<CategorySegment> = emptyList(),
    val uncategorizedMs: Long = 0L,
    val uncategorizedShare: Float = 0f,
    val social: SocialFacts? = null,
    val topApps: List<com.sumi.core.usage.TopUsedApp> = emptyList(),
    /** 24 aggregated hour buckets across the range. */
    val hourly: List<Long> = List(24) { 0L },
    val patterns: PatternFacts = PatternFacts(null, 0L, 0L, 0L, 0L, 0L, 0, 0),
    val coverageDays: Int = 0,
    val observations: List<Insight> = emptyList(),
    /** Per-day breakdowns for the tap-a-bar inspector. */
    val dailyDetails: List<DayDetail> = emptyList(),
    val selectedDayIndex: Int? = null,
) {
    /** Days recorded vs window size; drives the honest coverage note. */
    val coverageNote: String? get() = coverageNote(coverageDays, range.windowDays)
}

/**
 * Insights: range-aware analytics. Every number on screen states its window; the range
 * survives navigation to detail screens and process death via the saved-state handle.
 * Nothing renders when Usage Access is revoked (an honest full-state, never zeros).
 */
@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val repository: UsageRepository,
    private val categoryRepository: CategoryRepository,
    private val applicationDao: ApplicationDao,
    private val checker: UsageAccessChecker,
    private val clock: Clock,
    private val timeZone: TimeZoneProvider,
    private val savedStateHandle: SavedStateHandle,
    iconLoader: AppIconLoader,
) : ViewModel() {

    /** Platform icon resolution for row rendering; the UiState itself stays pure data. */
    val iconLoader: AppIconLoader = iconLoader

    private val rangeFlow = MutableStateFlow(InsightsRange.fromStorage(savedStateHandle[RANGE_KEY]))
    private val selectionFlow = MutableStateFlow<Int?>(null)

    val range: StateFlow<InsightsRange> = rangeFlow.asStateFlow()

    fun setRange(range: InsightsRange) {
        savedStateHandle[RANGE_KEY] = range.name
        selectionFlow.value = null
        rangeFlow.value = range
    }

    fun selectDay(index: Int?) {
        selectionFlow.value = index
    }

    val uiState: StateFlow<InsightsUiState> = rangeFlow
        .flatMapLatest { range -> rangeData(range) }
        .combine(selectionFlow) { state, selection ->
            state.copy(selectedDayIndex = selection)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())

    private fun rangeData(range: InsightsRange): Flow<InsightsUiState> {
        val zone = timeZone.currentZone()
        val now = clock.nowUtcMs()
        val keys = range.dateKeys(zone, now)
        val cmpKeys = range.comparisonKeys(zone, now)
        val todayKey = DateKey.of(now, zone)
        val usage = combine(
            repository.observeDailyTotalsRange(keys),
            repository.observeDailyAppUsageRange(keys),
            repository.observeHourlyUsageRange(keys),
        ) { totals, perApp, hourly -> Triple(totals, perApp, hourly) }
        val annotations = combine(
            repository.observeSessionsForDateKeys(keys + cmpKeys),
            categoryRepository.observeCategories(),
            applicationDao.observeInstalled(),
        ) { sessions, categories, applications -> Triple(sessions, categories, applications) }
        val base = combine(usage, annotations) { (totals, perApp, hourly), (sessions, categories, applications) ->
            RangeBase(totals, perApp, hourly, sessions, categories, applications)
        }
        val cmp = combine(
            repository.observeDailyTotalsRange(cmpKeys),
            repository.observeDailyAppUsageRange(cmpKeys),
        ) { totals, perApp -> RangeComparison(totals, perApp) }
        return combine(base, cmp) { b, c ->
            derive(b, c, range, keys, cmpKeys, zone, now, todayKey)
        }
    }

    private fun derive(
        b: RangeBase,
        c: RangeComparison,
        range: InsightsRange,
        keys: List<String>,
        cmpKeys: List<String>,
        zone: java.time.ZoneId,
        now: Long,
        todayKey: String,
    ): InsightsUiState {
        val totalsByKey = b.totals.associate { it.dateKey to it.totalDurationMs }
        val totalMs = windowTotalMs(keys, totalsByKey)
        val tracked = trackedMs(b.perApp)
        // Sessions carry no dateKey; derive their day from the UTC start instant.
        val sessionsByDay = b.sessions.groupBy { DateKey.of(it.startTimeUtcMs, zone) }
        val rangeSessions = keys.flatMap { sessionsByDay[it].orEmpty() }
        val cmpSessions = cmpKeys.flatMap { sessionsByDay[it].orEmpty() }
        val sessionDays = sessionsByDay.keys

        // Previous equivalent window; zeros included when any day was recorded, else omitted.
        val previousWindowRecorded = c.totals.isNotEmpty() || c.perApp.isNotEmpty()
        val previousMs: Long? = when (range) {
            InsightsRange.TODAY -> {
                if (cmpSessions.isEmpty()) {
                    null
                } else {
                    val (from, to) = range.comparisonWindow(zone, now)
                    IntervalMath.unionMsInWindow(cmpSessions, from, to, now)
                }
            }
            else -> c.totals.sumOf { it.totalDurationMs }
        }
        val comparison = headlineComparison(range, totalMs, previousMs, previousWindowRecorded)
        val averageMs = if (range.windowDays > 1) {
            com.sumi.domain.insights.Averages.dailyAverage(totalMs, range.windowDays)
        } else {
            null
        }

        val segments = com.sumi.core.usage.categorySegments(b.perApp, b.applications, b.categories)
        val uncategorized = segments.firstOrNull { it.categoryId == null }
        val social = socialFacts(
            range = range,
            rangeKeys = keys,
            trackedMs = tracked,
            perApp = b.perApp,
            comparisonPerApp = c.perApp,
            comparisonRecorded = previousWindowRecorded,
            applications = b.applications,
            categories = b.categories,
        )
        val dayTotals = dayBars(keys, totalsByKey, todayKey)
        val details = dayDetails(keys, totalsByKey, b.perApp, b.applications, b.categories, todayKey)
        val hourlyBuckets = com.sumi.core.usage.hourlyTotals(b.hourly)

        // Deterministic, tested observations over the *range* (facts plain, interpretations hedged).
        val dailyTotals = keys.mapNotNull { key ->
            totalsByKey[key]?.takeIf { it > 0L }?.let { DailyUsage(key, it, now) }
        }
        val insights = if (dailyTotals.isEmpty() && rangeSessions.isEmpty() && b.hourly.isEmpty()) {
            emptyList()
        } else {
            InsightsEngine.summarize(
                sessions = rangeSessions,
                dailyTotals = dailyTotals,
                hourly = b.hourly,
                zone = zone,
                nowUtcMs = now,
                windowTotalMs = totalMs,
                windowDays = range.windowDays,
                includeAverage = false,
                includeTimeOfDay = false,
            ).insights
        }

        val coverage = coverageDays(keys, b.totals, b.perApp, b.hourly, sessionDays)
        return InsightsUiState(
            isLoading = false,
            hasUsageAccess = checker.hasUsageAccess(),
            range = range,
            rangeCaption = rangeCaption(keys),
            totalMs = totalMs,
            averageMs = averageMs,
            comparison = comparison,
            trackedMs = tracked,
            dailyTotals = dayTotals,
            averageMsForBars = averageMs,
            categories = segments,
            uncategorizedMs = uncategorized?.durationMs ?: 0L,
            uncategorizedShare = if (tracked > 0L) {
                (uncategorized?.durationMs ?: 0L).toFloat() / tracked.toFloat()
            } else {
                0f
            },
            social = social,
            topApps = com.sumi.core.usage.topApps(b.perApp, b.applications, limit = 5),
            hourly = hourlyBuckets,
            patterns = patternFacts(keys, b.hourly, totalsByKey),
            coverageDays = coverage,
            observations = insights,
            dailyDetails = details,
        )
    }

    private data class RangeBase(
        val totals: List<DailyUsage>,
        val perApp: List<DailyAppUsage>,
        val hourly: List<HourlyUsage>,
        val sessions: List<UsageSession>,
        val categories: List<com.sumi.core.database.entity.AppCategory>,
        val applications: List<com.sumi.core.database.entity.Application>,
    )

    private data class RangeComparison(
        val totals: List<DailyUsage>,
        val perApp: List<DailyAppUsage>,
    )

    private companion object {
        const val RANGE_KEY = "insights_range"
    }
}

