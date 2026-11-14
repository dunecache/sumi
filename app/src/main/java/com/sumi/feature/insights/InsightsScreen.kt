package com.sumi.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sumi.core.designsystem.component.CategoryDistributionCard
import com.sumi.core.designsystem.component.EmptyState
import com.sumi.core.designsystem.component.LoadingState
import com.sumi.core.designsystem.component.SumiCard
import com.sumi.core.designsystem.component.SumiSectionHeader
import com.sumi.core.designsystem.component.WeeklyBars
import com.sumi.core.designsystem.component.formatDuration
import com.sumi.core.designsystem.theme.ReflectionBody
import com.sumi.core.designsystem.theme.SumiSpacing
import com.sumi.domain.insights.InsightKind
import com.sumi.domain.insights.Averages

/**
 * Insights (M3): facts plain, interpretations hedged and visually quiet — italic, muted,
 * with a small leading marker (DESIGN.md §7). No prefix rewriting of engine copy.
 */
@Composable
private fun UncategorizedPrompt(
    durationMs: Long,
    onReview: () -> Unit,
) {
    // Calm, actionable, always available — never a nag (DESIGN.md §7).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SumiSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${formatDuration(durationMs)} isn't categorized yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "These apps are waiting to be assigned a category.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onReview) {
            Text("Review")
        }
    }
}

/** Minimum share of uncategorized tracked time before we prompt (15%). */
private const val UNCATEGORIZED_PROMPT_SHARE = 0.15f

@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel = hiltViewModel(),
    onOpenCategoryDetail: (categoryId: String) -> Unit = {},
    onReviewUncategorized: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SumiSpacing.screenHorizontal),
    ) {
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
                if (state.categories.isNotEmpty()) {
                    Spacer(Modifier.height(SumiSpacing.xl))
                    CategoryDistributionCard(
                        title = "WHERE THE WEEK WENT",
                        segments = state.categories,
                        totalDurationMs = state.weekTrackedMs,
                        onCategoryClick = onOpenCategoryDetail,
                        onUncategorizedClick = onReviewUncategorized,
                    )
                }
                if (state.weekTrackedMs > 0L && state.uncategorizedShare >= UNCATEGORIZED_PROMPT_SHARE) {
                    Spacer(Modifier.height(SumiSpacing.md))
                    // The biggest "category" when the library is untrained — quiet prompt, not a nag.
                    UncategorizedPrompt(
                        durationMs = state.uncategorizedMs,
                        onReview = onReviewUncategorized,
                    )
                }
                state.socialWeekMs?.let { socialMs ->
                    Spacer(Modifier.height(SumiSpacing.md))
                    SumiCard {
                        Text(
                            text = "TIME IN SOCIAL APPS",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(SumiSpacing.xs))
                        Text(
                            text = "${formatDuration(socialMs)} this week",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(SumiSpacing.xs))
                        Text(
                            text = "${Averages.percentOfShare(state.socialWeekShare)}% of tracked time",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = { onOpenCategoryDetail(com.sumi.domain.categories.DefaultPackageCategories.SOCIAL) },
                            contentPadding = PaddingValues(horizontal = 0.dp),
                        ) {
                            Text("Open social analytics")
                        }
                    }
                }
                Spacer(Modifier.height(SumiSpacing.xl))
                SumiSectionHeader("OBSERVATIONS")
                Spacer(Modifier.height(SumiSpacing.sm))
                Column(verticalArrangement = Arrangement.spacedBy(SumiSpacing.sm)) {
                    state.insights.forEach { insight ->
                        if (insight.kind == InsightKind.INTERPRETATION) {
                            Row {
                                Text(
                                    text = "·",
                                    style = ReflectionBody,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(SumiSpacing.xs))
                                Text(
                                    text = insight.text,
                                    style = ReflectionBody,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontStyle = FontStyle.Italic,
                                )
                            }
                        } else {
                            Text(
                                text = insight.text,
                                style = ReflectionBody,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(SumiSpacing.lg))
            }
        }
    }
}
