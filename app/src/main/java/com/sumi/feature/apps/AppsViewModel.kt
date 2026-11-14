package com.sumi.feature.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumi.core.database.dao.ApplicationDao
import com.sumi.core.database.entity.AppCategory
import com.sumi.core.database.entity.Application
import com.sumi.data.categories.CategoryRepository
import com.sumi.domain.common.Clock
import com.sumi.domain.common.DateKey
import com.sumi.domain.common.TimeZoneProvider
import com.sumi.domain.usage.UsageRepository
import com.sumi.platform.applications.AppIconLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One apps-library row: friendly name, today's and the trailing week's usage. */
data class AppRow(
    val packageName: String,
    val displayName: String,
    val categoryId: String?,
    val todayMs: Long,
    val weekMs: Long,
)

/** Category chip shown under the search field. sortOrder mirrors the manager ordering. */
data class CategoryFilterChip(
    val id: String,
    val name: String,
    val colorToken: String,
    val sortOrder: Int,
)

/** Category filter for the library. */
sealed interface AppsFilter {
    data object All : AppsFilter
    data object Uncategorized : AppsFilter
    data class Category(val id: String) : AppsFilter
}

enum class AppsSort { MOST_USED, LEAST_USED, ALPHABETICAL, CATEGORY }

data class AppsUiState(
    val isLoading: Boolean = true,
    val apps: List<AppRow> = emptyList(),
    /** Total for today across all apps; denominates the per-row share bars. */
    val todayTotalMs: Long = 0L,
    val categories: List<CategoryFilterChip> = emptyList(),
    val filter: AppsFilter = AppsFilter.All,
    val sort: AppsSort = AppsSort.MOST_USED,
)

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val applicationDao: ApplicationDao,
    private val repository: UsageRepository,
    private val categoryRepository: CategoryRepository,
    private val clock: Clock,
    private val timeZone: TimeZoneProvider,
    iconLoader: AppIconLoader,
) : ViewModel() {

    /** Platform icon resolution for row rendering; the UiState itself stays pure data. */
    val iconLoader: AppIconLoader = iconLoader

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _filter = MutableStateFlow<AppsFilter>(AppsFilter.All)
    val filter: StateFlow<AppsFilter> = _filter

    /** Apps selected for bulk reassign (long-press or Select mode); cleared on Done. */
    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    private val _sort = MutableStateFlow(AppsSort.MOST_USED)
    val sort: StateFlow<AppsSort> = _sort

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onFilterChange(filter: AppsFilter) {
        _filter.value = filter
    }

    fun onSortChange(sort: AppsSort) {
        _sort.value = sort
    }

    /** Long-press or Select mode toggle. */
    fun toggleSelection(packageName: String) {
        _selection.value = toggleSelectionSet(_selection.value, packageName)
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    /** Deep-link entry: apply `uncategorized` (or `{categoryId}`) once; see AppsScreen. */
    fun applyInitialFilter(token: String?) {
        onFilterChange(appsFilterToken(token))
    }

    /**
     * Bulk assign: every row keeps `isCategoryUserSet = 1` (explicit user choice), the
     * library re-renders from the Flow, and category history re-derives at read time
     * (ARCHITECTURE §13) — the fix is retroactively visible everywhere.
     */
    fun assignCategory(categoryId: String?) {
        val packages = _selection.value
        if (packages.isEmpty()) return
        _selection.value = emptySet()
        viewModelScope.launch {
            applicationDao.setCategoryAll(packages.toList(), categoryId)
        }
    }

    private val base = combine(
        applicationDao.observeInstalled(),
        todayUsage(),
        weekUsage(),
        categoryRepository.observeCategories(),
    ) { applications, todayByApp, weekByApp, categories ->
        BaseData(applications, todayByApp, weekByApp, categories)
    }

    private val params = combine(_query, _filter, _sort) { query, filter, sort ->
        Triple(query, filter, sort)
    }

    val uiState: StateFlow<AppsUiState> = combine(base, params) { data, (q, f, s) ->
        val rows = appRows(data.applications, data.todayByApp, data.weekByApp)
        val chips = data.categories.map { CategoryFilterChip(it.id, it.name, it.colorToken, it.sortOrder) }
        AppsUiState(
            isLoading = false,
            apps = filterAndSort(rows, q, chips, f, s),
            todayTotalMs = data.todayByApp.values.sum(),
            categories = chips,
            filter = f,
            sort = s,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

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

    private data class BaseData(
        val applications: List<Application>,
        val todayByApp: Map<String, Long>,
        val weekByApp: Map<String, Long>,
        val categories: List<AppCategory>,
    )
}

/**
 * Library rows: every installed app appears; weekly totals are summed across the
 * trailing 7 days. Ordering is applied later by [filterAndSort].
 */
internal fun appRows(
    applications: List<Application>,
    todayByApp: Map<String, Long>,
    weekByApp: Map<String, Long>,
): List<AppRow> = applications.map { app ->
    AppRow(
        packageName = app.packageName,
        displayName = app.displayName,
        categoryId = app.categoryId,
        todayMs = todayByApp[app.packageName] ?: 0L,
        weekMs = weekByApp[app.packageName] ?: 0L,
    )
}

/** Pure and tested (AppsLogicTest): toggle a package in the selection set. */
internal fun toggleSelectionSet(selection: Set<String>, packageName: String): Set<String> =
    if (packageName in selection) selection - packageName else selection + packageName

/**
 * Deep-link token → filter. Pure and tested (AppsLogicTest).
 * `uncategorized` pins the residual; a category id pins that category; anything
 * else (including `all`/`null`) is the unfiltered library.
 */
internal fun appsFilterToken(token: String?): AppsFilter = when (token) {
    null, "", "all" -> AppsFilter.All
    "uncategorized" -> AppsFilter.Uncategorized
    else -> AppsFilter.Category(token)
}

/** Pure and tested (AppsLogicTest): search + category filter + sort, deterministic. */
internal fun filterAndSort(
    rows: List<AppRow>,
    query: String,
    categories: List<CategoryFilterChip>,
    filter: AppsFilter,
    sort: AppsSort,
): List<AppRow> {
    val q = query.trim()
    val filtered = rows.filter { row ->
        val matchesFilter = when (filter) {
            AppsFilter.All -> true
            AppsFilter.Uncategorized -> row.categoryId == null
            is AppsFilter.Category -> row.categoryId == filter.id
        }
        val matchesQuery = q.isEmpty() ||
            row.displayName.contains(q, ignoreCase = true) ||
            row.packageName.contains(q, ignoreCase = true)
        matchesFilter && matchesQuery
    }
    val byName = compareBy<AppRow> { it.displayName.lowercase() }
    return when (sort) {
        AppsSort.MOST_USED -> filtered.sortedWith(compareByDescending<AppRow> { it.todayMs }.then(byName))
        AppsSort.LEAST_USED -> filtered.sortedWith(compareBy<AppRow> { it.todayMs }.then(byName))
        AppsSort.ALPHABETICAL -> filtered.sortedWith(byName)
        AppsSort.CATEGORY -> {
            val order = categories.mapIndexed { index, chip -> chip.id to index }.toMap()
            filtered.sortedWith(
                compareBy<AppRow> { row -> row.categoryId?.let { order[it] } ?: Int.MAX_VALUE }
                    .then(byName),
            )
        }
    }
}
