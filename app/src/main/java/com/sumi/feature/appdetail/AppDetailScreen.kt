package com.sumi.feature.appdetail

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.sumi.core.designsystem.component.AppIcon
import com.sumi.core.designsystem.component.EmptyState
import com.sumi.core.designsystem.component.SumiCard
import com.sumi.core.designsystem.component.SumiSectionHeader
import com.sumi.core.designsystem.component.WeeklyBars
import com.sumi.core.designsystem.component.formatDuration
import com.sumi.core.designsystem.theme.NumericLarge
import com.sumi.core.designsystem.theme.SumiSpacing

/**
 * App detail (M2): individual usage history on real data — identity header, today's
 * total, a 7-day chart, a 30-day average, and today's session summary. The raw package
 * name appears here as quiet metadata; it never headlines the screen.
 */
@Composable
fun AppDetailScreen(
    packageName: String,
    viewModel: AppDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val iconLoader = remember(viewModel) { viewModel.iconLoader::iconFor }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SumiSpacing.screenHorizontal),
    ) {
        Spacer(Modifier.height(SumiSpacing.lg))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SumiSpacing.md),
        ) {
            AppIcon(
                packageName = state.packageName,
                displayName = state.displayName,
                loader = iconLoader,
            )
            Column {
                Text(text = state.displayName, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = state.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(SumiSpacing.xl))

        Text(
            text = "Screen time today",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (state.isLoading) "—" else formatDuration(state.todayMs),
            style = NumericLarge,
        )

        if (!state.isLoading && state.week.any { it > 0L }) {
            Spacer(Modifier.height(SumiSpacing.xl))
            SumiSectionHeader("LAST 7 DAYS")
            Spacer(Modifier.height(SumiSpacing.sm))
            SumiCard { WeeklyBars(dailyTotals = state.week) }
        }

        if (!state.isLoading) {
            val activeDays = state.month.count { it > 0L }
            val monthAverage = if (activeDays >= 2) {
                state.month.filter { it > 0L }.average()
            } else {
                null
            }
            monthAverage?.let { avg ->
                Spacer(Modifier.height(SumiSpacing.md))
                Text(
                    text = "About ${formatDuration(avg.toLong())} a day over the last 30 days.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!state.isLoading) {
            state.sessionSummary?.let { summary ->
                Spacer(Modifier.height(SumiSpacing.md))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!state.isLoading && state.week.none { it > 0L } && state.sessionSummary == null) {
            Spacer(Modifier.height(SumiSpacing.lg))
            EmptyState(
                title = "No history yet",
                message = "Usage history for this app will appear here.",
            )
        }

        Spacer(Modifier.height(SumiSpacing.lg))
    }
}
