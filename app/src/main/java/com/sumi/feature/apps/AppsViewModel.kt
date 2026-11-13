package com.sumi.feature.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumi.core.database.dao.ApplicationDao
import com.sumi.core.database.entity.Application
import com.sumi.domain.common.Clock
import com.sumi.domain.common.DateKey
import com.sumi.domain.common.TimeZoneProvider
import com.sumi.domain.usage.UsageRepository
import com.sumi.platform.applications.AppIconLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** One apps-library row: friendly name, today's and the trailing week's usage. */
data class AppRow(
    val packageName: String,
    val displayName: String,
    val categoryId: String?,
    val todayMs: Long,
    val weekMs: Long,
)

data class AppsUiState(
    val isLoading: Boolean = true,
    val apps: List<AppRow> = emptyList(),
    /** Total for today across all apps; denominates the per-row share bars. */
    val todayTotalMs: Long = 0L,
)

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val applicationDao: ApplicationDao,
    private val repository: UsageRepository,
    private val clock: Clock,
    private val timeZone: TimeZoneProvider,
    iconLoader: AppIconLoader,
) : ViewModel() {

    /** Platform icon resolution for row rendering; the UiState itself stays pure data. */
    val iconLoader: AppIconLoader = iconLoader

    val uiState: StateFlow<AppsUiState> = combine(
        applicationDao.observeInstalled(),
        todayUsage(),
        weekUsage(),
    ) { applications, todayByApp, weekByApp ->
        val rows = appRows(applications, todayByApp, weekByApp)
        AppsUiState(
            isLoading = false,
            apps = rows,
            todayTotalMs = todayByApp.values.sum(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    private val _query = kotlinx.coroutines.flow.MutableStateFlow("")
    val query: StateFlow<String> = _query

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun filtered(rows: List<AppRow>): List<AppRow> {
        val q = _query.value.trim()
        if (q.isEmpty()) return rows
        return rows.filter {
            it.displayName.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
        }
    }

    private fun todayUsage(): Flow<Map<String, Long>> =
        repository.observeDailyAppUsage(todayKey()).map { rows ->
            rows.associate { it.packageName to it.durationMs }
        }

    private fun weekUsage(): Flow<Map<String, Long>> =
        repository.observeDailyAppUsageRange(weekKeys()).map { rows ->
            val totals = mutableMapOf<String, Long>()
            for (row in rows) {
                totals[row.packageName] = (totals[row.packageName] ?: 0L) + row.durationMs
            }
            totals
        }

    private fun todayKey(): String = DateKey.of(clock.nowUtcMs(), timeZone.currentZone())

    private fun weekKeys(): List<String> {
        val today = todayKey()
        return (0..6).map { offset -> DateKey.shift(today, -offset.toLong()) }
    }
}

/**
 * Library rows: every installed app appears; used apps rank by today's usage (then name),
 * unused apps follow alphabetically. Weekly totals are summed across the trailing 7 days.
 */
internal fun appRows(
    applications: List<Application>,
    todayByApp: Map<String, Long>,
    weekByApp: Map<String, Long>,
): List<AppRow> = applications
    .map { app ->
        AppRow(
            packageName = app.packageName,
            displayName = app.displayName,
            categoryId = app.categoryId,
            todayMs = todayByApp[app.packageName] ?: 0L,
            weekMs = weekByApp[app.packageName] ?: 0L,
        )
    }
    .sortedWith(
        compareByDescending<AppRow> { it.todayMs }
            .thenByDescending { it.weekMs }
            .thenBy { it.displayName },
    )
