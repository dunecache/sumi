package com.sumi.feature.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sumi.core.designsystem.component.AppUsageRow
import com.sumi.core.designsystem.component.EmptyState
import com.sumi.core.designsystem.component.LoadingState
import com.sumi.core.designsystem.component.SumiCard
import com.sumi.core.designsystem.component.SumiSectionHeader
import com.sumi.core.designsystem.component.WeeklyBars
import com.sumi.core.designsystem.component.formatDuration
import com.sumi.core.designsystem.theme.NumericLarge
import com.sumi.core.designsystem.theme.SumiSpacing

/**
 * Category detail: total usage, share of tracked time, the trailing week, and every
 * assigned app. Derives from the same per-app records as everything else; the week
 * series applies the *current* assignment (ARCHITECTURE.md §13 recalculation policy).
 */
@Composable
fun CategoryDetailScreen(
    viewModel: CategoryDetailViewModel = hiltViewModel(),
    onOpenAppDetail: (packageName: String) -> Unit = {},
    onManageCategories: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val iconLoader = remember(viewModel) { viewModel.iconLoader::iconFor }
    CategoryDetailContent(
        state = state,
        iconLoader = iconLoader,
        onOpenAppDetail = onOpenAppDetail,
        onManageCategories = onManageCategories,
    )
}

/** Stateless content; previews below exercise loading, removed and populated states. */
@Composable
internal fun CategoryDetailContent(
    state: CategoryDetailUiState,
    modifier: Modifier = Modifier,
    iconLoader: (suspend (String) -> android.graphics.drawable.Drawable?)? = null,
    onOpenAppDetail: (packageName: String) -> Unit = {},
    onManageCategories: () -> Unit = {},
) {
    when {
        state.isLoading -> LoadingState()
        !state.exists -> Column(modifier.fillMaxSize().padding(SumiSpacing.screenHorizontal)) {
            Spacer(Modifier.height(SumiSpacing.lg))
            EmptyState(
                title = "Category removed",
                message = "This category no longer exists. Its apps are uncategorized.",
            )
        }
        else -> Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(SumiSpacing.screenHorizontal),
        ) {
            Spacer(Modifier.height(SumiSpacing.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(colorToken = state.colorToken, size = 28.dp)
                Text(
                    text = state.name,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = SumiSpacing.md),
                )
            }
            Spacer(Modifier.height(SumiSpacing.md))
            Text(
                text = "Today",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = formatDuration(state.todayMs), style = NumericLarge)
            Spacer(Modifier.height(SumiSpacing.xs))
            Text(
                text = "${(state.todayShare * 100).toInt()}% of tracked time today",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(SumiSpacing.xl))
            SumiSectionHeader("THIS WEEK")
            Spacer(Modifier.height(SumiSpacing.sm))
            Text(
                text = "${formatDuration(state.weekTotalMs)} in the trailing 7 days",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(SumiSpacing.sm))
            SumiCard {
                WeeklyBars(dailyTotals = state.week, caption = "Daily usage in ${state.name}")
            }

            Spacer(Modifier.height(SumiSpacing.xl))
            SumiSectionHeader("APPS IN CATEGORY")
            Spacer(Modifier.height(SumiSpacing.sm))
            if (state.apps.isEmpty()) {
                Text(
                    text = "No apps assigned yet. Assign some from an app's detail screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(SumiSpacing.xs)) {
                    state.apps.forEach { app ->
                        AppUsageRow(
                            packageName = app.packageName,
                            displayName = app.displayName,
                            durationMs = app.todayMs,
                            shareOfTotal = app.shareOfTracked,
                            iconLoader = iconLoader,
                            onClick = { onOpenAppDetail(app.packageName) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(SumiSpacing.lg))
            TextButton(onClick = onManageCategories) {
                Text("Manage categories")
            }
            Spacer(Modifier.height(SumiSpacing.xl))
        }
    }
}

