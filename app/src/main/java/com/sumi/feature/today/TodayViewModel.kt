package com.sumi.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumi.core.database.dao.ApplicationDao
import com.sumi.core.database.entity.Application
import com.sumi.core.designsystem.component.formatDuration
import com.sumi.domain.common.Clock
import com.sumi.domain.common.DateKey
import com.sumi.domain.common.TimeZoneProvider
import com.sumi.domain.usage.DailyAppUsage
import com.sumi.domain.usage.UsageRepository
import com.sumi.platform.usage.UsageAccessChecker
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
    val mostUsed: List<MostUsedApp> = emptyList(),
    val timeline: List<Long> = List(HOURS_PER_DAY) { 0L }, // hourly totals 0..23
    val observation: String? = null,
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
    private val checker: UsageAccessChecker,
    private val clock: Clock,
    private val timeZone: TimeZoneProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TodayUiState())
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    init {
        observe()
    }

    private fun observe() {
        viewModelScope.launch {
            val today = DateKey.of(clock.nowUtcMs(), timeZone.currentZone())
            val yesterday = DateKey.shift(today, -1L)
            combine(
                repository.observeDailyTotal(today),
                repository.observeDailyTotal(yesterday),
                repository.observeDailyAppUsage(today),
                applicationDao.observeInstalled(),
            ) { todayTotal, yesterdayTotal, perApp, applications ->
                val mostUsed = mostUsedApps(perApp, applications)
                TodayUiState(
                    isLoading = false,
                    hasUsageAccess = checker.hasUsageAccess(),
                    totalDurationMs = todayTotal?.totalDurationMs ?: 0L,
                    previousDayDurationMs = yesterdayTotal?.totalDurationMs,
                    mostUsed = mostUsed,
                    timeline = List(TodayUiState.HOURS_PER_DAY) { 0L },
                    observation = observationFor(todayTotal?.totalDurationMs ?: 0L, mostUsed),
                )
            }.collect { _uiState.value = it }
        }
    }
}

/** Milliseconds per minute for the sub-minute summary template. */
private const val MILLIS_PER_MINUTE = 60_000L

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

/** Package-tail suffix per §3.5; falls back to the parent segment (or "system") when the tail equals the name. */
private fun disambiguationSuffix(packageName: String, name: String): String {
    val tail = packageName.substringAfterLast('.')
    if (!tail.equals(name, ignoreCase = true)) return tail
    val head = packageName.substringBeforeLast('.')
    return head.substringAfterLast('.', "system")
}
