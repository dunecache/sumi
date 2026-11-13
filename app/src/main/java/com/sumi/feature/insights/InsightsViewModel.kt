package com.sumi.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumi.core.database.dao.ApplicationDao
import com.sumi.core.designsystem.component.CategorySegment
import com.sumi.data.categories.CategoryRepository
import com.sumi.data.categories.DefaultCategories
import com.sumi.domain.categories.DefaultPackageCategories
import com.sumi.domain.categories.AppUsage
import com.sumi.domain.categories.CategoryAnalytics
import com.sumi.domain.common.Clock
import com.sumi.domain.common.DateKey
import com.sumi.domain.common.TimeZoneProvider
import com.sumi.domain.insights.Insight
import com.sumi.domain.insights.InsightsEngine
import com.sumi.domain.usage.DailyAppUsage
import com.sumi.domain.usage.UsageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class InsightsUiState(
    val isLoading: Boolean = true,
    val insights: List<Insight> = emptyList(),
    /** Trailing 7 daily totals, oldest → today. */
    val weeklyTotals: List<Long> = emptyList(),
    /** 7-day category distribution; uncategorized last. */
    val categories: List<CategorySegment> = emptyList(),
    val weekTrackedMs: Long = 0L,
    /** Time in the Social category this week, with its share of week tracked usage; null when absent. */
    val socialWeekMs: Long? = null,
    val socialWeekShare: Float = 0f,
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val repository: UsageRepository,
    private val categoryRepository: CategoryRepository,
    private val applicationDao: ApplicationDao,
    private val clock: Clock,
    private val timeZone: TimeZoneProvider,
) : ViewModel() {

    val uiState: StateFlow<InsightsUiState> = weeklyTotalsFlow()
        .let { week ->
            val weekKeys = lastWeekKeys()
            val base = combine(
                week,
                repository.observeSessionsForDate(weekKeys.last()),
                repository.observeHourlyUsage(weekKeys.last()),
            ) { weekly, sessions, hourly -> Triple(weekly, sessions, hourly) }
            combine(
                base,
                repository.observeDailyAppUsageRange(weekKeys),
                categoryRepository.observeCategories(),
                applicationDao.observeInstalled(),
            ) { b, perApp, categories, applications ->
                deriveState(b.first, b.second, b.third, perApp, categories, applications)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())

    private fun lastWeekKeys(): List<String> {
        val today = DateKey.of(clock.nowUtcMs(), timeZone.currentZone())
        return (6 downTo 0).map { DateKey.shift(today, -it.toLong()) }
    }

    private fun weeklyTotalsFlow() =
        combine(*lastWeekKeys().map { repository.observeDailyTotal(it) }.toTypedArray()) { totals ->
            totals.map { it?.totalDurationMs ?: 0L }
        }

    private fun deriveState(
        weekly: List<Long>,
        sessions: List<com.sumi.domain.usage.UsageSession>,
        hourly: List<com.sumi.domain.usage.HourlyUsage>,
        perApp: List<DailyAppUsage>,
        categories: List<com.sumi.core.database.entity.AppCategory>,
        applications: List<com.sumi.core.database.entity.Application>,
    ): InsightsUiState {
        val weekKeys = lastWeekKeys()
        val dailyTotals = weekKeys.mapIndexedNotNull { index, key ->
            weekly[index].takeIf { it > 0L }?.let { com.sumi.domain.usage.DailyUsage(key, it, 0L) }
        }
        val summary = if (sessions.isEmpty() && dailyTotals.isEmpty() && hourly.isEmpty()) {
            null
        } else {
            InsightsEngine.summarize(
                sessions = sessions,
                dailyTotals = dailyTotals,
                hourly = hourly,
                zone = timeZone.currentZone(),
                nowUtcMs = clock.nowUtcMs(),
            )
        }

        // 7-day category distribution: same derivation as Today, current assignment wins.
        val names = DefaultCategories.ALL.associate { it.id to it.name } +
            categories.associate { it.id to it.name }
        val colors = DefaultCategories.ALL.associate { it.id to it.colorToken } +
            categories.associate { it.id to it.colorToken }
        val assignment = applications.associate { it.packageName to it.categoryId }
        val breakdown = CategoryAnalytics.breakdown(
            perApp = perApp.map { AppUsage(it.packageName, it.durationMs) },
            categoryNames = names,
            assignment = assignment,
        )
        val total = breakdown.totalDurationMs
        val segments = breakdown.categories.map { usage ->
            CategorySegment(
                categoryId = usage.categoryId,
                name = usage.name,
                durationMs = usage.durationMs,
                share = if (total > 0L) usage.durationMs.toFloat() / total.toFloat() else 0f,
                colorToken = usage.categoryId?.let { colors[it] } ?: "categoryNeutral",
            )
        }
        val social = breakdown.categories.firstOrNull { it.categoryId == DefaultPackageCategories.SOCIAL }

        return InsightsUiState(
            isLoading = false,
            insights = summary?.insights ?: emptyList(),
            weeklyTotals = weekly,
            categories = segments,
            weekTrackedMs = total,
            socialWeekMs = social?.durationMs,
            socialWeekShare = social?.let { usage -> if (total > 0L) usage.durationMs.toFloat() / total else 0f } ?: 0f,
        )
    }
}
