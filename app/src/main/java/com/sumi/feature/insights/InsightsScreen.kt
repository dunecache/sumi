package com.sumi.feature.insights

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.sumi.core.designsystem.component.EmptyState
import com.sumi.core.designsystem.component.LoadingState
import com.sumi.core.designsystem.component.SumiCard
import com.sumi.core.designsystem.component.WeeklyBars
import com.sumi.core.designsystem.theme.SumiSpacing
import com.sumi.domain.insights.InsightKind

/** Insights (M3): facts plain, interpretations hedged with a quiet marker. */
@Composable
fun InsightsScreen(viewModel: InsightsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    Column(Modifier.fillMaxSize().padding(SumiSpacing.screenHorizontal)) {
        Spacer(Modifier.height(SumiSpacing.lg))
        Text(text = "Insights", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(SumiSpacing.md))
        when {
            state.isLoading -> LoadingState()
            state.insights.isEmpty() -> EmptyState(
                title = "Patterns need a few days",
                message = "Sumi starts learning your day from now. Come back later.",
            )
            else -> {
                Text(
                    text = "Is my usage going up, down, or staying steady?",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(SumiSpacing.sm))
                SumiCard {
                    WeeklyBars(dailyTotals = state.weeklyTotals, caption = "Daily screen time this week")
                }
                Spacer(Modifier.height(SumiSpacing.md))
                state.insights.forEach { insight ->
                    Text(
                        text = (if (insight.kind == InsightKind.INTERPRETATION) "This might be " else "") + insight.text.lowercaseFirst(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (insight.kind == InsightKind.INTERPRETATION) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Spacer(Modifier.height(SumiSpacing.xs))
                }
            }
        }
    }
}

private fun String.lowercaseFirst(): String =
    if (isEmpty()) this else this[0].lowercaseChar() + substring(1)
