package com.sumi.feature.today

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import com.sumi.core.designsystem.component.DurationText
import com.sumi.core.designsystem.component.EmptyState
import com.sumi.core.designsystem.component.SumiCard
import com.sumi.core.designsystem.component.SumiSectionHeader
import com.sumi.core.designsystem.component.TimelineStrip
import com.sumi.core.designsystem.component.formatDuration
import com.sumi.core.designsystem.theme.SumiFontFamilies
import com.sumi.core.designsystem.theme.SumiSpacing
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Today per the navigation/Today reference §3: date eyebrow, serif headline, one primary
 * metric, most-used (≤5), timeline, one summary sentence, settings link — in that fixed
 * order, nothing else. Neutral framing only; loading renders em-dashes, never spinners.
 */
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
        // Loading keeps the layout with placeholders (§3.9) — previous content, never a spinner.
        state.isLoading -> TodayBody(state = state, onOpenSettings = onOpenSettings)
        !state.hasUsageAccess -> TodayShell(
            onOpenSettings = onOpenSettings,
            content = {
                EmptyState(
                    title = "Usage Access is off",
                    message = "Sumi starts learning your day from now. Enable Usage Access to see your usage.",
                )
            },
        )
        state.totalDurationMs == 0L && state.mostUsed.isEmpty() && state.previousDayDurationMs == null ->
            TodayShell(
                onOpenSettings = onOpenSettings,
                content = {
                    EmptyState(
                        title = "No usage yet",
                        message = "Sumi starts learning your day from now. Come back later.",
                    )
                },
            )
        else -> TodayBody(state = state, onOpenSettings = onOpenSettings)
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

/** Column shell: header, arbitrary content, settings link pinned to the bottom (§3.8). */
@Composable
private fun TodayShell(
    onOpenSettings: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(SumiSpacing.screenHorizontal)) {
        Spacer(Modifier.height(SumiSpacing.lg))
        TodayHeader()
        Spacer(Modifier.height(SumiSpacing.lg))
        content()
        Spacer(Modifier.weight(1f))
        SettingsLink(onOpenSettings)
    }
}

/** Settings text link: accent by default, ≥48dp touch target (§3.8, §7). */
@Composable
private fun SettingsLink(onOpenSettings: () -> Unit) {
    TextButton(
        onClick = onOpenSettings,
        modifier = Modifier.heightIn(min = SumiSpacing.minTouchTarget),
    ) { Text("Settings") }
}

/** Populated (or placeholder-loading) layout, vertical order fixed per §3.1. */
@Composable
private fun TodayBody(state: TodayUiState, onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(SumiSpacing.screenHorizontal)) {
        Spacer(Modifier.height(SumiSpacing.lg))
        TodayHeader()
        Spacer(Modifier.height(SumiSpacing.md))

        // Primary metric (§3.4): one number, normal weight, primary text — never accent, never a warning hue.
        Text(
            text = "Screen time today",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (state.isLoading) "—" else formatDuration(state.totalDurationMs),
            style = MaterialTheme.typography.displayLarge,
            fontFamily = SumiFontFamilies.Numeric,
        )
        state.previousDayDurationMs?.let { yesterday ->
            Text(
                text = "Yesterday: ${formatDuration(yesterday)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Most used (§3.5): max 5 rows, label + duration only — no bars, no percentages, no icons.
        if (!state.isLoading && state.mostUsed.isNotEmpty()) {
            Spacer(Modifier.height(SumiSpacing.md))
            SumiSectionHeader("MOST USED")
            Spacer(Modifier.height(SumiSpacing.sm))
            state.mostUsed.forEach { app ->
                Row(Modifier.fillMaxWidth().padding(vertical = SumiSpacing.xs)) {
                    Text(
                        text = app.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    DurationText(durationMs = app.durationMs)
                }
            }
        }

        // Timeline (§3.6): ghost bars read as "no data yet", never an error.
        Spacer(Modifier.height(SumiSpacing.md))
        SumiSectionHeader("TIMELINE")
        Spacer(Modifier.height(SumiSpacing.sm))
        SumiCard {
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
                    textAlign = TextAlign.Start,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        SettingsLink(onOpenSettings)
    }
}
