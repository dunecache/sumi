package com.sumi.feature.today

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sumi.core.datastore.DailyGoalConfig
import com.sumi.core.designsystem.component.SumiCard
import com.sumi.core.designsystem.component.SumiSectionHeader
import com.sumi.core.designsystem.component.formatDuration
import com.sumi.core.designsystem.theme.SumiSpacing
import com.sumi.domain.goal.DailyGoal
import com.sumi.domain.goal.DailyGoalProgress
import com.sumi.domain.goal.DailyGoalStatus

/**
 * Daily intention goal (Today section). An awareness instrument only: it never blocks,
 * gates, or notifies. Framing stays calm and non-judgmental (DESIGN.md §5); exceeding the
 * goal is reported in neutral language, never in alarm hues — and remaining time is never
 * shown as negative (there is no such value).
 */
sealed interface DailyIntentionState {
    /** No goal set, or the goal is configured but paused. The UI shows a quiet call-to-action. */
    data object Disabled : DailyIntentionState
    data class Progress(val progress: DailyGoalProgress) : DailyIntentionState
}

/** Pure mapping from config + the same aggregate as the headline stat to render state. */
internal fun intentionState(config: DailyGoalConfig, usedMs: Long): DailyIntentionState {
    val minutes = config.activeGoalMinutes ?: return DailyIntentionState.Disabled
    return DailyIntentionState.Progress(DailyGoal.evaluate(usedMs, minutes * MILLIS_PER_MINUTE))
}

/** Neutral status line for the progress state; remaining time never renders negative. */
internal fun intentionStatusLine(progress: DailyGoalProgress): String = when (progress.status) {
    DailyGoalStatus.ON_TRACK -> "${formatDuration(progress.remainingMs)} remaining"
    DailyGoalStatus.REACHED -> "Goal met — ${formatDuration(progress.goalMs)} today"
    DailyGoalStatus.EXCEEDED -> "${formatDuration(progress.overMs)} over your ${formatDuration(progress.goalMs)} goal"
}

@Composable
fun DailyIntentionCard(
    state: DailyIntentionState,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SumiCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SumiSectionHeader("Daily intention", modifier = Modifier.weight(1f))
            TextButton(onClick = onEdit) {
                Text(
                    text = if (state is DailyIntentionState.Disabled) "Set a goal" else "Edit",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        when (state) {
            DailyIntentionState.Disabled -> {
                Text(
                    text = "A calm target for the day. Set one to see your pace — it never locks anything.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is DailyIntentionState.Progress -> {
                val progress = state.progress
                Spacer(Modifier.height(SumiSpacing.xs))
                Text(
                    text = "${formatDuration(progress.usedMs)} of ${formatDuration(progress.goalMs)} used",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(SumiSpacing.sm))
                LinearProgressIndicator(
                    progress = { progress.fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    // Exceeding the goal never switches hue: calm neutrality by design.
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(SumiSpacing.sm))
                Text(
                    text = intentionStatusLine(progress),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val MILLIS_PER_MINUTE = 60_000L
