package com.sumi.feature.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.sumi.core.designsystem.component.AppUsageRow
import com.sumi.core.designsystem.component.DurationText
import com.sumi.core.designsystem.component.EmptyState
import com.sumi.core.designsystem.component.LoadingState
import com.sumi.core.designsystem.component.formatDuration
import com.sumi.core.designsystem.theme.SumiSpacing

/**
 * Apps: searchable library. Rows show the launcher icon, the friendly display name,
 * today's and the trailing week's usage, and a proportional share bar for today.
 * Raw package identifiers never appear as the primary presentation; they live in
 * App detail as quiet metadata.
 */
@Composable
fun AppsScreen(
    viewModel: AppsViewModel = hiltViewModel(),
    onOpenAppDetail: (packageName: String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val query by viewModel.query.collectAsState()
    val iconLoader = remember(viewModel) { viewModel.iconLoader::iconFor }
    Column(Modifier.fillMaxSize().padding(SumiSpacing.screenHorizontal)) {
        Spacer(Modifier.height(SumiSpacing.lg))
        Text(text = "Apps", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(SumiSpacing.md))
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            label = { Text("Search apps") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(SumiSpacing.md))
        when {
            state.isLoading -> LoadingState()
            state.apps.isEmpty() -> EmptyState(
                title = "No apps yet",
                message = "Sumi starts learning your day from now. Come back later.",
            )
            else -> {
                val filtered = viewModel.filtered(state.apps)
                if (filtered.isEmpty()) {
                    EmptyState(
                        title = "No matches",
                        message = "Try a different search.",
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(SumiSpacing.sm)) {
                        items(filtered, key = { it.packageName }) { app ->
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
                                            text = "wk ${formatWeek(app.weekMs)}",
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
}

/** Compact week total; "wk" prefix keeps the trailing column readable. */
private fun formatWeek(ms: Long): String = formatDuration(ms)
