package com.sumi.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Compose-native charts (Canvas, no chart library). Every chart states its question
 * as a caption at the call site and provides a content description with the takeaway.
 */

@Composable
fun TimelineStrip(
    hourlyTotals: List<Long>, // 24 values, 0..23
    modifier: Modifier = Modifier,
    caption: String = "Usage intensity per hour",
) {
    val max = hourlyTotals.maxOrNull()?.takeIf { it > 0 } ?: 1L
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.surfaceVariant
    val description = "$caption: peak ${max / 60_000L}m in one hour"
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics { contentDescription = description },
    ) {
        val barWidth = size.width / 24f
        hourlyTotals.take(24).forEachIndexed { index, value ->
            val fraction = (value.toFloat() / max.toFloat()).coerceIn(0f, 1f)
            val barHeight = (size.height * fraction).coerceAtLeast(2f)
            val left = index * barWidth + barWidth * 0.2f
            val widthPx = barWidth * 0.6f
            drawRoundRect(
                color = if (value > 0) accent else muted,
                topLeft = Offset(left, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(widthPx, barHeight),
                cornerRadius = CornerRadius(4f, 4f),
            )
        }
    }
}

@Composable
fun WeeklyBars(
    dailyTotals: List<Long>, // 7 values
    modifier: Modifier = Modifier,
    caption: String = "Daily screen time this week",
) {
    val max = dailyTotals.maxOrNull()?.takeIf { it > 0 } ?: 1L
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.surfaceVariant
    val description = "$caption: busiest day ${max / 60_000L}m"
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .semantics { contentDescription = description },
    ) {
        val barWidth = size.width / 7f
        dailyTotals.take(7).forEachIndexed { index, value ->
            val fraction = (value.toFloat() / max.toFloat()).coerceIn(0f, 1f)
            val barHeight = (size.height * fraction).coerceAtLeast(2f)
            val left = index * barWidth + barWidth * 0.25f
            val widthPx = barWidth * 0.5f
            drawRoundRect(
                color = if (index == 6) accent else muted,
                topLeft = Offset(left, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(widthPx, barHeight),
                cornerRadius = CornerRadius(6f, 6f),
            )
        }
    }
}
