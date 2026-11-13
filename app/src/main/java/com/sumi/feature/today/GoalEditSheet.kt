package com.sumi.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sumi.core.designsystem.component.formatDuration
import com.sumi.core.designsystem.theme.NumericBody
import com.sumi.core.designsystem.theme.SumiSpacing
import kotlin.math.roundToInt

/**
 * Goal editing sheet. Framing stays calm: the target is a pace cue, not a limit — nothing
 * locks, nothing nags. The slider moves in 15-minute steps between 30m and 12h.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditSheet(
    currentMinutes: Int?,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (minutes: Int) -> Unit,
    onRemove: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) {
    var sliderMinutes by rememberSaveable {
        mutableFloatStateOf((currentMinutes ?: DEFAULT_MINUTES).toFloat())
    }
    val minutes = sliderMinutes.roundToInt()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SumiSpacing.screenHorizontal),
        ) {
            Text(
                text = "Daily intention",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(SumiSpacing.xs))
            Text(
                text = "A target for your day, not a limit. It never blocks apps or sends reminders.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(SumiSpacing.xl))
            Text(
                text = formatDuration(minutes * MILLIS_PER_MINUTE),
                style = NumericBody,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(SumiSpacing.sm))
            Slider(
                value = sliderMinutes,
                onValueChange = { sliderMinutes = it },
                valueRange = MIN_MINUTES.toFloat()..MAX_MINUTES.toFloat(),
                steps = ((MAX_MINUTES - MIN_MINUTES) / STEP_MINUTES) - 1,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("30m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("12h", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(SumiSpacing.lg))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Goal active",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                )
            }
            Spacer(Modifier.height(SumiSpacing.lg))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (currentMinutes != null) {
                    TextButton(onClick = onRemove) {
                        Text("Remove")
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = { onSave(minutes) }) { Text("Save") }
            }
            Spacer(Modifier.height(SumiSpacing.xl))
        }
    }
}

private const val MILLIS_PER_MINUTE = 60_000L
private const val DEFAULT_MINUTES = 240 // 4h — a calm starting point, fully adjustable
private const val MIN_MINUTES = 30
private const val MAX_MINUTES = 720
private const val STEP_MINUTES = 15
