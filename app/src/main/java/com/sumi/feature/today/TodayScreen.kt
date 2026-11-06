package com.sumi.feature.today

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.sumi.core.designsystem.component.DurationText
import com.sumi.core.designsystem.component.EmptyState
import com.sumi.core.designsystem.component.LoadingState
import com.sumi.core.designsystem.component.SumiCard
import com.sumi.core.designsystem.component.SumiSectionHeader
import com.sumi.core.designsystem.component.TimelineStrip
import com.sumi.core.designsystem.theme.SumiFontFamilies
import com.sumi.core.designsystem.theme.SumiSpacing
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Today (DESIGN.md §5.1): total, previous-day comparison, most-used, timeline, observation. */
@Composable
fun TodayScreen(
    onOpenSettings: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    TodayContent(state = state, onOpenSettings = onOpenSettings)
}

@Composable
fun TodayContent(state: TodayUiState, onOpenSettings: () -> Unit) {
    when {
        state.isLoading -> LoadingState()
        !state.hasUsageAccess -> Column(
            Modifier.fillMaxSize().padding(SumiSpacing.screenHorizontal),
        ) {
            Spacer(Modifier.height(SumiSpacing.lg))
            Text(
                text = "TODAY · ${LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = "Your day, in perspective.", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(SumiSpacing.lg))
            EmptyState(
                title = "Usage Access is off",
                message = "Sumi starts learning your day from now. Enable Usage Access to see your usage.",
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenSettings) { Text("Settings") }
        }
        state.totalDurationMs == 0L && state.mostUsed.isEmpty() -> Column(
            Modifier.fillMaxSize().padding(SumiSpacing.screenHorizontal),
        ) {
            Spacer(Modifier.height(SumiSpacing.lg))
            Text(
                text = "TODAY · ${LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = "Your day, in perspective.", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(SumiSpacing.lg))
            EmptyState(
                title = "No usage yet",
                message = "Sumi starts learning your day from now. Come back later.",
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenSettings) { Text("Settings") }
        }
        else -> Column(Modifier.fillMaxSize().padding(SumiSpacing.screenHorizontal)) {
            Spacer(Modifier.height(SumiSpacing.lg))
            Text(
                text = "TODAY · ${LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = "Your day, in perspective.", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(SumiSpacing.md))
            Text(text = "Screen time today", style = MaterialTheme.typography.labelMedium)
            Text(
                text = formatHours(state.totalDurationMs),
                style = MaterialTheme.typography.displayLarge,
                fontFamily = SumiFontFamilies.Numeric,
            )
            Text(
                text = "Yesterday: ${formatHours(state.previousDayDurationMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(SumiSpacing.md))
            SumiSectionHeader("MOST USED")
            Spacer(Modifier.height(SumiSpacing.sm))
            state.mostUsed.forEach { app ->
                Row(Modifier.fillMaxWidth().padding(vertical = SumiSpacing.xs)) {
                    Text(text = app.displayName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    DurationText(durationMs = app.durationMs)
                }
            }
            Spacer(Modifier.height(SumiSpacing.md))
            SumiSectionHeader("TIMELINE")
            Spacer(Modifier.height(SumiSpacing.sm))
            SumiCard {
                TimelineStrip(hourlyTotals = state.timeline, caption = "When did usage happen today?")
            }
            state.observation?.let { note ->
                Spacer(Modifier.height(SumiSpacing.md))
                Text(text = note, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenSettings) { Text("Settings") }
        }
    }
}

private fun formatHours(ms: Long): String {
    val minutes = ms / 60_000L
    val hours = minutes / 60
    val rem = minutes % 60
    return if (hours > 0) "${hours}h ${rem}m" else "${rem}m"
}
