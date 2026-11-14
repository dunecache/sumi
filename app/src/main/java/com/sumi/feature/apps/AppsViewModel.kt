package com.sumi.feature.apps

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumi.core.database.dao.ApplicationDao
import com.sumi.core.database.entity.AppCategory
import com.sumi.core.database.entity.Application
import com.sumi.core.datastore.PreferencesRepository
import com.sumi.data.categories.CategoryRepository
import com.sumi.domain.common.Clock
import com.sumi.domain.common.DateKey
import com.sumi.domain.common.TimeZoneProvider
import com.sumi.domain.usage.UsageRepository
import com.sumi.feature.categories.nextInlineCreateToken
import com.sumi.platform.applications.AppIconLoader
import com.sumi.platform.usage.UsageAccessChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One apps-library row: friendly name, today's and the trailing week's usage. */
data class AppRow(
    val packageName: String,
    val displayName: String,
    val categoryId: String?,
    /** Current assignment's display name; null when uncategorized (never a raw id). */
    val categoryName: String?,
    /** Category color token for the icon fallback and the identity dot; null = stone (residual). */
    val categoryColorToken: String?,
    val todayMs: Long,
    val weekMs: Long,
    /** Quiet "New" marker: row created within [NEW_WINDOW_DAYS] of first discovery. */
    val isNew: Boolean,
    val isSystemApp: Boolean,
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

/**
 * Library sorts. Alphabetical and By-category omit percentages; the usage sorts always
 * show the share of the matching window (documented denominator: Σ per-app usage of the
 * window, exactly what Today excludes).
 */
enum class AppsSort { MOST_USED_TODAY, MOST_USED_WEEK, ALPHABETICAL, CATEGORY }

data class AppsUiState(
    val isLoading: Boolean = true,
    /** False → the screen renders the permission-missing full state, never fake zeros. */
    val hasUsageAccess: Boolean = false,
    val apps: List<AppRow> = emptyList(),
    /** Total for today across all apps; denominates the today-share. Excludes nothing (Today's denominator). */
    val todayTotalMs: Long = 0L,
    /** Σ per-app usage across the trailing 7 days; denominates the week-share. */
    val weekTotalMs: Long = 0L,
    val categories: List<CategoryFilterChip> = emptyList(),
    val filter: AppsFilter = AppsFilter.All,
    val sort: AppsSort = AppsSort.MOST_USED_TODAY,
    /** Visible rows with no assignment (respects the system-apps policy). */
    val uncategorizedCount: Int = 0,
)

/** One-shot effects: show the confirmation snackbar with Undo. */
sealed interface AppsEvent {
    /** Assignment persisted; [count] apps now carry the assignment. */
    data class AssignmentCompleted(val count: Int, val categoryName: String) : AppsEvent
}

/** Previous assignment of one package, captured before a write — used by [AppsViewModel.undoLastAssignment]. */
private data class PreviousAssignment(
    val packageName: String,
    val categoryId: String?,
    val isCategoryUserSet: Boolean,
)

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val applicationDao: ApplicationDao,
    private val repository: UsageRepository,
    private val categoryRepository: CategoryRepository,
    private val preferencesRepository: PreferencesRepository,
    private val checker: UsageAccessChecker,
    private val clock: Clock,
    private val timeZone: TimeZoneProvider,
    iconLoader: AppIconLoader,
) : ViewModel() {

    /** Platform icon resolution for row rendering; the UiState itself stays pure data. */
    val iconLoader: AppIconLoader = iconLoader

    /** Path to the system Usage Access screen; launched from the permission-missing state. */
    fun usageAccessIntent(): Intent = checker.usageAccessSettingsIntent()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _filter = MutableStateFlow<AppsFilter>(AppsFilter.All)
    val filter: StateFlow<AppsFilter> = _filter

    /** Apps selected for bulk reassign (long-press or Select mode); cleared on assignment. */
    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    /**
     * Explicit selection-mode flag. Selection emptiness alone can't express "mode on,
     * nothing ticked yet" (the toolbar's Select action) vs "mode off".
     */
    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    private val _sort = MutableStateFlow(AppsSort.MOST_USED_TODAY)
    val sort: StateFlow<AppsSort> = _sort

    private val _events = MutableSharedFlow<AppsEvent>(extraBufferCapacity = 4)
    val events: kotlinx.coroutines.flow.SharedFlow<AppsEvent> = _events.asSharedFlow()

    /** Snapshot for Undo; survives long enough for the snackbar to act on. Session-scoped. */
    private var lastAssignment: List<PreviousAssignment>? = null

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onFilterChange(filter: AppsFilter) {
        _filter.value = filter
    }

    fun onSortChange(sort: AppsSort) {
        _sort.value = sort
    }

    /** Row long-press: enters selection mode with the row selected (adds/removes). */
    fun toggleSelection(packageName: String) {
        _selection.value = toggleSelectionSet(_selection.value, packageName)
        _selectionMode.value = _selection.value.isNotEmpty()
    }

    /** Toolbar entry: enter selection mode with nothing ticked yet. */
    fun enterSelectionMode() {
        _selectionMode.value = true
    }

    fun clearSelection() {
        _selection.value = emptySet()
        _selectionMode.value = false
    }

    /** Select every row currently visible (filter, search and system policy applied). */
    fun selectAllVisible() {
        _selection.value = uiState.value.apps.map { it.packageName }.toSet()
        _selectionMode.value = true
    }

    /** Deep-link entry: apply `uncategorized` (or `{categoryId}`) once; see AppsScreen. */
    fun applyInitialFilter(token: String?) {
        onFilterChange(appsFilterToken(token))
    }

    /**
     * Bulk assign: every selected row keeps `isCategoryUserSet = 1` (explicit user choice),
     * the library re-renders from the Flow, and category history re-derives at read time
     * (ARCHITECTURE.md §13) — the fix is retroactively visible everywhere. The previous
     * assignments are snapshotted first so the confirmation snackbar can offer Undo.
     */
    fun assignSelectionTo(categoryId: String?, categoryName: String) {
        val packages = _selection.value.toList()
        if (packages.isEmpty()) return
        _selection.value = emptySet()
        _selectionMode.value = false
        viewModelScope.launch {
            applyAssignment(packages, categoryId, categoryName)
        }
    }

    /** Single reassign (row overflow or the New·Categorize pill). Same path, same snapshot. */
    fun assignPackage(packageName: String, categoryId: String?, categoryName: String) {
        viewModelScope.launch {
            applyAssignment(listOf(packageName), categoryId, categoryName)
        }
    }

    /**
     * Inline create from the picker: create a user category and assign the selection —
     * or one explicit package when [packageName] is set — as one motion. The new category
     * is real (persisted, user-created) so it shows up in the manager and in the chips.
     */
    fun createCategoryAndAssign(name: String, packageName: String? = null) {
        val single = packageName != null
        val packages = packageName?.let { listOf(it) } ?: _selection.value.toList()
        val trimmed = name.trim()
        if (packages.isEmpty() || trimmed.isEmpty()) return
        if (!single) {
            _selection.value = emptySet()
            _selectionMode.value = false
        }
        viewModelScope.launch {
            val existing = categoryRepository.observeCategories().firstOrNull()?.size ?: 0
            val newId = categoryRepository.create(trimmed, nextInlineCreateToken(existing))
            if (newId == null) {
                if (!single) {
                    _selection.value = packages.toSet()
                    _selectionMode.value = true
                }
                return@launch
            }
            applyAssignment(packages, newId, trimmed)
        }
    }

    /** Restores the assignments captured before the last bulk/single write. */
    fun undoLastAssignment() {
        val snapshot = lastAssignment ?: return
        lastAssignment = null
        viewModelScope.launch {
            // Rows that vanished since the snapshot simply update nothing.
            snapshot.forEach { previous ->
                applicationDao.setCategory(previous.packageName, previous.categoryId, previous.isCategoryUserSet)
            }
        }
    }

    private suspend fun applyAssignment(packages: List<String>, categoryId: String?, categoryName: String) {
        val previous = applicationDao.getByPackages(packages).map { app ->
            PreviousAssignment(app.packageName, app.categoryId, app.isCategoryUserSet)
        }
        applicationDao.setCategoryAll(packages, categoryId)
        lastAssignment = previous
        _events.tryEmit(AppsEvent.AssignmentCompleted(packages.size, categoryName))
    }

    private val base = combine(
        applicationDao.observeInstalled(),
        todayUsage(),
        weekUsage(),
        categoryRepository.observeCategories(),
    ) { applications, todayByApp, weekByApp, categories ->
        BaseData(applications, todayByApp, weekByApp, categories)
    }

    private val params = combine(_query, _filter, _sort, preferencesRepository.showSystemApps) { query, filter, sort, showSystemApps ->
        AppsParams(query, filter, sort, showSystemApps)
    }

    val uiState: StateFlow<AppsUiState> = combine(base, params) { data, (q, f, s, showSystemApps) ->
        val chips = categoryChips(data.categories)
        val chipById = chips.associateBy { it.id }
        val now = clock.nowUtcMs()
        val rows = appRows(data.applications, data.todayByApp, data.weekByApp, chipById, now)
        val filtered = filterAndSort(rows, q, chips, f, s, includeSystemApps = showSystemApps)
        AppsUiState(
            isLoading = false,
            hasUsageAccess = checker.hasUsageAccess(),
            apps = filtered,
            todayTotalMs = data.todayByApp.values.sum(),
            weekTotalMs = data.weekByApp.values.sum(),
            categories = chips,
            filter = f,
            sort = s,
            uncategorizedCount = filtered.count { it.categoryId == null },
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

    private data class AppsParams(
        val query: String,
        val filter: AppsFilter,
        val sort: AppsSort,
        val showSystemApps: Boolean,
    )
}

/** Window in which a freshly discovered app wears the quiet "New" marker. */
internal const val NEW_WINDOW_DAYS = 14L

/** Pure and tested: chip list built straight from the live categories (manager order). */
internal fun categoryChips(categories: List<AppCategory>): List<CategoryFilterChip> =
    categories.map { CategoryFilterChip(it.id, it.name, it.colorToken, it.sortOrder) }

/**
 * Library rows: every installed app appears; weekly totals are summed across the
 * trailing 7 days. Ordering is applied later by [filterAndSort].
 *
 * @param chipById current assignment → chip, for the row's identity line and icon tint.
 * @param nowUtcMs current time, for the "New" marker. `firstSeenAt == 0` means unknown.
 */
internal fun appRows(
    applications: List<Application>,
    todayByApp: Map<String, Long>,
    weekByApp: Map<String, Long>,
    chipById: Map<String, CategoryFilterChip> = emptyMap(),
    nowUtcMs: Long = 0L,
): List<AppRow> = applications.map { app ->
    val chip = app.categoryId?.let { chipById[it] }
    AppRow(
        packageName = app.packageName,
        displayName = app.displayName,
        categoryId = app.categoryId,
        categoryName = chip?.name,
        categoryColorToken = chip?.colorToken,
        todayMs = todayByApp[app.packageName] ?: 0L,
        weekMs = weekByApp[app.packageName] ?: 0L,
        isNew = app.firstSeenAt > 0L && nowUtcMs > 0L &&
            nowUtcMs - app.firstSeenAt < NEW_WINDOW_DAYS * 24L * 60L * 60L * 1000L,
        isSystemApp = app.isSystemApp,
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

/**
 * Pure and tested (AppsLogicTest): search (label, package, category name, "uncategorized"),
 * category filter, system-app policy and sort. Deterministic: usage sorts order by the
 * matching window descending with a name tiebreak; alphabetical and category sorts are
 * name-ordered. Uncategorized rows sort after every assigned category in the category sort.
 */
internal fun filterAndSort(
    rows: List<AppRow>,
    query: String,
    categories: List<CategoryFilterChip>,
    filter: AppsFilter,
    sort: AppsSort,
    includeSystemApps: Boolean = true,
): List<AppRow> {
    val q = query.trim().lowercase()
    val categoryNames = categories.associate { it.id to it.name.lowercase() }
    val filtered = rows.filter { row ->
        if (!includeSystemApps && row.isSystemApp) return@filter false
        val matchesFilter = when (filter) {
            AppsFilter.All -> true
            AppsFilter.Uncategorized -> row.categoryId == null
            is AppsFilter.Category -> row.categoryId == filter.id
        }
        val matchesQuery = q.isEmpty() ||
            row.displayName.lowercase().contains(q) ||
            row.packageName.lowercase().contains(q) ||
            (row.categoryId?.let { categoryNames[it]?.contains(q) } == true) ||
            (row.categoryId == null && "uncategorized".contains(q))
        matchesFilter && matchesQuery
    }
    val byName = compareBy<AppRow> { it.displayName.lowercase() }
    return when (sort) {
        AppsSort.MOST_USED_TODAY ->
            filtered.sortedWith(compareByDescending<AppRow> { it.todayMs }.then(byName))
        AppsSort.MOST_USED_WEEK ->
            filtered.sortedWith(compareByDescending<AppRow> { it.weekMs }.then(byName))
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

/**
 * Pure and tested: partition pre-sorted rows into (used, zero-usage) for the usage sorts.
 * In alphabetical/category sorts there is no divider — everything stays in one list.
 * Within the zero group the incoming (usage-descending, name-tiebroken) order keeps
 * zero-usage apps alphabetical.
 */
internal fun splitUsageRows(rows: List<AppRow>, sort: AppsSort): Pair<List<AppRow>, List<AppRow>> {
    val usage: (AppRow) -> Long = when (sort) {
        AppsSort.MOST_USED_TODAY -> AppRow::todayMs
        AppsSort.MOST_USED_WEEK -> AppRow::weekMs
        AppsSort.ALPHABETICAL, AppsSort.CATEGORY -> return rows to emptyList()
    }
    return rows.partition { usage(it) > 0L }
}
