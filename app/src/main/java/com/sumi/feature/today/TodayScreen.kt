package com.sumi.feature.today

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.sumi.core.designsystem.component.AppUsageRow
import com.sumi.core.designsystem.component.CategoryDistributionCard
import com.sumi.core.designsystem.component.EmptyState
import com.sumi.core.designsystem.component.SumiCard
import com.sumi.core.designsystem.component.SumiSectionHeader
import com.sumi.core.designsystem.component.TimelineStrip
import com.sumi.core.designsystem.component.formatDuration
import com.sumi.core.designsystem.theme.NumericLarge
import com.sumi.core.designsystem.theme.ReflectionBody
import com.sumi.core.designsystem.theme.SumiSpacing
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Today per the navigation/Today reference §3: date eyebrow, serif headline, one primary
 * metric, most-used (≤5), timeline, one summary sentence — in that fixed order, nothing
 * else. Settings lives in the top app bar only (no duplicate link at the bottom).
 * Neutral framing only; loading renders em-dashes, never spinners.
 */
@Composable
fun TodayScreen(
    viewModel: TodayViewModel = hiltViewModel(),
    onOpenAppDetail: (packageName: String) -> Unit = {},
    onOpenCategoryDetail: (categoryId: String) -> Unit = {},
    onOpenCategories: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val iconLoader = remember(viewModel) { viewModel.iconLoader::iconFor }
    var showGoalSheet by remember { mutableStateOf(false) }
    TodayContent(
        state = state,
        iconLoader = iconLoader,
        onOpenAppDetail = onOpenAppDetail,
        onOpenCategoryDetail = onOpenCategoryDetail,
        onOpenCategories = onOpenCategories,
        onGoalEdit = { showGoalSheet = true },
    )
    if (showGoalSheet) {
        GoalEditSheet(
            currentMinutes = state.intentionGoalMinutes,
            enabled = state.intentionEnabled,
            onDismiss = { showGoalSheet = false },
            onSave = { viewModel.saveGoal(it); showGoalSheet = false },
            onRemove = { viewModel.removeGoal(); showGoalSheet = false },
            onEnabledChange = viewModel::setGoalEnabled,
        )
    }
}

@Composable
fun TodayContent(
    state: TodayUiState,
    iconLoader: (suspend (String) -> Drawable?)? = null,
    onOpenAppDetail: (packageName: String) -> Unit = {},
    onOpenCategoryDetail: (categoryId: String) -> Unit = {},
    onOpenCategories: () -> Unit = {},
    onGoalEdit: () -> Unit = {},
) {
    when {
        // Loading keeps the layout with placeholders (§3.9) — previous content, never a spinner.
        state.isLoading -> TodayBody(state = state, iconLoader = iconLoader)
        !state.hasUsageAccess -> Column(Modifier.fillMaxSize().padding(SumiSpacing.screenHorizontal)) {
            Spacer(Modifier.height(SumiSpacing.lg))
            TodayHeader()
            Spacer(Modifier.weight(1f))
            EmptyState(
                title = "Usage Access is off",
                message = "Sumi starts learning your day from now. Enable Usage Access to see your usage.",
            )
            Spacer(Modifier.weight(1f))
        }
        state.totalDurationMs == 0L && state.mostUsed.isEmpty() && state.previousDayDurationMs == null ->
            Column(Modifier.fillMaxSize().padding(SumiSpacing.screenHorizontal)) {
                Spacer(Modifier.height(SumiSpacing.lg))
                TodayHeader()
                Spacer(Modifier.weight(1f))
                EmptyState(
                    title = "No usage yet",
                    message = "Sumi starts learning your day from now. Come back later.",
                )
                Spacer(Modifier.weight(1f))
            }
        else -> TodayBody(
            state = state,
            iconLoader = iconLoader,
            onOpenAppDetail = onOpenAppDetail,
            onOpenCategoryDetail = onOpenCategoryDetail,
            onOpenCategories = onOpenCategories,
            onGoalEdit = onGoalEdit,
        )
    }
}

/** Eyebrow + headline shared by every state; exact strings per reference §3.2/§3.3. */
@Composable
private fun TodayHeader() {
    Text(
        text = "TODAY · ${LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase()}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(text = "Your day, in perspective.", style = MaterialTheme.typography.displayLarge)
}

/** Populated (or placeholder-loading) layout, vertical order fixed per §3.1. */
@Composable
private fun TodayBody(
    state: TodayUiState,
    iconLoader: (suspend (String) -> Drawable?)?,
    onOpenAppDetail: (packageName: String) -> Unit = {},
    onOpenCategoryDetail: (categoryId: String) -> Unit = {},
    onOpenCategories: () -> Unit = {},
    onGoalEdit: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SumiSpacing.screenHorizontal),
    ) {
        Spacer(Modifier.height(SumiSpacing.lg))
        TodayHeader()
        Spacer(Modifier.height(SumiSpacing.xl))

        // Primary metric (§3.4): one number, tabular mono, primary text — never accent, never a warning hue.
        Text(
            text = "Screen time today",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (state.isLoading) "—" else formatDuration(state.totalDurationMs),
            style = NumericLarge,
        )
        Spacer(Modifier.height(SumiSpacing.xs))
        state.comparison?.let { comparison ->
            Text(
                text = if (state.isLoading) "—" else comparison,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Daily intention (§4): prominent, editable, purely informational — never an enforcement.
        if (!state.isLoading) {
            Spacer(Modifier.height(SumiSpacing.lg))
            DailyIntentionCard(
                state = state.intention,
                onEdit = onGoalEdit,
            )
        }

        // Category distribution (§5): total tracked, proportional bar, labeled list. Uncategorized last.
        if (!state.isLoading && state.categories.isNotEmpty()) {
            Spacer(Modifier.height(SumiSpacing.xl))
            CategoryDistributionCard(
                segments = state.categories,
                totalDurationMs = state.trackedDurationMs,
                onCategoryClick = onOpenCategoryDetail,
                onSeeAll = onOpenCategories,
            )
        }

        // Most used (§3.5): ranked rows with icon, name, duration, share — no bulky cards.
        if (!state.isLoading && state.mostUsed.isNotEmpty()) {
            Spacer(Modifier.height(SumiSpacing.xl))
            SumiSectionHeader("MOST USED")
            Spacer(Modifier.height(SumiSpacing.sm))
            Column(verticalArrangement = Arrangement.spacedBy(SumiSpacing.xs)) {
                state.mostUsed.forEach { app ->
                    AppUsageRow(
                        packageName = app.packageName,
                        displayName = app.displayName,
                        durationMs = app.durationMs,
                        // App percentages use total tracked application usage as the denominator.
                        shareOfTotal = if (state.trackedDurationMs > 0L) {
                            app.durationMs.toFloat() / state.trackedDurationMs.toFloat()
                        } else {
                            null
                        },
                        iconLoader = iconLoader,
                        onClick = { onOpenAppDetail(app.packageName) },
                    )
                }
            }
        }

        // Timeline (§3.6): real hourly buckets; ghost bars read as "no data yet", never an error.
        Spacer(Modifier.height(SumiSpacing.xl))
        SumiSectionHeader("TIMELINE")
        Spacer(Modifier.height(SumiSpacing.sm))
        SumiCard {
            Text(
                text = "When was the phone in use today?",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(SumiSpacing.md))
            TimelineStrip(hourlyTotals = state.timeline)
        }

        // Summary sentence (§3.7): one quiet line, reflection role (Literata), not bold.
        if (!state.isLoading) {
            state.observation?.let { note ->
                Spacer(Modifier.height(SumiSpacing.md))
                Text(
                    text = note,
                    style = ReflectionBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(SumiSpacing.lg))
    }
}
