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
import java.util.Locale
import com.sumi.core.designsystem.component.AppUsageRow
import com.sumi.core.designsystem.component.DailyBars
import com.sumi.core.designsystem.component.EmptyState
import com.sumi.core.designsystem.component.LoadingState
import com.sumi.core.designsystem.component.SumiCard
import com.sumi.core.designsystem.component.SumiSectionHeader
import com.sumi.core.designsystem.component.formatDuration
import com.sumi.core.designsystem.theme.NumericLarge
import com.sumi.core.designsystem.theme.SumiSpacing
import com.sumi.domain.insights.Averages

/**
 * Category detail: total for the selected range, share of tracked time, the daily trend,
 * the neutral comparison against the equivalent previous window, and every assigned app.
 * Derives from the same per-app records as everything else; the series applies the
 * *current* assignment (ARCHITECTURE.md §13 recalculation policy).
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
                text = state.rangeCaption.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = formatDuration(state.rangeTotalMs), style = NumericLarge)
            Spacer(Modifier.height(SumiSpacing.xs))
            Text(
                text = "${Averages.percentOfShare(state.rangeShare)}% of tracked time ${state.range.windowLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.comparison?.let { comparison ->
                Text(
                    text = comparison,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.rangeTotalMs > 0L) {
                Spacer(Modifier.height(SumiSpacing.xl))
                SumiSectionHeader("DAILY TREND")
                Spacer(Modifier.height(SumiSpacing.sm))
                SumiCard {
                    Text(
                        text = "Daily usage in ${state.name} across ${state.range.windowLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(SumiSpacing.md))
                    DailyBars(
                        days = state.dayBars,
                    )
                    Spacer(Modifier.height(SumiSpacing.sm))
                    state.mostActiveDayIndex?.let { index ->
                        Text(
                            text = "Most active day: ${state.dayLabels[index]} · ${formatDuration(state.series[index])}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.quietestDayIndex?.let { index ->
                        Text(
                            text = "Quietest day: ${state.dayLabels[index]} · ${formatDuration(state.series[index])}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(SumiSpacing.xl))
            SumiSectionHeader("APPS IN CATEGORY")
            Spacer(Modifier.height(SumiSpacing.sm))
            Text(
                text = "Usage across ${state.range.windowLabel}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(SumiSpacing.xs))
            if (state.apps.isEmpty()) {
                Text(
                    text = "No apps assigned yet. Assign some from an app's detail screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (state.rangeTotalMs == 0L) {
                Column {
                    EmptyState(
                        title = "No usage in this range",
                        message = "Nothing was recorded for ${state.name} across ${state.range.windowLabel}.",
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(SumiSpacing.xs)) {
                    state.apps.forEach { app ->
                        AppUsageRow(
                            packageName = app.packageName,
                            displayName = app.displayName,
                            durationMs = app.rangeMs,
                            shareOfTotal = app.shareOfTracked,
                            iconLoader = iconLoader,
                            onClick = { onOpenAppDetail(app.packageName) },
                            sharePeriod = state.range.windowLabel.replaceFirstChar { it.lowercase() },
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
