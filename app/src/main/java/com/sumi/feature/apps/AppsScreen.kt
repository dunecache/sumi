package com.sumi.feature.apps

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sumi.core.designsystem.component.EmptyState
import com.sumi.core.designsystem.component.LoadingState
import com.sumi.core.designsystem.component.PermissionState
import com.sumi.core.designsystem.theme.SumiSpacing
import com.sumi.core.designsystem.theme.categoryColorOrNeutral
import com.sumi.feature.categories.CategoryPickerOption
import com.sumi.feature.categories.CategoryPickerSheet

/**
 * Apps: searchable, filterable, sortable library. The categorization funnel lives here:
 * long-press (or the Select action) enters selection mode, "Move N apps to…" persists
 * assignments, and category history re-derives at read time — the residual closes fast.
 *
 * Raw package identifiers never appear as the primary presentation; they live in App
 * detail as quiet metadata. Category assignment also lives there and on each row's
 * overflow action.
 */
@Composable
fun AppsScreen(
    viewModel: AppsViewModel = hiltViewModel(),
    onOpenAppDetail: (packageName: String) -> Unit = {},
    onManageCategories: () -> Unit = {},
    /** Deep-link token from navigation: `uncategorized`, `all`, or a category id. */
    initialFilter: String? = null,
) {
    val state by viewModel.uiState.collectAsState()
    val query by viewModel.query.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val selectionMode by viewModel.selectionMode.collectAsState()
    val iconLoader = remember(viewModel) { viewModel.iconLoader::iconFor }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showBulkAssign by remember { mutableStateOf(false) }
    var reassignTarget by remember { mutableStateOf<AppRow?>(null) }

    // Reduced motion: the system animator scale is 0 — no entrance transitions.
    val reducedMotion = remember {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }

    // One-shot deep-link application (e.g. from the Insights uncategorized prompt).
    LaunchedEffect(initialFilter) { viewModel.applyInitialFilter(initialFilter) }

    LaunchedEffect(snackbarHostState) {
        viewModel.events.collect { event ->
            when (event) {
                is AppsEvent.AssignmentCompleted -> {
                    val plural = if (event.count == 1) "app" else "apps"
                    val result = snackbarHostState.showSnackbar(
                        message = "Moved ${event.count} $plural to ${event.categoryName}",
                        actionLabel = "Undo",
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoLastAssignment()
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AppsContent(
            state = state,
            query = query,
            selection = selection,
            selectionMode = selectionMode,
            reducedMotion = reducedMotion,
            iconLoader = iconLoader,
            onQueryChange = viewModel::onQueryChange,
            onFilterChange = viewModel::onFilterChange,
            onSortChange = viewModel::onSortChange,
            onToggleSelection = viewModel::toggleSelection,
            onClearSelection = viewModel::clearSelection,
            onSelectAll = viewModel::selectAllVisible,
            onEnterSelectionMode = viewModel::enterSelectionMode,
            onOpenDetail = onOpenAppDetail,
            onManageCategories = onManageCategories,
            onReassign = { reassignTarget = it },
            onOpenPermissionSettings = { context.startActivity(viewModel.usageAccessIntent()) },
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (selectionMode) SELECTION_BAR_CLEARANCE else 0.dp),
        )

        // The categorization funnel's main lever: a bottom bar, one tap from "move".
        AnimatedVisibility(
            visible = selectionMode,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            SelectionBottomBar(
                count = selection.size,
                onMove = { showBulkAssign = true },
            )
        }
    }

    if (showBulkAssign) {
        val count = selection.size
        val plural = if (count == 1) "app" else "apps"
        CategoryPickerSheet(
            title = "Move $count $plural to…",
            subtitle = "History updates everywhere — assignment is your call.",
            options = state.categories.map { CategoryPickerOption(it.id, it.name, it.colorToken) },
            onDismiss = { showBulkAssign = false },
            onSelect = { categoryId ->
                viewModel.assignSelectionTo(categoryId, categoryName(state, categoryId))
                showBulkAssign = false
            },
            onCreateCategory = { name ->
                viewModel.createCategoryAndAssign(name)
                showBulkAssign = false
            },
        )
    }

    reassignTarget?.let { row ->
        CategoryPickerSheet(
            title = "Move to…",
            subtitle = row.displayName,
            options = state.categories.map { CategoryPickerOption(it.id, it.name, it.colorToken) },
            onDismiss = { reassignTarget = null },
            showSelection = true,
            selectedId = row.categoryId,
            onSelect = { categoryId ->
                viewModel.assignPackage(row.packageName, categoryId, categoryName(state, categoryId))
                reassignTarget = null
            },
            onCreateCategory = { name ->
                viewModel.createCategoryAndAssign(name, packageName = row.packageName)
                reassignTarget = null
            },
        )
    }
}

/** Human name for an assignment target — feeds the confirmation snackbar. */
private fun categoryName(state: AppsUiState, categoryId: String?): String =
    categoryId?.let { id -> state.categories.firstOrNull { it.id == id }?.name } ?: "Uncategorized"

/**
 * Stateless rendering of the Apps destination (the TodayContent pattern): everything the
 * screen shows, minus ViewModel collection — directly renderable by UI tests and previews.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppsContent(
    state: AppsUiState,
    query: String,
    selection: Set<String>,
    selectionMode: Boolean,
    reducedMotion: Boolean,
    iconLoader: (suspend (String) -> Drawable?)?,
    onQueryChange: (String) -> Unit,
    onFilterChange: (AppsFilter) -> Unit,
    onSortChange: (AppsSort) -> Unit,
    onToggleSelection: (String) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onEnterSelectionMode: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onManageCategories: () -> Unit,
    onReassign: (AppRow) -> Unit,
    onOpenPermissionSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sortExpanded by remember { mutableStateOf(false) }

    if (!state.isLoading && !state.hasUsageAccess) {
        PermissionState(
            title = "Usage Access is off",
            message = "Sumi can't see which apps you use until you grant Usage Access. " +
                "Your existing data stays on this device — it just stops updating.",
            buttonLabel = "Open Usage Access settings",
            onFixClick = onOpenPermissionSettings,
        )
        return
    }

    Column(modifier.fillMaxSize().padding(SumiSpacing.screenHorizontal)) {
        Spacer(Modifier.height(SumiSpacing.lg))
        Text(text = "Apps", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(SumiSpacing.md))
        if (selectionMode) {
            SelectionHeader(
                count = selection.size,
                onClear = onClearSelection,
                onSelectAll = onSelectAll,
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SumiSpacing.sm),
            ) {
                SearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { sortExpanded = true }) {
                        Icon(Icons.Filled.Sort, contentDescription = "Sort apps")
                    }
                    DropdownMenu(
                        expanded = sortExpanded,
                        onDismissRequest = { sortExpanded = false },
                    ) {
                        AppsSortMenuItem("Most used today", AppsSort.MOST_USED_TODAY, state.sort) {
                            onSortChange(AppsSort.MOST_USED_TODAY); sortExpanded = false
                        }
                        AppsSortMenuItem("Most used this week", AppsSort.MOST_USED_WEEK, state.sort) {
                            onSortChange(AppsSort.MOST_USED_WEEK); sortExpanded = false
                        }
                        AppsSortMenuItem("Alphabetical", AppsSort.ALPHABETICAL, state.sort) {
                            onSortChange(AppsSort.ALPHABETICAL); sortExpanded = false
                        }
                        AppsSortMenuItem("By category", AppsSort.CATEGORY, state.sort) {
                            onSortChange(AppsSort.CATEGORY); sortExpanded = false
                        }
                    }
                }
                IconButton(onClick = onEnterSelectionMode) {
                    Icon(Icons.Filled.Checklist, contentDescription = "Select apps to move")
                }
            }
        }
        Spacer(Modifier.height(SumiSpacing.sm))
        CategoryFilterRow(
            state = state,
            onFilterChange = onFilterChange,
            onManageCategories = onManageCategories,
        )
        Spacer(Modifier.height(SumiSpacing.sm))

        // A quiet success moment — the residual bucket closed.
        val allCategorized = !state.isLoading && query.isBlank() &&
            state.filter == AppsFilter.All && state.apps.isNotEmpty() && state.uncategorizedCount == 0
        if (allCategorized) {
            Row(
                modifier = Modifier.padding(vertical = SumiSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(SUCCESS_ICON),
                )
                Text(
                    text = "Every app has a category.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = SumiSpacing.xs),
                )
            }
        }

        when {
            state.isLoading -> LoadingState()
            state.apps.isEmpty() -> AppsEmptyState(
                state = state,
                query = query,
            )
            else -> AppsList(
                state = state,
                selection = selection,
                reducedMotion = reducedMotion,
                iconLoader = iconLoader,
                selectionMode = selectionMode,
                onToggleSelection = onToggleSelection,
                onOpenDetail = onOpenDetail,
                onReassign = onReassign,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The Material 3 search field (docked search bar, results render live below). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    DockedSearchBar(
        expanded = false,
        onExpandedChange = {},
        modifier = modifier,
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = onQueryChange,
                expanded = false,
                onExpandedChange = {},
                placeholder = { Text("Search apps or categories") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                } else {
                    null
                },
            )
        },
        content = {},
    )
}

/** Live-data chip row: All, every category in the user's order, Uncategorized, then Manage…. */
@Composable
private fun CategoryFilterRow(
    state: AppsUiState,
    onFilterChange: (AppsFilter) -> Unit,
    onManageCategories: () -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(SumiSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item {
            SumiFilterChip(
                selected = state.filter is AppsFilter.All,
                onClick = { onFilterChange(AppsFilter.All) },
                label = "All",
            )
        }
        items(state.categories, key = { it.id }) { category ->
            SumiFilterChip(
                selected = state.filter == AppsFilter.Category(category.id),
                onClick = { onFilterChange(AppsFilter.Category(category.id)) },
                label = category.name,
                dotColorToken = category.colorToken,
            )
        }
        item {
            SumiFilterChip(
                selected = state.filter is AppsFilter.Uncategorized,
                onClick = { onFilterChange(AppsFilter.Uncategorized) },
                label = "Uncategorized",
                dotColorToken = "stone-1",
            )
        }
        item {
            TextButton(onClick = onManageCategories) {
                Text("Manage…")
            }
        }
    }
}

/** Filter chip styled with Sumi tokens — never default Material secondary styling. */
@Composable
private fun SumiFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    dotColorToken: String? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        border = BorderStroke(
            width = CHIP_BORDER,
            color = if (selected) {
                MaterialTheme.colorScheme.outline
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            labelColor = MaterialTheme.colorScheme.onSurface,
            leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        label = { Text(label, maxLines = 1) },
        leadingIcon = dotColorToken?.let { token ->
            {
                Box(
                    Modifier
                        .size(CHIP_DOT)
                        .clip(CircleShape)
                        .background(categoryColorOrNeutral(token)),
                )
            }
        },
    )
}

@Composable
private fun AppsEmptyState(
    state: AppsUiState,
    query: String,
) {
    when {
        query.isNotBlank() -> EmptyState(
            title = "No matches",
            message = "Nothing matches “$query”. Try a different search.",
        )
        state.filter == AppsFilter.Uncategorized -> EmptyState(
            title = "Everything is categorized",
            message = "Nothing is left in the uncategorized bucket. Nice.",
        )
        state.filter is AppsFilter.Category -> {
            val name = state.categories
                .firstOrNull { it.id == (state.filter as AppsFilter.Category).id }?.name
                ?: "this category"
            EmptyState(
                title = "Nothing in $name yet",
                message = "Long-press apps in All and move them here.",
            )
        }
        else -> EmptyState(
            title = "No apps yet",
            message = "Sumi starts learning your day from now. Come back later.",
        )
    }
}

@Composable
private fun AppsList(
    state: AppsUiState,
    selection: Set<String>,
    reducedMotion: Boolean,
    iconLoader: (suspend (String) -> Drawable?)?,
    selectionMode: Boolean,
    onToggleSelection: (String) -> Unit,
    onOpenDetail: (String) -> Unit,
    onReassign: (AppRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val windowIsWeek = state.sort == AppsSort.MOST_USED_WEEK
    val (usedRows, zeroRows) = splitUsageRows(state.apps, state.sort)

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SumiSpacing.sm),
        contentPadding = PaddingValues(bottom = if (selectionMode) SELECTION_BAR_CLEARANCE else 0.dp),
    ) {
        items(usedRows, key = { it.packageName }) { app ->
            AppRowSlot(
                app = app,
                state = state,
                selection = selection,
                selectionMode = selectionMode,
                windowIsWeek = windowIsWeek,
                iconLoader = iconLoader,
                onToggleSelection = onToggleSelection,
                onOpenDetail = onOpenDetail,
                onReassign = onReassign,
                rowModifier = if (reducedMotion) Modifier else Modifier.animateItem(),
            )
        }
        if (zeroRows.isNotEmpty()) {
            item(key = ZERO_DIVIDER_KEY) { NotUsedYetDivider() }
            items(zeroRows, key = { it.packageName }) { app ->
                AppRowSlot(
                    app = app,
                    state = state,
                    selection = selection,
                    selectionMode = selectionMode,
                    windowIsWeek = windowIsWeek,
                    iconLoader = iconLoader,
                    onToggleSelection = onToggleSelection,
                    onOpenDetail = onOpenDetail,
                    onReassign = onReassign,
                    rowModifier = if (reducedMotion) Modifier else Modifier.animateItem(),
                )
            }
        }
        item(key = FOOTER_KEY) {
            Text(
                text = "Totals exclude your launcher, keyboard and Sumi itself.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = SumiSpacing.md),
            )
        }
    }
}

@Composable
private fun AppRowSlot(
    app: AppRow,
    state: AppsUiState,
    selection: Set<String>,
    selectionMode: Boolean,
    windowIsWeek: Boolean,
    iconLoader: (suspend (String) -> Drawable?)?,
    onToggleSelection: (String) -> Unit,
    onOpenDetail: (String) -> Unit,
    onReassign: (AppRow) -> Unit,
    rowModifier: Modifier,
) {
    if (selectionMode) {
        SelectableAppRow(
            app = app,
            selected = app.packageName in selection,
            iconLoader = iconLoader,
            onToggle = { onToggleSelection(app.packageName) },
            modifier = rowModifier,
        )
        return
    }
    // Percentages only in usage sorts; the bar follows the matching window.
    val (share, windowWeek) = when (state.sort) {
        AppsSort.MOST_USED_TODAY -> todayShare(app.todayMs, state.todayTotalMs) to false
        AppsSort.MOST_USED_WEEK -> weekShare(app.weekMs, state.weekTotalMs) to true
        AppsSort.ALPHABETICAL, AppsSort.CATEGORY -> null to false
    }
    AppLibraryRow(
        app = app,
        shareOfTotal = share,
        iconLoader = iconLoader,
        windowIsWeek = windowWeek,
        onClick = { onOpenDetail(app.packageName) },
        onLongClick = { onToggleSelection(app.packageName) },
        onMoveToCategory = { onReassign(app) },
        onOpenDetails = { onOpenDetail(app.packageName) },
        onCategorizeNew = { onReassign(app) },
        modifier = rowModifier,
    )
}

/** In usage sorts, selection of the Select action enters selection mode with all visible rows selected. */
@Composable
private fun SelectionHeader(
    count: Int,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(SumiSpacing.minTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClear) {
            Icon(Icons.Filled.Close, contentDescription = "Clear selection")
        }
        Text(
            text = "$count selected",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onSelectAll) {
            Text("Select all")
        }
    }
}

/** The spec's bottom bar: one quiet, full-width call to action. */
@Composable
private fun SelectionBottomBar(
    count: Int,
    onMove: () -> Unit,
) {
    val plural = if (count == 1) "app" else "apps"
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = SumiSpacing.screenHorizontal, vertical = SumiSpacing.md),
        ) {
            Button(
                onClick = onMove,
                enabled = count > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (count > 0) "Move $count $plural to…" else "Select apps to move")
            }
        }
    }
}

@Composable
private fun AppsSortMenuItem(label: String, value: AppsSort, current: AppsSort, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = if (current == value) {
            { Icon(Icons.Filled.Check, contentDescription = "Selected") }
        } else {
            null
        },
    )
}

private fun todayShare(ms: Long, total: Long): Float? =
    if (total > 0L && ms > 0L) ms.toFloat() / total.toFloat() else null

private fun weekShare(ms: Long, total: Long): Float? =
    if (total > 0L && ms > 0L) ms.toFloat() / total.toFloat() else null

private const val ZERO_DIVIDER_KEY = "__zero_div__"
private const val FOOTER_KEY = "__footer__"
private const val SELECTION_BAR_CLEARANCE = 88.dp
private val CHIP_DOT = 10.dp
private val CHIP_BORDER = 1.dp
private val SUCCESS_ICON = 18.dp
