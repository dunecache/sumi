package com.sumi.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumi.domain.common.Clock
import com.sumi.domain.common.DateKey
import com.sumi.domain.common.TimeZoneProvider
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
    val previousDayDurationMs: Long = 0L,
    val mostUsed: List<MostUsedApp> = emptyList(),
    val timeline: List<Long> = emptyList(), // hourly totals 0..23
    val observation: String? = null,
    val error: String? = null,
)

data class MostUsedApp(
    val packageName: String,
    val displayName: String,
    val durationMs: Long,
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repository: UsageRepository,
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
            ) { todayTotal, yesterdayTotal, perApp ->
                val sorted = perApp
                    .sortedByDescending { it.durationMs }
                    .take(5)
                    .map { MostUsedApp(it.packageName, it.packageName.substringAfterLast('.'), it.durationMs) }
                val timeline = (0..23).map { hour ->
                    // Timeline is populated from hourly diagnostics below; empty until M2 wiring completes.
                    0L
                }
                TodayUiState(
                    isLoading = false,
                    hasUsageAccess = checker.hasUsageAccess(),
                    totalDurationMs = todayTotal?.totalDurationMs ?: 0L,
                    previousDayDurationMs = yesterdayTotal?.totalDurationMs ?: 0L,
                    mostUsed = sorted,
                    timeline = timeline,
                    observation = observationFor(todayTotal?.totalDurationMs ?: 0L, sorted),
                )
            }.collect { _uiState.value = it }
        }
    }

    private fun observationFor(totalMs: Long, mostUsed: List<MostUsedApp>): String? {
        if (totalMs == 0L || mostUsed.isEmpty()) return null
        val top = mostUsed.first()
        val hours = totalMs / 3_600_000L
        return "You spent ${hours}h on your phone today. Your most-used app was ${top.displayName}."
    }
}
