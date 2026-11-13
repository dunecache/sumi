package com.sumi.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumi.core.database.dao.ApplicationDao
import com.sumi.core.database.entity.AppCategory
import com.sumi.core.database.entity.Application
import com.sumi.core.designsystem.component.CategorySegment
import com.sumi.core.designsystem.component.formatDuration
import com.sumi.core.datastore.PreferencesRepository
import com.sumi.data.categories.CategoryRepository
import com.sumi.data.categories.DefaultCategories
import com.sumi.domain.categories.AppUsage
import com.sumi.domain.categories.CategoryAnalytics
import com.sumi.domain.common.Clock
import com.sumi.domain.common.DateKey
import com.sumi.domain.common.TimeZoneProvider
import com.sumi.domain.usage.DailyAppUsage
import com.sumi.domain.usage.HourlyUsage
import com.sumi.domain.usage.UsageRepository
import com.sumi.platform.usage.UsageAccessChecker
import com.sumi.platform.applications.AppIconLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class TodayUiState(
    val isLoading: Boolean = true,
    val hasUsageAccess: Boolean = false,
    val totalDurationMs: Long = 0L,
    /** Null = no data was collected for yesterday; renderers omit the line instead of printing "0m". */
    val previousDayDurationMs: Long? = null,
    /** Neutral comparison wording, or null when yesterday has no data (line omitted). */
    val comparison: String? = null,
    val mostUsed: List<MostUsedApp> = emptyList(),
    val timeline: List<Long> = List(HOURS_PER_DAY) { 0L }, // hourly totals 0..23
    val observation: String? = null,
    /** Daily intention goal — computed from the same daily aggregate as [totalDurationMs]. */
    val intention: DailyIntentionState = DailyIntentionState.Disabled,
    val intentionEnabled: Boolean = true,
    val intentionGoalMinutes: Int? = null,
    /** Derived category distribution of today's tracked (per-app) usage; uncategorized last. */
    val categories: List<CategorySegment> = emptyList(),
    /** Σ per-app usage used as the denominator for category percentages. */
    val trackedDurationMs: Long = 0L,
    val error: String? = null,
) {
    companion object {
        const val HOURS_PER_DAY = 24
    }
}

data class MostUsedApp(
    val packageName: String,
    val displayName: String,
    val durationMs: Long,
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repository: UsageRepository,
    private val applicationDao: ApplicationDao,
    private val categoryRepository: CategoryRepository,
    private val preferencesRepository: PreferencesRepository,
    private val checker: UsageAccessChecker,
    private val clock: Clock,
    private val timeZone: TimeZoneProvider,
    iconLoader: AppIconLoader,
) : ViewModel() {

    /** Platform icon resolution for row rendering; the UiState itself stays pure data. */
    val iconLoader: AppIconLoader = iconLoader

    private val _uiState = MutableStateFlow(TodayUiState())
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    init {
        observe()
    }

    private fun observe() {
        viewModelScope.launch {
            val today = DateKey.of(clock.nowUtcMs(), timeZone.currentZone())
            val yesterday = DateKey.shift(today, -1L)
            val base = combine(
                repository.observeDailyTotal(today),
                repository.observeDailyTotal(yesterday),
                repository.observeDailyAppUsage(today),
                repository.observeHourlyUsage(today),
                applicationDao.observeInstalled(),
            ) { todayTotal, yesterdayTotal, perApp, hourly, applications ->
                BaseData(todayTotal?.totalDurationMs ?: 0L, yesterdayTotal?.totalDurationMs, perApp, hourly, applications)
            }
            combine(
                base,
                categoryRepository.observeCategories(),
                preferencesRepository.dailyGoal,
            ) { data, categories, goalConfig ->
                val mostUsed = mostUsedApps(data.perApp, data.applications)
                val segments = categorySegments(data.perApp, data.applications, categories)
                val tracked = data.perApp.sumOf { it.durationMs }
                TodayUiState(
                    isLoading = false,
                    hasUsageAccess = checker.hasUsageAccess(),
                    totalDurationMs = data.totalDurationMs,
                    previousDayDurationMs = data.previousDayDurationMs,
                    comparison = data.previousDayDurationMs?.let {
                        yesterdayComparison(data.totalDurationMs, it)
                    },
                    mostUsed = mostUsed,
                    timeline = hourlyTotals(data.hourly),
                    observation = observationFor(data.totalDurationMs, mostUsed),
                    intention = intentionState(goalConfig, data.totalDurationMs),
                    intentionEnabled = goalConfig.enabled,
                    intentionGoalMinutes = goalConfig.goalMinutes,
                    categories = segments,
                    trackedDurationMs = tracked,
                )
            }.collect { _uiState.value = it }
        }
    }

    /** Sets (or changes) the daily goal target; always marks it enabled. */
    fun saveGoal(minutes: Int) {
        viewModelScope.launch { preferencesRepository.setDailyGoal(minutes) }
    }

    /** Removes the goal entirely. */
    fun removeGoal() {
        viewModelScope.launch { preferencesRepository.setDailyGoal(null) }
    }

    /** Pauses/resumes the configured goal without forgetting its value. */
    fun setGoalEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setDailyGoalEnabled(enabled) }
    }

    private data class BaseData(
        val totalDurationMs: Long,
        val previousDayDurationMs: Long?,
        val perApp: List<DailyAppUsage>,
        val hourly: List<HourlyUsage>,
        val applications: List<Application>,
    )
}

/** Milliseconds per minute for comparison math and summary thresholds. */
private const val MILLIS_PER_MINUTE = 60_000L

/** Deltas under this many minutes read as "about the same" (no noise for tiny changes). */
private const val SIMILAR_THRESHOLD_MINUTES = 5L

/**
 * Neutral previous-day comparison (DESIGN.md §5.1-4: no up/down arrows in red/green):
 * deltas under five minutes read as unchanged; otherwise the magnitude is reused from
 * [formatDuration] — "About 20m less than yesterday." / "About 2h 15m more than yesterday."
 */
internal fun yesterdayComparison(todayMs: Long, yesterdayMs: Long): String {
    val delta = todayMs - yesterdayMs
    val absMinutes = kotlin.math.abs(delta) / MILLIS_PER_MINUTE
    if (absMinutes < SIMILAR_THRESHOLD_MINUTES) return "About the same as yesterday."
    val amount = formatDuration(absMinutes * MILLIS_PER_MINUTE)
    return if (delta < 0) "About $amount less than yesterday." else "About $amount more than yesterday."
}

/**
 * Sums per-app hourly buckets into 24 wall-clock totals for the timeline strip.
 * Hour values from aggregation are always 0–23 (zone `hour`); values outside that range
 * are clamped defensively so malformed rows can never crash rendering.
 */
internal fun hourlyTotals(hourly: List<HourlyUsage>): List<Long> {
    val totals = MutableList(TodayUiState.HOURS_PER_DAY) { 0L }
    for (row in hourly) {
        val index = row.hour.coerceIn(0, TodayUiState.HOURS_PER_DAY - 1)
        totals[index] += row.durationMs.coerceAtLeast(0L)
    }
    return totals
}

/**
 * Calm summary templates (navigation/Today reference §3.7), in priority order:
 * `< 1min` → quiet-day sentence · top app known → largest-share sentence · no app data → total only.
 * Neutral framing only; "You spent …" is banned. Null means the section is omitted entirely.
 */
internal fun observationFor(totalMs: Long, mostUsed: List<MostUsedApp>): String? = when {
    totalMs < MILLIS_PER_MINUTE -> "A quiet day on the phone so far."
    mostUsed.isEmpty() -> "${formatDuration(totalMs)} today."
    else -> "${formatDuration(totalMs)} today — ${mostUsed.first().displayName} took the largest share."
}

/**
 * Top-5 most-used apps (navigation/Today reference §3.5): duration descending with an
 * alphabetical tiebreak, friendly display labels, and package-tail disambiguation when two
 * packages resolve to the same display name (`android (system)`).
 */
internal fun mostUsedApps(
    perApp: List<DailyAppUsage>,
    applications: List<Application>,
): List<MostUsedApp> {
    if (perApp.isEmpty()) return emptyList()
    val labels = applications.associate { it.packageName to it.displayName }
    val names = perApp.associate { usage ->
        usage.packageName to (labels[usage.packageName] ?: usage.packageName.substringAfterLast('.'))
    }
    val nameCounts = names.values.groupingBy { it }.eachCount()
    return perApp
        .sortedWith(
            compareByDescending<DailyAppUsage> { it.durationMs }
                .thenBy { names.getValue(it.packageName) },
        )
        .take(5)
        .map { usage ->
            val name = names.getValue(usage.packageName)
            val display = if (nameCounts.getValue(name) > 1) {
                "$name (${disambiguationSuffix(usage.packageName, name)})"
            } else {
                name
            }
            MostUsedApp(usage.packageName, display, usage.durationMs)
        }
}

/**
 * Derives the display segments for the category bar from today's per-app usage and the
 * current assignment. Seeded defaults act as a fallback name/color source so the bar
 * still renders sensibly in the brief window before [CategoryRepository] finishes seeding.
 */
internal fun categorySegments(
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

/** Package-tail suffix per §3.5; falls back to the parent segment (or "system") when the tail equals the name. */
private fun disambiguationSuffix(packageName: String, name: String): String {
    val tail = packageName.substringAfterLast('.')
    if (!tail.equals(name, ignoreCase = true)) return tail
    val head = packageName.substringBeforeLast('.')
    return head.substringAfterLast('.', "system")
}
