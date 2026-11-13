package com.sumi.feature.appdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumi.core.database.dao.ApplicationDao
import com.sumi.data.categories.CategoryRepository
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
import kotlinx.coroutines.launch

/** App detail state: friendly identity plus real per-day history and category assignment. */
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
    /** Category assignment; null = uncategorized. */
    val categoryId: String? = null,
    val categoryName: String = "Uncategorized",
    val categoryColorToken: String = "stone-1",
    /** True when the user explicitly chose the assignment (vs. automatic default). */
    val categoryUserSet: Boolean = false,
    /** Options for the assignment picker (all known categories). */
    val categories: List<CategoryOption> = emptyList(),
)

/** One row in the category picker. */
data class CategoryOption(
    val id: String,
    val name: String,
    val colorToken: String,
)

@HiltViewModel
class AppDetailViewModel @Inject constructor(
    private val applicationDao: ApplicationDao,
    private val repository: UsageRepository,
    private val categoryRepository: CategoryRepository,
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
        applicationDao.observeByPackage(packageName),
        categoryRepository.observeCategories(),
    ) { daily, todaySessions, app, categories ->
        val today = todayKey()
        val current = categories.firstOrNull { it.id == app?.categoryId }
        AppDetailUiState(
            isLoading = false,
            displayName = displayNameFallback(),
            packageName = packageName,
            todayMs = daily.firstOrNull { it.dateKey == today }?.durationMs ?: 0L,
            week = dailySeries(daily, today, 7),
            month = dailySeries(daily, today, 30),
            sessionSummary = sessionSummary(todaySessions, clock.nowUtcMs()),
            categoryId = app?.categoryId,
            categoryName = current?.name ?: "Uncategorized",
            categoryColorToken = current?.colorToken ?: "stone-1",
            categoryUserSet = app?.isCategoryUserSet == true,
            categories = categories.map { CategoryOption(it.id, it.name, it.colorToken) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppDetailUiState())

    /** Explicit user assignment — always wins over defaults. Pass null for uncategorized. */
    fun setCategory(categoryId: String?) {
        viewModelScope.launch { categoryRepository.setAppCategory(packageName, categoryId) }
    }

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
internal fun sessionSummary(sessions: List<UsageSession>, nowUtcMs: Long): String? {
    // UsageSession.durationMs is a function (open sessions measure up to now).
    val durations = sessions.map { it.durationMs(nowUtcMs) }.filter { it > 0L }
    if (durations.isEmpty()) return null
    val longest = durations.max()
    val count = durations.size
    return if (count == 1) {
        "One session today, ${formatDuration(longest)} long."
    } else {
        "$count sessions today, longest ${formatDuration(longest)}."
    }
}
