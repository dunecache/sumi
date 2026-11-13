package com.sumi.feature.appdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumi.core.database.dao.ApplicationDao
import com.sumi.core.designsystem.component.formatDuration
import com.sumi.domain.common.Clock
import com.sumi.domain.common.DateKey
import com.sumi.domain.common.TimeZoneProvider
import com.sumi.domain.usage.DailyAppUsage
import com.sumi.domain.usage.UsageRepository
import com.sumi.domain.usage.UsageSession
import com.sumi.platform.applications.AppIconLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** App detail state: friendly identity plus real per-day history and today's sessions. */
data class AppDetailUiState(
    val isLoading: Boolean = true,
    val displayName: String = "",
    /** Raw package id, presented as quiet metadata — never the headline. */
    val packageName: String = "",
    val todayMs: Long = 0L,
    /** Trailing 7 daily totals, oldest → today; 0 for days without data. */
    val week: List<Long> = List(7) { 0L },
    /** Trailing 30 daily totals, oldest → today. */
    val month: List<Long> = List(30) { 0L },
    /** One-line neutral session summary for today, or null when no sessions exist. */
    val sessionSummary: String? = null,
)

@HiltViewModel
class AppDetailViewModel @Inject constructor(
    private val applicationDao: ApplicationDao,
    private val repository: UsageRepository,
    private val clock: Clock,
    private val timeZone: TimeZoneProvider,
    iconLoader: AppIconLoader,
    savedStateHandle: androidx.lifecycle.SavedStateHandle,
) : ViewModel() {

    /** Platform icon resolution; the UiState itself stays pure data. */
    val iconLoader: AppIconLoader = iconLoader

    private val packageName: String = savedStateHandle.get<String>("packageName").orEmpty()

    val uiState: StateFlow<AppDetailUiState> = combine(
        repository.observeAppDailyUsage(packageName),
        repository.observeSessionsForPackageAndDate(packageName, todayKey()),
    ) { daily, todaySessions ->
        val today = todayKey()
        AppDetailUiState(
            isLoading = false,
            displayName = displayNameFallback(),
            packageName = packageName,
            todayMs = daily.firstOrNull { it.dateKey == today }?.durationMs ?: 0L,
            week = dailySeries(daily, today, 7),
            month = dailySeries(daily, today, 30),
            sessionSummary = sessionSummary(todaySessions),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppDetailUiState())

    /** Fallback label; the friendly display name arrives via the app row once loaded. */
    private var cachedName: String? = null

    private suspend fun displayNameFallback(): String =
        cachedName ?: applicationDao.getByPackage(packageName)?.displayName
            ?.also { cachedName = it }
            ?: packageName.substringAfterLast('.')

    private fun todayKey(): String = DateKey.of(clock.nowUtcMs(), timeZone.currentZone())
}

/**
 * Maps per-day usage into a dense series of [days] totals ending today; missing days are
 * zero so the chart reads "no usage" instead of skipping bars.
 */
internal fun dailySeries(
    daily: List<DailyAppUsage>,
    todayKey: String,
    days: Int,
): List<Long> {
    val byKey = daily.associate { it.dateKey to it.durationMs }
    return (days - 1 downTo 0).map { offset ->
        byKey[DateKey.shift(todayKey, -offset.toLong())] ?: 0L
    }
}

/** Neutral, observed summary of today's sessions for one app (no guilt framing). */
internal fun sessionSummary(sessions: List<UsageSession>): String? {
    val complete = sessions.filter { it.durationMs > 0L }
    if (complete.isEmpty()) return null
    val longest = complete.maxBy { it.durationMs }.durationMs
    val count = complete.size
    return if (count == 1) {
        "One session today, ${formatDuration(longest)} long."
    } else {
        "$count sessions today, longest ${formatDuration(longest)}."
    }
}
