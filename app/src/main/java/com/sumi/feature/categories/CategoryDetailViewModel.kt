package com.sumi.feature.categories

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumi.core.database.dao.ApplicationDao
import com.sumi.core.database.entity.AppCategory
import com.sumi.core.database.entity.Application
import com.sumi.data.categories.CategoryRepository
import com.sumi.domain.common.Clock
import com.sumi.domain.common.DateKey
import com.sumi.domain.common.TimeZoneProvider
import com.sumi.domain.usage.DailyAppUsage
import com.sumi.domain.usage.UsageRepository
import com.sumi.platform.applications.AppIconLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One app inside the category, with today's usage (and share of tracked time for coloring). */
data class CategoryAppRow(
    val packageName: String,
    val displayName: String,
    val todayMs: Long,
    val shareOfTracked: Float?,
)

data class CategoryDetailUiState(
    val isLoading: Boolean = true,
    /** False when the category id no longer exists (e.g. deleted while this screen was open). */
    val exists: Boolean = true,
    val categoryId: String = "",
    val name: String = "",
    val colorToken: String = "matcha-4",
    val todayMs: Long = 0L,
    /** Share of today's *tracked* (per-app) usage; the documented denominator. */
    val todayShare: Float = 0f,
    /** Trailing 7 daily category totals, oldest → today; derived from the CURRENT assignment. */
    val week: List<Long> = List(7) { 0L },
    val weekTotalMs: Long = 0L,
    /** All apps assigned to the category (installed or not), ranked by today's usage. */
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

    val uiState: StateFlow<CategoryDetailUiState> = run {
        val today = DateKey.of(clock.nowUtcMs(), timeZone.currentZone())
        val weekKeys = (6 downTo 0).map { DateKey.shift(today, -it.toLong()) }
        combine(
            categoryRepository.observeCategories(),
            applicationDao.observeByCategory(categoryId),
            repository.observeDailyAppUsageRange(weekKeys),
            repository.observeDailyAppUsage(today),
        ) { categories, apps, weekUsage, todayUsage ->
            deriveState(categoryId, weekKeys, categories, apps, weekUsage, todayUsage)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryDetailUiState())
}

/** Pure derivation, kept internal for tests. */
internal fun deriveState(
    categoryId: String,
    weekKeys: List<String>,
    categories: List<AppCategory>,
    apps: List<Application>,
    weekUsage: List<DailyAppUsage>,
    todayUsage: List<DailyAppUsage>,
): CategoryDetailUiState {
    val category = categories.firstOrNull { it.id == categoryId }
    if (category == null) {
        // Category was deleted (or the id is stale) while this screen was open.
        return CategoryDetailUiState(isLoading = false, exists = false, categoryId = categoryId)
    }
    // Members are decided by the *current* assignment, not by whatever the caller passed
    // in: only apps whose categoryId matches belong to this category's numbers. A
    // reassigned app therefore leaves the old category immediately (current-assignment
    // policy), and uncategorized apps never leak into any category bucket.
    val members = apps.filter { it.categoryId == categoryId }
    val memberPackages = members.mapTo(HashSet()) { it.packageName }
    val todayByPkg = todayUsage.associate { it.packageName to it.durationMs }
    val trackedToday = todayUsage.sumOf { it.durationMs }
    val todayMs = memberPackages.sumOf { todayByPkg[it] ?: 0L }

    val week = weekKeys.map { key ->
        weekUsage.asSequence()
            .filter { it.dateKey == key && it.packageName in memberPackages }
            .sumOf { it.durationMs }
    }
    val weekTotal = week.sum()

    val rows = members.map { app ->
        val ms = todayByPkg[app.packageName] ?: 0L
        CategoryAppRow(
            packageName = app.packageName,
            displayName = app.displayName,
            todayMs = ms,
            shareOfTracked = if (trackedToday > 0L && ms > 0L) ms.toFloat() / trackedToday else null,
        )
    }.sortedWith(compareByDescending<CategoryAppRow> { it.todayMs }.thenBy { it.displayName })

    return CategoryDetailUiState(
        isLoading = false,
        exists = true,
        categoryId = category.id,
        name = category.name,
        colorToken = category.colorToken,
        todayMs = todayMs,
        todayShare = if (trackedToday > 0L) todayMs.toFloat() / trackedToday else 0f,
        week = week,
        weekTotalMs = weekTotal,
        apps = rows,
    )
}
