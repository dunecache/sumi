package com.sumi.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sumi.core.designsystem.theme.SumiFontFamilies
import com.sumi.core.designsystem.theme.SumiSpacing
import java.util.Locale

/** Shared design-system components. No hardcoded colors, spacing, or type sizes. */

@Composable
fun SumiCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = { Column(modifier = Modifier.padding(SumiSpacing.lg), content = { content() }) },
    )
}

@Composable
fun SumiSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
        modifier = modifier,
    )
}

@Composable
fun SumiLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Formats milliseconds as `3h 42m` per PRINCIPLES.md §5. */
@Composable
fun DurationText(durationMs: Long, modifier: Modifier = Modifier) {
    Text(
        text = formatDuration(durationMs),
        style = MaterialTheme.typography.bodyLarge,
        fontFamily = SumiFontFamilies.Numeric,
        modifier = modifier,
    )
}

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60

fun formatDuration(durationMs: Long): String {
    val totalMinutes = (durationMs / MILLIS_PER_MINUTE).coerceAtLeast(0)
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
