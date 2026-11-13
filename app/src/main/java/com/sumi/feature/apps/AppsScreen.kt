package com.sumi.feature.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.sumi.core.designsystem.component.AppUsageRow
import com.sumi.core.designsystem.component.DurationText
import com.sumi.core.designsystem.component.EmptyState
import com.sumi.core.designsystem.component.LoadingState
import com.sumi.core.designsystem.component.formatDuration
import com.sumi.core.designsystem.theme.SumiSpacing
import com.sumi.core.designsystem.theme.categoryColorOrNeutral
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sort

/**
 * Apps: searchable, filterable library. Rows show the launcher icon, the friendly display
 * name, today's and the trailing week's usage, and a proportional share bar for today.
 * Raw package identifiers never appear as the primary presentation; they live in App
 * detail as quiet metadata. Category assignment lives on the app detail screen.
 */
@Composable
fun AppsScreen(
    viewModel: AppsViewModel = hiltViewModel(),
    onOpenAppDetail: (packageName: String) -> Unit = {},
    onManageCategories: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val query by viewModel.query.collectAsState()
    val iconLoader = remember(viewModel) { viewModel.iconLoader::iconFor }
    var sortExpanded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(SumiSpacing.screenHorizontal)) {
        Spacer(Modifier.height(SumiSpacing.lg))
        Text(text = "Apps", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(SumiSpacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Search apps") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Box {
                IconButton(onClick = { sortExpanded = true }) {
                    Icon(Icons.Filled.Sort, contentDescription = "Sort apps")
                }
                DropdownMenu(
                    expanded = sortExpanded,
                    onDismissRequest = { sortExpanded = false },
                ) {
                    SortMenuItem("Most used", state.sort == AppsSort.MOST_USED) {
                        viewModel.onSortChange(AppsSort.MOST_USED); sortExpanded = false
                    }
                    SortMenuItem("Least used", state.sort == AppsSort.LEAST_USED) {
                        viewModel.onSortChange(AppsSort.LEAST_USED); sortExpanded = false
                    }
                    SortMenuItem("Alphabetical", state.sort == AppsSort.ALPHABETICAL) {
                        viewModel.onSortChange(AppsSort.ALPHABETICAL); sortExpanded = false
                    }
                    SortMenuItem("Category", state.sort == AppsSort.CATEGORY) {
                        viewModel.onSortChange(AppsSort.CATEGORY); sortExpanded = false
                    }
                }
            }
        }
        Spacer(Modifier.height(SumiSpacing.sm))
        CategoryFilterRow(
            state = state,
            onFilterChange = viewModel::onFilterChange,
            onManageCategories = onManageCategories,
        )
        Spacer(Modifier.height(SumiSpacing.sm))
        when {
            state.isLoading -> LoadingState()
            state.apps.isEmpty() -> {
                val hasActiveFilter = query.isNotBlank() || state.filter !is AppsFilter.All
                EmptyState(
                    title = if (hasActiveFilter) "No matches" else "No apps yet",
                    message = if (hasActiveFilter) {
                        "Try a different search or filter."
                    } else {
                        "Sumi starts learning your day from now. Come back later."
                    },
                )
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(SumiSpacing.sm)) {
                    items(state.apps, key = { it.packageName }) { app ->
                        AppUsageRow(
                            packageName = app.packageName,
                            displayName = app.displayName,
                            durationMs = app.todayMs,
                            shareOfTotal = if (state.todayTotalMs > 0L && app.todayMs > 0L) {
                                app.todayMs.toFloat() / state.todayTotalMs.toFloat()
                            } else {
                                null
                            },
                            iconLoader = iconLoader,
                            onClick = { onOpenAppDetail(app.packageName) },
                            trailing = {
                                Column(horizontalAlignment = Alignment.End) {
                                    DurationText(durationMs = app.todayMs)
                                    Text(
                                        text = "wk ${formatDuration(app.weekMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

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
            FilterChip(
                selected = state.filter is AppsFilter.All,
                onClick = { onFilterChange(AppsFilter.All) },
                label = { Text("All") },
            )
        }
        items(state.categories, key = { it.id }) { category ->
            FilterChip(
                selected = state.filter == AppsFilter.Category(category.id),
                onClick = { onFilterChange(AppsFilter.Category(category.id)) },
                label = { Text(category.name) },
                leadingIcon = {
                    Box(
                        Modifier
                            .size(ChipDot)
                            .clip(CircleShape)
                            .background(categoryColorOrNeutral(category.colorToken)),
                    )
                },
            )
        }
        item {
            FilterChip(
                selected = state.filter is AppsFilter.Uncategorized,
                onClick = { onFilterChange(AppsFilter.Uncategorized) },
                label = { Text("Uncategorized") },
            )
        }
        item {
            TextButton(onClick = onManageCategories) {
                Text("Manage…")
            }
        }
    }
}

@Composable
private fun SortMenuItem(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = "Selected") }
        } else {
            null
        },
    )
}

private val ChipDot = 10.dp
