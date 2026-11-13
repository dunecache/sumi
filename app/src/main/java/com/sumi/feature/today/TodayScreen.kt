package com.sumi.feature.today

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
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.sumi.core.designsystem.component.AppUsageRow
import com.sumi.core.designsystem.component.EmptyState
import com.sumi.core.designsystem.component.SumiCard
import com.sumi.core.designsystem.component.SumiSectionHeader
import com.sumi.core.designsystem.component.TimelineStrip
import com.sumi.core.designsystem.component.formatDuration
import com.sumi.core.designsystem.theme.NumericLarge
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
) {
    val state by viewModel.uiState.collectAsState()
    val iconLoader = remember(viewModel) { viewModel.iconLoader::iconFor }
    TodayContent(state = state, iconLoader = iconLoader)
}

@Composable
fun TodayContent(
    state: TodayUiState,
    iconLoader: (suspend (String) -> android.graphics.drawable.Drawable?)? = null,
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
        else -> TodayBody(state = state, iconLoader = iconLoader)
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
private fun TodayBody(state: TodayUiState, iconLoader: (suspend (String) -> android.graphics.drawable.Drawable?)?) {
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
                        shareOfTotal = if (state.totalDurationMs > 0L) {
                            app.durationMs.toFloat() / state.totalDurationMs.toFloat()
                        } else {
                            null
                        },
                        iconLoader = iconLoader,
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

        // Summary sentence (§3.7): one quiet line, secondary size, not bold.
        if (!state.isLoading) {
            state.observation?.let { note ->
                Spacer(Modifier.height(SumiSpacing.md))
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(SumiSpacing.lg))
    }
}
