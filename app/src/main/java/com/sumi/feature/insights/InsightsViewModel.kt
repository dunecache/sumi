package com.sumi.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumi.domain.common.Clock
import com.sumi.domain.common.DateKey
import com.sumi.domain.common.TimeZoneProvider
import com.sumi.domain.insights.Insight
import com.sumi.domain.insights.InsightsEngine
import com.sumi.domain.usage.UsageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class InsightsUiState(
    val isLoading: Boolean = true,
    val insights: List<Insight> = emptyList(),
    val weeklyTotals: List<Long> = emptyList(),
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val repository: UsageRepository,
    private val clock: Clock,
    private val timeZone: TimeZoneProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val today = DateKey.of(clock.nowUtcMs(), timeZone.currentZone())
            val weekKeys = (6 downTo 0).map { DateKey.shift(today, -it.toLong()) }
            val dailyTotals = mutableListOf<com.sumi.domain.usage.DailyUsage>()
            val weekly = mutableListOf<Long>()
            weekKeys.forEach { key ->
                val total = repository.observeDailyTotal(key).first()
                if (total != null) dailyTotals += total
                weekly += (total?.totalDurationMs ?: 0L)
            }
            val sessions = repository.observeSessionsForDate(today).first()
            // Real hourly buckets now feed the rhythm analysis (was emptyList before).
            val hourly = repository.observeHourlyUsage(today).first()
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
            _uiState.value = InsightsUiState(
                isLoading = false,
                insights = summary?.insights ?: emptyList(),
                weeklyTotals = weekly,
            )
        }
    }
}
